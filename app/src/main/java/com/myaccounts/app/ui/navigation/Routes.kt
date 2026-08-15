package com.myaccounts.app.ui.navigation

object Routes {
    const val HOME = "home"
    const val PERSON_ACCOUNT = "person_account/{personId}"
    const val TRANSACTIONS = "transactions/{accountId}/{currencyCode}"
    const val REPORTS = "reports"
    const val PERSON_REPORT = "person_report/{personId}/{currencyCode}"
    const val ARCHIVE = "archive"

    fun personAccount(personId: Long): String = "person_account/$personId"
    fun transactions(accountId: Long, currencyCode: String): String = "transactions/$accountId/$currencyCode"
    fun reports(): String = REPORTS
    fun personReport(personId: Long, currencyCode: String): String = "person_report/$personId/$currencyCode"
    fun archive(): String = ARCHIVE
}
