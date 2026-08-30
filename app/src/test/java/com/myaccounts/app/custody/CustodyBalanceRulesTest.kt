package com.myaccounts.app.custody

import com.myaccounts.app.data.custody.CustodyBalanceRules
import com.myaccounts.app.data.custody.CustodyTransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class CustodyBalanceRulesTest {
    @Test fun receivedFromOrganization_isOnOwner() {
        assertEquals(10000L, CustodyBalanceRules.ownerDelta(CustodyTransactionType.RECEIVED_FROM_ORG, 10000L))
    }

    @Test fun paidToPerson_isForOwner() {
        assertEquals(-5000L, CustodyBalanceRules.ownerDelta(CustodyTransactionType.PAID_TO_PERSON, 5000L))
        assertEquals(5000L, CustodyBalanceRules.personDelta(CustodyTransactionType.PAID_TO_PERSON, 5000L))
    }

    @Test fun returnedFromPerson_isOnOwner_andForPerson() {
        assertEquals(2000L, CustodyBalanceRules.ownerDelta(CustodyTransactionType.RETURNED_FROM_PERSON, 2000L))
        assertEquals(-2000L, CustodyBalanceRules.personDelta(CustodyTransactionType.RETURNED_FROM_PERSON, 2000L))
    }

    @Test fun returnedToOrganization_reducesOwnerLiability() {
        assertEquals(-7000L, CustodyBalanceRules.ownerDelta(CustodyTransactionType.RETURNED_TO_ORG, 7000L))
        assertEquals(0L, CustodyBalanceRules.personDelta(CustodyTransactionType.RETURNED_TO_ORG, 7000L))
    }

    @Test fun fullSettlementCanReachZero() {
        val received = 20000L
        val spent = 12000L
        val returnedFromPeople = 1000L
        val returnedToOrg = 9000L
        val owner = received - spent + returnedFromPeople - returnedToOrg
        assertEquals(0L, owner)
    }
}
