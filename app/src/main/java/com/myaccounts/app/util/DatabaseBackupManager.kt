package com.myaccounts.app.util

import android.content.Context
import android.content.Intent
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
    private const val FORMAT_VERSION = 3
    private const val LEGACY_FORMAT_VERSION = 1
    private const val PREVIOUS_FORMAT_VERSION = 2
    private const val BACKUP_TYPE = "myaccounts_full_backup"
    private const val DATABASE_ENTRY = "backup.json"
    private const val FILES_PREFIX = "files/"
    private const val PREFS_NAME = "myaccounts_backup_preferences"
    private const val LAST_BACKUP_URI_KEY = "last_backup_uri"

    fun suggestedFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "myaccounts_backup_$timestamp.myaccounts"
    }

    suspend fun createBackup(context: Context, uri: Uri): Result<Unit> = runCatching {
        val sqlite = AppDatabase.getInstance(context).openHelper.readableDatabase
        val backup = buildBackupJson(sqlite)
        context.contentResolver.openOutputStream(uri)?.use { output ->
            ZipOutputStream(output.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(DATABASE_ENTRY))
                zip.write(backup.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                addAttachmentFiles(context, sqlite, zip)
            }
        } ?: error("تعذر فتح ملف النسخة الاحتياطية للكتابة.")
        rememberLastBackupUri(context, uri)
    }

    fun shareLastBackup(context: Context): Result<Unit> = try {
        val uriString = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(LAST_BACKUP_URI_KEY, null)
            ?: throw IllegalStateException("لا توجد نسخة احتياطية محفوظة للمشاركة. أنشئ نسخة احتياطية أولاً.")
        val uri = Uri.parse(uriString)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية من دفتر الحسابات")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة النسخة الاحتياطية").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Result.success(Unit)
    } catch (exception: Exception) {
        Result.failure(exception)
    }

    private fun rememberLastBackupUri(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(LAST_BACKUP_URI_KEY, uri.toString())
            .apply()
    }

    suspend fun restoreBackup(context: Context, uri: Uri): Result<Unit> = runCatching {
        val input = context.contentResolver.openInputStream(uri) ?: error("تعذر فتح ملف النسخة الاحتياطية.")
        val sourceFile = File.createTempFile("myaccounts_backup_", ".tmp", context.cacheDir)
        input.use { sourceFile.outputStream().buffered().use { output -> it.copyTo(output) } }
        val tempDirectory = File(context.cacheDir, "backup_restore_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val databaseJsonFile = File(tempDirectory, DATABASE_ENTRY)
            if (isZip(sourceFile)) extractZip(sourceFile, tempDirectory) else sourceFile.copyTo(databaseJsonFile, overwrite = true)
            require(databaseJsonFile.isFile) { "النسخة الاحتياطية لا تحتوي على بيانات قاعدة البيانات." }
            val backup = JSONObject(databaseJsonFile.readText(Charsets.UTF_8))
            validateBackup(backup)
            val attachments = backup.optJSONArray("attachments") ?: JSONArray()
            val filesToInstall = prepareAttachmentFiles(context, tempDirectory, attachments)
            val database = AppDatabase.getInstance(context)
            val sqlite = database.openHelper.writableDatabase
            val oldAttachmentPaths = existingAttachmentPaths(sqlite)
            val newAttachmentPaths = filesToInstall.map { it.destination.relativeTo(context.filesDir).path.replace(File.separatorChar, '/') }.toSet()
            val oldFilesDirectory = File(tempDirectory, "old-files").apply { mkdirs() }
            backupExistingFiles(context, oldAttachmentPaths, oldFilesDirectory)
            try {
                installAttachmentFiles(filesToInstall)
                sqlite.beginTransaction()
                try {
                    restoreIntoDatabase(sqlite, backup)
                    sqlite.setTransactionSuccessful()
                } finally {
                    sqlite.endTransaction()
                }
                oldAttachmentPaths.filter { it !in newAttachmentPaths }.forEach { File(context.filesDir, it).delete() }
            } catch (error: Throwable) {
                filesToInstall.forEach { it.destination.delete() }
                restoreExistingFiles(context, oldFilesDirectory)
                throw error
            }
        } finally {
            tempDirectory.deleteRecursively()
            sourceFile.delete()
        }
    }

    private data class FileToInstall(val source: File, val destination: File)

    private fun isZip(file: File): Boolean = file.inputStream().buffered().use { input -> input.read() == 'P'.code && input.read() == 'K'.code }

    private fun extractZip(source: File, tempDirectory: File) {
        source.inputStream().buffered().use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    require(!entry.isDirectory) { "ملف النسخة الاحتياطية يحتوي على مجلدات غير مدعومة." }
                    val safeRelativePath = safeZipPath(entry.name)
                    val destination = File(tempDirectory, safeRelativePath)
                    require(destination.canonicalPath.startsWith(tempDirectory.canonicalPath + File.separator)) { "مسار ملف غير صالح داخل النسخة الاحتياطية." }
                    destination.parentFile?.mkdirs()
                    destination.outputStream().use { output -> zip.copyTo(output) }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }

    private fun buildBackupJson(db: SupportSQLiteDatabase): JSONObject {
        val root = JSONObject().put("backupType", BACKUP_TYPE).put("formatVersion", FORMAT_VERSION).put("createdAt", System.currentTimeMillis())
        root.put("people", JSONArray().apply {
            db.query("SELECT id,name,phone,address,notes,createdAt,isActive FROM people ORDER BY id").use { c -> while (c.moveToNext()) put(JSONObject().put("id", c.getLong(0)).put("name", c.getString(1)).put("phone", c.getString(2)).put("address", c.getString(3)).put("notes", c.getString(4)).put("createdAt", c.getLong(5)).put("isActive", c.getInt(6) != 0)) }
        })
        root.put("currencyAccounts", JSONArray().apply {
            db.query("SELECT id,personId,currencyCode,balanceMinor,createdAt,updatedAt FROM currency_accounts ORDER BY id").use { c -> while (c.moveToNext()) put(JSONObject().put("id", c.getLong(0)).put("personId", c.getLong(1)).put("currencyCode", c.getString(2)).put("balanceMinor", c.getLong(3)).put("createdAt", c.getLong(4)).put("updatedAt", c.getLong(5))) }
        })
        root.put("transactions", JSONArray().apply {
            db.query("SELECT id,accountId,type,amountMinor,description,transactionDate,createdAt,isArchived FROM transactions ORDER BY id").use { c -> while (c.moveToNext()) put(JSONObject().put("id", c.getLong(0)).put("accountId", c.getLong(1)).put("type", c.getString(2)).put("amountMinor", c.getLong(3)).put("description", c.getString(4)).put("transactionDate", c.getLong(5)).put("createdAt", c.getLong(6)).put("isArchived", c.getInt(7) != 0)) }
        })
        root.put("attachments", JSONArray().apply {
            db.query("SELECT id,transactionId,fileName,mimeType,relativePath,sizeBytes,createdAt FROM transaction_attachments ORDER BY id").use { c -> while (c.moveToNext()) put(JSONObject().put("id", c.getLong(0)).put("transactionId", c.getLong(1)).put("fileName", c.getString(2)).put("mimeType", c.getString(3)).put("relativePath", c.getString(4)).put("sizeBytes", c.getLong(5)).put("createdAt", c.getLong(6))) }
        })
        return root
    }

    private fun addAttachmentFiles(context: Context, db: SupportSQLiteDatabase, zip: ZipOutputStream) {
        db.query("SELECT relativePath FROM transaction_attachments ORDER BY id").use { c ->
            while (c.moveToNext()) {
                val relativePath = safeZipPath(c.getString(0))
                val file = File(context.filesDir, relativePath)
                require(file.isFile) { "ملف المرفق غير موجود: $relativePath" }
                zip.putNextEntry(ZipEntry(FILES_PREFIX + relativePath))
                file.inputStream().buffered().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun validateBackup(backup: JSONObject) {
        require(backup.optString("backupType") == BACKUP_TYPE) { "ملف النسخة الاحتياطية غير صالح." }
        val version = backup.optInt("formatVersion", -1)
        require(version == LEGACY_FORMAT_VERSION || version == PREVIOUS_FORMAT_VERSION || version == FORMAT_VERSION) { "إصدار النسخة الاحتياطية غير مدعوم." }
        require(backup.has("people") && backup.has("currencyAccounts") && backup.has("transactions")) { "النسخة الاحتياطية لا تحتوي على البيانات الأساسية." }
        if (version >= PREVIOUS_FORMAT_VERSION) require(backup.has("attachments")) { "النسخة الاحتياطية لا تحتوي على المرفقات." }
        val people = backup.getJSONArray("people")
        val accounts = backup.getJSONArray("currencyAccounts")
        val transactions = backup.getJSONArray("transactions")
        val attachments = backup.optJSONArray("attachments") ?: JSONArray()
        val transactionIds = mutableSetOf<Long>()
        for (i in 0 until people.length()) { val p = people.getJSONObject(i); require(p.has("id") && p.has("name") && p.has("createdAt") && p.has("isActive")) }
        for (i in 0 until accounts.length()) { val a = accounts.getJSONObject(i); require(a.has("id") && a.has("personId") && a.has("currencyCode") && a.has("balanceMinor") && a.has("createdAt") && a.has("updatedAt")) }
        for (i in 0 until transactions.length()) {
            val t = transactions.getJSONObject(i)
            require(t.has("id") && t.has("accountId") && t.has("type") && t.has("amountMinor") && t.has("description") && t.has("transactionDate") && t.has("createdAt"))
            require(t.getString("type") == "RECEIVABLE" || t.getString("type") == "PAYABLE") { "نوع عملية غير مدعوم في النسخة الاحتياطية." }
            if (version >= FORMAT_VERSION) require(t.has("isArchived")) { "بيانات أرشفة العمليات غير مكتملة." }
            transactionIds += t.getLong("id")
        }
        for (i in 0 until attachments.length()) {
            val a = attachments.getJSONObject(i)
            require(a.has("id") && a.has("transactionId") && a.has("fileName") && a.has("mimeType") && a.has("relativePath") && a.has("sizeBytes") && a.has("createdAt"))
            require(transactionIds.contains(a.getLong("transactionId"))) { "المرفق مرتبط بعملية غير موجودة." }
            safeZipPath(a.getString("relativePath"))
        }
    }

    private fun prepareAttachmentFiles(context: Context, tempDirectory: File, attachments: JSONArray): List<FileToInstall> = buildList {
        for (i in 0 until attachments.length()) {
            val a = attachments.getJSONObject(i)
            val relativePath = safeZipPath(a.getString("relativePath"))
            val source = File(tempDirectory, FILES_PREFIX + relativePath)
            require(source.isFile) { "ملف المرفق غير موجود داخل النسخة الاحتياطية: $relativePath" }
            require(source.length() == a.getLong("sizeBytes")) { "حجم المرفق لا يطابق البيانات المسجلة: ${a.getString("fileName")}" }
            add(FileToInstall(source, File(context.filesDir, relativePath)))
        }
    }

    private fun installAttachmentFiles(files: List<FileToInstall>) {
        files.forEach { item ->
            item.destination.parentFile?.mkdirs()
            item.source.inputStream().buffered().use { input -> item.destination.outputStream().buffered().use { output -> input.copyTo(output) } }
        }
    }

    private fun existingAttachmentPaths(db: SupportSQLiteDatabase): List<String> = buildList {
        db.query("SELECT relativePath FROM transaction_attachments").use { c -> while (c.moveToNext()) add(c.getString(0)) }
    }

    private fun backupExistingFiles(context: Context, paths: List<String>, backupDirectory: File) {
        paths.forEach { relativePath ->
            val source = File(context.filesDir, relativePath)
            if (source.isFile) { val backup = File(backupDirectory, safeZipPath(relativePath)); backup.parentFile?.mkdirs(); source.copyTo(backup, overwrite = true) }
        }
    }

    private fun restoreExistingFiles(context: Context, backupDirectory: File) {
        if (!backupDirectory.isDirectory) return
        backupDirectory.walkTopDown().filter { it.isFile }.forEach { backup ->
            val relative = backup.relativeTo(backupDirectory).path.replace(File.separatorChar, '/')
            val destination = File(context.filesDir, relative)
            destination.parentFile?.mkdirs()
            backup.copyTo(destination, overwrite = true)
        }
    }

    private fun restoreIntoDatabase(db: SupportSQLiteDatabase, backup: JSONObject) {
        val version = backup.optInt("formatVersion", LEGACY_FORMAT_VERSION)
        db.execSQL("DELETE FROM transactions")
        db.execSQL("DELETE FROM currency_accounts")
        db.execSQL("DELETE FROM people")
        val people = backup.getJSONArray("people")
        for (i in 0 until people.length()) { val p = people.getJSONObject(i); db.execSQL("INSERT INTO people (id,name,phone,address,notes,createdAt,isActive) VALUES (?,?,?,?,?,?,?)", arrayOf(p.getLong("id"),p.getString("name"),p.optString("phone"),p.optString("address"),p.optString("notes"),p.getLong("createdAt"),if(p.getBoolean("isActive"))1 else 0)) }
        val accounts = backup.getJSONArray("currencyAccounts")
        for (i in 0 until accounts.length()) { val a=accounts.getJSONObject(i); db.execSQL("INSERT INTO currency_accounts (id,personId,currencyCode,balanceMinor,createdAt,updatedAt) VALUES (?,?,?,?,?,?)",arrayOf(a.getLong("id"),a.getLong("personId"),a.getString("currencyCode"),a.getLong(3),a.getLong("createdAt"),a.getLong("updatedAt"))) }
        val transactions = backup.getJSONArray("transactions")
        for (i in 0 until transactions.length()) {
            val t=transactions.getJSONObject(i)
            db.execSQL("INSERT INTO transactions (id,accountId,type,amountMinor,description,transactionDate,createdAt,isArchived) VALUES (?,?,?,?,?,?,?,?)",arrayOf(t.getLong("id"),t.getLong("accountId"),t.getString("type"),t.getLong("amountMinor"),t.getString("description"),t.getLong("transactionDate"),t.getLong("createdAt"),if(version>=FORMAT_VERSION && t.optBoolean("isArchived",false))1 else 0))
        }
        if (version >= PREVIOUS_FORMAT_VERSION) {
            val attachments=backup.optJSONArray("attachments") ?: JSONArray()
            for(i in 0 until attachments.length()){val a=attachments.getJSONObject(i);db.execSQL("INSERT INTO transaction_attachments (id,transactionId,fileName,mimeType,relativePath,sizeBytes,createdAt) VALUES (?,?,?,?,?,?,?)",arrayOf(a.getLong("id"),a.getLong("transactionId"),a.getString("fileName"),a.getString("mimeType"),a.getString("relativePath"),a.getLong("sizeBytes"),a.getLong("createdAt")))}
        }
    }

    private fun safeZipPath(path: String): String {
        val normalized = path.replace('\\','/')
        require(normalized.isNotBlank() && !normalized.startsWith('/') && !normalized.contains("../") && normalized != ".." && !normalized.contains("/./") && !normalized.startsWith("./")) { "مسار ملف غير صالح داخل النسخة الاحتياطية." }
        return normalized
    }
}
