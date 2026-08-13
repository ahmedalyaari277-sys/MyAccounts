package com.myaccounts.app.domain.usecase

import com.myaccounts.app.domain.repository.LedgerRepositoryContract

class UpdatePersonUseCase(
    private val repository: LedgerRepositoryContract
) {

    suspend operator fun invoke(
        personId: Long,
        name: String,
        phone: String,
        address: String,
        notes: String
    ): Result<Unit> {

        val normalizedName =
            name.trim()

        if (normalizedName.isBlank()) {
            return Result.failure(
                IllegalArgumentException(
                    "اسم الشخص مطلوب"
                )
            )
        }

        return repository.updatePerson(
            personId = personId,
            name = normalizedName,
            phone = phone.trim(),
            address = address.trim(),
            notes = notes.trim()
        )
    }
}
