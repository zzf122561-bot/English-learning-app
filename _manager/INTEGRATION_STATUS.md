# App 集成状态

文档整理日期：2026-09-23。以下沿用已接收的交接、构建及用户反馈；本轮合并文档未执行新的测试、构建或远端查询。

| 项目 | 当前状态 |
|---|---|
| 总控 Git | 根仓库 `master` 由总控管理，已配置 `origin`；功能任务使用独立 worktree，无提交、标签权限 |
| 当前用户验收基线 | App v0.3.0 / versionCode 4 / 功能1 v0.3.0 / 功能2 v0.1.1；正式标签 `app-v0.3.0`，同一 test.002 二进制提升正式版；该历史版本接受 `F02-LINK-001`、`F02-AUDIO-001` 并暂缓修复，后续候选已由用户重新授权处理 |
| 功能1 | v0.5.1已提交1453bdb并合并8379c34，去学习编号文本18项相关JVM通过；首段UI测试仅编译，未证明手机生成/播放可用。DB/原文不变，待语音共同修复后打包 |
| 功能2 | v0.2.1已增量审查、独立复测并合并：63 tests/0 failures，3个androidTest源码共10项编译，AAR 468,724 bytes；Oxford9真实快捷定位为`onclick/className`，受控词典内部默认放行 |
| 根 App 壳 | saber 1.2.0 / versionCode 7 test.001自动证据保留；10月7日用户核心语音验收不通过，已收JSON/WAV和截图，修复中。包名、签名、minSdk29及权限不变，旧APK保留 |
| 共享语音 | 0.3.0首批提交05981da，重复句差异提交49baa81、合并4b99ec5；沿用SOURCE_MANIFEST第11/12节证据。最新成功保留、删除与重复句一次朗读就绪；新流程未手机验收 |
| 内置词典 | 四个 MDX/MDD 构建前校验登记 SHA-256；生成 schemaVersion 1 清单，Git 忽略且不修改用户原文件 |
| 长期签名 | 已复制到 Git 外总控位置，证书指纹与 v0.2.0 一致 |
| v0.2.1 测试 APK | `test.001` 已从提交 `d18054e` 完整重建、签名并归档；仅标记自动构建通过 |
| v0.3.0 APK | `test.001` 用户实机不通过并永久保留；同一`test.002`二进制已获用户验收并提升正式版，不重新构建 |
| v1.0.0 APK | `test.001`用户实机不通过并永久保留；`test.002`已从干净提交`5719bb3`构建、签名和永久归档，等待用户覆盖安装验收 |
| 跨对话治理 | 独立 worktree 和固定格式汇报已跑通；首次全局 memory 误读已审计、修订规则并二次确认通过 |
| GitHub同步 | 最近接收的同步任务成功回报：`main=ce9bf2554bcfd2f339f60fa0161b3cf59c5eef5a`、`codex/1=207e308bb3d3106648eb82550698e1eb98d2baff`、`codex/feature-02-dictionary=de23797a29fa2c0c3927ce376bca6c4f8c3a65d5`；五个历史标签此前已同步。此处为已接收报告，非实时远端查询；后续治理提交仅在本地保存，推送按需授权 |
| 设备实机验证 | 2026-08-26用户确认v1.0.0 test.001词典内部链接仍不可用；其余本候选验收项未在本次反馈中确认；总控无设备 |

## 状态与证据边界

- 2026-10-08接收语音0.3.1候选：3023608提交、d4cda40合并；无源码冲突，STATUS冲突保留历史后解决。两轮50个不同模块JVM、Android源码/AAR/Lint及JNI静态证据按SOURCE_MANIFEST第13节沿用，不重复复跑。新原生诊断/队列预算/有限恢复与功能1去编号输入将组成1.2.0 test.002（仍code7）；只补宿主干净输入映射和新APK编译/打包。模型不变，真实怪音、001链与提速未验收，不把修复候选当已可用。

- 2026-10-07接收功能1 0.5.1交接：源码原因已确认是将带编号englishText直接送语音；使用target元数据建立独立干净文本后再分句。模块18测试/Android源码/AAR证据沿用，不重复复测；只检查新公共语义与新增Compose测试依赖（非release依赖），并因构建配置变化补架构检查，结果1 Application/3 Library通过。合并仅STATUS历史段冲突，无源码冲突。新契约摘要已写入语音原授权简报并转达，真正生成/播放失效仍在修复，不单独打包或提高验收级别。

- 2026-10-07新增用户反馈：最近交付的saber候选核心语音不可用（确切安装版本截图待补），1.2.0 test.001登记为`user_rejected_speech / repair_in_progress`，不能正式提升。唯一反馈正文见该候选ACCEPTANCE.md；布局基本可接受，不重做UI。语音任务负责截断/异常WAV/全文链路与可测提速，功能1负责001段选择链路和送语音前去学习编号；总控负责接口与根App连接，不以旧自动证据否定实机失败。

- 2026-10-06交付[saber 1.2.0 test.001](releases/app/v1.2.0/test.001/RELEASE.md)，当前`awaiting_user_device_validation`。完整验证与哈希只在该发布记录/ARTIFACT维护；尚无本候选设备执行/正式确认。语音与功能1停止等待反馈，无GitHub推送。

- 2026-10-06：已接收语音完整交接，45通过/1跳过与8项收尾测试按各自范围沿用，不把过滤执行后的8项XML当成首批全量结果。两个合并仅STATUS历史段冲突，无源码冲突。主App以稳定documentId和内容/选区摘要映射公共请求，不把正文存入Bundle；不再调用独立SpeechFeature或打开文章朗读覆盖页。只补宿主映射测试、集成编译与产物检查。

- 2026-10-05功能1 v0.5.0已接收、提交567b6b6并合并770e904。槽签名与简报一致，沿用11项受影响测试、Android测试源码编译和AAR交接，未重复运行旧测试。合并仅STATUS历史记录冲突，没有源码冲突。最大180dp槽约束已持久写入语音简报并通知语音任务；当时语音0.3.0尚未交接，当前进度见上方。

- 2026-10-05用户调整交互与音频保留规则：原阅读页内生成/播放，语音Tab仅模型/声音设置，已生成项目可删、同范围只保留最新成功版。执行范围见已有功能1/语音简报顶部修订，拟App1.2.0/code7；同时改名`saber`并加入Saber图标。当前处于派工/资源验证阶段，不把1.1.0 test.001标为本次改版通过，也不覆盖旧APK。

- 2026-10-03完成正式内嵌候选：三范围、音色/个性化要求、播放倍速及历史/缓存的模块证据按交接沿用；新增主App集成构建、8项宿主测试、1 Application/3 Library边界、签名及全部包内资产检查通过。交付[1.1.0 test.001](releases/app/v1.1.0/test.001/RELEASE.md)，本次目标处于`awaiting_user_device_validation`，不冒称实机通过或正式发布。
- 独立宿主与新共享模块兼容编译：2026-10-03在根工程使用既有JDK/SDK/hosts环境执行Gradle `-p _manager/probes/tts-device --no-daemon --no-configuration-cache --max-workers=2 :app:compileDebugKotlin`，BUILD SUCCESSFUL in 53s，14 tasks（9 executed / 5 up-to-date）。只编译新连接，不生成/覆盖旧独立APK；原测试App继续保留。

### 历史阶段记录（以下“等待开发/构建”为当时状态，已由上述交付取代）

- 宿主返回增量（2026-10-03）：关闭文章朗读按requestId校验，旧页面延迟回调不能清除新请求；重复关闭安全。新增1项测试后，ReadingRoute真实源的8项JVM测试全部通过；本轮精确源哈希证据为`_manager/local/reading-route-test/1d32bc54f5694f688d8cf303872699df/RESULT.json`，取代下述7项对当前源的覆盖，历史结果仍保留。不扩大为Compose/设备验证。

- 总控集成准备（2026-10-03）：已写三入口、文章朗读ViewModel路由（避免把整篇文章放进saved-state Bundle）及三种范围映射。7项宿主纯JVM测试已用既有Kotlin2.4.10/JUnit4.13.2直接编译真实ReadingRoute.kt与ReadingRouteTest.kt执行通过（7/0），精确源哈希在`_manager/local/reading-route-test/4f3cd647a0b24d0fb6acb10feaf2b874/RESULT.json`；不覆盖Compose或Android导航。功能1契约交付已合并，语音新接口仍在开发，尚不能整包编译。正式模型新副本已复制并校验，GGUF不压缩；打包脚本维护干净提交来源收据、长期签名连续性与完整APK资产/原生库校验。脚本语法检查及1 Application/3 Library架构边界通过。没有构建或交付1.1.0 APK。

- 2026-10-03正式语音内嵌目标已由用户明确批准，独立测试App同时保留。语音任务报告一次手机合成与“完整且清晰，没有明显漏读或重复”的用户确认；详细证据见其SOURCE_MANIFEST第9节，不代表新版本已验收。现向原语音任务下发[正式语音简报](features/feature-03-speech/BRIEFING.md)，向功能1下发[文章朗读回调简报](features/feature-01/SPEECH_BRIEFING.md)。目标候选App1.1.0/code6，等待两模块增量交接及总控宿主集成；尚未构建此候选。

- 2026-10-03独立本地模型验证test.001已签名归档（来源f098fda）；首期Qwen3-TTS英语朗读，公共基础支持后续独立引擎/任务适配。APK与手机步骤见 [测试宿主入口](probes/tts-device/README.md)，仅自动构建/打包验证通过，等待用户设备执行；不改变正式App版本、功能基线或设备验收状态。

- 2026-08-24 用户概括确认 App v0.3.0 test.002 除词条内容链接及内部发音外其余正常，未提供设备型号、Android 版本、逐项截图或日志；该结论仅适用于当时版本，不自动提升 App 1.0.0 的验收状态。
- 两套私有词典由总控在构建时校验并生成 Git 忽略的资产，文件登记与清单契约统一见 [BUILTIN_ASSETS.md](features/feature-02/BUILTIN_ASSETS.md)。
- 发布与每份 APK 的正式记录以 [发布索引](releases/app/INDEX.md) 为入口；功能行为基线以各功能的 `FEATURE_BASELINE.md` 为准，模块自动验证状态在各自 `STATUS.md` 维护。
