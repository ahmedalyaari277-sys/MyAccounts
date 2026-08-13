package com.myaccounts.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.myaccounts.app.data.local.dao.LedgerDao

@Database(
    entities = [
        PersonEntity::class,
        CurrencyAccountEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ledgerDao(): LedgerDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "myaccounts_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 =
            object : androidx.room.migration.Migration(1, 2) {
                override fun migrate(
                    database: androidx.sqlite.db.SupportSQLiteDatabase
                ) {
                    database.execSQL(
                        """
                        ALTER TABLE people
                        ADD COLUMN notes TEXT NOT NULL DEFAULT ''
                        """.trimIndent()
                    )

                    database.execSQL(
                        """
                        ALTER TABLE people
                        ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1
                        """.trimIndent()
                    )
                }
            }

        private val MIGRATION_2_3 =
            object : androidx.room.migration.Migration(2, 3) {
                override fun migrate(
                    database: androidx.sqlite.db.SupportSQLiteDatabase
                ) {
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS currency_accounts (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            personId INTEGER NOT NULL,
                            currencyCode TEXT NOT NULL,
                            balanceMinor INTEGER NOT NULL DEFAULT 0,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            FOREIGN KEY(personId)
                                REFERENCES people(id)
                                ON UPDATE CASCADE
                                ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )

                    database.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS index_currency_accounts_personId
                        ON currency_accounts(personId)
                        """.trimIndent()
                    )

                    database.execSQL(
                        """
                        CREATE UNIQUE INDEX IF NOT EXISTS
                        index_currency_accounts_personId_currencyCode
                        ON currency_accounts(personId, currencyCode)
                        """.trimIndent()
                    )

                    database.execSQL(
                        """
                        INSERT INTO currency_accounts
                        (personId, currencyCode, balanceMinor, createdAt, updatedAt)
                        SELECT
                            id,
                            'YER',
                            0,
                            createdAt,
                            createdAt
                        FROM people
                        WHERE isActive = 1
                        """.trimIndent()
                    )

                    database.execSQL(
                        """
                        INSERT OR IGNORE INTO currency_accounts
                        (personId, currencyCode, balanceMinor, createdAt, updatedAt)
                        SELECT
                            id,
                            'SAR',
                            0,
                            createdAt,
                            createdAt
                        FROM people
                        WHERE isActive = 1
                        """.trimIndent()
                    )

                    database.execSQL(
                        """
                        INSERT OR IGNORE INTO currency_accounts
                        (personId, currencyCode, balanceMinor, createdAt, updatedAt)
                        SELECT
                            id,
                            'USD',
                            0,
                            createdAt,
                            createdAt
                        FROM people
                        WHERE isActive = 1
                        """.trimIndent()
                    )
                }
            }
    }
}
