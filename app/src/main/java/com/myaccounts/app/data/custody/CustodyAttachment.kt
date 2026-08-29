package com.myaccounts.app.data.custody

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "custody_transaction_attachments",
    indices = [Index("transactionId")],
    foreignKeys = [
        ForeignKey(
            entity = CustodyTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CustodyTransactionAttachmentEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val fileName: String,
    val mimeType: String,
    val relativePath: String,
    val sizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis()
)
