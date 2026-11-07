package com.xuesui.englishapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xuesui.englishapp.dictionary.DictionaryFeature
import com.xuesui.englishapp.dictionary.DictionaryPresentation
import com.xuesui.englishapp.feature.WordMemoryFeature
import com.xuesui.englishapp.modelprobe.runtime.SpeechInlineReadingFeature
import com.xuesui.englishapp.modelprobe.runtime.SpeechReadingPlayButton
import com.xuesui.englishapp.modelprobe.runtime.SpeechRuntimeHost
import com.xuesui.englishapp.modelprobe.runtime.SpeechSettingsFeature

private enum class MainSection {
    WORD_MEMORY,
    DICTIONARY,
    SPEECH,
}

@Composable
internal fun EnglishAppShell() {
    var selectedSectionName by rememberSaveable { mutableStateOf(MainSection.WORD_MEMORY.name) }
    var quickLookupQuery by rememberSaveable { mutableStateOf<String?>(null) }
    val stateHolder = rememberSaveableStateHolder()
    val selectedSection = MainSection.valueOf(selectedSectionName)

    SaberTheme {
        // Keep the runtime host outside tab/quick-lookup branches. It reconnects generation
        // on foreground entry, but changing screens must not create another owner or auto-play.
        SpeechRuntimeHost()
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
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                        ) {
                            val itemColors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            NavigationBarItem(
                                selected = selectedSection == MainSection.WORD_MEMORY,
                                onClick = { selectedSectionName = MainSection.WORD_MEMORY.name },
                                icon = { Icon(Icons.Default.School, contentDescription = null) },
                                label = { Text("记单词") },
                                colors = itemColors,
                            )
                            NavigationBarItem(
                                selected = selectedSection == MainSection.DICTIONARY,
                                onClick = { selectedSectionName = MainSection.DICTIONARY.name },
                                icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                                label = { Text("词典") },
                                colors = itemColors,
                            )
                            NavigationBarItem(
                                selected = selectedSection == MainSection.SPEECH,
                                onClick = { selectedSectionName = MainSection.SPEECH.name },
                                icon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null) },
                                label = { Text("语音") },
                                colors = itemColors,
                            )
                        }
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
                                readingContent = { documentId, title, paragraphs, index, sentence ->
                                    val request = remember(documentId, title, paragraphs, index, sentence) {
                                        createInlineReadingRequest(documentId, title, paragraphs, index, sentence)
                                    }
                                    if (request != null) {
                                        SpeechInlineReadingFeature(documentId = documentId, request = request)
                                    } else {
                                        Text("当前朗读范围无效，请重新选择原文。")
                                    }
                                },
                                playbackControl = { documentId, title, paragraphs, index ->
                                    val request = remember(documentId, title, paragraphs, index) {
                                        createPlaybackReadingRequest(documentId, title, paragraphs, index)
                                    }
                                    if (request != null) {
                                        SpeechReadingPlayButton(documentId = documentId, request = request)
                                    } else {
                                        Text("当前播放范围无效。")
                                    }
                                },
                            )

                            MainSection.DICTIONARY -> DictionaryFeature(
                                presentation = DictionaryPresentation.FULL,
                            )
                            MainSection.SPEECH -> SpeechSettingsFeature()
                        }
                    }
                }
            }
        }
    }
}
