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

## Markdown 精简候选（2026-09-23，待用户决定）

已盘点本项目 Git 跟踪的 71 个 Markdown 文件，范围不包含用户自管 Skill 和私有词典内容。现有文件包含独有的权限、里程碑、来源或验收信息，未发现可直接确定无用的整份文件；本轮不删除、移动或合并文件。以下候选仅是建议，未获同意前保持原样。

| 编号 | 文件或段落 | 建议 | 需保留/确认的内容 |
|---|---|---|---|
| MD-01 | `_manager/features/feature-02/BUILTIN_DICTIONARIES.md` → 同目录 `BUILTIN_ASSETS.md` | 合并为一个资产登记文件，可减少 1 份 Markdown | 前者独有 Oxford PNG 项，后者有稳定 ID、清单与构建流程；全部保留并区分原始资料与打包资产，不能直接删其中一份 |
| MD-02 | `PROJECT.md` 的“当前状态”与 `_manager/INTEGRATION_STATUS.md` | 当前集成状态只在后者维护，前者改为入口说明；不删除总纲 | 总纲仍有旧版本和历史已知问题表述；合并时明确正式基线与候选状态，不推断用户已验收 |
| MD-03 | `feature-01-word-memory/README.md` 的测试/验收状态 → 同目录 `STATUS.md`；`feature-02-dictionary/README.md` 的“当前状态”与测试结论 → 同目录 `STATUS.md` | README 只留功能介绍、用法和状态链接，测试结论只在各自 STATUS 维护；不删除模块 README | 两功能分别处理，不互读或合并模块；保留历史版本介绍，防止将“已构建”和“待构建”混写 |
| MD-04 | `_manager/features/feature-02/DEVICE_FEEDBACK_TEST_001.md`、`DEVICE_FEEDBACK_V1.0.0_TEST_001.md` → 同目录 `KNOWN_ISSUES.md` 的分版本反馈历史 | 按日期/版本保留完整独有事实与诊断后合并，可减少 2 份 Markdown | 主文档 403 与词条内部链接是不同问题，必须分开；原始诊断、用户原意、失败结论及证据边界都不能丢失 |

执行条件：用户指明候选编号并回复“可以合并”后，才迁移内容、更新引用及授权路径并移除原文件；若要直接删除，须明确回复对应文件“可以去掉”。有新的不确定点再列出，不自行裁决。

明确保留：根与子目录 `AGENTS.md`、模块 `MANAGER_LINK.md`、受保护基线、公共契约、第三方许可证/来源映射、发布/验收记录、恢复与签名索引，以及各里程碑的独有审计证据。`MANAGER_OUTBOX.md` 是消息失败的持久回退入口，空闲并不代表无用途；不列为删除项。
