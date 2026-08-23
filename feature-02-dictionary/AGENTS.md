# 功能 2：词典维护规则

## 每次修改前

- 必读根 `PROJECT.md`、根 `AGENTS.md`，以及本目录的 `FEATURE_BASELINE.md`、`PLAN.md`、`STATUS.md`、`CHANGELOG.md` 和 `MANAGER_LINK.md`。
- 只读取和修改本目录；禁止读取 `feature-01-word-memory`、`short-story-generator` 或其他功能内部文件。
- 禁止读取全局或项目外 memory、历史摘要、其他对话缓存；需要额外上下文时向总控请求精确路径。
- 总控仅额外授权只读使用 `dictionary-users/柯林斯双解学习词典` 和 `dictionary-users/牛津9英英(推荐)`；不得修改、移动、上传或提交其中内容。

## 权限边界

- 本目录必须保持 Android Library，只生成 AAR，不生成 APK。
- 不得执行 Git 提交、合并、标签、回退或历史改写。
- 不得修改根 App 壳、全局 Gradle、App 版本、签名、`_manager` 发布记录或其他功能。
- 不得依赖功能1内部实现；跨模块调用只通过 `PUBLIC_CONTRACT.md` 中的稳定入口。
- 用户导入文件不得被原地修改；删除只能删除 App 私有副本。

## 许可证与安全边界

- `mdict-java` 只能引入固定提交中经文件级审计确认属于 Apache-2.0 核心的源码和宽松许可依赖。
- 禁止复制 `Gdict` 或任何 GPL/AGPL UI、解析器或 Android App 源码。
- 词典 HTML 默认关闭 JavaScript、文件访问、内容访问和 JS Bridge；不得自动加载外部页面。
- 许可证或兼容性门槛失败时停止实现并向总控汇报，不得擅自更换技术路线。

## 完成与汇报

- 新行为必须有单元测试或仪器测试；复跑模块全部测试、AndroidTest 源码编译和 AAR 构建。
- 更新本目录 `CHANGELOG.md`、`STATUS.md` 和 `README.md`。
- 版本完成、数据库或公共契约变化、测试成功/失败、遇到阻塞或需要 APK 时，必须按 `MANAGER_LINK.md` 主动汇报。
- 跨对话发送失败时把完整报告写入 `MANAGER_OUTBOX.md`，不得宣称已完成总控交接。
- 若误读未授权路径，立即停止扩展读取并报告精确路径和影响。
