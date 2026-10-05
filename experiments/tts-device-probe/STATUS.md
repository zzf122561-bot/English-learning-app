# 当前状态

- 2026-10-06：模块0.3.0为 `ready_for_integration / awaiting_manager_integration`。按2026-10-05总控修订完成文章内嵌和仅设置入口，源代码、受影响测试与Release AAR已就绪并成功发送完整交接；App接线、Git、签名、图标及APK归总控。
- 新增精确公共契约SpeechInlineReadingFeature(documentId,request,modifier)、SpeechSettingsFeature(modifier)，保留原请求类型及SpeechFeature/ModelProbeFeature。新入口共用Activity运行器/设置；底部控件适配180dp，主要操作在条内，历史/详细错误/删除确认在弹窗，不接管文章Back或滚动。
- 稳定文章/逻辑范围只保留最新成功结果；新WAV/报告完整提交并原子切换后回收旧版。失败/取消/触顶保留旧成功音频，引用/租约保护共享片段及播放/导出/生成；旧无documentId项目可手删但不猜配归属。正文、数据库、模型和已导出文件不在生成结果删除范围。
- 最终JVM批次46项：45通过、1跳过、0失败/错误；跳过原因是Windows缺创建测试符号链接的特权，Android专项仅编译。Android测试源码、Release AAR和Lint通过（0错误/6既有警告），API29/两份原生SO hash均未变。完整命令、结果和当前产物唯一正文见 [SOURCE_MANIFEST.md 第11节](SOURCE_MANIFEST.md#11-2026-10-05-阅读页内嵌030候选)，旧0.2.0证据保留在第10节。
- 当前无新增设备执行/视觉验收：180dp/大字体、文章滚动/Back、设置往返与旋转、真实取消/范围切换、删除/重生成/SAF和实际进程/低空间恢复待总控设备验收。相同段中重复相同句子无法由sentenceText唯一定位时会提示改选段，未扩大公共契约。
- 用户此前一次34.08秒设备合成及“完整且清晰，没有明显漏读或重复”的试听反馈仍仅对应 [SOURCE_MANIFEST.md 第9节](SOURCE_MANIFEST.md#9-2026-10-03-设备截图与用户试听反馈)，不推广为0.3.0设备通过。
- 当前实现规则见 [ARCHITECTURE.md](ARCHITECTURE.md)，授权/回复对象见 [MANAGER_LINK.md](MANAGER_LINK.md)。2026-10-06完整FEATURE_MILESTONE已成功发送至总控01a024cb-1fba-7b23-a357-67eb2ad592b4（消息工具isError=false）；现等待总控集成，不轮询、不自行Git/APK或扩展范围。
