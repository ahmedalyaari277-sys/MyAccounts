package com.myaccounts.app.custody

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myaccounts.app.data.custody.CustodyEntity
import com.myaccounts.app.data.custody.CustodyRepository
import com.myaccounts.app.data.custody.CustodyTransactionType
import com.myaccounts.app.data.local.AppDatabase
import com.myaccounts.app.util.CustodyBackupManager
import com.myaccounts.app.util.CustodyExcelDataManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CustodyTransferDataInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val db get() = AppDatabase.getInstance(context)
    private lateinit var externalId: String
    private var custodyId: Long = 0L
    private lateinit var workbook: File
    private lateinit var backup: File

    @Before
    fun setUp() {
        runBlocking {
            val token = UUID.randomUUID().toString()
            externalId = "TRANSFER-CUSTODY-$token"
            custodyId = CustodyRepository(db, context).createCustody(
                CustodyEntity(name = "اختبار نقل $token", organizationName = "جهة اختبار $token", externalId = externalId)
            )
            CustodyRepository(db, context).addTransaction(
                custodyId = custodyId,
                currency = "YER",
                type = CustodyTransactionType.RECEIVED_FROM_ORG,
                personId = null,
                amountMinor = 125000L,
                categoryName = "اختبار",
                description = "اختبار نقل",
                date = System.currentTimeMillis()
            )
            workbook = File(context.cacheDir, "custody-test-${System.nanoTime()}.xlsx")
            backup = File(context.cacheDir, "custody-test-${System.nanoTime()}.custody")
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            db.custodyDao().getCustodyByExternalId(externalId)?.let {
                db.custodyDao().deleteTransactions(it.id)
                db.custodyDao().deleteAccounts(it.id)
                db.custodyDao().deletePersons(it.id)
                db.custodyDao().deleteCustody(it.id)
            }
        }
        workbook.delete()
        backup.delete()
    }

    @Test
    fun custodyExcelExportAndPreviewStayInsideCustodyData() = runBlocking {
        val export = CustodyExcelDataManager.exportActive(context, Uri.fromFile(workbook)).getOrThrow()
        assertEquals(1, export.custodies)
        assertEquals(1, export.transactions)
        val preview = CustodyExcelDataManager.previewImport(context, Uri.fromFile(workbook)).getOrThrow()
        assertTrue(preview.isValid)
        assertEquals(1, preview.custodies)
        assertEquals(1, preview.transactions)
        assertEquals(1, db.custodyDao().getAllTransactions(custodyId, false).size)
    }

    @Test
    fun custodyBackupRestoreRoundTripRestoresCustodyOnly() = runBlocking {
        val created = CustodyBackupManager.createBackup(context, Uri.fromFile(backup)).getOrThrow()
        assertEquals(1, created.custodies)
        assertEquals(1, created.transactions)

        db.custodyDao().deleteTransactions(custodyId)
        db.custodyDao().deleteAccounts(custodyId)
        db.custodyDao().deletePersons(custodyId)
        db.custodyDao().deleteCustody(custodyId)
        assertTrue(db.custodyDao().getCustodyByExternalId(externalId) == null)

        val restored = CustodyBackupManager.restoreBackup(context, Uri.fromFile(backup)).getOrThrow()
        assertEquals(1, restored.custodies)
        assertEquals(1, restored.transactions)
        val custody = db.custodyDao().getCustodyByExternalId(externalId)!!
        assertEquals(1, db.custodyDao().getAllTransactions(custody.id, false).size)
        assertEquals(125000L, db.custodyDao().getOwnerAccount(custody.id, "YER")!!.balanceMinor)
        custodyId = custody.id
    }
}
