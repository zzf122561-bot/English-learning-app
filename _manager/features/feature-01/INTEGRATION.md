# 功能1集成记录

| 项目 | 当前值 |
|---|---|
| 功能 ID | `feature-01-word-memory` |
| 已验收功能版本 | `0.3.0`（test.002用户概括确认） |
| v0.2.0验收提交 | `088366cd8eb20590258609869c8141ea6ea7cd33` |
| v0.2.0标签 | `feature-01-v0.2.0-font-sizing`、`feature-01-v0.2.0-user-verified`、`feature-01-v0.2.0-user-tested` |
| v0.3.0 App验收来源 | `6010e73afd67d73ef953046fb9e227197499923e`；正式标签 `app-v0.3.0` |
| 公共入口 | `WordMemoryFeature(onLookupRequested: (String) -> Unit = {})`；原无参数调用保持兼容 |
| 数据库 | Room 版本 2；保留显式 1→2 迁移 |
| 集成状态 | v0.3.0回调增量已由总控审查、复测、提交并合并；20 tests/0 failures、AAR 229,042 bytes、功能目录APK=0；用户概括确认正常 |

## v0.3.0 增量记录

- 只在学习英文正文增加基于 `TextLayoutResult` 的长按定位和内部 Latin 单词边界提取；不读取或依赖功能2实现。
- 回调保留原始大小写与字符，只传普通 `String`；空白、中文、数字、纯标点和越界不触发。
- 总控独立强制复跑37个Gradle任务成功：20 tests、0 failures/errors/skipped，Android测试源码编译与AAR构建通过。
- AAR SHA-256：`589E74C3BAC4CE2FB78E121B249EA7CEFCD6801F4ED7E72EECA382C737D2DFD8`。
- 2026-08-24用户概括确认test.002除功能2两项已知问题外其余均正常，计入长按手势、滚动协同和返回位置验收；总控未连接设备，也未取得逐项截图或日志。
