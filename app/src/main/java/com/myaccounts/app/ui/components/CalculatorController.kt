package com.myaccounts.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class CalculatorController {
    var isOpen by mutableStateOf(false)
        private set
    var expression by mutableStateOf("")
        private set

    val result: String?
        get() = CalculatorEngine.evaluate(expression)

    fun open() { isOpen = true }
    fun close() { isOpen = false }
    fun clear() { expression = "" }
    fun backspace() { expression = expression.dropLast(1) }

    fun press(key: String) {
        when (key) {
            "=" -> Unit
            "×", "÷", "+", "−" -> appendOperator(key)
            "." -> appendDecimal()
            else -> expression += key
        }
    }

    private fun appendOperator(operator: String) {
        if (expression.isBlank()) return
        expression = expression.trimEnd('×', '÷', '+', '−') + operator
    }

    private fun appendDecimal() {
        val current = expression.takeLastWhile { it.isDigit() || it == '.' }
        if (!current.contains('.')) expression += if (current.isEmpty()) "0." else "."
    }
}
