package com.myaccounts.app.custody

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myaccounts.app.data.custody.CustodyAccountEntity
import com.myaccounts.app.data.custody.CustodyDao
import com.myaccounts.app.data.custody.CustodyEntity
import com.myaccounts.app.data.custody.CustodyPersonEntity
import com.myaccounts.app.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustodyDatabaseBootstrapTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val dao: CustodyDao = db.custodyDao()

    @After
    fun closeDb() = db.close()

    @Test
    fun custodyStartsWithThreeOwnerCurrencyAccounts() = runBlocking {
        val custodyId = dao.insertCustody(
            CustodyEntity(
                name = "اختبار صاحب العهدة",
                organizationName = "اختبار الجهة"
            )
        )
        dao.insertAccounts(listOf("YER", "SAR", "USD").map { code ->
            CustodyAccountEntity(custodyId = custodyId, holderType = "OWNER", currencyCode = code)
        })

        val accounts = dao.getAllAccounts(custodyId)
        assertEquals(setOf("YER", "SAR", "USD"), accounts.filter { it.holderType == "OWNER" }.map { it.currencyCode }.toSet())
        assertEquals(3, accounts.count { it.holderType == "OWNER" })
        assertEquals(0L, accounts.sumOf { it.balanceMinor })
    }

    @Test
    fun custodyPersonStartsWithThreeCurrencyAccounts() = runBlocking {
        val custodyId = dao.insertCustody(
            CustodyEntity(
                name = "اختبار صاحب العهدة",
                organizationName = "اختبار الجهة"
            )
        )
        val personId = dao.insertPerson(
            CustodyPersonEntity(custodyId = custodyId, name = "اختبار الشخص")
        )
        dao.insertAccounts(listOf("YER", "SAR", "USD").map { code ->
            CustodyAccountEntity(custodyId = custodyId, holderType = "PERSON", personId = personId, currencyCode = code)
        })

        val accounts = dao.getAllAccounts(custodyId).filter { it.personId == personId }
        assertEquals(3, accounts.size)
        assertEquals(setOf("YER", "SAR", "USD"), accounts.map { it.currencyCode }.toSet())
        accounts.forEach { assertNotNull(it.personId) }
    }
}
