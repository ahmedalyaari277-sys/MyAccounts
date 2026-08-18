package com.myaccounts.app.util

import android.content.Context
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import com.myaccounts.app.data.local.AppDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object DatabaseBackupManager {

    private const val FORMAT_VERSION = 2
    private const val LEGACY_FORMAT_VERSION = 1
    private const val BACKUP_TYPE = "myaccounts_full_backup"
    private const val DATABASE_ENTRY = "backup.json"
    private const val FILES_PREFIX = "files/"

    fun suggestedFileName(): String {
        val timestamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.US
        ).format(Date())
        return "myaccounts_backup_$timestamp.myaccounts"
    }

    suspend fun createBackup(
        context: Context,
        uri: Uri
    ): Result<Unit> = runCatching {
        val database = AppDatabase.getInstance(context)
        val sqlite = database.openHelper.readableDatabase
        val backup = buildBackupJson(sqlite)

        context.contentResolver.openOutputStream(uri)?.use { output ->
            ZipOutputStream(output.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(DATABASE_ENTRY))
                zip.write(backup.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                addAttachmentFiles(context, sqlite, zip)
            }
        } ?: error("تعذر فتح ملف النسخة الاحتياطية للكتابة.")
    }

    suspend fun restoreBackup(
        context: Context,
        uri: Uri
    ): Result<Unit> = runCatching {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("تعذر فتح ملف النسخة الاحتياطية.")

        val tempDirectory = File(context.cacheDir, "backup_restore_${System.currentTimeMillis()}")
        tempDirectory.mkdirs()

        try {
            val databaseJsonFile = File(tempDirectory, DATABASE_ENTRY)
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    require(!entry.isDirectory) { "ملف النسخة الاحتياطية يحتوي على مجلدات غير مدعومة." }
                    val safeRelativePath = safeZipPath(entry.name)
                    val destination = File(tempDirectory, safeRelativePath)
                    require(destination.canonicalPath.startsWith(tempDirectory.canonicalPath + File.separator)) {
                        "مسار ملف غير صالح داخل النسخة الاحتياطية."
                    }
                    destination.parentFile?.mkdirs()
                    destination.outputStream().use { output -> zip.copyTo(output) }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            require(databaseJsonFile.isFile) {
                "النسخة الاحتياطية لا تحتوي على بيانات قاعدة البيانات."
            }

            val backup = JSONObject(databaseJsonFile.readText(Charsets.UTF_8))
            validateBackup(backup)

            val attachments = backup.optJSONArray("attachments") ?: JSONArray()
            val filesToInstall = prepareAttachmentFiles(context, tempDirectory, attachments)
            val database = AppDatabase.getInstance(context)
            val sqlite = database.openHelper.writableDatabase
            val oldAttachmentPaths = existingAttachmentPaths(sqlite)

            try {
                installAttachmentFiles(context, filesToInstall)

                sqlite.beginTransaction()
                try {
                    restoreIntoDatabase(sqlite, backup)
                    sqlite.setTransactionSuccessful()
                } finally {
                    sqlite.endTransaction()
                }
            } catch (error: Throwable) {
                filesToInstall.forEach { it.destination.delete() }
                throw error
            }

            oldAttachmentPaths.forEach { File(context.filesDir, it).delete() }
        } finally {
            tempDirectory.deleteRecursively()
        }
    }

    private data class FileToInstall(
        val source: File,
        val destination: File
    )

    private fun buildBackupJson(db: SupportSQLiteDatabase): JSONObject {
        val root = JSONObject()
            .put("backupType", BACKUP_TYPE)
            .put("formatVersion", FORMAT_VERSION)
            .put("createdAt", System.currentTimeMillis())

        root.put("people", JSONArray().apply {
            db.query("SELECT id, name, phone, address, notes, createdAt, isActive FROM people ORDER BY id").use { cursor ->
                while (cursor.moveToNext()) {
                    put(JSONObject()
                        .put("id", cursor.getLong(0))
                        .put("name", cursor.getString(1))
                        .put("phone", cursor.getString(2))
                        .put("address", cursor.getString(3))
                        .put("notes", cursor.getString(4))
                        .put("createdAt", cursor.getLong(5))
                        .put("isActive", cursor.getInt(6) != 0))
                }
            }
        })

        root.put("currencyAccounts", JSONArray().apply {
            db.query("SELECT id, personId, currencyCode, balanceMinor, createdAt, updatedAt FROM currency_accounts ORDER BY id").use { cursor ->
                while (cursor.moveToNext()) {
                    put(JSONObject()
                        .put("id", cursor.getLong(0))
                        .put("personId", cursor.getLong(1))
                        .put("currencyCode", cursor.getString(2))
                        .put("balanceMinor", cursor.getLong(3))
                        .put("createdAt", cursor.getLong(4))
                        .put("updatedAt", cursor.getLong(5)))
                }
            }
        })

        root.put("transactions", JSONArray().apply {
            db.query("SELECT id, accountId, type, amountMinor, description, transactionDate, createdAt FROM transactions ORDER BY id").use { cursor ->
                while (cursor.moveToNext()) {
                    put(JSONObject()
                        .put("id", cursor.getLong(0))
                        .put("accountId", cursor.getLong(1))
                        .put("type", cursor.getString(2))
                        .put("amountMinor", cursor.getLong(3))
                        .put("description", cursor.getString(4))
                        .put("transactionDate", cursor.getLong(5))
                        .put("createdAt", cursor.getLong(6)))
                }
            }
        })

        root.put("attachments", JSONArray().apply {
            db.query("SELECT id, transactionId, fileName, mimeType, relativePath, sizeBytes, createdAt FROM transaction_attachments ORDER BY id").use { cursor ->
                while (cursor.moveToNext()) {
                    put(JSONObject()
                        .put("id", cursor.getLong(0))
                        .put("transactionId", cursor.getLong(1))
                        .put("fileName", cursor.getString(2))
                        .put("mimeType", cursor.getString(3))
                        .put("relativePath", cursor.getString(4))
                        .put("sizeBytes", cursor.getLong(5))
                        .put("createdAt", cursor.getLong(6)))
                }
            }
        })

        return root
    }

    private fun addAttachmentFiles(
        context: Context,
        db: SupportSQLiteDatabase,
        zip: ZipOutputStream
    ) {
        db.query("SELECT relativePath FROM transaction_attachments ORDER BY id").use { cursor ->
            while (cursor.moveToNext()) {
                val relativePath = cursor.getString(0)
                val file = File(context.filesDir, relativePath)
                require(file.isFile) { "ملف المرفق غير موجود: $relativePath" }
                val entryName = FILES_PREFIX + safeZipPath(relativePath)
                zip.putNextEntry(ZipEntry(entryName))
                file.inputStream().buffered().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun validateBackup(backup: JSONObject) {
        require(backup.optString("backupType") == BACKUP_TYPE) {
            "ملف النسخة الاحتياطية غير صالح."
        }
        val version = backup.optInt("formatVersion", -1)
        require(version == LEGACY_FORMAT_VERSION || version == FORMAT_VERSION) {
            "إصدار النسخة الاحتياطية غير مدعوم."
        }
        require(backup.has("people")) { "النسخة الاحتياطية لا تحتوي على بيانات الأشخاص." }
        require(backup.has("currencyAccounts")) { "النسخة الاحتياطية لا تحتوي على حسابات العملات." }
        require(backup.has("transactions")) { "النسخة الاحتياطية لا تحتوي على العمليات." }
        if (version >= FORMAT_VERSION) require(backup.has("attachments")) {
            "النسخة الاحتياطية لا تحتوي على المرفقات."
        }

        val people = backup.getJSONArray("people")
        val accounts = backup.getJSONArray("currencyAccounts")
        val transactions = backup.getJSONArray("transactions")
        val attachments = backup.optJSONArray("attachments") ?: JSONArray()

        val transactionIds = mutableSetOf<Long>()
        for (index in 0 until people.length()) {
            val person = people.getJSONObject(index)
            require(person.has("id") && person.has("name") && person.has("createdAt") && person.has("isActive"))
        }
        for (index in 0 until accounts.length()) {
            val account = accounts.getJSONObject(index)
            require(account.has("id") && account.has("personId") && account.has("currencyCode") && account.has("balanceMinor") && account.has("createdAt") && account.has("updatedAt"))
        }
        for (index in 0 until transactions.length()) {
            val transaction = transactions.getJSONObject(index)
            require(transaction.has("id") && transaction.has("accountId") && transaction.has("type") && transaction.has("amountMinor") && transaction.has("description") && transaction.has("transactionDate") && transaction.has("createdAt"))
            require(transaction.getString("type") == "RECEIVABLE" || transaction.getString("type") == "PAYABLE") {
                "نوع عملية غير مدعوم في النسخة الاحتياطية."
            }
            transactionIds += transaction.getLong("id")
        }
        for (index in 0 until attachments.length()) {
            val attachment = attachments.getJSONObject(index)
            require(attachment.has("id") && attachment.has("transactionId") && attachment.has("fileName") && attachment.has("mimeType") && attachment.has("relativePath") && attachment.has("sizeBytes") && attachment.has("createdAt"))
            require(transactionIds.contains(attachment.getLong("transactionId"))) {
                "المرفق مرتبط بعملية غير موجودة."
            }
            safeZipPath(attachment.getString("relativePath"))
        }
    }

    private fun prepareAttachmentFiles(
        context: Context,
        tempDirectory: File,
        attachments: JSONArray
    ): List<FileToInstall> {
        val result = mutableListOf<FileToInstall>()
        for (index in 0 until attachments.length()) {
            val attachment = attachments.getJSONObject(index)
            val relativePath = safeZipPath(attachment.getString("relativePath"))
            val source = File(tempDirectory, FILES_PREFIX + relativePath)
            require(source.isFile) { "ملف المرفق غير موجود داخل النسخة الاحتياطية: $relativePath" }
            require(source.length() == attachment.getLong("sizeBytes")) {
                "حجم المرفق لا يطابق البيانات المسجلة: ${attachment.getString("fileName")}"
            }
            result += FileToInstall(source, File(context.filesDir, relativePath))
        }
        return result
    }

    private fun installAttachmentFiles(context: Context, files: List<FileToInstall>) {
        files.forEach { item ->
            item.destination.parentFile?.mkdirs()
            item.source.inputStream().buffered().use { input ->
                item.destination.outputStream().buffered().use { output -> input.copyTo(output) }
            }
        }
    }

    private fun existingAttachmentPaths(db: SupportSQLiteDatabase): List<String> {
        return buildList {
            db.query("SELECT relativePath FROM transaction_attachments").use { cursor ->
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
    }

    private fun restoreIntoDatabase(db: SupportSQLiteDatabase, backup: JSONObject) {
        db.execSQL("DELETE FROM transactions")
        db.execSQL("DELETE FROM currency_accounts")
        db.execSQL("DELETE FROM people")

        val people = backup.getJSONArray("people")
        for (index in 0 until people.length()) {
            val person = people.getJSONObject(index)
            db.execSQL(
                "INSERT INTO people (id, name, phone, address, notes, createdAt, isActive) VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf(person.getLong("id"), person.getString("name"), person.optString("phone"), person.optString("address"), person.optString("notes"), person.getLong("createdAt"), if (person.getBoolean("isActive")) 1 else 0)
            )
        }

        val accounts = backup.getJSONArray("currencyAccounts")
        for (index in 0 until accounts.length()) {
            val account = accounts.getJSONObject(index)
            db.execSQL(
                "INSERT INTO currency_accounts (id, personId, currencyCode, balanceMinor, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf(account.getLong("id"), account.getLong("personId"), account.getString("currencyCode"), account.getLong("balanceMinor"), account.getLong("createdAt"), account.getLong("updatedAt"))
            )
        }

        val transactions = backup.getJSONArray("transactions")
        for (index in 0 until transactions.length()) {
            val transaction = transactions.getJSONObject(index)
            db.execSQL(
                "INSERT INTO transactions (id, accountId, type, amountMinor, description, transactionDate, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf(transaction.getLong("id"), transaction.getLong("accountId"), transaction.getString("type"), transaction.getLong("amountMinor"), transaction.getString("description"), transaction.getLong("transactionDate"), transaction.getLong("createdAt"))
            )
        }

        if (backup.optInt("formatVersion", LEGACY_FORMAT_VERSION) >= FORMAT_VERSION) {
            val attachments = backup.getJSONArray("attachments")
            for (index in 0 until attachments.length()) {
                val attachment = attachments.getJSONObject(index)
                db.execSQL(
                    "INSERT INTO transaction_attachments (id, transactionId, fileName, mimeType, relativePath, sizeBytes, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(attachment.getLong("id"), attachment.getLong("transactionId"), attachment.getString("fileName"), attachment.getString("mimeType"), attachment.getString("relativePath"), attachment.getLong("sizeBytes"), attachment.getLong("createdAt"))
                )
            }
        }
    }

    private fun safeZipPath(path: String): String {
        val normalized = path.replace('\\', '/')
        require(normalized.isNotBlank() && !normalized.startsWith('/') && !normalized.contains("../") && normalized != ".." && !normalized.contains("/./") && !normalized.startsWith("./")) {
            "مسار ملف غير صالح داخل النسخة الاحتياطية."
        }
        return normalized
    }
}
