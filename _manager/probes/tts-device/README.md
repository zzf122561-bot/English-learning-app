# 英语语音手机验证总控入口

## 当前候选：0.5.0-probe / code2（2026-11-07，准备构建）

配合saber1.4.0，复用语音0.5.0和总控`_manager/ui`主题。独立包名与既有签名保持，新增手动确认的4/6/8线程冷暖测速，最多400码点的同文同设置串行比较；失败停止后续，结果可导出JSON，不能以构建成功宣称性能提高。使用1–2句短英文先验证完整听感，再跑对照；默认68词样例若超过400码点应手动选择完整短句，不自动截短。

本次构建/签名/资产/服务验收结果由总控发布记录保存。不会覆盖旧0.1.0 APK，不修改正式App数据；新版独立包可覆盖安装旧“本地模型验证”。主工程现在为1 Application/3 Library，此独立Application仍不加入正式settings。共享Library需要的生成/播放前台服务、通知/唤醒与Media3网络状态权限随包声明，仍无INTERNET/录音/外部存储权限。线程基准由用户主动启动，不自动后台测试。

以下为2026-10-02至10-03的历史初始化和首次交付记录，原测试/设备边界保持，不代表当前待办。

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

### 阶段2接收与独立APK打包（2026-10-03）

- 阶段2由总控提交为功能分支9f9cb88，并通过3dc3925合并；唯一冲突是STATUS的新旧状态，采用当前交付状态，旧脚手架证据保留在本文件和Git历史。未修改正式App或功能1/2。
- 沿用SOURCE_MANIFEST第8节的25项新增JVM、Android源码编译、Release AAR及Lint证据，不重复运行。仍没有native执行、模型加载合成、手机交互或听感通过证据。
- [build-apk.ps1](build-apk.ps1)从干净提交构建独立Release，使用本机独立PKCS12测试身份；口令使用Windows DPAPI保存在忽略目录，不读取正式App密钥。打包固定manifest、两份未压缩GGUF、私有样例和许可证；验证最终APK内全量资产哈希、两个ARM64库、ELF/ZIP 16KiB对齐、包名/版本/权限及v2签名。
- 模型清单正文为[model-manifest.json](model-manifest.json)。模型/样例的实际字节、APK和本机签名继续Git忽略；APK输出到`_manager/artifacts/tts-probe`，同名禁止覆盖。打包收据记录来源提交、模型身份、APK校验值和设备未测边界。
- 应用采用公共模型管理、排他调度、测量与结果存储层，加独立引擎适配器及任务页面；未来新增模型仍需真实适配器/资产契约及验证，不能把当前Qwen设置伪装成通用能力。
- 首次宿主Release编译通过（86任务，2m57s）。包装检查发现默认v3验证优先导致v2收据条件不匹配，现显式使用v2独立测试签名；权限检查仅允许AndroidX生成的本包signature级内部receiver权限，不允许联网/录音/存储权限。此次只修总控打包脚本，不复跑模块测试。
- APK级验证范围补充：Compose传递依赖`androidx.graphics:graphics-path:1.0.1`带入`libandroidx.graphics.path.so`，因此最终APK为三份arm64库（不是Library自身的两份）。包装检查精确限制三份名称，逐一记录哈希与ELF/ZIP对齐；此前模型、样例及notice的最终APK流式全量校验已通过，无需改动模块实现。

### 当前交付：test.001，等待用户设备测试

- APK：`_manager/artifacts/tts-probe/ModelProbe-v0.1.0-test.001-f098fda.apk`，1,464,060,879 bytes（约1.46GB / 1.36GiB）。来源干净提交`f098fdac878504f33719b9160f4686ca16fc5f88`。
- 唯一完整打包收据：[test.001 JSON](../../releases/tts-probe/ModelProbe-v0.1.0-test.001-f098fda.json)，含APK/全部模型与三份原生库哈希、独立证书指纹、v2签名、包元数据、ELF LOAD及ZIP16KiB对齐验证。Compose图形库与本机官方依赖AAR内arm64库哈希一致；Qwen库在总控根目录重新编译，最终字节身份以本收据为准，不冒称与功能worktree AAR相同。
- 模块回归继承第8节；本次新验证是宿主Release构建、模型/样例/notice完整嵌入、包边界/签名/原生对齐。独立工程边界通过，正式App、功能1/2及根版本目录自集成前没有差异。没有设备/模拟器运行、实机JNI/模型合成、UI或听感证据。
- 所有源码与发布元数据保存在本地Git；APK、模型、用户样例和密钥仍忽略；未推送GitHub、未创建正式标签。

#### 手机首轮步骤

1. 把上述APK传到手机安装，图标名“本地模型验证”；独立包名，不需卸载英语App。建议预留至少5GB空闲空间供APK、私有模型副本和临时结果（保守建议，不是实测峰值）。
2. 打开“操作”，先点“JNI无模型自检”，记录成功或错误；这只测试桥接基础，不代表语音合成通过。
3. 点“安装内置模型”，等待约1.44GB复制与完整校验。过程不下载；失败保留错误截图，不要反复点击。可断网验证本地安装和后续生成。
4. 先保留默认Ryan、4线程上限及单次运行；建议把输入临时改为固定样例第一句，点“冷启动生成”。完成后到“结果”播放，确认不是空音频/噪声。保持App在前台；切后台会请求取消。
5. 点击“恢复固定样例”测试68词全文段落；再对比Aiden、逐句、暖运行。先不要启用5次批量；首轮速度未知，不承诺实时。
6. 每次结果可导出JSON及WAV。回传错误截图或报告，以及音频是否自然、有无漏读/重复/截断；报告不含正文，音频会包含朗读样例。若明显过热，使用“停止当前任务”并等待实际退出。

完整设备验收表仍以SOURCE_MANIFEST第8节为准。用户确认运行结果后才决定是否接入正式听写功能，不自动提高验收级别。
