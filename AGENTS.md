# Codex EnglishApp 总控维护规则

## 对话类型

- `manager`：总纲对话，独占 Git 提交、合并、标签、App 版本、签名、APK 和正式发版权限。
- `app-feature`：由用户明确指定的 App 功能对话，只维护自己的功能模块。
- `skill`：由用户自行管理，不进入本仓库、不进入 App、总控不读取其内部文件。
- `pending-user-classification`：新对话的默认状态。只有用户可以改变类型；总控不得自行判断。

类型与任务 ID 以 `_manager/THREAD_REGISTRY.md` 为准。

## 功能对话修改前必读

1. 读取根目录 `PROJECT.md`。
2. 只进入本次目标功能目录，读取其中的 `AGENTS.md`、`FEATURE_BASELINE.md`、`STATUS.md` 和 `CHANGELOG.md`。
3. 不得读取其他功能目录、`_manager` 或 Skill 目录；只有总控消息明确列出的单个文件例外。
4. 不得读取全局或项目外的 memory、历史摘要、其他对话缓存；需要上下文时只能向总控汇报并请求精确授权路径。

## 权限边界

- 功能对话不得执行 Git 提交、合并、标签或历史改写。
- 功能对话不得修改根 App 壳、全局 Gradle 配置、版本号、签名、发布记录或生成 APK。
- 功能模块必须使用 Android Library 插件，不得声明 launcher Activity 或依赖其他功能内部实现。
- 未经用户明确许可，不得删除、替换或弱化已经写入功能基线的行为。
- 数据库只允许显式、可测试的向前迁移；禁止 destructive migration、删库重建和清空用户历史。
- 用户文档、个人数据库、签名密钥、APK、恢复包和构建缓存不得提交 Git。

## 总控完成标准

- 读取功能汇报，检查差异并复跑目标模块原有测试。
- 运行 `_manager/tools/verify-boundaries.ps1`，确认全项目只有一个 Application 模块。
- 从干净提交构建签名 APK，保存文件名、大小、SHA-256、证书指纹和验收边界。
- 自动测试、设备测试和用户确认分开记录；没有真实证据不得提高验收级别。
- 正式变更由总控提交和打标签，确保可以比较与回退。
