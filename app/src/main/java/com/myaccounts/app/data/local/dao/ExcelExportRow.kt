package com.myaccounts.app.data.local.dao

import androidx.room.ColumnInfo

/** One active person/account/transaction row for the one-sheet Excel exchange format. */
data class ExcelExportRow(
    @ColumnInfo(name = "personExternalId") val personExternalId: String,
    @ColumnInfo(name = "transactionExternalId") val transactionExternalId: String?,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "phone") val phone: String,
    @ColumnInfo(name = "address") val address: String,
    @ColumnInfo(name = "notes") val notes: String,
    @ColumnInfo(name = "currencyCode") val currencyCode: String,
    @ColumnInfo(name = "transactionType") val transactionType: String?,
    @ColumnInfo(name = "amountMinor") val amountMinor: Long?,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "transactionDate") val transactionDate: Long?
)
