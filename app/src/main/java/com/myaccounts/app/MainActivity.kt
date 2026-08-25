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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    private var hasStartedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        security = AppSecurityManager(applicationContext)
        unlocked = !security.isProtectionEnabled()

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (hasStartedOnce && security.isProtectionEnabled()) {
                        if (!security.isExternalActivityPending()) unlocked = false
                    }
                    hasStartedOnce = true
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (hasStartedOnce && security.isExternalActivityPending()) {
                        security.clearExternalActivityPending()
                    }
                }
                else -> Unit
            }
        })

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
                        onUnlocked = { unlocked = true }
                    )
                }
            }
        }
    }
}
