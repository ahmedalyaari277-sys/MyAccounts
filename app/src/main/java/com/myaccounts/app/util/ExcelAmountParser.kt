package com.myaccounts.app.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Parses the raw value stored by Excel, not its visual cell formatting.
 * Accepts numeric/formula-result representations, Arabic/Persian digits,
 * decimal/grouping separators and common currency decorations.
 */
object ExcelAmountParser {
    fun parse(value: String): Long? {
        var s = value.trim()
            .replace("\uFEFF", "")
            .replace("\u00A0", "")
            .replace("\u202F", "")
            .replace("−", "-")
            .replace("–", "-")
            .replace("—", "-")
            .replace("٫", ".")
            .replace("٬", "")
            .replace(" ", "")
        if (s.isBlank()) return null

        s = s.map { ch ->
            when (ch) {
                in '٠'..'٩' -> ('0'.code + ch.code - '٠'.code).toChar()
                in '۰'..'۹' -> ('0'.code + ch.code - '۰'.code).toChar()
                else -> ch
            }
        }.joinToString("")

        // Handle a common Excel/text export form such as
        // "123.45 ريال يمني" or "SAR 123.45".
        s = s.replace(Regex("[^0-9+.,eE-]"), "")
        if (s.isBlank() || s == "+" || s == "-") return null

        val canonical = when {
            s.contains('.') && s.contains(',') -> {
                val lastDot = s.lastIndexOf('.')
                val lastComma = s.lastIndexOf(',')
                if (lastComma > lastDot) s.replace(".", "").replace(',', '.')
                else s.replace(",", "")
            }
            s.count { it == ',' } > 0 -> {
                // One comma followed by 1–2 digits is a decimal separator.
                // A comma followed by exactly 3 digits is normally grouping.
                val last = s.lastIndexOf(',')
                val digitsAfter = s.length - last - 1
                if (s.count { it == ',' } == 1 && digitsAfter in 1..2) s.replace(',', '.')
                else s.replace(",", "")
            }
            else -> s
        }

        return runCatching {
            val decimal = BigDecimal(canonical)
            if (decimal.signum() < 0) return@runCatching null
            // More than two significant decimal places is invalid. Trailing
            // zeroes beyond two are harmless (e.g. 123.4500).
            if (decimal.scale() > 2) decimal.setScale(2, RoundingMode.UNNECESSARY)
            decimal.movePointRight(2).longValueExact()
        }.getOrNull()
    }
}
