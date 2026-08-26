# EnglishApp v1.0.0 test.002 发布清单

## 构建来源

- 构建日期：2026-08-26
- 来源提交：`5719bb3562e481c7512357974135da73736ddd8d`（短提交号 `5719bb3`）
- 功能2提交：`de23797`；主线功能合并：`68644a9`
- App版本：`1.0.0`
- versionCode：`5`
- 包名：`com.xuesui.englishapp`
- 最低系统版本：Android 10 / API 29
- 目标系统版本：API 36
- 包含功能：功能1 `0.3.0`；功能2 `0.2.1`

## 本次相对test.001的修复

- test.001已由用户实机确认词典应用内链接仍失败并永久淘汰；原APK与证据不覆盖、不改写。
- 不再枚举少数受控锚点/路径：当前 `dictionary.local` 词典文档、同源MDD资源、query、fragment、相对/绝对内部链接、`entry:`、`bword:`、自定义词典scheme和DOM动态链接默认兼容。
- 真实Oxford9结构审计确认快捷定位依赖386个 `onclick`，均通过 `className`修改DOM；受控词典WebView因此启用JavaScript和本地/内联脚本CSP。
- 没有新增JavaScript Bridge；外部网页、file/content/intent/android-app、文件/内容访问、多窗口和自动开窗继续阻止。
- 功能2发音、逐词典字号、Room 2及 `MIGRATION_1_2`、公共 `DictionaryFeature`签名和功能1均未改变。

## 产物

- 文件名：`EnglishApp-v1.0.0-test.002-5719bb3.apk`
- 本地归档：`_manager/artifacts/app/v1.0.0/test.002/EnglishApp-v1.0.0-test.002-5719bb3.apk`
- 大小：`106,004,624`字节
- SHA-256：`6EF00A222D073EA87D58CD246226B43B3ADDC5E42A12A37A1D59474FC0AD160C`
- ZIP alignment：4字节对齐验证通过
- 签名：APK Signature Scheme v2验证通过；1个签名者
- 签名证书SHA-256：`1B8799F7712D1DE409DAF80034DBBF8465C37AA807567F3EB1996EF17179A788`
- 签名连续性：与已安装历史版本及v1.0.0 test.001一致

## 内置词典证据

- `assets/dictionaries/manifest.json`：1,148 bytes；SHA-256 `30A2753EDC3E8B2F4E93BFD62120E9CAD3C7B99720732BB9D191F6825BBD0921`。
- Collins MDX：13,887,505 bytes；SHA-256 `14843F7E443FB1CDACC94145C9AE68879A582F13FA68399D21CAA5768A704C10`。
- Collins MDD：15,307 bytes；SHA-256 `15A1668EAB08DBAD960C942CB99A7842AC8C7B77736A1936A4E77FD00FE6ED6C`。
- Oxford MDX：41,514,928 bytes；SHA-256 `8E140C288D9F8D195F707F1297ADD0233E9CB44C31DC0BBCFEB46063C99C24BE`。
- Oxford MDD：4,104,095 bytes；SHA-256 `4ED26627CD7FC26916CA23CF9A3AF44FD03710EECFCDDC8607B2717C1AAF1BAE`。
- 总控直接打开归档APK的ZIP条目并重算五项哈希，均与登记值一致；用户原始词典未修改、未提交Git。

## 自动验证

- 构建起点：Git工作树干净；来源提交固定为 `5719bb3`。
- 功能2增量独立强制回归：`BUILD SUCCESSFUL in 1m 1s`；37个任务全部执行；63项JVM测试0失败/错误/跳过；3个Android测试源码共10项编译成功。
- 功能2AAR：468,724 bytes；SHA-256 `69931817087AFB9A500E09D0FFDFF25D1BCAF9B577A40E0313997C662D2223AD`；native/JNI=0、模块APK=0。
- 完整构建：`BUILD SUCCESSFUL in 1m 19s`；164 actionable tasks，27 executed、137 up-to-date。
- 功能1：20项JVM测试0失败/错误/跳过；3个Android测试源码共4项编译；模块APK=0。
- 根App：release Kotlin、资源/DEX、lintVital、签名和assembleRelease通过。
- 架构边界：全项目1个Application模块、2个功能Library模块。
- APK元数据：包名、1.0.0/code 5、minSdk 29、targetSdk 36和INTERNET权限通过。
- Build Tools 36.0.0：`zipalign -c 4`及 `apksigner verify --verbose --print-certs`通过。

## 验收状态

| 层级 | 状态 | 说明 |
|---|---|---|
| 自动测试与构建 | 通过 | 证明源码回归、结构、资产、元数据、对齐和签名正确 |
| Android仪器测试运行 | 未执行 | 当前没有连接设备或模拟器；只完成源码编译 |
| 设备覆盖安装 | 待用户自测 | 必须覆盖现有App，不卸载、不清数据 |
| 内部链接 | 待用户自测 | 自动测试不能证明Oxford真实onclick和WebView设备行为 |
| 发音/字号/Room迁移 | 待用户自测 | test.001反馈没有确认这些项目 |
| 正式发布 | 未完成 | 用户确认同一test.002 APK后才能提升并创建 `app-v1.0.0`标签 |

## 设备边界

- 总控没有连接或操作Android设备，不能把自动构建写成实机通过。
- `F02-LINK-001`当前只能标记implemented in v0.2.1 / awaiting test.002 device verification。
- 两本内置词典仍没有检测到MDD音频资源；发音正常预期为Android英语TTS回退，不得宣称MDD原声。
