package com.myaccounts.app.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll as foundationHorizontalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester as foundationFocusRequester

/**
 * Local aliases for Compose modifiers used by the custody screens.
 * They keep the existing screen source stable while resolving the modifier
 * extensions from the Compose packages explicitly.
 */
fun Modifier.horizontalScroll(state: ScrollState): Modifier = foundationHorizontalScroll(this, state)

fun Modifier.focusRequester(requester: FocusRequester): Modifier = foundationFocusRequester(requester)
