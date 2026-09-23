package com.myaccounts.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExcelAmountParserTest {
    @Test fun acceptsRoundedPositiveAmounts() {
        assertEquals(12345L, ExcelAmountParser.parse("123.45"))
        assertEquals(12345L, ExcelAmountParser.parse("123.4500"))
        assertEquals(12345L, ExcelAmountParser.parse("123,45"))
        assertEquals(12345L, ExcelAmountParser.parse("١٢٣٫٤٥"))
        assertEquals(12345L, ExcelAmountParser.parse("123.45E0"))
    }
    @Test fun acceptsExcelGroupingFormats() {
        assertEquals(123456L, ExcelAmountParser.parse("123,456.00"))
        assertEquals(123456L, ExcelAmountParser.parse("123.456,00"))
    }
    @Test fun rejectsRealThirdDecimalAndInvalidValues() {
        assertNull(ExcelAmountParser.parse("123.456"))
        assertNull(ExcelAmountParser.parse("abc"))
        assertNull(ExcelAmountParser.parse(""))
    }
}
