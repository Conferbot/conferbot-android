package com.conferbot.sdk.core.flow

/**
 * The rules behind the logic steps, ported unchanged from the web widget
 * (conferbot-widget/src/ts/chat/handlers/conditionEval.ts for condition-node,
 * handlers/logicValues.ts for math, boolean, variable, random split, jump-to
 * and business hours) by way of RN src/core/flow/logic.ts. Pure: the
 * variable lookup, clock and random number are passed in.
 */
object FlowLogic {
    private fun stringify(value: Any?): String = when (value) {
        null -> ""
        is Map<*, *>, is List<*> -> Js.stringify(value)
        else -> Js.str(value)
    }

    // "${name}" / "{name}" typed into the variable field still means `name`
    private fun variableKey(raw: Any?): String {
        val key = stringify(raw).trim()
        val wrapped = Regex("^\\$?\\{(\\w+)}$").find(key)
        return wrapped?.groupValues?.get(1) ?: key
    }

    /** Fill `${name}` placeholders in a typed value; unknown names stay as written. */
    fun interpolate(text: Any?, lookup: VariableLookup): String =
        Regex("\\$\\{(\\w+)}").replace(stringify(text)) { m ->
            val value = lookup(m.groupValues[1])
            if (value == null) m.value else stringify(value)
        }

    // A number or a quoted word, as typed: never an answer variable's name.
    private fun literalOf(raw: Any?): String? {
        val text = stringify(raw).trim()
        if (Regex("^[-+]?\\d+(?:\\.\\d+)?$").matches(text)) return text
        val quoted = Regex("^\"(.*)\"$|^'(.*)'$").find(text) ?: return null
        return quoted.groups[1]?.value ?: quoted.groups[2]?.value
    }

    /**
     * One side of the rule: the typed literal, or the variable's value. A plain
     * number (or a "quoted" word) left in the variable field is what the owner
     * meant to compare with, so when no answer has that name it is taken as typed.
     */
    fun resolveOperand(raw: Any?, isCustom: Any?, lookup: VariableLookup): Any? {
        if (Js.truthy(isCustom)) return interpolate(raw, lookup)
        val value = lookup(variableKey(raw))
        if (value != null) return value
        return literalOf(raw) ?: value
    }

    private val OPERATOR_ALIASES = mapOf(
        "==" to "=",
        "equals" to "=",
        "not_equals" to "!=",
        "greater_than" to ">",
        "less_than" to "<",
        "greater_than_or_equal" to ">=",
        "less_than_or_equal" to "<=",
        "not_contains" to "does not contain",
        "starts_with" to "starts with",
        "ends_with" to "ends with",
        "does_not_match" to "does not match"
    )

    private fun asNumber(value: Any?): Double = if (value is Number) value.toDouble() else Js.number(if (value is String) value.trim() else value)

    private fun isNumeric(value: Any?): Boolean {
        if (value is Number) return Js.isFinite(value.toDouble())
        val text = stringify(value).trim()
        return text != "" && Js.isFinite(Js.number(text))
    }

    private fun safeRegExp(pattern: String, ci: Boolean): Regex? = try {
        if (ci) Regex(pattern, RegexOption.IGNORE_CASE) else Regex(pattern)
    } catch (e: Exception) {
        null
    }

    /**
     * Ratings used to be stored as their emoji; they are now stored as the
     * number. A rule an owner wrote against the old emoji keeps working.
     */
    private val RATING_FACES = listOf("😡", "😐", "😊", "😀", "😁")

    fun ratingAlias(value: Any?): Any? {
        if (value !is String) return value
        val v = value.trim()
        if (Regex("^(⭐)+$").matches(v)) return v.codePointCount(0, v.length).toString()
        val face = RATING_FACES.indexOf(v)
        return if (face >= 0) (face + 1).toString() else value
    }

    /** true → the "yes" branch (source-0), false → "no" (source-1). */
    fun evaluateCondition(data: Map<String, Any?>?, lookup: VariableLookup): Boolean {
        val isNumber = Js.truthy(data?.get("isNumber"))
        val op = data?.get("operator")
        val rawOperator = stringify(if (Js.truthy(op)) op else "=").trim()
        val operator = OPERATOR_ALIASES[rawOperator] ?: rawOperator

        val left = ratingAlias(resolveOperand(data?.get("leftValue"), data?.get("customLeftValue"), lookup))
        val right = ratingAlias(resolveOperand(data?.get("rightValue"), data?.get("customRightValue"), lookup))

        if (operator == "is_set") return stringify(left).trim() != ""
        if (operator == "is_not_set") return stringify(left).trim() == ""

        val ci = Js.truthy(data?.get("caseInsensitive"))
        fun text(value: Any?) = if (ci) stringify(value).lowercase() else stringify(value)

        return when (operator) {
            "=", "!=" -> {
                val equal = if (isNumber) asNumber(left) == asNumber(right)
                else if (isNumeric(left) && isNumeric(right)) asNumber(left) == asNumber(right)
                else text(left) == text(right)
                if (operator == "=") equal else !equal
            }
            "<", ">", "<=", ">=" -> {
                if (!isNumber && !(isNumeric(left) && isNumeric(right))) return false
                val l = asNumber(left)
                val r = asNumber(right)
                if (l.isNaN() || r.isNaN()) return false
                when (operator) {
                    "<" -> l < r
                    ">" -> l > r
                    "<=" -> l <= r
                    else -> l >= r
                }
            }
            "contains" -> !isNumber && text(left).contains(text(right))
            "does not contain" -> !isNumber && !text(left).contains(text(right))
            "starts with" -> !isNumber && text(left).startsWith(text(right))
            "ends with" -> !isNumber && text(left).endsWith(text(right))
            "matches" -> {
                val re = safeRegExp(stringify(right), ci)
                !isNumber && re != null && re.containsMatchIn(stringify(left))
            }
            "does not match" -> {
                val re = safeRegExp(stringify(right), ci)
                !isNumber && re != null && !re.containsMatchIn(stringify(left))
            }
            else -> false
        }
    }

    // ----- handlers/logicValues.ts -----

    /** A name that reads like a variable: "price", "order.total", "_tmp". */
    private val IDENTIFIER = Regex("^[A-Za-z_][\\w.]*$")

    /** Words that are typed values, never variable names. */
    private val BOOLEAN_WORDS = setOf("true", "false", "yes", "no", "on", "off")

    /** A ${name} or {name} placeholder (the "$" is optional). */
    private val PLACEHOLDER = Regex("\\$?\\{([A-Za-z_][\\w.]*)}")

    private fun wholePlaceholder(text: String): String? = Regex("^\\$?\\{([A-Za-z_][\\w.]*)}$").find(text)?.groupValues?.get(1)

    /** A saved value as text: lists read "a, b", other objects stay JSON. */
    private fun asText(value: Any?): String {
        if (value is List<*> && value.all { it == null || (it !is Map<*, *> && it !is List<*>) }) return value.joinToString(", ") { Js.str(it) }
        return if (value is Map<*, *> || value is List<*>) Js.stringify(value) else Js.str(value)
    }

    /** Fill ${name} and {name} from the variables; unknown ones stay as written. */
    fun fillPlaceholders(text: Any?, lookup: VariableLookup): String =
        PLACEHOLDER.replace(Js.str(text)) { m ->
            val value = lookup(m.groupValues[1])
            if (value != null) asText(value) else m.value
        }

    /** Drop one pair of matching quotes around a typed value. */
    private fun unquote(text: String): String {
        val m = Regex("^([\"'])([\\s\\S]*)\\1$").find(text) ?: return text
        return m.groupValues[2]
    }

    /**
     * One side of a rule. A variable's value when `raw` names one that has a
     * value; null when it names a variable with no answer yet; otherwise the
     * typed text, with any placeholders inside it filled in.
     */
    fun valueOf(raw: Any?, lookup: VariableLookup): Any? {
        if (raw == null) return null
        if (raw !is String) return raw
        val text = raw.trim()
        val wrapped = wholePlaceholder(text)
        val name = wrapped ?: text
        if (name != "") {
            val value = lookup(name)
            if (value != null) return value
        }
        if (wrapped != null) return null
        if (IDENTIFIER.matches(text) && text.lowercase() !in BOOLEAN_WORDS) return null
        return fillPlaceholders(unquote(text), lookup)
    }

    /** A value as a number: NaN when it isn't one (never 0 for empty). */
    fun toNumber(v: Any?): Double {
        if (v is Number) return v.toDouble()
        if (v is Boolean) return if (v) 1.0 else 0.0
        if (v !is String) return Double.NaN
        var text = v.trim().replace(Regex("\\s+"), "")
        if (text == "") return Double.NaN
        if (Regex("^[-+]?\\d{1,3}(,\\d{3})+(\\.\\d+)?$").matches(text)) text = text.replace(",", "")
        if (!Regex("^[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?$").matches(text)) return Double.NaN
        return text.toDouble()
    }

    private val FALSE_WORDS = setOf("false", "no", "n", "0", "off", "none", "null", "undefined", "")

    /** A value as true / false: "No", "off", "0" and empty are false. */
    fun toBool(v: Any?): Boolean = when (v) {
        null, "" -> false
        is Boolean -> v
        is Number -> v.toDouble() != 0.0
        is String -> v.trim().lowercase() !in FALSE_WORDS
        is List<*> -> v.isNotEmpty()
        else -> true
    }

    // ----- Math step -----
    const val DEFAULT_MATH_VARIABLE = "math_operations"

    fun mathVariableName(data: Map<String, Any?>?): String = Js.str(data?.get("answerVariable")).trim().ifEmpty { DEFAULT_MATH_VARIABLE }

    /** The math step's result, or "" when it can't be worked out. */
    fun mathResult(data: Map<String, Any?>?, lookup: VariableLookup): Any {
        val l = toNumber(valueOf(data?.get("leftValue"), lookup))
        val r = toNumber(valueOf(data?.get("rightValue"), lookup))
        if (l.isNaN() || r.isNaN()) return ""
        val op = Js.str(data?.get("operator")).trim().ifEmpty { "+" }
        val x = when (op) {
            "+" -> l + r
            "-" -> l - r
            "*" -> l * r
            "/" -> {
                if (r == 0.0) return ""
                l / r
            }
            else -> return ""
        }
        if (!Js.isFinite(x)) return ""
        return Js.tidy(java.math.BigDecimal(x).setScale(10, java.math.RoundingMode.HALF_UP).toDouble())
    }

    // ----- Boolean step -----

    /** The boolean step's answer: true takes source-0, false source-1. */
    fun booleanResult(data: Map<String, Any?>?, lookup: VariableLookup): Boolean {
        val l = toBool(valueOf(data?.get("leftValue"), lookup))
        val r = toBool(valueOf(data?.get("rightValue"), lookup))
        return when (Js.str(data?.get("operator")).trim().uppercase().ifEmpty { "AND" }) {
            "AND" -> l && r
            "OR" -> l || r
            "NOT" -> l && !r
            "XOR" -> l != r
            "NAND" -> !(l && r)
            "NOR" -> !(l || r)
            "XNOR" -> l == r
            else -> false
        }
    }

    // ----- Variable step -----

    /** The value a variable step stores: placeholders filled, a number when asked. */
    fun variableValue(data: Map<String, Any?>?, lookup: VariableLookup): Any {
        val text = fillPlaceholders(data?.get("value") ?: "", lookup)
        if (Js.truthy(data?.get("isNumber"))) {
            val n = toNumber(text)
            if (!n.isNaN()) return Js.tidy(n)
        }
        return text
    }

    // ----- Random split -----

    /** A handle no edge uses: routing to it ends the flow. */
    const val NO_PATH = "__no_path__"

    private fun weightOf(b: Any?): Double {
        val n = toNumber(Js.map(b)?.get("weight"))
        return if (Js.isFinite(n) && n > 0) n else 0.0
    }

    private fun <T> pickWeighted(items: List<T>, weights: List<Double>, rand: Double): T {
        val total = weights.sum()
        if (total <= 0) return items[Math.min(items.size - 1, Math.floor(rand * items.size).toInt())]
        val target = rand * total
        var acc = 0.0
        for (i in items.indices) {
            acc += weights[i]
            if (weights[i] > 0 && target < acc) return items[i]
        }
        for (i in items.indices.reversed()) if (weights[i] > 0) return items[i]
        return items.last()
    }

    /**
     * The handle a random split leaves by, or null when no branch has a line.
     * Branches are matched by id (source-<id>); position handles only for
     * flows saved before branches had ids.
     */
    fun pickRandomHandle(branches: Any?, edgeHandles: List<String?>, rand: Double): String? {
        val list = Js.list(branches)
        val wired = edgeHandles.filterNotNull().toSet()
        fun idOf(b: Any?): String? {
            val id = Js.map(b)?.get("id")
            return if (id != null && id != "") Js.str(id) else null
        }
        val byId = list.filter { idOf(it) != null && "source-${idOf(it)}" in wired }
        if (byId.isNotEmpty()) return "source-${idOf(pickWeighted(byId, byId.map { weightOf(it) }, rand))}"
        val byPosition = list.withIndex().filter { "source-${it.index}" in wired }
        if (byPosition.isNotEmpty()) return "source-${pickWeighted(byPosition, byPosition.map { weightOf(it.value) }, rand).index}"
        return null
    }

    // ----- Jump to -----

    /** The step a jump-to goes to ("" when none is set). */
    fun jumpTargetOf(data: Map<String, Any?>?): String {
        val target = listOf(data?.get("nodeId"), data?.get("targetNodeId"), data?.get("jumpTo")).firstOrNull { it != null && Js.str(it).trim() != "" }
        return if (target != null) Js.str(target).trim() else ""
    }

    // ----- Business hours -----
    private val DAY_NAMES = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    private fun hhmm(t: Any?): String? {
        val m = Regex("^(\\d{1,2}):(\\d{2})").find(Js.str(t).trim()) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        if (h > 24 || min > 59) return null
        return "${(h % 24).toString().padStart(2, '0')}:${m.groupValues[2]}"
    }

    /** "25-12-2026", "2026-12-25" (or with / or .) -> "2026-12-25"; else null. */
    fun normaliseDate(d: Any?): String? {
        val text = Js.str(d).trim()
        Regex("^(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})$").find(text)?.let {
            return "${it.groupValues[1]}-${it.groupValues[2].padStart(2, '0')}-${it.groupValues[3].padStart(2, '0')}"
        }
        Regex("^(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4})$").find(text)?.let {
            return "${it.groupValues[3]}-${it.groupValues[2].padStart(2, '0')}-${it.groupValues[1].padStart(2, '0')}"
        }
        return null
    }

    data class ZonedNow(val zone: String, val date: String, val time: String, val dayIndex: Int, val dayName: String)

    /** The time, date and weekday in a zone (an empty or invalid zone uses the fallback). */
    fun zonedNow(nowMs: Long, zone: Any?, fallbackZone: String? = null): ZonedNow {
        val z = FlowCalendar.validZone(zone) ?: FlowCalendar.validZone(fallbackZone) ?: java.util.TimeZone.getDefault()
        val p = FlowCalendar.parts(nowMs, z)
        return ZonedNow(zone = z.id, date = p.date, time = p.time, dayIndex = p.weekday, dayName = DAY_NAMES[p.weekday])
    }

    /**
     * Whether the business is open at `now`, read in the owner's timezone.
     * An overnight slot (22:00 - 02:00) is open from its start until
     * midnight and, the next day, until its end.
     */
    fun isWithinBusinessHours(data: Map<String, Any?>?, nowMs: Long, fallbackZone: String? = null): Boolean {
        val at = zonedNow(nowMs, data?.get("timezone"), fallbackZone)
        val weekly = Js.list(data?.get("weeklyHours")).mapNotNull { Js.map(it) }
        val excludedDays = Js.list(data?.get("excludeDays")).map { Js.str(it).trim().lowercase() }.toSet()
        val excludedDates = Js.list(data?.get("excludeDates")).mapNotNull { normaliseDate(it) }.toSet()

        fun isClosedDay(dayName: String, date: String) = dayName.lowercase() in excludedDays || date in excludedDates
        fun hoursFor(dayIndex: Int): Map<String, Any?>? =
            weekly.firstOrNull { Js.str(it["dayName"]).trim().lowercase() == DAY_NAMES[dayIndex].lowercase() }
                ?: weekly.firstOrNull { it["day"] != null && it["day"] != "" && Js.number(it["day"]) == dayIndex.toDouble() }
        fun slotsOf(day: Map<String, Any?>?): List<Pair<String, String>> =
            if (day != null && Js.truthy(day["available"]) && day["slots"] is List<*>)
                Js.list(day["slots"]).mapNotNull { s ->
                    val m = Js.map(s)
                    val start = hhmm(m?.get("start"))
                    val end = hhmm(m?.get("end"))
                    if (start != null && end != null) start to end else null
                }
            else emptyList()

        if (at.dayIndex < 0 || isClosedDay(at.dayName, at.date)) return false
        for ((start, end) in slotsOf(hoursFor(at.dayIndex))) {
            if (if (start <= end) start <= at.time && at.time <= end else at.time >= start) return true
        }
        val yIndex = (at.dayIndex + 6) % 7
        val yDate = FlowCalendar.previousDate(at.date)
        if (!isClosedDay(DAY_NAMES[yIndex], yDate)) {
            for ((start, end) in slotsOf(hoursFor(yIndex))) {
                if (end < start && at.time <= end) return true
            }
        }
        return false
    }
}
