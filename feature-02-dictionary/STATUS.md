# 功能 2 当前状态（Android Library）

更新时间：2026-08-24

## 当前阶段

- 用户已明确将“功能2：词典”指定为 `app-feature`。
- 产品方案、技术边界、公共契约和三个开发里程碑已由总控锁定。
- Android Library 功能实现与交接包已完成并生成 AAR；总控边界检查确认为1个 Application、2个 Library。
- 原 `mdict-java` 路线已永久停止：禁止引入 KnIfER/mdict-java、`org.anarres.lzo:lzo-core`、Gdict、mdict-cpp 或任何 GPL/AGPL 解析器源码。
- v0.1.0已由总控集成进入v0.3.0 test.001；用户实机确认查询标签出现但WebView正文因data主框架被误拦截而失败。
- v0.1.1只修复WebView主文档请求分类，已由总控审查、合并并进入签名test.002；用户验收通过，当前为 `accepted_with_known_issues`。
- 两个固定来源归档 SHA-256 与授权值匹配，LICENSE 均为 MIT；完整 notice、文件级映射和未采用内容已记录。
- 项目自有 Kotlin/JVM 解析内核已实现 `RandomAccessFile` / `FileChannel` 随机读取、逐块解压、RIPEMD128 key-info 解密、MDX 精确/前缀查询和 MDD 资源读取；没有 Rust、Cargo、NDK、JNI 或解析器运行依赖。
- 两套本地词典的 MDX/MDD 2.0、`Encrypted=2`、随机词条、CSS 与图片兼容验证全部通过；正文未写入报告。
- 已完成唯一公共 Compose 入口、查询/建议/索引签、单 WebView、独立 Room v1、内置清单安装、SAF 文件夹导入、管理与发音回退。
- 里程碑 2 第二次增量修复已完成：重 I/O 调度到可注入 IO dispatcher、引擎并发锁、Room `ABORT` 安全写入、真实词条内部链接改写、真实音频引用驱动回退、流式音频上限、发音串行/utteranceId 关联，以及删除/分卷路径防护。
- 两本授权词典真实 `entry://` 链接改写后均精确命中；两本 MDD 全部资源键的音频资源计数均为0，明确记录 MDD 缺失并回退 TTS。
- 公共契约核对完成：公开源码入口仅 `DictionaryPresentation` 与 `DictionaryFeature`；旧解析接口已最小收紧为 `internal`，没有暴露DAO、实体、解析器、ViewModel或页面内部类型。
- 根App接线阻塞已修复：共享Activity ViewModel时，FULL→QUICK与连续不同QUICK会提交最新非空 `initialQuery`；安装前查询在就绪后执行一次；QUICK首帧不继承FULL管理态且一次返回只调用一次 `onClose`。公共签名和Room不变。
- WebView请求分类已拆为主文档放行、受控MDD子资源读取和阻止三类：严格data HTML主框架与精确base URL交给WebView；用户确认test.002正文恢复。
- 用户实机发现具体内容页面链接被阻止、无法获得内部发音，分别登记为 `F02-LINK-001`、`F02-AUDIO-001`；用户明确接受本版并要求暂不修复。
- 总控当前没有连接Android设备；androidTest源码已编译但未在设备执行。用户已提供概括验收结论，但没有逐项UI/SAF/WebView/MediaPlayer/TTS日志。

## 验证边界

| 项目 | 当前状态 |
|---|---|
| 模块源码 | 唯一公共 Compose 入口完成；DAO、实体、解析器、ViewModel与内部页面均未公开 |
| MDX/MDD 解析 | 纯 Kotlin/JVM v2 内核完成；两套真实词典门槛通过 |
| 查询与 UI | FULL / QUICK_LOOKUP、前缀建议、有序标签、单安全 WebView、管理页完成 |
| 数据与文件 | `englishapp_dictionary.db` v1、内置清单、SAF 导入、原子回滚完成 |
| 发音 | 真实词条引用驱动 MDD → 严格 HTTPS → 英语 TTS；单请求、8 MiB 流式上限、无自动联网 |
| 单元测试 | 52 tests，0 failures/errors/skipped；新增4项主文档/子资源分类回归 |
| Android 仪器测试 | 2 个真实源码文件、8 项测试；编译成功；无设备，未运行 |
| AAR 构建 | `assembleDebug` 成功；430,712 bytes；SHA-256 `0E4D71...79FAF`；native/JNI 条目 0 |
| APK | 功能模块禁止生成；边界检查确认数量为 0 |
| 实机验证 | 用户概括确认test.002除两项登记问题外其余正常；总控无设备且无逐项日志 |

## 下一步

v0.1.1已由用户验收并随App v0.3.0正式提升；两项已知问题按用户要求只记录、不修复。功能任务保持停止，后续必须等待总控新的精确简报。
