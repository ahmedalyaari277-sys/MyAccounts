package com.myaccounts.app.util

import android.content.Context
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
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
import android.util.Base64

/**
 * Backup/restore used by the global and section-specific backup actions.
 * It deliberately works at the SQLite table level so newly added columns are
 * carried automatically without changing Room entities or financial logic.
 */
enum class BackupScope(val key: String, val title: String, val tables: List<String>, val attachmentTables: List<String>) {
    ALL(
        "all", "الحسابات والعُهَد بالكامل",
        listOf("people", "currency_accounts", "transactions", "transaction_attachments", "custodies", "custody_persons", "custody_accounts", "custody_transactions", "custody_transaction_attachments"),
        listOf("transaction_attachments", "custody_transaction_attachments")
    ),
    ACCOUNTS(
        "accounts", "الحسابات فقط",
        listOf("people", "currency_accounts", "transactions", "transaction_attachments"),
        listOf("transaction_attachments")
    ),
    CUSTODY(
        "custody", "العُهَد فقط",
        listOf("custodies", "custody_persons", "custody_accounts", "custody_transactions", "custody_transaction_attachments"),
        listOf("custody_transaction_attachments")
    )
}

object ScopedBackupManager {
    private const val BACKUP_TYPE = "myaccounts_scoped_backup"
    private const val FORMAT_VERSION = 1
    private const val DATABASE_ENTRY = "backup.json"
    private const val FILES_PREFIX = "files/"

    private const val TYPE_NULL = "null"
    private const val TYPE_INTEGER = "integer"
    private const val TYPE_FLOAT = "float"
    private const val TYPE_TEXT = "text"
    private const val TYPE_BLOB = "blob"

    fun suggestedFileName(scope: BackupScope): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "myaccounts_${scope.key}_backup_$timestamp.myaccounts"
    }

    suspend fun createBackup(context: Context, uri: Uri, scope: BackupScope): Result<Unit> = runCatching {
        val db = AppDatabase.getInstance(context).openHelper.readableDatabase
        val backup = buildBackup(context, db, scope)
        context.contentResolver.openOutputStream(uri)?.use { output ->
            ZipOutputStream(output.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(DATABASE_ENTRY))
                zip.write(backup.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                addAttachmentFiles(context, db, scope, zip)
            }
        } ?: error("تعذر فتح ملف النسخة الاحتياطية للكتابة.")
        val rowCount = backup.getJSONArray("tables").let { tables ->
            var count = 0
            for (i in 0 until tables.length()) count += tables.getJSONObject(i).getJSONArray("rows").length()
            count
        }
        require(rowCount > 0) { "لم يتم العثور على أي بيانات لحفظها في النسخة الاحتياطية." }
    }

    suspend fun restoreBackup(context: Context, uri: Uri, scope: BackupScope): Result<Unit> = runCatching {
        val source = File.createTempFile("myaccounts_scoped_backup_", ".tmp", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            source.outputStream().buffered().use { output -> input.copyTo(output) }
        } ?: error("تعذر فتح ملف النسخة الاحتياطية.")
        require(source.length() > 0L) { "ملف النسخة الاحتياطية فارغ." }

        val temp = File(context.cacheDir, "scoped_restore_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            if (isZip(source)) extractZip(source, temp) else source.copyTo(File(temp, DATABASE_ENTRY), overwrite = true)
            val jsonFile = File(temp, DATABASE_ENTRY)
            require(jsonFile.isFile) { "النسخة الاحتياطية لا تحتوي على بيانات قاعدة البيانات." }
            val backup = JSONObject(jsonFile.readText(Charsets.UTF_8))
            validate(backup, scope)
            val dbHolder = AppDatabase.getInstance(context)
            val db = dbHolder.openHelper.writableDatabase
            val oldFiles = File(temp, "old-files").apply { mkdirs() }
            val oldPaths = existingAttachmentPaths(db, scope)
            backupExistingFiles(context, oldPaths, oldFiles)
            val files = prepareFiles(context, temp, backup, scope)
            try {
                installFiles(files)
                restoreTables(db, backup, scope)
                dbHolder.invalidationTracker.refreshVersionsAsync()
                oldPaths.filter { path -> files.none { it.destination.relativeTo(context.filesDir).path.replace(File.separatorChar, '/') == path } }
                    .forEach { File(context.filesDir, safePath(it)).delete() }
            } catch (t: Throwable) {
                files.forEach { it.destination.delete() }
                restoreExistingFiles(context, oldFiles)
                throw t
            }
        } finally {
            temp.deleteRecursively()
            source.delete()
        }
    }

    private fun buildBackup(context: Context, db: SupportSQLiteDatabase, scope: BackupScope): JSONObject {
        db.beginTransactionNonExclusive()
        return try {
            val root = JSONObject()
                .put("backupType", BACKUP_TYPE)
                .put("formatVersion", FORMAT_VERSION)
                .put("scope", scope.key)
                .put("createdAt", System.currentTimeMillis())
            val tables = JSONArray()
            scope.tables.forEach { tables.put(dumpTable(db, it)) }
            root.put("tables", tables)
            db.setTransactionSuccessful()
            root
        } finally {
            db.endTransaction()
        }
    }

    private fun dumpTable(db: SupportSQLiteDatabase, table: String): JSONObject {
        val columns = mutableListOf<String>()
        db.query("PRAGMA table_info(${quote(table)})").use { c ->
            while (c.moveToNext()) columns += c.getString(c.getColumnIndexOrThrow("name"))
        }
        require(columns.isNotEmpty()) { "جدول غير موجود في قاعدة البيانات: $table" }
        val rows = JSONArray()
        db.query("SELECT * FROM ${quote(table)} ORDER BY rowid").use { c ->
            while (c.moveToNext()) {
                val row = JSONArray()
                for (i in 0 until c.columnCount) row.put(encode(c, i))
                rows.put(row)
            }
        }
        return JSONObject().put("name", table).put("columns", JSONArray(columns)).put("rows", rows)
    }

    private fun addAttachmentFiles(context: Context, db: SupportSQLiteDatabase, scope: BackupScope, zip: ZipOutputStream) {
        scope.attachmentTables.forEach { table ->
            db.query("SELECT relativePath FROM ${quote(table)} ORDER BY id").use { c ->
                while (c.moveToNext()) {
                    val path = safePath(c.getString(0))
                    val file = File(context.filesDir, path)
                    require(file.isFile) { "ملف المرفق غير موجود: $path" }
                    zip.putNextEntry(ZipEntry(FILES_PREFIX + path))
                    file.inputStream().buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    private fun validate(backup: JSONObject, scope: BackupScope) {
        require(backup.optString("backupType") == BACKUP_TYPE) { "ملف النسخة الاحتياطية غير صالح." }
        require(backup.optInt("formatVersion", -1) == FORMAT_VERSION) { "إصدار النسخة الاحتياطية غير مدعوم." }
        require(backup.optString("scope") == scope.key) { "هذه النسخة تخص ${backup.optString("scope")} وليست ${scope.key}." }
        val tables = backup.optJSONArray("tables") ?: error("لا توجد جداول في النسخة الاحتياطية.")
        val names = mutableSetOf<String>()
        for (i in 0 until tables.length()) {
            val table = tables.getJSONObject(i)
            val name = table.getString("name")
            require(name in scope.tables) { "النسخة تحتوي على جدول خارج النطاق: $name" }
            require(names.add(name)) { "الجدول مكرر: $name" }
            require(table.has("columns") && table.has("rows")) { "بيانات الجدول $name غير مكتملة." }
        }
        scope.tables.forEach { required -> require(required in names) { "الجدول $required غير موجود في النسخة." } }
    }

    private fun restoreTables(db: SupportSQLiteDatabase, backup: JSONObject, scope: BackupScope) {
        val definitions = backup.getJSONArray("tables").let { array ->
            buildMap<String, JSONObject> { for (i in 0 until array.length()) put(array.getJSONObject(i).getString("name"), array.getJSONObject(i)) }
        }
        val deleteOrder = when (scope) {
            BackupScope.ACCOUNTS -> listOf("transaction_attachments", "transactions", "currency_accounts", "people")
            BackupScope.CUSTODY -> listOf("custody_transaction_attachments", "custody_transactions", "custody_accounts", "custody_persons", "custodies")
            BackupScope.ALL -> listOf("custody_transaction_attachments", "custody_transactions", "custody_accounts", "custody_persons", "custodies", "transaction_attachments", "transactions", "currency_accounts", "people")
        }
        val insertOrder = when (scope) {
            BackupScope.ACCOUNTS -> listOf("people", "currency_accounts", "transactions", "transaction_attachments")
            BackupScope.CUSTODY -> listOf("custodies", "custody_persons", "custody_accounts", "custody_transactions", "custody_transaction_attachments")
            BackupScope.ALL -> listOf("people", "currency_accounts", "transactions", "transaction_attachments", "custodies", "custody_persons", "custody_accounts", "custody_transactions", "custody_transaction_attachments")
        }

        db.execSQL("PRAGMA foreign_keys=OFF")
        try {
            db.beginTransaction()
            deleteOrder.forEach { db.delete(it, null, null) }
            insertOrder.forEach { insertTable(db, definitions.getValue(it)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.execSQL("PRAGMA foreign_keys=ON")
        }
    }

    private fun insertTable(db: SupportSQLiteDatabase, definition: JSONObject) {
        val name = definition.getString("name")
        val columnsJson = definition.getJSONArray("columns")
        val columns = List(columnsJson.length()) { columnsJson.getString(it) }
        if (columns.isEmpty()) return
        val placeholders = columns.joinToString(",") { "?" }
        val sql = "INSERT INTO ${quote(name)} (${columns.joinToString(",") { quote(it) }}) VALUES ($placeholders)"
        val rows = definition.getJSONArray("rows")
        for (r in 0 until rows.length()) {
            val row = rows.getJSONArray(r)
            require(row.length() == columns.size) { "عدد أعمدة الصف لا يطابق الجدول $name." }
            val statement: SupportSQLiteStatement = db.compileStatement(sql)
            try {
                for (i in columns.indices) bind(statement, i + 1, row.get(i))
                statement.executeInsert()
            } finally {
                statement.close()
            }
        }
    }

    private fun encode(cursor: android.database.Cursor, index: Int): Any = when (cursor.getType(index)) {
        android.database.Cursor.FIELD_TYPE_NULL -> JSONObject().put("type", TYPE_NULL)
        android.database.Cursor.FIELD_TYPE_INTEGER -> JSONObject().put("type", TYPE_INTEGER).put("value", cursor.getLong(index))
        android.database.Cursor.FIELD_TYPE_FLOAT -> JSONObject().put("type", TYPE_FLOAT).put("value", cursor.getDouble(index))
        android.database.Cursor.FIELD_TYPE_BLOB -> JSONObject().put("type", TYPE_BLOB).put("value", Base64.encodeToString(cursor.getBlob(index), Base64.NO_WRAP))
        else -> JSONObject().put("type", TYPE_TEXT).put("value", cursor.getString(index))
    }

    private fun bind(statement: SupportSQLiteStatement, index: Int, value: Any) {
        val item = value as JSONObject
        when (item.getString("type")) {
            TYPE_NULL -> statement.bindNull(index)
            TYPE_INTEGER -> statement.bindLong(index, item.getLong("value"))
            TYPE_FLOAT -> statement.bindDouble(index, item.getDouble("value"))
            TYPE_BLOB -> statement.bindBlob(index, Base64.decode(item.getString("value"), Base64.NO_WRAP))
            TYPE_TEXT -> statement.bindString(index, item.getString("value"))
            else -> error("نوع بيانات غير مدعوم.")
        }
    }

    private fun prepareFiles(context: Context, temp: File, backup: JSONObject, scope: BackupScope): List<FileToInstall> {
        val result = mutableListOf<FileToInstall>()
        val tables = backup.getJSONArray("tables")
        for (i in 0 until tables.length()) {
            val definition = tables.getJSONObject(i)
            if (definition.getString("name") !in scope.attachmentTables) continue
            val columns = definition.getJSONArray("columns")
            val pathIndex = (0 until columns.length()).firstOrNull { columns.getString(it) == "relativePath" } ?: continue
            val sizeIndex = (0 until columns.length()).firstOrNull { columns.getString(it) == "sizeBytes" } ?: continue
            val rows = definition.getJSONArray("rows")
            for (r in 0 until rows.length()) {
                val row = rows.getJSONArray(r)
                val path = (row.get(pathIndex) as JSONObject).getString("value")
                val size = (row.get(sizeIndex) as JSONObject).getLong("value")
                val safe = safePath(path)
                val source = File(temp, FILES_PREFIX + safe)
                require(source.isFile) { "ملف المرفق غير موجود داخل النسخة الاحتياطية: $safe" }
                require(source.length() == size) { "حجم المرفق لا يطابق البيانات المسجلة: $safe" }
                result += FileToInstall(source, File(context.filesDir, safe))
            }
        }
        return result
    }

    private fun existingAttachmentPaths(db: SupportSQLiteDatabase, scope: BackupScope): List<String> = buildList {
        scope.attachmentTables.forEach { table ->
            db.query("SELECT relativePath FROM ${quote(table)}").use { c -> while (c.moveToNext()) add(c.getString(0)) }
        }
    }

    private fun backupExistingFiles(context: Context, paths: List<String>, directory: File) {
        paths.forEach { path ->
            val source = File(context.filesDir, safePath(path))
            if (source.isFile) {
                val target = File(directory, safePath(path))
                target.parentFile?.mkdirs()
                source.copyTo(target, overwrite = true)
            }
        }
    }

    private fun restoreExistingFiles(context: Context, directory: File) {
        if (!directory.isDirectory) return
        directory.walkTopDown().filter { it.isFile }.forEach { file ->
            val path = file.relativeTo(directory).path.replace(File.separatorChar, '/')
            val target = File(context.filesDir, safePath(path))
            target.parentFile?.mkdirs()
            file.copyTo(target, overwrite = true)
        }
    }

    private fun installFiles(files: List<FileToInstall>) {
        files.forEach { file ->
            file.destination.parentFile?.mkdirs()
            file.source.inputStream().buffered().use { input -> file.destination.outputStream().buffered().use { output -> input.copyTo(output) } }
        }
    }

    private fun isZip(file: File): Boolean = file.inputStream().buffered().use { input -> input.read() == 'P'.code && input.read() == 'K'.code }

    private fun extractZip(source: File, temp: File) {
        source.inputStream().buffered().use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    require(!entry.isDirectory) { "ملف النسخة الاحتياطية يحتوي على مجلدات غير مدعومة." }
                    val path = safePath(entry.name)
                    val destination = File(temp, path)
                    require(destination.canonicalPath.startsWith(temp.canonicalPath + File.separator)) { "مسار ملف غير صالح." }
                    destination.parentFile?.mkdirs()
                    destination.outputStream().use { output -> zip.copyTo(output) }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }

    private fun safePath(path: String): String {
        val normalized = path.replace('\\', '/').trimStart('/')
        require(normalized.isNotBlank() && !normalized.split('/').contains("..") && !normalized.split('/').contains(".")) { "مسار ملف غير صالح." }
        return normalized
    }

    private fun quote(identifier: String): String = "\"${identifier.replace("\"", "\"\"")}\""

    private data class FileToInstall(val source: File, val destination: File)
}
