package com.myaccounts.app.custody

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myaccounts.app.data.custody.CustodyEntity
import com.myaccounts.app.data.custody.CustodyRepository
import com.myaccounts.app.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustodyFormFieldsDatabaseTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val db get() = AppDatabase.getInstance(context)
    private lateinit var externalId: String

    @Before
    fun setUp() {
        externalId = "TEST-CUSTODY-FIELDS-${System.nanoTime()}"
    }

    @After
    fun tearDown() = runBlocking {
        db.custodyDao().getCustodyByExternalId(externalId)?.let {
            db.custodyDao().deleteTransactions(it.id)
            db.custodyDao().deleteAccounts(it.id)
            db.custodyDao().deletePersons(it.id)
            db.custodyDao().deleteCustody(it.id)
        }
        Unit
    }

    @Test
    fun allHolderAndOrganizationFieldsPersist() = runBlocking {
        val expected = CustodyEntity(
            name = "صاحب العهدة الكامل",
            phone = "777123456",
            address = "صنعاء",
            notes = "ملاحظات صاحب العهدة",
            organizationName = "الجهة الكاملة",
            organizationPhone = "711654321",
            organizationAddress = "الحديدة",
            organizationNotes = "ملاحظات الجهة",
            externalId = externalId
        )

        val repo = CustodyRepository(db, context)
        val id = repo.createCustody(expected)
        val actual = db.custodyDao().getCustodyByExternalId(externalId)!!

        assertEquals(id, actual.id)
        assertEquals(expected.name, actual.name)
        assertEquals(expected.phone, actual.phone)
        assertEquals(expected.address, actual.address)
        assertEquals(expected.notes, actual.notes)
        assertEquals(expected.organizationName, actual.organizationName)
        assertEquals(expected.organizationPhone, actual.organizationPhone)
        assertEquals(expected.organizationAddress, actual.organizationAddress)
        assertEquals(expected.organizationNotes, actual.organizationNotes)
    }
}
