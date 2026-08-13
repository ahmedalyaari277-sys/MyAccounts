package com.myaccounts.app.data.local.converter

import androidx.room.TypeConverter
import com.myaccounts.app.data.local.entity.CurrencyCode
import com.myaccounts.app.data.local.entity.TransactionType

class DatabaseConverters {

    @TypeConverter
    fun fromCurrencyCode(value: CurrencyCode): String {
        return value.code
    }

    @TypeConverter
    fun toCurrencyCode(value: String): CurrencyCode {
        return CurrencyCode.fromCode(value)
    }

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String {
        return value.name
    }

    @TypeConverter
    fun toTransactionType(value: String): TransactionType {
        return TransactionType.valueOf(value)
    }
}
