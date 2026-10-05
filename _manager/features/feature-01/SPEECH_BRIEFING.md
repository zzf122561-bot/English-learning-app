# 2026-10-03 功能1 v0.4.0 文章朗读入口

## 2026-10-05 当前修订：原阅读页内嵌播放槽（功能1 v0.5.0）

用户明确要求边看原文章边听，不再跳语音页。原本全文/本段/单句选择继续保留，但本轮由功能1提供阅读页底部紧凑槽，总控注入语音组件。用户另要求生成记录删除/同段只留最新，由语音任务负责，不在本模块实现文件清理。

### 授权与回复

仍只修改本worktree的feature-01-word-memory。明确授权只读本简报绝对路径与`D:/Codex_Project/Codex_EnglishApp/PROJECT.md`；不读语音/词典内部。可按本次明确用户授权更新本功能基线/公共契约，保留其余学习数据行为。不Git/APK/签名/版本根配置。回复总控01a024cb-1fba-7b23-a357-67eb2ad592b4；一次完整报告含精确接口、位置/选择状态、测试与剩余设备项，发送后等待；失败写MANAGER_OUTBOX.md，阻塞即时报告，不相互联系其他任务。

### 精确追加契约

在WordMemoryFeature现有两个参数之后追加，不删除旧参数或默认实现：

```kotlin
readingContent: (@Composable (
    documentId: String,
    title: String,
    englishParagraphs: List<String>,
    paragraphIndex: Int?,
    sentenceText: String?,
) -> Unit)? = null
```

- documentId稳定为`word-memory:<notebook.id>`；不得使用标题、随机ID或文章hash。改标题不改变身份，不同笔记本不共用身份。
- 只有StudyScreen绘制槽。槽非null时，全文/本段/句子入口只更新本文章内选区（初始全文），验证后把当前上下文交给槽，不调用旧onReadingRequested，不跳页，不自行生成。槽null时保留旧callback兼容逻辑。函数默认无槽调用仍可用。
- 同一StudyScreen内始终保留原LazyColumn及其state；底部播放槽不在滚动列表内，文章区使用剩余高度；不能因切换范围重新key整页/滚回顶部。文章始终占主要空间，原章节、中文、默写均保持。选择句子的临时对话框保留，选择后回到阅读页，不维持全屏覆盖。
- 选区按notebook.id隔离、跨文章重置，旋转可恢复轻量索引/句子标识，不能把全文数组塞Bundle。加载空态不提供错误旧文章上下文；正文变动后失效选区回到合法选择且提示，不能误播放前一段内容。
- 不引入语音类型/engine/存储依赖，不读模型，无系统TTS。现有长按查词、显隐/默写/字号/Room/位置不变。

### 核验与交接

只覆盖新增槽和受影响选择路径：稳定身份、同文选择切换、跨笔记本隔离、槽非空不发旧跳转、空槽旧行为、合法英文提取及保留滚动state。用合成数据；模块测试和Android源码编译/AAR。无设备不宣称边看边听或触摸验收通过。沿用26项中未受影响证据，不复审旧解析/数据库。

设计方向为正文优先、底部紧凑控制条，沿用Paper/Slate等既有主题，不改用户字号或文字版式。模块正文生成/播放均由注入组件负责。

---

以下0.4.0为历史，冲突点以上述当前修订为准。

用户已明确批准把语音嵌入原英语App并保留独立测试App。只实现功能1的朗读选择/公共回调，语音模型由其他模块承担。总控回复对象01a024cb-1fba-7b23-a357-67eb2ad592b4；实现任务01a02f8c-2b77-7360-9d8f-56d7faa11773。不得直接联系其他功能。

仅允许本功能目录和既有授权文件；额外只读本简报绝对路径 D:/Codex_Project/Codex_EnglishApp/_manager/features/feature-01/SPEECH_BRIEFING.md。不读取语音或词典模块实现。旧worktree中的简报不否定本次明确授权。

## 公共契约

```kotlin
@Composable
fun WordMemoryFeature(
    onLookupRequested: (String) -> Unit = {},
    onReadingRequested: (title: String, englishParagraphs: List<String>, paragraphIndex: Int?, sentenceText: String?) -> Unit = { _, _, _, _ -> }
)
```

全文：paragraphIndex=null/sentenceText=null，传当前笔记本文章全部英文自然段，保持原文与顺序。
整段：传合法零起始paragraphIndex，sentenceText=null。
句子：传合法paragraphIndex以及该段选定句子原文，非空且是原段连续片段。
回调只传公开基础类型，默认空实现向后兼容，不引入语音枚举/数据库/引擎依赖。

## 行为

- 学习文章提供清楚的“全文朗读”入口；每个英文段落提供“朗读”入口，选择“本段”或该段某一句。句子选择使用适合英文的分句，合理处理缩写、小数、引号与末句，展示实际选中内容供确认；不把标点或中文译文作为英文句子。
- 现有长按单词查词保留，不抢同一个手势；默写、强调、译文显隐、字号、位置、滚动和数据库保持原行为。中文隐藏时不能意外朗读中文。
- 不自建语音页面或导航，不调用系统TTS，不提交Git，不构建APK，不修改根App。返回后保留页面状态的根接线归总控；本模块不能为了朗读清空当前文章/位置。
- 明确收集用于回调的英文原文，避免把UI标记/中文拼进正文；不读取用户私有文档，测试用合成文本。

## 测试和交接

只验证新增提取/分句/回调及受影响点击路径：全文顺序、段落索引、句子属于原段、空文本/中文/越界拒绝、默认回调兼容；保留原有长按查词行为。按既有方法运行受影响JVM、Android测试源码编译及AAR；没有设备不宣称实机通过。
版本目标0.4.0；PUBLIC_CONTRACT.md/STATUS/CHANGELOG按实际变化更新。完成后向总控一次报告修改路径、精确签名、数据变化（预计无）、测试/证据/局限及AAR，等待集成。阻塞/失败即时汇报；消息失败写MANAGER_OUTBOX.md。
