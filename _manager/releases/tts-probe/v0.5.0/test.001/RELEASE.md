# 本地模型验证 0.5.0-probe test.001 — 2026-11-07

状态：`automated_build_and_artifact_checks_passed / awaiting_device_benchmark`。与saber1.4.0共用语音0.5.0及一份总控主题，独立安装与数据空间保留。

## 产物

- [ModelProbe-v0.5.0-test.001-3271e2f.apk](../../../../artifacts/tts-probe/ModelProbe-v0.5.0-test.001-3271e2f.apk)
- 干净来源：`3271e2f80f5a0ee5d7ed9279302a0f01ba039352`。大小：1,471,000,987 bytes（约1.47 GB）。SHA-256：`29c2ec33792db8e68c61670ab428f5762848f71757d6819fc9665e518fc2f8c8`。
- 包名`com.xuesui.englishapp.modelprobe`，显示名“本地模型验证”，versionName`0.5.0-probe`、versionCode`2`、minSdk31、arm64-v8a。可覆盖旧独立测试App，不替换saber。
- v2签名/单签名验证通过，沿用原独立证书：`15caf7130817c1de52e377e558068470dad0cc109495f5f87dd5c9c7b9bc4694`。密钥/密码仍在Git外，未生成替代签名。
- [完整机器清单](ARTIFACT.json)。旧0.1.0 APK/记录永久保留；本候选未设备验收、没有正式提升或GitHub推送。

## 新增诊断与保持内容

- 手动确认“同段4 / 6 / 8线程对照”，冻结同一文本、音色、生成设置和实际随机种子，按4/6/8线程各冷/暖一次串行运行，实际线程受设备能力约束。只允许最多400码点的完整短文本，不自动截断默认样例。
- 不使用已有音频缓存冒充生成耗时。失败/取消停止后续，部分结果可导出JSON；记录加载、prefill、token、decode、WAV、RTF、内存、温度/热状态与实际后端，未知值保持为空。
- 冷表示重新加载模型，非清空Android系统文件缓存；CPU能力报告与实际启用能力分开。当前仍通用armv8-a CPU，没有GPU/NPU/dotprod/i8mm加速启用结论。
- 原Qwen1.7B CustomVoice Q4模型与tokenizer、声音、默认参数及手动试听/导出保留；共享语音模块的临时缓冲复用及设置界面一并接入。性能和听感仍待手机证据。

## 自动证据

- 独立工程`:app:assembleRelease`成功，6m14s，86任务/50执行/36缓存。日志`_manager/local/build-probe-0.5.0-test001.log`，没有重复运行未受影响模块JVM测试。
- 语音39项定向JVM、Android源码编译、AAR/Lint/原生检查收据沿用[SOURCE_MANIFEST第15节](../../../../../experiments/tts-device-probe/SOURCE_MANIFEST.md#15-2026-11-07-语音050展开播放器布局与性能对照)。原生OFF仅语法检查，不是完整OFF构建或设备音频对照。
- 实际APK验证包名/版本/minSdk、原证书/v2/单签名、8项预期权限、两个前台服务的非导出/stopWithTask=false及精确类型；无INTERNET、录音或全盘访问权限。
- 14个模型/已授权样例/notice/SO条目尺寸和完整哈希通过；3个预期arm64库、GGUF未压缩、ZIP/ELF 16KiB对齐通过。样例正文和模型仍不入Git。
- 没有Android设备执行、真实模型生成、听感或4/6/8性能结果。按[ACCEPTANCE.md](ACCEPTANCE.md)手动验证后再判断提速路线。

主App播放器和新布局交付见[saber1.4.0记录](../../../app/v1.4.0/test.001/RELEASE.md)。两个APK均来自同一提交，后续发布文档提交不改变产物来源。
