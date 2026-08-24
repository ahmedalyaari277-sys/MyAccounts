package com.myaccounts.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.myaccounts.app.data.local.AppDatabase
import com.myaccounts.app.data.local.CurrencyAccountEntity
import com.myaccounts.app.data.local.PersonEntity
import com.myaccounts.app.data.local.dao.LedgerDao
import com.myaccounts.app.data.local.dao.PersonWithAccounts
import com.myaccounts.app.data.local.dao.TransactionAttachmentDao
import com.myaccounts.app.data.local.dao.TransactionDao
import com.myaccounts.app.util.TransactionAttachmentStorage
import kotlinx.coroutines.flow.Flow

class LedgerRepository(
    private val dao: LedgerDao,
    private val transactionDao: TransactionDao,
    private val transactionAttachmentDao: TransactionAttachmentDao,
    private val database: AppDatabase,
    private val context: Context
) : LedgerRepositoryContract {
    override fun observePeople(query: String): Flow<List<PersonEntity>> = dao.observePeople(query)
    override fun observePerson(personId: Long): Flow<PersonEntity?> = dao.observePerson(personId)
    override fun observePersonsWithAccounts(): Flow<List<PersonWithAccounts>> = dao.observePersonsWithAccounts()
    override fun observeArchivedPersonsWithAccounts(): Flow<List<PersonWithAccounts>> = dao.observeArchivedPersonsWithAccounts()
    override fun observePersonWithAccounts(personId: Long): Flow<PersonWithAccounts?> = dao.observePersonWithAccounts(personId)
    override fun observeCurrencyAccounts(personId: Long): Flow<List<CurrencyAccountEntity>> = dao.observeCurrencyAccounts(personId)
    override fun observeCurrencyAccount(accountId: Long): Flow<CurrencyAccountEntity?> = dao.observeCurrencyAccount(accountId)

    override suspend fun insertPerson(person: PersonEntity): Long =
        dao.insertPersonWithCurrencyAccounts(
            person = person,
            currencyCodes = DEFAULT_CURRENCIES
        )

    override suspend fun updatePerson(person: PersonEntity) = dao.updatePerson(person)

    /** Archive the account as a unit: all transactions existing at that moment move with it. */
    override suspend fun deletePerson(personId: Long) {
        transactionDao.archivePersonAndUpdateBalances(personId)
    }

    /** Restore the account as a unit and restore only the transactions still archived with it. */
    override suspend fun restorePerson(personId: Long) {
        transactionDao.restorePersonAndUpdateBalances(personId)
    }

    /**
     * Permanently delete the account and all database records belonging to it.
     * Attachment files are collected before the Room transaction and removed only
     * after the database deletion succeeds, so a failed database transaction never
     * destroys files that may still be referenced.
     */
    override suspend fun permanentlyDeletePerson(personId: Long) {
        val attachments = transactionAttachmentDao.getAttachmentsForPerson(personId)

        database.withTransaction {
            transactionDao.deletePersonArchiveAttachmentSnapshots(personId)
            transactionDao.deletePersonArchiveSnapshots(personId)
            dao.permanentlyDeletePerson(personId)
        }

        attachments
            .groupBy { it.transactionId }
            .forEach { (transactionId, transactionAttachments) ->
                TransactionAttachmentStorage.deleteTransactionFiles(
                    context = context,
                    transactionId = transactionId,
                    attachments = transactionAttachments
                )
            }
    }

    override suspend fun getCurrencyAccount(
        personId: Long,
        currencyCode: String
    ): CurrencyAccountEntity? = dao.getCurrencyAccount(personId, currencyCode)

    override suspend fun updateCurrencyBalance(
        accountId: Long,
        balanceMinor: Long
    ) = dao.updateCurrencyBalance(accountId, balanceMinor)

    companion object {
        val DEFAULT_CURRENCIES = listOf("YER", "SAR", "USD")
    }
}
