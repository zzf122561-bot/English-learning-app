# 2026-10-03 功能1 v0.4.0 文章朗读入口

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
