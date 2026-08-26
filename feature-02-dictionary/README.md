# 功能 2：词典

本目录是 EnglishApp 的独立 Android Library 词典模块，当前目标版本为 `0.2.1`。

模块负责 MDX/MDD 查询、词条 HTML 与资源显示、发音、词典管理、本地导入，以及逐词典正文字号；不拥有 Activity、App 导航、签名、APK 或其他功能实现。

v0.1.1已随App v0.3.0由用户概括验收，历史上接受 `F02-LINK-001` 与 `F02-AUDIO-001`；用户于2026-08-25重新授权在v0.2.0处理这两项问题并增加逐词典正文字号。

v0.2.0在不改变公共 `DictionaryFeature` 签名的前提下，接通正文与顶部发音、增加逐词典十档正文字号，并通过显式Room `MIGRATION_1_2`升级到v2；但App 1.0.0 test.001实机确认其受控锚点方案仍不能兼容真实内部链接。

v0.2.1 根据 App 1.0.0 test.001 的用户设备反馈，取消词典受控 WebView 内部链接的格式白名单：`dictionary.local` 同源页面、路径、query、fragment、MDD 资源、相对链接、自定义词典 scheme、DOM 动态链接和词典内脚本默认兼容。Oxford9 的真实快捷定位依赖 `onclick` 修改 `className`，因此受控词典文档启用 JavaScript 与本地/内联脚本 CSP；不添加 JS Bridge，外部网页、file/content/intent/跨应用访问、文件访问、内容访问和多窗口仍隔离。

当前状态为 `manager_review_passed / awaiting_app_1.0.0_test.002_build`。总控独立回归、AAR与边界检查已通过，但当前没有Android设备，v0.2.1仍等待test.002用户验收。增量证据见 `V0.2.1_INTERNAL_LINK_COMPATIBILITY_AUDIT.md`；v0.2.0证据见 `V0.2.0_AUDIT.md`；历史、交接、解析来源与许可证证据继续保留在既有审计文档中。
