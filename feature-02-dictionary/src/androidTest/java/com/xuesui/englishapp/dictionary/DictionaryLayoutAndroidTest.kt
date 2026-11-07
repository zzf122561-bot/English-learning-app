package com.xuesui.englishapp.dictionary

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xuesui.englishapp.dictionary.data.DictionaryEntity
import com.xuesui.englishapp.dictionary.data.DictionarySourceType
import com.xuesui.englishapp.dictionary.data.DictionaryStatus
import com.xuesui.englishapp.dictionary.data.DictionaryWithResources
import com.xuesui.englishapp.dictionary.query.DictionaryQuerySnapshot
import com.xuesui.englishapp.dictionary.query.DictionaryResultTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device tests for the actual state-driven screen; no dictionary files, database or audio needed. */
@RunWith(AndroidJUnit4::class)
class DictionaryLayoutAndroidTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun managementAt320DpWithDoubleFontKeepsLongNameAndActionsReachable() {
        verifyManagement(widthDp = 320, fontScale = 2f)
    }

    @Test
    fun managementAt360DpWithLargeFontKeepsLongNameAndActionsReachable() {
        verifyManagement(widthDp = 360, fontScale = 1.5f)
    }

    @Test
    fun lookupAt360DpWithDoubleFontSeparatesSearchHeadwordTabsAndDocument() {
        val searches = mutableListOf<String>()
        val selections = mutableListOf<String>()
        var pronunciationCount = 0
        val word = "pneumonoultramicroscopicsilicovolcanoconiosis"
        val tabs = listOf(
            DictionaryResultTab("imported", LONG_NAME, null, null),
            DictionaryResultTab("builtin", "第二本测试词典", null, null),
        )
        show(
            widthDp = 360,
            fontScale = 2f,
            state = DictionaryUiState(
                input = word,
                dictionaries = fixtures(),
                query = DictionaryQuerySnapshot(word, word, tabs, "imported", emptyList()),
            ),
            onSearch = { searches += it },
            onSelect = { selections += it },
            onPronounce = { pronunciationCount++ },
        )

        compose.onNodeWithTag("dictionary-search").assertIsDisplayed().performTextReplacement("apple")
        compose.onNodeWithContentDescription("查询").performClick()
        compose.runOnIdle { assertEquals(listOf("apple"), searches) }
        compose.onNodeWithText(word).performScrollTo().assertTextFits()
        compose.onNodeWithText("发音").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("dictionary-tabs").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(LONG_NAME).assertTextFits()
        compose.onNodeWithTag("dictionary-tabs").performScrollToNode(hasText("第二本测试词典"))
        compose.onNodeWithText("第二本测试词典").performClick()
        compose.onNodeWithTag("dictionary-document").assertIsDisplayed()

        val header = compose.onNodeWithTag("dictionary-lookup-header").fetchSemanticsNode().boundsInRoot
        val document = compose.onNodeWithTag("dictionary-document").fetchSemanticsNode().boundsInRoot
        assertTrue("Document must have its own vertical region", document.top >= header.bottom)
        assertTrue("Document remains visible with a large header", document.height > 0f)
        compose.runOnIdle {
            assertEquals(1, pronunciationCount)
            assertEquals(listOf("builtin"), selections)
        }
    }

    @Test
    fun quickLookupSystemBackAndToolbarShareOneCloseRequest() {
        verifyQuickReturn(systemFirst = true)
    }

    @Test
    fun quickLookupToolbarAndSystemBackShareOneCloseRequest() {
        verifyQuickReturn(systemFirst = false)
    }

    private fun verifyManagement(widthDp: Int, fontScale: Float) {
        val enabledChanges = mutableListOf<Pair<String, Boolean>>()
        val moves = mutableListOf<Pair<Int, Int>>()
        val fontChanges = mutableListOf<Pair<String, Int>>()
        val deletions = mutableListOf<String>()
        show(
            widthDp = widthDp,
            fontScale = fontScale,
            state = DictionaryUiState(dictionaries = fixtures(), managing = true),
            onEnabled = { id, enabled -> enabledChanges += id to enabled },
            onMove = { from, to -> moves += from to to },
            onFont = { id, level -> fontChanges += id to level },
            onDelete = { deletions += it },
        )

        compose.onNodeWithText("导入文件夹").assertIsDisplayed()
        compose.onNodeWithTag("dictionary-name-imported").performScrollTo().assertTextFits(expectWrap = true)
        compose.onNodeWithTag("dictionary-enabled-imported").performScrollTo().assertIsDisplayed().performClick()
        inImportedCard(hasContentDescription("上移")).performScrollTo().assertIsNotEnabled()
        inImportedCard(hasContentDescription("下移")).performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("dictionary-font-imported").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertTrue(fontChanges.isEmpty()) }
        compose.onNodeWithTag("dictionary-font-imported").performClick()
        compose.onNodeWithText("保存").performClick()
        inImportedCard(hasText("删除导入词典")).performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue("Deletion still needs confirmation", deletions.isEmpty()) }
        compose.onNodeWithText("删除", substring = false).performClick()
        compose.onNodeWithTag("dictionary-management-list").performScrollToNode(hasText("内置词典不可删除"))
        compose.onNodeWithText("内置词典不可删除").assertIsDisplayed()
        compose.onNode(
            hasText("删除导入词典") and hasAnyAncestor(hasTestTag("dictionary-card-builtin")),
        ).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf("imported" to false), enabledChanges)
            assertEquals(listOf(0 to 1), moves)
            assertEquals(listOf("imported" to 7), fontChanges)
            assertEquals(listOf("imported"), deletions)
        }
    }

    private fun verifyQuickReturn(systemFirst: Boolean) {
        var closes = 0
        var managementChanges = 0
        show(
            widthDp = 320,
            fontScale = 2f,
            state = DictionaryUiState(dictionaries = fixtures(), managing = true),
            presentation = DictionaryPresentation.QUICK_LOOKUP,
            onClose = { closes++ },
            onManagement = { managementChanges++ },
        )
        compose.onNodeWithText("快速查词").assertIsDisplayed()
        compose.onNodeWithText("词典管理").assertDoesNotExist()
        if (systemFirst) {
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithContentDescription("返回").performClick()
        } else {
            compose.onNodeWithContentDescription("返回").performClick()
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        }
        compose.runOnIdle {
            assertEquals(1, closes)
            assertEquals(0, managementChanges)
        }
    }

    private fun show(
        widthDp: Int,
        fontScale: Float,
        state: DictionaryUiState,
        presentation: DictionaryPresentation = DictionaryPresentation.FULL,
        onClose: () -> Unit = {},
        onManagement: (Boolean) -> Unit = {},
        onEnabled: (String, Boolean) -> Unit = { _, _ -> },
        onMove: (Int, Int) -> Unit = { _, _ -> },
        onFont: (String, Int) -> Unit = { _, _ -> },
        onDelete: (String) -> Unit = {},
        onSearch: (String) -> Unit = {},
        onSelect: (String) -> Unit = {},
        onPronounce: () -> Unit = {},
    ) {
        compose.setContent {
            val density = LocalDensity.current
            var input by remember { mutableStateOf(state.input) }
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF315DA8))) {
                    Box(Modifier.requiredWidth(widthDp.dp).fillMaxHeight()) {
                        DictionaryScreen(
                            state = state.copy(input = input),
                            presentation = presentation,
                            onClose = onClose,
                            snackbar = remember { SnackbarHostState() },
                            onManagement = onManagement,
                            onImport = {},
                            onEnabled = onEnabled,
                            onFontLevel = onFont,
                            onMove = onMove,
                            onDelete = onDelete,
                            onInput = { input = it },
                            onSearch = onSearch,
                            onSelectDictionary = onSelect,
                            onPronounce = onPronounce,
                            onAudioAction = {},
                            onInternalLookup = {},
                            resourceReader = { null },
                            onResourceError = {},
                            onNavigationBlocked = {},
                        )
                    }
                }
            }
        }
    }

    private fun inImportedCard(matcher: androidx.compose.ui.test.SemanticsMatcher) =
        compose.onNode(matcher and hasAnyAncestor(hasTestTag("dictionary-card-imported")))

    private fun SemanticsNodeInteraction.assertTextFits(expectWrap: Boolean = false) {
        val layouts = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Must inspect actual Text layout", layouts.isNotEmpty())
        layouts.forEach { layout ->
            assertFalse("Text must not overflow horizontally", layout.didOverflowWidth)
            assertFalse("Text must not be cropped vertically", layout.didOverflowHeight)
            if (expectWrap) assertTrue("Long names must wrap", layout.lineCount > 1)
        }
    }

    private fun fixtures() = listOf(
        fixture("imported", LONG_NAME, DictionarySourceType.IMPORTED),
        fixture("builtin", "内置测试词典", DictionarySourceType.BUILTIN),
    )

    private fun fixture(id: String, name: String, source: String) = DictionaryWithResources(
        dictionary = DictionaryEntity(
            id = id,
            displayName = name,
            sourceType = source,
            privateMdxPath = "/unused/$id.mdx",
            mdxSha256 = id,
            enabled = true,
            displayOrder = if (id == "imported") 0 else 1,
            formatVersion = "2.0",
            runtimeStatus = DictionaryStatus.READY,
            createdAt = 0,
            updatedAt = 0,
            fontLevel = 7,
        ),
        resources = emptyList(),
    )

    private companion object {
        const val LONG_NAME = "用于验证窄屏与大字号完整换行的超长双解学习词典名称 Long Dictionary Name"
    }
}
