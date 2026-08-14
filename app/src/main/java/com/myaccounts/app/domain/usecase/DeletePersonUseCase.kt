package com.myaccounts.app.domain.usecase

import com.myaccounts.app.data.repository.LedgerRepositoryContract

class DeletePersonUseCase(
    private val repository: LedgerRepositoryContract
) {

    suspend operator fun invoke(
        personId: Long
    ) {
        repository.deletePerson(personId)
    }
}
