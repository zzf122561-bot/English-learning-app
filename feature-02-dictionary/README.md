# 功能 2：词典

本目录是 EnglishApp 的独立 Android Library 词典模块，目标版本为 `0.1.0`。

模块负责 MDX/MDD 查询、词条 HTML 与资源显示、发音、词典管理和 Android 本地导入；不拥有 Activity、App 导航、签名、APK 或其他功能实现。

里程碑 2 已完成查询与单词条展示、独立 Room v1 数据库、词典管理、内置清单安装、SAF 文件夹导入、安全 WebView 和 MDD → HTTPS → TTS 发音回退。里程碑 1B 的纯 Kotlin/JVM 内核与两套真实词典回归持续通过。模块停在门槛处等待总控增量审查，尚未进入里程碑 3或根 App 集成。增量实现与验证见 `MILESTONE_2_AUDIT.md`，解析来源见 `MILESTONE_1B_AUDIT.md`，许可证原文见 `THIRD_PARTY_NOTICES.md`。
