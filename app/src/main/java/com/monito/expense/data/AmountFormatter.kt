package com.monito.expense.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Formats and parses amounts according to the user's currency settings (2 decimals, like the original). */
object AmountFormatter {

    fun format(amount: BigDecimal, c: CurrencySettings, withSymbol: Boolean = true): String {
        val symbols = DecimalFormatSymbols(Locale.US).apply {
            decimalSeparator = c.decimalSeparator
            groupingSeparator = c.thousandSeparator ?: ','
        }
        val df = DecimalFormat("#,##0.00", symbols).apply {
            isGroupingUsed = c.thousandSeparator != null
            roundingMode = RoundingMode.HALF_UP
        }
        val number = df.format(amount)
        if (!withSymbol || c.symbol.isBlank()) return number
        return if (c.symbolOnLeft) "${c.symbol}$number" else "$number ${c.symbol}"
    }

    /** Parses user input; accepts the configured separators, a bare '.' and simple arithmetic expressions. */
    fun parse(text: String, c: CurrencySettings): BigDecimal? {
        var t = text.trim()
        if (t.isEmpty()) return null
        t = t.replace(c.symbol, "").replace(" ", "")
        if (c.thousandSeparator != null && c.thousandSeparator != ' ') {
            // Only strip thousand separators when they are not also the decimal separator.
            if (c.thousandSeparator != c.decimalSeparator) t = t.replace(c.thousandSeparator.toString(), "")
        }
        if (c.decimalSeparator != '.') t = t.replace(c.decimalSeparator, '.')
        return ExpressionEvaluator.evaluate(t)
    }
}

/**
 * Tiny calculator for the amount field: numbers with optional decimals combined with + - * / and
 * parentheses. Mirrors the numpad of the original app which allowed expressions such as 120+35.5.
 */
object ExpressionEvaluator {

    fun evaluate(expr: String): BigDecimal? {
        val tokens = tokenize(expr) ?: return null
        if (tokens.isEmpty()) return null
        return runCatching { Parser(tokens).parse() }.getOrNull()?.stripTrailingZerosSafe()
    }

    private fun BigDecimal.stripTrailingZerosSafe(): BigDecimal {
        val s = stripTrailingZeros()
        return if (s.scale() < 0) s.setScale(0) else s
    }

    private fun tokenize(s: String): List<String>? {
        val out = ArrayList<String>()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            when {
                ch.isWhitespace() -> i++
                ch.isDigit() || ch == '.' -> {
                    val start = i
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                    out.add(s.substring(start, i))
                }
                ch in "+-*/()" -> { out.add(ch.toString()); i++ }
                ch == '×' -> { out.add("*"); i++ }
                ch == '÷' -> { out.add("/"); i++ }
                else -> return null
            }
        }
        return out
    }

    private class Parser(private val tokens: List<String>) {
        private var pos = 0

        fun parse(): BigDecimal {
            val v = expression()
            if (pos != tokens.size) throw IllegalArgumentException("trailing tokens")
            return v
        }

        private fun peek(): String? = tokens.getOrNull(pos)

        private fun expression(): BigDecimal {
            var v = term()
            while (true) {
                when (peek()) {
                    "+" -> { pos++; v += term() }
                    "-" -> { pos++; v -= term() }
                    else -> return v
                }
            }
        }

        private fun term(): BigDecimal {
            var v = factor()
            while (true) {
                when (peek()) {
                    "*" -> { pos++; v *= factor() }
                    "/" -> {
                        pos++
                        val d = factor()
                        if (d.signum() == 0) throw ArithmeticException("divide by zero")
                        v = v.divide(d, 10, RoundingMode.HALF_UP)
                    }
                    else -> return v
                }
            }
        }

        private fun factor(): BigDecimal {
            val t = peek() ?: throw IllegalArgumentException("unexpected end")
            return when (t) {
                "-" -> { pos++; factor().negate() }
                "+" -> { pos++; factor() }
                "(" -> {
                    pos++
                    val v = expression()
                    if (peek() != ")") throw IllegalArgumentException("missing )")
                    pos++
                    v
                }
                else -> { pos++; BigDecimal(t) }
            }
        }
    }
}
