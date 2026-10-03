# 当前状态

- 总控集成补充（2026-10-03）：0.2.0已提交0f18a3b、合并47286c4并用于App1.1.0 test.001；正式APK签名/资产验证及两个宿主连接编译通过。模块任务保持等待；本次新增设备流程待用户自测。以下交接时状态为历史来源，不再表示等待构建。

- 总控历史记录：阶段2已提交9f9cb88并集成；独立`ModelProbe-v0.1.0-test.001-f098fda.apk`完成宿主构建、签名、资产哈希与原生打包对齐检查并永久保留。打包当时尚无手机证据，随后收到下述第9节的单次用户反馈；不将该反馈扩大为当前新流程验收。
- 2026-10-03：模块0.2.0为 `ready_for_integration / awaiting_manager_integration`。用户已批准正式内嵌并保留独立测试App；本模块实现及增量编译/测试完成，正式App接线、Git、APK归总控。
- 保留ModelProbeFeature，新增总控约定的SpeechFeature/SpeechReadingRequest/SpeechReadingScope精确公共契约。正式页支持全文/段落/句子串行队列、个性化要求、保存/缓存、历史、暂停/继续、倍速及导出；同Activity独立页与文章入口分别保留草稿，正式页不依赖probe-sample.txt。
- 31个不同JVM用例通过（21新增、10受影响已有）；Android测试源码编译、API29原生及Release AAR构建、ELF/notice检查通过；Lint 0错误、6项已说明警告。完整命令、两批测试覆盖、最终AAR/hash和兼容边界统一见 [SOURCE_MANIFEST.md 第10节](SOURCE_MANIFEST.md#10-正式语音020内嵌候选)。
- Library/native实际目标API29，无宿主最低版本提升、无overrideLibrary。原模型、源码/补丁及音质策略保留；新流程没有手机运行或视觉验收证据，不标记compatibility_gate_passed或App发布完成。
- 用户此前一次设备合成及“完整且清晰，没有明显漏读或重复”的试听反馈见 [SOURCE_MANIFEST.md 第9节](SOURCE_MANIFEST.md#9-2026-10-03-设备截图与用户试听反馈)。该次34.08秒音频/RTF4.71不推广为0.2.0新队列、取消、倍速、API29或全部场景通过；原始JSON/WAV不作为本轮实现前置。
- 当前架构、存储/缓存、队列和生命周期语义见 [ARCHITECTURE.md](ARCHITECTURE.md)，授权及总控回复对象见 [MANAGER_LINK.md](MANAGER_LINK.md)。历史阶段证据直接沿用，仅为本次差异补验证。
- 阶段1/1B/2报告与设备反馈均已成功交接；0.2.0最终完整FEATURE_MILESTONE于2026-10-03成功发送至总控01a024cb-1fba-7b23-a357-67eb2ad592b4（消息工具isError=false）。现等待总控集成或新范围，不轮询或自行扩展。
