# 功能 2：词典

本目录是 EnglishApp 的独立 Android Library 词典模块，当前修复目标版本为 `0.1.1`。

模块负责 MDX/MDD 查询、词条 HTML 与资源显示、发音、词典管理和 Android 本地导入；不拥有 Activity、App 导航、签名、APK 或其他功能实现。

v0.1.1 仅修复test.001实机发现的WebView主文档误拦截：严格应用生成的 `data:text/html...` 主框架交给WebView，子框架data、外部主框架和非法MDD路径仍封锁。当前状态为 `ready_for_integration / awaiting_test.002_device_retest`；没有设备，不能宣称实机修复。证据见 `WEBVIEW_MAIN_DOCUMENT_FIX_AUDIT.md`，完整交接见 `MILESTONE_3_HANDOFF.md`，解析来源见 `MILESTONE_1B_AUDIT.md`，许可证原文见 `THIRD_PARTY_NOTICES.md`。
