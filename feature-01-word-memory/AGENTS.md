# 功能 1：语境记忆维护规则

## 每次修改前

- 必读 `FEATURE_BASELINE.md`、`PLAN.md`、`STATUS.md` 和 `CHANGELOG.md`。
- 只读取和修改本目录；不得读取 `short-story-generator` 或其他功能的内部文件。
- 读取 `MANAGER_LINK.md`，确认本次总控授权和汇报目标。
- 先确认功能基线和模块测试状态，再开始变更；不得检查或修改 Git 历史。

## 不得破坏的功能

- 一个 Word 文件导入为一个本地笔记本。
- 按正文段落读取 DOCX，加粗的“编号. 内容”是学习目标。
- 中英文目标按完整编号配对，不按出现顺序猜测。
- 英文常显、中文逐段显隐、目标加粗着色、逐段默写和学习位置持久化。
- 导入必须完整校验后以事务写入；失败或取消不得留下残缺笔记本。
- 笔记本可打开、重命名和删除，用户已有内容不得因升级丢失。

## 数据与兼容性

- Room 数据库只能使用显式、可测试的向前迁移。
- 禁止 `fallbackToDestructiveMigration`、删库重建或改变现有字段语义。
- 新偏好默认值必须兼容旧数据；仅显示设置不得改变笔记本排序时间。

## 交付要求

- 新行为必须补充单元测试或仪器测试，并复跑解析器回归测试。
- 只运行本模块测试和 Android Library 编译；不得构建、签名或复制 APK。
- 不得执行 Git 提交、合并、标签、回退或历史改写。
- 不得修改根 App 壳、全局 Gradle、App 版本、签名或 `_manager` 内的正式发布记录。
- 更新 `CHANGELOG.md`、`STATUS.md` 和 `README.md`，完成后按 `MANAGER_LINK.md` 主动汇报总控。
- 总控消息未明确授权时，不得读取 `_manager`；获准后也只能读取消息列出的单个文件。
- 未经用户明确许可，不得删除本文件或 `FEATURE_BASELINE.md` 中列出的行为。

## 技术边界

- 本目录是 Android Library 功能模块，只对外暴露 `WordMemoryFeature()`。
- 禁止使用 `com.android.application`、`applicationId`、App 版本号、签名配置或 launcher Activity。
- 禁止直接依赖其他功能目录；跨模块协作必须由总控建立公共契约。
- 若跨对话消息发送失败，将完整报告写入 `MANAGER_OUTBOX.md`，并明确标记尚未完成总控交接。
