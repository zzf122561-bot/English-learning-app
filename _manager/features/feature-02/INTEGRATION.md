# 功能2集成记录

| 项目 | 当前值 |
|---|---|
| 功能 ID | `feature-02-dictionary` |
| 功能任务 | `功能2：词典` / `01a02fd4-7dcb-7882-aad0-0ed84ad3254c` |
| 工作区 | `C:\Users\zzf86\.codex\worktrees\b83b\Codex_EnglishApp`；分支 `codex/feature-02-dictionary` |
| 目标功能版本 | `0.1.0` |
| 目标 App 版本 | `0.3.0` / versionCode 4 |
| 公共入口 | `DictionaryFeature(initialQuery, presentation, onClose)` |
| 数据库 | 独立 Room 数据库 `englishapp_dictionary.db`，版本 1 |
| 私有内置资料 | 两套 MDX/MDD；原始文件不进入 Git，只记录哈希 |
| 集成状态 | v0.1.0模块和最终入口生命周期修复已由总控增量审查、复测、提交并合并；根App v0.3.0 test.001已从干净提交构建、签名并归档 |
| 设备验收 | 未连接设备；后续由用户自行覆盖安装验收 |

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
