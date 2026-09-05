package com.myaccounts.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myaccounts.app.data.local.dao.PersonWithAccounts
import com.myaccounts.app.ui.components.AppTopBar
import com.myaccounts.app.ui.components.ConfirmationDialog
import com.myaccounts.app.ui.components.DangerButton
import com.myaccounts.app.ui.components.InformationCard
import com.myaccounts.app.ui.components.PrimaryButton
import com.myaccounts.app.ui.components.SummaryCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedPersonDetailScreen(
    personWithAccounts: PersonWithAccounts,
    onBack: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val person = personWithAccounts.person

    Scaffold(
        topBar = { AppTopBar(title = person.name, onBack = onBack) }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryCard(title = "حساب مؤرشف") {
                Text("هذا الحساب محفوظ في الأرشيف ويمكن استعادته أو حذفه نهائيًا.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("عدد الحسابات: ${personWithAccounts.accounts.size}", style = MaterialTheme.typography.bodyLarge)
            }

            InformationCard {
                Text("بيانات الشخص", style = MaterialTheme.typography.titleMedium)
                if (person.phone.isNotBlank()) Text("الهاتف: ${person.phone}", style = MaterialTheme.typography.bodyLarge)
                if (person.address.isNotBlank()) Text("العنوان: ${person.address}", style = MaterialTheme.typography.bodyLarge)
                if (person.notes.isNotBlank()) Text("الملاحظات: ${person.notes}", style = MaterialTheme.typography.bodyLarge)
                if (person.phone.isBlank() && person.address.isBlank() && person.notes.isBlank()) {
                    Text("لا توجد بيانات إضافية.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            InformationCard {
                Text("الحالة", style = MaterialTheme.typography.titleMedium)
                Text("الحساب مؤرشف مع حساباته وأرصدته وحركاته ومرفقاته.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    text = "استعادة",
                    onClick = onRestore,
                    modifier = Modifier.weight(1f)
                )
                DangerButton(
                    text = "حذف نهائي",
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    if (showDeleteDialog) {
        ConfirmationDialog(
            title = "حذف نهائي",
            message = "سيتم حذف ${person.name} وجميع حساباته وحركاته ومرفقاته نهائيًا. لا يمكن التراجع عن هذا الإجراء.",
            confirmText = "حذف نهائي",
            danger = true,
            onConfirm = {
                showDeleteDialog = false
                onPermanentDelete()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}
