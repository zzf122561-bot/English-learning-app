# 功能 2 当前状态（Android Library）

更新时间：2026-11-07

## 当前阶段

- 功能版本：`0.3.0`，目标集成 saber `1.4.0`。
- 状态：`accepted / awaiting_app_1.4.0_build`；总控已提交6402e84并接收本轮模块证据，等待其他模块和整包验证。
- 本轮来源为总控登记的 `de23797`，仅改 Compose 查询/结果/管理布局、UI 测试及功能文档；未执行 Git。
- 查询页区分页头/搜索、词头/发音、词典标签和正文；长词头及按钮可换行，顶部大字号内容可滚动，正文拥有独立可见区域。
- 管理页使用纵向卡片，长名称完整换行，启停、排序、字号和删除各自成区；字号对话框只改排版，保存/取消、十档映射与删除确认继续使用原逻辑。
- 直接使用宿主 MaterialTheme，留白 16/20dp、圆角 16dp、轻边线、主要触点至少 48dp，宽屏内容居中且不超过 720dp；宿主负责 system bar/bottom bar inset。
- v0.2.1 受控内部链接和脚本兼容、词典 CSS 与逐词典字号继续保留。解析、资源、HTML 改写、音频、导入、Room v2/迁移和公共 DictionaryFeature 签名未修改。

## 本轮验证与产物

命令（本 worktree，使用既有授权 hosts 文件）：

```powershell
& 'feature-02-dictionary\tools\test-module.ps1' -Tasks @(
  ':feature-02-dictionary:testDebugUnitTest',
  '--tests', 'com.xuesui.englishapp.dictionary.DictionaryFeatureContractTest',
  ':feature-02-dictionary:compileDebugAndroidTestKotlin',
  ':feature-02-dictionary:assembleDebug'
)
# UI 测试 API/视口定位与空词典提示优先级收尾后的最终增量编译：
& 'feature-02-dictionary\tools\test-module.ps1' -Tasks @(
  ':feature-02-dictionary:compileDebugAndroidTestKotlin',
  ':feature-02-dictionary:assembleDebug'
)
```

| 项目 | 2026-11-07 结果 |
|---|---|
| Gradle | 定向回归 BUILD SUCCESSFUL，37 tasks：12 executed、25 up-to-date；最终增量编译 BUILD SUCCESSFUL，31 tasks：6 executed、25 up-to-date |
| 受影响 JVM 契约 | 7 tests，0 failures/errors/skipped；覆盖 FULL→QUICK、新查询、安装门槛、一次关闭与字号草稿 |
| Android 测试源码 | 4 个 Kotlin 源文件、15 项测试编译成功；新增 UI 5 项，未在设备执行 |
| UI 用例范围 | 管理页 320dp/200% 与 360dp/150% 字号、长名称换行、启停/排序/字号保存取消/删除确认可达；查询页 360dp/200% 长词头与长标签、搜索和发音回调及正文分区；QUICK 继承管理态时系统/页面返回共用一次关闭 |
| AAR | `build/outputs/aar/feature-02-dictionary-debug.aar`，480,961 bytes |
| AAR SHA-256 | `DFCD2059E82FB745F995EF7C7FC4A268C5EBB6B37A8A62F0321D873CA6D0B463` |
| 模块边界 | Android Library 不变；AAR Manifest 无 Activity，native/JNI=0，功能目录 APK=0 |
| 数据库/公共契约 | Room 2 与 `MIGRATION_1_2`、`DictionaryFeature(...)` 均无变化 |
| 未覆盖 | 无 Android 设备，新增 UI 用例只编译；未进行视觉、仪器或用户实机验收 |

- 首次沙箱执行在 Gradle 启动时无法加载 `native-platform.dll`，未进入源码编译；经平台授权在沙箱外执行同一命令后成功。
- 新 UI 测试使用当前 Compose `junit4.v2` API，4 个源文件/15 项测试已编译；最终只涉及外围显示条件的小改动未改变测试源码，AndroidTest 编译任务为 up-to-date。7 项入口契约结果继续适用，未重复运行无变化用例。
- 仅增加 BOM 管理的 AndroidX Compose UI 测试依赖：`androidTestImplementation(ui-test-junit4)` 与 `debugImplementation(ui-test-manifest)`；测试宿主不进入生产源码/本 AAR Manifest，无新解析或音频依赖。
- 入口测试 XML：`build/test-results/testDebugUnitTest/TEST-com.xuesui.englishapp.dictionary.DictionaryFeatureContractTest.xml`。本轮未复跑未受影响的 63 项旧全套；其清晰证据沿用既有总控记录和下列审计入口。

## 既有 v0.2.1 结构证据（沿用，未重新审查）

| 词典 | 抽样内部机制 | 事件/脚本机制 | 结论 |
|---|---|---|---|
| Collins | `entry:` 1307、相对链接 517 | 事件 0、脚本 0、MDD `.js` 0 | 静态内部链接默认兼容 |
| Oxford9 | `entry:` 553、相对链接 524 | `onclick` 386，均为 `className`；脚本文件与 MDD `.js` 0 | 需要受控内联 JavaScript |

只记录结构计数，没有记录或复制词典正文。本轮未读取私人词典，未重新扫描其链接或脚本。受控文档继续默认兼容内部路径/query/fragment、相对链接、自定义词典 scheme 与内联事件；外部网页、file/content/intent/跨应用访问仍隔离，不新增 JS Bridge。

## 下一步

本轮已完成一次 FEATURE_MILESTONE 汇报并由总控接收，功能任务停止等待。总控完成共享根主题/系统栏留白接线后构建 saber 1.4.0 候选；设备需验证窄屏/大字号/长名称、管理操作、真实 CSS/正文和快速查词一次返回原阅读位置。共享根主题和宿主接线由总控覆盖；不以模块源码编译替代视觉实机。

## 历史验收与证据入口

- 总控既有记录确认 v0.2.1 已完成增量审查、独立模块复测与主线集成，App 1.0.0 test.002 已签名归档；其历史状态不是等待构建，没有新增设备通过结论。
- v0.1.1 随 App v0.3.0 获用户概括验收；历史接受 F02-LINK-001/F02-AUDIO-001，随后按用户授权在 v0.2.0/v0.2.1 实施。App 1.0.0 test.001 内部链接实机失败历史保留。
- v0.2.1 自动与结构证据：[V0.2.1_INTERNAL_LINK_COMPATIBILITY_AUDIT.md](V0.2.1_INTERNAL_LINK_COMPATIBILITY_AUDIT.md)；v0.2.0：[V0.2.0_AUDIT.md](V0.2.0_AUDIT.md)。
- 初版交接：[MILESTONE_3_HANDOFF.md](MILESTONE_3_HANDOFF.md)；解析来源与许可：[MILESTONE_1B_AUDIT.md](MILESTONE_1B_AUDIT.md)、[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。既有审计继续保留，本轮未重新审查未变化内核。

## 历史 v0.2.1 自动验证（2026-08-26）

以下为此前总控接收的历史表格；1 Application / 2 Library 是当时尚未集成语音的范围，不能替代当前整包边界。原 mdict-java/lzo-core GPL 路线仍停止；项目自有纯Kotlin/JVM解析内核的MIT来源证据继续沿用。

| 项目 | 当前状态 |
|---|---|
| 模块与边界 | 唯一公共Compose入口；DAO、实体、解析器和内部页面未公开；1个Application、2个Library |
| JVM 单元测试 | 强制复跑 63 tests，0 failures/errors/skipped；37 tasks 全部 executed |
| 真实词典兼容 | Collins/Oxford9内部链接精确命中；Oxford9 `onclick/className`机制已结构验证；未记录正文 |
| Android 测试源码 | 3 个源码文件、10 项测试编译成功；无设备，未运行 |
| AAR | 468,724 bytes；SHA-256 `69931817087AFB9A500E09D0FFDFF25D1BCAF9B577A40E0313997C662D2223AD` |
| native/JNI | AAR 内 0 |
| APK | 功能目录 0；功能任务未生成 APK |
| 数据库 | 保持 Room 2 与既有 `MIGRATION_1_2`，无变化 |
| 公共契约 | `DictionaryFeature(...)` 签名不变 |
| 实机 | 无设备；不得宣称 v0.2.1 内部链接已在设备修复 |
