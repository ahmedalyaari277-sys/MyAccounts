package com.myaccounts.app.data.custody

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.UUID

@Entity(tableName = "custodies", indices = [Index("name"), Index("organizationName"), Index(name = "index_custodies_externalId", value = ["externalId"], unique = true)])
data class CustodyEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val notes: String = "",
    val organizationName: String,
    val organizationPhone: String = "",
    val organizationAddress: String = "",
    val organizationNotes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false,
    val archivedAt: Long? = null,
    val externalId: String = "C-${UUID.randomUUID()}",
    val isClosed: Boolean = false,
    val closedAt: Long? = null,
    val settlementYerActualMinor: Long? = null,
    val settlementSarActualMinor: Long? = null,
    val settlementUsdActualMinor: Long? = null,
    val settlementNotes: String = ""
)

@Entity(tableName = "custody_persons", indices = [Index("custodyId"), Index("name"), Index(name = "index_custody_persons_custody_external", value = ["custodyId", "externalId"], unique = true)], foreignKeys = [ForeignKey(entity = CustodyEntity::class, parentColumns = ["id"], childColumns = ["custodyId"], onDelete = ForeignKey.CASCADE)])
data class CustodyPersonEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val custodyId: Long,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false,
    val archivedAt: Long? = null,
    val externalId: String = "CP-${UUID.randomUUID()}"
)

@Entity(tableName = "custody_accounts", indices = [Index("custodyId"), Index("personId"), Index(name = "index_custody_accounts_unique", value = ["custodyId", "holderType", "personId", "currencyCode"], unique = true)], foreignKeys = [ForeignKey(entity = CustodyEntity::class, parentColumns = ["id"], childColumns = ["custodyId"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = CustodyPersonEntity::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE)])
data class CustodyAccountEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val custodyId: Long,
    val holderType: String,
    val personId: Long? = null,
    val currencyCode: String,
    val balanceMinor: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "custody_transactions", indices = [Index("custodyId"), Index("accountId"), Index("personId"), Index("transactionDate"), Index("type"), Index(name = "index_custody_transactions_externalId", value = ["externalId"], unique = true)], foreignKeys = [ForeignKey(entity = CustodyEntity::class, parentColumns = ["id"], childColumns = ["custodyId"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = CustodyAccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = CustodyPersonEntity::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.SET_NULL)])
data class CustodyTransactionEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val custodyId: Long,
    val accountId: Long,
    val personId: Long? = null,
    val currencyCode: String,
    val type: String,
    val amountMinor: Long,
    val description: String = "",
    val transactionDate: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val externalId: String = "CT-${UUID.randomUUID()}",
    val isArchived: Boolean = false
)
