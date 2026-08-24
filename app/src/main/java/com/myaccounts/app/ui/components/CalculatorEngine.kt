package com.myaccounts.app.ui.components

import java.math.BigDecimal
import java.math.RoundingMode

object CalculatorEngine {
    fun evaluate(expression: String): String {
        val normalized = expression
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace(" ", "")

        if (normalized.isBlank()) return "0"

        return runCatching {
            val tokens = tokenize(normalized) ?: return "خطأ"
            if (tokens.isEmpty()) return "0"

            // First pass: multiplication and division have higher precedence.
            val reducedValues = mutableListOf<BigDecimal>()
            val reducedOperators = mutableListOf<Char>()
            var current = tokens[0].value
            var index = 1

            while (index < tokens.size) {
                val operator = tokens[index].operator ?: return "خطأ"
                val next = tokens[index + 1].value
                when (operator) {
                    '*' -> current = current.multiply(next)
                    '/' -> current = current.divide(next, 12, RoundingMode.HALF_UP)
                    '+', '-' -> {
                        reducedValues += current
                        reducedOperators += operator
                        current = next
                    }
                    else -> return "خطأ"
                }
                index += 2
            }
            reducedValues += current

            // Second pass: addition and subtraction from left to right.
            var result = reducedValues.first()
            for (operatorIndex in reducedOperators.indices) {
                result = when (reducedOperators[operatorIndex]) {
                    '+' -> result.add(reducedValues[operatorIndex + 1])
                    '-' -> result.subtract(reducedValues[operatorIndex + 1])
                    else -> return "خطأ"
                }
            }

            result.stripTrailingZeros().toPlainString()
        }.getOrElse { "خطأ" }
    }

    private data class Token(
        val value: BigDecimal,
        val operator: Char? = null
    )

    private fun tokenize(expression: String): List<Token>? {
        val tokens = mutableListOf<Token>()
        var index = 0
        var expectingNumber = true

        while (index < expression.length) {
            var sign = ""
            if (expectingNumber && (expression[index] == '+' || expression[index] == '-')) {
                sign = expression[index].toString()
                index++
            }

            val start = index
            var dotSeen = false
            while (index < expression.length) {
                val char = expression[index]
                when {
                    char.isDigit() -> index++
                    char == '.' && !dotSeen -> {
                        dotSeen = true
                        index++
                    }
                    else -> break
                }
            }

            if (start == index) return null

            val numberText = sign + expression.substring(start, index)
            val number = numberText.toBigDecimalOrNull() ?: return null
            tokens += Token(value = number)
            expectingNumber = false

            if (index < expression.length) {
                val operator = expression[index]
                if (operator !in charArrayOf('+', '-', '*', '/')) return null
                tokens += Token(value = BigDecimal.ZERO, operator = operator)
                index++
                expectingNumber = true
            }
        }

        return if (expectingNumber) null else tokens
    }
}
