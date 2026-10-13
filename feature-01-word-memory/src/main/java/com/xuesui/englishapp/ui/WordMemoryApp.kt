package com.xuesui.englishapp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuesui.englishapp.ImportState
import com.xuesui.englishapp.WordMemoryViewModel
import com.xuesui.englishapp.data.NotebookEntity
import com.xuesui.englishapp.data.StudySegmentWithTargets
import com.xuesui.englishapp.data.TargetEntity
import com.xuesui.englishapp.study.StudyFontScale
import com.xuesui.englishapp.study.ReadingParagraphPreparation
import com.xuesui.englishapp.study.ReadingTargetMarker
import com.xuesui.englishapp.study.buildReadingRequest
import com.xuesui.englishapp.study.deliverReadingRequest
import com.xuesui.englishapp.study.findLatinWordAtOffset
import com.xuesui.englishapp.study.prepareReadingParagraph
import com.xuesui.englishapp.study.readingSelectionNotice
import com.xuesui.englishapp.study.splitEnglishSentences
import com.xuesui.englishapp.study.wordMemoryDocumentId
import com.xuesui.englishapp.ui.theme.Cobalt
import com.xuesui.englishapp.ui.theme.Hairline
import com.xuesui.englishapp.ui.theme.Ink
import com.xuesui.englishapp.ui.theme.Paper
import com.xuesui.englishapp.ui.theme.Slate
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun WordMemoryApp(
    model: WordMemoryViewModel,
    onLookupRequested: (String) -> Unit,
    onReadingRequested: (String, List<String>, Int?, String?) -> Unit,
    readingContent: (@Composable (
        documentId: String,
        title: String,
        englishParagraphs: List<String>,
        paragraphIndex: Int?,
        sentenceText: String?,
    ) -> Unit)?,
    playbackControl: (@Composable (
        documentId: String,
        title: String,
        englishParagraphs: List<String>,
        paragraphIndex: Int?,
    ) -> Unit)?,
) {
    val notebooks by model.notebooks.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeNotebook by model.activeNotebook.collectAsStateWithLifecycle(initialValue = null)
    val segments by model.activeSegments.collectAsStateWithLifecycle(initialValue = emptyList())
    val importState by model.importState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { model.importDocx(context.contentResolver, it) }
    }

    if (activeNotebook == null) {
        LibraryScreen(
            notebooks = notebooks,
            onImport = { picker.launch(arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) },
            onOpen = model::openNotebook,
            onRename = model::renameNotebook,
            onDelete = model::deleteNotebook,
        )
    } else {
        StudyScreen(
            notebook = activeNotebook!!,
            segments = segments,
            onBack = model::closeNotebook,
            onVisibilityChange = model::setChineseVisible,
            onDictationChange = model::updateDictation,
            onPositionChange = model::savePosition,
            onFontLevelChange = model::setFontLevel,
            onLookupRequested = onLookupRequested,
            onReadingRequested = onReadingRequested,
            readingContent = readingContent,
            playbackControl = playbackControl,
        )
    }

    when (val state = importState) {
        ImportState.Idle -> Unit
        is ImportState.Reading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("正在整理学习档案") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(buildString {
                        append("正在读取 ${state.fileName}")
                        state.percent?.let { append("  $it%") }
                        append("\n完成前不会写入半份笔记。")
                    })
                }
            },
            confirmButton = { TextButton(onClick = model::cancelImport) { Text("取消") } },
        )
        is ImportState.Failed -> AlertDialog(
            onDismissRequest = model::clearImportError,
            title = { Text("这份 Word 暂时无法导入") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = model::clearImportError) { Text("知道了") } },
        )
    }
}

@Composable
private fun LibraryScreen(
    notebooks: List<NotebookEntity>,
    onImport: () -> Unit,
    onOpen: (Long) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var renameTarget by remember { mutableStateOf<NotebookEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<NotebookEntity?>(null) }
    Scaffold(containerColor = Paper) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp)) {
                Text("语境记忆", style = MaterialTheme.typography.displaySmall, color = Ink)
                Text("你的私人学习档案", style = MaterialTheme.typography.bodyMedium, color = Slate)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onImport) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("导入 Word 笔记本")
                }
            }
            HorizontalDivider(color = Hairline)
            if (notebooks.isEmpty()) {
                EmptyLibrary(Modifier.fillMaxSize())
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text(
                            "学习档案  ${notebooks.size.toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Slate,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                        )
                    }
                    items(notebooks, key = { it.id }) { notebook ->
                        NotebookRow(
                            notebook = notebook,
                            onOpen = { onOpen(notebook.id) },
                            onRename = { renameTarget = notebook },
                            onDelete = { deleteTarget = notebook },
                        )
                    }
                    item { Spacer(Modifier.height(28.dp)) }
                }
            }
        }
    }
    renameTarget?.let { notebook -> RenameDialog(notebook, { renameTarget = null }) { title ->
        onRename(notebook.id, title); renameTarget = null
    } }
    deleteTarget?.let { notebook -> DeleteDialog(notebook, { deleteTarget = null }) {
        onDelete(notebook.id); deleteTarget = null
    } }
}

@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier) {
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("还没有学习档案", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("导入短文 Word 后，内容会保存在本机。", color = Slate)
        }
    }
}

@Composable
private fun NotebookRow(notebook: NotebookEntity, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 24.dp, end = 12.dp, top = 18.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(46.dp).background(Cobalt),
            contentAlignment = Alignment.Center,
        ) { Text("${notebook.lastSegmentIndex + 1}", color = Color.White, style = MaterialTheme.typography.labelMedium) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(notebook.title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text("${notebook.segmentCount} 段  ·  ${notebook.targetCount} 个目标", style = MaterialTheme.typography.labelMedium, color = Slate)
            Text(notebook.originalFileName, style = MaterialTheme.typography.bodyMedium, color = Slate, maxLines = 1)
        }
        IconButton(onClick = onRename) { Icon(Icons.Rounded.Edit, "重命名", tint = Slate) }
        IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "删除", tint = Slate) }
    }
    HorizontalDivider(color = Hairline, modifier = Modifier.padding(start = 86.dp))
}

@Composable
internal fun StudyScreen(
    notebook: NotebookEntity,
    segments: List<StudySegmentWithTargets>,
    onBack: () -> Unit,
    onVisibilityChange: (Long, Boolean) -> Unit,
    onDictationChange: (Long, String) -> Unit,
    onPositionChange: (Int) -> Unit,
    onFontLevelChange: (Int) -> Unit,
    onLookupRequested: (String) -> Unit,
    onReadingRequested: (String, List<String>, Int?, String?) -> Unit,
    readingContent: (@Composable (
        documentId: String,
        title: String,
        englishParagraphs: List<String>,
        paragraphIndex: Int?,
        sentenceText: String?,
    ) -> Unit)?,
    playbackControl: (@Composable (
        documentId: String,
        title: String,
        englishParagraphs: List<String>,
        paragraphIndex: Int?,
    ) -> Unit)? = null,
) {
    BackHandler(onBack = onBack)
    var displayedFontLevel by remember(notebook.id) {
        mutableIntStateOf(StudyFontScale.normalize(notebook.fontLevel))
    }
    var showFontDialog by remember(notebook.id) { mutableStateOf(false) }
    LaunchedEffect(notebook.fontLevel) {
        displayedFontLevel = StudyFontScale.normalize(notebook.fontLevel)
    }
    val startIndex = notebook.lastSegmentIndex.coerceIn(0, (notebook.segmentCount - 1).coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val orderedSegments = remember(segments) { segments.sortedBy { it.segment.position } }
    val preparedParagraphs = remember(orderedSegments) {
        orderedSegments.map { item ->
            prepareReadingParagraph(
                storedText = item.segment.englishText,
                targets = item.targets.map { target ->
                    ReadingTargetMarker(
                        key = target.key,
                        english = target.english,
                        start = target.englishStart,
                        end = target.englishEnd,
                    )
                },
            )
        }
    }
    val readingPreparationError = preparedParagraphs
        .mapIndexedNotNull { index, preparation ->
            (preparation as? ReadingParagraphPreparation.Invalid)?.let {
                "第 ${index + 1} 段暂时无法朗读：${it.reason}"
            }
        }
        .firstOrNull()
    val englishParagraphs = if (readingPreparationError == null) {
        preparedParagraphs.map { (it as ReadingParagraphPreparation.Ready).text }
    } else {
        emptyList()
    }
    val currentReadingRequested by rememberUpdatedState(onReadingRequested)
    var selectedParagraphIndex by rememberSaveable(notebook.id) { mutableIntStateOf(FULL_ARTICLE_SELECTION) }
    var selectedSentenceText by rememberSaveable(notebook.id) { mutableStateOf<String?>(null) }
    var selectionNotice by remember(notebook.id) { mutableStateOf<String?>(null) }
    val submitReadingRequest = { paragraphIndex: Int?, sentenceText: String? ->
        if (readingPreparationError != null) {
            selectionNotice = readingPreparationError
        } else {
            val request = deliverReadingRequest(
                title = notebook.title,
                englishParagraphs = englishParagraphs,
                paragraphIndex = paragraphIndex,
                sentenceText = sentenceText,
                inlineContentAvailable = readingContent != null,
                onReadingRequested = currentReadingRequested,
            )
            when {
                request == null -> selectionNotice = "无法选择此朗读范围，请重新选择。"
                readingContent != null -> {
                    selectedParagraphIndex = request.paragraphIndex ?: FULL_ARTICLE_SELECTION
                    selectedSentenceText = request.sentenceText
                    selectionNotice = readingSelectionNotice(request.paragraphIndex, request.sentenceText)
                }
            }
        }
    }
    val selectedReadingRequest = remember(
        notebook.title,
        englishParagraphs,
        selectedParagraphIndex,
        selectedSentenceText,
    ) {
        if (englishParagraphs.isEmpty()) {
            null
        } else {
            buildReadingRequest(
                title = notebook.title,
                englishParagraphs = englishParagraphs,
                paragraphIndex = selectedParagraphIndex.takeIf { it >= 0 },
                sentenceText = selectedSentenceText,
            )
        }
    }
    LaunchedEffect(notebook.id, englishParagraphs, selectedParagraphIndex, selectedSentenceText) {
        if (englishParagraphs.isNotEmpty() && selectedReadingRequest == null) {
            selectedParagraphIndex = FULL_ARTICLE_SELECTION
            selectedSentenceText = null
            selectionNotice = "文章内容已更新，朗读范围已重置为全文。"
        }
    }
    LaunchedEffect(listState, notebook.id) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect(onPositionChange)
    }
    Scaffold(containerColor = Paper) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            StudyHeader(
                notebook = notebook,
                englishParagraphs = englishParagraphs,
                documentId = wordMemoryDocumentId(notebook.id),
                readingContentAvailable = readingContent != null,
                playbackControl = playbackControl,
                onBack = onBack,
                onGenerateFull = { submitReadingRequest(null, null) },
                onShowFontDialog = { showFontDialog = true },
            )
            HorizontalDivider(color = Hairline)
            if (segments.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
                    itemsIndexed(orderedSegments, key = { _, item -> item.segment.id }) { paragraphIndex, item ->
                        StudySegment(
                            data = item,
                            onVisibilityChange = { onVisibilityChange(item.segment.id, it) },
                            onDictationChange = { onDictationChange(item.segment.id, it) },
                            fontLevel = displayedFontLevel,
                            onLookupRequested = onLookupRequested,
                            paragraphIndex = paragraphIndex,
                            readingParagraphText = englishParagraphs.getOrNull(paragraphIndex).orEmpty(),
                            readingEnabled = readingPreparationError == null,
                            inlineSelectionEnabled = readingContent != null,
                            onReadingRequested = { sentenceText ->
                                submitReadingRequest(paragraphIndex, sentenceText)
                            },
                            playbackControl = if (playbackControl != null && readingPreparationError == null) {
                                {
                                    playbackControl(
                                        wordMemoryDocumentId(notebook.id),
                                        notebook.title,
                                        englishParagraphs,
                                        paragraphIndex,
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                    item { Spacer(Modifier.height(42.dp)) }
                }
                if (readingContent != null) {
                    HorizontalDivider(color = Hairline)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .background(Paper)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        (readingPreparationError ?: selectionNotice)?.let { notice ->
                            Text(notice, style = MaterialTheme.typography.labelMedium, color = Slate)
                            Spacer(Modifier.height(6.dp))
                        }
                        selectedReadingRequest?.let { request ->
                            readingContent(
                                wordMemoryDocumentId(notebook.id),
                                request.title,
                                request.englishParagraphs,
                                request.paragraphIndex,
                                request.sentenceText,
                            )
                        }
                    }
                } else if (readingPreparationError != null) {
                    Text(
                        readingPreparationError,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
    if (showFontDialog) {
        FontSizeDialog(
            fontLevel = displayedFontLevel,
            onLevelChange = { requestedLevel ->
                displayedFontLevel = StudyFontScale.normalize(requestedLevel)
                onFontLevelChange(displayedFontLevel)
            },
            onDismiss = { showFontDialog = false },
        )
    }
}

private const val FULL_ARTICLE_SELECTION = -1

@Composable
private fun StudyHeader(
    notebook: NotebookEntity,
    documentId: String,
    englishParagraphs: List<String>,
    readingContentAvailable: Boolean,
    playbackControl: (@Composable (
        documentId: String,
        title: String,
        englishParagraphs: List<String>,
        paragraphIndex: Int?,
    ) -> Unit)?,
    onBack: () -> Unit,
    onGenerateFull: () -> Unit,
    onShowFontDialog: () -> Unit,
) {
    var generationMenuExpanded by remember(documentId) { mutableStateOf(false) }

    @Composable
    fun Title(modifier: Modifier) {
        Column(modifier) {
            Text(notebook.title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(
                "${notebook.segmentCount} 段  ·  ${notebook.targetCount} 个目标",
                style = MaterialTheme.typography.labelMedium,
                color = Slate,
            )
        }
    }

    @Composable
    fun Actions() {
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (playbackControl == null) {
                TextButton(
                    onClick = onGenerateFull,
                    enabled = englishParagraphs.isNotEmpty(),
                ) {
                    Text(if (readingContentAvailable) "选全文" else "全文朗读")
                }
            } else {
                if (englishParagraphs.isNotEmpty()) {
                    playbackControl(documentId, notebook.title, englishParagraphs, null)
                }
                Box {
                    IconButton(
                        onClick = { generationMenuExpanded = true },
                        modifier = Modifier.testTag("full-reading-menu"),
                        enabled = englishParagraphs.isNotEmpty(),
                    ) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "更多朗读操作")
                    }
                    DropdownMenu(
                        expanded = generationMenuExpanded,
                        onDismissRequest = { generationMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("生成全文") },
                            onClick = {
                                generationMenuExpanded = false
                                onGenerateFull()
                            },
                            modifier = Modifier.testTag("generate-full-reading"),
                        )
                    }
                }
            }
            IconButton(onClick = onShowFontDialog) {
                Text("Aa", fontWeight = FontWeight.Bold)
            }
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxWidth().background(Paper).padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        if (maxWidth < 560.dp && playbackControl != null) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
                    Title(Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Actions()
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
                Title(Modifier.weight(1f))
                Actions()
            }
        }
    }
}

@Composable
private fun StudySegment(
    data: StudySegmentWithTargets,
    onVisibilityChange: (Boolean) -> Unit,
    onDictationChange: (String) -> Unit,
    fontLevel: Int,
    onLookupRequested: (String) -> Unit,
    paragraphIndex: Int,
    readingParagraphText: String,
    readingEnabled: Boolean,
    inlineSelectionEnabled: Boolean,
    onReadingRequested: (String?) -> Unit,
    playbackControl: (@Composable () -> Unit)? = null,
) {
    val segment = data.segment
    val targets = data.targets.sortedBy { it.position }
    val textSize = StudyFontScale.sizeFor(fontLevel)
    val studyTextStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = textSize.fontSizeSp.sp,
        lineHeight = textSize.lineHeightSp.sp,
    )
    var draft by remember(segment.id, segment.dictationText) { mutableStateOf(segment.dictationText) }
    var englishLayout by remember(segment.id, segment.englishText) { mutableStateOf<TextLayoutResult?>(null) }
    var showReadingDialog by remember(segment.id) { mutableStateOf(false) }
    val currentLookupRequested by rememberUpdatedState(onLookupRequested)
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 26.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("段落 ${(segment.position + 1).toString().padStart(3, '0')}", style = MaterialTheme.typography.labelMedium, color = Cobalt)
            Spacer(Modifier.width(12.dp))
            HorizontalDivider(Modifier.weight(1f), color = Hairline)
            Spacer(Modifier.width(12.dp))
            Text("${targets.size} TARGETS", style = MaterialTheme.typography.labelMedium, color = Slate)
            Spacer(Modifier.width(6.dp))
            TextButton(
                onClick = { showReadingDialog = true },
                modifier = Modifier.testTag("paragraph-reading-$paragraphIndex"),
                enabled = readingEnabled,
            ) {
                Text(if (inlineSelectionEnabled) "选范围" else "朗读")
            }
        }
        Spacer(Modifier.height(19.dp))
        Text(
            text = highlightedText(segment.englishText, targets, false),
            modifier = Modifier.pointerInput(segment.id, segment.englishText) {
                detectTapGestures(
                    onLongPress = { position ->
                        val textOffset = englishLayout?.getOffsetForPosition(position) ?: return@detectTapGestures
                        findLatinWordAtOffset(segment.englishText, textOffset)?.let(currentLookupRequested)
                    },
                )
            },
            style = studyTextStyle,
            color = Ink,
            onTextLayout = { englishLayout = it },
        )
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it; onDictationChange(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("我的默写") },
            placeholder = { Text("只看英文，在这里写下你记住的中文或目标词义") },
            textStyle = studyTextStyle,
            minLines = 3,
        )
        Spacer(Modifier.height(14.dp))
        SegmentTranslationActions(
            chineseVisible = segment.chineseVisible,
            onVisibilityChange = onVisibilityChange,
            playbackControl = playbackControl,
        )
        if (segment.chineseVisible) {
            Spacer(Modifier.height(16.dp))
            Text(highlightedText(segment.chineseText, targets, true), style = studyTextStyle, color = Ink)
        }
    }
    if (showReadingDialog) {
        ReadingSelectionDialog(
            paragraphText = readingParagraphText,
            inlineSelectionEnabled = inlineSelectionEnabled,
            onDismiss = { showReadingDialog = false },
            onConfirm = { sentenceText ->
                onReadingRequested(sentenceText)
                showReadingDialog = false
            },
        )
    }
    HorizontalDivider(color = Hairline)
}

@Composable
private fun SegmentTranslationActions(
    chineseVisible: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
    playbackControl: (@Composable () -> Unit)?,
) {
    @Composable
    fun TranslationButton(modifier: Modifier) {
        OutlinedButton(
            onClick = { onVisibilityChange(!chineseVisible) },
            modifier = modifier,
            border = BorderStroke(1.dp, Hairline),
        ) {
            Icon(if (chineseVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (chineseVisible) "隐藏本段中文" else "显示本段中文")
        }
    }

    if (playbackControl == null) {
        TranslationButton(Modifier.fillMaxWidth())
        return
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 520.dp) {
            Column(Modifier.fillMaxWidth()) {
                TranslationButton(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    playbackControl()
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TranslationButton(Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                playbackControl()
            }
        }
    }
}

@Composable
private fun ReadingSelectionDialog(
    paragraphText: String,
    inlineSelectionEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    val sentences = remember(paragraphText) { splitEnglishSentences(paragraphText) }
    var selectedSentenceIndex by remember(paragraphText) { mutableIntStateOf(-1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择朗读范围") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                item {
                    ReadingChoice(
                        selected = selectedSentenceIndex == -1,
                        label = "本段",
                        content = paragraphText,
                        onClick = { selectedSentenceIndex = -1 },
                    )
                }
                itemsIndexed(sentences) { index, sentence ->
                    ReadingChoice(
                        selected = selectedSentenceIndex == index,
                        label = "第 ${index + 1} 句",
                        content = sentence,
                        onClick = { selectedSentenceIndex = index },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(sentences.getOrNull(selectedSentenceIndex)) },
                modifier = Modifier.testTag("reading-selection-confirm"),
            ) {
                Text(if (inlineSelectionEnabled) "确认范围" else "开始朗读")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ReadingChoice(
    selected: Boolean,
    label: String,
    content: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = Cobalt)
            Spacer(Modifier.height(4.dp))
            Text(content, style = MaterialTheme.typography.bodyMedium, color = Ink)
        }
    }
}

@Composable
private fun FontSizeDialog(
    fontLevel: Int,
    onLevelChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val normalizedLevel = StudyFontScale.normalize(fontLevel)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调整学习字号") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("档位 $normalizedLevel / ${StudyFontScale.MAX_LEVEL}")
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onLevelChange(normalizedLevel - 1) },
                        enabled = normalizedLevel > StudyFontScale.MIN_LEVEL,
                    ) {
                        Text("−", style = MaterialTheme.typography.headlineSmall)
                    }
                    Spacer(Modifier.width(24.dp))
                    Text(normalizedLevel.toString(), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(24.dp))
                    IconButton(
                        onClick = { onLevelChange(normalizedLevel + 1) },
                        enabled = normalizedLevel < StudyFontScale.MAX_LEVEL,
                    ) {
                        Text("+", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

private fun highlightedText(text: String, targets: List<TargetEntity>, chinese: Boolean) = buildAnnotatedString {
    append(text)
    targets.forEach { target ->
        val start = if (chinese) target.chineseStart else target.englishStart
        val end = if (chinese) target.chineseEnd else target.englishEnd
        if (start in 0..text.length && end in start..text.length) {
            addStyle(SpanStyle(color = Cobalt, fontWeight = FontWeight.Bold), start, end)
        }
    }
}

@Composable
private fun RenameDialog(notebook: NotebookEntity, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember(notebook.id) { mutableStateOf(notebook.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名学习档案") },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("标题") }) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun DeleteDialog(notebook: NotebookEntity, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除这本学习档案？") },
        text = { Text("“${notebook.title}”及其中的默写内容会从本机删除，此操作无法撤销。") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("保留") } },
    )
}
