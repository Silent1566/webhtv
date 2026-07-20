#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOCK_FILE="$ROOT/third_party/mpv-native-lock.json"
OVERRIDE_DIR="$ROOT/third_party/mpv-native-overrides"
MPV_DISC_PATCH="$ROOT/third_party/patches/mpv-stream-cb-disc-controls.patch"
WORK_DIR="${MPV_NATIVE_WORK_DIR:-$ROOT/build/mpv-native}"
ABI="arm64-v8a"
JOBS="${MPV_NATIVE_JOBS:-}"
INSTALL_ASSETS=0
PREPARE_ONLY=0
INCREMENTAL=0

usage() {
  cat <<'EOF'
Usage: scripts/build_mpv_native.sh [options]

Rebuild the pinned MPV/FFmpeg native stack. Normal Gradle and GitHub Actions
builds do not call this script; they reuse the committed .so assets.

Options:
  --abi arm64-v8a        Build the validated 64-bit stack (default)
  --abi armeabi-v7a      Build the 32-bit stack
  --abi all              Build both ARM ABIs
  --install              Replace the matching app/src/*/assets/mpv-libs files
  --prepare-only         Download and pin all sources without compiling
  --incremental          Keep build prefixes instead of doing a clean rebuild
  --lock-file PATH       Use an alternate dependency lock for compatibility tests
  --work-dir PATH        Build/cache directory (default: build/mpv-native)
  --jobs N               Parallel compiler jobs
  -h, --help             Show this help

Examples:
  scripts/build_mpv_native.sh --abi arm64-v8a --install
  scripts/build_mpv_native.sh --abi all --install --jobs 8
EOF
}

log() {
  printf '\n==> %s\n' "$*"
}

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "missing command: $1"
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --abi)
      [ "$#" -ge 2 ] || die "--abi requires a value"
      ABI="$2"
      shift 2
      ;;
    --install)
      INSTALL_ASSETS=1
      shift
      ;;
    --prepare-only)
      PREPARE_ONLY=1
      shift
      ;;
    --incremental)
      INCREMENTAL=1
      shift
      ;;
    --lock-file)
      [ "$#" -ge 2 ] || die "--lock-file requires a value"
      LOCK_FILE="$2"
      shift 2
      ;;
    --work-dir)
      [ "$#" -ge 2 ] || die "--work-dir requires a value"
      WORK_DIR="$2"
      shift 2
      ;;
    --jobs)
      [ "$#" -ge 2 ] || die "--jobs requires a value"
      JOBS="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "unknown option: $1"
      ;;
  esac
done

case "$ABI" in
  arm64-v8a|armeabi-v7a|all) ;;
  *) die "unsupported ABI: $ABI" ;;
esac

need_cmd git
need_cmd curl
need_cmd tar
need_cmd make
need_cmd python3
need_cmd pkg-config
need_cmd perl

eval "$(python3 - "$LOCK_FILE" <<'PY'
import json
import shlex
import sys

data = json.load(open(sys.argv[1], encoding="utf-8"))
values = {
    "BUILDER_REPO": data["builder"]["repo"],
    "BUILDER_REV": data["builder"]["commit"],
    "NDK_VERSION": data["android"]["ndk_version"],
    "NDK_LABEL": data["android"]["ndk_label"],
    "MESON_VERSION": data["python_tools"]["meson"],
    "NINJA_VERSION": data["python_tools"]["ninja"],
}
for name, source in data["sources"].items():
    prefix = name.upper().replace("-", "_")
    for key in ("repo", "commit", "url", "sha256", "version", "describe_tag", "history_depth"):
        if key in source:
            values[f"{prefix}_{key.upper()}"] = str(source[key])
    values[f"{prefix}_SUBMODULES"] = "1" if source.get("submodules") else "0"
for key, value in values.items():
    print(f"{key}={shlex.quote(value)}")
PY
)"

ENABLE_LIBCURL=0
if [ -n "${CURL_URL:-}${CURL_COMMIT:-}" ] || [ -n "${NGHTTP2_URL:-}${NGHTTP2_COMMIT:-}" ]; then
  [ -n "${CURL_URL:-}${CURL_COMMIT:-}" ] || die "curl source is required when nghttp2 is enabled"
  [ -n "${NGHTTP2_URL:-}${NGHTTP2_COMMIT:-}" ] || die "nghttp2 source is required when curl is enabled"
  ENABLE_LIBCURL=1
fi

detect_sdk_root() {
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
        value = line.split("=", 1)[1]
        print(value.replace("\\\\", "\\").replace("\\:", ":"))
        break
PY
    return
  fi
  case "$(uname -s)" in
    Darwin) printf '%s\n' "$HOME/Library/Android/sdk" ;;
    *) printf '%s\n' "$HOME/Android/Sdk" ;;
  esac
}

SDK_ROOT="$(detect_sdk_root)"
NDK_ROOT="${ANDROID_NDK_HOME:-$SDK_ROOT/ndk/$NDK_VERSION}"
[ -f "$NDK_ROOT/source.properties" ] || die "Android NDK $NDK_VERSION not found at $NDK_ROOT. Install it with sdkmanager \"ndk;$NDK_VERSION\" or set ANDROID_NDK_HOME."
grep -q "Pkg.Revision = $NDK_VERSION" "$NDK_ROOT/source.properties" || die "NDK revision mismatch: expected $NDK_VERSION at $NDK_ROOT"

case "$(uname -s)" in
  Darwin) HOST_TAG=darwin-x86_64 ;;
  Linux) HOST_TAG=linux-x86_64 ;;
  *) die "supported hosts are macOS and Linux" ;;
esac

TOOLCHAIN="$NDK_ROOT/toolchains/llvm/prebuilt/$HOST_TAG"
[ -d "$TOOLCHAIN" ] || die "NDK toolchain not found: $TOOLCHAIN"
OBJCOPY="$TOOLCHAIN/bin/llvm-objcopy"
STRIP="$TOOLCHAIN/bin/llvm-strip"
READELF="$TOOLCHAIN/bin/llvm-readelf"
[ -x "$OBJCOPY" ] && [ -x "$STRIP" ] && [ -x "$READELF" ] || die "NDK LLVM tools are incomplete"

if [ -z "$JOBS" ]; then
  if command -v nproc >/dev/null 2>&1; then
    JOBS="$(nproc)"
  elif command -v sysctl >/dev/null 2>&1; then
    JOBS="$(sysctl -n hw.ncpu)"
  else
    JOBS=4
  fi
fi
case "$JOBS" in
  ''|*[!0-9]*) die "invalid --jobs value: $JOBS" ;;
esac

mkdir -p "$WORK_DIR" "$WORK_DIR/downloads"
FRAMEWORK_DIR="$WORK_DIR/mpv-android"
BUILDSCRIPTS="$FRAMEWORK_DIR/buildscripts"
VENV="$WORK_DIR/python-tools"

checkout_repo() {
  local name="$1"
  local repo="$2"
  local revision="$3"
  local directory="$4"
  local submodules="${5:-0}"

  log "Preparing $name @ ${revision:0:12}"
  if [ ! -d "$directory/.git" ]; then
    rm -rf "$directory"
    mkdir -p "$directory"
    git -C "$directory" init -q
    git -C "$directory" remote add origin "$repo"
  elif [ "$(git -C "$directory" remote get-url origin)" != "$repo" ]; then
    git -C "$directory" remote set-url origin "$repo"
  fi
  if ! git -C "$directory" cat-file -e "$revision^{commit}" 2>/dev/null; then
    git -C "$directory" fetch --depth 1 origin "$revision"
  fi
  git -C "$directory" checkout -q --force --detach "$revision"
  if [ "$submodules" = "1" ]; then
    git -C "$directory" submodule sync --recursive
    if ! git -C "$directory" submodule update --init --recursive --depth 1; then
      git -C "$directory" submodule update --init --recursive
    fi
  fi
  local actual
  actual="$(git -C "$directory" rev-parse HEAD)"
  [ "$actual" = "$revision" ] || die "$name revision mismatch: $actual"
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

extract_archive() {
  local name="$1"
  local url="$2"
  local expected_sha="$3"
  local directory="$4"
  local archive="$WORK_DIR/downloads/${url##*/}"
  local marker="$directory/.webhtv-source-sha256"

  if [ ! -f "$archive" ] || [ "$(sha256_file "$archive")" != "$expected_sha" ]; then
    log "Downloading $name"
    rm -f "$archive"
    curl --location --fail --retry 3 --output "$archive" "$url"
  fi
  local actual_sha
  actual_sha="$(sha256_file "$archive")"
  [ "$actual_sha" = "$expected_sha" ] || die "$name SHA-256 mismatch: $actual_sha"
  if [ ! -f "$marker" ] || [ "$(cat "$marker")" != "$expected_sha" ]; then
    rm -rf "$directory"
    mkdir -p "$directory"
    tar -xzf "$archive" -C "$directory" --strip-components=1
    printf '%s\n' "$expected_sha" >"$marker"
  fi
}

prepare_python_tools() {
  if [ ! -x "$VENV/bin/python" ]; then
    log "Creating isolated Meson/Ninja environment"
    python3 -m venv "$VENV"
  fi
  "$VENV/bin/python" -m pip install --disable-pip-version-check --quiet \
    "meson==$MESON_VERSION" "ninja==$NINJA_VERSION"
  export PATH="$VENV/bin:$PATH"
  meson --version | grep -qx "$MESON_VERSION" || die "Meson version mismatch"
}

prepare_framework() {
  checkout_repo "mpv-android build framework" "$BUILDER_REPO" "$BUILDER_REV" "$FRAMEWORK_DIR"
  mkdir -p "$BUILDSCRIPTS/sdk"
  rm -rf "$BUILDSCRIPTS/sdk/android-ndk-$NDK_LABEL"
  ln -s "$NDK_ROOT" "$BUILDSCRIPTS/sdk/android-ndk-$NDK_LABEL"

  cp "$OVERRIDE_DIR/depinfo.sh" "$BUILDSCRIPTS/include/depinfo.sh"
  cp "$OVERRIDE_DIR/path.sh" "$BUILDSCRIPTS/include/path.sh"
  cp "$OVERRIDE_DIR/libass.sh" "$BUILDSCRIPTS/scripts/libass.sh"
  cp "$OVERRIDE_DIR/lua.sh" "$BUILDSCRIPTS/scripts/lua.sh"
  cp "$OVERRIDE_DIR/libplacebo.sh" "$BUILDSCRIPTS/scripts/libplacebo.sh"
  cp "$OVERRIDE_DIR/nghttp2.sh" "$BUILDSCRIPTS/scripts/nghttp2.sh"
  cp "$OVERRIDE_DIR/curl.sh" "$BUILDSCRIPTS/scripts/curl.sh"
  cp "$OVERRIDE_DIR/mpv.sh" "$BUILDSCRIPTS/scripts/mpv.sh"
  local lock_hash
  lock_hash="$(sha256_file "$LOCK_FILE")"
  printf '\n# WebHTV wrapper cache identity: exact selected lock file.\nci_tarball="prefix-webhtv-%s.tgz"\n' \
    "$lock_hash" >> "$BUILDSCRIPTS/include/depinfo.sh"
  chmod +x "$BUILDSCRIPTS/scripts/libass.sh" "$BUILDSCRIPTS/scripts/lua.sh" \
    "$BUILDSCRIPTS/scripts/libplacebo.sh" "$BUILDSCRIPTS/scripts/nghttp2.sh" \
    "$BUILDSCRIPTS/scripts/curl.sh" "$BUILDSCRIPTS/scripts/mpv.sh"
  python3 - "$BUILDSCRIPTS/buildall.sh" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
old = "\t$BUILDSCRIPT build\n"
new = "\t$BUILDSCRIPT build || return $?\n"
if old not in text and new not in text:
    raise SystemExit("unexpected upstream buildall.sh layout")
path.write_text(text.replace(old, new), encoding="utf-8")
PY
}

prepare_sources() {
  local deps="$BUILDSCRIPTS/deps"
  mkdir -p "$deps"
  checkout_repo mbedtls "$MBEDTLS_REPO" "$MBEDTLS_COMMIT" "$deps/mbedtls" "$MBEDTLS_SUBMODULES"
  "$VENV/bin/python" -m pip install --disable-pip-version-check --quiet \
    -r "$deps/mbedtls/scripts/basic.requirements.txt"
  checkout_repo dav1d "$DAV1D_REPO" "$DAV1D_COMMIT" "$deps/dav1d"
  checkout_repo FFmpeg "$FFMPEG_REPO" "$FFMPEG_COMMIT" "$deps/ffmpeg"
  checkout_repo FreeType "$FREETYPE2_REPO" "$FREETYPE2_COMMIT" "$deps/freetype2"
  checkout_repo FriBidi "$FRIBIDI_REPO" "$FRIBIDI_COMMIT" "$deps/fribidi"
  checkout_repo HarfBuzz "$HARFBUZZ_REPO" "$HARFBUZZ_COMMIT" "$deps/harfbuzz"
  extract_archive libunibreak "$UNIBREAK_URL" "$UNIBREAK_SHA256" "$deps/unibreak"
  checkout_repo libass "$LIBASS_REPO" "$LIBASS_COMMIT" "$deps/libass"
  extract_archive Lua "$LUA_URL" "$LUA_SHA256" "$deps/lua"
  checkout_repo libplacebo "$LIBPLACEBO_REPO" "$LIBPLACEBO_COMMIT" "$deps/libplacebo" "$LIBPLACEBO_SUBMODULES"
  if [ "$ENABLE_LIBCURL" -eq 1 ]; then
    extract_archive nghttp2 "$NGHTTP2_URL" "$NGHTTP2_SHA256" "$deps/nghttp2"
    extract_archive curl "$CURL_URL" "$CURL_SHA256" "$deps/curl"
  fi
  checkout_repo mpv "$MPV_REPO" "$MPV_COMMIT" "$deps/mpv"
  # The initial exact-commit fetch is shallow. Fetch enough ancestry and the
  # release tag so MPV embeds the version string recorded by the selected lock.
  if [ "$(git -C "$deps/mpv" describe --abbrev=9 --tags --match "$MPV_DESCRIBE_TAG" HEAD 2>/dev/null || true)" != "v$MPV_VERSION" ]; then
    git -C "$deps/mpv" fetch --deepen="$MPV_HISTORY_DEPTH" origin "$MPV_COMMIT"
    git -C "$deps/mpv" fetch --depth=1 origin \
      "refs/tags/$MPV_DESCRIBE_TAG:refs/tags/$MPV_DESCRIBE_TAG"
  fi
  [ "$(git -C "$deps/mpv" describe --abbrev=9 --tags --match "$MPV_DESCRIBE_TAG" HEAD)" = "v$MPV_VERSION" ] || die "MPV describe version mismatch"
  [ -f "$MPV_DISC_PATCH" ] || die "missing MPV disc controls patch: $MPV_DISC_PATCH"
  git -C "$deps/mpv" apply --check "$MPV_DISC_PATCH"
  git -C "$deps/mpv" apply "$MPV_DISC_PATCH"
  mkdir -p "$deps/shaderc"
  printf '%s\n' "shaderc is supplied by Android NDK $NDK_VERSION" >"$deps/shaderc/README.webhtv"
}

patch_dynamic_names() {
  local directory="$1"
  local file tmp patched
  for file in "$directory"/*.so; do
    tmp="$directory/.dynstr.$(basename "$file")"
    patched="$file.patched"
    "$OBJCOPY" --dump-section ".dynstr=$tmp" "$file"
    python3 - "$tmp" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
data = path.read_bytes()
replacements = {
    b"libavcodec.so": b"libmvcodec.so",
    b"libavdevice.so": b"libmvdevice.so",
    b"libavfilter.so": b"libmvfilter.so",
    b"libavformat.so": b"libmvformat.so",
    b"libavutil.so": b"libmvutil.so",
    b"libswresample.so": b"libmwresample.so",
    b"libswscale.so": b"libmwscale.so",
}
for old, new in replacements.items():
    if len(old) != len(new):
        raise SystemExit(f"replacement length mismatch: {old!r} -> {new!r}")
    data = data.replace(old, new)
path.write_bytes(data)
PY
    "$OBJCOPY" --update-section ".dynstr=$tmp" "$file" "$patched"
    mv "$patched" "$file"
    rm -f "$tmp"
  done
}

verify_directory() {
  local directory="$1"
  local required=(
    libc++_shared.so libmpv.so libmvcodec.so libmvdevice.so libmvfilter.so
    libmvformat.so libmvutil.so libmwresample.so libmwscale.so
  )
  local file dynamic name soname
  for name in "${required[@]}"; do
    [ -f "$directory/$name" ] || die "missing native output: $directory/$name"
  done
  for file in "$directory"/*.so; do
    dynamic="$("$READELF" -d "$file")"
    if printf '%s\n' "$dynamic" | grep -Eq 'Shared library: \[lib(av|sw).+\.so\]'; then
      die "unrenamed FFmpeg dependency in $file"
    fi
    name="$(basename "$file")"
    if [ "$name" != "libc++_shared.so" ]; then
      soname="$(printf '%s\n' "$dynamic" | sed -n 's/.*Library soname: \[\([^]]*\)\].*/\1/p')"
      [ "$soname" = "$name" ] || die "SONAME mismatch in $file: $soname"
    fi
  done
  dynamic="$("$READELF" -d "$directory/libmpv.so")"
  for name in libmvcodec.so libmvdevice.so libmvfilter.so libmvformat.so libmvutil.so libmwresample.so libmwscale.so libvulkan.so; do
    printf '%s\n' "$dynamic" | grep -Fq "Shared library: [$name]" || die "libmpv.so does not depend on $name"
  done
  local version_strings
  version_strings="$(strings "$directory/libmpv.so")"
  grep -Fq "mpv v$MPV_VERSION" <<<"$version_strings" || die "unexpected MPV version in $directory/libmpv.so"
  grep -Fq "v$LIBPLACEBO_VERSION" <<<"$version_strings" || die "unexpected libplacebo version in $directory/libmpv.so"
  grep -Fq "WebHTV stream_cb controls enabled" <<<"$version_strings" || die "MPV stream_cb disc controls patch missing from $directory/libmpv.so"
  if [ "$ENABLE_LIBCURL" -eq 1 ]; then
    grep -Fq "libcurl/$CURL_VERSION" <<<"$version_strings" || die "libcurl $CURL_VERSION missing from $directory/libmpv.so"
    grep -Fq "HTTP2" <<<"$version_strings" || die "HTTP/2 support missing from $directory/libmpv.so"
  fi
}

verify_curl_ffmpeg_compat() {
  [ "$ENABLE_LIBCURL" -eq 1 ] || return
  python3 - "$BUILDSCRIPTS/deps/ffmpeg/libavformat/version_major.h" \
    "$BUILDSCRIPTS/deps/ffmpeg/libavformat/version.h" <<'PY'
import re
import sys
from pathlib import Path

text = "\n".join(Path(path).read_text(encoding="utf-8") for path in sys.argv[1:])
values = {}
for key in ("MAJOR", "MINOR", "MICRO"):
    match = re.search(rf"LIBAVFORMAT_VERSION_{key}\s+(\d+)", text)
    if not match:
        raise SystemExit(f"missing libavformat {key.lower()} version")
    values[key] = int(match.group(1))

version = (values["MAJOR"], values["MINOR"], values["MICRO"])
safe = version[0] > 62 or (
    version[0] == 62 and (
        version[1] >= 15 or
        (version[1] == 12 and version[2] >= 102) or
        (version[1] == 3 and version[2] >= 103)
    )
)
if not safe:
    raise SystemExit(
        "libcurl requires the nested AVIO cleanup fix; "
        f"libavformat {version[0]}.{version[1]}.{version[2]} is too old"
    )
PY
}

stage_abi() {
  local arch="$1"
  local abi="$2"
  local prefix_name="$3"
  local cxx_abi="$4"
  local source="$BUILDSCRIPTS/prefix/$prefix_name/lib"
  local output="$WORK_DIR/output/$abi"
  local assets

  case "$abi" in
    arm64-v8a) assets="$ROOT/app/src/arm64_v8a/assets/mpv-libs/arm64-v8a" ;;
    armeabi-v7a) assets="$ROOT/app/src/armeabi_v7a/assets/mpv-libs/armeabi-v7a" ;;
  esac

  rm -rf "$output"
  mkdir -p "$output"
  for name in libmpv.so libavcodec.so libavdevice.so libavfilter.so libavformat.so libavutil.so libswresample.so libswscale.so; do
    [ -f "$source/$name" ] || die "build output missing: $source/$name"
    cp "$source/$name" "$output/$name"
  done
  patch_dynamic_names "$output"
  mv "$output/libavcodec.so" "$output/libmvcodec.so"
  mv "$output/libavdevice.so" "$output/libmvdevice.so"
  mv "$output/libavfilter.so" "$output/libmvfilter.so"
  mv "$output/libavformat.so" "$output/libmvformat.so"
  mv "$output/libavutil.so" "$output/libmvutil.so"
  mv "$output/libswresample.so" "$output/libmwresample.so"
  mv "$output/libswscale.so" "$output/libmwscale.so"
  cp "$TOOLCHAIN/sysroot/usr/lib/$cxx_abi/libc++_shared.so" "$output/libc++_shared.so"
  "$STRIP" --strip-unneeded "$output"/*.so
  chmod 644 "$output"/*.so
  verify_directory "$output"

  if [ "$INSTALL_ASSETS" -eq 1 ]; then
    log "Installing $abi native assets"
    mkdir -p "$assets"
    cp "$output"/*.so "$assets/"
    verify_directory "$assets"
  fi
  log "$abi output ready: $output"
}

build_abi() {
  local abi="$1"
  local arch prefix_name cxx_abi
  case "$abi" in
    arm64-v8a)
      arch=arm64
      prefix_name=arm64
      cxx_abi=aarch64-linux-android
      ;;
    armeabi-v7a)
      arch=armv7l
      prefix_name=armv7l
      cxx_abi=arm-linux-androideabi
      ;;
  esac

  log "Building pinned MPV native stack for $abi"
  if [ "$INCREMENTAL" -eq 0 ]; then
    rm -rf "$BUILDSCRIPTS/prefix/$prefix_name"
  fi
  export cores="$JOBS"
  local targets=(mbedtls unibreak dav1d ffmpeg freetype2 fribidi harfbuzz libass lua shaderc libplacebo)
  if [ "$ENABLE_LIBCURL" -eq 1 ]; then
    targets+=(nghttp2 curl)
    export WEBHTV_MPV_LIBCURL=enabled
  else
    export WEBHTV_MPV_LIBCURL=auto
  fi
  targets+=(mpv)
  local target
  for target in "${targets[@]}"; do
    if [ "$INCREMENTAL" -eq 1 ]; then
      bash "$BUILDSCRIPTS/buildall.sh" -n --arch "$arch" "$target"
    else
      bash "$BUILDSCRIPTS/buildall.sh" -n --clean --arch "$arch" "$target"
    fi
    case "$target" in
      mbedtls) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libmbedtls.a" ] ;;
      unibreak) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libunibreak.a" ] ;;
      dav1d) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libdav1d.a" ] ;;
      ffmpeg) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libavcodec.so" ] ;;
      freetype2) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libfreetype.a" ] ;;
      fribidi) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libfribidi.a" ] ;;
      harfbuzz) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libharfbuzz.a" ] ;;
      libass) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libass.a" ] ;;
      lua) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/liblua.a" ] ;;
      shaderc) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libshaderc_combined.a" ] ;;
      libplacebo) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libplacebo.a" ] ;;
      nghttp2) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libnghttp2.a" ] ;;
      curl) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libcurl.a" ] ;;
      mpv) [ -f "$BUILDSCRIPTS/prefix/$prefix_name/lib/libmpv.so" ] ;;
    esac || die "$target did not produce its expected $abi output"
    [ "$target" != "ffmpeg" ] || verify_curl_ffmpeg_compat
  done
  stage_abi "$arch" "$abi" "$prefix_name" "$cxx_abi"
}

log "Using lock file $LOCK_FILE"
log "Using Android NDK $NDK_VERSION at $NDK_ROOT"
prepare_python_tools
prepare_framework
prepare_sources

if [ "$PREPARE_ONLY" -eq 1 ]; then
  log "All sources are downloaded and pinned under $WORK_DIR"
  exit 0
fi

case "$ABI" in
  arm64-v8a) build_abi arm64-v8a ;;
  armeabi-v7a) build_abi armeabi-v7a ;;
  all)
    build_abi arm64-v8a
    build_abi armeabi-v7a
    ;;
esac

log "MPV native build completed"
if [ "$INSTALL_ASSETS" -eq 1 ]; then
  printf '%s\n' "The committed libplayer.so was preserved. Build the app normally with Gradle."
else
  printf '%s\n' "Review output under $WORK_DIR/output, then rerun with --install to update app assets."
fi
