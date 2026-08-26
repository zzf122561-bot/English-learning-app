# EnglishApp v1.0.0 test.001 发布清单

## 构建来源

- 构建日期：2026-08-25
- 来源提交：`a3b5e2ac81be50df3c12ccb2007eeef8dd9257aa`（短提交号 `a3b5e2a`）
- 功能2提交：`fc93964`；主线功能合并：`add4c38`
- App版本：`1.0.0`
- versionCode：`5`
- 包名：`com.xuesui.englishapp`
- 最低系统版本：Android 10 / API 29
- 目标系统版本：API 36
- 包含功能：功能1 `0.3.0`；功能2 `0.2.0`

## 本次更新

- `F02-LINK-001`：只允许受控词条主文档内的安全锚点，用于快速定位词性/释义；不改变查询或返回栈，外部导航继续阻止。
- `F02-AUDIO-001`：正文发音链接改为受控播放动作；正文与顶部按钮固定执行MDD→严格HTTPS→Android英语TTS。
- 词典管理页增加每本词典独立十档正文字号；查询页无字号按钮，FULL/QUICK共用每本词典的持久化值。
- `englishapp_dictionary.db`从Room 1显式迁移到2，新增默认5档的`fontLevel`；禁止destructive migration。
- 公共`DictionaryFeature`签名、功能1、App包名、签名身份、双入口和QUICK_LOOKUP导航不变。

## 产物

- 文件名：`EnglishApp-v1.0.0-test.001-a3b5e2a.apk`
- 本地归档：`_manager/artifacts/app/v1.0.0/test.001/EnglishApp-v1.0.0-test.001-a3b5e2a.apk`
- 大小：`106,021,008`字节
- SHA-256：`99230CB21ACD6104675F88D7604C0C46BF6B64A716B392DF7E4A6F68DD0985FE`
- ZIP alignment：4字节对齐验证通过
- 签名：APK Signature Scheme v2验证通过；1个签名者
- 签名证书SHA-256：`1B8799F7712D1DE409DAF80034DBBF8465C37AA807567F3EB1996EF17179A788`
- 签名连续性：与已验收v0.2.0、v0.2.1 test.001、v0.3.0 test.001/test.002一致

## 内置词典证据

- 清单：`assets/dictionaries/manifest.json`，schemaVersion=1，SHA-256 `30A2753EDC3E8B2F4E93BFD62120E9CAD3C7B99720732BB9D191F6825BBD0921`。
- Collins MDX：`14843F7E443FB1CDACC94145C9AE68879A582F13FA68399D21CAA5768A704C10`。
- Collins MDD：`15A1668EAB08DBAD960C942CB99A7842AC8C7B77736A1936A4E77FD00FE6ED6C`。
- Oxford MDX：`8E140C288D9F8D195F707F1297ADD0233E9CB44C31DC0BBCFEB46063C99C24BE`。
- Oxford MDD：`4ED26627CD7FC26916CA23CF9A3AF44FD03710EECFCDDC8607B2717C1AAF1BAE`。
- 总控直接读取归档APK的ZIP条目并逐项重算，五项均与登记值一致；用户原始词典未修改、未提交Git。

## 自动验证

- 构建起点：Git工作树干净；来源提交固定为`a3b5e2a`。
- 第一次沙箱构建在AGP依赖解析阶段被缓存/网络边界阻止，没有产生或归档候选；随后使用获准的本机工具链上下文重跑同一提交。
- 完整构建：`BUILD SUCCESSFUL in 2m 12s`；164 actionable tasks，39 executed、125 up-to-date。
- 架构边界：通过；全项目1个Application模块、2个功能Library模块。
- 功能1：20项JVM测试结果为0失败/错误/跳过；3个Android测试源码共4项编译；模块无APK。
- 功能2：总控先独立强制复跑61项JVM测试，0失败/错误/跳过；3个Android测试源码共10项编译；AAR 466,380 bytes，SHA-256 `27890D223523524E9B7C16B2977EC2ED940D1E307BCC946D9B69CD1DDBFF7ECD`；模块无APK/JNI。
- 根App：release Kotlin、资源/DEX、lintVital、签名和assembleRelease通过。
- APK元数据：包名、1.0.0/code 5、minSdk 29、targetSdk 36和INTERNET权限核对通过。
- APK对齐：Build Tools 36.0.0 `zipalign -c -v 4`验证通过。
- APK签名：Build Tools 36.0.0 `apksigner verify --verbose --print-certs`验证v2签名和证书连续性通过。

## 验收状态

2026-08-26用户实机反馈词典应用内链接仍不可用；test.001判定不通过。其他设备验收项本次未确认。

| 层级 | 状态 | 说明 |
|---|---|---|
| 自动测试与构建 | 通过 | 只证明源码测试、结构、资产、元数据、对齐与签名正确 |
| Android仪器测试运行 | 未执行 | 当前没有连接设备或模拟器；只完成源码编译 |
| 设备覆盖安装 | 已收到用户反馈 | 用户已在设备发现内部链接仍失败；总控未操作设备 |
| 锚点/发音/字号/Room迁移 | 不通过/其余未报告 | 内部链接不通过；发音、字号和迁移本次没有用户结论 |
| 正式发布 | 候选淘汰 | 永久保留test.001，不创建`app-v1.0.0`标签 |

## 设备边界与音源说明

- 总控没有连接或操作Android设备，不能把自动构建写成实机通过。
- 两本随包内置词典的MDD中没有检测到音频资源，且抽样词条没有显式HTTPS音频引用；正常预期是回退Android英语TTS，不得宣称为MDD原声。
- `F02-LINK-001`的v0.2.0受控锚点方案已被用户实机判定仍失败；功能2v0.2.1重新处理。
- `F02-AUDIO-001`、逐词典字号和Room迁移在本次反馈中没有用户结论，不得推断通过或失败。
