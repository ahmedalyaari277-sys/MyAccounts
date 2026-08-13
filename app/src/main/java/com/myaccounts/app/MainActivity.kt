package com.myaccounts.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.myaccounts.app.ui.screens.HomeScreen
import com.myaccounts.app.ui.viewmodel.LedgerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: LedgerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            val personsList by viewModel.personsWithAccounts.collectAsState()

            HomeScreen(
                personsList = personsList,

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
