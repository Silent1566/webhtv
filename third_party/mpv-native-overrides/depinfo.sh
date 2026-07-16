#!/bin/bash -e

v_sdk=11076708_latest
v_ndk=r28c
v_ndk_n=28.2.13676358
v_sdk_platform=37
v_sdk_build_tools=37.0.0

v_lua=5.2.4
v_unibreak=6.1
v_harfbuzz=12.3.2
v_fribidi=1.0.16
v_freetype=2.14.1
v_mbedtls=3.6.5

dep_mbedtls=()
dep_dav1d=()
dep_ffmpeg=(mbedtls dav1d)
dep_freetype2=()
dep_fribidi=()
dep_harfbuzz=()
dep_unibreak=()
dep_libass=(freetype2 fribidi harfbuzz unibreak)
dep_lua=()
dep_shaderc=()
dep_libplacebo=(shaderc)
dep_mpv=(ffmpeg libass lua libplacebo)
dep_mpv_android=(mpv)

v_ci_ffmpeg=5ba2525c7affc29cbd99e6266946b382d3fffe8b
ci_tarball="prefix-ndk-${v_ndk}-lua-${v_lua}-unibreak-${v_unibreak}-harfbuzz-${v_harfbuzz}-fribidi-${v_fribidi}-freetype-${v_freetype}-mbedtls-${v_mbedtls}-ffmpeg-${v_ci_ffmpeg}.tgz"
