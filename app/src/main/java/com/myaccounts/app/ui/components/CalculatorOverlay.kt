package com.myaccounts.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Small, screen-local calculator overlay. It never writes to the database. */
@Composable
fun CalculatorOverlay(
    expression: String,
    result: String,
    onKey: (String) -> Unit,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onDismiss: () -> Unit,
    onUseResult: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(expression.ifBlank { "0" }, modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Medium)
            Text(result.ifBlank { "0" }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val rows = listOf(
                listOf("7", "8", "9", "÷"),
                listOf("4", "5", "6", "×"),
                listOf("1", "2", "3", "−"),
                listOf("0", ".", "+", "=")
            )
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { key -> Button(onClick = { onKey(key) }, modifier = Modifier.weight(1f)) { Text(key) } }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onClear) { Text("مسح") }
                TextButton(onClick = onBackspace) { Text("حذف") }
                onUseResult?.let { TextButton(onClick = it, enabled = result.isNotBlank()) { Text("استخدام النتيجة") } }
                TextButton(onClick = onDismiss) { Text("إغلاق") }
            }
        }
    }
}
