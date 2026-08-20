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
    fun shareLatestReport(context: Context, fileNamePrefix: String, mimeType: String): Result<Unit> = try {
        val expectedExtension = extensionForMime(mimeType)
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findLatestDownloadUri(context, fileNamePrefix, expectedExtension)
        } else {
            findLatestLegacyFile(context, fileNamePrefix, expectedExtension)?.let {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
            }
        } ?: throw IllegalStateException("لم يتم العثور على ملف التقرير المطلوب. قم بتصدير التقرير أولاً.")

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "مشاركة التقرير")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        Result.success(Unit)
    } catch (exception: Exception) {
        Result.failure(exception)
    }

    private fun extensionForMime(mimeType: String): String = when (mimeType.lowercase()) {
        "application/pdf" -> ".pdf"
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> ".xlsx"
        else -> throw IllegalArgumentException("نوع ملف التقرير غير مدعوم للمشاركة.")
    }

    /**
     * Exporters use two filename conventions:
     * - general reports keep the Arabic title in the filename;
     * - multi-currency reports sanitize Arabic/person names to underscores.
     *
     * Sharing must therefore search for both forms. This keeps one sharing
     * implementation for all report screens instead of duplicating filename logic.
     */
    private fun candidatePrefixes(prefix: String): List<String> = buildList {
        add(prefix)
        val normalized = prefix.replace(" ", "_")
        if (normalized != prefix) add(normalized)

        val sanitized = sanitizeForFilename(prefix)
        if (sanitized != prefix) add(sanitized)

        val sanitizedNormalized = sanitizeForFilename(normalized)
        if (sanitizedNormalized != prefix && sanitizedNormalized != sanitized) {
            add(sanitizedNormalized)
        }

        // Compatibility with report names produced by earlier versions.
        when (normalized) {
            "MyAccounts_التقرير_العام" -> add("MyAccounts_التقرير_التفصيلي")
            "MyAccounts_أرصدة_الحسابات" -> add("MyAccounts_ملخص_الأشخاص")
            "MyAccounts_ملخص_تقرير_الأشخاص" -> add("MyAccounts_ملخص_الأشخاص")
        }
    }.distinct()

    private fun sanitizeForFilename(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun findLatestDownloadUri(context: Context, prefix: String, extension: String): Uri? {
        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.DATE_ADDED
        )
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("${Environment.DIRECTORY_DOWNLOADS}/MyAccounts%")
        var latestId: Long? = null
        var latestDate = Long.MIN_VALUE
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
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_ADDED)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex) ?: continue
                if (!name.endsWith(extension, ignoreCase = true) || candidates.none { name.startsWith(it) }) continue
                val dateAdded = cursor.getLong(dateIndex)
                if (dateAdded >= latestDate) {
                    latestDate = dateAdded
                    latestId = cursor.getLong(idIndex)
                }
            }
        }
        return latestId?.let {
            Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, it.toString())
        }
    }

    private fun findLatestLegacyFile(context: Context, prefix: String, extension: String): File? {
        val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "MyAccounts")
        val candidates = candidatePrefixes(prefix)
        return directory.listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.name.endsWith(extension, ignoreCase = true) &&
                    candidates.any { file.name.startsWith(it) }
            }
            ?.maxByOrNull { it.lastModified() }
    }
}
