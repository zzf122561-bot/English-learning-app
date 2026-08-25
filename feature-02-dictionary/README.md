# 功能 2：词典

本目录是 EnglishApp 的独立 Android Library 词典模块，当前目标版本为 `0.2.0`。

模块负责 MDX/MDD 查询、词条 HTML 与资源显示、发音、词典管理、本地导入，以及逐词典正文字号；不拥有 Activity、App 导航、签名、APK 或其他功能实现。

v0.1.1已随App v0.3.0由用户概括验收，历史上接受 `F02-LINK-001` 与 `F02-AUDIO-001`；用户于2026-08-25重新授权在v0.2.0处理这两项问题并增加逐词典正文字号。

v0.2.0 在不改变公共 `DictionaryFeature` 签名的前提下：仅恢复受控同文档锚点；把正文发音入口接入 MDD → 严格 HTTPS → Android 英语 TTS；在词典管理页提供每本词典独立十档正文字号；通过显式 Room `MIGRATION_1_2` 将数据库升级到 v2 并保留旧数据。

当前状态为 `manager_review_passed / awaiting_app_1.0.0_build`。自动回归、AAR与总控边界检查已通过，但当前没有 Android 设备，WebView、发音、Room迁移和UI仪器测试均未在设备运行。v0.2.0证据见 `V0.2.0_AUDIT.md`；v0.1.1设备历史见 `WEBVIEW_MAIN_DOCUMENT_FIX_AUDIT.md`，完整交接见 `MILESTONE_3_HANDOFF.md`，解析来源与许可证证据分别见 `MILESTONE_1B_AUDIT.md` 和 `THIRD_PARTY_NOTICES.md`。
