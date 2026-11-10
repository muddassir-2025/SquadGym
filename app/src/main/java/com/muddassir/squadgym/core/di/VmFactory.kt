package com.muddassir.squadgym.core.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** Compact ViewModel factory; DI is manual, so this keeps screens tidy. */
inline fun <reified T : ViewModel> vmFactory(crossinline builder: () -> T): ViewModelProvider.Factory =
    viewModelFactory { initializer { builder() } }
