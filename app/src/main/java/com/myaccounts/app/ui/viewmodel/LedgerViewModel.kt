package com.myaccounts.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myaccounts.app.data.local.AppDatabase
import com.myaccounts.app.data.local.dao.PersonWithAccounts
import com.myaccounts.app.data.repository.LedgerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LedgerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: LedgerRepository =
        LedgerRepository(
            AppDatabase.getInstance(application).ledgerDao()
        )

    val personsWithAccounts: StateFlow<List<PersonWithAccounts>> =
        repository.allPersonsFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

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

    fun getPersonWithAccounts(
        personId: Long
    ): Flow<PersonWithAccounts?> {

        return repository.getPersonWithAccounts(
            personId
        )
    }
}
