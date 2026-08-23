package com.myaccounts.app.data.repository

import com.myaccounts.app.ui.screens.RestoreConfirmationDecision
import com.myaccounts.app.ui.screens.restoreConfirmationDecision
import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreSameNameMergeFlowTest {

    @Test
    fun matching_active_name_requires_explicit_confirmation() {
        val decision = restoreConfirmationDecision(
            archivedPersonName = "  أحمد علي  ",
            activePersonNames = setOf("أحمد علي")
        )

        assertEquals(
            RestoreConfirmationDecision.REQUIRE_SAME_NAME_CONFIRMATION,
            decision
        )
    }

    @Test
    fun case_and_outer_spaces_are_normalized_before_matching() {
        val decision = restoreConfirmationDecision(
            archivedPersonName = "  Ahmed Ali  ",
            activePersonNames = setOf("ahmed ali")
        )

        assertEquals(
            RestoreConfirmationDecision.REQUIRE_SAME_NAME_CONFIRMATION,
            decision
        )
    }

    @Test
    fun no_matching_active_name_restores_directly() {
        val decision = restoreConfirmationDecision(
            archivedPersonName = "أحمد علي",
            activePersonNames = setOf("محمد علي")
        )

        assertEquals(
            RestoreConfirmationDecision.RESTORE_DIRECTLY,
            decision
        )
    }

    @Test
    fun empty_active_names_restore_directly() {
        val decision = restoreConfirmationDecision(
            archivedPersonName = "أحمد علي",
            activePersonNames = emptySet()
        )

        assertEquals(
            RestoreConfirmationDecision.RESTORE_DIRECTLY,
            decision
        )
    }
}
