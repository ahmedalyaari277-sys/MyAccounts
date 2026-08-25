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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myaccounts.app.data.local.dao.PersonWithAccounts
import com.myaccounts.app.data.repository.RestorePersonResult

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
            TopAppBar(
                title = { Text("الأرشيف", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.primary),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } },
                actions = { if (hasArchive) IconButton(onClick = { showClearArchiveDialog = true }) { Icon(Icons.Default.DeleteForever, contentDescription = "إفراغ الأرشيف", tint = MaterialTheme.colorScheme.error) } }
            )
        }
    ) { paddingValues ->
        if (!hasArchive) {
            Column(Modifier.fillMaxSize().padding(paddingValues).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(48.dp))
                Spacer(Modifier.height(10.dp)); Text("الأرشيف فارغ", fontSize = 20.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp))
                Text("الحسابات المؤرشفة ستظهر هنا.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("الحسابات المؤرشفة", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                items(archivedPersons, key = { "person_${it.person.id}" }) { person ->
                    ArchivedPersonCard(person, onOpen = { onOpenPerson(person.person.id) }, onRestore = { onRestorePerson(person.person.id) }, onDelete = { personToDelete = person })
                }
            }
        }
    }

    if (showClearArchiveDialog) {
        AlertDialog(
            onDismissRequest = { showClearArchiveDialog = false },
            title = { Text("إفراغ الأرشيف") },
            text = { Text("سيتم حذف جميع الحسابات المؤرشفة نهائيًا، بما في ذلك جميع حساباتها المالية وعملياتها ومرفقاتها. لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = { Button(onClick = { showClearArchiveDialog = false; onClearArchive() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("إفراغ الأرشيف") } },
            dismissButton = { TextButton(onClick = { showClearArchiveDialog = false }) { Text("إلغاء") } }
        )
    }

    if (restorePersonResult != null) {
        val restored = restorePersonResult == RestorePersonResult.RESTORED
        val message = when (restorePersonResult) {
            RestorePersonResult.RESTORED -> "تمت استعادة الحساب بالكامل مع حساباته وعملياته."
            RestorePersonResult.NAME_CONFLICT -> "لا يمكن استعادة الحساب لأن هناك حسابًا نشطًا بنفس الاسم."
            RestorePersonResult.NOT_FOUND -> "الحساب المؤرشف غير موجود."
        }
        AlertDialog(onDismissRequest = onDismissRestoreResult, title = { Text(if (restored) "تمت الاستعادة" else "تعذر الاستعادة") }, text = { Text(message) }, confirmButton = { TextButton(onClick = onDismissRestoreResult) { Text("حسنًا") } })
    }

    personToDelete?.let { person ->
        AlertDialog(
            onDismissRequest = { personToDelete = null },
            title = { Text("حذف نهائي") },
            text = { Text("سيتم حذف ${person.person.name} وجميع حساباته وحركاته ومرفقاته نهائيًا. لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = { Button(onClick = { onPermanentDelete(person.person.id); personToDelete = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("حذف نهائي") } },
            dismissButton = { TextButton(onClick = { personToDelete = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun ArchivedPersonCard(person: PersonWithAccounts, onOpen: () -> Unit, onRestore: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = MaterialTheme.shapes.medium, elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(person.person.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            if (person.person.phone.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(person.person.phone, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (person.person.address.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text("العنوان: ${person.person.address}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(8.dp)); Text("الحسابات: ${person.accounts.size}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRestore, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Restore, contentDescription = null); Spacer(Modifier.padding(horizontal = 2.dp)); Text("استعادة") }
                Button(onClick = onDelete, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.DeleteForever, contentDescription = null); Spacer(Modifier.padding(horizontal = 2.dp)); Text("حذف نهائي") }
            }
        }
    }
}
