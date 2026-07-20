# WebHomeTV

WebHomeTV 是基于 [FongMi](https://github.com/FongMi/TV) / CatVod 生态二次开发的 Android 影音应用,保留原有点播、直播、Spider、解析、投屏、本地 HTTP 服务等能力,并重点增强了 **WebHome 自定义首页**、**App Native SDK**、**管理页面**、**远程托管**、**WebHome 扩展**、**登录态学习/同步**、**网盘链接检测**、**站点健康排序**、**观影记录同步** 和 **Nostr/TMDB 推荐首页**。

项目的核心目标不是替换 CSP/Spider 体系,而是让 CSP 站点首页变成一个真正可开发的网页应用:开发者用 HTML/CSS/JavaScript 定制首页,再通过 App 暴露的 Native 能力完成搜索、播放、跨域请求、资源代理、最近观看、网盘检测和状态同步。

### 增强功能

- **网盘检测**:内置网盘分享链接有效性检测,WebHome 和本地 HTTP API 均可调用。
- **站点健康排序**:自动学习站点搜索、详情和播放成功率,搜索与换源优先使用更可用的站点;站点弹窗默认保留用户配置顺序,可在弹窗内单独开启健康排序。
- **管理页面**:在 App 内启动局域网浏览器管理页 `/m`,可管理本机或远端设备文件、登录态、同步目录、站点注入、接口、壳代理、搜索和推送,运行期间通过前台服务保活。
- **远程托管**:通过自建 Cloudflare/Deno/Vercel/Go/Rust 中转服务绑定多台 WebHTV 设备,支持设备状态、远程搜索/推送、接口配置、主页设置、一键同步和最近日志;Go/Rust 版支持 WebSocket 实时通道,不支持时自动回退 HTTP 轮询。部署说明和二进制见 [远程托管中转服务器文档及二进制](serverless)。
- **一键同步**:在同一局域网设备间同步配置、站源数据(Jar/脚本保存数据)、登录态、WebHome 数据、搜索记录、观看历史、收藏和应用设置,每项可单独勾选。
- **站点注入**:添加自定义 WebHome 或通用 CSP 站点,主列表显示核心摘要和快捷操作,新增/修改在独立表单中维护启用状态、插入位置、首页、搜索和换源行为;顶部“识别”可粘贴单个或多个松散站点 JSON 片段并自动归类追加;WebHome 站点级扩展可直接填写扩展 URL / JSON,也可选择本地 JS/CSS/JSON 自动生成配置。
- **WebHome 扩展**:给真实网页注入用户脚本,主列表显示扩展摘要和状态,新增/修改在独立表单中配置本地文件、远程链接/manifest、直接代码、表单生成或 JSON;匹配范围默认从当前点播配置的 WebHome 站点弹窗多选,也可切换到 CSP key 正则;提供调试工作台用于 Web 预览、Console/Network/Elements 和代码保存预览。
- **观影记录同步**:增强功能中提供独立总览页,包含总开关、本机 API 修改开关、远端同步源和 Webhook 上报。爬虫可通过 `/api/playback/current` 读取当前播放记录,也可在用户开启修改后调用 `/api/playback/progress`、`/api/playback/progress/batch` 或 `/api/playback/progress/delete` 写入/清理本地进度;App 也可从用户配置的远端 API 拉取批量记录合并到本地历史。完整协议见 `webhome-devkit/docs/应用完整开发文档.md` 的“观影记录同步”章节。
- **登录态学习**:用户手动开启后学习 Cookie、Token、接口 Jar 网盘登录文件等登录态路径,待确认项可在管理页查看/编辑,并可参与一键同步。
- **APP 代理**:配置代理地址和域名匹配规则,可按当前站点自动建议代理域名,用于改善特定站点、接口或播放链路的网络访问。
- **调试日志**:本机和局域网日志查看入口,便于排查播放、代理、站源和 WebHome 相关问题。

以上能力集中在设置页的"增强功能"入口,手机端和电视端均为独立设置页。

## 效果演示

https://github.com/user-attachments/assets/984c274f-8a9b-4857-b641-d251e061f5cc

演示视频对应的站点配置(Nostr/TMDB 推荐首页):

```json
{
  "key": "Nostr",
  "name": "Nostr推荐",
  "type": 3,
  "api": "csp_Nostr",
  "homePage": "https://www.252035.xyz/xs/tvbox/nostr.html"
}
```

## 文档

完整开发说明见 [**应用完整开发文档.md**](webhome-devkit/docs/应用完整开发文档.md),包含:

- App 配置字段(点播、解析、直播、样式)
- Spider 开发,JS/Python Spider 运行时
- 本地 HTTP 服务端点总览
- WebHome SDK 全部方法的参数和返回值
- 透明背景、电视端遥控器 UX、性能最佳实践
- 网盘检测 API 和站点健康排序
- 观影记录同步、Webhook 上报和爬虫 HTTP API
- 管理页面和局域网 HTTP 能力
- 远程托管部署、绑定流程和能力边界
- WebHome 扩展脚本开发
- 登录态学习与同步
- PanSou 集成、Nostr 首页实现要点
- 隐藏功能和使用技巧
- Android Intent、DLNA、MediaSession
- CORS、Cookie 和网络策略

WebHome 主页、扩展、模板、示例和 AI skills 统一放在 [webhome-devkit/](webhome-devkit/) （附 [独立CNB仓库](https://cnb.cool/fish2018/ext)）：

- 扩展脚本开发指南见 [webhome-devkit/README.md](webhome-devkit/README.md)。
- 扩展示例见 [webhome-devkit/examples/extensions/](webhome-devkit/examples/extensions/)。
- 主页示例见 [webhome-devkit/examples/homepages/](webhome-devkit/examples/homepages/)。
- 模板见 [webhome-devkit/templates/](webhome-devkit/templates/)。
- AI 编程客户端如何接入和复用 Skills,见 [webhome-devkit/skills/](webhome-devkit/skills/)。

配置文件和示例 HTML 放在同一服务器目录时,`homePage` 可直接写相对路径。

社区内容:

- [网友自制分享](https://github.com/fish2018/webhtv/issues/13)

## 构建

本节按“新机器 clone 后直接复制命令打包”为目标维护。当前分支使用较新的 Android/Gradle/Media3/MPV native 组合，环境不满足时最常见的失败点是 JDK、Android SDK 37、NDK 和依赖下载网络。

### 环境要求

- JDK 21。不要使用 JDK 17；当前 `sourceCompatibility` / `targetCompatibility` 均为 Java 21。
- Android SDK Platform 37 和 Build Tools 37.0.0。当前 `compileSdk=37`、`minSdk=24`、`targetSdk=28`。
- Android NDK 28.2.13676358。普通 Gradle 打包会直接使用仓库内置的 MPV native assets 和 `libplayer.so`。`scripts/build_mpv_player_jni.sh` 只重建 JNI 桥接库 `libplayer.so`，不会重编 `libmpv.so`、FFmpeg 或 libplacebo。
- 使用仓库内置 Gradle Wrapper：Gradle 9.5.1，Android Gradle Plugin 9.2.1。
- 能访问 Maven Central、Google Maven、Gradle Plugin Portal 和 JitPack。仓库内已带定制 Media3、nextlib 和本地 AAR，但普通 Android 依赖仍需要联网下载。

macOS/Linux 可用以下命令确认版本：

```bash
java -version
bash gradlew --version
```

如果依赖下载需要代理，先在当前终端设置：

```bash
export https_proxy=http://127.0.0.1:7897
export http_proxy=http://127.0.0.1:7897
export all_proxy=socks5://127.0.0.1:7897
```

### 从零 clone 到打包

先安装或确认 Android SDK。Android Studio 打开项目时通常会自动生成 `local.properties`；命令行构建需要进入仓库根目录后手动创建。macOS 默认 SDK 路径示例：

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
```

Linux 常见路径是 `$HOME/Android/Sdk`，Windows 使用 Android Studio 打开项目或创建 `local.properties`，内容类似 `sdk.dir=C\:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk`。

如 SDK 未安装 API 37/Build Tools/NDK，可用 Android Studio SDK Manager 安装，或使用命令行工具：

```bash
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
  "platform-tools" \
  "platforms;android-37.0" \
  "build-tools;37.0.0" \
  "ndk;28.2.13676358"
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
```

clone 仓库后直接打 debug 包：

```bash
git clone https://github.com/fish2018/webhtv.git
cd webhtv
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
bash gradlew :app:assembleMobileArm64_v8aDebug :app:assembleLeanbackArmeabi_v7aDebug :app:assembleLeanbackArm64_v8aDebug
```

如果构建指定开发分支，在 clone 后切换到对应分支再打包：

```bash
git fetch origin
git switch feature/android-mpv-player
bash gradlew clean
bash gradlew :app:assembleMobileArm64_v8aDebug
```

已 clone 仓库更新当前分支：

```bash
git fetch origin
git pull --ff-only
bash gradlew clean
```

### 常用本地打包命令

debug 包适合本地安装测试，构建速度更快：

```bash
bash gradlew :app:assembleMobileArm64_v8aDebug
bash gradlew :app:assembleMobileArmeabi_v7aDebug
bash gradlew :app:assembleLeanbackArm64_v8aDebug
bash gradlew :app:assembleLeanbackArmeabi_v7aDebug
```

release 包适合分发测试或正式发布：

```bash
bash gradlew :app:assembleMobileArm64_v8aRelease
bash gradlew :app:assembleMobileArmeabi_v7aRelease
bash gradlew :app:assembleLeanbackArm64_v8aRelease
bash gradlew :app:assembleLeanbackArmeabi_v7aRelease
```

临时验证“仅 Release 包可用”的接口时，可以关闭 R8/资源压缩，获得接近 debug 的构建速度：

```bash
bash gradlew :app:assembleMobileArm64_v8aRelease -PfastRelease=true
```

快速 Release 的版本标识为 `<versionName>-fast-yyyyMMddHHmm`（当前例如 `5.5.6-fast-202607112354`），时间使用上海时区；不传 `-PfastRelease=true` 时仍执行正常 Release 优化，版本标识保持 `<versionName>-yyyyMMddHHmm`。快速包只用于临时测试，不代替正式发布包。

也可以一次打常用三包：手机 64 位、电视 32 位、电视 64 位。

```bash
bash gradlew :app:assembleMobileArm64_v8aDebug :app:assembleLeanbackArmeabi_v7aDebug :app:assembleLeanbackArm64_v8aDebug
```

### MPV native/JNI 重建

普通打包不需要执行本节命令，Gradle 会把仓库内已提交的 MPV assets 和 `libplayer.so` 打进 APK。修改以下内容时需要重建 MPV JNI：

- `third_party/mpv-player-jni/src/**`
- `third_party/mpv-player-jni/include/mpv/client.h`
- `third_party/mpv-player-jni/include/mpv/stream_cb.h`
- 升级 MPV client API，或新的 `libmpv.so` 与现有 JNI 头文件/API 不兼容

当前 `libmpv.so` 同时启用 OpenGL、Vulkan、libcurl 和 HTTP/2；`libplayer.so` 由本仓库 `third_party/mpv-player-jni` 构建，用于保留 END_FILE reason/error 等本地桥接能力。当前 native 基线：

| ABI | MPV | FFmpeg | libplacebo | 网络后端 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `arm64-v8a` | `0.41.0-878-g94335ab87` | `8ae0b34901ba`（n8.0.3） | `7.371.0` / `a7a18af88ff0` | curl 8.21.0 + nghttp2 1.69.0 | vivo Android 15 MPV 网络播放已验证，未出现 destroyed-mutex、SIGABRT 或 SIGSEGV |
| `armeabi-v7a` | `0.41.0-878-g94335ab87` | `8ae0b34901ba`（n8.0.3） | `7.371.0` / `a7a18af88ff0` | curl 8.21.0 + nghttp2 1.69.0 | 同一 lock 独立构建并通过版本、补丁、HTTP/2 和 ELF 依赖校验；待 32 位真机播放回归 |

替换或升级 MPV native 时必须遵守：

- `libmpv.so`、FFmpeg（codec/device/filter/format/util/swresample/swscale）、静态链接进 MPV 的 libplacebo、curl、nghttp2、MbedTLS 和 `libc++_shared.so` 必须按同一 ABI、同一 lock 成套构建，不能再混用旧 `libmpv.so` 与新依赖作为正式方案。
- 当前已提交 assets 使用 MPV `94335ab87ab225ca3e36e0faeac831639d3e1d4e`、FFmpeg n8.0.3 `8ae0b34901ba60a802f183ee75a250a9fc3e09a5`、libplacebo `a7a18af88ff0a17c04840dcb3246047bb6b46df3`（7.371.0）、curl 8.21.0、nghttp2 1.69.0 和 NDK r28c。curl 使用 MbedTLS，只启用 HTTP/HTTPS 与 HTTP/2，不包含 HTTP/3、ngtcp2、nghttp3 或 quiche。FFmpeg 8.1.2 组合在 vivo Android 15 播放初始化时可触发 `pthread_mutex_lock called on a destroyed mutex`，因此没有进入正式 lock。
- curl 与 nghttp2 静态链接进 `libmpv.so`，APK 不新增独立网络 `.so`。它增强 MPV 直接远程 HTTP/HTTPS 输入；App 自己处理的本地 HLS 代理、`stream_cb` 和 FFmpeg/lavf 路径仍按各自实现工作，不能把启用 curl 理解为所有播放请求都强制走同一后端。
- FFmpeg 文件名、ELF `SONAME` 和所有 `DT_NEEDED` 都要从 `libav*`/`libsw*` 等长改为 `libmv*`/`libmw*`，不能只重命名文件，否则会和 `nextlib-media3ext` 内置 FFmpeg 发生 Android linker 复用冲突。
- 固定 MPV 源码会应用 `third_party/patches/mpv-stream-cb-disc-controls.patch`。该补丁扩展 `stream_cb` 光盘控制并接入 `demux_disc`；修改补丁或 `stream_cb.h` 后必须同时重建 `libmpv.so` 和 `libplayer.so`。
- 更新后用 NDK `llvm-readelf -d` 确认没有残留 `libav*.so`/`libsw*.so` 依赖，再分别回归 OpenGL、Vulkan、硬解/软解、LUT、字幕、线路切换、连续起播/退出和 Blu-ray ISO。Android 15 必须同时检查 crash buffer 中是否出现 destroyed mutex。

从固定源码重新生成 MPV/FFmpeg `.so`：

```bash
scripts/build_mpv_native.sh --abi arm64-v8a
bash gradlew :app:assembleMobileArm64_v8aRelease -PfastRelease=true
```

需要更新仓库 assets 时，同步安装两套 ARM ABI；只有 JNI 源码、MPV client API 或 `stream_cb.h` 变化时才重建 JNI 桥：

```bash
scripts/build_mpv_native.sh --abi all --install
# 按需执行：scripts/build_mpv_player_jni.sh
```

脚本读取 `third_party/mpv-native-lock.json`，自动下载固定 commit、应用 MPV 光盘控制补丁、构建依赖、修改 ELF 依赖名、strip 并校验。当前 lock 与两套已提交 assets 一致，可复现正式 native 组合；普通 Gradle 和 GitHub Actions 不会调用该脚本，直接复用仓库已提交的 `.so`。Android Release Action 会在 Gradle 打包前运行 `scripts/verify_mpv_native_assets.sh --require-elf`，检查两套 assets 的文件集合、ABI、版本字符串、HTTP/2/光盘补丁标记、`SONAME` 和 `DT_NEEDED`，但不会现场重编 MPV。完整排查记录见本地 `plans/MPV原生依赖升级与Android崩溃排查记录.md`。

只校验当前仓库已经提交的 MPV native assets：

```bash
bash scripts/verify_mpv_native_assets.sh
```

发布或 native 提交前应要求完整 ELF 校验；Linux 可使用系统 `readelf`，macOS 可使用 NDK 中的 `llvm-readelf`：

```bash
bash scripts/verify_mpv_native_assets.sh --require-elf
```

只重建 App JNI 桥接库 `libplayer.so`：

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
scripts/build_mpv_player_jni.sh
bash gradlew :app:assembleMobileArm64_v8aDebug
```

脚本会替换：

```text
app/src/arm64_v8a/assets/mpv-libs/arm64-v8a/libplayer.so
app/src/armeabi_v7a/assets/mpv-libs/armeabi-v7a/libplayer.so
```

### IJK native/FFmpeg 重建

普通 App 构建同样不会现场编译 IJK。仓库已经提交两套 ABI 的 `libijkffmpeg.so`、`libijksdl.so` 和 `libijkplayer.so`，Gradle 与 GitHub Actions 直接复用：

```text
app/src/main/jniLibs/arm64-v8a/
app/src/main/jniLibs/armeabi-v7a/
```

`ijk-bilibili-grouped-seek-20260713-083211` 的最终稳定方案保留 TVBoxOSC FFmpeg 4.0 ABI，并在 App 的本地 DASH/HLS 代理层兼容 Bilibili。开发者修改 IJK C/C++、FFmpeg demuxer/protocol、MediaCodec 桥接或 native seek 行为时，才需要重建 `.so`。源码和工具链固定在 `third_party/ijk-native-lock.json`。

安装 native 构建依赖：

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk" # Linux 通常为 $HOME/Android/Sdk
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
  "ndk;27.2.12479018" \
  "ndk;21.4.7075529"

# macOS
brew install git yasm

# Ubuntu
sudo apt-get update
sudo apt-get install -y git make yasm python3
```

重建并安装 arm64 IJK，然后打快速 Release：

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.2.12479018"
scripts/build_ijk_native.sh --abi arm64-v8a --install
bash gradlew :app:assembleMobileArm64_v8aRelease -PfastRelease=true
```

Ubuntu 下重建 32 位 IJK：

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/21.4.7075529"
scripts/build_ijk_native.sh --abi armeabi-v7a --install
bash gradlew :app:assembleLeanbackArmeabi_v7aRelease -PfastRelease=true
```

脚本会拉取锁定的 IJK/FFmpeg 4.0 源码、编译 OpenSSL/FFmpeg/IJK、检查三项输出并按 `--install` 写入对应 ABI 目录。32 位 native 重建推荐 Ubuntu；只需打包 App 时不必安装这两套额外 NDK，也不要运行该脚本。

### APK 输出路径

debug 原始输出：

```text
app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk
app/build/outputs/apk/mobileArmeabi_v7a/debug/app-mobile-armeabi_v7a-debug.apk
app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk
app/build/outputs/apk/leanbackArmeabi_v7a/debug/app-leanback-armeabi_v7a-debug.apk
```

release 构建完成后会自动复制到 `Release/apk/`，Gradle 原始输出仍在 `app/build/outputs/apk/<flavor>/release/`：

```text
Release/apk/mobile-arm64_v8a.apk
Release/apk/mobile-armeabi_v7a.apk
Release/apk/leanback-arm64_v8a.apk
Release/apk/leanback-armeabi_v7a.apk
```

安装到已连接设备：

```bash
adb devices
adb install -r app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk
```

### GitHub 手动发布

仓库内置 `.github/workflows/android-release.yml`,只支持在 GitHub Actions 页面手动触发,不会在每次 push 代码时自动打包。默认 tag 会从 `app/build.gradle` 读取当前 `versionName`:稳定版生成 `v<versionName>-yyyyMMddHHmm`;在 `fongmi-sync` 分支选择 `auto` 通道时生成测试版 `v<versionName>-beta-yyyyMMddHHmm`,APK/JSON 文件名同步追加 `-beta`。

工作流会构建 4 个 release APK,生成同名更新清单 JSON 并发布到 GitHub Release。启用 CNB 同步后,大 APK 会上传到同 tag 的 CNB Release 附件（单文件支持 64GB）,代码仓库的 `apk/` 目录只保存小型 JSON 清单,避免超过 100MB 时 Git 下载返回 413。正式发布前建议在 GitHub Secrets 配置:

```text
RELEASE_KEYSTORE_BASE64  # release keystore 的 base64 内容
RELEASE_KEY_ALIAS        # key alias
RELEASE_STORE_PASSWORD   # store password,key password 复用该值
RELEASE_KEY_PASSWORD     # 可选,key password 与 store password 不同时配置
CNB_TOKEN                # CNB 访问令牌,需要 repo-contents 读写权限
```

CNB 默认同步到 `https://cnb.cool/fish2018/webhtv-release.git`,仓库标识为 `fish2018/webhtv-release`。`CNB Release Sync` 手动补同步时可通过输入参数临时指定其他仓库。更新 JSON 内的 APK 地址使用固定版本直链 `https://cnb.cool/<slug>/-/releases/download/<tag>/<apk>`，公开仓库可匿名下载并支持 Range/断点续传。

GitHub Actions 正式发布必须配置签名 secrets,否则会直接失败,避免使用 runner 临时 debug key 生成无法覆盖安装的 APK。

如果 CNB 同步失败,不需要重新打包。修正 `CNB_TOKEN` 或网络问题后,在 GitHub Actions 手动运行 `CNB Release Sync`,填写已有 `release_tag` 即可把该 GitHub Release 的 APK 上传到 CNB Release 附件,并把改写为 CNB 绝对下载地址的 JSON 补同步到代码仓库；`release_tag` 留空时同步最新 release。

### 签名

默认不需要配置 release 签名文件。未配置签名时，release 包使用 debug signing 兜底，方便 clone 后直接打包测试。

如需正式签名，在根目录的 `local.properties` 增加以下字段；`keyPassword` 可省略，省略时复用 `storePassword`：

```properties
sdk.dir=/path/to/android/sdk
storeFile=/path/to/keystore.jks
keyAlias=your_alias
storePassword=your_password
keyPassword=your_key_password
```

### 播放层依赖

- `app/libs/*.aar`:内置 Hook、TVBus、Thunder、ForceTech、JianPian 播放能力依赖。
- `third_party/maven`:已生成的 `androidx.media3:*:1.11.0-alpha01-fongmi` 本地 Maven 产物，以及定制 `nextlib-media3ext`。
- `third_party/media-lock.json`:记录 Media3 锁定版本,升级 Media3 时使用(配套脚本 `scripts/build_media_deps.sh`)。
- `third_party/mpv-player-jni`:MPV `libplayer.so` JNI 桥接源码，修改后用 `scripts/build_mpv_player_jni.sh` 重建。
- `app/src/*/assets/mpv-libs/*`:随 APK 打包的 MPV native 库和 JNI 桥接库。
- `nextlib-media3ext`:`io.github.anilbeesetti:nextlib-media3ext:1.10.0-0.12.1-fongmi-softload`,提供 FFmpeg renderer。

`settings.gradle` 中的依赖顺序是仓库本地 `third_party/maven`、Maven Central、Google Maven、`app/libs` 和 JitPack。`app/build.gradle` 会强制所有 `androidx.media3` 依赖使用 `1.11.0-alpha01-fongmi`，避免传递依赖拉回官方版本。

### 常见构建失败

- `Unsupported class file major version`、`invalid source release: 21`：当前终端没有使用 JDK 21。
- `SDK location not found`：缺少 `local.properties`，或 `sdk.dir` 指向错误。
- `failed to find target with hash string 'android-37'`：未安装 Android SDK Platform 37。
- `NDK clang++ not found under .../ndk/28.2.13676358`：未安装 NDK 28.2.13676358，或 `ANDROID_NDK_HOME` 指向错误。
- `Missing MPV asset directory`：MPV assets 缺失或 ABI 目录名不匹配，确认 `app/src/arm64_v8a/assets/mpv-libs/arm64-v8a` 和 `app/src/armeabi_v7a/assets/mpv-libs/armeabi-v7a` 存在。
- `missing llvm-readelf/readelf`：运行完整 native assets 校验时缺少 ELF 工具；Linux 安装 `binutils`，macOS 安装 NDK 28.2.13676358 或设置 `ANDROID_NDK_HOME`。
- 运行后提示 `dlopen failed`、`libplayer.so` 或 `libmpv.so` 相关错误：先确认对应 ABI 的整套 MPV/FFmpeg `.so` 已打包，并用 NDK `llvm-readelf -d` 检查 `SONAME`/`DT_NEEDED`。只有 JNI 或 client API 变化才运行 `scripts/build_mpv_player_jni.sh`；该脚本不能修复不配套的 `libmpv.so`、FFmpeg 或 libplacebo。
- `Could not resolve ...`：依赖下载失败，检查网络或设置代理后重新执行 Gradle。
- `Permission denied: ./gradlew`：本仓库文档统一使用 `bash gradlew`，不依赖可执行位。

## 目录结构

```text
app/          Android 主应用(mobile/leanback 双 flavor)
catvod/       CatVod 抽象层、Spider 接口、网络和代理工具
quickjs/      JavaScript Spider 运行时
chaquo/       Python Spider 运行时
webhome-devkit/ WebHome 开发套件(文档、主页/扩展示例、模板、AI skills)
scripts/      Media3 和 MPV JNI 本地依赖构建脚本
third_party/  Media3 本地 Maven、nextlib 源码、MPV JNI 源码和版本锁定文件
Release/      release 构建的 APK 输出
other/        Logo 图片和辅助工具
```

## 上游基线

本项目二开起始于[原版影视](https://github.com/FongMi/TV) commit `bec0f1d2fc22f394ba05f8e63a9ef2ba7ecbba0e`,当前已同步合并到[原版影视](https://github.com/FongMi/TV) commit `5fdff00a602dc56e8ba756174daef20edab024f2`。

## 免费声明与社区分享

WebHomeTV 是基于开源生态二次开发的技术学习与研究项目,软件本体完全免费,不提供任何付费服务、影视内容、直播源、接口源、资源存储或内容分发能力。

本软件仅供技术学习、研究和个人测试使用,请在下载、安装或试用后 24 小时内自行卸载。继续使用本软件所产生的一切行为及后果,由使用者自行承担。

本软件不内置、不售卖、不传播任何影视资源,不对用户自行添加的接口、站源、插件、脚本、链接、网盘资源或第三方服务内容负责。使用者应遵守所在地法律法规,尊重版权方和内容提供方的合法权益,不得将本软件用于任何侵权、盗版、传播非法内容或其他违法违规用途。

严禁任何个人或组织以本软件名义进行售卖、引流、收费维护、会员服务、广告变现、盒子预装、电视盒子捆绑销售或其他任何形式的获益行为。对于将本软件内置于电视盒子、机顶盒、付费套餐或商业服务中进行销售、推广的行为,项目方明确反对并予以谴责。

### 友情链接
[![Linux.do](https://img.shields.io/badge/-Linux.do-1c1c1e?style=flat-square&logo=data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZlcnNpb249IjEuMiIgYmFzZVByb2ZpbGU9InRpbnktcHMiIHdpZHRoPSIxMjgiIGhlaWdodD0iMTI4IiB2aWV3Qm94PSIwIDAgMTIwIDEyMCI+CiAgPGNsaXBQYXRoIGlkPSJhIj4KICAgIDxjaXJjbGUgY3g9IjYwIiBjeT0iNjAiIHI9IjQ3Ii8+CiAgPC9jbGlwUGF0aD4KICA8Y2lyY2xlIGZpbGw9IiNmMGYwZjAiIGN4PSI2MCIgY3k9IjYwIiByPSI1MCIvPgogIDxyZWN0IGZpbGw9IiMxYzFjMWUiIGNsaXAtcGF0aD0idXJsKCNhKSIgeD0iMTAiIHk9IjEwIiB3aWR0aD0iMTAwIiBoZWlnaHQ9IjMwIi8+CiAgPHJlY3QgZmlsbD0iI2YwZjBmMCIgY2xpcC1wYXRoPSJ1cmwoI2EpIiB4PSIxMCIgeT0iNDAiIHdpZHRoPSIxMDAiIGhlaWdodD0iNDAiLz4KICA8cmVjdCBmaWxsPSIjZmZiMDAzIiBjbGlwLXBhdGg9InVybCgjYSkiIHg9IjEwIiB5PSI4MCIgd2lkdGg9IjEwMCIgaGVpZ2h0PSIzMCIvPgo8L3N2Zz4K)](https://linux.do/)
