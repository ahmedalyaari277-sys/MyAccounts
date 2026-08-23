package com.myaccounts.app.ui.screens

import java.util.Locale

internal enum class RestoreConfirmationDecision {
    RESTORE_DIRECTLY,
    REQUIRE_SAME_NAME_CONFIRMATION
}

internal fun normalizeRestorePersonName(name: String): String =
    name.trim().lowercase(Locale.ROOT)

internal fun restoreConfirmationDecision(
    archivedPersonName: String,
    activePersonNames: Set<String>
): RestoreConfirmationDecision {
    val normalizedArchivedName = normalizeRestorePersonName(archivedPersonName)
    return if (activePersonNames.contains(normalizedArchivedName)) {
        RestoreConfirmationDecision.REQUIRE_SAME_NAME_CONFIRMATION
    } else {
        RestoreConfirmationDecision.RESTORE_DIRECTLY
    }
}
