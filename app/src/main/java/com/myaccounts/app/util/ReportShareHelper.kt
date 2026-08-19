package com.myaccounts.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ReportShareHelper {
    fun shareFile(context: Context, file: File, mimeType: String, title: String = "مشاركة التقرير") {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        shareUri(context, uri, mimeType, title)
    }

    fun shareUri(context: Context, uri: Uri, mimeType: String, title: String = "مشاركة التقرير") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
}
