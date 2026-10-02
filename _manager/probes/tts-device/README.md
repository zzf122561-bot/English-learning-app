# 英语语音手机验证总控入口

2026-10-02用户授权独立测试App；设备为天玑9400/16GB/512GB/Android16。开发任务使用 GPT-6-astra / xhigh（用户要求高配置）。

这是独立Gradle工程，未 include 到正式根 settings.gradle.kts。此处是总控独占的 Application 壳，唯一依赖是 experiments/tts-device-probe Android Library。既有EnglishApp仍保持1 Application/2 Library，不因测试包改变版本或发布状态。

- 开发简报：[MANAGER_LINK.md](../../../experiments/tts-device-probe/MANAGER_LINK.md)。
- 包名：com.xuesui.englishapp.modelprobe。名称：本地模型验证。测试版0.1.0-probe，versionCode1；不是正式App 1.0.0的更新。
- 用户追加要求：适宜后续嵌入其他大模型。公共模型管理/调度/测量层、引擎适配器和任务页面分离，参数按能力和模型版本管理；不做一次性写死于Qwen的壳。本轮仅语音验证，不扩展其他任务。
- 初始阶段仅初始化脚手架，尚无原生运行或APK证据。首轮门槛通过后才开发完整设置与测试界面。
- 2026-10-02脚手架验证：现有Gradle/JDK/SDK成功执行 :runtime:assembleDebug，23任务执行；仅占位Library编译，不代表模型/原生引擎或手机通过。初次沙箱Gradle无法加载native-platform.dll，经平台批准使用现有工具链编译成功。
- 新任务ID `01a0fd10-c97b-7110-abab-9a56c6f3794b`；工作树5551从初始化提交951766d建立，现为总控创建的 codex/model-probe 分支。登记入口以THREAD_REGISTRY为准。
- 首选1.7B CustomVoice Q4，CPU/ARM64；不承诺NPU/GPU或实时生成。
- 原文来自用户唯一授权的 short-story-generator/（A0001-A1412）20260822.docx 首个英文段落；仅去除词汇编号，68词5句。样例是Git忽略的 app/src/main/assets/probe-sample.txt，不读取生成器其他内容。
- 模型/样例/音频/报告原始数据只保留本地，不提交或上传；许可证、模型revision/hash等来源元信息可提交。模型由总控在通过兼容门槛后获取与打包，首选内嵌，不擅自改为联网服务或下载必需。
- APK仅在实现交接并由总控完成新增集成验证后，从干净本地提交构建。签名使用独立测试身份，不读取正式App密钥；归档到_manager/artifacts/tts-probe，记录APK和模型哈希、来源提交及测试边界。
- 本轮不push GitHub；不覆盖正式App，不安装手机App或读取手机资料。
- 初始SDK检查缺NDK/CMake，等原生任务确定固定版本后由总控安装。用户安装APK后执行断网、冷/暖生成、连续生成、播放、取消、导出及听感验收。

## 当前推进 2026-10-02

- 阶段1静态交付已接收并保存于功能分支`69bc5df`。SOURCE_MANIFEST.md为源码/模型身份、静态格式及API缺口的唯一正文；ARCHITECTURE.md为扩展设计正文。沿用代理证据，不重复静态检查。
- 门槛未通过：缺真实native编译/完整模型加载合成及安全取消证据。已续派MANAGER_LINK末节阶段1B；完整UI和APK仍未开始。
- 固定工具：NDK28.2.13676358 / CMake3.22.1。新sdkmanager兼容层拆分分号包名且错误返回0，改用已安装android.exe原生sdk install和斜杠包路径；首次下载I/O中断，原范围重试中，不更新CLI或其他SDK、不改DNS/代理。安装完成必须检查两份source.properties而非仅看退出码。
- 模型只取GGUF固定revision b7ee2e8c7459c3bea99da23e3d178125a7d1713c 的1.7B CustomVoice Q4与Q4 tokenizer；下载工具[fetch-models.ps1](fetch-models.ps1)严格检查尺寸及SHA256，支持断点，未校验文件保留.partial且不能启用。初次传输在约464MB处连接重置，续传中。
- 所有本轮提交仅在本地；正式EnglishApp仍未集成试验代码。技术静态检查不能提高用户设备验收级别。

### 阶段1B接收与模型就绪

- 总控模型下载任务已退出0，两个文件完整尺寸/SHA256匹配SOURCE_MANIFEST，MODEL_PAIR_VERIFIED。NDK/CMake安装进程虽然最终非零退出，但两份source.properties与功能任务的实际native编译证明指定版本已可用；不重复安装。
- 接收阶段1B：3项Kotlin测试通过、Android测试与ARM native契约入口仅编译、Release AAR 1,394,913字节/SHA256 47e4a65fc275092c4cd0a5d0a78c23f0277e2391f210d9ffe1122c4d3dd28d96、JNI与16KiB ELF/notice检查通过。唯一详细证据在功能分支SOURCE_MANIFEST第7节，不复跑相同检查。
- 仍无native/JNI设备执行和真实模型运行；为避免无设备导致候选页前置死锁，已授权阶段2设备验证页面与公共基础，不提高compatibility验收级别。最终APK由总控打包，随后用户手机验证。
- 阶段1B由总控提交到功能分支563ae58；上游vendor尾随空白保持原样，不因diff --check格式警告重写第三方代码。项目自有文件差异检查通过。模型已完整就绪通知与阶段2派工已成功发给功能任务。
