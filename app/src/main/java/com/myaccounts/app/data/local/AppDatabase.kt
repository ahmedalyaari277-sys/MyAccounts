package com.myaccounts.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.myaccounts.app.data.custody.CustodyAccountEntity
import com.myaccounts.app.data.custody.CustodyDao
import com.myaccounts.app.data.custody.CustodyEntity
import com.myaccounts.app.data.custody.CustodyPersonEntity
import com.myaccounts.app.data.custody.CustodyTransactionEntity
import com.myaccounts.app.data.local.converter.TransactionConverters
import com.myaccounts.app.data.local.dao.LedgerDao
import com.myaccounts.app.data.local.dao.TransactionAttachmentDao
import com.myaccounts.app.data.local.dao.TransactionDao
import com.myaccounts.app.data.reports.ReportDao
import java.util.concurrent.Callable

@Database(
    entities = [
        PersonEntity::class,
        CurrencyAccountEntity::class,
        TransactionEntity::class,
        TransactionAttachmentEntity::class,
        ArchivedTransactionSnapshotEntity::class,
        ArchivedTransactionAttachmentSnapshotEntity::class,
        CustodyEntity::class,
        CustodyPersonEntity::class,
        CustodyAccountEntity::class,
        CustodyTransactionEntity::class
    ],
    version = 9,
    exportSchema = true
)
@TypeConverters(TransactionConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
    abstract fun transactionDao(): TransactionDao
    abstract fun transactionAttachmentDao(): TransactionAttachmentDao
    abstract fun reportDao(): ReportDao
    abstract fun custodyDao(): CustodyDao

    fun <R> withSynchronousTransaction(block: () -> R): R = runInTransaction(Callable { block() })

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

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
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE people ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE people ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS currency_accounts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        personId INTEGER NOT NULL,
                        currencyCode TEXT NOT NULL,
                        balanceMinor INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(personId) REFERENCES people(id) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_currency_accounts_personId ON currency_accounts(personId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_currency_accounts_personId_currencyCode ON currency_accounts(personId, currencyCode)")
                db.execSQL("INSERT INTO currency_accounts (personId,currencyCode,balanceMinor,createdAt,updatedAt) SELECT id,'YER',0,createdAt,createdAt FROM people")
                db.execSQL("INSERT OR IGNORE INTO currency_accounts (personId,currencyCode,balanceMinor,createdAt,updatedAt) SELECT id,'SAR',0,createdAt,createdAt FROM people")
                db.execSQL("INSERT OR IGNORE INTO currency_accounts (personId,currencyCode,balanceMinor,createdAt,updatedAt) SELECT id,'USD',0,createdAt,createdAt FROM people")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        accountId INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        amountMinor INTEGER NOT NULL,
                        description TEXT NOT NULL,
                        transactionDate INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(accountId) REFERENCES currency_accounts(id) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_accountId ON transactions(accountId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_transactionDate ON transactions(transactionDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_type ON transactions(type)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS transaction_attachments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        transactionId INTEGER NOT NULL,
                        fileName TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        relativePath TEXT NOT NULL,
                        sizeBytes INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(transactionId) REFERENCES transactions(id) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_attachments_transactionId ON transaction_attachments(transactionId)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_isArchived ON transactions(isArchived)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS archived_transaction_snapshots (
                        transactionId INTEGER NOT NULL PRIMARY KEY,
                        accountId INTEGER NOT NULL,
                        personId INTEGER NOT NULL,
                        personName TEXT NOT NULL,
                        personPhone TEXT NOT NULL,
                        personAddress TEXT NOT NULL,
                        personNotes TEXT NOT NULL,
                        currencyCode TEXT NOT NULL,
                        type TEXT NOT NULL,
                        amountMinor INTEGER NOT NULL,
                        description TEXT NOT NULL,
                        transactionDate INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        archivedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS archived_transaction_attachment_snapshots (
                        attachmentId INTEGER NOT NULL PRIMARY KEY,
                        transactionId INTEGER NOT NULL,
                        fileName TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        relativePath TEXT NOT NULL,
                        sizeBytes INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_archived_transaction_attachment_snapshots_transactionId ON archived_transaction_attachment_snapshots(transactionId)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN archivedWithPerson INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_archivedWithPerson ON transactions(archivedWithPerson)")
                db.execSQL("ALTER TABLE archived_transaction_snapshots ADD COLUMN archivedWithPerson INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS custodies (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,name TEXT NOT NULL,phone TEXT NOT NULL,address TEXT NOT NULL,notes TEXT NOT NULL,organizationName TEXT NOT NULL,organizationPhone TEXT NOT NULL,organizationAddress TEXT NOT NULL,organizationNotes TEXT NOT NULL,createdAt INTEGER NOT NULL,isArchived INTEGER NOT NULL,archivedAt INTEGER,externalId TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_custodies_externalId ON custodies(externalId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_custodies_name ON custodies(name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_custodies_organizationName ON custodies(organizationName)")
                db.execSQL("CREATE TABLE IF NOT EXISTS custody_persons (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,custodyId INTEGER NOT NULL,name TEXT NOT NULL,phone TEXT NOT NULL,address TEXT NOT NULL,notes TEXT NOT NULL,createdAt INTEGER NOT NULL,isArchived INTEGER NOT NULL,archivedAt INTEGER,externalId TEXT NOT NULL,FOREIGN KEY(custodyId) REFERENCES custodies(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_custody_persons_custodyId ON custody_persons(custodyId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_custody_persons_name ON custody_persons(name)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_custody_persons_custody_external ON custody_persons(custodyId,externalId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS custody_accounts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,custodyId INTEGER NOT NULL,holderType TEXT NOT NULL,personId INTEGER,currencyCode TEXT NOT NULL,balanceMinor INTEGER NOT NULL,createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL,FOREIGN KEY(custodyId) REFERENCES custodies(id) ON DELETE CASCADE,FOREIGN KEY(personId) REFERENCES custody_persons(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_custody_accounts_custodyId ON custody_accounts(custodyId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_custody_accounts_personId ON custody_accounts(personId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_custody_accounts_unique ON custody_accounts(custodyId,holderType,personId,currencyCode)")
                db.execSQL("CREATE TABLE IF NOT EXISTS custody_transactions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,custodyId INTEGER NOT NULL,accountId INTEGER NOT NULL,personId INTEGER,currencyCode TEXT NOT NULL,type TEXT NOT NULL,amountMinor INTEGER NOT NULL,description TEXT NOT NULL,transactionDate INTEGER NOT NULL,createdAt INTEGER NOT NULL,externalId TEXT NOT NULL,isArchived INTEGER NOT NULL,FOREIGN KEY(custodyId) REFERENCES custodies(id) ON DELETE CASCADE,FOREIGN KEY(accountId) REFERENCES custody_accounts(id) ON DELETE CASCADE,FOREIGN KEY(personId) REFERENCES custody_persons(id) ON DELETE SET NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_custody_transactions_externalId ON custody_transactions(externalId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_custody_transactions_custodyId ON custody_transactions(custodyId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_custody_transactions_accountId ON custody_transactions(accountId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_custody_transactions_personId ON custody_transactions(personId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_custody_transactions_transactionDate ON custody_transactions(transactionDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_custody_transactions_type ON custody_transactions(type)")
            }
        }
    }
}
