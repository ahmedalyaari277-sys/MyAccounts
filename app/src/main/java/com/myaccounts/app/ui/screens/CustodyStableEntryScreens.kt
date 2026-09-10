package com.myaccounts.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myaccounts.app.ui.viewmodel.CustodyViewModel

@Composable
fun StableCustodyOperationsEntry(
    vm: CustodyViewModel,
    custodyId: Long,
    onBack: () -> Unit,
    onPerson: (Long) -> Unit
) {
    val custody by vm.custody(custodyId).collectAsStateWithLifecycle()

    if (custody == null) {
        CustodyEntryLoading()
        return
    }

    CustodyOperationsScreen(
        vm = vm,
        custodyId = custodyId,
        onBack = onBack,
        onPerson = onPerson
    )
}

@Composable
fun StableCustodyPersonEntry(
    vm: CustodyViewModel,
    custodyId: Long,
    personId: Long,
    onBack: () -> Unit
) {
    val people by vm.persons(custodyId).collectAsStateWithLifecycle()
    val personExists = people.any { it.id == personId }

    if (!personExists) {
        CustodyEntryLoading()
        return
    }

    CustodyPersonOperationsScreen(
        vm = vm,
        custodyId = custodyId,
        personId = personId,
        onBack = onBack
    )
}

@Composable
private fun CustodyEntryLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "جارٍ تحميل بيانات العهدة…",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
