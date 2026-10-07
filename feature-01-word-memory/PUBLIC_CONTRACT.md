# 功能1公共契约

## Android 入口

```kotlin
@Composable
fun WordMemoryFeature(
    onLookupRequested: (String) -> Unit = {},
    onReadingRequested: (title: String, englishParagraphs: List<String>, paragraphIndex: Int?, sentenceText: String?) -> Unit = { _, _, _, _ -> },
    readingContent: (@Composable (
        documentId: String,
        title: String,
        englishParagraphs: List<String>,
        paragraphIndex: Int?,
        sentenceText: String?,
    ) -> Unit)? = null
)
```

入口由功能模块内部创建 Room 数据库、Repository 与 ViewModel，并渲染现有语境记忆界面。根 App 壳不得直接访问功能模块内部的数据类、DAO、解析器或页面私有实现。

学习英文正文中长按 Unicode Latin 单词时，功能模块通过 `onLookupRequested` 回传保留原始大小写和字符的普通 `String`。默认空回调保证原有 `WordMemoryFeature()` 调用继续有效；功能模块不创建词典页面或持有根 App 导航。

`onReadingRequested` 只回传笔记本标题、按存储段落顺序排列的英文朗读文本，以及可选的零起始段落索引和句子朗读文本：

- 全文：`paragraphIndex = null`、`sentenceText = null`；
- 整段：`paragraphIndex` 为有效索引、`sentenceText = null`；
- 单句：`paragraphIndex` 为有效索引，`sentenceText` 为该段中非空的连续原文片段。

朗读文本与屏幕/数据库中的学习正文分离：

- 依据每个已解析 target 的 `key`、`englishStart`、`englishEnd` 及 `english` 内容，只在该 target span 内移除已确认的“编号＋句点＋空白”学习标记；
- 支持项目已有的纯数字和字母数字编号，不扫描或删除其他数字，因此年份、小数、金额、型号和词内字符保留；
- 先构建整篇干净朗读段落，再进行单句切分；全文、整段和单句使用同一组段落；
- span、key 或目标正文不一致时不猜删、不传递半截文本，而是停止该文章的朗读交接并在原页提示。

该回调默认为空实现，因此旧的无参或仅查词调用仍可继续编译。功能1不暴露语音枚举或引擎类型，不调用系统 TTS，也不负责朗读页面导航。

`readingContent` 用于将宿主的生成与播放控件注入原学习页底部：

- `documentId` 稳定为 `word-memory:<notebook.id>`，重命名不改变身份，不同笔记本不共用身份；
- 有注入槽时，全文/整段/单句入口只更新当前页的朗读范围，不调用 `onReadingRequested`；
- 槽位为 `null` 时，完整保留 v0.4.0 的 `onReadingRequested` 行为；
- 槽只在有效的 `StudyScreen` 中绘制，初始选区为全文；空加载态不传递旧文章上下文。

选区只保存段落索引和句子原文，不将全文数组写入 Bundle。切换笔记本时重置为全文，旋转可恢复当前轻量选区；正文变动使原选区失效时，会回退到全文并在原页提示。

点击范围入口只完成“当前朗读范围”选择，不自动生成或播放；功能1会在原页显示已选范围，生成/播放仍由注入槽执行。该说明不代表生成或播放链已经通过设备验证。

## 稳定性

- 入口不接受其他功能的内部对象；查词、旧朗读回调和新槽都只使用 Compose 及 Kotlin 基础类型。
- 数据仍写入现有 Room 数据库，名称、版本 2 和迁移语义保持不变。
- 修改本契约必须先向总控汇报，由总控评估集成和 App 版本影响。
