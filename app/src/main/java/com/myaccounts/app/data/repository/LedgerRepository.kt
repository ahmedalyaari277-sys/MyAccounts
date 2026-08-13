package com.myaccounts.app.data.repository

import com.myaccounts.app.data.local.dao.LedgerDao
import com.myaccounts.app.data.local.dao.PersonWithAccounts
import com.myaccounts.app.data.local.entity.CurrencyAccountEntity
import com.myaccounts.app.data.local.entity.CurrencyCode
import com.myaccounts.app.data.local.entity.PersonEntity
import com.myaccounts.app.domain.repository.LedgerRepositoryContract
import kotlinx.coroutines.flow.Flow

class LedgerRepository(
    private val dao: LedgerDao
) : LedgerRepositoryContract {

    override val allPersonsFlow:
        Flow<List<PersonWithAccounts>> =
        dao.getAllPersonsWithAccountsFlow()

    override suspend fun addPerson(
        name: String,
        phone: String,
        address: String,
        notes: String
    ): Long {

        val person = PersonEntity(
            name = name.trim(),
            phone = phone.trim(),
            address = address.trim(),
            notes = notes.trim(),
            isActive = true
        )

        val defaultAccounts =
            CurrencyCode.entries.map { currency ->

                CurrencyAccountEntity(
                    personId = 0L,
                    currency = currency
                )
            }

        return dao.insertPersonWithAccounts(
            person = person,
            accounts = defaultAccounts
        )
    }

    override suspend fun getPersonWithAccounts(
        personId: Long
    ): PersonWithAccounts? {

        return dao.getPersonWithAccounts(
            personId
        )
    }

    override suspend fun updatePerson(
        personId: Long,
        name: String,
        phone: String,
        address: String,
        notes: String
    ): Result<Unit> {

        return runCatching {

            val affectedRows =
                dao.updatePerson(
                    personId = personId,
                    name = name.trim(),
                    phone = phone.trim(),
                    address = address.trim(),
                    notes = notes.trim()
                )

            check(affectedRows > 0) {
                "تعذر تعديل بيانات الشخص"
            }
        }
    }

    override suspend fun deletePerson(
        personId: Long
    ): Result<Unit> {

        return runCatching {

            val affectedRows =
                dao.deactivatePerson(
                    personId
                )

            check(affectedRows > 0) {
                "تعذر حذف الشخص"
            }
        }
    }
}
