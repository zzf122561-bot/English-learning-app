# 对话登记表

只有用户可以确定或改变对话类型。未列出的新对话一律视为 `pending-user-classification`，总控不得读取其内容或自行归类。

| 对话标题 | 任务 ID | 用户指定类型 | 管理状态 | 目标目录 | 工作区/分支 | 最近汇报 |
|---|---|---|---|---|---|---|
| 总纲 | `01a024cb-1fba-7b23-a357-67eb2ad592b4` | `manager` | 总控 | 项目根目录与 `_manager` | `master` | 2026-08-24 |
| 功能1 | 当前：`01a02f8c-2b77-7360-9d8f-56d7faa11773`；交接前：`01a024d0-fa64-7382-96e0-920fec364fc5` | `app-feature` | v0.3.0长按查词回调已集成；用户概括确认test.002其余验收正常 | `feature-01-word-memory` | `C:\Users\zzf86\.codex\worktrees\d2f2\Codex_EnglishApp` / `codex/1` | 2026-08-24：用户概括确认除功能2两项已知问题外其余正常 |
| 功能2：词典 | `01a02fd4-7dcb-7882-aad0-0ed84ad3254c` | `app-feature`（用户于 2026-08-24 明确指定） | v0.2.1已集成；App 1.0.0 test.002已签名归档，等待用户实机 | `feature-02-dictionary` | `C:\Users\zzf86\.codex\worktrees\b83b\Codex_EnglishApp` / `codex/feature-02-dictionary` | 2026-08-26：test.002来源`5719bb3`，APK SHA-256 `6EF00A...160C` |
| GitHub同步：EnglishApp | `01a0a8b9-837e-7252-935f-8c92ce0cdb1b` | `github-sync`（用户于 2026-09-16 明确指定） | `main`及五个标签已同步；两个功能分支待直接授权 | 不维护项目文件 | `C:\Users\zzf86\.codex\worktrees\299f\Codex_EnglishApp` / detached只读工作区 | 2026-09-16：远端`main=828313d`和五个标签已核对；功能分支只读预检通过、未push |
| 英语语境短文生成器 | `01a024f4-f059-7dd1-a10f-04bbc4211e7b` | `skill` | 用户自行管理；总控不读取 | `short-story-generator` | 不纳入本仓库 | 不适用 |

## 交接审计

- 2026-08-24：功能1成功交接到独立 Codex worktree，分支与 `master` 同起点 `06fca5e`。
- 首次规则回报链路已打通；功能1未读取其他功能目录或 Skill 目录，也未修改文件。
- 功能1随后主动披露误读 `C:\Users\zzf86\.codex\memories\MEMORY.md` 第 50–59 行。该内容未用于改动，但超出授权范围；总控已补充禁止读取全局 memory 的明确规则。
- 二次确认仅授权读取根 `AGENTS.md`、功能 `AGENTS.md` 和 `MANAGER_LINK.md`；功能1逐项确认已读，本轮无越界读取。纠正验证通过，首次偏差记录永久保留。
- 2026-08-24：功能2由用户明确分类为 `app-feature`；总控从干净提交 `f3dcf49` 创建独立 worktree 任务并交接完整开发简报。
- `.worktreeinclude` 仅复制用户明确授权的两套 Git 忽略词典到功能2 worktree；总控已确认两个 MDX 文件存在，原始词典仍未进入 Git。
- 2026-09-16：用户明确创建 `github-sync` 类型对话。首次只读检查确认本地 `master` 为 `680193e`、工作树干净且尚未配置 remote；GitHub 域名解析失败，因此没有核对远端引用，也没有执行 fetch、pull、push 或修改 Git 配置。
- 2026-09-16：用户首次明确授权上传。总控固定 `master=1701d5f`、远端 `main` 和五个历史标签；同步任务核对本地引用无误，但即使申请网络权限，SSH只读预检仍因DNS无法解析`github.com`失败。授权以失败结束；远端未发生本任务造成的变更。
