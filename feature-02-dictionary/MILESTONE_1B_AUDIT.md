# 里程碑 1B：来源、实现与真实词典兼容审计

更新时间：2026-08-24

## 结论

项目自有纯 Kotlin/JVM `MdictEngine` 已完成文件级来源审计和两套真实词典兼容验证。生产构建不含 Rust、Cargo、NDK、JNI 或第三方解析器运行依赖；原 mdict-java、lzo-core、Gdict、mdict-cpp 及 GPL/AGPL 路线保持永久禁止。

当前只完成里程碑 1B，并停在兼容门槛等待总控审查。未进入页面、Room、SAF 导入、WebView、发音或里程碑 2。

## 固定来源与完整性

| 来源 | 固定提交 | 本地归档 SHA-256 | 复核结果 | 许可证 |
|---|---|---|---|---|
| `whistooy/mdict-reader` | `e25373923035f06156dbfa8aedeb802b5167e6df` | `87C45EC9B6775B954B3EED20EF213A7E0F259E43727D7042E18803C545C99AE0` | 匹配 | MIT |
| `encounter/lzokay-rs` | `c762f2522d0d19ca6e4b6b8ca7ba51b512dc93b0` | `0E56626FCF4A76A92C6D38D644AFA0FB54761121E4D0227E971F6FF8AB5AF218` | 匹配 | MIT |

两份 LICENSE 原文保留在 `THIRD_PARTY_NOTICES.md`。`mdict-reader/Cargo.lock` 固定 `lzokay 2.0.1`；所有 Cargo 依赖只用于上游来源审计，没有加入 Android 构建。

## 文件级参考映射

| 授权参考文件 | 项目 Kotlin 文件 | 采用范围 |
|---|---|---|
| `mdict-reader/src/mdict/layout/header.rs` | `internal/MdictV2File.kt` | v2 UTF-16LE XML 头、属性、Adler32、Encrypted 位解析 |
| `mdict-reader/src/mdict/layout/index/v1v2.rs` | `internal/MdictV2File.kt` | v2 key/record info、索引尺寸、块偏移与边界 |
| `mdict-reader/src/mdict/layout/index/common.rs` | `internal/BinaryAccess.kt` | 长度前缀文本跳过与 UTF-16 单元宽度 |
| `mdict-reader/src/mdict/codec/crypto.rs` | `internal/Ripemd128.kt`, `internal/MdictV2File.kt` | RIPEMD128 key derivation 与 fast decrypt；未采用 Salsa20/v3 |
| `mdict-reader/src/mdict/codec/compression.rs` | `internal/BlockCodec.kt` | None/LZO/Zlib 分派与输出长度校验 |
| `mdict-reader/src/mdict/layout/blocks.rs` | `internal/BlockCodec.kt`, `internal/MdictV2File.kt` | 8-byte block header、Adler32、词条范围读取 |
| `mdict-reader/src/mdict/reader.rs` | `internal/BinaryAccess.kt`, `internal/MdictV2File.kt` | FileChannel 定位读取、单块解压、虚拟 record stream |
| `mdict-reader/src/mdict/iter.rs` | `internal/MdictV2File.kt`, `PureKotlinMdictEngine.kt` | 逐 key block 建立轻量 key/offset 索引、按 record block 读取 |
| `mdict-reader/src/mdict/types/file_type.rs` | `PureKotlinMdictEngine.kt` | MDX 文本、MDD 字节、`@@@LINK=` 跳转与资源路径 |
| `mdict-reader/src/mdict/utils.rs` | `internal/BinaryAccess.kt`, `internal/MdictV2File.kt` | 大端数字、字符单元宽度、GBK 到 GB18030 规范化 |
| `mdict-reader/tests/fixtures_spec.rs` | `RealDictionaryCompatibilityTest.kt` | 多位置 key/record、MDD 资源与块边界测试策略 |
| `lzokay-rs/src/decompress.rs` | `internal/Lzo1x.kt` | LZO1X 解压状态机；保留 MIT 注释，不采用压缩器 |
| `lzokay-rs/src/lib.rs` | `internal/Lzo1x.kt` | 错误边界语义 |

## 明确未采用的内容与依赖

- 未采用 `mdict-reader/src/main.rs`、CLI、日志、发布工作流、fixture 生成器、v3 index/Salsa20/xxHash、stylesheet 展开和 Rust iterator 类型。
- 未采用 `lzokay-rs/src/compress.rs`、Rust crate 包装、`zerocopy` 或 Rust 测试二进制。
- 未把上游测试 MDX/MDD、图片、音频或二进制 fixture 复制进项目。
- 未使用 `mdict-reader` 的 `adler2`、`byteorder`、`clap`、`encoding_rs`、`env_logger`、`flate2`、`hex`、`log`、`lzokay`、`quick-xml`、`regex`、`ripemd`、`serde_json`、`thiserror`、`twox-hash` 或其传递依赖。
- Kotlin 实现使用 JDK/Android 自带 `FileChannel`、`Charset`、`Inflater`、`Adler32`；RIPEMD128 和 LZO1X 是模块内可审计实现，没有新增运行依赖。
- `Lzo1xTest` 使用 `lzokay-rs/src/decompress.rs` 文档中的 10-byte 示例，验证其解压为预期的 512 bytes；没有复制上游二进制 fixture。

## 随机访问与内存边界

- `BinaryAccess` 以 `RandomAccessFile` 打开只读句柄，并通过 `FileChannel.read(buffer, absolutePosition)` 执行绝对位置读取。
- 打开阶段只保留 headword + record offset 索引；key block 逐块读取和解压，不保留词典正文。
- 查询阶段只解压覆盖目标记录的 record block；资源也按 record 范围读取。
- 未调用 `readBytes()` 或任何整文件载入 API；`wholeFileRead=false` 由诊断字段固定并由测试断言。

| 词典 | MDX+MDD 源字节 | 最大单次文件读取 | 最大压缩块 | 最大解压块 | 整文件读取 |
|---|---:|---:|---:|---:|---|
| Collins | 13,902,812 | 18,688 | 14,332 | 65,273 | 否 |
| Oxford 9 | 45,619,023 | 93,792 | 58,728 | 62,590 | 否 |

所有观测缓冲区均远小于对应源文件组合；词条正文与 MDD 资源只在目标查询期间按需存在。

## 两套真实词典兼容证据

两套 MDX/MDD 均通过 Engine 2.0、`Encrypted=2` / RIPEMD128 key-info 解密、真实 key 精确查询、前缀建议、四处分散词条随机读取、MDD CSS 与三处分散资源读取。未记录或复制词典正文。

### Collins

- 查询 `24-7`：命中，1,538 bytes。
- 查询 `fly-drive`：命中，1,312 bytes。
- 查询 `precursor`：命中，1,628 bytes。
- 查询 `Zulu`：命中，1,425 bytes。
- CSS：`text/css`，11,242 bytes。
- 图片：`image/png`，17,740 bytes；另有随机图片读取 17,813 bytes。

### Oxford 9

- 查询 `007`：命中，2,185 bytes。
- 查询 `Frisian`：命中，3,521 bytes。
- 查询 `protuberance`：命中，3,270 bytes。
- 查询 `’zine`：命中，2,770 bytes。
- CSS：`text/css`，17,129 bytes。
- 图片：`image/jpeg`，10,970 bytes；另有随机图片读取 38,657 和 16,306 bytes。

## 验证边界

- 真实词典验证是 Windows/JVM 模块单元测试；未进行 Android 设备运行验证。
- 词典文件仅只读打开，未修改、移动、复制、上传或提交。
- 本门槛通过不代表页面、数据库、导入、发音或完整 App 已完成。

## 模块构建结果

命令：

```text
powershell.exe -NoProfile -ExecutionPolicy Bypass -File feature-02-dictionary\tools\test-module.ps1
```

- `testDebugUnitTest`：通过，5 tests，0 failures。
- `compileDebugAndroidTestKotlin`：成功执行，当前模块没有 androidTest 源码，结果 `NO-SOURCE`。
- `assembleDebug`：通过。
- AAR：`feature-02-dictionary/build/outputs/aar/feature-02-dictionary-debug.aar`，70,482 bytes，SHA-256 `16C3145BBE60A3089F88E2F94EB56915E50230731A8A746610F859995C80A5DA`。
- AAR native/JNI 条目：0。
- 功能目录 APK：0。
