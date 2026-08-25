# 功能 2 当前状态（Android Library）

更新时间：2026-08-25

## 当前阶段

- 功能版本：`0.2.0`。
- 状态：`manager_review_passed / awaiting_app_1.0.0_build`。
- 用户已明确将“功能2：词典”指定为 `app-feature`；功能模块保持Android Library，总控边界检查为1个Application、2个Library。
- 原 `mdict-java` / `lzo-core` GPL路线永久停止；当前是有完整MIT来源审计的项目自有纯Kotlin/JVM解析内核，没有Rust、Cargo、NDK、JNI或解析器运行依赖。
- v0.1.1随App v0.3.0获用户概括验收，但历史上接受 `F02-LINK-001`、`F02-AUDIO-001`；用户于2026-08-25重新授权本轮修复。
- 用户已重新授权处理 `F02-LINK-001`、`F02-AUDIO-001`，并批准逐词典正文字号。
- 同文档链接只允许精确受控主文档的非空安全 `#fragment`；由 WebView 在当前正文内滚动，不替换查询、不增加模块返回栈。跨词条查询仍只走受控 `/lookup?q=`。
- 正文可识别发音链接改写为受控播放动作并由原发音协调器处理；顶部蓝色按钮使用当前词条候选。顺序固定为 MDD → 严格 HTTPS → Android 英语 TTS，新请求取消旧请求。
- 两套授权真实词典抽样审计均保持 `entry` 内部链接精确命中；抽样词条未发现显式音频引用，两本 MDD 音频资源计数均为 0，因此审计结果为 `resource=missing / fallback=TTS`，未宣称 MDD 原声命中。
- 逐词典字号只位于词典管理页，共 10 档，默认 5；查询页无 Aa。保存才持久化，取消不更改，恢复默认只把待保存值设为 5。
- Room 数据库已从 v1 升级到 v2；`fontLevel INTEGER NOT NULL DEFAULT 5` 通过显式 `MIGRATION_1_2` 添加，没有 destructive migration。字号更新只写目标 ID 的 `fontLevel`，不改时间、顺序或状态。
- 公共 `DictionaryPresentation` / `DictionaryFeature` 签名不变；解析内核、导入、外部导航和其余 WebView 安全边界未放宽。

## 验证边界

| 项目 | 当前状态 |
|---|---|
| 模块与边界 | 唯一公共Compose入口；DAO、实体、解析器和内部页面未公开；1个Application、2个Library |
| JVM 单元测试 | 强制复跑 61 tests，0 failures/errors/skipped；37 tasks 全部 executed |
| 真实词典兼容 | Collins、Oxford9 的内部词条链接命中；两本均无 MDD 音频资源，按设计回退 TTS；未记录正文 |
| Android 测试源码 | 3 个源码文件、10 项测试编译成功；含真实 v1→v2 Room 迁移测试；无设备，未运行 |
| AAR | `assembleDebug` 成功；466,380 bytes；SHA-256 `27890D223523524E9B7C16B2977EC2ED940D1E307BCC946D9B69CD1DDBFF7ECD` |
| native/JNI | AAR 内 0 |
| APK | 功能目录 0；功能任务未生成 APK |
| 实机 | 未连接设备；不得宣称 WebView、发音、字号或迁移实机通过 |

## 下一步

总控增量审查和独立模块复测已通过；等待升级根App并构建签名App 1.0.0候选。功能任务保持停止，不执行根App修改、签名或APK构建。
