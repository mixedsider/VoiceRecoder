cmake_minimum_required(VERSION 3.22.1)
project("voicelog")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ─── whisper.cpp ───────────────────────────────────────────────────────────
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
add_subdirectory(libs/whisper.cpp)

# ─── llama.cpp ─────────────────────────────────────────────────────────────
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
add_subdirectory(libs/llama.cpp)

# ─── JNI 공유 라이브러리 ──────────────────────────────────────────────────
add_library(
    voicelog
    SHARED
    whisper_jni.cpp
    llama_jni.cpp
)

target_include_directories(voicelog PRIVATE
    libs/whisper.cpp
    libs/whisper.cpp/include
    libs/llama.cpp/include
    libs/llama.cpp/ggml/include
)

find_library(log-lib log)

target_link_libraries(voicelog
    whisper
    llama
    ggml
    ${log-lib}
    android
)
