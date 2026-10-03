# 功能1公共契约

## Android 入口

```kotlin
@Composable
fun WordMemoryFeature(
    onLookupRequested: (String) -> Unit = {},
    onReadingRequested: (title: String, englishParagraphs: List<String>, paragraphIndex: Int?, sentenceText: String?) -> Unit = { _, _, _, _ -> }
)
```

入口由功能模块内部创建 Room 数据库、Repository 与 ViewModel，并渲染现有语境记忆界面。根 App 壳不得直接访问功能模块内部的数据类、DAO、解析器或页面私有实现。

学习英文正文中长按 Unicode Latin 单词时，功能模块通过 `onLookupRequested` 回传保留原始大小写和字符的普通 `String`。默认空回调保证原有 `WordMemoryFeature()` 调用继续有效；功能模块不创建词典页面或持有根 App 导航。

`onReadingRequested` 只回传笔记本标题、按原文顺序排列的英文段落列表，以及可选的零起始段落索引和句子原文：

- 全文：`paragraphIndex = null`、`sentenceText = null`；
- 整段：`paragraphIndex` 为有效索引、`sentenceText = null`；
- 单句：`paragraphIndex` 为有效索引，`sentenceText` 为该段中非空的连续原文片段。

该回调默认为空实现，因此旧的无参或仅查词调用仍可继续编译。功能1不暴露语音枚举或引擎类型，不调用系统 TTS，也不负责朗读页面导航。

## 稳定性

- 入口不接受其他功能的内部对象；查词和朗读回调都只使用 Kotlin 基础类型。
- 数据仍写入现有 Room 数据库，名称、版本 2 和迁移语义保持不变。
- 修改本契约必须先向总控汇报，由总控评估集成和 App 版本影响。
