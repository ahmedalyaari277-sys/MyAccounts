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

            // The keypad is explicitly LTR so that its visual order is stable
            // inside the Arabic RTL app: the operator column is on the user's right.
            // Only button positions are changed; all existing callbacks are preserved.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                // KK-402-style top function row, using the calculator's existing
                // non-arithmetic actions without changing what any action does.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text("مسح") }
                    TextButton(onClick = onBackspace, modifier = Modifier.weight(1f)) { Text("حذف") }
                    onUseResult?.let {
                        TextButton(onClick = it, enabled = result.isNotBlank(), modifier = Modifier.weight(1f)) {
                            Text("استخدام النتيجة")
                        }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("إغلاق") }
                }

                // Physical calculator number order, with the arithmetic operator
                // in the rightmost column from the user's point of view.
                calculatorRow(listOf("7", "8", "9", "÷"), onKey)
                calculatorRow(listOf("4", "5", "6", "×"), onKey)
                calculatorRow(listOf("1", "2", "3", "−"), onKey)
                calculatorRow(listOf("0", ".", "=", "+"), onKey)
            }
        }
    }
}

@Composable
private fun calculatorRow(keys: List<String>, onKey: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.forEach { key -> calculatorKey(key, onKey, Modifier.weight(1f)) }
    }
}

@Composable
private fun calculatorKey(key: String, onKey: (String) -> Unit, modifier: Modifier) {
    Button(onClick = { onKey(key) }, modifier = modifier) { Text(key) }
}
