# 功能2 test.001 实机反馈

日期：2026-08-24

状态：`confirmed_device_failure`

证据来源：用户本人提供的Android实机截图。截图不复制进Git；本文件只保存可审计事实。

## 已观察事实

- App 已运行并进入底部“词典”入口。
- 搜索框内容为 `another place`，页面标题同步显示该查询。
- Collins 与 Oxford 两个结果标签均已显示，说明内置词典登记、查询协调和结果选择至少已运行到正文展示之前。
- 选中词典的正文区域由系统WebView错误页替代：
  - 地址：`data:text/html;charset=utf-8;base64,`
  - 错误：`net::ERR_HTTP_RESPONSE_CODE_FAILURE`
- 用户没有在本次消息中确认覆盖安装方式、旧数据、功能1、返回导航、管理、导入、图片或发音；这些项目不得提高验收级别。

## 总控增量诊断

`SecureDictionaryWebView` 使用 `loadDataWithBaseURL()` 加载应用生成的词条文档。当前 `SecureDictionaryWebViewClient.shouldInterceptRequest()` 对任何不能解析为 `https://dictionary.local/<安全资源路径>` 的请求直接返回403。

该设备把主文档请求呈现为 `data:text/html;charset=utf-8;base64,...`，因此主文档也落入403分支，WebView显示 `ERR_HTTP_RESPONSE_CODE_FAILURE`。这是总控依据截图与当前源码得到的直接因果诊断；修复后的真实显示仍需用户用test.002复测。

## 候选处置

- v0.3.0 test.001 判定用户验收不通过，永久保留原APK和哈希，不覆盖、不删除。
- 不创建 `app-v0.3.0` 标签。
- 功能2修复目标为v0.1.1；App仍为v0.3.0，下一产物序号为test.002。
