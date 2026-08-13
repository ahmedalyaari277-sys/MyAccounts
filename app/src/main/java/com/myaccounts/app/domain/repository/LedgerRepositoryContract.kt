package com.myaccounts.app.domain.repository

import com.myaccounts.app.data.local.dao.PersonWithAccounts
import kotlinx.coroutines.flow.Flow

interface LedgerRepositoryContract {

    val allPersonsFlow: Flow<List<PersonWithAccounts>>

    suspend fun addPerson(
        name: String,
        phone: String,
        address: String
    ): Long

    suspend fun getPersonWithAccounts(
        personId: Long
    ): PersonWithAccounts?

    suspend fun deletePerson(
        personId: Long
    )
}
