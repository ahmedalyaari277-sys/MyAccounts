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
    const val ORG_LOAN_FROM_OWNER = "ORG_LOAN_FROM_OWNER"
    const val ORG_LOAN_REPAYMENT = "ORG_LOAN_REPAYMENT"
    const val PERSON_LOAN_TO_OWNER = "PERSON_LOAN_TO_OWNER"
    const val OWNER_REPAY_PERSON_LOAN = "OWNER_REPAY_PERSON_LOAN"
}

object CustodyBalanceRules {
    fun ownerCashDelta(type: String, amount: Long): Long = when (type) {
        CustodyTransactionType.RECEIVED_FROM_ORG, CustodyTransactionType.RETURNED_FROM_PERSON,
        CustodyTransactionType.ORG_LOAN_REPAYMENT, CustodyTransactionType.PERSON_LOAN_TO_OWNER -> amount
        CustodyTransactionType.PAID_TO_PERSON, CustodyTransactionType.RETURNED_TO_ORG,
        CustodyTransactionType.ORG_LOAN_FROM_OWNER, CustodyTransactionType.OWNER_REPAY_PERSON_LOAN -> -amount
        else -> error("نوع عملية عهدة غير معروف")
    }
    fun personCustodyDelta(type: String, amount: Long): Long = when (type) {
        CustodyTransactionType.PAID_TO_PERSON -> amount
        CustodyTransactionType.RETURNED_FROM_PERSON -> -amount
        else -> 0L
    }
    fun ownerOrgDebtDelta(type: String, amount: Long): Long = when (type) {
        CustodyTransactionType.ORG_LOAN_FROM_OWNER -> amount
        CustodyTransactionType.ORG_LOAN_REPAYMENT -> -amount
        else -> 0L
    }
    fun ownerPeopleDebtDelta(type: String, amount: Long): Long = when (type) {
        CustodyTransactionType.PERSON_LOAN_TO_OWNER -> -amount
        CustodyTransactionType.OWNER_REPAY_PERSON_LOAN -> amount
        else -> 0L
    }
    fun personDebtDelta(type: String, amount: Long): Long = when (type) {
        CustodyTransactionType.PERSON_LOAN_TO_OWNER -> amount
        CustodyTransactionType.OWNER_REPAY_PERSON_LOAN -> -amount
        else -> 0L
    }
    fun ownerDelta(type: String, amount: Long): Long = ownerCashDelta(type, amount)
    fun personDelta(type: String, amount: Long): Long = personCustodyDelta(type, amount)
}

class CustodyRepository(private val db: com.myaccounts.app.data.local.AppDatabase, context: Context) {
    private val dao = db.custodyDao()
    private val attachmentStore = CustodyAttachmentStore(context.applicationContext)
    private val currencies = listOf("YER", "SAR", "USD")
    private val allowedTypes = setOf(
        CustodyTransactionType.RECEIVED_FROM_ORG, CustodyTransactionType.PAID_TO_PERSON,
        CustodyTransactionType.RETURNED_FROM_PERSON, CustodyTransactionType.RETURNED_TO_ORG,
        CustodyTransactionType.ORG_LOAN_FROM_OWNER, CustodyTransactionType.ORG_LOAN_REPAYMENT,
        CustodyTransactionType.PERSON_LOAN_TO_OWNER, CustodyTransactionType.OWNER_REPAY_PERSON_LOAN
    )
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
        dao.insertAccounts(currencies.map { CustodyAccountEntity(custodyId = id, holderType = "OWNER", currencyCode = it) })
        id
    }
    suspend fun updateCustody(c: CustodyEntity) {
        require(c.name.isNotBlank()) { "اسم صاحب العهدة مطلوب" }
        require(c.organizationName.isNotBlank()) { "اسم الجهة مطلوب" }
        dao.updateCustody(c.copy(name = c.name.trim(), organizationName = c.organizationName.trim()))
    }
    suspend fun addPerson(custodyId: Long, p: CustodyPersonEntity): Long = db.withTransaction {
        val custody = dao.getCustody(custodyId) ?: error("العهدة غير موجودة")
        require(!custody.isArchived) { "العهدة مؤرشفة" }
        require(!custody.isClosed) { "العهدة مغلقة ومسواة" }
        require(p.name.isNotBlank()) { "اسم الشخص مطلوب" }
        val id = dao.insertPerson(p.copy(custodyId = custodyId, name = p.name.trim()))
        dao.insertAccounts(currencies.map { CustodyAccountEntity(custodyId = custodyId, holderType = "PERSON", personId = id, currencyCode = it) })
        id
    }
    suspend fun updatePerson(p: CustodyPersonEntity) {
        require(p.name.isNotBlank()) { "اسم الشخص مطلوب" }
        val custody = dao.getCustody(p.custodyId) ?: error("العهدة غير موجودة")
        require(!custody.isClosed) { "العهدة مغلقة ومسواة" }
        require(!custody.isArchived) { "العهدة مؤرشفة" }
        dao.updatePerson(p.copy(name = p.name.trim()))
    }
    suspend fun deletePerson(personId: Long) = db.withTransaction {
        val person = dao.getPerson(personId) ?: return@withTransaction
        val custody = dao.getCustody(person.custodyId) ?: return@withTransaction
        require(!custody.isClosed) { "العهدة مغلقة ومسواة" }
        require(!custody.isArchived) { "العهدة مؤرشفة" }
        val transactions = dao.getTransactionsForPerson(personId)
        transactions.forEach { attachmentStore.deleteForTransaction(it.id) }
        dao.deleteTransactionsForPerson(personId)
        dao.deleteAccountsForPerson(personId)
        dao.deletePerson(personId)
    }
    private fun validateOperation(type: String, personId: Long?) {
        require(type in allowedTypes) { "نوع عملية عهدة غير صالح" }
        val needsPerson = type == CustodyTransactionType.PAID_TO_PERSON || type == CustodyTransactionType.RETURNED_FROM_PERSON || type == CustodyTransactionType.PERSON_LOAN_TO_OWNER || type == CustodyTransactionType.OWNER_REPAY_PERSON_LOAN
        require(needsPerson == (personId != null)) { "العملية لا تتوافق مع الطرف المحدد" }
    }
    suspend fun addTransaction(custodyId: Long, currency: String, type: String, personId: Long?, amountMinor: Long, description: String, date: Long, attachments: List<CustodyAttachmentStorage.Selected> = emptyList()): Long = withContext(Dispatchers.IO) {
        require(currency in currencies); require(amountMinor > 0); validateOperation(type, personId)
        val custody = dao.getCustody(custodyId) ?: error("العهدة غير موجودة")
        require(!custody.isArchived) { "العهدة مؤرشفة" }; require(!custody.isClosed) { "العهدة مغلقة ومسواة" }
        if (personId != null) require(dao.getPerson(personId)?.let { it.custodyId == custodyId && !it.isArchived } == true) { "الشخص لا ينتمي إلى هذه العهدة" }
        val ownerAccount = dao.getOwnerAccount(custodyId, currency) ?: error("حساب صاحب العهدة للعملة غير موجود")
        val personAccount = personId?.let { dao.getPersonAccount(custodyId, it, currency) }
        val ownerDelta = CustodyBalanceRules.ownerCashDelta(type, amountMinor); val personDelta = CustodyBalanceRules.personCustodyDelta(type, amountMinor)
        var transactionId = 0L
        try {
            transactionId = db.withTransaction {
                val id = dao.insertTransaction(CustodyTransactionEntity(custodyId = custodyId, accountId = ownerAccount.id, personId = personId, currencyCode = currency, type = type, amountMinor = amountMinor, description = description.trim(), transactionDate = date))
                dao.adjustAccountBalance(ownerAccount.id, ownerDelta, System.currentTimeMillis())
                if (personAccount != null && personDelta != 0L) dao.adjustAccountBalance(personAccount.id, personDelta, System.currentTimeMillis())
                id
            }
            attachmentStore.save(transactionId, attachments); transactionId
        } catch (e: Throwable) {
            if (transactionId != 0L) { attachmentStore.deleteForTransaction(transactionId); runCatching { db.withTransaction { dao.getTransaction(transactionId)?.let { old -> dao.adjustAccountBalance(old.accountId, -CustodyBalanceRules.ownerCashDelta(old.type, old.amountMinor), System.currentTimeMillis()); if (old.personId != null) dao.getPersonAccount(old.custodyId, old.personId, old.currencyCode)?.let { pa -> val d = CustodyBalanceRules.personCustodyDelta(old.type, old.amountMinor); if (d != 0L) dao.adjustAccountBalance(pa.id, -d, System.currentTimeMillis()) }; dao.deleteTransaction(transactionId) } } } }
            throw e
        }
    }
    suspend fun updateTransaction(id: Long, currency: String, type: String, personId: Long?, amountMinor: Long, description: String, date: Long, newAttachments: List<CustodyAttachmentStorage.Selected> = emptyList(), deletedAttachments: List<CustodyTransactionAttachmentEntity> = emptyList()) = withContext(Dispatchers.IO) {
        require(currency in currencies); require(amountMinor > 0); val old = dao.getTransaction(id) ?: return@withContext; validateOperation(type, personId)
        val custody = dao.getCustody(old.custodyId) ?: error("العهدة غير موجودة"); require(!custody.isArchived) { "العهدة مؤرشفة" }; require(!custody.isClosed) { "العهدة مغلقة ومسواة" }
        if (personId != null) require(dao.getPerson(personId)?.let { it.custodyId == old.custodyId && !it.isArchived } == true) { "الشخص لا ينتمي إلى هذه العهدة" }
        val newOwnerAccount = dao.getOwnerAccount(old.custodyId, currency) ?: error("حساب صاحب العهدة للعملة غير موجود")
        val newPersonAccount = personId?.let { dao.getPersonAccount(old.custodyId, it, currency) }; val now = System.currentTimeMillis()
        db.withTransaction {
            dao.adjustAccountBalance(old.accountId, -CustodyBalanceRules.ownerCashDelta(old.type, old.amountMinor), now)
            if (old.personId != null) dao.getPersonAccount(old.custodyId, old.personId, old.currencyCode)?.let { pa -> val d = CustodyBalanceRules.personCustodyDelta(old.type, old.amountMinor); if (d != 0L) dao.adjustAccountBalance(pa.id, -d, now) }
            dao.updateTransaction(old.copy(accountId = newOwnerAccount.id, currencyCode = currency, type = type, personId = personId, amountMinor = amountMinor, description = description.trim(), transactionDate = date))
            dao.adjustAccountBalance(newOwnerAccount.id, CustodyBalanceRules.ownerCashDelta(type, amountMinor), now)
            if (newPersonAccount != null) { val d = CustodyBalanceRules.personCustodyDelta(type, amountMinor); if (d != 0L) dao.adjustAccountBalance(newPersonAccount.id, d, now) }
        }
        deletedAttachments.forEach { attachmentStore.delete(it) }; attachmentStore.save(id, newAttachments)
    }
    suspend fun transferTransaction(id: Long, newPersonId: Long, reason: String) = db.withTransaction {
        val old = dao.getTransaction(id) ?: error("العملية غير موجودة")
        require(old.personId != null) { "لا يمكن نقل عملية لا ترتبط بشخص" }
        val custody = dao.getCustody(old.custodyId) ?: error("العهدة غير موجودة")
        require(!custody.isArchived) { "العهدة مؤرشفة" }; require(!custody.isClosed) { "العهدة مغلقة ومسواة" }
        require(old.personId != newPersonId) { "لا يمكن نقل العملية إلى الشخص نفسه" }
        val newPerson = dao.getPerson(newPersonId) ?: error("الشخص الجديد غير موجود")
        require(newPerson.custodyId == old.custodyId && !newPerson.isArchived) { "الشخص الجديد لا ينتمي إلى هذه العهدة" }
        val oldPerson = dao.getPerson(old.personId) ?: error("الشخص الحالي غير موجود")
        val cleanReason = reason.trim(); require(cleanReason.isNotBlank()) { "سبب النقل مطلوب" }
        val appended = "\n\n- العملية منقولة من حساب ${oldPerson.name} بسبب: $cleanReason"
        dao.updateTransaction(old.copy(personId = newPersonId, description = old.description + appended))
    }
    suspend fun deleteTransaction(id: Long) = db.withTransaction {
        val old = dao.getTransaction(id) ?: return@withTransaction; val custody = dao.getCustody(old.custodyId) ?: return@withTransaction; require(!custody.isClosed) { "العهدة مغلقة ومسواة" }
        dao.adjustAccountBalance(old.accountId, -CustodyBalanceRules.ownerCashDelta(old.type, old.amountMinor), System.currentTimeMillis())
        if (old.personId != null) dao.getPersonAccount(old.custodyId, old.personId, old.currencyCode)?.let { pa -> val d = CustodyBalanceRules.personCustodyDelta(old.type, old.amountMinor); if (d != 0L) dao.adjustAccountBalance(pa.id, -d, System.currentTimeMillis()) }
        attachmentStore.deleteForTransaction(id); dao.deleteTransaction(id)
    }
    suspend fun closeCustody(id: Long, settlementYerActualMinor: Long, settlementSarActualMinor: Long, settlementUsdActualMinor: Long, notes: String) = db.withTransaction {
        val custody = dao.getCustody(id) ?: error("العهدة غير موجودة")
        require(!custody.isArchived) { "العهدة مؤرشفة" }; require(!custody.isClosed) { "العهدة مغلقة بالفعل" }
        val actuals = mapOf("YER" to settlementYerActualMinor, "SAR" to settlementSarActualMinor, "USD" to settlementUsdActualMinor)
        require(actuals.values.all { it >= 0L }) { "القيم الفعلية لا يمكن أن تكون سالبة" }
        currencies.forEach { currency ->
            val orgDebt = dao.getAllTransactions(id, false).filter { it.currencyCode == currency }.sumOf { CustodyBalanceRules.ownerOrgDebtDelta(it.type, it.amountMinor) }
            require(orgDebt == 0L) { "$currency: لا يمكن إغلاق العهدة؛ ذمة الجهة غير مسواة." }
        }
        dao.updateCustody(custody.copy(isClosed = true, closedAt = System.currentTimeMillis(), settlementYerActualMinor = settlementYerActualMinor, settlementSarActualMinor = settlementSarActualMinor, settlementUsdActualMinor = settlementUsdActualMinor, settlementNotes = notes.trim()))
    }
    suspend fun reopenCustody(id: Long) = db.withTransaction { val custody = dao.getCustody(id) ?: error("العهدة غير موجودة"); require(!custody.isArchived) { "العهدة مؤرشفة" }; dao.updateCustody(custody.copy(isClosed = false, closedAt = null, settlementYerActualMinor = null, settlementSarActualMinor = null, settlementUsdActualMinor = null, settlementNotes = "")) }
    suspend fun archive(id: Long) = dao.archiveCustody(id, System.currentTimeMillis())
    suspend fun delete(id: Long) = db.withTransaction { dao.deleteTransactions(id); dao.deleteAccounts(id); dao.deletePersons(id); dao.deleteCustody(id) }
}
