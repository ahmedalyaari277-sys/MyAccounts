package com.myaccounts.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myaccounts.app.data.local.CurrencyAccountEntity
import com.myaccounts.app.data.local.PersonEntity
import com.myaccounts.app.data.repository.LedgerRepositoryContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class LedgerViewModel(
    private val repository: LedgerRepositoryContract
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    private val _people = MutableStateFlow<List<PersonEntity>>(emptyList())
    val people: StateFlow<List<PersonEntity>> = _people.asStateFlow()

    private val _currencyAccounts =
        MutableStateFlow<List<CurrencyAccountEntity>>(emptyList())

    val currencyAccounts: StateFlow<List<CurrencyAccountEntity>> =
        _currencyAccounts.asStateFlow()

    init {
        viewModelScope.launch {
            searchQuery
                .flatMapLatest { query ->
                    repository.observePeople(query)
                }
                .collect { result ->
                    _people.value = result
                }
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun loadCurrencyAccounts(personId: Long) {
        viewModelScope.launch {
            repository
                .observeCurrencyAccounts(personId)
                .collect { accounts ->
                    _currencyAccounts.value = accounts
                }
        }
    }

    fun addPerson(
        name: String,
        phone: String = "",
        address: String = "",
        notes: String = ""
    ) {
        viewModelScope.launch {
            repository.insertPerson(
                PersonEntity(
                    name = name.trim(),
                    phone = phone.trim(),
                    address = address.trim(),
                    notes = notes.trim()
                )
            )
        }
    }

    fun updatePerson(person: PersonEntity) {
        viewModelScope.launch {
            repository.updatePerson(person)
        }
    }

    fun deletePerson(personId: Long) {
        viewModelScope.launch {
            repository.deletePerson(personId)
        }
    }
}
