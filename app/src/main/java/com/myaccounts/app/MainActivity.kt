package com.myaccounts.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.myaccounts.app.security.AppSecurityManager
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
                        if (!security.isExternalActivityPending()) {
                            // A normal return to the app requires authentication.
                            unlocked = false
                        }
                    }
                    hasStartedOnce = true
                }

                Lifecycle.Event.ON_RESUME -> {
                    if (hasStartedOnce && security.isExternalActivityPending()) {
                        // The external activity has returned control to MyAccounts.
                        // Keep the current screen unlocked for this return, then
                        // consume the exception so the next real app return locks.
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
                    AppNavHost(
                        navController = navController,
                        viewModel = viewModel
                    )
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
