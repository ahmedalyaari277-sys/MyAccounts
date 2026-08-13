package com.myaccounts.app.data.repository

import com.myaccounts.app.data.local.CurrencyAccountEntity
import com.myaccounts.app.data.local.PersonEntity
import kotlinx.coroutines.flow.Flow

interface LedgerRepositoryContract {

    fun observePeople(query: String): Flow<List<PersonEntity>>

    fun observePerson(personId: Long): Flow<PersonEntity?>

    fun observeCurrencyAccounts(
        personId: Long
    ): Flow<List<CurrencyAccountEntity>>

    fun observeCurrencyAccount(
        accountId: Long
    ): Flow<CurrencyAccountEntity?>

    suspend fun insertPerson(
        person: PersonEntity
    ): Long

    suspend fun updatePerson(
        person: PersonEntity
    )

    suspend fun deletePerson(
        personId: Long
    )

    suspend fun getCurrencyAccount(
        personId: Long,
        currencyCode: String
    ): CurrencyAccountEntity?

    suspend fun updateCurrencyBalance(
        accountId: Long,
        balanceMinor: Long
    )
}
