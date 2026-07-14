#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/Users/macbookpro/Downloads/bizhi/android-sdk}}"
NDK="${ANDROID_NDK_HOME:-$ANDROID_SDK/ndk/28.2.13676358}"

case "$(uname -s)" in
  Darwin) HOST_TAG="darwin-x86_64" ;;
  Linux) HOST_TAG="linux-x86_64" ;;
  *) echo "Unsupported host: $(uname -s)" >&2; exit 2 ;;
esac

TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin"
SYSROOT="$(cd "$TOOLCHAIN/.." && pwd)/sysroot"
SRC_DIR="$ROOT/third_party/mpv-player-jni/src"
MPV_INCLUDE="$ROOT/third_party/mpv-player-jni/include"

if [[ ! -x "$TOOLCHAIN/aarch64-linux-android24-clang++" ]]; then
  echo "NDK clang++ not found under: $TOOLCHAIN" >&2
  exit 2
fi

SOURCES=(
  "$SRC_DIR/main.cpp"
  "$SRC_DIR/render.cpp"
  "$SRC_DIR/log.cpp"
  "$SRC_DIR/jni_utils.cpp"
  "$SRC_DIR/property.cpp"
  "$SRC_DIR/event.cpp"
  "$SRC_DIR/thumbnail.cpp"
  "$SRC_DIR/iso_dvd.cpp"
)

build_abi() {
  local abi="$1"
  local flavor="$2"
  local cxx="$3"
  local assets="$ROOT/app/src/$flavor/assets/mpv-libs/$abi"
  local ffmpeg="$ROOT/third_party/sources/nextlib/ffmpeg/build/$abi"
  local cxx_lib_abi
  local libcxx_shared
  local out="$assets/libplayer.so"
  local tmp="$out.tmp"
  local dvd="$ROOT/build/dvd-native/$abi/prefix"

  case "$abi" in
    arm64-v8a) cxx_lib_abi="aarch64-linux-android" ;;
    armeabi-v7a) cxx_lib_abi="arm-linux-androideabi" ;;
    *) echo "Unsupported ABI: $abi" >&2; exit 2 ;;
  esac
  libcxx_shared="$SYSROOT/usr/lib/$cxx_lib_abi/libc++_shared.so"

  if [[ ! -d "$assets" ]]; then
    echo "Missing MPV asset directory: $assets" >&2
    exit 2
  fi
  if [[ ! -d "$ffmpeg/include" ]]; then
    echo "Missing FFmpeg include directory: $ffmpeg/include" >&2
    exit 2
  fi
  if [[ ! -s "$dvd/lib/libdvdnav.a" || ! -s "$dvd/lib/libdvdread.a" || ! -s "$dvd/lib/libbluray.a" ]]; then
    echo "Missing DVD native dependencies for $abi. Run scripts/build_dvd_deps.sh first." >&2
    exit 2
  fi

  echo "Building libplayer.so for $abi"
  "$TOOLCHAIN/$cxx" \
    -fPIC -shared -O2 -std=c++11 -Werror \
    -I"$MPV_INCLUDE" \
    -I"$ffmpeg/include" \
    -I"$dvd/include" \
    "${SOURCES[@]}" \
    -L"$assets" \
    -Wl,-rpath-link,"$assets" \
    -Wl,-soname,libplayer.so \
    -lmwscale -lmvcodec -lmpv "$dvd/lib/libdvdnav.a" "$dvd/lib/libdvdread.a" "$dvd/lib/libbluray.a" -llog -latomic -ldl -lz -lm "$libcxx_shared" \
    -nostdlib++ \
    -stdlib=libc++ \
    -o "$tmp"
  "$TOOLCHAIN/llvm-strip" --strip-unneeded "$tmp"
  mv "$tmp" "$out"
  chmod 644 "$out"
}

build_abi "arm64-v8a" "arm64_v8a" "aarch64-linux-android24-clang++"
build_abi "armeabi-v7a" "armeabi_v7a" "armv7a-linux-androideabi24-clang++"
