package com.myaccounts.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myaccounts.app.data.custody.CustodyEntity
import com.myaccounts.app.data.custody.CustodyPersonEntity
import com.myaccounts.app.data.custody.CustodyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CustodyViewModel(app: Application): AndroidViewModel(app) {
    private val repo=CustodyRepository(com.myaccounts.app.data.local.AppDatabase.getInstance(app))
    val custodies=repo.observeCustodies().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    fun custody(id:Long)=repo.observeCustody(id).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
    fun persons(id:Long)=repo.observePersons(id).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    fun accounts(id:Long)=repo.observeAccounts(id).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    fun transactions(id:Long)=repo.observeTransactions(id).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    fun balance(accountId:Long)=repo.observeBalance(accountId).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),0L)
    fun create(c:CustodyEntity)=viewModelScope.launch{repo.createCustody(c)}
    fun addPerson(id:Long,p:CustodyPersonEntity)=viewModelScope.launch{repo.addPerson(id,p)}
    fun addTransaction(id:Long,currency:String,type:String,personId:Long?,amount:Long,description:String,date:Long)=viewModelScope.launch{repo.addTransaction(id,currency,type,personId,amount,description,date)}
    fun updateTransaction(id:Long,type:String,amount:Long,description:String,date:Long)=viewModelScope.launch{repo.updateTransaction(id,type,amount,description,date)}
    fun archive(id:Long)=viewModelScope.launch{repo.archive(id)}
}
