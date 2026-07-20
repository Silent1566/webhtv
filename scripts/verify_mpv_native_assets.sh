#!/bin/bash
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOCK_FILE="$ROOT/third_party/mpv-native-lock.json"
REQUIRE_ELF=0

usage() {
  cat <<'EOF'
Usage: scripts/verify_mpv_native_assets.sh [--require-elf]

Validate the committed MPV/FFmpeg assets for both Android ARM ABIs against
third_party/mpv-native-lock.json. Presence, ABI and embedded version checks are
always performed. ELF SONAME/DT_NEEDED checks run when llvm-readelf/readelf is
available; --require-elf makes the absence of that tool an error.
EOF
}

die() {
  printf 'verify_mpv_native_assets: %s\n' "$*" >&2
  exit 1
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --require-elf) REQUIRE_ELF=1 ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown option: $1" ;;
  esac
  shift
done

[ -f "$LOCK_FILE" ] || die "missing lock file: $LOCK_FILE"
command -v python3 >/dev/null 2>&1 || die "missing command: python3"
command -v file >/dev/null 2>&1 || die "missing command: file"

eval "$(python3 - "$LOCK_FILE" <<'PY'
import json
import shlex
import sys

with open(sys.argv[1], encoding="utf-8") as fh:
    lock = json.load(fh)

sources = lock["sources"]
values = {
    "NDK_VERSION": lock["android"]["ndk_version"],
    "MPV_VERSION": sources["mpv"]["version"],
    "LIBPLACEBO_VERSION": sources["libplacebo"]["version"],
    "CURL_VERSION": sources.get("curl", {}).get("version", ""),
}
for key, value in values.items():
    print(f"{key}={shlex.quote(str(value))}")
PY
)"

find_sdk_root() {
  if [ -n "${ANDROID_HOME:-}" ]; then
    printf '%s\n' "$ANDROID_HOME"
    return
  fi
  if [ -n "${ANDROID_SDK_ROOT:-}" ]; then
    printf '%s\n' "$ANDROID_SDK_ROOT"
    return
  fi
  if [ -f "$ROOT/local.properties" ]; then
    python3 - "$ROOT/local.properties" <<'PY'
import sys
from pathlib import Path

for line in Path(sys.argv[1]).read_text(encoding="utf-8").splitlines():
    if line.startswith("sdk.dir="):
        print(line.split("=", 1)[1].replace("\\\\", "\\").replace("\\:", ":"))
        break
PY
    return
  fi
  case "$(uname -s)" in
    Darwin) printf '%s\n' "$HOME/Library/Android/sdk" ;;
    *) printf '%s\n' "$HOME/Android/Sdk" ;;
  esac
}

find_readelf() {
  if [ -n "${READELF:-}" ]; then
    if [ -x "$READELF" ]; then
      printf '%s\n' "$READELF"
      return
    fi
    if command -v "$READELF" >/dev/null 2>&1; then
      command -v "$READELF"
      return
    fi
  fi
  if command -v llvm-readelf >/dev/null 2>&1; then
    command -v llvm-readelf
    return
  fi
  if command -v readelf >/dev/null 2>&1; then
    command -v readelf
    return
  fi
  local sdk_root ndk_root candidate
  sdk_root="$(find_sdk_root)"
  for ndk_root in "${ANDROID_NDK_HOME:-}" "$sdk_root/ndk/$NDK_VERSION"; do
    [ -n "$ndk_root" ] || continue
    for candidate in "$ndk_root"/toolchains/llvm/prebuilt/*/bin/llvm-readelf; do
      if [ -x "$candidate" ]; then
        printf '%s\n' "$candidate"
        return
      fi
    done
  done
}

find_strings() {
  if [ -n "${STRINGS:-}" ]; then
    if [ -x "$STRINGS" ]; then
      printf '%s\n' "$STRINGS"
      return
    fi
    if command -v "$STRINGS" >/dev/null 2>&1; then
      command -v "$STRINGS"
      return
    fi
  fi
  if command -v llvm-strings >/dev/null 2>&1; then
    command -v llvm-strings
    return
  fi
  command -v strings 2>/dev/null || true
}

READELF_BIN="$(find_readelf || true)"
STRINGS_BIN="$(find_strings)"
[ -n "$STRINGS_BIN" ] || die "missing strings/llvm-strings"
if [ -z "$READELF_BIN" ] && [ "$REQUIRE_ELF" -eq 1 ]; then
  die "missing llvm-readelf/readelf; install NDK $NDK_VERSION or binutils"
fi

contains_string() {
  local file_path="$1"
  local expected="$2"
  if ! "$STRINGS_BIN" "$file_path" | grep -F "$expected" >/dev/null; then
    die "missing embedded string in $file_path: $expected"
  fi
}

verify_abi() {
  local abi="$1"
  local flavor="$2"
  local file_pattern="$3"
  local directory="$ROOT/app/src/$flavor/assets/mpv-libs/$abi"
  local required name file_path file_info dynamic soname mpv_dynamic

  required="libc++_shared.so libmpv.so libmvcodec.so libmvdevice.so libmvfilter.so libmvformat.so libmvutil.so libmwresample.so libmwscale.so libplayer.so"
  [ -d "$directory" ] || die "missing asset directory: $directory"
  for name in $required; do
    [ -f "$directory/$name" ] || die "missing $abi asset: $name"
  done
  for file_path in "$directory"/*.so; do
    name="$(basename "$file_path")"
    case " $required " in
      *" $name "*) ;;
      *) die "unexpected $abi native asset: $name" ;;
    esac
  done
  [ ! -e "$directory/libcurl.so" ] || die "libcurl must remain static: $directory/libcurl.so"
  [ ! -e "$directory/libnghttp2.so" ] || die "nghttp2 must remain static: $directory/libnghttp2.so"

  file_info="$(file "$directory/libmpv.so")"
  printf '%s\n' "$file_info" | grep -E "$file_pattern" >/dev/null || die "unexpected $abi ELF type: $file_info"

  contains_string "$directory/libmpv.so" "mpv v$MPV_VERSION"
  contains_string "$directory/libmpv.so" "v$LIBPLACEBO_VERSION"
  contains_string "$directory/libmpv.so" "WebHTV stream_cb controls enabled"
  if [ -n "$CURL_VERSION" ]; then
    contains_string "$directory/libmpv.so" "libcurl/$CURL_VERSION"
    contains_string "$directory/libmpv.so" "HTTP2"
  fi

  if [ -n "$READELF_BIN" ]; then
    for file_path in "$directory"/*.so; do
      dynamic="$("$READELF_BIN" -d "$file_path")" || die "unable to read ELF dynamic section: $file_path"
      if printf '%s\n' "$dynamic" | grep -Eq 'Shared library: \[lib(av|sw).+\.so\]'; then
        die "unrenamed FFmpeg dependency in $file_path"
      fi
      if printf '%s\n' "$dynamic" | grep -Eq 'Shared library: \[lib(curl|nghttp2|mbed[^]]*)\.so'; then
        die "network dependency must remain static in $file_path"
      fi
      name="$(basename "$file_path")"
      if [ "$name" != "libc++_shared.so" ]; then
        soname="$(printf '%s\n' "$dynamic" | sed -n 's/.*Library soname: \[\([^]]*\)\].*/\1/p')"
        [ "$soname" = "$name" ] || die "SONAME mismatch in $file_path: ${soname:-missing}"
      fi
    done

    mpv_dynamic="$("$READELF_BIN" -d "$directory/libmpv.so")"
    for name in libmvcodec.so libmvdevice.so libmvfilter.so libmvformat.so libmvutil.so libmwresample.so libmwscale.so libvulkan.so; do
      printf '%s\n' "$mpv_dynamic" | grep -F "Shared library: [$name]" >/dev/null || die "libmpv.so does not depend on $name for $abi"
    done
  fi

  printf 'Verified MPV native assets: %s\n' "$abi"
}

verify_abi arm64-v8a arm64_v8a 'ARM aarch64|ARM64'
verify_abi armeabi-v7a armeabi_v7a 'ARM(,| )|ARM EABI'

if [ -z "$READELF_BIN" ]; then
  printf 'Warning: ELF SONAME/DT_NEEDED checks skipped; rerun with --require-elf and NDK/binutils installed.\n' >&2
else
  printf 'ELF checks used: %s\n' "$READELF_BIN"
fi
printf 'All committed MPV native assets match lock versions and packaging rules.\n'
