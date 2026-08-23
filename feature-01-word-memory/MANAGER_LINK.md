# 功能1总控入口

## 总控身份

- 总控对话：`总纲`
- 任务 ID：`01a024cb-1fba-7b23-a357-67eb2ad592b4`
- 功能 ID：`feature-01-word-memory`

## 默认读取权限

功能对话默认只能读取根 `PROJECT.md`、根 `AGENTS.md` 和本功能目录。

总控下发跨对话消息并列出精确路径后，才允许读取对应的 `_manager` 文件；不得借此浏览同目录中的其他文件。

当前允许读取：

- `_manager/REPORTING_PROTOCOL.md`
- `_manager/features/feature-01/BRIEFING.md`

## 必须主动汇报

新版本代码完成、数据库或公共接口变化、测试完成或阻塞、需要测试 APK、收到设备或用户验收时，必须按总控汇报协议向上述任务 ID 发送跨对话消息。

发送失败时，把完整报告写入 `MANAGER_OUTBOX.md`，不得宣称已经完成总控交接。
