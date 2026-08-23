package com.xuesui.englishapp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuesui.englishapp.ImportState
import com.xuesui.englishapp.WordMemoryViewModel
import com.xuesui.englishapp.data.NotebookEntity
import com.xuesui.englishapp.data.StudySegmentWithTargets
import com.xuesui.englishapp.data.TargetEntity
import com.xuesui.englishapp.ui.theme.Cobalt
import com.xuesui.englishapp.ui.theme.Hairline
import com.xuesui.englishapp.ui.theme.Ink
import com.xuesui.englishapp.ui.theme.Paper
import com.xuesui.englishapp.ui.theme.Slate
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun WordMemoryApp(model: WordMemoryViewModel) {
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
private fun StudyScreen(
    notebook: NotebookEntity,
    segments: List<StudySegmentWithTargets>,
    onBack: () -> Unit,
    onVisibilityChange: (Long, Boolean) -> Unit,
    onDictationChange: (Long, String) -> Unit,
    onPositionChange: (Int) -> Unit,
) {
    BackHandler(onBack = onBack)
    val startIndex = notebook.lastSegmentIndex.coerceIn(0, (notebook.segmentCount - 1).coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    LaunchedEffect(listState, notebook.id) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect(onPositionChange)
    }
    Scaffold(containerColor = Paper) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().background(Paper).padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
                Column(Modifier.weight(1f)) {
                    Text(notebook.title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    Text("${notebook.segmentCount} 段  ·  ${notebook.targetCount} 个目标", style = MaterialTheme.typography.labelMedium, color = Slate)
                }
            }
            HorizontalDivider(color = Hairline)
            if (segments.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(segments, key = { it.segment.id }) { item ->
                        StudySegment(
                            data = item,
                            onVisibilityChange = { onVisibilityChange(item.segment.id, it) },
                            onDictationChange = { onDictationChange(item.segment.id, it) },
                        )
                    }
                    item { Spacer(Modifier.height(42.dp)) }
                }
            }
        }
    }
}

@Composable
private fun StudySegment(
    data: StudySegmentWithTargets,
    onVisibilityChange: (Boolean) -> Unit,
    onDictationChange: (String) -> Unit,
) {
    val segment = data.segment
    val targets = data.targets.sortedBy { it.position }
    var draft by remember(segment.id, segment.dictationText) { mutableStateOf(segment.dictationText) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 26.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("段落 ${(segment.position + 1).toString().padStart(3, '0')}", style = MaterialTheme.typography.labelMedium, color = Cobalt)
            Spacer(Modifier.width(12.dp))
            HorizontalDivider(Modifier.weight(1f), color = Hairline)
            Spacer(Modifier.width(12.dp))
            Text("${targets.size} TARGETS", style = MaterialTheme.typography.labelMedium, color = Slate)
        }
        Spacer(Modifier.height(19.dp))
        Text(highlightedText(segment.englishText, targets, false), style = MaterialTheme.typography.bodyLarge, color = Ink)
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it; onDictationChange(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("我的默写") },
            placeholder = { Text("只看英文，在这里写下你记住的中文或目标词义") },
            minLines = 3,
        )
        Spacer(Modifier.height(14.dp))
        OutlinedButton(
            onClick = { onVisibilityChange(!segment.chineseVisible) },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, Hairline),
        ) {
            Icon(if (segment.chineseVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (segment.chineseVisible) "隐藏本段中文" else "显示本段中文")
        }
        if (segment.chineseVisible) {
            Spacer(Modifier.height(16.dp))
            Text(highlightedText(segment.chineseText, targets, true), style = MaterialTheme.typography.bodyLarge, color = Ink)
        }
    }
    HorizontalDivider(color = Hairline)
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
