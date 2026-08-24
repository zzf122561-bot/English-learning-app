# 里程碑 2 增量实现与验证审计

更新时间：2026-08-24

状态：`milestone_passed / awaiting_manager_incremental_review`。本任务已停在里程碑 2 门槛，未进入里程碑 3，也不是 `ready_for_integration`。

## 审查锚点与四项定向加固

总控已批准并建立里程碑 1B 解析内核审查锚点。本轮没有全面重写、搬迁或格式化解析内核，只修改以下已识别差异：

1. `MdictV2File` 查询键使用 `lowercase(Locale.ROOT)`；词条与资源重定向规范化也使用 `Locale.ROOT`。
2. MDD 资源重定向使用 `RedirectGuard`，拒绝大小写/斜杠规范化后的循环，并限制最大深度 16。
3. 两套真实词典分别要求图片资源：Collins 验证 PNG 8-byte 文件头，Oxford 验证 JPEG `FF D8 FF` 文件头。
4. `MILESTONE_1_AUDIT.md` 已明确标记为废止的 mdict-java/GPL 历史失败路线，并指向已批准的 `MILESTONE_1B_AUDIT.md`。

里程碑 1B 的既有 5 项解析回归持续保留并通过。

## 第二次增量审查修复

本轮只修改里程碑 2 审查锚点后的新增实现及受影响链路，没有重复审查或改写里程碑 1B 解析内核：

1. `DictionaryViewModel` 将内置安装、SAF 扫描/复制/哈希/探测、MDX 打开/查询、MDD 发音读取及临时音频写入调度到可注入的 `CoroutineDispatcher`（生产默认 `Dispatchers.IO`）；UI state 只在 `viewModelScope` Main 上更新。查询和发音保留单 Job，取消显式传播；安装、查询、导入及发音链不再以 `runCatching` 吞掉 `CancellationException`。
2. `DictionaryQueryCoordinator` 使用引擎锁串行化 map/open/query/read/close，使用独立快照锁处理标签选择，避免主线程选择标签等待长查询，同时保证 WebView 资源读取与查询/关闭不并发访问同一引擎。
3. Room DAO 删除 `REPLACE`，改为 `INSERT ABORT` 与同 ID `UPDATE ABORT`；MDX 哈希或显示顺序冲突会回滚并报错，不会删除、覆盖无关词典；资源删除与新资源插入仍处于同一事务。
4. `DictionaryHtmlRewriter` 从真实词条解析 `entry://`、`bword://`、安全相对链接、MDD 资源及 sound/audio 引用，严格改写到 `https://dictionary.local`；内部点击只替换当前查询快照，不增加返回栈。
5. 两本授权词典的真实词条测试均识别到 `entry://`，改写后的目标均可精确命中。两本 MDD 的全部资源键音频扩展计数均为 0，因此明确记录 MDD 缺失并由真实引用驱动的状态机回退到 TTS，没有伪称 MDD 命中。
6. HTTPS 未知长度流在写入超过 8 MiB 的首个分块时立即中止并删除临时文件；MDD `AudioPayload` 在创建临时文件前执行同一上限。发音状态机用 Mutex 串行化，ViewModel 新请求取消旧 Job；TTS 完成回调严格匹配 `utteranceId`，取消/关闭会停止并释放当前后端。
7. 导入拒绝 `.mdd` 与 `.0.mdd`、大小写重复等同一逻辑分卷冲突；导入删除只允许 `importedRoot` 下直接、非隐藏的受控词典子目录，且绝不允许根目录自身。

## 稳定公共入口

唯一 UI 入口位于 `com.xuesui.englishapp.dictionary`：

```kotlin
enum class DictionaryPresentation { FULL, QUICK_LOOKUP }

@Composable
fun DictionaryFeature(
    initialQuery: String? = null,
    presentation: DictionaryPresentation = DictionaryPresentation.FULL,
    onClose: () -> Unit = {}
)
```

- `FULL` 提供搜索、词典索引签、单词条 WebView 和词典管理入口。
- `QUICK_LOOKUP` 直接使用 `initialQuery` 查询；返回按钮与系统返回共用一次性 `CloseRequestGate`，只调用宿主 `onClose`。
- 词条内部链接替换当前查询；模块不维护嵌套页面返回栈。
- 未读取、引用或模拟功能 1 类型。

## Room v1 独立数据库

- 文件：`englishapp_dictionary.db`。
- 版本：1；导出 schema 位于 `schemas/com.xuesui.englishapp.dictionary.data.DictionaryDatabase/1.json`。
- 表 `dictionaries` 保存稳定 ID、显示名、内置/导入类型、私有 MDX 路径与 SHA-256、启用状态、唯一稳定顺序、格式版本、运行状态及创建/更新时间。
- 表 `dictionary_resources` 独立保存 MDD 分卷私有路径、SHA-256 和分卷顺序，以外键关联词典元数据。
- 没有 destructive migration 或删库重建配置。
- DAO 删除 SQL 只允许 `sourceType='IMPORTED'`；Repository 在领域层再次拒绝内置词典删除，并验证待删除目录是 App 私有 `dictionaries/imported` 根下直接、非隐藏的受控子目录且不等于根本身。
- 词典插入使用 `ABORT`；同 ID 更新使用 `UPDATE ABORT`。哈希/顺序唯一冲突会使整个事务失败，既有词典和资源保持原样。
- 排序事务先迁移到不相交负数区间，再写回连续顺序，避免唯一索引碰撞；启停和顺序均持久化。

## 内置清单安装

- 固定读取 `assets/dictionaries/manifest.json`，仅接受 `schemaVersion=1`。
- 缺少清单或词典数组为空时返回空安装结果，页面正常显示“暂无内置词典”。
- 资产路径拒绝绝对路径、反斜杠、空段、`.` 和 `..`；SHA-256 必须为 64 位十六进制。
- 文件流式复制到 `filesDir/dictionaries/installing/<operation-id>/`，逐文件校验 SHA-256，并以目录重命名原子提升到 `builtin/<id>/`。
- 已安装文件与清单哈希一致时跳过复制；哈希变化时保留启用、排序和创建时间，并进行带备份的受控替换。
- 格式探测或数据库事务失败时恢复旧目录、删除暂存，不留下半成品记录。
- 本模块没有复制用户词典或加入实际内置资产；真实资产仍由总控整合阶段提供。

## SAF 文件夹导入

- UI 使用平台 `OpenDocumentTree`，仅获取持久只读授权；不请求写权限。
- `DocumentsContract` 扫描所选目录，不使用其作为运行时数据源。
- 以 MDX 基础名匹配 `.mdd`、`.1.mdd`、`.2.mdd` 等大小写不敏感分卷；同一逻辑分卷重复（包括 `.mdd` 与 `.0.mdd`）会拒绝；无 MDD 的 MDX 允许导入并标记资源缺失；孤立 MDD 忽略。
- MDX/MDD 流式复制至 `dictionaries/importing/<operation-id>/` 并计算 SHA-256；相同 MDX 哈希在复制后、数据库写入前拒绝。
- 使用真实 `MdictEngine` 完成格式探测后原子提升到 `dictionaries/imported/<stable-id>/`，再事务写库。
- 损坏、取消、复制、探测、提升或事务异常都触发完整暂存回滚；代码从不删除或修改 SAF 用户原文件。

## 查询与单结果状态

- 每次查询按 Room 稳定顺序打开所有启用词典；单个词典的打开、解析或资源错误记录在对应标签，不阻断其他词典。
- 精确无结果时合并前缀建议，并用 `Locale.ROOT` 去重；页面标题保留用户原始输入。
- 结果标签顺序与管理顺序一致；Compose 同一时间只创建一个 WebView 并渲染选中词典的完整 HTML。
- 原始词条 HTML 的真实 `entry://`、`bword://` 和安全相对链接先改写为受控 `/lookup?q=`；内部链接调用 `openInternalLink()` 替换当前 snapshot，不生成模块内部历史栈。

## WebView 安全边界

- base URL 固定 `https://dictionary.local/`；只有该 HTTPS 域名、默认/443 端口、无 user-info 的 MDD 资源可被拦截读取。
- JavaScript、JS 开窗、文件访问、内容访问、file URL 跨域、DOM storage、Web database、混合内容和多窗口均关闭；没有 JS Bridge。
- CSP 使用 `default-src 'none'`，只允许受控域的图片、样式、媒体和字体。
- 拒绝 `file://`、`content://`、HTTP、其他域名、查询/fragment 资源、反斜杠、NUL、`.`、`..`、百分号编码穿越、超长路径和超过 8 MiB 的资源。
- 唯一允许的导航语义是受控域 `/lookup?q=...`，它替换当前查询；其他内部/外部导航都阻止并报告 UI。
- WebView 随 Compose 页面停止加载、清空 client、加载空页、移除子视图并 `destroy()`。

## 发音回退

- 用户点击后依次执行：当前词条实际 sound/audio 引用对应的 MDD 资源 → 词条明确提供的严格 HTTPS 音频 → `TextToSpeech` 英语 `Locale.US`；不再按词头猜测 MDD 路径。
- 页面加载不播放、不自动联网；HTTP、file/content、user-info、非 443 自定义端口和 fragment URL 在状态机前被拒绝。
- HTTPS 下载使用 `HttpsURLConnection`、关闭重定向、限制连接/读取超时、要求 HTTP 200 和 `audio/*`；未知长度流写入超过 8 MiB 时立即中止并删除残留。MDD payload 也在临时文件创建前执行上限。
- 多次点击由单 Job 取消旧请求并由状态机 Mutex 串行化；TTS 回调只完成相同 `utteranceId` 的请求，旧回调不能完成新请求。
- 任一网络/解码/播放失败继续回退 TTS；ViewModel 释放时停止并关闭 MediaPlayer 和 TTS。
- 本模块未修改 Manifest、未加入 `INTERNET` 权限。App 壳整合真实 HTTPS 发音时需要总控决定是否添加 `android.permission.INTERNET`。

## UI 实现

- 使用简报固定的墨水蓝、纸张蓝、钴蓝、琥珀、玉绿和分隔灰；词头使用 Serif，正文与控件使用 SansSerif，状态使用 Monospace。
- FULL：固定搜索栏、词头/发音、按顺序的词典索引签、单一 WebView 和管理入口。
- QUICK_LOOKUP：紧凑返回与相同结果区域，无管理或额外返回层。
- 管理页显示顺序、名称、类型、状态、启停、拖动把手及上/下移动；仅导入词典显示确认删除。
- 关键交互最小 48 dp；没有渐变、玻璃效果或装饰性动画。

## 自动验证

强制命令：

```text
powershell.exe -NoProfile -ExecutionPolicy Bypass -File feature-02-dictionary\tools\test-module.ps1 -RerunTasks
```

结果：`BUILD SUCCESSFUL in 1m 13s`，37 actionable tasks 全部重新执行。

- JVM 单元测试：44，失败/错误 0（保留原32项并新增12项）。
- 里程碑 1B 既有解析回归：5，全部保留并通过。
- androidTest 源文件：2；共8项测试（Room 7项、真实 WebView 设置1项）。其中新增哈希冲突、顺序冲突和资源替换事务回滚测试。
- `compileDebugAndroidTestKotlin`：实际执行并成功，不再是 `NO-SOURCE`。
- 当前无 Android 设备，androidTest 只完成源码编译，未在设备运行。
- `assembleDebug`：成功。
- AAR：`feature-02-dictionary-debug.aar`，420,005 bytes，SHA-256 `8AB43658462C528339FB85ABA6F4A20D947150EB546C8812010658B04FFC0894`。
- AAR native/JNI 条目：0；功能目录 APK：0。
- 模块仍使用 Android Library 插件；Manifest 无 Activity、launcher 和权限；无签名、版本或功能 1 依赖。

## 未验证与停止边界

- 没有连接 Android 设备：不得宣称 Room androidTest、WebView androidTest、UI、SAF 选择器、MediaPlayer 或 TTS 已在设备运行。
- 没有根 App 集成、实际内置 assets、整包导航、签名 APK 或用户视觉验收证据。
- 当前只完成里程碑 2，等待总控增量审查；不得自行进入里程碑 3。
