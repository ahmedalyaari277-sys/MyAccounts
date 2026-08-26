package com.myaccounts.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.UUID

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CurrencyAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["transactionDate"]),
        Index(value = ["type"]),
        Index(value = ["externalId"], unique = true)
    ]
)
data class TransactionEntity(
    @androidx.room.PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    val type: TransactionType,
    val amountMinor: Long,
    val description: String = "",
    val transactionDate: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val externalId: String = "T-${UUID.randomUUID()}"
)

enum class TransactionType {
    RECEIVABLE,
    PAYABLE
}
