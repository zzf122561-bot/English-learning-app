# saber 1.3.0 已批准实施契约

批准日期：2026-10-14。状态：implementation_dispatched（模块交付、集成和设备验收尚未完成）。本文件是本轮唯一产品/跨模块契约正文，各任务简报引用而不重复复制。

## 范围、版本与证据

- 用户已明确批准本计划并补充直接播放入口；当前反馈为“基本上语音功能都是正常的”，仅登记为概括性用户反馈，不将旧失败记录改为通过，也不替代本轮后台/恢复验收。
- App 目标1.3.0/code8；功能1目标0.6.0，语音目标0.4.0；词典0.2.1不改。根基线834bfac；功能1工作区基线1453bdb，语音基线3023608，对应模块源已集成。
- 保留现有Qwen1.7B Q4、原生修复、包名/签名、阅读数据和独立测试App；不重新审查解析/原生旧实现，不以本次变更承诺推理提速，不推送GitHub。
- 总控：01a024cb-1fba-7b23-a357-67eb2ad592b4。所有任务只向总控回复，不互读实现或互相派工。收到持久简报即实施，无须纯确认；完成一次交接后停止等待。阻塞、数据/接口例外及时一次报告，消息失败写本模块MANAGER_OUTBOX.md。

## 阅读页与播放语义

1. 每段显示/隐藏本段中文旁注入“播放本段”；中文显隐不影响入口。单段播放结束即停，播放/暂停态分别显示“暂停本段”/“继续本段”；点击另一段切换，不叠加播放器。窄屏、大字体操作行可换行。
2. 顶部选全文替换为“播放全文”（与Aa同区域），全文播放/暂停态为“暂停全文”/“继续全文”。通过自然段列表从第1段顺序播放；“生成全文”放同区域更多菜单，明确独立于播放。保留原单句选择/范围生成。底部槽继续最大180dp，详细任务/错误/管理用展开对话框，保留滚动位置。
3. 本段缺音频：确认“生成并播放/取消”；全文部分缺失：显示已有/缺失段数，确认“补齐并播放/仅播放已有内容/取消”。全部已有立即播放。仅播放已有内容明确跳过缺段并显示实际段号；补齐并播放不得跳读，遇未完成段等待。用户暂停或更换播放目标后，迟到完成事件不得启动旧播放意图。
4. 点击播放不能静默开始推理；单独生成全文不自动出声。重新打开自动恢复生成不恢复出声。一个全局播放器，播放与生成分别暂停/取消。底部显示当前段、进度、倍速、生成进度，可展开任务暂停/继续/取消。
5. 语音Tab继续仅模型/设置，不新增独立播放页面；原音频删除、导出、单句、译文/默写/字号/查词和位置保护保持。

## 精确公共接口（两任务不得自行改名）

功能1 `com.xuesui.englishapp.feature.WordMemoryFeature` 在既有参数末尾追加可空槽：

```kotlin
playbackControl: (@Composable (
    documentId: String,
    title: String,
    englishParagraphs: List<String>,
    paragraphIndex: Int?,
) -> Unit)? = null
```

- 顶部以null索引注入，段落译文按钮旁以零起始索引注入；其余参数沿用现有稳定身份和去编号英文，正文不进Bundle。功能1仅放置槽，不实现播放/后台状态，不引入语音类型。槽为空保留旧入口行为，现有readingContent/onReadingRequested兼容。
- “生成全文”菜单将旧选区设为全文并显示现有底部生成操作，不悄悄播放。独立播放按钮无需改变底部生成范围即可播放对应范围。
- 语音在现有公开包 `com.xuesui.englishapp.modelprobe.runtime` 新增：

```kotlin
@Composable
fun SpeechReadingPlayButton(
    documentId: String,
    request: SpeechReadingRequest,
    modifier: Modifier = Modifier,
)
@Composable
fun SpeechRuntimeHost()
```

- PlayButton负责TextButton标签、订阅全局播放状态及缺失确认，request只允许FULL/PARAGRAPH；沿用现有SpeechReadingRequest，不创建第二份领域DTO。宿主创建请求并注入功能1，无需功能1读取语音代码。
- RuntimeHost由总控在Activity内容根调用一次，负责前台可见时初始化/重连、恢复资格与必要通知权限引导；不得依赖进入阅读页才恢复。无界面常驻覆盖，不在销毁时取消服务，不自动出声。
- 既有SpeechInlineReadingFeature/SpeechSettingsFeature/ModelProbeFeature/SpeechFeature保留；UI ViewModel只观察/发送命令，服务拥有运行期。通知点击先打开现有App（不传文章正文），宿主后续接收稳定documentId并恢复阅读的扩展如确需增加接口先报告，不私自依赖功能1DAO。

## 后台、持久任务与音频

- 独立生成前台服务串行运行整个App任务队列，持有唯一模型实例。切页、切Tab、ON_STOP、Compose dispose不能主动取消；启动来源为用户可见操作/前台恢复，stopWithTask=false。不承诺系统永不杀进程。
- Android35+使用mediaProcessing，29–34使用适用本地文件处理dataSync；不以mediaPlayback假保活。遵循FGS权限/启动限制和onTimeout，及时持久标记中断并stopSelf，不阻塞主线程等待native退出；native资源必须继续由原所有者安全退出，不能UAF或误启第二引擎。适当提前停止预算，完整报告取消延迟边界。
- 生成任务按需有界partial wake lock，暂停/完成/错误必释放，不再保持屏幕常亮。通知显示文章标题、完成自然段数、当前段，暂停/继续/取消；服务默认不导出，仅明确所需MediaSession入口最小权限。
- 播放使用Media3 MediaSessionService/ExoPlayer，mediaPlayback权限，单独通知/音频焦点/noisy耳机拔出暂停；锁屏、切后台继续。播放暂停不影响生成，生成暂停不影响有效音频播放。
- 不用长时WorkManager包住整篇推理（Android16 JobScheduler配额），不注册开机自动生成以绕过启动限制。系统/进程死后重新打开App自动续跑；主动paused/cancelled不续跑；强制停止时不能继续运行。通知权限拒绝应明确解释可见性，不导致崩溃或谎报后台成功。
- 模块私有持久SQLite任务库（Android SQLiteOpenHelper，初始schema1；避免为任务账本新增Room/KSP构建体系），包含批次/自然段子任务、stable documentId、内容/配置/引擎摘要、顺序、状态/原因、音频引用。只向前显式迁移，不接触其他模块数据库。既有RunStore音频仍为文件存储，建立持久引用与其回收协议，不把内存Lease当崩溃保护。
- 全文一次持久入队，按自然段串行；同请求去重，跨文章FIFO。每段完全成功并校验后原子发布音频及任务状态，立即可播放，全文进度按自然段计数。段内保留已有安全分片/有限重试，不将token_limit/半成品发布成功。
- 恢复跳过完成段，未完成段可复用已校验片段；随机模式入队时固定实际seed和每段/片seed，恢复不能重新抽种子。配置和文本冻结，修改设置只用于新任务。失败段暂停该篇后续、不无限重试，不阻塞其他文章队列；重试须明确用户操作。
- pause/cancel保留已完成段，取消不自动恢复；用户重新生成可按内容/参数/模型一致性复用。内容变化产生新快照，不把旧音频配给新文字。
- 同文同段最新成功替换，失败保留旧版；队列/播放器引用安全，删除要确认并避免复活，未引用旧数据安全回收。全文列表不永久双存整篇WAV，导出按需拼接并清临时产物；原有完整/单句音频兼容只读播放/导出/删除，无法可靠映射不猜拆、不删旧数据。
- 参数帮助保留默认：threads min(4,cores)、temperature0.9、topK50、topP1、repetition1.05、maxAudioTokens1024、seed42；中文解释线程热/性能非单调、随机性、候选筛选、过强重复抑制风险、单次帧预算非全文长度、seed局限。语气指令非精确语速，播放倍速不重生成；折叠高级区、恢复默认、现任务快照/新设置生效范围明确。

## 短文格式契约 story-docx-v1

- 唯一新生成格式：英文标题（core title与标题段一致，标题全加粗）；其后严格英文自然段/【中文译文】自然段配对；非表格/非文本框/非列表。目标为 `A0001. word`：大写A、至少4位十进制数、ASCII句点、一个ASCII空格，整个目标为一个bold run；普通上下文不加粗。App兼容Word相邻bold runs合并，不能取消旧数字编号文档读取。
- 每个选定ID在英中文各一次、对应释义/单词与词库一致，禁止未知、重号、缺号/不匹配。只收单个英语词（允许词内撇号/连字符），忽略材料旧分类/编号/音标，case-insensitive复用原ID；A9999后A10000，不重编号。
- 模型交结构化故事JSON、以⟦⟦ID|text⟧⟧标记，由唯一确定性builder构建并校验。文件名 `（START_ID-END_ID）YYYYMMDD.docx`；同名事件用独立输出目录，避免覆盖。App按target span去编号，保留2026、3.14等正常数字；不让TTS自行猜。
- 生成器契约自测与功能1实际parser/cleaner测试共同遵守本节；生成器交付一个使用临时词库生成的合成DOCX及预期JSON的精确路径供总控转交，不授权功能1扫描Skill目录。真实词库/文章不作为Git测试fixture。

## Skill迁移与共享记录（仅生成器任务）

- 用户明确授权本轮跨对话协调与迁移，类型仍skill、不进入App或Git；不新增项目/线程、不联系其他管理员或功能任务。
- 沿用原 `short-story-generator/skills/english-vocabulary-story`，最终唯一实现 `D:/CodexData/skills/english-vocabulary-story`；唯一活动数据库 `D:/CodexData/skills-data/english-vocabulary-story/vocabulary.sqlite3`；链接入口 `C:/Users/zzf86/.codex/skills/english-vocabulary-story`。正常自动发现与显式调用均支持，不静默安装到其他工具配置。
- 原目录作为旧资料/生成输出/迁移恢复入口保留。先SQLite一致备份、源清单和内容核对，迁移完成验证后将旧库归档为非活动备份，旧脚本改薄转发、旧AGENTS/HANDOFF引用规范，不能留下两个可写源。不得覆盖已有不明目标；外部路径被平台拒绝停止并报告，不绕过。
- 固定库缺失/不可访问/不兼容停止，不自动空库；新ID事务BEGIN IMMEDIATE和唯一约束保护；归一化去重、原词/释义/来源/编号/历史关联均保留。数据迁移只显式向前，有可恢复备份；不得通过重建/清空丢历史。
- 成品先结构校验再幂等登记生成事件、词条使用、契约版本/摘要/路径；保留旧filename历史关系，不以DELETE同名文档覆盖历史。DB与文件两阶段操作需可恢复、失败不宣称交付；测试用隔离库，不能将测试故事写进真实词库。
- 使用skill-creator，不重初始化已有Skill；agents/openai.yaml按其reference维护。移除已失效专属运行环境要求时保留等价可运行入口/依赖声明，不要求其他模型知道本对话历史。用户原定不做视觉渲染仍保留，必须结构验证。
- 交付迁移前后汇总/一致性、备份与唯一库位置、并发/缺库/幂等/失败恢复测试、合成DOCX/JSON路径、Skill验证及其他模型实际调用边界。不将任何个人词库、故事或外部Skill实现写入本Git。

## 验收与总控后续

- 模块新增测试覆盖直接入口/状态/缺音频确认、后台生命周期、44段中断保留43段、恢复与用户暂停区别、参数冻结、单实例/去重、存储失败与引用回收、设置说明/持久化。Android测试实际执行与仅编译区分；无设备不得声称锁屏/FGS/声音验收。
- 功能1parser/cleaner使用合成fixture测试格式、错误编号/加粗/译文、正常数字及跨段顺序；不扩大旧解析重审。总控在两个交接后补真实生成器fixture导入链。
- 语音报告准确新增依赖版本/许可、Manifest service全名/权限/启动API/通知intent、数据库schema、公开入口、独立宿主兼容及风险，供总控接线。只构建AAR/编译测试，不APK/Git。
- 总控接受已有模块证据，只补新增宿主接线/依赖/架构边界；干净提交签名归档EnglishApp-v1.3.0-test.001-<commit>.apk。新FGS/MediaSession组件属于本轮明确新增许可，不把它们误判为模块Application/launcher。
- 用户手机先少量段落验证，再长文：直接按钮、生成/播放分离、切词典/设置/后台/锁屏、系统终止再开App、暂停不续跑、缺段等待、全文WAV、旧数据/字号/查词保持。不能以模拟43段测试冒充118段真实推理通过。保留旧包/失败证据，无新设备验收不正式提升。
- 官方依据：https://developer.android.com/develop/background-work/services/fgs/service-types 、https://developer.android.com/develop/background-work/services/fgs/timeout 、https://developer.android.com/media/media3/session/background-playback 。实现时只查相关官方兼容资料，不重做模型调研。
