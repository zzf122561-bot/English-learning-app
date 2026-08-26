# 功能 2 当前状态（Android Library）

更新时间：2026-08-26

## 当前阶段

- 功能版本：`0.2.1`。
- 状态：`ready_for_integration / awaiting_manager_build`。
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
| JVM 单元测试 | 强制复跑 63 tests，0 failures/errors/skipped；37 tasks 全部 executed |
| Android 测试源码 | 3 个源码文件、10 项测试编译成功；无设备，未运行 |
| AAR | 468,724 bytes；SHA-256 `69931817087AFB9A500E09D0FFDFF25D1BCAF9B577A40E0313997C662D2223AD` |
| native/JNI | AAR 内 0 |
| APK | 功能目录 0；功能任务未生成 APK |
| 数据库 | 保持 Room 2 与既有 `MIGRATION_1_2`，无变化 |
| 公共契约 | `DictionaryFeature(...)` 签名不变 |
| 实机 | 无设备；不得宣称 v0.2.1 内部链接已在设备修复 |

## 下一步

等待总控仅审查 v0.2.1 WebView 内部链接增量、复跑边界并构建 App 1.0.0 test.002。功能任务停止，不执行 Git、根 App 修改、签名或 APK 构建。
