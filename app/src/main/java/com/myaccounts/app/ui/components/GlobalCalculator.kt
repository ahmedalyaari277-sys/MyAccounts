package com.myaccounts.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode

private fun evaluate(expression: String): String {
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

@Composable
fun GlobalCalculator(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expression by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }

    Card(modifier = modifier.padding(12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("الحاسبة", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onDismiss) { Text("إغلاق") }
            }
            Text(if (result.isBlank()) expression.ifBlank { "0" } else result)
            val keys = listOf("7","8","9","÷","4","5","6","×","1","2","3","−","0",".","+","=")
            keys.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { key ->
                        Button(modifier = Modifier.weight(1f), onClick = {
                            when (key) {
                                "=" -> result = evaluate(expression)
                                else -> { expression += key; result = "" }
                            }
                        }) { Text(key) }
                    }
                }
            }
            TextButton(onClick = { expression = ""; result = "" }) { Text("مسح") }
        }
    }
}
