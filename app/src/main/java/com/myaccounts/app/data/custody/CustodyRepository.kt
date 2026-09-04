package com.myaccounts.app.data.custody

import android.content.Context
import androidx.room.withTransaction
import com.myaccounts.app.data.currency.CurrencyCatalog
import com.myaccounts.app.util.CustodyAttachmentStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

object CustodyTransactionType {
    const val RECEIVED_FROM_ORG = "RECEIVED_FROM_ORG"
    const val PAID_TO_PERSON = "PAID_TO_PERSON"
    const val RETURNED_FROM_PERSON = "RETURNED_FROM_PERSON"
    const val RETURNED_TO_ORG = "RETURNED_TO_ORG"
}

object CustodyBalanceRules {
    fun ownerDelta(type: String, amount: Long): Long = when (type) {
        CustodyTransactionType.RECEIVED_FROM_ORG, CustodyTransactionType.RETURNED_FROM_PERSON -> amount
        CustodyTransactionType.PAID_TO_PERSON, CustodyTransactionType.RETURNED_TO_ORG -> -amount
        else -> error("نوع عملية عهدة غير معروف")
    }
    fun personDelta(type: String, amount: Long): Long = when (type) {
        CustodyTransactionType.PAID_TO_PERSON -> amount
        CustodyTransactionType.RETURNED_FROM_PERSON -> -amount
        else -> 0L
    }
}

class CustodyRepository(private val db: com.myaccounts.app.data.local.AppDatabase, context: Context) {
    private val dao = db.custodyDao()
    private val attachmentStore = CustodyAttachmentStorage(context.applicationContext)
    private fun currencies(): List<String> = CurrencyCatalog.definitions.map { it.code }
    private val allowedTypes = setOf(CustodyTransactionType.RECEIVED_FROM_ORG, CustodyTransactionType.PAID_TO_PERSON, CustodyTransactionType.RETURNED_FROM_PERSON, CustodyTransactionType.RETURNED_TO_ORG)

    fun observeCustodies(): Flow<List<CustodyEntity>> = dao.observeCustodies()
    fun observeCustody(id: Long): Flow<CustodyEntity?> = dao.observeCustody(id)
    fun observePersons(id: Long): Flow<List<CustodyPersonEntity>> = dao.observePersons(id)
    fun observeAccounts(id: Long): Flow<List<CustodyAccountEntity>> = dao.observeAccounts(id)
