#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
NDK="${ANDROID_NDK_HOME:-${SDK:+$SDK/ndk/28.2.13676358}}"
BUILD="$ROOT/build/dvd-native"
SOURCE="$ROOT/third_party/sources"
READ_SRC="$SOURCE/libdvdread"
NAV_SRC="$SOURCE/libdvdnav"
BLURAY_SRC="$SOURCE/libbluray"
READ_REV="c7f373951bae9642e1ce1fbb2cd02f92c09756e0"
NAV_REV="38238caf599dc9405eddf1531c858c725015f776"
BLURAY_REV="8b4fb6e2562bb86601ea5a2c4140af6d8f3f1cf4"

[[ -n "$SDK" && -d "$SDK" ]] || { echo "Set ANDROID_HOME" >&2; exit 2; }
[[ -n "$NDK" && -d "$NDK" ]] || { echo "Set ANDROID_NDK_HOME" >&2; exit 2; }
for tool in git meson ninja pkg-config; do command -v "$tool" >/dev/null || { echo "Missing tool: $tool" >&2; exit 2; }; done

case "$(uname -s)" in
  Darwin) HOST_TAG="darwin-x86_64" ;;
  Linux) HOST_TAG="linux-x86_64" ;;
  *) echo "Unsupported host" >&2; exit 2 ;;
esac
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin"

clone_locked() {
  local repo="$1" revision="$2" dir="$3"
  if [[ ! -d "$dir/.git" ]]; then git clone "$repo" "$dir"; fi
  git -C "$dir" fetch --tags origin
  git -C "$dir" checkout --force --detach "$revision"
  git -C "$dir" clean -fdx
}

clone_locked https://code.videolan.org/videolan/libdvdread.git "$READ_REV" "$READ_SRC"
clone_locked https://code.videolan.org/videolan/libdvdnav.git "$NAV_REV" "$NAV_SRC"
clone_locked https://code.videolan.org/videolan/libbluray.git "$BLURAY_REV" "$BLURAY_SRC"
git -C "$BLURAY_SRC" submodule update --init --recursive contrib/libudfread

build_abi() {
  local abi="$1" target host
  case "$abi" in
    arm64-v8a) target="aarch64-linux-android24"; host="aarch64-linux-android" ;;
    armeabi-v7a) target="armv7a-linux-androideabi24"; host="arm-linux-androideabi" ;;
    *) echo "Unsupported ABI: $abi" >&2; exit 2 ;;
  esac
  local prefix="$BUILD/$abi/prefix"
  local read_build="$BUILD/$abi/libdvdread"
  local nav_build="$BUILD/$abi/libdvdnav"
  local bluray_build="$BUILD/$abi/libbluray"
  local cross="$BUILD/$abi/android.ini"
  rm -rf "$read_build" "$nav_build" "$bluray_build" "$prefix"
  mkdir -p "$read_build" "$nav_build" "$bluray_build" "$prefix"
  cat > "$cross" <<EOF
[binaries]
c = '$TOOLCHAIN/$target-clang'
ar = '$TOOLCHAIN/llvm-ar'
strip = '$TOOLCHAIN/llvm-strip'
pkg-config = 'pkg-config'

[host_machine]
system = 'android'
cpu_family = '$([[ "$abi" == "arm64-v8a" ]] && echo aarch64 || echo arm)'
cpu = '$([[ "$abi" == "arm64-v8a" ]] && echo armv8-a || echo armv7-a)'
endian = 'little'
EOF

  meson setup "$read_build" "$READ_SRC" --cross-file "$cross" --prefix "$prefix" --default-library static -Dlibdvdcss=disabled
  meson compile -C "$read_build"
  meson install -C "$read_build"

  PKG_CONFIG_PATH="$prefix/lib/pkgconfig" PKG_CONFIG_LIBDIR="$prefix/lib/pkgconfig" \
    meson setup "$nav_build" "$NAV_SRC" --cross-file "$cross" --prefix "$prefix" --default-library static -Denable_examples=false
  meson compile -C "$nav_build"
  meson install -C "$nav_build"

  meson setup "$bluray_build" "$BLURAY_SRC" --cross-file "$cross" --prefix "$prefix" --default-library static \
    -Denable_tools=false -Denable_devtools=false -Denable_examples=false -Dbdj_jar=disabled \
    -Dfontconfig=disabled -Dfreetype=disabled -Dlibxml2=disabled
  meson compile -C "$bluray_build"
  meson install -C "$bluray_build"

  test -s "$prefix/lib/libdvdread.a"
  test -s "$prefix/lib/libdvdnav.a"
  test -s "$prefix/lib/libbluray.a"
  echo "DVD native dependencies ready: $prefix"
}

build_abi arm64-v8a
build_abi armeabi-v7a
