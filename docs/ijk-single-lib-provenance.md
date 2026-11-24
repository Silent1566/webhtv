# IJK native 产物来源与构建状态（FFmpeg7 单库）

> 状态：本分支的 IJK 已从上游 FFmpeg4 三库方案切换为 FFmpeg7 单库产物。
> 但仓库内 `third_party/ijk-native-lock.json` 与 `scripts/build_ijk_native.sh` 仍描述旧的
> FFmpeg4 三库构建方案，二者与当前随包产物**不对应**。本文档如实记录已知事实与缺口，
> 避免把「已打包可用的二进制」误表述为「可从仓库锁文件复现构建」。

## 1. 当前随包产物（事实）

| 产物 | 路径 | ABI | SHA-256 |
| --- | --- | --- | --- |
| 单库 player | `app/src/main/jniLibs/arm64-v8a/libijkplayer.so` | arm64-v8a | `85b5e17973e1d2406866d047053fada4d9f28e1f60c1558b61fcf8ae7d93440b` |
| 单库 player | `app/src/main/jniLibs/armeabi-v7a/libijkplayer.so` | armeabi-v7a | `356169e7803830edfbb641654a9f192120044b6c3296d96becdb41278205e6ad` |
| AAR | `app/libs/ijkplayer-cmake-release.aar` | arm64-v8a / armeabi-v7a / x86 / x86_64 | 内含同名 `libijkplayer.so` |

已核对：`ijkplayer-cmake-release.aar` 内 `jni/arm64-v8a/libijkplayer.so` 与
`jni/armeabi-v7a/libijkplayer.so` 的 SHA-256 与 `app/src/main/jniLibs/` 下的两份**逐字节相同**。
即同一份 native 产物被 AAR 与 jniLibs 各打包一次；Gradle 最终 APK 内 IJK player 为单库 `libijkplayer.so`，
不再有 `libijkffmpeg.so` / `libijksdl.so`。

## 2. 来源（已知范围）

- 该单库 `libijkplayer.so` 与上游 fongmi/哔哩哔哩系 FFmpeg7 IJK 产物同源（仓库历史 `C10`
  供应链对齐记录记有「已打包 IJK `.so` 与上游 blob 相同」）。
- 版本号线索：native 报错与选项范围指向 FFmpeg `n7.1.1-4-g834d732` 一系（见 `UPDATE_NOTES.md` v5.6.39）。
- AAR 由 CMake 构建流程产出（文件名 `ijkplayer-cmake-release`），Java 侧 `IjkMediaPlayer` API
  由 AAR 内 `classes.jar` 提供；C/C++ 与 FFmpeg 合并编入单个 `libijkplayer.so`。

## 3. 缺口（待补，不臆造）

以下内容仓库内**没有**可复现记录，本 PR 不做虚构填充：

1. `ijkplayer-cmake-release.aar` 与单库 `libijkplayer.so` 的**精确源码仓库 URL、commit/tag、
   CMake 工具链版本、补丁集与构建命令**。
2. `third_party/ijk-native-lock.json` 仍指向 `ShikinChen/ijkplayer-android` + FFmpeg4
   `ff4.0--ijk0.8.8` + `libijkffmpeg/libijksdl/libijkplayer` 三项输出 —— 与当前单库产物无关。
3. `scripts/build_ijk_native.sh` 仍按 FFmpeg4 三库流程构建，并 `--install` 三份 `.so`，
   无法重建当前单库产物。

## 4. 建议的补齐路径

- 由持有该 CMake 构建工程的一方提供：源码仓库与固定 revision、NDK 版本、CMake 选项、
  FFmpeg 配置与补丁、以及 `build.sh --abi <abi> --install` 形式的可执行命令。
- 补齐后新增 `third_party/ijk-single-lib-lock.json`（不与旧 FFmpeg4 lock 混用），
  并在 `scripts/` 下提供独立构建脚本；随后可将本文件第 3 节缺口标记为已解决。
- 在补齐前，`UPDATE_NOTES.md` 与 PR 描述均按「已打包产物可用、构建可复现性待补」如实表述。

## 5. 验证记录

- 双 ABI 编译（leanback armeabi-v7a / mobile arm64-v8a Release JavaWithJavac）通过。
- AAR 内 `.so` 与 jniLibs `.so` SHA-256 一致性已核对（见第 1 节）。
- 设备侧回归（普通代理 HLS、B 站 DASH 内联 MPD、手动换核、自动回退）需实机验证，
  见 `UPDATE_NOTES.md` v5.6.39 的回归验证建议清单。
