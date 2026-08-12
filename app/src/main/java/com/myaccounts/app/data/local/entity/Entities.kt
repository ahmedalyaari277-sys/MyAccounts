package com.myaccounts.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val createdAt: Long = System.currentTimeMillis()
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
    indices = [Index(value = ["personId"]), Index(value = ["currency"])]
)
data class CurrencyAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val currency: String // "YER", "SAR", "USD"
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
    indices = [Index(value = ["currencyAccountId"]), Index(value = ["transactionDate"])]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val currencyAccountId: Long,
    val type: String, // "RECEIVABLE" (لي) أو "PAYABLE" (علي)
    val amountMinor: Long,
    val description: String,
    val transactionDate: Long,
    val createdAt: Long = System.currentTimeMillis()
)
