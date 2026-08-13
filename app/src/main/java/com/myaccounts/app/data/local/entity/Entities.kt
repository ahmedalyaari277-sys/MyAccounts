package com.myaccounts.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CurrencyCode(
    val code: String
) {
    YER("YER"),
    SAR("SAR"),
    USD("USD");

    companion object {
        fun fromCode(value: String): CurrencyCode {
            return entries.firstOrNull { it.code == value }
                ?: throw IllegalArgumentException(
                    "Unsupported currency code: $value"
                )
        }
    }
}

enum class TransactionType {
    RECEIVABLE,
    PAYABLE
}

@Entity(
    tableName = "persons"
)
data class PersonEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    val phone: String = "",

    val address: String = "",

    val notes: String = "",

    val createdAt: Long = System.currentTimeMillis(),

    val isActive: Boolean = true
)

@Entity(
    tableName = "currency_accounts",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["personId"]),
        Index(value = ["currency"])
    ]
)
data class CurrencyAccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val personId: Long,

    val currency: CurrencyCode
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CurrencyAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["currencyAccountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["currencyAccountId"]),
        Index(value = ["transactionDate"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val currencyAccountId: Long,

    val type: TransactionType,

    val amountMinor: Long,

    val description: String = "",

    val transactionDate: Long,

    val createdAt: Long = System.currentTimeMillis()
)
