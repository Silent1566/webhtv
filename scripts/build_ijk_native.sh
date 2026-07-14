#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
SOURCE_DIR="${IJK_BUILD_DIR:-$ROOT_DIR/third_party/sources/ijkplayer-android}"
IJK_REPO="https://github.com/ShikinChen/ijkplayer-android.git"
IJK_REVISION="be89479c77c52acfb023d3b3acefccc5d8b9a101"
FF4_CONFIG_REVISION="96598d75"
FFMPEG_REPO="https://github.com/ShikinChen/FFmpeg.git"
FFMPEG_TAG="ff4.0--ijk0.8.8--20210426--001"
ABI="arm64-v8a"
INSTALL=0
CLEAN=0

usage() {
  cat <<'USAGE'
Usage: scripts/build_ijk_native.sh [options]

Options:
  --abi arm64-v8a|armeabi-v7a  ABI to build (default: arm64-v8a)
  --install                    Copy generated .so files into app/src/main/jniLibs
  --clean                      Remove the locked source checkout before building
  -h, --help                   Show this help

Environment:
  ANDROID_HOME / ANDROID_SDK_ROOT  Android SDK root
  ANDROID_NDK_HOME                 NDK root for the selected ABI
  IJK_BUILD_DIR                    Optional source/build directory

Recommended NDK:
  arm64-v8a     27.2.12479018
  armeabi-v7a   21.4.7075529 (Ubuntu recommended)
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --abi) ABI="${2:?Missing ABI}"; shift ;;
    --install) INSTALL=1 ;;
    --clean) CLEAN=1 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 1 ;;
  esac
  shift
done

case "$ABI" in
  arm64-v8a) IJK_ARCH="arm64"; EXPECTED_NDK="27.2.12479018" ;;
  armeabi-v7a) IJK_ARCH="armv7a"; EXPECTED_NDK="21.4.7075529" ;;
  *) echo "Unsupported ABI: $ABI" >&2; exit 1 ;;
esac

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK_ROOT" && -f "$ROOT_DIR/local.properties" ]]; then
  SDK_ROOT="$(awk -F= '$1 == "sdk.dir" { print substr($0, 9); exit }' "$ROOT_DIR/local.properties")"
fi
if [[ -z "$SDK_ROOT" || ! -d "$SDK_ROOT" ]]; then
  echo "Android SDK not found. Set ANDROID_HOME or ANDROID_SDK_ROOT." >&2
  exit 1
fi

NDK_ROOT="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-$SDK_ROOT/ndk/$EXPECTED_NDK}}"
if [[ ! -x "$NDK_ROOT/ndk-build" ]]; then
  echo "Android NDK not found: $NDK_ROOT" >&2
  echo "Install it with: $SDK_ROOT/cmdline-tools/latest/bin/sdkmanager \"ndk;$EXPECTED_NDK\"" >&2
  exit 1
fi

for tool in git make python3; do
  command -v "$tool" >/dev/null || { echo "Missing tool: $tool" >&2; exit 1; }
done
if ! command -v yasm >/dev/null && ! command -v nasm >/dev/null; then
  echo "Missing assembler: install yasm (macOS: brew install yasm; Ubuntu: apt install yasm)." >&2
  exit 1
fi

if [[ $CLEAN -eq 1 ]]; then rm -rf "$SOURCE_DIR"; fi
mkdir -p "$(dirname "$SOURCE_DIR")"
if [[ ! -d "$SOURCE_DIR/.git" ]]; then git clone "$IJK_REPO" "$SOURCE_DIR"; fi
git -C "$SOURCE_DIR" fetch --tags origin
git -C "$SOURCE_DIR" checkout --force --detach "$IJK_REVISION"
git -C "$SOURCE_DIR" clean -fdx

# Keep the newer Android/NDK build fixes, but restore the proven FFmpeg 4.0
# configuration used by WebHTV's IJK ABI.
git -C "$SOURCE_DIR" show "$FF4_CONFIG_REVISION:init-android.sh" > "$SOURCE_DIR/init-android.sh"
git -C "$SOURCE_DIR" show "$FF4_CONFIG_REVISION:config/module.sh" > "$SOURCE_DIR/config/module.sh"
sed -i.bak "s#https://github.com/Bilibili/FFmpeg.git#$FFMPEG_REPO#g" "$SOURCE_DIR/init-android.sh"
sed -i.bak "s#IJK_FFMPEG_COMMIT=.*#IJK_FFMPEG_COMMIT=$FFMPEG_TAG#" "$SOURCE_DIR/init-android.sh"
rm -f "$SOURCE_DIR/init-android.sh.bak"

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK="$SDK_ROOT"
export ANDROID_NDK="$NDK_ROOT"
export ANDROID_NDK_HOME="$NDK_ROOT"

cd "$SOURCE_DIR"
./init-android.sh
./init-android-openssl.sh
cd android/contrib
./compile-openssl.sh "$IJK_ARCH"
./compile-ffmpeg.sh "$IJK_ARCH"
cd ..
./compile-ijk.sh "$IJK_ARCH"

OUTPUT_DIR="$SOURCE_DIR/android/ijkplayer/ijkplayer-$IJK_ARCH/src/main/libs/$ABI"
if [[ ! -d "$OUTPUT_DIR" ]]; then
  echo "IJK output directory not found: $OUTPUT_DIR" >&2
  exit 1
fi

for name in libijkffmpeg.so libijksdl.so libijkplayer.so; do
  file="$OUTPUT_DIR/$name"
  [[ -s "$file" ]] || { echo "Missing output: $file" >&2; exit 1; }
  file "$file"
done

if [[ $INSTALL -eq 1 ]]; then
  DEST="$ROOT_DIR/app/src/main/jniLibs/$ABI"
  mkdir -p "$DEST"
  cp "$OUTPUT_DIR/libijkffmpeg.so" "$OUTPUT_DIR/libijksdl.so" "$OUTPUT_DIR/libijkplayer.so" "$DEST/"
  echo "Installed IJK libraries into $DEST"
else
  echo "IJK libraries are ready in $OUTPUT_DIR"
  echo "Re-run with --install to copy them into the app."
fi
