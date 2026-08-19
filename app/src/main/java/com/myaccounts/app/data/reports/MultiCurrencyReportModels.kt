package com.myaccounts.app.data.reports

data class CurrencyReportPersonRowWithCurrency(
    val currencyCode: String,
    val personId: Long,
    val personName: String,
    val totalReceivableMinor: Long,
    val totalPayableMinor: Long,
    val balanceMinor: Long,
    val transactionCount: Int
)

data class MultiCurrencyPersonReport(
    val personId: Long,
    val personName: String,
    val phone: String,
    val address: String,
    val reports: List<PersonCurrencyReport>
)

data class PersonCurrencyReport(
    val currencyCode: String,
    val summary: PersonReportSummary,
    val transactions: List<PersonReportTransaction>
)
