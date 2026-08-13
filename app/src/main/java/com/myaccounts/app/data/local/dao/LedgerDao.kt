package com.myaccounts.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.myaccounts.app.data.local.CurrencyAccountEntity
import com.myaccounts.app.data.local.PersonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {

    @Query(
        """
        SELECT * FROM people
        WHERE isActive = 1
        AND (
            name LIKE '%' || :query || '%'
            OR phone LIKE '%' || :query || '%'
            OR address LIKE '%' || :query || '%'
            OR notes LIKE '%' || :query || '%'
        )
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    fun observePeople(query: String): Flow<List<PersonEntity>>

    @Query(
        """
        SELECT * FROM people
        WHERE id = :personId
        LIMIT 1
        """
    )
    fun observePerson(personId: Long): Flow<PersonEntity?>

    @Transaction
    @Query(
        """
        SELECT * FROM people
        WHERE isActive = 1
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    fun observePersonsWithAccounts(): Flow<List<PersonWithAccounts>>

    @Transaction
    @Query(
        """
        SELECT * FROM people
        WHERE id = :personId
        AND isActive = 1
        LIMIT 1
        """
    )
    fun observePersonWithAccounts(
        personId: Long
    ): Flow<PersonWithAccounts?>

    @Insert
    suspend fun insertPerson(
        person: PersonEntity
    ): Long

    @Update
    suspend fun updatePerson(
        person: PersonEntity
    )

    @Query(
        """
        UPDATE people
        SET isActive = 0
        WHERE id = :personId
        """
    )
    suspend fun softDeletePerson(
        personId: Long
    )

    @Insert(
        onConflict = OnConflictStrategy.IGNORE
    )
    suspend fun insertCurrencyAccounts(
        accounts: List<CurrencyAccountEntity>
    )

    @Query(
        """
        SELECT * FROM currency_accounts
        WHERE personId = :personId
        ORDER BY currencyCode ASC
        """
    )
    fun observeCurrencyAccounts(
        personId: Long
    ): Flow<List<CurrencyAccountEntity>>

    @Query(
        """
        SELECT * FROM currency_accounts
        WHERE id = :accountId
        LIMIT 1
        """
    )
    fun observeCurrencyAccount(
        accountId: Long
    ): Flow<CurrencyAccountEntity?>

    @Query(
        """
        SELECT * FROM currency_accounts
        WHERE personId = :personId
        AND currencyCode = :currencyCode
        LIMIT 1
        """
    )
    suspend fun getCurrencyAccount(
        personId: Long,
        currencyCode: String
    ): CurrencyAccountEntity?

    @Update
    suspend fun updateCurrencyAccount(
        account: CurrencyAccountEntity
    )

    @Query(
        """
        UPDATE currency_accounts
        SET balanceMinor = :balanceMinor,
            updatedAt = :updatedAt
        WHERE id = :accountId
        """
    )
    suspend fun updateCurrencyBalance(
        accountId: Long,
        balanceMinor: Long,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Transaction
    suspend fun insertPersonWithCurrencyAccounts(
        person: PersonEntity,
        currencyCodes: List<String>
    ): Long {
        val personId = insertPerson(person)

        insertCurrencyAccounts(
            currencyCodes.map { currencyCode ->
                CurrencyAccountEntity(
                    personId = personId,
                    currencyCode = currencyCode,
                    balanceMinor = 0L
                )
            }
        )

        return personId
    }
}
