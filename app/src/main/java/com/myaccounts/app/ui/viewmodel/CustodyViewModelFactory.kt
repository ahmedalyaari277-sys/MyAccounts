package com.myaccounts.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class CustodyViewModelFactory(private val app:Application):ViewModelProvider.Factory{
 override fun <T:ViewModel> create(modelClass:Class<T>):T{
  if(modelClass.isAssignableFrom(CustodyViewModel::class.java))return CustodyViewModel(app) as T
  throw IllegalArgumentException("Unknown ViewModel")
 }
}
