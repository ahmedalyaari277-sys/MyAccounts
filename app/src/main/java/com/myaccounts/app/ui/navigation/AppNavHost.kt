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
import com.myaccounts.app.ui.screens.*
import com.myaccounts.app.ui.screens.reports.*
import com.myaccounts.app.ui.theme.AppearanceMode
import com.myaccounts.app.ui.viewmodel.*

@Composable
fun AppNavHost(navController: NavHostController, viewModel: LedgerViewModel, appearanceMode: AppearanceMode, onAppearanceModeChange: (AppearanceMode) -> Unit) {
    val persons by viewModel.personsWithAccounts.collectAsState()
    val archived by viewModel.archivedPersonsWithAccounts.collectAsState()
    val restore by viewModel.restorePersonResult.collectAsState()
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val security = AppSecurityManager(context)
    val reports: ReportsViewModel = viewModel(factory = ReportsViewModelFactory(app))
    val transactions: TransactionViewModel = viewModel(factory = TransactionViewModelFactory(app))
    val custody: CustodyViewModel = viewModel(factory = CustodyViewModelFactory(app))

    NavHost(navController = navController, startDestination = Routes.GATEWAY) {
        composable(Routes.GATEWAY) { AppGatewayScreen(onAccounts = { navController.navigate(Routes.HOME) }, onCustodies = { navController.navigate(Routes.CUSTODIES) }, onSettings = { navController.navigate(Routes.SETTINGS) }) }
        composable(Routes.HOME) { HomeScreen(personsList = persons, onAddPerson = { n, p, a, no -> viewModel.addPerson(n, p, a, no) }, onPersonClick = { navController.navigate(Routes.personAccount(it)) }, onQuickTransactionSave = { t, a -> transactions.addTransaction(t, a) }, onReportsClick = { navController.navigate(Routes.REPORTS) }, onArchiveClick = { navController.navigate(Routes.ARCHIVE) }, onBackupRestoreClick = { navController.navigate(Routes.BACKUP_RESTORE) }, onSettingsClick = { navController.navigate(Routes.SETTINGS) }) }
        composable(Routes.PERSON_ACCOUNT, arguments = listOf(navArgument("personId") { type = NavType.LongType })) { e -> val id = e.arguments?.getLong("personId"); persons.firstOrNull { it.person.id == id }?.let { p -> PersonAccountScreen(p, { navController.popBackStack() }, { n, ph, a, no -> viewModel.updatePerson(id!!, n, ph, a, no) }, { viewModel.deletePerson(id!!); navController.popBackStack() }, { cur -> navController.navigate(Routes.personReport(id!!, cur)) }, transactions) } }
        composable(Routes.TRANSACTIONS, arguments = listOf(navArgument("accountId") { type = NavType.LongType }, navArgument("currencyCode") { type = NavType.StringType })) { e -> val id = e.arguments?.getLong("accountId"); val c = e.arguments?.getString("currencyCode"); if (id != null && c != null) TransactionScreen(id, c, { navController.popBackStack() }, transactions) }
        composable(Routes.REPORTS) { ReportsScreen(reports, { navController.popBackStack() }, { id -> navController.navigate(Routes.personReport(id, "ALL")) }) }
        composable(Routes.PERSON_REPORT, arguments = listOf(navArgument("personId") { type = NavType.LongType }, navArgument("currencyCode") { type = NavType.StringType })) { e -> val id = e.arguments?.getLong("personId"); if (id != null) PersonReportScreen(id, e.arguments?.getString("currencyCode") ?: "ALL", reports, { navController.popBackStack() }) }
        composable(Routes.ARCHIVE) { ArchiveScreen(archived, { navController.popBackStack() }, { navController.navigate(Routes.archivedPerson(it)) }, { viewModel.restorePerson(it) }, { viewModel.permanentlyDeletePerson(it) }, { viewModel.clearArchive() }, restore, { viewModel.clearRestorePersonResult() }) }
        composable(Routes.ARCHIVED_PERSON, arguments = listOf(navArgument("personId") { type = NavType.LongType })) { e -> val id = e.arguments?.getLong("personId"); archived.firstOrNull { it.person.id == id }?.let { p -> ArchivedPersonDetailScreen(p, { navController.popBackStack() }, { viewModel.restorePerson(id!!); navController.popBackStack(Routes.ARCHIVE, false) }, { viewModel.permanentlyDeletePerson(id!!); navController.popBackStack(Routes.ARCHIVE, false) }) } }
        composable(Routes.BACKUP_RESTORE) { BackupRestoreScreen { navController.popBackStack() } }
        composable(Routes.SETTINGS) { SettingsScreen(security, appearanceMode, onAppearanceModeChange, { navController.popBackStack() }, { navController.navigate(Routes.DETAILS) }) }
        composable(Routes.DETAILS) { DetailsScreen { navController.popBackStack() } }
        composable(Routes.CUSTODIES) {
            CustodyHomeWithArchiveScreen(
                custody,
                { navController.popBackStack() },
                { navController.navigate(Routes.custody(it)) },
                { navController.navigate(Routes.CUSTODY_ARCHIVE) },
                { navController.navigate(Routes.CUSTODY_REPORTS) },
                { navController.navigate(Routes.CUSTODY_TRANSFER) }
            )
        }
        composable(Routes.CUSTODY_ARCHIVE) { CustodyArchiveScreen(custody) { navController.popBackStack() } }
        composable(Routes.CUSTODY_REPORTS) { CustodyReportsScreen(custody) { navController.popBackStack() } }
        composable(Routes.CUSTODY_TRANSFER) { CustodyTransferScreen(custody) { navController.popBackStack() } }
        composable(Routes.CUSTODY, arguments = listOf(navArgument("custodyId") { type = NavType.LongType })) { e ->
            e.arguments?.getLong("custodyId")?.let { id ->
                CustodyOperationsScreen(custody, id, { navController.popBackStack() }, { personId -> navController.navigate(Routes.custodyPerson(id, personId)) }, { navController.navigate(Routes.custodyOwner(id)) })
            }
        }
        composable(Routes.CUSTODY_OWNER, arguments = listOf(navArgument("custodyId") { type = NavType.LongType })) { e -> e.arguments?.getLong("custodyId")?.let { id -> CustodyLedgerScreen(custody, id, null, { navController.popBackStack() }) } }
        composable(Routes.CUSTODY_PERSON, arguments = listOf(navArgument("custodyId") { type = NavType.LongType }, navArgument("personId") { type = NavType.LongType })) { e -> val cid = e.arguments?.getLong("custodyId"); val pid = e.arguments?.getLong("personId"); if (cid != null && pid != null) CustodyPersonOperationsScreen(custody, cid, pid, { navController.popBackStack() }) }
    }
}
