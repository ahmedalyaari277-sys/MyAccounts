package com.myaccounts.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.myaccounts.app.ui.screens.HomeScreen
import com.myaccounts.app.ui.screens.PersonAccountScreen
import com.myaccounts.app.ui.viewmodel.LedgerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: LedgerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            val personsList by
                viewModel.personsWithAccounts.collectAsState()

            var selectedPersonId by remember {
                mutableStateOf<Long?>(null)
            }

            if (selectedPersonId == null) {

                HomeScreen(

                    personsList = personsList,

                    onAddPerson = { name, phone, address ->

                        viewModel.addPerson(
                            name = name,
                            phone = phone,
                            address = address
                        )
                    },

                    onPersonClick = { personId ->

                        selectedPersonId = personId
                    }
                )

            } else {

                val selectedPerson =
                    personsList.firstOrNull {
                        it.person.id == selectedPersonId
                    }

                if (selectedPerson != null) {

                    PersonAccountScreen(

                        personWithAccounts =
                            selectedPerson,

                        onBack = {

                            selectedPersonId = null
                        }
                    )

                } else {

                    selectedPersonId = null
                }
            }
        }
    }
}
