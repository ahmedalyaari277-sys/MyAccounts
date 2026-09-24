package com.myaccounts.app.data.custody

data class CustodySearchResult(
    val kind: String,
    val custodyId: Long,
    val personId: Long?,
    val transactionId: Long?,
    val currencyCode: String?,
    val title: String,
    val subtitle: String,
    val amountMinor: Long?,
    val transactionDate: Long?
)
