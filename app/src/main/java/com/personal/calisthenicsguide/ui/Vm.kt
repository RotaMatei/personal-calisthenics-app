package com.personal.calisthenicsguide.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.personal.calisthenicsguide.CalisthenicsApp

/** Creates (or reuses) a ViewModel that needs the application's repository / session controller. */
@Composable
inline fun <reified T : ViewModel> appViewModel(crossinline create: (CalisthenicsApp) -> T): T {
    val app = LocalContext.current.applicationContext as CalisthenicsApp
    return viewModel(factory = viewModelFactory { initializer { create(app) } })
}
