package com.myaccounts.app.util

import android.content.Context
import android.net.Uri

/**
 * Compatibility facade for the custody-only Excel transfer.
 *
 * CustodyExcelDataManager is the single source of truth for the custody
 * workbook format. It currently exports/imports one sheet with 21 columns,
 * including the native "التصنيف" column. This facade intentionally delegates
 * directly to it so the custody screen and global Excel transfer use exactly
 * the same format and validation rules.
 */
object CustodyTwoSheetExcelDataManager {
    const val MIME_TYPE = CustodyExcelDataManager.MIME_TYPE
    const val SUGGESTED_FILE_NAME = CustodyExcelDataManager.SUGGESTED_FILE_NAME

    suspend fun exportActive(
        context: Context,
        uri: Uri
    ): Result<CustodyExcelDataManager.ExportSummary> =
        CustodyExcelDataManager.exportActive(context, uri)

    fun previewImport(
        context: Context,
        uri: Uri
    ): Result<CustodyExcelDataManager.ImportPreview> =
        CustodyExcelDataManager.previewImport(context, uri)

    suspend fun import(
        context: Context,
        uri: Uri
    ): Result<CustodyExcelDataManager.ImportSummary> =
        CustodyExcelDataManager.import(context, uri)
}
