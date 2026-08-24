# 功能 2：词典

本目录是 EnglishApp 的独立 Android Library 词典模块，当前修复目标版本为 `0.1.1`。

模块负责 MDX/MDD 查询、词条 HTML 与资源显示、发音、词典管理和 Android 本地导入；不拥有 Activity、App 导航、签名、APK 或其他功能实现。

三个功能里程碑均已完成并经总控增量审查；v0.1.0进入App v0.3.0 test.001后暴露WebView主文档误拦截，v0.1.1完成定向修复并进入test.002。用户概括确认test.002除具体内容页面链接被阻止、无法获得内部发音外其余正常，验收通过并明确要求两项暂不修复；当前状态为 `accepted_with_known_issues`。证据见 `WEBVIEW_MAIN_DOCUMENT_FIX_AUDIT.md` 与总控 `_manager/features/feature-02/KNOWN_ISSUES.md`；完整交接见 `MILESTONE_3_HANDOFF.md`，解析来源见 `MILESTONE_1B_AUDIT.md`，许可证原文见 `THIRD_PARTY_NOTICES.md`。
