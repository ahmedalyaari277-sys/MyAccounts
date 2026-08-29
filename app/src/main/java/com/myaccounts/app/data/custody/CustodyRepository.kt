package com.myaccounts.app.data.custody

import androidx.room.withTransaction
import com.myaccounts.app.data.local.AppDatabase
import kotlinx.coroutines.flow.Flow

class CustodyRepository(private val db: AppDatabase) {
    private val dao = db.custodyDao()
    private val currencies = listOf("YER", "SAR", "USD")
    fun observeCustodies(): Flow<List<CustodyEntity>> = dao.observeCustodies()
    fun observeCustody(id: Long): Flow<CustodyEntity?> = dao.observeCustody(id)
    fun observePersons(id: Long): Flow<List<CustodyPersonEntity>> = dao.observePersons(id)
    fun observeAccounts(id: Long): Flow<List<CustodyAccountEntity>> = dao.observeAccounts(id)
    fun observeTransactions(id: Long): Flow<List<CustodyTransactionEntity>> = dao.observeTransactions(id)
    fun observeAccountTransactions(id: Long): Flow<List<CustodyTransactionEntity>> = dao.observeAccountTransactions(id)
    fun observeBalance(id: Long): Flow<Long> = dao.observeBalance(id)
    suspend fun createCustody(c: CustodyEntity): Long = db.withTransaction { val id = dao.insertCustody(c); dao.insertAccounts(currencies.map { CustodyAccountEntity(custodyId=id, holderType="OWNER", currencyCode=it) }); id }
    suspend fun addPerson(custodyId: Long, p: CustodyPersonEntity): Long = db.withTransaction { val id=dao.insertPerson(p.copy(custodyId=custodyId)); dao.insertAccounts(currencies.map { CustodyAccountEntity(custodyId=custodyId, holderType="PERSON", personId=id, currencyCode=it) }); id }
    suspend fun addTransaction(custodyId: Long, currency: String, type: String, personId: Long?, amountMinor: Long, description: String, date: Long): Long = db.withTransaction {
        require(amountMinor > 0)
        val account = if (personId == null) dao.getOwnerAccount(custodyId,currency) else dao.getPersonAccount(custodyId,personId,currency)
        ?: error("حساب العملة غير موجود")
        dao.insertTransaction(CustodyTransactionEntity(custodyId=custodyId,accountId=account.id,personId=personId,currencyCode=currency,type=type,amountMinor=amountMinor,description=description.trim(),transactionDate=date))
    }
    suspend fun updateTransaction(id: Long,type: String,amountMinor: Long,description: String,date: Long) { dao.getTransaction(id)?.let { dao.updateTransaction(it.copy(type=type,amountMinor=amountMinor,description=description.trim(),transactionDate=date)) } }
    suspend fun archive(id: Long) = dao.archiveCustody(id,System.currentTimeMillis())
    suspend fun delete(id: Long) = db.withTransaction { dao.deleteTransactions(id);dao.deleteAccounts(id);dao.deletePersons(id);dao.deleteCustody(id) }
}
