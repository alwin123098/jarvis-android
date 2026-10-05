cmake_minimum_required(VERSION 3.22.1)
project("jarvis")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---------------------------------------------------------------------------
# Third-party sources are vendored by scripts/fetch_native.sh into
#   third_party/llama.cpp
#   third_party/whisper.cpp
# The whisper.cpp build vendors its own copy of ggml; we reuse llama.cpp's ggml
# for the LLM side and let whisper.cpp build its own static ggml target.
# ---------------------------------------------------------------------------
set(THIRD_PARTY ${CMAKE_CURRENT_SOURCE_DIR}/third_party)

if(NOT EXISTS ${THIRD_PARTY}/llama.cpp/CMakeLists.txt)
    message(FATAL_ERROR "llama.cpp not found. Run scripts/fetch_native.sh first.")
endif()

set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory(${THIRD_PARTY}/llama.cpp ${CMAKE_BINARY_DIR}/llama.cpp)

add_library(jarvis_llm SHARED jarvis_llm.cpp)
target_include_directories(jarvis_llm PRIVATE ${THIRD_PARTY}/llama.cpp/include ${THIRD_PARTY}/llama.cpp/ggml/include)
target_link_libraries(jarvis_llm PRIVATE llama ggml android log)

# ---- Whisper (speech to text) --------------------------------------------
if(EXISTS ${THIRD_PARTY}/whisper.cpp/CMakeLists.txt)
    set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
    add_subdirectory(${THIRD_PARTY}/whisper.cpp ${CMAKE_BINARY_DIR}/whisper.cpp)

    add_library(jarvis_whisper SHARED jarvis_whisper.cpp)
    target_include_directories(jarvis_whisper PRIVATE ${THIRD_PARTY}/whisper.cpp/include ${THIRD_PARTY}/whisper.cpp/ggml/include)
    target_link_libraries(jarvis_whisper PRIVATE whisper android log)
endif()
