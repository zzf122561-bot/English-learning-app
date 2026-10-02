# 变更记录

## 0.1.0 开发中

- 初始化隔离语音验证模块；目标为 Qwen3-TTS 英文离线段落合成与手机性能/听感测试。
- 阶段1B：纳入固定 Qwen/GGML CPU 源子集和许可证；新增全部生成参数的类型化 JNI、精确模型路径约束、requested/effective 快照、单 session 生命周期和原生合作取消补丁。
- 新增 arm64/API31 原生 Release Library 构建、参数单元测试、JNI 测试源码和无模型原生契约测试入口；新增 AAR ELF/notice 检查工具。运行证据与限制见 SOURCE_MANIFEST.md 第7节，完整 UI 尚未实现。
