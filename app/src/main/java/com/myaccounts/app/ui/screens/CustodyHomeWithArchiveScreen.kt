package com.myaccounts.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myaccounts.app.data.custody.CustodyEntity
import com.myaccounts.app.ui.viewmodel.CustodyViewModel

private enum class CustodySortOrder { LATEST, ALPHABETICAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustodyHomeWithArchiveScreen(
    vm: CustodyViewModel,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onArchive: () -> Unit,
    onReports: () -> Unit,
    onTransfer: () -> Unit
) {
    val custodies by vm.custodies.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf(CustodySortOrder.LATEST) }
    val displayedCustodies = when (sortOrder) {
        CustodySortOrder.LATEST -> custodies
        CustodySortOrder.ALPHABETICAL -> custodies.sortedBy { it.name.trim().lowercase() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("العُهَد") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
                actions = {
                    TextButton(onClick = onReports) { Text("التقارير", fontWeight = FontWeight.Bold) }
                    IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.Sort, "ترتيب العُهَد") }
                    IconButton(
                        onClick = onTransfer,
                        modifier = Modifier.semantics { contentDescription = "النسخ الاحتياطي والاستعادة" }
                    ) { Icon(Icons.Default.Backup, contentDescription = null) }
                    IconButton(onClick = onArchive) { Icon(Icons.Default.Archive, "الأرشيف") }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        DropdownMenuItem(text = { Text("حسب الأحدث") }, onClick = { sortOrder = CustodySortOrder.LATEST; showSortMenu = false })
                        DropdownMenuItem(text = { Text("حسب الأبجدية") }, onClick = { sortOrder = CustodySortOrder.ALPHABETICAL; showSortMenu = false })
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Default.Add, "إضافة عهدة") }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(displayedCustodies, key = { it.id }) { custody ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(custody.id) }.padding(14.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(custody.name, fontWeight = FontWeight.Bold)
                        Text("الجهة: ${custody.organizationName}")
                    }
                }
            }
        }
    }

    if (adding) {
        CustodyCreateDialog(
            onDismiss = { adding = false },
            onSave = { vm.create(it); adding = false }
        )
    }
}

@Composable
private fun CustodyCreateDialog(onDismiss: () -> Unit, onSave: (CustodyEntity) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var organization by remember { mutableStateOf("") }
    var organizationPhone by remember { mutableStateOf("") }
    var organizationAddress by remember { mutableStateOf("") }
    var organizationNotes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة صاحب عهدة") },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("اسم صاحب العهدة") }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, modifier = Modifier.fillMaxWidth(), label = { Text("هاتف صاحب العهدة") }, singleLine = true)
                OutlinedTextField(value = address, onValueChange = { address = it }, modifier = Modifier.fillMaxWidth(), label = { Text("عنوان صاحب العهدة") }, singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ملاحظات صاحب العهدة") }, singleLine = true)
                Text("بيانات الجهة", fontWeight = FontWeight.Bold)
                OutlinedTextField(value = organization, onValueChange = { organization = it }, modifier = Modifier.fillMaxWidth(), label = { Text("اسم الجهة") }, singleLine = true)
                OutlinedTextField(value = organizationPhone, onValueChange = { organizationPhone = it }, modifier = Modifier.fillMaxWidth(), label = { Text("هاتف الجهة") }, singleLine = true)
                OutlinedTextField(value = organizationAddress, onValueChange = { organizationAddress = it }, modifier = Modifier.fillMaxWidth(), label = { Text("عنوان الجهة") }, singleLine = true)
                OutlinedTextField(value = organizationNotes, onValueChange = { organizationNotes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ملاحظات الجهة") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && organization.isNotBlank(),
                onClick = {
                    onSave(
                        CustodyEntity(
                            name = name.trim(),
                            phone = phone.trim(),
                            address = address.trim(),
                            notes = notes.trim(),
                            organizationName = organization.trim(),
                            organizationPhone = organizationPhone.trim(),
                            organizationAddress = organizationAddress.trim(),
                            organizationNotes = organizationNotes.trim()
                        )
                    )
                }
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
