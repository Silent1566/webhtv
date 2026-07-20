#!/bin/bash -e

v_sdk=11076708_latest
v_ndk=r28c
v_ndk_n=28.2.13676358
v_sdk_platform=37
v_sdk_build_tools=37.0.0

v_lua=5.2.4
v_unibreak=6.1
v_harfbuzz=13.0.1
v_fribidi=1.0.16
v_freetype=2.14.2
v_mbedtls=3.6.5
v_nghttp2=1.69.0
v_curl=8.21.0

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
dep_nghttp2=()
dep_curl=(mbedtls nghttp2)
dep_mpv=(ffmpeg libass lua libplacebo curl)
dep_mpv_android=(mpv)

v_ci_ffmpeg=8ae0b34901ba60a802f183ee75a250a9fc3e09a5
ci_tarball="prefix-ndk-${v_ndk}-lua-${v_lua}-unibreak-${v_unibreak}-harfbuzz-${v_harfbuzz}-fribidi-${v_fribidi}-freetype-${v_freetype}-mbedtls-${v_mbedtls}-nghttp2-${v_nghttp2}-curl-${v_curl}-ffmpeg-${v_ci_ffmpeg}.tgz"
