package com.myaccounts.app.data.reports

import kotlinx.coroutines.flow.Flow

class ReportRepository(
    private val reportDao: ReportDao
) {

    fun observeCurrencyReportPeople(
        currencyCode: String
    ): Flow<List<CurrencyReportPersonRow>> {
        return reportDao.observeCurrencyReportPeople(currencyCode)
    }

    suspend fun getCurrencyReportSummary(
        currencyCode: String
    ): CurrencyReportSummary {
        return reportDao.getCurrencyReportSummary(currencyCode)
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
}
