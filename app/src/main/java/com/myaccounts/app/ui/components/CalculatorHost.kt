package com.myaccounts.app.ui.components

import androidx.activity.compose.BackHandler
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
            // Register this handler after the underlying screen has been
            // composed so it has priority over any BackHandler declared by
            // the current data-entry screen. While the calculator is open,
            // phone/system Back must behave exactly like calculator Close.
            BackHandler {
                controller.close()
            }

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
                modifier = Modifier.fillMaxSize().imePadding().padding(start = 12.dp, bottom = 12.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                CalculatorButton(onClick = controller::open)
            }
        }
    }
}
