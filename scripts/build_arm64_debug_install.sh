#!/usr/bin/env bash
# 打包 Mobile Arm64_v8a Debug APK 并推送到 Android 模拟器/设备。
# 用法:
#   bash scripts/build_arm64_debug_install.sh [--serial 192.168.50.3:5555] [--adb /path/to/adb] [--skip-check]
# 示例:
#   bash scripts/build_arm64_debug_install.sh
#   bash scripts/build_arm64_debug_install.sh --serial 192.168.50.3:5559
#
# 流程: 检查 Gradle 守护进程是否空闲 -> 打包 :app:assembleMobileArm64_v8aDebug
#       -> adb 连接/安装 -> 校验模拟器上包存在。

set -euo pipefail

SERIAL="192.168.50.3:5555"
ADB=""
SKIP_IDLE_CHECK=0

usage() {
  sed -n '2,9p' "$0" | sed 's/^# //; s/^#$//'
  exit 0
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -s|--serial) SERIAL="${2:?missing serial}"; shift 2 ;;
    -a|--adb) ADB="${2:?missing adb path}"; shift 2 ;;
    --skip-check) SKIP_IDLE_CHECK=1; shift ;;
    -h|--help) usage ;;
    *) echo "未知参数: $1" >&2; usage ;;
  esac
done

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$ROOT_DIR"

if [[ -z "$ADB" ]]; then
  for candidate in \
    "$HOME/android-sdk/platform-tools/adb" \
    "$HOME/Android/Sdk/platform-tools/adb" \
    "/usr/local/android-sdk/platform-tools/adb" \
    "adb"; do
    if command -v "$candidate" >/dev/null 2>&1 || [[ -x "$candidate" ]]; then
      ADB="$candidate"
      break
    fi
  done
fi
if [[ -z "$ADB" ]]; then
  echo "❌ 未找到 adb，请用 --adb 指定路径" >&2
  exit 1
fi

echo "==> adb: $ADB"
echo "==> 设备: $SERIAL"

if [[ "$SKIP_IDLE_CHECK" -eq 0 ]]; then
  echo "==> 检查 Gradle 守护进程是否空闲（避免与其他打包任务冲突）..."
  STATUS_OUTPUT="$(./gradlew --status 2>&1 || true)"
  BUSY_PIDS="$(echo "$STATUS_OUTPUT" | awk '$2 == "BUSY" {print $1}')"
  if [[ -n "$BUSY_PIDS" ]]; then
    echo "❌ 检测到 Gradle 守护进程正在忙（PID: $BUSY_PIDS），可能有其他打包任务在进行。" >&2
    echo "   请等待其结束后再运行，或用 --skip-check 跳过此检查。" >&2
    exit 1
  fi
  echo "==> Gradle 守护进程空闲，可以开始打包"
fi

echo "==> 开始打包 :app:assembleMobileArm64_v8aDebug ..."
./gradlew :app:assembleMobileArm64_v8aDebug

APK="app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk"
if [[ ! -f "$APK" ]]; then
  echo "❌ 打包结束但未找到 APK: $APK" >&2
  exit 1
fi
echo "==> APK: $APK ($(du -h "$APK" | cut -f1))"

echo "==> 连接设备 $SERIAL ..."
"$ADB" connect "$SERIAL" >/dev/null 2>&1 || true

echo "==> 安装 APK 到 $SERIAL ..."
"$ADB" -s "$SERIAL" install -r "$APK"

echo "==> 校验安装 ..."
PKG="com.silent.android.webhtv"
if "$ADB" -s "$SERIAL" shell "pm list packages | grep -q '$PKG'"; then
  echo "✅ 安装成功: $PKG 已存在于 $SERIAL"
else
  echo "❌ 安装后未在设备上找到 $PKG" >&2
  exit 1
fi

echo "✅ 全部完成: 打包并安装到 $SERIAL"
