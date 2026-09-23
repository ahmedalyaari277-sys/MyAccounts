package com.myaccounts.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusEvent
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.myaccounts.app.data.custody.CustodyEntity
import com.myaccounts.app.ui.components.AppTopBar
import com.myaccounts.app.ui.components.BalanceStatus
import com.myaccounts.app.ui.components.EmptyState
import com.myaccounts.app.ui.components.EmptyStateType
import com.myaccounts.app.ui.components.InformationCard
import com.myaccounts.app.ui.components.SearchField
import com.myaccounts.app.ui.theme.EntityName
import com.myaccounts.app.ui.theme.EntityNameDark
import com.myaccounts.app.ui.viewmodel.CustodyViewModel

private val custodyHomeCurrencies = listOf("YER", "SAR", "USD")
private enum class CustodySortOrder { LATEST_TRANSACTION, ALPHABETICAL }

@Composable
private fun Modifier.custodyKeepFocusedFieldVisible(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return bringIntoViewRequester(requester).onFocusEvent { if (it.isFocused) scope.launch { kotlinx.coroutines.delay(180); requester.bringIntoView() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustodyHomeWithArchiveScreen(vm: CustodyViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit, onArchive: () -> Unit, onReports: () -> Unit, onBackupRestore: () -> Unit, onTransfer: () -> Unit) {
    val custodies by vm.custodies.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf(CustodySortOrder.LATEST_TRANSACTION) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val displayedCustodies = when (sortOrder) { CustodySortOrder.LATEST_TRANSACTION -> custodies; CustodySortOrder.ALPHABETICAL -> custodies.sortedBy { it.name.trim().lowercase() } }

    Scaffold(topBar = {
        AppTopBar(title = "العُهَد", onBack = onBack, actions = {
            IconButton(onClick = onReports) { Icon(Icons.Default.Assessment, "التقارير") }
            Box {
                IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.Sort, "ترتيب العُهَد") }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    DropdownMenuItem(text = { Text("حسب أحدث عملية") }, onClick = { sortOrder = CustodySortOrder.LATEST_TRANSACTION; showSortMenu = false })
                    DropdownMenuItem(text = { Text("حسب الأبجدية") }, onClick = { sortOrder = CustodySortOrder.ALPHABETICAL; showSortMenu = false })
                }
            }
            IconButton(onClick = { showMoreMenu = true }) { Icon(Icons.Default.MoreVert, "المزيد من الخيارات") }
            DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                DropdownMenuItem(text = { Text("النسخ الاحتياطي و الاستعادة") }, leadingIcon = { Icon(Icons.Default.Backup, null) }, onClick = { showMoreMenu = false; onBackupRestore() })
                DropdownMenuItem(text = { Text("الأرشيف") }, leadingIcon = { Icon(Icons.Default.Archive, null) }, onClick = { showMoreMenu = false; onArchive() })
            }
        })
    }, floatingActionButton = {
        FloatingActionButton(onClick = { adding = true }, modifier = Modifier.padding(16.dp).size(56.dp), containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = MaterialTheme.shapes.large) { Icon(Icons.Default.Add, "إضافة عهدة") }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it; vm.setSearchQuery(it) },
                placeholder = "بحث في العُهَد: الاسم، المبلغ، التاريخ، التفاصيل أو أي بيانات",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (custodies.isEmpty()) item { EmptyState(type = EmptyStateType.Custody, title = "لا توجد عُهَد", description = "أضف أول عهدة للبدء في متابعة أصحاب العُهَد والعمليات المالية.") }
            items(displayedCustodies, key = { it.id }) { custody ->
                val accounts by vm.accounts(custody.id).collectAsState(initial = emptyList())
                val entityNameColor = MaterialTheme.colorScheme.primary
                InformationCard(Modifier.fillMaxWidth().clickable { onOpen(custody.id) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(custody.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = entityNameColor)
                            Text("حامل العهدة: ${custody.holderName.ifBlank { custody.name }}", style = MaterialTheme.typography.bodySmall, color = entityNameColor)
                            Text("الجهة: ${custody.organizationName}", style = MaterialTheme.typography.bodySmall)
                            if (custody.purpose.isNotBlank()) Text("الغرض: ${custody.purpose}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            custodyHomeCurrencies.forEach { code ->
                                val balance = accounts.firstOrNull { it.holderType == "OWNER" && it.personId == null && it.currencyCode == code }?.balanceMinor ?: 0L
                                val status = when { balance > 0L -> BalanceStatus.Due; balance < 0L -> BalanceStatus.Owed; else -> BalanceStatus.Neutral }
                                val color = when (status) { BalanceStatus.Due -> com.myaccounts.app.ui.theme.Due; BalanceStatus.Owed -> com.myaccounts.app.ui.theme.Owed; BalanceStatus.Neutral -> com.myaccounts.app.ui.theme.Neutral }
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.small,
                                    tonalElevation = 1.dp,
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        Text(code, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(when { balance > 0 -> "عليه ${balance / 100.0}"; balance < 0 -> "له ${(-balance) / 100.0}"; else -> "متوازن 0" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
    if (adding) CustodyCreateDialog(onDismiss = { adding = false }) { vm.create(it); adding = false }
}

@Composable
private fun CustodyCreateDialog(onDismiss: () -> Unit, onSave: (CustodyEntity) -> Unit) {
    var custodyName by remember { mutableStateOf("") }
    var holderName by remember { mutableStateOf("") }
    var holderPhone by remember { mutableStateOf("") }
    var holderAddress by remember { mutableStateOf("") }
    var holderNotes by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }
    var organization by remember { mutableStateOf("") }
    var organizationPhone by remember { mutableStateOf("") }
    var organizationAddress by remember { mutableStateOf("") }
    var organizationNotes by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        androidx.compose.material3.Surface(modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f).imePadding().navigationBarsPadding(), shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.fillMaxSize()) {
                Text("إضافة عهدة", modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                HorizontalDivider()
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("بيانات العهدة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(custodyName, { custodyName = it }, Modifier.fillMaxWidth().custodyKeepFocusedFieldVisible(), label = { Text("اسم العهدة") }, supportingText = { Text("اسم مميز للعهدة ولا يتكرر") }, singleLine = true)
                    OutlinedTextField(purpose, { purpose = it }, Modifier.fillMaxWidth().custodyKeepFocusedFieldVisible(), label = { Text("الغرض من العهدة") }, minLines = 2)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text("بيانات حامل العهدة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(holderName, { holderName = it }, Modifier.fillMaxWidth().custodyKeepFocusedFieldVisible(), label = { Text("اسم حامل العهدة") }, supportingText = { Text("يمكن أن يكون حامل العهدة نفسه في أكثر من عهدة") }, singleLine = true)
                    OutlinedTextField(holderPhone, { holderPhone = it }, Modifier.fillMaxWidth().custodyKeepFocusedFieldVisible(), label = { Text("هاتف حامل العهدة") }, singleLine = true)
                    OutlinedTextField(holderAddress, { holderAddress = it }, Modifier.fillMaxWidth().custodyKeepFocusedFieldVisible(), label = { Text("عنوان حامل العهدة") }, singleLine = true)
                    OutlinedTextField(holderNotes, { holderNotes = it }, Modifier.fillMaxWidth().custodyKeepFocusedFieldVisible(), label = { Text("ملاحظات حامل العهدة") }, minLines = 2)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text("بيانات جهة العهدة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(organization, { organization = it }, Modifier.fillMaxWidth().custodyKeepFocusedFieldVisible(), label = { Text("اسم الجهة") }, singleLine = true)
                    OutlinedTextField(organizationPhone, { organizationPhone = it }, Modifier.fillMaxWidth().custodyKeepFocusedFieldVisible(), label = { Text("هاتف الجهة") }, singleLine = true)
                    OutlinedTextField(organizationAddress, { organizationAddress = it }, Modifier.fillMaxWidth().custodyKeepFocusedFieldVisible(), label = { Text("عنوان الجهة") }, singleLine = true)
                    OutlinedTextField(organizationNotes, { organizationNotes = it }, Modifier.fillMaxWidth().custodyKeepFocusedFieldVisible(), label = { Text("ملاحظات الجهة") }, minLines = 2)
                    Spacer(Modifier.height(12.dp))
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("إلغاء") }
                    Spacer(Modifier.width(8.dp))
                    Button(enabled = custodyName.isNotBlank() && holderName.isNotBlank() && organization.isNotBlank(), onClick = { onSave(CustodyEntity(name = custodyName.trim(), holderName = holderName.trim(), phone = holderPhone.trim(), address = holderAddress.trim(), notes = holderNotes.trim(), purpose = purpose.trim(), organizationName = organization.trim(), organizationPhone = organizationPhone.trim(), organizationAddress = organizationAddress.trim(), organizationNotes = organizationNotes.trim())) }) { Text("حفظ") }
                }
            }
        }
    }
}
