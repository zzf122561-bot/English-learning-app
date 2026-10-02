# 阶段 1：来源、兼容性与构建门槛

审计日期：2026-10-02（Asia/Shanghai）。范围仅本 Library。派工基线为 `951766dedceb3557ff8eddb389ef5826d229dfb9`，由总控提供，本任务没有执行 Git 操作。

**结论：`blocked`，静态来源/格式核验完成，尚不能报 `compatibility_gate_passed`。** 保留 Danmoreng 主候选；未发现需要改换模型家族或切换 ServeurpersoCom 引擎的格式证据。阻塞是原生工具链尚待总控补齐，以及完整 JNI 参数、批量取消、原生加载/合成尚无执行证据。不得据此进入完整 UI 实现或宣称手机可用。

| 核验层级 | 本轮结果 | 证据边界 |
|---|---|---|
| 公开来源与许可声明 | 已固定源码和依赖；核对 MIT / Apache-2.0 声明 | 尚未把第三方源码或模型纳入交付 |
| 实际 GGUF 结构 | 指定 1.7B CustomVoice Q4 的 404 个 talker 张量名称/形状匹配；两文件头部解析通过 | 不含完整权重校验、原生加载或数值正确性 |
| 音色与能力 | Ryan / Aiden / 英语 ID、指令和 C++ 采样调用链有源码依据 | 原 JNI 未完整暴露参数；不是听感结果 |
| 原生 Android 构建 | 未运行 | 沿用总控“SDK 尚无 NDK/CMake”的明确检查，不重复脚手架验证 |
| 模型推理、取消、设备、听感 | 未运行 | 无 Android 设备、本地模型或手机速度证据 |

## 1. 固定源码及依赖

采用提交固定的 codeload ZIP，只解压审计，未执行其脚本、未创建嵌套 Git 仓库。原始文件位于本模块被忽略的 `vendor-downloads/`。

| 用途 | 仓库及精确提交 | 许可证 / 处理 |
|---|---|---|
| 主候选引擎 | [Danmoreng/qwen3-tts.cpp][engine]，`16bb5afcd06311031c72a8488f8d59660dc2fb46` | 根 `LICENSE` 为 MIT，Copyright 2026 Danmoreng；采用时保留原文和文件版权说明 |
| 上述引擎直接依赖 | [ggml-org/ggml][ggml]，`af97976c7810cdabb1863172f31c432dab767de7` | 引擎 Git tree 的 `ggml` gitlink 与 `.gitmodules` 指向此仓库；MIT，Copyright 2023–2026 The ggml authors |
| Android CMake/JNI 参考 | [Danmoreng/qwen3-tts-android][android]，`35738304b31c425cc08fb5405372fedfd6531b3b` | MIT；仅参考构建接线，不复制其 Application/Activity、下载器或 UI |
| 官方模型语义参考 | [QwenLM/Qwen3-TTS][official]，`022e286b98fbec7e1e916cb940cdf532cd9f488e` | 根 `LICENSE` 为 Apache-2.0；Python 实现仅作语义参考，不成为 Android 运行依赖 |

下载 ZIP 的 SHA-256（对应本次收到的归档字节，不是 Git commit hash）：

| 源码 | 字节数 | 归档 SHA-256 |
|---|---:|---|
| qwen3-tts.cpp | 2,497,732 | `18e3effcee521f696109049f1f589d2d5a77130944baa45028c5111c54f90627` |
| ggml | 4,600,830 | `da59dcf57123c06638aa01f9c6ae12ce80cb22e5e48ffb3aca032c8bcc3c0530` |
| qwen3-tts-android | 643,074 | `b8bf2711aa37c8ca404d40a50e1cc217eb73e8ab5269860c3eede719214593bb` |
| Qwen3-TTS | 7,205,699 | `0f2732485bce635f419f142a57d4309415fd79397e7c47edeaf0f8215a4dbb76` |

**参考工程版本差异不能忽略：** Android 参考仓库自己的 gitlink 是引擎 `4562731dc612cb87b4cd1eedac275a17d1078773`，该引擎又固定 GGML `eced84c86f8b012c752c016f7fe789adea168e1e`。本方案选用上表较新的引擎/GGML，因此参考工程的历史构建结果不能覆盖本组合。两条 gitlink 链已通过公开 GitHub tree API 核对，记录在 `vendor-downloads/engine-tree.json`、`android-tree.json`、`android-engine-tree.json`。

首轮 CPU 运行依赖限定为 C++17、上述 GGML、NDK libc++、Android 系统线程/日志库。显式关闭 CUDA、Vulkan、OpenCL、BLAS、Accelerate、OpenMP、KleidiAI、llamafile、动态后端及示例/测试目标；不从构建中自动下载可选依赖。GGML 的 KleidiAI 路径含 FetchContent，本轮关闭。GGML 内 llamafile 的 MIT 文件版权属于 Mozilla，虽未启用也不能在以后复制时剥离。未采用 GPL 组件；未因主路线格式问题触发备用引擎比较。

### 许可与 notice 的交付处理

- 两个 Danmoreng 仓库与 GGML 根 LICENSE 已实读；官方代码根 LICENSE 已实读。下载树中没有单独的根 NOTICE 文件。后续裁剪源码时仍逐项保留实际采用文件的版权头与附带 notice，不能只复制项目名称。
- GGUF 发布仓库的 model card / API 声明 `apache-2.0`；两个官方模型的 model card 也声明 `apache-2.0`。所查模型文件清单中没有独立 LICENSE 文件，不能写成“已下载模型仓库 LICENSE”。官方代码仓库提供 Apache-2.0 全文，模型声明证据必须另行随包留存。
- 如总控决定分发模型，随包保留模型仓库/revision、许可证全文、作者归属、量化转换说明和发布方已有 notice；修改过的源码注明修改。未引入未知许可预编译 `.so`。
- 发布方没有在本次取得的元数据中给出转换时所用官方模型的精确源 revision。下表官方 revision 是本次核对语义的快照，不能宣称就是该 GGUF 的转换输入版本。

## 2. 模型文件锁定

GGUF 仓库：[Serveurperso/Qwen3-TTS-GGUF][models]；revision **`b7ee2e8c7459c3bea99da23e3d178125a7d1713c`**。尺寸和 SHA-256 来自该 revision 的 [公开 blobs API][model-api] 的 LFS 元数据；不是本机对完整文件计算的结果。总控实际取得文件后必须重新计算完整 SHA-256。

| 模型文件 | 用途 | 大小（bytes） | 发布方 LFS SHA-256 |
|---|---|---:|---|
| `qwen-talker-1.7b-customvoice-Q4_K_M.gguf` | 首选 talker | 1,182,631,296 | `cc328834a631bc08bf9f43e62fa23f8a1383d9b429864ce6690cfb172077fc4a` |
| `qwen-tokenizer-12hz-Q4_K_M.gguf` | 首选 codec / vocoder | 254,974,752 | `cf3788b4d50aaa665fb6e57c170396aae03a3555fea52d2b5d0cda902d658039` |
| `qwen-talker-1.7b-customvoice-Q8_0.gguf` | 可选 Q8 对照，未解析头部 | 2,042,834,304 | `cab2cff67a0a557310febe558dc83076b28ed790e491867eb2751759f4cd89fa` |
| `qwen-tokenizer-12hz-Q8_0.gguf` | 可选 Q8 codec 对照，未解析头部 | 291,150,624 | `1883beeed99348fc35e23dd225e9082f93f6f8c109330a33d935baa8acdbfd94` |
| `qwen-talker-0.6b-customvoice-Q4_K_M.gguf` | 可选小模型对照，未解析头部 | 604,878,080 | `b3a7e6613d80f8a703c06267fc1e94d48ce91932ab82ab6e31c50f4ca4868e1e` |

固定下载地址形式为 `https://huggingface.co/Serveurperso/Qwen3-TTS-GGUF/resolve/b7ee2e8c7459c3bea99da23e3d178125a7d1713c/<文件名>`，不得使用浮动 `main`。首选两文件共 **1,437,606,048 bytes**；这不是内存需求估算。Q8 / 0.6B 只有文件清单证据，不能与首选项标成同等已验证，更不能自动降级选择。

官方语义快照：

- [Qwen/Qwen3-TTS-12Hz-1.7B-CustomVoice][official-model]：`0c0e3051f131929182e2c023b9537f8b1c68adfe`。
- [Qwen/Qwen3-TTS-Tokenizer-12Hz][official-codec]：`7dd38ad4e9bad454aae9cd937d0cd577604fe229`。

官方 `config.json` 明确 `tts_model_size=1b7`、`tts_model_type=custom_voice`；命名音色用途由官方配置、官方 `generate_custom_voice` 和真实 GGUF 决定。Base 用于克隆、CustomVoice 用于预置音色，不能采信 GGUF model card 中反转的用途表。本轮不引入克隆、录音或 VoiceDesign。

## 3. 实际 GGUF 与加载器的对应证据

只以 HTTP Range 取得文件开头片段，要求响应为 206 且有长度上限，未下载完整模型。片段超过头部的少量字节不用于分析。

| 项目 | talker Q4 | tokenizer Q4 |
|---|---:|---:|
| 读取片段字节数 | 8,388,608 | 262,144 |
| GGUF 版本 / KV 数 / 张量数 | 3 / 65 / 404 | 3 / 51 / 398 |
| 张量数据起始偏移 | 5,949,824 | 29,760 |
| 张量实际类型 | F32 × 137、Q4_K × 250、Q6_K × 17 | F32 × 238、F16 × 50、Q4_K × 110 |
| 片段 SHA-256 | `64d47657205bd8f02e220eb9a1cc76aba6e26f5184b20c10f5161e9359c7b080` | `ebf0099d941287b09f18908da00698bb706761b6fa19e3ad3b62dac5073149a6` |

对应固定引擎中的实读代码：

- `src/transformer/transformer_loader_metadata.cpp:91` 起接受 `qwen3-tts.talker.*`、`qwen3-tts.code_pred.*`。实际 talker hidden=2048、28 层，code predictor hidden=1024、5 层，16 codebooks，符合 1.7B 分支；不是仅凭文件名推断。
- 同文件 `:315` 起接受 `qwen3-tts.codec.speaker_names/speaker_ids` 数组。本次数组与官方配置 9 个 ID 全匹配，其中 **ryan=3061、aiden=2861、english=2050**。
- `src/transformer/transformer_loader_tensors.cpp` 所要求的 404 个张量名称/维度静态比对全匹配，包括 1.7B 必需的 `code_pred.mtp_proj.weight` `[2048,1024]`，28/5 层及 15 组 code predictor embedding/head。
- `src/transformer/transformer_embeddings.cpp:704` 从 codec embedding 取命名 speaker 行，量化 embedding 可走 GGML get_rows 路径。`src/pipeline/pipeline_synthesize.cpp:629` 把命名 speaker 送入合成；不是仅有列表展示。
- `src/decoder/decoder_loader.cpp:173` 接受实际 tokenizer 的 `output_sample_rate=24000`、16 quantizers、2048 codebook size 和 `tok_dec.*` 命名。实际含 253 个 decoder、145 个 encoder 张量，当前语音生成只需要 decoder。29 个会转为 F16 的卷积张量原始类型均在加载器允许集合中，未落入“不支持 Q4 转 F16”的错误分支。
- GGML `include/ggml.h` 将 Q4_K/Q6_K 定义为 type 12/14，与实际文件一致。`Q4_K_M` 是混合量化策略，不能称每一个张量都是 4-bit。此发布物的 `general.file_type` 是字符串；本引擎加载路径不依赖其为整数。
- `src/pipeline/pipeline_models.cpp` 默认探测偏向 0.6B、codec 偏向更高精度。适配器必须使用隔离模型目录，调用 C++ `load_models(dir, exactTalkerName, exactTokenizerPath)`，预先验证精确白名单/hash，禁止沿用默认探测或缺失时降级。

证据可复现入口（Python 标准库，无第三方安装）：

```powershell
python experiments/tts-device-probe/tools/inspect-gguf-header.py experiments/tts-device-probe/vendor-downloads/qwen-talker-1.7b-customvoice-Q4_K_M.gguf.header.bin --full-file-bytes 1182631296 --out experiments/tts-device-probe/vendor-downloads/talker-header.json
python experiments/tts-device-probe/tools/inspect-gguf-header.py experiments/tts-device-probe/vendor-downloads/qwen-tokenizer-12hz-Q4_K_M.gguf.header.bin --full-file-bytes 254974752 --out experiments/tts-device-probe/vendor-downloads/tokenizer-header.json
python experiments/tts-device-probe/tools/audit-qwen3-format.py
```

本轮三个命令退出码均为 0。结果 `vendor-downloads/format-check.json`：`passed=true`、404 个 talker 张量形状、8 项附加检查通过。前两个工具只解析完整头部，不验证权重内容；后一个只比较固定格式，不替代完整原生 decoder 加载、推理数值或音质验收。原始模型 API、官方配置与 SDK XML 一并保存在忽略目录；本文件保留交接需要的关键结论，不要求总控重复运行已有静态检查。

## 4. 真实 API 能力与必须补齐的接线

| 配置/能力 | 固定上游代码中的实际情况 | 本模块后续实现要求 |
|---|---|---|
| 英语 / Ryan / Aiden | 官方支持列表与 GGUF 一致；C++、C ABI、JNI 有对应路径 | 固定英语 2050；音色由该模型 capability 与实际 speaker 列表共同约束，默认 Ryan |
| 语气指令 | 1.7B `custom_voice`、hidden≥2048 时 capability 开启；指令经过 tokenizer 进入 transformer | 空串为默认；不将指令效果或逐字服从当成已验证。官方 0.6B CustomVoice 不支持 instruct |
| temperature / top-k / top-p / repetition penalty | C++ `tts_params` 和 C ABI 有字段，生成路径确实使用；现有 JNI 将它们固定为 0.9 / 50 / 1.0 / 1.05 | 自建完整类型化 JNI 桥接，不能直接复制参考 UI 或把字段仅保存在 Kotlin |
| max tokens | C++/C ABI 支持；Android 参考 NativeParams 有 maxAudioTokens；引擎仓库 common Kotlin wrapper 较旧、没有该字段 | 采用本模块自己的 DTO 与 JNI 对应关系；含义是音频帧上限，不是文本词数，触顶提示可能截断 |
| seed | C++ `int64_t seed` 经生成和采样使用；C ABI 和现有 JNI 缺失 | 桥接直接调用 C++，记录实际生效 seed；接线验证前不可显示为可用 |
| CPU 线程数 | `tts_params.n_threads` 虽被 C ABI 赋值，pipeline 未消费此字段；有效入口是 `set_cpu_thread_count` / `qwen3_tts_set_cpu_threads` | 在受串行保护的加载/运行前调用实际全局 setter，回读生效数值；不能只写 params |
| 取消 | 流式 PCM 回调返回 false 经 frame callback 中止；批量进度回调返回 void，批量路径没有 cancel API；load / prefill / decode 也缺统一取消令牌 | 在本模块最小补丁中接入原子取消令牌、帧/加载循环检查与 CPU backend abort；退出后才能销毁句柄。现状不满足完整停止要求 |
| 首音频指标 | C++ `t_ttfa_ms` 是首个 codec frame，不是首 PCM；默认 JNI 只返回总时间 | 首轮批量路径 `firstPcmLatency=null`；若另启流式路径，必须真实 PCM 回调计时并区分运行模式 |
| GPU / NPU / 合成语速 | 有其他上游实验后端，不构成本设备证据；没有专门的合成速度参数 | 当前仅 CPU。播放器速度单独记录；不提供伪造的 GPU/NPU/模型语速开关 |

取消并非已证明“不可实现”，而是**上游公开接口不足、补丁尚未编译和验证**。GGML 固定版本提供 `ggml_backend_cpu_set_abort_callback`（`include/ggml-cpu.h:136`）；这提供接入点，不提供手机停止延迟保证。应覆盖 load、prefill、生成、decode、重复运行后的状态清理；禁止在 native 仍运行时 free、卸载/删模型，或仅取消 Coroutine 就宣称计算停止。不得以自动改用分段流式合成替代默认完整段落批量模式。

## 5. 给总控的固定工具版本与 16 KB 策略

建议总控安装到已授权 SDK：**`ndk;28.2.13676358`（r28c）和 `cmake;3.22.1`**。两者均已在 [Google 官方 SDK repository2-3.xml][sdk-index] 核对 Windows 包条目（`android-ndk-r28c-windows.zip`、`cmake-3.22.1-windows.zip`）；本任务未下载/安装工具。

这些是本方案的可复现选型，不是声称上游只支持这两个版本。依据：[NDK r28c 官方发布][ndk] 默认启用 16 KiB，toolchain 要求 CMake≥3.10；Android 参考 CMake 要求≥3.22；GGML 要求≥3.14。3.22.1 满足本方案独立 Android CMake 接线。不要直接使用引擎根 CMake：它向 Release 注入 `-march=native`、包含 CLI/tests，JNI `find_package` 也不同于 Android 参考接线。

后续构建固定：arm64-v8a、API 31、C++17、NDK r28c、CMake 3.22.1、Release；AGP 9.3.0 / Kotlin 2.4.10 / JDK17 / Gradle9.5.0 沿用总控独立宿主，不改根工程。模块维护独立 CMake 源文件集合，GGML 静态链接进一个模块 JNI `.so`，libc++ 使用同 NDK 的 `c++_shared`。`GGML_NATIVE=OFF`，首轮 CPU 架构固定为 `armv8-a`；更高指令集/优化只有在相同测试条件下单列比较后再启用。

16 KB 计划依据 [Android 官方页面][pages16k]：

1. 使用 r28c 默认 flexible page sizes，显式保持 `ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`；本模块 JNI 最终链接指定 `-Wl,-z,max-page-size=16384` 与 `-Wl,-z,common-page-size=16384`。全部 native 依赖从固定源重新构建，不混入未经检查的 prebuilt。
2. 当前读取的模型加载器用 fseek/fread；GGML CPU 的物理内存查询使用 sysconf 页大小。后续补丁不得引入 4096/PAGE_SIZE 的系统页假设；GGUF 文件内 32-byte 对齐与操作系统页大小是不同概念。
3. 功能任务在原生构建后用同 NDK 的 `llvm-objdump -p` / `llvm-readelf -Wl` 检查 JNI 与 libc++ 的 LOAD 对齐≥2**14、RELRO 页边界。尚无二进制，故本轮不能报对齐通过。
4. 总控负责最终 APK 内全部 `.so` 及 ZIP 包装检查：AGP≥8.5.1、`zipalign -c -P 16 -v 4 <APK>`。最终包检查不能用 AAR 结果代替。
5. 真实 16 KB 环境验证要记录 `adb shell getconf PAGE_SIZE=16384` 并运行加载/合成/停止；Android16 或处理器型号本身不证明页大小。本轮无该证据。

## 6. 解除门槛所需最小动作

总控补齐上述 NDK/CMake，并下发“继续阶段1原生适配/验证”的明确范围；本任务随后只在本模块落地必要源/许可证、完整 JNI 参数和取消补丁，编译 Library native 目标并记录 ELF 结果。总控提供按本清单完整校验过的模型的精确只读路径/内置交付方式后，再做真实模型加载与合成验证；此次未读取私有样例正文或原 DOCX。

完整 UI 仍等待门槛后的实施授权。没有设备时，只能记录原生构建/主机验证，手机速度、内存峰值、断网运行、停止延迟、漏读/重复、热降频及听感继续留给实机。报告统一 RTF=合成秒数/音频秒数；不得复制上游 README 中以倒数表达的速度表，亦不采用其桌面 CUDA 速度作为手机结论。

[engine]: https://github.com/Danmoreng/qwen3-tts.cpp/tree/16bb5afcd06311031c72a8488f8d59660dc2fb46
[ggml]: https://github.com/ggml-org/ggml/tree/af97976c7810cdabb1863172f31c432dab767de7
[android]: https://github.com/Danmoreng/qwen3-tts-android/tree/35738304b31c425cc08fb5405372fedfd6531b3b
[official]: https://github.com/QwenLM/Qwen3-TTS/tree/022e286b98fbec7e1e916cb940cdf532cd9f488e
[models]: https://huggingface.co/Serveurperso/Qwen3-TTS-GGUF/tree/b7ee2e8c7459c3bea99da23e3d178125a7d1713c
[model-api]: https://huggingface.co/api/models/Serveurperso/Qwen3-TTS-GGUF/revision/b7ee2e8c7459c3bea99da23e3d178125a7d1713c?blobs=true
[official-model]: https://huggingface.co/Qwen/Qwen3-TTS-12Hz-1.7B-CustomVoice/tree/0c0e3051f131929182e2c023b9537f8b1c68adfe
[official-codec]: https://huggingface.co/Qwen/Qwen3-TTS-Tokenizer-12Hz/tree/7dd38ad4e9bad454aae9cd937d0cd577604fe229
[sdk-index]: https://dl.google.com/android/repository/repository2-3.xml
[ndk]: https://github.com/android/ndk/releases/tag/r28c
[pages16k]: https://developer.android.com/guide/practices/page-sizes
