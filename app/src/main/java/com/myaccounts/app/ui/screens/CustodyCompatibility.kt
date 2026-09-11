package com.myaccounts.app.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll as foundationHorizontalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight as ComposeFontWeight
import com.myaccounts.app.ui.viewmodel.CustodyViewModel

typealias FontWeight = ComposeFontWeight

fun Modifier.horizontalScroll(
    state: ScrollState,
    reverseScrolling: Boolean = false
): Modifier = this.foundationHorizontalScroll(state, reverseScrolling)

fun CustodyViewModel.addTransaction(
    id: Long,
    currency: String,
    type: String,
    personId: Long?,
    amount: Long,
    description: String,
    date: Long
) = addTransaction(id, currency, type, personId, amount, "", description, date)