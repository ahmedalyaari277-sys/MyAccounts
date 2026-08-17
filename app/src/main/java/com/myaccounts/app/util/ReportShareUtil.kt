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
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findLatestDownloadUri(context, fileNamePrefix)
        } else {
            findLatestLegacyFile(context, fileNamePrefix)?.let {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
            }
        } ?: throw IllegalStateException("لم يتم العثور على ملف التقرير. قم بتصدير التقرير أولاً.")

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "مشاركة التقرير")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        Result.success(Unit)
    } catch (exception: Exception) {
        Result.failure(exception)
    }

    private fun findLatestDownloadUri(context: Context, prefix: String): Uri? {
        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.DATE_ADDED
        )
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf(
            "${Environment.DIRECTORY_DOWNLOADS}/MyAccounts%",
            "$prefix%"
        )
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Downloads.DATE_ADDED} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                return ContentUrisCompat.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            }
        }
        return null
    }

    private fun findLatestLegacyFile(context: Context, prefix: String): File? {
        val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "MyAccounts")
        return directory.listFiles()
            ?.filter { it.isFile && it.name.startsWith(prefix) }
            ?.maxByOrNull { it.lastModified() }
    }

    private object ContentUrisCompat {
        fun withAppendedId(baseUri: Uri, id: Long): Uri = Uri.withAppendedPath(baseUri, id.toString())
    }
}
