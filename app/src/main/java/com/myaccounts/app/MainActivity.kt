package com.myaccounts.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.myaccounts.app.data.currency.CurrencyCatalog
import com.myaccounts.app.security.AppSecurityManager
import com.myaccounts.app.ui.components.CalculatorController
import com.myaccounts.app.ui.components.CalculatorHost
import com.myaccounts.app.ui.components.LocalCalculatorController
import com.myaccounts.app.ui.navigation.AppNavHost
import com.myaccounts.app.ui.security.AppLockGate
import com.myaccounts.app.ui.theme.AppearanceMode
import com.myaccounts.app.ui.theme.MyAccountsTheme
import com.myaccounts.app.ui.theme.ThemePreferences
import com.myaccounts.app.ui.viewmodel.LedgerViewModel
import com.myaccounts.app.ui.viewmodel.LedgerViewModelFactory

class MainActivity : FragmentActivity() {

    private val viewModel: LedgerViewModel by viewModels {
        LedgerViewModelFactory(application)
    }

    private lateinit var security: AppSecurityManager
    private lateinit var themePreferences: ThemePreferences
    private var unlocked by mutableStateOf(false)
    private var appearanceMode by mutableStateOf(AppearanceMode.SYSTEM)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        security = AppSecurityManager(applicationContext)
        themePreferences = ThemePreferences(applicationContext)
        CurrencyCatalog.initialize(applicationContext)
        appearanceMode = themePreferences.getAppearance()
        unlocked = !security.isProtectionEnabled() || security.isSessionUnlocked()

        setContent {
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (appearanceMode) {
                AppearanceMode.LIGHT -> false
                AppearanceMode.DARK -> true
                AppearanceMode.SYSTEM -> systemDark
            }

            MyAccountsTheme(darkTheme = darkTheme) {
                if (unlocked) {
                    val navController = rememberNavController()
                    val calculatorController = androidx.compose.runtime.remember { CalculatorController() }
                    CompositionLocalProvider(LocalCalculatorController provides calculatorController) {
                        CalculatorHost(
                            controller = calculatorController,
                            onUseResult = calculatorController::useResult
                        ) {
                            AppNavHost(
                                navController = navController,
                                viewModel = viewModel,
                                appearanceMode = appearanceMode,
                                onAppearanceModeChange = { mode ->
                                    themePreferences.setAppearance(mode)
                                    appearanceMode = mode
                                }
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

    override fun onDestroy() {
        if (isFinishing) {
            security.clearSessionUnlocked()
        }
        super.onDestroy()
    }
}
