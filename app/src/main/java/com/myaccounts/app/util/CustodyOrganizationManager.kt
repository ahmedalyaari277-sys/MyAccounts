package com.myaccounts.app.util

import androidx.room.withTransaction
import com.myaccounts.app.data.custody.CustodyAccountEntity
import com.myaccounts.app.data.custody.CustodyPersonEntity
import com.myaccounts.app.data.local.AppDatabase

/** Creates the custody organization as one fixed ENTITY party without introducing a new accounting model. */
object CustodyOrganizationManager {
    private val currencies = listOf("YER", "SAR", "USD")

    suspend fun ensure(db: AppDatabase, custodyId: Long) = db.withTransaction {
        val dao = db.custodyDao()
        val custody = dao.getCustody(custodyId) ?: return@withTransaction
        val externalId = "ORG-${custody.externalId}"
        val byExternalId = dao.getPersonByExternalId(custodyId, externalId)
        val fallback = dao.getEntityPerson(custodyId)?.takeIf { it.name.trim() == custody.organizationName.trim() }
        val existing = byExternalId ?: fallback
        val cleanName = custody.organizationName.trim()
        val excludedPersonId = existing?.id ?: 0L
        require(!dao.hasPersonWithNameInCustody(custodyId, cleanName, excludedPersonId)) { "اسم جهة العهدة موجود بالفعل كطرف في هذه العهدة" }
        val person = if (existing == null) {
            val id = dao.insertPerson(CustodyPersonEntity(custodyId = custodyId, name = cleanName, phone = custody.organizationPhone.trim(), address = custody.organizationAddress.trim(), notes = custody.organizationNotes.trim(), partyType = "ENTITY", externalId = externalId))
            dao.getPerson(id) ?: error("تعذر إنشاء طرف الجهة")
        } else {
            val updated = existing.copy(name = cleanName, phone = custody.organizationPhone.trim(), address = custody.organizationAddress.trim(), notes = custody.organizationNotes.trim(), partyType = "ENTITY", isArchived = false, externalId = externalId)
            if (updated != existing) dao.updatePerson(updated)
            updated
        }
        val accounts = dao.getAllAccounts(custodyId)
        val missing = currencies.filter { code -> accounts.none { it.holderType == "PERSON" && it.personId == person.id && it.currencyCode == code } }
        if (missing.isNotEmpty()) dao.insertAccounts(missing.map { code -> CustodyAccountEntity(custodyId = custodyId, holderType = "PERSON", personId = person.id, currencyCode = code) })
    }
}
