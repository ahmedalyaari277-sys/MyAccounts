package com.myaccounts.app.custody

import org.junit.Assert.assertEquals
import org.junit.Test

class CustodyBalanceTest {
 private fun delta(type:String,amount:Long)=if(type=="RECEIVED_FROM_ORG"||type=="RETURNED_FROM_PERSON")amount else -amount
 @Test fun owner_received_is_on_him(){assertEquals(1000,delta("RECEIVED_FROM_ORG",1000))}
 @Test fun owner_return_to_org_is_for_him(){assertEquals(-1000,delta("RETURNED_TO_ORG",1000))}
 @Test fun person_paid_is_for_person(){assertEquals(-1000,delta("PAID_TO_PERSON",1000))}
 @Test fun person_return_is_on_person(){assertEquals(1000,delta("RETURNED_FROM_PERSON",1000))}
}
