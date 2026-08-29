package com.myaccounts.app.ui.navigation

object Routes {
 const val GATEWAY="gateway";const val HOME="home";const val PERSON_ACCOUNT="person_account/{personId}";const val TRANSACTIONS="transactions/{accountId}/{currencyCode}";const val QUICK_TRANSACTION="quick_transaction/{personId}";const val REPORTS="reports";const val PERSON_REPORT="person_report/{personId}/{currencyCode}";const val ARCHIVE="archive";const val ARCHIVED_PERSON="archived_person/{personId}";const val BACKUP_RESTORE="backup_restore";const val SETTINGS="settings";const val DETAILS="details";const val CUSTODIES="custodies";const val CUSTODY="custody/{custodyId}"
 fun personAccount(id:Long)="person_account/"+id;fun transactions(id:Long,c:String)="transactions/"+id+"/"+c;fun quickTransaction(id:Long)="quick_transaction/"+id;fun personReport(id:Long,c:String="ALL")="person_report/"+id+"/"+c;fun archivedPerson(id:Long)="archived_person/"+id;fun custody(id:Long)="custody/"+id
}
