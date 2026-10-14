package com.conferbot.sdk.core.agent

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import org.junit.Test

class AgentBotParserTest {

    @Test
    fun `reads agentBot only when it is well formed`() {
        assertThat(AgentBotParser.parseAgentBot(mapOf())).isNull()
        assertThat(AgentBotParser.parseAgentBot(mapOf("agentBot" to null))).isNull()
        assertThat(AgentBotParser.parseAgentBot(mapOf("agentBot" to mapOf("displayName" to "x")))).isNull()
        val bot = AgentBotParser.parseAgentBot(
            mapOf(
                "name" to "Shop",
                "agentBot" to mapOf(
                    "agentId" to "a1",
                    "greeting" to " Hi there ",
                    "starters" to listOf("Pricing?", "pricing?", "", mapOf("text" to "Refunds")),
                    "avatar" to "https://x/a.png"
                )
            )
        )
        assertThat(bot).isEqualTo(AgentBot("a1", "Shop", "Hi there", listOf("Pricing?", "Refunds"), "https://x/a.png"))
        assertThat(bot!!.agentDetailsId).isEqualTo("ai-agent-a1")
    }

    @Test
    fun `knows an ai agent from a person`() {
        assertThat(AgentBotParser.isAIAgent(mapOf("isAIAgent" to true))).isTrue()
        assertThat(AgentBotParser.isAIAgent(mapOf("_id" to "ai-agent-x"))).isTrue()
        assertThat(AgentBotParser.isAIAgent(mapOf("_id" to "64f0", "name" to "Sam"))).isFalse()
        assertThat(AgentBotParser.isAIAgent(null)).isFalse()
    }

    @Test
    fun `uses only the handover assigned to this agent, never a form before it`() {
        val nodes = listOf(
            mapOf("id" to "w", "type" to "welcome-node"),
            mapOf("id" to "h1", "type" to "human-handover-node", "data" to mapOf("assignedAIAgents" to listOf("other"))),
            mapOf(
                "id" to "h2", "type" to "human-handover-node",
                "data" to mapOf("assignedAIAgents" to listOf("a1"), "enablePreChatQuestions" to true, "handoverMessage" to "wait", "priority" to "high")
            )
        )
        val (welcome, handover) = AgentBotParser.agentShellNodes(nodes, "a1")
        assertThat(welcome?.get("id")).isEqualTo("w")
        assertThat(handover?.get("id")).isEqualTo("h2")
        assertThat(AgentBotParser.agentShellNodes(nodes.take(2), "a1")).isEqualTo(null to null)
        @Suppress("UNCHECKED_CAST")
        val data = AgentBotParser.agentHandoverData(handover?.get("data") as Map<String, Any?>, "a1")
        assertThat(data["assignedAIAgents"]).isEqualTo(listOf("a1"))
        assertThat(data["handoverMessage"]).isEqualTo("")
        assertThat(data["enablePreChatQuestions"]).isEqualTo(false)
        assertThat(data["priority"]).isEqualTo("high")
    }

    @Test
    fun `knows when the ai is still answering`() {
        assertThat(AgentBotParser.isAgentAIActive(emptyList())).isTrue()
        assertThat(AgentBotParser.isAgentAIActive(listOf("agent-left-chat"))).isFalse()
        assertThat(AgentBotParser.isAgentAIActive(listOf("ai-agent-inactive-close", "ai-agent-resumed"))).isTrue()
    }

    @Test
    fun `cleans sources, buttons and cards like the web widget`() {
        val sources = AgentBotParser.normalizeSources(
            listOf(
                mapOf("title" to "Pricing", "url" to "https://www.acme.com/pricing"),
                mapOf("title" to "dup", "url" to "https://www.acme.com/pricing"),
                mapOf("title" to "bad", "url" to "javascript:alert(1)"),
                mapOf("title" to "Refunds", "articleId" to "0123456789abcdef01234567"),
                mapOf("url" to "https://acme.com/a"),
                mapOf("url" to "https://acme.com/b")
            )
        )
        assertThat(sources.map { it.title }).containsExactly("Pricing", "Refunds", "acme.com").inOrder()
        assertThat(AgentBotParser.sourceHost("https://www.conferbot.com/pricing")).isEqualTo("conferbot.com")
        assertThat(
            AgentBotParser.answerActionsOf(
                listOf(mapOf("label" to "See pricing", "url" to "https://a.co/p"), mapOf("label" to "x", "url" to "ftp://a"), mapOf("label" to "Again", "url" to "https://a.co/p"))
            )
        ).containsExactly(AnswerAction("See pricing", "https://a.co/p"))
        assertThat(AgentBotParser.answerCardOf(mapOf("rows" to listOf(listOf("Pro", "$49"))))).isNull()
        assertThat(AgentBotParser.answerCardOf(mapOf("title" to "Plans", "rows" to listOf(listOf("Pro", "$49"), listOf("Team", 99)), "url" to "http://insecure")))
            .isEqualTo(AnswerCard("Plans", listOf("Pro" to "$49", "Team" to "99"), null, null))
        val result = AgentBotParser.resultCardOf(mapOf("title" to "Order", "status" to "In transit", "tone" to "weird", "link" to mapOf("url" to "https://t.co/1")))
        assertThat(result?.tone).isEqualTo("neutral")
        assertThat(result?.linkLabel).isEqualTo("Open")
        assertThat(AgentBotParser.actionRequestOf(mapOf("token" to "t", "name" to "Cancel order", "summary" to listOf(mapOf("label" to "Order", "value" to 1042), mapOf("nope" to 1)))))
            .isEqualTo(ActionRequest("t", "Cancel order", listOf(ActionSummaryLine("Order", "1042"))))
        assertThat(AgentBotParser.actionRequestOf(mapOf("name" to "x"))).isNull()
        assertThat(AgentBotParser.followUpsOf(listOf("a", "", 3, "b", "c", "d"))).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `keeps only drawable rich messages`() {
        assertThat(AgentBotParser.safeRich(mapOf("kind" to "quick_replies", "options" to listOf("Yes")))).isNull()
        assertThat(AgentBotParser.safeRich(mapOf("kind" to "quick_replies", "text" to "Pick", "options" to listOf("Yes", "No"))))
            .isEqualTo(RichEntry.QuickReplies("Pick", listOf("Yes", "No")))
        val card = AgentBotParser.safeRich(mapOf("kind" to "card", "title" to "Pro", "imageUrl" to "http://x", "button" to mapOf("label" to "Buy", "url" to "https://b"))) as RichEntry.Card
        assertThat(card.imageUrl).isNull()
        assertThat(card.buttonUrl).isEqualTo("https://b")
        assertThat(AgentBotParser.safeRich(mapOf("kind" to "video"))).isNull()
    }

    @Test
    fun `adds extras only to ai replies, and nothing but sources to a fallback`() {
        assertThat(AgentBotParser.aiReplyExtrasOf(mapOf("agentDetails" to mapOf("_id" to "u1"), "sources" to listOf(mapOf("url" to "https://a.co"))))).isNull()
        val ai = mapOf("_id" to "ai-agent-a1", "isAIAgent" to true)
        val fallback = AgentBotParser.aiReplyExtrasOf(mapOf("agentDetails" to ai, "isFallback" to true, "actions" to listOf(mapOf("label" to "x", "url" to "https://a.co"))))
        assertThat(fallback?.isFallback).isTrue()
        assertThat(fallback?.actions).isEmpty()
        val reply = AgentBotParser.aiReplyExtrasOf(mapOf("agentDetails" to ai, "actions" to listOf(mapOf("label" to "Go", "url" to "https://a.co"))))
        assertThat(reply?.actions).containsExactly(AnswerAction("Go", "https://a.co"))
    }

    @Test
    fun `quick actions, ratings and goals`() {
        val actions = AgentBotParser.quickActionsOf(
            mapOf("chatQuickActions" to listOf(mapOf("label" to "Book a demo", "url" to "https://cal.com/x"), mapOf("label" to "Mail", "url" to "mailto:a@b.co"), mapOf("label" to "x", "url" to "javascript:1")))
        )
        assertThat(actions.map { it.label }).containsExactly("Book a demo", "Mail").inOrder()
        assertThat(AgentBotParser.answerFeedbackOn(mapOf("hideAnswerFeedback" to true))).isFalse()
        assertThat(AgentBotParser.answerFeedbackOn(null)).isTrue()
        assertThat(AgentBotParser.canRate("0123456789abcdef01234567")).isTrue()
        assertThat(AgentBotParser.canRate("agent-greeting-s1")).isFalse()
        assertThat(AgentBotParser.conversionEventOf("Book a demo")).isEqualTo("demo_booked")
    }

    @Test
    fun `reply extras survive the gson round trip used for persistence`() {
        val extras = AIReplyExtras(
            sources = listOf(AgentSource("Pricing", "https://a.co")),
            card = AnswerCard("Plans", listOf("Pro" to "$49", "Team" to "$99"), null, null)
        )
        val back = Gson().fromJson(Gson().toJson(extras), AIReplyExtras::class.java)
        assertThat(back).isEqualTo(extras)
    }
}
