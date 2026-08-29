package com.myaccounts.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myaccounts.app.data.custody.CustodyEntity
import com.myaccounts.app.data.custody.CustodyPersonEntity
import com.myaccounts.app.data.custody.CustodyRepository
import com.myaccounts.app.data.custody.CustodyTransactionAttachmentEntity
import com.myaccounts.app.util.CustodyAttachmentStorage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CustodyViewModel2(app: Application): AndroidViewModel(app) {
    private val repo = CustodyRepository(com.myaccounts.app.data.local.AppDatabase.getInstance(app), app)
    val custodies = repo.observeCustodies().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun custody(id: Long) = repo.observeCustody(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun persons(id: Long) = repo.observePersons(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun accounts(id: Long) = repo.observeAccounts(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun transactions(id: Long) = repo.observeTransactions(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun personTransactions(id: Long, personId: Long, currency: String) = repo.observePersonTransactions(id, personId, currency).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun attachments(transactionId: Long): List<CustodyTransactionAttachmentEntity> = repo.attachments(transactionId)
    fun create(c: CustodyEntity) = viewModelScope.launch { repo.createCustody(c) }
    fun addPerson(id: Long, p: CustodyPersonEntity) = viewModelScope.launch { repo.addPerson(id, p) }
    fun updatePerson(p: CustodyPersonEntity) = viewModelScope.launch { repo.updatePerson(p) }
    fun addTransaction(id: Long, currency: String, type: String, personId: Long?, amount: Long, description: String, date: Long, attachments: List<CustodyAttachmentStorage.Selected> = emptyList()) = viewModelScope.launch { repo.addTransaction(id, currency, type, personId, amount, description, date, attachments) }
    fun updateTransaction(id: Long, currency: String, type: String, personId: Long?, amount: Long, description: String, date: Long, newAttachments: List<CustodyAttachmentStorage.Selected> = emptyList(), deleted: List<CustodyTransactionAttachmentEntity> = emptyList()) = viewModelScope.launch { repo.updateTransaction(id, currency, type, personId, amount, description, date, newAttachments, deleted) }
    fun deleteTransaction(id: Long) = viewModelScope.launch { repo.deleteTransaction(id) }
    fun deleteAttachment(a: CustodyTransactionAttachmentEntity) = viewModelScope.launch { com.myaccounts.app.data.custody.CustodyAttachmentStore(getApplication()).delete(a) }
    fun archive(id: Long) = viewModelScope.launch { repo.archive(id) }
}
