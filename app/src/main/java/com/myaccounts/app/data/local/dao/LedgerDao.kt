package com.myaccounts.app.data.local.dao

import androidx.room.*
import com.myaccounts.app.data.local.entity.CurrencyAccountEntity
import com.myaccounts.app.data.local.entity.PersonEntity
import com.myaccounts.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

data class PersonWithAccounts(
    @Embedded val person: PersonEntity,
    @Relation(parentColumn = "id", entityColumn = "personId")
    val accounts: List<CurrencyAccountEntity>
)

@Dao
interface LedgerDao {
    @Transaction
    @Query("SELECT * FROM persons ORDER BY name ASC")
    fun getAllPersonsWithAccountsFlow(): Flow<List<PersonWithAccounts>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPerson(person: PersonEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCurrencyAccounts(accounts: List<CurrencyAccountEntity>)

    @Query("DELETE FROM persons WHERE id = :personId")
    suspend fun deletePerson(personId: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: TransactionEntity): Long
}
