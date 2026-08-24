# 功能2总控入口

## 总控身份

- 总控对话：`总纲`
- 任务 ID：`01a024cb-1fba-7b23-a357-67eb2ad592b4`
- 功能 ID：`feature-02-dictionary`

## 默认读取权限

功能任务默认只能读取根 `PROJECT.md`、根 `AGENTS.md`、本功能目录，以及下列只读测试资料：

- `dictionary-users/柯林斯双解学习词典`
- `dictionary-users/牛津9英英(推荐)`

允许读取的总控文件仅限：

- `_manager/REPORTING_PROTOCOL.md`
- `_manager/features/feature-02/BRIEFING.md`
- `_manager/tools/gradle-hosts.txt`（仅供既有模块测试脚本只读使用，不得修改或向外复制）
- `_manager/vendor/source-archives/mdict-reader-e25373923035f06156dbfa8aedeb802b5167e6df.zip`（只读；只可解压到本功能目录用于审计和实现）
- `_manager/vendor/source-archives/lzokay-rs-c762f2522d0d19ca6e4b6b8ca7ba51b512dc93b0.zip`（只读；只可解压到本功能目录用于审计和实现）

不得浏览 `_manager` 的其他文件或归档目录列表，不得读取其他功能、Skill、全局 memory、历史摘要或其他对话缓存。

## 必须主动汇报

三个开发里程碑、数据库或公共契约变化、测试成功/失败、阻塞、需要测试 APK或收到用户验收结果时，必须按总控协议向上述任务 ID 发送消息。

发送失败时，将完整报告写入 `MANAGER_OUTBOX.md`，并保持“待总控接收”状态。
