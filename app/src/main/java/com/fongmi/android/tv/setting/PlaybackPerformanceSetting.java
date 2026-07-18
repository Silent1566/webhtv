package com.fongmi.android.tv.setting;

import com.github.catvod.utils.Prefers;

public class PlaybackPerformanceSetting {

    public static final int PROFILE_RECOMMENDED = 0;
    public static final int PROFILE_COMPATIBLE = 1;
    public static final int PROFILE_CUSTOM = 2;
    public static final int PROFILE_LIGHTWEIGHT = 3;
    public static final int PROFILE_ORIGINAL = 4;

    public static final String KEY_PROFILE = "playback_performance_profile";
    private static final String KEY_PROFILE_MIGRATED = "playback_performance_profile_per_kernel";
    private static final String KEY_PROFILE_EXO = "perf_exo_profile";
    private static final String KEY_PROFILE_MPV = "perf_mpv_profile";
    private static final String KEY_PROFILE_IJK = "perf_ijk_profile";
    private static final String KEY_INITIALIZED = "playback_performance_initialized";
    private static final String KEY_BUFFER_WATERMARKS_MIGRATED = "playback_performance_buffer_watermarks_v2";
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
        if (!Prefers.getPrefers().contains(KEY_INITIALIZED)) {
            applyRecommendedValues();
            Prefers.put(KEY_INITIALIZED, true);
        }
        migrateProfiles();
        migrateBufferWatermarks();
    }

    public static int getProfile() {
        ensureInitialized();
        return clampProfile(Prefers.getInt(profileKey(PlayerSetting.getPlayer()), Prefers.getInt(KEY_PROFILE, PROFILE_RECOMMENDED)));
    }

    public static void applyRecommended() {
        KernelPerformanceSetting.applyPreset(PlayerSetting.getPlayer(), PROFILE_RECOMMENDED);
        applyRecommendedValues();
        if (PlayerSetting.getPlayer() == PlayerSetting.EXO) ExoPerformanceSetting.applyRecommended();
        if (PlayerSetting.getPlayer() == PlayerSetting.MPV) MpvPerformanceSetting.applyRecommended();
        if (PlayerSetting.getPlayer() == PlayerSetting.IJK) IjkPerformanceSetting.applyRecommended();
        putCurrentProfile(PROFILE_RECOMMENDED);
    }

    private static void applyRecommendedValues() {
        int kernel = PlayerSetting.getPlayer();
        KernelPerformanceSetting.applyPreset(kernel, PROFILE_RECOMMENDED);
        if (kernel != PlayerSetting.EXO) return;
        putRecommendedFlags();
        Prefers.put("render", PlayerSetting.RENDER_SURFACE);
        Prefers.put("tunnel", false);
        Prefers.put("exo_4k_compat", true);
    }

    public static void applyCompatible() {
        KernelPerformanceSetting.applyPreset(PlayerSetting.getPlayer(), PROFILE_COMPATIBLE);
        if (PlayerSetting.getPlayer() == PlayerSetting.EXO) ExoPerformanceSetting.applyCompatible();
        if (PlayerSetting.getPlayer() == PlayerSetting.MPV) MpvPerformanceSetting.applyCompatible();
        if (PlayerSetting.getPlayer() == PlayerSetting.IJK) IjkPerformanceSetting.applyCompatible();
        if (PlayerSetting.getPlayer() == PlayerSetting.EXO) {
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
        }
        if (PlayerSetting.getPlayer() == PlayerSetting.EXO) {
            Prefers.put("render", PlayerSetting.RENDER_SURFACE);
            Prefers.put("tunnel", false);
            Prefers.put("exo_4k_compat", false);
        }
        putCurrentProfile(PROFILE_COMPATIBLE);
    }

    public static void applyLightweight() {
        KernelPerformanceSetting.applyPreset(PlayerSetting.getPlayer(), PROFILE_LIGHTWEIGHT);
        if (PlayerSetting.getPlayer() == PlayerSetting.EXO) ExoPerformanceSetting.applyLightweight();
        if (PlayerSetting.getPlayer() == PlayerSetting.MPV) MpvPerformanceSetting.applyLightweight();
        if (PlayerSetting.getPlayer() == PlayerSetting.IJK) IjkPerformanceSetting.applyLightweight();
        if (PlayerSetting.getPlayer() == PlayerSetting.EXO) {
            put(KEY_CODEC_ASYNC_QUEUEING, true);
            put(KEY_DYNAMIC_SCHEDULING, false);
            put(KEY_VIDEO_DURATION_PROGRESS, false);
            put(KEY_LATE_DROP_INPUT, false);
            put(KEY_TRACK_LIMIT, true);
            put(KEY_ADAPTIVE_DOWNGRADE, true);
            put(KEY_LOAD_ONLY_SELECTED_TRACKS, true);
            put(KEY_SURFACE_FIXED_SIZE, false);
            put(KEY_DECODER_FALLBACK, true);
            put(KEY_SOFT_VIDEO_TUNE, true);
            put(KEY_HIGH_BUFFER, true);
            put(KEY_BANDWIDTH_METER, false);
        }
        if (PlayerSetting.getPlayer() == PlayerSetting.EXO) {
            Prefers.put("render", PlayerSetting.RENDER_SURFACE);
            Prefers.put("tunnel", false);
            Prefers.put("exo_4k_compat", false);
        }
        putCurrentProfile(PROFILE_LIGHTWEIGHT);
    }

    public static void applyOriginal() {
        put(KEY_CODEC_ASYNC_QUEUEING, false);
        put(KEY_DYNAMIC_SCHEDULING, false);
        put(KEY_VIDEO_DURATION_PROGRESS, false);
        put(KEY_LATE_DROP_INPUT, false);
        put(KEY_TRACK_LIMIT, false);
        put(KEY_ADAPTIVE_DOWNGRADE, false);
        put(KEY_LOAD_ONLY_SELECTED_TRACKS, false);
        put(KEY_SURFACE_FIXED_SIZE, false);
        put(KEY_DECODER_FALLBACK, true);
        put(KEY_SOFT_VIDEO_TUNE, true);
        put(KEY_HIGH_BUFFER, false);
        put(KEY_BANDWIDTH_METER, false);
        Prefers.put("render", PlayerSetting.RENDER_SURFACE);
        Prefers.put("tunnel", false);
        Prefers.put("buffer", 1);
        Prefers.put("buffer_bytes", 0);
        Prefers.put("back_buffer", 0);
        Prefers.put("play_cache", 0);
        Prefers.put("preload", false);
        Prefers.put("preload_threads", 1);
        Prefers.put("preload_size", 128);
        Prefers.put("preload_time", 120);
        Prefers.put("audio_pass_through", true);
        Prefers.put("prefer_aac", false);
        Prefers.put("audio_prefer", false);
        Prefers.put("video_prefer", false);
        Prefers.put("exo_4k_compat", false);
        putCurrentProfile(PROFILE_ORIGINAL);
    }

    public static void markCustom() {
        ensureInitialized();
        putCurrentProfile(PROFILE_CUSTOM);
    }

    public static String getProfileName() {
        return switch (getProfile()) {
            case PROFILE_COMPATIBLE -> "兼容";
            case PROFILE_LIGHTWEIGHT -> "轻量";
            case PROFILE_CUSTOM -> "自定义";
            case PROFILE_ORIGINAL -> "原版";
            default -> "推荐";
        };
    }

    public static boolean isRecommended() {
        return getProfile() == PROFILE_RECOMMENDED;
    }

    public static boolean isCompatible() {
        return getProfile() == PROFILE_COMPATIBLE;
    }

    public static boolean isLightweight() {
        return getProfile() == PROFILE_LIGHTWEIGHT;
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
        String preload = PreloadSetting.isPreload() ? "预载开" : "预载关";
        return switch (PlayerSetting.getPlayer()) {
            case PlayerSetting.IJK -> getProfileName() + " · IJK · " + preload;
            case PlayerSetting.MPV -> getProfileName() + " · MPV · " + MpvPerformanceSetting.getOptionPriorityText() + " · " + preload;
            default -> getProfileName() + " · " + (isTrackLimitEnabled() ? "轨道限制" : "不限轨道") + " · " + preload;
        };
    }

    public static String getDetail() {
        ensureInitialized();
        return "配置：" + getProfileName()
                + "\n渲染：" + (PlayerSetting.getRender() == PlayerSetting.RENDER_SURFACE ? "SurfaceView" : "TextureView")
                + "\n轨道限制：" + onOff(isTrackLimitEnabled()) + "，自适应降级：" + onOff(isAdaptiveDowngradeEnabled())
                + "\n缓冲：" + PlayerSetting.getBuffer() + "/10，容量：" + bufferBytesText() + "，回退：" + backBufferText()
                + bufferWatermarksText()
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

    private static int clampProfile(int profile) {
        return profile == PROFILE_COMPATIBLE || profile == PROFILE_CUSTOM || profile == PROFILE_LIGHTWEIGHT || profile == PROFILE_ORIGINAL ? profile : PROFILE_RECOMMENDED;
    }

    private static void put(String key, boolean value) {
        Prefers.put(key, value);
    }

    private static void putCustom(String key, boolean value) {
        ensureInitialized();
        Prefers.put(key, value);
        markCustom();
    }

    private static void migrateProfiles() {
        if (Prefers.getBoolean(KEY_PROFILE_MIGRATED)) return;
        int oldProfile = clampProfile(Prefers.getInt(KEY_PROFILE, PROFILE_RECOMMENDED));
        Prefers.put(KEY_PROFILE_EXO, oldProfile);
        Prefers.put(KEY_PROFILE_MPV, oldProfile);
        Prefers.put(KEY_PROFILE_IJK, oldProfile);
        applyKernelSpecificPreset(PlayerSetting.EXO, oldProfile);
        applyKernelSpecificPreset(PlayerSetting.MPV, oldProfile);
        applyKernelSpecificPreset(PlayerSetting.IJK, oldProfile);
        Prefers.put(KEY_PROFILE_MIGRATED, true);
    }

    private static void migrateBufferWatermarks() {
        if (Prefers.getBoolean(KEY_BUFFER_WATERMARKS_MIGRATED)) return;
        int exoProfile = clampProfile(Prefers.getInt(profileKey(PlayerSetting.EXO), PROFILE_RECOMMENDED));
        int mpvProfile = clampProfile(Prefers.getInt(profileKey(PlayerSetting.MPV), PROFILE_RECOMMENDED));
        if (exoProfile != PROFILE_CUSTOM) ExoPerformanceSetting.applyRebufferPreset(exoProfile);
        if (mpvProfile != PROFILE_CUSTOM) MpvPerformanceSetting.applyRebufferPreset(mpvProfile);
        Prefers.put(KEY_BUFFER_WATERMARKS_MIGRATED, true);
    }

    private static void applyKernelSpecificPreset(int kernel, int profile) {
        if (kernel == PlayerSetting.EXO) {
            if (profile == PROFILE_COMPATIBLE) ExoPerformanceSetting.applyCompatible();
            else if (profile == PROFILE_LIGHTWEIGHT) ExoPerformanceSetting.applyLightweight();
            else ExoPerformanceSetting.applyRecommended();
        } else if (kernel == PlayerSetting.MPV) {
            if (profile == PROFILE_COMPATIBLE) MpvPerformanceSetting.applyCompatible();
            else if (profile == PROFILE_LIGHTWEIGHT) MpvPerformanceSetting.applyLightweight();
            else MpvPerformanceSetting.applyRecommended();
        } else {
            if (profile == PROFILE_COMPATIBLE) IjkPerformanceSetting.applyCompatible();
            else if (profile == PROFILE_LIGHTWEIGHT) IjkPerformanceSetting.applyLightweight();
            else IjkPerformanceSetting.applyRecommended();
        }
    }

    private static void putCurrentProfile(int profile) {
        int value = clampProfile(profile);
        Prefers.put(profileKey(PlayerSetting.getPlayer()), value);
        Prefers.put(KEY_PROFILE, value);
    }

    private static String profileKey(int kernel) {
        return switch (kernel) {
            case PlayerSetting.IJK -> KEY_PROFILE_IJK;
            case PlayerSetting.MPV -> KEY_PROFILE_MPV;
            default -> KEY_PROFILE_EXO;
        };
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

    private static String bufferWatermarksText() {
        return switch (PlayerSetting.getPlayer()) {
            case PlayerSetting.EXO -> "\n起播阈值：" + secondsText(ExoPerformanceSetting.getStartBufferMs()) + "，重缓冲恢复：" + secondsText(ExoPerformanceSetting.getRebufferMs());
            case PlayerSetting.MPV -> "\n参数优先级：" + MpvPerformanceSetting.getOptionPriorityText() + "，重缓冲恢复：" + secondsText(MpvPerformanceSetting.getRebufferMs());
            default -> "";
        };
    }

    private static String secondsText(int milliseconds) {
        return milliseconds % 1000 == 0 ? milliseconds / 1000 + "秒" : String.format(java.util.Locale.US, "%.1f秒", milliseconds / 1000f);
    }
}
