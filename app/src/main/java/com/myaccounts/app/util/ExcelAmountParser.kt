package com.myaccounts.app.util

import java.math.BigDecimal
import java.math.RoundingMode

object ExcelAmountParser {
    fun parse(value: String): Long? {
        var s = value.trim()
            .replace("\u00A0", "")
            .replace("\u202F", "")
            .replace(" ", "")
            .replace("٬", "")
            .replace("٫", ".")
            .replace("−", "-")
            .replace("–", "-")
            .replace("—", "-")
        if (s.isBlank()) return null
        s = s.map { ch ->
            when (ch) {
                in '٠'..'٩' -> ('0'.code + ch.code - '٠'.code).toChar()
                in '۰'..'۹' -> ('0'.code + ch.code - '۰'.code).toChar()
                else -> ch
            }
        }.joinToString("")
        val canonical = when {
            s.contains('.') && s.contains(',') -> {
                val dot = s.lastIndexOf('.')
                val comma = s.lastIndexOf(',')
                if (comma > dot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            }
            else -> s.replace(',', '.')
        }
        return runCatching {
            val decimal = BigDecimal(canonical)
            if (decimal.scale() > 2) decimal.setScale(2, RoundingMode.UNNECESSARY)
            decimal.movePointRight(2).longValueExact()
        }.getOrNull()
    }
}
