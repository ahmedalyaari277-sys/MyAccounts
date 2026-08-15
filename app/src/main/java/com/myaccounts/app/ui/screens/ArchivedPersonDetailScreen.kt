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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
        topBar = {
            TopAppBar(
                title = { Text(person.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text("شخص مؤرشف", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (person.phone.isNotBlank()) Text("الهاتف: ${person.phone}")
            if (person.address.isNotBlank()) Text("العنوان: ${person.address}")
            if (person.notes.isNotBlank()) Text("الملاحظات: ${person.notes}")
            Spacer(Modifier.height(20.dp))
            Text(
                "الحسابات محفوظة مع جميع أرصدتها وحركاتها.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onRestore, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Text("استعادة")
                }
                Button(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Text("حذف نهائي")
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("حذف نهائي") },
            text = {
                Text(
                    "سيتم حذف ${person.name} وجميع حساباته وحركاته نهائيًا. لا يمكن التراجع عن هذا الإجراء."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showDeleteDialog = false
                    onPermanentDelete()
                }) {
                    Text("حذف نهائي")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
