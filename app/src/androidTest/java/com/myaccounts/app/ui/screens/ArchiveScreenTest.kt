package com.myaccounts.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myaccounts.app.data.local.PersonEntity
import com.myaccounts.app.data.local.dao.PersonWithAccounts
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchiveScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val archivedPerson = PersonWithAccounts(
        person = PersonEntity(
            id = 1L,
            name = "أحمد علي",
            phone = "777000000",
            address = "صنعاء",
            isActive = false
        ),
        accounts = emptyList()
    )

    private fun setScreen(
        activePersonNames: Set<String>,
        onRestore: (Long) -> Unit = {}
    ) {
        composeRule.setContent {
            ArchiveScreen(
                archivedPersons = listOf(archivedPerson),
                archivedTransactions = emptyList(),
                activePersonNames = activePersonNames,
                onBack = {},
                onRestore = onRestore,
                onPermanentDelete = {},
                onPersonClick = {},
                onRestoreTransaction = {},
                onPermanentDeleteTransaction = {}
            )
        }
    }

    @Test
    fun matchingActiveName_showsConfirmation_beforeRestore() {
        var restoreCalls = 0
        setScreen(setOf("أحمد علي")) { restoreCalls++ }

        composeRule.onNodeWithText("استعادة").performClick()

        composeRule.onNodeWithText("تأكيد استعادة الحساب").assertIsDisplayed()
        composeRule.onNodeWithText("متابعة الاستعادة").assertIsDisplayed()
        composeRule.onNodeWithText("إلغاء").assertIsDisplayed()
        assertEquals(0, restoreCalls)
    }

    @Test
    fun matchingActiveName_cancel_doesNotRestore() {
        var restoreCalls = 0
        setScreen(setOf("أحمد علي")) { restoreCalls++ }

        composeRule.onNodeWithText("استعادة").performClick()
        composeRule.onNodeWithText("إلغاء").performClick()

        assertEquals(0, restoreCalls)
    }

    @Test
    fun matchingActiveName_confirm_restores() {
        var restoredId: Long? = null
        setScreen(setOf("أحمد علي")) { restoredId = it }

        composeRule.onNodeWithText("استعادة").performClick()
        composeRule.onNodeWithText("متابعة الاستعادة").performClick()

        assertEquals(1L, restoredId)
    }

    @Test
    fun noMatchingActiveName_restoresDirectly_withoutConfirmation() {
        var restoredId: Long? = null
        setScreen(setOf("محمد علي")) { restoredId = it }

        composeRule.onNodeWithText("استعادة").performClick()

        assertEquals(1L, restoredId)
    }
}
