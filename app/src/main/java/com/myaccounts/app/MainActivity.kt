package com.myaccounts.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.myaccounts.app.ui.screens.HomeScreen
import com.myaccounts.app.ui.theme.MyAccountsTheme

class MainActivity : ComponentActivity() {

    private val viewModel: LedgerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MyAccountsTheme {
                HomeScreen(
                    viewModel = viewModel
                )
            }
        }
    }
}
