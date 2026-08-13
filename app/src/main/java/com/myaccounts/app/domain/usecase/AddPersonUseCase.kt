package com.myaccounts.app.domain.usecase

import com.myaccounts.app.data.local.PersonEntity
import com.myaccounts.app.data.repository.LedgerRepositoryContract

class AddPersonUseCase(
    private val repository: LedgerRepositoryContract
) {

    suspend operator fun invoke(
        name: String,
        phone: String = "",
        address: String = "",
        notes: String = ""
    ): Long {
        require(name.isNotBlank()) {
            "اسم الشخص مطلوب"
        }

        return repository.insertPerson(
            PersonEntity(
                name = name.trim(),
                phone = phone.trim(),
                address = address.trim(),
                notes = notes.trim()
            )
        )
    }
}
