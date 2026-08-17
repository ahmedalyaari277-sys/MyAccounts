package com.myaccounts.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myaccounts.app.data.local.TransactionAttachmentEntity
import com.myaccounts.app.data.local.TransactionEntity
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

    private val selectedAccountId =
        MutableStateFlow<Long?>(null)

    private val _transactions =
        MutableStateFlow<List<TransactionEntity>>(emptyList())

    val transactions: StateFlow<List<TransactionEntity>> =
        _transactions.asStateFlow()

    private val _balance =
        MutableStateFlow(0L)

    val balance: StateFlow<Long> =
        _balance.asStateFlow()

    init {
        viewModelScope.launch {
            selectedAccountId
                .flatMapLatest { accountId ->
                    if (accountId == null) {
                        flowOf(emptyList())
                    } else {
                        repository.observeTransactions(accountId)
                    }
                }
                .collect { result ->
                    _transactions.value = result
                }
        }

        viewModelScope.launch {
            selectedAccountId
                .flatMapLatest { accountId ->
                    if (accountId == null) {
                        flowOf(0L)
                    } else {
                        repository.observeBalance(accountId)
                    }
                }
                .collect { result ->
                    _balance.value = result
                }
        }
    }

    fun selectAccount(accountId: Long?) {
        selectedAccountId.value = accountId
    }

    fun addTransaction(
        transaction: TransactionEntity,
        attachments: List<TransactionAttachmentStorage.SelectedAttachment> = emptyList()
    ) {
        viewModelScope.launch {
            val transactionId = repository.addTransaction(transaction)
            if (attachments.isEmpty()) return@launch

            try {
                val stored = TransactionAttachmentStorage.saveAttachments(
                    application,
                    transactionId,
                    attachments
                )
                repository.addAttachments(stored)
            } catch (error: Throwable) {
                repository.deleteTransactionById(transactionId)
            }
        }
    }

    fun updateTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.updateTransaction(transaction)
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            deleteTransactionAndFiles(transaction.id)
        }
    }

    fun deleteTransactionById(transactionId: Long) {
        viewModelScope.launch {
            deleteTransactionAndFiles(transactionId)
        }
    }

    private suspend fun deleteTransactionAndFiles(transactionId: Long) {
        val attachments = repository.getAttachments(transactionId)
        repository.deleteTransactionById(transactionId)
        TransactionAttachmentStorage.deleteTransactionFiles(
            application,
            transactionId,
            attachments
        )
    }

    suspend fun getTransaction(transactionId: Long): TransactionEntity? {
        return repository.getTransaction(transactionId)
    }

    suspend fun getTransactions(accountId: Long): List<TransactionEntity> {
        return repository.getTransactions(accountId)
    }

    suspend fun getBalance(accountId: Long): Long {
        return repository.getBalance(accountId)
    }

    fun observeAttachments(
        transactionId: Long
    ): Flow<List<TransactionAttachmentEntity>> {
        return repository.observeAttachments(transactionId)
    }

    fun observeAttachmentCount(
        transactionId: Long
    ): Flow<Int> {
        return repository.observeAttachmentCount(transactionId)
    }

    fun deleteAttachment(
        attachment: TransactionAttachmentEntity
    ) {
        viewModelScope.launch {
            repository.deleteAttachment(attachment)
            TransactionAttachmentStorage.deleteFile(application, attachment)
        }
    }
}
