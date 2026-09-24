package com.myaccounts.app.data.local.dao

data class LedgerSearchResult(
    val kind: String,
    val personId: Long,
    val accountId: Long?,
    val transactionId: Long?,
    val currencyCode: String?,
    val title: String,
    val subtitle: String,
    val amountMinor: Long?,
    val transactionDate: Long?
)
