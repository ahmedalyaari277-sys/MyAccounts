package com.myaccounts.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.myaccounts.app.util.TransferMode

@Composable
fun TransferModeDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onModeSelected: (TransferMode) -> Unit,
    danger: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onModeSelected(TransferMode.REPLACE_ALL) }) { Text("استبدال الكل") }
        },
        dismissButton = {
            TextButton(onClick = { onModeSelected(TransferMode.ADD_REMAINING) }) { Text("فحص وإضافة المتبقي") }
        }
    )
}
