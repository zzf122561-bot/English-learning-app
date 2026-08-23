# 对话登记表

只有用户可以确定或改变对话类型。未列出的新对话一律视为 `pending-user-classification`，总控不得读取其内容或自行归类。

| 对话标题 | 任务 ID | 用户指定类型 | 管理状态 | 目标目录 | 工作区/分支 | 最近汇报 |
|---|---|---|---|---|---|---|
| 总纲 | `01a024cb-1fba-7b23-a357-67eb2ad592b4` | `manager` | 总控 | 项目根目录与 `_manager` | `master` | 2026-08-24 |
| 功能1 | `01a024d0-fa64-7382-96e0-920fec364fc5` | `app-feature` | 总控管理 | `feature-01-word-memory` | 待交接到独立 Codex worktree | 2026-08-24：v0.2.0 用户确认通过 |
| 英语语境短文生成器 | `01a024f4-f059-7dd1-a10f-04bbc4211e7b` | `skill` | 用户自行管理；总控不读取 | `short-story-generator` | 不纳入本仓库 | 不适用 |
