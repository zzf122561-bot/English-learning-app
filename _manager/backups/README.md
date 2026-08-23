# 本地恢复包

## 重构前恢复点

- 文件：`pre-manager-migration-20260824-088366c.bundle`
- 内容：重构前完整 Git 历史、`master` 和当时已有的三个标签
- SHA-256：`8D8D4CB3CFCEC535F1B74CE4D1C5349D2F38CCFA5ADC15875F4FA2A363A89154`
- 验证：`git bundle verify` 已通过

## 总控接管完成恢复点

- 文件：`manager-governance-20260824-7880599.bundle`
- 内容：截至 `7880599` 的完整历史、`master`、功能分支 `codex/1` 和四个功能标签，包括新增 `feature-01-v0.2.0-user-tested`
- SHA-256：`93100E75B3ED0FBC7CC0D00A2BBC6EFD922334D7A6CB4CABEA729EACD4458E9C`
- 验证：`git bundle verify` 已通过

两份恢复包都只位于同一硬盘，不属于异地灾备。
