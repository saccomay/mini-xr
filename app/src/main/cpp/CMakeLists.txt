cmake_minimum_required(VERSION 3.22.1)

project("qr_tracker_native")

add_library(${CMAKE_PROJECT_NAME} SHARED
        qr_tracker_native.cpp)

# Tích hợp thư viện log và cơ bản của Android NDK
find_library(log-lib log)

# OpenXR loader qua Prefab (cung cấp openxr_loader.so + headers chuẩn)
find_package(OpenXR REQUIRED CONFIG)

# Include headers: dùng local include trước (chứa Android XR extensions)
# vì header Android XR có thêm các struct QR tracking không có trong Khronos release
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE
        -DXR_USE_PLATFORM_ANDROID
        -std=c++17
)

target_link_libraries(${CMAKE_PROJECT_NAME}
        ${log-lib}
        OpenXR::openxr_loader
)
