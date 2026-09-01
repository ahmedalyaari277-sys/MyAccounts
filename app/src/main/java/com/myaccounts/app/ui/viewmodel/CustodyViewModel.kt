package com.myaccounts.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myaccounts.app.data.custody.*
import com.myaccounts.app.util.CustodyAttachmentStorage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CustodyViewModel(app: Application): AndroidViewModel(app) {
    private val repo = CustodyRepository(com.myaccounts.app.data.local.AppDatabase.getInstance(app), app)
    private val dao = com.myaccounts.app.data.local.AppDatabase.getInstance(app).custodyDao()
    val custodies = repo.observeCustodies().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val custodyFlows = mutableMapOf<Long, StateFlow<CustodyEntity?>>()
    private val personFlows = mutableMapOf<Long, StateFlow<List<CustodyPersonEntity>>>()
    private val accountFlows = mutableMapOf<Long, StateFlow<List<CustodyAccountEntity>>>()
    private val transactionFlows = mutableMapOf<Long, StateFlow<List<CustodyTransactionEntity>>>()
    private val personTransactionFlows = mutableMapOf<String, StateFlow<List<CustodyTransactionEntity>>>()
    private val balanceFlows = mutableMapOf<Long, StateFlow<Long>>()

    fun custody(id: Long): StateFlow<CustodyEntity?> = custodyFlows.getOrPut(id) { repo.observeCustody(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null) }
    fun persons(id: Long): StateFlow<List<CustodyPersonEntity>> = personFlows.getOrPut(id) { repo.observePersons(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()) }
    fun accounts(id: Long): StateFlow<List<CustodyAccountEntity>> = accountFlows.getOrPut(id) { repo.observeAccounts(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()) }
    fun transactions(id: Long): StateFlow<List<CustodyTransactionEntity>> = transactionFlows.getOrPut(id) { repo.observeTransactions(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()) }
    fun personTransactions(id: Long, personId: Long, currency: String): StateFlow<List<CustodyTransactionEntity>> {
        val key = "$id:$personId:$currency"
        return personTransactionFlows.getOrPut(key) { repo.observePersonTransactions(id, personId, currency).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()) }
    }
    fun balance(accountId: Long): StateFlow<Long> = balanceFlows.getOrPut(accountId) { repo.observeBalance(accountId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L) }

    fun attachments(id: Long): List<CustodyTransactionAttachmentEntity> = repo.attachments(id)
    suspend fun archivedCustodies(): List<CustodyEntity> = dao.getAllCustodies(true)
    fun create(c: CustodyEntity) = viewModelScope.launch { repo.createCustody(c) }
    suspend fun createAndWait(c: CustodyEntity): Long = repo.createCustody(c)
    fun addPerson(id: Long, p: CustodyPersonEntity) = viewModelScope.launch { repo.addPerson(id, p) }
    suspend fun addPersonAndWait(id: Long, p: CustodyPersonEntity): Long = repo.addPerson(id, p)
    fun updatePerson(p: CustodyPersonEntity) = viewModelScope.launch { repo.updatePerson(p) }
    fun updateCustody(c: CustodyEntity) = viewModelScope.launch { repo.updateCustody(c) }
    fun addTransaction(id: Long, currency: String, type: String, personId: Long?, amount: Long, description: String, date: Long, attachments: List<CustodyAttachmentStorage.Selected> = emptyList()) = viewModelScope.launch { repo.addTransaction(id, currency, type, personId, amount, description, date, attachments) }
    fun updateTransaction(id: Long, currency: String, type: String, personId: Long?, amount: Long, description: String, date: Long, newAttachments: List<CustodyAttachmentStorage.Selected> = emptyList(), deleted: List<CustodyTransactionAttachmentEntity> = emptyList()) = viewModelScope.launch { repo.updateTransaction(id, currency, type, personId, amount, description, date, newAttachments, deleted) }
    suspend fun addTransactionAndWait(id: Long, currency: String, type: String, personId: Long?, amount: Long, description: String, date: Long, attachments: List<CustodyAttachmentStorage.Selected> = emptyList()) = repo.addTransaction(id, currency, type, personId, amount, description, date, attachments)
    suspend fun updateTransactionAndWait(id: Long, currency: String, type: String, personId: Long?, amount: Long, description: String, date: Long, newAttachments: List<CustodyAttachmentStorage.Selected> = emptyList(), deleted: List<CustodyTransactionAttachmentEntity> = emptyList()) = repo.updateTransaction(id, currency, type, personId, amount, description, date, newAttachments, deleted)
    fun deleteTransaction(id: Long) = viewModelScope.launch { repo.deleteTransaction(id) }
    suspend fun closeCustodyAndWait(id: Long, yerActualMinor: Long, sarActualMinor: Long, usdActualMinor: Long, notes: String) = repo.closeCustody(id, yerActualMinor, sarActualMinor, usdActualMinor, notes)
    fun reopenCustody(id: Long) = viewModelScope.launch { repo.reopenCustody(id) }
    fun deleteAttachment(a: CustodyTransactionAttachmentEntity) = viewModelScope.launch { CustodyAttachmentStore(getApplication()).delete(a) }
    fun archive(id: Long) = viewModelScope.launch { repo.archive(id) }
    fun restore(id: Long) = viewModelScope.launch { dao.restoreCustody(id) }
    fun deleteCustody(id: Long) = viewModelScope.launch { repo.delete(id) }
}
