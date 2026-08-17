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
    val transactionDate: Long,
    val type: String,
    val amountMinor: Long,
    val description: String,
    val balanceMinor: Long
)

data class PersonReportTransactionRow(
    val transactionId: Long,
    val transactionDate: Long,
    val type: String,
    val amountMinor: Long,
    val description: String,
    val balanceMinor: Long
)

data class GeneralReportTransactionRow(
    val transactionId: Long,
    val transactionDate: Long,
    val personName: String,
    val currencyCode: String,
    val description: String,
    val type: String,
    val amountMinor: Long
)

data class PersonCurrencySummaryRow(
    val personId: Long,
    val personName: String,
    val currencyCode: String,
    val totalReceivableMinor: Long,
    val totalPayableMinor: Long,
    val balanceMinor: Long,
    val firstReceivableDate: Long?,
    val lastReceivableDate: Long?,
    val firstPayableDate: Long?,
    val lastPayableDate: Long?,
    val transactionCount: Int
)

data class ReportDateRange(
    val startDateMillis: Long?,
    val endDateMillisExclusive: Long?
)
