# 功能 2 当前状态（Android Library）

更新时间：2026-08-24

## 当前阶段

- 用户已明确将“功能2：词典”指定为 `app-feature`。
- 产品方案、技术边界、公共契约和三个开发里程碑已由总控锁定。
- Android Library 功能实现与交接包已完成并生成 AAR；总控边界检查确认为1个 Application、2个 Library。
- 原 `mdict-java` 路线已永久停止：禁止引入 KnIfER/mdict-java、`org.anarres.lzo:lzo-core`、Gdict、mdict-cpp 或任何 GPL/AGPL 解析器源码。
- 总控已批准里程碑 1B 与里程碑 2 并建立审查锚点；里程碑 3 交接已整理为 `ready_for_integration / awaiting_manager_commit`。
- 两个固定来源归档 SHA-256 与授权值匹配，LICENSE 均为 MIT；完整 notice、文件级映射和未采用内容已记录。
- 项目自有 Kotlin/JVM 解析内核已实现 `RandomAccessFile` / `FileChannel` 随机读取、逐块解压、RIPEMD128 key-info 解密、MDX 精确/前缀查询和 MDD 资源读取；没有 Rust、Cargo、NDK、JNI 或解析器运行依赖。
- 两套本地词典的 MDX/MDD 2.0、`Encrypted=2`、随机词条、CSS 与图片兼容验证全部通过；正文未写入报告。
- 已完成唯一公共 Compose 入口、查询/建议/索引签、单 WebView、独立 Room v1、内置清单安装、SAF 文件夹导入、管理与发音回退。
- 里程碑 2 第二次增量修复已完成：重 I/O 调度到可注入 IO dispatcher、引擎并发锁、Room `ABORT` 安全写入、真实词条内部链接改写、真实音频引用驱动回退、流式音频上限、发音串行/utteranceId 关联，以及删除/分卷路径防护。
- 两本授权词典真实 `entry://` 链接改写后均精确命中；两本 MDD 全部资源键的音频资源计数均为0，明确记录 MDD 缺失并回退 TTS。
- 公共契约核对完成：公开源码入口仅 `DictionaryPresentation` 与 `DictionaryFeature`；旧解析接口已最小收紧为 `internal`，没有暴露DAO、实体、解析器、ViewModel或页面内部类型。
- 当前没有 Android 设备；androidTest 源码已编译但未在设备执行，UI/SAF/WebView/MediaPlayer/TTS 均无实机声明。

## 验证边界

| 项目 | 当前状态 |
|---|---|
| 模块源码 | 唯一公共 Compose 入口完成；DAO、实体、解析器、ViewModel与内部页面均未公开 |
| MDX/MDD 解析 | 纯 Kotlin/JVM v2 内核完成；两套真实词典门槛通过 |
| 查询与 UI | FULL / QUICK_LOOKUP、前缀建议、有序标签、单安全 WebView、管理页完成 |
| 数据与文件 | `englishapp_dictionary.db` v1、内置清单、SAF 导入、原子回滚完成 |
| 发音 | 真实词条引用驱动 MDD → 严格 HTTPS → 英语 TTS；单请求、8 MiB 流式上限、无自动联网 |
| 单元测试 | 44 tests，0 failures/errors/skipped；保留原32项及既有5项解析回归 |
| Android 仪器测试 | 2 个真实源码文件、8 项测试；编译成功；无设备，未运行 |
| AAR 构建 | `assembleDebug` 成功；420,004 bytes；SHA-256 `95EC34...4F59`；native/JNI 条目 0 |
| APK | 功能模块禁止生成；边界检查确认数量为 0 |
| 实机验证 | 无连接设备；后续由用户自行验收 |

## 下一步

交接文件见 `MILESTONE_3_HANDOFF.md`。等待总控审查唯一源码可见性修复、执行 Git 提交和根 App 集成；功能任务停止，不修改根 App/Manifest、不生成 APK、不宣称实机通过。
