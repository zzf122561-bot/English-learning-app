# EnglishApp v0.3.0 test.001 发布清单

## 构建来源

- 构建日期：2026-08-24
- 来源提交：`bfbfcc5d01e14471a4f79080c33859867b2caac3`（短提交号 `bfbfcc5`）
- App 版本：`0.3.0`
- versionCode：`4`
- 包名：`com.xuesui.englishapp`
- 最低系统版本：Android 10 / API 29
- 目标系统版本：API 36
- 包含功能：功能1 `0.3.0`；功能2 `0.1.0`

## 产物

- 文件名：`EnglishApp-v0.3.0-test.001-bfbfcc5.apk`
- 本地归档：`_manager/artifacts/app/v0.3.0/test.001/EnglishApp-v0.3.0-test.001-bfbfcc5.apk`
- 大小：`105,955,472` 字节
- SHA-256：`03E1863D2BBE44BD224187A09A0D0093A12F567072C6F4FFBEE402650F1D0E35`
- ZIP alignment：4字节对齐验证通过
- 签名：APK Signature Scheme v2 验证通过；1个签名者
- 签名证书 SHA-256：`1B8799F7712D1DE409DAF80034DBBF8465C37AA807567F3EB1996EF17179A788`
- 签名连续性：与已验收 v0.2.0 及 v0.2.1 test.001 的证书指纹一致

## 内置词典证据

- 清单：`assets/dictionaries/manifest.json`，`schemaVersion=1`，SHA-256 `30A2753EDC3E8B2F4E93BFD62120E9CAD3C7B99720732BB9D191F6825BBD0921`。
- Collins MDX：`14843F7E443FB1CDACC94145C9AE68879A582F13FA68399D21CAA5768A704C10`。
- Collins MDD：`15A1668EAB08DBAD960C942CB99A7842AC8C7B77736A1936A4E77FD00FE6ED6C`。
- Oxford MDX：`8E140C288D9F8D195F707F1297ADD0233E9CB44C31DC0BBCFEB46063C99C24BE`。
- Oxford MDD：`4ED26627CD7FC26916CA23CF9A3AF44FD03710EECFCDDC8607B2717C1AAF1BAE`。
- 总控直接读取 APK ZIP 条目并逐项计算哈希，五项均与构建登记一致；用户原始文件未修改、未提交 Git。

## 自动验证

- 构建起点：Git 工作树干净；来源提交固定为 `bfbfcc5`。
- 清理：`:app:clean`、`:feature-01-word-memory:clean`、`:feature-02-dictionary:clean` 成功。
- 完整构建：`BUILD SUCCESSFUL in 2m 47s`；164 actionable tasks，153 executed、11 up-to-date。
- 架构边界：通过；全项目1个Application模块、2个功能Library模块。
- 功能1：20项JVM测试通过，0失败/错误/跳过；Android测试源码编译通过；功能目录无APK。
- 功能2：48项JVM测试通过，0失败/错误/跳过；2个androidTest源码共8项编译通过；功能目录无APK。
- 根 App：release Kotlin编译、lintVital、资源/DEX合并、签名与APK组装通过。
- APK 元数据：包名、版本号、versionCode、minSdk、targetSdk和INTERNET权限核对通过。
- APK 对齐：Build Tools 36.0.0 `zipalign -c -v 4` 验证通过。
- APK 签名：Build Tools 36.0.0 `apksigner verify --verbose --print-certs` 验证v2签名和证书连续性通过。

## 验收状态

| 层级 | 状态 | 说明 |
|---|---|---|
| 自动测试与构建 | 通过 | 只证明源码测试、结构、资产、元数据、对齐与签名正确 |
| Android仪器测试运行 | 未执行 | 当前没有连接设备或模拟器；只完成源码编译 |
| 设备覆盖安装 | 已收到部分实机证据 | 用户截图证明App已运行并进入词典；是否覆盖安装及旧数据状态尚未确认 |
| 用户功能确认 | 不通过 | 查询到达词典标签，但词条主WebView报HTTP响应码错误；test.001不得提升 |

## 已知实机缺陷

- 2026-08-24 用户截图：查询 `another place` 后显示Collins/Oxford标签，但正文显示 `data:text/html;charset=utf-8;base64,` 无法加载，错误为 `net::ERR_HTTP_RESPONSE_CODE_FAILURE`。
- 总控定位为本地WebView拦截器对应用自身 `data:` 主文档返回403；与MDX未命中、词典未安装或网络断开无关。
- test.001保留为失败历史产物，不删除、不覆盖、不创建 `app-v0.3.0` 标签。修复候选必须另存为test.002。
