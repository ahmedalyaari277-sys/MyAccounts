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
import com.myaccounts.app.ui.screens.ArchiveScreen
import com.myaccounts.app.ui.screens.ArchivedPersonDetailScreen
import com.myaccounts.app.ui.screens.BackupRestoreScreen
import com.myaccounts.app.ui.screens.HomeScreen
import com.myaccounts.app.ui.screens.PersonAccountScreen
import com.myaccounts.app.ui.screens.QuickTransactionScreen
import com.myaccounts.app.ui.screens.TransactionScreen
import com.myaccounts.app.ui.screens.reports.PersonReportScreen
import com.myaccounts.app.ui.screens.reports.ReportsScreen
import com.myaccounts.app.ui.viewmodel.LedgerViewModel
import com.myaccounts.app.ui.viewmodel.ReportsViewModel
import com.myaccounts.app.ui.viewmodel.ReportsViewModelFactory
import com.myaccounts.app.ui.viewmodel.TransactionViewModel
import com.myaccounts.app.ui.viewmodel.TransactionViewModelFactory

@Composable
fun AppNavHost(navController: NavHostController, viewModel: LedgerViewModel) {
    val persons by viewModel.personsWithAccounts.collectAsState()
    val archivedPersons by viewModel.archivedPersonsWithAccounts.collectAsState()
    val application = LocalContext.current.applicationContext as Application
    val reportsViewModel: ReportsViewModel = viewModel(factory = ReportsViewModelFactory(application))
    val transactionViewModel: TransactionViewModel = viewModel(factory = TransactionViewModelFactory(application))

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                personsList = persons,
                onAddPerson = { name, phone, address, notes -> viewModel.addPerson(name, phone, address, notes) },
                onPersonClick = { navController.navigate(Routes.personAccount(it)) },
                onQuickTransactionClick = { personId, _ ->
                    navController.navigate(Routes.quickTransaction(personId))
                },
                onReportsClick = { navController.navigate(Routes.REPORTS) },
                onArchiveClick = { navController.navigate(Routes.ARCHIVE) },
                onBackupRestoreClick = { navController.navigate(Routes.BACKUP_RESTORE) }
            )
        }

        composable(
            Routes.QUICK_TRANSACTION,
            arguments = listOf(navArgument("personId") { type = NavType.LongType })
        ) { entry ->
            val personId = entry.arguments?.getLong("personId")
            val person = persons.firstOrNull { it.person.id == personId }
            if (person != null) {
                QuickTransactionScreen(
                    personName = person.person.name,
                    accounts = person.accounts,
                    onSave = { transaction ->
                        transactionViewModel.addTransaction(transaction)
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() }
                )
            }
        }

        composable(Routes.PERSON_ACCOUNT, arguments = listOf(navArgument("personId") { type = NavType.LongType })) { entry ->
            val personId = entry.arguments?.getLong("personId")
            val person = persons.firstOrNull { it.person.id == personId }
            if (person != null) {
                PersonAccountScreen(
                    personWithAccounts = person,
                    onBack = { navController.popBackStack() },
                    onUpdatePerson = { name, phone, address, notes -> viewModel.updatePerson(person.person.id, name, phone, address, notes) },
                    onDeletePerson = { viewModel.deletePerson(person.person.id); navController.popBackStack() },
                    onAccountClick = { accountId ->
                        person.accounts.firstOrNull { it.id == accountId }?.let {
                            navController.navigate(Routes.transactions(it.id, it.currencyCode))
                        }
                    }
                )
            }
        }

        composable(
            Routes.TRANSACTIONS,
            arguments = listOf(
                navArgument("accountId") { type = NavType.LongType },
                navArgument("currencyCode") { type = NavType.StringType }
            )
        ) { entry ->
            val accountId = entry.arguments?.getLong("accountId")
            val currencyCode = entry.arguments?.getString("currencyCode")
            if (accountId != null && currencyCode != null) {
                TransactionScreen(accountId, currencyCode, { navController.popBackStack() }, transactionViewModel)
            }
        }

        composable(Routes.REPORTS) {
            ReportsScreen(
                viewModel = reportsViewModel,
                onBack = { navController.popBackStack() },
                onPersonClick = { personId, currencyCode -> navController.navigate(Routes.personReport(personId, currencyCode)) }
            )
        }

        composable(
            Routes.PERSON_REPORT,
            arguments = listOf(
                navArgument("personId") { type = NavType.LongType },
                navArgument("currencyCode") { type = NavType.StringType }
            )
        ) { entry ->
            val personId = entry.arguments?.getLong("personId")
            val currencyCode = entry.arguments?.getString("currencyCode")
            if (personId != null && currencyCode != null) {
                PersonReportScreen(
                    personId = personId,
                    currencyCode = currencyCode,
                    viewModel = reportsViewModel,
                    transactionViewModel = transactionViewModel,
                    onTransactionClick = { accountId, selectedCurrencyCode -> navController.navigate(Routes.transactions(accountId, selectedCurrencyCode)) },
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(Routes.ARCHIVE) {
            ArchiveScreen(
                archivedPersons = archivedPersons,
                onBack = { navController.popBackStack() },
                onRestore = { viewModel.restorePerson(it) },
                onPermanentDelete = { viewModel.permanentlyDeletePerson(it) },
                onPersonClick = { navController.navigate(Routes.archivedPerson(it)) }
            )
        }

        composable(
            Routes.ARCHIVED_PERSON,
            arguments = listOf(navArgument("personId") { type = NavType.LongType })
        ) { entry ->
            val personId = entry.arguments?.getLong("personId")
            val person = archivedPersons.firstOrNull { it.person.id == personId }
            if (person != null) {
                ArchivedPersonDetailScreen(
                    personWithAccounts = person,
                    onBack = { navController.popBackStack() },
                    onRestore = {
                        viewModel.restorePerson(person.person.id)
                        navController.popBackStack(Routes.ARCHIVE, inclusive = false)
                    },
                    onPermanentDelete = {
                        viewModel.permanentlyDeletePerson(person.person.id)
                        navController.popBackStack(Routes.ARCHIVE, inclusive = false)
                    }
                )
            }
        }

        composable(Routes.BACKUP_RESTORE) {
            BackupRestoreScreen(onBack = { navController.popBackStack() })
        }
    }
}
