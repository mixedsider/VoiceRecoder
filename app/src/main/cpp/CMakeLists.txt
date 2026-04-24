cmake_minimum_required(VERSION 3.22.1)
project("voicelog")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(
    voicelog
    SHARED
    whisper_jni.cpp
    llama_jni.cpp
)

find_library(log-lib log)

target_link_libraries(
    voicelog
    ${log-lib}
    android
)
