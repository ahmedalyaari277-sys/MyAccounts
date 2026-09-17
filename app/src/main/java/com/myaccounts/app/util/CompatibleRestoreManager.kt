package com.myaccounts.app.util

import android.content.Context
import android.net.Uri

/**
 * Restore entry point used by UI screens. It keeps the current scoped format
 * as the primary path and falls back to the legacy account/custody formats so
 * backups created by older app versions remain usable.
 */
object CompatibleRestoreManager {
    suspend fun restore(context: Context, uri: Uri, scope: BackupScope): Result<Unit> {
        val scoped = ScopedBackupManager.restoreBackup(context, uri, scope)
        if (scoped.isSuccess) return Result.success(Unit)

        return when (scope) {
            BackupScope.ACCOUNTS -> DatabaseBackupManager.restoreBackup(context, uri)
                .map { Unit }
                .recoverCatching { throw scoped.exceptionOrNull() ?: it }

            BackupScope.CUSTODY -> CustodyBackupManager.restoreBackup(context, uri)
                .map { Unit }
                .recoverCatching { throw scoped.exceptionOrNull() ?: it }

            BackupScope.ALL -> Result.failure(
                IllegalStateException(
                    "تعذر استعادة النسخة الحالية. تأكد أن الملف نسخة احتياطية كاملة صادرة من إصدار يدعم النسخ الاحتياطي الكامل.",
                    scoped.exceptionOrNull()
                )
            )
        }
    }
}
