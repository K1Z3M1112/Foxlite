cmake_minimum_required(VERSION 3.22.1)
project(native_runtime)

add_library(native_runtime SHARED
    native_runtime.cpp
    vulkan_bridge.cpp
)

find_library(log-lib log)
find_library(android-lib android)
find_library(vulkan-lib vulkan)

target_compile_features(native_runtime PRIVATE cxx_std_20)
target_include_directories(native_runtime PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(native_runtime ${log-lib} ${android-lib} ${vulkan-lib})
