package com.xuesui.englishapp.dictionary

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xuesui.englishapp.dictionary.data.DictionarySourceType
import com.xuesui.englishapp.dictionary.data.DictionaryStatus
import com.xuesui.englishapp.dictionary.data.DictionaryFontScale
import com.xuesui.englishapp.dictionary.data.DictionaryWithResources
import com.xuesui.englishapp.dictionary.engine.MdictResource
import com.xuesui.englishapp.dictionary.web.DictionaryAudioAction
import com.xuesui.englishapp.dictionary.web.SecureDictionaryWebView

enum class DictionaryPresentation {
    FULL,
    QUICK_LOOKUP,
}

internal class CloseRequestGate(private val close: () -> Unit) {
    private var requested = false
    fun request() {
        if (!requested) {
            requested = true
            close()
        }
    }
}

internal object DictionaryEntryPolicy {
    fun isManaging(presentation: DictionaryPresentation, storedManaging: Boolean): Boolean =
        presentation != DictionaryPresentation.QUICK_LOOKUP && storedManaging
}

internal class FontLevelDraft(originalLevel: Int) {
    val original: Int = DictionaryFontScale.normalize(originalLevel)
    var candidate: Int = original
        private set

    fun set(value: Int) { candidate = DictionaryFontScale.normalize(value) }
    fun decrease() = set(candidate - 1)
    fun increase() = set(candidate + 1)
    fun restoreDefault() = set(DictionaryFontScale.DEFAULT_LEVEL)
    fun save(): Int = candidate
    fun cancel(): Int = original
}

@Composable
fun DictionaryFeature(
    initialQuery: String? = null,
    presentation: DictionaryPresentation = DictionaryPresentation.FULL,
    onClose: () -> Unit = {},
) {
    val context = LocalContext.current
    val factory = remember(context, initialQuery) { DictionaryViewModel.factory(context, initialQuery) }
    val model: DictionaryViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(model, presentation, initialQuery) {
        model.enterPresentation(presentation, initialQuery)
    }

    DictionaryScreen(
        state = state,
        presentation = presentation,
        onClose = onClose,
        snackbar = snackbar,
        onManagement = model::showManagement,
        onImport = model::importDirectory,
        onEnabled = model::setEnabled,
        onFontLevel = model::setFontLevel,
        onMove = model::moveDictionary,
        onDelete = model::deleteImported,
        onInput = model::setInput,
        onSearch = model::submitQuery,
        onSelectDictionary = model::selectDictionary,
        onPronounce = model::pronounce,
        onAudioAction = model::pronounce,
        onInternalLookup = model::openInternalLink,
        resourceReader = model::readSelectedResource,
        onResourceError = model::reportResourceError,
        onNavigationBlocked = { model.reportMessage("已阻止外部或不安全链接") },
    )
}

/** The same state-driven UI is used by the public entry and layout/return instrumentation tests. */
@Composable
internal fun DictionaryScreen(
    state: DictionaryUiState,
    presentation: DictionaryPresentation,
    onClose: () -> Unit,
    snackbar: SnackbarHostState,
    onManagement: (Boolean) -> Unit,
    onImport: (android.net.Uri) -> Unit,
    onEnabled: (String, Boolean) -> Unit,
    onFontLevel: (String, Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDelete: (String) -> Unit,
    onInput: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSelectDictionary: (String) -> Unit,
    onPronounce: () -> Unit,
    onAudioAction: (DictionaryAudioAction) -> Unit,
    onInternalLookup: (String) -> Unit,
    resourceReader: (String) -> MdictResource?,
    onResourceError: (String) -> Unit,
    onNavigationBlocked: (String) -> Unit,
) {
    val latestClose by rememberUpdatedState(onClose)
    val closeGate = remember(presentation) { CloseRequestGate { latestClose() } }
    val isManaging = DictionaryEntryPolicy.isManaging(presentation, state.managing)

    BackHandler(enabled = isManaging) { onManagement(false) }
    BackHandler(enabled = presentation == DictionaryPresentation.QUICK_LOOKUP && !isManaging) {
        closeGate.request()
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        // The App shell owns system bars and bottom-navigation insets.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        if (isManaging) {
            DictionaryManagementPage(
                dictionaries = state.dictionaries,
                busy = state.busy,
                onBack = { onManagement(false) },
                onImport = onImport,
                onEnabled = onEnabled,
                onFontLevel = onFontLevel,
                onMove = onMove,
                onDelete = onDelete,
                modifier = Modifier.padding(padding),
            )
        } else {
            DictionaryLookupPage(
                state = state,
                presentation = presentation,
                onClose = closeGate::request,
                onInput = onInput,
                onSearch = onSearch,
                onManage = { onManagement(true) },
                onSelectDictionary = onSelectDictionary,
                onPronounce = onPronounce,
                onAudioAction = onAudioAction,
                onInternalLookup = onInternalLookup,
                resourceReader = resourceReader,
                onResourceError = onResourceError,
                onNavigationBlocked = onNavigationBlocked,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DictionaryLookupPage(
    state: DictionaryUiState,
    presentation: DictionaryPresentation,
    onClose: () -> Unit,
    onInput: (String) -> Unit,
    onSearch: (String) -> Unit,
    onManage: () -> Unit,
    onSelectDictionary: (String) -> Unit,
    onPronounce: () -> Unit,
    onAudioAction: (DictionaryAudioAction) -> Unit,
    onInternalLookup: (String) -> Unit,
    resourceReader: (String) -> MdictResource?,
    onResourceError: (String) -> Unit,
    onNavigationBlocked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val query = state.query
    Box(modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.TopCenter) {
        BoxWithConstraints(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
            val horizontalPadding = if (maxWidth < 360.dp) 16.dp else 20.dp
            val contentWidth = maxWidth - horizontalPadding * 2
            val headerMaxHeight = maxHeight * 0.60f
            Column(Modifier.fillMaxSize().padding(horizontal = horizontalPadding)) {
                // Keep the document visible while large text/landscape headers remain scrollable.
                Column(
                    Modifier.fillMaxWidth().heightIn(max = headerMaxHeight)
                        .testTag("dictionary-lookup-header")
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (presentation == DictionaryPresentation.QUICK_LOOKUP) {
                            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        }
                        Text(
                            if (presentation == DictionaryPresentation.FULL) "词典" else "快速查词",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onBackground,
                        )
                        if (presentation == DictionaryPresentation.FULL) {
                            TextButton(onClick = onManage, modifier = Modifier.heightIn(min = 48.dp)) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("管理", fontSize = 14.sp)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = onInput,
                        modifier = Modifier.fillMaxWidth().testTag("dictionary-search"),
                        singleLine = true,
                        label = { Text("查单词") },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
                        trailingIcon = {
                            IconButton(onClick = { onSearch(state.input) }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Search, contentDescription = "查询")
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch(state.input) }),
                    )
                    if (state.busy) DictionaryBusyIndicator()
                    if (query.displayQuery.isNotEmpty()) {
                        FlowRow(
                            Modifier.fillMaxWidth().testTag("dictionary-headword"),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                query.displayQuery,
                                modifier = Modifier.align(Alignment.CenterVertically),
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp,
                                color = colors.onSurface,
                            )
                            Button(onClick = onPronounce, modifier = Modifier.heightIn(min = 48.dp)) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("发音", fontSize = 14.sp)
                            }
                        }
                    }
                    if (query.tabs.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("dictionary-tabs"),
                        ) {
                            items(query.tabs.size, key = { query.tabs[it].dictionaryId }) { index ->
                                val tab = query.tabs[index]
                                FilterChip(
                                    selected = query.selectedDictionaryId == tab.dictionaryId,
                                    onClick = { onSelectDictionary(tab.dictionaryId) },
                                    modifier = Modifier.widthIn(max = contentWidth).heightIn(min = 48.dp),
                                    label = { Text(tab.displayName, modifier = Modifier.padding(vertical = 8.dp), fontSize = 14.sp) },
                                    leadingIcon = {
                                        Box(
                                            Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(
                                                if (tab.error == null) colors.primary else colors.error,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                    if (query.suggestions.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("未找到精确结果，试试：", fontSize = 14.sp, color = colors.onSurfaceVariant)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(query.suggestions.size) { index ->
                                    val suggestion = query.suggestions[index]
                                    AssistChip(
                                        onClick = { onInput(suggestion); onSearch(suggestion) },
                                        modifier = Modifier.widthIn(max = contentWidth).heightIn(min = 48.dp),
                                        label = { Text(suggestion, fontSize = 14.sp) },
                                    )
                                }
                            }
                        }
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 12.dp)
                        .testTag("dictionary-document"),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outlineVariant),
                ) {
                    val selectedResult = query.selectedResult
                    val selectedHtml = selectedResult?.html
                    val selectedError = selectedResult?.error
                    val noDictionaries = state.dictionaries.isEmpty() && !state.busy
                    if (selectedHtml != null && !noDictionaries) {
                        SecureDictionaryWebView(
                            html = selectedHtml,
                            modifier = Modifier.fillMaxSize(),
                            resourceReader = resourceReader,
                            onResourceError = onResourceError,
                            onInternalLookup = { onInput(it); onInternalLookup(it) },
                            onAudioAction = onAudioAction,
                            onNavigationBlocked = onNavigationBlocked,
                            fontLevel = selectedResult.fontLevel,
                        )
                    } else {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            when {
                                noDictionaries -> {
                                    Text("暂无内置词典", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                                    Text("可在词典管理中导入 MDX 文件夹", fontSize = 16.sp, color = colors.onSurfaceVariant)
                                    Button(onClick = onManage, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text("打开词典管理", fontSize = 14.sp)
                                    }
                                }
                                selectedError != null -> Text(selectedError, fontSize = 16.sp, color = colors.error)
                                query.displayQuery.isNotEmpty() -> Text("没有精确结果", fontSize = 16.sp, color = colors.onSurfaceVariant)
                                else -> Text("输入单词开始查询", fontSize = 16.sp, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DictionaryBusyIndicator() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text("正在准备词典…", modifier = Modifier.weight(1f), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DictionaryManagementPage(
    dictionaries: List<DictionaryWithResources>,
    busy: Boolean,
    onBack: () -> Unit,
    onImport: (android.net.Uri) -> Unit,
    onEnabled: (String, Boolean) -> Unit,
    onFontLevel: (String, Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onImport(it)
        }
    }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var fontDictionaryId by remember { mutableStateOf<String?>(null) }

    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.TopCenter) {
        BoxWithConstraints(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
            val horizontalPadding = if (maxWidth < 360.dp) 16.dp else 20.dp
            LazyColumn(
                Modifier.fillMaxSize().testTag("dictionary-management-list"),
                contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回查询")
                            }
                            Text(
                                "词典管理",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onBackground,
                            )
                        }
                        Text(
                            "启用的词典按下方顺序显示。拖动把手或使用箭头调整顺序。",
                            fontSize = 14.sp,
                            color = colors.onSurfaceVariant,
                        )
                        Button(
                            onClick = { launcher.launch(null) },
                            enabled = !busy,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("导入文件夹", fontSize = 14.sp)
                        }
                        if (busy) DictionaryBusyIndicator()
                    }
                }
                if (dictionaries.isEmpty()) {
                    item(key = "empty") { Text("暂无词典", Modifier.padding(vertical = 12.dp), color = colors.onSurfaceVariant) }
                }
                itemsIndexed(dictionaries, key = { _, item -> item.dictionary.id }) { index, item ->
                    DictionaryManagementCard(
                        item = item,
                        index = index,
                        count = dictionaries.size,
                        onEnabled = onEnabled,
                        onEditFont = { fontDictionaryId = item.dictionary.id },
                        onMove = onMove,
                        onDelete = { deleteId = item.dictionary.id },
                    )
                }
            }
        }
    }

    val deleting = dictionaries.firstOrNull { it.dictionary.id == deleteId }
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("删除导入词典？") },
            text = { Text("只删除 App 私有副本，不会修改所选文件夹中的原文件。") },
            confirmButton = {
                TextButton(onClick = { onDelete(deleting.dictionary.id); deleteId = null }) {
                    Text("删除", color = colors.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("取消") } },
        )
    }


    val fontDictionary = dictionaries.firstOrNull { it.dictionary.id == fontDictionaryId }
    if (fontDictionary != null) {
        FontLevelDialog(
            dictionaryName = fontDictionary.dictionary.displayName,
            initialLevel = fontDictionary.dictionary.fontLevel,
            onDismiss = { fontDictionaryId = null },
            onSave = { level ->
                onFontLevel(fontDictionary.dictionary.id, level)
                fontDictionaryId = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DictionaryManagementCard(
    item: DictionaryWithResources,
    index: Int,
    count: Int,
    onEnabled: (String, Boolean) -> Unit,
    onEditFont: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onDelete: () -> Unit,
) {
    val dictionary = item.dictionary
    val colors = MaterialTheme.colorScheme
    var dragDistance by remember(item.dictionary.id) { mutableFloatStateOf(0f) }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("dictionary-card-${dictionary.id}"),
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                dictionary.displayName,
                modifier = Modifier.fillMaxWidth().testTag("dictionary-name-${dictionary.id}"),
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = colors.onSurface,
            )
            val statusLabel = when (dictionary.runtimeStatus) {
                DictionaryStatus.READY -> "可用"
                DictionaryStatus.RESOURCE_MISSING -> "资源缺失"
                DictionaryStatus.ERROR -> "无法打开"
                else -> dictionary.runtimeStatus
            }
            Text(
                "第 ${index + 1} 位 · ${if (dictionary.sourceType == DictionarySourceType.BUILTIN) "内置词典" else "导入词典"} · $statusLabel",
                fontSize = 14.sp,
                color = colors.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("启用词典", modifier = Modifier.weight(1f), fontSize = 14.sp, color = colors.onSurface)
                Switch(
                    checked = dictionary.enabled,
                    onCheckedChange = { onEnabled(dictionary.id, it) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("dictionary-enabled-${dictionary.id}"),
                )
            }
            HorizontalDivider(color = colors.outlineVariant)
            FlowRow(
                Modifier.fillMaxWidth().testTag("dictionary-order-${dictionary.id}"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("结果顺序", modifier = Modifier.align(Alignment.CenterVertically), fontSize = 14.sp, color = colors.onSurfaceVariant)
                Row {
                    IconButton(
                        onClick = { onMove(index, index - 1) },
                        enabled = index > 0,
                        modifier = Modifier.size(48.dp),
                    ) { Icon(Icons.Default.ArrowUpward, contentDescription = "上移") }
                    IconButton(
                        onClick = { onMove(index, index + 1) },
                        enabled = index < count - 1,
                        modifier = Modifier.size(48.dp),
                    ) { Icon(Icons.Default.ArrowDownward, contentDescription = "下移") }
                    Box(
                        modifier = Modifier.size(48.dp).pointerInput(index, count) {
                            detectDragGestures(
                                onDragEnd = { dragDistance = 0f },
                                onDragCancel = { dragDistance = 0f },
                            ) { change, drag ->
                                change.consume()
                                dragDistance += drag.y
                                if (dragDistance > 42f && index < count - 1) {
                                    onMove(index, index + 1)
                                    dragDistance = 0f
                                } else if (dragDistance < -42f && index > 0) {
                                    onMove(index, index - 1)
                                    dragDistance = 0f
                                }
                            }
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.DragHandle, contentDescription = "拖动排序", tint = colors.primary)
                    }
                }
            }
            OutlinedButton(
                onClick = onEditFont,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("dictionary-font-${dictionary.id}"),
            ) { Text("正文字号：${dictionary.fontLevel}档", fontSize = 14.sp) }
            if (dictionary.sourceType == DictionarySourceType.IMPORTED) {
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = colors.error, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("删除导入词典", color = colors.error, fontSize = 14.sp)
                }
            } else {
                Text("内置词典不可删除", fontSize = 13.sp, color = colors.onSurfaceVariant)
            }
        }
    }
}


@Composable
private fun FontLevelDialog(
    dictionaryName: String,
    initialLevel: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var candidate by remember(dictionaryName, initialLevel) {
        mutableIntStateOf(DictionaryFontScale.normalize(initialLevel))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("正文字号") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(dictionaryName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("${candidate}档 · ${DictionaryFontScale.textZoom(candidate)}%")
                Slider(
                    value = candidate.toFloat(),
                    onValueChange = { candidate = DictionaryFontScale.normalize(it.toInt()) },
                    valueRange = 1f..10f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        onClick = { candidate = DictionaryFontScale.normalize(candidate - 1) },
                        enabled = candidate > 1,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("减小") }
                    TextButton(
                        onClick = { candidate = DictionaryFontScale.normalize(candidate + 1) },
                        enabled = candidate < 10,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("增大") }
                }
                Text(
                    "The quick brown fox · 正文字号预览",
                    fontSize = (10 + candidate).sp,
                    fontFamily = FontFamily.Serif,
                )
                TextButton(onClick = { candidate = DictionaryFontScale.DEFAULT_LEVEL }) {
                    Text("恢复默认（5档）")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(candidate) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
