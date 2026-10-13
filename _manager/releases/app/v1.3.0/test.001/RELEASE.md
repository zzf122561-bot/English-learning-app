# saber 1.3.0 test.001 — 2026-10-14

状态：`automated_integration_passed / awaiting_user_device_validation`。已从干净本地提交构建、签名和永久归档；不是正式验收版，不创建app-v1.3.0标签。用户“当前语音基本正常”按原范围保留，不代替本候选后台/恢复/新入口验收。

## 安装包

- [EnglishApp-v1.3.0-test.001-eb6b5fb.apk](../../../../artifacts/app/v1.3.0/test.001/EnglishApp-v1.3.0-test.001-eb6b5fb.apk)
- 来源：`eb6b5fb8c019d6679bd9c7e39011e0cd58d6da4f`。大小：1,556,364,866 bytes（约1.56 GB）；SHA-256：`44ba96c489e197f17f8af25d3a02ae29f69e1f4820e86ec9dfb5cffd36d6f7b9`。
- 包名`com.xuesui.englishapp`、显示名`saber`、versionName`1.3.0`、versionCode`8`、minSdk29、arm64-v8a。Saber图标、旧签名、词典及原Qwen1.7B Q4模型不变。
- v2签名验证通过；证书SHA-256：`1b8799f7712d1de409daf80034dbbf8465c37aa807567f3eb1996ef17179a788`。
- 完整机器证据：[ARTIFACT.json](ARTIFACT.json)。请覆盖安装，不卸载旧App，不清除数据；全部历史APK继续保留。

## 本次交付

- 功能1 **0.6.0**：中文显隐旁“播放本段”，顶部“播放全文”，生成全文放更多菜单；保留单句/范围生成、原阅读位置、字号、默写和临时查词。只通过公共槽交接干净英文。
- 语音 **0.4.0**：生成任务由进程级服务和持久自然段队列承载；完成一段即可保存和播放。全文播放采用Media3段落列表，播放/生成分别暂停；缺音频先确认，补齐等待缺段，或明确选择跳过缺段。恢复生成不自动出声。
- 每个新任务冻结设置与随机种子，失败不覆盖旧成功音频，同段新成功结果替换后按引用回收；全文导出按需拼接。语音Tab仍只管理模型/音色/参数，高级设置增加中文效果说明，默认参数不随意改变。
- 新语音账本是模块自有SQLite schema1，不读取/修改功能1或词典数据库；原Room版本保持。仅当前新队列采用自然段持久记录，旧音频兼容证据沿用模块交接。
- 生成服务按系统版本选择mediaProcessing/dataSync，播放为mediaPlayback；两个服务均不导出、stopWithTask=false。主动单实例预算350分钟，wake lock兜底多1分钟；系统共享额度可能更早触发超时。强停/重启/系统杀进程不能承诺持续计算，只依靠持久进度在再次打开时恢复；用户暂停/取消不自动恢复。
- 词典 **0.2.1**不改，保留独立语音测试App；本轮未重打其旧APK。

## 自动证据与集成边界

- 功能1提交519c487/合并d16d416：39项JVM、Android测试源码/AAR证据沿用模块STATUS。实际共享builder合成DOCX定向测试bb5691e/64f7d5f：1项非skip通过，2段/4目标和干净英文逐项匹配，2026/3.14保留、显示原文不变。未把外部fixture放进Git/APK。
- 语音c45be63/8192603：SOURCE_MANIFEST第14节66项受影响测试（65通过/1 Windows符号链接环境跳过）、Android源码编译/AAR、Lint0错误8提示；预算修正2项通过。按规则直接沿用，不重复旧模型/native/解析审查。过滤运行后的XML不代表前一轮66项全量。
- 总控新增接线：RuntimeHost位于Tab/查词分支之外，playbackControl映射同文章同段稳定请求。宿主18项JVM（10项InlineReadingRequest、8项ReadingRoute）通过、0失败/错误/跳过，验证范围和旧调用兼容，不是实际UI触摸/播放证据。
- 首次集成8192603完整构建2m57s成功。加入仅测试交接与归档脚本后，最终干净eb6b5fb增量构建25s成功（230 tasks，5 executed/225 up-to-date）；未重新运行无变更模块测试。日志在Git外`_manager/local/build-saber-1.3.0-{test001,final}.log`。
- 架构检查：1 Application / 3 Library。归档实际核对包名/版本/最小系统/签名、18个资产/notice/SO条目哈希、3个预期arm64库、ZIP/ELF 16KiB对齐、两个服务的精确Manifest属性。
- 独立测试宿主新依赖/接线兼容：以既有JDK17/SDK36和同一启动hosts执行独立工程`:app:compileDebugKotlin :app:processDebugMainManifest`，BUILD SUCCESSFUL in 1m（18 tasks，8 executed/10 up-to-date）；只编译，不生成新独立APK。日志`_manager/local/verify-probe-130.log`。
- 新权限为FGS及三种服务类型、通知、唤醒；Media3 1.11.1 exoplayer/common依赖带入ACCESS_NETWORK_STATE，合并报告确认后显式登记。保留原INTERNET；无麦克风、文件读取或全盘访问新权限。归档检查仍拒绝未知权限，aapt2布尔/十六进制格式适配不放宽服务值。
- Android仪器测试只完成源码编译；没有运行模型/锁屏/超时/实际恢复或实际播放，不宣称体验和速度改进已在手机验证。

## 独立共享Skill

已接收生成器Git外MANAGER_OUTBOX完整迁移报告：实现`D:/CodexData/skills/english-vocabulary-story`，唯一库`D:/CodexData/skills-data/english-vocabulary-story/vocabulary.sqlite3`，Codex发现入口为Junction。1412词/A0001–A1412、1份旧故事、1412使用关系保留；显式schema1→2、只读备份、缺库拒绝、事务并发去重、幂等事件和格式门禁通过隔离测试。Skill/词库/故事不进入本Git或APK。

固定story-docx-v1及脚本生成流程已接通上述真实builder合成fixture→App parser/cleaner；未做Word视觉渲染，也不等同手机系统文件选择器/Room导入验收。平台拒绝生成器跨任务发送的历史保留，总控按已授权本地OUTBOX接收，没有换通道重发。

设备验收按[ACCEPTANCE.md](ACCEPTANCE.md)，先少量段再长文。没有GitHub推送；发布证据后续提交不改变本APK来源。
