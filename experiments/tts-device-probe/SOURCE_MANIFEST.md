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

## 7. 阶段1B追加：最小原生适配和 Android 编译证据

2026-10-02，依据 MANAGER_LINK.md 的阶段1B续派实施。前六节保留首轮静态审计的历史状态；本节记录新增差异。404个talker张量/8项格式检查直接沿用，未重复执行。

**当前结论：Android 原生 Library 构建及 ELF 检查通过；总体仍为 `blocked / phase1b_android_build_passed_runtime_pending`。** 没有主机或设备上的实际模型推理、取消执行证据，不能报告 `compatibility_gate_passed`，完整UI没有实施。

### 固定源及本地差异

- `tools/vendor-native.py` 从此前固定归档离线选取134个源/头/CMake/许可证文件，记录于 `vendor/UPSTREAM_FILES.json`，保留每个上游文件的 SHA-256。未执行上游脚本。35个引擎实现文件加 GGML CPU；未采用上游 CLI/JNI、下载器或其他后端实现。
- 8个上游文件的本地改动完整保存在 `vendor/LOCAL_CHANGES.patch`，SHA-256：`08f2da86a52f4225435d80eab2a0d15935bd377705f2293e38c69faf7aea0af1`。`tools/write-vendor-patch.py` 用原始文件hash核对后生成差异，不调用 Git。内容为取消/阶段检查、分块读、token触顶标记及取消GGML构建时的Git探测；固定源码身份不变。
- 自有 `src/main/cpp/probe_engine.*`、`probe_jni.cpp` 及 Kotlin `engines/qwen3tts/QwenNativeBindings.kt` 实现参数和生命周期；模块 CMake 明确列出源码、CPU及禁用后端，未改总控宿主。
- AAR 的 `classes.jar!/META-INF/model-probe/` 包含 Qwen、GGML原始MIT全文、署名 NOTICE，以及 NDK 自带 LLVM工具链原始 NOTICE（涵盖libc++/libc++abi及LLVM例外）。文件版权头保留。未包含模型权重，模型许可/转换输入revision限制仍按第1–2节表述。

### 实际接线和取消约束

Kotlin/JNI/C++ 类型逐项传递英语ID、音色、instruction、线程数、temperature、top-k、top-p、repetition penalty、max audio tokens、seed。Kotlin与C++分别验证范围和有限数；随机请求 `seed=-1` 在适配器内解析为显式非负seed。真实调用 CPU setter/getter，随后再调用上游 batch synthesize；禁止自动挑选其他模型文件。requested与effective分开记录，尚未进入推理的取消或失败不产生effective参数快照。token数量达到上限时保留“可能截断”，不声称完整读完。

本轮通过 `javap -s` 核对 Kotlin 参数/结果构造签名和9个native方法名，与JNI定义一致；最终 `.so` 中9个JNI导出均存在。这是字节码/链接接线证据。C++非默认参数映射测试、线程setter/CPU graph abort测试入口已编译，但尚未执行；不能据此声称参数的模型效果或运行时线程数已验收。

排他 registry 只允许一个session，句柄不暴露裸指针；单调操作ID防过期取消。执行锁覆盖加载、推理和释放，close先设置取消并等待执行锁，再销毁引擎；backend abort回调只读取进程寿命的原子量，不保存Java/请求对象地址。失败/取消不返回PCM并卸载引擎，重新使用需load。原生安全退出的运行证据仍待设备测试，以上不是无竞态的实测结论。

可中断点覆盖：load开始/张量循环/1MiB分块fread；batch tokenize前/预填充前；每个生成frame；decode前，以及这些阶段使用的实际GGML CPU backend abort。GGUF元数据解析、单次系统I/O、内存分配、张量转换和单个CPU计算节点未变为可抢占操作，停止响应不保证即时；取消后释放和返回也可能耗时。默认仍为完整段落batch，没有以分句或流式规避取消要求。首PCM延迟仍不可测，没有输出伪造的首音频指标。

当前最小JNI只校验精确文件名/同目录/大小/加载后能力，完整SHA256与ModelLease尚未实现。因此只接受总控完成完整hash校验并保持不变的授权文件用于本轮验证，尚不是面向用户的模型导入功能。

### 本次执行结果

| 验证 | 结果与范围 |
|---|---|
| `:runtime:testDebugUnitTest` | 3项通过，0失败/错误/跳过；非默认参数、非法范围/NaN/Infinity、Unicode指令长度。纯Kotlin，不加载native/model |
| `:runtime:compileDebugAndroidTestKotlin` | 通过；预取消、过期操作ID、绕过Kotlin后的native非法参数测试仅编译 |
| `:runtime:assembleRelease` | 通过；NDK28.2.13676358 / CMake3.22.1 / arm64-v8a / API31 / C++17；CMake缓存为Release，实际编译flags含 `-O3 -DNDEBUG` |
| `cmake --build … --target probe_native_contract_test --parallel 2` | 通过；AArch64 Android PIE、解释器 `/system/bin/linker64`，不是Windows可执行文件；未运行 |
| `tools/inspect-native-artifact.py` | 最终AAR仅含两份预期arm64 `.so`；ELF/RELRO布局及4份notice字节检查通过；`runtime_or_apk_validation=false` |
| `llvm-readelf` / `llvm-nm` / `javap` | ABI、程序段、依赖及JNI方法/构造签名核对通过；无Activity/Application/launcher，AAR manifest只有minSdk31 |

主构建使用已授权独立宿主：`gradle.bat -p _manager/probes/tts-device --no-daemon --no-configuration-cache --max-workers=2 :runtime:testDebugUnitTest :runtime:compileDebugAndroidTestKotlin :runtime:assembleRelease`。可复用入口 `tools/test-module.ps1` 已改为生成Release AAR。后来仅补入LLVM notice，增量执行assembleRelease，不重复无关测试。

初次构建因沙箱不能写Gradle native DLL lock而未启动；经工具批准访问既有缓存后运行。首轮C++编译缺少取消声明的include，补上后完整构建成功。单独CMake/Ninja在沙箱内无进展，中止本次会话后经批准用相同现有工具编译成功；未安装工具、未改变代理或凭据。JNI标准UTF转换使用C++17仍支持的codecvt/wstring_convert，编译器报告4条弃用警告；没有以屏蔽警告或改编码语义处理。

持久证据入口：`build/test-results/testDebugUnitTest/TEST-com.xuesui.englishapp.modelprobe.engines.qwen3tts.QwenParametersTest.xml`、`build/reports/tests/testDebugUnitTest/index.html`、`.local/artifact-check/inspection.json`；构建目录均忽略，不提交模型、产物或缓存。

### 产物与 ELF 结果

| 产物 | 字节数 | SHA-256 |
|---|---:|---|
| `build/outputs/aar/runtime-release.aar` | 1,394,913 | `47e4a65fc275092c4cd0a5d0a78c23f0277e2391f210d9ffe1122c4d3dd28d96` |
| AAR `libmodel_probe_qwen.so` | 2,302,096 | `d4fcb62d35e1754cea5c32af6f1f1aaed74630705d552f9d575df0c2a80e825c` |
| AAR `libc++_shared.so` | 1,253,544 | `cd61762848882a16c8244c964a6f396c0caa0b440588a210ce9cc4ab0e6d9f0c` |
| `build/intermediates/cxx/Release/39684l4y/obj/arm64-v8a/probe_native_contract_test` | 18,115,936 | `f87b5012c340ff664a4a11f15c421431708fa9972c37c745d54878e5ecf669c8` |

两份AAR `.so` 的全部LOAD段 `p_align=0x4000`，地址与文件offset在16KiB上同余；与[Android官方ELF检查规则][pages16k]一致。JNI的RELRO结束于16KiB边界；NDK libc++的原始RELRO结束于 `0x13a000`，取整后的保护结束为 `0x13c000`，下一可写LOAD起于 `0x13d430`，没有误覆盖可写段。初版检查把“RELRO末端本身必须16KiB对齐”当作硬条件，经核对[AOSP固定源码 `_phdr_table_set_gnu_relro_prot`](https://android.googlesource.com/platform/bionic/+/0c0f92ca86264980d28c327e208a205af6e016a9/linker/linker_phdr.cpp#1352)的page_start/page_end规则后修正检查；没有改动NDK二进制。这是静态布局推断，不等同于设备加载。

原生测试入口也为16KiB LOAD对齐，但没有打入AAR或APK。总控如在已授权ARM设备执行无模型契约测试，需把该可执行文件和同NDK的 `libc++_shared.so` 放在专用可执行测试目录，用该目录的LD_LIBRARY_PATH运行。JNI的Android测试源码还需由总控测试宿主执行。本任务未执行adb、未生成或安装APK。

### 精确剩余缺口与交接

在本轮发现范围内，PATH上没有可用的cl/clang++/g++主机C++工具链，标准vswhere路径也不存在；没有继续扫描其他项目或安装编译器。NDK里的clang用于Android交叉编译，不能把已生成ARM ELF当作Windows主机运行结果。因此本轮无主机短句WAV、时长或取消后恢复证据。

截至交接未收到总控 `MODEL_PAIR_VERIFIED` 通知；没有读取所授权目录的两个完整模型，也没有读取.partial或重新下载。即使文件到位，也仍需主机工具链或ARM设备执行环境。

总控最小后续动作：接收本Library差异与已有编译证据并登记本地提交；在完整模型hash确认后，安排原生契约/JNI测试及1.7B英语Ryan/Aiden短句实际加载与合成，记录effective参数、WAV真实样本数/时长、load/prefill/generate/decode各阶段取消以及取消后重新load+run。设备上的16KiB页大小、断网、停止延迟、内存/温度/热降频、听感，以及最终APK ZIP对齐仍未覆盖。无需重跑本节已通过且未变化的模块检查。后续UI范围等待总控下发。

## 8. 阶段2追加：设备验证候选

2026-10-03，依据 MANAGER_LINK.md 最后一节授权，完成独立 Library 的公共基础、Qwen 适配器和中文测试页面。**当前状态为 `ready_for_device_validation / awaiting_manager_apk`；不是 `compatibility_gate_passed`。** 第1–7节保留原轮次历史；本节取代其中对“完整UI尚未实施”“未收到模型通知”的当前状态描述，不改写旧证据。

### 接收的条件与实现范围

已接收总控 `MODEL_PAIR_VERIFIED`：第2节首选两文件的完整大小/SHA-256已由总控验证，模型 revision 不变。直接沿用该收据，本轮没有重新读取或计算管理端两份完整模型哈希，没有读取私有DOCX、私有样例正文或.partial。NDK/CMake安装结果与阶段1B实际编译证据也直接沿用，没有重新安装工具。

本轮仅修改 `experiments/tts-device-probe/`：新增 core/model、core/runtime、core/metrics、core/storage、contracts/speech、Qwen Kotlin 适配器/参数编辑器、features/speech、runtime/ProbeViewModel，替换原占位入口并增加模块依赖和测试。实际职责、默认值、设置schema、事务存储、任务退出和测量语义统一见 [ARCHITECTURE.md](ARCHITECTURE.md)。本轮没有修改 C++、JNI、vendor源、CMake或原生补丁；适配器版本为 `16bb5afc-ggml-af97976c-patch-08f2da86-adapter2`。

中文页面按操作/设置/结果组织，连接模型安装/全量校验/租约、加载/卸载、JNI无模型契约自检、文本编辑/恢复、真实参数、完整段落/逐句、冷启动/暖运行/有限批次/显式缓存、取消、播放/变速、历史和SAF WAV/JSON导出。模型替换或删除保留历史，缓存清理仅移除索引。跨控制器租约和活动草稿保护覆盖旧页面退出尚未完成时的新页面初始化。

同一暖运行批次只解析一次随机seed，使各次条件相同；失败、取消、音频帧触顶停止余下批次。requested/effective分别保存，包括原生失败调用实际返回的effective；未返回则为null。语音模式、批次数、播放速度与全部生成参数一起保存/恢复。以上是实现及模拟测试覆盖，尚无真实设备交互或模型执行证据。

### 总控宿主的资产契约

宿主仍调用同一个 `ModelProbeFeature()`；本轮不新增宿主权限、Activity、Application、Service或正式App依赖。MediaPlayer、PSS、热状态、电池温度与SAF使用现有平台API；不申请联网、录音、广泛存储或唤醒锁权限。

模块新增依赖为已有catalog中的 `androidx.compose.foundation`、`androidx.activity.compose`、`androidx.lifecycle.runtime.compose`；仅JVM测试增加 `org.json:json:20250517`，Android运行时使用平台org.json。根catalog/全局Gradle未改。运行要求沿用 arm64-v8a、API31及现有Compose宿主。

总控需要注入以下资产。文件名与字节身份都是固定契约，解析器不扫描目录或猜测替代文件：

- `assets/probe-sample.txt`：总控处理后的指定68词5句英文段落，正文保持原样；本模块不复制私有正文。读取上限65536 bytes，空白或含汉字拒绝，缺失显示原因并允许手动输入，不自动造样例。
- `assets/models/qwen-talker-1.7b-customvoice-Q4_K_M.gguf` 与 `assets/models/qwen-tokenizer-12hz-Q4_K_M.gguf`：第2节的精确配对。
- `assets/models/manifest.json`：schemaVersion数值1，当前只有一个受信任注册项。最小完整内容如下；sizeBytes必须是JSON整数，sha256为表中小写字符串。

```json
{
  "schemaVersion": 1,
  "models": [{
    "modelId": "qwen3-tts-1.7b-customvoice-q4",
    "engineId": "qwen3tts-cpu",
    "taskId": "speech.synthesis.v1",
    "revision": "b7ee2e8c7459c3bea99da23e3d178125a7d1713c",
    "license": "Apache-2.0",
    "files": [{
      "role": "talker",
      "name": "qwen-talker-1.7b-customvoice-Q4_K_M.gguf",
      "assetPath": "models/qwen-talker-1.7b-customvoice-Q4_K_M.gguf",
      "sizeBytes": 1182631296,
      "sha256": "cc328834a631bc08bf9f43e62fa23f8a1383d9b429864ce6690cfb172077fc4a"
    }, {
      "role": "tokenizer",
      "name": "qwen-tokenizer-12hz-Q4_K_M.gguf",
      "assetPath": "models/qwen-tokenizer-12hz-Q4_K_M.gguf",
      "sizeBytes": 254974752,
      "sha256": "cf3788b4d50aaa665fb6e57c170396aae03a3555fea52d2b5d0cda902d658039"
    }]
  }]
}
```

未知modelId、身份/revision不一致、角色重复/缺失或文件路径/大小/hash不符均拒绝。内置清单通过只说明声明匹配；设备首次安装仍须对新私有副本做完整字节校验。SAF调试导入也只接受相同两个hash，不能替代宿主内嵌交付。合计1,437,606,048 bytes；安装私有副本另占相同空间，并要求64MiB余量，重装还要保留旧版直至原子切换成功。

### 本轮验证与可复用证据

最后一次受影响范围构建于北京时间2026-10-03完成，`BUILD SUCCESSFUL in 1m29s`，74 tasks（24 executed、50 up-to-date）。测试XML时间为2026-10-02T16:57:57–16:58:00Z。本次有意只运行stage2新增测试，未重复阶段1B三项参数测试、404项张量静态比对、JNI符号/构造签名审计或无变化的原生契约编译。

```powershell
$env:JAVA_HOME = 'D:\CodexData\android-toolchain\jdk-17'
$env:ANDROID_HOME = 'D:\CodexData\android-toolchain\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_OPTS = '-Xmx3g -Dfile.encoding=UTF-8 -Djdk.net.hosts.file=C:/Users/zzf86/.codex/worktrees/5551/Codex_EnglishApp/_manager/tools/gradle-hosts.txt'
& 'D:\CodexData\android-toolchain\gradle\gradle-9.5.0\bin\gradle.bat' `
  -p '_manager/probes/tts-device' --no-daemon --no-configuration-cache --max-workers=2 `
  :runtime:testDebugUnitTest --tests 'com.xuesui.englishapp.modelprobe.stage2.*' `
  :runtime:compileDebugAndroidTestKotlin :runtime:assembleRelease :runtime:lintDebug
python 'experiments/tts-device-probe/tools/inspect-native-artifact.py'
```

以上在本worktree根运行；Gradle使用经工具批准的现有缓存访问，未生成APK。新增测试均用合成小文件、fake引擎与可控时钟，不含私有资料或大模型。

| 验证 | 结果与覆盖 |
|---|---|
| ModelStoreTest | 7项通过：精确清单、配对完整校验/租约、失败保留旧安装、取消/空间不足、损坏/重启复验、受控残留清理、跨控制器租约 |
| StorageSettingsTest | 7项通过：原子结果/音频hash、损坏不命中缓存、清缓存及删模型保留历史、参数保存与版本变化、非法编辑值、活动草稿保护、页面偏好保存/范围 |
| SpeechServiceTest | 9项通过：真实WAV头/样本/RTF计算、cold/warm/cache分流、完整缓存身份、取消无PCM及重新加载、逐句顺序/seed、触顶停止、不杜撰样例、同条件随机seed批次、失败native有效参数快照 |
| TaskRunnerTest | 2项通过：取消保持busy直至真实退出、清理排在退出后、回调登记前取消 |
| `compileDebugAndroidTestKotlin` | 通过；既有JNI契约和新增合成小文件私有存储/原子安装/租约测试仅编译，未在Android执行 |
| `assembleRelease` | 通过；最终Release AAR含新页面/核心/适配器，无模型权重；未构建APK |
| `lintDebug` | 完成，0 errors、6 warnings，说明见下表 |
| 最终AAR检查 | 两份预期arm64 `.so`与阶段1B逐字节一致；16KiB ELF布局、RELRO边界及4份notice字节匹配通过；不替代APK ZIP或设备加载 |

新增JVM合计 **25项，0失败、0错误、0跳过**。明细在 `build/test-results/testDebugUnitTest/TEST-com.xuesui.englishapp.modelprobe.stage2.*.xml`，汇总在 `build/reports/tests/testDebugUnitTest/index.html`。Lint报告在 `build/reports/lint-results-debug.html` / `.sarif`。产物检查在 `.local/artifact-check/inspection.json`。这些目录是本地忽略产物，测试过滤会重写当前测试报告目录，阶段1B结论仍以第7节当时记录为准。

| Lint警告 | 数量 | 本轮处理与边界 |
|---|---:|---|
| GradleDependency | 1 | 建议compileSdk37；沿用总控既定36，不扩展宿主/工具升级范围 |
| NewerVersionAvailable | 1 | org.json有更新；本次测试固定20250517并通过，不为无关升级重跑 |
| ChromeOsAbiSupport | 1 | 本候选明确仅arm64，不添加未经验证的x86_64 |
| UsableSpace | 2 | 当前采用保守usableSpace + 64MiB检查；没有用可回收其他缓存空间扩大可用容量 |
| UseTomlInstead | 1 | 新org.json只属于此Library的JVM测试；根catalog不在写权限内 |

定时测量/阶段提示改用固定延迟，避免Android固定频率定时器积压后密集回补；ViewModel显式持有Application上下文。相关Lint问题已修复，无警告屏蔽。SAF提供方、音频焦点、页面布局/输入法、Activity重建等平台交互尚未执行，不能由JVM或Lint通过推定。

### 最终产物

| 产物 | 字节数 | SHA-256 |
|---|---:|---|
| `build/outputs/aar/runtime-release.aar` | 1,630,998 | `8c5df2adfa20345a31c64189177f18740c09065ec6f81e398b4efb6d4a101544` |
| AAR `libmodel_probe_qwen.so` | 2,302,096 | `d4fcb62d35e1754cea5c32af6f1f1aaed74630705d552f9d575df0c2a80e825c` |
| AAR `libc++_shared.so` | 1,253,544 | `cd61762848882a16c8244c964a6f396c0caa0b440588a210ce9cc4ab0e6d9f0c` |

AAR哈希变化来自阶段2Kotlin/Compose内容；原生源、补丁及两份最终SO身份不变，沿用第7节JNI和ELF深入分析。最终AAR manifest只有库package与minSdk31；四份notice仍在classes.jar的META-INF/model-probe内。APK中的模型大小/hash、所有SO、ZIP16KiB对齐、签名及包可安装性属于总控新增包装验证，不能以本表替代。

### 待总控安排的设备验证

1. 从总控登记的干净提交注入精确资产、制作独立候选APK，验证包内模型/notice、签名与ZIP16KiB对齐。记录目标设备与实际PAGE_SIZE，不从Android版本猜测16KiB。Library产物不能直接安装。
2. 执行已有原生契约入口/JNI instrumentation和新增Android存储测试；页面“JNI无模型契约自检”可作为便捷入口，但不覆盖运行中CPU取消。首次安装验证约1.44GB私有副本，覆盖资产缺失、错误hash、空间不足、取消、重新打开及重装失败保留旧版。
3. 用1.7B首选模型实际加载并合成英语Ryan/Aiden短句，再使用总控指定段落；检查instruction、线程回读、非默认参数、requested/effective、token触顶与WAV样本数/时长。必须依据实际返回记录，不能把本轮fake引擎WAV当模型音频。
4. 在load、prefill、generate、decode阶段分别取消，记录请求到原生退出的延迟和结果；取消后重新load/run，覆盖连续执行与释放。旋转不应启动重复任务，退出/后台请求停止，旧实例退出期间不能被新实例删除模型或草稿。无法退出或native崩溃须回报，不提高验收级别。
5. 比较完整段落与逐句、cold/warm/batch/cache；确认同一批次seed一致、逐句派生seed可追溯、缓存不计推理RTF、播放速度不改变WAV。保存/重启/恢复默认应覆盖全部参数和页面偏好。
6. 验证小屏滚动/输入法/状态反馈、播放/重播/焦点、切后台、SAF导出及取消/提供方失败；WAV/JSON应绑定所选历史，删模型/清缓存后历史仍可播放导出。记录当前私有结果长期积累的空间使用；本候选没有自动删除历史。
7. 断网实际合成，观察采样PSS、真实采样间隔、电池温度、Android热等级与连续运行速度变化，用户听查漏读、重复、截断和自然度。首PCM仍不可测，不编造首声指标。native成功、设备流程、断网和听感分开验收。

本轮没有主机/手机native执行、完整模型加载或合成、真实短句WAV、Android测试执行、UI视觉运行、APK、断网或听感证据。唯一宿主公共入口未变；无数据库、正式App基线或全局构建变化。总控接收本轮报告后负责Git、宿主资产/打包与设备验证；已有明确模块证据不重复审查或复跑。本功能任务交接后进入 `awaiting_manager_apk` 等待状态。

## 9. 2026-10-03 设备截图与用户试听反馈

证据来自本功能对话中用户提供的三张截图及随后直接反馈。三图展示同一个结果ID，并非三次成功运行。截图文件名为 `8d1a541cac4ff8f39612ca344982338b.jpg`、`14b7bdd1de3b4b9c92d7b4637b1a0839.jpg`、`23d4e8bb065ae23caffdc420b11cb062.jpg`；未复制图片或私有正文进入仓库。本节只追加本次新证据，第8节的交接时点记录保持不变。

| 项目 | 截图所示 / 用户反馈 |
|---|---|
| 结果ID | d7a7b157-0733-4d30-8e6b-28e841dbc59d |
| 页面状态 / 运行 | 合成完成；cold；paragraph |
| 音频 | 817,920个样本，24,000Hz，34.08秒；样本数/采样率与显示时长一致 |
| 原生加载 | 2,252ms |
| RTF | 4.71，生成+解码/音频时长；由两位小数显示值估算生成+解码约160.5秒，不能作为精确原始耗时 |
| 采样内存 | 峰值PSS 4,071.27MiB，约3.98GiB；是进程采样峰值，不是模型权重大小或瞬时绝对峰值 |
| 采样 | 567次，间隔目标200ms；尚无本次原始JSON的真实间隔/最大gap，不能用次数×目标间隔反推总耗时 |
| 热状态 / 温度 | Android正常→中度，末次电池38.80°C；不能据此认定已发生CPU降频或把电池温度当CPU温度 |
| 参数 | Ryan、英语、4线程、seed42、temperature0.9、top-k50、top-p1.0、repetition penalty1.05、最大1024帧、无instruction；截图中请求配置与生效片段1一致 |
| 模型 / 引擎 | qwen3-tts-1.7b-customvoice-q4；16bb5afc-ggml-af97976c-patch-08f2da86-adapter2 |
| 用户试听 | 先反馈“听着不错”；随后明确选择“完整且清晰，没有明显漏读或重复” |

当前可确认的是：用户手机端报告完成一次1.7B Q4、Ryan、4线程的冷启动完整段落合成，并正面确认这一条音频的完整度和清晰度。该证据已超出仅编译阶段；不再将当前状态写为“尚无任何手机合成/试听证据”。本任务没有亲自运行设备、读取实际WAV或原始JSON；截图未提供设备型号、实际PAGE_SIZE、APK版本/hash、文本hash，也不证明其他音色、断网、各阶段取消、长期稳定性或全部参数组合通过。因此仍不标记 compatibility_gate_passed 或正式功能验收。

性能判断：当前生成34.08秒音频约需160.5秒，尚未达到实时速度。2.252秒原生加载相比生成+解码很小，仅维持模型驻留不能消除主要等待时间；具体瓶颈在生成还是解码，需要现成JSON的分段计时。热状态变化是进一步比较时需要控制的条件，暂不据单次样本认定因果。保留这条用户认可的1.7B音质基线，不擅自降低模型规格、改采样参数或开启未验证后端。

用户已明确要求与管理员商量后续推进。本轮仅更新证据/状态并向登记总控提出收敛方案：先沿用总控已有包/设备证据；取得本次JSON后判断生成/解码占比，少量补暖运行、断网及取消后恢复的缺口；如需性能对照，固定样例/seed/模型，控制温度后一次只改线程条件。正式接入、是否采用提前生成/缓存，以及0.6B/GPU/NPU/其他引擎研究由总控结合用途另行决定并下发范围。本轮不改代码、不复跑构建或既有测试。

## 10. 正式语音0.2.0内嵌候选

2026-10-03，用户要求与管理员商量后，总控转发用户正式批准“接入原英语App，独立测试App也保留”，并给出精确实施简报及宿主接线只读文件（授权记录在MANAGER_LINK.md）。方案已对齐为保留1.7B Q4音质、提前生成、串行分段、保存/缓存与重复播放，不把新的性能实验作为实现前置。本节是0.2.0模块交付证据，不能把第9节的0.1候选单次试听提升为全部新代码实机验收。

### 差异、契约和宿主条件

新增同包 `com.xuesui.englishapp.modelprobe.runtime` 的公开 `SpeechReadingScope`、`SpeechReadingRequest` 与 `SpeechFeature(request: SpeechReadingRequest? = null, onClose: () -> Unit = {})`，字段/可空默认值与总控简报完全一致；原 `ModelProbeFeature()` 保留。最终AAR内两入口/请求类型存在，javap核对新增公开入口及request各getter/构造签名通过。除这些公开契约外，新增业务类型保持internal。

- `runtime/SpeechReadingRequest.kt`、`SpeechFeature.kt`：正式公共入口及宿主请求映射，只通过onClose返回，不访问文章模块、数据库或导航。
- `features/reading/ReadingPlan.kt`、`ReadingQueue.kt`、`ReadingUiState.kt`、`ReadingScreen.kt`：范围提取、受限原文切片、串行队列、整篇/子片段历史、请求去重/停止后切换，以及正式界面。
- `features/speech/SpeechPlayback.kt`、`runtime/AndroidAudioFactory.kt`、`RuntimeLifecycle.kt`：可测试播放状态机、真实平台播放器、暂停/继续/倍速/焦点与退出绑定；两入口复用。
- `runtime/ProbeViewModel.kt`：共享装配；正式request=null与文章入口同Activity往返分别保留草稿，重复请求不触发推理；正式页不读取probe-sample.txt，空白输入可用。
- `contracts/speech/SpeechContracts.kt`、`SpeechService.kt`、`core/storage/RunStore.kt`：可选reading来源元数据、精确匹配的缓存复用、流式PCM16合并和在分组后截取历史。原诊断cold/warm/cache语义保留。
- `engines/qwen3tts/QwenSettings.kt`：Ryan/Aiden、定性生成风格与可编辑个性化instruction，完整高级参数保留。没有新增引擎/量化、精确native速度开关、联网/克隆或GPU/NPU。
- 模块build.gradle.kts、新增reading JVM测试与ReadingRuntimeDeviceTest，以及本模块既有文档。没有编辑正式宿主、其他功能、根配置、C++/JNI/vendor/CMake源；没有Git或APK操作。

完整运行/分层/存储语义集中在 [ARCHITECTURE.md](ARCHITECTURE.md) 的正式朗读与播放部分，不在此复制实现细节。正式范围最多256段/100000字符，最多256片，每片最多800个code point；全文保持原文顺序和范围标识。失败、取消、触顶不继续后续片段，已完成部分独立保存，合并WAV明确标记全部或部分完成。已提交子音频在进程中断而父报告尚未提交时仍可找回；长队列不会用上百个子记录挤掉整篇历史。

新增公开契约由总控按已约定brief接入；本任务仅只读了明确授权的EnglishAppShell.kt，以补覆盖两个入口往返。没有读取或修改功能1/2。正式宿主无需probe-sample.txt，只需第8节固定manifest和配对模型；独立测试宿主继续使用样例。两个App各用自己的私有目录，不互读或自动搬运模型/音频。

### API29与原生兼容方案

Library minSdk由31降至29，NDK实际重新编译/链接的目标同时为API29，没有使用tools:overrideLibrary或要求提高正式App最低版本。CMake本次目录 `.cxx/Release/362z5g3x/arm64-v8a/CMakeCache.txt` 中 `ANDROID_PLATFORM=android-29`、`CMAKE_SYSTEM_VERSION=29`；最终AAR manifest为minSdk29，没有Activity/Application/uses-permission。

现有平台API经minSdk29下的Lint校验，没有NewApi错误。Android [getCurrentThermalStatus](https://developer.android.com/reference/android/os/PowerManager#getCurrentThermalStatus()) 从API29提供；[MediaPlayer.setPlaybackParams](https://developer.android.com/reference/android/media/MediaPlayer#setPlaybackParams(android.media.PlaybackParams)) 从API23提供，准备完成后设置非零速度可能启动播放，因此实现仅在播放/明确继续时调用，暂停状态只保存新倍速。pitch显式设1，并回读speed；不支持的设置报错/释放，不虚报已经生效。API29安装和实际播放/加载仍需总控设备或模拟环境确认，编译/链接通过不是设备执行证据。

原生源、模型、取消补丁与Qwen参数schema未改，仍是固定CPU/armv8-a；因目标API变化，JNI二进制hash/段布局变化，需要用本次AAR。引擎源码/适配器身份保持adapter2，不无故丢弃已存参数；本轮未安装工具或重复审计上游来源。ABI仅arm64，非支持设备入口不会调用生成。原生C++17 UTF转换的4条已知弃用警告沿用记录，没有屏蔽警告或改编码语义。

### 增量验证

本轮首批完成20项新增JVM测试与9项受影响SpeechService测试；随后发现长文历史的父子分组/进程中断可见性缺口，仅补改该处并执行对应8项队列测试及1项原历史事务检查。**合计31个不同测试用例通过（21项新增、10项受影响已有），0失败/错误/跳过；第二批包含对第一批7项队列测试的必要回归，不把重复次数算成新用例。** 没有重跑无变化的来源/许可、404项张量、原3项参数审计或其他功能测试。

| 测试范围 | 不同用例数 | 结果与覆盖 |
|---|---:|---|
| ReadingPlanTest | 8 | 全文/段/句边界、长文有界且不丢/乱原文、Unicode不截断surrogate、非法输入、公共请求字段、同ID/连续新ID、同Activity草稿切换 |
| ReadingQueueTest | 8 | 串行顺序/来源ID/实际样本合并、取消后不启动下一片及恢复、失败/触顶非全成功、同内容新请求缓存、音色/自然语言指令接线、随机seed派生、损坏合并失败、父子历史与中断片段可见性 |
| SpeechPlaybackTest | 5 | 暂停/原位置继续/重播、prepare期间暂停、退出后的迟到回调隔离、焦点中断/不支持倍速、速度边界/完成释放 |
| 阶段2 SpeechServiceTest | 9 | 本轮新增preferCache/reading元数据/WAV合并涉及的原服务链回归；cold/warm/cache、参数身份、RTF/样本、取消/截断和effective快照保持 |
| 原StorageSettingsTest指定用例 | 1 | history筛选调整后，只有原子提交结果进入原诊断历史的行为保持 |

以下沿用第8节JDK/SDK/GRADLE_OPTS，仅列本次任务参数；均在本worktree根，以已授权既有工具与缓存执行：

```powershell
# 首批：29项，BUILD SUCCESSFUL in 1m57s，74 tasks（38 executed / 36 up-to-date）
& 'D:\CodexData\android-toolchain\gradle\gradle-9.5.0\bin\gradle.bat' `
  -p '_manager/probes/tts-device' --no-daemon --no-configuration-cache --max-workers=2 `
  :runtime:testDebugUnitTest --tests 'com.xuesui.englishapp.modelprobe.reading.*' `
  --tests 'com.xuesui.englishapp.modelprobe.stage2.SpeechServiceTest' `
  :runtime:compileDebugAndroidTestKotlin :runtime:assembleRelease :runtime:lintDebug
# 最终历史差异：9项，BUILD SUCCESSFUL in 1m35s，74 tasks（24 executed / 50 up-to-date）
& 'D:\CodexData\android-toolchain\gradle\gradle-9.5.0\bin\gradle.bat' `
  -p '_manager/probes/tts-device' --no-daemon --no-configuration-cache --max-workers=2 `
  :runtime:testDebugUnitTest --tests 'com.xuesui.englishapp.modelprobe.reading.ReadingQueueTest' `
  --tests 'com.xuesui.englishapp.modelprobe.stage2.StorageSettingsTest.onlyAtomicallyCommittedAudioReportPairsBecomeHistory' `
  :runtime:compileDebugAndroidTestKotlin :runtime:assembleRelease :runtime:lintDebug
python 'experiments/tts-device-probe/tools/inspect-native-artifact.py'
```

当前 `build/test-results/testDebugUnitTest/` 与 `build/reports/tests/testDebugUnitTest/index.html` 是最后过滤执行的9项结果（XML时间2026-10-03T02:42:41–02:42:47Z）。首批29项结果已从当轮XML/构建输出读出并记于上表；过滤执行会覆盖报告，未为生成一份合并报告重复跑31项。

Android测试源码编译通过，新增 `runtime/ReadingRuntimeDeviceTest.kt` 使用合成小文件和独立cache子目录，覆盖正式页无样例、同Activity入口/重复请求无推理副作用及真实MediaPlayer准备/暂停/继续/重播测试入口。它与已有JNI/私有模型存储测试本轮都只编译，未执行；没有新增手机或API29运行通过结论。

Release AAR构建、API29原生编译/链接、最终ELF与notice检查通过。Lint完成：0 errors、6 warnings，仍是第8节记录的compileSdk更新建议、org.json更新/catalog、arm64-only和2处保守usableSpace提示；没有新增API兼容问题。报告为 `build/reports/lint-results-debug.html` / `.sarif`，产物检查为 `.local/artifact-check/inspection.json`。新增页面未做Android视觉或触摸验收。

### 最终0.2.0产物与宿主交接

| 产物 | 字节数 | SHA-256 |
|---|---:|---|
| `build/outputs/aar/runtime-release.aar` | 1,814,831 | `09ba4e90f95c1a4e2890cc6ea206d23eb697c1f3ccbf12f8f9e7c409b065b438` |
| AAR `libmodel_probe_qwen.so`（API29） | 2,302,032 | `1e90f7d682ef31332a89c70e831f857efeb2dcd7cb23aa96f374dc3b389c1c29` |
| AAR `libc++_shared.so` | 1,253,544 | `cd61762848882a16c8244c964a6f396c0caa0b440588a210ce9cc4ab0e6d9f0c` |

最终只有预期两份arm64 SO，LOAD为16KiB对齐，RELRO取整不覆盖其他可写LOAD；四份notice与源文件字节匹配。JNI段布局随API29链接变化，本表取代第8节产物作为当前候选；源许可、模型revision/哈希、原生补丁不变。未包含权重、样例、APK、签名或其他功能代码。

数据库迁移：无。设置schema2与原目录保持；单片报告schema1新增可选reading字段，父报告以新taskId标识，旧历史兼容，无清库/删历史。新增公开契约是总控已约定的正式接口，没有临时改名或额外宿主参数；没有新增依赖/权限超出第8节模块依赖。

总控接收后：登记本地提交，正式speech-runtime别名与文章回调接线、双宿主构建、注入固定资产、APK包内hash/ZIP16KiB/签名/安装由总控完成。需补的新增设备范围是文章全文/段/句及返回保留学习状态、同Activity独立页往返、队列取消/重开与部分历史、暂停/继续/焦点/倍速、个性化实际效果、长文截断反馈、SAF导出和API29兼容；断网/持续热状态等旧缺口继续保留。总控已有包或设备证据直接沿用，仅对新接线补验证。本任务状态为 `ready_for_integration / awaiting_manager_integration`，不是App发布完成或全部设备验收。

## 11. 2026-10-05 阅读页内嵌0.3.0候选

### 授权、接口与差异

来源为总控已持久化的 `D:/Codex_Project/Codex_EnglishApp/_manager/features/feature-03-speech/BRIEFING.md` 2026-10-05修订节及同日180dp补充。总控通知0.2.0已提交0f18a3b并集成；本任务未执行Git，以本worktree的模块0.3.0增量交接。模型/原生/许可/源提交不变，原充分证据沿用；仅修改experiments/tts-device-probe，没有读取功能1/2实现、全局memory、其他会话缓存或生成APK。

新增公开接口严格对应总控约定，包仍为com.xuesui.englishapp.modelprobe.runtime：

```kotlin
@Composable
fun SpeechInlineReadingFeature(documentId: String, request: SpeechReadingRequest, modifier: Modifier = Modifier)
@Composable
fun SpeechSettingsFeature(modifier: Modifier = Modifier)
```

SpeechReadingRequest/SpeechReadingScope字段、SpeechFeature(request,onClose)、ModelProbeFeature()保留兼容。最终AAR的classes.jar经javap核对包含上述两个新入口、两个旧入口及原请求构造/字段；默认Modifier在Compose编译后的默认参数掩码中保留。没有新增依赖/权限、Activity或Application，minSdk继续29，ABI继续arm64-v8a。

新增/受影响主要源路径（生产代码以下述短路径加本模块src/main/java/com/xuesui/englishapp/modelprobe前缀定位；测试路径相对本模块）：

- `runtime/SpeechInlineReadingFeature.kt`、`SpeechSettingsFeature.kt`：正式文章底部入口和仅设置入口；旧SpeechFeature改为调用相同Activity作用域装配函数。
- `runtime/ProbeViewModel.kt`、`RuntimeLifecycle.kt`：共享运行器/设置、范围切换、旧结果匹配、播放/导出租约、确认删除后的排队与回收。
- `features/reading/ReadingTarget.kt`、`InlineReadingState.kt`、`ReadingQueue.kt`：稳定文章/范围身份、正文与设置校验、直接复用子片段、来源记录与全部项目管理。
- `core/storage/RunStore.kt`、`features/speech/SpeechPlayback.kt`、`contracts/speech/SpeechContracts.kt`：发布/回收索引、实际文件删除、父子引用、持有到实际释放的文件租约和可证明归属的中断子片段。
- `src/test/.../reading/ReadingRetentionTest.kt`新增20项JVM用例；`src/androidTest/.../runtime/InlineRuntimeDeviceTest.kt`新增4项Android测试源码。模块Gradle/原生/CMake/vendor未编辑。

当前实现与存储规则唯一正文见 [ARCHITECTURE.md](ARCHITECTURE.md) 的0.3.0及最新结果事务部分。新增results/catalog.json schema1及报告可选字段，不迁移文章或用户数据库；原设置schema2、报告schema1和旧目录兼容。无documentId的旧项目不按标题/hash猜配，保留且可确认手删。新范围只在新完整音频验证、目录提交及latest原子切换后回收旧版；失败/取消/截断不替换旧成功结果。共享子WAV和活动播放/导出/生成受到引用保护；父完整WAV与子缓存可双存以支持连续播放/单文件导出及分片复用，重复生成后旧的无引用文件实际回收。未操作开发机真实用户音频/文章/导出文件。

### 增量验证结果

最终受影响JVM批次：**46项发现，45项通过，1项跳过，0失败/错误**。XML时间为2026-10-05T15:51:25.856Z至15:51:57.214Z。跳过项是Windows主机无法创建fixture符号链接的专项，使用JUnit Assume退出，不能称该项在Windows实测通过；另提供Android私有目录符号链接用例，仍仅源码编译。

| 范围 | 用例数 | 本轮结果与覆盖 |
|---|---:|---|
| ReadingRetentionTest（新增） | 20 | 19通过、1跳过；跨音色/指令/seed/标题/requestId替换，跨文/跨段隔离，正文与参数错配，句子身份，失败/取消/触顶保留，旧历史及超过100项管理，共享片段/缓存/孤儿回收，提交前后/激活前后/删除中断恢复，模拟索引空间不足，回收延迟不误报生成失败，播放/导出/生成租约，路径穿越拒绝 |
| ReadingQueueTest | 8 | 全通过；新引用/历史分组直接影响原队列链，覆盖旧父记录的来源分组与中断子片段可见性 |
| SpeechPlaybackTest | 5 | 全通过；文件释放接入后原暂停/继续、prepare、迟到回调、焦点及速度行为保持 |
| SpeechServiceTest | 9 | 全通过；存储和可选origin字段变化后的cold/warm/cache、真实参数、RTF/音频、取消/触顶回归 |
| StorageSettingsTest指定4项 | 4 | 全通过；原子提交可见性、文件篡改、清缓存保留历史/模型独立性、活动草稿不被另一控制器清理 |

首轮发现旧历史仅有reading.sessionId、没有items数组时不能正确分组，已修正并由最终ReadingQueueTest覆盖。180dp布局初次编译发现Compose DSL隐式receiver限制，改为捕获有界容器高度后编译通过；这两项已解决，不列为未处理失败。仅因这些差异与存储补充覆盖执行必要回归，没有重跑未变化的词典/文章功能、来源/许可、张量或原参数审计。

最终命令沿用第8/10节已授权工具链及JAVA_HOME/ANDROID_HOME/GRADLE_OPTS，项目根执行：

```powershell
& 'D:\CodexData\android-toolchain\gradle\gradle-9.5.0\bin\gradle.bat' `
  -p '_manager/probes/tts-device' --no-daemon --no-configuration-cache --max-workers=2 `
  :runtime:testDebugUnitTest `
  --tests 'com.xuesui.englishapp.modelprobe.reading.ReadingRetentionTest' `
  --tests 'com.xuesui.englishapp.modelprobe.reading.ReadingQueueTest' `
  --tests 'com.xuesui.englishapp.modelprobe.reading.SpeechPlaybackTest' `
  --tests 'com.xuesui.englishapp.modelprobe.stage2.SpeechServiceTest' `
  --tests 'com.xuesui.englishapp.modelprobe.stage2.StorageSettingsTest.onlyAtomicallyCommittedAudioReportPairsBecomeHistory' `
  --tests 'com.xuesui.englishapp.modelprobe.stage2.StorageSettingsTest.tamperedArtifactsCannotBePlayedExportedOrReusedAsCache' `
  --tests 'com.xuesui.englishapp.modelprobe.stage2.StorageSettingsTest.clearingCachePreservesHistoryAndModelStorageIsIndependent' `
  --tests 'com.xuesui.englishapp.modelprobe.stage2.StorageSettingsTest.reopeningStoreDoesNotCleanAnOlderControllersActiveDraft' `
  :runtime:compileDebugAndroidTestKotlin :runtime:assembleRelease :runtime:lintDebug
python 'experiments/tts-device-probe/tools/inspect-native-artifact.py'
```

最终 **BUILD SUCCESSFUL in 2m41s，74 tasks（27 executed / 47 up-to-date）**。JUnit完整结果在build/test-results/testDebugUnitTest及build/reports/tests/testDebugUnitTest/index.html；这是本次最终46项结果，不是上轮0.2.0的9项报告。Lint为0错误、6项既有警告：GradleDependency、NewerVersionAvailable、ChromeOsAbiSupport、UsableSpace×2、UseTomlInstead。报告在build/reports/lint-results-debug.html及.sarif；没有因本轮新增API或权限问题。

Android新增4项源码编译通过：同VM设置往返/显式点击才推理、忙时换范围等待真实工作线程退出且不自动开始新任务、无效范围清空错配选择、私有目录符号链接清理拒绝。均使用cache子目录、微小模型fixture及模拟引擎，**没有设备执行、真实模型或UI截图证据**。原Android测试源码也保持可编译，不把编译当测试通过。

### 最终产物及交接边界

| 产物 | 字节数 | SHA-256 |
|---|---:|---|
| build/outputs/aar/runtime-release.aar | 1,927,636 | `3990cd23018e91dd7a3324d9a94dd7feefa5e3ad390dff45c84b814d086771dc` |
| AAR libmodel_probe_qwen.so | 2,302,032 | `1e90f7d682ef31332a89c70e831f857efeb2dcd7cb23aa96f374dc3b389c1c29` |
| AAR libc++_shared.so | 1,253,544 | `cd61762848882a16c8244c964a6f396c0caa0b440588a210ce9cc4ab0e6d9f0c` |

inspect-native-artifact.py返回passed=true/runtime_or_apk_validation=false，两份SO字节hash与0.2.0相同，AAR内16KiB LOAD/RELRO与四份notice检查通过；没有重审上游。产物检查正文在.local/artifact-check/inspection.json。AAR manifest仅含minSdk29，没有Activity/Application/uses-permission。

总控集成要点：将文章页底部槽接SpeechInlineReadingFeature，以稳定word-memory:<notebookId>传documentId，requestId按文章/选区稳定；槽在文章存续期保持Compose挂载，放在固定底部而非滚动回收item。语音Tab改接SpeechSettingsFeature。180dp内主要操作用两行布局，详细错误/管理/删除确认使用弹窗；未引入BackHandler或正文滚动接管，实际大字/横屏/返回/边看边听触摸效果仍待设备。相同段内重复句子仅凭sentenceText无法唯一定位时会提示改选段，未私自扩充公共请求参数或读取文章实现。

待设备覆盖：新底部槽180dp及大字体的可达性、文章滚动/Back、设置往返与旋转、切换范围时取消与旧音频隔离、重生成成功/失败后的保留、播放/导出中删除和实际SAF、进程终止/低空间后的恢复、Android符号链接用例。旧断网/持续热状态/API29真机等缺口继续沿用。单次34.08秒“完整且清晰”的听感记录仍只证明第9节那次运行。

本轮为 `ready_for_integration / awaiting_manager_integration`。总控独占接线、Git、App版本/图标/签名/APK；本任务交付模块源与AAR后一次主动汇报并等待，不轮询或自行扩展。

## 12. 2026-10-06 重复单句收尾差异

总控已接收第11节交付并通知源提交05981da及合并完成，随后在同一获授权BRIEFING新增“2026-10-06 收尾差异”。本节仅接续该差异，取代第11节的“重复句要求改选段”限制与当时AAR；原45通过/1跳过、存储/原生/设备边界继续按适用范围沿用，不执行Git或读取功能1。

ReadingTarget对同文章同段重复出现的相同选中文字采用带repeated前缀的精确内容hash作为句子身份，出现次数、标题/requestId或生成参数变化不产生另一保留范围；跨文章/段或不同精确文字仍隔离。唯一句继续使用既有first/last sentence位置身份，原key与定位不变；没有公共签名、设置/索引schema或数据库变更，也不将旧唯一句记录猜配迁入重复句范围。

ReadingPlan仍从原文提取所选文字一次，超过既有上限时只分片这一次选中文字；首个匹配位置仅作为内部提取代表。ReadingItem/ReadingTarget标签为“第N段 · 单句（重复文本）”，进度、父items和新子reading报告均不声称具体第几次出现：occurrence=unspecified，matchingOccurrences记录匹配数，相关sentenceRange/firstSentence/lastSentence/startOffset/endOffset为null。唯一句具体位置保持，原文空值/缺失/越界校验不放宽。缓存源子报告保留原始来源；本次选区的歧义/匹配次数在父报告明确记录。

生产变更仅限 `src/main/java/com/xuesui/englishapp/modelprobe/features/reading/{ReadingTarget,ReadingPlan,ReadingQueue}.kt` 与 `contracts/speech/SpeechContracts.kt` 的受影响定位/报告链；新增 `src/test/java/com/xuesui/englishapp/modelprobe/reading/DuplicateSentenceTest.kt` 6项，调整原ReadingRetentionTest的句子身份用例，并更新现有文档。未修改RunStore、播放器/生命周期、UI布局、原生、模型或构建配置。

验证：**8项通过，0失败/错误/跳过**。其中DuplicateSentenceTest 6项覆盖唯一句旧key/label/offset、重复句身份稳定及精确内容/跨文跨段隔离、服务调用链只向模拟引擎传入一次所选文字、父子元数据不虚报位置、长重复句分片一次、换音色/seed替换且旧唯一句历史保留、缓存来源与当前选区元数据分别准确、非法请求拒绝；另执行原ReadingRetentionTest的句子身份用例1项，以及ReadingQueueTest的完整队列来源/顺序用例1项。最终XML时间2026-10-05T16:12:08.581Z至16:12:12.638Z（北京时间2026-10-06 00:12）。

```powershell
& 'D:\CodexData\android-toolchain\gradle\gradle-9.5.0\bin\gradle.bat' `
  -p '_manager/probes/tts-device' --no-daemon --no-configuration-cache --max-workers=2 `
  :runtime:testDebugUnitTest `
  --tests 'com.xuesui.englishapp.modelprobe.reading.DuplicateSentenceTest' `
  --tests 'com.xuesui.englishapp.modelprobe.reading.ReadingRetentionTest.sentenceSelectionUsesDocumentParagraphAndLogicalSentencePosition' `
  --tests 'com.xuesui.englishapp.modelprobe.reading.ReadingQueueTest.completeQueueSavesEachPartAndCombinedWaveWithCorrectOriginAndOrder' `
  :runtime:compileDebugAndroidTestKotlin :runtime:assembleRelease
```

沿用之前JAVA_HOME/ANDROID_HOME/GRADLE_OPTS。BUILD SUCCESSFUL in 51s，51 tasks（12 executed / 39 up-to-date）；Android测试源码及Release AAR编译通过。`build/test-results/testDebugUnitTest/`及`build/reports/tests/testDebugUnitTest/index.html`现对应上述8项，过滤执行覆盖报告，旧46项结果以第11节已接收证据为准。未复跑其他存储链、原生/许可审计或Lint；第11节Lint的0错误/6既有警告及Windows符号链接专项跳过边界保留，不称本次又执行通过。

更新产物 `build/outputs/aar/runtime-release.aar`：**1,929,016字节**，SHA-256 **`e1c75f1b025d944cd0ae5f37989bcc18391dd2f1a35050673d1c6e8f0867fd3f`**。本轮直接读取新AAR与两个SO成员hash：libmodel_probe_qwen.so为`1e90f7d682ef31332a89c70e831f857efeb2dcd7cb23aa96f374dc3b389c1c29`，libc++_shared.so为`cd61762848882a16c8244c964a6f396c0caa0b440588a210ce9cc4ab0e6d9f0c`，与第11节相同，沿用其ELF证据；没有新模型或原生行为变更。

本次只有合成fixture/JVM与源码/产物编译证据，没有新设备或视觉验收。待总控将本最小差异提交集成、用更新产物打包；首次设备测试补看重复单句标签与一次播放效果，其余缺口沿用第11节。不扩公开契约、不迁移/清理实际用户历史，本任务一次报告后等待。


## 13. 2026-10-08 核心语音0.3.1修复候选与失败证据

本节执行总控2026-10-07持续更新的同一BRIEFING顶部修复节，来源为总控登记49baa81后的本模块增量（功能任务未执行Git）。功能1的1453bdb／总控8379c34按target span/key清理学习编号的公共语义已由总控转达；未读取该功能实现。此次交付状态为ready_for_integration，设备失败尚未闭环；旧第9节用户对34.08秒音频的肯定只用作对照。

### 已提供样本的事实与限制

仅原地只读分析获授权的 `D:/App data/QQ聊天记录/QQ files/reading-7c0fc0f0-d2ea-461c-b0b2-3fe191947e42.json` 与同名WAV；没有扫描QQ目录、复制原件、上传或记录朗读正文。

- JSON SHA-256：`2b501c58f870463f64f145f223099515b2d7767bf7b7c2291482d252a6f66d03`；runId为7c0fc0f0-d2ea-461c-b0b2-3fe191947e42，scope=FULL，plannedCount118／completedCount0，第一片token_limit、reused=false，后117片not_started。第一片为原第0段offset0..561，子runId为fce759fa-55a6-4373-884e-4abcc00add49，textSha256为6760c013891949f49eb31e7713775f56a787e3f50a67a820f1c0993289bcb56f。
- 请求与resolvedBase均Ryan、英语2050、4线程、temp0.9、topK50、topP1、repetition1.05、maxAudioTokens1024、seed42、空instruction；实际存储浮点值是对应Float转Double的精度。engineVersion为16bb5afc-ggml-af97976c-patch-08f2da86-adapter2，modelFingerprint为613f4b1971604bd2044e3fccfab97e954dd9d912e02f7b428907511fff508f3e。
- elapsedMillis311990（约5分12秒）；WAV3,932,204字节，SHA-256 `3976c2219c4f218407075b681e8fe871e33d9ade6a66e650406e91d81b9e21c2`，与JSON artifact相同。24kHz单声道PCM16，1,966,080样本，81.92秒，正好1024×1920样本。只能确认输出长度与预算吻合，不能推出加大上限能恢复正确内容。
- stdlib wave/array只读计算：归一化PCM16峰值约0.468719，RMS0.047969，DC0.00006626，没有abs>=32767的样本，2秒窗口有持续信号。可排除全静音或明显PCM16硬削波；不能据此证明音质、正确采样率语义、无重复/漏读或原始Float PCM健康。本轮没有听感/ASR判定。
- 父JSON没有原文、子effective、分阶段耗时及原始终止token，error=null。不能确认实际子参数或自然EOS为何未出现。截图PARAGRAPH底部已显示第1段，只证明截图时选区显示，不能证明生成/播放成功；FULL样本不能代替001单段故障证据。

### 已证实代码问题与修复

1. 原800码点规划忽略当前帧预算，遇首片token_limit即停止全部队列，没有恢复路径。现按Qwen参数取clamp((maxAudioTokens-64)/2,24,400)码点，优先句/词边界；这只是保守启发式。最多1024个执行叶子；只对触顶一分为二，单支最多2层、整请求最多32次重分片（最多1056次尝试），取消/异常/不可再分短片停止。原文与offset覆盖保持，不删除任何数字、不提高用户帧上限。预览/逻辑保留身份使用规范800码点规划，实际进度和父报告使用执行计划。
2. 原规划先校验所有段落，未选中中文/空段也拒绝合法001。现只校验所选段的内容，整个请求的256段/100000字符资源边界仍在；越界/不存在句子不回退。合成fixture已执行确认，尚不能认定这是用户001的实际根因。
3. 同一活动范围每次enterInline都发起历史读取，占用唯一runner；读取latest异常可能让switching一直为true。现同范围重选不重复提交，离开后再进入才读一次；读取失败清除切换锁并显示原因。显式生成的无效范围/参数、busy/switching及提交拒绝均给出原因。失败/取消后等真实worker退出再生成；FULL失败→001再次生成→匹配→播放的Android用例已编译，未执行，不能以修提示结案。
4. 原生终止改为记录是否采到EOS及实际循环步数，不再仅从输出帧数推测；补textTokens、generatedFrames、generationSteps、prefillMillis、tokenGenerationMillis。prefill是提示构建、图准备/KV初始化及前向的整体时间；原generateMillis仍包含它。JNI采用匹配的类型化构造参数。
5. 新检查Qwen的24kHz／1920样本每帧／实际PCM长度一致性，拒绝参数不一致、全静音、非有限PCM及终止状态矛盾。WAV记录peak/RMS/DC/clippedSamples，保留已有有限值/大小检查。数值有效、EOS正常仍不代表语义正确，非静音怪音或漏读仍需听感判断。
6. 重试替换失败片段，成功父WAV只拼最终叶子；失败尝试保留在attempts/childRunIds，后续替换统一回收无引用记录。异常/触顶不写完整缓存或latest、不删旧成功。规划版本english-budget-queue-v2，音频缓存speech-cache-v2-validated-output，engineVersion升级adapter3，收到去编号正文后不会命中旧带编号缓存。旧历史不批量清除、旧唯一句身份不猜配。
7. 父报告新增逐尝试输入hash/长度、子实际参数、runKind、PCM/终止和时间，初始/最终片数、重分片数及首个完整子WAV提交耗时firstCompletedSegmentMillis。后者不是流式首PCM，不表示面板已自动播放；firstPcmLatencyMillis保持null。阶段汇总只算已返回且可测的片段并含重试，缓存排除；未返回的原生异常细分阶段不可测，覆盖数单列。整体elapsed仍不含最终父报告哈希/提交。

公共请求字段/Composable签名/宿主依赖/模型权重/采样算法/重复惩罚/线程默认值/UI布局不变，未增加云端或新后端。模型fingerprint与安装身份不含engineVersion，因此无需重新安装权重。设置schema2不变；只对白名单adapter2→adapter3只读沿用原设置，用户保存才写新版本，未知版本/坏schema仍显式报错；数据库迁移无。原生补丁只增加观测和终止判定，未改KV清理/解码算法。

### 仍待日志或实机的假设

固定源码pipeline在每次合成前clear_kv_cache，generate也初始化KV/采样状态；批量decoder每次上传本片codes/positions/mask，CPU图按帧数缓存，未发现此差异引入跨片PCM拼接。正式和诊断均经同SpeechService/Qwen适配器；该FULL首片没有前一片，因此“跨片残留”不能单独解释本例。但这些静态事实与模拟A/B不能排除原生模型长文本退化、真实输入差异、早期EOS/异常非静音输出或设备状态问题。没有证据把怪音归因为上述已修的UI/规划问题。

本机PATH未发现cl.exe/g++/clang++可用主机工具链；既有NDK用于ARM64 Android库编译，本轮没有主机真实模型合成或手机执行。不安装新环境、不下载替代模型。已知失败不要求用户先重复一次来证明；补原始子JSON可帮助追查effective/时间，若没有也可直接用新候选新增报告完成下一次复测。

### 新增验证与实际边界

第一轮修正work返回类型引起的6处Kotlin覆盖方法编译错误后，执行49项JVM全通过（0失败/错误/跳过）：ReadingPlanTest8、ReadingQueueTest8、DuplicateSentenceTest6、SpeechServiceTest9、StorageSettingsTest7、SpeechRepairTest11。Android测试源码、Release AAR、Lint均通过；BUILD SUCCESSFUL 2m20s，74任务（24执行／50up-to-date）。随后只补报告计时覆盖说明、重试记录回收断言及全请求32次重分片上限用例，针对SpeechRepairTest复跑12项全部通过（0失败/错误/跳过），重编译AAR/Lint；BUILD SUCCESSFUL 1m40s，74任务（23执行／51up-to-date）。两轮合计50个不同用例，最终XML为12项，不称最终又跑了50项。

SpeechRepairTest覆盖：合法001不受其他非法段影响；小预算/编号长文/Unicode精确切片；超过资源边界提前拒绝；同一合成英文段的诊断与队列输入、seed/参数、WAV字节一致；触顶后两片成功只拼新PCM；失败/全静音/NaN/Infinity保留旧成功且不缓存；取消重分片后可再次生成；118段合成fixture完整覆盖且只load一次；去编号输入不命中旧音频且12/2026等正常数字不改；实际参数/终止矛盾拒绝；adapter2设置只读兼容；32次全局重试上限。所有PCM均来自模拟引擎，绝非真实1.7B音质测试。旧无关许可/模型解析/完整存储破坏恢复证据沿用第10至12节，没有重复整套核验。

InlineRuntimeDeviceTest新增2项（FULL触顶后001生成匹配及播放、同范围新requestId不占worker且其他非法段不阻塞）；原4项保持，模拟PCM改为非零信号。QwenJniContractTest补新诊断字段在失败返回时的契约断言。以上Android源码只编译，未在设备运行，UI点击/生命周期/实际MediaPlayer/JNI运行均待验证。

```powershell
$env:JAVA_HOME='D:\CodexData\android-toolchain\jdk-17'
$env:ANDROID_HOME='D:\CodexData\android-toolchain\android-sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
$env:GRADLE_OPTS='-Xmx3g -Dfile.encoding=UTF-8 -Djdk.net.hosts.file=C:/Users/zzf86/.codex/worktrees/5551/Codex_EnglishApp/_manager/tools/gradle-hosts.txt'
& 'D:\CodexData\android-toolchain\gradle\gradle-9.5.0\bin\gradle.bat' `
  -p '_manager/probes/tts-device' --no-daemon --no-configuration-cache --max-workers=2 `
  :runtime:testDebugUnitTest --tests '*SpeechRepairTest' --tests '*ReadingPlanTest' `
  --tests '*ReadingQueueTest' --tests '*DuplicateSentenceTest' --tests '*SpeechServiceTest' --tests '*StorageSettingsTest' `
  :runtime:compileDebugAndroidTestKotlin :runtime:assembleRelease :runtime:lintDebug
# 收尾命令相同，但--tests仅保留'*SpeechRepairTest'。
python experiments/tts-device-probe/tools/inspect-native-artifact.py
```

最终JVM XML时间2026-10-07T16:08:18.205Z（北京时间10-08 00:08），位于build/test-results/testDebugUnitTest；HTML在build/reports/tests/testDebugUnitTest/index.html。Lint最终0错误／6项既有警告：GradleDependency1、NewerVersionAvailable1、ChromeOsAbiSupport1、UsableSpace2、UseTomlInstead1；报告build/reports/lint-results-debug.{xml,html,sarif}。JNI编译4条既有codecvt弃用警告，无新增错误。

### 最终产物

- `build/outputs/aar/runtime-release.aar`：1,947,076字节，SHA-256 `f2e7e4cc99d851f6fa036e37daac1114a096f8a6287ef40e00983bb42275e2c2`。
- 新libmodel_probe_qwen.so：2,303,760字节，SHA-256 `3da2a46ff18771ae35afa2d9b762142b1b80dc61593ea537930f1df47ff48c5b`；libc++_shared.so仍1,253,544字节，SHA-256 `cd61762848882a16c8244c964a6f396c0caa0b440588a210ce9cc4ab0e6d9f0c`。
- 新vendor/LOCAL_CHANGES.patch SHA-256 `ae30dc345902b789e3bd0bc6b7c5837033147e3605ad0b8b50640309a81e1233`；引擎身份`16bb5afc-ggml-af97976c-patch-ae30dc34-adapter3`，upstream提交和GGML仍与第7节一致。
- AAR独立检查：仅arm64-v8a两份SO，LOAD 16384对齐/RELRO覆盖通过，4份notice匹配；Manifest只有minSdk29，无Activity/Application/新增权限。检查结果`.local/artifact-check/inspection.json`（当前产物，历史hash仍在旧节）。javap核对新QwenNativeResult构造descriptor与JNI一致：参数后缀IIILjava/lang/String;JJ；这是静态构造契约，不声称已跑JNI。

### 总控下一步与停止条件

交由总控接收本模块源/AAR，结合已合入的功能1去编号输入，从其干净提交生成候选APK；无需重跑已覆盖模块检查，只补新增集成/JNI及实际手机链。最小验证顺序：

1. 同一无私有资料的短句及不足400码点英文段，在旧诊断与新正式PARAGRAPH入口用同Ryan/seed42/4线程及原参数生成，分别保存JSON/WAV并试听。核对输入hash、实际参数、EOS、帧/PCM、分阶段耗时；没有一致输入不能作性能或音质优劣结论。
2. 新候选直接验证用户001单段的接收→显式生成→退出busy/switching→匹配完整结果→播放；保留同次JSON/WAV与实际安装版本。不要以底部出现第1段或仅WAV头正确验收；无需强求用户导出完整正文。若还失败，最小补充是该单段同次报告及实际安装版本；原失败子fce759fa…的JSON若可取得有助回溯。
3. 短多段/含正常数字的长段，检查无漏读/重复、分片接缝和无学习编号；再扩大到118段。触顶/取消后切001再生成，旧成功版本应保留。首片保存时间与整篇时间分别读新报告，不把批量接口称流式。

不能承诺速度提升倍数：已有样本311990ms是失败整请求时间，旧34.08秒样本RTF4.71来自另一文本且没有同文报告；二者不能直接比较。模拟只证明串行队列包括重试无需每片重新load。保留1.7B Q4，未提高线程或引入GPU/NPU/新依赖。完成一次交接后等待总控，不Git/APK/上传，不以此结案真实怪音和001不可用。


## 14. 2026-10-14 语音0.4.0后台与自然段持久队列候选

本节对应总控明确批准的 saber1.3.0 / 语音0.4.0；实施契约唯一正文为已授权的 `D:/Codex_Project/Codex_EnglishApp/_manager/features/feature-03-speech/V1_3_0_PLAN.md`。旧模型、原生/解析和第13节修复证据沿用；总控概括性“基本正常”反馈及第9节单次“完整且清晰、没有明显漏读或重复”不提高为本轮后台/恢复/音质验收。无Git操作、APK、根宿主、全局Gradle/代理/系统环境变更。

### 公共接口与宿主交接

在 `com.xuesui.englishapp.modelprobe.runtime` 新增精确接口：

```kotlin
@Composable
fun SpeechReadingPlayButton(documentId: String, request: SpeechReadingRequest, modifier: Modifier = Modifier)
@Composable
fun SpeechRuntimeHost()
```

- 总控在正式Activity内容根调用RuntimeHost一次，覆盖非阅读Tab的前台恢复；给功能1的playbackControl基础类型槽映射FULL/PARAGRAPH请求，稳定documentId/原段索引/去编号英文沿用。请求正文不进Intent/Bundle。进入前台仅恢复生成，绝不恢复出声。
- ModelProbeFeature、SpeechFeature、SpeechInlineReadingFeature、SpeechSettingsFeature与请求字段保留。独立ModelProbeFeature内部已放RuntimeHost；独立宿主不需要再重复放置它。旧完整/单句音频可管理、播放、导出和删除；不猜拆旧全文文件。
- ProbeViewModel为观察/命令门面，所有实际入口共享进程级ProbeController与唯一TaskRunner/模型。正式生成经服务驱动，页面、Tab、ON_STOP、旋转和ViewModel清理不触发原生取消。诊断显式加载仍可保持模型驻留用于warm测试；正式持久队列结束后释放模型。
- 缺段必须确认；全部已有直接播放，补齐按顺序等缺段，显式“仅播放已有”显示真实段号。补齐保留已有随机take的实际seed。新范围/暂停使异步文件准备与迟到生成事件失效，取消弹窗不开始生成。生成全文与播放独立。
- 底部槽继续最大180dp，详情包含全局播放段号/时间/速度、各批生成进度及暂停/继续/取消、音频管理和全文WAV导出。设置仍只管理模型/声音；正在生成时可改新任务设置，现任务快照不变。默认参数未改，中文说明见QwenSettings.ParameterHelp。

### 新依赖、服务与最小权限

直接依赖 `androidx.media3:media3-exoplayer:1.11.1`、`androidx.media3:media3-session:1.11.1`，使用官方Google Maven、固定版本；复用既有catalog的core-ktx。Media3发布和许可证分别核对 [官方发布页](https://developer.android.com/jetpack/androidx/releases/media3) 与 [1.11.1 LICENSE](https://raw.githubusercontent.com/androidx/media/1.11.1/LICENSE)，Apache-2.0。原生第三方notice保留，新增LICENSE-media3.txt和NOTICE依赖说明；这些Maven库由宿主依赖图解析，AAR不嵌入第二套依赖classes。

新增Manifest服务（没有Application类、Activity或launcher）：

| 全名 | Manifest | 启动与用途 |
|---|---|---|
| `com.xuesui.englishapp.modelprobe.runtime.SpeechGenerationService` | exported=false，stopWithTask=false，foregroundServiceType=dataSync\|mediaProcessing | 用户可见操作/RuntimeHost前台恢复调用ContextCompat.startForegroundService；API35+以mediaProcessing类型startForeground，29–34以dataSync类型启动。返回START_NOT_STICKY，无开机/后台自动重启 |
| `com.xuesui.englishapp.modelprobe.runtime.SpeechMediaService` | exported=false，stopWithTask=false，foregroundServiceType=mediaPlayback；action=androidx.media3.session.MediaSessionService | 用户明确播放时启动，Media3 MediaSessionService管理媒体前台通知。MediaSession仅接纳本UID、系统UID或平台trusted控制器；不开放更换MediaItem命令 |

六项权限为 `android.permission.FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_DATA_SYNC`、`FOREGROUND_SERVICE_MEDIA_PROCESSING`、`FOREGROUND_SERVICE_MEDIA_PLAYBACK`、`POST_NOTIFICATIONS`、`WAKE_LOCK`（后五项同android.permission前缀）。不新增网络/录音/广泛存储权限；播放只使用已校验的本地文件。

生成通知channel `speech-generation-v1` / ID41031，显示标题、完成自然段数和当前段，有暂停/继续/取消；Intent仅带批次UUID，服务不导出。暂停/失败保留可继续的非前台通知；通知点击使用PackageManager现有App启动Intent及immutable PendingIntent，不增加宿主路由。MediaSession复用同一App启动PendingIntent、独立媒体通知和音频焦点/noisy控制；没有用mediaPlayback给推理保活。RuntimeHost在需要时解释并请求通知权限；拒绝后说明生成通知控制不可见，媒体通知遵循系统独立处理，不因拒绝崩溃。

生成服务本次主动预算45分钟，partial wake lock带46分钟兜底超时且在暂停/退出释放；服务预算不因同一实例多次启动指令延长。onTimeout和主动时限请求合作取消、保存中断状态并stopForeground/stopSelf，不在主线程等待原生返回；原所有者仍持有工作线程/模型租约，直到实际退出才释放或开下一任务。内核I/O、模型解析、内存分配、单个CPU计算节点仍可能延迟取消，未实测最大退出延迟。不使用WorkManager或开机恢复；强制停止后需重新打开App，平台/厂商仍可能杀进程。

依据沿用 [FGS类型](https://developer.android.com/develop/background-work/services/fgs/service-types)、[FGS时限](https://developer.android.com/develop/background-work/services/fgs/timeout)、[Media3后台播放](https://developer.android.com/media/media3/session/background-playback)。平台共享时限由系统执行，本模块不声称45分钟预算等于系统剩余额度。

### SQLite schema1与文件发布/恢复

仅新增本宿主 `noBackupFilesDir/model-probe-v1/speech-queue.sqlite3`，SQLiteOpenHelper、WAL、synchronous=FULL、外键启用。四表定义在runtime/SqliteSpeechQueue.kt：

- batches：id主键、queue_order唯一、document_id、state、payload。
- paragraphs：id主键、batch_id外键、paragraph_index、state、audio_run、pending_run、payload；batch_id+paragraph_index唯一，并有batch/state/index索引。
- latest_audio：identity主键、audio_run、payload。
- queue_meta：key主键、value，保存待回收run ID集合。

payload包含冻结正文/参数与实际seed、内容/配置/引擎摘要、标题、任务顺序、状态/原因和音频引用。一次全文入队在一个事务中写完；保存采用UPDATE/INSERT，不通过替换父行破坏外键。没有升级重建或降级兜底：未来版本需显式向前迁移，未知版本报错保留原库。其他模块数据库与原有results/settings不迁移。

进程恢复仅续queued/interrupted；user-paused/cancelled不自动续，failed需要明确重试。单段失败暂停本篇后续，下一文章仍按FIFO运行。随机seed在入队时解析一次，每自然段预留2048个段内attempt序号；恢复使用同一参数字符串，不重新抽取。配置/正文变化创建新快照；取消保留已经发布的完成段。

文件与SQLite之间使用可恢复发布协议而非声称一个跨文件系统事务：先登记pending_run，RunStore完整写入/哈希/原子rename；新run先写入catalog持久pins，再用SQLite事务同时切换DONE和latest_audio，最后按最新账本裁剪pins并回收无引用旧版。数据库写失败时旧latest保持，额外pins宁可暂留；重开可核验完整草稿并完成发布或重试未完成段。RunStore构造恢复先读durablePins，不只依赖内存Lease；已提交子片可精确缓存复用，未提交父的废弃子片按显式sessionId回收。

queue/player/export的持久pin互不覆盖；player在ExoPlayer实际release后再释放引用。替换/删除会保留仍被队列、播放器、导出或其他父结果引用的文件。删除已确认音频同时禁止相关未完成任务迟到发布，防止自动复活；取消后只能显式新建任务。完成/取消批次保留任务元数据，不无限pin被新成功版本替换的音频。

旧PARAGRAPH仅依据明确稳定retention身份进入新latest索引，新body/settings不匹配时不播放错配音频；旧FULL/单句/无documentId的历史不猜测迁移，仍可显式管理。新FULL没有长期整篇WAV，只有自然段WAV和必要段内子片；全文SAF导出先绑定完整段列表、临时顺序拼接、结束/失败清临时产物，下一进程清遗留临时文件。SAF选择器期间若进程被终止，需要重新发起导出。

### 增量验证与边界

受影响命令从仓库根调用（只构建Library）：

```powershell
& '.\experiments\tts-device-probe\tools\test-module.ps1' -Tests @(
  '*ParagraphLedgerTest','*DurableAudioTest','*ReadingRetentionTest',
  '*ReadingQueueTest','*SpeechRepairTest','*TaskRunnerTest','*StorageSettingsTest')
```

脚本运行testDebugUnitTest、compileDebugAndroidTestKotlin、assembleRelease、lintDebug；JDK17/SDK36/min29/NDK28.2/CMake3.22.1/Gradle9.5.0保持。按总控同日授权，仅Gradle调用作用域设置JAVA_TOOL_OPTIONS的固定主工作区hosts路径，finally恢复原值。初始两次Gradle官方POM超时以及一次独立HEAD超时保留为失败尝试；总控后续新JVM启动参数修正和87制品解析成功直接沿用，未换源/版本/代理。

新增JVM测试覆盖44段完成43段后重建账本、冻结随机seed、暂停/取消不恢复、FIFO/失败让出其他文章、并发入队去重、禁止双claim、存储事务失败保留旧latest、删除禁止迟到发布、跨任务持久pin/子引用/孤儿回收、补齐只生成缺段、缺段等待/显式跳段、暂停后迟到可用事件以及中文帮助语义。其余受影响存储、段内重试和原生工作线程边界回归沿用并补跑调用链；不重审未变模型/native/解析。

新增Android SQLite真实事务/重开44段测试及Media3合成WAV解析/seek测试均只编译；旧fake运行期生命周期测试改为切页不中止，并明确不再把独立fake控制器当作进程MediaSession播放验收。旧MediaPlayer测试仅作为兼容实现历史用例，不宣称测试了新Media3服务。没有运行Android测试、真实模型、FGS、锁屏、音频焦点、系统时限、通知拒绝或视觉UI验证。

设备下一步由总控完成宿主接线与干净提交APK后安排：先2–3段验证直接播放/缺失确认、生成播放分离、切Tab/后台/锁屏、暂停不续、杀进程再开续段但不出声；再验证全文导出、旧音频删除/替换、断点43/44，最后扩大到实际长文。一次合成样本和概括性“基本正常”反馈均不能替代这些新验收；不承诺推理速度倍数改善。

最终测试计数、AAR/二进制hash及交接状态见下方本轮验证收据。Git、集成、根版本/签名和APK由总控负责；本任务报告一次后等待。


### 本轮最终验证收据

- 最终命令成功退出0，Gradle `BUILD SUCCESSFUL in 2m 35s`。JVM XML时间2026-10-13T17:26:18.451Z至17:27:01.837Z（北京时间2026-10-14 01:26–01:27），66项：65通过、1环境跳过、0失败/错误。分布：ParagraphLedgerTest10、DurableAudioTest7、ReadingRetentionTest20（含1跳过）、ReadingQueueTest8、SpeechRepairTest12、StorageSettingsTest7、TaskRunnerTest2。
- 跳过项为既有 `symbolicLinksAreRejectedBeforeAnyResultOrExternalFileIsRemoved`：Windows主机不允许测试创建符号链接。Android对应安全用例已编译，未执行，不将跳过计入通过。
- `compileDebugAndroidTestKotlin`、`assembleRelease`、`lintDebug`均通过。Lint 0错误/8警告：GradleDependency1、ChromeOsAbiSupport1、UsableSpace2、UseKtx1、UseTomlInstead3。固定SDK/ARM64与模块内依赖版本在本轮范围内保留；UseKtx为建议改写显式SQLite事务的风格提示。精确报告为build/reports/lint-results-debug.{xml,html,sarif}。
- `build/outputs/aar/runtime-release.aar`：2,124,766字节；SHA-256 `64ce84178bb36675e1611b0a92e22c638521f7d5c21bb77e47006da9b4ce436f`。
- AAR内 `libmodel_probe_qwen.so`：2,303,760字节，SHA-256 `3da2a46ff18771ae35afa2d9b762142b1b80dc61593ea537930f1df47ff48c5b`；`libc++_shared.so`：1,253,544字节，SHA-256 `cd61762848882a16c8244c964a6f396c0caa0b440588a210ce9cc4ab0e6d9f0c`。两份与第13节逐字节身份一致，因此旧原生/ELF证据直接沿用，没有重复原生审计或模型执行。
- 读取最终AAR确认两个新增入口class、两个服务class、上述六权限/两个未导出服务及minSdk29；无Activity/launcher/Application类。classes.jar含5份model-probe notice/license资源，包括新增11,358字节LICENSE-media3.txt。此为AAR静态检查，不是APK安装或运行结果。
- 模块结论 `ready_for_integration / awaiting_manager_integration`。源码/AAR与交接范围完整，Android/FGS/锁屏/焦点/通知拒绝/恢复实际执行、Compose视觉与真实生成音质仍待总控宿主和手机验收。详细当前实现见ARCHITECTURE，授权入口见MANAGER_LINK；不改写旧失败记录。

### 2026-10-14 接收后定向修正：长篇生成预算

总控接收上述0.4.0交付及65通过/1环境跳过等证据后，在同一已授权BRIEFING顶部要求单一预算修正。本小节取代前文45分钟/46分钟的当前运行策略和上一份AAR身份；原实现与验证收据保留为交接时点历史，不撤销其他已接收证据。

- `SpeechGenerationService.kt`主动预算为5小时50分钟（350分钟，21,000,000 ms），wake lock兜底为预算加1分钟（351分钟，21,060,000 ms），通过内部常量`WAKE_TIMEOUT_MS`供原acquire调用使用。保留原`onTimeout`、同一运行实例不续期、`START_NOT_STICKY`、唯一native所有者和安全退出；没有增加重启循环，系统共享额度仍可能更早耗尽，不能保证手机连续运行满预算。
- 同时仅清理`SpeechInlineReadingFeature.kt`第106行`else`后的行尾空格。公共API、依赖、Manifest、数据库schema、队列/原生/播放状态机及宿主需求不变。
- 新增`src/test/java/com/xuesui/englishapp/modelprobe/runtime/SpeechServiceBudgetTest.kt`，两项纯JVM断言分别验证5小时50分钟主动时限和wake lock多1分钟的契约。没有启动Android Service或等待实际时限，不能视作真实设备时限测试。

精确增量命令（既有进程级工具链/hosts作用域保持）：

```powershell
& './experiments/tts-device-probe/tools/test-module.ps1' -Tests '*SpeechServiceBudgetTest' -Tasks @(':runtime:testDebugUnitTest', ':runtime:assembleRelease')
```

- 退出0，`BUILD SUCCESSFUL in 38s`，45 tasks（12 executed / 33 up-to-date）；Debug/Release Kotlin增量编译和Release AAR成功。预算测试**2通过、0跳过、0失败/错误**，XML时间2026-10-13T17:37:14.741Z（北京时间2026-10-14 01:37）。当前`build/test-results/testDebugUnitTest/`和对应HTML报告只对应本次2项，先前66项以已接收的上一份收据为准；没有重跑Android测试源码编译、Lint或旧66项。
- 新`build/outputs/aar/runtime-release.aar`：**2,124,811字节**；SHA-256 **`791603640fed2891bcb09dc446a28f4d262facc275ac265ce3fc7f2726057573`**。直接读取新AAR中两份SO的大小/hash，与本节上一份收据完全一致；旧原生/ELF/模型来源证据继续沿用。
- 变更仅上述2个生产源码、1个定向测试，以及当前架构/状态/变更/授权和本收据5份文档；未执行Git、构建APK或提升任何设备验收。状态仍为`ready_for_integration / awaiting_manager_integration`，完成一次定向交接后等待总控集成。

## 15. 2026-11-07 语音0.5.0：展开播放器、布局与性能对照

执行总控同日语音BRIEFING顶部和V1_4_0_PLAN.md完整正文；来源由总控登记c45be63，本任务未执行Git。0.4.0完整交付、350分钟修正和成功发送收据均保留；总控已经通报1.3.0 test.001完成集成/签名，并未通报新设备验收。本节是0.5.0增量正文，非App已发布声明。

### 产品与契约差异

- 公共SpeechInlineReadingFeature、SpeechSettingsFeature、SpeechReadingPlayButton、SpeechRuntimeHost、ModelProbeFeature及请求DTO签名不变，不要求新宿主接线。两个宿主继续复用Library，不新增Activity/Application/launcher。生产依赖、Manifest、SQLite schema1及数据迁移不变；新增依赖仅Android测试配置`androidx.compose.ui:ui-test-junit4`，版本由既有Compose BOM约束。
- 紧凑条保留180dp上限，展示实际播放器文章/状态/进度，展开进入Library自己的全屏Dialog。收起/Back仅销毁展示层，仍使用原Media3 Session/Player，不调用停止、取消或阅读导航。当前选择的生成范围与实际播放范围分开；原单句入口保留。
- 展开页有实际文章标题、原段号/总段数、当前段正文、真实时长/已播时长、受实际duration约束的seek、上一/下一自然段、播放/暂停、0.5–2倍速和片段列表。未知时长禁用拖动；提交拖动带runId，防止换段后的旧操作落到新音频。单段序列不能越界；全文从所选段继续原序列，“仅已有”保留稀疏原段号。切段时保留暂停意图，显式点击已有段可播放，缺失段先确认，不在纯导航中启动推理。后台迟到结果不会自行解除暂停。
- Media3服务继续承接通知/锁屏/焦点；seek和倍速由同一运行器处理，外部倍速变化同步到页面。播放文件引用仍保持到Player释放；切段不提前释放所需文件。展开页不使用虚构逐字时间轴或在UI暴露内部ID/路径。
- 设置按“当前模型、声音与风格、高级参数”分区，说明按项展开；风格动作可换行，正式页沿用根Material主题，移除重复安全区。生成控制、音频管理/删除和导出放原详情入口；不改默认/权重/音色/保存设置。

### 定向性能结论与回退

总控提供的“队列已复用模型”证据沿用；第9节34.08秒/RTF4.71是旧单次样本，不是此次测速。当前主要计算仍是1.7B CPU自回归模型；本轮没有实机分阶段占比，不声称加速倍数。

固定ggml源码的`vendor/ggml/src/CMakeLists.txt`明确要求CPU_ALL_VARIANTS依赖BACKEND_DL，而BACKEND_DL依赖BUILD_SHARED_LIBS；当前采用静态CPU子集。ARM变体又使用独立`cpu-feats.cpp`和禁止LTO的能力检测对象。直接更改整库`-march`不能提供旧机安全回退。因此本轮保持`armv8-a`，未打开dotprod/i8mm、GPU/NPU/OpenMP/KleidiAI或动态后端。新JNI `backendInfo()`返回实际后端和编译开关，并用Android HWCAP区分设备是否支持dotprod/i8mm；“设备支持”不写成“本构建已启用”。后端信息进入单次报告、正式阅读尝试行和线程汇总。

已确认并修改的开销仅两处每帧临时分配：`transformer_runtime_code_pred.cpp`中词表大小的host logits vector，改为串行模型state内复用容量，每次仍assign零、每步仍由相同tensor_get覆盖；`transformer_generate.cpp`的15码本输出vector移到当前合成调用外层，每帧clear、由原函数resize/写入。没有改变采样、矩阵计算、EOS、取消位置、分片、重试、PCM检查或发布协议。模型state销毁即回收logits容量，不使用跨模型全局缓存。

回退开关是本模块CMake `PROBE_REUSE_CODE_BUFFERS`（默认ON）；在本模块cmake arguments传`-DPROBE_REUSE_CODE_BUFFERS=OFF`后重新构建可恢复原临时vector路径，报告的`bufferReuse`会为false。`tools/check-buffer-fallback.py`基于实际Android编译数据库、仅将该定义改为0，对三份受影响编译单元做`-fsyntax-only`，不改变AAR；其证据不能替代回退包设备推理。结果见下方最终收据。

当前累计vendor补丁由既有固定原件重新生成：`vendor/LOCAL_CHANGES.patch` SHA-256 `bf474c9ed4f563e8c0a2be2e0feaa6a79f46ebfc3062e04581a05213cb21aa26`，引擎身份`16bb5afc-ggml-af97976c-patch-bf474c9e-adapter4`。上游和GGML提交/许可不变，未重新审计许可或下载权重。adapter2/3设置均在显式兼容集合内只读加载；旧成功音频/失败历史仍保留，新引擎的缓存身份独立，不冒充旧音频来自新实现。

### 设备线程对照入口

仅ModelProbeFeature的独立诊断页提供按钮与开始确认；正式设置页不增加基准或生成器。用户输入同一小段英文（最多400字符），超过限制可见拒绝，不自动截断。可用线程为4/6/8中不超过Java和原生共同上限的档位；不足4核心时用实际上限。每档先COLD重载再WARM复用，所有轮次使用同一份冻结正文、音色/指令/采样参数及一次解析的实际seed，不改form或持久设置、不并行、不命中缓存。

失败、触顶或取消即停止后续，已返回的单次报告和汇总保留。汇总任务`speech.thread-benchmark.v1`仅增加一种诊断JSON记录（不改数据库schema），含来源runId、正文hash、实际参数/后端、音频时长、RTF、总耗时、加载/预填充/token生成/解码/WAV耗时、PSS采样峰值、起止电池温度与Android热等级；缺失指标保留null。可在诊断历史选择并导出JSON。没有首PCM数据，不记录全文，不将电池温度写成CPU温度。COLD仅代表模型重载，不清系统页缓存；固定顺序可能受热累积影响，设备比较需同文、同配置并考虑冷却/重复。

### 更快模型的接入差距（未采纳或下载）

| 候选 | 一手资料与许可边界 | 本项目需要补齐的接口/验证 |
|---|---|---|
| Kokoro-82M + sherpa-onnx | [模型卡](https://huggingface.co/hexgrad/Kokoro-82M)标注82M及Apache-2.0权重；[sherpa Android清单](https://k2-fsa.github.io/sherpa/onnx/tts/apk-engine.html)提供Android TTS构建，框架有[自己的许可](https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE)。模型示例还使用misaki/espeak-ng，不能把权重许可当全链许可结论。 | 新增独立SpeechEngine/参数能力表、固定ONNX/voices/词法前端资产与hash、Android JNI/ABI和16KiB包验证；逐项核对语音前端及所用导出包的依赖许可。Ryan/Aiden不可假定有等价音色；必须单独标识声音并经用户听感对照。 |
| Pocket TTS 100M | [官方仓库](https://github.com/kyutai-labs/pocket-tts)提供CPU/流式路径，代码[MIT](https://github.com/kyutai-labs/pocket-tts/blob/main/LICENSE)；[官方权重页](https://huggingface.co/kyutai/pocket-tts)为CC-BY-4.0且文件访问需接受条件。不是整链MIT，也不是本机测速。 | 确定受维护的Android可打包运行时/固定revision、资产获取和署名要求；语音state/取消/串行所有权/自然段完整发布适配。当前批量SpeechEngine接口不能伪报流式首PCM，若做真流式须单独设计回调契约。 |

后续A/B应作为显式候选引擎适配：保持正文、采样条件和测量口径可追踪，分别记录音色、引擎身份、完整性与听感；不得以第三方电脑速度代替这台手机结果。本轮没有新后端/权重、云服务或自动选型；新增更小模型的宣传不改变当前已授权范围。

### 验证边界与总控下一步

受影响JVM覆盖完整/单段/稀疏序列切段、暂停与缺段、seek边界、线程档位与阶段统计/null保持，并沿用/补跑相应SpeechService、SpeechRepair和音频持久保护用例。新增Android播放器表面测试用合成正文和一份真实Session/Player核对320dp、360dp大字体、横屏滚动、展开/收起及控制回调；该测试不假装验证生产服务/通知/锁屏。原真实WAV Media3、SQLite和生命周期测试仍只编译，不宣称手机执行。

总控接收后继续集成到1.4.0候选，根主题提供深蓝/银白色阶，独立宿主兼容检查由总控完成。手机仍需验证紧凑条不超180dp、长标题/大字、Back收起保留阅读位置、全文/本段/单句、拖动/倍速/切段、缺失确认及用户暂停、通知/锁屏/焦点同步、后台生成和旧音频管理。性能必须同文同配置测线程/冷暖，并比较缓冲复用ON/OFF的完整PCM/听感与耗时；无真实证据不登记提速或设备通过。

### 本轮最终验证与产物收据

只运行本Library受影响范围，既有0.4.0的66项及其他未变证据不重复验证。完整验证命令如下，工具链沿用JDK17、SDK36/min29、NDK28.2.13676358、CMake3.22.1、Gradle9.5.0；脚本只在当前Gradle进程作用域使用既有固定hosts设置并在finally恢复。

```powershell
& './experiments/tts-device-probe/tools/test-module.ps1' -Tests @(
  '*PlaybackNavigationTest', '*ThreadBenchmarkTest', '*DurableAudioTest',
  '*SpeechServiceTest', '*SpeechRepairTest'
) -Tasks @(':runtime:testDebugUnitTest', ':runtime:compileDebugAndroidTestKotlin',
  ':runtime:assembleRelease', ':runtime:lintDebug')
```

- 完整命令最后一次运行成功退出0，`BUILD SUCCESSFUL in 6m 42s`，74项任务（24执行/50 up-to-date）。JVM XML时间2026-11-07T11:35:48.600Z至11:37:16.039Z：39项全部通过，0失败/错误/跳过。分布为DurableAudioTest7、PlaybackNavigationTest6、SpeechRepairTest12、SpeechServiceTest9、ThreadBenchmarkTest5。覆盖新增导航/暂停/seek/线程报告，以及受影响服务、分片修复、音频持久保护调用链。
- 随后只调整UI测试的v2 rule导入、滚动定位与对应生产UI测试标记，未改逻辑。最终增量命令为同一脚本加 `-Tasks @(':runtime:compileDebugAndroidTestKotlin', ':runtime:assembleRelease', ':runtime:lintDebug')`，成功退出0，`BUILD SUCCESSFUL in 4m 27s`，69项任务（22执行/47 up-to-date）。39项逻辑结果直接沿用，不重复运行。
- 新增PlayerSurfaceDeviceTest的320×640、360×640大字体、640×320横屏大字体三项用例已编译。用例使用合成正文及一个真实ExoPlayer/MediaSession，覆盖展示和控制回调；没有执行这些Android用例，不将其等同生产服务/通知/锁屏或手机视觉验收。
- 最终Lint为0错误/9警告：GradleDependency1、ChromeOsAbiSupport1、UsableSpace2、UseKtx1、UseTomlInstead4。相较旧8项多1条新增Android测试依赖的版本目录建议，不更改总控全局catalog。报告为 `build/reports/lint-results-debug.{xml,html,sarif}`。
- 回退检查工具 `tools/check-buffer-fallback.py` 对当前 `.cxx/Release/362z5g3x/arm64-v8a/compile_commands.json` 中三个受影响编译单元，将 `PROBE_REUSE_CODE_BUFFERS` 改为0后仅做Android ARM64编译器 `-fsyntax-only`，三个退出码均0。两个vendor单元无诊断，JNI保留4条既有C++17 codecvt/wstring_convert弃用警告。原始结果在 `.local/cpu-buffer-fallback.json`；此项不是OFF完整链接/设备执行或ON/OFF数值等价实测。
- 最终 `build/outputs/aar/runtime-release.aar`：**2,194,811字节**，SHA-256 **`93a21e1256c0e59fd65564b17da878d7c496ef55e387c260d9b3ceac05b95211`**。
- AAR仅包含arm64-v8a两份SO。新 `libmodel_probe_qwen.so` 为2,304,960字节，SHA-256 `8d21b695a090ce7a570ff188edd47fc6cf81fd0894df8991c2dc0fe0a0a20085`；`libc++_shared.so` 仍为1,253,544字节，SHA-256 `cd61762848882a16c8244c964a6f396c0caa0b440588a210ce9cc4ab0e6d9f0c`。两者均与本轮已检查的AArch64 ELF完全一致，沿用其LOAD 16,384对齐/同余及RELRO覆盖通过证据。llvm-nm已确认新增 `Java_com_xuesui_englishapp_modelprobe_engines_qwen3tts_QwenNativeBindings_backendInfo` 导出；现有JNI keep规则覆盖，无新增反射入口。
- 最终classes.jar内5份license/notice与模块源逐字节相同。`.local/artifact-check/final-receipt.json`为最终AAR/两SO/5份notice/Lint/JVM计数收据；`inspection.json`保留前一中间AAR的ELF检查，只有其中两SO身份与最终一致，不把中间AAR hash充当最终值。
- 模块状态为 `ready_for_integration / awaiting_manager_integration`。本轮没有运行真实模型、原生OFF/ON对照或任何手机测试，没有声称提速、音质改善或App发布。第9节34.08秒样本的用户反馈“完整且清晰，没有明显漏读或重复”仍仅适用于该旧样本。总控完成集成、提交、签名候选APK和新增设备验证；本任务完整报告一次后等待。
