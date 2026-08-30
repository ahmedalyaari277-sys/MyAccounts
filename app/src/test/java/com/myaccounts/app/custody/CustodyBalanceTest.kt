package com.myaccounts.app.custody

import com.myaccounts.app.data.custody.CustodyBalanceRules
import com.myaccounts.app.data.custody.CustodyTransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class CustodyBalanceTest {
    @Test fun owner_received_from_org_is_on_owner() = assertEquals(1000, CustodyBalanceRules.ownerDelta(CustodyTransactionType.RECEIVED_FROM_ORG, 1000))
    @Test fun owner_returned_to_org_is_for_org() = assertEquals(-1000, CustodyBalanceRules.ownerDelta(CustodyTransactionType.RETURNED_TO_ORG, 1000))
    @Test fun owner_paid_to_person_is_for_person() = assertEquals(-1000, CustodyBalanceRules.ownerDelta(CustodyTransactionType.PAID_TO_PERSON, 1000))
    @Test fun owner_received_person_return_is_on_owner() = assertEquals(1000, CustodyBalanceRules.ownerDelta(CustodyTransactionType.RETURNED_FROM_PERSON, 1000))
    @Test fun person_paid_is_on_person() = assertEquals(1000, CustodyBalanceRules.personDelta(CustodyTransactionType.PAID_TO_PERSON, 1000))
    @Test fun person_return_is_for_person() = assertEquals(-1000, CustodyBalanceRules.personDelta(CustodyTransactionType.RETURNED_FROM_PERSON, 1000))
}
