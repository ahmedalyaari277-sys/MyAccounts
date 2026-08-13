package com.myaccounts.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.myaccounts.app.ui.screens.HomeScreen
import com.myaccounts.app.ui.viewmodel.LedgerViewModel

class MainActivity : FragmentActivity() {

    private val viewModel: LedgerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val persons by viewModel.personsWithAccounts.collectAsState()

            HomeScreen(
                personsList = persons,
                onAddPerson = { name, phone, address ->
                    viewModel.addPerson(
                        name = name,
                        phone = phone,
                        address = address
                    )
                }
            )
        }
    }
}
