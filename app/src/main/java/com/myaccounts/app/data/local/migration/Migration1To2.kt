package com.myaccounts.app.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {

    override fun migrate(
        database: SupportSQLiteDatabase
    ) {
        database.execSQL(
            """
            ALTER TABLE persons
            ADD COLUMN notes TEXT NOT NULL DEFAULT ''
            """.trimIndent()
        )

        database.execSQL(
            """
            ALTER TABLE persons
            ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1
            """.trimIndent()
        )
    }
}
