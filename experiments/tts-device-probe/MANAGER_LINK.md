# 语音手机验证开发简报

## 授权和通信

- 用户于2026-10-02明确授权开始独立测试 App，并允许创建新对话；随后要求新对话模型更高、配置齐全。此次 app-feature 授权仅限本测试任务，不代表正式听写模块立项。
- 用户随后明确要求此App适宜长期扩展，未来还会内嵌其他大模型；因此它是可持续扩展的独立本地模型验证App，不是一次性丢弃的Qwen测试壳。当前只实现语音场景，不凭此增加聊天、图像、云服务或新模型下载。
- 总控：ENG-APP管理员（原总纲），任务ID `01a024cb-1fba-7b23-a357-67eb2ad592b4`。
- 用户既有授权功能任务向总控主动汇报，本任务按同一机制发送消息。仅向上述ID汇报，不联系其他功能或外部项目。
- 允许额外只读：根 AGENTS.md、PROJECT.md、gradle/libs.versions.toml；_manager/REPORTING_PROTOCOL.md、_manager/tools/gradle-hosts.txt；_manager/probes/tts-device 下 settings.gradle.kts、build.gradle.kts、gradle.properties、app/build.gradle.kts、app/src/main/AndroidManifest.xml、app/src/main/java/com/xuesui/englishapp/modelprobe/MainActivity.kt。
- 不授权读取原 DOCX、short-story-generator、其他 _manager 文件、全局 memory、其他会话历史。
- 可使用本机 D:/CodexData/android-toolchain/jdk-17、android-sdk、gradle/gradle-9.5.0 执行模块构建；缺 NDK/CMake 等工具时报告所需固定版本，总控安装。2026-10-02总控只读检查 SDK 中尚无 ndk/cmake 目录。
- 主动汇报格式沿用授权 REPORTING_PROTOCOL；消息失败写本目录 MANAGER_OUTBOX.md，不能称已交接。不要发送纯确认回执或反复进度消息。

## 技术路线与首轮门槛

1. 先获取并固定精确源码提交和各依赖提交，确认许可证/notice及1.7B CustomVoice命名音色的实际支持；不要只依赖README。
2. 优先候选： https://github.com/Danmoreng/qwen3-tts.cpp 及其 Android/JNI 接线参考 https://github.com/Danmoreng/qwen3-tts-android 。官方模型语义以 https://github.com/QwenLM/Qwen3-TTS 为准。
3. GGUF候选 https://huggingface.co/Serveurperso/Qwen3-TTS-GGUF ，首选 qwen-talker-1.7b-customvoice-Q4_K_M.gguf + qwen-tokenizer-12hz-Q4_K_M.gguf 。必须核实所选引擎与这个导出格式匹配；GGUF同扩展名不代表格式兼容。该model card的Base/CustomVoice用途表与官方说明存在反转，禁止照抄。
4. 另一明确候选 https://github.com/ServeurpersoCom/qwentts.cpp 仅在首选格式/Android支持不符时比较，变更主路线前报告总控；不得自行改模型家族或复制GPL组件。
5. 固定SHA、许可证、GGML依赖、模型revision/LFS SHA-256、所需NDK/CMake、16KB页兼容策略和真实API能力形成一份 SOURCE_MANIFEST.md。网络失败可尝试同一公开官方仓库的GitHub connector/web/raw/codeload路径，不读取凭据，不更改代理/系统配置。
6. 本轮到完成此门槛及必要原生编译验证为止，主动给总控一次 compatibility_gate_passed 或 blocked 报告，等总控确认再实现完整UI与模块。禁止提前做一个空壳冒充模型已运行。

## 完整测试模块设计（首轮门槛通过后下发实施）

- 唯一公开入口 `@Composable fun ModelProbeFeature()`，包 `com.xuesui.englishapp.modelprobe.runtime`。Activity/Manifest/包名/签名/最终APK由总控维护。模块允许内部JNI等必要入口，但不成为其他功能API。
- 架构必须适宜后续模型开发：本Library内先按 core/model、core/runtime、core/metrics、core/storage、engines/qwen3tts、features/speech 隔离；后续模型有各自 engines/<id> 和 features/<task>，不互相读取/依赖实现。将来由总控在实际第二个引擎加入时决定独立Gradle模块，不为假想功能预建大量空模块。
- 公共模型描述包含稳定ID、版本、task类型、运行时/格式、模型文件revision/hash、license、硬件/ABI、capabilities。注册表由显式受信任适配器构成，不动态加载任意第三方代码或把任意GGUF当成兼容模型。
- 通用层负责校验/原子安装、私有存储、加载与释放、单任务调度/取消、测量/导出和历史；TTS请求/音频结果使用任务专属类型，不能将日后的文本生成强塞到TTS接口。
- 参数定义归适配器所有：具名类型、默认值、范围、支持条件、schemaVersion；公共界面根据capabilities显示，按modelId+engineVersion保存，预留显式迁移。Qwen音色/采样参数不可硬编码进通用核心。
- 模型文件、生成结果、性能报告分别存储；删除/替换模型不默默删除历史结果，运行中不能删除正在使用的文件。模型/缓存变更不触及正式EnglishApp。
- 提交一个精简 ARCHITECTURE.md 说明边界、契约草案和“以后加入第二个不同任务模型”需要改的具体位置；首轮门槛同时交接它，避免锁死于Qwen。
- 中文简洁测试界面：状态、测试文本、生成/停止、播放/重播、结果/导出、设置。默认从宿主 assets 的 probe-sample.txt 读取；允许编辑及恢复样例，样例内容不写入受跟踪源码或日志。
- 模型管理：实际可用的模型/量化选择、文件存在/大小/SHA256校验、载入/卸载、加载错误，默认1.7B Q4。不默认下载任何模型，不同时驻留多个模型。总控决定模型内置打包，保留模型文件导入接口供调试但不取代内嵌目标。
- 配置齐全但必须真实：英语固定；实际支持的Ryan/Aiden等音色；支持时提供语气指令；CPU线程数；temperature/top-k/top-p/repetition penalty/max tokens/seed等仅暴露真实API参数，范围校验、保存、恢复默认。当前CPU是首选后端，GPU/NPU没有验证则标不支持，不能放虚假开关。
- 语速须区分合成端能力与播放器变速。不能把后处理变速声称模型控制；采样参数保留高级区，提供稳定默认。
- 完整段落与逐句合成两种测试模式；默认保持完整段落，避免分句拼接影响自然度的比较。支持单次冷启动、同条件暖运行及有限次数连续测试；冷启动不能用缓存冒充。
- 缓存键含文本、模型/引擎版本、音色和全部生成参数；可清理测试App自己的缓存、导出WAV和JSON报告（SAF），不访问正式App目录。
- 指标：模型加载耗时、合成+解码耗时、音频真实时长、RTF=合成秒数/音频秒数（越小越快）、峰值采样PSS/采样间隔和局限、Android热状态、电池温度（不得称CPU温度）、音频采样率、线程、引擎revision和模型hash。批量接口若无首PCM回调则首音频延迟标不可测，不能杜撰流式指标。
- 不把网页试听或桌面GPU数据当手机证据；断网合成、漏读/重复、自然度、热降频由用户实机测试。

## 验收与交接

- 阶段1：以上原生/模型/许可门槛；先报告，等待。
- 阶段2：授权后实现并验证 Library、单元测试、Android测试源码编译、AAR；无手机则明确仅编译。
- 覆盖配置持久化/校验、样例缺失、文件hash和损坏、音频长度/RTF、取消、缓存隔离、重复生成/生命周期、导出；报告模拟测试与实际模型验证各自边界。
- APK永远由总控从干净提交构建，单独归档，不创建正式App标签、不push GitHub。
- 遇到1.7B不支持、原生崩溃、取消不可行、许可证不清、模型校验失败或权限不足时立即报告具体证据和最小需要，不擅自扩大范围。
