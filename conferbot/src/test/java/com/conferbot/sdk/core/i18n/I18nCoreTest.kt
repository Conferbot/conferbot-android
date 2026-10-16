package com.conferbot.sdk.core.i18n

import com.conferbot.sdk.core.agent.AgentBotParser
import com.conferbot.sdk.core.agent.AnswerFeedback
import com.conferbot.sdk.core.agent.Helpful
import com.conferbot.sdk.core.customization.Customizations
import com.conferbot.sdk.core.customization.Greeting
import com.conferbot.sdk.core.customization.Launcher
import com.conferbot.sdk.core.customization.LauncherIdleCall
import com.conferbot.sdk.core.customization.MascotMoods
import com.conferbot.sdk.core.home.Home
import com.conferbot.sdk.core.livechat.EmailTranscript
import com.conferbot.sdk.core.livechat.HandoverLifecycle
import com.conferbot.sdk.core.livechat.HandoverOutcome
import com.conferbot.sdk.core.livechat.HandoverState
import com.conferbot.sdk.core.livechat.HandoverWaiting
import com.conferbot.sdk.core.livechat.HandoverWaitingTest
import com.conferbot.sdk.core.livechat.LiveChat
import com.conferbot.sdk.core.livechat.LiveEvents
import com.conferbot.sdk.core.livechat.QueueStatus
import com.conferbot.sdk.core.livechat.ReplyTime
import com.conferbot.sdk.core.livechat.ReplyTimeInfo
import com.conferbot.sdk.core.requests.RequestState
import com.conferbot.sdk.core.requests.Requests
import com.conferbot.sdk.core.waiting.GamesCard
import com.conferbot.sdk.core.waiting.GamesLogic
import com.conferbot.sdk.core.waiting.GamesText
import com.conferbot.sdk.core.waiting.WaitArticles
import com.conferbot.sdk.core.waiting.WaitingGames
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * The core's words follow the widget language (core/i18n/I18n.kt), as RN's
 * __tests__/core/i18nCore.test.ts: German under setWidgetLanguage("de"),
 * the web's own strings, English again after; the owner's own words kept as
 * written and the stock English shown translated. A sweep then reads every
 * core text producer under "de" and fails on any English table value.
 */
class I18nCoreTest {

    private fun de(key: String, vararg vars: Pair<String, Any?>) = I18n.tFor("de", key, vars.toMap())

    @Before
    fun german() {
        I18n.setWidgetLanguage("de", emptyList())
    }

    @After
    fun english() {
        I18n.setWidgetLanguage("en", emptyList())
    }

    @Test
    fun `Helpful and its reasons`() {
        assertThat(Helpful.ASK).isEqualTo("Hilfreich?")
        assertThat(Helpful.REASONS.map { it.second }).containsExactly(
            de("helpful.reason.wrong"), de("helpful.reason.outdated"), de("helpful.reason.unanswered"), de("helpful.reason.other")
        ).inOrder()
        assertThat(Helpful.ratedLabel(AnswerFeedback(true))).isEqualTo(de("helpful.rated.up"))
        assertThat(Helpful.ratedLabel(AnswerFeedback(false, "outdated"))).isEqualTo("${de("helpful.rated.down")} · ${de("helpful.reason.outdated")}")
        assertThat(Helpful.TALK_TO_PERSON_ASK).isEqualTo(de("chat.talkToPersonAsk"))
        assertThat(AgentBotParser.FEEDBACK_REASONS.map { it.second }).isEqualTo(Helpful.REASONS.map { it.second })
    }

    @Test
    fun `handover lines, joins and leaves`() {
        assertThat(HandoverState.lineText(null)).isEqualTo(de("handover.pending"))
        assertThat(HandoverState.outcomeText(HandoverOutcome.JOINED)).isEqualTo(de("handover.joined"))
        assertThat(HandoverState.joinedText("Ada")).isEqualTo("Ada${de("system.joined")}")
        assertThat(HandoverState.joinedText("")).isEqualTo("${de("agent.anAgent")}${de("system.joined")}")
        assertThat(HandoverState.agentLeftText(null, false)).isEqualTo("${de("agent.theAgent")}${de("system.left")}")
        assertThat(HandoverState.handedOverNoteText(HandoverState.HANDED_OVER_NOTE)).isEqualTo(de("system.handedOverNote"))
        assertThat(HandoverState.handedOverNoteText("Own note")).isEqualTo("Own note")
        assertThat(HandoverLifecycle.chatEndedLine()).isEqualTo(de("system.chatEnded"))
    }

    @Test
    fun `live events and the closed chat, the record keeping English`() {
        assertThat(LiveEvents.requeuedText(null)).isEqualTo(de("takeover.requeued"))
        assertThat(LiveEvents.requeuedText("Sales")).isEqualTo(de("takeover.requeuedTeam", "team" to "Sales"))
        assertThat(LiveEvents.blockedText(emptyMap())).isEqualTo(de("system.closed"))
        assertThat(LiveEvents.NEW_CHAT_TEXT).isEqualTo("Neuen Chat starten")
        val ended = LiveEvents.closedStateOf(listOf("agent-joined-message" to null, "agent-chat-ended" to null))
        assertThat(ended?.text).isEqualTo(de("system.chatEnded"))
        assertThat(LiveEvents.REQUEUED_TEXT).isEqualTo("Connecting you to another agent...")
        assertThat(LiveEvents.CLOSED_TEXT).isEqualTo("This conversation has been closed.")
        assertThat(WaitArticles.RECORD_TEXT).isEqualTo("Resolved while waiting")
        assertThat(HandoverLifecycle.CHAT_ENDED_TEXT).isEqualTo("Chat has ended")
    }

    @Test
    fun `email transcript - the server's reason only in English`() {
        assertThat(EmailTranscript.TITLE).isEqualTo(de("chat.menu.emailChat"))
        assertThat(EmailTranscript.sentText("a@b.co")).isEqualTo(de("mailchat.sent", "email" to "a@b.co"))
        assertThat(EmailTranscript.errorText(EmailTranscript.Result(false, "Too many requests"))).isEqualTo(de("form.sendFailed"))
        I18n.setWidgetLanguage("en", emptyList())
        assertThat(EmailTranscript.errorText(EmailTranscript.Result(false, "Too many requests"))).isEqualTo("Too many requests")
    }

    @Test
    fun `while you wait, requests and relative times`() {
        assertThat(WaitArticles.TITLE).isEqualTo(de("wait.title"))
        assertThat(WaitArticles.LEFT_HELPED).isEqualTo(de("queue.leftHelped"))
        assertThat(Requests.TITLE).isEqualTo(de("requests.title"))
        assertThat(Requests.seeAll(4)).isEqualTo(de("requests.seeAll", "n" to 4))
        assertThat(Requests.STATE_WORDS.getValue(RequestState.SOLVED).label).isEqualTo(de("requests.solved.label"))
        val now = Calendar.getInstance().apply { set(2026, 9, 16, 15, 0, 0) }.timeInMillis
        assertThat(Requests.whenWords(now - 5 * 60_000L, now)).isEqualTo(de("when.minutesAgo", "n" to 5))
        assertThat(Requests.whenWords(now - 10_000L, now)).isEqualTo(de("when.justNow"))
        assertThat(Requests.whenWords(now - 3 * 3_600_000L, now)).isEqualTo(de("when.hoursAgo", "n" to 3))
        val yesterday = Calendar.getInstance().apply { set(2026, 9, 15, 12, 0, 0) }.timeInMillis
        assertThat(Requests.whenWords(yesterday, now)).isEqualTo(de("when.yesterday"))
        val monday = Calendar.getInstance().apply { set(2026, 9, 12, 12, 0, 0) }.timeInMillis
        assertThat(Requests.whenWords(monday, now)).isEqualTo("Montag")
        val old = Calendar.getInstance().apply { set(2026, 0, 3, 12, 0, 0) }.timeInMillis
        assertThat(Requests.whenWords(old, now)).isEqualTo("3 Jan.")
    }

    @Test
    fun `reply time on the header and the home card`() {
        assertThat(ReplyTime.headerStatus(null)).isEqualTo(de("status.online"))
        assertThat(ReplyTime.PHRASES["few_minutes"]).isEqualTo(de("reply.phrase.few_minutes"))
        val online = ReplyTimeInfo(mode = "auto", handover = true, state = "online", bucket = "few_minutes")
        assertThat(ReplyTime.homePeople(online).text).isEqualTo(de("reply.usually", "phrase" to de("reply.phrase.few_minutes")))
        assertThat(ReplyTime.headerStatus(online)).isEqualTo(de("reply.typicallyShort", "phrase" to de("reply.phrase.few_minutes")))
        val away = ReplyTimeInfo(mode = "auto", handover = true, state = "away", leaveMessage = true)
        assertThat(ReplyTime.headerStatus(away)).isEqualTo("${de("status.online")} · ${de("reply.teamByEmail")}")
    }

    @Test
    fun `the idle call, the games and their lists`() {
        assertThat(LauncherIdleCall.LINES["knock"]).isEqualTo(de("idle.knock"))
        assertThat(LauncherIdleCall.line("psst", "")).isEqualTo(de("idle.psst"))
        assertThat(WaitingGames.GAMES[0].name).isEqualTo(de("games.rps.name"))
        assertThat(WaitingGames.offerLine(WaitingGames.DEFAULT_OFFER_TEXT)).isEqualTo(de("games.offer"))
        assertThat(WaitingGames.offerLine("Spiel?")).isEqualTo("Spiel?")
        assertThat(GamesLogic.Hand.ROCK.label).isEqualTo(de("games.rps.rock"))
        assertThat(GamesLogic.quizVerdict(3, 3)).isEqualTo(de("games.verdict.perfect"))
        assertThat(GamesLogic.reactionVerdict(200)).isEqualTo(de("games.reaction.fast"))
        assertThat(GamesLogic.triviaFor("de")).isNotEqualTo(GamesLogic.TRIVIA)
        assertThat(GamesLogic.wordsFor("xx")).isEqualTo(GamesLogic.wordsFor("en"))
        assertThat(GamesText.pausedNamed("Ada")).isEqualTo(de("games.paused.named", "name" to "Ada"))
        // The emoji riddles are English wordplay: not offered in German.
        assertThat(GamesCard.create(mapOf("waitingGames" to mapOf("games" to listOf("emoji"))), HandoverWaitingTest.FakeClock())).isNull()
        val card = GamesCard.create(emptyMap(), HandoverWaitingTest.FakeClock())!!
        assertThat(card.state.title).isEqualTo(de("games.offer"))
        assertThat(card.state.chips.map { it.id }).doesNotContain("emoji")
    }

    @Test
    fun `queue, rating, home, greeting, launcher and typing`() {
        assertThat(LiveChat.queueLine(QueueStatus("waiting", 1, 0, 1, null))).isEqualTo(de("queue.next"))
        assertThat(LiveChat.queueLine(QueueStatus("waiting", 3, 2, 1, null))).isEqualTo(de("queue.position", "n" to 3, "ord" to "3rd"))
        assertThat(LiveChat.queueSubline(null)).isEqualTo(de("queue.soon"))
        assertThat(LiveChat.receiptLabel(com.conferbot.sdk.core.livechat.ReceiptState.SEEN)).isEqualTo(de("receipt.seen"))
        assertThat(LiveChat.handoverWaitingLine(HandoverWaiting(false, "busy", null, false, null)))
            .isEqualTo("${de("sdk.waiting.busy")} ${de("sdk.waiting.leaveEmail")}")
        assertThat(LiveChat.csatConfigOf(mapOf("enabled" to true))?.titleText).isEqualTo(de("csat.title"))
        assertThat(LiveChat.csatConfigOf(mapOf("enabled" to true, "feedbackPlaceholder" to "Tell us more..."))?.feedbackPlaceholder).isEqualTo(de("csat.placeholder"))
        assertThat(LiveChat.csatConfigOf(mapOf("enabled" to true, "titleText" to "Wie war es?"))?.titleText).isEqualTo("Wie war es?")
        assertThat(Home.HELP_CENTRE).isEqualTo(de("home.helpCentre"))
        assertThat(Home.welcomeBackName("Jamie")).isEqualTo(de("home.welcomeBackName", "name" to "Jamie"))
        val (before, after) = Home.askQueryParts()
        assertThat("${before}Q$after").isEqualTo(de("home.askQuery", "query" to "Q"))
        val g = Greeting.decide(mapOf("chatIconCta" to true, "chatIconCtaText" to "Hi 👋, how can I help?"), Greeting.Facts(returning = false, signedIn = false))
        assertThat(g?.text).isEqualTo(de("greeting.stock"))
        val own = Greeting.decide(mapOf("chatIconCta" to true, "chatIconCtaText" to "Moin!"), Greeting.Facts(returning = false, signedIn = false))
        assertThat(own?.text).isEqualTo("Moin!")
        assertThat(Launcher.resolve(emptyMap()).label).isEqualTo(de("home.chatWithUs"))
        assertThat(Launcher.resolve(mapOf("launcherLabel" to "Fragen?")).label).isEqualTo("Fragen?")
        assertThat(MascotMoods.typingLabel("mascot", "Pip")).isEqualTo(de("typing.named", "name" to "Pip"))
        assertThat(MascotMoods.typingLabel("dots", "")).isEqualTo(de("typing.typing"))
        assertThat(AgentBotParser.replyTimeoutText()).isEqualTo(de("chat.replyTimeout"))
        assertThat(AgentBotParser.defaultGreeting()).isEqualTo(de("agent.defaultGreeting"))
        assertThat(Customizations.resolve(mapOf("enableTagline" to true, "tagline" to "We're here to help")).tagline).isEqualTo(de("header.tagline"))
        assertThat(Customizations.resolve(mapOf("enableTagline" to true, "tagline" to "Immer da")).tagline).isEqualTo("Immer da")
    }

    // ----- the sweep -----

    // English table values a German screen must not show: a few words long,
    // different in German, and not part of any German string themselves.
    private val englishOnly: List<String> by lazy {
        val en = I18n.dictionary("en")
        val deDict = I18n.dictionary("de")
        val deValues = deDict.values.toList()
        en.entries.mapNotNull { (k, v) ->
            val s = v.trim()
            if (s.length < 6 || '{' in s || s == deDict[k]?.trim() || !s.any { it.isLetter() }) null
            else if (deValues.any { it.contains(s) }) null
            else s
        }.distinct()
    }

    private fun englishIn(texts: List<String>): List<String> =
        texts.flatMap { text -> englishOnly.filter { text.contains(it) }.map { "\"$it\" in \"$text\"" } }

    @Test
    fun `sweep - every core text producer under de has no English`() {
        val now = Calendar.getInstance().apply { set(2026, 9, 16, 15, 0, 0) }.timeInMillis
        val texts = mutableListOf<String>()
        texts += listOf(
            Helpful.ASK, Helpful.GROUP_LABEL, Helpful.YES, Helpful.NO, Helpful.RATED_UP, Helpful.RATED_DOWN, Helpful.THANKS,
            Helpful.SORRY, Helpful.WHAT_WENT_WRONG, Helpful.COMMENT, Helpful.SEND_FEEDBACK, Helpful.RETRY, Helpful.THANKS_COMMENT,
            Helpful.TALK_TO_PERSON, Helpful.TALK_TO_PERSON_ASK
        ) + Helpful.REASONS.map { it.second }
        texts += HandoverOutcome.values().map { HandoverState.outcomeText(it) }
        texts += listOf(HandoverState.PENDING_TEXT, HandoverState.joinedText(null), HandoverState.agentLeftText(null, false), HandoverState.handedOverNote)
        texts += listOf(LiveEvents.requeuedText(null), LiveEvents.requeuedText("Sales"), LiveEvents.blockedText(null), LiveEvents.NEW_CHAT_TEXT, LiveEvents.Text.chatEnded)
        texts += listOf(1, 2, 3, null).map { LiveChat.queueLine(QueueStatus("waiting", it, null, null, null)) }
        texts += listOf(LiveChat.queueSubline(null), LiveChat.TALK_TO_PERSON_ASK)
        texts += com.conferbot.sdk.core.livechat.ReceiptState.values().map { LiveChat.receiptLabel(it) }
        texts += listOf(
            LiveChat.handoverWaitingLine(HandoverWaiting(true, "offline", "2026-10-17T09:00:00Z", true, null), java.util.Date(now)),
            LiveChat.handoverWaitingLine(HandoverWaiting(true, "offline", null, true, "a@b.co"))
        )
        LiveChat.csatConfigOf(mapOf("enabled" to true))?.let { texts += listOf(it.titleText, it.subtitleText, it.thankYouText, it.feedbackPlaceholder) }
        texts += listOf(
            EmailTranscript.MENU, EmailTranscript.SUB, EmailTranscript.LABEL, EmailTranscript.SEND, EmailTranscript.SENDING,
            EmailTranscript.CANCEL, EmailTranscript.CHECK_EMAIL, EmailTranscript.SEND_FAILED, EmailTranscript.sentText("a@b.co")
        )
        texts += listOf(WaitArticles.LABEL, WaitArticles.TITLE, WaitArticles.SETTLED_TITLE, WaitArticles.READ, WaitArticles.SOLVED, WaitArticles.DONE, WaitArticles.LEFT_HELPED)
        texts += listOf(
            Requests.TITLE, Requests.ONE, Requests.LOADING, Requests.LOAD_FAILED, Requests.TRY_AGAIN, Requests.EMPTY, Requests.YOU,
            Requests.SUPPORT_TEAM, Requests.REPLY_PLACEHOLDER, Requests.REOPEN_PLACEHOLDER, Requests.YOUR_REPLY, Requests.SENDING,
            Requests.NOT_SENT, Requests.LOCKED, Requests.NEW_REPLY, Requests.newReplyFrom("Ada"), Requests.seeAll(3), Requests.menu(1)
        )
        texts += Requests.STATE_WORDS.values.flatMap { listOf(it.label, it.detail) }
        texts += listOf(10_000L, 5 * 60_000L, 3_600_000L, 3 * 3_600_000L, 30 * 3_600_000L, 3 * 86_400_000L, 40 * 86_400_000L).map { Requests.whenWords(now - it, now) }
        val rts = listOf(
            ReplyTimeInfo(mode = "auto", handover = true, state = "online", bucket = "few_minutes"),
            ReplyTimeInfo(mode = "auto", handover = true, state = "away", backAt = "2026-10-17T09:00:00Z"),
            ReplyTimeInfo(mode = "auto", handover = true, state = "away", backAt = "2026-10-20T09:30:00Z"),
            ReplyTimeInfo(mode = "auto", handover = true, state = "away", backAt = "2026-11-20T09:30:00Z"),
            ReplyTimeInfo(mode = "auto", handover = true, state = "away", leaveMessage = false),
            ReplyTimeInfo(mode = "auto", handover = true, state = "offline", leaveMessage = true),
            ReplyTimeInfo(mode = "auto", handover = true, state = "offline", leaveMessage = false),
            null
        )
        for (rt in rts) {
            for (agent in listOf(false, true)) {
                ReplyTime.headerStatus(rt, agentBot = agent, now = java.util.Date(now))?.let { texts += it }
                texts += ReplyTime.homePeople(rt, agent = agent, now = java.util.Date(now)).text
                ReplyTime.homeLine(rt, agent = agent, now = java.util.Date(now))?.let { texts += it.text }
            }
        }
        texts += ReplyTime.PHRASES.values
        texts += LauncherIdleCall.LINES.values
        texts += listOf(Launcher.resolve(emptyMap()).label, MascotMoods.typingLabel("mascot", "Pip"), MascotMoods.typingLabel("dots", ""))
        texts += listOf(AgentBotParser.replyTimeoutText(), AgentBotParser.defaultGreeting())
        texts += WaitingGames.GAMES.flatMap { listOf(it.name, it.blurb) }
        texts += listOf(WaitingGames.offerLine(WaitingGames.DEFAULT_OFFER_TEXT))
        texts += GamesLogic.HANDS.map { it.label } + GamesLogic.Verdict.all + listOf(100L, 300L, 400L, 900L).map { GamesLogic.reactionVerdict(it) }
        texts += GamesLogic.triviaFor("de").flatMap { listOf(it.q, it.a) + it.wrong }
        texts += listOf(GamesText.CARD, GamesText.CLOSE, GamesText.PICK, GamesText.AGAIN, GamesText.ANOTHER, GamesText.NOT_NOW, GamesText.pausedNamed("Ada"), GamesText.PAUSED_SOMEONE, GamesText.PAUSED_SAVED, GamesText.RESUME)
        // Each game opened: its title, score line, status and board words.
        for (id in WaitingGames.GAMES.map { it.id }.filter { it != "emoji" }) {
            val clock = HandoverWaitingTest.FakeClock()
            val card = GamesCard.create(emptyMap(), clock, rng = { 0.3 })!!
            texts += listOf(card.state.title)
            texts += card.state.chips.map { it.name }
            card.open(id)
            clock.tick(5_000)
            texts += listOf(card.state.title, card.state.meta, card.state.status, card.state.board.toString())
            texts += card.state.foot.map { it.label }
            card.wrapUp("Ada")
            texts += listOf(card.state.title, card.state.status) + card.state.foot.map { it.label }
        }
        texts += listOf(
            Home.TITLE, Home.DESCRIPTION, Home.ONLINE_NOW, Home.SUPPORT, Home.CHAT_WITH_US, Home.OUR_ASSISTANT, Home.RESUME,
            Home.CONTINUE_CONVERSATION, Home.ASK_AGAIN, Home.HELP_CENTRE, Home.RECENT_ARTICLES, Home.SEE_ALL, Home.SEARCH_PLACEHOLDER,
            Home.SEARCH_LABEL, Home.noMatch("x"), Home.ASK_INSTEAD, Home.CHAT_INSTEAD, Home.ASK_PLACEHOLDER, Home.askLabel("Pip"),
            Home.answersRightAway("Pip"), Home.WELL_HELP, Home.ARTICLES, Home.GOOD_MORNING, Home.GOOD_AFTERNOON, Home.GOOD_EVENING,
            Home.HELLO, Home.welcomeBackName("Jo"), Home.WELCOME_BACK, Home.REQUESTS_TITLE, Home.requestsSeeAll(3),
            Home.rated("4.5", 12, Home.REVIEWS), Home.OUR_TEAM, Home.CUSTOMER_LOGO, Home.CLOSE, Home.BACK
        )
        texts += listOf(5, 13, 19, 23).map { Home.salutation(it) }
        Home.ratingLine(mapOf("average" to 4.6, "count" to 1))?.let { texts += listOf(it.count, it.label) }
        Greeting.decide(mapOf("chatIconCta" to true, "chatIconCtaText" to "Hi 👋, how can I help?"), Greeting.Facts(returning = false, signedIn = false))?.let { texts += it.text }
        texts += listOf(HandoverLifecycle.chatEndedLine())
        assertThat(texts.size).isGreaterThan(300)
        assertThat(englishIn(texts.filter { it.isNotBlank() })).isEmpty()
        // The sweep bites: the same words in English are caught.
        assertThat(englishIn(listOf(I18n.tFor("en", "wait.title")))).isNotEmpty()
        assertThat(englishIn(listOf(I18n.tFor("en", "games.rps.intro")))).isNotEmpty()
    }

    @Test
    fun `under en the core keeps its English`() {
        I18n.setWidgetLanguage("en", emptyList())
        assertThat(Helpful.ASK).isEqualTo("Helpful?")
        assertThat(LiveChat.queueLine(QueueStatus("waiting", 3, 2, 1, null))).isEqualTo("You're 3rd in line")
        assertThat(HandoverLifecycle.chatEndedLine()).isEqualTo("Chat has ended")
        assertThat(Requests.whenWords(System.currentTimeMillis() - 5 * 60_000L)).isEqualTo("5 min ago")
        assertThat(GamesText.NOT_NOW).isEqualTo("Not now")
        assertThat(Launcher.resolve(emptyMap()).label).isEqualTo("Chat with us")
    }
}
