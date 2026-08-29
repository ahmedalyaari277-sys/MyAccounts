package com.myaccounts.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorEngineTest {
    @Test fun addition() = assertEquals("15", CalculatorEngine.evaluate("10+5"))
    @Test fun subtraction() = assertEquals("5", CalculatorEngine.evaluate("10-5"))
    @Test fun multiplication() = assertEquals("50", CalculatorEngine.evaluate("10×5"))
    @Test fun division() = assertEquals("2", CalculatorEngine.evaluate("10÷5"))
    @Test fun precedence() = assertEquals("160", CalculatorEngine.evaluate("100+20×3"))
    @Test fun decimalDivision() = assertEquals("2.5", CalculatorEngine.evaluate("10÷4"))
    @Test fun roundsDivisionToTwoDecimalPlaces() = assertEquals("33.33", CalculatorEngine.evaluate("100÷3"))
    @Test fun roundsHalfUpToTwoDecimalPlaces() = assertEquals("1.24", CalculatorEngine.evaluate("1.235"))
    @Test fun divideByZeroIsInvalid() = assertNull(CalculatorEngine.evaluate("10÷0"))
}
