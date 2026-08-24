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
                                "=" -> result = CalculatorEngine.evaluate(expression)
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
