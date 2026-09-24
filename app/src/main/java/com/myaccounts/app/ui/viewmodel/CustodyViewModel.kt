package com.myaccounts.app.ui.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myaccounts.app.data.custody.*
import com.myaccounts.app.util.CustodyAttachmentStorage
import com.myaccounts.app.util.CustodyOrganizationManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CustodyViewModel(app: Application): AndroidViewModel(app) {
    private val repo = com.myaccounts.app.data.custody.CustodyRepository(com.myaccounts.app.data.local.AppDatabase.getInstance(app), app)
    private val dao = com.myaccounts.app.data.local.AppDatabase.getInstance(app).custodyDao()
    private val db = com.myaccounts.app.data.local.AppDatabase.getInstance(app)
    private val custodySearchQuery = MutableStateFlow("")
    val searchResults: StateFlow<List<CustodySearchResult>> = custodySearchQuery.flatMapLatest { query -> repo.search(query) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val custodies = custodySearchQuery.flatMapLatest { repo.observeCustodies(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val custodyFlows = mutableMapOf<Long, StateFlow<CustodyEntity?>>()
    private val personFlows = mutableMapOf<Long, StateFlow<List<CustodyPersonEntity>>>()
    private val accountFlows = mutableMapOf<Long, StateFlow<List<CustodyAccountEntity>>>()
    private val transactionFlows = mutableMapOf<Long, StateFlow<List<CustodyTransactionEntity>>>()
    private val personTransactionFlows = mutableMapOf<String, StateFlow<List<CustodyTransactionEntity>>>()
    private val balanceFlows = mutableMapOf<Long, StateFlow<Long>>()

    init {
        viewModelScope.launch {
            custodies.collectLatest { list ->
                list.forEach { custody -> runCatching { CustodyOrganizationManager.ensure(db, custody.id) } }
            }
        }
    }

    fun setSearchQuery(query: String) { custodySearchQuery.value = normalizeSearchQuery(query) }

    private fun normalizeSearchQuery(value: String): String = value.trim().map { ch -> when (ch) { in '٠'..'٩' -> ('0'.code + ch.code - '٠'.code).toChar(); in '۰'..'۹' -> ('0'.code + ch.code - '۰'.code).toChar(); '٫' -> '.'; '٬' -> null; else -> ch } }.filterNotNull().joinToString("").replace(',', '.')

    fun custody(id: Long): StateFlow<CustodyEntity?> = custodyFlows.getOrPut(id) { repo.observeCustody(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), custodies.value.firstOrNull { it.id == id }) }
    fun persons(id: Long): StateFlow<List<CustodyPersonEntity>> = personFlows.getOrPut(id) { repo.observePersons(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()) }
    fun accounts(id: Long): StateFlow<List<CustodyAccountEntity>> = accountFlows.getOrPut(id) { repo.observeAccounts(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()) }
    fun transactions(id: Long): StateFlow<List<CustodyTransactionEntity>> = transactionFlows.getOrPut(id) { repo.observeTransactions(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()) }
    suspend fun reportSnapshots(ids: List<Long>): List<CustodyReportSnapshot> = repo.reportSnapshots(ids)
    fun personTransactions(id: Long, personId: Long, currency: String): StateFlow<List<CustodyTransactionEntity>> { val key = "$id:$personId:$currency"; return personTransactionFlows.getOrPut(key) { repo.observePersonTransactions(id, personId, currency).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()) } }
    fun balance(accountId: Long): StateFlow<Long> = balanceFlows.getOrPut(accountId) { repo.observeBalance(accountId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L) }
    fun attachments(id: Long): List<CustodyTransactionAttachmentEntity> = repo.attachments(id)
    suspend fun archivedCustodies(): List<CustodyEntity> = dao.getAllCustodies(true)

    fun create(c: CustodyEntity) = viewModelScope.launch {
        runCatching { repo.createCustody(c) }
            .onFailure { Toast.makeText(getApplication(), it.message ?: "تعذر حفظ العهدة", Toast.LENGTH_LONG).show() }
    }
    suspend fun createAndWait(c: CustodyEntity): Long = repo.createCustody(c).also { CustodyOrganizationManager.ensure(db, it) }

    fun addPerson(id: Long, p: CustodyPersonEntity) = viewModelScope.launch {
        runCatching { repo.addPerson(id, p) }
            .onFailure { Toast.makeText(getApplication(), it.message ?: "تعذر حفظ الطرف", Toast.LENGTH_LONG).show() }
    }
    suspend fun addPersonAndWait(id: Long, p: CustodyPersonEntity): Long = repo.addPerson(id, p)
    fun updatePerson(p: CustodyPersonEntity) = viewModelScope.launch {
        runCatching { repo.updatePerson(p) }
            .onFailure { Toast.makeText(getApplication(), it.message ?: "تعذر تعديل الطرف", Toast.LENGTH_LONG).show() }
    }
    suspend fun updatePersonAndWait(p: CustodyPersonEntity) = repo.updatePerson(p)
    suspend fun deletePersonAndWait(id: Long) = repo.deletePerson(id)
    fun updateCustody(c: CustodyEntity) = viewModelScope.launch {
        runCatching { repo.updateCustody(c); CustodyOrganizationManager.ensure(db, c.id) }
            .onFailure { Toast.makeText(getApplication(), it.message ?: "تعذر تعديل العهدة", Toast.LENGTH_LONG).show() }
    }
    suspend fun updateCustodyAndWait(c: CustodyEntity) { repo.updateCustody(c); CustodyOrganizationManager.ensure(db, c.id) }
    fun addTransaction(id: Long, currency: String, type: String, personId: Long?, amount: Long, categoryName: String, description: String, date: Long, attachments: List<CustodyAttachmentStorage.Selected> = emptyList()) = viewModelScope.launch { repo.addTransaction(id, currency, type, personId, amount, categoryName, description, date, attachments) }
    fun addTransaction(id: Long, currency: String, type: String, personId: Long?, amount: Long, description: String, date: Long, attachments: List<CustodyAttachmentStorage.Selected> = emptyList()) = viewModelScope.launch { repo.addTransaction(id, currency, type, personId, amount, "", description, date, attachments) }
    fun updateTransaction(id: Long, currency: String, type: String, personId: Long?, amount: Long, categoryName: String, description: String, date: Long, newAttachments: List<CustodyAttachmentStorage.Selected> = emptyList(), deleted: List<CustodyTransactionAttachmentEntity> = emptyList()) = viewModelScope.launch { repo.updateTransaction(id, currency, type, personId, amount, categoryName, description, date, newAttachments, deleted) }
    fun updateTransaction(id: Long, currency: String, type: String, personId: Long?, amount: Long, description: String, date: Long, newAttachments: List<CustodyAttachmentStorage.Selected> = emptyList(), deleted: List<CustodyTransactionAttachmentEntity> = emptyList()) = viewModelScope.launch { repo.updateTransaction(id, currency, type, personId, amount, "", description, date, newAttachments, deleted) }
    suspend fun addTransactionAndWait(id: Long, currency: String, type: String, personId: Long?, amount: Long, categoryName: String, description: String, date: Long, attachments: List<CustodyAttachmentStorage.Selected> = emptyList()) = repo.addTransaction(id, currency, type, personId, amount, categoryName, description, date, attachments)
    suspend fun addTransactionAndWait(id: Long, currency: String, type: String, personId: Long?, amount: Long, description: String, date: Long, attachments: List<CustodyAttachmentStorage.Selected> = emptyList()) = repo.addTransaction(id, currency, type, personId, amount, "", description, date, attachments)
    suspend fun updateTransactionAndWait(id: Long, currency: String, type: String, personId: Long?, amount: Long, categoryName: String, description: String, date: Long, newAttachments: List<CustodyAttachmentStorage.Selected> = emptyList(), deleted: List<CustodyTransactionAttachmentEntity> = emptyList()) = repo.updateTransaction(id, currency, type, personId, amount, categoryName, description, date, newAttachments, deleted)
    suspend fun updateTransactionAndWait(id: Long, currency: String, type: String, personId: Long?, amount: Long, description: String, date: Long, newAttachments: List<CustodyAttachmentStorage.Selected> = emptyList(), deleted: List<CustodyTransactionAttachmentEntity> = emptyList()) = repo.updateTransaction(id, currency, type, personId, amount, "", description, date, newAttachments, deleted)
    suspend fun transferTransactionAndWait(id: Long, newPersonId: Long, reason: String) = repo.transferTransaction(id, newPersonId, reason)
    fun deleteTransaction(id: Long) = viewModelScope.launch { repo.deleteTransaction(id) }
    suspend fun closeCustodyAndWait(id: Long, yerActualMinor: Long, sarActualMinor: Long, usdActualMinor: Long, notes: String) = repo.closeCustody(id, yerActualMinor, sarActualMinor, usdActualMinor, notes)
    fun reopenCustody(id: Long) = viewModelScope.launch { repo.reopenCustody(id) }
    fun deleteAttachment(a: CustodyTransactionAttachmentEntity) = viewModelScope.launch { CustodyAttachmentStore(getApplication()).delete(a) }
    fun archive(id: Long) = viewModelScope.launch { repo.archive(id) }
    fun restore(id: Long) = viewModelScope.launch { dao.restoreCustody(id) }
    fun deleteCustody(id: Long) = viewModelScope.launch { repo.delete(id) }
}
