# 功能2集成记录

| 项目 | 当前值 |
|---|---|
| 功能 ID | `feature-02-dictionary` |
| 功能任务 | `功能2：词典` / `01a02fd4-7dcb-7882-aad0-0ed84ad3254c` |
| 工作区 | `C:\Users\zzf86\.codex\worktrees\b83b\Codex_EnglishApp`；起点 `f3dcf49`；预留分支 `codex/feature-02-dictionary` |
| 目标功能版本 | `0.1.0` |
| 目标 App 版本 | `0.3.0` / versionCode 4 |
| 公共入口 | `DictionaryFeature(initialQuery, presentation, onClose)` |
| 数据库 | 计划使用独立 Room 数据库 `englishapp_dictionary.db`，初始版本 1 |
| 私有内置资料 | 两套 MDX/MDD；原始文件不进入 Git，只记录哈希 |
| 集成状态 | Library 空壳 AAR 与架构边界检查通过；里程碑1A 因 `mdict-java` 的 GPL 依赖失败且未引入上游代码；总控已下发纯 Kotlin/JVM 替代路线的里程碑1B；尚未接入 App、尚未构建 v0.3.0 APK |
| 设备验收 | 未连接设备；后续由用户自行覆盖安装验收 |

## 解析路线审计历史

- 2026-08-24：功能任务确认两套词典均为 MDX/MDD 2.0、`Encrypted=2`，并固定四个主文件 SHA-256。
- 2026-08-24：`mdict-java` 路线因 GPL-3.0 `lzo-core:1.0.6` 运行依赖和跨许可包引用失败；没有引入该库或依赖。
- 2026-08-24：GitHub 连接恢复后，总控复核固定提交源码，仍维持失败结论；改为审核 MIT `mdict-reader` 与 MIT `lzokay` 的纯 Kotlin/JVM 实现路线。
- 2026-08-24：功能任务接收里程碑1B 后，GitHub connector 连续三次发生 `Transport send error`，没有取得任何新文件并按门槛停止。用户随后明确授权总控从 GitHub 官方 codeload 下载两个固定提交 ZIP；两次命令均在真正启动前因本机提权审批审查超时被拒，浏览器直连又被安全审查拒绝。归档目录保持空白，等待用户人工下载并落到登记路径后再校验。
- 2026-08-24：用户人工落盘两个官方固定提交 ZIP；总控核对 SHA-256、完整提交顶层目录、MIT 许可证、必要源码与依赖锁，`SOURCE_ARCHIVE_CHECK_PASSED archives=2`。来源获取阻塞解除，功能2获准继续里程碑1B；真实词典兼容门槛仍未通过。
