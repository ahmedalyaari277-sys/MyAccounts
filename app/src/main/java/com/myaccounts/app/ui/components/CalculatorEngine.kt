package com.myaccounts.app.ui.components

import java.math.BigDecimal
import java.math.RoundingMode

object CalculatorEngine {
    fun evaluate(expression: String): String {
        val normalized = expression.replace("×", "*").replace("÷", "/").replace("−", "-").replace(" ", "")
        if (normalized.isBlank()) return "0"
        val parts = Regex("(?<=[+\\-*/])|(?=[+\\-*/])").split(normalized).filter { it.isNotEmpty() }
        if (parts.isEmpty() || parts.size % 2 == 0) return "خطأ"
        return runCatching {
            var value = BigDecimal(parts[0])
            var i = 1
            while (i < parts.size) {
                val op = parts[i]
                val next = BigDecimal(parts[i + 1])
                value = when (op) {
                    "+" -> value.add(next)
                    "-" -> value.subtract(next)
                    "*" -> value.multiply(next)
                    "/" -> value.divide(next, 12, RoundingMode.HALF_UP)
                    else -> return "خطأ"
                }
                i += 2
            }
            value.stripTrailingZeros().toPlainString()
        }.getOrElse { "خطأ" }
    }
}
