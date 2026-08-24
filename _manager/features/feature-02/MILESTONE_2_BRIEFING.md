# 功能2里程碑2开发简报

下发日期：2026-08-24

功能 ID：`feature-02-dictionary`

目标功能版本：`0.1.0`

本里程碑：查询、展示、词典管理、导入与独立数据库

## 1. 授权范围与审查锚点

- 总控已批准里程碑1B：纯 Kotlin/JVM MDict 2.x 内核、两本真实词典兼容门槛、5 项测试、AAR、无 JNI/NDK 和无 APK 均已完成一次审查与强制复测。
- 里程碑1B当时的功能目录状态是后续审查锚点。不要为了“再审查”而重写、格式化或搬迁未变化的解析内核；里程碑2只审新增/修改差异及受影响调用链，同时保留原有解析回归。
- 本任务只允许修改 `feature-02-dictionary` Android Library 及其模块测试、工具和功能文档。
- 禁止读取或修改功能1内部文件；禁止修改根 App、根 Manifest、版本目录、全局 Gradle、App 版本、签名和发布记录；禁止 Git 提交、打标签或生成 APK。
- 继续允许只读使用原简报授权的两套 `dictionary-users` 词典，以及两份固定来源 ZIP。不得编辑、移动、上传或提交用户词典。
- 本里程碑结束即停止并汇报 `milestone_passed`；不得自行进入里程碑3或宣称 `ready_for_integration`。

## 2. 里程碑1B的定向加固

只对以下已识别差异做定向修改和测试，不扩大成旧代码全面返工：

1. 所有查询键规范化使用 `Locale.ROOT`，不得依赖设备默认地区。
2. MDD 资源重定向加入循环检测和最大深度，行为与词条重定向同等级防护。
3. 真实词典兼容测试要求两本词典分别提供图片证据，并验证 PNG/JPEG 文件头，不只根据扩展名判断。
4. 将旧 `MILESTONE_1_AUDIT.md` 明确标记为历史失败路线，指向已批准的 `MILESTONE_1B_AUDIT.md`，避免状态误读。

## 3. 可使用的既有依赖

模块可在自身 `build.gradle.kts` 使用根版本目录中已经存在的以下别名；不得修改版本目录：

- `libs.plugins.ksp`
- `libs.androidx.activity.compose`
- `libs.androidx.lifecycle.runtime.compose`
- `libs.androidx.lifecycle.viewmodel.compose`
- Compose BOM、UI、Foundation、Material 3、扩展图标
- Room runtime、KTX、compiler、testing
- Kotlin coroutines Android
- AndroidX Test core、ext JUnit、runner 与 JUnit 4

优先使用平台 `WebView`、`DocumentsContract`、`ContentResolver`、`MediaPlayer` 和 `TextToSpeech`。未经总控批准，不新增第三方依赖或解析器，不引入 Rust、Cargo、JNI、NDK，也不新增 `androidx.webkit`。若确有无法替代的新依赖需求，先停止并汇报许可证、用途和最小范围。

## 4. 稳定公共契约

公共 API 位于 `com.xuesui.englishapp.dictionary`，保持以下唯一 UI 入口；数据库实体、DAO、解析器、WebView 和页面状态实现均为模块内部：

```kotlin
enum class DictionaryPresentation {
    FULL,
    QUICK_LOOKUP
}

@Composable
fun DictionaryFeature(
    initialQuery: String? = null,
    presentation: DictionaryPresentation = DictionaryPresentation.FULL,
    onClose: () -> Unit = {}
)
```

- `FULL` 是完整词典页。
- `QUICK_LOOKUP` 收到 `initialQuery` 后直接查词；系统返回或页面返回动作只调用 `onClose`。
- 词条内部链接只替换当前查询结果，不创建模块内部的嵌套页面返回栈，确保 App 壳以后按一次返回就能回到功能1。
- 本阶段不得读取、引用或模拟功能1的内部类型；跨模块连接由总控后续完成。

## 5. 独立数据库与状态模型

- 数据库文件固定为 `englishapp_dictionary.db`，初始版本 `1`，不得接触功能1数据库。
- 至少分离词典元数据与资源文件记录，保存：稳定 ID、显示名、内置/导入类型、私有 MDX 路径、MDD 分卷路径、各文件 SHA-256、是否启用、显示顺序、格式版本、运行状态、创建/更新时间。
- 启停和排序必须持久化且顺序稳定；所有启用词典的顺序是查词结果标签顺序。
- 内置词典只能启用、停用和排序，删除操作必须在领域层和 DAO 层共同拒绝；导入词典删除时只删除 App 私有副本及其数据库记录，不触碰用户原文件。
- 以后若升级数据库，只能显式向前迁移；禁止 destructive migration、删库重建或静默清空。

## 6. 内置资产安装契约

总控在整合阶段才会提供实际 MDX/MDD 资产。功能模块现在实现读取和安装能力，但不得把用户原始词典复制进源码。

- 固定读取 `assets/dictionaries/manifest.json`，`schemaVersion=1`。
- 清单中每本词典至少包含：稳定 ID、显示名、MDX 相对资产路径与 SHA-256、零到多个 MDD 资源分卷相对路径与 SHA-256。
- 首次运行将文件流式复制到 `filesDir/dictionaries/builtin/<dictionary-id>/` 的暂存目录，逐文件校验哈希后原子提升；任何失败都清理暂存，不留下半成品或数据库记录。
- 已存在且哈希一致时不得重复复制；清单哈希变化时才执行受控替换。
- 没有清单或清单为空时，模块仍应正常打开并显示“暂无内置词典”，便于当前 Library 独立测试。

建议清单结构（字段名可按类型安全实现细化，但语义不得改变）：

```json
{
  "schemaVersion": 1,
  "dictionaries": [
    {
      "id": "builtin.example",
      "displayName": "Example",
      "mdx": { "path": "example/example.mdx", "sha256": "..." },
      "resources": [
        { "path": "example/example.mdd", "sha256": "..." }
      ]
    }
  ]
}
```

## 7. Android 文件夹导入

- UI 通过 Android 文件夹选择器取得目录授权；模块内部使用平台 SAF API 扫描和流式复制，不持久保存或依赖原目录作为运行时数据源。
- 以 MDX 基础名匹配同目录的 `.mdd`、`.1.mdd`、`.2.mdd` 等分卷，扩展名大小写不敏感。
- MDX 无 MDD 时允许导入，但显示“资源缺失”提示；孤立 MDD 不导入。
- 对 MDX 和全部资源计算 SHA-256；重复 MDX 哈希必须拒绝，不创建第二份私有副本。
- 先复制到 `filesDir/dictionaries/importing/<operation-id>/`，完成格式探测、哈希和数据库事务后再原子提升到正式目录；损坏、取消或异常均完整回滚。
- 删除导入词典只删除对应 App 私有目录。任何代码都不得删除或修改 SAF 所选用户原文件。

## 8. 查询、结果与管理行为

- 查询所有已启用词典，按管理页顺序显示词典标签；同一时间只渲染一个完整词条，避免多 WebView、重复解压与样式冲突。
- 精确查询无结果时显示去重后的前缀建议；选择建议后发起新的当前查询。
- 规范化查询使用小写 `Locale.ROOT`，页面仍保留用户原始输入作为标题。
- 管理页提供导入、启用/停用、拖动排序、运行状态；仅导入词典显示带确认的删除动作。
- 解析或资源异常必须隔离到对应词典状态，不得使整个页面崩溃；其他启用词典仍可查询。

## 9. HTML 与 WebView 安全边界

- 词条 HTML 以受控本地 HTTPS 域名作为 base URL，并通过 `WebViewClient.shouldInterceptRequest` 只解析该域名下的 MDD CSS、图片、字体和音频资源。
- 默认并持续关闭 JavaScript、文件访问、内容访问和 JS Bridge；禁止 `file://`、`content://` 及混合内容加载。
- 外部页面不在词条 WebView 中打开；首版可阻止并向 UI 报告链接，真正的外部跳转由以后总控决定。
- 对 URL 解码、路径规范化、`..`、反斜杠、重定向循环和超大资源设置明确防护；MDD 资源只通过 `MdictEngine` 按需读取。
- WebView 生命周期必须随 Compose 页面释放，避免保留 Activity/Context；测试至少验证安全设置和域名/路径拦截策略。

## 10. 发音回退

点击发音后按顺序尝试：

1. 当前词典 MDD 中的内置音频；
2. 词条明确提供的 HTTPS 音频地址；
3. Android 英语 `TextToSpeech`。

- 不得自动联网或页面载入即播放；只在用户点击时访问 HTTPS。
- 本模块不得自行向根 Manifest 加 `INTERNET` 权限。里程碑报告中列出 App 壳整合时的权限需求，由总控决定。
- HTTP、文件和不受控重定向不得请求；网络、解码或播放失败应继续回退到 TTS。
- 播放器和 TTS 必须在页面/模块释放时停止并关闭，状态可测试。

## 11. 界面设计方向

设计目标只有一个：快速查词，同时不让用户丢失阅读位置。不要做通用后台面板，也不要堆叠渐变、玻璃效果和无意义卡片。

- 色彩：墨水蓝 `#14213D`、纸张蓝 `#F4F7FB`、词典钴蓝 `#2F5D8C`、标注琥珀 `#E3A43B`、学习玉绿 `#2E7D6B`、分隔灰 `#CCD6E2`。
- 字体：词头使用 `FontFamily.Serif`；正文和控件使用 `SansSerif`；紧凑状态与格式信息可用 `Monospace`。
- 识别性元素是页面边缘的“词典索引签”：按管理顺序显示启用词典，既承担结果切换，也让顺序可见，不作为纯装饰。
- `FULL`：固定搜索栏 → 词头/音标/发音 → 有序索引签 → 单一词条 WebView，并提供进入管理页的明确动作。
- `QUICK_LOOKUP`：紧凑返回与搜索栏 → 同一套结果和索引签；不得创建额外返回层级。
- 管理页：有序行/卡片显示顺序、名称、类型、状态、启停和拖动把手；仅导入词典显示删除。
- 支持大字号、清晰对比、键盘焦点和触控尺寸；减少不必要动画，并在系统减少动态效果时保持静态可用。

## 12. 自动验证与完成门槛

必须保留并复跑里程碑1B的 5 项解析回归，但总控不会重复审查没有变化的解析源码。新增自动测试至少覆盖：

- `Locale.ROOT` 查询、资源重定向循环/深度、两本词典分别验证图片魔数；
- Room 的启停、稳定排序、导入删除、内置不可删除；
- MDX/MDD 配对、多分卷、MDX 无资源、孤立 MDD、重复哈希、损坏文件、取消与原子回滚；
- 内置清单哈希、幂等安装和失败回滚；
- 精确结果、去重前缀建议、词典顺序与单结果渲染状态；
- `QUICK_LOOKUP` 一次关闭语义和内部链接不增加返回栈；
- WebView 安全设置、受控域名、路径穿越和外部导航阻止；
- MDD 音频 → HTTPS → TTS 的回退状态机与资源释放。

本里程碑必须新增实际 `androidTest` 源码并保证 `compileDebugAndroidTestKotlin` 不再是 `NO-SOURCE`；当前没有设备，因此只记录源码编译，不能宣称仪器测试已在设备运行。

完成时执行模块单元测试、Android 测试源码编译和 `assembleDebug`，确认：

- 只生成 AAR，功能目录没有 APK；
- AAR 不含 native/JNI；
- 模块仍为 Android Library、没有 launcher Activity、签名或版本配置；
- 未依赖功能1内部实现。

按固定 `[FEATURE_MILESTONE]` 格式汇报，状态使用 `milestone_passed`，明确列出新增/修改路径、测试计数、数据库版本、公共契约、网络权限需求、尚未实机执行的边界，以及请求总控进行里程碑2增量审查。汇报后停止，等待总控批准里程碑3。
