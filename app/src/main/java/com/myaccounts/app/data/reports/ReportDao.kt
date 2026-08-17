package com.myaccounts.app.data.reports

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {

    @Query(
        """
        SELECT
            p.id AS personId,
            p.name AS personName,

            COALESCE(SUM(CASE
                WHEN t.transactionDate >= :startDateMillis
                 AND t.transactionDate < :endDateMillisExclusive
                 AND t.type = 'RECEIVABLE'
                THEN t.amountMinor ELSE 0 END), 0) AS totalReceivableMinor,

            COALESCE(SUM(CASE
                WHEN t.transactionDate >= :startDateMillis
                 AND t.transactionDate < :endDateMillisExclusive
                 AND t.type = 'PAYABLE'
                THEN t.amountMinor ELSE 0 END), 0) AS totalPayableMinor,

            COALESCE(SUM(CASE
                WHEN t.transactionDate >= :startDateMillis
                 AND t.transactionDate < :endDateMillisExclusive
                THEN CASE
                    WHEN t.type = 'RECEIVABLE' THEN t.amountMinor
                    WHEN t.type = 'PAYABLE' THEN -t.amountMinor
                    ELSE 0
                END
                ELSE 0 END), 0) AS balanceMinor,

            COUNT(CASE
                WHEN t.transactionDate >= :startDateMillis
                 AND t.transactionDate < :endDateMillisExclusive
                THEN t.id ELSE NULL END) AS transactionCount

        FROM people p

        INNER JOIN currency_accounts ca
            ON ca.personId = p.id
            AND ca.currencyCode = :currencyCode

        LEFT JOIN transactions t
            ON t.accountId = ca.id

        WHERE p.isActive = 1

        GROUP BY p.id, p.name

        ORDER BY p.name COLLATE NOCASE ASC
        """
    )
    fun observeCurrencyReportPeople(
        currencyCode: String,
        startDateMillis: Long,
        endDateMillisExclusive: Long
    ): Flow<List<CurrencyReportPersonRow>>

    @Query(
        """
        SELECT
            :currencyCode AS currencyCode,

            COALESCE(SUM(CASE
                WHEN t.transactionDate >= :startDateMillis
                 AND t.transactionDate < :endDateMillisExclusive
                 AND t.type = 'RECEIVABLE'
                THEN t.amountMinor ELSE 0 END), 0) AS totalReceivableMinor,

            COALESCE(SUM(CASE
                WHEN t.transactionDate >= :startDateMillis
                 AND t.transactionDate < :endDateMillisExclusive
                 AND t.type = 'PAYABLE'
                THEN t.amountMinor ELSE 0 END), 0) AS totalPayableMinor,

            COALESCE(SUM(CASE
                WHEN t.transactionDate >= :startDateMillis
                 AND t.transactionDate < :endDateMillisExclusive
                THEN CASE
                    WHEN t.type = 'RECEIVABLE' THEN t.amountMinor
                    WHEN t.type = 'PAYABLE' THEN -t.amountMinor
                    ELSE 0
                END
                ELSE 0 END), 0) AS balanceMinor,

            COUNT(CASE
                WHEN t.transactionDate >= :startDateMillis
                 AND t.transactionDate < :endDateMillisExclusive
                THEN t.id ELSE NULL END) AS transactionCount

        FROM people p

        INNER JOIN currency_accounts ca
            ON ca.personId = p.id
            AND ca.currencyCode = :currencyCode

        LEFT JOIN transactions t
            ON t.accountId = ca.id

        WHERE p.isActive = 1
        """
    )
    suspend fun getCurrencyReportSummary(
        currencyCode: String,
        startDateMillis: Long,
        endDateMillisExclusive: Long
    ): CurrencyReportSummary

    @Query(
        """
        SELECT
            p.id AS personId,
            p.name AS personName,
            ca.currencyCode AS currencyCode,

            COALESCE(SUM(CASE
                WHEN t.transactionDate < :startDateMillis
                THEN CASE
                    WHEN t.type = 'RECEIVABLE' THEN t.amountMinor
                    WHEN t.type = 'PAYABLE' THEN -t.amountMinor
                    ELSE 0
                END
                ELSE 0 END), 0) AS openingBalanceMinor,

            COALESCE(SUM(CASE
                WHEN t.transactionDate >= :startDateMillis
                 AND t.transactionDate < :endDateMillisExclusive
                 AND t.type = 'RECEIVABLE'
                THEN t.amountMinor ELSE 0 END), 0) AS periodReceivableMinor,

            COALESCE(SUM(CASE
                WHEN t.transactionDate >= :startDateMillis
                 AND t.transactionDate < :endDateMillisExclusive
                 AND t.type = 'PAYABLE'
                THEN t.amountMinor ELSE 0 END), 0) AS periodPayableMinor,

            COALESCE(SUM(CASE
                WHEN t.transactionDate >= :startDateMillis
                 AND t.transactionDate < :endDateMillisExclusive
                THEN CASE
                    WHEN t.type = 'RECEIVABLE' THEN t.amountMinor
                    WHEN t.type = 'PAYABLE' THEN -t.amountMinor
                    ELSE 0
                END
                ELSE 0 END), 0) AS periodBalanceMinor,

            COALESCE(SUM(CASE
                WHEN t.transactionDate < :endDateMillisExclusive
                THEN CASE
                    WHEN t.type = 'RECEIVABLE' THEN t.amountMinor
                    WHEN t.type = 'PAYABLE' THEN -t.amountMinor
                    ELSE 0
                END
                ELSE 0 END), 0) AS closingBalanceMinor,

            COUNT(CASE
                WHEN t.transactionDate >= :startDateMillis
                 AND t.transactionDate < :endDateMillisExclusive
                THEN t.id ELSE NULL END) AS transactionCount

        FROM people p

        INNER JOIN currency_accounts ca
            ON ca.personId = p.id
            AND ca.currencyCode = :currencyCode

        LEFT JOIN transactions t
            ON t.accountId = ca.id

        WHERE p.id = :personId
          AND p.isActive = 1

        GROUP BY p.id, p.name, ca.currencyCode
        """
    )
    suspend fun getPersonReportSummary(
        personId: Long,
        currencyCode: String,
        startDateMillis: Long,
        endDateMillisExclusive: Long
    ): PersonReportSummary

    @Query(
        """
        SELECT COALESCE(SUM(
            CASE
                WHEN t.type = 'RECEIVABLE' THEN t.amountMinor
                WHEN t.type = 'PAYABLE' THEN -t.amountMinor
                ELSE 0
            END
        ), 0)
        FROM transactions t
        INNER JOIN currency_accounts ca ON ca.id = t.accountId
        INNER JOIN people p ON p.id = ca.personId
        WHERE ca.personId = :personId
          AND ca.currencyCode = :currencyCode
          AND p.isActive = 1
          AND t.transactionDate < :startDateMillis
        """
    )
    suspend fun getPersonOpeningBalance(
        personId: Long,
        currencyCode: String,
        startDateMillis: Long
    ): Long

    @Query(
        """
        SELECT
            t.id AS transactionId,
            t.transactionDate AS transactionDate,
            t.type AS type,
            t.amountMinor AS amountMinor,
            t.description AS description

        FROM transactions t

        INNER JOIN currency_accounts ca ON ca.id = t.accountId
        INNER JOIN people p ON p.id = ca.personId

        WHERE ca.personId = :personId
          AND ca.currencyCode = :currencyCode
          AND p.isActive = 1
          AND t.transactionDate >= :startDateMillis
          AND t.transactionDate < :endDateMillisExclusive

        ORDER BY t.transactionDate DESC, t.id DESC
        """
    )
    suspend fun getPersonReportTransactions(
        personId: Long,
        currencyCode: String,
        startDateMillis: Long,
        endDateMillisExclusive: Long
    ): List<PersonReportTransaction>

    @Query(
        """
        SELECT
            t.id AS transactionId,
            t.transactionDate AS transactionDate,
            t.type AS type,
            t.amountMinor AS amountMinor,
            t.description AS description,

            COALESCE((
                SELECT SUM(
                    CASE
                        WHEN t2.type = 'RECEIVABLE' THEN t2.amountMinor
                        WHEN t2.type = 'PAYABLE' THEN -t2.amountMinor
                        ELSE 0
                    END
                )
                FROM transactions t2
                WHERE t2.accountId = t.accountId
                  AND (
                      t2.transactionDate < t.transactionDate
                      OR (
                          t2.transactionDate = t.transactionDate
                          AND t2.id <= t.id
                      )
                  )
            ), 0) AS balanceMinor

        FROM transactions t

        INNER JOIN currency_accounts ca ON ca.id = t.accountId
        INNER JOIN people p ON p.id = ca.personId

        WHERE ca.personId = :personId
          AND ca.currencyCode = :currencyCode
          AND p.isActive = 1
          AND t.transactionDate >= :startDateMillis
          AND t.transactionDate < :endDateMillisExclusive

        ORDER BY t.transactionDate DESC, t.id DESC
        """
    )
    suspend fun getPersonReportTransactionRows(
        personId: Long,
        currencyCode: String,
        startDateMillis: Long,
        endDateMillisExclusive: Long
    ): List<PersonReportTransactionRow>
}
