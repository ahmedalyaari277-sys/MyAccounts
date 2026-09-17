package com.myaccounts.app.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.myaccounts.app.data.local.AppDatabase
import java.io.File

/** Unified import policy for Excel transfers. */
enum class TransferMode(val title: String) {
    REPLACE_ALL("استبدال الكل"),
    ADD_REMAINING("فحص وإضافة المتبقي")
}

object TransferModeManager {
    suspend fun importAccounts(context: Context, uri: Uri, mode: TransferMode): Result<ExcelDataManager.ImportSummary> =
        importWithScope(context, uri, BackupScope.ACCOUNTS, mode) { ExcelDataManager.import(context, uri).getOrThrow() }

    suspend fun importCustody(context: Context, uri: Uri, mode: TransferMode): Result<CustodyExcelDataManager.ImportSummary> =
        importWithScope(context, uri, BackupScope.CUSTODY, mode) { CustodyTwoSheetExcelDataManager.import(context, uri).getOrThrow() }

    suspend fun importGlobal(context: Context, uri: Uri, mode: TransferMode): Result<GlobalExcelDataManager.ImportSummary> =
        importWithScope(context, uri, BackupScope.ALL, mode) { GlobalExcelDataManager.import(context, uri).getOrThrow() }

    private suspend fun <T> importWithScope(context: Context, uri: Uri, scope: BackupScope, mode: TransferMode, importer: suspend () -> T): Result<T> = runCatching {
        if (mode == TransferMode.ADD_REMAINING) return@runCatching importer()
        val snapshot = File.createTempFile("myaccounts-transfer-snapshot-", ".myaccounts", context.cacheDir)
        val snapshotUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", snapshot)
        try {
            val snapshotResult = ScopedBackupManager.createBackup(context, snapshotUri, scope)
            val hadData = snapshotResult.isSuccess
            if (snapshotResult.isFailure && snapshotResult.exceptionOrNull()?.message != "لم يتم العثور على أي بيانات لحفظها في النسخة الاحتياطية.") snapshotResult.getOrThrow()
            clearScope(context, scope)
            try { importer() } catch (failure: Throwable) {
                if (hadData) {
                    val rollback = ScopedBackupManager.restoreBackup(context, snapshotUri, scope)
                    if (rollback.isFailure) throw IllegalStateException("فشل الاستيراد وفشلت محاولة التراجع: ${rollback.exceptionOrNull()?.message ?: "خطأ غير معروف"}", failure)
                }
                throw failure
            }
        } finally { snapshot.delete() }
    }

    private fun clearScope(context: Context, scope: BackupScope) {
        val db = AppDatabase.getInstance(context).openHelper.writableDatabase
        db.beginTransaction()
        try {
            when (scope) {
                BackupScope.ACCOUNTS -> listOf("transaction_attachments", "transactions", "currency_accounts", "people")
                BackupScope.CUSTODY -> listOf("custody_transaction_attachments", "custody_transactions", "custody_accounts", "custody_persons", "custodies")
                BackupScope.ALL -> listOf("custody_transaction_attachments", "custody_transactions", "custody_accounts", "custody_persons", "custodies", "transaction_attachments", "transactions", "currency_accounts", "people")
            }.forEach { db.execSQL("DELETE FROM \"$it\"") }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
