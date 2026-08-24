# 功能 2：词典

本目录是 EnglishApp 的独立 Android Library 词典模块，当前修复目标版本为 `0.1.1`。

模块负责 MDX/MDD 查询、词条 HTML 与资源显示、发音、词典管理和 Android 本地导入；不拥有 Activity、App 导航、签名、APK 或其他功能实现。

三个功能里程碑均已完成并经总控增量审查；v0.1.0 已进入 App v0.3.0 test.001，但实机发现 WebView 主文档误拦截。v0.1.1 仅修复该问题：严格应用生成的 `data:text/html...` 主框架交给 WebView，data 子框架、外部主框架和非法 MDD 路径仍封锁。当前状态为 `ready_for_integration / awaiting_test.002_device_retest`；没有 v0.1.1 设备证据，不能宣称实机修复。证据见 `WEBVIEW_MAIN_DOCUMENT_FIX_AUDIT.md`，完整交接见 `MILESTONE_3_HANDOFF.md`，解析来源见 `MILESTONE_1B_AUDIT.md`，许可证原文见 `THIRD_PARTY_NOTICES.md`。
