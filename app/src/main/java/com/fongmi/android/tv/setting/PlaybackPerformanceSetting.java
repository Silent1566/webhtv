package com.fongmi.android.tv.setting;

import com.github.catvod.utils.Prefers;

public class PlaybackPerformanceSetting {

    public static final int PROFILE_RECOMMENDED = 0;
    public static final int PROFILE_COMPATIBLE = 1;
    public static final int PROFILE_CUSTOM = 2;

    public static final String KEY_PROFILE = "playback_performance_profile";
    private static final String KEY_INITIALIZED = "playback_performance_initialized";
    private static final String KEY_CODEC_ASYNC_QUEUEING = "perf_codec_async_queueing";
    private static final String KEY_DYNAMIC_SCHEDULING = "perf_dynamic_scheduling";
    private static final String KEY_VIDEO_DURATION_PROGRESS = "perf_video_duration_progress";
    private static final String KEY_LATE_DROP_INPUT = "perf_late_drop_input";
    private static final String KEY_TRACK_LIMIT = "perf_track_limit";
    private static final String KEY_ADAPTIVE_DOWNGRADE = "perf_adaptive_downgrade";
    private static final String KEY_LOAD_ONLY_SELECTED_TRACKS = "perf_load_only_selected_tracks";
    private static final String KEY_SURFACE_FIXED_SIZE = "perf_surface_fixed_size";
    private static final String KEY_DECODER_FALLBACK = "perf_decoder_fallback";
    private static final String KEY_SOFT_VIDEO_TUNE = "perf_soft_video_tune";
    private static final String KEY_HIGH_BUFFER = "perf_high_buffer";
    private static final String KEY_BANDWIDTH_METER = "perf_bandwidth_meter";

    public static void ensureInitialized() {
        if (Prefers.getPrefers().contains(KEY_INITIALIZED)) return;
        applyRecommended();
        Prefers.put(KEY_INITIALIZED, true);
    }

    public static int getProfile() {
        ensureInitialized();
        return clampProfile(Prefers.getInt(KEY_PROFILE, PROFILE_RECOMMENDED));
    }

    public static void applyRecommended() {
        putRecommendedFlags();
        Prefers.put("render", PlayerSetting.RENDER_SURFACE);
        Prefers.put("tunnel", false);
        Prefers.put("buffer", 10);
        Prefers.put("buffer_bytes", 3);
        Prefers.put("back_buffer", 2);
        Prefers.put("play_cache", 2);
        Prefers.put("preload", true);
        Prefers.put("preload_threads", recommendedPreloadThreads());
        Prefers.put("preload_size", 512);
        Prefers.put("preload_time", 120);
        Prefers.put("audio_pass_through", true);
        Prefers.put("prefer_aac", false);
        Prefers.put("audio_prefer", false);
        Prefers.put("video_prefer", false);
        Prefers.put("exo_4k_compat", true);
        Prefers.put(KEY_PROFILE, PROFILE_RECOMMENDED);
    }

    public static void applyCompatible() {
        put(KEY_CODEC_ASYNC_QUEUEING, true);
        put(KEY_DYNAMIC_SCHEDULING, false);
        put(KEY_VIDEO_DURATION_PROGRESS, false);
        put(KEY_LATE_DROP_INPUT, false);
        put(KEY_TRACK_LIMIT, true);
        put(KEY_ADAPTIVE_DOWNGRADE, true);
        put(KEY_LOAD_ONLY_SELECTED_TRACKS, false);
        put(KEY_SURFACE_FIXED_SIZE, false);
        put(KEY_DECODER_FALLBACK, true);
        put(KEY_SOFT_VIDEO_TUNE, true);
        put(KEY_HIGH_BUFFER, true);
        put(KEY_BANDWIDTH_METER, false);
        Prefers.put("render", PlayerSetting.RENDER_SURFACE);
        Prefers.put("tunnel", false);
        Prefers.put("buffer", 5);
        Prefers.put("buffer_bytes", 1);
        Prefers.put("back_buffer", 1);
        Prefers.put("play_cache", 1);
        Prefers.put("preload", false);
        Prefers.put("preload_threads", 1);
        Prefers.put("preload_size", 128);
        Prefers.put("preload_time", 60);
        Prefers.put("audio_pass_through", false);
        Prefers.put("prefer_aac", true);
        Prefers.put("audio_prefer", false);
        Prefers.put("video_prefer", false);
        Prefers.put("exo_4k_compat", false);
        Prefers.put(KEY_PROFILE, PROFILE_COMPATIBLE);
    }

    public static void markCustom() {
        ensureInitialized();
        Prefers.put(KEY_PROFILE, PROFILE_CUSTOM);
    }

    public static String getProfileName() {
        return switch (getProfile()) {
            case PROFILE_COMPATIBLE -> "兼容";
            case PROFILE_CUSTOM -> "自定义";
            default -> "推荐";
        };
    }

    public static boolean isRecommended() {
        return getProfile() == PROFILE_RECOMMENDED;
    }

    public static boolean isCompatible() {
        return getProfile() == PROFILE_COMPATIBLE;
    }

    public static boolean isHighBufferEnabled() {
        ensureInitialized();
        return Prefers.getBoolean(KEY_HIGH_BUFFER, true);
    }

    public static void putHighBufferEnabled(boolean value) {
        putCustom(KEY_HIGH_BUFFER, value);
    }

    public static boolean isBandwidthMeterEnabled() {
        ensureInitialized();
        return Prefers.getBoolean(KEY_BANDWIDTH_METER, true);
    }

    public static void putBandwidthMeterEnabled(boolean value) {
        putCustom(KEY_BANDWIDTH_METER, value);
    }

    public static boolean isDynamicSchedulingEnabled() {
        ensureInitialized();
        return Prefers.getBoolean(KEY_DYNAMIC_SCHEDULING, true);
    }

    public static void putDynamicSchedulingEnabled(boolean value) {
        putCustom(KEY_DYNAMIC_SCHEDULING, value);
    }

    public static boolean isCodecAsyncQueueingEnabled() {
        ensureInitialized();
        return Prefers.getBoolean(KEY_CODEC_ASYNC_QUEUEING, true);
    }

    public static void putCodecAsyncQueueingEnabled(boolean value) {
        putCustom(KEY_CODEC_ASYNC_QUEUEING, value);
    }

    public static boolean isVideoDurationProgressEnabled() {
        ensureInitialized();
        return Prefers.getBoolean(KEY_VIDEO_DURATION_PROGRESS, true);
    }

    public static void putVideoDurationProgressEnabled(boolean value) {
        putCustom(KEY_VIDEO_DURATION_PROGRESS, value);
    }

    public static boolean isLateDropInputEnabled() {
        ensureInitialized();
        return Prefers.getBoolean(KEY_LATE_DROP_INPUT, true);
    }

    public static void putLateDropInputEnabled(boolean value) {
        putCustom(KEY_LATE_DROP_INPUT, value);
    }

    public static boolean isTrackLimitEnabled() {
        ensureInitialized();
        return Prefers.getBoolean(KEY_TRACK_LIMIT, true);
    }

    public static void putTrackLimitEnabled(boolean value) {
        putCustom(KEY_TRACK_LIMIT, value);
    }

    public static boolean isAdaptiveDowngradeEnabled() {
        ensureInitialized();
        return Prefers.getBoolean(KEY_ADAPTIVE_DOWNGRADE, true);
    }

    public static void putAdaptiveDowngradeEnabled(boolean value) {
        putCustom(KEY_ADAPTIVE_DOWNGRADE, value);
    }

    public static boolean isLoadOnlySelectedTracksEnabled() {
        ensureInitialized();
        return Prefers.getBoolean(KEY_LOAD_ONLY_SELECTED_TRACKS, true);
    }

    public static void putLoadOnlySelectedTracksEnabled(boolean value) {
        putCustom(KEY_LOAD_ONLY_SELECTED_TRACKS, value);
    }

    public static boolean isSurfaceFixedSizeEnabled() {
        ensureInitialized();
        return Prefers.getBoolean(KEY_SURFACE_FIXED_SIZE, true);
    }

    public static void putSurfaceFixedSizeEnabled(boolean value) {
        putCustom(KEY_SURFACE_FIXED_SIZE, value);
    }

    public static boolean isDecoderFallbackEnabled() {
        ensureInitialized();
        return Prefers.getBoolean(KEY_DECODER_FALLBACK, true);
    }

    public static void putDecoderFallbackEnabled(boolean value) {
        putCustom(KEY_DECODER_FALLBACK, value);
    }

    public static boolean isSoftVideoTuneEnabled() {
        ensureInitialized();
        return Prefers.getBoolean(KEY_SOFT_VIDEO_TUNE, true);
    }

    public static void putSoftVideoTuneEnabled(boolean value) {
        putCustom(KEY_SOFT_VIDEO_TUNE, value);
    }

    public static String getSummary() {
        ensureInitialized();
        return getProfileName() + " · " + (isTrackLimitEnabled() ? "轨道限制" : "不限轨道") + " · " + (PreloadSetting.isPreload() ? "预载开" : "预载关");
    }

    public static String getDetail() {
        ensureInitialized();
        return "配置：" + getProfileName()
                + "\n渲染：" + (PlayerSetting.getRender() == PlayerSetting.RENDER_SURFACE ? "SurfaceView" : "TextureView")
                + "\n轨道限制：" + onOff(isTrackLimitEnabled()) + "，自适应降级：" + onOff(isAdaptiveDowngradeEnabled())
                + "\n缓冲：" + PlayerSetting.getBuffer() + "/10，容量：" + bufferBytesText() + "，回退：" + backBufferText()
                + "\n播放缓存：" + playCacheText()
                + "\n预载：" + onOff(PreloadSetting.isPreload()) + "，线程：" + PreloadSetting.getPreloadThreads() + "，容量：" + PreloadSetting.getPreloadSizeMb() + "MB，时间：" + PreloadSetting.getPreloadTimeSeconds() + "秒"
                + "\nMediaCodec异步：" + onOff(isCodecAsyncQueueingEnabled()) + "，动态调度：" + onOff(isDynamicSchedulingEnabled())
                + "\n解码耗时推进：" + onOff(isVideoDurationProgressEnabled()) + "，输入丢帧阈值：" + onOff(isLateDropInputEnabled())
                + "\n只加载选中轨道：" + onOff(isLoadOnlySelectedTracksEnabled()) + "，Surface固定尺寸：" + onOff(isSurfaceFixedSizeEnabled())
                + "\n音频直通：" + onOff(PlayerSetting.isAudioPassThrough()) + "，AAC优先：" + onOff(PlayerSetting.isPreferAAC())
                + "\n视频软解优先：" + onOff(PlayerSetting.isVideoPrefer()) + "，音频软解优先：" + onOff(PlayerSetting.isAudioPrefer())
                + "\n软解降负载：" + onOff(isSoftVideoTuneEnabled());
    }

    private static void putRecommendedFlags() {
        put(KEY_CODEC_ASYNC_QUEUEING, true);
        put(KEY_DYNAMIC_SCHEDULING, true);
        put(KEY_VIDEO_DURATION_PROGRESS, true);
        put(KEY_LATE_DROP_INPUT, true);
        put(KEY_TRACK_LIMIT, true);
        put(KEY_ADAPTIVE_DOWNGRADE, true);
        put(KEY_LOAD_ONLY_SELECTED_TRACKS, true);
        put(KEY_SURFACE_FIXED_SIZE, true);
        put(KEY_DECODER_FALLBACK, true);
        put(KEY_SOFT_VIDEO_TUNE, true);
        put(KEY_HIGH_BUFFER, true);
        put(KEY_BANDWIDTH_METER, true);
    }

    private static int recommendedPreloadThreads() {
        return Math.max(2, Math.min(3, Runtime.getRuntime().availableProcessors() / 2));
    }

    private static int clampProfile(int profile) {
        return profile == PROFILE_COMPATIBLE || profile == PROFILE_CUSTOM ? profile : PROFILE_RECOMMENDED;
    }

    private static void put(String key, boolean value) {
        Prefers.put(key, value);
    }

    private static void putCustom(String key, boolean value) {
        ensureInitialized();
        Prefers.put(key, value);
        markCustom();
    }

    private static String onOff(boolean value) {
        return value ? "开" : "关";
    }

    private static String bufferBytesText() {
        return switch (PlayerSetting.getBufferBytesOption()) {
            case 1 -> "64MB";
            case 2 -> "128MB";
            case 3 -> "256MB";
            default -> "自动";
        };
    }

    private static String backBufferText() {
        return switch (PlayerSetting.getBackBufferOption()) {
            case 1 -> "15秒";
            case 2 -> "30秒";
            case 3 -> "60秒";
            default -> "关";
        };
    }

    private static String playCacheText() {
        return switch (PlayerSetting.getPlayCacheOption()) {
            case 1 -> "256MB";
            case 2 -> "512MB";
            case 3 -> "1GB";
            case 4 -> "2GB";
            default -> "128MB";
        };
    }
}
