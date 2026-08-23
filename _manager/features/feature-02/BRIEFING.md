# 功能2总控开发简报

更新时间：2026-08-24

## 身份与权限

- 功能 ID：`feature-02-dictionary`；目标版本：`0.1.0`。
- 本任务只维护 `feature-02-dictionary` Android Library 和模块测试。
- 禁止读取 `feature-01-word-memory`、`short-story-generator` 或其他功能内部文件。
- 禁止 Git 提交、合并、标签、App 版本、签名、APK 和根 App 壳修改。
- 允许只读使用本 worktree 中以下兼容测试资料：
  - `dictionary-users/柯林斯双解学习词典`
  - `dictionary-users/牛津9英英(推荐)`
- 不得编辑、移动、重命名、上传或提交上述词典文件。

## 固定产品决策

- 两个现有词典随 APK 内置；原始文件由总控在集成阶段打包，功能模块不得自行复制进源码。
- 完整词典页由 App 底部“词典”入口打开。
- 功能1长按英文单词通过公共回调进入 `QUICK_LOOKUP`；按一次 Android 返回键回到功能1原页面。
- 发音顺序：MDD 内置原声 → 词条 HTTPS 原声 → Android 英语 TTS。
- 内置词典只能启停和排序，不能删除；用户后续导入的词典可以删除 App 私有副本。
- 首版不做全文检索、模糊纠错、收藏历史、云同步、在线词典下载、OCR 或系统级悬浮查词。

## 技术决策

- 解析器必须封装在项目自有 `MdictEngine` 接口后，随机访问、按需解压，不整文件载入内存。
- 第一里程碑审计 `mdict-java` 固定提交，仅允许引入上游明确声明 Apache-2.0 的 `com.knziha.plod.dictionary.*` 核心和已确认宽松许可依赖；其余 GPL 源码禁止引入。
- `Gdict` 只能作为行为参考，禁止复制其 GPL 源码。
- 若许可证、依赖或现有两套词典兼容性任一不通过，停止该里程碑并汇报总控，不得自行换库。
- 使用独立 Room 数据库 `englishapp_dictionary.db`；禁止访问功能1数据库。
- MDX/MDD 导入使用 Storage Access Framework，原子复制到 App 私有目录；匹配同名 `.mdd`、`.1.mdd`、`.2.mdd` 等资源文件。
- HTML 采用受控本地域名和资源拦截；默认关闭 JavaScript、文件访问、内容访问与 JS Bridge。

## 里程碑与汇报

1. 完成解析内核许可证审计，并用两套现有词典验证精确查询、前缀查询、加密索引、CSS 和图片资源。
2. 完成查询、HTML 展示、发音回退、词典管理、SAF 导入和独立数据库。
3. 完成模块回归、AAR 构建和边界自检，向总控提交 `ready_for_integration` 汇报。

每个里程碑均按 `_manager/REPORTING_PROTOCOL.md` 主动向总控报告；跨对话发送失败时写入 `MANAGER_OUTBOX.md`。
