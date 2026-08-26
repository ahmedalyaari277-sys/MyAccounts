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
        AND (name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%' OR address LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%')
        ORDER BY name COLLATE NOCASE ASC
    """)
    fun observePeople(query: String): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE id = :personId LIMIT 1")
    fun observePerson(personId: Long): Flow<PersonEntity?>

    @Transaction
    @Query("""
        SELECT * FROM people
        WHERE isActive = 1
        ORDER BY
            CASE WHEN (SELECT MAX(t.createdAt) FROM transactions t INNER JOIN currency_accounts ca ON ca.id = t.accountId WHERE ca.personId = people.id) IS NULL THEN 0 ELSE 1 END DESC,
            (SELECT MAX(t.createdAt) FROM transactions t INNER JOIN currency_accounts ca ON ca.id = t.accountId WHERE ca.personId = people.id) DESC,
            createdAt DESC,
            id DESC
    """)
    fun observePersonsWithAccounts(): Flow<List<PersonWithAccounts>>

    @Transaction
    @Query("SELECT * FROM people WHERE isActive = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeArchivedPersonsWithAccounts(): Flow<List<PersonWithAccounts>>

    @Transaction
    @Query("SELECT * FROM people WHERE id = :personId AND isActive = 1 LIMIT 1")
    fun observePersonWithAccounts(personId: Long): Flow<PersonWithAccounts?>

    @Query("SELECT * FROM people WHERE id = :personId LIMIT 1")
    suspend fun getPersonForArchive(personId: Long): PersonEntity?

    @Query("SELECT * FROM people WHERE externalId = :externalId LIMIT 1")
    suspend fun getPersonByExternalId(externalId: String): PersonEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM people WHERE isActive = 1 AND id != :excludedPersonId AND name = :name COLLATE NOCASE)")
    suspend fun hasActivePersonWithName(name: String, excludedPersonId: Long): Boolean

    @Insert
    suspend fun insertPerson(person: PersonEntity): Long

    @Update
    suspend fun updatePerson(person: PersonEntity)

    @Query("UPDATE people SET isActive = 0, archivedAt = :archivedAt WHERE id = :personId AND isActive = 1")
    suspend fun archivePerson(personId: Long, archivedAt: Long): Int

    @Query("UPDATE people SET isActive = 1, archivedAt = NULL WHERE id = :personId AND isActive = 0")
    suspend fun restorePerson(personId: Long): Int

    @Query("DELETE FROM people WHERE id = :personId")
    suspend fun permanentlyDeletePerson(personId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCurrencyAccounts(accounts: List<CurrencyAccountEntity>)

    @Query("SELECT * FROM currency_accounts WHERE personId = :personId ORDER BY currencyCode ASC")
    fun observeCurrencyAccounts(personId: Long): Flow<List<CurrencyAccountEntity>>

    @Query("SELECT * FROM currency_accounts WHERE id = :accountId LIMIT 1")
    fun observeCurrencyAccount(accountId: Long): Flow<CurrencyAccountEntity?>

    @Query("SELECT * FROM currency_accounts WHERE personId = :personId AND currencyCode = :currencyCode LIMIT 1")
    suspend fun getCurrencyAccount(personId: Long, currencyCode: String): CurrencyAccountEntity?

    @Update
    suspend fun updateCurrencyAccount(account: CurrencyAccountEntity)

    @Query("UPDATE currency_accounts SET balanceMinor = :balanceMinor, updatedAt = :updatedAt WHERE id = :accountId")
    suspend fun updateCurrencyBalance(accountId: Long, balanceMinor: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("""
        SELECT
            p.externalId AS personExternalId,
            t.externalId AS transactionExternalId,
            p.name AS name,
            p.phone AS phone,
            p.address AS address,
            p.notes AS notes,
            ca.currencyCode AS currencyCode,
            t.type AS transactionType,
            t.amountMinor AS amountMinor,
            t.description AS description,
            t.transactionDate AS transactionDate
        FROM people p
        INNER JOIN currency_accounts ca ON ca.personId = p.id
        LEFT JOIN transactions t ON t.accountId = ca.id
        WHERE p.isActive = 1
          AND p.archivedAt IS NULL
        ORDER BY p.id ASC, ca.currencyCode ASC, t.transactionDate ASC, t.id ASC
    """)
    suspend fun getActiveExcelRows(): List<ExcelExportRow>

    @Transaction
    suspend fun insertPersonWithCurrencyAccounts(person: PersonEntity, currencyCodes: List<String>): Long {
        val personId = insertPerson(person)
        insertCurrencyAccounts(currencyCodes.map { CurrencyAccountEntity(personId = personId, currencyCode = it, balanceMinor = 0L) })
        return personId
    }
}
