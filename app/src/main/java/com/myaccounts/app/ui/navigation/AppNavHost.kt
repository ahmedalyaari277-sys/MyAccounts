package com.myaccounts.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.myaccounts.app.ui.screens.HomeScreen
import com.myaccounts.app.ui.screens.PersonAccountScreen
import com.myaccounts.app.ui.viewmodel.LedgerViewModel

@Composable
fun AppNavHost(
    navController: NavHostController,
    viewModel: LedgerViewModel
) {

    val persons by viewModel
        .personsWithAccounts
        .collectAsState()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {

        composable(Routes.HOME) {

            HomeScreen(
                personsList = persons,

                onAddPerson = {
                        name,
                        phone,
                        address,
                        notes ->

                    viewModel.addPerson(
                        name = name,
                        phone = phone,
                        address = address,
                        notes = notes
                    )
                },

                onPersonClick = { personId ->

                    navController.navigate(
                        Routes.personAccount(personId)
                    )
                }
            )
        }

        composable(
            route = Routes.PERSON_ACCOUNT,

            arguments = listOf(
                navArgument("personId") {
                    type = NavType.LongType
                }
            )
        ) { entry ->

            val personId =
                entry.arguments?.getLong("personId")

            val person =
                persons.firstOrNull {
                    it.person.id == personId
                }

            if (person != null) {

                PersonAccountScreen(

                    personWithAccounts = person,

                    onBack = {
                        navController.popBackStack()
                    },

                    onUpdatePerson = {
                            name,
                            phone,
                            address,
                            notes ->

                        viewModel.updatePerson(
                            personId = person.person.id,
                            name = name,
                            phone = phone,
                            address = address,
                            notes = notes
                        )
                    },

                    onDeletePerson = {

                        viewModel.deletePerson(
                            person.person.id
                        )

                        navController.popBackStack()
                    },

                    onAccountClick = { accountId ->

                        // سيتم ربط TransactionViewModel
                        // بالحساب المحدد في الخطوة التالية.

                    }
                )
            }
        }
    }
}
