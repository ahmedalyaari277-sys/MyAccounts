package com.myaccounts.app.ui.navigation

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.*
import androidx.navigation.compose.*
import com.myaccounts.app.security.AppSecurityManager
import com.myaccounts.app.ui.screens.*
import com.myaccounts.app.ui.screens.reports.*
import com.myaccounts.app.ui.theme.AppearanceMode
import com.myaccounts.app.ui.viewmodel.*

@Composable fun AppNavHost(navController:NavHostController,viewModel:LedgerViewModel,appearanceMode:AppearanceMode,onAppearanceModeChange:(AppearanceMode)->Unit){
 val persons by viewModel.personsWithAccounts.collectAsState();val archived by viewModel.archivedPersonsWithAccounts.collectAsState();val restore by viewModel.restorePersonResult.collectAsState();val context=LocalContext.current;val app=context.applicationContext as Application;val security=AppSecurityManager(context);val reports:ReportsViewModel=viewModel(factory=ReportsViewModelFactory(app));val transactions:TransactionViewModel=viewModel(factory=TransactionViewModelFactory(app));val custody:CustodyViewModel=viewModel(factory=CustodyViewModelFactory(app))
 NavHost(navController,startDestination=Routes.GATEWAY){
  composable(Routes.GATEWAY){AppGatewayScreen({navController.navigate(Routes.HOME)},{navController.navigate(Routes.CUSTODIES)},{navController.navigate(Routes.SETTINGS)})}
  composable(Routes.HOME){HomeScreen(personsList=persons,onAddPerson={n,p,a,no->viewModel.addPerson(n,p,a,no)},onPersonClick={navController.navigate(Routes.personAccount(it))},onQuickTransactionSave={t,a->transactions.addTransaction(t,a)},onReportsClick={navController.navigate(Routes.REPORTS)},onArchiveClick={navController.navigate(Routes.ARCHIVE)},onBackupRestoreClick={navController.navigate(Routes.BACKUP_RESTORE)})}
  composable(Routes.PERSON_ACCOUNT,arguments=listOf(navArgument("personId"){type=NavType.LongType})){e->val id=e.arguments?.getLong("personId");persons.firstOrNull{it.person.id==id}?.let{p->PersonAccountScreen(p,{navController.popBackStack()},{n,ph,a,no->viewModel.updatePerson(id!!,n,ph,a,no)},{viewModel.deletePerson(id!!);navController.popBackStack()},{cur->navController.navigate(Routes.personReport(id!!,cur))},transactions)}}
  composable(Routes.TRANSACTIONS,arguments=listOf(navArgument("accountId"){type=NavType.LongType},navArgument("currencyCode"){type=NavType.StringType})){e->val id=e.arguments?.getLong("accountId");val c=e.arguments?.getString("currencyCode");if(id!=null&&c!=null)TransactionScreen(id,c,{navController.popBackStack()},transactions)}
  composable(Routes.REPORTS){ReportsScreen(reports,{navController.popBackStack()},{id->navController.navigate(Routes.personReport(id,"ALL"))})}
  composable(Routes.PERSON_REPORT,arguments=listOf(navArgument("personId"){type=NavType.LongType},navArgument("currencyCode"){type=NavType.StringType})){e->val id=e.arguments?.getLong("personId");if(id!=null)PersonReportScreen(id,e.arguments?.getString("currencyCode")?:"ALL",reports,{navController.popBackStack()})}
  composable(Routes.ARCHIVE){ArchiveScreen(archived,{navController.popBackStack()},{navController.navigate(Routes.archivedPerson(it))},{viewModel.restorePerson(it)},{viewModel.permanentlyDeletePerson(it)},{viewModel.clearArchive()},restore,{viewModel.clearRestorePersonResult()})}
  composable(Routes.ARCHIVED_PERSON,arguments=listOf(navArgument("personId"){type=NavType.LongType})){e->val id=e.arguments?.getLong("personId");archived.firstOrNull{it.person.id==id}?.let{p->ArchivedPersonDetailScreen(p,{navController.popBackStack()},{viewModel.restorePerson(id!!);navController.popBackStack(Routes.ARCHIVE,false)},{viewModel.permanentlyDeletePerson(id!!);navController.popBackStack(Routes.ARCHIVE,false)})}}
  composable(Routes.BACKUP_RESTORE){BackupRestoreScreen{navController.popBackStack()}}
  composable(Routes.SETTINGS){SettingsScreen(security,appearanceMode,onAppearanceModeChange,{navController.popBackStack()},{navController.navigate(Routes.DETAILS)})}
  composable(Routes.DETAILS){DetailsScreen{navController.popBackStack()}}
  composable(Routes.CUSTODIES){CustodyHomeScreen(custody,{navController.popBackStack()},{navController.navigate(Routes.custody(it))})}
  composable(Routes.CUSTODY,arguments=listOf(navArgument("custodyId"){type=NavType.LongType})){e->e.arguments?.getLong("custodyId")?.let{id->CustodyDetailScreen(custody,id,{navController.popBackStack()})}}
 }
}
