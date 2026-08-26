# 功能2集成记录

| 项目 | 当前值 |
|---|---|
| 功能 ID | `feature-02-dictionary` |
| 功能任务 | `功能2：词典` / `01a02fd4-7dcb-7882-aad0-0ed84ad3254c` |
| 工作区 | `C:\Users\zzf86\.codex\worktrees\b83b\Codex_EnglishApp`；分支 `codex/feature-02-dictionary` |
| 当前功能版本 | `0.2.1`；已由总控增量审查、复测并合并，等待App test.002实机验收 |
| 目标 App 版本 | `1.0.0` / versionCode 5 / test.002 |
| 公共入口 | `DictionaryFeature(initialQuery, presentation, onClose)` |
| 数据库 | 独立 Room数据库 `englishapp_dictionary.db`，版本2；显式 `MIGRATION_1_2` |
| 私有内置资料 | 两套 MDX/MDD；原始文件不进入 Git，只记录哈希 |
| 集成状态 | v0.2.1功能提交 `de23797`、主线合并 `68644a9`；App 1.0.0 test.002已从 `5719bb3`签名归档 |
| 设备验收 | v0.3.0 test.002历史验收接受2项问题；App 1.0.0 test.001内部链接仍失败；新test.002待用户复测 |

## 解析路线审计历史

- 2026-08-24：功能任务确认两套词典均为 MDX/MDD 2.0、`Encrypted=2`，并固定四个主文件 SHA-256。
- 2026-08-24：`mdict-java` 路线因 GPL-3.0 `lzo-core:1.0.6` 运行依赖和跨许可包引用失败；没有引入该库或依赖。
- 2026-08-24：GitHub 连接恢复后，总控复核固定提交源码，仍维持失败结论；改为审核 MIT `mdict-reader` 与 MIT `lzokay` 的纯 Kotlin/JVM 实现路线。
- 2026-08-24：功能任务接收里程碑1B 后，GitHub connector 连续三次发生 `Transport send error`，没有取得任何新文件并按门槛停止。用户随后明确授权总控从 GitHub 官方 codeload 下载两个固定提交 ZIP；两次命令均在真正启动前因本机提权审批审查超时被拒，浏览器直连又被安全审查拒绝。归档目录保持空白，等待用户人工下载并落到登记路径后再校验。
- 2026-08-24：用户人工落盘两个官方固定提交 ZIP；总控核对 SHA-256、完整提交顶层目录、MIT 许可证、必要源码与依赖锁，`SOURCE_ARCHIVE_CHECK_PASSED archives=2`。来源获取阻塞解除，功能2获准继续里程碑1B；真实词典兼容门槛仍未通过。
- 2026-08-24：功能任务完成里程碑1B并停在门槛处。总控完成一次代码/许可证/测试审查，并用 `--rerun-tasks` 强制执行 35 个 Gradle 任务：5 tests/0 failures、`compileDebugAndroidTestKotlin=NO-SOURCE`、AAR 70,482 bytes、SHA-256 `16C3145BBE60A3089F88E2F94EB56915E50230731A8A746610F859995C80A5DA`、native/JNI=0、APK=0；架构边界仍为 1 个 Application、2 个 Library。里程碑1B批准，审查锚点建立；后续不重复审查未变化解析代码。
- 2026-08-24：里程碑2获准开发查询展示、独立数据库、词典管理、SAF 导入、受控 WebView 和发音回退；仍不得修改根 App、生成 APK或进入里程碑3。
- 2026-08-24：功能任务提交里程碑2，报告32 tests/0 failures、2个 androidTest 源码编译、AAR 375,767 bytes、无 JNI/APK。总控只审查里程碑1B锚点之后的新增/修改链路，并强制复跑37个任务成功；未重复审查旧解析内核。
- 2026-08-24：里程碑2首次增量审查发现主线程执行大文件安装/导入/查询、Room `REPLACE` 可能因唯一索引冲突删除无关词典、真实词条内部链接和MDD原声引用尚未接通、未知长度HTTPS音频在8 MiB检查前可无限写入，以及发音并发和删除边界问题。总控未批准该门槛，已定向退回修复；里程碑1B批准状态不受影响。
- 2026-08-24：功能任务只修改上述7组新增链路并补充测试；总控未回看里程碑1B旧解析代码。第二次增量复核确认I/O调度、取消/引擎并发、Room安全写入、真实链接改写、实际音频引用、流式上限、发音并发和删除/分卷边界均有对应实现与证据。
- 2026-08-24：总控再次用 `--rerun-tasks` 强制执行37个任务：44 tests/0 failures/errors/skipped、2个androidTest源码编译、AAR 420,005 bytes、SHA-256 `8AB43658462C528339FB85ABA6F4A20D947150EB546C8812010658B04FFC0894`、native/JNI=0、APK=0；架构边界仍为1个Application、2个Library。两本真实词典均为 `entry` 链接且精确命中；两本MDD音频资源均为0，明确回退TTS。里程碑2批准并建立新审查锚点。
- 2026-08-24：里程碑3将旧解析接口可见性收紧为 `internal` 并完成稳定交接；模块提交 `260ec07`，主线合并 `13bbbcd`。该阶段未重复审查已批准解析内核。
- 2026-08-24：根App接线前发现同Activity复用ViewModel会忽略后续QUICK查询并可能继承管理态；功能任务只修该入口链路并新增4项契约测试。总控只复审这3个源码/测试路径，独立复跑37任务：48 tests/0 failures/errors/skipped、AAR 425,353 bytes、SHA-256 `8837BBACF8B5615F028E5D2F9C02CA2B430E3FDD02A8DB2CCAC52CB30069D404`、APK=0；修复提交 `0d7a57a`，主线合并 `e9523da`。
- 2026-08-24：根App增加默认记单词的双入口、QUICK_LOOKUP临时页、构建时哈希校验的两套内置资产和仅点击发音使用的INTERNET权限；从干净提交 `bfbfcc5` 完整构建签名 `test.001` 并归档。当前没有设备，功能2仍不得标记实机通过或正式发布。
- 2026-08-24：用户安装test.001后提供实机截图：搜索 `another place` 可显示查询词、Collins/Oxford标签，但正文区域显示 `data:text/html;charset=utf-8;base64,` 加载失败和 `net::ERR_HTTP_RESPONSE_CODE_FAILURE`。总控确认查询/词典选择链路已到达，正文WebView主请求失败；test.001判定不通过。
- 2026-08-24：总控增量定位 `SecureDictionaryWebViewClient.shouldInterceptRequest` 对所有非受控资源直接返回403，而设备上的 `loadDataWithBaseURL` 主文档请求表现为 `data:`，因此应用自己的主页面被误拦截。修复范围仅限安全区分主文档与子资源，不回看解析、Room、导入、音频或其他已批准代码。
- 2026-08-24：功能任务仅修改WebView请求分类、同组测试和状态文档；总控只增量审查该范围并独立复跑37个任务：52 tests/0 failures/errors/skipped、AAR 430,712 bytes、SHA-256 `0E4D71852AB7A8D520F6C625EB0F18D62C36DAC5CEFF84181D8BF0BF08879FAF`、JNI/APK=0。功能提交 `49229d4`，主线合并 `6010e73`。
- 2026-08-24：从干净提交 `6010e73` 清理三个模块并完成167个Gradle任务、边界检查、签名和资产逐项校验；`EnglishApp-v0.3.0-test.002-6010e73.apk` 已永久归档。当前仅有自动证据，正文恢复、CSS/图片及导航仍待用户实机复测。
- 2026-08-24：用户实机概括确认test.002除“具体内容页面链接被阻止”和“无法获得内部发音”外其余均正常，并要求先记录、不修。两项登记为 `F02-LINK-001`、`F02-AUDIO-001`；本版接受已知问题并正式提升，未修改功能代码或APK。
- 2026-08-25：用户重新授权功能2v0.2.0修复内部链接与双发音入口，并增加逐词典十档字号；Room通过显式 `MIGRATION_1_2`升级到2。总控独立复跑61项JVM测试并合并，App 1.0.0 test.001从干净提交构建、签名和永久归档。
- 2026-08-26：用户实机确认App 1.0.0 test.001词典应用内链接“还是不行”，要求取消所有应用内链接限制；test.001判定不通过且不得覆盖、提升或重建。
- 2026-08-26：真实两本词典结构审计确认Oxford9快捷定位依赖386个 `onclick/className`事件。功能2v0.2.1取消 `dictionary.local`内部白名单并在受控页面启用本地/内联JavaScript，不添加JS Bridge；外部/本地/跨应用边界继续隔离。
- 2026-08-26：总控仅审查v0.2.1 WebView增量并独立强制复跑：63 tests/0 failures/errors/skipped、3个androidTest源码共10项编译、AAR 468,724 bytes、SHA-256 `69931817087AFB9A500E09D0FFDFF25D1BCAF9B577A40E0313997C662D2223AD`、JNI/APK=0；边界仍为1 Application/2 Library。功能提交 `de23797`，主线合并 `68644a9`。
- 2026-08-26：从干净来源提交 `5719bb3`完成完整release构建、lint、签名、对齐、元数据和内置资产验证；`EnglishApp-v1.0.0-test.002-5719bb3.apk`大小106,004,624 bytes，SHA-256 `6EF00A222D073EA87D58CD246226B43B3ADDC5E42A12A37A1D59474FC0AD160C`，永久归档等待用户实机。
