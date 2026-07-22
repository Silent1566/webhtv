#!/usr/bin/env bash

set -euo pipefail

if [ -z "${CNB_TOKEN:-}" ]; then
  echo "::error title=CNB token missing::Configure CNB_TOKEN with repo-contents read/write permission."
  exit 1
fi

CNB_API_ENDPOINT="${CNB_API_ENDPOINT:-https://api.cnb.cool}"
CNB_WEB_ENDPOINT="${CNB_WEB_ENDPOINT:-https://cnb.cool}"
CNB_REPO_SLUG="${CNB_REPO_SLUG:-fish2035/webhtv-release}"
CNB_REPO_URL="${CNB_REPO_URL:-${CNB_WEB_ENDPOINT}/${CNB_REPO_SLUG}.git}"
CNB_TARGET_BRANCH="${CNB_TARGET_BRANCH:-main}"
CNB_RELEASE_TAG="${CNB_RELEASE_TAG:?CNB_RELEASE_TAG is required}"
CNB_RELEASE_TITLE="${CNB_RELEASE_TITLE:-${CNB_RELEASE_TAG#v}}"
CNB_RELEASE_NOTES="${CNB_RELEASE_NOTES:-GitHub Actions release ${CNB_RELEASE_TAG}}"
CNB_RELEASE_PRERELEASE="${CNB_RELEASE_PRERELEASE:-false}"
CNB_RELEASE_LATEST="${CNB_RELEASE_LATEST:-false}"
DIST_DIR="${DIST_DIR:-dist}"
WORKSPACE="${GITHUB_WORKSPACE:-$PWD}"

shopt -s nullglob
apk_files=("${DIST_DIR}"/*.apk)
json_files=("${DIST_DIR}"/*.json)
if [ "${#apk_files[@]}" -eq 0 ] || [ "${#json_files[@]}" -eq 0 ]; then
  echo "::error title=CNB assets missing::${DIST_DIR} must contain APK and JSON files."
  exit 1
fi

release_download_base="${CNB_WEB_ENDPOINT}/${CNB_REPO_SLUG}/-/releases/download/${CNB_RELEASE_TAG}"
for manifest in "${json_files[@]}"; do
  apk_name="$(basename "${manifest%.json}.apk")"
  if [ ! -f "${DIST_DIR}/${apk_name}" ]; then
    echo "::error title=APK missing::No APK matches ${manifest}."
    exit 1
  fi
  temp_manifest="$(mktemp)"
  jq --arg apk "${release_download_base}/${apk_name}" '.apk = $apk' "${manifest}" > "${temp_manifest}"
  mv "${temp_manifest}" "${manifest}"
done

auth_repo="$(printf '%s' "${CNB_REPO_URL}" | sed "s#^https://#https://cnb:${CNB_TOKEN}@#")"
clone_cnb() {
  local attempt max_attempts delay
  max_attempts=4
  delay=15
  for attempt in $(seq 1 "${max_attempts}"); do
    echo "CNB clone attempt ${attempt}/${max_attempts}"
    rm -rf cnb-mirror
    if timeout 180s git -c http.lowSpeedLimit=1000 -c http.lowSpeedTime=60 clone --depth 1 --branch "${CNB_TARGET_BRANCH}" "${auth_repo}" cnb-mirror; then
      return 0
    fi
    if [ "${attempt}" -lt "${max_attempts}" ]; then
      echo "::warning title=CNB clone retry::Clone failed, retrying in ${delay}s."
      sleep "${delay}"
      delay=$((delay * 2))
    fi
  done
  echo "::error title=CNB clone failed::Cannot connect to ${CNB_REPO_URL}."
  return 1
}

clone_cnb
mkdir -p cnb-mirror/apk
cp "${json_files[@]}" cnb-mirror/apk/
git -C cnb-mirror config user.name "github-actions[bot]"
git -C cnb-mirror config user.email "github-actions[bot]@users.noreply.github.com"
git -C cnb-mirror add apk/
if ! git -C cnb-mirror diff --cached --quiet; then
  git -C cnb-mirror commit -m "${CNB_RELEASE_TAG}"
  git -C cnb-mirror push origin "HEAD:${CNB_TARGET_BRANCH}"
else
  echo "CNB manifests already match ${CNB_RELEASE_TAG}."
fi

api_headers=(
  -H "Authorization: Bearer ${CNB_TOKEN}"
  -H "Accept: application/vnd.cnb.api+json"
)
release_url="${CNB_API_ENDPOINT}/${CNB_REPO_SLUG}/-/releases"
release_status="$(curl -sS -o cnb-release.json -w '%{http_code}' "${api_headers[@]}" "${release_url}/tags/${CNB_RELEASE_TAG}")"
release_existed=false
if [ "${release_status}" = "200" ]; then
  release_existed=true
  echo "CNB Release ${CNB_RELEASE_TAG} already exists."
else
  jq -n \
    --arg name "${CNB_RELEASE_TITLE}" \
    --arg body "${CNB_RELEASE_NOTES}" \
    --arg tag "${CNB_RELEASE_TAG}" \
    --arg target "${CNB_TARGET_BRANCH}" \
    --arg latest "${CNB_RELEASE_LATEST}" \
    --argjson prerelease "${CNB_RELEASE_PRERELEASE}" \
    '{name: $name, body: $body, is_draft: false, is_prerelease: $prerelease, make_latest: $latest, tag_name: $tag, target_commitish: $target}' \
    > cnb-release-request.json
  release_status="$(curl -sS -o cnb-release.json -w '%{http_code}' \
    -X POST "${api_headers[@]}" \
    -H "Content-Type: application/json" \
    --data-binary @cnb-release-request.json \
    "${release_url}")"
  if [[ ! "${release_status}" =~ ^2 ]]; then
    echo "::error title=CNB Release creation failed::HTTP ${release_status}: $(cat cnb-release.json)"
    exit 1
  fi
  echo "Created CNB Release ${CNB_RELEASE_TAG}."
fi

plugin_attachments=""
for apk in "${apk_files[@]}"; do
  if [ -n "${plugin_attachments}" ]; then
    plugin_attachments+=","
  fi
  plugin_attachments+="./${apk}"
done

run_attachments_plugin() {
  local operation="$1"
  docker run --rm \
    -e TZ=Asia/Shanghai \
    -e CNB_TOKEN="${CNB_TOKEN}" \
    -e CNB_API_ENDPOINT="${CNB_API_ENDPOINT}" \
    -e CNB_WEB_ENDPOINT="${CNB_WEB_ENDPOINT}" \
    -e CNB_REPO_SLUG="${CNB_REPO_SLUG}" \
    -e PLUGIN_TAG="${CNB_RELEASE_TAG}" \
    -e PLUGIN_TYPE="${operation}" \
    -e PLUGIN_ATTACHMENTS="${plugin_attachments}" \
    -v "${WORKSPACE}:${WORKSPACE}" \
    -w "${WORKSPACE}" \
    cnbcool/attachments:latest \
    | sed '/^##\[set-output FILES=/d'
}

if [ "${release_existed}" = "true" ]; then
  if ! run_attachments_plugin DELETE; then
    echo "::warning title=CNB attachment cleanup::No matching old APK attachment was deleted; upload will still be attempted."
  fi
fi
run_attachments_plugin UPLOAD

for apk in "${apk_files[@]}"; do
  echo "CNB APK: ${release_download_base}/$(basename "${apk}")"
done
