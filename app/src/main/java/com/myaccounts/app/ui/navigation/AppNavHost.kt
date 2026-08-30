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
import com.myaccounts.app.security.AppSecurityManager
import com.myaccounts.app.ui.screens.ArchiveScreen
import com.myaccounts.app.ui.screens.ArchivedPersonDetailScreen
import com.myaccounts.app.ui.screens.BackupRestoreScreen
import com.myaccounts.app.ui.screens.CustodyArchiveScreen
import com.myaccounts.app.ui.screens.CustodyHomeWithArchiveScreen
import com.myaccounts.app.ui.screens.CustodyOperationsScreen
import com.myaccounts.app.ui.screens.CustodyPersonOperationsScreen
import com.myaccounts.app.ui.screens.CustodyReportsScreen
import com.myaccounts.app.ui.screens.CustodyTransferScreen
import com.myaccounts.app.ui.screens.DetailsScreen
import com.myaccounts.app.ui.screens.HomeScreen
import com.myaccounts.app.ui.screens.PersonAccountScreen
import com.myaccounts.app.ui.screens.SettingsScreen
import com.myaccounts.app.ui.screens.TransactionScreen
import com.myaccounts.app.ui.screens.AppGatewayScreen
import com.myaccounts.app.ui.screens.normalizeRestorePersonName
import com.myaccounts.app.ui.screens.reports.PersonReportScreen
import com.myaccounts.app.ui.screens.reports.ReportsScreen
import com.myaccounts.app.ui.theme.ThemeMode
import com.myaccounts.app.ui.viewmodel.CustodyViewModel
import com.myaccounts.app.ui.viewmodel.CustodyViewModelFactory
import com.myaccounts.app.ui.viewmodel.LedgerViewModel
import com.myaccounts.app.ui.viewmodel.ReportsViewModel
import com.myaccounts.app.ui.viewmodel.ReportsViewModelFactory
import com.myaccounts.app.ui.viewmodel.TransactionViewModel
import com.myaccounts.app.ui.viewmodel.TransactionViewModelFactory

@Composable
fun AppNavHost(navController: NavHostController, viewModel: LedgerViewModel, themeMode: ThemeMode, onThemeModeChanged: (ThemeMode) -> Unit) {
    val persons by viewModel.personsWithAccounts.collectAsState()
    val archivedPersons by viewModel.archivedPersonsWithAccounts.collectAsState()
    val activePersonNames = persons.asSequence().map { normalizeRestorePersonName(it.person.name) }.toSet()
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val security = AppSecurityManager(context)
    val reportsViewModel: ReportsViewModel = viewModel(factory = ReportsViewModelFactory(application))
    val transactionViewModel: TransactionViewModel = viewModel(factory = TransactionViewModelFactory(application))
    val custodyViewModel: CustodyViewModel = viewModel(factory = CustodyViewModelFactory(application))
    val archivedTransactions by transactionViewModel.archivedTransactionRows.collectAsState()

    NavHost(navController = navController, startDestination = Routes.GATEWAY) {
        composable(Routes.GATEWAY) {
            AppGatewayScreen(
                onAccounts = { navController.navigate(Routes.HOME) },
                onCustodies = { navController.navigate(Routes.CUSTODIES) },
                onSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(personsList = persons, onAddPerson = { n, p, a, no -> viewModel.addPerson(n, p, a, no) }, onPersonClick = { navController.navigate(Routes.personAccount(it)) }, onQuickTransactionClick = { _, _ -> }, onQuickTransactionSave = { transaction, attachments -> transactionViewModel.addTransaction(transaction, attachments) }, onReportsClick = { navController.navigate(Routes.REPORTS) }, onArchiveClick = { navController.navigate(Routes.ARCHIVE) }, onBackupRestoreClick = { navController.navigate(Routes.BACKUP_RESTORE) }, onSettingsClick = { navController.navigate(Routes.SETTINGS) })
        }
        composable(Routes.PERSON_ACCOUNT, arguments = listOf(navArgument("personId") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("personId")
            val person = persons.firstOrNull { it.person.id == id }
            if (person != null) PersonAccountScreen(personWithAccounts = person, transactionViewModel = transactionViewModel, onBack = { navController.popBackStack() }, onUpdatePerson = { n, p, a, no -> viewModel.updatePerson(person.person.id, n, p, a, no) }, onDeletePerson = { viewModel.deletePerson(person.person.id); navController.popBackStack() }, onReportClick = { currency -> navController.navigate(Routes.personReport(person.person.id, currency)) })
        }
        composable(Routes.TRANSACTIONS, arguments = listOf(navArgument("accountId") { type = NavType.LongType }, navArgument("currencyCode") { type = NavType.StringType })) { entry ->
            val aid = entry.arguments?.getLong("accountId")
            val c = entry.arguments?.getString("currencyCode")
            if (aid != null && c != null) TransactionScreen(aid, c, { navController.popBackStack() }, transactionViewModel)
        }
        composable(Routes.REPORTS) { ReportsScreen(viewModel = reportsViewModel, onBack = { navController.popBackStack() }, onPersonClick = { id -> navController.navigate(Routes.personReport(id, "ALL")) }) }
        composable(Routes.PERSON_REPORT, arguments = listOf(navArgument("personId") { type = NavType.LongType }, navArgument("currencyCode") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getLong("personId")
            val currency = entry.arguments?.getString("currencyCode") ?: "ALL"
            if (id != null) PersonReportScreen(personId = id, currencyCode = currency, viewModel = reportsViewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.ARCHIVE) {
            ArchiveScreen(archivedPersons = archivedPersons, archivedTransactions = archivedTransactions, activePersonNames = activePersonNames, onBack = { navController.popBackStack() }, onRestore = { viewModel.restorePerson(it) }, onPermanentDelete = { viewModel.permanentlyDeletePerson(it) }, onPersonClick = { navController.navigate(Routes.archivedPerson(it)) }, onRestoreTransaction = { transactionViewModel.restoreTransaction(it) }, onPermanentDeleteTransaction = { transactionViewModel.permanentlyDeleteTransaction(it) })
        }
        composable(Routes.ARCHIVED_PERSON, arguments = listOf(navArgument("personId") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("personId")
            val person = archivedPersons.firstOrNull { it.person.id == id }
            val archivedPersonTransactions = if (id != null) transactionViewModel.observeArchivedTransactionsForPerson(id).collectAsState(initial = emptyList()) else androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(emptyList<com.myaccounts.app.data.local.TransactionEntity>()) }
            if (person != null) ArchivedPersonDetailScreen(personWithAccounts = person, archivedTransactions = archivedPersonTransactions.value, hasActiveSameName = activePersonNames.contains(normalizeRestorePersonName(person.person.name)), onBack = { navController.popBackStack() }, onRestore = { viewModel.restorePerson(person.person.id); navController.popBackStack(Routes.ARCHIVE, false) }, onRestoreTransaction = { transactionViewModel.restoreTransaction(it); navController.popBackStack(Routes.ARCHIVE, false) }, onPermanentDelete = { viewModel.permanentlyDeletePerson(person.person.id); navController.popBackStack(Routes.ARCHIVE, false) })
        }
        composable(Routes.BACKUP_RESTORE) { BackupRestoreScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SETTINGS) { SettingsScreen(security = security, themeMode = themeMode, onThemeModeChanged = onThemeModeChanged, onBack = { navController.popBackStack() }, onDetailsClick = { navController.navigate(Routes.DETAILS) }) }
        composable(Routes.DETAILS) { DetailsScreen(onBack = { navController.popBackStack() }) }

        composable(Routes.CUSTODIES) {
            CustodyHomeWithArchiveScreen(custodyViewModel, { navController.popBackStack() }, { navController.navigate(Routes.custody(it)) }, { navController.navigate(Routes.CUSTODY_ARCHIVE) }, { navController.navigate(Routes.CUSTODY_REPORTS) }, { navController.navigate(Routes.CUSTODY_TRANSFER) })
        }
        composable(Routes.CUSTODY_ARCHIVE) { CustodyArchiveScreen(custodyViewModel) { navController.popBackStack() } }
        composable(Routes.CUSTODY_REPORTS) { CustodyReportsScreen(custodyViewModel) { navController.popBackStack() } }
        composable(Routes.CUSTODY_TRANSFER) { CustodyTransferScreen(custodyViewModel) { navController.popBackStack() } }
        composable(Routes.CUSTODY, arguments = listOf(navArgument("custodyId") { type = NavType.LongType })) { entry ->
            entry.arguments?.getLong("custodyId")?.let { id -> CustodyOperationsScreen(custodyViewModel, id, { navController.popBackStack() }, { personId -> navController.navigate(Routes.custodyPerson(id, personId)) }) }
        }
        composable(Routes.CUSTODY_PERSON, arguments = listOf(navArgument("custodyId") { type = NavType.LongType }, navArgument("personId") { type = NavType.LongType })) { entry ->
            val custodyId = entry.arguments?.getLong("custodyId")
            val personId = entry.arguments?.getLong("personId")
            if (custodyId != null && personId != null) CustodyPersonOperationsScreen(custodyViewModel, custodyId, personId) { navController.popBackStack() }
        }
    }
}
