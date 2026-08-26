# 功能 2 变更记录

## 0.2.1 — 已由总控增量审查；待App 1.0.0 test.002构建

- 2026-08-26：App 1.0.0 test.001 用户设备确认词典应用内链接仍失败；用户要求取消所有词典应用内链接限制。无设备日志、具体词条或原始链接标记，本功能任务未宣称复现。
- 2026-08-26：取消 v0.2.0 对受控内部链接的片段字符/长度、路径、query、fragment、扩展名和少量格式白名单；`dictionary.local` 同源页面与 MDD 资源不再因未知形态直接 403。
- 2026-08-26：相对/绝对同源、`entry:`、`bword:`、其他词典自定义 scheme、DOM 动态链接、普通/编码/空 fragment 默认兼容；跨词条仍替换当前查询，QUICK_LOOKUP 不增加返回层。
- 2026-08-26：真实结构审计确认 Collins 为 `entry:`+相对链接且无脚本；Oxford9 存在 386 个 `onclick`，均通过 `className` 完成快捷定位，词条与 MDD 均无脚本文件。由此仅在受控词典 WebView 启用 JavaScript/内联脚本，并支持相对 MDD `.js` 资源读取；没有注入兼容脚本或 JS Bridge。
- 2026-08-26：外部 HTTP/HTTPS 页面、file/content/intent/android-app/跨应用访问、文件/内容访问、多窗口和外部脚本继续阻止；当前词典 MDD 读取与 8 MiB 上限保持。
- 2026-08-26：`--rerun-tasks` 强制回归通过：63 项 JVM 测试、0 failures/errors/skipped、37 tasks executed；3 个 androidTest 源文件共 10 项编译成功；AAR 468,724 bytes，SHA-256 `69931817087AFB9A500E09D0FFDFF25D1BCAF9B577A40E0313997C662D2223AD`；native/JNI=0、APK=0。
- 总控仅审查本轮WebView增量并独立强制复跑通过；当前无设备，等待构建 App 1.0.0 test.002 和用户设备验收。解析内核、Room、导入、音频、字号与公共契约均未变化。

## 0.2.0 — 已集成；App 1.0.0 test.001内部链接实机不通过

- 2026-08-25：用户重新授权处理 `F02-LINK-001`、`F02-AUDIO-001`，并批准每本词典独立正文字号；未改变公共 `DictionaryFeature` 签名。
- 2026-08-25：新增受控同文档锚点决策；只让精确 `https://dictionary.local/#fragment` 的非空安全片段由 WebView 处理，外部主框架、data 子框架、file/content、脚本、非法受控路径和 MDD 主文档继续阻止；跨词条 `/lookup?q=` 行为不变。
- 2026-08-25：正文可识别发音链接改写为受控内部播放动作，点击不导航；顶部按钮与正文入口共享当前词条候选，固定执行 MDD → 严格 HTTPS → Android 英语 TTS，并保留取消、大小/MIME/重定向与路径防护。
- 2026-08-25：两本授权词典真实审计仍为 `audioReferences=0 / mddAudioResources=0 / resource=missing / fallback=TTS`；未复制正文、未伪称内置原声命中。
- 2026-08-25：词典管理页新增逐词典十档正文字号，档位映射为 `[71,82,88,94,100,112,124,135,153,176]%`；默认 5，只有保存持久化，查询页不新增 Aa。
- 2026-08-25：Room 数据库从 v1 升至 v2；显式 `MIGRATION_1_2` 只执行 `ALTER TABLE dictionaries ADD COLUMN fontLevel INTEGER NOT NULL DEFAULT 5`，不使用 destructive migration；新增 v2 schema 与迁移/隔离更新测试源码。
- 2026-08-25：`--rerun-tasks` 强制回归通过：61 项 JVM 测试、0 failures/errors/skipped、37 tasks executed；3 个 androidTest 源文件共 10 项编译成功；AAR 466,380 bytes，SHA-256 `27890D223523524E9B7C16B2977EC2ED940D1E307BCC946D9B69CD1DDBFF7ECD`；native/JNI=0、APK=0。
- 当前无设备；androidTest 只完成源码编译，WebView、发音、字号与 Room 迁移均未宣称实机或仪器运行通过。状态为 `ready_for_integration / awaiting_manager_build`。

## 0.1.1 — 已由总控集成；test.002用户验收通过并登记历史问题

- 2026-08-24：v0.3.0 test.001用户实机确认查询与Collins/Oxford标签可到达，但正文显示 `data:text/html... / ERR_HTTP_RESPONSE_CODE_FAILURE`；总控定位为WebView data主框架被资源拦截器误返回403。
- 2026-08-24：新增纯策略请求分类；仅主框架的严格base64 HTML data URL或精确受控base URL交给WebView，只有非主框架安全MDD路径进入资源读取器，其余继续403。
- 2026-08-24：新增4项JVM回归，覆盖data主框架放行、data子框架阻止、外部/file/content主框架阻止、精确base URL主框架限制、MDD子资源与非法路径分类。
- 2026-08-24：强制回归通过：52 tests、0 failures/errors/skipped、37 tasks executed、2个androidTest源码共8项编译成功；AAR 430,712 bytes，SHA-256 `0E4D71852AB7A8D520F6C625EB0F18D62C36DAC5CEFF84181D8BF0BF08879FAF`，native/JNI=0、APK=0。
- 总控增量审查、独立复测和主线合并已完成，签名test.002已归档；当前无设备，自动测试不能证明实机正文已恢复，等待用户复测。
- 用户验收：2026-08-24用户概括确认test.002除具体内容页面链接被阻止、无法获得内部发音外其余正常；两项登记为 `F02-LINK-001`、`F02-AUDIO-001`，按用户决定本版接受并暂不修复。

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
