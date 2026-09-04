package com.myaccounts.app.data.custody

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CustodyDao {
    @Query("SELECT * FROM custodies WHERE isArchived = 0 ORDER BY createdAt DESC, id DESC") fun observeCustodies(): Flow<List<CustodyEntity>>
    @Query("SELECT * FROM custodies WHERE id = :id LIMIT 1") fun observeCustody(id: Long): Flow<CustodyEntity?>
    @Query("SELECT * FROM custody_persons WHERE custodyId = :custodyId AND isArchived = 0 ORDER BY name COLLATE NOCASE ASC") fun observePersons(custodyId: Long): Flow<List<CustodyPersonEntity>>
    @Query("SELECT * FROM custody_accounts WHERE custodyId = :custodyId ORDER BY holderType ASC, personId ASC, currencyCode ASC") fun observeAccounts(custodyId: Long): Flow<List<CustodyAccountEntity>>
    @Query("SELECT * FROM custody_transactions WHERE custodyId = :custodyId AND isArchived = 0 ORDER BY transactionDate DESC, id DESC") fun observeTransactions(custodyId: Long): Flow<List<CustodyTransactionEntity>>
    @Query("SELECT * FROM custody_transactions WHERE accountId = :accountId AND isArchived = 0 ORDER BY transactionDate DESC, id DESC") fun observeAccountTransactions(accountId: Long): Flow<List<CustodyTransactionEntity>>
    @Query("SELECT * FROM custody_transactions WHERE custodyId = :custodyId AND personId = :personId AND currencyCode = :currency AND isArchived = 0 ORDER BY transactionDate DESC, id DESC") fun observePersonTransactions(custodyId: Long, personId: Long, currency: String): Flow<List<CustodyTransactionEntity>>
    @Query("SELECT COALESCE(SUM(CASE WHEN type IN ('RECEIVED_FROM_ORG','RETURNED_FROM_PERSON') THEN amountMinor ELSE -amountMinor END),0) FROM custody_transactions WHERE accountId = :accountId AND isArchived = 0") fun observeBalance(accountId: Long): Flow<Long>
    @Query("SELECT * FROM custodies WHERE externalId = :externalId LIMIT 1") suspend fun getCustodyByExternalId(externalId: String): CustodyEntity?
    @Query("SELECT * FROM custody_persons WHERE custodyId = :custodyId AND externalId = :externalId LIMIT 1") suspend fun getPersonByExternalId(custodyId: Long, externalId: String): CustodyPersonEntity?
    @Query("SELECT * FROM custody_transactions WHERE externalId = :externalId LIMIT 1") suspend fun getTransactionByExternalId(externalId: String): CustodyTransactionEntity?
    @Query("SELECT * FROM custodies WHERE id = :id LIMIT 1") suspend fun getCustody(id: Long): CustodyEntity?
    @Query("SELECT * FROM custodies WHERE isArchived = :archived ORDER BY createdAt DESC, id DESC") suspend fun getAllCustodies(archived: Boolean): List<CustodyEntity>
    @Query("SELECT * FROM custody_persons WHERE custodyId = :custodyId AND isArchived = 0 ORDER BY id ASC") suspend fun getAllPersons(custodyId: Long): List<CustodyPersonEntity>
    @Query("SELECT * FROM custody_accounts WHERE custodyId = :custodyId ORDER BY id ASC") suspend fun getAllAccounts(custodyId: Long): List<CustodyAccountEntity>
    @Query("SELECT * FROM custody_transactions WHERE custodyId = :custodyId AND isArchived = :archived ORDER BY id ASC") suspend fun getAllTransactions(custodyId: Long, archived: Boolean): List<CustodyTransactionEntity>
    @Query("SELECT * FROM custody_accounts WHERE custodyId = :custodyId AND holderType = 'OWNER' AND personId IS NULL AND currencyCode = :currency LIMIT 1") suspend fun getOwnerAccount(custodyId: Long, currency: String): CustodyAccountEntity?
    @Query("SELECT * FROM custody_accounts WHERE custodyId = :custodyId AND holderType = 'PERSON' AND personId = :personId AND currencyCode = :currency LIMIT 1") suspend fun getPersonAccount(custodyId: Long, personId: Long, currency: String): CustodyAccountEntity?
    @Query("SELECT * FROM custody_accounts WHERE id = :id LIMIT 1") suspend fun getAccount(id: Long): CustodyAccountEntity?
    @Query("SELECT * FROM custody_transactions WHERE id = :id LIMIT 1") suspend fun getTransaction(id: Long): CustodyTransactionEntity?
    @Query("SELECT * FROM custody_persons WHERE id = :id LIMIT 1") suspend fun getPerson(id: Long): CustodyPersonEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCustody(custody: CustodyEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPerson(person: CustodyPersonEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAccounts(accounts: List<CustodyAccountEntity>)
    @Query("""
        INSERT OR IGNORE INTO custody_accounts(custodyId, holderType, personId, currencyCode, balanceMinor, createdAt, updatedAt)
        SELECT custodyId, holderType, personId, :currencyCode, 0, :now, :now FROM custody_accounts
    """) suspend fun addCurrencyToAllAccounts(currencyCode: String, now: Long = System.currentTimeMillis())
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTransaction(transaction: CustodyTransactionEntity): Long
    @Update suspend fun updateCustody(custody: CustodyEntity)
    @Update suspend fun updatePerson(person: CustodyPersonEntity)
    @Update suspend fun updateTransaction(transaction: CustodyTransactionEntity)
    @Query("UPDATE custody_accounts SET balanceMinor = balanceMinor + :delta, updatedAt = :updatedAt WHERE id = :accountId") suspend fun adjustAccountBalance(accountId: Long, delta: Long, updatedAt: Long)
    @Query("DELETE FROM custody_transactions WHERE id = :id") suspend fun deleteTransaction(id: Long)
    @Query("UPDATE custodies SET isArchived = 1, archivedAt = :at WHERE id = :id") suspend fun archiveCustody(id: Long, at: Long)
    @Query("UPDATE custodies SET isArchived = 0, archivedAt = NULL WHERE id = :id") suspend fun restoreCustody(id: Long)
    @Query("DELETE FROM custody_transactions WHERE custodyId = :id") suspend fun deleteTransactions(id: Long)
    @Query("DELETE FROM custody_accounts WHERE custodyId = :id") suspend fun deleteAccounts(id: Long)
    @Query("DELETE FROM custody_persons WHERE custodyId = :id") suspend fun deletePersons(id: Long)
    @Query("DELETE FROM custodies WHERE id = :id") suspend fun deleteCustody(id: Long)
}
