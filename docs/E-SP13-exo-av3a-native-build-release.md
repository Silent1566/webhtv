# E-SP13：Exo AV3A 原生库按 Release 重建

## Recovery anchor

- 目标：把 Exo 侧 `libarcdav3a`（AV3A/AVS3-P3 音频）从"未指定 CMAKE_BUILD_TYPE"改为显式 Release 重建，验证是否消除 AV3A 直播的持续供数不足（声音断续/延迟）。
- 现象基线（真机 Hisense VIDAA_TV / MT9653 / Android 11 / API 30）：同一路 a3va 直播，本 App 的 **MPV 正常**，**Exo 不正常**；OK影视 5.1.6 的 exo 正常。
- 已修部分：提交 `559f2df59`（AV3A 10 声道输出与 AudioTrack 掩码自洽 + 路由声道上限），画面卡死已消失，剩余为声音失真与延迟。
- guard：`E-SP13-exo-av3a-build-release` / `upstream`。状态：**实施中**。

## 证据

| 证据 | 结论 |
| --- | --- |
| debug-log (37) 第二次起播：输出 `channelMask=12 / channels=2 / routeMax=2` | 双声道修正确实生效 |
| 同日志 write 侧 acceptedBytes 增量 905216 ÷ 192000 = 4.7147 s，`lastPtsUs` 增量同为 4.7147 s | 字节↔时间戳自洽，排除残留 10/2 帧长错配 |
| 同上，这批音频交付耗时 11.358 s，占比约 0.415；`underruns` 累计 180；每块 4096 B=1024 帧=21.3 ms，约每 49 ms 到达 | 供数不足是当前唯一已证实故障，出在解码/转换侧（上游 bufferedMs 13–15 s 充足） |
| `third_party/media-lock.json:211-214` 与 `mpv-native-lock.json:52-55` 均锁 FongMi/FFmpeg `177f090e0503b7e013922ca903bde14b1c375f18` | 两条链 AV3A 源码相同，不是版本差异 |
| `nextlib-av3a.patch:74-95` 调 CMake 编译 `dependency/avs3a` 时只传 PLATFORM/ABI/TOOLCHAIN/PREFIX/PIC/STATIC，**无 CMAKE_BUILD_TYPE** | Exo AV3A 走 CMake 默认空构建类型 |
| 上游 `FongMi/FFmpeg@177f090e:dependency/avs3a/CMakeLists.txt` 无任何 `CMAKE_C_FLAGS`/`-O`/`CMAKE_BUILD_TYPE` 默认 | 空构建类型 ⇒ 使用空 `CMAKE_C_FLAGS`，接近 -O0 且不定义 NDEBUG |
| NDK 28.2 `android-legacy.toolchain.cmake:523-524`：`ANDROID_COMPILER_FLAGS_RELEASE = -O3 -DNDEBUG` | 只有 BuildType=Release 才注入优化与 NDEBUG |
| `FongMi/mpv-android@99a60ad2:buildscripts/include/cmake-android.sh:38` 显式 `-DCMAKE_BUILD_TYPE=Release` | MPV 链 AV3A 是 Release，构成对照 |
| 产物对照：Exo `libavcodec.so`（sha256 `2f3059fb…`，与真机日志 `env.native` 一致）导入 `__assert2`，并保留 9 条 `dependency/avs3a/src/*.c` 路径；MPV `libmvcodec.so`（`275c63b2…`）**无** `__assert2`、**无** AV3A 源路径字面量 | 与"Exo AV3A 未启用 NDEBUG/优化"一致；非性能证明 |

未证实：该构建差异是否就是 0.415 供数比的全部原因；解码与 swr_convert 的相对耗时未分别测量。

## 设计

- 变更点唯一：`buildArcDav3a()` 的 CMake 调用补 `-DCMAKE_BUILD_TYPE=Release`（与 MPV 链一致），不改源码、不改版本、不改 JNI、不改声道/下混策略。
- 不采用：升级 FFmpeg/nextlib 版本、`-ffast-math`、手写 NEON、改用 MPV 兜底、加大音频缓存（后者只是掩盖持续欠载）。
- 关闭断言的连带影响：AV3A 解码器内部 assert 不再生效。评估为可接受——上游 assert 保护的是自检不变量而非输入校验；输入错误仍由 `AUDIO_DECODER_ERROR_INVALID_DATA` 路径返回。若真机出现此前被 assert 拦住的数据，表现为解码错误而非静默错误输出，诊断日志可观测。

## 验收

1. 同一路 a3va 直播：`underruns` 不再持续增长；`acceptedBytes` 增量/秒 ≈ 192000（实时）。
2. 声音不再断续、无明显失真与累计延迟；音画同步正常。
3. 画面维持已修复状态，不回退卡死。
4. MPV、其他编码（AC3/EAC3/DTS 直通与普通 AAC）行为不变。
5. 产物 ABI/依赖/包内容与既有基线一致，`env.native` digest 更新且有记录。

## 回滚

- 撤销 `nextlib-av3a.patch` 的 CMAKE_BUILD_TYPE 一行，恢复现有 r5 AAR 与 `gradle/libs.versions.toml` 锁定版本；App 侧不涉及。
- 保留现有已提交修复 `559f2df59` 作为回滚锚。

## 实施与状态

- 2026-11-13：开始构建 nextlib AV3A（arm64-v8a 优先）对照产物；先只做本机可复现的 Release 静态库，再打包 AAR 验证。
- 下一步：完成 Release 版 AAR 构建 → 装入本地 maven → 构建 TV APK → 交真机复测上述验收项。

## Checkpoint 1：2026-11-13 22:0x CST 构建可行性核查

- 已完成：确认改点、工具链与既有产物的关系；补丁在仓库外的旧头计数问题用 `git apply --recount` 可应用，未修改正式补丁。
- 已证实（A 级）：
  - `nextlib-av3a.patch` 的 `buildArcDav3a()` 未传 `CMAKE_BUILD_TYPE`；上游 `dependency/avs3a/CMakeLists.txt` 无任何优化/类型默认；NDK 28.2 仅在 Release 注入 `-O3 -DNDEBUG`；MPV 链 helper 显式 `-DCMAKE_BUILD_TYPE=Release`。
  - 产物对照：Exo `libavcodec.so`（`2f3059fb…`，与真机 `env.native` 一致）保留 `__assert2` 与 9 条 `dependency/avs3a/src/*.c` 路径；MPV `libmvcodec.so`（`275c63b2…`）两者皆无。
  - 真机：输出已双声道且字节↔PTS 自洽，但供数比 0.415、欠载 180。
- 先前判断更正：**"HPM 接线缺失"是我的误判**。HPM 由 `buildUavs3d()` 间接构建：`build/av3a-native/nextlib-src/ffmpeg/setup.sh:220-234` 应用 `ffmpeg-avs3.patch` 后调用 `scripts/build_avs3_native.sh`，后者 `:115-118` 对每个 ABI 调 `scripts/build_hpm_native.sh`（Release 静态），`:119-127` 把 `-lwebhtvhpm` 追加进 uavs3d.pc，FFmpeg 经 pkg-config 静态链接。故重建无需新写代码。
- 产物一致性：从发货 r5 内嵌的 FFmpeg configure 字符串与当前配方比对，判别项与顺序一致（`-O3 -fPIC`、`--pkg-config-flags=--static`、`--enable-libarcdav3a`、`--enable-libuavs3d`、`--disable-debug` 等），只有 prefix/NDK 路径不同，未见配方漂移。
- 仍需决定（真实副作用，不可忽略）：PR 级 Release 会同时打开 `-DNDEBUG`。AV3A 被固定的 9 个编译单元共 53 处 assert，其中 `avs3_hoa_com.c:313,342`（HOA 阶数）、`bwe_com.c:193`（码率）、`avs3_init_dec.c:423`（不支持的编解码格式）、`avs3_bitstream_dec.c:514,530,540,547`（码流域）当前以 abort 结束进程；NDEBUG 后这些路径会落到默认分支继续解码。仓内已有先例：`third_party/avs3-hpm/CMakeLists.txt:16` 用 `-O3` 同时保持 `-UNDEBUG`。若要同样保留断言，需 Release + `-DCMAKE_C_FLAGS_RELEASE="-O3 -UNDEBUG"`，超出"只加一个构建类型参数"的原批准范围。
- 状态：重建可行但**尚未执行**；用户已选择先做低成本判定（MPV/Exo 解码速率对照）再决定是否重建。未修改二进制、未替换 AAR、未改版本锁。
- 唯一下一动作：取得同路流的 MPV 诊断日志，按同一口径（acceptedBytes 增量 ÷ 192000 ÷ 墙钟）与 Exo 的 0.415 对比。


## Checkpoint 2：与 OK影视 5.1.6 的静态对照（2026-11-13）

- 可用工具面：readelf / nm / strings / objdump + NDK llvm-objdump；无 Ghidra/IDA/REA 服务端，故只做静态取证，不做反编译级思路还原。
- OK影视 libmedia3ext.so（v7a，sha256 8fa25826…）：
  - 导入 __assert2 且保留 8 条 AV3A 源路径 → 断言同样开启，因此“断言存在”不能区分能播/不能播（推翻先前佐证）。
  - 源布局为 libavs3_common/ + src/，与本仓库 dependency/avs3a/src/ 属不同代集成。
  - 无 arm64-v8a 目录；FFmpeg 代际 Lavc60.3.100 → 与本仓库 r5（Lavc63）不同代，不能直接复用二进制。
  - 内嵌 configure 串属 libvpx（--force-target=armv7-android-gcc --disable-neon --disable-runtime-cpu-detect，NDK 25.2.9519653），非 FFmpeg，不能据此推断 AV3A 编译参数。
  - 保留 ARC 解码器自带吞吐日志 AVS3 Decoder finished / frame per s / samples per s / speed。
- 本仓库 r5 libavcodec.so（arm64，2f3059fb…）不含该串（count 0）→ 我们这版拿不到解码器自带速率，而它正是待测指标。
- 结论影响：若后续重建，可同时打开该速率日志，一次拿到“是否修复”与“解码吞吐”两项证据；该开关属 native 改动，需单独批准。
- 唯一下一动作：仍等 MPV 诊断日志做供数比对照。

## Checkpoint 3：编译参数实测与诊断包构建（2026-11-14）

- 实测编译参数（compile_commands.json，NDK 28.2 / CMake 3.22.1 / API21 / PIC+STATIC）：
  - 现行配方（无 CMAKE_BUILD_TYPE）：`--target=aarch64-none-linux-android21 -g -fPIC`，**无任何 -O 标志**（等价 -O0）。
  - `Release`：`-g -O3 -DNDEBUG -fPIC`。
  - `Release + -DCMAKE_C_FLAGS_RELEASE="-O3 -UNDEBUG"`：`-g -O3 -DNDEBUG -O3 -UNDEBUG -fPIC`，优化生效且保留断言。
- 断言语义对照：发货 r5 保留 `__assert2` 与 9 条 avs3a 源路径；无构建类型版与 Release+UNDEBUG 版都保留 `__assert2`，单 Release（-DNDEBUG）会丢弃。因此只有 Release+UNDEBUG 同时满足“优化”与“不改变错误语义”。
- 主机构建正确性探针（同一源码、固定 LCG 输入、FFT/IFFT 256、MDCT/IMDCT 1024）：基线版与 `-O3 -UNDEBUG` 版输出**逐位一致**，无 NaN/Inf，断言未触发。
- 主机微基准（5 次中位数，**主机 x86-64，非电视播放性能**）：FFT256 10.94µs→3.14µs（3.48x），MDCT1024 18.18µs→4.87µs（3.73x）。
- 本轮原生改动（仅诊断包范围）：
  1. `buildArcDav3a()` 增加 `-DCMAKE_BUILD_TYPE=Release -DCMAKE_C_FLAGS_RELEASE="-O3 -UNDEBUG"`；
  2. `ffaudio.cpp` 增加每 2 秒一行的分段计时：`send_ms/recv_ms/swr_ms/ratio/in_ch/in_order/sr/out_ch/gap_ms`（LOG_TAG `ffmpeg_jni`，不逐帧打印以免扰动播放）。
- 环境陷阱（复现必读）：本机**未安装 `pkg-config`**，FFmpeg configure 报 `arcdav3a not found using pkg-config`。已在 `build/av3a-native/tools/pkg-config` 放置最小兼容实现（支持 .pc 变量、--exists/--libs/--cflags/--static），需把该目录前置到 PATH。
- 目标：一次真机复测即拿到（a）声音是否恢复、（b）解码/重采样/入包间隔三段耗时，避免再靠猜测发版。
- 下一步：完成 AAR 封装 → 装入本地 maven → 构建 TV 诊断包 → 单次真机复测。

## Checkpoint 4：可自证的诊断包（2026-11-14）

- 环境：本机缺 `pkg-config` → 已加 `build/av3a-native/tools/pkg-config` 兼容层（不装系统包、不需 root）。
- 原生：`buildArcDav3a` 改为 `-DCMAKE_BUILD_TYPE=Release -DCMAKE_C_FLAGS_RELEASE="-O3 -UNDEBUG"`；`ffaudio.cpp` 加每 2 秒一行 `av3a-stats`（send/recv/swr 分段耗时、ratio、in_ch/in_order/sr/out_ch/gap）。
- AAR 重建成功：`libavcodec.so` 6 055 512 B（发货版 6 055 480 B），`libmedia3ext.so` 含 `av3a-stats` 标记；App 侧编译通过（Java 接口兼容）。
- 用户不愿让真机用户跑 adb；应用内已挂 `NativeAudioLogTap`（后台守护线程、每 tick 至多一次 `logcat -d -s ffmpeg_jni`、LRU 去重、每次至多 20 行、异常吞掉），把原生窗口耗时并进现有诊断导出（事件 `audio.native`）。
- 诊断包 sha256：见下（`app-leanback-arm64_v8a-debug.apk`）。
- 注意：`third_party/maven` 的 r5 AAR 为临时替换，原件在 `build/av3a-native/backup/`；本轮未提交。
- 下一步：真机播 30 秒后导出诊断日志，按 `ratio/recv_ms/swr_ms/gap_ms` 判因；结论成立再决定正式提交 AAR 与补丁。

## Checkpoint 5：合入 OK 可借鉴实现后的单包（2026-11-14）

本包同时包含：AV3A `-O3`（保留断言）、分段耗时日志、应用内 logcat 采集，以及三条来自 OK 5.1.6 对照的实现改进：

1. **免重采样快路径**：帧已交错、采样格式等于目标格式、且输出声道数等于输入声道数时直接 `memcpy`，绕开 swr。
   - 说明：AV3A 10→2 不满足条件（声道数不同），AAC/MP3 解码为平面格式也不满足，因此该路径主要覆盖“格式已匹配”的解码器；属结构性收益，不承诺对当前流明显提速。
2. **AVFrame 复用**：`FrameGuard` 在每次 decode 调用内分配一次，循环内只 `av_frame_unref`，析构负责在所有返回路径释放，消除每输出缓冲的 malloc/free。
3. **swr 排空循环**：主转换后循环 `swr_get_out_samples`/`swr_convert` 取尽残留样本，减少重采样尾部在 seek/stop 处的滞留。

未采纳：OK 的 `frame->data` 传参（≥9 声道不安全，我方继续用 `extended_data`）；输出缓冲高水位（需引入跨调用的 handle 状态，收益小、状态风险高，留待需要时再做）。

- 产物：`libmedia3ext.so` 280 824 B，含 `av3a-stats` / `direct copy` / `swr drain` 三个标记；APK 构建通过。
- 仍为临时 AAR 替换（原件 `build/av3a-native/backup/`），**未提交**；`docs` 之外的补丁文件尚未同步这三条改动，提交前必须重生成 `third_party/patches/nextlib-av3a.patch`。
