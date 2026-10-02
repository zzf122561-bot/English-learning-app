cmake_minimum_required(VERSION 3.22.1)
project(model_probe_qwen LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
get_filename_component(PROBE_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../.." ABSOLUTE)
set(QWEN_DIR "${PROBE_ROOT}/vendor/qwen3-tts")
set(GGML_DIR "${PROBE_ROOT}/vendor/ggml")

# Audited CPU-only subset; no FetchContent, architecture autodetection, or tools.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_CPU ON CACHE BOOL "" FORCE)
foreach(flag NATIVE BACKEND_DL CPU_ALL_VARIANTS OPENMP CPU_KLEIDIAI LLAMAFILE BLAS ACCELERATE
        CUDA HIP MUSA VULKAN OPENCL METAL CANN RPC SYCL OPENVINO WEBGPU ZDNN ZENDNN HEXAGON
        VIRTGPU VIRTGPU_BACKEND ET CPU_HBM BUILD_TESTS BUILD_EXAMPLES CCACHE)
    set(GGML_${flag} OFF CACHE BOOL "" FORCE)
endforeach()
if(ANDROID)
    if(NOT ANDROID_ABI STREQUAL "arm64-v8a")
        message(FATAL_ERROR "The model probe currently supports arm64-v8a only")
    endif()
    set(GGML_CPU_ARM_ARCH "armv8-a" CACHE STRING "" FORCE)
endif()
add_subdirectory("${GGML_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/ggml")
find_package(Threads REQUIRED)

set(QWEN_SOURCES
    text_tokenizer.cpp gguf_loader.cpp tokenizer_unicode.cpp tokenizer_unicode_data.cpp
    tts_transformer.cpp coreml_code_predictor_stub.cpp qwen3_tts.cpp
    audio_tokenizer_encoder.cpp speech_tokenizer_encoder.cpp
    transformer/transformer_cache.cpp transformer/transformer_debug.cpp
    transformer/transformer_embeddings.cpp transformer/transformer_graph_code_pred.cpp
    transformer/transformer_graph_talker.cpp transformer/transformer_loader.cpp
    transformer/transformer_loader_metadata.cpp transformer/transformer_loader_tensors.cpp
    transformer/transformer_runtime.cpp transformer/transformer_runtime_code_pred.cpp
    transformer/transformer_generate.cpp
    encoder/encoder_frontend.cpp encoder/encoder_graph.cpp encoder/encoder_loader.cpp encoder/encoder_runtime.cpp
    decoder/decoder_cache.cpp decoder/decoder_graph.cpp decoder/decoder_layers.cpp
    decoder/decoder_loader.cpp decoder/decoder_runtime.cpp
    common/audio_io.cpp common/icl_prompt_io.cpp common/speaker_embedding_io.cpp
    pipeline/pipeline_models.cpp pipeline/pipeline_runtime.cpp pipeline/pipeline_synthesize.cpp)
list(TRANSFORM QWEN_SOURCES PREPEND "${QWEN_DIR}/src/")
add_library(probe_engine STATIC ${QWEN_SOURCES} probe_engine.cpp)
target_include_directories(probe_engine PUBLIC "${QWEN_DIR}/src" "${CMAKE_CURRENT_SOURCE_DIR}" "${GGML_DIR}/include")
target_link_libraries(probe_engine PUBLIC ggml ggml-base ggml-cpu Threads::Threads)
if(WIN32)
    target_link_libraries(probe_engine PUBLIC Psapi)
endif()

if(ANDROID)
    add_library(model_probe_qwen SHARED probe_jni.cpp)
    target_link_libraries(model_probe_qwen PRIVATE probe_engine log)
    target_link_options(model_probe_qwen PRIVATE
        "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")
endif()

# Native contract executable, not an APK. Built explicitly; no model needed.
add_executable(probe_native_contract_test EXCLUDE_FROM_ALL "${PROBE_ROOT}/src/test/cpp/native_contract_test.cpp")
target_link_libraries(probe_native_contract_test PRIVATE probe_engine)
if(ANDROID)
    target_link_options(probe_native_contract_test PRIVATE
        "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")
endif()
