package com.myaccounts.app.data.reports

import kotlinx.coroutines.flow.Flow

class ReportRepository(
    private val reportDao: ReportDao
) {
    companion object {
        val SUPPORTED_CURRENCIES = listOf("YER", "SAR", "USD")
    }

    fun observeCurrencyReportPeople(
        currencyCode: String,
        startDateMillis: Long,
        endDateMillisExclusive: Long
    ): Flow<List<CurrencyReportPersonRow>> = reportDao.observeCurrencyReportPeople(currencyCode, startDateMillis, endDateMillisExclusive)

    suspend fun getCurrencyReportSummary(currencyCode: String, startDateMillis: Long, endDateMillisExclusive: Long): CurrencyReportSummary =
        reportDao.getCurrencyReportSummary(currencyCode, startDateMillis, endDateMillisExclusive)

    suspend fun getPersonReportSummary(personId: Long, currencyCode: String, startDateMillis: Long, endDateMillisExclusive: Long): PersonReportSummary =
        reportDao.getPersonReportSummary(personId, currencyCode, startDateMillis, endDateMillisExclusive)

    suspend fun getPersonOpeningBalance(personId: Long, currencyCode: String, startDateMillis: Long): Long =
        reportDao.getPersonOpeningBalance(personId, currencyCode, startDateMillis)

    suspend fun getPersonReportTransactions(personId: Long, currencyCode: String, startDateMillis: Long, endDateMillisExclusive: Long): List<PersonReportTransaction> =
        reportDao.getPersonReportTransactions(personId, currencyCode, startDateMillis, endDateMillisExclusive)

    suspend fun getPersonReportTransactionRows(personId: Long, currencyCode: String, startDateMillis: Long, endDateMillisExclusive: Long): List<PersonReportTransactionRow> =
        reportDao.getPersonReportTransactionRows(personId, currencyCode, startDateMillis, endDateMillisExclusive)

    suspend fun getPersonCurrencySummary(currencyCode: String, startDateMillis: Long, endDateMillisExclusive: Long): List<PersonCurrencySummaryRow> =
        reportDao.getPersonCurrencySummary(currencyCode, startDateMillis, endDateMillisExclusive)

    suspend fun getGeneralReportTransactions(currencyCode: String, startDateMillis: Long, endDateMillisExclusive: Long): List<GeneralReportTransactionRow> =
        reportDao.getGeneralReportTransactions(currencyCode, startDateMillis, endDateMillisExclusive)

    suspend fun getAllCurrencyReportSummaries(startDateMillis: Long, endDateMillisExclusive: Long): List<CurrencyReportSummary> =
        SUPPORTED_CURRENCIES.map { getCurrencyReportSummary(it, startDateMillis, endDateMillisExclusive) }

    suspend fun getAllCurrencyPeople(startDateMillis: Long, endDateMillisExclusive: Long): List<CurrencyReportPersonRowWithCurrency> {
        return SUPPORTED_CURRENCIES.flatMap { currency ->
            reportDao.getPersonCurrencySummary(currency, startDateMillis, endDateMillisExclusive).map { row ->
                CurrencyReportPersonRowWithCurrency(
                    currencyCode = currency,
                    personId = row.personId,
                    personName = row.personName,
                    totalReceivableMinor = row.totalReceivableMinor,
                    totalPayableMinor = row.totalPayableMinor,
                    balanceMinor = row.balanceMinor,
                    transactionCount = row.transactionCount
                )
            }
        }.sortedWith(compareBy<CurrencyReportPersonRowWithCurrency> { it.personName.lowercase() }.thenBy { SUPPORTED_CURRENCIES.indexOf(it.currencyCode) })
    }

    suspend fun getAllCurrencyGeneralTransactions(startDateMillis: Long, endDateMillisExclusive: Long): List<GeneralReportTransactionRow> =
        SUPPORTED_CURRENCIES.flatMap { getGeneralReportTransactions(it, startDateMillis, endDateMillisExclusive) }
            .sortedWith(compareBy<GeneralReportTransactionRow> { it.transactionDate }.thenBy { it.transactionId })

    suspend fun getAllCurrencyPersonSummaries(startDateMillis: Long, endDateMillisExclusive: Long): List<PersonCurrencySummaryRow> =
        SUPPORTED_CURRENCIES.flatMap { getPersonCurrencySummary(it, startDateMillis, endDateMillisExclusive) }
            .sortedWith(compareBy<PersonCurrencySummaryRow> { it.personName.lowercase() }.thenBy { SUPPORTED_CURRENCIES.indexOf(it.currencyCode) })

    suspend fun getMultiCurrencyPersonReport(personId: Long, startDateMillis: Long, endDateMillisExclusive: Long): MultiCurrencyPersonReport {
        val reports = SUPPORTED_CURRENCIES.mapNotNull { currency ->
            val summary = getPersonReportSummary(personId, currency, startDateMillis, endDateMillisExclusive)
            if (summary.personName.isBlank()) null
            else PersonCurrencyReport(currency, summary, getPersonReportTransactions(personId, currency, startDateMillis, endDateMillisExclusive))
        }
        val first = reports.firstOrNull()
        return MultiCurrencyPersonReport(
            personId = personId,
            personName = first?.summary?.personName.orEmpty(),
            phone = first?.summary?.phone.orEmpty(),
            address = first?.summary?.address.orEmpty(),
            reports = reports
        )
    }
}
