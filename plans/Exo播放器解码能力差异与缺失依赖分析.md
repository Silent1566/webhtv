# Exo 硬解卡顿的关键缺失分析

日期：2026-06-27

## 前提修正

用户反馈的卡顿场景始终使用 `Exo` 硬解。因此，不能把主因继续归到“用户选择软解导致 FFmpeg video 抢 4K 解码”。

硬解模式下我们当前代码的 render mode 是：

```java
decode == PlayerEngine.HARD
        ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
        : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER;
```

`EXTENSION_RENDERER_MODE_ON` 会把扩展 renderer 放在系统 MediaCodec renderer 后面。也就是说，在正常硬解路径中，系统 MediaCodec 仍应优先被选择。`NextRenderersFactory` 的 FFmpeg video 仍是风险，但不是当前最关键主结论。

## 最关键缺失部分

最关键缺失不是某一行业务代码，而是：我们没有拿到原版影视当前源码实际期望的完整二开 Media3 硬解产物。

原版影视 `ExoUtil` 直接调用：

```java
DefaultRenderersFactory factory = new DefaultRenderersFactory(App.get()) {
    @Override
    protected AudioSink buildAudioSink(@NonNull Context context, boolean enableFloatOutput, boolean enableAudioOutputPlaybackParams) {
        return ExoUtil.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams);
    }
};
return factory
        .setFfmpegAudioPrefer(audioPrefer)
        .setFfmpegVideoPrefer(videoPrefer)
        .setEnableDecoderFallback(true)
        .setExtensionRendererMode(renderMode);
```

这说明原版不是官方标准 `DefaultRenderersFactory`，而是一个被二开过的 `androidx.media3.exoplayer.DefaultRenderersFactory`。这条路径即使在硬解下也一定会被调用，因为 ExoPlayer 构建 renderer 列表时必须经过：

```text
ExoPlayer.Builder
  -> RenderersFactory.createRenderers()
  -> DefaultRenderersFactory.buildVideoRenderers()
  -> MediaCodecVideoRenderer / MediaCodecAdapterFactory
```

我们当前本地 `third_party/maven/androidx.media3:1.10.1-fongmi` 的 `DefaultRenderersFactory` 没有：

```java
setFfmpegAudioPrefer(boolean)
setFfmpegVideoPrefer(boolean)
```

公开 `FongMi/media` 仓库也没有这两个 API。由此可以判断：原版作者实际发布或本地构建用的 Media3 产物，和我们当前 `third_party/maven` 中的 Media3 产物不是同一个完整实现。

对“始终硬解”的用户来说，最值得怀疑的是这个二开 Media3 产物里可能包含但我们缺失的硬解相关改动：

- `DefaultRenderersFactory` 的 renderer 插入、排序、过滤逻辑。
- `MediaCodecVideoRenderer` 的 codec 初始化、fallback、丢帧、首帧、Surface 切换处理。
- `DefaultMediaCodecAdapterFactory` 或 codec adapter 的异步/同步队列策略。
- 设备 codec 黑名单、白名单、profile/level 兼容修正。
- 4K HEVC/H264 在部分设备上的 `MediaCodecInfo` 能力判断修正。
- tunnel + SurfaceView 的约束和降级策略。

这部分是硬解用户真正会调用的链路。缺失它，比 FFmpeg 软解是否存在更关键。

## 硬解路径差异总表

| 优先级 | 差异项 | 原版影视 | 我们当前 | 对 Exo 硬解的影响 | 结论 |
|---|---|---|---|---|---|
| P0 | Media3 `DefaultRenderersFactory` 产物 | 源码调用 `setFfmpegAudioPrefer()`、`setFfmpegVideoPrefer()`，说明依赖二开产物 | 当前 `third_party/maven` AAR 不存在这两个 API | 硬解构建 renderer 列表必须经过该类；如果原版二开还包含 codec/renderer 修正，我们全部缺失 | 最关键缺失 |
| P0 | `MediaCodecVideoRenderer` / codec adapter 二开补丁 | 可能在原版私有 Media3 产物中存在 | 当前无法确认具备同等补丁 | 用户始终硬解时，真正解 4K 的就是 MediaCodec renderer；这里的差异可直接导致 dropped frames 或初始化 fallback | 最需要补齐或绕开 |
| P0 | tunnel 与 SurfaceView 强绑定 | `isTunnelingEnabled() = isTunnel() && getRender() == RENDER_SURFACE`；开启 tunnel 会强制 render 为 Surface | `setTunnelingEnabled(PlayerSetting.isTunnel())`，没有确保 Surface | tunnel 在 TextureView 或异常 render 设置下可能造成硬解输出路径异常、掉帧或兼容问题 | 应优先对齐 |
| P1 | RenderersFactory 类型 | 原版使用二开 `DefaultRenderersFactory` | 我们使用 `NextRenderersFactory` | 硬解时扩展 renderer 排在系统后面，一般不抢首选；但 renderer 列表和 fallback 行为仍不同 | 应回到原版风格 |
| P1 | FFmpeg video 扩展 | 公开 `FongMi/media` 的 `ExperimentalFfmpegVideoRenderer.supportsFormat()` 返回不支持 | `NextRenderersFactory` 插入可用 `FfmpegVideoRenderer` | 硬解首选不是它，但系统 codec 初始化失败时可能 fallback 到 FFmpeg video，4K 会像 PPT | 降级为 fallback 风险 |
| P1 | `PlaybackActivity.attachSurface()` | 原版 attach 逻辑简单，播放器为空才 setPlayer | 我们会根据 render 变化 `setPlayer(null)`、`setRender()`、再 setPlayer，并有 shutter 同步 | Surface detach/attach 时机差异可能影响硬解 Surface 生命周期，特别是 4K、全屏、投屏、后台恢复 | 需要继续对齐 |
| P2 | `LoadControl` | 原版没有自定义 `LoadControl` | 我们使用 `buildLoadControl()`，支持用户 buffer 倍数/bytes/backBuffer | 主要影响缓冲，不是解码能力；若已排除 BUFFERING，优先级低于硬解 renderer | 保留观察 |
| P2 | 字幕语言选择 | 原版 `LangUtil.getPreferredTextLanguages()` | 我们 `Locale.getDefault().getISO3Language()` | 不影响 4K 硬解卡顿 | 非关键 |
| P2 | AudioSink | 原版 override `buildAudioSink()`，含 passthrough 输出策略 | 我们当前未在 `NextRenderersFactory` 路径 override AudioSink | 主要影响音频输出/直通；一般不是视频 PPT 主因，但也是原版路径差异 | 后续对齐 |

## 为什么 P0 是“Media3 硬解产物缺失”

如果用户始终硬解，真正决定 4K 是否能稳定播放的是系统硬解 renderer，而不是 FFmpeg video renderer。

系统硬解 renderer 的关键行为包括：

- 选择哪个硬件 decoder，例如 `c2.qti.*`、`OMX.MTK.*`、`OMX.Exynos.*`、`OMX.amlogic.*`。
- 判断当前 4K codec profile/level 是否支持。
- 是否启用 decoder fallback。
- codec 初始化失败后 fallback 到哪个 renderer。
- output surface 是 `SurfaceView` 还是 `TextureView`。
- tunnel mode 是否只在合法输出链路启用。
- decoder queue 和 render thread 是否容易阻塞。
- dropped frame 策略是否激进或保守。

这些都在 Media3 的 `DefaultRenderersFactory`、`MediaCodecVideoRenderer`、`MediaCodecAdapterFactory`、`MediaCodecSelector` 一层。原版源码明确依赖一个我们没有的二开 `DefaultRenderersFactory` API，所以不能假设我们当前 Media3 与原版硬解行为一致。

## 对 `NextRenderersFactory` 的重新定位

在硬解前提下，`NextRenderersFactory` 不是“首要抢解码”的主因，因为 `EXTENSION_RENDERER_MODE_ON` 下系统 MediaCodec 排在前面。

但它仍然不是原版路径，风险在于：

- 它插入的是可用的 FFmpeg video renderer。
- 原版公开 Media3 的 `ExperimentalFfmpegVideoRenderer` 明确不支持任何格式。
- 当系统 MediaCodec 不支持、初始化失败或触发 fallback 时，我们可能落到 FFmpeg video，而原版未必会。
- 它没有复刻原版私有 `setFfmpegVideoPrefer()` 的真实语义。

所以它应归为 P1：不是“硬解用户首选路径”，但会改变 fallback 行为。

## 当前最接近原版的修复方向

1. 优先补齐 `PlayerSetting.isTunnelingEnabled()`，确保 tunnel 只在 SurfaceView 下启用，并在开启 tunnel 时强制 render 为 Surface。
2. 将播放主路径从 `NextRenderersFactory` 逐步改回 `DefaultRenderersFactory` 风格，至少不要让可用 FFmpeg video 进入硬解 fallback 链路。
3. 补一个本地兼容 factory，模拟原版公开行为：保留 FFmpeg audio 能力，视频扩展默认不参与 4K 硬解竞争。
4. 对齐原版 `AudioSink` override，减少播放链路差异。
5. 继续用实机日志确认硬解用户的 `decoderName`、`droppedFrames`、`state=BUFFERING`、`surface/render`、`tunnel`。
6. 若仍是硬解 decoder 且无 BUFFERING 但掉帧严重，再重点追 `MediaCodecVideoRenderer`/adapter 层差异，考虑从公开 Media3 最佳实践中补 codec selector、异步队列策略或设备兼容规则。

## 最短结论

用户始终是 Exo 硬解时，最关键缺失部分是：

```text
原版影视实际依赖的二开 Media3 硬解 renderer/codec 产物
```

具体落在：

```text
DefaultRenderersFactory
MediaCodecVideoRenderer
MediaCodecAdapterFactory
MediaCodecSelector / codec fallback 策略
tunnel + SurfaceView 约束
```

我们当前 `third_party/maven` 里的 Media3 不包含原版源码正在调用的 `setFfmpegAudioPrefer()`、`setFfmpegVideoPrefer()`，因此不能认为硬解链路已经和原版一致。这个缺失比“FFmpeg video 软解抢 4K”更关键。

## 开源项目与公开资料搜索补充

本轮按“Exo 硬解能力优化”扩大搜索，覆盖 GitHub 开源播放器、官方 Media3/ExoPlayer issue、公开播放器实践和相关文章线索。重点不是收集 demo，而是找能迁移到我们播放链路的真实策略。

### 已检查的参考来源

| 来源 | 类型 | 相关性 | 关键发现 | 对我们的价值 |
|---|---|---|---|---|
| `androidx/media` | 官方 Media3 源码 | 高 | `DefaultRenderersFactory` 提供 `forceEnableMediaCodecAsynchronousQueueing()`、`forceDisableMediaCodecAsynchronousQueueing()`、`setAllowedVideoJoiningTimeMs()`、`experimentalSetLateThresholdToDropDecoderInputUs()`、`setMediaCodecSelector()` | 我们本地 `1.10.1-fongmi` AAR 已具备这些 API，可作为闭源补丁缺失后的替代优化入口 |
| `moneytoo/Player` / Just Player | 成熟 Android 视频播放器 | 高 | 使用 `DefaultRenderersFactory`，提供 decoder priority；README 明确写 tunneling 可改善 Android TV 4K/HDR；拿到视频 format 后对 `SurfaceView` 调 `holder.setFixedSize(width, height)` | 证明“SurfaceView + tunneling + decoder priority”是成熟播放器真实使用的方向 |
| `anilbeesetti/nextplayer` | 成熟 Android 视频播放器 | 高 | 使用 `NextRenderersFactory`，但 decoder priority 有 `DEVICE_ONLY`、`PREFER_DEVICE`、`PREFER_APP`；`DEVICE_ONLY` 对应 `EXTENSION_RENDERER_MODE_OFF` | 我们当前硬解只用 `ON`，可新增“纯系统硬解/设备优先/应用优先”区分，排除扩展 renderer fallback 干扰 |
| `brunochanrio/DangoPlayer` | Android TV/IPTV 播放器 | 中 | 作为 TV 播放器参考，但源码中硬解专项配置不如 Just Player 明确 | 可作为 UI/TV 播放形态参考，不是主要性能依据 |
| `AmbitiousJun/gemby` | Android TV/Emby 播放器 | 中 | 项目同时支持 MPV/Media3，未发现比 Just Player 更直接的 Exo 硬解策略 | 说明重度 4K 本地/网盘场景很多项目会保留 MPV 兜底 |
| `androidx/media#2990` | 官方 issue/feature request | 高 | 讨论 TV 设备高质量视频播放 dropped frames；指出 `VideoFrameReleaseControl` 50ms early release window 对低端 TV 可能不足；提到异步 MediaCodec adapter 是已有优化方向 | 对“硬解但还是 PPT”很关键：问题可能不是 codec 选错，而是 render/frame release 调度跟不上 |
| `androidx/media#2972` | 官方 issue | 中高 | Google TV Streamer 上“视频冻结、音频继续”，日志中硬解 decoder 是 `c2.mtk.avc.decoder`，证明硬解也可能出现视频 freeze | 支持继续看硬解 renderer/render loop，而不是只看网络或软解 |
| `androidx/media#2941` | 官方 issue | 中 | 切换不同帧率流时，不 `stop()` 会保留旧 frame timing 状态导致 choppy；`stop()` 可清 renderer timing 但会黑屏 | 对 VOD 连续切源、换清晰度、换线路有参考；纯单个 VOD 播放优先级低 |
| `androidx/media#2557` | 官方 issue | 中 | 部分 Android TV 设备 Exo 硬解 HDR10 异常，系统播放器/IJK 正常 | 支持“设备 codec 兼容/厂商 MediaCodec 路径”仍可能是根因 |
| Just Player README 引用的 ExoPlayer tunneling 文章 | 文章线索 | 中高 | Medium 原文被 Cloudflare 拦截，但 Just Player README 直接引用并总结：tunneling 可改善 Android TV 4K/HDR，但不是所有设备可用 | 结论可采纳，但实现必须做开关和 SurfaceView 约束 |

### 可借鉴策略按落地优先级排序

| 优先级 | 策略 | 参考来源 | 我们当前状态 | 可落地性 | 风险 |
|---|---|---|---|---|---|
| P0 | 新增“纯系统硬解”模式：硬解卡顿排查时把 extension renderer mode 设为 `OFF` | NextPlayer `DEVICE_ONLY` | 当前硬解是 `EXTENSION_RENDERER_MODE_ON`，扩展 renderer 在系统后面但仍参与 fallback | 高 | 可能失去部分特殊格式的 app decoder 兜底，但对 4K 硬解排查最干净 |
| P0 | tunnel 必须绑定 SurfaceView：`isTunnelingEnabled() = isTunnel() && render == Surface`，开启 tunnel 时强制 Surface | 原版影视、Just Player、Exo tunneling 文章线索 | 当前直接 `setTunnelingEnabled(PlayerSetting.isTunnel())` | 高 | tunnel 不是所有设备可用，需要保留开关和失败回退 |
| P1 | 使用官方异步 MediaCodec 队列：对 Android 12+ 或 TV/低性能设备尝试 `forceEnableMediaCodecAsynchronousQueueing()` | 官方 Media3 API、`androidx/media#2990` | 当前未使用 | 中高 | 异步队列历史上有设备兼容风险，建议做开关/灰度 |
| P1 | 对 SurfaceView 设置视频固定尺寸：拿到 `Format.width/height` 后 `SurfaceView.getHolder().setFixedSize(width, height)` | Just Player、Exo issue 8611 代码注释 | 当前未做 | 中 | 需要只对 `SurfaceView` 做，且注意旋转/切换视频后的重置 |
| P1 | 对 TV/低性能设备放宽 video joining 或 late-drop 阈值实验：`setAllowedVideoJoiningTimeMs()`、`experimentalSetLateThresholdToDropDecoderInputUs()` | 官方 Media3 API、`androidx/media#2990` | 当前未用 | 中 | 参数过大可能增加响应延迟或掩盖真实卡顿，需要实机对比 |
| P1 | 增加 codec selector 过滤/排序能力：优先厂商硬解，必要时屏蔽已知问题 codec | 官方 `setMediaCodecSelector()` | 当前用默认 selector | 中 | 需要设备日志和黑名单数据；不能盲目全局屏蔽 |
| P2 | 对切源/换线路/换帧率场景强制释放重建 player 或 renderer | `androidx/media#2941` | 当前播放链路有复用 player 和 surface attach 逻辑 | 中 | 会带来黑屏或首帧延迟；对单 VOD 4K PPT 不是第一优先 |
| P2 | 自动刷新率匹配 / frame rate strategy | Just Player README、Android TV 实践 | 当前未见明确使用 | 中低 | 主要影响 TV 端 24/25/50/60fps judder，不一定解决手机/投影 4K 解码吞吐 |

### 对我们困境的判断

如果用户日志始终是厂商硬解 decoder，例如 `c2.mtk.*`、`OMX.MTK.*`、`c2.qti.*`、`OMX.amlogic.*`，但画面仍像 PPT，下一步不能只盯“是否软解”。公开资料显示，硬解仍可能卡在这些层：

- MediaCodec 同步队列/异步队列调度。
- VideoFrameReleaseControl / VideoFrameReleaseHelper 的帧释放节奏。
- SurfaceView/TextureView 输出路径。
- tunnel 是否合法启用。
- 设备厂商 codec 对 4K/HDR/Profile/Level 的兼容缺陷。
- ExoPlayer renderer state 在切源/恢复/Surface 重建后的旧状态残留。

所以，在拿不到原版闭源 Media3 二开产物的情况下，最现实的替代路线不是继续猜网络，而是：

1. 先做“纯系统硬解”模式，彻底移除扩展 renderer fallback 干扰。
2. 立即对齐原版 tunnel + SurfaceView 约束。
3. 增加可开关的异步 MediaCodec 队列实验项。
4. 给 SurfaceView 增加 fixed size 处理。
5. 用实机日志记录 decoderName、droppedFrames、surface 类型、tunnel、async codec mode、video size、frame rate。
6. 再根据设备日志做 codec selector 黑名单/白名单，而不是全局硬编码。

### 当前推荐的修复顺序

| 顺序 | 修复项 | 理由 |
|---|---|---|
| 1 | `PlayerSetting.isTunnelingEnabled()` 与原版一致，tunnel 强制 Surface | 低风险、直接对齐原版、硬解实际调用 |
| 2 | 增加“纯系统硬解/设备优先/应用优先”三档，默认硬解排查可用 `EXTENSION_RENDERER_MODE_OFF` | 借鉴 NextPlayer，能确认 nextlib fallback 是否干扰 |
| 3 | 保持主路径回归 `DefaultRenderersFactory` 风格，必要时只保留 FFmpeg audio | 更接近原版和公开 FongMi/media 行为 |
| 4 | 增加 `forceEnableMediaCodecAsynchronousQueueing()` 实验开关 | 官方 API 已存在，针对低端 TV/4K dropped frames 有明确讨论 |
| 5 | SurfaceView fixed size | Just Player 已实践，风险可控 |
| 6 | 收集设备 codec 日志后做 `MediaCodecSelector` 兼容规则 | 需要真实数据，不能先拍脑袋 |

### 不建议直接照搬的点

- 不建议全局强制异步 MediaCodec 队列。官方也保留了 force enable/disable，说明它是兼容性敏感项。
- 不建议全局开启 tunnel。Just Player 也把它作为用户设置，因为并非所有设备可用。
- 不建议直接把 `setAllowedVideoJoiningTimeMs()` 或 late-drop 阈值调很大。这类参数能减少掉帧，但可能增加交互延迟或隐藏根因。
- 不建议只因为某个设备系统播放器正常，就假设 Exo 无法解决。系统播放器可能走私有厂商路径，但 Exo 仍可通过 Surface/tunnel/async/codec selector 缓解一部分。

## 官方底层资料与非 Exo 播放器补充

本轮继续扩大到 Android 官方文档、Media3 官方 issue、IJK、mpv-android、VLC、Kodi、Nova、Just Player、NextPlayer、GSY/DKVideoPlayer 等方向。结论不是“找到一段代码照搬”，而是把成熟播放器处理 4K 卡顿时反复出现的思路抽出来。

### 参考来源总表

| 来源 | 类型 | 是否直接相关 | 已确认的关键点 | 对我们的启发 |
|---|---|---:|---|---|
| Android `MediaCodec` 官方文档 | 官方底层 API | 是 | `SurfaceView` 使用 `releaseOutputBuffer(index, renderTimestampNs)` 时按 VSYNC 调度；timestamp 必须接近 `System.nanoTime()`；多个 buffer 命中同一 VSYNC 时只显示最后一个，其余会被丢弃；最佳时机约为目标渲染前两个 VSYNC，60Hz 约 33ms | Exo 硬解已经选中后，仍可能因为帧释放节奏、SurfaceView 输出时机不稳而像 PPT |
| Android `MediaCodec` 异步模式文档 | 官方底层 API | 是 | async callback 必须在 `configure()` 前设置；async 模式不能再走同步 dequeue；`flush()` 后必须 `start()` 才会继续收到 buffer | Media3 的 async codec queueing 是有效入口，但必须用官方/Media3 已封装 API，不能手写混用同步/异步 |
| Android `PARAMETER_KEY_LOW_LATENCY` | 官方底层 API | 中 | API 30+ 可启用低延迟解码，减少 codec 持有 input/output 数据 | 可作为实验项或设备定向项，不适合默认全局打开 |
| Android `VideoCapabilities.PerformancePoint` | 官方能力 API | 是 | API 29+ 提供像素数、像素率、帧率上界，`covers()` 可判断 codec 性能点是否覆盖目标视频 | 可做 4K/60 风险分类和日志，不应该作为唯一拒绝依据，因为部分设备上报不可靠 |
| Media3 `MediaCodecVideoRenderer` | 官方播放器实现 | 是 | 内部已有 dropped input/output buffer、late-drop、operating-rate、Surface 切换判断、VideoFrameReleaseControl 等逻辑 | 闭源二开 Media3 缺失时，优先用公开 factory API 暴露的开关，而不是乱改 renderer |
| Media3 `DefaultRenderersFactory` | 官方播放器入口 | 是 | 可配置 `forceEnableMediaCodecAsynchronousQueueing()`、`forceDisableMediaCodecAsynchronousQueueing()`、`setMediaCodecSelector()`、`setAllowedVideoJoiningTimeMs()`、`experimentalSetLateThresholdToDropDecoderInputUs()` | 这是我们能在不拿到原版闭源补丁的前提下最现实的 Exo 优化入口 |
| Media3 issue `#2972` | 官方 issue | 是 | Google TV Streamer 上硬解 `c2.mtk.avc.decoder` 后视频冻结、音频继续；VLC 不冻结 | 证明“硬解 decoderName 正常”不等于 Exo 链路正常；同源 VLC/IJK 正常时仍要查 Exo renderer/Surface/同步 |
| Media3 issue `#2941` | 官方 issue | 中高 | 不同帧率流切换后，不 `stop()` 会保留旧 frame timing 状态并持续 choppy；`stop()` 会恢复平滑但带黑屏 | 如果卡顿发生在换集、换源、恢复播放后，需要考虑重置 renderer/frame timing 状态 |
| Media3 issue `#1621` | 官方 issue | 中 | 多个盒子上字幕 extraction/audio discontinuity 会导致视频 pixelating | 音频 sink、字幕解析、demux 负载也可能影响视频流畅；但带开关且未启用的功能不用优先动 |
| Just Player | Exo 成熟播放器 | 是 | 提供 decoder priority：偏好设备、偏好 app、仅设备；tunneling 明确用于改善 Android TV 4K/HDR；`SurfaceView` 收到 format 后 `setFixedSize(width, height)`；实现 display mode/refresh rate 匹配 | 我们应补“纯系统硬解”模式、tunnel + SurfaceView 约束、SurfaceView fixed size、TV 刷新率诊断 |
| NextPlayer | Exo/nextlib 播放器 | 是 | decoder priority 有 `DEVICE_ONLY`、`PREFER_DEVICE`、`PREFER_APP`，其中 `DEVICE_ONLY` 对应 extension renderer off | 我们当前硬解仍是 `EXTENSION_RENDERER_MODE_ON`，排查 4K PPT 时不够干净 |
| mpv-android | 非 Exo 成熟播放器 | 是 | `hwdec=mediacodec,mediacodec-copy`；`profile=fast`；可选 `video-sync=audio/display-resample/display-vdrop`；设置 `display-fps-override`；支持 `vd-lavc-fast`、`skiploopfilter`；限制 demuxer cache；Surface 变化时设置 `android-surface-size` | 成熟播放器会把解码吞吐、显示同步、Surface 尺寸、缓存背压分开调；Exo 也应分层诊断，不只看 decoderName |
| IJK / bilibili ijkplayer | 非 Exo 成熟播放器 | 是 | 支持 MediaCodec；有 codec selector 枚举、评分、拒绝低分 codec；播放器参数常见 `framedrop=1`、`video-pictq-size`、`mediacodec-handle-resolution-change` | IJK 同源不卡时，重点对比“codec 选择、迟到帧处理、分辨率变化、输出队列”而不是只看硬解是否开启 |
| VLC Android / libVLC | 非 Exo 成熟播放器 | 中高 | 大型长期维护播放器，Android 端基于 libVLC，实测 issue 中常作为 Exo 冻结时的正常对照 | 可借鉴“多播放内核兜底”和“设备特例修正”思路，但不适合直接搬 VLC 内核逻辑到 Exo |
| Kodi / XBMC | 非 Exo 家庭影院播放器 | 中 | 大型 TV/盒子媒体中心，长期处理刷新率、HDMI、音视频同步、硬解兼容 | 强化判断：TV 4K 卡顿不只是 decoder，还包括显示模式、刷新率、HDR/色彩、音频直通 |
| Nova Video Player | 非 Exo/系统兼容经验 | 中高 | FAQ/变更长期强调 HEVC 硬解能力、Dolby Vision/HDR、HDMI 显示模式/色彩空间、自适应刷新率、音频实现差异、WebDAV/SFTP 高码率稳定性 | 对用户侧诊断很有价值：同样 4K 卡顿可能由 DV/HDR/profile/display/audio sink 触发 |
| GSYVideoPlayer / DKVideoPlayer | 多内核封装播放器 | 中低 | 同时封装 IJK、Exo、MediaPlayer，多用于业务播放器组件 | 对底层 Exo 优化帮助有限，但证明“保留多内核切换”是 Android 播放器常见生产策略 |

## IJK 同一个 4K 不卡、Exo 反而卡的判断

这个现象很重要。它说明不能简单归因成“设备硬解能力不够”，因为 IJK 同样可以调用 Android MediaCodec 硬解。如果同源、同设备、同网络下 IJK 流畅，Exo 卡得像 PPT，更可能是下面这些链路差异。

| 差异点 | 我们当前 IJK | 我们当前 Exo | 为什么会导致 IJK 更顺 |
|---|---|---|---|
| codec 选择策略 | `IjkMediaPlayer.DefaultMediaCodecSelector` 会枚举所有 codec，按 known list/rank 评分，拒绝 rank `< 600` 的 codec；MTK、qcom、Exynos、amlogic 等有显式排名 | 当前 Exo 走默认 `MediaCodecSelector`，没有本地评分/诊断/黑名单 | 同一 mime 下 Exo 和 IJK 可能选到不同硬解器；即使名字相同，IJK 会更明确地排除低可信 codec |
| 迟到帧处理 | `framedrop=1` | Exo 迟到帧策略由 `MediaCodecVideoRenderer` 和 frame release control 决定，当前没有显式实验参数 | IJK 更倾向“丢迟到帧保播放节奏”，用户感知可能是顺；Exo 如果保留过多迟到帧，容易像 PPT |
| 解码/显示队列 | `video-pictq-size=3`，native 播放队列较小 | Exo 由 renderer/codec adapter/Surface 调度决定，当前没有针对 4K 的队列策略开关 | 小队列能降低积压，避免越播越落后；Exo 队列和 VSYNC 释放不匹配时会积压 |
| 分辨率变化 | `mediacodec-handle-resolution-change=decode` | Exo 支持 codec 复用/重建，但当前没有针对异常设备的强制重建策略 | 某些网盘/转封装源可能中途 format/sps 信息变化，IJK 显式处理可能更稳 |
| Surface 绑定 | IJK 直接 `setDisplay(surfaceHolder)` / `setSurface(surface)`，Surface 回调中重新绑定 | Exo 通过 `PlayerView` + render 类型管理，之前已有 Surface attach/detach 对齐问题 | Surface 生命周期时机不同会影响硬解输出，尤其是 TextureView、全屏、恢复播放 |
| 音频输出 | IJK 当前 `opensles=0`，音频同步由 IJK/native 管 | Exo 走 `DefaultAudioSink`，原版影视还有二开 `buildAudioSink()` override | 音频 clock 是视频释放的重要基准，音频 sink 差异可能让视频帧释放节奏不同 |
| demux/读取 | IJK 有 `http-detect-range-support=0`、`max-buffer-size=15728640`、`fflags=fastseek` | Exo 走 MediaSource/DataSource/LoadControl，当前有自定义 buffer 倍数和 bytes | 如果日志不是 BUFFERING，而是 dropped frames，这不是主因；但同源下读包粒度仍可能影响上游背压 |
| 扩展 renderer/fallback | IJK 是单内核配置；硬解失败就按 IJK 规则处理 | Exo 当前 `NextRenderersFactory` + `EXTENSION_RENDERER_MODE_ON`，扩展 renderer 仍在 fallback 链路 | 4K 硬解排查时应有“仅系统硬解”档，避免 fallback 行为污染判断 |

### 对 IJK 不卡现象的更准确结论

IJK 不卡不等于“Exo 一定无解”，但它基本排除了“这台设备绝对无法硬解这个 4K”的说法。更合理的根因范围是：

1. Exo 选择的 codec、profile/level 判断或 fallback 顺序不如 IJK 稳。
2. Exo 的 `VideoFrameReleaseControl` / `SurfaceView` timestamp / VSYNC 调度在这台设备上不如 IJK 的 native 输出路径稳。
3. Exo 的迟到帧策略更保守，导致播放节奏落后后表现成 PPT；IJK 通过 `framedrop=1` 保住时间轴。
4. Exo 的 audio sink 或音视频同步基准与原版影视二开 Media3 不一致。
5. Exo 的 Surface 生命周期、tunnel、TextureView/SurfaceView 约束没有完全贴近原版。

## 4K 60fps 用户反馈专项补充

用户新增反馈：低端电视盒子播放 4K 网盘卡成 PPT 的样本有一个共同点：基本都是 `60fps`；只要不是这么高码率/高帧率的内容，播放通常正常。

这个信息非常关键，需要把 `4K60` 单独作为 P0 诊断维度。原因是同为 4K，`60fps` 的像素吞吐约等于 `30fps` 的两倍：

```text
3840 x 2160 x 60fps = 497,664,000 pixels/s
3840 x 2160 x 30fps = 248,832,000 pixels/s
```

低端电视盒子常见宣传“支持 4K”不代表稳定支持目标编码、profile、level、HDR、码率和 `60fps` 的组合。很多设备只能稳定覆盖 `4K30` 或特定格式的 `4K60`，一旦遇到 `HEVC Main10 / HDR / 高码率 / 60fps / 网盘封装` 叠加，Exo 即使走硬解也可能大量 dropped frames。

### 4K60 对现有结论的修正

| 维度 | 之前判断 | 加入 60fps 信息后的判断 |
|---|---|---|
| 网络/缓存 | 已基本排除单纯 BUFFERING | 仍需看日志，但如果低码率非 60fps 正常，主因更偏解码吞吐或显示调度 |
| 是否软解 | 用户始终 Exo 硬解 | 更明确：重点不是软解抢占，而是硬解能力上限、codec 选择和帧释放 |
| 原版闭源 Media3 差异 | P0 | 仍是 P0，因为硬解 renderer/codec adapter 对 4K60 dropped frames 很敏感 |
| tunnel + SurfaceView | P0/P1 | 升级为 4K60 优先实验项。官方 issue 中已有 4k@60hz stutter 通过 tunneling 缓解的案例 |
| 异步 MediaCodec 队列 | P1 | 对 4K60 更有价值，可能降低 codec/render 线程阻塞，但必须做开关 |
| SurfaceView fixed size | P1 | 对 4K60 更有价值，避免 Surface/缩放路径额外压力 |
| 设备能力判断 | 原文提到 PerformancePoint | 升级为必须补日志/诊断项：判断 codec 是否声明覆盖目标 width/height/fps |
| 自动降级/提示 | 原文只作为思路 | 对低端盒子 4K60 应作为产品策略：提示切 IJK、开 tunnel、换低帧率/低码率源 |

### 针对性外部搜索结果

| 来源 | 关键发现 | 对我们的结论 |
|---|---|---|
| `androidx/media#926` | 小米/Nokia TV 盒子在 `4k@60hz` 显示模式下 Exo stutter，切到 `4k@50hz` 正常；Exo demo app 也可复现 | 证明这类问题可能不是业务代码，而是低端盒子 + 显示刷新率/输出链路/Exo frame release 的组合问题 |
| `androidx/media#926` 评论 | 官方让排查 `VideoFrameReleaseHelper` 的 display refresh rate、`vfpo`、`db/dropped buffers`、`releaseTimeNs` 是否重复或间隔异常 | 我们日志应补显示刷新率、视频 fps、dropped frames、release/渲染指标；仅有 decoderName 不够 |
| `androidx/media#926` 结论 | 用户反馈开启 `tunneling` 后问题消失；官方建议只在 SurfaceView 且播放音视频时启用，不建议全局默认开启 | 我们已对齐 tunnel 只能 SurfaceView，但可以针对 4K60 增加“建议开启/自动实验/设备记忆” |
| `androidx/media#2941` | 60fps 与其他帧率流切换时可能残留 frame timing 状态，`stop()` 会清掉 renderer timing | 如果卡顿发生在切源、换集、恢复播放后，应考虑 4K60/帧率变化时重建 player 或 stop+prepare |
| Android `VideoCapabilities.PerformancePoint` | API 29+ 可用性能点判断 codec 是否覆盖目标分辨率和帧率 | 可用于 4K60 风险日志和策略提示，但不能作为唯一拦截条件，因为部分设备上报不准 |

### 新的优先级建议

| 优先级 | 动作 | 原因 | 风险 |
|---|---|---|---|
| P0 | 播放日志补齐 `format.frameRate`、`bitrate`、`decoderName`、`droppedFrames`、`surface/render`、`tunnel`、设备显示刷新率 | 4K60 必须先区分“解码跟不上”还是“显示/帧释放不稳” | 只加日志风险低 |
| P0 | 增加 codec 性能点诊断：API 29+ 用 `VideoCapabilities.PerformancePoint.covers()` 检查当前 codec 是否声明覆盖 `width x height x fps` | 可以直接判断低端盒子是否只是标称 4K，但未覆盖 4K60 | 设备上报可能不准，只能用于日志/提示 |
| P0 | 给 4K60 Exo 增加“纯系统硬解”开关或策略：`EXTENSION_RENDERER_MODE_OFF` | 排除 FFmpeg video fallback 链路对高帧率的干扰 | 可能失去部分特殊格式 fallback |
| P0 | 4K60 + SurfaceView 时优先建议开启 tunnel，或做可撤销实验开关 | 官方 issue 中 `4k@60hz stutter` 通过 tunneling 缓解；原版也要求 tunnel 绑定 SurfaceView | tunnel 有设备特例，不适合无条件默认 |
| P1 | 4K60 时尝试 `forceEnableMediaCodecAsynchronousQueueing()` 实验开关 | 针对高吞吐硬解队列阻塞，公开 Media3 API 支持 | 部分设备兼容性未知 |
| P1 | SurfaceView 收到视频尺寸后 `holder.setFixedSize(width, height)` | Just Player 实践，减少 Surface 缩放/合成压力 | 需要处理旋转、切源和恢复默认 |
| P1 | 对 4K60 严重 dropped frames 做用户提示：建议 IJK、开启 tunnel、换低帧率/低码率源 | 如果设备硬解性能点不覆盖，继续硬撑 Exo 没意义 | 需要避免频繁打扰 |
| P2 | 切源/换集/恢复播放且 fps 变化明显时，考虑 stop/rebuild 清 renderer timing | 对 `60fps -> 25/30fps` 或反向切换有参考 | 会带来黑屏或首帧延迟 |

### 当前最可落地的下一步

先不直接拍脑袋改默认策略，建议先补一个“4K60 诊断闭环”：

1. 在 `PlaybackAnalyticsListener` 里增加显示刷新率、当前 render/tunnel、是否 4K60 风险的日志。
2. 在视频 format 到达时，API 29+ 枚举当前 mime 的硬解 codec performance points，记录是否覆盖 `width x height x fps`。
3. 如果 `frameRate >= 50` 且 `width >= 3840` 或 `height >= 2160`，把日志标记为 `risk=4k60`。
4. 实机验证同一个源下：
   - Exo + tunnel off
   - Exo + tunnel on
   - Exo + pure device decoder / extension off
   - IJK
5. 如果 tunnel on 明显改善，做“4K60 低端盒子建议开启 tunnel”的产品策略。
6. 如果 tunnel 无效但 dropped frames 高，继续试异步 MediaCodec 队列和 pure device decoder。
7. 如果 performance point 明确不覆盖 4K60，优先提示用户切 IJK 或换低帧率源，而不是继续在 Exo 上调缓存。

### 结论

`60fps` 是当前反馈里最关键的新线索。它把问题从“泛泛的 4K 网盘卡顿”收窄为：

```text
低端盒子 + Exo 硬解 + 4K60 高像素吞吐 + 显示刷新率/帧释放/tunnel/codec 性能点
```

这比继续调网盘缓存更有方向。下一轮代码优化应优先做 4K60 诊断日志和可切换实验项，而不是直接全局改默认播放策略。

## 可迁移到我们代码的处理逻辑清单

| 优先级 | 处理逻辑 | 来源依据 | 是否应默认开启 | 预期收益 | 主要风险 |
|---|---|---|---:|---|---|
| P0 | 增加 Exo codec 诊断日志：列出候选 codec、最终 codec、profile/level、PerformancePoint 覆盖、hardware/software、secure/tunnel 支持、operating rate、video size/fps/HDR/DV 信息 | IJK selector、Android PerformancePoint、Media3 EventLogger | 是，仅日志 | 先知道 Exo 和 IJK 是否选了同一 decoder，以及 4K 是否超上报能力 | 日志量需受调试开关控制 |
| P0 | 增加“仅系统硬解”模式，把 extension renderer mode 设为 `OFF`，用于 4K 卡顿排查 | Just Player / NextPlayer decoder priority | 不一定默认，可作为硬解排查档 | 排除 nextlib/FFmpeg video fallback 干扰 | 少数格式失去 app decoder 兜底 |
| P0 | tunnel 与 SurfaceView 强绑定，开启 tunnel 时强制 SurfaceView，否则不启用 tunneling | 原版影视、Just Player、Exo tunneling 实践 | 保持用户开关，但约束合法条件 | TV 4K/HDR 可能改善，且更贴近原版 | 部分设备 tunnel 不稳定，必须可关闭 |
| P1 | 实现 Exo `MediaCodecSelector` 诊断版，再逐步加入 IJK 风格 ranking：拒绝明显 software/低可信 codec，优先厂商硬解和已知低延迟 codec | IJK `IjkMediaCodecInfo` | 初期只日志，不改变选择 | 能定位 Exo 是否选错 codec；后续可做设备白/黑名单 | 盲目改选择会误伤设备 |
| P1 | 增加 async MediaCodec queueing 实验开关：`forceEnableMediaCodecAsynchronousQueueing()` / `forceDisableMediaCodecAsynchronousQueueing()` | Media3 API、Android async 文档、Media3 issue | 不默认全局开启 | 降低 codec 同步 dequeue 阻塞，改善部分 TV/盒子掉帧 | 厂商兼容敏感 |
| P1 | SurfaceView 收到真实视频尺寸后 `holder.setFixedSize(width, height)`，切换视频时更新或重置 | Just Player、Android SurfaceView 输出实践 | 可对 SurfaceView 默认做，需谨慎验证 | 降低 Surface 缩放/缓冲尺寸不匹配带来的 4K 输出压力 | 旋转、竖屏短剧、全屏切换需验证 |
| P1 | 增加 late-drop 实验参数：`experimentalSetLateThresholdToDropDecoderInputUs()`，模拟 IJK `framedrop=1` 的“保节奏”思路 | IJK framedrop、Media3 API | 不默认，作为高级/实验 | 如果 Exo PPT 是越积越落后，丢迟到帧可能明显改善体感 | 画面完整性下降，参数过激会跳帧明显 |
| P1 | 记录并可选设置 codec operating rate / assumed minimum codec operating rate | Media3 `KEY_OPERATING_RATE` | 初期日志优先 | 让 codec 按目标帧率/倍速准备吞吐 | `DefaultRenderersFactory` 不直接暴露所有 builder 能力，可能需自定义 renderer |
| P2 | TV 端刷新率/显示模式诊断和可选匹配：优先同分辨率、刷新率为视频 fps 整数倍或不低于 fps | Just Player、Kodi/Nova/mpv 思路 | 诊断默认，自动切换需开关 | 解决 24/25/50/60fps 与显示刷新率不匹配造成的 judder | 切换 display mode 会黑屏/闪烁，手机端不应强做 |
| P2 | HDR/Dolby Vision/profile 风险识别：DV 不支持时尝试映射/降级 HEVC 或提示换内核 | Nova、Just Player `setMapDV7ToHevc` | 只在明确格式/设备不支持时 | 某些“4K 卡”其实是 DV/HDR/profile 兼容问题 | 错误降级可能色彩异常 |
| P2 | 切源/换集/恢复播放后必要时重置 renderer/frame timing，而不是盲目复用旧状态 | Media3 issue `#2941` | 场景触发 | 解决换不同 fps/格式后持续 choppy | stop/rebuild 可能黑屏或首帧慢 |
| P3 | 继续保留 IJK 作为 4K 问题源兜底内核 | GSY/DKVideoPlayer 多内核实践、用户实测 IJK 不卡 | 是 | 当 Exo 遇到设备特定 bug 时给用户可用路径 | 不是修复 Exo 本身 |

## 当前最可能的突破口

结合“原版影视 Exo 正常、我们 Exo 卡、我们 IJK 同源不卡”这三个条件，优先级应调整为：

1. 先确认 Exo 与 IJK/原版实际选中的 decoder 是否一致。如果不同，优先做 Exo codec selector 诊断和“仅系统硬解”模式。
2. 如果 decoder 一致但 Exo dropped frames 高，优先看 SurfaceView/tunnel/async codec queueing/late-drop，而不是网络。
3. 如果 Exo 没有明显 dropped frames 但体感卡，查显示刷新率、frame release、HDR/DV、音频 sink 同步。
4. 如果只在换源、换集、恢复后卡，查 renderer/frame timing 旧状态残留，必要时重建播放器或 renderer。
5. 如果所有 Exo 公开策略都无效，而原版影视仍正常，则闭源二开 Media3 renderer/codec 补丁仍是最大缺口。

因此，下一个代码改造不应该一次性大改播放逻辑。更稳的路径是先落地 P0 诊断和“仅系统硬解”排查档，再用实机日志决定是否启用 codec selector ranking、async queueing、late-drop 或刷新率匹配。
