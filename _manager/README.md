# EnglishApp 总控区

本目录由“总纲”对话管理，保存对话登记、集成状态、发版记录、测试 APK 索引、恢复证据和总控工具。

- 功能对话默认不得读取本目录。
- Skill 对话不受本目录管理。
- `artifacts`、`backups` 和 `local` 中的二进制或本机秘密不提交 Git。
- Git、App 版本、签名、APK 和正式发版只有总控可以操作。

## 文档维护入口

- 根 `AGENTS.md`：角色、通信、核验复用及文档精简规则；`PROJECT.md`：长期目标。
- `REPORTING_PROTOCOL.md`：派工前回复规则、交接格式和等待流程；功能任务只读其明确授权路径。
- `THREAD_REGISTRY.md`：对话归属与工作区；`INTEGRATION_STATUS.md`：App 集成状态。
- 功能目录 `STATUS.md`：模块当前状态与证据入口；功能 `CHANGELOG.md`：版本变化；`README.md`：用途与用法。已有审计/交接文档作为证据引用，不全文复制到上述各文件。
- `RELEASE_POLICY.md` 与 `releases/`：发布流程和每份产物的验收证据；`GITHUB_SYNC_POLICY.md` 与 `GITHUB_SYNC_LOG.md`：按需推送规则和结果。

## Markdown 合并记录（2026-09-23）

用户已明确批准“MD-01～04 可以合并”。四项均已完成，受 Git 管理的 Markdown 从 71 份减为 68 份；移除的三份文件可由合并前 Git 历史恢复。未涉及用户自管 Skill 或私有词典内容。

| 编号 | 原位置 | 现维护入口 | 处理结果 |
|---|---|---|---|
| MD-01 | `_manager/features/feature-02/BUILTIN_DICTIONARIES.md`（已移除） | [BUILTIN_ASSETS.md](features/feature-02/BUILTIN_ASSETS.md) | 保留全部文件名、大小和哈希，包括独有 Oxford PNG；稳定 ID、清单和构建约束继续保留 |
| MD-02 | `PROJECT.md` 的“当前状态” | [INTEGRATION_STATUS.md](INTEGRATION_STATUS.md) | 总纲改为状态入口；分别标明已验收正式基线和待用户验收候选 |
| MD-03 | 两功能 `README.md` 中的测试/验收状态 | 各自 `STATUS.md` | README 保留用途、用法和历史功能介绍；沿用已有反馈纠正过时状态，不提高验收级别 |
| MD-04 | `_manager/features/feature-02/DEVICE_FEEDBACK_TEST_001.md`、`DEVICE_FEEDBACK_V1.0.0_TEST_001.md`（均已移除） | [KNOWN_ISSUES.md](features/feature-02/KNOWN_ISSUES.md) 的分版本反馈历史 | 分开保留主文档 403 和内部链接故障；完整保留原始观察、诊断、用户意图、候选哈希、失败处置及证据边界 |

后续新的删除/合并不确定项仍须先列出，等待用户“可以去掉”或“可以合并”的明确回复；本次批准仅对应 MD-01～04。目前没有新增待用户决定的候选。

明确保留：根与子目录 `AGENTS.md`、模块 `MANAGER_LINK.md`、受保护基线、公共契约、第三方许可证/来源映射、发布/验收记录、恢复与签名索引，以及各里程碑的独有审计证据。`MANAGER_OUTBOX.md` 是消息失败的持久回退入口，空闲并不代表无用途；不列为删除项。
