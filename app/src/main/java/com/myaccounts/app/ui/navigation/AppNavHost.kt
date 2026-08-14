package com.myaccounts.app.ui.navigation

import android.app.Application

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext

import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

import com.myaccounts.app.ui.screens.HomeScreen
import com.myaccounts.app.ui.screens.PersonAccountScreen
import com.myaccounts.app.ui.screens.TransactionScreen
import com.myaccounts.app.ui.screens.reports.PersonReportScreen
import com.myaccounts.app.ui.screens.reports.ReportsScreen

import com.myaccounts.app.ui.viewmodel.LedgerViewModel
import com.myaccounts.app.ui.viewmodel.ReportsViewModel
import com.myaccounts.app.ui.viewmodel.ReportsViewModelFactory
import com.myaccounts.app.ui.viewmodel.TransactionViewModel
import com.myaccounts.app.ui.viewmodel.TransactionViewModelFactory

@Composable
fun AppNavHost(
    navController: NavHostController,
    viewModel: LedgerViewModel
) {

    val persons by viewModel
        .personsWithAccounts
        .collectAsState()

    val application =
        LocalContext.current
            .applicationContext as Application

    val reportsViewModel: ReportsViewModel =
        viewModel(
            factory = ReportsViewModelFactory(
                application
            )
        )

    val transactionViewModel: TransactionViewModel =
        viewModel(
            factory = TransactionViewModelFactory(
                application
            )
        )

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {

        // ---------------------------------------------------------
        // HOME
        // ---------------------------------------------------------

        composable(
            route = Routes.HOME
        ) {

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
                        Routes.personAccount(
                            personId
                        )
                    )
                }
            )
        }

        // ---------------------------------------------------------
        // PERSON ACCOUNT
        // ---------------------------------------------------------

        composable(
            route = Routes.PERSON_ACCOUNT,

            arguments = listOf(
                navArgument("personId") {
                    type = NavType.LongType
                }
            )
        ) { entry ->

            val personId =
                entry.arguments?.getLong(
                    "personId"
                )

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

                        val account =
                            personWithAccountsAccount(
                                person.accounts,
                                accountId
                            )

                        if (account != null) {

                            navController.navigate(
                                Routes.transactions(
                                    accountId = account.id,
                                    currencyCode =
                                        account.currencyCode
                                )
                            )
                        }
                    }
                )
            }
        }

        // ---------------------------------------------------------
        // TRANSACTIONS
        // ---------------------------------------------------------

        composable(
            route = Routes.TRANSACTIONS,

            arguments = listOf(

                navArgument("accountId") {
                    type = NavType.LongType
                },

                navArgument("currencyCode") {
                    type = NavType.StringType
                }
            )
        ) { entry ->

            val accountId =
                entry.arguments?.getLong(
                    "accountId"
                )

            val currencyCode =
                entry.arguments?.getString(
                    "currencyCode"
                )

            if (
                accountId != null &&
                currencyCode != null
            ) {

                TransactionScreen(

                    accountId = accountId,

                    currencyCode =
                        currencyCode,

                    onBack = {
                        navController.popBackStack()
                    },

                    transactionViewModel =
                        transactionViewModel
                )
            }
        }

        // ---------------------------------------------------------
        // REPORTS
        // ---------------------------------------------------------

        composable(
            route = Routes.REPORTS
        ) {

            ReportsScreen(

                viewModel = reportsViewModel,

                onBack = {
                    navController.popBackStack()
                },

                onPersonClick = {
                        personId,
                        currencyCode ->

                    navController.navigate(
                        Routes.personReport(
                            personId = personId,
                            currencyCode = currencyCode
                        )
                    )
                }
            )
        }

        // ---------------------------------------------------------
        // PERSON REPORT
        // ---------------------------------------------------------

        composable(
            route = Routes.PERSON_REPORT,

            arguments = listOf(

                navArgument("personId") {
                    type = NavType.LongType
                },

                navArgument("currencyCode") {
                    type = NavType.StringType
                }
            )
        ) { entry ->

            val personId =
                entry.arguments?.getLong(
                    "personId"
                )

            val currencyCode =
                entry.arguments?.getString(
                    "currencyCode"
                )

            if (
                personId != null &&
                currencyCode != null
            ) {

                PersonReportScreen(

                    personId = personId,

                    currencyCode =
                        currencyCode,

                    viewModel =
                        reportsViewModel,

                    transactionViewModel =
                        transactionViewModel,

                    onTransactionClick = {
                            accountId,
                            selectedCurrencyCode ->

                        navController.navigate(
                            Routes.transactions(
                                accountId = accountId,
                                currencyCode =
                                    selectedCurrencyCode
                            )
                        )
                    },

                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}

// -------------------------------------------------------------
// Find the selected currency account
// -------------------------------------------------------------

private fun personWithAccountsAccount(
    accounts:
        List<com.myaccounts.app.data.local.CurrencyAccountEntity>,
    accountId: Long
): com.myaccounts.app.data.local.CurrencyAccountEntity? {

    return accounts.firstOrNull {
        it.id == accountId
    }
}
