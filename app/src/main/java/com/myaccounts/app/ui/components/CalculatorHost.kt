package com.myaccounts.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CalculatorHost(
    controller: CalculatorController,
    onUseResult: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        content()
        if (controller.isOpen) {
            Box(
                modifier = Modifier.fillMaxSize().imePadding(),
                contentAlignment = Alignment.BottomCenter
            ) {
                CalculatorOverlay(
                    expression = controller.expression,
                    result = controller.result.orEmpty(),
                    onKey = controller::press,
                    onClear = controller::clear,
                    onBackspace = controller::backspace,
                    onDismiss = controller::close,
                    onUseResult = onUseResult
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize().imePadding().padding(end = 12.dp, bottom = 12.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                CalculatorButton(onClick = controller::open)
            }
        }
    }
}
