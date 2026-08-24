package com.myaccounts.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myaccounts.app.data.local.TransactionAttachmentEntity
import com.myaccounts.app.data.local.TransactionEntity
import com.myaccounts.app.data.local.dao.ArchivedTransactionRow
import com.myaccounts.app.data.repository.TransactionRepositoryContract
import com.myaccounts.app.util.TransactionAttachmentStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class TransactionViewModel(
    private val repository: TransactionRepositoryContract,
    private val application: Application
) : ViewModel() {
    private val selectedAccountId = MutableStateFlow<Long?>(null)
    private val _transactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val transactions: StateFlow<List<TransactionEntity>> = _transactions.asStateFlow()
    private val _archivedTransactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val archivedTransactions: StateFlow<List<TransactionEntity>> = _archivedTransactions.asStateFlow()
    private val _archivedTransactionRows = MutableStateFlow<List<ArchivedTransactionRow>>(emptyList())
    val archivedTransactionRows: StateFlow<List<ArchivedTransactionRow>> = _archivedTransactionRows.asStateFlow()
    private val _balance = MutableStateFlow(0L)
    val balance: StateFlow<Long> = _balance.asStateFlow()

    init {
        viewModelScope.launch {
            selectedAccountId.flatMapLatest { accountId ->
                if (accountId == null) flowOf(emptyList()) else repository.observeTransactions(accountId)
            }.collect { _transactions.value = it }
        }
        viewModelScope.launch {
            selectedAccountId.flatMapLatest { accountId ->
                if (accountId == null) flowOf(0L) else repository.observeBalance(accountId)
            }.collect { _balance.value = it }
        }
        viewModelScope.launch { repository.observeArchivedTransactions().collect { _archivedTransactions.value = it } }
        viewModelScope.launch { repository.observeArchivedTransactionRows().collect { _archivedTransactionRows.value = it } }
    }

    fun selectAccount(accountId: Long?) { selectedAccountId.value = accountId }

    fun addTransaction(transaction: TransactionEntity, attachments: List<TransactionAttachmentStorage.SelectedAttachment> = emptyList()) {
        viewModelScope.launch {
            val transactionId = repository.addTransaction(transaction)
            if (attachments.isEmpty()) return@launch
            try {
                val stored = TransactionAttachmentStorage.saveAttachments(application, transactionId, attachments)
                repository.addAttachments(stored)
            } catch (error: Throwable) {
                repository.deleteTransactionById(transactionId)
            }
        }
    }

    fun updateTransaction(transaction: TransactionEntity) { viewModelScope.launch { repository.updateTransaction(transaction) } }

    fun updateTransactionWithAttachments(
        transaction: TransactionEntity,
        newAttachments: List<TransactionAttachmentStorage.SelectedAttachment>,
        removedAttachments: List<TransactionAttachmentEntity>
    ) {
        viewModelScope.launch {
            val stored = if (newAttachments.isEmpty()) {
                emptyList()
            } else {
                TransactionAttachmentStorage.saveAttachments(application, transaction.id, newAttachments)
            }
            try {
                repository.updateTransaction(transaction)
                if (removedAttachments.isNotEmpty()) {
                    removedAttachments.forEach { attachment ->
                        repository.deleteAttachment(attachment)
                        TransactionAttachmentStorage.deleteFile(application, attachment)
                    }
                }
                if (stored.isNotEmpty()) repository.addAttachments(stored)
            } catch (error: Throwable) {
                stored.forEach { attachment ->
                    TransactionAttachmentStorage.deleteFile(application, attachment)
                }
            }
        }
    }

    fun archiveTransaction(transaction: TransactionEntity) { viewModelScope.launch { repository.archiveTransaction(transaction.id) } }
    fun archiveTransactionById(transactionId: Long) { viewModelScope.launch { repository.archiveTransaction(transactionId) } }
    fun restoreTransaction(transactionId: Long) { viewModelScope.launch { repository.restoreTransaction(transactionId) } }

    /** زر حذف العملية من الحساب ينقلها إلى الأرشيف، ولا يحذفها نهائيًا. */
    fun deleteTransaction(transaction: TransactionEntity) { viewModelScope.launch { repository.archiveTransaction(transaction.id) } }
    fun deleteTransactionById(transactionId: Long) { viewModelScope.launch { repository.archiveTransaction(transactionId) } }

    /** الحذف النهائي متاح فقط من الأرشيف. */
    fun permanentlyDeleteTransaction(transactionId: Long) {
        viewModelScope.launch {
            val attachments = repository.getAttachments(transactionId)
            repository.deleteTransactionById(transactionId)
            TransactionAttachmentStorage.deleteTransactionFiles(application, transactionId, attachments)
        }
    }

    suspend fun getTransaction(transactionId: Long): TransactionEntity? = repository.getTransaction(transactionId)
    suspend fun getTransactions(accountId: Long): List<TransactionEntity> = repository.getTransactions(accountId)
    suspend fun getBalance(accountId: Long): Long = repository.getBalance(accountId)
    fun observeArchivedTransactionsForPerson(personId: Long): Flow<List<TransactionEntity>> = repository.observeArchivedTransactionsForPerson(personId)
    fun observeAttachments(transactionId: Long): Flow<List<TransactionAttachmentEntity>> = repository.observeAttachments(transactionId)
    fun observeAttachmentCount(transactionId: Long): Flow<Int> = repository.observeAttachmentCount(transactionId)

    fun deleteAttachment(attachment: TransactionAttachmentEntity) {
        viewModelScope.launch {
            repository.deleteAttachment(attachment)
            TransactionAttachmentStorage.deleteFile(application, attachment)
        }
    }
}
