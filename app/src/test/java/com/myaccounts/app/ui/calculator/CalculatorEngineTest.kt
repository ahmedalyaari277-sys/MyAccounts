package com.myaccounts.app.ui.calculator

import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorEngineTest {
    private fun eval(expression: String): Double = CalculatorEngine.evaluate(expression)

    @Test fun addition() { assertEquals(15.0, eval("10+5"), 0.000001) }
    @Test fun subtraction() { assertEquals(5.0, eval("10-5"), 0.000001) }
    @Test fun multiplication() { assertEquals(50.0, eval("10*5"), 0.000001) }
    @Test fun division() { assertEquals(2.0, eval("10/5"), 0.000001) }
    @Test fun precedence() { assertEquals(160.0, eval("100+20*3"), 0.000001) }
    @Test fun decimalNumbers() { assertEquals(12.5, eval("10.5+2"), 0.000001) }
    @Test fun chainedOperations() { assertEquals(20.0, eval("100/5+3*4"), 0.000001) }
    @Test fun negativeResult() { assertEquals(-5.0, eval("5-10"), 0.000001) }
    @Test fun divisionByZeroIsSafe() {
        try {
            eval("10/0")
        } catch (_: ArithmeticException) {
            return
        } catch (_: IllegalArgumentException) {
            return
        }
        // The engine must not crash the app; non-finite results are acceptable.
    }
}
