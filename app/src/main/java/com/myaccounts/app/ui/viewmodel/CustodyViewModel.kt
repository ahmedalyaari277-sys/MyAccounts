package com.myaccounts.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myaccounts.app.data.custody.*
import com.myaccounts.app.util.CustodyAttachmentStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class CustodyViewModel(app: Application): AndroidViewModel(app) {
    private val repo = CustodyRepository(com.myaccounts.app.data.local.AppDatabase.getInstance(app), app)
    private val dao = com.myaccounts.app.data.local.AppDatabase.getInstance(app).custodyDao()

    val custodies = repo.observeCustodies().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Keep one long-lived StateFlow per entity. This prevents recomposition from replacing
    // the observed flow and resetting a custody screen to its initial state.
    private val custodyFlows = ConcurrentHashMap<Long, StateFlow<CustodyEntity?>>()
    private val personFlows = ConcurrentHashMap<Long, StateFlow<List<CustodyPersonEntity>>>()
    private val accountFlows = ConcurrentHashMap<Long, StateFlow<List<CustodyAccountEntity>>>()
    private val transactionFlows = ConcurrentHashMap<Long, StateFlow<List<CustodyTransactionEntity>>>()
    private val personTransactionFlows = ConcurrentHashMap<String, StateFlow<List<CustodyTransactionEntity>>>()
    private val balanceFlows = ConcurrentHashMap<Long, StateFlow<Long>>()

    fun custody(id: Long): StateFlow<CustodyEntity?> = custodyFlows.computeIfAbsent(id) {
        repo.observeCustody(id).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    }

    fun persons(id: Long): StateFlow<List<CustodyPersonEntity>> = personFlows.computeIfAbsent(id) {
        repo.observePersons(id).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    }

    fun accounts(id: Long): StateFlow<List<CustodyAccountEntity>> = accountFlows.computeIfAbsent(id) {
        repo.observeAccounts(id).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    }

    fun transactions(id: Long): StateFlow<List<CustodyTransactionEntity>> = transactionFlows.computeIfAbsent(id) {
        repo.observeTransactions(id).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    }

    fun personTransactions(id: Long, personId: Long, currency: String): StateFlow<List<CustodyTransactionEntity>> {
        val key = "$id:$personId:$currency"
        return personTransactionFlows.computeIfAbsent(key) {
            repo.observePersonTransactions(id, personId, currency)
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        }
    }

    fun balance(accountId: Long): StateFlow<Long> = balanceFlows.computeIfAbsent(accountId) {
        repo.observeBalance(accountId).stateIn(viewModelScope, SharingStarted.Eagerly, 0L)
    }

    fun attachments(id: Long): List<CustodyTransactionAttachmentEntity> = repo.attachments(id)
    fun archivedCustodies(): Flow<List<CustodyEntity>> = kotlinx.coroutines.flow.flow { emit(dao.getAllCustodies(true)) }

    fun create(c: CustodyEntity) = viewModelScope.launch { repo.createCustody(c) }
    fun addPerson(id: Long, p: CustodyPersonEntity) = viewModelScope.launch { repo.addPerson(id, p) }
    suspend fun addPersonAndWait(id: Long, p: CustodyPersonEntity): Long = withContext(Dispatchers.IO) {
        repo.addPerson(id, p)
    }
    fun updatePerson(p: CustodyPersonEntity) = viewModelScope.launch { repo.updatePerson(p) }
    fun updateCustody(c: CustodyEntity) = viewModelScope.launch { repo.updateCustody(c) }
    fun addTransaction(id: Long, currency: String, type: String, personId: Long?, amount: Long, description: String, date: Long, attachments: List<CustodyAttachmentStorage.Selected> = emptyList()) = viewModelScope.launch { repo.addTransaction(id, currency, type, personId, amount, description, date, attachments) }
    fun updateTransaction(id: Long, currency: String, type: String, personId: Long?, amount: Long, description: String, date: Long, newAttachments: List<CustodyAttachmentStorage.Selected> = emptyList(), deleted: List<CustodyTransactionAttachmentEntity> = emptyList()) = viewModelScope.launch { repo.updateTransaction(id, currency, type, personId, amount, description, date, newAttachments, deleted) }
    suspend fun addTransactionAndWait(id: Long, currency: String, type: String, personId: Long?, amount: Long, description: String, date: Long, attachments: List<CustodyAttachmentStorage.Selected> = emptyList()) = repo.addTransaction(id, currency, type, personId, amount, description, date, attachments)
    suspend fun updateTransactionAndWait(id: Long, currency: String, type: String, personId: Long?, amount: Long, description: String, date: Long, newAttachments: List<CustodyAttachmentStorage.Selected> = emptyList(), deleted: List<CustodyTransactionAttachmentEntity> = emptyList()) = repo.updateTransaction(id, currency, type, personId, amount, description, date, newAttachments, deleted)
    fun deleteTransaction(id: Long) = viewModelScope.launch { repo.deleteTransaction(id) }
    fun deleteAttachment(a: CustodyTransactionAttachmentEntity) = viewModelScope.launch { CustodyAttachmentStore(getApplication()).delete(a) }
    fun archive(id: Long) = viewModelScope.launch { repo.archive(id) }
    fun restore(id: Long) = viewModelScope.launch { dao.restoreCustody(id) }
    fun deleteCustody(id: Long) = viewModelScope.launch { repo.delete(id) }
}
