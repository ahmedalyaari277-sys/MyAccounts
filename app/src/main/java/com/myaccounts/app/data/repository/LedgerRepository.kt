package com.myaccounts.app.data.repository

import com.myaccounts.app.data.local.CurrencyAccountEntity
import com.myaccounts.app.data.local.LedgerDao
import com.myaccounts.app.data.local.PersonEntity
import kotlinx.coroutines.flow.Flow

class LedgerRepository(
    private val dao: LedgerDao
) : LedgerRepositoryContract {

    override fun observePeople(
        query: String
    ): Flow<List<PersonEntity>> {
        return dao.observePeople(query)
    }

    override fun observePerson(
        personId: Long
    ): Flow<PersonEntity?> {
        return dao.observePerson(personId)
    }

    override fun observeCurrencyAccounts(
        personId: Long
    ): Flow<List<CurrencyAccountEntity>> {
        return dao.observeCurrencyAccounts(personId)
    }

    override fun observeCurrencyAccount(
        accountId: Long
    ): Flow<CurrencyAccountEntity?> {
        return dao.observeCurrencyAccount(accountId)
    }

    override suspend fun insertPerson(
        person: PersonEntity
    ): Long {
        return dao.insertPersonWithCurrencyAccounts(
            person = person,
            currencyCodes = DEFAULT_CURRENCIES
        )
    }

    override suspend fun updatePerson(
        person: PersonEntity
    ) {
        dao.updatePerson(person)
    }

    override suspend fun deletePerson(
        personId: Long
    ) {
        dao.softDeletePerson(personId)
    }

    override suspend fun getCurrencyAccount(
        personId: Long,
        currencyCode: String
    ): CurrencyAccountEntity? {
        return dao.getCurrencyAccount(
            personId = personId,
            currencyCode = currencyCode
        )
    }

    override suspend fun updateCurrencyBalance(
        accountId: Long,
        balanceMinor: Long
    ) {
        dao.updateCurrencyBalance(
            accountId = accountId,
            balanceMinor = balanceMinor
        )
    }

    companion object {
        val DEFAULT_CURRENCIES = listOf(
            "YER",
            "SAR",
            "USD"
        )
    }
}
