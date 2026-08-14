package com.myaccounts.app.domain.usecase

import com.myaccounts.app.data.local.PersonEntity
import com.myaccounts.app.data.repository.LedgerRepositoryContract

class UpdatePersonUseCase(
    private val repository: LedgerRepositoryContract
) {

    suspend operator fun invoke(
        person: PersonEntity
    ) {
        repository.updatePerson(person)
    }
}
