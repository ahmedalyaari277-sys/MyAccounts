package com.myaccounts.app.data.custody

data class CustodyDisplayBalance(
    val custodyMinor: Long,
    val organizationDebtMinor: Long,
    val peopleDebtMinor: Long
)

object CustodyFinancialSummary {
    fun custodyOwnerBalance(transactions: List<CustodyTransactionEntity>, currency: String): Long =
        transactions.asSequence().filter { it.currencyCode == currency }.sumOf { CustodyBalanceRules.ownerCashDelta(it.type, it.amountMinor) }

    fun custodyTotalBalance(transactions: List<CustodyTransactionEntity>, accounts: List<CustodyAccountEntity>, currency: String, persons: List<CustodyPersonEntity>): Long {
        val owner = accounts.firstOrNull { it.holderType == "OWNER" && it.personId == null && it.currencyCode == currency }?.balanceMinor ?: custodyOwnerBalance(transactions, currency)
        val people = persons.sumOf { person -> accounts.firstOrNull { it.holderType == "PERSON" && it.personId == person.id && it.currencyCode == currency }?.balanceMinor ?: 0L }
        return owner + people
    }

    fun personCustodyBalance(transactions: List<CustodyTransactionEntity>, personId: Long, currency: String): Long =
        transactions.asSequence().filter { it.personId == personId && it.currencyCode == currency }.sumOf { CustodyBalanceRules.personCustodyDelta(it.type, it.amountMinor) }

    fun ownerOrganizationDebt(transactions: List<CustodyTransactionEntity>, currency: String): Long =
        transactions.asSequence().filter { it.currencyCode == currency }.sumOf { CustodyBalanceRules.ownerOrgDebtDelta(it.type, it.amountMinor) }

    fun ownerPeopleDebt(transactions: List<CustodyTransactionEntity>, currency: String): Long =
        transactions.asSequence().filter { it.currencyCode == currency }.sumOf { CustodyBalanceRules.ownerPeopleDebtDelta(it.type, it.amountMinor) }

    fun personDebt(transactions: List<CustodyTransactionEntity>, personId: Long, currency: String): Long =
        transactions.asSequence().filter { it.personId == personId && it.currencyCode == currency }.sumOf { CustodyBalanceRules.personDebtDelta(it.type, it.amountMinor) }

    fun ownerDisplay(transactions: List<CustodyTransactionEntity>, accounts: List<CustodyAccountEntity>, persons: List<CustodyPersonEntity>, currency: String): CustodyDisplayBalance =
        CustodyDisplayBalance(
            custodyMinor = custodyOwnerBalance(transactions, currency),
            organizationDebtMinor = ownerOrganizationDebt(transactions, currency),
            peopleDebtMinor = ownerPeopleDebt(transactions, currency)
        )
}
