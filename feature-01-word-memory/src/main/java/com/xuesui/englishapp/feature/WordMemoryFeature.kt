package com.xuesui.englishapp.feature

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xuesui.englishapp.WordMemoryViewModel
import com.xuesui.englishapp.data.NotebookRepository
import com.xuesui.englishapp.data.WordMemoryDatabase
import com.xuesui.englishapp.ui.WordMemoryApp
import com.xuesui.englishapp.ui.theme.WordMemoryTheme

@Composable
fun WordMemoryFeature() {
    val context = LocalContext.current.applicationContext
    val repository = NotebookRepository(WordMemoryDatabase.getInstance(context))
    WordMemoryTheme {
        val model: WordMemoryViewModel = viewModel(factory = WordMemoryViewModel.Factory(repository))
        WordMemoryApp(model)
    }
}
