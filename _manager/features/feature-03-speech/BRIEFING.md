# 2026-10-03 正式 App 内嵌语音开发与交接

## 用户目标与权限

- 用户明确要求今天完成全部内嵌开发，保留语音生成，支持分段、分句、全文朗读，音色、语速、个性化语音生成要求；已明确确认“接入原英语 App，独立测试 App 也保留”。本任务升级为正式语音功能实施，不再只做性能验证。
- 总控：01a024cb-1fba-7b23-a357-67eb2ad592b4。语音实现仍由既有app-feature任务01a0fd10-c97b-7110-abab-9a56c6f3794b负责；不是新建对话。只向总控汇报，不联系功能1。
- 实现目录暂保留experiments/tts-device-probe，成为两个宿主复用的受管Library；正式Gradle别名由总控设置为speech-runtime。不复制第二份引擎，不为更名搬迁未变化源码。根App/独立宿主/签名/版本/打包仍归总控。
- 可额外只读本简报的绝对路径 D:/Codex_Project/Codex_EnglishApp/_manager/features/feature-03-speech/BRIEFING.md。它优先于旧worktree中“不得正式集成”的阶段性限制；只授权本模块实现和模块测试，不授权读取功能1/2或更多_manager文件。
- 模块目标0.2.0，正式App候选1.1.0/versionCode6，功能1目标0.4.0；不得自行写App版本、Git或APK。

## 已有证据与约束

- 用户在语音任务提供截图并表示“听着不错”。语音任务初步解读一次段落成功：音频34.08秒、RTF4.71、采样峰值约3.98GiB。接收并保留这次听感基线；由语音任务登记准确截图字段及局限，不把一次结果推广为全部验收。
- 补收完整设备报告：用户还明确确认“完整且清晰，没有明显漏读或重复”；单次运行d7a7b157-0733-4d30-8e6b-28e841dbc59d，详细字段及限制唯一正文沿用语音任务SOURCE_MANIFEST第9节。总控未收到原始JSON/WAV，不要求取得它们才开始本次开发；此报告证明一次手机合成/试听，不证明断网、取消、多次稳定或新集成。
- 不再把新性能实验作为实现的前置门槛；不更换模型/量化、不牺牲已认可音质来追求今天完成。等待时间需要真实进度、分段队列、保存结果与重复播放管理；不得承诺实时。
- 25项阶段2与3项阶段1B测试、许可/模型来源不重复审核；只测试本次变更及受影响调用链。不能把静态或模拟测试当新实机通过。

## 稳定宿主契约（先按此实现，发现真实问题一次报告）

包保持com.xuesui.englishapp.modelprobe.runtime。保留独立测试宿主ModelProbeFeature()不破坏原入口，新增：

```kotlin
enum class SpeechReadingScope { FULL, PARAGRAPH, SENTENCE }
data class SpeechReadingRequest(
    val requestId: String,
    val title: String,
    val englishParagraphs: List<String>,
    val scope: SpeechReadingScope,
    val paragraphIndex: Int? = null,
    val sentenceText: String? = null
)
@Composable
fun SpeechFeature(
    request: SpeechReadingRequest? = null,
    onClose: () -> Unit = {}
)
```

- request=null为独立“语音”页，可编辑/粘贴英文，自行选择全文、段落、句子并生成/播放；不能只显示实验诊断面板。
- FULL读取全部英文段落；PARAGRAPH读取选中段落；SENTENCE读取该段中回传的具体英文句子（校验非空并确实属于该段，禁止越界静默全文回退）。requestId一次消费；相同重组不重复生成，不同请求即使同文本也可进入；新请求不能在旧native运行中偷偷并发或覆写旧任务，需可取消切换提示。
- 正式页收到文章请求先显示选择范围/文本/设置及生成按钮，不突然耗费数分钟。支持生成后保存/重播、停止、播放暂停/继续及逐段/逐句选择；全文长文用受限串行分段执行而非一次塞超长上下文。准确保持英文顺序、范围与段落/句子标识，显示完成/当前/待执行；取消后不继续队列，截断不得报完整成功。
- 回到文章由根App负责，只回调onClose；保留学习页面/滚动/默写状态。模块不访问功能1数据库、解析器或导航。
- 宿主实际接线已写在 D:/Codex_Project/Codex_EnglishApp/app/src/main/java/com/xuesui/englishapp/EnglishAppShell.kt，额外授权只读这个单文件以核对生命周期。正式“语音”Tab传request=null，文章覆盖页传有requestId的请求；两者位于同一个Activity，可能复用同类ViewModel。覆盖页关闭后再进null入口不能遗留“返回文章”管理态，也不得因重组/旋转重复生成；不同requestId可靠切换。App只把文章请求保留在Activity ViewModel内，不把整篇文章放入Bundle，进程死亡回到持久化学习页。请把同Activity两入口往返纳入此次新增测试。正式App不注入probe-sample.txt，null入口允许空白编辑/粘贴，不应因缺少实验样例而阻止使用；独立测试App原样例仍保留。

## 音色、速度和个性化

- 保留已支持Ryan/Aiden，真实其他命名音色仅在引擎可证实支持后加入，不做假开关。音色及生成参数持久化，并参与缓存身份。
- 个性化要求提供中文说明和可编辑自然语言指令（例如清晰、温和、适合听写、慢速），真实传入模型，保留原始要求和effective快照。选择要求不能改写原始朗读正文。
- 明确提供播放倍速0.5–2.0（平台支持范围以实际能力验证），音调尽量保持；播放倍速不冒称模型合成参数，不改变原始WAV。另提供生成时快慢风格要求（通过真实instruction能力）；不得用它承诺准确数值语速。若精确生成速度没有原生参数，应UI明确区分，不伪造native speed。
- 保留完整高级配置，但正式页面先突出音色/速度/个性化。保存与恢复默认覆盖全部设置；不同模型适配器不共享错误参数。原测试页仍可用于冷/暖/线程实验。

## 长期与数据要求

- 继续公共模型/任务/存储层与引擎适配器隔离；不把正式文章对象塞进通用引擎核心。生成历史继续播放/导出且不能被删模型连带删除；生成与播放状态明确，音频与报告一致绑定。
- App root现有minSdk29必须保持；Library目前minSdk31。请做受影响API/native可用性评估并保持API29可编译/安装兼容（必要时低版本语音入口明确不支持且不loadLibrary）；目标Android16完整可用。不得要求总控直接提高正式App最低版本或tools:overrideLibrary绕过问题。准确回报最小兼容方案与测试。
- 模型仍同一固定1.7B Q4配对，正式assets由总控准备；独立测试App数据与正式App各自私有，不越权迁移或读对方目录。无联网语音、录音/克隆或新模型下载。

## 验证和交接

- 覆盖三种范围提取/顺序/边界、长文队列、取消不再启动下一段、截断/失败不报全成功、重复requestId与连续新请求、保存缓存身份、音色/指令真实接线、速度语义与持久化、生成音频重播及暂停继续、退出返回、原测试入口兼容。
- 模块测试、Android测试源码编译和Release AAR；无设备的新代码不宣称实机通过。若需改native，补对应最小参数/取消测试，不重复审计整个引擎。
- 完成后一次完整报告：代码路径、契约是否完全一致、数据库/存储迁移、minSdk方案、宿主依赖/权限、测试及来源、AAR、剩余设备项，发总控01a024cb-1fba-7b23-a357-67eb2ad592b4；等待总控集成，不自行Git/APK。阻塞或契约不适用即时汇报；消息失败写MANAGER_OUTBOX.md。
