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

- `mdict-java` / `lzo-core` 路线已因 GPL 运行依赖和跨许可包依赖判定失败；禁止引入其整库、POM 依赖或 GPL 范围源码。
- 当前仅允许以 `whistooy/mdict-reader@e25373923035f06156dbfa8aedeb802b5167e6df`（MIT）和 `encounter/lzokay-rs@c762f2522d0d19ca6e4b6b8ca7ba51b512dc93b0`（MIT）为固定参考，开发项目自有纯 Kotlin/JVM 解析内核。
- 引用或改写宽松许可实现时必须保留来源、固定提交、许可证文本和文件级对应关系；在依赖审计与两套词典兼容门槛通过前不得进入页面、数据库或导入功能开发。
- 未经总控另行批准，不得引入 Rust、NDK、JNI、Cargo 依赖或新的解析器候选。
- 禁止复制 `Gdict` 或任何 GPL/AGPL UI、解析器或 Android App 源码。
- 词典 HTML 默认关闭 JavaScript、文件访问、内容访问和 JS Bridge；不得自动加载外部页面。
- 许可证或兼容性门槛失败时停止实现并向总控汇报，不得擅自更换技术路线。

## 完成与汇报

- 新行为必须有单元测试或仪器测试；复跑模块全部测试、AndroidTest 源码编译和 AAR 构建。
- 更新本目录 `CHANGELOG.md`、`STATUS.md` 和 `README.md`。
- 版本完成、数据库或公共契约变化、测试成功/失败、遇到阻塞或需要 APK 时，必须按 `MANAGER_LINK.md` 主动汇报。
- 跨对话发送失败时把完整报告写入 `MANAGER_OUTBOX.md`，不得宣称已完成总控交接。
- 若误读未授权路径，立即停止扩展读取并报告精确路径和影响。
