package com.myaccounts.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.myaccounts.app.ui.viewmodel.CustodyViewModel

@Composable
fun CustodyPersonOperationsScreen(
    vm: CustodyViewModel,
    custodyId: Long,
    personId: Long,
    onBack: () -> Unit
) {
    val people by vm.persons(custodyId).collectAsState()
    val person = people.firstOrNull { it.id == personId }
    if (person?.partyType == "ENTITY") {
        CustodyOrganizationOperationsScreen(vm = vm, custodyId = custodyId, personId = personId, onBack = onBack)
    } else {
        CustodyPersonLedgerScreen(vm = vm, custodyId = custodyId, personId = personId, onBack = onBack)
    }
}
