# 功能 2：词典

本目录是 EnglishApp 的独立 Android Library 词典模块，目标版本为 `0.1.0`。

模块负责 MDX/MDD 查询、词条 HTML 与资源显示、发音、词典管理和 Android 本地导入；不拥有 Activity、App 导航、签名、APK 或其他功能实现。

当前总控初始化和公共入口空壳已完成；原 `mdict-java` 路线因 GPL 依赖失败，正在进行纯 Kotlin/JVM 替代内核的许可证与真实词典兼容门槛。尚未实现可用解析器、数据库或用户功能，开发状态以 `STATUS.md` 为准。
