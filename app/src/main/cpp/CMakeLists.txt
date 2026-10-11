cmake_minimum_required(VERSION 3.22.1)
project(milo_llama CXX C)

# 16 KB page-size devices (Galaxy S25 line and newer):
# every native library must link with 16 KB ELF alignment
# or Android refuses the install outright (found the hard
# way in v0.13.0/v0.14.0 — the engine lib shipped 4 KB
# aligned). Applies to every target below, including
# ggml's bundled OpenMP runtime. A CI step re-verifies
# the alignment inside the finished APK.
add_link_options("-Wl,-z,max-page-size=16384")

# llama.cpp is cloned into ./llama.cpp by CI (pinned tag
# b11541) — it is not vendored into this repository.
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

add_subdirectory(llama.cpp)

add_library(milo_llama SHARED milo_llama.cpp)
target_include_directories(milo_llama PRIVATE
    llama.cpp/include
    llama.cpp/ggml/include
)
target_link_libraries(milo_llama llama android log)
