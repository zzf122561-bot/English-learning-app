# 功能2解析内核决策记录

决策日期：2026-08-24

## 结论

- 不直接或间接引入 `mdict-java`、`lzo-core`、`Gdict`、`mdict-cpp` 或其他 GPL/AGPL 解析器源码。
- Android 运行时采用项目自有纯 Kotlin/JVM `MdictEngine`，使用随机访问与按块解压，不整文件载入内存。
- 固定 MIT 参考为：
  - `whistooy/mdict-reader@e25373923035f06156dbfa8aedeb802b5167e6df`；
  - `encounter/lzokay-rs@c762f2522d0d19ca6e4b6b8ca7ba51b512dc93b0`。
- 参考、改写或移植任何代码时，必须保留原始 MIT 许可证、固定提交、原文件路径和项目内对应文件。

## 原路线失败证据

- `KnIfER/mdict-java@c3bc4e4fd71fc507e5b56b99394d1a5d1f941a2d` 的 `pom.xml` 声明运行依赖 `org.anarres.lzo:lzo-core:1.0.6`。
- `lzo-core` 所属项目的许可声明为 GPL-3.0；没有证据允许把该构件整体视为宽松许可依赖。
- `mdBase.java`、`mdict.java` 虽带 Apache-2.0 文件头，但还调用 `org.anarres.lzo.*`，并引用上游明确划入 GPL 范围的 `com.knziha.rbtree.*` 等包。要形成可维护的宽松许可构件需要大幅裁剪和替换，不再采用。

## 替代路线选择理由

- `mdict-reader` 是独立实现，仓库许可证为 MIT；固定版本包含 MDX/MDD 2.0、Encrypted=2、RIPEMD、LZO 和按块文件访问所需逻辑。
- 其 LZO 依赖锁定为 MIT `lzokay 2.0.1`，没有继续依赖 GPL `lzo-core`。
- 当前主机没有 Rust、Cargo 或 Android NDK。纯 Kotlin/JVM 能避免 ABI、JNI 生命周期和额外原生工具链，符合长期模块化维护目标。

## 里程碑1B审查结论

- 2026-08-24：文件级来源映射、完整 MIT notice、未采用源码/依赖清单和纯 Kotlin/JVM 实现已由功能任务提交。
- 两套用户词典的 Encrypted=2、精确/前缀查询、MDD CSS、PNG/JPEG及随机资源读取已通过；总控强制复跑 5 tests/0 failures，AAR 无 native/JNI，架构边界通过。
- 总控批准里程碑1B并建立审查锚点。后续只审查该锚点之后的新增/修改差异，不重复逐行审查未变化解析代码。
- 本结论只批准进入里程碑2，不代表完整功能、App 集成、设备运行或用户验收通过。

## 离线来源归档

- 功能任务通过 GitHub connector 读取固定提交时连续三次出现 `Transport send error`，没有返回来源文件。
- 用户已于 2026-08-24 明确批准总控从 GitHub 官方 codeload 下载两个固定提交 ZIP。
- 自动下载通道被本机安全审查阻止后，用户人工从登记的 GitHub 官方 codeload 地址下载并按固定文件名落盘。
- 总控已运行 `_manager/tools/verify-source-archives.ps1`：两个 ZIP 的 SHA-256、固定提交顶层目录、MIT 许可证、必要源码文件及 `mdict-reader` 对 `lzokay 2.0.1` 的锁定全部通过。
- 功能任务获准只读这两个精确 ZIP，并可把其内容解压到本功能目录用于来源审计与纯 Kotlin/JVM 实现；不得读取同目录其他文件，也不得执行上游 Rust 代码。
- 离线来源阻塞已经解除；文件级移植审计和两套用户词典兼容门槛随后已通过总控审查。
