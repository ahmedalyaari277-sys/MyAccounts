package com.myaccounts.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    onReports: () -> Unit
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
                    TextButton(onClick = onReports) { Text("التقارير") }
                    IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.Sort, "ترتيب العُهَد") }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        DropdownMenuItem(text = { Text("حسب الأحدث") }, onClick = { sortOrder = CustodySortOrder.LATEST; showSortMenu = false })
                        DropdownMenuItem(text = { Text("حسب الأبجدية") }, onClick = { sortOrder = CustodySortOrder.ALPHABETICAL; showSortMenu = false })
                    }
                    IconButton(onClick = onArchive) { Icon(Icons.Default.Archive, "الأرشيف") }
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
                Row(Modifier.fillMaxWidth().clickable { onOpen(custody.id) }.padding(14.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(custody.name, fontWeight = FontWeight.Bold)
                        Text("الجهة: ${custody.organizationName}")
                    }
                }
            }
        }
    }
    if (adding) {
        CustodyCreateDialog(onDismiss = { adding = false }, onSave = { vm.create(it); adding = false })
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
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("اسم صاحب العهدة") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("هاتف صاحب العهدة") }, singleLine = true)
                OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), label = { Text("عنوان صاحب العهدة") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("ملاحظات صاحب العهدة") }, singleLine = true)
                Text("بيانات الجهة", fontWeight = FontWeight.Bold)
                OutlinedTextField(organization, { organization = it }, Modifier.fillMaxWidth(), label = { Text("اسم الجهة") }, singleLine = true)
                OutlinedTextField(organizationPhone, { organizationPhone = it }, Modifier.fillMaxWidth(), label = { Text("هاتف الجهة") }, singleLine = true)
                OutlinedTextField(organizationAddress, { organizationAddress = it }, Modifier.fillMaxWidth(), label = { Text("عنوان الجهة") }, singleLine = true)
                OutlinedTextField(organizationNotes, { organizationNotes = it }, Modifier.fillMaxWidth(), label = { Text("ملاحظات الجهة") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && organization.isNotBlank(),
                onClick = {
                    onSave(CustodyEntity(
                        name = name.trim(), phone = phone.trim(), address = address.trim(), notes = notes.trim(),
                        organizationName = organization.trim(), organizationPhone = organizationPhone.trim(),
                        organizationAddress = organizationAddress.trim(), organizationNotes = organizationNotes.trim()
                    ))
                }
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
