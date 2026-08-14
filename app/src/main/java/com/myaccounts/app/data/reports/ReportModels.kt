package com.myaccounts.app.data.reports

data class CurrencyReportPersonRow(
val personId: Long,
val personName: String,
val totalReceivableMinor: Long,
val totalPayableMinor: Long,
val balanceMinor: Long,
val transactionCount: Int
)

data class CurrencyReportSummary(
val currencyCode: String,
val totalReceivableMinor: Long,
val totalPayableMinor: Long,
val balanceMinor: Long,
val transactionCount: Int
)

data class PersonReportSummary(
val personId: Long,
val personName: String,
val currencyCode: String,
val openingBalanceMinor: Long,
val periodReceivableMinor: Long,
val periodPayableMinor: Long,
val periodBalanceMinor: Long,
val closingBalanceMinor: Long,
val transactionCount: Int
)

data class PersonReportTransaction(
val transactionId: Long,
val accountId: Long,
val transactionDate: Long,
val type: String,
val amountMinor: Long,
val description: String
)

data class ReportDateRange(
val startDateMillis: Long?,
val endDateMillisExclusive: Long?
)
