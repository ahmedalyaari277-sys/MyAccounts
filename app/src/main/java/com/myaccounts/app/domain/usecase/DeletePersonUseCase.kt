package com.myaccounts.app.domain.usecase

import com.myaccounts.app.domain.repository.LedgerRepositoryContract

class DeletePersonUseCase(
    private val repository: LedgerRepositoryContract
) {

    suspend operator fun invoke(
        personId: Long
    ): Result<Unit> {
        return runCatching {
            repository.deletePerson(personId)
        }
    }
}
