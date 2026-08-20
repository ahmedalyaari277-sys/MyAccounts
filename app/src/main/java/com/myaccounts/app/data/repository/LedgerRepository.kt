package com.myaccounts.app.data.repository

import androidx.room.withTransaction
import com.myaccounts.app.data.local.AppDatabase
import com.myaccounts.app.data.local.CurrencyAccountEntity
import com.myaccounts.app.data.local.PersonEntity
import com.myaccounts.app.data.local.dao.LedgerDao
import com.myaccounts.app.data.local.dao.PersonWithAccounts
import com.myaccounts.app.data.local.dao.TransactionDao
import kotlinx.coroutines.flow.Flow

class LedgerRepository(
    private val dao: LedgerDao,
    private val transactionDao: TransactionDao,
    private val database: AppDatabase
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
    override suspend fun deletePerson(personId: Long) = dao.softDeletePerson(personId)
    override suspend fun restorePerson(personId: Long) = dao.restorePerson(personId)

    override suspend fun permanentlyDeletePerson(personId: Long) {
        database.withTransaction {
            transactionDao.snapshotArchivedTransactionsForPerson(personId)
            transactionDao.snapshotArchivedAttachmentsForPerson(personId)
            dao.permanentlyDeletePerson(personId)
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
