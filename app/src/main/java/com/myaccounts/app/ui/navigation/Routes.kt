package com.myaccounts.app.ui.navigation

object Routes {

    const val HOME = "home"

    const val PERSON_ACCOUNT = "person_account/{personId}"

    fun personAccount(
        personId: Long
    ): String {
        return "person_account/$personId"
    }
}
