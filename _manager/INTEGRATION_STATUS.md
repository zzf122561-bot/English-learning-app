# App 集成状态

文档整理日期：2026-09-23。以下沿用已接收的交接、构建及用户反馈；本轮合并文档未执行新的测试、构建或远端查询。

| 项目 | 当前状态 |
|---|---|
| 总控 Git | 根仓库 `master` 由总控管理，已配置 `origin`；功能任务使用独立 worktree，无提交、标签权限 |
| 当前用户验收基线 | App v0.3.0 / versionCode 4 / 功能1 v0.3.0 / 功能2 v0.1.1；正式标签 `app-v0.3.0`，同一 test.002 二进制提升正式版；该历史版本接受 `F02-LINK-001`、`F02-AUDIO-001` 并暂缓修复，后续候选已由用户重新授权处理 |
| 功能1 | v0.4.0 全文/本段/单句回调已由总控提交ebfd6d8并合并4139f75；26 tests/0 failures，Android源码编译和AAR通过，沿用模块交接证据。Room/原学习行为不变；新增朗读交互尚未实机验收 |
| 功能2 | v0.2.1已增量审查、独立复测并合并：63 tests/0 failures，3个androidTest源码共10项编译，AAR 468,724 bytes；Oxford9真实快捷定位为`onclick/className`，受控词典内部默认放行 |
| 根 App 壳 | 开发中的1.1.0 / versionCode 6，新增“语音”第三入口与文章朗读接线；等待新模块契约交付后集成编译。包名、签名、minSdk29、原词典INTERNET权限不变；唯一正式Application集成三个Library，独立测试宿主保留但不被正式工程包含 |
| 内置词典 | 四个 MDX/MDD 构建前校验登记 SHA-256；生成 schemaVersion 1 清单，Git 忽略且不修改用户原文件 |
| 长期签名 | 已复制到 Git 外总控位置，证书指纹与 v0.2.0 一致 |
| v0.2.1 测试 APK | `test.001` 已从提交 `d18054e` 完整重建、签名并归档；仅标记自动构建通过 |
| v0.3.0 APK | `test.001` 用户实机不通过并永久保留；同一`test.002`二进制已获用户验收并提升正式版，不重新构建 |
| v1.0.0 APK | `test.001`用户实机不通过并永久保留；`test.002`已从干净提交`5719bb3`构建、签名和永久归档，等待用户覆盖安装验收 |
| 跨对话治理 | 独立 worktree 和固定格式汇报已跑通；首次全局 memory 误读已审计、修订规则并二次确认通过 |
| GitHub同步 | 最近接收的同步任务成功回报：`main=ce9bf2554bcfd2f339f60fa0161b3cf59c5eef5a`、`codex/1=207e308bb3d3106648eb82550698e1eb98d2baff`、`codex/feature-02-dictionary=de23797a29fa2c0c3927ce376bca6c4f8c3a65d5`；五个历史标签此前已同步。此处为已接收报告，非实时远端查询；后续治理提交仅在本地保存，推送按需授权 |
| 设备实机验证 | 2026-08-26用户确认v1.0.0 test.001词典内部链接仍不可用；其余本候选验收项未在本次反馈中确认；总控无设备 |

## 状态与证据边界

- 总控集成准备（2026-10-03）：已写三入口、文章朗读ViewModel路由（避免把整篇文章放进saved-state Bundle）及三种范围映射。7项宿主纯JVM测试已用既有Kotlin2.4.10/JUnit4.13.2直接编译真实ReadingRoute.kt与ReadingRouteTest.kt执行通过（7/0），精确源哈希在`_manager/local/reading-route-test/4f3cd647a0b24d0fb6acb10feaf2b874/RESULT.json`；不覆盖Compose或Android导航。功能1契约交付已合并，语音新接口仍在开发，尚不能整包编译。正式模型新副本已复制并校验，GGUF不压缩；打包脚本维护干净提交来源收据、长期签名连续性与完整APK资产/原生库校验。脚本语法检查及1 Application/3 Library架构边界通过。没有构建或交付1.1.0 APK。

- 2026-10-03正式语音内嵌目标已由用户明确批准，独立测试App同时保留。语音任务报告一次手机合成与“完整且清晰，没有明显漏读或重复”的用户确认；详细证据见其SOURCE_MANIFEST第9节，不代表新版本已验收。现向原语音任务下发[正式语音简报](features/feature-03-speech/BRIEFING.md)，向功能1下发[文章朗读回调简报](features/feature-01/SPEECH_BRIEFING.md)。目标候选App1.1.0/code6，等待两模块增量交接及总控宿主集成；尚未构建此候选。

- 2026-10-03独立本地模型验证test.001已签名归档（来源f098fda）；首期Qwen3-TTS英语朗读，公共基础支持后续独立引擎/任务适配。APK与手机步骤见 [测试宿主入口](probes/tts-device/README.md)，仅自动构建/打包验证通过，等待用户设备执行；不改变正式App版本、功能基线或设备验收状态。

- 2026-08-24 用户概括确认 App v0.3.0 test.002 除词条内容链接及内部发音外其余正常，未提供设备型号、Android 版本、逐项截图或日志；该结论仅适用于当时版本，不自动提升 App 1.0.0 的验收状态。
- 两套私有词典由总控在构建时校验并生成 Git 忽略的资产，文件登记与清单契约统一见 [BUILTIN_ASSETS.md](features/feature-02/BUILTIN_ASSETS.md)。
- 发布与每份 APK 的正式记录以 [发布索引](releases/app/INDEX.md) 为入口；功能行为基线以各功能的 `FEATURE_BASELINE.md` 为准，模块自动验证状态在各自 `STATUS.md` 维护。
