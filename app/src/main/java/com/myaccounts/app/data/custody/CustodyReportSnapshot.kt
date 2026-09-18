package com.myaccounts.app.data.custody

data class CustodyReportSnapshot(
    val custody: CustodyEntity,
    val people: List<CustodyPersonEntity>,
    val transactions: List<CustodyTransactionEntity>
)
