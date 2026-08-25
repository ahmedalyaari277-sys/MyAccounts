package com.myaccounts.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query

@Dao
interface ArchiveDao {
    @Query("""
        SELECT t.id
        FROM transactions t
        INNER JOIN currency_accounts ca ON ca.id = t.accountId
        WHERE ca.personId = :personId
    """)
    suspend fun getPersonTransactionIds(personId: Long): List<Long>

    @Query("""
        SELECT t.id
        FROM transactions t
        INNER JOIN currency_accounts ca ON ca.id = t.accountId
        INNER JOIN people p ON p.id = ca.personId
        WHERE p.isActive = 0
    """)
    suspend fun getArchivedPersonTransactionIds(): List<Long>

    @Query("DELETE FROM people WHERE isActive = 0")
    suspend fun clearArchivedPeople()
}
