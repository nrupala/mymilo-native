package org.aimlds.mymilo.tools

/**
 * Local tools — work that runs entirely on the phone, no model,
 * no network. The first tier of the offline brain.
 *
 * Returns a reply if the message is a local-tool request, else null.
 */
object LocalTools {

    fun tryHandle(message: String): String? {
        val text = message.trim()
        return tryCalculator(text)
            ?: tryUnitConversion(text)
    }

    // ── Calculator ──────────────────────────────────────────
    // "calculate 45 * 3.2", "what is 15% of 2400", or a bare "45*3.2"
    private fun tryCalculator(text: String): String? {
        val lower = text.lowercase()

        // "X% of Y"
        val pct = Regex("""(\d+(?:\.\d+)?)\s*%\s*of\s*(\d+(?:\.\d+)?)""").find(lower)
        if (pct != null) {
            val p = pct.groupValues[1].toDouble()
            val of = pct.groupValues[2].toDouble()
            return "${trimNum(p)}% of ${trimNum(of)} = ${trimNum(p / 100.0 * of)}"
        }

        val expr = lower
            .removePrefix("calculate")
            .removePrefix("calc")
            .removePrefix("what is")
            .removePrefix("what's")
            .trim().trimEnd('?', '=')
        if (!expr.matches(Regex("""[\d\s\.\+\-\*/\(\)^%]+"""))) return null
        if (!expr.any { it in "+-*/^" }) return null
        return try {
            val result = ExpressionParser(expr).parse()
            if (result.isNaN() || result.isInfinite()) null
            else "$expr = ${trimNum(result)}"
        } catch (e: Exception) {
            null
        }
    }

    // ── Unit conversion ─────────────────────────────────────
    private fun tryUnitConversion(text: String): String? {
        val lower = text.lowercase()
        val m = Regex(
            """(\d+(?:\.\d+)?)\s*(km|kilometers?|mi|miles?|kg|kilograms?|lbs?|pounds?|c|f|celsius|fahrenheit)\s*(?:in|to|=)\s*(km|kilometers?|mi|miles?|kg|kilograms?|lbs?|pounds?|c|f|celsius|fahrenheit)"""
        ).find(lower) ?: return null
        val value = m.groupValues[1].toDouble()
        val from = normalizeUnit(m.groupValues[2])
        val to = normalizeUnit(m.groupValues[3])
        val converted = convert(value, from, to) ?: return null
        return "${trimNum(value)} $from = ${trimNum(converted)} $to"
    }

    private fun normalizeUnit(u: String): String = when (u) {
        "kilometer", "kilometers" -> "km"
        "mile", "miles" -> "mi"
        "kilogram", "kilograms" -> "kg"
        "lb", "lbs", "pound", "pounds" -> "lb"
        "celsius" -> "c"
        "fahrenheit" -> "f"
        else -> u
    }

    private fun convert(v: Double, from: String, to: String): Double? = when {
        from == "km" && to == "mi" -> v * 0.621371
        from == "mi" && to == "km" -> v * 1.60934
        from == "kg" && to == "lb" -> v * 2.20462
        from == "lb" && to == "kg" -> v * 0.453592
        from == "c" && to == "f" -> v * 9.0 / 5.0 + 32
        from == "f" && to == "c" -> (v - 32) * 5.0 / 9.0
        from == to -> v
        else -> null
    }

    private fun trimNum(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString()
        else String.format("%.4f", d).trimEnd('0').trimEnd('.')
}

/** Small recursive-descent arithmetic parser (no deps). */
private class ExpressionParser(private val input: String) {
    private var pos = 0

    fun parse(): Double {
        val v = parseExpr()
        skipSpaces()
        if (pos != input.length) throw IllegalArgumentException("trailing input")
        return v
    }

    private fun skipSpaces() { while (pos < input.length && input[pos] == ' ') pos++ }
    private fun peek(): Char? { skipSpaces(); return if (pos < input.length) input[pos] else null }

    private fun parseExpr(): Double {
        var v = parseTerm()
        while (true) {
            when (peek()) {
                '+' -> { pos++; v += parseTerm() }
                '-' -> { pos++; v -= parseTerm() }
                else -> return v
            }
        }
    }

    private fun parseTerm(): Double {
        var v = parseFactor()
        while (true) {
            when (peek()) {
                '*' -> { pos++; v *= parseFactor() }
                '/' -> { pos++; v /= parseFactor() }
                '%' -> { pos++; v %= parseFactor() }
                else -> return v
            }
        }
    }

    private fun parseFactor(): Double {
        val base = parseUnary()
        if (peek() == '^') {
            pos++
            return Math.pow(base, parseFactor())
        }
        return base
    }

    private fun parseUnary(): Double {
        return when (peek()) {
            '-' -> { pos++; -parseUnary() }
            '+' -> { pos++; parseUnary() }
            '(' -> {
                pos++
                val v = parseExpr()
                if (peek() == ')') pos++
                v
            }
            else -> parseNumber()
        }
    }

    private fun parseNumber(): Double {
        skipSpaces()
        val start = pos
        while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
        if (start == pos) throw IllegalArgumentException("number expected")
        return input.substring(start, pos).toDouble()
    }
}
