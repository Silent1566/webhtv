# IJK native 产物来源与构建状态（FFmpeg7 单库）

> 本分支的 IJK 已从上游 FFmpeg4 三库方案切换为 FFmpeg7 单库产物。
> 仓库内 `third_party/ijk-native-lock.json` 与 `scripts/build_ijk_native.sh` 仍描述旧的
> FFmpeg4 三库构建方案，与当前随包产物**不对应**。本文档记录已核实的真实来源、
> 产物对应关系，以及无法从本仓库复现构建的边界。

## 1. 当前随包产物（实测）

| 产物 | 路径 | ABI | SHA-256 |
| --- | --- | --- | --- |
| 单库 player | `app/src/main/jniLibs/arm64-v8a/libijkplayer.so` | arm64-v8a | `85b5e17973e1d2406866d047053fada4d9f28e1f60c1558b61fcf8ae7d93440b` |
| 单库 player | `app/src/main/jniLibs/armeabi-v7a/libijkplayer.so` | armeabi-v7a | `356169e7803830edfbb641654a9f192120044b6c3296d96becdb41278205e6ad` |
| AAR | `app/libs/ijkplayer-cmake-release.aar` | arm64-v8a / armeabi-v7a / x86 / x86_64 | `bb8db505aa08c22c2dba2e0cb7727341197ea2a8a3d80a165c80824226bbd230` |

AAR 内含 4 个 ABI 的 `libijkplayer.so`；`jni/arm64-v8a` 与 `jni/armeabi-v7a` 两份的
SHA-256 与 `app/src/main/jniLibs/` 下的两份**逐字节相同**。即同一份 native 产物被 AAR 与
jniLibs 各放一份；最终 APK 内 IJK player 为单库 `libijkplayer.so`，不再有
`libijkffmpeg.so` / `libijksdl.so`。

## 2. 来源（已核实，非推测）

- **直接来源**：本地 `app/libs/ijkplayer-cmake-release.aar` 与
  `https://github.com/cat3399/blbl` 的 `app/libs/ijkplayer-cmake-release.aar`（main 分支）
  **逐字节完全相同**——两者大小同为 17,356,718 字节，SHA-256 同为
  `bb8db505aa08c22c2dba2e0cb7727341197ea2a8a3d80a165c80824226bbd230`，`cmp` 一致。
  blbl 的 `app/build.gradle.kts` 用 `implementation(files("libs/ijkplayer-cmake-release.aar"))`
  直接打包该 AAR。
- **上游构建方**：blbl README 技术栈列出 `[Ijkplayer](https://github.com/cat3399/ijkplayer)`，
  感谢 `debugly/ijkplayer`。`cat3399/ijkplayer` 是镜像，原始工程为
  `https://github.com/debugly/ijkplayer`。镜像 README 说明：预编译 Android AAR 取代旧三 so、
  用 CMake 重组工程抛弃 ndk-build、NDK r27c、FFmpeg/openssl 等编成静态库、openssl 1.1.1w、
  soundtouch 2.3.3、`module-lite.sh` 配置、支持 RTSP。
- **注意**：debugly **官方 release** `k0.8.9` 的 `ijkplayer-cmake-release.aar` 为
  13,382,651 字节（SHA-256 `101dab53d192e4fdb1f0f639055a0c183028b8cc767fb66e30c4ffb13a5d2e63`），
  与本 fork 的 17,356,718 字节**不同**。因此本产物不是 k0.8.9 官方发布包，
  而是 blbl 仓库内随源码维护的定制 AAR。不要把它误标为 k0.8.9。

## 3. FFmpeg 版本与编译参数（从 .so 内嵌字符串提取）

| 项 | 值 | 提取依据 |
| --- | --- | --- |
| FFmpeg 版本 | `n7.1.1-4-g834d732` | so 内多处 `FFmpeg version n7.1.1-4-g834d732` |
| libavformat | `Lavf61.7.100`（FFmpeg 7.1.x） | so 内版本字符串 |
| 关键 configure 参数 | `--enable-openssl --enable-demuxer=dash --enable-libxml2` | so 内 configure 命令行片段 |
| 编译工具链 | NDK `27.2.12479018` | so 内 clang 绝对路径 `/usr/local/lib/android/sdk/ndk/27.2.12479018/...` |
| 内部构建标识 | `ijkplayer/FFToolChain/build/product/android/ijkffmpeg-arm64` | so 内 configure 行前缀 |

正是 FFmpeg 7.1 的 HLS 解复用器 `test_segment()` 在 `extension_picky=1` 时拒绝无扩展名分片，
触发 v5.6.39 的 `extension_picky=0` 修复（见 `UPDATE_NOTES.md`）。

## 4. 可复现构建边界（如实说明）

- **本产物不是从本仓库脚本构建的**：`third_party/ijk-native-lock.json` 与
  `scripts/build_ijk_native.sh` 指向 `ShikinChen/ijkplayer-android` + FFmpeg4
  `ff4.0--ijk0.8.8` + `libijkffmpeg/libijksdl/libijkplayer` 三库方案，
  只能重建旧三库，无法重建当前单库。
- **上游 CMake 构建工程的精确 revision / 补丁集 / 命令未在本仓库 vendored**，
  因此无法声称「从锁文件复现构建」。本 PR 不臆造这部分信息。
- 已新增 `third_party/ijk-single-lib-lock.json`，记录真实 AAR 来源、SHA-256、FFmpeg 版本、
  编译参数与「非本仓库可复现」状态。刷新产物的可操作步骤见该文件 `how_to_refresh`。

## 5. 验证记录

- 下载 `cat3399/blbl` 的 AAR 与本地 AAR `cmp` 逐字节一致、SHA-256 一致（见第 2 节）。
- AAR 内 ARM so 与 jniLibs so SHA-256 一致（见第 1 节）。
- 双 ABI 编译（leanback armeabi-v7a / mobile arm64-v8a Release JavaWithJavac）通过。
- 设备侧回归（普通代理 HLS、B 站 DASH 内联 MPD、手动换核、自动回退）需实机验证，
  见 `UPDATE_NOTES.md` v5.6.39 的回归验证建议清单。
