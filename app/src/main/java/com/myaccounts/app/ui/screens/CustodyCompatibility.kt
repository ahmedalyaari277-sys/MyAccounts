package com.myaccounts.app.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll as foundationHorizontalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight as ComposeFontWeight
import com.myaccounts.app.ui.viewmodel.CustodyViewModel

/**
 * Compatibility shims for the custody-only UI. They keep older custody call sites
 * source-compatible while the new party/category model is rolled out incrementally.
 * No ledger/accounting code is changed.
 */

typealias FontWeight = ComposeFontWeight

fun Modifier.horizontalScroll(
    state: ScrollState,
    reverseScrolling: Boolean = false
): Modifier = foundationHorizontalScroll(this, state, reverseScrolling)

fun CustodyViewModel.addTransaction(
    id: Long,
    currency: String,
    type: String,
    personId: Long?,
    amount: Long,
    description: String,
    date: Long
) = addTransaction(id, currency, type, personId, amount, "", description, date)
