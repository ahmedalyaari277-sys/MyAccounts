package com.myaccounts.app.util

import android.content.Context
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import com.myaccounts.app.data.local.AppDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DatabaseBackupManager {

    private const val FORMAT_VERSION = 1
    private const val BACKUP_TYPE = "myaccounts_full_backup"

    fun suggestedFileName(): String {
        val timestamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.US
        ).format(Date())

        return "myaccounts_backup_$timestamp.json"
    }

    suspend fun createBackup(
        context: Context,
        uri: Uri
    ): Result<Unit> = runCatching {
        val database = AppDatabase.getInstance(context)
        val sqlite = database.openHelper.readableDatabase
        val backup = buildBackupJson(sqlite)

        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.writer(Charsets.UTF_8).use { writer ->
                writer.write(backup.toString(2))
            }
        } ?: error("تعذر فتح ملف النسخة الاحتياطية للكتابة.")
    }

    suspend fun restoreBackup(
        context: Context,
        uri: Uri
    ): Result<Unit> = runCatching {
        val json = context.contentResolver
            .openInputStream(uri)
            ?.use { input ->
                input.reader(Charsets.UTF_8).use { it.readText() }
            }
            ?: error("تعذر فتح ملف النسخة الاحتياطية.")

        val backup = JSONObject(json)
        validateBackup(backup)

        val database = AppDatabase.getInstance(context)
        val sqlite = database.openHelper.writableDatabase

        sqlite.beginTransaction()
        try {
            restoreIntoDatabase(sqlite, backup)
            sqlite.setTransactionSuccessful()
        } finally {
            sqlite.endTransaction()
        }
    }

    private fun buildBackupJson(
        db: SupportSQLiteDatabase
    ): JSONObject {
        val root = JSONObject()
            .put("backupType", BACKUP_TYPE)
            .put("formatVersion", FORMAT_VERSION)
            .put("createdAt", System.currentTimeMillis())

        root.put("people", JSONArray().apply {
            db.query(
                """
                SELECT id, name, phone, address, notes, createdAt, isActive
                FROM people
                ORDER BY id
                """.trimIndent()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    put(
                        JSONObject()
                            .put("id", cursor.getLong(0))
                            .put("name", cursor.getString(1))
                            .put("phone", cursor.getString(2))
                            .put("address", cursor.getString(3))
                            .put("notes", cursor.getString(4))
                            .put("createdAt", cursor.getLong(5))
                            .put("isActive", cursor.getInt(6) != 0)
                    )
                }
            }
        })

        root.put("currencyAccounts", JSONArray().apply {
            db.query(
                """
                SELECT id, personId, currencyCode, balanceMinor, createdAt, updatedAt
                FROM currency_accounts
                ORDER BY id
                """.trimIndent()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    put(
                        JSONObject()
                            .put("id", cursor.getLong(0))
                            .put("personId", cursor.getLong(1))
                            .put("currencyCode", cursor.getString(2))
                            .put("balanceMinor", cursor.getLong(3))
                            .put("createdAt", cursor.getLong(4))
                            .put("updatedAt", cursor.getLong(5))
                    )
                }
            }
        })

        root.put("transactions", JSONArray().apply {
            db.query(
                """
                SELECT id, accountId, type, amountMinor, description, transactionDate, createdAt
                FROM transactions
                ORDER BY id
                """.trimIndent()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    put(
                        JSONObject()
                            .put("id", cursor.getLong(0))
                            .put("accountId", cursor.getLong(1))
                            .put("type", cursor.getString(2))
                            .put("amountMinor", cursor.getLong(3))
                            .put("description", cursor.getString(4))
                            .put("transactionDate", cursor.getLong(5))
                            .put("createdAt", cursor.getLong(6))
                    )
                }
            }
        })

        return root
    }

    private fun validateBackup(
        backup: JSONObject
    ) {
        require(
            backup.optString("backupType") == BACKUP_TYPE
        ) {
            "ملف النسخة الاحتياطية غير صالح."
        }

        require(
            backup.optInt("formatVersion", -1) == FORMAT_VERSION
        ) {
            "إصدار النسخة الاحتياطية غير مدعوم."
        }

        require(backup.has("people")) {
            "النسخة الاحتياطية لا تحتوي على بيانات الأشخاص."
        }
        require(backup.has("currencyAccounts")) {
            "النسخة الاحتياطية لا تحتوي على حسابات العملات."
        }
        require(backup.has("transactions")) {
            "النسخة الاحتياطية لا تحتوي على العمليات."
        }

        val people = backup.getJSONArray("people")
        val accounts = backup.getJSONArray("currencyAccounts")
        val transactions = backup.getJSONArray("transactions")

        for (index in 0 until people.length()) {
            val person = people.getJSONObject(index)
            require(person.has("id"))
            require(person.has("name"))
            require(person.has("createdAt"))
            require(person.has("isActive"))
        }

        for (index in 0 until accounts.length()) {
            val account = accounts.getJSONObject(index)
            require(account.has("id"))
            require(account.has("personId"))
            require(account.has("currencyCode"))
            require(account.has("balanceMinor"))
            require(account.has("createdAt"))
            require(account.has("updatedAt"))
        }

        for (index in 0 until transactions.length()) {
            val transaction = transactions.getJSONObject(index)
            require(transaction.has("id"))
            require(transaction.has("accountId"))
            require(transaction.has("type"))
            require(transaction.has("amountMinor"))
            require(transaction.has("description"))
            require(transaction.has("transactionDate"))
            require(transaction.has("createdAt"))

            require(
                transaction.getString("type") == "RECEIVABLE" ||
                    transaction.getString("type") == "PAYABLE"
            ) {
                "نوع عملية غير مدعوم في النسخة الاحتياطية."
            }
        }
    }

    private fun restoreIntoDatabase(
        db: SupportSQLiteDatabase,
        backup: JSONObject
    ) {
        db.execSQL("DELETE FROM transactions")
        db.execSQL("DELETE FROM currency_accounts")
        db.execSQL("DELETE FROM people")

        val people = backup.getJSONArray("people")
        for (index in 0 until people.length()) {
            val person = people.getJSONObject(index)

            db.execSQL(
                """
                INSERT INTO people
                (id, name, phone, address, notes, createdAt, isActive)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(
                    person.getLong("id"),
                    person.getString("name"),
                    person.optString("phone"),
                    person.optString("address"),
                    person.optString("notes"),
                    person.getLong("createdAt"),
                    if (person.getBoolean("isActive")) 1 else 0
                )
            )
        }

        val accounts = backup.getJSONArray("currencyAccounts")
        for (index in 0 until accounts.length()) {
            val account = accounts.getJSONObject(index)

            db.execSQL(
                """
                INSERT INTO currency_accounts
                (id, personId, currencyCode, balanceMinor, createdAt, updatedAt)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(
                    account.getLong("id"),
                    account.getLong("personId"),
                    account.getString("currencyCode"),
                    account.getLong("balanceMinor"),
                    account.getLong("createdAt"),
                    account.getLong("updatedAt")
                )
            )
        }

        val transactions = backup.getJSONArray("transactions")
        for (index in 0 until transactions.length()) {
            val transaction = transactions.getJSONObject(index)

            db.execSQL(
                """
                INSERT INTO transactions
                (id, accountId, type, amountMinor, description, transactionDate, createdAt)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(
                    transaction.getLong("id"),
                    transaction.getLong("accountId"),
                    transaction.getString("type"),
                    transaction.getLong("amountMinor"),
                    transaction.getString("description"),
                    transaction.getLong("transactionDate"),
                    transaction.getLong("createdAt")
                )
            )
        }
    }
}
