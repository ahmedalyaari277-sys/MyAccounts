package com.myaccounts.app.data.repository

import com.myaccounts.app.data.local.dao.LedgerDao
import com.myaccounts.app.data.local.dao.PersonWithAccounts
import com.myaccounts.app.data.local.entity.CurrencyAccountEntity
import com.myaccounts.app.data.local.entity.PersonEntity
import kotlinx.coroutines.flow.Flow

class LedgerRepository(
    private val dao: LedgerDao
) {

    val allPersonsFlow: Flow<List<PersonWithAccounts>> =
        dao.getAllPersonsWithAccountsFlow()

    suspend fun addPerson(
        name: String,
        phone: String,
        address: String
    ) {

        val person = PersonEntity(
            name = name,
            phone = phone,
            address = address
        )

        val personId =
            dao.insertPerson(person)

        /*
         * عند إنشاء شخص جديد يتم إنشاء ثلاثة
         * حسابات مستقلة له تلقائياً.
         *
         * الشخص الواحد يستطيع أن يكون له:
         *
         * 1. حساب بالريال اليمني
         * 2. حساب بالريال السعودي
         * 3. حساب بالدولار
         *
         * وكل حساب مستقل تماماً عن الآخر.
         */

        val currencyAccounts = listOf(

            CurrencyAccountEntity(
                personId = personId,
                currency = "YER"
            ),

            CurrencyAccountEntity(
                personId = personId,
                currency = "SAR"
            ),

            CurrencyAccountEntity(
                personId = personId,
                currency = "USD"
            )
        )

        dao.insertCurrencyAccounts(
            currencyAccounts
        )
    }
}
