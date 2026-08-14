package com.myaccounts.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.myaccounts.app.data.local.converter.TransactionConverters
import com.myaccounts.app.data.local.dao.LedgerDao
import com.myaccounts.app.data.local.dao.TransactionDao
import com.myaccounts.app.data.reports.ReportDao

@Database(
    entities = [
        PersonEntity::class,
        CurrencyAccountEntity::class,
        TransactionEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(
    TransactionConverters::class
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ledgerDao(): LedgerDao

    abstract fun transactionDao(): TransactionDao

    abstract fun reportDao(): ReportDao

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
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4
                    )
                    .build()
                    .also {
                        INSTANCE = it
                    }
            }
        }

        private val MIGRATION_1_2 =
            object : Migration(1, 2) {

                override fun migrate(
                    db: SupportSQLiteDatabase
                ) {
                    db.execSQL(
                        """
                        ALTER TABLE people
                        ADD COLUMN notes TEXT NOT NULL DEFAULT ''
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        ALTER TABLE people
                        ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1
                        """.trimIndent()
                    )
                }
            }

        private val MIGRATION_2_3 =
            object : Migration(2, 3) {

                override fun migrate(
                    db: SupportSQLiteDatabase
                ) {
                    db.execSQL(
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

                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS
                        index_currency_accounts_personId
                        ON currency_accounts(personId)
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE UNIQUE INDEX IF NOT EXISTS
                        index_currency_accounts_personId_currencyCode
                        ON currency_accounts(personId, currencyCode)
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        INSERT INTO currency_accounts
                        (
                            personId,
                            currencyCode,
                            balanceMinor,
                            createdAt,
                            updatedAt
                        )
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

                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO currency_accounts
                        (
                            personId,
                            currencyCode,
                            balanceMinor,
                            createdAt,
                            updatedAt
                        )
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

                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO currency_accounts
                        (
                            personId,
                            currencyCode,
                            balanceMinor,
                            createdAt,
                            updatedAt
                        )
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

        private val MIGRATION_3_4 =
            object : Migration(3, 4) {

                override fun migrate(
                    db: SupportSQLiteDatabase
                ) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS transactions (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            accountId INTEGER NOT NULL,
                            type TEXT NOT NULL,
                            amountMinor INTEGER NOT NULL,
                            description TEXT NOT NULL,
                            transactionDate INTEGER NOT NULL,
                            createdAt INTEGER NOT NULL,
                            FOREIGN KEY(accountId)
                                REFERENCES currency_accounts(id)
                                ON UPDATE CASCADE
                                ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS
                        index_transactions_accountId
                        ON transactions(accountId)
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS
                        index_transactions_transactionDate
                        ON transactions(transactionDate)
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS
                        index_transactions_type
                        ON transactions(type)
                        """.trimIndent()
                    )
                }
            }
    }
}
