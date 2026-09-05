package com.myaccounts.app.data.custody

import android.content.Context
import androidx.room.withTransaction
import com.myaccounts.app.data.currency.CurrencyCatalog
import com.myaccounts.app.data.local.AppDatabase
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

class CustodyRepository(private val db: AppDatabase, context: Context) {
    private val dao = db.custodyDao()
    private val attachmentStore = CustodyAttachmentStore(context.applicationContext)
    private fun currencies(): List<String> = CurrencyCatalog.enabledCodes()

    private fun isAllowedType(type: String): Boolean =
        type == CustodyTransactionType.RECEIVED_FROM_ORG ||
            type == CustodyTransactionType.PAID_TO_PERSON ||
            type == CustodyTransactionType.RETURNED_FROM_PERSON ||
            type == CustodyTransactionType.RETURNED_TO_ORG

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
        require(c.name.isNotBlank()) { "اسم صاحب العهدة مطلوب" }
        require(c.organizationName.isNotBlank()) { "اسم الجهة مطلوب" }
        val id = dao.insertCustody(c.copy(name = c.name.trim(), organizationName = c.organizationName.trim()))
        dao.insertAccounts(currencies().map { CustodyAccountEntity(custodyId = id, holderType = "OWNER", currencyCode = it) })
        id
    }

    suspend fun updateCustody(c: CustodyEntity) {
        require(c.name.isNotBlank()) { "اسم صاحب العهدة مطلوب" }
        require(c.organizationName.isNotBlank()) { "اسم الجهة مطلوب" }
        dao.updateCustody(c.copy(name = c.name.trim(), organizationName = c.organizationName.trim()))
    }

    suspend fun addPerson(custodyId: Long, p: CustodyPersonEntity): Long = db.withTransaction {
        require(dao.getCustody(custodyId) != null) { "العهدة غير موجودة" }
        require(p.name.isNotBlank()) { "اسم الشخص مطلوب" }
        val id = dao.insertPerson(p.copy(custodyId = custodyId, name = p.name.trim()))
        dao.insertAccounts(currencies().map { CustodyAccountEntity(custodyId = custodyId, holderType = "PERSON", personId = id, currencyCode = it) })
        id
    }

    suspend fun updatePerson(p: CustodyPersonEntity) {
        require(p.name.isNotBlank()) { "اسم الشخص مطلوب" }
        dao.updatePerson(p.copy(name = p.name.trim()))
    }

    suspend fun addTransaction(custodyId: Long, currency: String, type: String, personId: Long?, amountMinor: Long, description: String, date: Long, attachments: List<CustodyAttachmentStorage.Selected> = emptyList()): Long = withContext(Dispatchers.IO) {
        require(currency in currencies())
        require(isAllowedType(type))
        require(amountMinor > 0)
        val personOperation = type == CustodyTransactionType.PAID_TO_PERSON || type == CustodyTransactionType.RETURNED_FROM_PERSON
        require(personOperation == (personId != null))
        require(dao.getCustody(custodyId)?.isArchived == false) { "العهدة غير موجودة أو مؤرشفة" }
        if (personId != null) require(dao.getPerson(personId)?.let { it.custodyId == custodyId && !it.isArchived } == true) { "الشخص لا ينتمي إلى هذه العهدة" }
        val ownerAccount = dao.getOwnerAccount(custodyId, currency) ?: error("حساب صاحب العهدة للعملة غير موجود")
        val personAccount = personId?.let { dao.getPersonAccount(custodyId, it, currency) }
        val ownerDelta = CustodyBalanceRules.ownerDelta(type, amountMinor)
        val personDelta = CustodyBalanceRules.personDelta(type, amountMinor)
        var transactionId = 0L
        try {
            transactionId = db.withTransaction {
                val id = dao.insertTransaction(CustodyTransactionEntity(custodyId = custodyId, accountId = ownerAccount.id, personId = personId, currencyCode = currency, type = type, amountMinor = amountMinor, description = description.trim(), transactionDate = date))
                dao.adjustAccountBalance(ownerAccount.id, ownerDelta, System.currentTimeMillis())
                if (personAccount != null && personDelta != 0L) dao.adjustAccountBalance(personAccount.id, personDelta, System.currentTimeMillis())
                id
            }
            attachmentStore.save(transactionId, attachments)
            transactionId
        } catch (e: Throwable) {
            if (transactionId != 0L) {
                attachmentStore.deleteForTransaction(transactionId)
                runCatching { db.withTransaction { dao.getTransaction(transactionId)?.let { old ->
                    dao.adjustAccountBalance(old.accountId, -CustodyBalanceRules.ownerDelta(old.type, old.amountMinor), System.currentTimeMillis())
                    if (old.personId != null) dao.getPersonAccount(old.custodyId, old.personId, old.currencyCode)?.let { pa ->
                        val pd = CustodyBalanceRules.personDelta(old.type, old.amountMinor)
                        if (pd != 0L) dao.adjustAccountBalance(pa.id, -pd, System.currentTimeMillis())
                    }
                    dao.deleteTransaction(transactionId)
                } } }
            }
            throw e
        }
    }

    suspend fun updateTransaction(id: Long, currency: String, type: String, personId: Long?, amountMinor: Long, description: String, date: Long, newAttachments: List<CustodyAttachmentStorage.Selected> = emptyList(), deletedAttachments: List<CustodyTransactionAttachmentEntity> = emptyList()) = withContext(Dispatchers.IO) {
        require(currency in currencies())
        require(isAllowedType(type))
        require(amountMinor > 0)
        val old = dao.getTransaction(id) ?: return@withContext
        val personOperation = type == CustodyTransactionType.PAID_TO_PERSON || type == CustodyTransactionType.RETURNED_FROM_PERSON
        require(personOperation == (personId != null))
        require(dao.getCustody(old.custodyId)?.isArchived == false) { "العهدة غير موجودة أو مؤرشفة" }
        if (personId != null) require(dao.getPerson(personId)?.let { it.custodyId == old.custodyId && !it.isArchived } == true)
        val newOwnerAccount = dao.getOwnerAccount(old.custodyId, currency) ?: error("حساب صاحب العهدة للعملة غير موجود")
        val newPersonAccount = personId?.let { dao.getPersonAccount(old.custodyId, it, currency) }
        val now = System.currentTimeMillis()
        db.withTransaction {
            dao.adjustAccountBalance(old.accountId, -CustodyBalanceRules.ownerDelta(old.type, old.amountMinor), now)
            if (old.personId != null) dao.getPersonAccount(old.custodyId, old.personId, old.currencyCode)?.let { pa ->
                val delta = CustodyBalanceRules.personDelta(old.type, old.amountMinor)
                if (delta != 0L) dao.adjustAccountBalance(pa.id, -delta, now)
            }
            dao.updateTransaction(old.copy(accountId = newOwnerAccount.id, currencyCode = currency, type = type, personId = personId, amountMinor = amountMinor, description = description.trim(), transactionDate = date))
            dao.adjustAccountBalance(newOwnerAccount.id, CustodyBalanceRules.ownerDelta(type, amountMinor), now)
            if (newPersonAccount != null) {
                val delta = CustodyBalanceRules.personDelta(type, amountMinor)
                if (delta != 0L) dao.adjustAccountBalance(newPersonAccount.id, delta, now)
            }
        }
        deletedAttachments.forEach { attachmentStore.delete(it) }
        attachmentStore.save(id, newAttachments)
    }

    suspend fun deleteTransaction(id: Long) = db.withTransaction {
        val old = dao.getTransaction(id) ?: return@withTransaction
        dao.adjustAccountBalance(old.accountId, -CustodyBalanceRules.ownerDelta(old.type, old.amountMinor), System.currentTimeMillis())
        if (old.personId != null) dao.getPersonAccount(old.custodyId, old.personId, old.currencyCode)?.let { pa ->
            val delta = CustodyBalanceRules.personDelta(old.type, old.amountMinor)
            if (delta != 0L) dao.adjustAccountBalance(pa.id, -delta, System.currentTimeMillis())
        }
        attachmentStore.deleteForTransaction(id)
        dao.deleteTransaction(id)
    }

    suspend fun archive(id: Long) = dao.archiveCustody(id, System.currentTimeMillis())
    suspend fun delete(id: Long) = db.withTransaction { dao.deleteTransactions(id); dao.deleteAccounts(id); dao.deletePersons(id); dao.deleteCustody(id) }
}
