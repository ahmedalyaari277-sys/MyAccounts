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

    @Transaction
    @Query(
        """
        SELECT *
        FROM persons
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    fun getAllPersonsWithAccountsFlow(): Flow<List<PersonWithAccounts>>

    @Transaction
    @Query(
        """
        SELECT *
        FROM persons
        WHERE id = :personId
        LIMIT 1
        """
    )
    suspend fun getPersonWithAccounts(
        personId: Long
    ): PersonWithAccounts?

    @Insert(
        onConflict = OnConflictStrategy.ABORT
    )
    suspend fun insertPerson(
        person: PersonEntity
    ): Long

    @Insert(
        onConflict = OnConflictStrategy.ABORT
    )
    suspend fun insertCurrencyAccounts(
        accounts: List<CurrencyAccountEntity>
    )

    @Transaction
    suspend fun insertPersonWithAccounts(
        person: PersonEntity,
        accounts: List<CurrencyAccountEntity>
    ): Long {
        val personId = insertPerson(person)

        val accountsForPerson = accounts.map { account ->
            account.copy(
                personId = personId
            )
        }

        insertCurrencyAccounts(accountsForPerson)

        return personId
    }

    @Query(
        """
        DELETE FROM persons
        WHERE id = :personId
        """
    )
    suspend fun deletePerson(
        personId: Long
    )

    @Insert(
        onConflict = OnConflictStrategy.ABORT
    )
    suspend fun insertTransaction(
        transaction: TransactionEntity
    ): Long
}
