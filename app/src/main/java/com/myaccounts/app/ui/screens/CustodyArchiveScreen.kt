package com.myaccounts.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myaccounts.app.data.custody.CustodyEntity
import com.myaccounts.app.ui.components.AppTopBar
import com.myaccounts.app.ui.components.ConfirmationDialog
import com.myaccounts.app.ui.components.DangerButton
import com.myaccounts.app.ui.components.EmptyState
import com.myaccounts.app.ui.components.EmptyStateType
import com.myaccounts.app.ui.components.InformationCard
import com.myaccounts.app.ui.components.PrimaryButton
import com.myaccounts.app.ui.viewmodel.CustodyViewModel
import kotlinx.coroutines.flow.first

@Composable
fun CustodyArchiveScreen(vm: CustodyViewModel, onBack: () -> Unit) {
    var archived by remember { mutableStateOf<List<CustodyEntity>>(emptyList()) }
    var pendingDelete by remember { mutableStateOf<CustodyEntity?>(null) }
    LaunchedEffect(Unit) { archived = vm.archivedCustodies().first() }
    androidx.compose.material3.Scaffold(topBar = { AppTopBar(title = "أرشيف العُهَد", onBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (archived.isEmpty()) {
                item {
                    EmptyState(type = EmptyStateType.Custody, title = "أرشيف العُهَد فارغ", description = "لا توجد عُهَد مؤرشفة حاليًا.")
                }
            }
            items(archived, key = { it.id }) { custody ->
                InformationCard {
                    Text(custody.name, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("الجهة: ${custody.organizationName}", style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton(text = "استعادة", onClick = { vm.restore(custody.id); archived = archived.filterNot { it.id == custody.id } }, modifier = Modifier.weight(1f))
                        DangerButton(text = "حذف نهائي", onClick = { pendingDelete = custody }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
    pendingDelete?.let { custody ->
        ConfirmationDialog(
            title = "حذف العهدة نهائيًا",
            message = "سيتم حذف العهدة وجميع الأشخاص والحسابات والعمليات المرتبطة بها. لا يمكن التراجع عن ذلك.",
            confirmText = "حذف نهائي",
            danger = true,
            onConfirm = { vm.deleteCustody(custody.id); archived = archived.filterNot { it.id == custody.id }; pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }
}
