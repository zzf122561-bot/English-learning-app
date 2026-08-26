# 功能 2：词典

本目录是 EnglishApp 的独立 Android Library 词典模块，当前目标版本为 `0.2.1`。

模块负责 MDX/MDD 查询、词条 HTML 与资源显示、发音、词典管理、本地导入，以及逐词典正文字号；不拥有 Activity、App 导航、签名、APK 或其他功能实现。

v0.2.1 根据 App 1.0.0 test.001 的用户设备反馈，取消词典受控 WebView 内部链接的格式白名单：`dictionary.local` 同源页面、路径、query、fragment、MDD 资源、相对链接、自定义词典 scheme、DOM 动态链接和词典内脚本默认兼容。Oxford9 的真实快捷定位依赖 `onclick` 修改 `className`，因此受控词典文档启用 JavaScript 与本地/内联脚本 CSP；不添加 JS Bridge，外部网页、file/content/intent/跨应用访问、文件访问、内容访问和多窗口仍隔离。

当前状态为 `ready_for_integration / awaiting_manager_build`。自动回归和 AAR 构建已通过，但当前没有 Android 设备，test.001 的失败未由本功能任务复现，v0.2.1 仍等待总控构建 App 1.0.0 test.002 和用户设备验收。增量证据见 `V0.2.1_INTERNAL_LINK_COMPATIBILITY_AUDIT.md`。
