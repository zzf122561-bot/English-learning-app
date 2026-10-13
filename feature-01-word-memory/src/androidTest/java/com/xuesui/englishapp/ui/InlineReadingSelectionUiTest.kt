package com.xuesui.englishapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xuesui.englishapp.data.NotebookEntity
import com.xuesui.englishapp.data.StudySegmentEntity
import com.xuesui.englishapp.data.StudySegmentWithTargets
import com.xuesui.englishapp.data.TargetEntity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InlineReadingSelectionUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun firstParagraphSelectionUpdatesInlineSlotWithCleanTextAndVisibleNextStep() {
        val stored = "In 2026, A0001. articulate costs 3.14 dollars."
        val marked = "A0001. articulate"
        val start = stored.indexOf(marked)
        val notebook = NotebookEntity(
            id = 42,
            title = "Notebook",
            originalFileName = "fixture.docx",
            segmentCount = 1,
            targetCount = 1,
            createdAt = 1,
            updatedAt = 1,
        )
        val segment = StudySegmentEntity(
            id = 7,
            notebookId = notebook.id,
            position = 0,
            englishText = stored,
            chineseText = "【中文译文】",
        )
        val target = TargetEntity(
            id = 9,
            notebookId = notebook.id,
            segmentId = segment.id,
            key = "A0001",
            english = "articulate",
            chinese = "清晰表达",
            englishStart = start,
            englishEnd = start + marked.length,
            chineseStart = 0,
            chineseEnd = 0,
            position = 0,
        )

        composeRule.setContent {
            MaterialTheme {
                StudyScreen(
                    notebook = notebook,
                    segments = listOf(StudySegmentWithTargets(segment, listOf(target))),
                    onBack = {},
                    onVisibilityChange = { _, _ -> },
                    onDictationChange = { _, _ -> },
                    onPositionChange = {},
                    onFontLevelChange = {},
                    onLookupRequested = {},
                    onReadingRequested = { _, _, _, _ -> error("Inline selection must not use legacy callback") },
                    readingContent = { documentId, _, paragraphs, paragraphIndex, sentenceText ->
                        Text("slot=$documentId|index=$paragraphIndex|sentence=$sentenceText|text=${paragraphs.single()}")
                    },
                )
            }
        }

        composeRule.onNodeWithTag("paragraph-reading-0").performClick()
        composeRule.onNodeWithText("选择朗读范围").assertIsDisplayed()
        composeRule.onNodeWithTag("reading-selection-confirm").performClick()

        composeRule.onNodeWithText("朗读范围已选为第 1 段，请点击下方“生成朗读”。").assertIsDisplayed()
        composeRule.onNodeWithText(
            "slot=word-memory:42|index=0|sentence=null|text=In 2026, articulate costs 3.14 dollars.",
        ).assertIsDisplayed()
    }

    @Test
    fun playbackSlotsReceiveCleanFullAndParagraphContextsWhileGenerationStaysSeparate() {
        val stored = "In 2026, A0001. articulate costs 3.14 dollars."
        val marked = "A0001. articulate"
        val start = stored.indexOf(marked)
        val notebook = NotebookEntity(
            id = 42,
            title = "Notebook",
            originalFileName = "story-docx-v1.docx",
            segmentCount = 1,
            targetCount = 1,
            createdAt = 1,
            updatedAt = 1,
        )
        val segment = StudySegmentEntity(
            id = 7,
            notebookId = notebook.id,
            position = 0,
            englishText = stored,
            chineseText = "【中文译文】A0001. 清晰表达。",
        )
        val target = TargetEntity(
            id = 9,
            notebookId = notebook.id,
            segmentId = segment.id,
            key = "A0001",
            english = "articulate",
            chinese = "清晰表达",
            englishStart = start,
            englishEnd = start + marked.length,
            chineseStart = "【中文译文】".length,
            chineseEnd = "【中文译文】A0001. 清晰表达".length,
            position = 0,
        )

        composeRule.setContent {
            MaterialTheme {
                StudyScreen(
                    notebook = notebook,
                    segments = listOf(StudySegmentWithTargets(segment, listOf(target))),
                    onBack = {},
                    onVisibilityChange = { _, _ -> },
                    onDictationChange = { _, _ -> },
                    onPositionChange = {},
                    onFontLevelChange = {},
                    onLookupRequested = {},
                    onReadingRequested = { _, _, _, _ -> error("Injected controls must not use legacy callback") },
                    readingContent = { _, _, _, paragraphIndex, _ ->
                        Text(
                            "generation-index=$paragraphIndex",
                            modifier = Modifier.testTag("generation-${paragraphIndex ?: "full"}"),
                        )
                    },
                    playbackControl = { documentId, _, paragraphs, paragraphIndex ->
                        Text(
                            "playback=$documentId|index=$paragraphIndex|text=${paragraphs.single()}",
                            modifier = Modifier.testTag("playback-${paragraphIndex ?: "full"}"),
                        )
                    },
                )
            }
        }

        composeRule.onNodeWithTag("playback-full").assertIsDisplayed()
        composeRule.onNodeWithTag("playback-0").fetchSemanticsNode()
        composeRule.onNodeWithText(
            "playback=word-memory:42|index=null|text=In 2026, articulate costs 3.14 dollars.",
        ).assertIsDisplayed()

        composeRule.onNodeWithTag("paragraph-reading-0").performClick()
        composeRule.onNodeWithTag("reading-selection-confirm").performClick()
        composeRule.onNodeWithTag("generation-0").fetchSemanticsNode()

        composeRule.onNodeWithTag("full-reading-menu").performClick()
        composeRule.onNodeWithTag("generate-full-reading").performClick()
        composeRule.onNodeWithTag("generation-full").fetchSemanticsNode()
    }
}
