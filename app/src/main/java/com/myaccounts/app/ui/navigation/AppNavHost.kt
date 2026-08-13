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
    val persons by viewModel.personsWithAccounts.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {

        composable(
            route = Routes.HOME
        ) {
            HomeScreen(
                personsList = persons,

                onAddPerson = { name, phone, address ->
                    viewModel.addPerson(
                        name = name,
                        phone = phone,
                        address = address
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
        ) { backStackEntry ->

            val personId =
                backStackEntry.arguments?.getLong("personId")

            val personWithAccounts =
                persons.firstOrNull {
                    it.person.id == personId
                }

            if (personWithAccounts != null) {

                PersonAccountScreen(
                    personWithAccounts = personWithAccounts,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
