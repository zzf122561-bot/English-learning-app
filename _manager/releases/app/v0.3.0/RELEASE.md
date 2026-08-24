# EnglishApp v0.3.0 正式发布记录

## 正式提升

- 提升日期：2026-08-24
- 正式标签：`app-v0.3.0`
- 标签目标 / APK来源提交：`6010e73afd67d73ef953046fb9e227197499923e`
- 正式二进制：沿用 v0.3.0 test.002，不重新构建、不改名、不覆盖
- App版本：`0.3.0` / versionCode `4`
- 功能组成：功能1 `0.3.0`；功能2 `0.1.1`

## 锁定产物

- 文件：`_manager/artifacts/app/v0.3.0/test.002/EnglishApp-v0.3.0-test.002-6010e73.apk`
- 大小：`105,971,856` 字节
- SHA-256：`C0D02FCB78051626EEC53C5CF64CEA41B06B2DC17E281ABB51EA511DF3FE64B7`
- 签名：APK Signature Scheme v2通过；证书SHA-256 `1B8799F7712D1DE409DAF80034DBBF8465C37AA807567F3EB1996EF17179A788`
- 完整自动构建证据：`test.002/RELEASE.md`
- 用户实机记录：`test.002/ACCEPTANCE.md`

## 用户验收

- 用户于2026-08-24反馈：除词典具体内容页面链接被阻止、无法获得内部发音外，其余均正常，并明确要求先记录、不修复。
- 验收结论：用户自测通过，接受两个已知问题后正式提升 v0.3.0。
- 已知问题：`F02-LINK-001`、`F02-AUDIO-001`，详见 `_manager/features/feature-02/KNOWN_ISSUES.md`。
- 证据限制：用户未提供设备型号、Android版本、逐项截图或日志；总控未执行设备测试，Android仪器测试仍只有源码编译证据。

## 历史边界

- test.001 的主WebView失败结论与原APK永久保留。
- test.002 的同一APK被提升为正式版本；不得以同版本重建的其他二进制替换。
