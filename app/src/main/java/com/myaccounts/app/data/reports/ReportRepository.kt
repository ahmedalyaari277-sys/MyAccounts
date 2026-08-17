package com.myaccounts.app.data.reports

import kotlinx.coroutines.flow.Flow

class ReportRepository(
    private val reportDao: ReportDao
) {

    fun observeCurrencyReportPeople(
        currencyCode: String,
        startDateMillis: Long,
        endDateMillisExclusive: Long
    ): Flow<List<CurrencyReportPersonRow>> {
        return reportDao.observeCurrencyReportPeople(
            currencyCode = currencyCode,
            startDateMillis = startDateMillis,
            endDateMillisExclusive = endDateMillisExclusive
        )
    }

    suspend fun getCurrencyReportSummary(
        currencyCode: String,
        startDateMillis: Long,
        endDateMillisExclusive: Long
    ): CurrencyReportSummary {
        return reportDao.getCurrencyReportSummary(
            currencyCode = currencyCode,
            startDateMillis = startDateMillis,
            endDateMillisExclusive = endDateMillisExclusive
        )
    }

    suspend fun getPersonReportSummary(
        personId: Long,
        currencyCode: String,
        startDateMillis: Long,
        endDateMillisExclusive: Long
    ): PersonReportSummary {
        return reportDao.getPersonReportSummary(
            personId = personId,
            currencyCode = currencyCode,
            startDateMillis = startDateMillis,
            endDateMillisExclusive = endDateMillisExclusive
        )
    }

    suspend fun getPersonOpeningBalance(
        personId: Long,
        currencyCode: String,
        startDateMillis: Long
    ): Long {
        return reportDao.getPersonOpeningBalance(
            personId = personId,
            currencyCode = currencyCode,
            startDateMillis = startDateMillis
        )
    }

    suspend fun getPersonReportTransactions(
        personId: Long,
        currencyCode: String,
        startDateMillis: Long,
        endDateMillisExclusive: Long
    ): List<PersonReportTransaction> {
        return reportDao.getPersonReportTransactions(
            personId = personId,
            currencyCode = currencyCode,
            startDateMillis = startDateMillis,
            endDateMillisExclusive = endDateMillisExclusive
        )
    }

    suspend fun getPersonReportTransactionRows(
        personId: Long,
        currencyCode: String,
        startDateMillis: Long,
        endDateMillisExclusive: Long
    ): List<PersonReportTransactionRow> {
        return reportDao.getPersonReportTransactionRows(
            personId = personId,
            currencyCode = currencyCode,
            startDateMillis = startDateMillis,
            endDateMillisExclusive = endDateMillisExclusive
        )
    }

    suspend fun getPersonCurrencySummary(
        currencyCode: String,
        startDateMillis: Long,
        endDateMillisExclusive: Long
    ): List<PersonCurrencySummaryRow> {
        return reportDao.getPersonCurrencySummary(
            currencyCode = currencyCode,
            startDateMillis = startDateMillis,
            endDateMillisExclusive = endDateMillisExclusive
        )
    }

    suspend fun getGeneralReportTransactions(
        currencyCode: String,
        startDateMillis: Long,
        endDateMillisExclusive: Long
    ): List<GeneralReportTransactionRow> {
        return reportDao.getGeneralReportTransactions(
            currencyCode = currencyCode,
            startDateMillis = startDateMillis,
            endDateMillisExclusive = endDateMillisExclusive
        )
    }
}
