package com.myaccounts.app.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll as foundationHorizontalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester as foundationFocusRequester

/**
 * Explicit compatibility wrappers for custody-screen modifier extensions.
 */
fun Modifier.horizontalScroll(state: ScrollState): Modifier = foundationHorizontalScroll(state)

fun Modifier.focusRequester(requester: FocusRequester): Modifier = foundationFocusRequester(requester)
