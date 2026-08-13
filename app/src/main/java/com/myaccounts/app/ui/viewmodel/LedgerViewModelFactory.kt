package com.myaccounts.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.myaccounts.app.data.local.AppDatabase
import com.myaccounts.app.data.repository.LedgerRepository

class LedgerViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        if (modelClass.isAssignableFrom(LedgerViewModel::class.java)) {

            val database = AppDatabase.getInstance(application)

            val repository = LedgerRepository(
                database.ledgerDao()
            )

            return LedgerViewModel(
                repository = repository
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}
