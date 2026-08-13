package com.myaccounts.app.data.repository

import com.myaccounts.app.data.local.dao.LedgerDao
import com.myaccounts.app.data.local.dao.PersonWithAccounts
import com.myaccounts.app.data.local.entity.CurrencyAccountEntity
import com.myaccounts.app.data.local.entity.PersonEntity
import kotlinx.coroutines.flow.Flow

class LedgerRepository(
    private val dao: LedgerDao
) {

    /**
     * جميع الأشخاص مع حساباتهم
     */
    val allPersonsFlow: Flow<List<PersonWithAccounts>> =
        dao.getAllPersonsWithAccountsFlow()

    /**
     * إضافة شخص جديد مع إنشاء حساباته بالعملات الأساسية
     */
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

        val personId = dao.insertPerson(person)

        /*
         * عند إنشاء الشخص يتم إنشاء ثلاثة حسابات له:
         *
         * YER = ريال يمني
         * SAR = ريال سعودي
         * USD = دولار أمريكي
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

    /**
     * الحصول على شخص محدد مع جميع حساباته
     */
    suspend fun getPersonWithAccounts(
        personId: Long
    ): PersonWithAccounts? {

        return dao.getPersonWithAccounts(
            personId
        )
    }

    /**
     * حذف شخص.
     *
     * بسبب ForeignKey + CASCADE سيتم حذف
     * حساباته ومعاملاته المرتبطة به تلقائياً.
     */
    suspend fun deletePerson(
        personId: Long
    ) {

        dao.deletePerson(
            personId
        )
    }
}
