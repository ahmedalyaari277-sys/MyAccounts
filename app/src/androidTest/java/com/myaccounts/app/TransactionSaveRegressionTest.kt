package com.myaccounts.app

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.myaccounts.app.data.local.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionSaveRegressionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val device: UiDevice get() = UiDevice.getInstance(instrumentation)
    private val database get() = AppDatabase.getInstance(context)
    private val personId = 960001L
    private val accountId = 960002L
    private val personExternalId = "P-M01-SAVE-001"

    @Before
    fun setUp() {
        clearData()
        val db = database.openHelper.writableDatabase
        db.execSQL(
            "INSERT INTO people (id,name,phone,address,notes,createdAt,isActive,archivedAt,externalId) VALUES (?,?,?,?,?,?,?,?,?)",
            arrayOf(personId, "اختبار حفظ العملية", "777000901", "صنعاء", "M01 save", 3000L, 1, null, personExternalId)
        )
        db.execSQL(
            "INSERT INTO currency_accounts (id,personId,currencyCode,balanceMinor,createdAt,updatedAt) VALUES (?,?,?,?,?,?)",
            arrayOf(accountId, personId, "YER", 0L, 3001L, 3002L)
        )
        instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        device.waitForIdle()
        assertTrue("MyAccounts did not become visible", device.wait(Until.hasObject(By.pkg(context.packageName)), 15_000))
    }

    @After
    fun tearDown() = clearData()

    @Test
    fun quickTransactionSavePersistsTransactionAndBalanceWithoutCrash() {
        click(By.desc("إضافة عملية سريعة"), "Quick transaction button")
        waitForText("إضافة عملية")

        val fields = waitForEditTexts(3)
        fields[0].clear()
        fields[0].text = "100"
        fields[2].clear()
        fields[2].text = "عملية حفظ سريعة"
        dismissKeyboard()
        clickVisibleText("حفظ", "Quick transaction save")

        assertTrue("Quick transaction dialog did not close", device.wait(Until.gone(By.text("إضافة عملية")), 10_000))
        assertTransactionPersisted("عملية حفظ سريعة", 10000L)
    }

    @Test
    fun embeddedTransactionSavePersistsTransactionAndBalanceWithoutCrash() {
        click(By.text("اختبار حفظ العملية"), "Test person")
        waitForText("إضافة عملية")
        click(By.text("إضافة عملية"), "Embedded add transaction button")

        val fields = waitForEditTexts(2)
        fields[0].clear()
        fields[0].text = "50"
        fields[1].clear()
        fields[1].text = "عملية حفظ عادية"
        clickVisibleText("حفظ", "Embedded transaction save")

        assertTrue("Transaction dialog did not close", device.wait(Until.gone(By.text("التفاصيل")), 10_000))
        assertTransactionPersisted("عملية حفظ عادية", 5000L)
    }

    private fun assertTransactionPersisted(description: String, expectedBalance: Long) {
        val db = database.openHelper.writableDatabase
        db.query("SELECT accountId, amountMinor, description FROM transactions WHERE accountId=? ORDER BY id DESC LIMIT 1", arrayOf(accountId.toString())).use { c ->
            assertTrue("Saved transaction was not found", c.moveToFirst())
            assertEquals(accountId, c.getLong(0))
            assertEquals(expectedBalance, c.getLong(1))
            assertEquals(description, c.getString(2))
        }
        db.query("SELECT balanceMinor FROM currency_accounts WHERE id=?", arrayOf(accountId.toString())).use { c ->
            assertTrue("Currency account was not found", c.moveToFirst())
            assertEquals(expectedBalance, c.getLong(0))
        }
    }

    private fun waitForEditTexts(minimum: Int): List<UiObject2> {
        val deadline = System.currentTimeMillis() + 7_000L
        while (System.currentTimeMillis() < deadline) {
            val objects = device.findObjects(By.clazz("android.widget.EditText"))
            if (objects.size >= minimum) return objects
            device.waitForIdle()
            Thread.sleep(100)
        }
        val count = device.findObjects(By.clazz("android.widget.EditText")).size
        error("Expected at least $minimum text fields, found $count")
    }

    private fun click(selector: BySelector, description: String) {
        val object2 = device.wait(Until.findObject(selector), 10_000) ?: error("$description was not found")
        object2.click()
        device.waitForIdle()
    }

    private fun dismissKeyboard() {
        device.pressBack()
        device.waitForIdle()
    }

    private fun clickVisibleText(text: String, description: String) {
        val deadline = System.currentTimeMillis() + 12_000L
        var keyboardDismissed = false
        while (System.currentTimeMillis() < deadline) {
            val object2 = device.findObject(By.text(text))
            if (object2 != null) {
                object2.click()
                device.waitForIdle()
                return
            }
            if (!keyboardDismissed) {
                keyboardDismissed = true
                device.pressBack()
                device.waitForIdle()
                continue
            }
            device.swipe(
                device.displayWidth / 2,
                device.displayHeight * 3 / 4,
                device.displayWidth / 2,
                device.displayHeight / 3,
                350
            )
            device.waitForIdle()
            Thread.sleep(150)
        }
        error("$description was not found as a UI element")
    }

    private fun waitForText(text: String) {
        assertTrue("Text '$text' was not found", device.wait(Until.hasObject(By.text(text)), 10_000))
    }

    private fun clearData() {
        val db = database.openHelper.writableDatabase
        db.execSQL("DELETE FROM transaction_attachments")
        db.execSQL("DELETE FROM transactions WHERE accountId=?", arrayOf(accountId))
        db.execSQL("DELETE FROM currency_accounts WHERE id=?", arrayOf(accountId))
        db.execSQL("DELETE FROM people WHERE id=?", arrayOf(personId))
    }
}
