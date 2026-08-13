package com.myaccounts.app.data.local.dao

import com.myaccounts.app.data.local.entity.TransactionEntity

data class TransactionWithBalance(
    val transaction: TransactionEntity,
    val runningBalance: Long
)
