package com.myaccounts.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.myaccounts.app.ui.screens.HomeScreen
import com.myaccounts.app.ui.screens.PersonAccountScreen
import com.myaccounts.app.ui.viewmodel.LedgerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: LedgerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            val navController = rememberNavController()

            val personsList by viewModel.personsWithAccounts.collectAsState()

            NavHost(
                navController = navController,
                startDestination = "home"
            ) {

                composable(
                    route = "home"
                ) {

                    HomeScreen(
                        personsList = personsList,

                        onAddPerson = { name, phone, address ->

                            viewModel.addPerson(
                                name = name,
                                phone = phone,
                                address = address
                            )
                        },

                        onPersonClick = { person ->

                            navController.navigate(
                                "person/${person.person.id}"
                            )
                        }
                    )
                }

                composable(
                    route = "person/{personId}",
                    arguments = listOf(
                        navArgument("personId") {
                            type = NavType.LongType
                        }
                    )
                ) { backStackEntry ->

                    val personId =
                        backStackEntry.arguments
                            ?.getLong("personId")

                    val selectedPerson =
                        personsList.firstOrNull {
                            it.person.id == personId
                        }

                    if (selectedPerson != null) {

                        PersonAccountScreen(

                            personWithAccounts =
                                selectedPerson,

                            onBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}
