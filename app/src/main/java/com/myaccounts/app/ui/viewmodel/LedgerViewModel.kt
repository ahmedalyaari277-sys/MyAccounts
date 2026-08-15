package com.myaccounts.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myaccounts.app.data.local.PersonEntity
import com.myaccounts.app.data.local.dao.PersonWithAccounts
import com.myaccounts.app.data.repository.LedgerRepositoryContract
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModel(
    private val repository: LedgerRepositoryContract
) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    private val _people = MutableStateFlow<List<PersonEntity>>(emptyList())
    val people: StateFlow<List<PersonEntity>> = _people.asStateFlow()
    private val _personsWithAccounts = MutableStateFlow<List<PersonWithAccounts>>(emptyList())
    val personsWithAccounts: StateFlow<List<PersonWithAccounts>> = _personsWithAccounts.asStateFlow()
    private val _archivedPersonsWithAccounts = MutableStateFlow<List<PersonWithAccounts>>(emptyList())
    val archivedPersonsWithAccounts: StateFlow<List<PersonWithAccounts>> = _archivedPersonsWithAccounts.asStateFlow()

    init {
        viewModelScope.launch {
            searchQuery.flatMapLatest { query -> repository.observePeople(query) }.collect { _people.value = it }
        }
        viewModelScope.launch {
            repository.observePersonsWithAccounts().collect { _personsWithAccounts.value = it }
        }
        viewModelScope.launch {
            repository.observeArchivedPersonsWithAccounts().collect { _archivedPersonsWithAccounts.value = it }
        }
    }

    fun setSearchQuery(query: String) { searchQuery.value = query }

    fun addPerson(name: String, phone: String = "", address: String = "", notes: String = "") {
        if (name.isBlank()) return
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

    fun updatePerson(personId: Long, name: String, phone: String, address: String, notes: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val currentPerson = _personsWithAccounts.value.firstOrNull { it.person.id == personId }?.person
            if (currentPerson != null) {
                repository.updatePerson(
                    currentPerson.copy(
                        name = name.trim(),
                        phone = phone.trim(),
                        address = address.trim(),
                        notes = notes.trim()
                    )
                )
            }
        }
    }

    fun deletePerson(personId: Long) {
        viewModelScope.launch { repository.deletePerson(personId) }
    }

    fun restorePerson(personId: Long) {
        viewModelScope.launch { repository.restorePerson(personId) }
    }

    fun permanentlyDeletePerson(personId: Long) {
        viewModelScope.launch { repository.permanentlyDeletePerson(personId) }
    }
}
