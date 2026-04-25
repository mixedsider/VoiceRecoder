cmake_minimum_required(VERSION 3.22.1)
project("voicelog")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_FLAGS_DEBUG "${CMAKE_C_FLAGS_DEBUG} -O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} -O3 -DNDEBUG")

set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
add_subdirectory(libs/llama.cpp)

add_library(
    voicelog
    SHARED
    llama_jni.cpp
)

target_include_directories(voicelog PRIVATE
    libs/llama.cpp/include
    libs/llama.cpp/ggml/include
)

find_library(log-lib log)

target_link_libraries(voicelog
    llama
    ggml
    ${log-lib}
    android
)
