package com.myaccounts.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Durable snapshot of an archived transaction.
 *
 * It deliberately has no foreign keys to people/accounts/transactions so an
 * archived transaction survives permanent deletion of its original owner.
 */
@Entity(tableName = "archived_transaction_snapshots")
data class ArchivedTransactionSnapshotEntity(
    @PrimaryKey
    val transactionId: Long,
    val accountId: Long,
    val personId: Long,
    val personName: String,
    val personPhone: String,
    val personAddress: String,
    val personNotes: String,
    val currencyCode: String,
    val type: String,
    val amountMinor: Long,
    val description: String,
    val transactionDate: Long,
    val createdAt: Long,
    val archivedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "archived_transaction_attachment_snapshots")
data class ArchivedTransactionAttachmentSnapshotEntity(
    @PrimaryKey
    val attachmentId: Long,
    val transactionId: Long,
    val fileName: String,
    val mimeType: String,
    val relativePath: String,
    val sizeBytes: Long,
    val createdAt: Long
)
