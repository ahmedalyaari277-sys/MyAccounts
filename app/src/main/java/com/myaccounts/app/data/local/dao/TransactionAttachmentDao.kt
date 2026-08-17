package com.myaccounts.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.myaccounts.app.data.local.TransactionAttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionAttachmentDao {

    @Insert
    suspend fun insertAttachments(
        attachments: List<TransactionAttachmentEntity>
    ): List<Long>

    @Query(
        """
        SELECT *
        FROM transaction_attachments
        WHERE transactionId = :transactionId
        ORDER BY createdAt ASC, id ASC
        """
    )
    fun observeAttachments(
        transactionId: Long
    ): Flow<List<TransactionAttachmentEntity>>

    @Query(
        """
        SELECT *
        FROM transaction_attachments
        WHERE transactionId = :transactionId
        ORDER BY createdAt ASC, id ASC
        """
    )
    suspend fun getAttachments(
        transactionId: Long
    ): List<TransactionAttachmentEntity>

    @Query(
        """
        SELECT COUNT(*)
        FROM transaction_attachments
        WHERE transactionId = :transactionId
        """
    )
    fun observeAttachmentCount(
        transactionId: Long
    ): Flow<Int>

    @Query(
        """
        SELECT *
        FROM transaction_attachments
        WHERE id = :attachmentId
        LIMIT 1
        """
    )
    suspend fun getAttachment(
        attachmentId: Long
    ): TransactionAttachmentEntity?

    @Delete
    suspend fun deleteAttachment(
        attachment: TransactionAttachmentEntity
    )

    @Query(
        """
        DELETE FROM transaction_attachments
        WHERE transactionId = :transactionId
        """
    )
    suspend fun deleteAttachmentsForTransaction(
        transactionId: Long
    )
}
