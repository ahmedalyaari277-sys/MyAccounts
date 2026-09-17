package com.myaccounts.app.util

import android.content.Context
import android.net.Uri

/**
 * Restore entry point for all three backup screens. The current scoped format
 * is tried first; legacy account and custody formats are then accepted so old
 * backups remain usable in their corresponding section.
 */
object CompatibleRestoreManager {
    suspend fun restore(context: Context, uri: Uri, scope: BackupScope): Result<Unit> {
        val scoped = ScopedBackupManager.restoreBackup(context, uri, scope)
        if (scoped.isSuccess) return Result.success(Unit)

        return when (scope) {
            BackupScope.ACCOUNTS -> restoreLegacyAccounts(context, uri, scoped)
            BackupScope.CUSTODY -> restoreLegacyCustody(context, uri, scoped)
            BackupScope.ALL -> restoreLegacyAll(context, uri, scoped)
        }
    }

    private suspend fun restoreLegacyAccounts(context: Context, uri: Uri, scoped: Result<Unit>): Result<Unit> =
        DatabaseBackupManager.restoreBackup(context, uri)
            .map { Unit }
            .recoverCatching { throw legacyFailure("الحسابات", scoped.exceptionOrNull(), it) }

    private suspend fun restoreLegacyCustody(context: Context, uri: Uri, scoped: Result<Unit>): Result<Unit> =
        CustodyBackupManager.restoreBackup(context, uri)
            .map { Unit }
            .recoverCatching { throw legacyFailure("العُهَد", scoped.exceptionOrNull(), it) }

    private suspend fun restoreLegacyAll(context: Context, uri: Uri, scoped: Result<Unit>): Result<Unit> {
        val accounts = DatabaseBackupManager.restoreBackup(context, uri)
        if (accounts.isSuccess) return Result.success(Unit)

        val custody = CustodyBackupManager.restoreBackup(context, uri)
        if (custody.isSuccess) return Result.success(Unit)

        return Result.failure(
            legacyFailure(
                "الحسابات والعُهَد",
                scoped.exceptionOrNull(),
                custody.exceptionOrNull() ?: accounts.exceptionOrNull()
            )
        )
    }

    private fun legacyFailure(scope: String, scopedError: Throwable?, legacyError: Throwable?): IllegalStateException {
        val detail = legacyError?.message?.takeIf { it.isNotBlank() }
            ?: scopedError?.message?.takeIf { it.isNotBlank() }
            ?: "الملف غير مدعوم"
        return IllegalStateException("تعذر استعادة نسخة $scope القديمة: $detail", legacyError ?: scopedError)
    }
}
