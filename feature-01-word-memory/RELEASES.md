# 功能 1 发布记录

发布记录只追加；APK 不提交到 Git，使用 SHA-256 关联产物。

## 0.1.0 — 用户验收基线

- 日期：2026-08-23
- Git 标签：`feature-01-v0.1.0-user-tested`
- 主要功能：DOCX 导入、笔记本管理、中文显隐、目标强调、默写和学习位置持久化。
- 自动测试：5 项 JVM 测试通过；`assembleDebug` 成功。
- APK 签名：历史验证记录为 APK Signature Scheme v2 通过。
- 用户确认：用户报告此前提供的真机测试清单全部正确。
- APK SHA-256：将在 v0.2.0 产物最终验证时重新生成并记录；旧产物未纳入 Git。

## 0.2.0 — 十档学习字号

- 日期：2026-08-23
- 代码 Git 标签：`feature-01-v0.2.0-font-sizing`
- 用户验收 Git 标签：`feature-01-v0.2.0-user-verified`
- 主要变更：每笔记本独立十档字号、英文/中文/默写正文同步调整、Room 1→2 无损迁移。
- 自动测试：7 项 JVM 测试通过，0 失败；Android 仪器测试源码编译成功。
- 构建时设备边界：没有已连接设备，因此自动化 Android 仪器测试未运行。
- 用户验收：2026-08-24 用户确认 v0.2.0 已验证可用、无问题。
- APK：`app-debug.apk`，62,572,813 字节。
- APK SHA-256：`24FEC0ACC8440E16040EBE9981A1E19DADDE2DC1F0A5D36F226CEF72A10A14BB`
- APK 签名：APK Signature Scheme v2 验证通过；Android Debug 证书 SHA-256 为 `1B8799F7712D1DE409DAF80034DBBF8465C37AA807567F3EB1996EF17179A788`。
