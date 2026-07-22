package com.fongmi.android.tv.setting;

import java.util.ArrayList;
import java.util.List;

public final class PlaybackPerformanceCatalog {

    public static final String PROFILE = "profile";
    public static final String RENDER = "render";
    public static final String TRACK_LIMIT = "track_limit";
    public static final String ADAPTIVE_DOWNGRADE = "adaptive_downgrade";
    public static final String BANDWIDTH_METER = "bandwidth_meter";
    public static final String TUNNEL = "tunnel";
    public static final String BUFFER_TIME = "buffer_time";
    public static final String BUFFER_BYTES = "buffer_bytes";
    public static final String BACK_BUFFER = "back_buffer";
    public static final String PLAY_CACHE = "play_cache";
    public static final String LOAD_SELECTED_TRACKS = "load_selected_tracks";
    public static final String PRELOAD = "preload";
    public static final String PRELOAD_THREADS = "preload_threads";
    public static final String PRELOAD_SIZE = "preload_size";
    public static final String PRELOAD_TIME = "preload_time";
    public static final String CODEC_ASYNC = "codec_async";
    public static final String DYNAMIC_SCHEDULING = "dynamic_scheduling";
    public static final String DURATION_PROGRESS = "duration_progress";
    public static final String LATE_DROP = "late_drop";
    public static final String SURFACE_FIXED_SIZE = "surface_fixed_size";
    public static final String DECODER_FALLBACK = "decoder_fallback";
    public static final String SOFT_VIDEO_TUNE = "soft_video_tune";
    public static final String AUDIO_PASSTHROUGH = "audio_passthrough";
    public static final String PREFER_AAC = "prefer_aac";
    public static final String AUDIO_SOFT_PREFER = "audio_soft_prefer";
    public static final String VIDEO_SOFT_PREFER = "video_soft_prefer";
    public static final String MPV_OUTPUT = "mpv_output";
    public static final String MPV_RENDER = "mpv_render";
    public static final String MPV_HWDEC = "mpv_hwdec";
    public static final String MPV_SYNC = "mpv_sync";
    public static final String MPV_FRAME_DROP = "mpv_frame_drop";
    public static final String MPV_INTERPOLATION = "mpv_interpolation";
    public static final String MPV_SOFT_TUNE = "mpv_soft_tune";
    public static final String MPV_VERBOSE_LOG = "mpv_verbose_log";
    public static final String MPV_FRAME_RATE = "mpv_frame_rate";
    public static final String MPV_HLS_BITRATE = "mpv_hls_bitrate";
    public static final String MPV_REBUFFER = "mpv_rebuffer";
    public static final String MPV_OPTION_PRIORITY = "mpv_option_priority";
    public static final String IJK_SCENE = "ijk_scene";
    public static final String IJK_BUFFER = "ijk_buffer";
    public static final String IJK_PACKET_BUFFERING = "ijk_packet_buffering";
    public static final String IJK_WATER = "ijk_water";
    public static final String IJK_PICTURE_QUEUE = "ijk_picture_queue";
    public static final String IJK_FRAME_DROP = "ijk_frame_drop";
    public static final String IJK_ACCURATE_SEEK = "ijk_accurate_seek";
    public static final String IJK_PROBE = "ijk_probe";
    public static final String IJK_SOFT_TUNE = "ijk_soft_tune";
    public static final String IJK_RTSP_TRANSPORT = "ijk_rtsp_transport";
    public static final String IJK_RECONNECT = "ijk_reconnect";
    public static final String EXO_FRAME_RATE = "exo_frame_rate";
    public static final String EXO_START_BUFFER = "exo_start_buffer";
    public static final String EXO_REBUFFER = "exo_rebuffer";
    public static final String EXO_PRIORITIZE_TIME = "exo_prioritize_time";

    private static final String BASIC = "基础性能";
    private static final String BUFFER = "缓冲与缓存";
    private static final String PRELOAD_SECTION = "预载";
    private static final String DECODE = "解码与渲染";
    private static final String AUDIO = "音频";

    private PlaybackPerformanceCatalog() {
    }

    public static List<PlaybackPerformanceOption> forKernel(int kernel) {
        List<PlaybackPerformanceOption> options = new ArrayList<>();
        options.add(option(PROFILE, BASIC, "性能配置", profileDescription(kernel)));
        if (kernel == PlayerSetting.EXO) addExo(options);
        else if (kernel == PlayerSetting.MPV) addMpv(options);
        else addIjk(options);
        return options;
    }

    private static void addExo(List<PlaybackPerformanceOption> options) {
        options.add(option(RENDER, BASIC, "渲染方式", "作用：决定视频输出控件。选择 SurfaceView（默认）通常最省 GPU、最适合电视和4K；只有需要动画、旋转或自由变换时才选 TextureView。代价：TextureView 会增加一次 GPU 合成，低性能电视更容易掉帧。"));
        options.add(option(TRACK_LIMIT, BASIC, "视频轨道限制", "作用：阻止 EXO 选择超过屏幕/硬解能力的轨道。想要“少卡顿”请选择开启（默认）；关闭只适合确认设备能稳定解码最高画质的情况。代价：开启可能主动放弃过高分辨率，换取播放成功率。"));
        options.add(option(ADAPTIVE_DOWNGRADE, BASIC, "自适应降级", "作用：重缓冲、连续掉帧或带宽不足时自动降到更容易播放的轨道。弱网、4K大文件建议开启（默认）；追求始终最高画质可关闭。代价：降级后本次播放不会自动升回，画质可能降低。"));
        options.add(option(BANDWIDTH_METER, BASIC, "带宽估算", "作用：用实际下载速度帮助 EXO 选轨和判断是否降级。网络忽快忽慢时建议开启（默认），可减少反复切换和卡顿；固定高速内网可关闭。代价：估算偏保守时可能提前选择低画质，它不会额外测速。"));
        options.add(option(TUNNEL, BASIC, "隧道模式", "作用：尝试让音视频走硬件直通，降低 CPU 并改善同步。电视硬解且只追求播放流畅时可尝试开启；出现黑屏、无声、字幕/LUT失效立即关闭。代价：依赖设备和 SurfaceView，兼容性不如普通路径。"));
        options.add(option(EXO_FRAME_RATE, BASIC, "帧率匹配", "作用：请求显示刷新率贴合视频帧率。电视播放电影/25或24fps内容建议保持“仅无缝”（默认），可减少抖动；遇到切换黑屏或刷新率异常选关闭。代价：仅无缝不会强行切换显示模式，效果取决于电视系统。"));
        addSharedBuffer(options, true, false);
        options.add(option(EXO_START_BUFFER, BUFFER, "起播阈值", "作用：开始播放前至少准备多少秒。1.5秒（均衡/默认）适合大多数网络；弱网或4K卡顿可调到2～3秒，追求秒开可用0.5～1秒。代价：阈值越高首帧越慢。"));
        options.add(option(EXO_REBUFFER, BUFFER, "重缓冲恢复", "作用：卡住后积累多少缓冲才恢复。自动档会在2～8秒间根据上一轮表现调整；手动建议均衡3秒、兼容5秒、轻量2秒。代价：数值越高越不易再次卡，但等待更久。"));
        options.add(option(EXO_PRIORITIZE_TIME, BUFFER, "时间优先", "作用：优先满足“缓冲秒数”，不因目标字节容量已达到就停止加载。网络波动或长视频建议开启；内存紧张设备保持关闭。代价：可能超过目标容量并暂时占用更多内存，不能突破系统可用内存。"));
        options.add(option(LOAD_SELECTED_TRACKS, BUFFER, "只加载选中轨道", "作用：只请求当前音视频轨道，减少带宽和内存。网速/内存紧张建议开启；经常切换清晰度、音轨时可关闭以减少重新请求。代价：切换轨道可能需要重新缓冲。"));
        addPreload(options);
        options.add(option(CODEC_ASYNC, DECODE, "MediaCodec 队列", "作用：决定解码输出由异步还是同步队列驱动。保持自动（默认）通常吞吐最高；只有旧设备异步回调异常时才改同步。代价：同步可能更稳，但会增加等待和 CPU 调度压力。"));
        options.add(option(DYNAMIC_SCHEDULING, DECODE, "Media3 动态调度", "作用：按渲染器可工作时间调度播放循环。保持开启（默认）通常更省 CPU、掉帧更少；遇到特定机型时序异常再关闭。代价：关闭后可能增加无效唤醒。"));
        options.add(option(DURATION_PROGRESS, DECODE, "解码耗时推进", "作用：把异步解码耗时反馈给播放器，减少无效等待。异步队列下建议开启（默认）；同步队列不生效。代价很小，关闭只用于排查时序问题。"));
        options.add(option(LATE_DROP, DECODE, "输入丢帧阈值", "作用：输入帧明显迟到时提前丢弃，优先保证“跟上进度”。CPU不足、4K掉帧时建议开启；希望保留每一帧可关闭。代价：画面可能跳帧，但通常比持续延迟更容易接受。"));
        options.add(option(SURFACE_FIXED_SIZE, DECODE, "Surface 固定尺寸", "作用：按视频尺寸创建 Surface，减少超高分辨率合成压力。电视4K建议开启（默认）；切清晰度/旋转出现画面尺寸异常时关闭。代价：少数设备切换分辨率需要重建 Surface。"));
        options.add(option(DECODER_FALLBACK, DECODE, "解码器兜底", "作用：首选硬解初始化失败时尝试其他解码器。兼容性优先建议开启（默认）；只想快速暴露硬件问题可关闭。代价：可能多等待一次初始化，且备用解码器性能可能较低。"));
        options.add(option(SOFT_VIDEO_TUNE, DECODE, "软解降负载", "作用：仅在 EXO 使用 FFmpeg 软解时降低滤波和解码负载。低性能设备/软解视频可开启；硬解4K基本不受影响。代价：积极降负载会牺牲细节，不能替代硬解。"));
        options.add(option(AUDIO_PASSTHROUGH, AUDIO, "音频直通", "作用：把 Dolby/DTS 等压缩音频交给电视或功放解码，保留多声道。设备明确支持且要环绕声才开启；出现无声立即关闭。代价：输出链不支持时不会自动变成可播放音频。"));
        options.add(option(PREFER_AAC, AUDIO, "AAC 优先", "作用：有多条音轨时优先选兼容性更高的 AAC。电视无声、切换音轨失败时建议开启；追求原始多声道/高码率时关闭。代价：可能放弃质量更高的音轨。"));
        options.add(option(AUDIO_SOFT_PREFER, AUDIO, "音频软解优先", "作用：优先用 FFmpeg 解码冷门音频格式。硬解无声或格式不支持时开启；普通设备保持关闭。代价：增加 CPU、功耗，通常不影响视频画面流畅度。"));
        options.add(option(VIDEO_SOFT_PREFER, AUDIO, "视频软解优先", "作用：绕过异常硬件解码器，改用 FFmpeg。仅在硬解花屏/崩溃且分辨率较低时尝试；4K电视不要开启。代价：CPU、发热和掉帧风险显著增加。"));
    }

    private static void addMpv(List<PlaybackPerformanceOption> options) {
        options.add(option(MPV_OUTPUT, BASIC, "输出模式", "怎么选：保持“自动”（默认）最省心；电视播放4K且不需要MPV字幕/LUT/shader/滤镜时会自动用“电视直出”，这是当前最低GPU开销、最优先保证流畅的路径。需要MPV完整图像处理选“GPU完整”；自动判断不正确时可手动选“电视直出”。代价：电视直出不经过OpenGL/Vulkan，MPV原生字幕和GPU滤镜不可用。"));
        options.add(option(MPV_RENDER, BASIC, "渲染后端", "怎么选：GPU完整模式先选 OpenGL，兼容性最好；确认设备 Vulkan 驱动稳定且需要 gpu-next/libplacebo 时再选 Vulkan。电视直出时本参数不参与视频输出，切换也不会变快。代价：Vulkan可能更高效，也可能因驱动问题卡顿、黑屏并自动回退OpenGL。"));
        options.add(option(MPV_HWDEC, BASIC, "硬解路径", "怎么选：保持“自动回退”（默认）；它先试 mediacodec 零拷贝，失败再试兼容复制。电视4K追求最高流畅可选“零拷贝优先”；只有零拷贝黑屏、崩溃或解码异常时选“兼容复制”。代价：兼容复制会复制每帧，4K 10bit内存带宽开销大，可能明显卡顿。"));
        options.add(option(MPV_FRAME_RATE, BASIC, "帧率匹配", "怎么选：电影、剧集在电视上保持“仅无缝”（默认），可减少24/25fps抖动；切换后黑屏、闪屏或电视刷新率异常时关闭。代价：仅无缝不会强制切换不兼容模式，旧Android自动忽略。"));
        options.add(option(MPV_OPTION_PRIORITY, BASIC, "参数优先级", "怎么选：普通用户选“播放性能优先”（默认），界面中的缓存、硬解、同步、丢帧和HLS设置才能可靠生效；只有明确维护了mpv.conf并希望同名配置覆盖界面时选“mpv.conf优先”。选错会出现“界面改了但实际被配置文件覆盖”。"));
        addSharedBuffer(options, false, true);
        options.add(option(MPV_REBUFFER, BUFFER, "重缓冲恢复", "作用：缓存耗尽后至少重新准备多少秒再继续。均衡建议2秒；网络反复卡顿可升到3～5秒；稳定高速网络可用1秒。代价：越高越不易刚恢复又卡住，但每次恢复等待越久。"));
        options.add(option(MPV_HLS_BITRATE, BUFFER, "HLS码率首选", "怎么选：网络和设备足够时选“最高码率”（默认）；4K HLS卡顿先降到15Mbps，再降到8Mbps；只求能播选最低。代价：这是选初始轨道，不是动态ABR；限制越低画质越低，清单码率标错时判断也会失准。"));
        addPreload(options);
        options.add(option(MPV_SYNC, DECODE, "同步模式", "怎么选：保持“音频同步”（默认），兼容性最好。只有屏幕刷新率与视频不匹配、能感到规律性微抖且未开启音频直通时，才试“显示重采样”。代价：显示重采样会轻微调整音频速度并增加处理，直通音频不适用。"));
        options.add(option(MPV_FRAME_DROP, DECODE, "丢帧策略", "怎么选：保持“输出丢帧”（默认），跟不上时优先丢渲染帧以维持音画进度；卡顿仍严重可试“解码丢帧”；不要为追求完整画面关闭丢帧，除非设备性能充足。代价：策略越积极，跳帧越明显。"));
        options.add(option(MPV_INTERPOLATION, DECODE, "平滑运动", "怎么选：默认关闭。只有GPU余量充足、使用GPU完整＋显示重采样且想改善低帧率运动时才开启；电视4K、HDR、LUT或已经卡顿时必须关闭。代价：会明显增加GPU负载，电视直出时不生效。"));
        options.add(option(MPV_SOFT_TUNE, DECODE, "软解降负载", "作用：仅软件解码时减少滤波和解码工作。默认“温和”；软解仍掉帧可选“积极”；硬解视频无需靠它提速。代价：模式越积极，细节和画面连续性损失越大。"));
        options.add(option(MPV_VERBOSE_LOG, DECODE, "详细日志", "怎么选：正常播放保持“正常”（默认）；只在排查崩溃、解码或缓冲问题时临时打开详细日志。代价：增加JNI、字符串处理和日志I/O，可能干扰低性能设备的流畅度。"));
        options.add(option(AUDIO_PASSTHROUGH, AUDIO, "音频直通", "怎么选：电视/功放明确支持Dolby、DTS且需要多声道时开启；出现无声、杂音或同步异常立即关闭。代价：压缩音频交给外部设备后，MPV无法完成所有混音和重采样处理。"));
        options.add(option(PREFER_AAC, AUDIO, "AAC 优先", "怎么选：高级音轨无声或设备兼容性差时开启；功放支持原始多声道、希望保留最佳音轨时关闭。代价：可能从Dolby/DTS切到质量或声道较低的AAC。"));
    }

    private static void addIjk(List<PlaybackPerformanceOption> options) {
        options.add(option(IJK_SCENE, BASIC, "场景模式", "怎么选：不确定就选“自动”（默认）；普通影视选“点播”；直播经常缓冲选“直播稳定”；只有网络很好且必须追求低延迟时选“直播低延迟”。代价：稳定模式延迟更高，低延迟模式更容易卡顿。"));
        options.add(option(IJK_BUFFER, BUFFER, "读包缓冲", "怎么选：普通和大码率视频选15MB（默认，当前编译上限）；内存紧张选8MB；极低内存才选4MB。代价：缓冲越小越容易因网络抖动卡顿，越大占用更多内存。"));
        options.add(option(IJK_PACKET_BUFFERING, BUFFER, "Packet缓冲", "怎么选：点播和稳定直播保持开启（默认），数据不足时等待队列恢复；只为降低直播延迟才关闭。代价：开启会增加延迟，关闭在网络抖动时更容易卡顿、花屏。"));
        options.add(option(IJK_WATER, BUFFER, "缓冲水位", "怎么选：点播选“标准”（默认）；网络抖动/直播反复卡选“稳定”；只追求低延迟选“低”。代价：水位越高恢复越稳但等待更久，越低越容易再次断流。"));
        options.add(option(IJK_PICTURE_QUEUE, BUFFER, "画面队列", "怎么选：点播和低延迟用3帧（默认）；渲染偶发抖动用5帧；8帧只用于明显不稳且能接受更高延迟。代价：队列越大，内存和直播延迟越高。"));
        options.add(option(PLAY_CACHE, BUFFER, "HLS 播放缓存", "作用：限制IJK经HLS代理写入的磁盘缓存。频繁回看/拖动可增大；普通播放保持默认即可。代价：增加磁盘占用和写入，它不能扩大IJK native的15MB读包内存。"));
        addPreload(options);
        options.add(option(IJK_FRAME_DROP, DECODE, "丢帧策略", "怎么选：普通播放选“标准”（默认）；低性能设备持续落后时选“积极”；设备性能充足且必须保留每帧才关闭。代价：越积极越能追上进度，但画面跳帧越明显。"));
        options.add(option(IJK_SOFT_TUNE, DECODE, "软解降负载", "怎么选：默认“温和”；软解高负载、持续掉帧选“积极”；CPU充足且重视画质选关闭。代价：越积极越省CPU，但细节和连续性越差，硬解时帮助有限。"));
        options.add(option(IJK_ACCURATE_SEEK, DECODE, "精确Seek", "怎么选：默认关闭，拖动可更快恢复；只有必须准确落在目标时间点时开启。代价：需要从关键帧继续解码，拖动等待和CPU占用都会增加，不会改善正常播放流畅度。"));
        options.add(option(IJK_PROBE, DECODE, "流探测", "怎么选：普通资源保持“系统默认”；起播太慢可试“快速”；漏音轨、格式识别失败或直播信息不全时选“完整”。代价：快速可能误判，完整会延长起播。"));
        options.add(option(IJK_RTSP_TRANSPORT, DECODE, "RTSP传输", "怎么选：优先TCP（默认），公网和Wi-Fi更稳定；局域网质量很好且必须低延迟时选UDP；不确定可选自动。代价：TCP延迟略高，UDP丢包时会花屏或卡顿。"));
        options.add(option(IJK_RECONNECT, DECODE, "断线重连", "怎么选：直播和不稳定网络保持开启（默认），短暂断线可自动恢复；需要失败立即返回时关闭。代价：无效地址或服务器故障时，开启会延长最终报错时间。"));
    }

    private static void addSharedBuffer(List<PlaybackPerformanceOption> options, boolean exo, boolean playCache) {
        options.add(option(BUFFER_TIME, BUFFER, "缓冲时间", exo
                ? "怎么选：保持档位默认最均衡；网盘大文件/网络波动频繁可提高1～2档，内存紧张或直播低延迟才降低。数值越高越能跨过短时断流，但起播/恢复可能更慢且占用更多内存。它不能解决上游持续低于视频码率的问题。"
                : "怎么选：保持档位默认；网盘大文件/网络忽快忽慢可提高1～2档，低内存或低延迟直播才降低。数值越高越抗短时波动，但会增加MPV内存、预读流量和恢复等待；上游长期速度不足仍会卡。"));
        options.add(option(BUFFER_BYTES, BUFFER, "缓冲容量", exo
                ? "怎么选：优先“自动”（默认），EXO会按轨道估算；低内存设备固定64MB，普通设备可128MB，高码率4K且内存充足可256MB。容量过小会提前停止加载，过大增加内存压力，不能提升真实网速。"
                : "怎么选：普通设备用档位默认；4K高码率、内存充足可提高，低内存设备降低。容量太小可能装不下目标缓冲时间，太大会挤压系统内存；移动/电视设备不建议照搬桌面端超大缓存。"));
        options.add(option(BACK_BUFFER, BUFFER, "回退缓冲", exo
                ? "怎么选：不常向后拖动可关闭以最省内存；常回看选15～30秒；60秒只适合内存充足设备。它只加快向后拖动，不会改善向前播放卡顿。代价是保留时间越长占用越多内存。"
                : "怎么选：不常回退就关闭；常回看选15～30秒；60秒只用于内存充足设备。它只保留已播放数据、改善回退Seek，不会提高下载速度或解决向前卡顿；档位越高占用越大。"));
        if (playCache) options.add(option(PLAY_CACHE, BUFFER, "HLS 播放缓存", "怎么选：普通播放保持默认128MB；频繁回看/拖动可选256～512MB；1～2GB只适合存储充足且长时间播放HLS。它是代理磁盘缓存，不会直接提高MPV解码流畅度；代价是更多磁盘占用和写入。"));
    }

    private static void addPreload(List<PlaybackPerformanceOption> options) {
        options.add(option(PRELOAD, PRELOAD_SECTION, "预载", "怎么选：网盘点播和大文件建议保持自动/开启，可提前准备后续数据；直播、流量受限或播放本身已被预载抢带宽时关闭。代价：增加流量、磁盘写入和后台连接，预载不能抢占当前播放。"));
        options.add(option(PRELOAD_THREADS, PRELOAD_SECTION, "预载线程", "怎么选：自动档使用0～2条；手动通常1条最稳，播放带宽有富余可试2条，不建议盲目加高。线程越多不等于播放越快，反而可能挤占当前播放、触发服务器限流或412。"));
        options.add(option(PRELOAD_SIZE, PRELOAD_SECTION, "预载容量", "怎么选：保持档位默认；长视频/网盘大文件且存储充足可提高，空间紧张则降低。容量决定最多保存多少预载数据，不提高瞬时网速；越大占用磁盘越多。"));
        options.add(option(PRELOAD_TIME, PRELOAD_SECTION, "预载时间", "怎么选：自动档每次10～30秒；网络有短时波动可适当提高，流量或磁盘受限则降低。范围越长越能跨过较长波动，也会下载更多；过长可能让预载持续占用连接。"));
    }

    private static String profileDescription(int kernel) {
        return switch (kernel) {
            case PlayerSetting.MPV -> "首选“自动”：电视4K硬解且不需要MPV字幕/LUT/shader/滤镜时自动用低开销电视直出，其他场景保留GPU完整能力。“均衡”固定使用同一组通用参数；“兼容”改用GPU完整＋mediacodec-copy，适合零拷贝异常但4K可能更卡；“轻量”限制HLS至8Mbps并降低缓存，适合低内存/低性能设备。手动改任一项后显示“自定义”。";
            case PlayerSetting.IJK -> "首选“自动”：按协议采用稳定的点播/直播基线。“均衡”固定使用15MB读包、标准水位和标准丢帧；“兼容”提高水位、探测和画面队列，起播/恢复更慢但更稳；“轻量”降到4MB、快速探测和积极丢帧，省内存但更容易卡顿和损失画面。手动改任一项后显示“自定义”。";
            default -> "首选“自动”（也是默认）：以均衡参数起步，根据真实缓冲、码率和带宽把预载调为0～2线程，并在下一播放会话把重缓冲恢复调到2～8秒。“均衡”固定为1.5秒起播/3秒恢复；“兼容”用同步队列、2秒起播/5秒恢复，适合异步解码异常但启动更慢；“轻量”缩小缓存、1秒起播/2秒恢复，省内存但抗波动更弱。手动改任一项后显示“自定义”。";
        };
    }

    private static PlaybackPerformanceOption option(String id, String section, String title, String description) {
        return new PlaybackPerformanceOption(id, section, title, description);
    }
}
