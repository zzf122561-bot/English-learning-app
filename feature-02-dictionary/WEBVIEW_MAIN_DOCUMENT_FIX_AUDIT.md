# v0.1.1 WebView 主文档请求分类修复证据

日期：2026-08-24

状态：`ready_for_integration / awaiting_test.002_device_retest`

## 实机缺陷边界

v0.3.0 test.001 的用户实机证据显示：查询和 Collins/Oxford 结果标签已经出现，但正文区域显示 `data:text/html;charset=utf-8;base64,` 与 `net::ERR_HTTP_RESPONSE_CODE_FAILURE`。总控定位为 `loadDataWithBaseURL()` 产生的 `data:` 主框架请求被 `shouldInterceptRequest()` 当作非法MDD资源返回403。

本功能任务没有设备，不能确认实机已经修复；最终状态只能等待 test.002 用户设备复测。

## 唯一代码修复

只修改：

- `web/DictionaryWebSecurityPolicy.kt`
- `web/SecureDictionaryWebView.kt`
- `DictionaryWebSecurityPolicyTest.kt`

新增纯 Kotlin/JVM `InterceptDecision` 三分支：

1. `AllowGeneratedMainDocument`：仅主框架且URL为严格应用主文档形式时交给WebView，客户端返回 `null`。
2. `ReadControlledResource(path)`：仅非主框架、`https://dictionary.local/<安全MDD路径>` 才调用 `resourceReader`。
3. `Blocked`：其余请求继续返回403。

允许的应用主文档形式只有：

- `data:text/html;charset=utf-8;base64,`（允许空或仅ASCII标准Base64负载，大小写不敏感匹配固定前缀）；
- 精确 `https://dictionary.local/` base URL。

两者都必须同时满足 `isForMainFrame=true`。相同URL用于子框架/子资源时不放行。

## 保持的安全矩阵

| 请求 | 主框架 | 非主框架 |
|---|---|---|
| 严格 `data:text/html;charset=utf-8;base64,...` | 交给WebView | 403 |
| 精确 `https://dictionary.local/` | 交给WebView | 403 |
| `https://dictionary.local/<安全MDD路径>` | 403 | 读取MDD；缺失404；超过8MiB返回413 |
| 受控host路径穿越、反斜杠、lookup伪资源或非法路径 | 403 | 403 |
| 外部HTTPS、HTTP、file、content、非受控host | 403 | 403 |

- `shouldOverrideUrlLoading()` 的内部查词与外部导航阻止未修改。
- CSP、JavaScript关闭、文件/内容访问关闭、混合内容、DOM storage、Web database、多窗口与JS Bridge边界未修改。
- 解析、Room、导入、查询协调、HTML改写、音频、公共Compose契约与根App均未修改。

## 新增JVM回归

- `generatedDataHtmlMainFrameIsHandledByWebViewButDataSubframesAreBlocked`
- `mainFrameConditionCannotAllowExternalFileContentOrHttpDocuments`
- `exactControlledBaseIsMainOnlyAndMddResourcesAreSubresourcesOnly`
- `invalidControlledSubresourcesNeverReachResourceReaderClassification`

既有外部导航、路径穿越、CSP、危险WebSettings、超限资源相关实现与测试继续保留。

## 强制验证

```text
powershell.exe -NoProfile -ExecutionPolicy Bypass -File feature-02-dictionary\tools\test-module.ps1 -RerunTasks
```

- `BUILD SUCCESSFUL in 1m 16s`；37 actionable tasks，37 executed。
- JVM：52 tests，0 failures，0 errors，0 skipped。
- androidTest：2个 Kotlin 源文件、8项测试；`compileDebugAndroidTestKotlin` 成功。
- AAR：`feature-02-dictionary-debug.aar`，430,712 bytes。
- SHA-256：`0E4D71852AB7A8D520F6C625EB0F18D62C36DAC5CEFF84181D8BF0BF08879FAF`。
- native/JNI：0；功能目录APK：0。

## 验收边界

- 自动测试只证明策略分类、客户端分支编译和既有模块回归通过。
- 当前没有Android设备，未运行仪器测试，也未看到修复后的WebView正文。
- 必须由总控集成并生成test.002，再由用户实机确认词条正文、CSS/图片以及既有导航/安全行为。
