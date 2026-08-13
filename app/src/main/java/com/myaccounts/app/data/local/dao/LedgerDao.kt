package com.myaccounts.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.myaccounts.app.data.local.entity.CurrencyAccountEntity
import com.myaccounts.app.data.local.entity.PersonEntity
import com.myaccounts.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * يجمع بيانات الشخص مع حساباته بالعملات الثلاث.
 */
data class PersonWithAccounts(
    @androidx.room.Embedded
    val person: PersonEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "personId"
    )
    val accounts: List<CurrencyAccountEntity>
)

/**
 * يجمع بيانات حساب العملة مع بيانات الشخص.
 */
data class AccountWithPerson(
    @androidx.room.Embedded
    val account: CurrencyAccountEntity,

    @Relation(
        parentColumn = "personId",
        entityColumn = "id"
    )
    val person: PersonEntity
)

@Dao
interface LedgerDao {

    // ============================================================
    // الأشخاص
    // ============================================================

    /**
     * جلب جميع الأشخاص مع حساباتهم الثلاثة.
     */
    @Transaction
    @Query("SELECT * FROM persons ORDER BY name COLLATE NOCASE ASC")
    fun getAllPersonsWithAccountsFlow(): Flow<List<PersonWithAccounts>>

    /**
     * جلب شخص بواسطة ID.
     */
    @Query("SELECT * FROM persons WHERE id = :personId LIMIT 1")
    suspend fun getPersonById(personId: Long): PersonEntity?

    /**
     * إضافة شخص.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPerson(person: PersonEntity): Long

    /**
     * تعديل بيانات الشخص.
     */
    @Update
    suspend fun updatePerson(person: PersonEntity)

    /**
     * حذف شخص.
     *
     * بسبب ForeignKey + CASCADE سيتم حذف حساباته
     * وحركات حساباته تلقائياً.
     */
    @Query("DELETE FROM persons WHERE id = :personId")
    suspend fun deletePerson(personId: Long)


    // ============================================================
    // حسابات العملات
    // ============================================================

    /**
     * إنشاء حسابات العملات الثلاث للشخص.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCurrencyAccounts(
        accounts: List<CurrencyAccountEntity>
    )

    /**
     * جلب حسابات شخص معين.
     */
    @Query(
        """
        SELECT *
        FROM currency_accounts
        WHERE personId = :personId
        ORDER BY
            CASE currency
                WHEN 'YER' THEN 1
                WHEN 'SAR' THEN 2
                WHEN 'USD' THEN 3
                ELSE 4
            END
        """
    )
    suspend fun getCurrencyAccounts(
        personId: Long
    ): List<CurrencyAccountEntity>

    /**
     * جلب حساب عملة محدد لشخص.
     */
    @Query(
        """
        SELECT *
        FROM currency_accounts
        WHERE personId = :personId
        AND currency = :currency
        LIMIT 1
        """
    )
    suspend fun getCurrencyAccount(
        personId: Long,
        currency: String
    ): CurrencyAccountEntity?

    /**
     * جلب حساب بواسطة ID.
     */
    @Query(
        """
        SELECT *
        FROM currency_accounts
        WHERE id = :accountId
        LIMIT 1
        """
    )
    suspend fun getCurrencyAccountById(
        accountId: Long
    ): CurrencyAccountEntity?

    /**
     * جلب حساب مع الشخص المرتبط به.
     */
    @Transaction
    @Query(
        """
        SELECT *
        FROM currency_accounts
        WHERE id = :accountId
        LIMIT 1
        """
    )
    suspend fun getAccountWithPerson(
        accountId: Long
    ): AccountWithPerson?


    // ============================================================
    // الحركات المالية
    // ============================================================

    /**
     * إضافة حركة مالية.
     *
     * RECEIVABLE = لي
     * PAYABLE    = علي
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(
        transaction: TransactionEntity
    ): Long

    /**
     * تعديل حركة مالية.
     */
    @Update
    suspend fun updateTransaction(
        transaction: TransactionEntity
    )

    /**
     * حذف حركة مالية.
     */
    @Query(
        """
        DELETE FROM transactions
        WHERE id = :transactionId
        """
    )
    suspend fun deleteTransaction(
        transactionId: Long
    )

    /**
     * جلب حركة واحدة.
     */
    @Query(
        """
        SELECT *
        FROM transactions
        WHERE id = :transactionId
        LIMIT 1
        """
    )
    suspend fun getTransactionById(
        transactionId: Long
    ): TransactionEntity?

    /**
     * جلب جميع حركات حساب معين.
     *
     * الأحدث أولاً.
     */
    @Query(
        """
        SELECT *
        FROM transactions
        WHERE currencyAccountId = :accountId
        ORDER BY transactionDate DESC, id DESC
        """
    )
    fun getTransactionsFlow(
        accountId: Long
    ): Flow<List<TransactionEntity>>

    /**
     * جلب جميع حركات حساب معين كقائمة.
     */
    @Query(
        """
        SELECT *
        FROM transactions
        WHERE currencyAccountId = :accountId
        ORDER BY transactionDate ASC, id ASC
        """
    )
    suspend fun getTransactions(
        accountId: Long
    ): List<TransactionEntity>


    // ============================================================
    // حساب الرصيد
    // ============================================================

    /**
     * حساب الرصيد الحالي لحساب العملة.
     *
     * RECEIVABLE = لي  -> يضاف
     * PAYABLE    = علي -> يطرح
     *
     * مثال:
     *
     * لي    100,000
     * علي    30,000
     *
     * الرصيد = 70,000 له
     */
    @Query(
        """
        SELECT COALESCE(
            SUM(
                CASE
                    WHEN type = 'RECEIVABLE'
                        THEN amountMinor
                    WHEN type = 'PAYABLE'
                        THEN -amountMinor
                    ELSE 0
                END
            ),
            0
        )
        FROM transactions
        WHERE currencyAccountId = :accountId
        """
    )
    suspend fun calculateBalance(
        accountId: Long
    ): Long

    /**
     * حساب إجمالي "لي".
     */
    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0)
        FROM transactions
        WHERE currencyAccountId = :accountId
        AND type = 'RECEIVABLE'
        """
    )
    suspend fun calculateTotalReceivable(
        accountId: Long
    ): Long

    /**
     * حساب إجمالي "علي".
     */
    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0)
        FROM transactions
        WHERE currencyAccountId = :accountId
        AND type = 'PAYABLE'
        """
    )
    suspend fun calculateTotalPayable(
        accountId: Long
    ): Long


    // ============================================================
    // التحقق من وجود الحسابات الثلاثة
    // ============================================================

    /**
     * عدد حسابات العملات الموجودة للشخص.
     *
     * المفترض أن يكون 3:
     *
     * YER
     * SAR
     * USD
     */
    @Query(
        """
        SELECT COUNT(*)
        FROM currency_accounts
        WHERE personId = :personId
        """
    )
    suspend fun getCurrencyAccountsCount(
        personId: Long
    ): Int
}
