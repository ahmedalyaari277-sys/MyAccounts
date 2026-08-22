package com.myaccounts.app.backup

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.myaccounts.app.data.local.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupRestoreSchemaInvariantTest {
    private lateinit var database: AppDatabase
    private lateinit var sqlite: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sqlite = database.openHelper.writableDatabase
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun transactionRestoreInsertMustExplicitlyProvideArchiveState() {
        val personId = insertPerson("نسخة احتياطية")
        val accountId = insertAccount(personId)

        sqlite.execSQL(
            """
            INSERT INTO transactions (
                id, accountId, type, amountMinor, description,
                transactionDate, createdAt, isArchived, archivedWithPerson
            ) VALUES (?,?,?,?,?,?,?,?,?)
            """.trimIndent(),
            arrayOf(1L, accountId, "RECEIVABLE", 10000L, "اختبار", 1L, 1L, 1, 1)
        )

        sqlite.query("SELECT isArchived, archivedWithPerson FROM transactions WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals(1, cursor.getInt(1))
        }
    }

    @Test
    fun archiveSnapshotSchemaAcceptsArchiveOwnershipState() {
        sqlite.execSQL(
            """
            INSERT INTO archived_transaction_snapshots (
                transactionId, accountId, personId, personName, personPhone,
                personAddress, personNotes, currencyCode, type, amountMinor,
                description, transactionDate, createdAt, archivedWithPerson, archivedAt
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """.trimIndent(),
            arrayOf(1L, 1L, 1L, "نسخة", "", "", "", "YER", "RECEIVABLE", 100L, "", 1L, 1L, 1, 1L)
        )

        sqlite.query("SELECT archivedWithPerson FROM archived_transaction_snapshots WHERE transactionId = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
    }

    private fun insertPerson(name: String): Long {
        sqlite.execSQL(
            "INSERT INTO people (name,phone,address,notes,createdAt,isActive) VALUES (?,?,?,?,?,?)",
            arrayOf(name, "", "", "", 1L, 1)
        )
        return sqlite.query("SELECT id FROM people ORDER BY id DESC LIMIT 1").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }
    }

    private fun insertAccount(personId: Long): Long {
        sqlite.execSQL(
            "INSERT INTO currency_accounts (personId,currencyCode,balanceMinor,createdAt,updatedAt) VALUES (?,?,?,?,?)",
            arrayOf(personId, "YER", 0L, 1L, 1L)
        )
        return sqlite.query("SELECT id FROM currency_accounts ORDER BY id DESC LIMIT 1").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }
    }
}
