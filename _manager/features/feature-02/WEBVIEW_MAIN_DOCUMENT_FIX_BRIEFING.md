# 功能2 v0.1.1：WebView主文档误拦截修复简报

日期：2026-08-24

功能任务：`功能2：词典` / `01a02fd4-7dcb-7882-aad0-0ed84ad3254c`

目标版本：功能2 `0.1.1`

## 实机缺陷

v0.3.0 test.001在用户实机中可以查询并显示Collins/Oxford结果标签，但词条正文显示：

```text
data:text/html;charset=utf-8;base64,
net::ERR_HTTP_RESPONSE_CODE_FAILURE
```

总控已定位：`SecureDictionaryWebViewClient.shouldInterceptRequest()` 把 `loadDataWithBaseURL()` 产生的 `data:` 主框架请求当作非受控资源返回403。

## 唯一授权范围

只修改 `feature-02-dictionary` 内下列受影响链路及同组测试/文档：

- `web/SecureDictionaryWebView.kt`
- 必要时的 `web/DictionaryWebSecurityPolicy.kt`
- 对应 `src/test`、`src/androidTest`
- `STATUS.md`、`CHANGELOG.md`，并新增本次修复证据文档

不得读取、复审、格式化或修改解析内核、Room、导入、查询协调、HTML重写、音频、公共Compose契约、根App或其他功能。不得执行Git、签名或APK。

## 固定修复行为

1. `shouldInterceptRequest` 必须允许WebView自行处理应用生成的主框架HTML：至少覆盖本机实证的主框架 `data:text/html...` 请求；如需兼容不同WebView实现，可同时仅允许主框架的精确受控base URL。
2. 放行必须同时满足“主框架”和严格允许的应用主文档形式；子框架/子资源的 `data:` 继续阻止。
3. `http://`、外部 `https://`、`file://`、`content://`、非受控host、路径穿越和受控host上的非法路径继续返回阻止响应。
4. 只有 `https://dictionary.local/<安全MDD路径>` 子资源可进入 `resourceReader`；404、413和CSP/JavaScript/文件访问等既有安全行为保持不变。
5. `shouldOverrideUrlLoading` 的内部查词与外部导航阻止行为保持不变。
6. 公共契约、Room schema和功能版本以外的行为不变。

建议将“主文档交给WebView / 受控MDD资源 / 阻止”抽成可在JVM测试的纯策略，客户端的可空返回只负责落实策略，避免只靠没有设备执行的androidTest。

## 必须新增的回归测试

- 主框架 `data:text/html;charset=utf-8;base64,...` → 交给WebView，不返回403。
- 相同 `data:` 作为非主框架请求 → 阻止。
- 外部主框架HTTPS和file/content主框架 → 阻止，不能借主框架条件绕过。
- 精确受控base URL若被允许，只能在主框架允许；受控MDD子资源仍按安全路径读取。
- 现有外部导航、路径穿越、超限资源、CSP和危险WebSettings测试继续通过。

## 汇报与验证

完成后复跑功能2全部JVM测试、Android测试源码编译和AAR；报告测试数、失败数、AAR大小/SHA-256、JNI/APK数量。没有连接设备，不得宣称WebView实机修复通过，只能标记 `ready_for_integration / awaiting_test.002_device_retest`。
