package com.myaccounts.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.myaccounts.app.security.AppSecurityManager
import com.myaccounts.app.ui.components.CalculatorController
import com.myaccounts.app.ui.components.CalculatorHost
import com.myaccounts.app.ui.components.LocalCalculatorController
import com.myaccounts.app.ui.navigation.AppNavHost
import com.myaccounts.app.ui.security.AppLockGate
import com.myaccounts.app.ui.theme.MyAccountsTheme
import com.myaccounts.app.ui.viewmodel.LedgerViewModel
import com.myaccounts.app.ui.viewmodel.LedgerViewModelFactory

class MainActivity : FragmentActivity() {

    private val viewModel: LedgerViewModel by viewModels {
        LedgerViewModelFactory(application)
    }

    private lateinit var security: AppSecurityManager
    private var unlocked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        security = AppSecurityManager(applicationContext)
        unlocked = !security.isProtectionEnabled() || security.isSessionUnlocked()

        setContent {
            MyAccountsTheme {
                if (unlocked) {
                    val navController = rememberNavController()
                    val calculatorController = remember { CalculatorController() }
                    CompositionLocalProvider(LocalCalculatorController provides calculatorController) {
                        CalculatorHost(
                            controller = calculatorController,
                            onUseResult = calculatorController::useResult
                        ) {
                            AppNavHost(
                                navController = navController,
                                viewModel = viewModel
                            )
                        }
                    }
                } else {
                    AppLockGate(
                        security = security,
                        onUnlocked = {
                            security.markSessionUnlocked()
                            unlocked = true
                        }
                    )
                }
            }
        }
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // Removing MyAccounts from the recent-tasks list is an explicit end of
        // the app task. External activities (camera, picker, viewer, etc.) do
        // not call this callback, so they keep the authenticated session alive.
        security.clearSessionUnlocked()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // Leaving the activity temporarily (camera, picker, viewer, etc.) must
        // not end the authenticated session. Only a real finish of this task
        // ends the session; configuration changes keep it alive.
        if (isFinishing) {
            security.clearSessionUnlocked()
        }
        super.onDestroy()
    }
}
