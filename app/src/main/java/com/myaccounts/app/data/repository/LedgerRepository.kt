package com.myaccounts.app.data.repository

import com.myaccounts.app.data.local.dao.LedgerDao
import com.myaccounts.app.data.local.dao.PersonWithAccounts
import com.myaccounts.app.data.local.entity.CurrencyAccountEntity
import com.myaccounts.app.data.local.entity.PersonEntity
import kotlinx.coroutines.flow.Flow

class LedgerRepository(private val dao: LedgerDao) {

    val allPersonsFlow: Flow<List<PersonWithAccounts>> = dao.getAllPersonsWithAccountsFlow()

    suspend fun addPerson(name: String, phone: String, address: String) {
        val person = PersonEntity(name = name, phone = phone, address = address)
        val personId = dao.insertPerson(person)

        // إنشاء حسابات العملات الثلاث تلقائياً فور إضافة الشخص
        val defaultAccounts = listOf(
            CurrencyAccountEntity(personId = personId, currency = "YER"),
            CurrencyAccountEntity(personId = personId, currency = "SAR"),
            CurrencyAccountEntity(personId = personId, currency = "USD")
        )
        dao.insertCurrencyAccounts(defaultAccounts)
    }
}
