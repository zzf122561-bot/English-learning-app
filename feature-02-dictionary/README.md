# 功能 2：词典

本目录是 EnglishApp 的独立 Android Library 词典模块，当前版本与验收状态统一见 [STATUS.md](STATUS.md)。

模块负责 MDX/MDD 查询、词条 HTML 与资源显示、发音、词典管理、本地导入，以及逐词典正文字号；不拥有 Activity、App 导航、签名、APK 或其他功能实现。

v0.1.1修复了词条 HTML 主文档被误拦截的问题；后续 v0.2.0处理词条链接与发音，并增加逐词典正文字号。

v0.2.0在不改变公共 `DictionaryFeature` 签名的前提下，接通正文与顶部发音、增加逐词典十档正文字号，并通过显式Room `MIGRATION_1_2`升级到v2。

v0.2.1 根据 App 1.0.0 test.001 的用户设备反馈，取消词典受控 WebView 内部链接的格式白名单：`dictionary.local` 同源页面、路径、query、fragment、MDD 资源、相对链接、自定义词典 scheme、DOM 动态链接和词典内脚本默认兼容。Oxford9 的真实快捷定位依赖 `onclick` 修改 `className`，因此受控词典文档启用 JavaScript 与本地/内联脚本 CSP；不添加 JS Bridge，外部网页、file/content/intent/跨应用访问、文件访问、内容访问和多窗口仍隔离。

测试结果、App 交付进度、用户反馈和既有审计证据入口统一见 [STATUS.md](STATUS.md)。历史功能变化见 [CHANGELOG.md](CHANGELOG.md)，公开调用签名见 [PUBLIC_CONTRACT.md](PUBLIC_CONTRACT.md)。
