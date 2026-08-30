package com.myaccounts.app.ui.navigation

object Routes {
    const val GATEWAY = "gateway"
    const val HOME = "home"
    const val PERSON_ACCOUNT = "person_account/{personId}"
    const val TRANSACTIONS = "transactions/{accountId}/{currencyCode}"
    const val QUICK_TRANSACTION = "quick_transaction/{personId}"
    const val REPORTS = "reports"
    const val PERSON_REPORT = "person_report/{personId}/{currencyCode}"
    const val ARCHIVE = "archive"
    const val ARCHIVED_PERSON = "archived_person/{personId}"
    const val BACKUP_RESTORE = "backup_restore"
    const val SETTINGS = "settings"
    const val DETAILS = "details"
    const val CUSTODIES = "custodies"
    const val CUSTODY = "custody/{custodyId}"
    const val CUSTODY_PERSON = "custody/{custodyId}/person/{personId}"
    const val CUSTODY_REPORTS = "custody_reports"
    const val CUSTODY_ARCHIVE = "custody_archive"
    const val CUSTODY_TRANSFER = "custody_transfer"

    fun personAccount(personId: Long): String = "person_account/$personId"
    fun transactions(accountId: Long, currencyCode: String): String = "transactions/$accountId/$currencyCode"
    fun quickTransaction(personId: Long): String = "quick_transaction/$personId"
    fun reports(): String = REPORTS
    fun personReport(personId: Long, currencyCode: String = "ALL"): String = "person_report/$personId/$currencyCode"
    fun archive(): String = ARCHIVE
    fun archivedPerson(personId: Long): String = "archived_person/$personId"
    fun backupRestore(): String = BACKUP_RESTORE
    fun settings(): String = SETTINGS
    fun details(): String = DETAILS
    fun custody(custodyId: Long): String = "custody/$custodyId"
    fun custodyPerson(custodyId: Long, personId: Long): String = "custody/$custodyId/person/$personId"
}
