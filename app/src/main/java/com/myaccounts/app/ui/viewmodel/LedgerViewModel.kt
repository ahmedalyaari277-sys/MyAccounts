package com.myaccounts.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myaccounts.app.data.local.dao.PersonWithAccounts
import com.myaccounts.app.domain.repository.LedgerRepositoryContract
import com.myaccounts.app.domain.usecase.AddPersonUseCase
import com.myaccounts.app.domain.usecase.DeletePersonUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LedgerUiState(
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null
)

class LedgerViewModel(
    repository: LedgerRepositoryContract
) : ViewModel() {

    private val addPersonUseCase = AddPersonUseCase(repository)

    private val deletePersonUseCase = DeletePersonUseCase(repository)

    val personsWithAccounts: StateFlow<List<PersonWithAccounts>> =
        repository.allPersonsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _uiState = MutableStateFlow(LedgerUiState())

    val uiState: StateFlow<LedgerUiState> =
        _uiState.asStateFlow()

    fun addPerson(
        name: String,
        phone: String,
        address: String
    ) {
        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(
                isSaving = true,
                errorMessage = null
            )

            val result = addPersonUseCase(
                name = name,
                phone = phone,
                address = address
            )

            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = error.message
                            ?: "حدث خطأ أثناء حفظ الشخص"
                    )
                }
            )
        }
    }

    fun deletePerson(
        personId: Long
    ) {
        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(
                isDeleting = true,
                errorMessage = null
            )

            val result = deletePersonUseCase(personId)

            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        errorMessage = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        errorMessage = error.message
                            ?: "حدث خطأ أثناء حذف الشخص"
                    )
                }
            )
        }
    }

    suspend fun getPersonWithAccounts(
        personId: Long
    ): PersonWithAccounts? {
        return personsWithAccounts.value.firstOrNull {
            it.person.id == personId
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null
        )
    }
}
