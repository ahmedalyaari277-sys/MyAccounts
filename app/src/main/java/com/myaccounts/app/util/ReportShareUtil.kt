package com.myaccounts.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ReportShareUtil {
    suspend fun shareGeneratedReport(
        context: Context,
        fileNamePrefix: String,
        mimeType: String,
        launchChooser: Boolean = true,
        generate: () -> Result<String>
    ): Result<Unit> = try {
        val expectedExtension = extensionForMime(mimeType)
        val generatedAt = System.currentTimeMillis()
        val beforeSources = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            downloadIds(context, expectedExtension)
        } else {
            emptySet()
        }

        generate().getOrThrow()

        val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findNewDownloadUri(context, expectedExtension, generatedAt, beforeSources)
                ?: throw IllegalStateException("تعذر الوصول إلى التقرير الذي تم إنشاؤه للمشاركة.")
        } else {
            findNewLegacyFile(context, expectedExtension, generatedAt)
                ?: throw IllegalStateException("تعذر الوصول إلى التقرير الذي تم إنشاؤه للمشاركة.")
        }

        val shareDir = File(context.cacheDir, "report_share").apply {
            if (!exists() && !mkdirs()) {
                throw IllegalStateException("تعذر إنشاء ملف المشاركة المؤقت.")
            }
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

            if (!shareFile.isFile || shareFile.length() == 0L) {
                throw IllegalStateException("تعذر تجهيز التقرير للمشاركة.")
            }

            if (launchChooser) {
                val shareUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    shareFile
                )
                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, shareUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(intent, "مشاركة التقرير")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }

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

    suspend fun shareLatestReport(
        context: Context,
        fileNamePrefix: String,
        mimeType: String,
        launchChooser: Boolean = true
    ): Result<Unit> = try {
        val expectedExtension = extensionForMime(mimeType)
        val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findLatestDownloadUri(context, fileNamePrefix, expectedExtension)
                ?: throw IllegalStateException("لم يتم العثور على ملف التقرير المطلوب.")
        } else {
            findLatestLegacyFile(context, fileNamePrefix, expectedExtension)
                ?: throw IllegalStateException("لم يتم العثور على ملف التقرير المطلوب.")
        }
        val shareFile = File(context.cacheDir, "report_share")
            .apply { if (!exists()) mkdirs() }
            .resolve("share_${System.currentTimeMillis()}$expectedExtension")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.openInputStream(source as Uri).use { input ->
                    if (input == null) throw IllegalStateException("تعذر فتح ملف التقرير للمشاركة.")
                    shareFile.outputStream().use { output -> input.copyTo(output) }
                }
            } else {
                (source as File).inputStream().use { input ->
                    shareFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (!shareFile.isFile || shareFile.length() == 0L) {
                throw IllegalStateException("تعذر تجهيز التقرير للمشاركة.")
            }
            val shareUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                shareFile
            )
            if (launchChooser) {
                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, shareUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(intent, "مشاركة التقرير")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
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

    private fun downloadIds(context: Context, extension: String): Set<Long> {
        val resolver = context.contentResolver
        val ids = mutableSetOf<Long>()
        val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME)
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("${Environment.DIRECTORY_DOWNLOADS}/MyAccounts%")
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex) ?: continue
                if (name.endsWith(extension, ignoreCase = true)) {
                    ids += cursor.getLong(idIndex)
                }
            }
        }
        return ids
    }

    private fun findNewDownloadUri(
        context: Context,
        extension: String,
        generatedAtMillis: Long,
        beforeIds: Set<Long>
    ): Uri? {
        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.DATE_ADDED
        )
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("${Environment.DIRECTORY_DOWNLOADS}/MyAccounts%")
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Downloads.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val name = cursor.getString(nameIndex) ?: continue
                val dateAddedMillis = cursor.getLong(dateIndex) * 1000L
                if (id !in beforeIds &&
                    name.endsWith(extension, ignoreCase = true) &&
                    dateAddedMillis >= generatedAtMillis - 1000L
                ) {
                    return Uri.withAppendedPath(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                }
            }
        }
        return null
    }

    private fun findNewLegacyFile(
        context: Context,
        extension: String,
        generatedAtMillis: Long
    ): File? {
        val directory = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "MyAccounts"
        )
        return directory.listFiles()
            ?.filter {
                it.isFile &&
                    it.name.endsWith(extension, ignoreCase = true) &&
                    it.lastModified() >= generatedAtMillis - 1000L
            }
            ?.maxByOrNull { it.lastModified() }
    }

    private fun findLatestDownloadUri(
        context: Context,
        prefix: String,
        extension: String
    ): Uri? {
        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME
        )
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("${Environment.DIRECTORY_DOWNLOADS}/MyAccounts%")
        val candidates = candidatePrefixes(prefix)
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Downloads.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex) ?: continue
                if (name.endsWith(extension, ignoreCase = true) && candidates.any { name.startsWith(it) }) {
                    return Uri.withAppendedPath(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idIndex).toString()
                    )
                }
            }
        }
        return null
    }

    private fun findLatestLegacyFile(
        context: Context,
        prefix: String,
        extension: String
    ): File? {
        val directory = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "MyAccounts"
        )
        val candidates = candidatePrefixes(prefix)
        return directory.listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.name.endsWith(extension, ignoreCase = true) &&
                    candidates.any { file.name.startsWith(it) }
            }
            ?.maxByOrNull { it.lastModified() }
    }

    private fun candidatePrefixes(prefix: String): List<String> = buildList {
        add(prefix)
        val normalized = prefix.replace(" ", "_")
        if (normalized != prefix) add(normalized)
        if (normalized.contains("ملخص_تقرير_الأشخاص")) {
            add(normalized.replace("ملخص_تقرير_الأشخاص", "ملخص_الأشخاص"))
        }
    }.distinct()
}
