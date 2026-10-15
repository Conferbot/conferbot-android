package com.conferbot.sdk.core.livechat

import com.conferbot.sdk.core.agent.HandoverPayload
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class LiveChatTest {

    @Test
    fun `reads a waiting queue status and drops none`() {
        assertThat(LiveChat.queueStatusOf(mapOf("status" to "none"))).isNull()
        assertThat(LiveChat.queueStatusOf(null)).isNull()
        val q = LiveChat.queueStatusOf(mapOf("status" to "waiting", "position" to 2, "ahead" to 1, "agentsOnline" to 3, "estimate" to mapOf("label" to " About 2 min ")))
        assertThat(q).isEqualTo(QueueStatus("waiting", 2, 1, 3, "About 2 min"))
    }

    @Test
    fun `says where the visitor stands, with the server's estimate as is`() {
        assertThat(LiveChat.queueLine(QueueStatus("waiting", 1, 0, 1, null))).isEqualTo("You're next in line")
        assertThat(LiveChat.queueLine(QueueStatus("waiting", 3, 2, 1, null))).isEqualTo("You're 3rd in line")
        assertThat(LiveChat.queueLine(null)).isEqualTo("Connecting you to the team")
        assertThat(LiveChat.queueSubline(QueueStatus("waiting", 1, 0, 1, "Under a minute"))).isEqualTo("Under a minute")
        assertThat(LiveChat.queueSubline(null)).isEqualTo("Our team will be with you soon")
    }

    @Test
    fun `writes ordinals`() {
        assertThat(listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 101, 111).map(LiveChat::ordinal))
            .containsExactly("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "101st", "111th").inOrder()
    }

    @Test
    fun `reads handover waiting and words it`() {
        val w = LiveChat.handoverWaitingOf(mapOf("offline" to false, "askEmail" to true))!!
        assertThat(w.reason).isEqualTo("busy")
        assertThat(LiveChat.handoverWaitingLine(w)).isEqualTo("Everyone's busy right now. Leave your email and they will reply.")
        val withEmail = w.copy(email = "jo@example.com")
        assertThat(LiveChat.handoverWaitingLine(withEmail)).endsWith("They'll reply to jo@example.com.")
    }

    @Test
    fun `labels back at times in the device clock`() {
        val utc = TimeZone.getTimeZone("UTC")
        val now = Calendar.getInstance(utc).apply { set(2026, Calendar.OCTOBER, 14, 10, 0, 0) }.time
        assertThat(LiveChat.backAtLabel(null)).isNull()
        assertThat(LiveChat.backAtLabel("nope")).isNull()
        assertThat(LiveChat.backAtLabel("2026-10-14T15:30:00.000Z", now, utc)).doesNotContain("day")
        assertThat(LiveChat.backAtLabel("2026-10-16T09:00:00Z", now, utc)).startsWith("Friday")
    }

    @Test
    fun `keeps a translation only with an original`() {
        assertThat(LiveChat.agentTranslationOf(mapOf("translation" to mapOf("from" to "en", "to" to "es", "original" to "Hi"))))
            .isEqualTo(AgentTranslation("en", "es", "Hi"))
        assertThat(LiveChat.agentTranslationOf(mapOf("translation" to mapOf("to" to "es", "original" to " ")))).isNull()
        assertThat(LiveChat.translationLabels("es-MX")).isEqualTo("Ver original" to "Ver traducción")
        assertThat(LiveChat.translationLabels("xx")).isEqualTo("See original" to "See translation")
    }

    private val record = listOf(LiveEntry("a1", "agent-message"), LiveEntry("v1", "user-live-message"), LiveEntry("v2", "user-live-message"))

    @Test
    fun `marks the latest visitor message sent, delivered or seen`() {
        assertThat(LiveChat.visitorReceiptOf(record, ReceiptMarks())).isEqualTo(VisitorReceipt("v2", ReceiptState.SENT))
        assertThat(LiveChat.visitorReceiptOf(record, ReceiptMarks(deliveredMessageId = "v2"))!!.state).isEqualTo(ReceiptState.DELIVERED)
        assertThat(LiveChat.visitorReceiptOf(record, ReceiptMarks("v2", "v2"))!!.state).isEqualTo(ReceiptState.SEEN)
        // Seen an earlier message only: the latest is not seen yet.
        assertThat(LiveChat.visitorReceiptOf(record, ReceiptMarks(seenMessageId = "v1"))!!.state).isEqualTo(ReceiptState.SENT)
        // An agent reply takes the line away.
        assertThat(LiveChat.visitorReceiptOf(record + LiveEntry("a2", "agent-message"), ReceiptMarks())).isNull()
    }

    @Test
    fun `finds the latest agent message`() {
        assertThat(LiveChat.latestAgentMessageId(record + LiveEntry("f1", "agent-message-file") + LiveEntry("v3", "user-live-message"))).isEqualTo("f1")
        assertThat(LiveChat.latestAgentMessageId(emptyList())).isEmpty()
    }

    @Test
    fun `checks emails and the owner's upload switch`() {
        assertThat(LiveChat.isEmail(" a@b.co ")).isTrue()
        assertThat(LiveChat.isEmail("a@b")).isFalse()
        assertThat(LiveChat.visitorUploadsOn(null)).isTrue()
        assertThat(LiveChat.visitorUploadsOn(mapOf("enableVisitorFileUpload" to false))).isFalse()
    }

    @Test
    fun `reads the rating settings and builds the payload`() {
        assertThat(LiveChat.csatConfigOf(null)).isNull()
        assertThat(LiveChat.csatConfigOf(mapOf("ratingType" to "stars"))).isNull()
        val c = LiveChat.csatConfigOf(mapOf("enabled" to true, "ratingType" to "bogus"))!!
        assertThat(c.ratingType).isEqualTo("stars")
        assertThat(c.titleText).isEqualTo("How was your experience?")
        assertThat(LiveChat.ratingPayload("s1", "stars", 4, " great "))
            .containsExactly("chatSessionId", "s1", "source", "post_chat_survey", "csatScore", 4, "feedback", "great")
        assertThat(LiveChat.ratingPayload("s1", "thumbs", false)).containsEntry("thumbsUp", false)
        assertThat(LiveChat.ratingPayload("s1", "nps", 9, "  ")).doesNotContainKey("feedback")
    }

    @Test
    fun `resolves file links and names them`() {
        assertThat(LiveChat.resolveMediaUrl("/api/v1/media/1", "https://api.example.com/")).isEqualTo("https://api.example.com/api/v1/media/1")
        assertThat(LiveChat.resolveMediaUrl("java\nscript:alert(1)", "https://a")).isNull()
        assertThat(LiveChat.isImageUrl("https://x/a.JPG?x=1")).isTrue()
        assertThat(LiveChat.isImageUrl("/media/abc", "image/png")).isTrue()
        assertThat(LiveChat.isImageUrl("/media/abc")).isFalse()
        assertThat(LiveChat.fileNameOf("Invoice.pdf", "/media/1")).isEqualTo("Invoice.pdf")
        assertThat(LiveChat.fileNameOf(null, "https://x/My%20File.pdf?sig=1")).isEqualTo("My File.pdf")
    }

    @Test
    fun `sends the wait time as the builder wrote it`() {
        fun wait(v: Any?) = HandoverPayload.waitMinutesOf(v)
        assertThat(wait("0.5")).isEqualTo(0.5)
        assertThat(wait("3")).isEqualTo(3)
        assertThat(wait(1.5)).isEqualTo(1.5)
        assertThat(wait(null)).isEqualTo(2)
        assertThat(wait("x")).isEqualTo(2)
    }

    @Test
    fun `prefills leave a message as the web widget does`() {
        assertThat(LiveChat.knownEmail("jo@example.com", emptyList())).isEqualTo("jo@example.com")
        assertThat(LiveChat.knownEmail(null, listOf("old@a.co", "Jo", "new@a.co"))).isEqualTo("new@a.co")
        assertThat(LiveChat.knownEmail("", emptyList())).isEmpty()
        assertThat(LiveChat.lastQuestion(listOf(mapOf("shape" to "user-live-message", "text" to "Do you ship to <b>Oman</b>?"), mapOf("shape" to "user-input-response", "text" to "Jo"))))
            .isEqualTo("Do you ship to Oman ?")
        assertThat(LiveChat.lastQuestion(listOf(mapOf("shape" to "user-live-message", "text" to "jo@example.com")))).isEmpty()
    }

    @Test
    fun `asks the server to decide once the max wait passes`() {
        val p = LiveChat.handoverTimeoutPayload("s1", mapOf("assignmentType" to "manual", "assignedAgents" to listOf("a2"), "agentAssignmentStrategy" to "round_robin"), "w1")
        assertThat(p["chatSessionId"]).isEqualTo("s1")
        assertThat(p["workspaceId"]).isEqualTo("w1")
        assertThat(p["assignmentData"]).isEqualTo(mapOf("assignmentType" to "manual", "assignmentStrategy" to "round_robin", "assignedAgents" to listOf("a2")))
    }

    @Test
    fun `the handover survey's rating goes as submit-chat-rating (#61)`() {
        assertThat(LiveChat.surveyRatingPayload("s1", 5, 4, " Quick help "))
            .isEqualTo(mapOf("chatSessionId" to "s1", "source" to "post_chat_survey", "csatScore" to 4, "feedback" to "Quick help"))
        assertThat(LiveChat.surveyRatingPayload("s1", 10, 9)).isEqualTo(mapOf("chatSessionId" to "s1", "source" to "post_chat_survey", "npsScore" to 9))
        assertThat(LiveChat.surveyRatingPayload("s1", null, 7)!!["csatScore"]).isEqualTo(5)
        assertThat(LiveChat.surveyRatingPayload("s1", 5, null)).isNull()
    }
}
