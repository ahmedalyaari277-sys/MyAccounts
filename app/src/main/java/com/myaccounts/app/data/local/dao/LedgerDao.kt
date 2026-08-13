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
 * بيانات الشخص مع جميع حساباته بالعملات المختلفة.
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

@Dao
interface LedgerDao {

    /**
     * الحصول على جميع الأشخاص مع حساباتهم.
     */
    @Transaction
    @Query(
        "SELECT * FROM persons ORDER BY name COLLATE NOCASE ASC"
    )
    fun getAllPersonsWithAccountsFlow():
            Flow<List<PersonWithAccounts>>

    /**
     * الحصول على شخص واحد مع جميع حساباته.
     */
    @Transaction
    @Query(
        "SELECT * FROM persons WHERE id = :personId LIMIT 1"
    )
    suspend fun getPersonWithAccounts(
        personId: Long
    ): PersonWithAccounts?

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
     * حذف شخص.
     *
     * الحذف المتسلسل CASCADE في قاعدة البيانات
     * سيحذف الحسابات والمعاملات المرتبطة به.
     */
    @Query(
        "DELETE FROM persons WHERE id = :personId"
    )
    suspend fun deletePerson(
        personId: Long
    )

    /**
     * إضافة معاملة مالية.
     */
    @Insert(
        onConflict = OnConflictStrategy.ABORT
    )
    suspend fun insertTransaction(
        transaction: TransactionEntity
    ): Long
}
