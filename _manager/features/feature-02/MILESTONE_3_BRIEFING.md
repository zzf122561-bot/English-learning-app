# 功能2里程碑3收尾交接简报

下发日期：2026-08-24

功能 ID：`feature-02-dictionary`

目标版本：`0.1.0`

## 已批准基线

- 总控已批准里程碑2并建立新的审查锚点。
- 锚点证据：44项JVM测试全部通过；2个androidTest源码编译；AAR 420,005 bytes；SHA-256 `8AB43658462C528339FB85ABA6F4A20D947150EB546C8812010658B04FFC0894`；无native/JNI和APK；全项目边界为1个Application、2个Library。
- 两本真实词典内部 `entry` 链接已改写并精确命中；两本MDD音频资源数均为0，当前如实回退Android英语TTS。
- 后续不重复审查未变化代码。若本里程碑不改源码，可直接复用上述新鲜测试证据；只有源码或构建配置变化时才复跑受影响测试。

## 本里程碑唯一目标

把已批准的功能模块整理成可由总控提交和集成的稳定交接包。不得新增产品功能、重构解析内核、修改公共契约、修改数据库schema或进入根App集成。

允许修改范围仍仅为 `feature-02-dictionary` 的功能文档、必要的交接清单，以及确有阻塞时的最小模块修复。禁止Git、根App/Manifest、全局Gradle、版本、签名、APK、功能1和其他功能目录。

## 必须完成的交接内容

1. 新增 `MILESTONE_3_HANDOFF.md`，逐项列出：
   - 公共入口及包名；
   - Room数据库名、版本、schema文件；
   - 模块运行所需根App依赖、Manifest权限和生命周期动作；
   - 内置清单schema与私有目录契约；
   - 导入、WebView、音频和返回导航边界；
   - 自动测试证据、未连接设备边界和已知风险；
   - 总控集成顺序及验收清单。
2. 将 `STATUS.md` 标为 `ready_for_integration / awaiting_manager_commit`，把 `CHANGELOG.md` 的 `0.1.0` 保持“待总控集成”，不能标记正式发布或实机通过。
3. 核对 `PUBLIC_CONTRACT.md` 与源码完全一致；不得新增第二个公共入口或暴露DAO、实体、解析器和页面内部类型。
4. 核对用户词典、构建产物、数据库、缓存、APK和签名都未进入Git候选文件。
5. 给出总控集成所需的精确资产清单：
   - `builtin.collins-advanced-bilingual`：柯林斯MDX与MDD；
   - `builtin.oxford-ald9-en-en`：Oxford MDX与MDD；
   - 文件哈希继续以总控登记值为准，不得复制用户词典到模块。
6. 明确根App后续工作：依赖本Library、提供底部双入口和导航栈、连接功能1回调、生成内置assets/manifest、决定 `android.permission.INTERNET`、保持默认进入记单词。

## 验证与汇报

- 若只改文档，核对当前AAR、测试XML和源码状态未发生变化即可，不做冗余构建。
- 若修改任何Kotlin、Manifest或 `build.gradle.kts`，必须强制复跑模块测试、Android测试源码编译和AAR构建，并报告新哈希。
- 当前没有Android设备，任何情况下都不得宣称仪器测试、WebView、SAF、媒体、TTS或实机运行通过。
- 完成后按固定 `[FEATURE_MILESTONE]` 格式汇报，状态使用 `ready_for_integration`，列出本里程碑是否修改源码、最终交接文件、沿用或更新的测试证据、数据库/公共契约变化和请求总控动作。
- 汇报后停止；只有总控负责Git提交、合并、根App集成、签名APK和发布。
