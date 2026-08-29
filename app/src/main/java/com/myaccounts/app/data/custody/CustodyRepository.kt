package com.myaccounts.app.data.custody

import android.content.Context
import androidx.room.withTransaction
import com.myaccounts.app.util.CustodyAttachmentStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

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

class CustodyRepository(private val db: com.myaccounts.app.data.local.AppDatabase, context: Context) {
    private val dao = db.custodyDao()
    private val attachmentStore = CustodyAttachmentStore(context.applicationContext)
    private val currencies = listOf("YER", "SAR", "USD")
    private val allowedTypes = setOf(CustodyTransactionType.RECEIVED_FROM_ORG, CustodyTransactionType.PAID_TO_PERSON, CustodyTransactionType.RETURNED_FROM_PERSON, CustodyTransactionType.RETURNED_TO_ORG)

    fun observeCustodies(): Flow<List<CustodyEntity>> = dao.observeCustodies()
    fun observeCustody(id: Long): Flow<CustodyEntity?> = dao.observeCustody(id)
    fun observePersons(id: Long): Flow<List<CustodyPersonEntity>> = dao.observePersons(id)
    fun observeAccounts(id: Long): Flow<List<CustodyAccountEntity>> = dao.observeAccounts(id)
    fun observeTransactions(id: Long): Flow<List<CustodyTransactionEntity>> = dao.observeTransactions(id)
    fun observeAccountTransactions(id: Long): Flow<List<CustodyTransactionEntity>> = dao.observeAccountTransactions(id)
    fun observePersonTransactions(id: Long, personId: Long, currency: String): Flow<List<CustodyTransactionEntity>> = dao.observePersonTransactions(id, personId, currency)
    fun observeBalance(id: Long): Flow<Long> = dao.observeBalance(id)
    fun attachments(transactionId: Long): List<CustodyTransactionAttachmentEntity> = attachmentStore.list(transactionId)

    suspend fun createCustody(c: CustodyEntity): Long = db.withTransaction {
        val id = dao.insertCustody(c.copy(name = c.name.trim(), organizationName = c.organizationName.trim()))
        dao.insertAccounts(currencies.map { CustodyAccountEntity(custodyId = id, holderType = "OWNER", currencyCode = it) })
        id
    }

    suspend fun updateCustody(c: CustodyEntity) = dao.updateCustody(c.copy(name = c.name.trim(), organizationName = c.organizationName.trim()))

    suspend fun addPerson(custodyId: Long, p: CustodyPersonEntity): Long = db.withTransaction {
        require(dao.getCustody(custodyId) != null) { "العهدة غير موجودة" }
        val id = dao.insertPerson(p.copy(custodyId = custodyId, name = p.name.trim()))
        dao.insertAccounts(currencies.map { CustodyAccountEntity(custodyId = custodyId, holderType = "PERSON", personId = id, currencyCode = it) })
        id
    }

    suspend fun updatePerson(p: CustodyPersonEntity) = dao.updatePerson(p.copy(name = p.name.trim()))

    suspend fun addTransaction(custodyId: Long, currency: String, type: String, personId: Long?, amountMinor: Long, description: String, date: Long, attachments: List<CustodyAttachmentStorage.Selected> = emptyList()): Long = withContext(Dispatchers.IO) {
        require(currency in currencies)
        require(type in allowedTypes)
        require(amountMinor > 0)
        val personOperation = type == CustodyTransactionType.PAID_TO_PERSON || type == CustodyTransactionType.RETURNED_FROM_PERSON
        require(personOperation == (personId != null))
        if (personId != null) require(dao.getPerson(personId)?.let { it.custodyId == custodyId && !it.isArchived } == true) { "الشخص لا ينتمي إلى هذه العهدة" }
        val ownerAccount = dao.getOwnerAccount(custodyId, currency) ?: error("حساب صاحب العهدة للعملة غير موجود")
        var transactionId = 0L
        try {
            transactionId = dao.insertTransaction(CustodyTransactionEntity(custodyId = custodyId, accountId = ownerAccount.id, personId = personId, currencyCode = currency, type = type, amountMinor = amountMinor, description = description.trim(), transactionDate = date))
            attachmentStore.save(transactionId, attachments)
            transactionId
        } catch (e: Throwable) {
            if (transactionId != 0L) { attachmentStore.deleteForTransaction(transactionId); runCatching { dao.deleteTransaction(transactionId) } }
            throw e
        }
    }

    suspend fun updateTransaction(id: Long, currency: String, type: String, personId: Long?, amountMinor: Long, description: String, date: Long, newAttachments: List<CustodyAttachmentStorage.Selected> = emptyList(), deletedAttachments: List<CustodyTransactionAttachmentEntity> = emptyList()) = withContext(Dispatchers.IO) {
        require(currency in currencies)
        require(type in allowedTypes)
        require(amountMinor > 0)
        val old = dao.getTransaction(id) ?: return@withContext
        val personOperation = type == CustodyTransactionType.PAID_TO_PERSON || type == CustodyTransactionType.RETURNED_FROM_PERSON
        require(personOperation == (personId != null))
        if (personId != null) require(dao.getPerson(personId)?.let { it.custodyId == old.custodyId && !it.isArchived } == true)
        val account = dao.getOwnerAccount(old.custodyId, currency) ?: error("حساب صاحب العهدة للعملة غير موجود")
        dao.updateTransaction(old.copy(accountId = account.id, currencyCode = currency, type = type, personId = personId, amountMinor = amountMinor, description = description.trim(), transactionDate = date))
        deletedAttachments.forEach { attachmentStore.delete(it) }
        attachmentStore.save(id, newAttachments)
    }

    suspend fun deleteTransaction(id: Long) {
        dao.getTransaction(id) ?: return
        attachmentStore.deleteForTransaction(id)
        dao.deleteTransaction(id)
    }

    suspend fun archive(id: Long) = dao.archiveCustody(id, System.currentTimeMillis())
    suspend fun delete(id: Long) = db.withTransaction { dao.deleteTransactions(id); dao.deleteAccounts(id); dao.deletePersons(id); dao.deleteCustody(id) }
}
