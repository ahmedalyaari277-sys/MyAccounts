package com.myaccounts.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myaccounts.app.data.local.TransactionEntity
import com.myaccounts.app.data.repository.TransactionRepositoryContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class TransactionViewModel(
    private val repository: TransactionRepositoryContract
) : ViewModel() {

    private val selectedAccountId =
        MutableStateFlow<Long?>(null)

    private val _transactions =
        MutableStateFlow<List<TransactionEntity>>(
            emptyList()
        )

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
                        repository.observeTransactions(
                            accountId
                        )
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
                        repository.observeBalance(
                            accountId
                        )
                    }
                }
                .collect { result ->

                    _balance.value = result
                }
        }
    }

    fun selectAccount(
        accountId: Long?
    ) {
        selectedAccountId.value = accountId
    }

    fun addTransaction(
        transaction: TransactionEntity
    ) {

        viewModelScope.launch {

            repository.addTransaction(
                transaction
            )
        }
    }

    fun updateTransaction(
        transaction: TransactionEntity
    ) {

        viewModelScope.launch {

            repository.updateTransaction(
                transaction
            )
        }
    }

    fun deleteTransaction(
        transaction: TransactionEntity
    ) {

        viewModelScope.launch {

            repository.deleteTransaction(
                transaction
            )
        }
    }

    fun deleteTransactionById(
        transactionId: Long
    ) {

        viewModelScope.launch {

            repository.deleteTransactionById(
                transactionId
            )
        }
    }

    suspend fun getTransaction(
        transactionId: Long
    ): TransactionEntity? {

        return repository.getTransaction(
            transactionId
        )
    }

    suspend fun getTransactions(
        accountId: Long
    ): List<TransactionEntity> {

        return repository.getTransactions(
            accountId
        )
    }

    suspend fun getBalance(
        accountId: Long
    ): Long {

        return repository.getBalance(
            accountId
        )
    }
}
