package com.myaccounts.app.data.repository

import com.myaccounts.app.data.local.dao.AccountBalance
import com.myaccounts.app.data.local.dao.LedgerDao
import com.myaccounts.app.data.local.dao.PersonWithAccounts
import com.myaccounts.app.data.local.entity.CurrencyAccountEntity
import com.myaccounts.app.data.local.entity.PersonEntity
import com.myaccounts.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

class LedgerRepository(
    private val dao: LedgerDao
) {

    // =========================================================
    // الأشخاص
    // =========================================================

    /**
     * جميع الأشخاص مع حساباتهم.
     */
    val allPersonsFlow: Flow<List<PersonWithAccounts>> =
        dao.getAllPersonsWithAccountsFlow()


    /**
     * جلب شخص واحد مع حساباته.
     */
    fun getPersonWithAccountsFlow(
        personId: Long
    ): Flow<PersonWithAccounts?> {

        return dao.getPersonWithAccountsFlow(
            personId
        )
    }


    /**
     * إضافة شخص جديد.
     *
     * عند إنشاء الشخص يتم إنشاء ثلاثة حسابات
     * تلقائياً:
     *
     * YER
     * SAR
     * USD
     */
    suspend fun addPerson(
        name: String,
        phone: String,
        address: String
    ) {

        val person = PersonEntity(
            name = name.trim(),
            phone = phone.trim(),
            address = address.trim()
        )

        val personId = dao.insertPerson(
            person
        )

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
     * حذف شخص.
     */
    suspend fun deletePerson(
        personId: Long
    ) {

        dao.deletePerson(
            personId
        )
    }


    // =========================================================
    // الحسابات
    // =========================================================

    /**
     * جلب حسابات شخص معين.
     */
    fun getAccountsForPersonFlow(
        personId: Long
    ): Flow<List<CurrencyAccountEntity>> {

        return dao.getAccountsForPersonFlow(
            personId
        )
    }


    /**
     * جلب حساب معين.
     */
    suspend fun getAccount(
        accountId: Long
    ): CurrencyAccountEntity? {

        return dao.getAccount(
            accountId
        )
    }


    // =========================================================
    // الأرصدة
    // =========================================================

    /**
     * جلب أرصدة جميع عملات الشخص.
     */
    fun getAccountBalancesForPersonFlow(
        personId: Long
    ): Flow<List<AccountBalance>> {

        return dao.getAccountBalancesForPersonFlow(
            personId
        )
    }


    /**
     * جلب رصيد حساب معين.
     */
    fun getAccountBalanceFlow(
        accountId: Long
    ): Flow<AccountBalance?> {

        return dao.getAccountBalanceFlow(
            accountId
        )
    }


    // =========================================================
    // المعاملات
    // =========================================================

    /**
     * إضافة معاملة.
     *
     * type:
     *
     * RECEIVABLE = لي
     * PAYABLE    = علي
     *
     * amountMinor:
     * المبلغ بوحدة التخزين الصحيحة.
     *
     * مثال:
     *
     * 500 ريال
     *
     * يتم تخزينها:
     *
     * 500
     */
    suspend fun addTransaction(
        currencyAccountId: Long,
        type: String,
        amountMinor: Long,
        description: String,
        transactionDate: Long = System.currentTimeMillis()
    ): Long {

        require(
            type == "RECEIVABLE" ||
                    type == "PAYABLE"
        ) {
            "نوع المعاملة غير صحيح"
        }

        require(
            amountMinor > 0
        ) {
            "المبلغ يجب أن يكون أكبر من صفر"
        }

        val transaction = TransactionEntity(

            currencyAccountId = currencyAccountId,

            type = type,

            amountMinor = amountMinor,

            description = description.trim(),

            transactionDate = transactionDate
        )

        return dao.insertTransaction(
            transaction
        )
    }


    /**
     * إضافة مبلغ "لي".
     */
    suspend fun addReceivable(
        currencyAccountId: Long,
        amountMinor: Long,
        description: String,
        transactionDate: Long = System.currentTimeMillis()
    ): Long {

        return addTransaction(
            currencyAccountId = currencyAccountId,
            type = "RECEIVABLE",
            amountMinor = amountMinor,
            description = description,
            transactionDate = transactionDate
        )
    }


    /**
     * إضافة مبلغ "علي".
     */
    suspend fun addPayable(
        currencyAccountId: Long,
        amountMinor: Long,
        description: String,
        transactionDate: Long = System.currentTimeMillis()
    ): Long {

        return addTransaction(
            currencyAccountId = currencyAccountId,
            type = "PAYABLE",
            amountMinor = amountMinor,
            description = description,
            transactionDate = transactionDate
        )
    }


    /**
     * جلب معاملات حساب معين.
     */
    fun getTransactionsForAccountFlow(
        accountId: Long
    ): Flow<List<TransactionEntity>> {

        return dao.getTransactionsForAccountFlow(
            accountId
        )
    }


    /**
     * جلب جميع معاملات الشخص عبر العملات.
     */
    fun getTransactionsForPersonFlow(
        personId: Long
    ): Flow<List<TransactionEntity>> {

        return dao.getTransactionsForPersonFlow(
            personId
        )
    }


    /**
     * جلب معاملة واحدة.
     */
    suspend fun getTransaction(
        transactionId: Long
    ): TransactionEntity? {

        return dao.getTransaction(
            transactionId
        )
    }


    /**
     * حذف معاملة.
     */
    suspend fun deleteTransaction(
        transactionId: Long
    ) {

        dao.deleteTransaction(
            transactionId
        )
    }
}
