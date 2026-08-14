package com.myaccounts.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.navigation.compose.rememberNavController
import com.myaccounts.app.ui.navigation.AppNavHost
import com.myaccounts.app.ui.theme.MyAccountsTheme
import com.myaccounts.app.ui.viewmodel.LedgerViewModel
import com.myaccounts.app.ui.viewmodel.LedgerViewModelFactory
import com.myaccounts.app.ui.viewmodel.ReportsViewModel
import com.myaccounts.app.ui.viewmodel.ReportsViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: LedgerViewModel by viewModels {
        LedgerViewModelFactory(application)
    }

    private val reportsViewModel: ReportsViewModel by viewModels {
        ReportsViewModelFactory(application)
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContent {

            MyAccountsTheme {

                val navController =
                    rememberNavController()

                AppNavHost(
                    navController = navController,
                    viewModel = viewModel
                )
            }
        }
    }
}
