package com.xuesui.englishapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xuesui.englishapp.data.NotebookEntity
import com.xuesui.englishapp.data.StudySegmentEntity
import com.xuesui.englishapp.data.StudySegmentWithTargets
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WordMemoryResponsiveLayoutUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun libraryAt360DpKeepsLongTitleProgressAndActionsReachable() {
        val notebook = notebook(
            title = "A Very Long English Story Title That Must Use Two Lines Without Covering Its Actions",
        )

        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.5f)) {
                    Box(Modifier.requiredWidth(360.dp).requiredHeight(900.dp)) {
                        LibraryScreen(
                            notebooks = listOf(notebook),
                            onImport = {},
                            onOpen = {},
                            onRename = { _, _ -> },
                            onDelete = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("notebook-card-42").assertIsDisplayed()
        composeRule.onNodeWithTag("notebook-title-42").assertIsDisplayed()
        composeRule.onNodeWithText("上次读到第 18 段 · 共 118 段").assertIsDisplayed()
        composeRule.onNodeWithTag("continue-notebook-42").assertIsDisplayed()
        composeRule.onNodeWithTag("notebook-menu-42").assertIsDisplayed()
    }

    @Test
    fun studyAt320DpAndLargeFontSeparatesMetadataBodyAndWrappedActions() {
        val notebook = notebook(
            title = "The Extremely Long 2026 Compass Story Title That Must Never Cover Playback Controls",
        ).copy(fontLevel = 10)
        val segment = StudySegmentEntity(
            id = 7,
            notebookId = notebook.id,
            position = 0,
            englishText = "In 2026, the learner reads a deliberately long English paragraph at the largest saved study font.",
            chineseText = "【中文译文】学习者用最大的已保存字号阅读一段较长的英文正文。",
        )

        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.6f)) {
                    Box(Modifier.requiredWidth(320.dp).requiredHeight(1000.dp)) {
                        StudyScreen(
                            notebook = notebook,
                            segments = listOf(StudySegmentWithTargets(segment, emptyList())),
                            onBack = {},
                            onVisibilityChange = { _, _ -> },
                            onDictationChange = { _, _ -> },
                            onPositionChange = {},
                            onFontLevelChange = {},
                            onLookupRequested = {},
                            onReadingRequested = { _, _, _, _ -> },
                            readingContent = { _, _, _, _, _ -> Text("生成摘要") },
                            playbackControl = { _, _, _, paragraphIndex ->
                                Button(
                                    onClick = {},
                                    modifier = Modifier.testTag("responsive-playback-${paragraphIndex ?: "full"}"),
                                ) {
                                    Text(if (paragraphIndex == null) "播放全文" else "播放本段")
                                }
                            },
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("study-title").assertIsDisplayed()
        composeRule.onNodeWithTag("font-size-control").assertIsDisplayed()
        composeRule.onNodeWithTag("full-reading-menu").assertIsDisplayed()
        composeRule.onNodeWithTag("responsive-playback-full").assertIsDisplayed()

        composeRule.onNodeWithTag("segment-primary-actions-0").performScrollTo()
        composeRule.onNodeWithText("显示本段中文").assertIsDisplayed()
        composeRule.onNodeWithTag("responsive-playback-0").assertIsDisplayed()
        composeRule.onNodeWithTag("paragraph-reading-0").assertIsDisplayed()

        val metadata = composeRule.onNodeWithTag("segment-metadata-0").fetchSemanticsNode().boundsInRoot
        val body = composeRule.onNodeWithTag("segment-english-0").fetchSemanticsNode().boundsInRoot
        assertTrue("Paragraph body must start below its metadata block", metadata.bottom <= body.top)
        assertTrue("Paragraph body should retain useful reading width", body.width >= 220f)
    }

    private fun notebook(title: String) = NotebookEntity(
        id = 42,
        title = title,
        originalFileName = "long-title-fixture.docx",
        segmentCount = 118,
        targetCount = 1412,
        lastSegmentIndex = 17,
        createdAt = 1,
        updatedAt = 1,
    )
}
