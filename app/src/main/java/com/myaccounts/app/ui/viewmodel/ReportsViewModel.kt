package com.myaccounts.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myaccounts.app.data.reports.CurrencyReportPersonRow
import com.myaccounts.app.data.reports.CurrencyReportSummary
import com.myaccounts.app.data.reports.CurrencyReportPersonRowWithCurrency
import com.myaccounts.app.data.reports.GeneralReportTransactionRow
import com.myaccounts.app.data.reports.PersonCurrencySummaryRow
import com.myaccounts.app.data.reports.PersonReportSummary
import com.myaccounts.app.data.reports.PersonReportTransaction
import com.myaccounts.app.data.reports.PersonReportTransactionRow
import com.myaccounts.app.data.reports.MultiCurrencyPersonReport
import com.myaccounts.app.data.reports.ReportRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReportsUiState(
    val selectedCurrencyCode: String = "ALL",
    val people: List<CurrencyReportPersonRow> = emptyList(),
    val allCurrencyPeople: List<CurrencyReportPersonRowWithCurrency> = emptyList(),
    val currencySummary: CurrencyReportSummary? = null,
    val allCurrencySummaries: List<CurrencyReportSummary> = emptyList(),
    val selectedPersonSummary: PersonReportSummary? = null,
    val selectedPersonTransactions: List<PersonReportTransaction> = emptyList(),
    val selectedPersonTransactionRows: List<PersonReportTransactionRow> = emptyList(),
    val selectedPersonOpeningBalanceMinor: Long = 0L,
    val selectedPersonId: Long? = null,
    val selectedPersonMultiCurrencyReport: MultiCurrencyPersonReport? = null,
    val personCurrencySummaries: List<PersonCurrencySummaryRow> = emptyList(),
    val allCurrencyPersonSummaries: List<PersonCurrencySummaryRow> = emptyList(),
    val generalTransactions: List<GeneralReportTransactionRow> = emptyList(),
    val allCurrencyGeneralTransactions: List<GeneralReportTransactionRow> = emptyList(),
    val startDateMillis: Long? = null,
    val endDateMillisExclusive: Long? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ReportsViewModel(private val repository: ReportRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()
    private var peopleObservationJob: Job? = null

    fun selectCurrency(currencyCode: String) {
        _uiState.value = _uiState.value.copy(selectedCurrencyCode = currencyCode, errorMessage = null)
        if (currencyCode == "ALL") loadAllCurrencyReport() else loadCurrencyReport()
    }

    fun loadCurrencyReport() {
        val state = _uiState.value
        val currencyCode = state.selectedCurrencyCode.takeIf { it != "ALL" } ?: "YER"
        val start = state.startDateMillis ?: 0L
        val end = state.endDateMillisExclusive ?: Long.MAX_VALUE
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val summary = repository.getCurrencyReportSummary(currencyCode, start, end)
                val personSummaries = repository.getPersonCurrencySummary(currencyCode, start, end)
                val transactions = repository.getGeneralReportTransactions(currencyCode, start, end)
                _uiState.value = _uiState.value.copy(currencySummary = summary, personCurrencySummaries = personSummaries, generalTransactions = transactions, isLoading = false)
                observePeople(currencyCode, start, end)
            } catch (exception: Exception) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = exception.message ?: "حدث خطأ أثناء تحميل التقرير")
            }
        }
    }

    fun loadAllCurrencyReport() {
        val state = _uiState.value
        val start = state.startDateMillis ?: 0L
        val end = state.endDateMillisExclusive ?: Long.MAX_VALUE
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val summaries = repository.getAllCurrencyReportSummaries(start, end)
                val people = repository.getAllCurrencyPeople(start, end)
                val transactions = repository.getAllCurrencyGeneralTransactions(start, end)
                val personSummaries = repository.getAllCurrencyPersonSummaries(start, end)
                _uiState.value = _uiState.value.copy(allCurrencySummaries = summaries, allCurrencyPeople = people, allCurrencyGeneralTransactions = transactions, allCurrencyPersonSummaries = personSummaries, isLoading = false)
            } catch (exception: Exception) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = exception.message ?: "حدث خطأ أثناء تحميل التقارير")
            }
        }
    }

    private fun observePeople(currencyCode: String, start: Long, end: Long) {
        peopleObservationJob?.cancel()
        peopleObservationJob = viewModelScope.launch {
            try {
                repository.observeCurrencyReportPeople(currencyCode, start, end).collect { people -> _uiState.value = _uiState.value.copy(people = people) }
            } catch (exception: Exception) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                _uiState.value = _uiState.value.copy(errorMessage = exception.message ?: "حدث خطأ أثناء تحميل بيانات الأشخاص")
            }
        }
    }

    fun selectPerson(personId: Long) {
        _uiState.value = _uiState.value.copy(selectedPersonId = personId, selectedPersonSummary = null, selectedPersonTransactions = emptyList(), selectedPersonTransactionRows = emptyList(), selectedPersonOpeningBalanceMinor = 0L, selectedPersonMultiCurrencyReport = null, errorMessage = null)
        loadPersonReport()
        loadMultiCurrencyPersonReport()
    }

    fun loadPersonReport() {
        val state = _uiState.value
        val personId = state.selectedPersonId ?: return
        val currency = state.selectedCurrencyCode.takeIf { it != "ALL" } ?: "YER"
        val start = state.startDateMillis ?: 0L
        val end = state.endDateMillisExclusive ?: Long.MAX_VALUE
        viewModelScope.launch {
            try {
                val summary = repository.getPersonReportSummary(personId, currency, start, end)
                val rows = repository.getPersonReportTransactionRows(personId, currency, start, end)
                _uiState.value = _uiState.value.copy(selectedPersonSummary = summary, selectedPersonTransactions = rows.map { PersonReportTransaction(it.transactionId, it.transactionDate, it.type, it.amountMinor, it.description, it.balanceMinor) }, selectedPersonTransactionRows = rows, selectedPersonOpeningBalanceMinor = repository.getPersonOpeningBalance(personId, currency, start))
            } catch (exception: Exception) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                _uiState.value = _uiState.value.copy(errorMessage = exception.message ?: "حدث خطأ أثناء تحميل تقرير الشخص")
            }
        }
    }

    fun loadMultiCurrencyPersonReport() {
        val state = _uiState.value
        val personId = state.selectedPersonId ?: return
        val start = state.startDateMillis ?: 0L
        val end = state.endDateMillisExclusive ?: Long.MAX_VALUE
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                _uiState.value = _uiState.value.copy(selectedPersonMultiCurrencyReport = repository.getMultiCurrencyPersonReport(personId, start, end), isLoading = false)
            } catch (exception: Exception) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = exception.message ?: "حدث خطأ أثناء تحميل تقرير الشخص")
            }
        }
    }

    fun setAllTime() {
        _uiState.value = _uiState.value.copy(startDateMillis = null, endDateMillisExclusive = null, errorMessage = null)
        if (_uiState.value.selectedCurrencyCode == "ALL") loadAllCurrencyReport() else loadCurrencyReport()
        if (_uiState.value.selectedPersonId != null) loadMultiCurrencyPersonReport()
    }

    fun setDateRange(startDateMillis: Long, endDateMillisExclusive: Long) {
        require(endDateMillisExclusive > startDateMillis) { "يجب أن يكون تاريخ النهاية بعد تاريخ البداية" }
        _uiState.value = _uiState.value.copy(startDateMillis = startDateMillis, endDateMillisExclusive = endDateMillisExclusive, errorMessage = null)
        if (_uiState.value.selectedCurrencyCode == "ALL") loadAllCurrencyReport() else loadCurrencyReport()
        if (_uiState.value.selectedPersonId != null) loadMultiCurrencyPersonReport()
    }

    fun clearDateRange() = setAllTime()
    fun clearError() { _uiState.value = _uiState.value.copy(errorMessage = null) }
    fun refresh() { if (_uiState.value.selectedCurrencyCode == "ALL") loadAllCurrencyReport() else loadCurrencyReport() }

    override fun onCleared() {
        peopleObservationJob?.cancel()
        peopleObservationJob = null
        super.onCleared()
    }
}
