package com.conferbot.sdk.core.flow

import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.core.nodes.handlers.carouselCards
import com.conferbot.sdk.ui.compose.steps.inkBounds
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/**
 * The Compose step cards (ui/compose/steps) answer each step with
 * FlowStepAnswer and keep their own state in RankingOrder / MatrixPicks.
 * Here every visitor action of the shared flow fixtures for those steps is
 * played as the card sends it - the same builders, the same card state, and
 * only what the card offers (the shown results, the free slots, the path
 * buttons, the Checkout) - through Conferbot.answerStep's path
 * (submitStepResponse). The whole outcome must still be the web's, and each
 * waiting step must reach its card stamped with its type.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlowStepViewAnswerTest {

    private var savedZone: TimeZone? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        savedZone?.let { TimeZone.setDefault(it) }
        Dispatchers.resetMain()
    }

    private class NotOffered(msg: String) : IllegalStateException(msg)

    /** A fixture action as the step's card sends it; (false, _) when no card does (the composer, a wait). */
    private val card = FlowDriver.ViewAnswer { a, ui, node, memo ->
        val type = node?.type ?: ""
        val data = node?.data ?: emptyMap()
        val nodeId = node?.id ?: ""
        val step = FlowStepCards.cardStep(ui)
        fun need(ok: Boolean, what: String) { if (!ok) throw NotOffered("$nodeId: the card does not offer $what") }
        if (type in FlowStepCards.CARD_TYPES) need(step?.type == type, "itself (no step stamped on ${ui?.javaClass?.simpleName})")
        when (Js.str(a["do"])) {
            "consent" -> true to FlowStepAnswer.consent(a["agreed"] == true)
            "otpEmail" -> true to FlowStepAnswer.otpSend(Js.str(a["email"]))
            "otpCode" -> {
                need(step?.extra?.get("stage") == "code", "the code boxes")
                true to FlowStepAnswer.otpVerify(FlowStepAnswer.digitsFromPaste(a["code"]))
            }
            "otpSkip" -> {
                need(step?.extra?.get("allowSkip") == true, "Skip")
                true to FlowStepAnswer.otpSkip
            }
            "addressSearch" -> true to FlowStepAnswer.addressSearch(Js.str(a["q"]))
            "addressPick" -> {
                val i = Js.number(a["index"]).toInt()
                need(Js.list(step?.extra?.get("results")).getOrNull(i) != null, "result $i")
                true to FlowStepAnswer.addressPick(i)
            }
            "addressManual" -> {
                need(step?.extra?.get("allowManual") != false, "typing by hand")
                fun f(k: String) = a[k]?.let { Js.str(it) } ?: ""
                true to FlowStepAnswer.addressManual(f("line1"), f("city"), f("region"), f("postal"), f("country"))
            }
            "signTyped" -> {
                need(FlowRich.signatureConfig(data).allowTyped, "Type instead")
                true to FlowStepAnswer.signTyped(Js.str(a["name"]), a["fullName"]?.let { Js.str(it) })
            }
            "matrix" -> {
                var picks = (memo[nodeId] as? MatrixPicks) ?: MatrixPicks.of(data)
                Js.map(a["cells"])?.forEach { (row, cols) ->
                    (if (cols is List<*>) cols else listOf(cols)).forEach { c -> if (!picks.isOn(row, Js.str(c))) picks = picks.tap(row, Js.str(c)) }
                }
                memo[nodeId] = picks
                true to (if (a["submit"] == false) null else FlowStepAnswer.matrix(picks))
            }
            "rankingMove" -> {
                val order = (memo[nodeId] as? RankingOrder) ?: RankingOrder(FlowRich.rankingItems(data).map { it.id })
                memo[nodeId] = order.move(Js.str(a["id"]), Js.number(a["dir"]).toInt())
                true to null
            }
            "rankingSubmit" -> true to FlowStepAnswer.ranking(((memo[nodeId] as? RankingOrder) ?: RankingOrder(FlowRich.rankingItems(data).map { it.id })).ids)
            "slider" -> {
                // The slider only lands on its steps.
                val cfg = FlowRich.sliderConfig(data)
                true to FlowStepAnswer.value(FlowRich.snapToStep(Js.number(a["value"]), cfg.min, cfg.max, cfg.step))
            }
            "datetime" -> true to FlowStepAnswer.dateTime(Js.str(a["value"]))
            "calendar" -> {
                val cal = ui as? NodeUIState.Calendar
                val date = Js.str(a["date"])
                val slot = a["slot"]?.let { Js.str(it) }
                if (slot != null) need(cal?.availableSlots.orEmpty().any { it.date == date && it.time == slot }, "$date $slot")
                else need(date in Js.list(step?.extra?.get("days")).map { Js.str(it) }, date)
                true to FlowStepAnswer.calendar(date, slot)
            }
            "choose" -> when (type) {
                "carousel-node", "product-card-node" -> {
                    val cards = carouselCards(if (type == "product-card-node") FlowRich.productCarouselData(data) else data)
                    val b = a["buttonId"]?.let { Js.str(it) }
                    if (b == null) {
                        need(cards.none { c -> c.buttons.any { it.action == "path" } }, "Continue")
                        true to FlowStepAnswer.cardsContinue
                    } else {
                        val c = cards.firstOrNull { c -> c.buttons.any { it.id == b && it.action == "path" } }
                        need(c != null, "button $b")
                        true to FlowStepAnswer.cardButton(b, if (type == "product-card-node") null else c!!.id)
                    }
                }
                else -> false to null
            }
            "navigateLink" -> {
                val i = Js.number(a["index"]).toInt()
                need((ui as? NodeUIState.SingleChoice)?.choices?.any { it.id == "link-$i" && it.targetPort?.startsWith("http") == true } == true, "link $i")
                true to FlowStepAnswer.navigateLink(i)
            }
            "navigateDone" -> true to FlowStepAnswer.navigateDone
            "payOpen" -> {
                need(Js.str(step?.extra?.get("checkoutUrl")).startsWith("https://"), "Pay now (no Checkout)")
                true to FlowStepAnswer.payment("open")
            }
            "payCancel" -> true to FlowStepAnswer.payment("cancel")
            else -> false to null
        }
    }

    /** Actions no card sends: typed in the composer, an empty pad (Sign is off), the clock, the server. */
    private val notFromACard = setOf("text", "signEmpty", "wait", "serverEvent")

    private fun check(vararg files: String) {
        for (name in files) {
            val fx = FlowFixturesTest.load("$name.json")
            val unhandled = mutableListOf<String>()
            val outcome = FlowDriver.run(fx, card, unhandled)
            val diffs = outcome.problems.map { "problem: $it" } + FlowFixturesTest.compare(name, Js.map(fx["expect"]) ?: emptyMap(), outcome.data)
            if (diffs.isNotEmpty()) throw AssertionError("$name\n  ${diffs.joinToString("\n  ")}")
            assertWithMessage("$name: actions no card answered").that(unhandled.filter { it !in notFromACard }).isEmpty()
        }
    }

    @Test fun consentCard() = check("consent.required", "consent.optional-declined")
    @Test fun otpCard() = check("otp.verified", "otp.wrong-code-then-locked", "otp.skip-without-failed-edge-takes-source", "otp.send-fails-network")
    @Test fun addressCard() = check("address.manual", "address.search-and-pick")
    @Test fun signatureCard() = check("signature.typed")
    @Test fun matrixCard() = check("matrix.single-require-all", "matrix.multiple")
    @Test fun rankingCard() = check("ranking.reorder")
    @Test fun sliderCard() = check("slider.value")
    @Test fun dateTimeCard() = check("datetime.date-rules", "datetime.time-mode")
    @Test fun calendarCard() = check("calendar.date-only", "calendar.with-slots")
    @Test fun cardsCard() = check("carousel.path-button", "carousel.links-only-continue", "product-card.buy-button")
    @Test fun navigateCard() = check("navigate.links-then-done")
    @Test fun paymentCard() = check("payment.paid", "payment.cancelled-without-failed-edge-ends", "payment.timeout-falls-to-failed")

    /** The QA - All Nodes bot end to end, every card answered as it sends. */
    @Test fun qaAllNodesTour() = check("real.qa-all-nodes-tour")

    /** A drawn signature: the card's PNG is accepted, uploaded and leaves by "source". */
    @Test
    fun drawnSignatureIsAccepted() {
        val fx = FlowFixturesTest.load("signature.typed.json").toMutableMap()
        fx["inputs"] = listOf(mapOf("at" to "sg2", "do" to "signDrawn"))
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val outcome = FlowDriver.run(fx, { a, _, _, _ -> if (a["do"] == "signDrawn") true to FlowStepAnswer.signDrawn(png, 280, 120) else false to null })
        assertThat(outcome.problems).isEmpty()
        assertThat(FlowFixturesTest.canon(outcome.data["ports"])).isEqualTo(FlowFixturesTest.canon(listOf(mapOf("node" to "sg2", "handle" to "source", "wired" to true))))
        val answer = Js.list(outcome.data["answers"]).mapNotNull { Js.map(it) }.first { it["shape"] == "user-signature" }
        assertThat(answer["method"]).isEqualTo("drawn")
        assertThat(Js.number(answer["width"])).isEqualTo(280.0)
    }

    // ----- the cards' own state and helpers -----

    @Test
    fun rankingMovesStopAtEitherEnd() {
        var o = RankingOrder(listOf("a", "b", "c"))
        o = o.move("a", -1).move("c", 1)
        assertThat(o.ids).containsExactly("a", "b", "c").inOrder()
        assertThat(o.move("c", -1).ids).containsExactly("a", "c", "b").inOrder()
    }

    @Test
    fun matrixSinglePicksOnePerRowMultipleToggles() {
        assertThat(MatrixPicks("single").tap("r0", "c0").tap("r0", "c1").selection).isEqualTo(mapOf("r0" to listOf("c1")))
        val m = MatrixPicks("multiple").tap("r0", "c0").tap("r0", "c1").tap("r0", "c0")
        assertThat(m.selection).isEqualTo(mapOf("r0" to listOf("c1")))
        assertThat(FlowStepAnswer.matrix(m)).isEqualTo(mapOf("cells" to mapOf("r0" to listOf("c1"))))
        assertThat(FlowStepAnswer.matrix(MatrixPicks("single").tap("r1", "c2"))).isEqualTo(mapOf("cells" to mapOf("r1" to "c2")))
    }

    @Test
    fun otpHelpers() {
        assertThat(FlowStepAnswer.digitsFromPaste("123 45-6789")).isEqualTo("123456")
        assertThat(FlowStepAnswer.digitsFromPaste("١٢٣")).isEqualTo("123")
        assertThat(FlowStepAnswer.maskEmail("Ana@Acme.io")).isEqualTo("a***@acme.io")
        assertThat(FlowStepAnswer.maskEmail("nope")).isEqualTo("***")
        assertThat(FlowStepAnswer.resendLabel(27)).isEqualTo("Resend in 0:27")
        assertThat(FlowStepAnswer.resendLabel(0)).isEqualTo("Resend code")
    }

    @Test
    fun calendarMonths() {
        // October 2026 starts on a Thursday: four blanks, then 31 days.
        val oct = FlowStepAnswer.monthGrid(2026, 10)
        assertThat(oct.take(4)).containsExactly(null, null, null, null)
        assertThat(oct[4]).isEqualTo("2026-10-01")
        assertThat(oct.last()).isEqualTo("2026-10-31")
        assertThat(FlowStepAnswer.monthGrid(2028, 2).filterNotNull()).hasSize(29)
        assertThat(FlowStepAnswer.addMonths("2026-12", 1)).isEqualTo("2027-01")
        assertThat(FlowStepAnswer.addMonths("2026-01", -1)).isEqualTo("2025-12")
        assertThat(FlowStepAnswer.monthTitle("2026-10")).isEqualTo("October 2026")
    }

    @Test
    fun dateRulesTheCardFollows() {
        val data = mapOf("minDate" to "today", "maxDate" to "+30", "disabledWeekdays" to listOf(0, 6))
        assertThat(FlowStepAnswer.dayInBounds(data, "2026-10-14", "2026-10-15")).isFalse()
        assertThat(FlowStepAnswer.dayInBounds(data, "2026-10-17", "2026-10-15")).isTrue()
        assertThat(FlowStepAnswer.dayAllowed(data, "2026-10-17", "2026-10-15")).isFalse() // a Saturday
        assertThat(FlowStepAnswer.dayAllowed(data, "2026-10-19", "2026-10-15")).isTrue()
        val times = FlowStepAnswer.timeChoices(mapOf("minTime" to "09:00", "maxTime" to "17:00"))
        assertThat(times.first()).isEqualTo("09:00")
        assertThat(times.last()).isEqualTo("17:00")
        assertThat(times).contains("14:30")
        assertThat(FlowStepAnswer.dateTimeValue(1_792_419_000_000L, "datetime", TimeZone.getTimeZone("Asia/Dubai"))).isEqualTo("2026-10-19T18:10")
    }

    @Test
    fun base64MatchesTheStandard() {
        assertThat(FlowStepAnswer.base64("".toByteArray())).isEqualTo("")
        assertThat(FlowStepAnswer.base64("f".toByteArray())).isEqualTo("Zg==")
        assertThat(FlowStepAnswer.base64("fo".toByteArray())).isEqualTo("Zm8=")
        assertThat(FlowStepAnswer.base64("foobar".toByteArray())).isEqualTo("Zm9vYmFy")
        assertThat(FlowStepAnswer.base64(byteArrayOf(-1, -2, -3))).isEqualTo("//79")
    }

    @Test
    fun signatureIsTrimmedToTheInk() {
        assertThat(inkBounds(emptyList(), 4f, 300, 140)).isNull()
        val b = inkBounds(listOf(listOf(Offset(50f, 40f), Offset(120f, 90f)), listOf(Offset(200f, 60f))), 5f, 300, 140)!!
        assertThat(listOf(b.left, b.top, b.width, b.height)).containsExactly(45, 35, 160, 60).inOrder()
        // Kept inside the pad.
        val edge = inkBounds(listOf(listOf(Offset(1f, 1f), Offset(299f, 139f))), 10f, 300, 140)!!
        assertThat(listOf(edge.left, edge.top, edge.width, edge.height)).containsExactly(0, 0, 300, 140).inOrder()
    }

    @Test
    fun theComposerHidesUnderCardsThatTakeTheTyping() {
        val otp = NodeUIState.TextInput("q", NodeUIState.TextInput.InputType.EMAIL, nodeId = "o", answerKey = "", step = NodeUIState.StepInfo(type = "otp-node"))
        val ask = NodeUIState.TextInput("q", NodeUIState.TextInput.InputType.EMAIL, nodeId = "a", answerKey = "", step = NodeUIState.StepInfo(type = "ask-email-node"))
        assertThat(FlowStepCards.hidesComposer(otp)).isTrue()
        assertThat(FlowStepCards.hidesComposer(ask)).isFalse()
        assertThat(FlowStepCards.cardStep(ask)).isNull()
    }
}
