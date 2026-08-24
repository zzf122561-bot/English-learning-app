# EnglishApp v0.3.0 test.002 发布清单

## 构建来源

- 构建日期：2026-08-24
- 来源提交：`6010e73afd67d73ef953046fb9e227197499923e`（短提交号 `6010e73`）
- App 版本：`0.3.0`
- versionCode：`4`
- 包名：`com.xuesui.englishapp`
- 最低系统版本：Android 10 / API 29
- 目标系统版本：API 36
- 包含功能：功能1 `0.3.0`；功能2 `0.1.1`

## 本次修复

- 保留 v0.3.0 test.001 的全部功能，只修复实机发现的词条 WebView 主文档被资源拦截器误返回403。
- 应用生成的严格 base64 HTML 主框架和精确受控 base URL 交给 WebView；data 子框架、外部主框架、file/content/http URL、主框架 MDD 资源和非法受控路径仍返回403。
- 功能2公共 Compose 契约、Room v1 schema、解析器、查询、导入、HTML 改写、发音和根 App 导航均未改变。

## 产物

- 文件名：`EnglishApp-v0.3.0-test.002-6010e73.apk`
- 本地归档：`_manager/artifacts/app/v0.3.0/test.002/EnglishApp-v0.3.0-test.002-6010e73.apk`
- 大小：`105,971,856` 字节
- SHA-256：`C0D02FCB78051626EEC53C5CF64CEA41B06B2DC17E281ABB51EA511DF3FE64B7`
- ZIP alignment：4字节对齐验证通过
- 签名：APK Signature Scheme v2 验证通过；1个签名者
- 签名证书 SHA-256：`1B8799F7712D1DE409DAF80034DBBF8465C37AA807567F3EB1996EF17179A788`
- 签名连续性：与已验收 v0.2.0、v0.2.1 test.001 和 v0.3.0 test.001 的证书指纹一致

## 内置词典证据

- 清单：`assets/dictionaries/manifest.json`，`schemaVersion=1`，SHA-256 `30A2753EDC3E8B2F4E93BFD62120E9CAD3C7B99720732BB9D191F6825BBD0921`。
- Collins MDX：`14843F7E443FB1CDACC94145C9AE68879A582F13FA68399D21CAA5768A704C10`。
- Collins MDD：`15A1668EAB08DBAD960C942CB99A7842AC8C7B77736A1936A4E77FD00FE6ED6C`。
- Oxford MDX：`8E140C288D9F8D195F707F1297ADD0233E9CB44C31DC0BBCFEB46063C99C24BE`。
- Oxford MDD：`4ED26627CD7FC26916CA23CF9A3AF44FD03710EECFCDDC8607B2717C1AAF1BAE`。
- 总控直接读取归档 APK 的 ZIP 条目并逐项计算哈希，五项均与构建登记一致；用户原始文件未修改、未提交 Git。

## 自动验证

- 构建起点：Git 工作树干净；来源提交固定为 `6010e73`。
- 清理：`:app:clean`、`:feature-01-word-memory:clean`、`:feature-02-dictionary:clean` 成功。
- 完整构建：`BUILD SUCCESSFUL in 2m 27s`；167 actionable tasks，156 executed、11 up-to-date。
- 架构边界：通过；全项目1个 Application 模块、2个功能 Library 模块。
- 功能1：20项 JVM 测试通过，0失败/错误/跳过；3个 Android 测试源码编译通过；功能目录无 APK。
- 功能2：52项 JVM 测试通过，0失败/错误/跳过；2个 Android 测试源码共8项编译通过；AAR 430,712 bytes；功能目录无 APK/JNI。
- 根 App：release Kotlin 编译、lintVital、资源/DEX 合并、签名与 APK 组装通过。
- APK 元数据：包名、版本名、versionCode、minSdk、targetSdk和INTERNET权限核对通过。
- APK 对齐：Build Tools 36.0.0 `zipalign -c -v 4` 验证通过。
- APK 签名：Build Tools 36.0.0 `apksigner verify --verbose --print-certs` 验证 v2 签名和证书连续性通过。

## 验收状态

| 层级 | 状态 | 说明 |
|---|---|---|
| 自动测试与构建 | 通过 | 只证明源码测试、结构、资产、元数据、对齐与签名正确 |
| Android仪器测试运行 | 未执行 | 当前没有连接设备或模拟器；只完成源码编译 |
| 设备覆盖安装 | 用户自测通过 | 用户概括确认除两项已知问题外其余均正常；未提供设备型号或逐项证据 |
| WebView主文档缺陷复测 | 用户自测通过 | 词条正文恢复；未再报告 test.001 的主文档加载错误 |
| 用户功能确认 | 通过（接受已知问题） | `F02-LINK-001`与`F02-AUDIO-001`明确延期，其余项目由用户概括确认正常 |

## 历史边界

- test.001 的实机失败结论和 APK 永久保留，不因本次自动构建通过而改写。
- test.002 已获得用户实机概括确认；总控仍未执行设备测试，Android仪器测试也未在设备运行。
- 用户接受两个已知问题后，同一test.002二进制提升为正式v0.3.0并创建 `app-v0.3.0` 标签，不重新构建。

## 已知问题（用户接受延期）

- `F02-LINK-001`：词典具体内容页面中的链接被阻止。
- `F02-AUDIO-001`：无法获得词条内部发音。
- 两项均只登记、不在本版修复；证据边界与后续规则见 `_manager/features/feature-02/KNOWN_ISSUES.md`。
