# 对话登记表

只有用户可以确定或改变对话类型。未列出的新对话一律视为 `pending-user-classification`，总控不得读取其内容或自行归类。

| 对话标题 | 任务 ID | 用户指定类型 | 管理状态 | 目标目录 | 工作区/分支 | 最近汇报 |
|---|---|---|---|---|---|---|
| 总纲 | `01a024cb-1fba-7b23-a357-67eb2ad592b4` | `manager` | 总控 | 项目根目录与 `_manager` | `master` | 2026-08-24 |
| 功能1 | 当前：`01a02f8c-2b77-7360-9d8f-56d7faa11773`；交接前：`01a024d0-fa64-7382-96e0-920fec364fc5` | `app-feature` | v0.7.0阅读/书架布局已合入，等待其他模块与设备 | `feature-01-word-memory` | `C:\Users\zzf86\.codex\worktrees\d2f2\Codex_EnglishApp` / `codex/1` | 2026-11-07：99a9547/f5dde44；模块证据见STATUS，设备未执行 |
| 功能2：词典 | `01a02fd4-7dcb-7882-aad0-0ed84ad3254c` | `app-feature`（用户于 2026-08-24 明确指定） | v0.3.0查询/管理布局已合入，等待语音与整包设备验收 | `feature-02-dictionary` | `C:\Users\zzf86\.codex\worktrees\b83b\Codex_EnglishApp` / `codex/feature-02-dictionary` | 2026-11-07：6402e84/bb21814；模块证据见STATUS，设备未执行 |
| GitHub同步：EnglishApp | `01a0a8b9-837e-7252-935f-8c92ce0cdb1b` | `github-sync`（用户于 2026-09-16 明确指定） | `main`及五个标签已同步；两个功能分支待直接授权 | 不维护项目文件 | `C:\Users\zzf86\.codex\worktrees\299f\Codex_EnglishApp` / detached只读工作区 | 2026-09-16：远端`main=828313d`和五个标签已核对；功能分支只读预检通过、未push |
| 英语语境短文生成器 | `01a024f4-f059-7dd1-a10f-04bbc4211e7b` | `skill` | 用户限定授权的迁移与格式契约已完成，仍独立Skill | `short-story-generator` → `D:/CodexData/skills/english-vocabulary-story`；唯一库见HANDOFF | 不纳入本仓库 | 2026-10-14：总控从MANAGER_OUTBOX接收完整报告；保留消息平台拒绝记录；当前等待 |
| 本地模型验证：Qwen语音 | `01a0fd10-c97b-7110-abab-9a56c6f3794b` | `app-feature`（2026-10-03用户追加批准正式语音内嵌，保留独立测试App） | 0.5.0已集成，准备1.4.0与独立测速候选 | `experiments/tts-device-probe`（同一Library供双宿主使用） | `C:\Users\zzf86\.codex\worktrees\5551\Codex_EnglishApp` / `codex/model-probe`；GPT-6-astra / xhigh | 2026-11-07：b01f3f1/4344e08；第15节完整收据沿用，无速度/设备验收 |

## 交接审计

- 2026-11-07：用户授权全App布局重设计、展开式播放器与语音性能排查。V1_4_0_PLAN.md及三份既有简报已先持久化，跨对话工具成功下发功能1/词典/语音，沿用d2f2/b83b/5551及现有模型设置；目标0.7.0/0.3.0/0.5.0。登记表最近汇报仍为旧完成记录，本次等待主动交接，无新设备证据/远端同步。语音工作区原STATUS发送收据保留。

- 2026-10-14：用户批准1.3.0计划；功能1/语音仍使用登记worktree，当前模块基线1453bdb/3023608。目标0.6.0/0.4.0；短文生成器仍skill，用户明确授权本轮共享迁移，不重新分类。单一计划V1_3_0_PLAN.md与各原有简报固定权限/回复/停止规则，交付前不更新上述旧版本为完成。

- 2026-08-24：功能1成功交接到独立 Codex worktree，分支与 `master` 同起点 `06fca5e`。
- 首次规则回报链路已打通；功能1未读取其他功能目录或 Skill 目录，也未修改文件。
- 功能1随后主动披露误读 `C:\Users\zzf86\.codex\memories\MEMORY.md` 第 50–59 行。该内容未用于改动，但超出授权范围；总控已补充禁止读取全局 memory 的明确规则。
- 二次确认仅授权读取根 `AGENTS.md`、功能 `AGENTS.md` 和 `MANAGER_LINK.md`；功能1逐项确认已读，本轮无越界读取。纠正验证通过，首次偏差记录永久保留。
- 2026-08-24：功能2由用户明确分类为 `app-feature`；总控从干净提交 `f3dcf49` 创建独立 worktree 任务并交接完整开发简报。
- `.worktreeinclude` 仅复制用户明确授权的两套 Git 忽略词典到功能2 worktree；总控已确认两个 MDX 文件存在，原始词典仍未进入 Git。
- 2026-09-16：用户明确创建 `github-sync` 类型对话。首次只读检查确认本地 `master` 为 `680193e`、工作树干净且尚未配置 remote；GitHub 域名解析失败，因此没有核对远端引用，也没有执行 fetch、pull、push 或修改 Git 配置。
- 2026-09-16：用户首次明确授权上传。总控固定 `master=1701d5f`、远端 `main` 和五个历史标签；同步任务核对本地引用无误，但即使申请网络权限，SSH只读预检仍因DNS无法解析`github.com`失败。授权以失败结束；远端未发生本任务造成的变更。
