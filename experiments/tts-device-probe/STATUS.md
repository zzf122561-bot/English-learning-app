# 当前状态

- 2026-10-14：语音0.4.0 / saber1.3.0模块已获总控接收；接收后预算定向修正亦完成，`ready_for_integration / awaiting_manager_integration`。唯一完整验收与交接正文见 [SOURCE_MANIFEST 第14节](SOURCE_MANIFEST.md#14-2026-10-14-语音040后台与自然段持久队列候选)，当前AAR以节末“接收后定向修正”收据为准。总控登记来源为语音3023608之后增量；本任务未执行Git或构建APK。
- 新增生成FGS、自然段SQLite持久队列/恢复、RunStore持久引用、Media3后台播放器、精确SpeechReadingPlayButton/SpeechRuntimeHost。切页不中止；缺音频确认、补齐等缺段/显式仅已有、生成播放独立控制、全文按需导出和中文参数帮助已实现。旧接口、模型/native/默认参数和旧音频保留规则按授权兼容。
- 本次仅将主动预算延长至350分钟、wake lock兜底为预算加1分钟并清理指定行尾空格；2项预算JVM测试通过，增量Kotlin/Release AAR构建通过。先前66项（65通过/1 Windows符号链接条件跳过）、Android测试源码编译与Lint0错误/8提示的已接收证据直接沿用，未重跑；当前测试报告已被本次定向2项覆盖。AAR大小/hash、Manifest、数据库schema、依赖和精确命令只维护于第14节。
- Android SQLite/Media3测试仅编译；无真实设备、FGS/锁屏/时限/音频焦点/通知拒绝或新音质执行证据。旧第9节34.08秒试听与第13节失败/修复证据仍按原范围保留，总控概括性“基本正常”不能替代0.4.0设备验收。
- 需要总控在正式Activity根接RuntimeHost，并将功能1的playbackControl映射到新播放入口；独立ModelProbeFeature内部已调用RuntimeHost。由总控负责集成/Git/根版本/签名/APK，再先少量段落验证，随后长文。当前无依赖或编译阻塞。
- 授权/回复对象见MANAGER_LINK；当前架构见ARCHITECTURE。原完整FEATURE_MILESTONE已成功发送并获总控接收；预算修正收据已完成，待一次发送后等待总控集成/设备反馈，不轮询或自行扩展。
