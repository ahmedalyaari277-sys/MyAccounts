package com.myaccounts.app.ui.navigation

object Routes {

    const val HOME = "home"

    const val PERSON_ACCOUNT =
        "person_account/{personId}"

    const val TRANSACTIONS =
        "transactions/{accountId}/{currencyCode}"

    const val REPORTS =
        "reports"

    const val PERSON_REPORT =
        "person_report/{personId}/{currencyCode}"

    fun personAccount(
        personId: Long
    ): String {
        return "person_account/$personId"
    }

    fun transactions(
        accountId: Long,
        currencyCode: String
    ): String {
        return "transactions/$accountId/$currencyCode"
    }

    fun reports(): String {
        return REPORTS
    }

    fun personReport(
        personId: Long,
        currencyCode: String
    ): String {
        return "person_report/$personId/$currencyCode"
    }
}
