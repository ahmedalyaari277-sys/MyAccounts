package com.myaccounts.app.domain.usecase

import com.myaccounts.app.domain.repository.LedgerRepositoryContract

class AddPersonUseCase(
    private val repository: LedgerRepositoryContract
) {

    suspend operator fun invoke(
        name: String,
        phone: String,
        address: String
    ): Result<Long> {

        val normalizedName = name.trim()

        if (normalizedName.isBlank()) {
            return Result.failure(
                IllegalArgumentException("اسم الشخص مطلوب")
            )
        }

        return runCatching {
            repository.addPerson(
                name = normalizedName,
                phone = phone.trim(),
                address = address.trim()
            )
        }
    }
}
