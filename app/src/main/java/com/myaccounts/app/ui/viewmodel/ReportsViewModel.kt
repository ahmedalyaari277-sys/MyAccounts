package com.myaccounts.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myaccounts.app.data.reports.CurrencyReportPersonRow
import com.myaccounts.app.data.reports.CurrencyReportSummary
import com.myaccounts.app.data.reports.PersonReportSummary
import com.myaccounts.app.data.reports.PersonReportTransaction
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
    val selectedPersonId: Long? = null,
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
                val summary = repository.getCurrencyReportSummary(
                    currencyCode = currencyCode,
                    startDateMillis = startDateMillis,
                    endDateMillisExclusive = endDateMillisExclusive
                )

                _uiState.value = _uiState.value.copy(
                    currencySummary = summary,
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

    private fun observePeople(
        currencyCode: String,
        startDateMillis: Long,
        endDateMillisExclusive: Long
    ) {
        peopleObservationJob?.cancel()
        peopleObservationJob = viewModelScope.launch {
            try {
                repository.observeCurrencyReportPeople(
                    currencyCode = currencyCode,
                    startDateMillis = startDateMillis,
                    endDateMillisExclusive = endDateMillisExclusive
                ).collect { people ->
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
            errorMessage = null
        )
        loadPersonReport()
    }

    fun setAllTime() {
        _uiState.value = _uiState.value.copy(
            startDateMillis = null,
            endDateMillisExclusive = null,
            errorMessage = null
        )
        loadCurrencyReport()
        if (_uiState.value.selectedPersonId != null) loadPersonReport()
    }

    fun setDateRange(startDateMillis: Long, endDateMillisExclusive: Long) {
        require(endDateMillisExclusive > startDateMillis) {
            "يجب أن يكون تاريخ النهاية بعد تاريخ البداية"
        }
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
                val summary = repository.getPersonReportSummary(
                    personId = personId,
                    currencyCode = currencyCode,
                    startDateMillis = startDateMillis,
                    endDateMillisExclusive = endDateMillisExclusive
                )
                val transactions = repository.getPersonReportTransactions(
                    personId = personId,
                    currencyCode = currencyCode,
                    startDateMillis = startDateMillis,
                    endDateMillisExclusive = endDateMillisExclusive
                )
                _uiState.value = _uiState.value.copy(
                    selectedPersonSummary = summary,
                    selectedPersonTransactions = transactions,
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

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun refresh() = loadCurrencyReport()

    override fun onCleared() {
        peopleObservationJob?.cancel()
        peopleObservationJob = null
        super.onCleared()
    }
}
