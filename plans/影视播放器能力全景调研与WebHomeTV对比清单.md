# 影视播放器能力全景调研与 WebHomeTV 对比清单

日期：2026-07-01
分支：`research/player-capability-benchmark-20260701`

## 1. 调研结论摘要

这次调研不只看官方文档，也参考了 GitHub README、开源播放器说明、社区脚本生态、媒体中心文档和项目内已有实现。结论先放前面：

- 世界上“能力最强”的播放器不是单一 App，而是几类路线叠加：`mpv/libmpv` 的渲染/脚本/shader 生态、`VLC/libVLC` 的格式与协议覆盖、`Kodi/Plex/Jellyfin/Emby/Infuse` 的媒体库和家庭影院体系、`PotPlayer/MPC-HC+LAV+madVR+SVP` 的 Windows 硬核玩法、`Just Player/Next Player` 的 Android ExoPlayer 实践。
- WebHomeTV 当前已经具备普通影视 App 很少同时拥有的能力：站点/网盘聚合、Exo/IJK 双内核、FFmpeg 扩展解码、LUT 调色、弹幕、外挂字幕/音轨、DLNA、本机 HTTP 能力、播放记录远端同步/Webhook、CSP/JS 注入。
- WebHomeTV 与硬核播放器相比，明显缺口集中在：高级视频渲染链、音频处理链、AI 字幕/翻译/语音玩法、缩略图/章节/片段工具、媒体库刮削、跳片头/跳广告/跳片尾、插件脚本生态、播放器诊断面板。
- 最值得优先集成的不是“再堆格式名”，而是几类能直接提升观影体验的能力：画质参数面板、shader/LUT 扩展、虚拟环绕/音量均衡、双字幕/字幕搜索/字幕复读、缩略图进度条、跳片头片尾、播放器统计信息、下一集预加载、按站点/片源保存播放画像。
- AV3A、VVC/H.266、HDR10+/Dolby Vision、MPEG-H/IAMF/Atmos/DTS:X 这类能力要分清“标准支持”和“实际 Android 设备可播放”。很多能力依赖系统硬解、FFmpeg/libmpv 构建选项、商业授权或设备厂商实现，不能简单用配置开关承诺。

## 2. 调研对象分层

### 2.1 硬核本地播放器

| 播放器 | 平台 | 技术路线 | 核心强项 |
| --- | --- | --- | --- |
| mpv | Windows/macOS/Linux/Android 派生 | libmpv + FFmpeg + GPU 渲染 + 脚本 | shader、滤镜、脚本、字幕、yt-dlp、播放控制高度可编程 |
| VLC | 全平台 | libVLC + FFmpeg/自研模块 | 格式、协议、设备、流媒体、字幕、跨平台覆盖极强 |
| PotPlayer | Windows | DirectShow/内置解码/滤镜 | 画质参数、滤镜、3D、捕获、DXVA/CUDA/QSV、madVR/SVP 搭配 |
| MPC-HC/MPC-BE | Windows | DirectShow + LAV/madVR/SVP 生态 | 轻量、外部滤镜、madVR HDR/缩放、SVP 插帧 |
| IINA | macOS | mpv 前端 | mpv 能力 + macOS 原生体验 + 插件 |
| Just Player | Android/TV | Media3/ExoPlayer + FFmpeg audio | Android 电视 4K/HDR、隧道播放、帧率匹配、音频格式覆盖 |
| Next Player | Android | Media3/ExoPlayer + nextlib | 软解 H.264/HEVC、FFmpeg 音频、SAF、PIP、简洁移动端体验 |
| nPlayer/MX Player | Android/iOS | 私有播放器/系统解码/软解 | 移动端手势、网络协议、字幕和音轨体验 |

### 2.2 媒体中心和家庭影院

| 产品 | 平台 | 核心强项 |
| --- | --- | --- |
| Kodi | TV/桌面/盒子 | 插件、媒体库、皮肤、UPnP、PVR/IPTV、字幕、家庭影院设备适配 |
| Plex | Server + 全平台 Client | 服务端媒体库、远程串流、转码、DVR、广告检测、Skip Ads、家庭共享 |
| Jellyfin | Server + 全平台 Client | 开源媒体库、Direct Play/Direct Stream/Transcode、HDR 转 SDR、硬件转码 |
| Emby | Server + 全平台 Client | 媒体库、用户权限、转码、Live TV、远程播放 |
| Infuse | Apple 生态 | SMB/NFS/WebDAV/Plex/Jellyfin/Emby、TMDB 刮削、版本/花絮/杜比视界体验 |
| Nova Video Player | Android/TV | 本地媒体库、网络存储、TMDB/TVDB/Trakt、刮削和家庭影院浏览 |
| Stremio | 桌面/移动/TV | 插件聚合、发现、继续观看、Trakt、流媒体聚合 |

### 2.3 需要重点吸收的生态能力

- mpv 脚本生态：自动找字幕、字幕逐句复读、无字幕/静音段跳过、字幕转 Anki、Whisper 实时字幕、SponsorBlock、缩略图预览、yt-dlp 格式选择、IPTV+EPG、自动选轨、双音轨、片段裁剪。
- mpv shader 生态：Anime4K、FSR、SGSR、KrigBilateral、deband、denoise、sharpen、film grain、chroma upscaling、非线性拉伸、颜色/对比度/锐度/阴影/vibrance 调节。
- Windows 家庭影院生态：LAV Filters、madVR HDR tone mapping、显示刷新率匹配、SVP/MEMC 插帧、音频 bitstream、DirectShow 外挂滤镜。
- 媒体中心生态：刮削、合集、版本、花絮、片头片尾/广告识别、观看历史同步、多用户、离线下载、转码、PVR/IPTV、EPG。

## 3. 能力维度大清单

### 3.1 容器、协议和媒体源

- 本地文件：MP4、MOV、MKV、WebM、AVI、FLV、TS/M2TS、MPEG-PS、Ogg、ISO、BDMV、DVD。
- 自适应流：HLS、MPEG-DASH、SmoothStreaming、CMAF、低延迟 HLS/DASH。
- 实时流：RTSP、RTP/UDP、MMS、SRT、RIST、DVB、IPTV M3U/M3U8、XMLTV EPG。
- 网络文件：HTTP/HTTPS、FTP/SFTP、SMB/CIFS、NFS、WebDAV、UPnP/DLNA、Google Drive/OneDrive/Box/Dropbox 等。
- 云盘/网盘：夸克、阿里云盘、115、天翼、UC、WebDAV、自建目录索引。
- 种子/磁力：torrent/magnet 边下边播，需考虑版权和合规风险。
- 网页视频：yt-dlp/youtube-dl 解析，B 站/YouTube/直播平台格式选择。
- 推送播放：浏览器、手机、局域网设备向播放器推送 URL。

### 3.2 视频解码

- 常规：MPEG-1/2、MPEG-4 ASP、H.263、H.264/AVC、H.265/HEVC、VP8、VP9、AV1。
- 专业/老格式：Theora、Dirac/VC-2、VC-1/WMV、MJPEG、RealVideo、ProRes、DNxHD/DNxHR。
- 新标准：VVC/H.266、EVC、LCEVC、AV2。Android 生态目前不能默认承诺，需看硬件、系统和 FFmpeg/libdav1d/libvvc 等支持。
- 3D/沉浸式：MVC 3D、SBS/TAB、VR180/360。播放器需要画面布局识别、投影、字幕深度和交互控制。
- 硬解：MediaCodec、DXVA2/D3D11VA、VideoToolbox、VAAPI、VDPAU、NVDEC、QSV、AMF、V4L2/RKMPP 等。
- 软解：FFmpeg、dav1d、libgav1、OpenH264 等。移动端要限制分辨率和功耗预期。
- 解码策略：设备优先、应用优先、仅系统硬解、失败 fallback、codec 黑白名单、profile/level/performance point 诊断。

### 3.3 HDR、色彩和视频渲染

- HDR 格式：HDR10、HDR10+、HLG、Dolby Vision Profile 5/7/8、Technicolor HDR。
- 色彩管理：BT.709、BT.2020、Display P3、色域转换、ICC/ColorSync、全范围/限范围、YUV/RGB 矩阵。
- HDR to SDR：tone mapping、peak nits、target nits、gamma、desaturation、scene-adaptive mapping。
- SDR to HDR：伪 HDR、动态拉伸、峰值亮度映射，需谨慎，容易过曝。
- LUT：1D LUT、3D LUT、`.cube`、Hald CLUT、lookup PNG、强度混合、预览、收藏、导入。
- 画质参数：亮度、对比度、饱和度、色温、色调、gamma、锐度、阴影、高光、vibrance、肤色保护。
- Shader：GLSL/HLSL/libplacebo shader，Anime4K、FSR、SGSR、CAS、Krig、deband、denoise、film grain、chroma upscaling、anti-ringing。
- 缩放算法：bilinear、bicubic、Lanczos、Spline、ewa_lanczos、NNEDI3、RAVU、FSRCNNX。
- 去隔行：yadif、bwdif、motion adaptive deinterlace。
- 插帧/MEMC：SVP、RIFE、显示端运动补偿，移动端成本高。
- 显示适配：裁切、填充、拉伸、非线性拉伸、旋转、镜像、竖屏视频、自适应刷新率、24p/25p/50p/60p。
- 低延迟：低延迟解码、低延迟缓冲、直播追帧、音画同步策略。

### 3.4 音频解码和处理

- 常规：MP3、AAC、HE-AAC、xHE-AAC、Vorbis、Opus、FLAC、ALAC、PCM/WAV、APE、WMA、AMR。
- 家庭影院：AC-3、E-AC-3、DTS、DTS-HD MA/HRA、TrueHD、MLP、Atmos、DTS:X。
- 新/沉浸式：IAMF、MPEG-H 3D Audio、AC-4、AV3A。AV3A 属于需要单独确认解码库/授权/设备支持的方向，当前 Android/Exo/FFmpeg 默认支持不能直接假设。
- 直通：HDMI/SPDIF bitstream passthrough，AC3/EAC3/DTS/TrueHD/DTS-HD，需要设备和系统支持。
- 下混/上混：5.1/7.1 下混立体声，立体声上混 5.1，普通音频虚拟 3D/环绕。
- 空间音频：HRTF、耳机虚拟环绕、头部追踪、房间模拟。
- 音效：均衡器、响度均衡、夜间模式、动态范围压缩、音量增强、左右声道切换、声道重映射、人声增强。
- 同步：音频延迟、蓝牙延迟补偿、自动校准、按设备保存偏移。
- 变速：倍速不变调、pitch 调节、A-B 复读、字幕驱动复读。
- 多音轨：默认语言、强制默认、双音轨同时播放、双语左右声道分离。

### 3.5 字幕、弹幕和语言学习

- 字幕格式：SRT、ASS/SSA、VTT、TTML、SAMI、MicroDVD、SubViewer、VobSub、PGS、DVB、CEA-608/708。
- 字幕样式：字体、字号、描边、阴影、背景、位置、行距、编码、繁简转换。
- 外挂字幕：同名自动匹配、多语言匹配、手动选择、在线搜索下载、压缩包内字幕。
- 多字幕：双字幕、上/下字幕、双语字幕、第二字幕、强制字幕、听障字幕。
- 字幕同步：手动偏移、自动同步、按句跳转、当前句重播、上一句/下一句。
- AI 字幕：Whisper/本地 ASR、语音识别生成字幕、在线翻译、术语表、双语学习。
- 字幕互动：点击查词、导出 Anki、TTS 读字幕、字幕遮挡/听写模式。
- 弹幕：B 站/Niconico/AcFun 格式、滚动/顶部/底部/定位弹幕、密度、透明度、颜色、描边、弹幕屏蔽词、弹幕时间轴校准。

### 3.6 媒体库和刮削

- 刮削源：TMDB、TVDB、IMDb、Douban、Bangumi、AniDB、Trakt。
- 命名识别：电影年份、剧集 SxxExx、季/集、动漫长季、IMDB/TMDB ID、版本标签。
- 媒体组织：合集、季、特别篇、花絮、预告、删减片段、幕后、采访、多个版本/导演剪辑/IMAX/黑白版。
- 资产：海报、背景、Logo、剧照、NFO、主题音乐/视频。
- 观看状态：继续观看、看过/未看、播放进度、跨设备同步、Trakt scrobble。
- 多用户：家长控制、用户权限、资料库权限、观看历史隔离。
- 服务端能力：Direct Play、Direct Stream、实时转码、离线下载、带宽自适应、远程访问。

### 3.7 播放交互和特色玩法

- 手势：左右滑 seek、上下滑音量/亮度、双击快进/快退、捏合缩放。
- 遥控器：方向键 seek、长按加速、按键自定义、TV 焦点可视化。
- 播放控制：倍速、逐帧、章节、书签、A-B repeat、循环当前集、随机、播放队列。
- 片段工具：截图、GIF、录制、无损裁剪、批量裁剪、字幕截图、带字幕截图。
- 缩略图：进度条缩略图、storyboard、章节缩略图、播放前预览。
- 跳过：片头、片尾、前情提要、广告、静音段、无字幕段、SponsorBlock。
- 投屏：DLNA/UPnP、Chromecast、AirPlay、远程控制、网页控制台。
- 后台：音频后台播放、PIP、小窗、锁屏媒体控制、车机/蓝牙按键。
- 配置画像：按站点、按片源、按文件夹、按设备保存字幕/音轨/画质/内核/偏移。

### 3.8 诊断和开发者能力

- 叠加信息：分辨率、帧率、码率、解码器名、硬解/软解、掉帧、缓冲、音频格式、字幕格式。
- 事件日志：buffering、decoder init、fallback、track change、surface change、drm、网络错误。
- 可导出日志：普通用户过滤噪音，开发者模式保留详细链路。
- 媒体信息：MediaInfo/ffprobe 风格元数据。
- 设备能力：codec list、profile/level、HDR 能力、音频直通能力、刷新率列表。
- A/B 实验：不同内核、不同 render、不同硬解策略、不同缓存策略快速切换并记录结果。

## 4. 竞品能力枚举

### 4.1 mpv / libmpv

已确认能力：

- 基于 FFmpeg，支持非常广的容器、协议、音视频编码。
- GPU 视频输出，支持硬件解码和软解 fallback。
- 支持 Lua、JavaScript、C 插件、client API，适合做脚本和外部控制。
- 支持 GLSL shader、video filters、audio filters。
- 支持 HDR/tone mapping/libplacebo 方向的高级渲染配置。
- 支持 yt-dlp/youtube-dl hook、网络流格式选择、代理、HLS 变体选择。
- 支持播放列表、watch-later/resume、截图、OSD、章节、按键高度自定义。
- 支持多字幕、第二字幕、字幕延迟、字体/位置/语言自动选择。
- 用户脚本生态覆盖：字幕搜索、字幕逐句复读、自动暂停、跳静音段/无字幕段、字幕转 Anki、TTS、Whisper 运行时字幕、双音轨、缩略图预览、SponsorBlock、IPTV+EPG、裁剪、YouTube 队列/质量菜单。
- shader 生态覆盖：亮度/对比度/颜色/vibrance/阴影、Anime4K、锐化、降噪、超分、Krig 色度缩放、防振铃、film grain、FSR、SGSR、非线性拉伸、deband。

对 WebHomeTV 的启发：

- MPV 内核如果集成，价值不只在“多一个播放器”，而在 shader、脚本、字幕、截图、逐帧、A-B repeat、高级音频滤镜这套能力。
- 可先不完整接 MPV UI，但保留未来 `libmpv` 能力面板设计：shader 列表、mpv option 编辑、脚本目录、日志桥接。

### 4.2 VLC / libVLC

已确认能力：

- 播放文件、光盘、摄像头、设备和网络流。
- 多平台硬解和软件 fallback，支持 GPU zero-copy 路线。
- 视频格式覆盖 MPEG-1/2、DivX/Xvid、MPEG-4 ASP、H.261/H.263、H.264/AVC、Theora、Dirac/VC-2、MJPEG、WMV/VC-1、RealVideo 等。
- 音频覆盖 MP3、AAC、Vorbis、AC3、E-AC-3、TrueHD、DTS、WMA、FLAC、ALAC、Speex、APE、RealAudio、MIDI、LPCM、ADPCM 等。
- 字幕覆盖 DVD、MicroDVD、SubRip、SubViewer、SSA/ASS、SAMI、VPlayer、Closed Captions、VobSub、USF、DVB、OGM 等。
- 协议覆盖 UDP/RTP 单播/组播、HTTP/FTP、MMS、TCP/RTP、DVD/VCD/SVCD/CD、DVB 等。
- 支持滤镜、音视频处理、字幕同步、皮肤和扩展。

对 WebHomeTV 的启发：

- VLC 的强项是“遇到什么都尽量能播”。WebHomeTV 当前更偏“能找到资源并交给内核播”，对极老格式/光盘/广播协议不是核心需求。
- 可吸收的是媒体信息、协议兼容和作为兜底内核的思路，但直接引入 libVLC 会显著增加包体和维护成本。

### 4.3 PotPlayer / MPC-HC / MPC-BE + LAV + madVR + SVP

公开资料和社区生态常见能力：

- DirectShow 外部滤镜，可组合 LAV Splitter/LAV Video/LAV Audio。
- madVR 高质量渲染：HDR tone mapping、高级缩放、色彩管理、显示模式切换。
- SVP/MEMC 插帧，实现 24fps 到 60fps/120fps 的运动补偿。
- DXVA/CUDA/QuickSync/NVDEC 等硬解选项。
- 画面调节：亮度、对比度、饱和度、色相、锐化、降噪、deband、裁切、拉伸。
- 3D 视频和 3D 字幕：SBS/TAB、红蓝、输出模式转换。
- 音频处理：均衡器、规格化、混音、声道映射、SPDIF/HDMI bitstream。
- 截图、连续截图、录制、A-B repeat、逐帧、书签、章节。
- 外挂字幕、字幕搜索、字幕样式、字幕同步。

对 WebHomeTV 的启发：

- “高级画质参数面板”应单独做，不要只用 LUT 替代所有画质调节。
- TV/盒子上 MEMC/RIFE 级插帧成本高，但可以先做显示刷新率匹配、缩略图、A-B repeat、逐帧、截图。

### 4.4 Just Player / Next Player

已确认能力：

- Android Media3/ExoPlayer 路线，Android 6+ 和 Android TV。
- Just Player 使用 ExoPlayer FFmpeg audio extension，覆盖 AC3、EAC3、DTS、DTS-HD、TrueHD、IAMF、MPEG-H 等音频说明。
- 视频格式：H.263、H.264、H.265、MPEG-4 SP、VP8、VP9、AV1，依赖设备支持。
- 容器：MP4、MOV、WebM、MKV、Ogg、MPEG-TS、MPEG-PS、FLV、AVI。
- 流媒体：DASH、HLS、SmoothStreaming、RTSP。
- 字幕：SRT、SSA/ASS、TTML、VTT、DVB。
- HDR10+/Dolby Vision 在兼容硬件上播放。
- 音轨/字幕选择、倍速、滑动 seek/亮度/音量、捏合缩放、PIP、缩放/裁切、音量增强。
- Android TV 自动帧率匹配、tunneled playback、decoder priority、Dolby Vision profile 7 作为 HDR HEVC 播放。
- Next Player 支持软解 H.264/HEVC、外部字幕、URL 播放、SAF、PIP。

对 WebHomeTV 的启发：

- WebHomeTV 已经在 Exo 方向做了纯系统硬解/增强 Exo/FFmpeg 扩展/隧道约束等工作，可以继续对齐 Just Player 的“诊断可见、策略可切换”。
- Android 端最现实的硬核优化是：decoder priority、设备能力显示、帧率匹配、SurfaceView fixed size、音频直通能力检测。

### 4.5 Plex / Jellyfin / Emby / Infuse / Nova

已确认能力：

- Plex：个人媒体服务器、多平台 App、远程串流、媒体组织、设备/带宽优化、DVR、离线下载、家长控制、音频增强、免费 VOD/Live TV。DVR 录制可检测广告，播放器显示 Skip Ads，也可选择破坏性移除广告。
- Jellyfin：媒体命名/元数据、外挂字幕/音轨、多版本、3D 视频、多段视频、花絮、主题媒体。播放时有 Direct Play、Direct Stream、Transcode；硬件转码支持 Intel/AMD/Nvidia/Apple/Rockchip；支持 HDR to SDR tone mapping。
- Infuse：TMDB 刮削，电影/剧集/动漫/演唱会/短片/合集；支持电影年份、TMDB/IMDb ID、剧集命名；支持导演剪辑/加长版/IMAX/黑白版等版本标签；支持花絮、删减、采访、短片、预告等 extras；常见 Apple 生态高规格 HDR/Dolby 体验。
- Nova/Archos：Android/TV 本地媒体库，TMDB/TVDB/Trakt，网络存储，刮削和 scrobbling。

对 WebHomeTV 的启发：

- WebHomeTV 强在资源聚合，弱在本地媒体库。可以不做完整服务端，但可以吸收“文件命名识别、海报刮削、版本/花絮/合集、Trakt/观看状态”的能力。
- Skip Ads/Skip Intro/Skip Credits 可先做手动标记和本地记忆，再考虑指纹/服务端算法。

### 4.6 IINA

已确认能力：

- 基于 mpv，macOS 原生体验。
- 字幕、播放列表、章节、Force Touch、PIP、Touch Bar、自定义 UI、音乐模式。
- 视频缩略图、在线字幕搜索、智能本地字幕匹配、无限播放历史。
- 交互式视频/音频滤镜设置。
- 键盘、鼠标、触控板、手势完全自定义。
- 支持 mpv 配置文件和脚本系统、命令行和浏览器扩展。
- 插件清单包括 Online Media、OpenSubtitles、User Scripts、Anime4K、Bilingual Audio、Bookmarks、Clickable Subtitles、Danmaku、Jellyfin、Jump to Frame、Hold to Speed、Multiple Clips、Skip Intro、Trakt Scrobbler 等。

对 WebHomeTV 的启发：

- 插件系统不一定第一期做完整，但可以先把“播放器能力”抽成可扩展入口：字幕扩展、画质扩展、播放行为扩展、诊断扩展。
- WebHomeTV 已有站点注入/CSP，未来可以把播放页扩展也纳入统一脚本能力。

## 5. WebHomeTV 当前播放器能力

以下按当前代码和已有 plans 归纳，主要参考：

- `app/src/main/java/com/fongmi/android/tv/player/`
- `app/src/main/java/com/fongmi/android/tv/player/exo/`
- `app/src/main/java/com/fongmi/android/tv/player/engine/`
- `app/src/main/java/com/fongmi/android/tv/player/lut/`
- `app/src/main/java/com/fongmi/android/tv/playback/`
- `app/src/main/java/com/fongmi/android/tv/dlna/`
- `app/src/main/java/com/fongmi/android/tv/api/DanmakuApi.java`
- `plans/video-lut-color-grading-plan.md`
- `plans/安卓MPV播放器改造调研与实施方案.md`
- `plans/Exo播放器解码能力差异与缺失依赖分析.md`

### 5.1 已有内核与解码能力

- ExoPlayer/Media3 播放内核。
- IJK 播放内核，通过 `IjkSimplePlayer` 适配 Media3 `SimpleBasePlayer`。
- 播放时可切换 Exo/IJK。
- 支持硬解/软解切换。
- Exo 使用自定义 `FfmpegRenderersFactory`，可插入 FFmpeg audio renderer 和 FFmpeg video renderer。
- Exo 硬解增强模式下，视频 extension renderer 可关闭，保留更干净的系统硬解排查路径。
- Exo 增强模式启用更大的 LoadControl buffer、异步 MediaCodec 队列、late input drop 阈值实验。
- 支持 decoder fallback。
- TS extractor 开启 `FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS`，有利于 HDMV DTS 音频流识别。
- 支持音频 FFmpeg 优先、视频 FFmpeg 优先、AAC 优先、音频直通开关。
- 支持 tunnel，并已有 SurfaceView 约束方案。
- 支持 DRM 配置构建，包括 license uri/header、多 session、ClearKey 判断。

### 5.2 媒体源与播放源

- 支持普通 URL、带 header URL、M3U8/常见流。
- 支持 format retry：容器/manifest 解析失败时尝试切换 mime。
- 支持拼接媒体源：通过分隔符构建 `ConcatenatingMediaSource2`。
- Exo 数据源使用 OkHttp，按媒体项注入 headers。
- Exo cache 使用 `SimpleCache` + LRU，按预加载设置和可用存储限制容量。
- 有 `PreCache` 预缓存入口。
- 具备网盘/云盘资源播放能力，且近期处理过夸克等场景。
- 具备本机 HTTP 服务/本地代理相关能力。
- 具备站点注入/CSP/JS 扩展和 QuickJS/drpy 解析能力。

### 5.3 字幕、音轨和弹幕

- Exo MediaItem 构建时支持外挂字幕配置。
- TrackDialog 支持音轨和字幕轨选择。
- TrackDialog 文件选择支持 `application/x-subrip`、SSA、VTT、TTML、`audio/*`、`text/*`、`application/octet-stream`，说明已可手动选择外挂字幕/音频类文件。
- 支持关闭字幕轨。
- 支持保存 track selection。
- 支持重置 track。
- 支持文本字幕偏移 `setTextOffsetMs()`。
- 支持音频偏移 `setAudioOffsetMs()`。
- 支持系统字幕样式或应用默认描边字幕样式。
- 支持字幕位置和字幕字号。
- 支持弹幕加载、自动搜索、站点弹幕优先策略。
- 弹幕配置覆盖字号、透明度、粗体、阴影/描边/投影、颜色模式、滚动时间、固定时间、时间偏移、最大数量、滚动区域、行数、行距、类型开关、反向滚动等。
- 支持发送本地弹幕文本。

### 5.4 LUT 和画面能力

- 已实现 Media3 `setVideoEffects()` 路线的 LUT。
- 支持内置 `.cube` LUT 预设，当前 assets 下已有多套中文命名预设。
- 支持用户 LUT 目录扫描。
- 支持 LUT 开关、预设选择、强度、预览时间、收藏、导入/目录。
- 支持播放中动态应用 LUT，含 warmup、失败回退、preview。
- 已明确 LUT 限制：只适合 Exo、SDR、非 DRM、非 tunnel、非软解/扩展视频优先、非 HDR/DV、有视频 track。
- LUT 实现包含 `.cube` 解析、动态强度混合、pipeline warmup、eligibility 判断。
- 目前画质能力主要集中在 LUT，尚未形成通用亮度/对比度/饱和度/锐度/gamma/deband/denoise 面板。

### 5.5 播放控制和状态

- 倍速预设：0.5x、0.75x、1x、1.2x、1.25x、1.5x、1.75x、2x、2.5x、3x、5x。
- 支持设置倍速和一键循环切换倍速。
- 支持 repeat one。
- 支持片头/片尾标记能力入口：`canSetOpening()`、`canSetEnding()`。
- 支持播放超时、Exo fallback、实时 fallback、LUT warmup retry 等错误恢复。
- 支持播放元数据更新。
- 支持画面宽高/横竖屏判断。
- 支持播放器按钮自定义显示和排序。
- UI 字符串中已有播放页信息项：分辨率、时间、进度、网速、音轨、字幕等。

### 5.6 投屏、远程和同步

- 有 DLNA 相关实现：`DLNAServiceConfiguration`、`OkHttpStreamClient`、`SocketHttpStreamServer`。
- 有远程操作/推送播放相关 UI 字符串和能力。
- 播放记录支持本地保存。
- 播放记录支持远端同步配置、启动同步、定时同步、最大条数、token。
- 播放记录支持 Webhook 上报，字段预设包含基础/标准/完整/匿名/自定义。
- Webhook 可上报播放状态、位置、时长、进度、倍速等。
- 有隐私提示和字段策略，可做安全裁剪。

### 5.7 目前缺口

- 缺少完整 MPV/libmpv 内核。
- 缺少 VLC/libVLC 兜底。
- 缺少高级 shader/filter 面板。
- 缺少通用画质参数面板。
- 缺少音频 EQ、响度均衡、夜间模式、虚拟环绕、普通音频转 3D。
- 缺少更完整的音频直通能力检测和设备能力展示。
- 缺少双字幕/第二字幕/双语字幕。
- 缺少在线字幕搜索下载、自动字幕匹配。
- 缺少 AI 字幕生成/翻译/字幕学习工具。
- 缺少进度条缩略图/storyboard。
- 缺少截图/GIF/片段裁剪/逐帧。
- 缺少 SponsorBlock、自动跳片头、跳片尾、跳广告。
- 缺少媒体库刮削、合集、版本、花絮、主题媒体。
- 缺少播放诊断面板和设备 codec/HDR/音频能力页。
- 缺少按站点/片源保存播放器画像。

## 6. WebHomeTV 对比矩阵

| 能力 | WebHomeTV 当前 | 竞品成熟形态 | 差距 | 集成难度 | 建议 |
| --- | --- | --- | --- | --- | --- |
| Exo 硬解/软解 | 已有，且有 FFmpeg 扩展和增强模式 | Just/Next 可切 decoder priority、tunnel、帧率匹配 | 接近，但诊断和设备能力展示不足 | 中 | P0 |
| IJK 兜底 | 已有 | 多内核播放器常见 | 可用，但高级能力不多 | 低 | 保留 |
| MPV 内核 | 已有调研，未完整集成 | mpv/IINA shader+脚本极强 | 明显缺失 | 高 | P1/P2 分阶段 |
| VLC 内核 | 未集成 | VLC 格式/协议极强 | 缺失 | 高 | P3 评估 |
| LUT | 已有 `.cube`、预设、强度、预览 | mpv/madVR 可 shader+HDR tone mapping | SDR LUT 已较强，HDR/通用画质不足 | 中 | P0 扩展 |
| 画质参数 | 缺少系统面板 | PotPlayer/mpv 常见 | 缺失 | 中 | P0 |
| Shader | 缺少通用 shader | mpv/IINA 插件生态成熟 | 缺失 | 高 | P1/P2 |
| HDR tone mapping | 依赖系统/Exo，LUT HDR 禁用 | madVR/libplacebo/Jellyfin 转码成熟 | 缺失 | 高 | P2 |
| 帧率匹配 | 未见完整能力 | Just/Kodi/Nova 常见 | 缺失 | 中 | P1 |
| 音频格式 | FFmpeg audio 已接入，DTS TS flag | Just/VLC/mpv 覆盖广 | 接近，但设备能力和直通诊断不足 | 中 | P0 |
| 虚拟环绕/3D 音频 | 未见 | PotPlayer、系统音效、第三方 EQ | 缺失 | 中高 | P1 |
| EQ/响度均衡 | 未见 | VLC/PotPlayer/系统 EQ | 缺失 | 中 | P1 |
| AV3A | 未见 | 行业趋势，实际 App 支持不明 | 缺失且需验证库 | 高 | P3 调研 |
| 字幕轨/外挂字幕 | 已有 | 主流播放器标配 | 基础可用 | 低 | 继续优化 |
| 双字幕 | 未见 | mpv/IINA/学习插件常见 | 缺失 | 中 | P1 |
| 在线字幕 | 未见完整 | IINA/OpenSubtitles/mpv scripts | 缺失 | 中 | P1 |
| AI 字幕/翻译 | 未见 | mpv Whisper scripts、学习插件 | 缺失 | 高 | P2 |
| 弹幕 | 已有且配置丰富 | IINA 插件/B 站类 App | WebHomeTV 有优势 | 中 | 继续打磨 |
| 缩略图预览 | 未见 | IINA/mpv scripts/YouTube 常见 | 缺失 | 中高 | P1 |
| 截图/裁剪 | 未见完整 | mpv/PotPlayer/IINA | 缺失 | 中 | P2 |
| 跳片头/片尾 | 有标记入口，自动化不足 | Plex Skip Ads/IINA Skip Intro | 部分已有 | 中 | P1 |
| SponsorBlock | 未见 | mpv scripts/浏览器生态 | 缺失 | 中 | P2 |
| 媒体库刮削 | 影视源聚合强，本地媒体库弱 | Plex/Jellyfin/Infuse/Nova | 缺失 | 高 | P2 |
| 远端同步/Webhook | 已有 | Plex/Jellyfin/Trakt | WebHomeTV 有特色 | 中 | 继续完善 |
| DLNA/投屏 | 已有 | VLC/Kodi/系统投屏 | 基础可用 | 中 | 优化稳定性 |
| 插件/脚本 | 站点注入强，播放页扩展弱 | mpv/IINA/Kodi/Stremio | 部分已有 | 高 | P2 |
| 诊断面板 | 日志多，UI 诊断不足 | mpv stats/Exo EventLogger/MediaInfo | 缺失 | 中 | P0 |

## 7. 建议集成优先级

### 7.1 P0：近期最值得做

1. 播放器诊断面板
   显示内核、解码器名、硬解/软解、分辨率、帧率、HDR、音频格式、声道、字幕格式、掉帧、缓冲、网速、render、tunnel、cache 命中。这个能直接服务后续所有播放 Bug。

2. 通用画质参数面板
   在现有 LUT 之外增加亮度、对比度、饱和度、gamma、锐度、色温、色调。第一期只对 Exo + SDR + effects 可用链路开放，和 LUT eligibility 共用限制提示。

3. 音频能力诊断和直通提示
   展示当前音频 mime、声道、采样率、decoder、是否 passthrough、设备是否支持 AC3/EAC3/DTS/TrueHD。不要先承诺 Atmos/DTS:X，只做能力可见。

4. Exo 策略显式化
   把“仅系统硬解 / 设备优先 / 应用优先 / 软解优先 / 增强 Exo”做成用户可理解的选项，并在诊断面板中显示实际 renderer。

5. 缩短问题定位链路
   调试日志默认过滤高频请求日志已经做过，下一步应让播放页一键复制“播放诊断摘要”，用户发截图/文字即可定位。

### 7.2 P1：体验提升明显

1. 双字幕/第二字幕
   支持上方第二字幕或双语字幕，优先支持 SRT/VTT/ASS 文本字幕。可以先只做 Exo UI overlay，不强行改底层字幕 renderer。

2. 在线字幕搜索和本地自动匹配
   参考 IINA/OpenSubtitles/mpv scripts。国内可支持射手/伪射手类源、资源站字幕接口、用户配置 API。

3. 进度条缩略图
   优先对网盘/站点支持已有图片或 storyboard 的情况直接展示；本地生成缩略图要注意性能和缓存。

4. 跳片头/跳片尾
   先做手动标记 + 按剧/站点记忆 + 自动跳过开关。后续再做指纹或众包。

5. 基础音频增强
   音量增强、响度均衡、夜间模式、左右声道/单声道、音频延迟按设备记忆。虚拟环绕可作为独立实验项。

6. 自适应刷新率/帧率匹配
   TV 端先做能力检测和手动开关，避免全局自动切换造成黑屏或 HDMI 握手问题。

### 7.3 P2：硬核玩法和生态

1. MPV 内核 MVP
   只先支持 URL 播放、pause/seek/speed、Surface、基础轨道、基础字幕、日志桥接。不要第一期就追完整 Media3 等价。

2. MPV shader/script 能力
   在 MPV MVP 稳定后，开放 shader 目录、脚本目录、常用 shader 预设。优先 Anime4K、FSR/CAS、deband、sharpen。

3. AI 字幕和翻译
   支持外部服务或本地模型配置：音频提取、ASR、翻译、双语字幕。移动端本地模型要做性能/发热提示。

4. 截图、GIF、片段裁剪
   可借助 FFmpeg 或播放器截图。先做截图和带字幕截图，再做裁剪。

5. SponsorBlock/静音段/无字幕段跳过
   对资源站视频可能价值有限，但对网页/YouTube/课程类内容有价值。

6. 播放画像
   按站点/资源类型保存内核、解码策略、字幕偏移、音频偏移、画质参数、LUT、弹幕设置。

### 7.4 P3：长期或高成本探索

1. AV3A 解码
   作为中国自主沉浸式音频方向值得调研，但当前不能假设 Android/Exo/FFmpeg 默认可播。需要确认开源 decoder、授权、样片、设备输出链路。

2. VVC/H.266/AV2
   软件解码成本高，硬解设备少。可以先做媒体信息识别和错误提示，不急着承诺播放。

3. HDR/Dolby Vision 高级处理
   HDR LUT、DV Profile 转换、动态 metadata tone mapping 都是重工程。短期更现实的是诊断、正确禁用、提示和设备能力展示。

4. 服务端转码
   Jellyfin/Plex 那类能力很强，但需要服务端。WebHomeTV 可以先支持对接，而不是内置完整转码服务器。

## 8. 可直接转成需求的功能清单

### 8.1 播放诊断页

- 当前内核：Exo/IJK/未来 MPV。
- 当前解码：硬解/软解/系统优先/扩展优先。
- 视频：mime、codec、decoderName、profile、level、width、height、fps、bitrate、HDR、color space、droppedFrames。
- 音频：mime、decoderName、sampleRate、channels、bitrate、passthrough、audioSessionId。
- 字幕：轨道数、当前字幕格式、外挂/内嵌、语言。
- 输出：SurfaceView/TextureView、tunnel、刷新率、显示模式。
- 网络：当前速度、buffered、cache 状态、headers 数量、重试次数。
- 错误：最近 10 条 player error 和 decoder init 日志。

### 8.2 画质面板

- 原色 / LUT / 参数 / 预设 四个页签。
- 参数：亮度、对比度、饱和度、gamma、锐度、色温、色调。
- 预设：电影、动漫、夜间、提亮、柔和、鲜艳、黑白、护眼。
- 支持“仅本片生效 / 全局生效 / 当前站点生效”。
- 对 HDR/DV/DRM/tunnel/软解不可用时显示原因。

### 8.3 音频增强面板

- 音量增强。
- 响度均衡。
- 夜间模式/动态压缩。
- 低音增强。
- 人声增强。
- 左右声道/单声道。
- 虚拟环绕/耳机 3D 音频实验项。
- 音频延迟按设备记忆。

### 8.4 字幕增强

- 第二字幕。
- 字幕在线搜索。
- 同名字幕自动匹配。
- 字幕繁简转换。
- 字幕自动偏移保存。
- 当前句重播、上一句/下一句。
- 字幕导出/复制。
- AI 翻译/双语字幕实验项。

### 8.5 缩略图和章节

- 进度条 hover/遥控移动时显示缩略图。
- 支持站点/网盘返回 storyboard。
- 支持本地生成并缓存。
- 支持章节列表。
- 支持片头/片尾/广告段标记。

### 8.6 跳过系统

- 手动设置片头/片尾。
- 自动跳片头/片尾开关。
- 每剧记忆、每站点记忆。
- 支持跳过前情提要。
- 支持跳过静音段实验项。
- 后续接入 SponsorBlock 类众包接口。

### 8.7 MPV 方向

- libmpv 初始化和日志桥接。
- URL/file/content 播放。
- Surface 生命周期。
- pause/seek/speed/repeat。
- 音轨/字幕/章节读取。
- 外挂字幕。
- shader 目录和预设。
- mpv option 配置。
- stats OSD。
- 截图、逐帧、A-B repeat。

## 9. 风险和边界

- 不要把“竞品支持”直接等同于“Android WebHomeTV 能无成本支持”。很多能力依赖桌面 GPU、DirectShow、商业授权、系统硬解或服务端。
- 不要为少数格式牺牲主路径稳定性。WebHomeTV 的主场景是在线资源和网盘播放，P0 应优先服务常见 H.264/H.265/AV1/HLS/MKV/MP4。
- 不要默认开启高风险渲染链。LUT、shader、HDR tone mapping、虚拟环绕、异步 codec、tunnel 都应可关闭、可诊断、可回退。
- 不要混淆“解码”和“输出”。Atmos/DTS:X/AV3A 即使能解码，也未必能按沉浸式格式输出到设备。
- 不要把媒体库服务端一次性塞进 App。Plex/Jellyfin 的强项在服务端，WebHomeTV 更适合先做对接和轻量刮削。

## 10. 参考来源

- VLC features：`https://www.videolan.org/vlc/features.html`
- mpv manual：`https://mpv.io/manual/master/`
- mpv User Scripts：`https://github.com/mpv-player/mpv/wiki/User-Scripts`
- mpv-android README：`https://github.com/mpv-android/mpv-android`
- IINA README/插件列表：`https://github.com/iina/iina`
- Just Player README：`https://github.com/moneytoo/Player`
- Next Player README：`https://github.com/anilbeesetti/nextplayer`
- ExoPlayer FFmpeg extension README：`https://github.com/google/ExoPlayer/tree/release-v2/extensions/ffmpeg`
- Android Media3 supported formats：`https://developer.android.com/media/media3/exoplayer/supported-formats`
- MPC-HC official site：`https://mpc-hc.org/`
- Plex what is Plex：`https://support.plex.tv/articles/200288286-what-is-plex/`
- Plex commercial removal：`https://support.plex.tv/articles/115003944134-removing-commercials/`
- Jellyfin movie/media organization：`https://jellyfin.org/docs/general/server/media/movies/`
- Jellyfin transcoding：`https://jellyfin.org/docs/general/post-install/transcoding/`
- Infuse metadata/naming：`https://support.firecore.com/hc/en-us/articles/215090947-Metadata-101`
- Nova/Archos AVP README：`https://github.com/nova-video-player/aos-AVP`
- Stremio feature repository：`https://github.com/Stremio/stremio-features`
