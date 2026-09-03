package com.myaccounts.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

            // Keep the calculator keypad LTR so its visual arrangement matches
            // the physical KK-402: operators are on the user's right.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text("مسح") }
                    TextButton(onClick = onBackspace, modifier = Modifier.weight(1f)) { Text("حذف") }
                    onUseResult?.let {
                        TextButton(onClick = it, enabled = result.isNotBlank(), modifier = Modifier.weight(1f)) {
                            Text("استخدام النتيجة")
                        }
                    } ?: Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("إغلاق") }
                }

                calculatorRow(listOf("7", "8", "9", "÷"), onKey)
                calculatorRow(listOf("4", "5", "6", "×"), onKey)

                // The KK-402 places + in a tall right-hand key spanning
                // the 1-2-3 row and the 0-.-= row.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        calculatorRow(listOf("1", "2", "3"), onKey)
                        calculatorRow(listOf("0", ".", "="), onKey)
                    }
                    calculatorKey("−", onKey, Modifier.weight(1f))
                    calculatorKey("+", onKey, Modifier.weight(1f).height(102.dp))
                }
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
