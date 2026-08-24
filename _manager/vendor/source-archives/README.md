# 固定源码归档登记

本目录只保存总控批准、可审计且固定到精确提交的第三方源码归档。功能任务只有在总控记录文件大小、SHA-256、顶层提交目录和许可证结果后，才可获得单文件只读授权。

## 功能2固定归档

| 文件名 | 官方下载地址 | 固定提交 | 字节数 | SHA-256 | 状态 |
|---|---|---|---:|---|---|
| `mdict-reader-e25373923035f06156dbfa8aedeb802b5167e6df.zip` | `https://codeload.github.com/whistooy/mdict-reader/zip/e25373923035f06156dbfa8aedeb802b5167e6df` | `e25373923035f06156dbfa8aedeb802b5167e6df` | 90,371 | `87C45EC9B6775B954B3EED20EF213A7E0F259E43727D7042E18803C545C99AE0` | 已校验 |
| `lzokay-rs-c762f2522d0d19ca6e4b6b8ca7ba51b512dc93b0.zip` | `https://codeload.github.com/encounter/lzokay-rs/zip/c762f2522d0d19ca6e4b6b8ca7ba51b512dc93b0` | `c762f2522d0d19ca6e4b6b8ca7ba51b512dc93b0` | 22,151 | `0E56626FCF4A76A92C6D38D644AFA0FB54761121E4D0227E971F6FF8AB5AF218` | 已校验 |

## 落盘后必做校验

- 文件名不得复用或覆盖已有归档。
- ZIP 顶层目录必须包含对应仓库名与完整固定提交。
- `mdict-reader` 必须检查 `LICENSE`、`Cargo.toml`、`Cargo.lock` 和解析实现文件；依赖锁必须能证明使用 `lzokay 2.0.1`。
- `lzokay-rs` 必须检查 `LICENSE`、`Cargo.toml` 和实现文件。
- 两份许可证均须核对为 MIT，并记录 ZIP 文件大小和 SHA-256。
- 校验通过前不得向功能任务宣称来源门槛通过，不得进入兼容实现或 APK 集成。

## 2026-08-24 校验结果

- `_manager/tools/verify-source-archives.ps1` 返回 `SOURCE_ARCHIVE_CHECK_PASSED archives=2`。
- 两个 ZIP 的所有条目均位于“仓库名 + 完整固定提交”顶层目录，未发现绝对路径或 `..` 路径。
- `mdict-reader` 共 60 个条目、20 个 `src/*.rs` 文件；存在 MIT `LICENSE`、`Cargo.toml`、`Cargo.lock`、解析实现和测试入口，锁文件固定 `lzokay 2.0.1`。
- `lzokay-rs` 共 19 个条目、3 个 `src/*.rs` 文件；MIT `LICENSE` 与 `Cargo.toml` 均确认包名 `lzokay`、版本 `2.0.1`、许可证 `MIT`。
- 归档验证只证明来源文件和仓库级许可证可供里程碑1B审计，不等同于项目内文件级移植审计通过，也不证明两套用户词典已兼容。
