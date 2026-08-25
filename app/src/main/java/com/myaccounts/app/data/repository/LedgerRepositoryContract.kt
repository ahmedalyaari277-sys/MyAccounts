package com.myaccounts.app.data.repository

import com.myaccounts.app.data.local.CurrencyAccountEntity
import com.myaccounts.app.data.local.PersonEntity
import com.myaccounts.app.data.local.dao.PersonWithAccounts
import kotlinx.coroutines.flow.Flow

interface LedgerRepositoryContract {
    fun observePeople(query: String): Flow<List<PersonEntity>>
    fun observePerson(personId: Long): Flow<PersonEntity?>
    fun observePersonsWithAccounts(): Flow<List<PersonWithAccounts>>
    fun observeArchivedPersonsWithAccounts(): Flow<List<PersonWithAccounts>>
    fun observePersonWithAccounts(personId: Long): Flow<PersonWithAccounts?>
    fun observeCurrencyAccounts(personId: Long): Flow<List<CurrencyAccountEntity>>
    fun observeCurrencyAccount(accountId: Long): Flow<CurrencyAccountEntity?>
    suspend fun insertPerson(person: PersonEntity): Long
    suspend fun updatePerson(person: PersonEntity)
    suspend fun deletePerson(personId: Long)
    suspend fun restorePerson(personId: Long)
    suspend fun permanentlyDeletePerson(personId: Long): List<Long>
    suspend fun clearArchive(): List<Long>
    suspend fun getCurrencyAccount(personId: Long, currencyCode: String): CurrencyAccountEntity?
    suspend fun updateCurrencyBalance(accountId: Long, balanceMinor: Long)
}
