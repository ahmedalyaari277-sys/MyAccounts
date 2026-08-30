package com.myaccounts.app.ui.components

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Pure calculator logic. No Android/UI/database dependencies. */
object CalculatorEngine {
    private val mathContext = MathContext(34, RoundingMode.HALF_UP)

    fun evaluate(expression: String): String? {
        val normalized = expression.replace('×', '*').replace('÷', '/').replace('−', '-')
        if (normalized.isBlank()) return null
        return try {
            val parser = Parser(normalized)
            val value = parser.parseExpression()
            if (!parser.atEnd()) return null
            format(value)
        } catch (_: Exception) {
            null
        }
    }

    private fun format(value: BigDecimal): String =
        value.setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
            .let { if (it == "-0") "0" else it }

    private class Parser(private val input: String) {
        private var index = 0
        fun atEnd() = index >= input.length

        fun parseExpression(): BigDecimal {
            var value = parseTerm()
            while (!atEnd()) {
                value = when (input[index]) {
                    '+' -> { index++; value.add(parseTerm(), mathContext) }
                    '-' -> { index++; value.subtract(parseTerm(), mathContext) }
                    else -> return value
                }
            }
            return value
        }

        private fun parseTerm(): BigDecimal {
            var value = parseNumber()
            while (!atEnd()) {
                value = when (input[index]) {
                    '*' -> { index++; value.multiply(parseNumber(), mathContext) }
                    '/' -> {
                        index++
                        val divisor = parseNumber()
                        if (divisor.compareTo(BigDecimal.ZERO) == 0) throw ArithmeticException("division by zero")
                        value.divide(divisor, mathContext)
                    }
                    else -> return value
                }
            }
            return value
        }

        private fun parseNumber(): BigDecimal {
            while (!atEnd() && input[index].isWhitespace()) index++
            val start = index
            if (!atEnd() && (input[index] == '+' || input[index] == '-')) index++
            var dotSeen = false
            var digitSeen = false
            while (!atEnd()) {
                val c = input[index]
                when {
                    c.isDigit() -> { digitSeen = true; index++ }
                    c == '.' && !dotSeen -> { dotSeen = true; index++ }
                    else -> break
                }
            }
            if (!digitSeen) throw NumberFormatException("number expected")
            return BigDecimal(input.substring(start, index), mathContext)
        }
    }
}
