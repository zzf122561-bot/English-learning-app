# App 功能对话汇报协议

## 必须汇报的事件

- 功能版本开发完成；
- 数据库、公共契约或受保护基线变化；
- 模块测试完成、失败或出现阻塞；
- 需要总控生成测试 APK；
- 收到设备测试或用户确认结果。

## 固定格式

```text
[FEATURE_MILESTONE]
功能 ID：
目标版本：
状态：milestone_passed / compatibility_gate_passed / ready_for_integration / blocked / contract_change / user_feedback
完成摘要：
修改路径：
测试命令与结果：
数据库迁移：无 / 说明
公共契约变化：无 / 说明
已知风险：
请求总控动作：
```

功能对话通过 Codex 跨对话消息发送到 `MANAGER_LINK.md` 登记的总控任务 ID。发送失败时，原文写入功能目录 `MANAGER_OUTBOX.md`，并保持“待总控接收”状态。

总控收到后依次执行：增量差异审查、相关模块回归、边界验证、提交与合并、整包构建、归档和用户验收交付。已经建立审查锚点且没有变化的代码不得重复逐行审查；只有新增/修改差异及受影响调用链进入下一轮代码审查。
