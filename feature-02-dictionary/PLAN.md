# 功能 2：词典开发计划

## 里程碑 1A：原解析路线审计（已失败）

- `mdict-java` 固定提交的 POM 依赖 GPL-3.0 `lzo-core`，核心查询类还引用许可证范围外的实现；该路线已经停止，禁止继续接入。
- 已建立但尚未接入具体实现的项目自有 `MdictEngine` 接口。

## 里程碑 1B：纯 Kotlin/JVM 解析内核门槛

- 仅审计和参考固定 MIT 源：`whistooy/mdict-reader@e25373923035f06156dbfa8aedeb802b5167e6df` 与 `encounter/lzokay-rs@c762f2522d0d19ca6e4b6b8ca7ba51b512dc93b0`。
- 建立第三方来源、许可证、文件对应关系和未使用代码清单；不得复制 GPL/AGPL 项目源码。
- 使用 `RandomAccessFile` / `FileChannel` 和按块解压实现项目自有纯 Kotlin/JVM 引擎；不得整文件读入内存，不引入 Rust、NDK 或 JNI。
- 用两套授权只读词典验证 MDX 2.0、Encrypted=2 / RIPEMD128、精确查询、前缀查询、MDD CSS、图片和资源随机读取。
- 许可证、构建、内存边界或任一本词典兼容性失败即停止并向总控报告；不得自行切换第三条路线。

## 里程碑 2：模块功能

- 建立独立 Room 数据库、内置词典登记、启停与排序。
- 实现完整查询页、快速查询模式、安全 HTML 渲染和资源拦截。
- 实现 MDD 原声、HTTPS 原声和 Android TTS 回退。
- 使用 Storage Access Framework 导入并原子复制 MDX/MDD；支持删除导入词典私有副本。

## 里程碑 3：可集成验收

- 覆盖损坏文件、缺失资源、重复导入、原子回滚、内置不可删除、排序和发音回退测试。
- 编译 Android 仪器测试源码并生成 AAR，确认本目录没有 APK。
- 更新文档并按固定格式向总控提交 `ready_for_integration` 汇报。

根 App 底部导航、功能1长按回调、私有词典资产打包、App 版本、签名和 APK 均由总控另行完成。
