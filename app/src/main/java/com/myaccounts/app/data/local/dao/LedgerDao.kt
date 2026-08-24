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

    @Query("""
        SELECT * FROM people
        WHERE isActive = 1
        AND (
            name LIKE '%' || :query || '%'
            OR phone LIKE '%' || :query || '%'
            OR address LIKE '%' || :query || '%'
            OR notes LIKE '%' || :query || '%'
        )
        ORDER BY name COLLATE NOCASE ASC
    """)
    fun observePeople(query: String): Flow<List<PersonEntity>>

    @Query("""
        SELECT * FROM people
        WHERE id = :personId
        LIMIT 1
    """)
    fun observePerson(personId: Long): Flow<PersonEntity?>

    @Transaction
    @Query("""
        SELECT * FROM people
        WHERE isActive = 1
        ORDER BY
            CASE WHEN (
                SELECT MAX(t.createdAt)
                FROM transactions t
                INNER JOIN currency_accounts ca ON ca.id = t.accountId
                WHERE ca.personId = people.id
                  AND t.isArchived = 0
            ) IS NULL THEN 0 ELSE 1 END DESC,
            (
                SELECT MAX(t.createdAt)
                FROM transactions t
                INNER JOIN currency_accounts ca ON ca.id = t.accountId
                WHERE ca.personId = people.id
                  AND t.isArchived = 0
            ) DESC,
            createdAt DESC,
            id DESC
    """)
    fun observePersonsWithAccounts(): Flow<List<PersonWithAccounts>>

    @Transaction
    @Query("""
        SELECT * FROM people
        WHERE isActive = 0
        ORDER BY name COLLATE NOCASE ASC
    """)
    fun observeArchivedPersonsWithAccounts(): Flow<List<PersonWithAccounts>>

    @Transaction
    @Query("""
        SELECT * FROM people
        WHERE id = :personId
        AND isActive = 1
        LIMIT 1
    """)
    fun observePersonWithAccounts(personId: Long): Flow<PersonWithAccounts?>

    @Insert
    suspend fun insertPerson(person: PersonEntity): Long

    @Update
    suspend fun updatePersonEntity(person: PersonEntity)

    @Query("""
        SELECT id FROM people
        WHERE isActive = 1
          AND TRIM(name) = TRIM(:name) COLLATE NOCASE
        ORDER BY id ASC
        LIMIT 1
    """)
    suspend fun findActivePersonIdByName(name: String): Long?

    @Transaction
    suspend fun updatePerson(person: PersonEntity) {
        if (person.isActive) {
            val existingId = findActivePersonIdByName(person.name)
            require(existingId == null || existingId == person.id) {
                "يوجد حساب نشط بهذا الاسم. غيّر الاسم أو استعد الحساب المؤرشف."
            }
        }
        updatePersonEntity(person)
    }

    @Query("""
        UPDATE people
        SET isActive = 0
        WHERE id = :personId
    """)
    suspend fun softDeletePerson(personId: Long)

    @Query("""
        UPDATE people
        SET isActive = 1
        WHERE id = :personId
    """)
    suspend fun restorePerson(personId: Long)

    @Query("""
        DELETE FROM people
        WHERE id = :personId
    """)
    suspend fun permanentlyDeletePerson(personId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCurrencyAccounts(accounts: List<CurrencyAccountEntity>)

    @Query("""
        SELECT * FROM currency_accounts
        WHERE personId = :personId
        ORDER BY currencyCode ASC
    """)
    fun observeCurrencyAccounts(personId: Long): Flow<List<CurrencyAccountEntity>>

    @Query("""
        SELECT * FROM currency_accounts
        WHERE id = :accountId
        LIMIT 1
    """)
    fun observeCurrencyAccount(accountId: Long): Flow<CurrencyAccountEntity?>

    @Query("""
        SELECT * FROM currency_accounts
        WHERE personId = :personId
        AND currencyCode = :currencyCode
        LIMIT 1
    """)
    suspend fun getCurrencyAccount(personId: Long, currencyCode: String): CurrencyAccountEntity?

    @Update
    suspend fun updateCurrencyAccount(account: CurrencyAccountEntity)

    @Query("""
        UPDATE currency_accounts
        SET balanceMinor = :balanceMinor,
            updatedAt = :updatedAt
        WHERE id = :accountId
    """)
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
        require(findActivePersonIdByName(person.name) == null) {
            "يوجد حساب نشط بهذا الاسم. غيّر الاسم أو استعد الحساب المؤرشف."
        }
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
