# 功能 2 变更记录

## 0.1.0 — 待总控集成

- 2026-08-24：由总控初始化 Android Library 模块、公共契约、权限边界和开发里程碑。
- 2026-08-24：初始化 AAR 构建及全项目架构边界检查通过；尚无功能测试源码。
- 2026-08-24：建立项目自有 `MdictEngine` 隔离接口、基础契约测试和只读 MDX/MDD 头部/哈希审计工具。
- 2026-08-24：确认两套词典的 MDX/MDD 均为 Engine 2.0 且 `Encrypted=2`；mdict-java 固定提交与逐文件依赖审计因上游归档不可达而阻塞，未进入里程碑 2。
- 2026-08-24：确认 mdict-java 必需运行依赖 `org.anarres.lzo:lzo-core:1.0.6` 为 GPL-3.0；总控正式判定许可证硬门槛失败，当前技术路线停止且不得由功能任务自行换库。
- 2026-08-24：收到里程碑 1B 重新简报，路线固定为项目自有纯 Kotlin/JVM `MdictEngine`，仅允许参考两个指定 MIT 固定提交；原 mdict-java/GPL 路线永久禁止。
- 2026-08-24：GitHub connector 连续三次在传输层失败，无法读取固定提交的许可证与参考文件；许可证来源门槛无法审计，已按要求再次阻塞，未开始解析实现、里程碑 2 或其他替代路线。
- 2026-08-24：总控提供两个固定提交离线归档；功能任务复核 SHA-256 与 MIT LICENSE，建立完整 notice、文件级参考映射和未采用依赖清单。
- 2026-08-24：完成纯 Kotlin/JVM MDX/MDD 2.x 内核，使用 `RandomAccessFile` / `FileChannel` 随机读取和按块解压，实现 RIPEMD128 Encrypted=2 key-info、None/LZO/Zlib、精确/前缀查询及 MDD 资源读取；未新增运行依赖。
- 2026-08-24：两套授权真实词典通过 Engine 2.0、Encrypted=2、随机精确查询、前缀建议、CSS、PNG/JPEG 与内存边界验证；未复制词典正文。
- 2026-08-24：模块单元测试、`compileDebugAndroidTestKotlin` 与 `assembleDebug` 通过，只生成 AAR，功能目录 APK=0；停在里程碑 1B 等待总控审查。
- 2026-08-24：总控批准里程碑 1B 并建立审查锚点；完成 `Locale.ROOT`、MDD 重定向防循环/深度、两本词典分别图片魔数和历史审计标记四项定向加固。
- 2026-08-24：公共 Compose 入口迁至稳定包名，实现 FULL / QUICK_LOOKUP、按管理顺序的词典索引签、去重前缀建议、单安全 WebView 与一次关闭语义。
- 2026-08-24：新增独立 `englishapp_dictionary.db` v1、元数据/资源分表、启停/稳定排序、内置不可删除和导入私有副本删除。
- 2026-08-24：实现 schemaVersion=1 内置清单流式哈希安装、幂等/受控替换，以及 SAF 文件夹 MDX/MDD 分卷配对、去重、格式探测、原子提升和完整回滚。
- 2026-08-24：实现受控 `https://dictionary.local/` 资源拦截、安全设置、路径/大小防护和外部导航阻止；实现用户点击触发的 MDD → 严格 HTTPS → 英语 TTS 发音回退及释放。
- 2026-08-24：`--rerun-tasks` 强制回归通过：32 项 JVM 单测，2 个 androidTest 源文件实际编译，AAR 375,767 bytes（SHA-256 `47FECF7D99DA4A73AA2797FB4AE3F7E763546E3C6E63008E5767D7A2CDB0AEEC`）、native/JNI=0、APK=0。没有设备，未宣称仪器/实机运行。
- 2026-08-24：按总控第二次增量审查修复主线程重 I/O、引擎并发边界、Room `REPLACE` 删除风险、真实词条内部链接、真实音频引用链、未知长度音频流上限、发音并发/utteranceId，以及删除根目录与重复MDD分卷防护；未改写里程碑1B解析内核。
- 2026-08-24：两本授权词典真实 `entry://` 改写与精确目标命中通过；两本 MDD 全资源键音频计数均为0，明确记录 `resource=missing / fallback=TTS`，未伪称内置原声命中。
- 2026-08-24：第二次 `--rerun-tasks` 强制回归通过：44项 JVM 单测（原32项全部保留）、2个 androidTest 源文件共8项测试实际编译，AAR 420,005 bytes（SHA-256 `8AB43658462C528339FB85ABA6F4A20D947150EB546C8812010658B04FFC0894`）、native/JNI=0、APK=0。无设备，未运行仪器测试。
- 2026-08-24：总控批准里程碑2；里程碑3公共契约核对发现旧 `MdictEngine.kt` 解析器类型仍为默认public，已仅收紧为internal，未改实现、公共文档契约或数据库。
- 2026-08-24：可见性修复后强制回归通过：44 tests、0 failures/errors/skipped、37 tasks executed、2个androidTest源码共8项编译成功；AAR 420,004 bytes，SHA-256 `95EC34BFE1CF073A0AAD3B167CE751B69BD8A1270BA7997F056CD314FDC74F59`，native/JNI=0、APK=0。
- 2026-08-24：新增 `MILESTONE_3_HANDOFF.md`，整理公共入口、Room、资产/私有目录、根App依赖、导入/WebView/音频/导航边界、测试证据、风险、集成顺序与设备验收清单；状态为 `ready_for_integration / awaiting_manager_commit`。
- 2026-08-24：修复同一Activity复用ViewModel时的公共入口生命周期：FULL→QUICK和连续不同QUICK会提交最新非空initialQuery，安装前查询在就绪后执行一次，FULL(null)不清空状态；QUICK首帧不继承FULL管理态且一次返回只触发一次onClose。公共签名、Room和其他行为不变。
- 2026-08-24：入口生命周期最终强制回归通过：48 tests、0 failures/errors/skipped、37 tasks executed、2个androidTest源码共8项编译成功；AAR 425,353 bytes，SHA-256 `8837BBACF8B5615F028E5D2F9C02CA2B430E3FDD02A8DB2CCAC52CB30069D404`，native/JNI=0、APK=0。
- 0.1.0 仍为待根 App 集成，未执行签名APK、正式发布或设备验收。
