package com.myaccounts.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorKeypadLayoutTest {
    @Test
    fun keypadUsesConventionalRows() {
        assertEquals(
            listOf(
                listOf("7", "8", "9", "÷"),
                listOf("4", "5", "6", "×"),
                listOf("1", "2", "3", "−"),
                listOf("0", ".", "+"),
                listOf("=")
            ),
            calculatorKeypadRows
        )
    }
}
