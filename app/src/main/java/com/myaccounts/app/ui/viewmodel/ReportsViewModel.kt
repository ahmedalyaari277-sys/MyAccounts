package com.myaccounts.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myaccounts.app.data.reports.CurrencyReportPersonRow
import com.myaccounts.app.data.reports.CurrencyReportSummary
import com.myaccounts.app.data.reports.GeneralReportTransactionRow
import com.myaccounts.app.data.reports.PersonCurrencySummaryRow
import com.myaccounts.app.data.reports.PersonReportSummary
import com.myaccounts.app.data.reports.PersonReportTransaction
import com.myaccounts.app.data.reports.PersonReportTransactionRow
import com.myaccounts.app.data.reports.ReportRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReportsUiState(
    val selectedCurrencyCode: String = "YER",
    val people: List<CurrencyReportPersonRow> = emptyList(),
    val currencySummary: CurrencyReportSummary? = null,
    val selectedPersonSummary: PersonReportSummary? = null,
    val selectedPersonTransactions: List<PersonReportTransaction> = emptyList(),
    val selectedPersonTransactionRows: List<PersonReportTransactionRow> = emptyList(),
    val selectedPersonOpeningBalanceMinor: Long = 0L,
    val selectedPersonId: Long? = null,
    val personCurrencySummaries: List<PersonCurrencySummaryRow> = emptyList(),
    val generalTransactions: List<GeneralReportTransactionRow> = emptyList(),
    val startDateMillis: Long? = null,
    val endDateMillisExclusive: Long? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ReportsViewModel(
    private val repository: ReportRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    private var peopleObservationJob: Job? = null

    fun selectCurrency(currencyCode: String) {
        if (currencyCode == _uiState.value.selectedCurrencyCode) return

        _uiState.value = _uiState.value.copy(
            selectedCurrencyCode = currencyCode,
            people = emptyList(),
            currencySummary = null,
            selectedPersonId = null,
            selectedPersonSummary = null,
            selectedPersonTransactions = emptyList(),
            selectedPersonTransactionRows = emptyList(),
            selectedPersonOpeningBalanceMinor = 0L,
            personCurrencySummaries = emptyList(),
            generalTransactions = emptyList(),
            errorMessage = null
        )
        loadCurrencyReport()
    }

    fun loadCurrencyReport() {
        val state = _uiState.value
        val currencyCode = state.selectedCurrencyCode
        val startDateMillis = state.startDateMillis ?: 0L
        val endDateMillisExclusive = state.endDateMillisExclusive ?: Long.MAX_VALUE

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val summary = repository.getCurrencyReportSummary(currencyCode, startDateMillis, endDateMillisExclusive)
                val personSummaries = repository.getPersonCurrencySummary(currencyCode, startDateMillis, endDateMillisExclusive)
                val detailedTransactions = repository.getGeneralReportTransactions(currencyCode, startDateMillis, endDateMillisExclusive)

                _uiState.value = _uiState.value.copy(
                    currencySummary = summary,
                    personCurrencySummaries = personSummaries,
                    generalTransactions = detailedTransactions,
                    isLoading = false
                )
                observePeople(currencyCode, startDateMillis, endDateMillisExclusive)
            } catch (exception: Exception) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = exception.message ?: "حدث خطأ أثناء تحميل التقرير"
                )
            }
        }
    }

    private fun observePeople(currencyCode: String, startDateMillis: Long, endDateMillisExclusive: Long) {
        peopleObservationJob?.cancel()
        peopleObservationJob = viewModelScope.launch {
            try {
                repository.observeCurrencyReportPeople(currencyCode, startDateMillis, endDateMillisExclusive).collect { people ->
                    _uiState.value = _uiState.value.copy(people = people)
                }
            } catch (exception: Exception) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                _uiState.value = _uiState.value.copy(
                    errorMessage = exception.message ?: "حدث خطأ أثناء تحميل بيانات الأشخاص"
                )
            }
        }
    }

    fun selectPerson(personId: Long) {
        _uiState.value = _uiState.value.copy(
            selectedPersonId = personId,
            selectedPersonSummary = null,
            selectedPersonTransactions = emptyList(),
            selectedPersonTransactionRows = emptyList(),
            selectedPersonOpeningBalanceMinor = 0L,
            errorMessage = null
        )
        loadPersonReport()
    }

    fun setAllTime() {
        _uiState.value = _uiState.value.copy(startDateMillis = null, endDateMillisExclusive = null, errorMessage = null)
        loadCurrencyReport()
        if (_uiState.value.selectedPersonId != null) loadPersonReport()
    }

    fun setDateRange(startDateMillis: Long, endDateMillisExclusive: Long) {
        require(endDateMillisExclusive > startDateMillis) { "يجب أن يكون تاريخ النهاية بعد تاريخ البداية" }
        _uiState.value = _uiState.value.copy(
            startDateMillis = startDateMillis,
            endDateMillisExclusive = endDateMillisExclusive,
            errorMessage = null
        )
        loadCurrencyReport()
        if (_uiState.value.selectedPersonId != null) loadPersonReport()
    }

    fun clearDateRange() = setAllTime()

    fun loadPersonReport() {
        val state = _uiState.value
        val personId = state.selectedPersonId ?: return
        val currencyCode = state.selectedCurrencyCode
        val startDateMillis = state.startDateMillis ?: 0L
        val endDateMillisExclusive = state.endDateMillisExclusive ?: Long.MAX_VALUE

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val summary = repository.getPersonReportSummary(personId, currencyCode, startDateMillis, endDateMillisExclusive)
                val openingBalance = repository.getPersonOpeningBalance(personId, currencyCode, startDateMillis)
                val transactionRows = repository.getPersonReportTransactionRows(personId, currencyCode, startDateMillis, endDateMillisExclusive)
                val transactions = transactionRows.map { row ->
                    PersonReportTransaction(
                        transactionId = row.transactionId,
                        transactionDate = row.transactionDate,
                        type = row.type,
                        amountMinor = row.amountMinor,
                        description = row.description,
                        balanceMinor = row.balanceMinor
                    )
                }
                _uiState.value = _uiState.value.copy(
                    selectedPersonSummary = summary,
                    selectedPersonTransactions = transactions,
                    selectedPersonTransactionRows = transactionRows,
                    selectedPersonOpeningBalanceMinor = openingBalance,
                    isLoading = false
                )
            } catch (exception: Exception) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = exception.message ?: "حدث خطأ أثناء تحميل تقرير الشخص"
                )
            }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(errorMessage = null) }
    fun refresh() = loadCurrencyReport()

    override fun onCleared() {
        peopleObservationJob?.cancel()
        peopleObservationJob = null
        super.onCleared()
    }
}
