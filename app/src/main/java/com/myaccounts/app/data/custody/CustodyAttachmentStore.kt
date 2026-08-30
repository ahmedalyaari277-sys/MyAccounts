package com.myaccounts.app.data.custody

import android.content.Context
import com.myaccounts.app.util.CustodyAttachmentStorage

class CustodyAttachmentStore(context: Context) {
    private val appContext = context.applicationContext
    private val db = com.myaccounts.app.data.local.AppDatabase.getInstance(appContext)

    init {
        db.openHelper.writableDatabase.execSQL("CREATE TABLE IF NOT EXISTS custody_transaction_attachments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, transactionId INTEGER NOT NULL, fileName TEXT NOT NULL, mimeType TEXT NOT NULL, relativePath TEXT NOT NULL, sizeBytes INTEGER NOT NULL, createdAt INTEGER NOT NULL, FOREIGN KEY(transactionId) REFERENCES custody_transactions(id) ON DELETE CASCADE)")
        db.openHelper.writableDatabase.execSQL("CREATE INDEX IF NOT EXISTS index_custody_transaction_attachments_transactionId ON custody_transaction_attachments(transactionId)")
    }

    fun list(transactionId: Long): List<CustodyTransactionAttachmentEntity> {
        val result = mutableListOf<CustodyTransactionAttachmentEntity>()
        db.openHelper.readableDatabase.query("SELECT id,transactionId,fileName,mimeType,relativePath,sizeBytes,createdAt FROM custody_transaction_attachments WHERE transactionId=? ORDER BY id ASC", arrayOf(transactionId.toString())).use { c ->
            while (c.moveToNext()) result += CustodyTransactionAttachmentEntity(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4), c.getLong(5), c.getLong(6))
        }
        return result
    }

    fun save(transactionId: Long, selected: List<CustodyAttachmentStorage.Selected>): List<CustodyTransactionAttachmentEntity> {
        val saved = CustodyAttachmentStorage.saveAttachments(appContext, transactionId, selected)
        if (saved.isEmpty()) return saved
        db.openHelper.writableDatabase.beginTransaction()
        try {
            saved.forEach { a -> db.openHelper.writableDatabase.execSQL("INSERT INTO custody_transaction_attachments (transactionId,fileName,mimeType,relativePath,sizeBytes,createdAt) VALUES (?,?,?,?,?,?)", arrayOf(a.transactionId,a.fileName,a.mimeType,a.relativePath,a.sizeBytes,a.createdAt)) }
            db.openHelper.writableDatabase.setTransactionSuccessful()
        } catch (t: Throwable) {
            CustodyAttachmentStorage.deleteTransactionFiles(appContext, transactionId, saved)
            throw t
        } finally { db.openHelper.writableDatabase.endTransaction() }
        return saved
    }

    fun delete(a: CustodyTransactionAttachmentEntity) {
        db.openHelper.writableDatabase.execSQL("DELETE FROM custody_transaction_attachments WHERE id=?", arrayOf(a.id))
        CustodyAttachmentStorage.deleteFile(appContext, a)
    }

    fun deleteForTransaction(transactionId: Long) {
        val attachments = list(transactionId)
        db.openHelper.writableDatabase.execSQL("DELETE FROM custody_transaction_attachments WHERE transactionId=?", arrayOf(transactionId))
        CustodyAttachmentStorage.deleteTransactionFiles(appContext, transactionId, attachments)
    }
}
