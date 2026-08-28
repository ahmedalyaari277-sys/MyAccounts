package com.myaccounts.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

object ReportShareUtil {
    /**
     * Generates the requested report as part of the share action, copies it to a
     * private temporary cache file, opens Android's share sheet, and removes the
     * exported source file so sharing never depends on a previous user export.
     */
    fun shareGeneratedReport(
        context: Context,
        fileNamePrefix: String,
        mimeType: String,
        generate: () -> Result<String>
    ): Result<Unit> = try {
        val expectedExtension = extensionForMime(mimeType)
        val generated = generate()
        generated.getOrThrow()

        val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findLatestDownloadUri(context, fileNamePrefix, expectedExtension)
                ?: throw IllegalStateException("تعذر العثور على التقرير الذي تم إنشاؤه للمشاركة.")
        } else {
            findLatestLegacyFile(context, fileNamePrefix, expectedExtension)
                ?: throw IllegalStateException("تعذر العثور على التقرير الذي تم إنشاؤه للمشاركة.")
        }

        val shareDir = File(context.cacheDir, "report_share").apply {
            if (!exists() && !mkdirs()) throw IllegalStateException("تعذر إنشاء ملف المشاركة المؤقت.")
        }
        shareDir.listFiles()?.forEach { if (it.isFile) it.delete() }
        val shareFile = File(shareDir, "share_${System.currentTimeMillis()}$expectedExtension")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.openInputStream(source as Uri).use { input ->
                    if (input == null) throw IllegalStateException("تعذر فتح التقرير للمشاركة.")
                    shareFile.outputStream().use { output -> input.copyTo(output) }
                }
            } else {
                (source as File).inputStream().use { input ->
                    shareFile.outputStream().use { output -> input.copyTo(output) }
                }
            }

            if (!shareFile.isFile || shareFile.length() == 0L) throw IllegalStateException("تعذر تجهيز التقرير للمشاركة.")

            val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "مشاركة التقرير").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.delete(source as Uri, null, null)
            } else {
                (source as File).delete()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            shareFile.delete()
            throw e
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Kept for compatibility with older callers; new share actions must use shareGeneratedReport. */
    fun shareLatestReport(context: Context, fileNamePrefix: String, mimeType: String, launchChooser: Boolean = true): Result<Unit> = try {
        val expectedExtension = extensionForMime(mimeType)
        val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findLatestDownloadUri(context, fileNamePrefix, expectedExtension)
                ?: throw IllegalStateException("لم يتم العثور على ملف التقرير المطلوب.")
        } else {
            findLatestLegacyFile(context, fileNamePrefix, expectedExtension)
                ?.let { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
                ?: throw IllegalStateException("لم يتم العثور على ملف التقرير المطلوب.")
        }
        val shareFile = File(context.cacheDir, "report_share").apply { if (!exists()) mkdirs() }
            .resolve("share_${System.currentTimeMillis()}$expectedExtension")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.openInputStream(source as Uri).use { input ->
                    if (input == null) throw IllegalStateException("تعذر فتح ملف التقرير للمشاركة.")
                    shareFile.outputStream().use { output -> input.copyTo(output) }
                }
            } else {
                val sourceFile = findLatestLegacyFile(context, fileNamePrefix, expectedExtension)
                    ?: throw IllegalStateException("لم يتم العثور على ملف التقرير المطلوب.")
                sourceFile.inputStream().use { input -> shareFile.outputStream().use { output -> input.copyTo(output) } }
            }
            val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
            if (launchChooser) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "مشاركة التقرير").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            shareFile.delete()
            throw e
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun extensionForMime(mimeType: String): String = when (mimeType.lowercase()) {
        "application/pdf" -> ".pdf"
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> ".xlsx"
        else -> throw IllegalArgumentException("نوع ملف التقرير غير مدعوم للمشاركة.")
    }

    private fun candidatePrefixes(prefix: String): List<String> = buildList {
        add(prefix)
        val normalized = prefix.replace(" ", "_")
        if (normalized != prefix) add(normalized)
        if (normalized.contains("ملخص_تقرير_الأشخاص")) add(normalized.replace("ملخص_تقرير_الأشخاص", "ملخص_الأشخاص"))
    }.distinct()

    private fun findLatestDownloadUri(context: Context, prefix: String, extension: String): Uri? {
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.DATE_ADDED)
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("${Environment.DIRECTORY_DOWNLOADS}/MyAccounts%")
        val candidates = candidatePrefixes(prefix)
        var latestId: Long? = null
        var latestDate = Long.MIN_VALUE
        resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, "${MediaStore.Downloads.DATE_ADDED} DESC")?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_ADDED)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex) ?: continue
                if (!name.endsWith(extension, ignoreCase = true) || candidates.none { name.startsWith(it) }) continue
                val dateAdded = cursor.getLong(dateIndex)
                if (dateAdded >= latestDate) { latestDate = dateAdded; latestId = cursor.getLong(idIndex) }
            }
        }
        return latestId?.let { Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, it.toString()) }
    }

    private fun findLatestLegacyFile(context: Context, prefix: String, extension: String): File? {
        val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "MyAccounts")
        val candidates = candidatePrefixes(prefix)
        return directory.listFiles()?.filter { file -> file.isFile && file.name.endsWith(extension, ignoreCase = true) && candidates.any { file.name.startsWith(it) } }?.maxByOrNull { it.lastModified() }
    }
}
