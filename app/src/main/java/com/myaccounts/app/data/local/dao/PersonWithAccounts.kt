package com.myaccounts.app.data.local.dao

import androidx.room.Embedded
import androidx.room.Relation
import com.myaccounts.app.data.local.CurrencyAccountEntity
import com.myaccounts.app.data.local.PersonEntity

data class PersonWithAccounts(
    @Embedded
    val person: PersonEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "personId"
    )
    val accounts: List<CurrencyAccountEntity>
)
