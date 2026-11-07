package com.xuesui.englishapp.modelprobe.features.speech

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xuesui.englishapp.modelprobe.contracts.speech.*
import com.xuesui.englishapp.modelprobe.core.storage.SavedRun
import com.xuesui.englishapp.modelprobe.core.model.ModelDescriptor
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

@Composable
internal fun SpeechScreen(
    state: SpeechUiState, descriptor: ModelDescriptor, supportedAbi: Boolean, actions: SpeechActions, onTalker: () -> Unit, onCodec: () -> Unit,
    onExport: (String) -> Unit, settings: @Composable () -> Unit, parameterSummary: @Composable (JSONObject) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmBenchmark by remember { mutableStateOf(false) }
    val idle = !state.busy
    val runnable = idle && state.installed && state.parametersValid && state.text.isNotBlank() && supportedAbi
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("本地模型验证", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("语音合成 · ${descriptor.runtime}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("实验性设备验证", fontWeight = FontWeight.Bold)
                    Text(if (state.hasSynthesis) "已有本机合成记录；自然度、漏读/重复、断网和稳定性仍需实际确认。" else "1.7B实际运行尚待本机确认。编译完成不代表模型已能在手机运行。")
                    if (!supportedAbi) Text("本设备不具备arm64-v8a ABI，生成不可用。", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item {
            Panel("当前状态") {
                Text(state.message)
                if (state.busy) {
                    state.progress?.let { value -> LinearProgressIndicator(progress = { value }, modifier = Modifier.fillMaxWidth()) }
                        ?: LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("${state.phase} · ${state.elapsedSeconds}秒", style = MaterialTheme.typography.bodySmall)
                    Button(actions::cancel, enabled = !state.cancelling) { Text(if (state.cancelling) "等待原生退出…" else "停止当前任务") }
                    if (state.cancelling) Text("取消请求已发出。系统I/O或单个计算节点仍可能耗时，返回前不会释放模型。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("操作", "设置", "结果").forEachIndexed { index, title ->
                    FilterChip(tab == index, { tab = index }, label = { Text(title) }, modifier = Modifier.weight(1f))
                }
            }
        }
        if (tab == 0) {
            item {
                Panel("模型") {
                    Text(descriptor.title, style = MaterialTheme.typography.titleMedium)
                    Text(if (state.loaded) "已载入" else if (state.installed) "私有副本已安装，尚未载入" else "尚未安装私有副本")
                    if (state.modelIssue.isNotEmpty()) Text(state.modelIssue, color = MaterialTheme.colorScheme.error)
                    Text(state.manifestMessage, style = MaterialTheme.typography.bodySmall)
                    Text("两文件合计约1.44GB；首次安装还需同等私有存储及64MiB余量。进度表示复制和校验，不是下载。", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ actions.install() }, enabled = idle && !state.loaded && state.manifestReady) { Text(if (state.installed) "重新安装" else "安装内置模型") }
                        OutlinedButton(actions::verify, enabled = idle && !state.loaded) { Text("完整校验") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(actions::load, enabled = idle && state.installed && !state.loaded && state.parametersValid && supportedAbi) { Text("载入") }
                        OutlinedButton(actions::unload, enabled = idle && state.loaded) { Text("卸载") }
                        TextButton({ confirmDelete = true }, enabled = idle && state.installed && !state.loaded) { Text("删除副本") }
                    }
                    Text("Q8 / 0.6B：本候选未提供，不可选择。模型删除不会删除历史音频。", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Panel("英文测试文本") {
                    Text(state.sampleMessage, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(state.text, actions::editText, Modifier.fillMaxWidth(), enabled = idle,
                        minLines = 5, maxLines = 12, label = { Text("编辑英文文本") })
                    TextButton(actions::restoreSample, enabled = idle && state.sampleAvailable) { Text("恢复固定样例") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(state.mode == SpeechMode.PARAGRAPH, { actions.setMode(SpeechMode.PARAGRAPH) }, label = { Text("完整段落") }, enabled = idle)
                        FilterChip(state.mode == SpeechMode.SENTENCES, { actions.setMode(SpeechMode.SENTENCES) }, label = { Text("逐句试验") }, enabled = idle)
                    }
                    if (state.mode == SpeechMode.SENTENCES) Text("逐句独立合成，按顺序直接拼接，不添加静音；自然度不能与完整段落混为同一条件。", style = MaterialTheme.typography.bodySmall)
                    if (!state.parametersValid) Text(state.parameterError, color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ actions.generate(RunKind.COLD) }, enabled = runnable) { Text("冷启动生成") }
                        OutlinedButton({ actions.generate(RunKind.WARM) }, enabled = runnable && state.loaded) { Text("暖运行") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ actions.generate(RunKind.WARM, true) }, enabled = runnable && state.loaded) { Text("暖运行 × ${state.batchCount}") }
                        TextButton({ actions.generate(RunKind.CACHE) }, enabled = idle && state.parametersValid && state.text.isNotBlank() && state.cacheAllowed) { Text("查找同参数缓存") }
                    }
                    Text("冷启动会卸载并重新载入，始终真实推理；不清空系统文件缓存。暖运行使用驻留模型。缓存单独标记且不计算RTF。", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Panel("诊断与调试导入") {
                    OutlinedButton(actions::selfCheck, enabled = idle && supportedAbi) { Text("JNI无模型自检") }
                    Text("会卸载驻留模型；只验证JNI基础契约，不能证明1.7B合成或运行中取消通过。", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onTalker, enabled = idle && !state.loaded) { Text(if (state.talkerChosen) "Talker 已选" else "选择Talker") }
                        OutlinedButton(onCodec, enabled = idle && !state.loaded) { Text(if (state.tokenizerChosen) "Codec 已选" else "选择Codec") }
                    }
                    TextButton({ actions.install(true) }, enabled = idle && !state.loaded && state.talkerChosen && state.tokenizerChosen) { Text("校验并安装所选配对文件") }
                    Text("只接受登记的1.7B Q4配对文件，按完整SHA256核验，不支持任意GGUF。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (tab == 1) {
            item {
                Panel("下次生成的模型设置") {
                    settings()
                    if (!state.parametersValid) Text(state.parameterError, color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ actions.saveSettings() }, enabled = idle && state.parametersValid) { Text("保存全部设置") }
                        OutlinedButton({ actions.saveSettings(true) }, enabled = idle) { Text("恢复默认") }
                    }
                }
            }
            item {
                Panel("连续测试与播放") {
                    Text("暖运行批次：${state.batchCount}次（1…5）")
                    Text("同一批次固定全部条件；随机seed也只解析一次，各次记录相同有效seed。", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ actions.setBatch(state.batchCount - 1) }, enabled = idle && state.batchCount > 1) { Text("减少") }
                        OutlinedButton({ actions.setBatch(state.batchCount + 1) }, enabled = idle && state.batchCount < 5) { Text("增加") }
                    }
                    Text("播放器变速：${format(state.playbackSpeed.toDouble())}×")
                    Slider(state.playbackSpeed, actions::setPlaybackSpeed, valueRange = 0.5f..2f, steps = 5)
                    Text("播放器变速不改变WAV、生成参数、音频原始时长或RTF。切页、后台和旋转保留任务。", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton({ confirmBenchmark = true }, enabled = runnable) { Text("同段4 / 6 / 8线程对照") }
                    Text("仅测试当前小段英文（最多400字符），每档分别重载和暖运行；线程数受本机上限约束，可能耗时较长。", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(actions::clearCache, enabled = idle) { Text("清理缓存索引") }
                    Text("保留历史音频与报告，清理后不会再次命中这些缓存。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (tab == 2) {
            item {
                Panel("选定结果") {
                    val run = state.selected
                    if (run == null) Text("尚未选择结果。真实生成、自检完成后，或点击下面历史记录查看。")
                    else {
                        ResultDetails(run, parameterSummary)
                        val hasAudio = run.report.optJSONArray("artifacts")?.let { a -> (0 until a.length()).any { a.getJSONObject(it).getString("role") == "audio" } } == true
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ actions.play(run.id) }, enabled = idle && hasAudio) { Text("播放 / 重播") }
                            OutlinedButton(actions::stopPlayback) { Text("停止播放") }
                        }
                        Text(state.playback, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ onExport("audio") }, enabled = idle && hasAudio) { Text("导出WAV") }
                            OutlinedButton({ onExport("report") }, enabled = idle) { Text("导出JSON") }
                        }
                        Text("导出绑定此结果，报告包含模型身份、实际参数及采样指标，不含朗读正文。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Text("历史记录（最近100条）", style = MaterialTheme.typography.titleMedium) }
            items(state.history, key = { it.id }) { run ->
                OutlinedCard(onClick = { actions.select(run.id) }, modifier = Modifier.fillMaxWidth(), enabled = idle) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(runStatusTitle(run.status), fontWeight = FontWeight.Medium)
                        Text("${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(run.report.optLong("createdAtEpochMillis")))} · ${run.report.optString("runKind", "自检")} · ${run.id.take(8)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
    if (confirmBenchmark) AlertDialog(onDismissRequest = { confirmBenchmark = false }, title = { Text("开始同段线程对照？") },
        text = { Text("将用当前小段英文和固定的实际seed依次测试本机可用的4、6、8线程，每档重载一次、暖运行一次。不会改写已保存参数或自动提高线程；可能持续数分钟，可随时请求停止。完整结果可在历史中导出JSON，不能只按速度判定音质。") },
        confirmButton = { TextButton({ confirmBenchmark = false; actions.compareThreads() }, enabled = runnable) { Text("开始测试") } },
        dismissButton = { TextButton({ confirmBenchmark = false }) { Text("取消") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("删除私有模型副本？") },
        text = { Text("约1.44GB私有副本将被移除。内置资产、历史WAV与报告会保留，下次需重新安装。") },
        confirmButton = { TextButton({ confirmDelete = false; actions.deleteModel() }) { Text("删除副本") } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("保留") } })
}

@Composable
private fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun ResultDetails(run: SavedRun, parameterSummary: @Composable (JSONObject) -> Unit) {
    val report = run.report
    Text(runStatusTitle(run.status), style = MaterialTheme.typography.titleMedium)
    Text("结果ID：${run.id}", style = MaterialTheme.typography.bodySmall)
    if (!report.isNull("error")) Text(report.getString("error"), color = MaterialTheme.colorScheme.error)
    if (report.optString("taskId") == "speech.reading.v1") Text("${report.optString("title")} · ${report.optInt("completedCount")}/${report.optInt("plannedCount")}片段完成")
    if (report.has("message")) Text(report.getString("message"))
    report.optJSONArray("benchmarkRows")?.let { rows ->
        repeat(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            fun number(key: String) = if (row.isNull(key)) "未知" else row.get(key).toString()
            HorizontalDivider()
            Text("${row.getInt("threads")}线程 · ${if (row.getString("kind") == "cold") "重载模型" else "暖运行"} · ${runStatusTitle(row.getString("status"))}", style = MaterialTheme.typography.titleSmall)
            Text("RTF ${number("rtf")} · 总耗时 ${number("elapsedMillis")}ms · 音频 ${row.optJSONObject("audio")?.optDouble("durationSeconds") ?: "未知"}秒")
            Text("加载 ${number("loadMillis")}ms / 预填充 ${number("prefillMillis")}ms / token生成 ${number("tokenGenerationMillis")}ms / 解码 ${number("decodeMillis")}ms / WAV ${number("wavWriteMillis")}ms", style = MaterialTheme.typography.bodySmall)
            Text("峰值PSS ${number("sampledPeakPssKiB")} KiB · 电池 ${number("batteryTemperatureStartC")}→${number("batteryTemperatureEndC")}℃ · 热状态 ${number("thermalStart")}→${number("thermalEnd")}", style = MaterialTheme.typography.bodySmall)
            Text("后端 ${row.optJSONObject("backend")?.optString("backend") ?: "未知"} / ${row.optJSONObject("backend")?.optString("architecture") ?: "未知"}", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (run.status == "token_limit") Text("音频可能漏掉末尾内容，请提高上限或缩短文本后重新测试。", color = MaterialTheme.colorScheme.error)
    report.optJSONObject("audio")?.let { audio ->
        Text("${format(audio.getDouble("durationSeconds"))}秒 · ${audio.getInt("sampleRate")}Hz · ${audio.getLong("samples")}个样本")
    }
    if (report.has("runKind")) {
        Text("${report.getString("runKind")} · ${report.optString("mode")} · 加载 ${report.optLong("loadMillis")}ms")
        Text(if (report.isNull("rtf")) "RTF：不适用（缓存或未完成）" else "RTF：${format(report.getDouble("rtf"))}（生成+解码 / 音频时长，越小越快）")
        Text("首PCM延迟：不可测（批量接口）", style = MaterialTheme.typography.bodySmall)
    }
    report.optJSONObject("measurement")?.let { m ->
        val peak = if (m.isNull("sampledPeakPssKiB")) "未知" else "${format(m.getDouble("sampledPeakPssKiB") / 1024)} MiB"
        Text("采样峰值PSS：$peak · ${m.optInt("sampleCount")}次 · 间隔目标200ms")
        val samples = m.optJSONArray("samples")
        if (samples != null && samples.length() > 0) {
            val first = samples.getJSONObject(0); val last = samples.getJSONObject(samples.length() - 1)
            val temperature = if (last.isNull("batteryTemperatureC")) "未知" else "${format(last.getDouble("batteryTemperatureC"))}°C"
            Text("Android热状态：${thermal(first.optInt("thermalStatus", -1))} → ${thermal(last.optInt("thermalStatus", -1))}；末次电池温度：$temperature", style = MaterialTheme.typography.bodySmall)
        }
        Text("PSS是采样峰值；电池温度不代表CPU温度。", style = MaterialTheme.typography.bodySmall)
    }
    report.optJSONObject("requested")?.let { requested ->
        Text("请求配置", fontWeight = FontWeight.Medium)
        parameterSummary(requested)
    }
    report.optJSONArray("effectiveSegments")?.let { segments ->
        repeat(segments.length()) { i ->
            val segment = segments.getJSONObject(i)
            Text("生效片段 ${i + 1}", fontWeight = FontWeight.Medium)
            parameterSummary(segment.getJSONObject("parameters"))
        }
    }
    report.optJSONObject("failedSegmentEffective")?.let { parameters ->
        Text("未完成片段：原生已返回的生效参数", fontWeight = FontWeight.Medium)
        parameterSummary(parameters)
    }
    Text("${report.optString("modelId")}\n${report.optString("engineVersion")}", style = MaterialTheme.typography.bodySmall)
}
private fun format(value: Double) = String.format(Locale.ROOT, "%.2f", value)
private fun thermal(value: Int) = listOf("正常", "轻度", "中度", "严重", "临界", "紧急", "关闭").getOrNull(value) ?: "未知"
