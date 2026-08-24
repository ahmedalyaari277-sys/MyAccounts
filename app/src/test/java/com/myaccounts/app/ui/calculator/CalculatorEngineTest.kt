package com.myaccounts.app.ui.calculator

import com.myaccounts.app.ui.components.CalculatorEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorEngineTest {
    private fun eval(expression: String): String = CalculatorEngine.evaluate(expression)

    @Test fun addition() { assertEquals("15", eval("10+5")) }
    @Test fun subtraction() { assertEquals("5", eval("10-5")) }
    @Test fun multiplication() { assertEquals("50", eval("10*5")) }
    @Test fun division() { assertEquals("2", eval("10/5")) }
    @Test fun precedence() { assertEquals("160", eval("100+20*3")) }
    @Test fun decimalNumbers() { assertEquals("12.5", eval("10.5+2")) }
    @Test fun chainedOperations() { assertEquals("32", eval("100/5+3*4")) }
    @Test fun negativeResult() { assertEquals("-5", eval("5-10")) }
    @Test fun divisionByZeroIsSafe() { assertEquals("خطأ", eval("10/0")) }
}
