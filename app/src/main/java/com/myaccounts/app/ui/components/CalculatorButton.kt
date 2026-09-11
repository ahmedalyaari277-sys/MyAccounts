package com.myaccounts.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.myaccounts.app.ui.theme.Secondary

@Composable
fun CalculatorButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Default.Calculate,
            contentDescription = "الحاسبة",
            tint = Secondary
        )
    }
}
