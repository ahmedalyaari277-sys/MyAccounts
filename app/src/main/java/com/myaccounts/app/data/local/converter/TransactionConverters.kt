package com.myaccounts.app.data.local.converter

import androidx.room.TypeConverter
import com.myaccounts.app.data.local.TransactionType

class TransactionConverters {

    @TypeConverter
    fun fromTransactionType(
        type: TransactionType
    ): String {
        return type.name
    }

    @TypeConverter
    fun toTransactionType(
        value: String
    ): TransactionType {
        return TransactionType.valueOf(value)
    }
}
