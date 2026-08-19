package com.myaccounts.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private val mainHandler = Handler(Looper.getMainLooper())

    private val delayedRelock = Runnable {
        if (security.isProtectionEnabled() && security.isExternalActivityPending()) {
            security.clearExternalActivityPending()
            unlocked = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        security = AppSecurityManager(applicationContext)
        unlocked = !security.isProtectionEnabled()

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                mainHandler.removeCallbacks(delayedRelock)

                if (hasStartedOnce && security.isProtectionEnabled()) {
                    if (security.isExternalActivityPending()) {
                        // Android's document picker is an external activity.
                        // Give its ActivityResult callback time to clear the
                        // pending flag before deciding to lock the app again.
                        mainHandler.postDelayed(delayedRelock, EXTERNAL_ACTIVITY_RETURN_GRACE_MS)
                    } else {
                        unlocked = false
                    }
                }
                hasStartedOnce = true
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

    override fun onDestroy() {
        mainHandler.removeCallbacks(delayedRelock)
        super.onDestroy()
    }

    companion object {
        private const val EXTERNAL_ACTIVITY_RETURN_GRACE_MS = 400L
    }
}
