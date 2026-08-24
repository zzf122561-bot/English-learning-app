package com.xuesui.englishapp.dictionary

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xuesui.englishapp.dictionary.data.DictionarySourceType
import com.xuesui.englishapp.dictionary.data.DictionaryWithResources
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

private val InkBlue = Color(0xFF14213D)
private val PaperBlue = Color(0xFFF4F7FB)
private val CobaltBlue = Color(0xFF2F5D8C)
private val Amber = Color(0xFFE3A43B)
private val Jade = Color(0xFF2E7D6B)
private val DividerGray = Color(0xFFCCD6E2)

private val DictionaryColors: ColorScheme = lightColorScheme(
    primary = CobaltBlue,
    onPrimary = Color.White,
    secondary = Jade,
    tertiary = Amber,
    background = PaperBlue,
    surface = Color.White,
    onSurface = InkBlue,
    outline = DividerGray,
)

@Composable
fun DictionaryFeature(
    initialQuery: String? = null,
    presentation: DictionaryPresentation = DictionaryPresentation.FULL,
    onClose: () -> Unit = {},
) {
    val context = LocalContext.current
    val latestClose by rememberUpdatedState(onClose)
    val closeGate = remember(presentation) { CloseRequestGate { latestClose() } }
    val factory = remember(context, initialQuery) { DictionaryViewModel.factory(context, initialQuery) }
    val model: DictionaryViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val isManaging = DictionaryEntryPolicy.isManaging(presentation, state.managing)

    BackHandler(enabled = isManaging) { model.showManagement(false) }
    BackHandler(enabled = presentation == DictionaryPresentation.QUICK_LOOKUP && !isManaging) {
        closeGate.request()
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(model, presentation, initialQuery) {
        model.enterPresentation(presentation, initialQuery)
    }

    MaterialTheme(colorScheme = DictionaryColors) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = PaperBlue,
        ) { padding ->
            if (isManaging) {
                DictionaryManagementPage(
                    dictionaries = state.dictionaries,
                    busy = state.busy,
                    onBack = { model.showManagement(false) },
                    onImport = model::importDirectory,
                    onEnabled = model::setEnabled,
                    onMove = model::moveDictionary,
                    onDelete = model::deleteImported,
                    modifier = Modifier.padding(padding),
                )
            } else {
                DictionaryLookupPage(
                    state = state,
                    presentation = presentation,
                    onClose = closeGate::request,
                    onInput = model::setInput,
                    onSearch = model::submitQuery,
                    onManage = { model.showManagement(true) },
                    onSelectDictionary = model::selectDictionary,
                    onPronounce = model::pronounce,
                    onInternalLookup = model::openInternalLink,
                    resourceReader = model::readSelectedResource,
                    onResourceError = model::reportResourceError,
                    onNavigationBlocked = { model.reportMessage("已阻止外部或不安全链接") },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

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
    onInternalLookup: (String) -> Unit,
    resourceReader: (String) -> com.xuesui.englishapp.dictionary.engine.MdictResource?,
    onResourceError: (String) -> Unit,
    onNavigationBlocked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(PaperBlue).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (presentation == DictionaryPresentation.QUICK_LOOKUP) {
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
            OutlinedTextField(
                value = state.input,
                onValueChange = onInput,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("查单词") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(state.input) }),
            )
            IconButton(onClick = { onSearch(state.input) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Search, contentDescription = "查询")
            }
            if (presentation == DictionaryPresentation.FULL) {
                IconButton(onClick = onManage, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = "词典管理")
                }
            }
        }

        if (state.busy) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("正在准备词典…", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }

        val query = state.query
        if (query.displayQuery.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    query.displayQuery,
                    modifier = Modifier.weight(1f),
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = InkBlue,
                )
                IconButton(onClick = onPronounce, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "发音", tint = CobaltBlue)
                }
            }
        }

        if (query.tabs.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                items(query.tabs.size) { index ->
                    val tab = query.tabs[index]
                    FilterChip(
                        selected = query.selectedDictionaryId == tab.dictionaryId,
                        onClick = { onSelectDictionary(tab.dictionaryId) },
                        label = { Text(tab.displayName, maxLines = 1) },
                        leadingIcon = {
                            Box(
                                Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(
                                    if (tab.error == null) Jade else Amber,
                                ),
                            )
                        },
                    )
                }
            }
        }

        if (query.suggestions.isNotEmpty()) {
            Text("未找到精确结果，试试：", fontSize = 13.sp, color = CobaltBlue)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(query.suggestions.size) { index ->
                    val suggestion = query.suggestions[index]
                    AssistChip(onClick = { onInput(suggestion); onSearch(suggestion) }, label = { Text(suggestion) })
                }
            }
        }

        val selectedResult = query.selectedResult
        val selectedHtml = selectedResult?.html
        val selectedError = selectedResult?.error
        when {
            state.dictionaries.isEmpty() && !state.busy -> EmptyDictionaryState(onManage)
            selectedHtml != null -> SecureDictionaryWebView(
                html = selectedHtml,
                modifier = Modifier.fillMaxWidth().weight(1f),
                resourceReader = resourceReader,
                onResourceError = onResourceError,
                onInternalLookup = { onInput(it); onInternalLookup(it) },
                onNavigationBlocked = onNavigationBlocked,
            )
            selectedError != null -> Text(
                selectedError,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.error,
            )
            query.displayQuery.isNotEmpty() -> Text("没有精确结果", Modifier.padding(16.dp), color = InkBlue)
            else -> Text(
                "输入单词开始查询",
                modifier = Modifier.padding(16.dp),
                color = CobaltBlue,
                fontFamily = FontFamily.Serif,
            )
        }
    }
}

@Composable
private fun EmptyDictionaryState(onManage: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("暂无内置词典", fontFamily = FontFamily.Serif, fontSize = 22.sp, color = InkBlue)
        Text("可在词典管理中导入 MDX 文件夹", Modifier.padding(top = 8.dp), color = CobaltBlue)
        Button(onClick = onManage, modifier = Modifier.padding(top = 18.dp).height(48.dp)) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("打开词典管理")
        }
    }
}

@Composable
private fun DictionaryManagementPage(
    dictionaries: List<DictionaryWithResources>,
    busy: Boolean,
    onBack: () -> Unit,
    onImport: (android.net.Uri) -> Unit,
    onEnabled: (String, Boolean) -> Unit,
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

    Column(modifier.fillMaxSize().background(PaperBlue)) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回查询")
            }
            Column(Modifier.weight(1f)) {
                Text("词典管理", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                Text("拖动把手或使用箭头调整结果标签顺序", fontSize = 12.sp, color = CobaltBlue)
            }
            Button(onClick = { launcher.launch(null) }, enabled = !busy, modifier = Modifier.height(48.dp)) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("导入文件夹")
            }
        }
        HorizontalDivider(color = DividerGray)
        if (dictionaries.isEmpty()) {
            Text("暂无词典", Modifier.padding(24.dp), color = InkBlue)
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(dictionaries, key = { _, item -> item.dictionary.id }) { index, item ->
                    DictionaryManagementRow(
                        item = item,
                        index = index,
                        count = dictionaries.size,
                        onEnabled = onEnabled,
                        onMove = onMove,
                        onDelete = { deleteId = item.dictionary.id },
                    )
                    HorizontalDivider(color = DividerGray)
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
                TextButton(onClick = { onDelete(deleting.dictionary.id); deleteId = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun DictionaryManagementRow(
    item: DictionaryWithResources,
    index: Int,
    count: Int,
    onEnabled: (String, Boolean) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDelete: () -> Unit,
) {
    var dragDistance by remember(item.dictionary.id) { mutableFloatStateOf(0f) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.DragHandle,
            contentDescription = "拖动排序",
            tint = CobaltBlue,
            modifier = Modifier.size(48.dp).padding(10.dp).pointerInput(index, count) {
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
        )
        Column(Modifier.weight(1f)) {
            Text(item.dictionary.displayName, fontWeight = FontWeight.SemiBold, color = InkBlue)
            Text(
                "${index + 1} · ${if (item.dictionary.sourceType == DictionarySourceType.BUILTIN) "内置" else "导入"} · ${item.dictionary.runtimeStatus}",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = CobaltBlue,
            )
        }
        IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) {
            Icon(Icons.Default.ArrowUpward, contentDescription = "上移")
        }
        IconButton(onClick = { onMove(index, index + 1) }, enabled = index < count - 1) {
            Icon(Icons.Default.ArrowDownward, contentDescription = "下移")
        }
        Switch(
            checked = item.dictionary.enabled,
            onCheckedChange = { onEnabled(item.dictionary.id, it) },
        )
        if (item.dictionary.sourceType == DictionarySourceType.IMPORTED) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除导入词典", tint = MaterialTheme.colorScheme.error)
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}
