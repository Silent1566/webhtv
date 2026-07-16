# 安卓 MPV 播放器集成实现与踩坑记录

记录时间：2026-07-07
分支：`feature/android-mpv-player`
回滚标签：`mpv-player-integration-20260707-075715`

## 当前结论

当前 MPV 播放器是基于 `libmpv` 集成，不是调用外部 MPV App。底层入口是 `is.xyz.mpv.MPVLib`，Media3 适配层是 `androidx.media3.mpvplayer.MpvPlayer`，播放器引擎入口是 `com.fongmi.android.tv.player.engine.MpvPlayerEngine`。

MPV 作为独立播放器类型接入，不能自动切换到 Exo。播放失败只能报错，由用户手动切换播放器。这一点是产品约束，也是排查问题时必须保持的边界。

本次可播放的关键不是“MPV 比 Exo 弱”，而是项目里 Exo 已经有成熟的 HLS/TS 容错链路，而 libmpv/FFmpeg 对部分异常源更严格。MPV 需要补齐这些输入层处理。

## 主要实现

### Media3 适配层

新增/使用的核心类：

- `app/src/main/java/androidx/media3/mpvplayer/MpvPlayer.java`
- `app/src/main/java/androidx/media3/mpvplayer/MpvPlayerConfig.java`
- `app/src/main/java/androidx/media3/mpvplayer/MpvHlsProxy.java`
- `app/src/main/java/is/xyz/mpv/MPVLib.java`
- `app/src/main/java/com/fongmi/android/tv/player/engine/MpvPlayerEngine.java`

`MpvPlayer` 基于 `SimpleBasePlayer` 实现 Media3 `Player` 行为映射：

- `setMediaItem` / `prepare` -> `MPVLib.command(loadfile ...)`
- `play` / `pause` -> `pause` 属性
- `seekTo` -> `seek absolute+exact`
- `duration` / `position` -> `duration/full`、`time-pos/full`
- Surface -> `attachSurface` / `detachSurface`
- 状态 -> `STATE_BUFFERING`、`STATE_READY`、`STATE_ENDED`、`STATE_IDLE`

### Header 处理

MPV 路径会从 `MediaItem.requestMetadata.extras` 读取原始请求头，并补齐：

- `User-Agent`
- `Referer`
- `Origin`
- `Accept: */*`

显式传入的 header 优先保留。缺失 `Referer` 时按媒体 URL origin 自动补齐，缺失 `Origin` 时按 `Referer` 或媒体 URL origin 补齐。

对应 Exo 实现：

- `MediaSourceFactory.createMediaSource()` 每次用 `ExoUtil.extractHeaders(mediaItem)` 更新 `OkHttpDataSource.Factory` 默认请求头。
- HLS playlist、key、segment 由同一个 DataSource 链路继承 header。

MPV 必须显式设置 `user-agent`、`referrer`、`http-header-fields`，否则 playlist 可以打开但分片/key 可能因 header 不一致失败。

### HLS 代理

新增 `MpvHlsProxy`，只给 MPV HLS/http 场景使用。

流程：

1. MPV 准备播放 HLS 时，把远端 m3u8 改成本机地址：`http://127.0.0.1:<port>/mpv/index.m3u8?s=<session>`。
2. 代理用 OkHttp 拉原 m3u8，继承 MPV 整理后的 header。
3. 代理重写 m3u8 内的 segment URL、nested playlist URL、`#EXT-X-KEY URI="..."` 为本机 `/mpv/item`。
4. MPV 只访问本机 m3u8 和本机分片端点。
5. 本机分片端点再请求真实分片，必要时清洗异常前缀后流式返回给 MPV。

这个代理不接管 Exo/IJK，不改全局 server 行为。

## 踩坑记录

### 1. 不能用自动切换播放器掩盖 MPV 错误

问题：

早期出现 MPV 报错后自动切 Exo，用户看到的是“播放器自动切换”，不利于确认 MPV 自身问题。

处理：

MPV 错误保持在 MPV 引擎内上报，播放器切换只允许用户手动操作。

对应 Exo：

Exo 是成熟默认播放器，但不能作为 MPV 自动 fallback。对照 Exo 是为了补齐处理逻辑，不是播放失败就切回 Exo。

### 2. 早期 END_FILE 不是根因

问题：

曾尝试把早期 `END_FILE` 改成“不立即报错，继续等 FILE_LOADED 或 15 秒播放超时”。这个方向只能缓解事件时序误判，不能解决黑屏。

根因：

实际日志中 MPV 已经 `file-loaded`，但视频尺寸是 `0x0`，并出现：

- `Video: png, none`
- `Invalid data found when processing input`
- `no audio or video data played`

这说明不是单纯等待事件的问题，而是输入流被识别成图片/坏数据。

对应 Exo：

Exo 对 HLS/TS 的读取不是靠等待更多事件解决，而是在 extractor 层对 TS 同步字节做容错。

### 3. PNG 伪装 TS 分片

问题源表现：

某些 HLS 分片 URL 会 302 到阿里图片 CDN，响应头是 `Content-Type: image/png`，开头是一个 1x1 PNG。PNG `IEND` 之后紧跟 TS 数据：

- 前 8 字节是 PNG signature。
- 找到 `IEND AE 42 60 82` 后，后面出现 `0x47` TS sync byte。
- `strings` 可见 `FFmpeg`、`x264` 等视频编码痕迹。

MPV/FFmpeg 的表现：

FFmpeg demuxer 根据开头 PNG 签名把流识别为 PNG，随后报：

- `Could not find codec parameters for stream 0 (Video: png, none)`
- `Invalid data found when processing input`

处理：

`MpvHlsProxy.PngPrefixStrippingInputStream` 在分片首包内检测 PNG signature 和 IEND。若 IEND 后看起来是 TS，则剥掉 PNG 前缀，只把 TS 数据返回给 MPV，并把 mime 改成 `video/MP2T`。

对应 Exo：

项目使用的 Media3 `TsExtractor` 会查找 `0x47` TS sync byte，并丢弃同步字节前的数据。关键逻辑在 `TsExtractor.sniff()` / `findEndOfFirstTsPacketInBuffer()`：

- `TsUtil.tryToFindSyncBytePosition(...)`
- `TsUtil.findSyncBytePosition(...)`
- 丢弃 sync byte 之前的数据

所以 Exo 可以容忍“前面有垃圾头、后面才是 TS”的分片。MPV 没有这层容错时会被 PNG 签名带偏。

### 4. HLS 强制参数不是万能解法

尝试过的 MPV 参数：

- `demuxer=lavf`
- `demuxer-lavf-format=hls`
- `demuxer-lavf-probesize=10485760`
- `demuxer-lavf-analyzeduration=5`
- `http-allow-redirect=yes`
- `hls-bitrate=max`

这些参数可以帮助 MPV 正确按 HLS 打开 playlist，但不能解决分片开头是 PNG 的情况。因为 demuxer 已经在 segment 级别被错误签名误导。

对应 Exo：

Exo 的解决点不是“加大探测”，而是 TS extractor 读包时不断找 sync byte。MPV 也应在进入 FFmpeg 前把输入清洗干净。

### 5. 切换集/视频后黑屏、连接超时

现象：

第一集可以播放，切换下一集或另一个视频后黑屏，最后 UI 显示“连接超时”。

日志特征：

- 新 session 已创建：`mpv-proxy: enabled session=N ...`
- `MpvPlayer` 记录了 `load uri=http://127.0.0.1:<port>/mpv/index.m3u8?s=N`
- 但后续没有新的 `event=start-file`
- 也没有新 playlist 请求
- 只看到 `load retry attempt=...`

中间还出现过旧会话 item 被清空后，旧 HLS 请求打到 `/mpv/item?s=old&id=...` 返回 404，进一步干扰判断。

处理：

1. `MpvHlsProxy` 保留最近 session，旧会话自然过期，不在新视频开始时立即清空旧 item 表。
2. `MpvPlayer` 在新媒体 `setMediaItem` 时重置 libmpv context：
   - detach surface
   - remove observer/log observer
   - destroy
   - 下一次 `prepare` 重新 create/init
   - 重新绑定同一个 Surface
3. 保留短暂 `loadfile` 启动重试日志作为诊断，但主要依赖 context reset 解决复用 libmpv 时 stop/load 异步竞态。

对应 Exo：

Exo 每次 `setMediaItem(item, position)` + `prepare()` 会创建新的 MediaSource/MediaPeriod/Loader 生命周期。旧 HLS chunk load 会在 release/stop 时关闭，不会复用一个失控的旧 HLS demuxer 去吞掉新媒体命令。

MPV 的 `loadfile` 和 `stop` 是异步命令；如果在同一个 libmpv context 内快速切换，旧 HLS demuxer 可能还在处理分片，新 `loadfile` 不一定可靠进入 `START_FILE`。因此 MPV 切媒体要模拟 Exo 的隔离语义。

### 6. Surface 不是这次黑屏主因

多次日志显示：

- `surface attached ... vo=gpu`
- `VO: [gpu] 1280x720 mediacodec`
- `event=playback-restart ... size=1280x720`

这说明至少在首播成功路径，Surface 绑定和硬解输出是正常的。黑屏主要来自输入层和切换生命周期，而不是单纯 Surface 没绑定。

对应 Exo：

Exo 的 Surface 生命周期由 `PlayerView` 和 ExoPlayer 渲染器管理。MPV 必须自己维护 `SurfaceHolder.Callback`、`attachSurface`、`detachSurface`，但这次问题不能简单归咎于渲染层。

## 参考过的开源项目

- `mpv-android-anime4k`：header 补齐、`http-header-fields`、`http-allow-redirect`、`hls-bitrate=max` 等 MPV option 有参考价值。
- `mpvRex` / `mPlayer`：网络流自动补 referer/origin、MPV option 组织、本地代理思路有参考价值。
- `OmniPlay`：运行期 `setPropertyString` 和 libmpv 生命周期处理可参考。
- `William-Player`：主要是 Electron/Web MPV，Android 原生价值有限。

结论：这些项目能提供 MPV option 和 libmpv 使用经验，但不能直接解决本项目的 Media3 适配、HLS 输入清洗、以及播放器切换生命周期问题。

## 后续修改检查清单

以后每次动 MPV 播放链路，必须先对照 Exo 的成熟实现：

1. Header 是否和 Exo `OkHttpDataSource` 行为一致。
2. Playlist、key、segment 是否继承同一套请求头。
3. HLS/TS 异常输入是否有 extractor 等价处理，而不是只调 MPV 参数。
4. 切换媒体时旧请求是否被隔离或取消，不允许污染新媒体。
5. 失败后是否仍保持“只手动切换播放器”，不能自动 fallback Exo。
6. 每次代码修改后打一个 Git tag，便于回滚。
7. 测试至少覆盖：
   - 首次播放 HLS
   - 同视频切集
   - 切到另一个 HLS 视频
   - 返回退出再进入
   - MPV 错误时不会自动切 Exo

## 回滚建议

优先用 tag 回滚：

```bash
git switch feature/android-mpv-player
git reset --hard mpv-player-integration-20260707-075715
```

如果只是临时对比，不要 reset：

```bash
git switch --detach mpv-player-integration-20260707-075715
```

注意：tag 必须打在 commit 上。以后不要只在脏工作区打 tag，否则无法覆盖实际修改内容。
