#!/usr/bin/env bash
# 低配机器（2核/8GB）优化构建脚本
# 用法：bash build_release_optimized.sh [可选: --no-skip-lint] [可选: --no-stable]
# 默认：跳过 release lintVital（不影响 APK 内容与签名）+ 固定 BUILD_TIME（源码未变时可命中
# 增量缓存，两次 R8 直接 UP-TO-DATE）。首次编译仍会全量跑 R8，之后重复编译秒级完成。
# 正式出包若要真实构建时间戳，加 --no-stable 并跑一次全量编译。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
ANDROID_HOME="${ANDROID_HOME:-D:/AndroidBuildEnv/sdk}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
JAVA_HOME="${JAVA_HOME:-D:/AndroidBuildEnv/jdk-21.0.2}"
export ANDROID_HOME ANDROID_SDK_ROOT JAVA_HOME

SKIP_LINT=true
STABLE_BUILD=true
for arg in "$@"; do
  case "$arg" in
    --no-skip-lint) SKIP_LINT=false ;;
    --no-stable)    STABLE_BUILD=false ;;
  esac
done

LOG="D:/AndroidBuildEnv/build_optimized_$(date +%Y%m%d_%H%M%S).log"

echo "Building with: JAVA_HOME=$JAVA_HOME  skipLint=$SKIP_LINT  stableBuild=$STABLE_BUILD"
echo "Log: $LOG"

ARGS=()
[ "$SKIP_LINT" = "true" ] && ARGS+=("-PskipLint=true")
[ "$STABLE_BUILD" = "true" ] && ARGS+=("-PstableBuild=true")

cd "$ROOT_DIR"
./gradlew "${ARGS[@]}" \
    assembleLeanbackArmeabi_v7aRelease \
    assembleMobileArm64_v8aRelease 2>&1 | tee "$LOG"

echo "=== Result ==="
grep -E "BUILD SUCCESSFUL|BUILD FAILED|OutOfMemory" "$LOG" | tail -n 5

# === 编译后自动清理旧的编译产物 ===
# 只清 Debug 变体、lint 产物与历史日志；保留两个 release 变体的增量缓存，
# 这样下次重复编译仍可命中 UP-TO-DATE，不会拖慢后续构建。
cd "$ROOT_DIR"
if [ -d app/build ]; then
    find app/build/intermediates -maxdepth 2 -type d \( -iname "*debug*" -o -iname "*lint*" \) -prune -exec rm -rf {} + 2>/dev/null || true
    rm -rf app/build/intermediates/lint* app/build/tmp/lint* app/build/outputs/logs 2>/dev/null || true
    echo "=== Cleaned old artifacts (debug/lint) ==="
fi
# 历史构建日志只保留最近 3 份
ls -t D:/AndroidBuildEnv/build_optimized_*.log 2>/dev/null | tail -n +4 | xargs -r rm -f 2>/dev/null || true
