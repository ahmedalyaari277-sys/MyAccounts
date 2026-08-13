package com.myaccounts.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myaccounts.app.data.local.AppDatabase
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

    // ============================================================
    // Repository
    // ============================================================

    private val repository: LedgerRepository =
        LedgerRepository(
            AppDatabase
                .getInstance(application)
                .ledgerDao()
        )


    // ============================================================
    // الأشخاص
    // ============================================================

    /**
     * جميع الأشخاص مع حساباتهم.
     *
     * يتم تحديث القائمة تلقائياً عندما تتغير قاعدة البيانات.
     */
    val personsWithAccounts: StateFlow<List<PersonWithAccounts>> =
        repository.allPersonsFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )


    /**
     * إضافة شخص جديد.
     *
     * عند نجاح العملية يتم إنشاء حسابات:
     *
     * YER
     * SAR
     * USD
     */
    fun addPerson(
        name: String,
        phone: String,
        address: String,
        onSuccess: (Long) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {

        viewModelScope.launch {

            try {

                val personId =
                    repository.addPerson(
                        name = name,
                        phone = phone,
                        address = address
                    )

                onSuccess(personId)

            } catch (e: Exception) {

                onError(
                    e.message
                        ?: "حدث خطأ أثناء إضافة الشخص"
                )
            }
        }
    }


    /**
     * تعديل بيانات شخص.
     */
    fun updatePerson(
        personId: Long,
        name: String,
        phone: String,
        address: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {

        viewModelScope.launch {

            try {

                repository.updatePerson(
                    personId = personId,
                    name = name,
                    phone = phone,
                    address = address
                )

                onSuccess()

            } catch (e: Exception) {

                onError(
                    e.message
                        ?: "حدث خطأ أثناء تعديل بيانات الشخص"
                )
            }
        }
    }


    /**
     * حذف شخص.
     */
    fun deletePerson(
        personId: Long,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {

        viewModelScope.launch {

            try {

                repository.deletePerson(personId)

                onSuccess()

            } catch (e: Exception) {

                onError(
                    e.message
                        ?: "حدث خطأ أثناء حذف الشخص"
                )
            }
        }
    }


    // ============================================================
    // حسابات العملات
    // ============================================================

    /**
     * الحصول على حسابات الشخص.
     */
    fun getCurrencyAccounts(
        personId: Long,
        onResult: (List<CurrencyAccountEntity>) -> Unit,
        onError: (String) -> Unit = {}
    ) {

        viewModelScope.launch {

            try {

                val accounts =
                    repository.getCurrencyAccounts(personId)

                onResult(accounts)

            } catch (e: Exception) {

                onError(
                    e.message
                        ?: "حدث خطأ أثناء جلب الحسابات"
                )
            }
        }
    }


    /**
     * الحصول على حساب عملة محدد.
     *
     * currency:
     *
     * YER
     * SAR
     * USD
     */
    fun getCurrencyAccount(
        personId: Long,
        currency: String,
        onResult: (CurrencyAccountEntity?) -> Unit,
        onError: (String) -> Unit = {}
    ) {

        viewModelScope.launch {

            try {

                val account =
                    repository.getCurrencyAccount(
                        personId = personId,
                        currency = currency
                    )

                onResult(account)

            } catch (e: Exception) {

                onError(
                    e.message
                        ?: "حدث خطأ أثناء جلب حساب العملة"
                )
            }
        }
    }


    // ============================================================
    // الحركات
    // ============================================================

    /**
     * إضافة حركة مالية.
     *
     * type:
     *
     * RECEIVABLE = لي
     * PAYABLE    = علي
     */
    fun addTransaction(
        accountId: Long,
        type: String,
        amount: Long,
        description: String,
        transactionDate: Long = System.currentTimeMillis(),
        onSuccess: (Long) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {

        viewModelScope.launch {

            try {

                val transactionId =
                    repository.addTransaction(
                        accountId = accountId,
                        type = type,
                        amount = amount,
                        description = description,
                        transactionDate = transactionDate
                    )

                onSuccess(transactionId)

            } catch (e: Exception) {

                onError(
                    e.message
                        ?: "حدث خطأ أثناء إضافة الحركة"
                )
            }
        }
    }


    /**
     * الحصول على حركات حساب معين.
     *
     * Flow حتى تتحدث الشاشة تلقائياً.
     */
    fun getTransactions(
        accountId: Long
    ): Flow<List<TransactionEntity>> {

        return repository.getTransactions(accountId)
    }


    /**
     * تعديل حركة.
     */
    fun updateTransaction(
        transaction: TransactionEntity,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {

        viewModelScope.launch {

            try {

                repository.updateTransaction(transaction)

                onSuccess()

            } catch (e: Exception) {

                onError(
                    e.message
                        ?: "حدث خطأ أثناء تعديل الحركة"
                )
            }
        }
    }


    /**
     * حذف حركة.
     */
    fun deleteTransaction(
        transactionId: Long,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {

        viewModelScope.launch {

            try {

                repository.deleteTransaction(transactionId)

                onSuccess()

            } catch (e: Exception) {

                onError(
                    e.message
                        ?: "حدث خطأ أثناء حذف الحركة"
                )
            }
        }
    }


    // ============================================================
    // الأرصدة
    // ============================================================

    /**
     * الحصول على الرصيد:
     *
     * لي - علي
     */
    fun getBalance(
        accountId: Long,
        onResult: (Long) -> Unit,
        onError: (String) -> Unit = {}
    ) {

        viewModelScope.launch {

            try {

                val balance =
                    repository.getBalance(accountId)

                onResult(balance)

            } catch (e: Exception) {

                onError(
                    e.message
                        ?: "حدث خطأ أثناء حساب الرصيد"
                )
            }
        }
    }


    /**
     * إجمالي ما له.
     */
    fun getTotalReceivable(
        accountId: Long,
        onResult: (Long) -> Unit,
        onError: (String) -> Unit = {}
    ) {

        viewModelScope.launch {

            try {

                val total =
                    repository.getTotalReceivable(accountId)

                onResult(total)

            } catch (e: Exception) {

                onError(
                    e.message
                        ?: "حدث خطأ أثناء حساب إجمالي المبالغ التي له"
                )
            }
        }
    }


    /**
     * إجمالي ما عليه.
     */
    fun getTotalPayable(
        accountId: Long,
        onResult: (Long) -> Unit,
        onError: (String) -> Unit = {}
    ) {

        viewModelScope.launch {

            try {

                val total =
                    repository.getTotalPayable(accountId)

                onResult(total)

            } catch (e: Exception) {

                onError(
                    e.message
                        ?: "حدث خطأ أثناء حساب إجمالي المبالغ التي عليه"
                )
            }
        }
    }
}
