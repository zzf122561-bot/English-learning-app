# 里程碑 3 总控集成交接

更新时间：2026-08-24

功能 ID：`feature-02-dictionary`

目标版本：`0.1.0`

状态：`ready_for_integration / awaiting_manager_commit`

本交接包只面向总控提交与根 App 集成。功能任务没有执行 Git、根 App 修改、签名、APK 或发布，也没有复制用户词典。

## 1. 唯一公共入口

包名：`com.xuesui.englishapp.dictionary`

```kotlin
enum class DictionaryPresentation {
    FULL,
    QUICK_LOOKUP,
}

@Composable
fun DictionaryFeature(
    initialQuery: String? = null,
    presentation: DictionaryPresentation = DictionaryPresentation.FULL,
    onClose: () -> Unit = {},
)
```

- `FULL`：完整搜索、结果与词典管理入口。
- `QUICK_LOOKUP`：根 App 只传普通 `String` 查询；`onClose` 请求宿主关闭临时页面。
- FULL 与 QUICK 即使由同一 Activity `ViewModelStore` 复用同一 ViewModel，每次进入/变更非空 `initialQuery` 都会提交最新值；安装前到达的查询会在安装完成后提交一次，重组不会重复提交。`FULL(null)` 不清空现有查询。
- QUICK 首帧强制忽略并同步清除共享 ViewModel 遗留的管理态，因此第一次系统返回/页面返回直接经一次性关闭门调用一次 `onClose`。
- `PUBLIC_CONTRACT.md` 与源码签名一致。
- DAO、Room 实体、Repository、ViewModel、解析器、WebView、音频和页面内部类型均为 `internal`/`private`；没有第二个公共入口。

## 2. Room 数据库契约

- 数据库文件：`englishapp_dictionary.db`。
- Room 版本：`1`。
- schema：`schemas/com.xuesui.englishapp.dictionary.data.DictionaryDatabase/1.json`。
- 表：`dictionaries` 与 `dictionary_resources`；元数据和 MDD 分卷资源分表。
- 当前是初始 schema，没有 migration；禁止 destructive migration 或删库重建。
- 唯一冲突使用 `ABORT`；只有同 ID 执行受控更新，冲突不得删除其他词典。
- 本里程碑没有修改数据库版本、实体或 schema。

## 3. 根 App 运行依赖与生命周期

总控后续需要：

1. 在根 App 添加对 `:feature-02-dictionary` Android Library 的依赖；模块自身已声明 Compose、Lifecycle、Room、KSP 与 Coroutines 依赖。
2. 由根 App 持有导航栈并调用唯一公共 Composable；模块不声明 Activity、Service、Provider 或 launcher。
3. `QUICK_LOOKUP` 关闭时由 `onClose` 弹出宿主页面；功能1只能通过普通单词字符串和回调连接，不能传递功能1内部对象。
4. Compose 页面/ViewModel 离开生命周期后会停止查询/发音、关闭 MDict 文件、销毁 WebView 并释放 MediaPlayer/TTS；数据库和已安装私有词典按用户数据持久保留。
5. 模块 Manifest 当前为空。SAF 使用 `OpenDocumentTree`，不需要存储读写权限。
6. 若启用词条严格 HTTPS 发音回退，根 App 必须由总控决定是否声明 `android.permission.INTERNET`；不声明时当前两本内置词典会如实回退 Android 英语 TTS。

## 4. 内置资产与私有目录契约

内置清单固定位置：`assets/dictionaries/manifest.json`，只接受 `schemaVersion: 1`。每本词典项必须包含稳定 `id`、`displayName`、一个 MDX `{path, sha256}` 和 MDD `resources[]`。`path` 相对于 `assets/dictionaries/`；所有 SHA-256 使用总控登记值。

总控需要生成的精确资产清单：

| 稳定 ID | 类型 | 源文件名 | 建议清单相对路径 |
|---|---|---|---|
| `builtin.collins-advanced-bilingual` | MDX | `柯林斯高阶英汉双解学习词典（好看）.mdx` | `collins-advanced-bilingual/柯林斯高阶英汉双解学习词典（好看）.mdx` |
| `builtin.collins-advanced-bilingual` | MDD | `柯林斯高阶英汉双解学习词典（好看）.mdd` | `collins-advanced-bilingual/柯林斯高阶英汉双解学习词典（好看）.mdd` |
| `builtin.oxford-ald9-en-en` | MDX | `Oxford ALD_9th_En-En.mdx` | `oxford-ald9-en-en/Oxford ALD_9th_En-En.mdx` |
| `builtin.oxford-ald9-en-en` | MDD | `Oxford ALD_9th_En-En.mdd` | `oxford-ald9-en-en/Oxford ALD_9th_En-En.mdd` |

功能任务没有把上述只读用户词典复制进模块。文件哈希必须继续使用总控登记值，交接文档不重新登记或猜测哈希。

App 私有目录：

- 安装暂存：`filesDir/dictionaries/installing/<operation-id>/`
- 内置词典：`filesDir/dictionaries/builtin/<stable-id>/`
- 导入暂存：`filesDir/dictionaries/importing/<operation-id>/`
- 用户导入副本：`filesDir/dictionaries/imported/<stable-id>/`
- 音频临时文件：`cacheDir/dictionary-*-audio-*`

安装/导入采用流式复制、SHA-256、格式探测、目录原子提升与失败回滚，不把整本词典载入内存。

## 5. 行为与安全边界

### 导入

- SAF 只读取用户选定文件夹，复制到 App 私有目录后运行；不修改或删除原文件。
- MDX 与大小写不敏感 MDD 分卷配对；拒绝重复逻辑分卷、重复 MDX 哈希和损坏格式。
- 仅导入词典可删除；删除目标必须是 imported 根下直接受控子目录且绝不等于根。

### WebView

- base origin 固定 `https://dictionary.local/`。
- JavaScript、文件/内容访问、混合内容、DOM storage、Web database、多窗口和 JS Bridge 均禁用。
- 真实 `entry://`/`bword://`/安全相对链接改写为受控查询；点击只替换当前词条，不增加模块返回栈。
- MDD 图片/CSS/媒体只经受控 origin 随机读取；路径穿越和超过 8 MiB 的资源被拒绝。

### 音频

- 仅用户点击触发：真实词条 MDD 引用 → 严格 HTTPS → Android 英语 TTS。
- 两本当前授权 MDD 的全部资源键音频计数均为 0，因此当前真实结果是 `resource=missing / fallback=TTS`，没有宣称 MDD 原声命中。
- HTTPS 禁止重定向并限制 8 MiB；未知长度流超限立即中止并删除临时文件。
- 单发音 Job + Mutex 串行；TTS 回调严格关联 `utteranceId`，取消/关闭会停止并释放。

### 返回导航

- `FULL` 的管理页返回搜索页。
- `QUICK_LOOKUP` 的页面返回与系统返回共用一次性关闭门，只调用一次宿主 `onClose`。
- 模块不持有根 App 或功能1导航栈。

## 6. 自动测试与产物证据

里程碑3包含两组最小集成修复：将 `MdictEngine.kt` 的解析器接口和数据类型收紧为 `internal`；修复同一 Activity 复用 ViewModel 时 FULL→QUICK/连续 QUICK 的 `initialQuery` 与管理态同步。未改公共签名、Room、解析行为或其他功能。按要求最终重新强制验证：

```text
powershell.exe -NoProfile -ExecutionPolicy Bypass -File feature-02-dictionary\tools\test-module.ps1 -RerunTasks
```

- `BUILD SUCCESSFUL in 1m 25s`；37 actionable tasks，37 executed。
- JVM：48 tests，0 failures，0 errors，0 skipped；既有5项解析回归保留。入口契约测试覆盖 FULL创建VM后QUICK新词、连续不同QUICK查询、安装前查询排队、QUICK不继承管理态及一次关闭。
- androidTest：2个 Kotlin 源文件、8项测试，`compileDebugAndroidTestKotlin` 成功。
- AAR：`feature-02-dictionary-debug.aar`，425,353 bytes。
- SHA-256：`8837BBACF8B5615F028E5D2F9C02CA2B430E3FDD02A8DB2CCAC52CB30069D404`。
- native/JNI：0；功能目录 APK：0。
- 公开源码声明扫描只剩 `DictionaryPresentation` 与 `DictionaryFeature`。
- 候选文件扫描：MDX、MDD、数据库、缓存、APK、签名和 AAR 为 0；唯一 AAR 位于已排除的 `build/outputs/aar/`。

## 7. 未验证与已知风险

- 当前没有 Android 设备；androidTest 只编译源码，没有运行仪器测试。
- 未验证设备上的 Room、SAF provider 差异、WebView 渲染/安全设置、MediaPlayer、TTS、返回手势、可访问性或视觉效果。
- 未执行根 App 导航、功能1回调、内置 assets 安装、升级、签名 APK 或发布验证。
- 两本内置词典约57 MiB，首次安装需要足够私有存储与时间；重 I/O 已调度到 IO，但设备体验仍需验收。
- 是否启用网络发音及 `INTERNET` 权限由总控决定；两本当前 MDD 没有内置音频资源。

## 8. 总控集成顺序

1. 审查本里程碑最小源码差异（解析器类型 `public` → `internal`；公共入口复用VM时的查询/管理态同步）及本交接文件。
2. 使用总控登记 SHA-256 准备四个内置资产并生成 schemaVersion 1 manifest；不要修改用户原词典。
3. 根 App 依赖本 Library，建立底部“记单词 / 词典”双入口并保持默认进入记单词。
4. FULL 入口接入词典标签；功能1查词回调以普通字符串打开 QUICK_LOOKUP，并由 `onClose` 返回原导航栈。
5. 决定根 App 是否声明 `android.permission.INTERNET`；无权限时保持 MDD → TTS 的离线可用回退。
6. 总控复跑模块测试与全项目边界检查，再从干净提交构建签名 APK。
7. 在真实设备完成下列验收后，才能提高验收级别或发布。

## 9. 总控/设备验收清单

- [ ] 全项目仍为1个 Application，功能模块保持 Android Library；无功能模块 launcher。
- [ ] 内置 manifest 的4个文件路径、稳定ID和总控登记 SHA-256 全部一致。
- [ ] 首次安装与再次启动幂等；两本词典均可精确查询、前缀建议、切换标签及读取CSS/图片。
- [ ] 真实词条内部链接替换当前查询，QUICK_LOOKUP 关闭一次且回到原页面。
- [ ] FULL/QUICK_LOOKUP 底部入口和功能1普通字符串回调正确，默认仍进入记单词。
- [ ] SAF 导入、取消、重复、损坏、无MDD、分卷排序和仅私有副本删除符合边界。
- [ ] WebView 在真实设备禁用危险能力并阻止外部/穿越/超限资源。
- [ ] 当前两本词典明确回退英语TTS；若启用网络权限，严格HTTPS失败仍回退TTS。
- [ ] 数据库启停、排序、重启持久化及唯一冲突不删除既有数据。
- [ ] 无用户词典、数据库、缓存、APK或签名进入提交；由总控记录签名APK哈希与证书。

完成上述总控集成与设备验收前，本功能只能标记 `ready_for_integration`，不能标记已发布或实机通过。
