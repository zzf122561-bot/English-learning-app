package com.xuesui.englishapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.xuesui.englishapp.dictionary.DictionaryFeature
import com.xuesui.englishapp.dictionary.DictionaryPresentation
import com.xuesui.englishapp.feature.WordMemoryFeature

private enum class MainSection {
    WORD_MEMORY,
    DICTIONARY,
}

private val AppShellColors = lightColorScheme(
    primary = Color(0xFF2457D6),
    secondary = Color(0xFF2E7D6B),
    background = Color(0xFFF7F9FC),
    surface = Color.White,
    onSurface = Color(0xFF10243C),
)

@Composable
internal fun EnglishAppShell() {
    var selectedSectionName by rememberSaveable { mutableStateOf(MainSection.WORD_MEMORY.name) }
    var quickLookupQuery by rememberSaveable { mutableStateOf<String?>(null) }
    val stateHolder = rememberSaveableStateHolder()
    val selectedSection = MainSection.valueOf(selectedSectionName)

    MaterialTheme(colorScheme = AppShellColors) {
        val activeQuickQuery = quickLookupQuery
        if (activeQuickQuery != null) {
            stateHolder.SaveableStateProvider("quick-lookup") {
                DictionaryFeature(
                    initialQuery = activeQuickQuery,
                    presentation = DictionaryPresentation.QUICK_LOOKUP,
                    onClose = { quickLookupQuery = null },
                )
            }
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = selectedSection == MainSection.WORD_MEMORY,
                            onClick = { selectedSectionName = MainSection.WORD_MEMORY.name },
                            icon = { Icon(Icons.Default.School, contentDescription = null) },
                            label = { Text("记单词") },
                        )
                        NavigationBarItem(
                            selected = selectedSection == MainSection.DICTIONARY,
                            onClick = { selectedSectionName = MainSection.DICTIONARY.name },
                            icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                            label = { Text("词典") },
                        )
                    }
                },
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .consumeWindowInsets(padding),
                ) {
                    stateHolder.SaveableStateProvider("main-${selectedSection.name}") {
                        when (selectedSection) {
                            MainSection.WORD_MEMORY -> WordMemoryFeature(
                                onLookupRequested = { word ->
                                    if (word.isNotEmpty()) quickLookupQuery = word
                                },
                            )

                            MainSection.DICTIONARY -> DictionaryFeature(
                                presentation = DictionaryPresentation.FULL,
                            )
                        }
                    }
                }
            }
        }
    }
}
