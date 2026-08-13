package com.myaccounts.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myaccounts.app.data.local.AppDatabase
import com.myaccounts.app.data.local.dao.AccountBalance
import com.myaccounts.app.data.local.dao.PersonWithAccounts
import com.myaccounts.app.data.local.entity.CurrencyAccountEntity
import com.myaccounts.app.data.local.entity.TransactionEntity
import com.myaccounts.app.data.repository.LedgerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LedgerViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository: LedgerRepository =
        LedgerRepository(
            AppDatabase
                .getInstance(application)
                .ledgerDao()
        )


    // =========================================================
    // الأشخاص
    // =========================================================

    /**
     * جميع الأشخاص مع حساباتهم.
     *
     * هذه البيانات تستخدم في الشاشة الرئيسية.
     */
    val personsWithAccounts: StateFlow<List<PersonWithAccounts>> =
        repository.allPersonsFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )


    /**
     * إضافة شخص جديد.
     */
    fun addPerson(
        name: String,
        phone: String,
        address: String
    ) {

        viewModelScope.launch {

            if (name.isNotBlank()) {

                repository.addPerson(
                    name = name.trim(),
                    phone = phone.trim(),
                    address = address.trim()
                )
            }
        }
    }


    /**
     * حذف شخص.
     */
    fun deletePerson(
        personId: Long
    ) {

        viewModelScope.launch {

            repository.deletePerson(
                personId
            )
        }
    }


    // =========================================================
    // الشخص الواحد
    // =========================================================

    /**
     * الحصول على شخص معين مع حساباته.
     */
    fun getPersonWithAccountsFlow(
        personId: Long
    ): Flow<PersonWithAccounts?> {

        return repository.getPersonWithAccountsFlow(
            personId
        )
    }


    // =========================================================
    // الحسابات
    // =========================================================

    /**
     * الحصول على حسابات شخص معين.
     */
    fun getAccountsForPersonFlow(
        personId: Long
    ): Flow<List<CurrencyAccountEntity>> {

        return repository.getAccountsForPersonFlow(
            personId
        )
    }


    /**
     * الحصول على حساب معين.
     */
    fun getAccount(
        accountId: Long
    ): CurrencyAccountEntity? {

        return null
    }


    // =========================================================
    // الأرصدة
    // =========================================================

    /**
     * الحصول على أرصدة جميع عملات الشخص.
     *
     * النتيجة:
     *
     * YER
     * SAR
     * USD
     */
    fun getAccountBalancesForPersonFlow(
        personId: Long
    ): Flow<List<AccountBalance>> {

        return repository.getAccountBalancesForPersonFlow(
            personId
        )
    }


    /**
     * الحصول على رصيد حساب معين.
     */
    fun getAccountBalanceFlow(
        accountId: Long
    ): Flow<AccountBalance?> {

        return repository.getAccountBalanceFlow(
            accountId
        )
    }


    // =========================================================
    // المعاملات
    // =========================================================

    /**
     * إضافة معاملة عامة.
     *
     * type:
     *
     * RECEIVABLE = لي
     * PAYABLE    = علي
     */
    fun addTransaction(
        currencyAccountId: Long,
        type: String,
        amountMinor: Long,
        description: String,
        transactionDate: Long = System.currentTimeMillis()
    ) {

        viewModelScope.launch {

            if (amountMinor > 0) {

                repository.addTransaction(
                    currencyAccountId = currencyAccountId,
                    type = type,
                    amountMinor = amountMinor,
                    description = description,
                    transactionDate = transactionDate
                )
            }
        }
    }


    /**
     * إضافة مبلغ "لي".
     */
    fun addReceivable(
        currencyAccountId: Long,
        amountMinor: Long,
        description: String,
        transactionDate: Long = System.currentTimeMillis()
    ) {

        viewModelScope.launch {

            if (amountMinor > 0) {

                repository.addReceivable(
                    currencyAccountId = currencyAccountId,
                    amountMinor = amountMinor,
                    description = description,
                    transactionDate = transactionDate
                )
            }
        }
    }


    /**
     * إضافة مبلغ "علي".
     */
    fun addPayable(
        currencyAccountId: Long,
        amountMinor: Long,
        description: String,
        transactionDate: Long = System.currentTimeMillis()
    ) {

        viewModelScope.launch {

            if (amountMinor > 0) {

                repository.addPayable(
                    currencyAccountId = currencyAccountId,
                    amountMinor = amountMinor,
                    description = description,
                    transactionDate = transactionDate
                )
            }
        }
    }


    /**
     * الحصول على معاملات حساب معين.
     */
    fun getTransactionsForAccountFlow(
        accountId: Long
    ): Flow<List<TransactionEntity>> {

        return repository.getTransactionsForAccountFlow(
            accountId
        )
    }


    /**
     * الحصول على جميع معاملات الشخص.
     */
    fun getTransactionsForPersonFlow(
        personId: Long
    ): Flow<List<TransactionEntity>> {

        return repository.getTransactionsForPersonFlow(
            personId
        )
    }


    /**
     * حذف معاملة.
     */
    fun deleteTransaction(
        transactionId: Long
    ) {

        viewModelScope.launch {

            repository.deleteTransaction(
                transactionId
            )
        }
    }
}
