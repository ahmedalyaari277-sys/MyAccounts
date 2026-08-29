package com.myaccounts.app.data.custody

import android.content.Context
import com.myaccounts.app.util.CustodyAttachmentStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CustodyAttachmentStore(private val context: Context) {
    private val db by lazy { com.myaccounts.app.data.local.AppDatabase.getInstance(context) }

    init {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TABLE IF NOT EXISTS custody_transaction_attachments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, transactionId INTEGER NOT NULL, fileName TEXT NOT NULL, mimeType TEXT NOT NULL, relativePath TEXT NOT NULL, sizeBytes INTEGER NOT NULL, createdAt INTEGER NOT NULL, FOREIGN KEY(transactionId) REFERENCES custody_transactions(id) ON DELETE CASCADE)"
        )
        db.openHelper.writableDatabase.execSQL("CREATE INDEX IF NOT EXISTS index_custody_transaction_attachments_transactionId ON custody_transaction_attachments(transactionId)")
    }

    suspend fun save(transactionId: Long, selected: List<CustodyAttachmentStorage.Selected>): List<CustodyTransactionAttachmentEntity> = withContext(Dispatchers.IO) {
        val saved = CustodyAttachmentStorage.saveAttachments(context, transactionId, selected)
        if (saved.isNotEmpty()) {
            db.openHelper.writableDatabase.beginTransaction()
            try {
                saved.forEach { a ->
                    db.openHelper.writableDatabase.execSQL(
                        "INSERT INTO custody_transaction_attachments (transactionId,fileName,mimeType,relativePath,sizeBytes,createdAt) VALUES (?,?,?,?,?,?)",
                        arrayOf(a.transactionId, a.fileName, a.mimeType, a.relativePath, a.sizeBytes, a.createdAt)
                    )
                }
                db.openHelper.writableDatabase.setTransactionSuccessful()
            } finally { db.openHelper.writableDatabase.endTransaction() }
        }
        saved
    }

    fun list(transactionId: Long): List<CustodyTransactionAttachmentEntity> {
        val out = mutableListOf<CustodyTransactionAttachmentEntity>()
        db.openHelper.readableDatabase.query(
            "SELECT id,transactionId,fileName,mimeType,relativePath,sizeBytes,createdAt FROM custody_transaction_attachments WHERE transactionId=? ORDER BY id ASC",
            arrayOf(transactionId.toString())
        ).use { c -> while (c.moveToNext()) out += CustodyTransactionAttachmentEntity(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getString(4),c.getLong(5),c.getLong(6)) }
        return out
    }

    fun delete(attachment: CustodyTransactionAttachmentEntity) {
        db.openHelper.writableDatabase.execSQL("DELETE FROM custody_transaction_attachments WHERE id=?", arrayOf(attachment.id))
        CustodyAttachmentStorage.deleteFile(context, attachment)
    }

    fun deleteForTransaction(transactionId: Long) {
        val attachments = list(transactionId)
        db.openHelper.writableDatabase.execSQL("DELETE FROM custody_transaction_attachments WHERE transactionId=?", arrayOf(transactionId))
        CustodyAttachmentStorage.deleteTransactionFiles(context, transactionId, attachments)
    }
}
