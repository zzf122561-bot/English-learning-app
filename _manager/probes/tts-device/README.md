# 英语语音手机验证总控入口

2026-10-02用户授权独立测试App；设备为天玑9400/16GB/512GB/Android16。开发任务使用 GPT-6-astra / xhigh（用户要求高配置）。

这是独立Gradle工程，未 include 到正式根 settings.gradle.kts。此处是总控独占的 Application 壳，唯一依赖是 experiments/tts-device-probe Android Library。既有EnglishApp仍保持1 Application/2 Library，不因测试包改变版本或发布状态。

- 开发简报：[MANAGER_LINK.md](../../../experiments/tts-device-probe/MANAGER_LINK.md)。
- 包名：com.xuesui.englishapp.modelprobe。名称：本地模型验证。测试版0.1.0-probe，versionCode1；不是正式App 1.0.0的更新。
- 用户追加要求：适宜后续嵌入其他大模型。公共模型管理/调度/测量层、引擎适配器和任务页面分离，参数按能力和模型版本管理；不做一次性写死于Qwen的壳。本轮仅语音验证，不扩展其他任务。
- 初始阶段仅初始化脚手架，尚无原生运行或APK证据。首轮门槛通过后才开发完整设置与测试界面。
- 首选1.7B CustomVoice Q4，CPU/ARM64；不承诺NPU/GPU或实时生成。
- 原文来自用户唯一授权的 short-story-generator/（A0001-A1412）20260822.docx 首个英文段落；仅去除词汇编号，68词5句。样例是Git忽略的 app/src/main/assets/probe-sample.txt，不读取生成器其他内容。
- 模型/样例/音频/报告原始数据只保留本地，不提交或上传；许可证、模型revision/hash等来源元信息可提交。模型由总控在通过兼容门槛后获取与打包，首选内嵌，不擅自改为联网服务或下载必需。
- APK仅在实现交接并由总控完成新增集成验证后，从干净本地提交构建。签名使用独立测试身份，不读取正式App密钥；归档到_manager/artifacts/tts-probe，记录APK和模型哈希、来源提交及测试边界。
- 本轮不push GitHub；不覆盖正式App，不安装手机App或读取手机资料。
- 初始SDK检查缺NDK/CMake，等原生任务确定固定版本后由总控安装。用户安装APK后执行断网、冷/暖生成、连续生成、播放、取消、导出及听感验收。
