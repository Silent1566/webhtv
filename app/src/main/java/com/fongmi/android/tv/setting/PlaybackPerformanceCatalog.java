package com.fongmi.android.tv.setting;

import java.util.ArrayList;
import java.util.List;

public final class PlaybackPerformanceCatalog {

    public static final String KERNEL = "kernel";
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
        options.add(option(KERNEL, BASIC, "播放器内核", "显示当前正在配置的播放器内核。EXO、MPV 和 IJK 使用相互独立的性能参数与预设，切换内核不会把其他内核专用参数伪装成当前可用。"));
        options.add(option(PROFILE, BASIC, "性能配置", profileDescription(kernel)));
        if (kernel == PlayerSetting.EXO) addExo(options);
        else if (kernel == PlayerSetting.MPV) addMpv(options);
        else addIjk(options);
        return options;
    }

    private static void addExo(List<PlaybackPerformanceOption> options) {
        options.add(option(RENDER, BASIC, "渲染方式", "SurfaceView 通常更省电并支持隧道模式；TextureView 更适合动画和自由变换，但会增加 GPU 合成开销。该选项只作用于 EXO，原生播放器使用自己的输出路径。"));
        options.add(option(TRACK_LIMIT, BASIC, "视频轨道限制", "按屏幕和硬件解码能力限制分辨率、帧率与码率，避免选择设备带不动的轨道；关闭后画质上限更高，也更容易卡顿或解码失败。"));
        options.add(option(ADAPTIVE_DOWNGRADE, BASIC, "自适应降级", "发生重缓冲、连续掉帧或带宽不足时逐级降低本次播放的视频规格；稳定性更好，但画质可能下降且当前实现不会自动升回。"));
        options.add(option(BANDWIDTH_METER, BASIC, "带宽估算", "根据实际数据传输估算网络能力，辅助 EXO 选轨和降级；不会主动测速，但网络波动时可能提前降低画质。"));
        options.add(option(TUNNEL, BASIC, "隧道模式", "让音视频尽量走硬件直通管线，可能降低 CPU 和改善同步；依赖 SurfaceView 和设备支持，部分电视会黑屏、无声或无法使用 LUT。"));
        options.add(option(EXO_FRAME_RATE, BASIC, "帧率匹配", "Android 11及以上通过 Surface.setFrameRate 请求匹配内容帧率。当前 Media3 依赖版本只提供关闭和仅无缝两种策略；允许非无缝需要独立实现显示模式切换后再开放。"));
        addSharedBuffer(options, true, false);
        options.add(option(EXO_START_BUFFER, BUFFER, "起播阈值", "达到该缓冲量后开始播放。阈值低首帧更快，但弱网下更容易刚起播就再次缓冲。"));
        options.add(option(EXO_REBUFFER, BUFFER, "重缓冲恢复", "播放中断后达到该缓冲量再恢复。阈值高恢复更稳定，但用户等待时间更长。"));
        options.add(option(EXO_PRIORITIZE_TIME, BUFFER, "时间优先", "开启时即使达到目标字节容量，也尽量满足时间缓冲目标；抗弱网更好但可能增加内存。关闭更严格遵守容量上限。"));
        options.add(option(LOAD_SELECTED_TRACKS, BUFFER, "只加载选中轨道", "只加载当前选中的音视频轨道，可节省带宽和内存；切换清晰度或音轨时可能需要重新请求。"));
        addPreload(options);
        options.add(option(CODEC_ASYNC, DECODE, "MediaCodec 队列", "自动模式交给 Media3 按系统版本选择；异步通常吞吐更高；同步适合少数异步回调实现异常的旧设备。"));
        options.add(option(DYNAMIC_SCHEDULING, DECODE, "Media3 动态调度", "根据渲染器可继续工作的时间安排播放循环，减少无效 CPU 唤醒；属于较新的 Media3 路径，兼容模式会关闭。"));
        options.add(option(DURATION_PROGRESS, DECODE, "解码耗时推进", "让异步视频渲染器把可推进时间反馈给播放器调度，有助于减少 CPU 唤醒；依赖 MediaCodec 异步路径。"));
        options.add(option(LATE_DROP, DECODE, "输入丢帧阈值", "预测输入帧已经明显迟到时提前丢弃，以更快追上播放进度；会减少持续延迟，但卡顿时可能看到跳帧。"));
        options.add(option(SURFACE_FIXED_SIZE, DECODE, "Surface 固定尺寸", "按视频和设备能力设置 Surface 缓冲尺寸，降低超高分辨率合成压力；部分设备在切清晰度或旋转时可能尺寸异常。"));
        options.add(option(DECODER_FALLBACK, DECODE, "解码器兜底", "首选解码器初始化失败后尝试低优先级解码器，提高播放成功率；可能增加起播时间或使用性能较低的解码器。"));
        options.add(option(SOFT_VIDEO_TUNE, DECODE, "软解降负载", "仅 EXO 的 FFmpeg 视频软解生效，通过减少滤波、跳过部分帧和降低解码负载改善低性能设备播放，代价是画质和流畅度下降。"));
        options.add(option(AUDIO_PASSTHROUGH, AUDIO, "音频直通", "把 Dolby、DTS 等压缩音频交给电视或功放解码，可保留多声道；输出链不支持时可能无声。"));
        options.add(option(PREFER_AAC, AUDIO, "AAC 优先", "存在 AAC 音轨时优先选择兼容性更广的 AAC，可规避部分高级音频无声问题，但可能放弃更高质量音轨。"));
        options.add(option(AUDIO_SOFT_PREFER, AUDIO, "音频软解优先", "优先使用 FFmpeg 音频扩展解码冷门格式，兼容性更强但增加 CPU 和功耗。"));
        options.add(option(VIDEO_SOFT_PREFER, AUDIO, "视频软解优先", "优先使用 FFmpeg 视频扩展绕过异常硬件解码器；高分辨率内容会显著增加 CPU、发热和掉帧风险。"));
    }

    private static void addMpv(List<PlaybackPerformanceOption> options) {
        options.add(option(MPV_RENDER, BASIC, "渲染后端", "OpenGL 兼容性最好；Vulkan 使用 gpu-next/libplacebo，部分设备性能更好，也更依赖驱动。只有 native 和设备能力都满足时才会实际使用 Vulkan，否则自动回退 OpenGL。"));
        options.add(option(MPV_HWDEC, BASIC, "硬解路径", "自动回退依次尝试 MediaCodec 零拷贝和兼容复制；零拷贝开销最低但设备兼容差异更大；兼容复制增加内存带宽，部分 Amlogic/Mali 设备仍可能绿屏。"));
        options.add(option(MPV_FRAME_RATE, BASIC, "帧率匹配", "Android 11及以上根据 MPV 识别到的内容帧率调用 Surface.setFrameRate。仅无缝模式不会主动触发可能黑屏的显示模式切换；旧系统自动忽略。"));
        options.add(option(MPV_OPTION_PRIORITY, BASIC, "参数优先级", "播放性能优先时，缓存、硬解、同步、丢帧和HLS码率等受性能档管理，同名mpv.conf参数会被覆盖，其他自定义仍生效；mpv.conf优先时由配置文件接管同名参数。"));
        addSharedBuffer(options, false, true);
        options.add(option(MPV_REBUFFER, BUFFER, "重缓冲恢复", "缓存耗尽后重新积累到指定时长再恢复播放，避免只下载一小段就反复播放、反复转圈。"));
        options.add(option(MPV_HLS_BITRATE, BUFFER, "HLS码率首选", "按HLS清单声明的码率选择默认轨道。限制码率可降低网络、解码和内存压力，但可能降低画质；它不是动态ABR，服务端码率标记不准确时效果也会偏差。"));
        addPreload(options);
        options.add(option(MPV_SYNC, DECODE, "同步模式", "音频同步是兼容默认；显示重采样会轻微调整音频速度以匹配屏幕刷新率，运动更平滑，但不适合音频直通。"));
        options.add(option(MPV_FRAME_DROP, DECODE, "丢帧策略", "输出丢帧优先在渲染阶段追赶进度；关闭可保留每一帧但可能持续累积延迟；解码丢帧更积极，也更容易损失画面连续性。"));
        options.add(option(MPV_INTERPOLATION, DECODE, "平滑运动", "配合显示同步在帧率不匹配时生成过渡帧，可改善运动平滑度；会明显增加 GPU 负载，HDR、LUT 或低性能电视建议关闭。"));
        options.add(option(MPV_SOFT_TUNE, DECODE, "软解降负载", "MPV 软件解码时使用 lavc 快速解码和环路滤波裁剪。温和模式优先保留画质，积极模式进一步减轻 CPU，但会降低细节。"));
        options.add(option(MPV_VERBOSE_LOG, DECODE, "详细日志", "正常模式仅保留 warn 级日志，减少 native 到 Java 的日志传递；详细模式用于排障，会增加 JNI、字符串和主线程处理负载。"));
        options.add(option(AUDIO_PASSTHROUGH, AUDIO, "音频直通", "MPV 根据设备音频能力生成 SPDIF 格式列表，将压缩音频交给电视或功放；输出链不支持时可能无声。"));
        options.add(option(PREFER_AAC, AUDIO, "AAC 优先", "MPV 在轨道列表可用后优先选择 AAC 音轨，兼容性更广，但可能放弃声道更多或质量更高的音轨。"));
    }

    private static void addIjk(List<PlaybackPerformanceOption> options) {
        options.add(option(IJK_SCENE, BASIC, "场景模式", "自动模式按协议使用稳定策略；点播保留完整缓冲；直播稳定提高水位和队列；直播低延迟关闭 packet buffering 并降低探测和队列，网络抖动时更容易卡顿。"));
        options.add(option(IJK_BUFFER, BUFFER, "读包缓冲", "限制 IJK native 预读队列。当前上游编译常量最大为15MB，因此只提供4、8、15MB三个真实档位。"));
        options.add(option(IJK_PACKET_BUFFERING, BUFFER, "Packet缓冲", "数据不足时暂停输出，等待读包队列恢复。开启更抗网络抖动；关闭可降低直播延迟，但更容易卡顿或花屏。"));
        options.add(option(IJK_WATER, BUFFER, "缓冲水位", "组合 IJK 的第一、第二和最终水位线。低水位响应快但抗抖动弱；稳定水位恢复更稳但等待更久。"));
        options.add(option(IJK_PICTURE_QUEUE, BUFFER, "画面队列", "控制已解码画面队列3、5或8帧。队列小延迟和内存更低；队列大更抗渲染抖动但增加延迟。"));
        options.add(option(PLAY_CACHE, BUFFER, "HLS 播放缓存", "限制 IJK HLS 代理可使用的磁盘播放缓存。它不改变 IJK native 固定的读包内存队列。"));
        addPreload(options);
        options.add(option(IJK_FRAME_DROP, DECODE, "丢帧策略", "CPU或渲染跟不上时丢帧追赶。标准模式使用上游常见值1；积极模式使用受控值5，不向用户暴露-1～120的原始范围。"));
        options.add(option(IJK_SOFT_TUNE, DECODE, "软解降负载", "软件解码时组合 FFmpeg fast、skip_loop_filter 和 skip_frame。温和模式仅跳过非参考滤波；积极模式进一步牺牲细节和连续性。"));
        options.add(option(IJK_ACCURATE_SEEK, DECODE, "精确Seek", "解码到目标时间以提高拖动精度，代价是拖动恢复更慢和CPU占用增加；关闭时使用关键帧快速定位。"));
        options.add(option(IJK_PROBE, DECODE, "流探测", "系统默认不覆盖 FFmpeg；快速模式减少起播探测但可能漏音轨或误判格式；完整模式提高识别率但延长起播。"));
        options.add(option(IJK_RTSP_TRANSPORT, DECODE, "RTSP传输", "TCP 更稳定并能避免部分丢包；UDP 延迟更低但更依赖网络质量；自动交给 FFmpeg 选择。"));
        options.add(option(IJK_RECONNECT, DECODE, "断线重连", "网络读取失败时允许 IJK/FFmpeg 重新连接，提高直播连续性；异常地址可能延长最终报错时间。"));
    }

    private static void addSharedBuffer(List<PlaybackPerformanceOption> options, boolean exo, boolean playCache) {
        options.add(option(BUFFER_TIME, BUFFER, "缓冲时间", exo ? "控制 EXO LoadControl 的前向缓冲目标。数值高更抗网络波动，但占用更多内存并延长部分恢复等待。" : "控制 MPV demuxer 向前读取的时间窗口。数值高更抗网络波动，但会增加内存和流量占用。"));
        options.add(option(BUFFER_BYTES, BUFFER, "缓冲容量", exo ? "限制 EXO LoadControl 的目标内存容量；自动模式按选中轨道计算，固定容量便于低内存设备控制上限。" : "限制 MPV demuxer 的前向缓存字节数。移动设备不宜沿用桌面端超大缓存。"));
        options.add(option(BACK_BUFFER, BUFFER, "回退缓冲", exo ? "保留最近播放的数据以加快向后拖动，时间越长越占内存。" : "为 MPV 保留已播放数据，便于回退拖动；当前界面以时间档位换算成安全字节预算。"));
        if (playCache) options.add(option(PLAY_CACHE, BUFFER, "HLS 播放缓存", "限制 MPV HLS 代理的磁盘播放缓存，改善回看和重复拖动；它与 MPV native 内存缓存是两套机制。"));
    }

    private static void addPreload(List<PlaybackPerformanceOption> options) {
        options.add(option(PRELOAD, PRELOAD_SECTION, "预载", "提前准备当前播放位置之后的数据，改善拖动和网络波动；会增加网络流量、磁盘写入和后台连接。"));
        options.add(option(PRELOAD_THREADS, PRELOAD_SECTION, "预载线程", "并发越高填充越快，也越可能挤占当前播放的网络、CPU和服务器连接。"));
        options.add(option(PRELOAD_SIZE, PRELOAD_SECTION, "预载容量", "限制预载磁盘预算。容量越大可保存更多内容，也会占用更多存储空间。"));
        options.add(option(PRELOAD_TIME, PRELOAD_SECTION, "预载时间", "控制每次向前预载的时间范围。范围越长越抗较长网络波动，也会下载更多数据。"));
    }

    private static String profileDescription(int kernel) {
        return switch (kernel) {
            case PlayerSetting.MPV -> "MPV 独立预设，组合输出、硬解、同步、缓存和诊断参数。参数优先级决定它与用户mpv.conf同名设置的覆盖关系。";
            case PlayerSetting.IJK -> "IJK 独立预设。后续只组合 IJK 的读包、缓冲、水位、丢帧、探测和直播策略，不修改 EXO/MPV 专用值。";
            default -> "EXO 独立预设，组合 Media3 选轨、LoadControl、MediaCodec、Surface、预载和音频参数，不修改 MPV/IJK 专用值。";
        };
    }

    private static PlaybackPerformanceOption option(String id, String section, String title, String description) {
        return new PlaybackPerformanceOption(id, section, title, description);
    }
}
