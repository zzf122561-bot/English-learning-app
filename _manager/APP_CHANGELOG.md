# EnglishApp 总控变更记录

本文件记录 App 级集成和治理变更；功能内部行为仍记录在各功能的 `CHANGELOG.md`。

## 0.3.0 — 词典集成（开发中，尚未发布）

- 集成功能1 v0.3.0：英文正文长按按 Unicode Latin 边界回传原始单词，数据库和 v0.2.0 用户验收基线保持不变。
- 集成功能2 v0.1.0：纯 Kotlin/JVM MDX/MDD 2.x 解析、完整/快速查词、前缀建议、词典管理、SAF 导入、安全 WebView 与 MDD→HTTPS→TTS 发音回退。
- 根 App 更新为 `versionName=0.3.0`、`versionCode=4`，增加“记单词 / 词典”底部双入口，默认仍进入记单词。
- 长按查词打开独立 QUICK_LOOKUP 页面；关闭后恢复原主入口的可保存 Compose 状态，功能之间只传普通 `String`。
- 构建时从 Git 忽略的 `dictionary-users` 校验四个 MDX/MDD SHA-256，原子生成 `assets/dictionaries/manifest.json` 和两套内置词典资产；原始词典与生成资产均不提交 Git。
- 根 App 增加 `INTERNET` 权限，仅供用户点击发音时的严格 HTTPS 回退；当前两套 MDD 没有音频资源，离线仍可回退 Android 英语 TTS。
- 从干净来源提交 `bfbfcc5` 先清理全部模块构建目录，再完成两个模块测试、Android测试源码编译、release lint、签名 APK 与内置资产逐项校验；`test.001` 已归档。
- 当前只标记自动构建通过；没有连接 Android 设备，覆盖安装、交互和用户确认均待执行，不创建正式 `app-v0.3.0` 标签。

## 0.2.0 — 2026-08-24 验收基线

- 集成功能1 v0.2.0。
- 用户确认真机可用、无问题。

## 0.2.1 — 2026-08-24 管理重构（待用户验收）

- 总纲接管 Git、App 壳、签名、APK 和发版权限。
- 功能1从独立 Application 迁移为 Android Library；不改变用户功能。
- 建立对话分类、主动汇报、发布归档和架构边界检查。
- 自动验证：功能1模块测试、仪器测试源码编译、AAR、根 release APK、v2 签名和证书连续性通过。
- 已归档 `test.001`；尚未进行真机覆盖安装，因此未创建 `app-v0.2.1` 标签。
