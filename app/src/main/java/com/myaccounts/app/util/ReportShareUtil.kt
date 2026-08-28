package com.myaccounts.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

object ReportShareUtil {
    suspend fun shareLatestReport(context: Context, fileNamePrefix: String, mimeType: String): Result<Unit> = try {
        val expectedExtension = extensionForMime(mimeType)
        val source = awaitLatestSource(context, fileNamePrefix, expectedExtension)
            ?: throw IllegalStateException("تعذر إنشاء ملف التقرير للمشاركة.")
        val shareFile = File(context.cacheDir, "report_share").apply { if (!exists()) mkdirs() }
            .resolve("share_${System.currentTimeMillis()}$expectedExtension")
        try {
            when (source) {
                is Source.MediaStoreSource -> {
                    context.contentResolver.openInputStream(source.uri).use { input ->
                        if (input == null) throw IllegalStateException("تعذر فتح ملف التقرير للمشاركة.")
                        shareFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    context.contentResolver.delete(source.uri, null, null)
                }
                is Source.LegacyFileSource -> {
                    source.file.inputStream().use { input -> shareFile.outputStream().use { output -> input.copyTo(output) } }
                    source.file.delete()
                }
            }
            val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
            withContext(Dispatchers.Main) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "مشاركة التقرير").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            Result.success(Unit)
        } catch (exception: Exception) {
            shareFile.delete()
            throw exception
        }
    } catch (exception: Exception) {
        Result.failure(exception)
    }

    private sealed interface Source {
        data class MediaStoreSource(val uri: Uri) : Source
        data class LegacyFileSource(val file: File) : Source
    }

    private suspend fun awaitLatestSource(context: Context, prefix: String, extension: String): Source? {
        delay(500)
        repeat(30) {
            val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                findLatestDownloadUri(context, prefix, extension)?.let(Source::MediaStoreSource)
            } else {
                findLatestLegacyFile(context, prefix, extension)?.let(Source::LegacyFileSource)
            }
            if (source != null) return source
            delay(250)
        }
        return null
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
        var latestId: Long? = null
        var latestDate = Long.MIN_VALUE
        val candidates = candidatePrefixes(prefix)
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
