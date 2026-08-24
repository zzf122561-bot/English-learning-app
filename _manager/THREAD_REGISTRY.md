# 对话登记表

只有用户可以确定或改变对话类型。未列出的新对话一律视为 `pending-user-classification`，总控不得读取其内容或自行归类。

| 对话标题 | 任务 ID | 用户指定类型 | 管理状态 | 目标目录 | 工作区/分支 | 最近汇报 |
|---|---|---|---|---|---|---|
| 总纲 | `01a024cb-1fba-7b23-a357-67eb2ad592b4` | `manager` | 总控 | 项目根目录与 `_manager` | `master` | 2026-08-24 |
| 功能1 | 当前：`01a02f8c-2b77-7360-9d8f-56d7faa11773`；交接前：`01a024d0-fa64-7382-96e0-920fec364fc5` | `app-feature` | 独立 worktree；总控管理 | `feature-01-word-memory` | `C:\Users\zzf86\.codex\worktrees\d2f2\Codex_EnglishApp` / `codex/1` | 2026-08-24：新格式汇报完成；读取边界修订后二次确认通过 |
| 功能2：词典 | `01a02fd4-7dcb-7882-aad0-0ed84ad3254c` | `app-feature`（用户于 2026-08-24 明确指定） | 独立 worktree；里程碑1B已批准，进入里程碑2 | `feature-02-dictionary` | `C:\Users\zzf86\.codex\worktrees\b83b\Codex_EnglishApp` / detached `f3dcf49`；预留分支 `codex/feature-02-dictionary` | 2026-08-24：总控完成一次增量审查与强制复测；建立审查锚点并批准里程碑2 |
| 英语语境短文生成器 | `01a024f4-f059-7dd1-a10f-04bbc4211e7b` | `skill` | 用户自行管理；总控不读取 | `short-story-generator` | 不纳入本仓库 | 不适用 |

## 交接审计

- 2026-08-24：功能1成功交接到独立 Codex worktree，分支与 `master` 同起点 `06fca5e`。
- 首次规则回报链路已打通；功能1未读取其他功能目录或 Skill 目录，也未修改文件。
- 功能1随后主动披露误读 `C:\Users\zzf86\.codex\memories\MEMORY.md` 第 50–59 行。该内容未用于改动，但超出授权范围；总控已补充禁止读取全局 memory 的明确规则。
- 二次确认仅授权读取根 `AGENTS.md`、功能 `AGENTS.md` 和 `MANAGER_LINK.md`；功能1逐项确认已读，本轮无越界读取。纠正验证通过，首次偏差记录永久保留。
- 2026-08-24：功能2由用户明确分类为 `app-feature`；总控从干净提交 `f3dcf49` 创建独立 worktree 任务并交接完整开发简报。
- `.worktreeinclude` 仅复制用户明确授权的两套 Git 忽略词典到功能2 worktree；总控已确认两个 MDX 文件存在，原始词典仍未进入 Git。
