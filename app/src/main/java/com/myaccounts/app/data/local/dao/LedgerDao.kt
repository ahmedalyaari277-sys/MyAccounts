package com.myaccounts.app.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.myaccounts.app.data.local.entity.CurrencyAccountEntity
import com.myaccounts.app.data.local.entity.PersonEntity
import com.myaccounts.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * شخص مع جميع حساباته بالعملات المختلفة.
 */
data class PersonWithAccounts(

    @Embedded
    val person: PersonEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "personId"
    )
    val accounts: List<CurrencyAccountEntity>
)

/**
 * حساب عملة مع معاملاته.
 */
data class AccountWithTransactions(

    @Embedded
    val account: CurrencyAccountEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "currencyAccountId"
    )
    val transactions: List<TransactionEntity>
)

/**
 * ملخص رصيد حساب معين.
 *
 * totalReceivable = إجمالي المبالغ التي للشخص
 * totalPayable = إجمالي المبالغ التي على الشخص
 *
 * الرصيد النهائي:
 *
 * balance = totalReceivable - totalPayable
 */
data class AccountBalance(

    val accountId: Long,

    val currency: String,

    val totalReceivable: Long,

    val totalPayable: Long
) {

    val balance: Long
        get() = totalReceivable - totalPayable
}


/**
 * DAO الرئيسي للتطبيق.
 */
@Dao
interface LedgerDao {

    // =========================================================
    // الأشخاص والحسابات
    // =========================================================

    /**
     * جلب جميع الأشخاص مع حساباتهم.
     */
    @Transaction
    @Query(
        "SELECT * FROM persons ORDER BY name COLLATE NOCASE ASC"
    )
    fun getAllPersonsWithAccountsFlow():
            Flow<List<PersonWithAccounts>>


    /**
     * جلب شخص واحد مع حساباته.
     */
    @Transaction
    @Query(
        "SELECT * FROM persons WHERE id = :personId LIMIT 1"
    )
    fun getPersonWithAccountsFlow(
        personId: Long
    ): Flow<PersonWithAccounts?>


    /**
     * إضافة شخص جديد.
     */
    @Insert(
        onConflict = OnConflictStrategy.ABORT
    )
    suspend fun insertPerson(
        person: PersonEntity
    ): Long


    /**
     * إضافة حسابات العملات للشخص.
     */
    @Insert(
        onConflict = OnConflictStrategy.ABORT
    )
    suspend fun insertCurrencyAccounts(
        accounts: List<CurrencyAccountEntity>
    )


    /**
     * جلب جميع حسابات شخص معين.
     */
    @Query(
        "SELECT * FROM currency_accounts WHERE personId = :personId ORDER BY currency ASC"
    )
    fun getAccountsForPersonFlow(
        personId: Long
    ): Flow<List<CurrencyAccountEntity>>


    /**
     * جلب حساب معين.
     */
    @Query(
        "SELECT * FROM currency_accounts WHERE id = :accountId LIMIT 1"
    )
    fun getAccount(
        accountId: Long
    ): CurrencyAccountEntity?


    /**
     * حذف شخص.
     *
     * بسبب ForeignKey + CASCADE سيتم حذف حساباته
     * ومعاملاتها المرتبطة به تلقائياً.
     */
    @Query(
        "DELETE FROM persons WHERE id = :personId"
    )
    suspend fun deletePerson(
        personId: Long
    )


    // =========================================================
    // المعاملات
    // =========================================================

    /**
     * إضافة معاملة جديدة.
     */
    @Insert(
        onConflict = OnConflictStrategy.ABORT
    )
    suspend fun insertTransaction(
        transaction: TransactionEntity
    ): Long


    /**
     * جلب جميع معاملات حساب معين.
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
    fun getTransactionsForAccountFlow(
        accountId: Long
    ): Flow<List<TransactionEntity>>


    /**
     * جلب جميع معاملات شخص معين عبر جميع حساباته.
     *
     * الأحدث أولاً.
     */
    @Query(
        """
        SELECT transactions.*
        FROM transactions
        INNER JOIN currency_accounts
            ON transactions.currencyAccountId = currency_accounts.id
        WHERE currency_accounts.personId = :personId
        ORDER BY transactions.transactionDate DESC, transactions.id DESC
        """
    )
    fun getTransactionsForPersonFlow(
        personId: Long
    ): Flow<List<TransactionEntity>>


    /**
     * جلب معاملة واحدة.
     */
    @Query(
        """
        SELECT *
        FROM transactions
        WHERE id = :transactionId
        LIMIT 1
        """
    )
    suspend fun getTransaction(
        transactionId: Long
    ): TransactionEntity?


    /**
     * حذف معاملة.
     */
    @Query(
        "DELETE FROM transactions WHERE id = :transactionId"
    )
    suspend fun deleteTransaction(
        transactionId: Long
    )


    // =========================================================
    // حساب الأرصدة
    // =========================================================

    /**
     * حساب رصيد حساب واحد.
     *
     * RECEIVABLE = لي
     * PAYABLE    = علي
     *
     * amountMinor يستخدم Long لتجنب مشاكل الكسور
     * وأخطاء Floating Point.
     */
    @Query(
        """
        SELECT
            ca.id AS accountId,
            ca.currency AS currency,

            COALESCE(
                SUM(
                    CASE
                        WHEN t.type = 'RECEIVABLE'
                        THEN t.amountMinor
                        ELSE 0
                    END
                ),
                0
            ) AS totalReceivable,

            COALESCE(
                SUM(
                    CASE
                        WHEN t.type = 'PAYABLE'
                        THEN t.amountMinor
                        ELSE 0
                    END
                ),
                0
            ) AS totalPayable

        FROM currency_accounts ca

        LEFT JOIN transactions t
            ON t.currencyAccountId = ca.id

        WHERE ca.id = :accountId

        GROUP BY ca.id, ca.currency
        """
    )
    fun getAccountBalanceFlow(
        accountId: Long
    ): Flow<AccountBalance?>


    /**
     * حساب أرصدة جميع حسابات شخص معين.
     *
     * النتيجة ستكون مثلاً:
     *
     * YER -> لي / علي / الرصيد
     * SAR -> لي / علي / الرصيد
     * USD -> لي / علي / الرصيد
     */
    @Query(
        """
        SELECT
            ca.id AS accountId,
            ca.currency AS currency,

            COALESCE(
                SUM(
                    CASE
                        WHEN t.type = 'RECEIVABLE'
                        THEN t.amountMinor
                        ELSE 0
                    END
                ),
                0
            ) AS totalReceivable,

            COALESCE(
                SUM(
                    CASE
                        WHEN t.type = 'PAYABLE'
                        THEN t.amountMinor
                        ELSE 0
                    END
                ),
                0
            ) AS totalPayable

        FROM currency_accounts ca

        LEFT JOIN transactions t
            ON t.currencyAccountId = ca.id

        WHERE ca.personId = :personId

        GROUP BY ca.id, ca.currency

        ORDER BY
            CASE ca.currency
                WHEN 'YER' THEN 1
                WHEN 'SAR' THEN 2
                WHEN 'USD' THEN 3
                ELSE 4
            END
        """
    )
    fun getAccountBalancesForPersonFlow(
        personId: Long
    ): Flow<List<AccountBalance>>
}
