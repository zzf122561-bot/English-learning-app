# 里程碑 1 历史失败路线审计（已废止）

> 本文件只记录已永久停止的 mdict-java/GPL 失败路线，不代表当前状态。
> 已批准的项目自有纯 Kotlin/JVM 路线、许可证与兼容结论以
> `MILESTONE_1B_AUDIT.md` 为准。

更新时间：2026-08-24

## 里程碑 1B 重新简报与当前阻塞

总控已永久终止下述 `mdict-java` 路线，并把新路线固定为项目自有纯 Kotlin/JVM `MdictEngine`。仅授权只读参考：

- `whistooy/mdict-reader@e25373923035f06156dbfa8aedeb802b5167e6df`（MIT）；
- `encounter/lzokay-rs@c762f2522d0d19ca6e4b6b8ca7ba51b512dc93b0`（MIT，仅 LZO1X 解压参考）。

2026-08-24 审计尝试均失败于 GitHub connector 到 `https://chatgpt.com/backend-api/ps/mcp` 的传输层，未返回任何仓库内容：

1. `github.fetch`：两个固定提交的仓库树，并行请求均为 `Transport send error`；
2. `github.fetch`：`whistooy/mdict-reader` 固定提交的 Contents API 根目录，同一传输错误；
3. `github.fetch_file`：`whistooy/mdict-reader` 固定提交的 `LICENSE`，同一传输错误。

因此尚未取得许可证原文、`Cargo.toml` / `Cargo.lock`、实现或测试文件，不能建立可信的来源文件到项目文件映射，也不能开始派生实现。按里程碑 1B 硬门槛立即标记 `BLOCKED`；未使用终端网络、未浏览其他仓库、未引入任何第三方源码或依赖。

结论：`LICENSE GATE FAILED / BLOCKED`。总控已确认 `mdict-java` 的必需 `lzo-core:1.0.6` 运行依赖为 GPL-3.0，不符合“仅允许 Apache-2.0 核心和宽松许可依赖”的硬门槛。按计划立即停止，不得进入里程碑 2，也不得由本任务自行更换解析库。

## mdict-java 上游证据

- 官方仓库：<https://github.com/KnIfER/mdict-java>
- 固定提交：未继续取得。许可证依赖门槛已先行失败，无需为当前路线继续下载或引入源码。
- 上游 README 明确把 `com.knziha.plod.dictionary.*` 包声明为 Apache-2.0，把 builder、UI 与 Android App 等其余代码声明为 GPL-3.0。
- 上游 `License.txt` 只包含 GPL-3.0 全文，没有 Apache-2.0 全文。因此必须以固定提交逐文件确认纳入范围、包名、文件头、跨包引用和依赖，不能直接复制整个仓库或整个构建产物。
- 官方 POM 的运行依赖候选包括 `kxml2:2.3.0`、`commons-text:1.8`、`fastjson:1.2.62`、`lzo-core:1.0.6`、`jcodings:1.0.45` 和 `icafe:1.1-SNAPSHOT`；哪些被 Apache-2.0 核心实际引用仍待固定源码确认。
- `org.anarres.lzo:lzo-core:1.0.6` 的上游 `COPYING` 把整个包声明为 GPL-3.0，并说明额外权利只对带对应文件头的单个文件有效。mdict-java POM 把它作为运行依赖引入，因此当前依赖路线不满足宽松许可硬门槛；总控已据此正式判定失败。

禁止纳入：`PlainDict`、`dictionaryBuilder`、`dictionarymanager`、`dictionarymodels`、`ebook`、`settings`、`widgets`、根 `mdictBuilderBase.java`、Gdict 或任何其他 GPL/AGPL 代码。

## 未执行的逐文件清单

以下是失败判定前准备的上游包内文件清单。当前路线已停止，这些文件均未引入本项目：

- `mdBase.java`
- `mdict.java`
- `mdictRes.java`
- `Utils/AutoCloseFileStream.java`
- `Utils/BSI.java`
- `Utils/BU.java`
- `Utils/F1ag.java`
- `Utils/FIag.java`
- `Utils/Flag.java`
- `Utils/GetIndexedInteger.java`
- `Utils/GetIndexedString.java`
- `Utils/IU.java`
- `Utils/LinkastReUsageHashMap.java`
- `Utils/MyIntPair.java`
- `Utils/MyPair.java`
- `Utils/ReusableBufferedInputStream.java`
- `Utils/ReusableByteInputStream.java`
- `Utils/ReusableByteOutputStream.java`
- `Utils/SU.java`
- `Utils/key_info_struct.java`
- `Utils/myCpr.java`
- `Utils/record_info_struct.java`
- `Utils/ripemd128.java`

清单来自上游 `master` 文件树，仅用于准备审计，不代表允许引入，也不代表固定提交。

## 本地词典只读证据

工具：`tools/audit-mdict-headers.py`。工具只流式读取文件，不写入词典目录。

| 词典文件 | 大小 | SHA-256 | Engine | Encrypted |
|---|---:|---|---|---|
| 柯林斯 MDX | 13,887,505 | `14843f7e443fb1cdacc94145c9ae68879a582f13fa68399d21caa5768a704c10` | 2.0 | 2 |
| 柯林斯 MDD | 15,307 | `15a1668eab08dbad960c942cb99a7842ac8c7b77736a1936a4e77fd00fe6ed6c` | 2.0 | 2 |
| 牛津 MDX | 41,514,928 | `8e140c288d9f8d195f707f1297add0233e9cb44c31dc0bbcfeb46063c99c24be` | 2.0 | 2 |
| 牛津 MDD | 4,104,095 | `4ed26627cd7fc26916ca23cf9a3af44fd03710eecfcddc8607b2717c1aaf1bae` | 2.0 | 2 |

四个文件均为 `GeneratedByEngineVersion=2.0`、`RequiredEngineVersion=2.0`、`Encrypted=2`。这只证明测试材料要求 MDX/MDD 2.0 和 RIPEMD128 key-info 解密；尚未证明解析器能够完成解密、精确查询、前缀查询或读取 CSS/图片资源。

## 已完成的项目隔离

- 已建立项目自有 `MdictEngine`、`MdictEngineFactory`、输入/输出模型和异常边界。
- 接口要求随机访问、按需解压、不得整文件载入内存、不得修改源文件。
- 基础契约测试源码已建立，但 Gradle 由于插件依赖不可解析而尚未执行成功。

## 停止边界

- 不引入 mdict-java 或 lzo-core 源码/JAR。
- 不继续真实查询、MDD 资源兼容验证、数据库、页面、发音或导入开发。
- 不自行调研、选择或接入替代解析库。
- 只有总控在完成新的许可证清晰技术决策并重新下发明确任务后，功能任务才能恢复。
