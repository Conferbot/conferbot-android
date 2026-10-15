package com.conferbot.sdk.core.flow

/**
 * The few JavaScript value rules the web widget's flow code leans on
 * (String(v), Number(v), JSON.stringify), so the Kotlin ports below read
 * the same values the same way. Node data arrives as Maps, Lists, Strings,
 * Numbers and Booleans (parsed JSON).
 */
internal object Js {
    /** String(v ?? ""): numbers without a trailing ".0", lists joined by ",". */
    fun str(v: Any?): String = when (v) {
        null -> ""
        is String -> v
        is Number -> num(v.toDouble())
        is Boolean -> v.toString()
        is List<*> -> v.joinToString(",") { if (it == null) "" else str(it) }
        is Map<*, *> -> "[object Object]"
        else -> v.toString()
    }

    /** A number as JavaScript prints it: 1200, 3.5, NaN, Infinity. */
    fun num(d: Double): String = when {
        d.isNaN() -> "NaN"
        d == Double.POSITIVE_INFINITY -> "Infinity"
        d == Double.NEGATIVE_INFINITY -> "-Infinity"
        d == Math.floor(d) && Math.abs(d) < 1e21 -> java.math.BigDecimal(d).toPlainString()
        else -> {
            val s = d.toString()
            if (s.contains('E')) java.math.BigDecimal(s).stripTrailingZeros().toPlainString() else s
        }
    }

    private val NUMERIC = Regex("^[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?$")

    /** Number(v): "" and null are 0, junk is NaN. */
    fun number(v: Any?): Double = when (v) {
        null -> 0.0
        is Number -> v.toDouble()
        is Boolean -> if (v) 1.0 else 0.0
        is String -> {
            val t = v.trim()
            when {
                t.isEmpty() -> 0.0
                t == "Infinity" || t == "+Infinity" -> Double.POSITIVE_INFINITY
                t == "-Infinity" -> Double.NEGATIVE_INFINITY
                NUMERIC.matches(t) -> t.toDouble()
                else -> Double.NaN
            }
        }
        is List<*> -> if (v.isEmpty()) 0.0 else if (v.size == 1) number(str(v[0])) else Double.NaN
        else -> Double.NaN
    }

    fun isFinite(d: Double) = !d.isNaN() && !d.isInfinite()

    /** JavaScript truthiness: null, false, 0, NaN and "" are false. */
    fun truthy(v: Any?): Boolean = when (v) {
        null -> false
        is Boolean -> v
        is Number -> v.toDouble().let { it != 0.0 && !it.isNaN() }
        is String -> v.isNotEmpty()
        else -> true
    }

    /** A Double that holds a whole number becomes a Long (what JSON would give back). */
    fun tidy(d: Double): Any = if (isFinite(d) && d == Math.floor(d) && Math.abs(d) < 9.0E15) d.toLong() else d

    /** JSON.stringify for parsed-JSON values (Maps keep their key order). */
    fun stringify(v: Any?): String {
        val sb = StringBuilder()
        write(sb, v)
        return sb.toString()
    }

    private fun write(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> quote(sb, v)
            is Number -> {
                val d = v.toDouble()
                sb.append(if (isFinite(d)) num(d) else "null")
            }
            is Boolean -> sb.append(v.toString())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (value is Function<*>) continue
                    if (!first) sb.append(',')
                    first = false
                    quote(sb, k.toString())
                    sb.append(':')
                    write(sb, value)
                }
                sb.append('}')
            }
            is List<*> -> {
                sb.append('[')
                v.forEachIndexed { i, it ->
                    if (i > 0) sb.append(',')
                    write(sb, it)
                }
                sb.append(']')
            }
            is Array<*> -> write(sb, v.toList())
            else -> quote(sb, v.toString())
        }
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    /** JSON.parse for text that should hold an array or object; null when it does not parse. */
    fun parse(text: String): Any? = try {
        JsonReader(text).value().also { }
    } catch (e: Exception) {
        null
    }

    /** A tiny strict JSON reader (no org.json: it is stubbed in JVM tests). */
    private class JsonReader(private val s: String) {
        private var i = 0

        fun value(): Any? {
            val v = read()
            ws()
            if (i != s.length) throw IllegalArgumentException("trailing")
            return v
        }

        private fun ws() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun read(): Any? {
            ws()
            if (i >= s.length) throw IllegalArgumentException("end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> string()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) number() else throw IllegalArgumentException("bad")
            }
        }

        private fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) throw IllegalArgumentException("bad")
            i += word.length
            return v
        }

        private fun number(): Any {
            val m = Regex("-?(0|[1-9]\\d*)(\\.\\d+)?([eE][-+]?\\d+)?").find(s, i) ?: throw IllegalArgumentException("num")
            if (m.range.first != i) throw IllegalArgumentException("num")
            i = m.range.last + 1
            return tidy(m.value.toDouble())
        }

        private fun string(): String {
            val sb = StringBuilder()
            i++
            while (true) {
                if (i >= s.length) throw IllegalArgumentException("str")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        val e = s[i++]
                        when (e) {
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun obj(): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            i++
            ws()
            if (s[i] == '}') {
                i++
                return out
            }
            while (true) {
                ws()
                val k = string()
                ws()
                if (s[i++] != ':') throw IllegalArgumentException(":")
                out[k] = read()
                ws()
                when (s[i++]) {
                    ',' -> continue
                    '}' -> return out
                    else -> throw IllegalArgumentException("obj")
                }
            }
        }

        private fun arr(): List<Any?> {
            val out = ArrayList<Any?>()
            i++
            ws()
            if (s[i] == ']') {
                i++
                return out
            }
            while (true) {
                out.add(read())
                ws()
                when (s[i++]) {
                    ',' -> continue
                    ']' -> return out
                    else -> throw IllegalArgumentException("arr")
                }
            }
        }
    }

    /** A value as a String-keyed map, or null. */
    @Suppress("UNCHECKED_CAST")
    fun map(v: Any?): Map<String, Any?>? = v as? Map<String, Any?>

    fun list(v: Any?): List<Any?> = (v as? List<*>) ?: emptyList<Any?>()

    /** encodeURIComponent. */
    fun encodeURIComponent(s: String): String {
        val keep = "-_.!~*'()"
        val sb = StringBuilder()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = (b.toInt() and 0xff)
            val ch = c.toChar()
            if (c < 128 && (ch.isLetterOrDigit() || keep.indexOf(ch) >= 0)) sb.append(ch) else sb.append(String.format("%%%02X", c))
        }
        return sb.toString()
    }

    /** decodeURIComponent; the text as it is when it does not decode. */
    fun decodeURIComponent(s: String): String = try {
        java.net.URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    } catch (e: Exception) {
        s
    }
}
