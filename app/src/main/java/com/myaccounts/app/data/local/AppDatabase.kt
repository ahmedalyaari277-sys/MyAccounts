package com.myaccounts.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.myaccounts.app.data.local.converter.DatabaseConverters
import com.myaccounts.app.data.local.dao.LedgerDao
import com.myaccounts.app.data.local.entity.CurrencyAccountEntity
import com.myaccounts.app.data.local.entity.PersonEntity
import com.myaccounts.app.data.local.entity.TransactionEntity
import com.myaccounts.app.data.local.migration.MIGRATION_1_2

@Database(
    entities = [
        PersonEntity::class,
        CurrencyAccountEntity::class,
        TransactionEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ledgerDao(): LedgerDao

    companion object {

        private const val DATABASE_NAME =
            "ledger_local_database.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(
            context: Context
        ): AppDatabase {

            return INSTANCE ?: synchronized(this) {

                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also {
                        INSTANCE = it
                    }
            }
        }
    }
}
