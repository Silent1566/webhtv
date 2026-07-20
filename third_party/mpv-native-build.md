# MPV Native 可复现构建

本文是 WebHTV 重新生成 `libmpv.so` 及其 FFmpeg 依赖的权威说明。

## 两种构建必须分开

日常 App 构建不会编译 MPV native。仓库已经提交以下目录中的 `.so`，Gradle 和 GitHub Actions 直接把它们作为 assets 打进 APK：

```text
app/src/arm64_v8a/assets/mpv-libs/arm64-v8a/
app/src/armeabi_v7a/assets/mpv-libs/armeabi-v7a/
```

因此普通用户 clone 后直接执行 Gradle 即可，不需要运行本文的 native 脚本：

```bash
bash gradlew :app:assembleMobileArm64_v8aDebug
```

只有升级 MPV、FFmpeg、libplacebo、NDK，或需要重新生成原生库时，维护者才手动运行：

```bash
scripts/build_mpv_native.sh
```

该脚本没有挂接到 Gradle，也不会被 GitHub Actions 自动执行。Android Release Action 只对仓库已提交的 native assets 做完整性和 ELF 依赖校验，不会现场重新编译 MPV。

## 固定输入

所有仓库、commit、tar 包 SHA-256、NDK 和 Meson/Ninja 版本统一记录在：

```text
third_party/mpv-native-lock.json
```

构建包装脚本还会对当前选择的完整 lock 文件计算 SHA-256，并用它覆盖上游构建框架的 prefix cache 标识，避免升级 FFmpeg、字体栈、curl 或 nghttp2 后误复用旧缓存；使用 `--lock-file` 测试其他组合时也会生成独立缓存身份。

当前已对 `arm64-v8a` 和 `armeabi-v7a` 做过从源码到最终 `.so` 的完整实编验证，核心组合如下：

| 组件 | 固定版本 |
| --- | --- |
| 构建框架 | `marlboro-advance/mpv-android@f712d4dcf56c00d04e7dd05e157d953d665a6890` |
| NDK | `28.2.13676358`（r28c），API 24 |
| MPV | `94335ab87ab225ca3e36e0faeac831639d3e1d4e`（`0.41.0-878-g94335ab87`） |
| FFmpeg | `8ae0b34901ba60a802f183ee75a250a9fc3e09a5`（n8.0.3） |
| libplacebo | `a7a18af88ff0a17c04840dcb3246047bb6b46df3`（7.371.0） |
| curl | 8.21.0，MbedTLS，HTTP/HTTPS、HTTP/2 |
| nghttp2 | 1.69.0 |
| libass | `4a05d8127f525943ebf45fdc6497c9e665947f0d`（0.17.5） |
| dav1d | `54706fc6bc0cdecab7e9593974a4039cc038fca7`（1.5.4） |

其他字体、TLS、Lua 和构建工具版本也在 lock 文件中，不要只修改脚本里的单个组件。

验证状态：ARM64 已在 vivo V2453A / Android 15 使用 MPV 网络播放场景验证正常，监听日志未出现 destroyed-mutex、SIGABRT 或 SIGSEGV；ARMv7 已完成独立源码构建、curl/HTTP2 标记、版本字符串、补丁标记、SONAME 和 `DT_NEEDED` 校验，仍需独立 32 位真机播放回归。

当前 curl 使用 MbedTLS 3.6.5 和 nghttp2 1.69.0，静态链接进 `libmpv.so`，不会给 APK 增加独立 `libcurl.so` 或 `libnghttp2.so`。构建明确关闭 HTTP/3，不包含 ngtcp2、nghttp3 或 quiche。MPV 直接远程 HTTP/HTTPS 可使用 curl 后端；App 本地 HLS 代理、`stream_cb` 和 FFmpeg/lavf 输入仍保留原路径。

## 主机准备

支持 macOS 和 x86_64 Linux。先安装 JDK 21、Android SDK、NDK r28c，并确保根目录 `local.properties` 的 `sdk.dir` 正确。

macOS：

```bash
xcode-select --install
brew install pkg-config
```

Ubuntu/Debian：

```bash
sudo apt-get update
sudo apt-get install -y build-essential git curl file pkg-config python3 python3-venv perl
```

安装 Android NDK：

```bash
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "ndk;28.2.13676358"
```

如果 NDK 不在 `$ANDROID_HOME/ndk/28.2.13676358`，设置：

```bash
export ANDROID_NDK_HOME=/path/to/android-ndk-r28c
```

网络需要代理时，在执行脚本前设置标准代理环境变量：

```bash
export https_proxy=http://127.0.0.1:7897
export http_proxy=http://127.0.0.1:7897
export all_proxy=socks5://127.0.0.1:7897
```

## 从 clone 到重新生成 arm64 `.so`

```bash
git clone https://github.com/fish2018/webhtv.git
cd webhtv
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
scripts/build_mpv_native.sh --abi arm64-v8a --install
bash gradlew :app:assembleMobileArm64_v8aRelease -PfastRelease=true
```

`--install` 会更新八个 MPV/FFmpeg 库和匹配 NDK 的 `libc++_shared.so`，但保留仓库已有的 `libplayer.so`。

同时生成两种 ARM ABI：

```bash
scripts/build_mpv_native.sh --abi all --install
```

不重新编译，只校验仓库已经提交的两套 native assets：

```bash
bash scripts/verify_mpv_native_assets.sh
```

发布或提交 native 更新前使用完整 ELF 校验模式：

```bash
bash scripts/verify_mpv_native_assets.sh --require-elf
```

Linux 可使用系统 `readelf`；macOS 会尝试从 `ANDROID_NDK_HOME`、`ANDROID_HOME` 或 lock 指定的 NDK 目录寻找 `llvm-readelf`。普通模式在找不到 ELF 工具时仍会检查文件集合、ABI 和嵌入版本字符串，并明确提示跳过了 `SONAME`/`DT_NEEDED` 检查。

只下载并核对源码，不编译：

```bash
scripts/build_mpv_native.sh --prepare-only
```

指定并行数或构建目录：

```bash
scripts/build_mpv_native.sh --abi arm64-v8a --jobs 8 --work-dir /tmp/webhtv-mpv-native
```

默认进行干净构建。开发脚本本身时可以使用 `--incremental` 保留 prefix，但正式生成待提交的 `.so` 时不要使用该参数。

## 脚本执行内容

`scripts/build_mpv_native.sh` 会自动完成：

1. 读取 `third_party/mpv-native-lock.json`。
2. 检查 NDK revision 和 LLVM 工具。
3. 在独立 Python venv 中安装固定版本 Meson/Ninja及 MbedTLS 生成工具依赖。
4. 下载构建框架和每个固定 commit，初始化 MbedTLS、libplacebo 子模块，并校验 Lua、libunibreak、curl、nghttp2 tar 包 SHA-256。
5. 对固定 MPV commit 应用 `third_party/patches/mpv-stream-cb-disc-controls.patch`，为自定义 Blu-ray ISO stream 暴露光盘时间轴控制。
6. 按依赖顺序构建 MbedTLS、libunibreak、dav1d、FFmpeg、FreeType、FriBidi、HarfBuzz、libass、Lua、shaderc、libplacebo、nghttp2、curl 和 MPV。
7. 把 FFmpeg 的文件名、ELF `SONAME` 和 `DT_NEEDED` 从 `libav*`/`libsw*` 等长修改为 `libmv*`/`libmw*`。
8. 使用 NDK `llvm-strip --strip-unneeded` 处理最终库。
9. 使用 NDK `llvm-readelf` 检查每个 SONAME、MPV 的完整依赖和 Vulkan 依赖，并检查 MPV/libplacebo/curl 版本字符串、HTTP/2 标记及光盘控制补丁标识。

`scripts/verify_mpv_native_assets.sh` 对已提交 assets 执行同类校验，Android Release Action 会在 Gradle 打包四个 APK 前以 `--require-elf` 模式调用它，防止 lock、arm64/armv7 assets 或静态网络能力不一致的二进制进入 Release。

未指定 `--install` 时，输出位于：

```text
build/mpv-native/output/arm64-v8a/
build/mpv-native/output/armeabi-v7a/
```

`build/` 已被 `.gitignore` 忽略，源码和中间文件不会进入提交。

## 为什么必须成套更新

`libmpv.so` 直接链接固定 FFmpeg ABI。只替换 `libmpv.so`，保留旧 FFmpeg，可能在设备上出现：

```text
dlopen failed: cannot locate symbol "av_dynamic_hdr_smpte2094_app5_alloc"
```

当前 MPV `94335ab87` 与 libplacebo 7.371.0 按同一 lock 构建。不能通过篡改 pkg-config 版本号让 MPV 链接不满足版本要求的旧 libplacebo。

此前测试过的 FFmpeg 8.1.2 重新构建组合会在 vivo Android 15 播放初始化阶段触发：

```text
FORTIFY: pthread_mutex_lock called on a destroyed mutex
```

最新 MPV 与 FFmpeg n8.0.3、libplacebo 7.371.0、curl 8.21.0 和 nghttp2 1.69.0 的组合在同一设备正常，因此正式 lock 固定 n8.0.3。n8.0.3 同时包含 MPV curl 嵌套 AVIO 清理所需修复；脚本会拒绝缺少该修复的 FFmpeg 版本。现有证据只能把 destroyed-mutex 不兼容范围锁定在 FFmpeg 8.1.x 或其构建组合，不能据此把某个 FFmpeg 源文件认定为唯一根因。

最终目录必须包含：

```text
libc++_shared.so
libmpv.so
libmvcodec.so
libmvdevice.so
libmvfilter.so
libmvformat.so
libmvutil.so
libmwresample.so
libmwscale.so
libplayer.so
```

前九个由 native 脚本维护；`libplayer.so` 是 App JNI 桥接库。

## `libplayer.so` 的重建边界

修改以下内容或 MPV client API 时才执行：

```bash
scripts/build_mpv_player_jni.sh
```

- `third_party/mpv-player-jni/src/**`
- `third_party/mpv-player-jni/include/mpv/client.h`
- `third_party/mpv-player-jni/include/mpv/stream_cb.h`
- 新 `libmpv.so` 的 client API 与当前头文件不兼容

只重编相同 client API 的 MPV/FFmpeg 时，不需要重建 `libplayer.so`。

## 提交前验证

至少构建一个快速 Release：

```bash
bash gradlew :app:assembleMobileArm64_v8aRelease -PfastRelease=true
```

设备回归范围：

- OpenGL普通播放、硬解状态。
- OpenGL LUT 生效，预览竖线可见，拖动连续且无闪烁。
- Vulkan普通播放和 LUT。
- 文本字幕、图形字幕以及播放中切换。
- 播放成功前切换播放器内核。
- 连续起播、退出、换线路，并检查 crash buffer 中没有 destroyed-mutex。
- 大型 MKV/REMUX、硬解/软解以及前后台切换。

不要提交 `build/mpv-native/`。只提交 lock、脚本、文档和最终 assets 中发生变化的 `.so`。

当前完整稳定基线 tag：

```text
mpv-libcurl-http2-armv7-20260717-123347
```

## 常见错误

| 错误 | 处理 |
| --- | --- |
| `missing command: pkg-config` | macOS 安装 `brew install pkg-config`；Debian/Ubuntu 安装 `pkg-config` |
| `missing llvm-readelf/readelf` | Linux 安装 `binutils`；macOS 安装 NDK r28c，或设置 `ANDROID_NDK_HOME`/`READELF` |
| `Android NDK ... not found` | 安装 `ndk;28.2.13676358` 或设置 `ANDROID_NDK_HOME` |
| 下载 commit/tar 包失败 | 检查代理；重新执行会复用已校验缓存 |
| tar 包 `SHA-256 mismatch` | 不要绕过检查；确认下载地址或 lock 是否经过审核 |
| `libmpv.so does not depend on libvulkan.so` | 构建参数或 libplacebo/shaderc未正确启用 Vulkan |
| `unrenamed FFmpeg dependency` | 不要手动复制中间产物，使用脚本生成的 output/assets |
| App `dlopen failed` | 检查是否只更新了部分 `.so`，并确认 ABI、SONAME 和 `DT_NEEDED` |
| macOS 构建 shaderc 时出现 `fcntl(): Bad file descriptor` | NDK make jobserver 的输出噪声；只要后续仍在编译且脚本最终显示 `MPV native build completed`，无需处理 |
