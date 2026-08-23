package com.xuesui.englishapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xuesui.englishapp.data.NotebookRepository
import com.xuesui.englishapp.data.WordMemoryDatabase
import com.xuesui.englishapp.ui.WordMemoryApp
import com.xuesui.englishapp.ui.theme.WordMemoryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = NotebookRepository(WordMemoryDatabase.getInstance(this))
        setContent {
            WordMemoryTheme {
                val model: WordMemoryViewModel = viewModel(factory = WordMemoryViewModel.Factory(repository))
                WordMemoryApp(model)
            }
        }
    }
}
