package com.myaccounts.app.data.repository

import com.myaccounts.app.data.local.dao.LedgerDao
import com.myaccounts.app.data.local.dao.PersonWithAccounts
import com.myaccounts.app.data.local.entity.CurrencyAccountEntity
import com.myaccounts.app.data.local.entity.PersonEntity
import com.myaccounts.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository المسؤول عن جميع العمليات المتعلقة
 * بالأشخاص والحسابات والحركات المالية.
 */
class LedgerRepository(
    private val dao: LedgerDao
) {

    // ============================================================
    // الأشخاص
    // ============================================================

    /**
     * جميع الأشخاص مع حساباتهم.
     */
    val allPersonsFlow: Flow<List<PersonWithAccounts>> =
        dao.getAllPersonsWithAccountsFlow()

    /**
     * إضافة شخص جديد.
     *
     * عند إضافة الشخص يتم إنشاء ثلاثة حسابات تلقائياً:
     *
     * YER = ريال يمني
     * SAR = ريال سعودي
     * USD = دولار أمريكي
     */
    suspend fun addPerson(
        name: String,
        phone: String,
        address: String
    ): Long {

        require(name.isNotBlank()) {
            "اسم الشخص مطلوب"
        }

        val person = PersonEntity(
            name = name.trim(),
            phone = phone.trim(),
            address = address.trim()
        )

        val personId = dao.insertPerson(person)

        val accounts = listOf(
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

        dao.insertCurrencyAccounts(accounts)

        return personId
    }

    /**
     * الحصول على شخص.
     */
    suspend fun getPerson(
        personId: Long
    ): PersonEntity? {
        return dao.getPersonById(personId)
    }

    /**
     * تعديل بيانات الشخص.
     */
    suspend fun updatePerson(
        personId: Long,
        name: String,
        phone: String,
        address: String
    ) {

        require(name.isNotBlank()) {
            "اسم الشخص مطلوب"
        }

        val existingPerson =
            dao.getPersonById(personId)
                ?: return

        val updatedPerson = existingPerson.copy(
            name = name.trim(),
            phone = phone.trim(),
            address = address.trim()
        )

        dao.updatePerson(updatedPerson)
    }

    /**
     * حذف الشخص.
     *
     * سيتم حذف حساباته وحركاتها تلقائياً
     * بسبب ForeignKey CASCADE.
     */
    suspend fun deletePerson(
        personId: Long
    ) {
        dao.deletePerson(personId)
    }


    // ============================================================
    // حسابات العملات
    // ============================================================

    /**
     * الحصول على حسابات الشخص الثلاثة.
     */
    suspend fun getCurrencyAccounts(
        personId: Long
    ): List<CurrencyAccountEntity> {

        return dao.getCurrencyAccounts(personId)
    }

    /**
     * الحصول على حساب عملة محدد.
     */
    suspend fun getCurrencyAccount(
        personId: Long,
        currency: String
    ): CurrencyAccountEntity? {

        return dao.getCurrencyAccount(
            personId = personId,
            currency = currency
        )
    }

    /**
     * الحصول على حساب بواسطة ID.
     */
    suspend fun getCurrencyAccountById(
        accountId: Long
    ): CurrencyAccountEntity? {

        return dao.getCurrencyAccountById(accountId)
    }


    // ============================================================
    // الحركات المالية
    // ============================================================

    /**
     * إضافة حركة مالية.
     *
     * type:
     *
     * RECEIVABLE = لي
     * PAYABLE    = علي
     */
    suspend fun addTransaction(
        accountId: Long,
        type: String,
        amount: Long,
        description: String,
        transactionDate: Long
    ): Long {

        require(
            type == "RECEIVABLE" ||
            type == "PAYABLE"
        ) {
            "نوع الحركة غير صحيح"
        }

        require(amount > 0) {
            "المبلغ يجب أن يكون أكبر من صفر"
        }

        val account =
            dao.getCurrencyAccountById(accountId)
                ?: throw IllegalArgumentException(
                    "حساب العملة غير موجود"
                )

        val transaction = TransactionEntity(
            currencyAccountId = account.id,
            type = type,
            amountMinor = amount,
            description = description.trim(),
            transactionDate = transactionDate
        )

        return dao.insertTransaction(transaction)
    }

    /**
     * جلب حركات حساب معين.
     */
    fun getTransactions(
        accountId: Long
    ): Flow<List<TransactionEntity>> {

        return dao.getTransactionsFlow(accountId)
    }

    /**
     * جلب حركة محددة.
     */
    suspend fun getTransaction(
        transactionId: Long
    ): TransactionEntity? {

        return dao.getTransactionById(transactionId)
    }

    /**
     * تعديل حركة.
     */
    suspend fun updateTransaction(
        transaction: TransactionEntity
    ) {

        require(
            transaction.type == "RECEIVABLE" ||
            transaction.type == "PAYABLE"
        ) {
            "نوع الحركة غير صحيح"
        }

        require(transaction.amountMinor > 0) {
            "المبلغ يجب أن يكون أكبر من صفر"
        }

        dao.updateTransaction(transaction)
    }

    /**
     * حذف حركة.
     */
    suspend fun deleteTransaction(
        transactionId: Long
    ) {

        dao.deleteTransaction(transactionId)
    }


    // ============================================================
    // الأرصدة
    // ============================================================

    /**
     * الرصيد الحالي.
     *
     * الرصيد = لي - علي
     */
    suspend fun getBalance(
        accountId: Long
    ): Long {

        return dao.calculateBalance(accountId)
    }

    /**
     * إجمالي المبالغ التي له.
     */
    suspend fun getTotalReceivable(
        accountId: Long
    ): Long {

        return dao.calculateTotalReceivable(accountId)
    }

    /**
     * إجمالي المبالغ التي عليه.
     */
    suspend fun getTotalPayable(
        accountId: Long
    ): Long {

        return dao.calculateTotalPayable(accountId)
    }
}
