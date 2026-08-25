package com.myaccounts.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

val LocalCalculatorController = staticCompositionLocalOf<CalculatorController> {
    error("CalculatorController is not provided")
}
