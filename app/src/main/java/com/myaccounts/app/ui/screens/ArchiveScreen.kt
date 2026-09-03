package com.myaccounts.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myaccounts.app.data.local.dao.PersonWithAccounts
import com.myaccounts.app.data.repository.RestorePersonResult
import com.myaccounts.app.ui.components.AppTopBar
import com.myaccounts.app.ui.components.DangerButton
import com.myaccounts.app.ui.components.EmptyState
import com.myaccounts.app.ui.components.EmptyStateType
import com.myaccounts.app.ui.components.InformationCard
import com.myaccounts.app.ui.components.PrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    archivedPersons: List<PersonWithAccounts>,
    onBack: () -> Unit,
    onOpenPerson: (Long) -> Unit,
    onRestorePerson: (Long) -> Unit,
    onPermanentDelete: (Long) -> Unit,
    onClearArchive: () -> Unit,
    restorePersonResult: RestorePersonResult? = null,
    onDismissRestoreResult: () -> Unit = {}
) {
    var personToDelete by remember { mutableStateOf<PersonWithAccounts?>(null) }
    var showClearArchiveDialog by remember { mutableStateOf(false) }
    val hasArchive = archivedPersons.isNotEmpty()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "الأرشيف",
                onBack = onBack,
                actions = {
                    if (hasArchive) {
                        IconButton(onClick = { showClearArchiveDialog = true }) {
                            Icon(Icons.Default.DeleteForever, contentDescription = "إفراغ الأرشيف", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (!hasArchive) {
            EmptyState(
                type = EmptyStateType.People,
                title = "الأرشيف فارغ",
                description = "الحسابات المؤرشفة ستظهر هنا.",
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "الحسابات المؤرشفة",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(archivedPersons, key = { "person_${it.person.id}" }) { person ->
                    ArchivedPersonCard(
                        person = person,
                        onOpen = { onOpenPerson(person.person.id) },
                        onRestore = { onRestorePerson(person.person.id) },
                        onDelete = { personToDelete = person }
                    )
                }
            }
        }
    }

    if (showClearArchiveDialog) {
        AlertDialog(
            onDismissRequest = { showClearArchiveDialog = false },
            title = { Text("إفراغ الأرشيف", style = MaterialTheme.typography.titleLarge) },
            text = { Text("سيتم حذف جميع الحسابات المؤرشفة نهائيًا، بما في ذلك جميع حساباتها المالية وعملياتها ومرفقاتها. لا يمكن التراجع عن هذا الإجراء.", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                DangerButton(
                    text = "إفراغ الأرشيف",
                    onClick = { showClearArchiveDialog = false; onClearArchive() }
                )
            },
            dismissButton = { TextButton(onClick = { showClearArchiveDialog = false }) { Text("إلغاء", style = MaterialTheme.typography.labelLarge) } }
        )
    }

    restorePersonResult?.let { result ->
        val restored = result == RestorePersonResult.RESTORED
        val message = when (result) {
            RestorePersonResult.RESTORED -> "تمت استعادة الحساب بالكامل مع حساباته وعملياته."
            RestorePersonResult.NAME_CONFLICT -> "لا يمكن استعادة الحساب لأن هناك حسابًا نشطًا بنفس الاسم."
            RestorePersonResult.NOT_FOUND -> "الحساب المؤرشف غير موجود."
        }
        AlertDialog(
            onDismissRequest = onDismissRestoreResult,
            title = { Text(if (restored) "تمت الاستعادة" else "تعذر الاستعادة", style = MaterialTheme.typography.titleLarge) },
            text = { Text(message, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = onDismissRestoreResult) { Text("حسنًا", style = MaterialTheme.typography.labelLarge) } }
        )
    }

    personToDelete?.let { person ->
        AlertDialog(
            onDismissRequest = { personToDelete = null },
            title = { Text("حذف نهائي", style = MaterialTheme.typography.titleLarge) },
            text = { Text("سيتم حذف ${person.person.name} وجميع حساباته وحركاته ومرفقاته نهائيًا. لا يمكن التراجع عن هذا الإجراء.", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                DangerButton(
                    text = "حذف نهائي",
                    onClick = { onPermanentDelete(person.person.id); personToDelete = null }
                )
            },
            dismissButton = { TextButton(onClick = { personToDelete = null }) { Text("إلغاء", style = MaterialTheme.typography.labelLarge) } }
        )
    }
}

@Composable
private fun ArchivedPersonCard(
    person: PersonWithAccounts,
    onOpen: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    InformationCard(modifier = Modifier.fillMaxWidth()) {
        Text(person.person.name, style = MaterialTheme.typography.titleLarge)
        if (person.person.phone.isNotBlank()) Text("الهاتف: ${person.person.phone}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (person.person.address.isNotBlank()) Text("العنوان: ${person.person.address}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("الحسابات: ${person.accounts.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(text = "فتح", onClick = onOpen, modifier = Modifier.weight(1f))
            PrimaryButton(text = "استعادة", onClick = onRestore, modifier = Modifier.weight(1f))
            DangerButton(text = "حذف", onClick = onDelete, modifier = Modifier.weight(1f))
        }
    }
}
