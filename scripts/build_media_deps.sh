#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  scripts/build_media_deps.sh [--clean] [--with-nextlib]

Builds the locked FongMi Media3 artifacts into third_party/maven so the app can
depend on normal Maven coordinates instead of embedding external source trees.

Options:
  --clean          Remove generated third_party/maven before publishing.
  --with-nextlib   Also prepare the locked FongMi/nextlib source checkout.
                  The app normally consumes nextlib-media3ext from Maven Central;
                  local nextlib publishing needs Android NDK/CMake/FFmpeg setup.
USAGE
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
LOCK_FILE="$ROOT_DIR/third_party/media-lock.json"
THIRD_PARTY_DIR="$ROOT_DIR/third_party"
SOURCE_DIR="$THIRD_PARTY_DIR/sources"
LOCAL_MAVEN="$THIRD_PARTY_DIR/maven"
MEDIA_DIR="$SOURCE_DIR/media"
NEXTLIB_DIR="$SOURCE_DIR/nextlib"

CLEAN=0
WITH_NEXTLIB=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --clean)
      CLEAN=1
      ;;
    --with-nextlib)
      WITH_NEXTLIB=1
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
  shift
done

json_get() {
  local object="$1"
  local field="$2"
  awk -v object="\"$object\"" -v field="\"$field\"" '
    $0 ~ object { in_object = 1; next }
    in_object && $0 ~ /^ *}/ { in_object = 0 }
    in_object && $0 ~ field {
      line = $0
      sub(/^.*: *"/, "", line)
      sub(/".*$/, "", line)
      print line
      exit
    }
  ' "$LOCK_FILE"
}

require_value() {
  local name="$1"
  local value="$2"
  if [[ -z "$value" ]]; then
    echo "Missing $name in $LOCK_FILE" >&2
    exit 1
  fi
}

read_local_property() {
  local key="$1"
  local file="$ROOT_DIR/local.properties"
  [[ -f "$file" ]] || return 0
  awk -F= -v key="$key" '$1 == key { print substr($0, length(key) + 2); exit }' "$file"
}

resolve_android_sdk() {
  local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -z "$sdk" ]]; then
    sdk="$(read_local_property sdk.dir)"
  fi
  if [[ -z "$sdk" || ! -d "$sdk/platforms" ]]; then
    echo "Android SDK not found. Set ANDROID_HOME/ANDROID_SDK_ROOT or sdk.dir in local.properties." >&2
    exit 1
  fi
  printf '%s\n' "$sdk"
}

version_catalog_value() {
  local key="$1"
  awk -F'"' -v key="$key" '$1 ~ "^" key " *= *" { print $2; exit }' "$ROOT_DIR/gradle/libs.versions.toml"
}

platform_api_exists() {
  local sdk="$1"
  local api="$2"
  [[ -d "$sdk/platforms/android-$api" ]] && return 0
  local platform
  for platform in "$sdk"/platforms/android-*; do
    [[ -f "$platform/source.properties" ]] || continue
    if grep -Eq "^AndroidVersion.ApiLevel=${api}(\\.0)?$" "$platform/source.properties"; then
      return 0
    fi
  done
  return 1
}

patch_gradle_number_property() {
  local file="$1"
  local key="$2"
  local value="$3"
  perl -0pi -e "s/${key} = [0-9]+/${key} = ${value}/" "$file"
}

patch_gradle_string_property() {
  local file="$1"
  local key="$2"
  local value="$3"
  perl -0pi -e "s/${key} = '[^']+'/${key} = '${value}'/" "$file"
}

patch_toml_string_value() {
  local file="$1"
  local key="$2"
  local value="$3"
  perl -0pi -e "s/${key} = \"[0-9.]+\"/${key} = \"${value}\"/" "$file"
}

clone_or_update() {
  local repo="$1"
  local branch="$2"
  local commit="$3"
  local dir="$4"
  mkdir -p "$(dirname "$dir")"
  if [[ -d "$dir/.git" ]]; then
    echo "Fetching $repo"
    git -C "$dir" fetch --tags --prune origin "$branch"
  else
    echo "Cloning $repo"
    git clone "$repo" "$dir"
  fi
  git -C "$dir" fetch --tags origin "$branch"
  git -C "$dir" checkout --force --detach "$commit"
  git -C "$dir" clean -fdx
}

latest_installed_build_tools() {
  local sdk="$1"
  local build_tools_dir="$sdk/build-tools"
  [[ -d "$build_tools_dir" ]] || return 0
  find "$build_tools_dir" -maxdepth 1 -mindepth 1 -type d -exec basename {} \; | sort -V | tail -n 1
}

insert_line_after_android_block() {
  local file="$1"
  local line="$2"
  local tmp="$file.tmp"
  awk -v insert_line="$line" '{ print; if ($0 == "android {") print insert_line }' "$file" > "$tmp"
  mv "$tmp" "$file"
}

add_media_jar_dependency() {
  local dependency="$1"
  local file="$MEDIA_DIR/missing_aar_type_workaround.gradle"
  local tmp="$file.tmp"
  if grep -q "\"$dependency\"" "$file"; then
    return 0
  fi
  awk -v dep="$dependency" '{ print; if ($0 ~ /"com.google.guava:guava",/) print "        \"" dep "\"," }' "$file" > "$tmp"
  mv "$tmp" "$file"
}

patch_media_release_version() {
  local version="$1"
  patch_gradle_string_property "$MEDIA_DIR/constants.gradle" "releaseVersion" "$version"
}

patch_media_build_tools() {
  local build_tools
  build_tools="$(latest_installed_build_tools "$ANDROID_HOME")"
  if [[ -z "$build_tools" ]]; then
    echo "No Android SDK Build Tools found under $ANDROID_HOME/build-tools." >&2
    exit 1
  fi
  insert_line_after_android_block "$MEDIA_DIR/common_config.gradle" "    buildToolsVersion \"$build_tools\""
  echo "Building locked Media3 with installed Build Tools $build_tools."
}

patch_media_pom_workaround() {
  add_media_jar_dependency "com.googlecode.juniversalchardet:juniversalchardet"
  add_media_jar_dependency "com.hierynomus:smbj"
  add_media_jar_dependency "org.brotli:dec"
}

apply_media_patches() {
  local patch_dir="$THIRD_PARTY_DIR/patches"
  local patch_file
  [[ -d "$patch_dir" ]] || return 0
  for patch_file in "$patch_dir"/media3-*.patch; do
    [[ -f "$patch_file" ]] || continue
    echo "Applying Media3 patch $(basename "$patch_file")"
    git -C "$MEDIA_DIR" apply --check "$patch_file"
    git -C "$MEDIA_DIR" apply "$patch_file"
  done
}

prepare_android_env() {
  ANDROID_HOME="$(resolve_android_sdk)"
  export ANDROID_HOME
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
  export ANDROID_USER_HOME="$ROOT_DIR/.gradle/android-user-home"
  unset ANDROID_SDK_HOME
  export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT_DIR/.gradle/media-deps}"
  mkdir -p "$ANDROID_USER_HOME" "$GRADLE_USER_HOME"
}

prepare_media_compile_sdk() {
  local media_compile
  local app_compile
  media_compile="$(awk -F'= *' '/compileSdkVersion/ { print $2; exit }' "$MEDIA_DIR/constants.gradle" | tr -dc '0-9')"
  app_compile="$(version_catalog_value compileSdk)"
  if [[ -z "$media_compile" ]]; then
    echo "Unable to read Media3 compileSdkVersion from $MEDIA_DIR/constants.gradle" >&2
    exit 1
  fi
  if platform_api_exists "$ANDROID_HOME" "$media_compile"; then
    return 0
  fi
  if [[ -n "$app_compile" && "$app_compile" =~ ^[0-9]+$ ]] && platform_api_exists "$ANDROID_HOME" "$app_compile"; then
    echo "Android SDK platform $media_compile is not installed; building locked Media3 with project compileSdk $app_compile."
    patch_gradle_number_property "$MEDIA_DIR/constants.gradle" "compileSdkVersion" "$app_compile"
    return 0
  fi
  echo "Android SDK platform $media_compile is required to build FongMi/media." >&2
  echo "Install platforms;android-$media_compile or install the project compileSdk platform $app_compile." >&2
  exit 1
}

prepare_nextlib_compile_sdk() {
  local app_compile
  app_compile="$(version_catalog_value compileSdk)"
  if [[ -n "$app_compile" && "$app_compile" =~ ^[0-9]+$ ]] && platform_api_exists "$ANDROID_HOME" "$app_compile"; then
    patch_toml_string_value "$NEXTLIB_DIR/gradle/libs.versions.toml" "androidCompileSdk" "$app_compile"
  fi
}

publish_media() {
  local media_repo media_branch media_commit media_version
  media_repo="$(json_get fongmi_media repo)"
  media_branch="$(json_get fongmi_media branch)"
  media_commit="$(json_get fongmi_media commit)"
  media_version="$(json_get fongmi_media version)"
  require_value fongmi_media.repo "$media_repo"
  require_value fongmi_media.branch "$media_branch"
  require_value fongmi_media.commit "$media_commit"
  require_value fongmi_media.version "$media_version"

  clone_or_update "$media_repo" "$media_branch" "$media_commit" "$MEDIA_DIR"
  apply_media_patches
  patch_media_release_version "$media_version"
  patch_media_pom_workaround
  prepare_media_compile_sdk
  patch_media_build_tools

  local modules=(
    lib-common
    lib-container
    lib-database
    lib-datasource
    lib-datasource-okhttp
    lib-datasource-rtmp
    lib-decoder
    lib-effect
    lib-exoplayer
    lib-exoplayer-dash
    lib-exoplayer-hls
    lib-exoplayer-rtsp
    lib-exoplayer-smoothstreaming
    lib-extractor
    lib-session
    lib-ui
    lib-ui-danmaku
  )
  local tasks=()
  local module
  for module in "${modules[@]}"; do
    tasks+=(":$module:publishReleasePublicationToMavenRepository")
  done

  echo "Publishing FongMi/media $media_version to $LOCAL_MAVEN"
  (cd "$MEDIA_DIR" && ./gradlew --no-daemon -PmavenRepo="$LOCAL_MAVEN" -PreleaseVersion="$media_version" "${tasks[@]}")
}

prepare_nextlib() {
  local repo branch commit version
  repo="$(json_get fongmi_nextlib repo)"
  branch="$(json_get fongmi_nextlib branch)"
  commit="$(json_get fongmi_nextlib commit)"
  version="$(json_get fongmi_nextlib version)"
  require_value fongmi_nextlib.repo "$repo"
  require_value fongmi_nextlib.branch "$branch"
  require_value fongmi_nextlib.commit "$commit"
  require_value fongmi_nextlib.version "$version"

  clone_or_update "$repo" "$branch" "$commit" "$NEXTLIB_DIR"
  prepare_nextlib_compile_sdk
  echo "Prepared FongMi/nextlib $version source at $NEXTLIB_DIR"
  echo "The app consumes io.github.anilbeesetti:nextlib-media3ext:$version from Maven Central by default."
  echo "Local nextlib publishing is intentionally not run here because it requires Android NDK/CMake and FFmpeg setup."
}

prepare_android_env
mkdir -p "$THIRD_PARTY_DIR"
if [[ "$CLEAN" == "1" ]]; then
  echo "Cleaning $LOCAL_MAVEN"
  rm -rf "$LOCAL_MAVEN"
fi
mkdir -p "$LOCAL_MAVEN"

publish_media
if [[ "$WITH_NEXTLIB" == "1" ]]; then
  prepare_nextlib
fi

echo "Done. Local Media3 artifacts are available under $LOCAL_MAVEN"
