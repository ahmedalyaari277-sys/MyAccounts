package com.myaccounts.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.UUID

@Entity(
    tableName = "people",
    indices = [
        Index(value = ["name"]),
        Index(value = ["phone"]),
        Index(value = ["isActive"]),
        Index(value = ["archivedAt"]),
        Index(value = ["externalId"], unique = true)
    ]
)
data class PersonEntity(
    @androidx.room.PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val archivedAt: Long? = null,
    val externalId: String = "P-${UUID.randomUUID()}"
)

@Entity(
    tableName = "currency_accounts",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["personId"]),
        Index(value = ["personId", "currencyCode"], unique = true)
    ]
)
data class CurrencyAccountEntity(
    @androidx.room.PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val personId: Long,
    val currencyCode: String,
    val balanceMinor: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
