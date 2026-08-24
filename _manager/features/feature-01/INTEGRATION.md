# 功能1集成记录

| 项目 | 当前值 |
|---|---|
| 功能 ID | `feature-01-word-memory` |
| 已验收功能版本 | `0.2.0` |
| 验收提交 | `088366cd8eb20590258609869c8141ea6ea7cd33` |
| 代码标签 | `feature-01-v0.2.0-font-sizing` |
| 用户验收标签 | `feature-01-v0.2.0-user-verified`、`feature-01-v0.2.0-user-tested` |
| 公共入口 | 已验收基线为 `WordMemoryFeature()`；正在向后兼容扩展 `WordMemoryFeature(onLookupRequested = {})` |
| 数据库 | Room 版本 2；保留显式 1→2 迁移 |
| 集成状态 | v0.2.0受保护基线保持通过；总控已下发v0.3.0长按英文取词回调增量，数据库不变，只审本轮差异 |
