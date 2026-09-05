package com.myaccounts.app.ui.screens

import androidx.compose.runtime.Composable
import com.myaccounts.app.ui.viewmodel.CustodyViewModel

@Composable
fun CustodyPersonOperationsScreen(
    vm: CustodyViewModel,
    custodyId: Long,
    personId: Long,
    onBack: () -> Unit
) {
    CustodyPersonLedgerScreen(
        vm = vm,
        custodyId = custodyId,
        personId = personId,
        onBack = onBack
    )
}
