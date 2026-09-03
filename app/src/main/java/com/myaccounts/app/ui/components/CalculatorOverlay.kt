package com.myaccounts.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
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
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(expression.ifBlank { "0" }, modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Medium)
            Text(
                result.ifBlank { "0" },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // The application is Arabic RTL, but the calculator keypad itself is
            // deliberately LTR. This makes the physical left-to-right calculator
            // positions unambiguous and prevents Compose RTL mirroring.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                calculatorRow("7", "8", "9", "÷", onKey)
                calculatorRow("4", "5", "6", "×", onKey)
                calculatorRow("1", "2", "3", "−", onKey)

                // 0 occupies the first two columns; decimal and plus occupy the
                // third and fourth columns respectively.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    calculatorKey("0", onKey, Modifier.weight(2f))
                    calculatorKey(".", onKey, Modifier.weight(1f))
                    calculatorKey("+", onKey, Modifier.weight(1f))
                }

                // Equals is intentionally a separate final row, centered in the
                // keypad rather than sharing the numeric/operator row.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Spacer(Modifier.weight(1f))
                    calculatorKey("=", onKey, Modifier.weight(2f))
                    Spacer(Modifier.weight(1f))
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

@Composable
private fun calculatorRow(
    first: String,
    second: String,
    third: String,
    fourth: String,
    onKey: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        calculatorKey(first, onKey, Modifier.weight(1f))
        calculatorKey(second, onKey, Modifier.weight(1f))
        calculatorKey(third, onKey, Modifier.weight(1f))
        calculatorKey(fourth, onKey, Modifier.weight(1f))
    }
}

@Composable
private fun calculatorKey(key: String, onKey: (String) -> Unit, modifier: Modifier) {
    Button(onClick = { onKey(key) }, modifier = modifier) { Text(key) }
}
