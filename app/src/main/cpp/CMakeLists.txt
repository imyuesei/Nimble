cmake_minimum_required(VERSION 3.22.1)
project(nimble C CXX)

# Map the Android ABI to the Cactus platform folder fetched by scripts/fetch_engine.sh.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(ENGINE_PLATFORM android-arm64)
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(ENGINE_PLATFORM android-armv7)
else()
    message(FATAL_ERROR "No Cactus engine build for ABI ${ANDROID_ABI}")
endif()

set(ENGINE_DIR "${NIMBLE_ENGINE_ROOT}/${ENGINE_PLATFORM}")
file(GLOB ENGINE_STATIC "${ENGINE_DIR}/*.a")
file(GLOB ENGINE_SHARED "${ENGINE_DIR}/*.so")

add_library(nimble SHARED nimble_jni.c)
# The engine is C++ inside, so link with the C++ driver to pull in libc++.
set_target_properties(nimble PROPERTIES LINKER_LANGUAGE CXX)

if(ENGINE_STATIC)
    message(STATUS "Linking Cactus engine (static): ${ENGINE_STATIC}")
    target_link_libraries(nimble ${ENGINE_STATIC})
elseif(ENGINE_SHARED)
    message(STATUS "Linking Cactus engine (shared): ${ENGINE_SHARED}")
    list(GET ENGINE_SHARED 0 ENGINE_SO)
    add_library(cactus SHARED IMPORTED)
    set_target_properties(cactus PROPERTIES IMPORTED_LOCATION "${ENGINE_SO}")
    target_link_libraries(nimble cactus)
else()
    message(FATAL_ERROR "No Cactus engine library in ${ENGINE_DIR}. Run scripts/fetch_engine.sh")
endif()

find_library(log-lib log)
target_link_libraries(nimble ${log-lib} android m dl)
# 16 KB page alignment (required for Android 15+ devices with 16 KB pages).
target_link_options(nimble PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,--gc-sections")
