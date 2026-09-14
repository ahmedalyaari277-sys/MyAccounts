package com.myaccounts.app.util

import androidx.room.withTransaction
import com.myaccounts.app.data.custody.CustodyBalanceRules
import com.myaccounts.app.data.local.AppDatabase

/** Rebuilds only custody account balances from custody transactions. */
object CustodyBalanceRebuilder {
    suspend fun rebuildCustody(db: AppDatabase, custodyId: Long) {
        db.withTransaction { rebuildCustodyInTransaction(db, custodyId) }
    }

    /** Rebuilds a custody while the caller already owns the Room transaction. */
    suspend fun rebuildCustodyInTransaction(db: AppDatabase, custodyId: Long) {
        val dao = db.custodyDao()
        val accounts = dao.getAllAccounts(custodyId)
        val now = System.currentTimeMillis()
        accounts.forEach { account ->
            if (account.balanceMinor != 0L) dao.adjustAccountBalance(account.id, -account.balanceMinor, now)
        }
        dao.getAllTransactions(custodyId, false).forEach { tx ->
            val ownerDelta = CustodyBalanceRules.ownerDelta(tx.type, tx.amountMinor)
            dao.adjustAccountBalance(tx.accountId, ownerDelta, now)
            tx.personId?.let { personId ->
                val delta = CustodyBalanceRules.personDelta(tx.type, tx.amountMinor)
                if (delta != 0L) {
                    dao.getPersonAccount(custodyId, personId, tx.currencyCode)?.let { pa ->
                        dao.adjustAccountBalance(pa.id, delta, now)
                    }
                }
            }
        }
    }

    suspend fun rebuildAllActive(db: AppDatabase) {
        db.withTransaction {
            db.custodyDao().getAllCustodies(false).forEach { rebuildCustodyInTransaction(db, it.id) }
        }
    }
}