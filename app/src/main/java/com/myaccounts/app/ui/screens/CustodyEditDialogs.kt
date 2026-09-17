package com.myaccounts.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.unit.dp
import com.myaccounts.app.data.custody.CustodyEntity
import com.myaccounts.app.data.custody.CustodyPersonEntity
import com.myaccounts.app.ui.viewmodel.CustodyViewModel
import kotlinx.coroutines.launch

@Composable
private fun Modifier.keepFocusedFieldVisible(): Modifier {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return this.bringIntoViewRequester(bringIntoViewRequester).onFocusEvent { state -> if (state.isFocused) scope.launch { bringIntoViewRequester.bringIntoView() } }
}

@Composable
fun CustodyPersonEditDialog(vm: CustodyViewModel, person: CustodyPersonEntity, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var name by remember { mutableStateOf(person.name) }; var partyType by remember { mutableStateOf(person.partyType) }; var phone by remember { mutableStateOf(person.phone) }; var address by remember { mutableStateOf(person.address) }; var notes by remember { mutableStateOf(person.notes) }; var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("تعديل بيانات الطرف") }, text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("نوع الطرف", fontWeight = FontWeight.Bold); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(selected = partyType == "PERSON", onClick = { partyType = "PERSON" }, label = { Text("شخص") }); FilterChip(selected = partyType == "ENTITY", onClick = { partyType = "ENTITY" }, label = { Text("جهة") }) }
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().keepFocusedFieldVisible(), label = { Text("الاسم") }, singleLine = true, enabled = !saving); OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth().keepFocusedFieldVisible(), label = { Text("الهاتف") }, singleLine = true, enabled = !saving); OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth().keepFocusedFieldVisible(), label = { Text("العنوان") }, singleLine = true, enabled = !saving); OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth().keepFocusedFieldVisible(), label = { Text("الملاحظات") }, minLines = 2, enabled = !saving); error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { Button(enabled = name.isNotBlank() && !saving, onClick = { saving = true; scope.launch { runCatching { vm.updatePersonAndWait(person.copy(name = name.trim(), phone = phone.trim(), address = address.trim(), notes = notes.trim(), partyType = partyType)) }.onSuccess { saving = false; onSaved() }.onFailure { saving = false; error = it.message ?: "تعذر حفظ التعديل" } } }) { Text(if (saving) "جارٍ الحفظ…" else "حفظ") } }, dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("إلغاء") } })
}

@Composable
fun CustodyOwnerEditDialog(vm: CustodyViewModel, custody: CustodyEntity, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var holderName by remember { mutableStateOf(custody.holderName.ifBlank { custody.name }) }; var phone by remember { mutableStateOf(custody.phone) }; var address by remember { mutableStateOf(custody.address) }; var notes by remember { mutableStateOf(custody.notes) }; var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("تعديل بيانات حامل العهدة") }, text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(holderName, { holderName = it }, Modifier.fillMaxWidth().keepFocusedFieldVisible(), label = { Text("اسم حامل العهدة") }, singleLine = true, enabled = !saving); OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth().keepFocusedFieldVisible(), label = { Text("الهاتف") }, singleLine = true, enabled = !saving); OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth().keepFocusedFieldVisible(), label = { Text("العنوان") }, singleLine = true, enabled = !saving); OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth().keepFocusedFieldVisible(), label = { Text("الملاحظات") }, minLines = 2, enabled = !saving); error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { Button(enabled = holderName.isNotBlank() && !saving, onClick = { saving = true; scope.launch { runCatching { vm.updateCustodyAndWait(custody.copy(holderName = holderName.trim(), phone = phone.trim(), address = address.trim(), notes = notes.trim())) }.onSuccess { saving = false; onSaved() }.onFailure { saving = false; error = it.message ?: "تعذر حفظ التعديل" } } }) { Text(if (saving) "جارٍ الحفظ…" else "حفظ") } }, dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("إلغاء") } })
}

@Composable
fun CustodyDataEditDialog(vm: CustodyViewModel, custody: CustodyEntity, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var custodyName by remember { mutableStateOf(custody.name) }; var purpose by remember { mutableStateOf(custody.purpose) }; var organization by remember { mutableStateOf(custody.organizationName) }; var organizationPhone by remember { mutableStateOf(custody.organizationPhone) }; var organizationAddress by remember { mutableStateOf(custody.organizationAddress) }; var organizationNotes by remember { mutableStateOf(custody.organizationNotes) }; var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("تفاصيل العهدة") }, text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("بيانات العهدة", fontWeight = FontWeight.Bold)
        OutlinedTextField(custodyName, { custodyName = it }, Modifier.fillMaxWidth().keepFocusedFieldVisible(), label = { Text("اسم العهدة") }, supportingText = { Text("اسم فريد للعهدة") }, singleLine = true, enabled = !saving)
        OutlinedTextField(purpose, { purpose = it }, Modifier.fillMaxWidth().keepFocusedFieldVisible(), label = { Text("الغرض من العهدة") }, minLines = 2, enabled = !saving)
        HorizontalDivider()
        Text("بيانات الجهة", fontWeight = FontWeight.Bold)
        OutlinedTextField(organization, { organization = it }, Modifier.fillMaxWidth().keepFocusedFieldVisible(), label = { Text("اسم الجهة") }, singleLine = true, enabled = !saving); OutlinedTextField(organizationPhone, { organizationPhone = it }, Modifier.fillMaxWidth().keepFocusedFieldVisible(), label = { Text("هاتف الجهة") }, singleLine = true, enabled = !saving); OutlinedTextField(organizationAddress, { organizationAddress = it }, Modifier.fillMaxWidth().keepFocusedFieldVisible(), label = { Text("عنوان الجهة") }, singleLine = true, enabled = !saving); OutlinedTextField(organizationNotes, { organizationNotes = it }, Modifier.fillMaxWidth().keepFocusedFieldVisible(), label = { Text("ملاحظات الجهة") }, minLines = 2, enabled = !saving); error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { Button(enabled = custodyName.isNotBlank() && organization.isNotBlank() && !saving, onClick = { saving = true; scope.launch { runCatching { vm.updateCustodyAndWait(custody.copy(name = custodyName.trim(), purpose = purpose.trim(), organizationName = organization.trim(), organizationPhone = organizationPhone.trim(), organizationAddress = organizationAddress.trim(), organizationNotes = organizationNotes.trim())) }.onSuccess { saving = false; onSaved() }.onFailure { saving = false; error = it.message ?: "تعذر حفظ التعديل" } } }) { Text(if (saving) "جارٍ الحفظ…" else "حفظ") } }, dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("إلغاء") } })
}
