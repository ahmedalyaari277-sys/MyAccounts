package com.myaccounts.app.data.custody

import androidx.room.withTransaction
import com.myaccounts.app.data.local.AppDatabase
import kotlinx.coroutines.flow.Flow

object CustodyTransactionType {
    const val RECEIVED_FROM_ORG = "RECEIVED_FROM_ORG"
    const val PAID_TO_PERSON = "PAID_TO_PERSON"
    const val RETURNED_FROM_PERSON = "RETURNED_FROM_PERSON"
    const val RETURNED_TO_ORG = "RETURNED_TO_ORG"
}

object CustodyBalanceRules {
    fun ownerDelta(type: String, amount: Long): Long = when (type) {
        CustodyTransactionType.RECEIVED_FROM_ORG, CustodyTransactionType.RETURNED_FROM_PERSON -> amount
        CustodyTransactionType.PAID_TO_PERSON, CustodyTransactionType.RETURNED_TO_ORG -> -amount
        else -> error("نوع عملية عهدة غير معروف")
    }
    fun personDelta(type: String, amount: Long): Long = when (type) {
        CustodyTransactionType.PAID_TO_PERSON -> amount
        CustodyTransactionType.RETURNED_FROM_PERSON -> -amount
        else -> 0L
    }
}

class CustodyRepository(private val db: AppDatabase) {
    private val dao = db.custodyDao()
    private val currencies = listOf("YER", "SAR", "USD")
    fun observeCustodies(): Flow<List<CustodyEntity>> = dao.observeCustodies()
    fun observeCustody(id: Long): Flow<CustodyEntity?> = dao.observeCustody(id)
    fun observePersons(id: Long): Flow<List<CustodyPersonEntity>> = dao.observePersons(id)
    fun observeAccounts(id: Long): Flow<List<CustodyAccountEntity>> = dao.observeAccounts(id)
    fun observeTransactions(id: Long): Flow<List<CustodyTransactionEntity>> = dao.observeTransactions(id)
    fun observeAccountTransactions(id: Long): Flow<List<CustodyTransactionEntity>> = dao.observeAccountTransactions(id)
    fun observePersonTransactions(id: Long, personId: Long, currency: String): Flow<List<CustodyTransactionEntity>> = dao.observePersonTransactions(id, personId, currency)
    fun observeBalance(id: Long): Flow<Long> = dao.observeBalance(id)

    suspend fun createCustody(c: CustodyEntity): Long = db.withTransaction {
        val id = dao.insertCustody(c)
        dao.insertAccounts(currencies.map { CustodyAccountEntity(custodyId = id, holderType = "OWNER", currencyCode = it) })
        id
    }

    suspend fun addPerson(custodyId: Long, p: CustodyPersonEntity): Long = db.withTransaction {
        val id = dao.insertPerson(p.copy(custodyId = custodyId))
        dao.insertAccounts(currencies.map { CustodyAccountEntity(custodyId = custodyId, holderType = "PERSON", personId = id, currencyCode = it) })
        id
    }

    suspend fun addTransaction(custodyId: Long, currency: String, type: String, personId: Long?, amountMinor: Long, description: String, date: Long): Long = db.withTransaction {
        require(currency in currencies)
        require(amountMinor > 0)
        val personOperation = type == CustodyTransactionType.PAID_TO_PERSON || type == CustodyTransactionType.RETURNED_FROM_PERSON
        require(personOperation == (personId != null))
        if (personId != null) require(dao.getPerson(personId)?.let { it.custodyId == custodyId && !it.isArchived } == true)
        val ownerAccount = dao.getOwnerAccount(custodyId, currency) ?: error("حساب صاحب العهدة للعملة غير موجود")
        dao.insertTransaction(CustodyTransactionEntity(custodyId = custodyId, accountId = ownerAccount.id, personId = personId, currencyCode = currency, type = type, amountMinor = amountMinor, description = description.trim(), transactionDate = date))
    }

    suspend fun updateTransaction(id: Long, type: String, amountMinor: Long, description: String, date: Long) {
        require(amountMinor > 0)
        val old = dao.getTransaction(id) ?: return
        val personOperation = type == CustodyTransactionType.PAID_TO_PERSON || type == CustodyTransactionType.RETURNED_FROM_PERSON
        require(personOperation == (old.personId != null))
        dao.updateTransaction(old.copy(type = type, amountMinor = amountMinor, description = description.trim(), transactionDate = date))
    }
    suspend fun archive(id: Long) = dao.archiveCustody(id, System.currentTimeMillis())
    suspend fun delete(id: Long) = db.withTransaction { dao.deleteTransactions(id); dao.deleteAccounts(id); dao.deletePersons(id); dao.deleteCustody(id) }
}
