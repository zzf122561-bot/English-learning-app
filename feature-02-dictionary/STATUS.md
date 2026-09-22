# 功能 2 当前状态（Android Library）

证据日期：2026-08-26；文档合并日期：2026-09-23，本轮未重新运行测试。

## 当前阶段

- 功能版本：`0.2.1`。
- 状态：`integrated / awaiting_app_1.0.0_test.002_device_verification`。总控既有集成记录确认 test.002 已签名归档，原“等待构建”表述已过时；没有新增实机通过证据。
- 用户已明确将“功能2：词典”指定为 `app-feature`；功能模块保持Android Library，总控边界检查为1个Application、2个Library。
- 原 `mdict-java` / `lzo-core` GPL路线永久停止；当前是有完整MIT来源审计的项目自有纯Kotlin/JVM解析内核，没有Rust、Cargo、NDK、JNI或解析器运行依赖。
- App 1.0.0 test.001 已由用户设备判定词典应用内链接仍失败；没有设备日志、具体词条或原始链接标记，本功能任务未宣称复现。
- v0.2.0 的兼容缺口是仍以少量路径/fragment/格式白名单分类受控内部链接，并关闭 JavaScript。真实结构审计进一步确认 Oxford9 的快捷定位存在 `onclick`/`className` 机制。
- v0.2.1 对当前词典受控文档内部默认放行：同源页面、未知路径、query、fragment、MDD 资源、相对/绝对同源链接、`entry:`、`bword:`、其他词典自定义 scheme、DOM 动态链接和内联事件不再因未枚举而进入 blocked/403。
- 受控 WebView 已启用 JavaScript，CSP 只允许 `dictionary.local` 与内联词典脚本；MDD `.js` 资源可按需从当前词典读取。没有 JS Bridge、弹窗或多窗口能力。
- 外部 HTTP/HTTPS 页面、file/content/intent/android-app/跨应用 scheme、文件/内容访问和外部脚本继续阻止；MDD 资源读取仍只经过当前已打开词典，8 MiB 大小边界保持。
- 跨词条 `entry:`/`bword:`/相对/自定义 scheme 仍替换当前查询，不建立功能内返回栈；QUICK_LOOKUP 的一次返回契约不变。
- 发音、字号、Room v2、`MIGRATION_1_2`、解析内核、导入和公共 `DictionaryFeature` 签名均未修改。

## 真实词典结构证据

| 词典 | 抽样内部机制 | 事件/脚本机制 | 结论 |
|---|---|---|---|
| Collins | `entry:` 1307、相对链接 517 | 事件 0、脚本 0、MDD `.js` 0 | 静态内部链接默认兼容 |
| Oxford9 | `entry:` 553、相对链接 524 | `onclick` 386，均为 `className`；脚本文件与 MDD `.js` 0 | 需要受控内联 JavaScript |

只记录结构计数，没有记录或复制词典正文。

## 验证边界

| 项目 | 当前状态 |
|---|---|
| 模块与边界 | 唯一公共Compose入口；DAO、实体、解析器和内部页面未公开；1个Application、2个Library |
| JVM 单元测试 | 强制复跑 63 tests，0 failures/errors/skipped；37 tasks 全部 executed |
| 真实词典兼容 | Collins/Oxford9内部链接精确命中；Oxford9 `onclick/className`机制已结构验证；未记录正文 |
| Android 测试源码 | 3 个源码文件、10 项测试编译成功；无设备，未运行 |
| AAR | 468,724 bytes；SHA-256 `69931817087AFB9A500E09D0FFDFF25D1BCAF9B577A40E0313997C662D2223AD` |
| native/JNI | AAR 内 0 |
| APK | 功能目录 0；功能任务未生成 APK |
| 数据库 | 保持 Room 2 与既有 `MIGRATION_1_2`，无变化 |
| 公共契约 | `DictionaryFeature(...)` 签名不变 |
| 实机 | 无设备；不得宣称 v0.2.1 内部链接已在设备修复 |

## 下一步

总控此前已完成 v0.2.1 增量审查、模块/边界验证及 App 1.0.0 test.002 签名归档，等待用户覆盖安装验收。功能任务保持等待；当前文档整理不启动新的开发或测试。

## 历史验收与证据入口

- v0.1.1 随 App v0.3.0 已获用户概括验收，历史上接受 `F02-LINK-001` 与 `F02-AUDIO-001`；用户于 2026-08-25 重新授权在 v0.2.0 处理两项问题并增加逐词典字号。
- App 1.0.0 test.001 实机确认 v0.2.0 受控锚点方案仍失败，该历史结论保留；v0.2.1 test.002 不能仅凭自动验证标记实机修复。
- v0.2.1 增量与自动证据：[V0.2.1_INTERNAL_LINK_COMPATIBILITY_AUDIT.md](V0.2.1_INTERNAL_LINK_COMPATIBILITY_AUDIT.md)；v0.2.0 证据：[V0.2.0_AUDIT.md](V0.2.0_AUDIT.md)。
- 历史交接：[MILESTONE_3_HANDOFF.md](MILESTONE_3_HANDOFF.md)；解析来源与许可：[MILESTONE_1B_AUDIT.md](MILESTONE_1B_AUDIT.md)、[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。现存里程碑与修复审计继续保留。
