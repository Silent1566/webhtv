package com.fongmi.android.tv.setting;

import com.github.catvod.utils.Prefers;

public final class MpvPerformanceSetting {

    public static final int HWDEC_AUTO = 0;
    public static final int HWDEC_DIRECT = 1;
    public static final int HWDEC_COPY = 2;
    public static final int SYNC_AUDIO = 0;
    public static final int SYNC_DISPLAY_RESAMPLE = 1;
    public static final int FRAME_DROP_OUTPUT = 0;
    public static final int FRAME_DROP_OFF = 1;
    public static final int FRAME_DROP_DECODER = 2;
    public static final int SOFT_TUNE_OFF = 0;
    public static final int SOFT_TUNE_MILD = 1;
    public static final int SOFT_TUNE_AGGRESSIVE = 2;
    public static final int FRAME_RATE_OFF = 0;
    public static final int FRAME_RATE_SEAMLESS = 1;
    public static final int HLS_HIGHEST = 0;
    public static final int HLS_15_MBPS = 1;
    public static final int HLS_8_MBPS = 2;
    public static final int HLS_LOWEST = 3;
    public static final int PRIORITY_PERFORMANCE = 0;
    public static final int PRIORITY_CONFIG = 1;

    private static final String KEY_HWDEC = "perf_mpv_hwdec";
    private static final String KEY_SYNC = "perf_mpv_sync";
    private static final String KEY_FRAME_DROP = "perf_mpv_frame_drop";
    private static final String KEY_INTERPOLATION = "perf_mpv_interpolation";
    private static final String KEY_SOFT_TUNE = "perf_mpv_soft_tune";
    private static final String KEY_VERBOSE_LOG = "perf_mpv_verbose_log";
    private static final String KEY_FRAME_RATE = "perf_mpv_frame_rate";
    private static final String KEY_HLS_BITRATE = "perf_mpv_hls_bitrate";
    private static final String KEY_REBUFFER_MS = "perf_mpv_rebuffer_ms";
    private static final String KEY_OPTION_PRIORITY = "perf_mpv_option_priority";

    private MpvPerformanceSetting() {
    }

    public static int getHwdecMode() {
        return clamp(Prefers.getInt(KEY_HWDEC, HWDEC_AUTO), HWDEC_AUTO, HWDEC_COPY);
    }

    public static void putHwdecMode(int value) {
        Prefers.put(KEY_HWDEC, clamp(value, HWDEC_AUTO, HWDEC_COPY));
        PlaybackPerformanceSetting.markCustom();
    }

    public static String getHwdecOption() {
        return switch (getHwdecMode()) {
            case HWDEC_DIRECT -> "mediacodec";
            case HWDEC_COPY -> "mediacodec-copy";
            default -> "mediacodec,mediacodec-copy";
        };
    }

    public static String getHwdecText() {
        return switch (getHwdecMode()) {
            case HWDEC_DIRECT -> "零拷贝优先";
            case HWDEC_COPY -> "兼容复制";
            default -> "自动回退";
        };
    }

    public static int getSyncMode() {
        return clamp(Prefers.getInt(KEY_SYNC, SYNC_AUDIO), SYNC_AUDIO, SYNC_DISPLAY_RESAMPLE);
    }

    public static void putSyncMode(int value) {
        Prefers.put(KEY_SYNC, clamp(value, SYNC_AUDIO, SYNC_DISPLAY_RESAMPLE));
        PlaybackPerformanceSetting.markCustom();
    }

    public static String getSyncOption() {
        return getSyncMode() == SYNC_DISPLAY_RESAMPLE ? "display-resample" : "audio";
    }

    public static String getSyncText() {
        return getSyncMode() == SYNC_DISPLAY_RESAMPLE ? "显示重采样" : "音频同步";
    }

    public static int getFrameDropMode() {
        return clamp(Prefers.getInt(KEY_FRAME_DROP, FRAME_DROP_OUTPUT), FRAME_DROP_OUTPUT, FRAME_DROP_DECODER);
    }

    public static void putFrameDropMode(int value) {
        Prefers.put(KEY_FRAME_DROP, clamp(value, FRAME_DROP_OUTPUT, FRAME_DROP_DECODER));
        PlaybackPerformanceSetting.markCustom();
    }

    public static String getFrameDropOption() {
        return switch (getFrameDropMode()) {
            case FRAME_DROP_OFF -> "no";
            case FRAME_DROP_DECODER -> "decoder";
            default -> "vo";
        };
    }

    public static String getFrameDropText() {
        return switch (getFrameDropMode()) {
            case FRAME_DROP_OFF -> "关闭";
            case FRAME_DROP_DECODER -> "解码丢帧";
            default -> "输出丢帧";
        };
    }

    public static boolean isInterpolation() {
        return Prefers.getBoolean(KEY_INTERPOLATION);
    }

    public static void putInterpolation(boolean value) {
        Prefers.put(KEY_INTERPOLATION, value);
        PlaybackPerformanceSetting.markCustom();
    }

    public static int getSoftTuneMode() {
        return clamp(Prefers.getInt(KEY_SOFT_TUNE, SOFT_TUNE_MILD), SOFT_TUNE_OFF, SOFT_TUNE_AGGRESSIVE);
    }

    public static void putSoftTuneMode(int value) {
        Prefers.put(KEY_SOFT_TUNE, clamp(value, SOFT_TUNE_OFF, SOFT_TUNE_AGGRESSIVE));
        PlaybackPerformanceSetting.markCustom();
    }

    public static String getSoftTuneText() {
        return switch (getSoftTuneMode()) {
            case SOFT_TUNE_OFF -> "关闭";
            case SOFT_TUNE_AGGRESSIVE -> "积极";
            default -> "温和";
        };
    }

    public static boolean isVerboseLog() {
        return Prefers.getBoolean(KEY_VERBOSE_LOG);
    }

    public static int getFrameRateMode() {
        return clamp(Prefers.getInt(KEY_FRAME_RATE, FRAME_RATE_SEAMLESS), FRAME_RATE_OFF, FRAME_RATE_SEAMLESS);
    }

    public static void putFrameRateMode(int value) {
        Prefers.put(KEY_FRAME_RATE, clamp(value, FRAME_RATE_OFF, FRAME_RATE_SEAMLESS));
        PlaybackPerformanceSetting.markCustom();
    }

    public static String getFrameRateText() {
        return getFrameRateMode() == FRAME_RATE_OFF ? "关闭" : "仅无缝";
    }

    public static int getHlsBitrateMode() {
        return clamp(Prefers.getInt(KEY_HLS_BITRATE, HLS_HIGHEST), HLS_HIGHEST, HLS_LOWEST);
    }

    public static void putHlsBitrateMode(int value) {
        Prefers.put(KEY_HLS_BITRATE, clamp(value, HLS_HIGHEST, HLS_LOWEST));
        PlaybackPerformanceSetting.markCustom();
    }

    public static String getHlsBitrateOption() {
        return switch (getHlsBitrateMode()) {
            case HLS_15_MBPS -> "15000000";
            case HLS_8_MBPS -> "8000000";
            case HLS_LOWEST -> "min";
            default -> "max";
        };
    }

    public static String getHlsBitrateText() {
        return switch (getHlsBitrateMode()) {
            case HLS_15_MBPS -> "不超过15Mbps";
            case HLS_8_MBPS -> "不超过8Mbps";
            case HLS_LOWEST -> "最低码率";
            default -> "最高码率";
        };
    }

    public static int getRebufferMs() {
        return normalizeRebuffer(Prefers.getInt(KEY_REBUFFER_MS, 10_000));
    }

    public static void putRebufferMs(int value) {
        Prefers.put(KEY_REBUFFER_MS, normalizeRebuffer(value));
        PlaybackPerformanceSetting.markCustom();
    }

    public static int nextRebufferMs() {
        return switch (getRebufferMs()) {
            case 3_000 -> 5_000;
            case 5_000 -> 8_000;
            case 8_000 -> 10_000;
            case 10_000 -> 15_000;
            default -> 3_000;
        };
    }

    public static int getOptionPriority() {
        return clamp(Prefers.getInt(KEY_OPTION_PRIORITY, PRIORITY_PERFORMANCE), PRIORITY_PERFORMANCE, PRIORITY_CONFIG);
    }

    public static void putOptionPriority(int value) {
        Prefers.put(KEY_OPTION_PRIORITY, clamp(value, PRIORITY_PERFORMANCE, PRIORITY_CONFIG));
    }

    public static boolean isPerformancePriority() {
        return getOptionPriority() == PRIORITY_PERFORMANCE;
    }

    public static String getOptionPriorityText() {
        return isPerformancePriority() ? "播放性能优先" : "mpv.conf优先";
    }

    public static void putVerboseLog(boolean value) {
        Prefers.put(KEY_VERBOSE_LOG, value);
        PlaybackPerformanceSetting.markCustom();
    }

    public static void applyRecommended() {
        Prefers.put(KEY_HWDEC, HWDEC_AUTO);
        Prefers.put(KEY_SYNC, SYNC_AUDIO);
        Prefers.put(KEY_FRAME_DROP, FRAME_DROP_OUTPUT);
        Prefers.put(KEY_INTERPOLATION, false);
        Prefers.put(KEY_SOFT_TUNE, SOFT_TUNE_MILD);
        Prefers.put(KEY_VERBOSE_LOG, false);
        Prefers.put(KEY_FRAME_RATE, FRAME_RATE_SEAMLESS);
        Prefers.put(KEY_HLS_BITRATE, HLS_HIGHEST);
        applyRebufferPreset(PlaybackPerformanceSetting.PROFILE_RECOMMENDED);
    }

    public static void applyCompatible() {
        Prefers.put(KEY_HWDEC, HWDEC_COPY);
        Prefers.put(KEY_SYNC, SYNC_AUDIO);
        Prefers.put(KEY_FRAME_DROP, FRAME_DROP_OUTPUT);
        Prefers.put(KEY_INTERPOLATION, false);
        Prefers.put(KEY_SOFT_TUNE, SOFT_TUNE_MILD);
        Prefers.put(KEY_VERBOSE_LOG, false);
        Prefers.put(KEY_FRAME_RATE, FRAME_RATE_OFF);
        Prefers.put(KEY_HLS_BITRATE, HLS_HIGHEST);
        applyRebufferPreset(PlaybackPerformanceSetting.PROFILE_COMPATIBLE);
    }

    public static void applyLightweight() {
        Prefers.put(KEY_HWDEC, HWDEC_AUTO);
        Prefers.put(KEY_SYNC, SYNC_AUDIO);
        Prefers.put(KEY_FRAME_DROP, FRAME_DROP_OUTPUT);
        Prefers.put(KEY_INTERPOLATION, false);
        Prefers.put(KEY_SOFT_TUNE, SOFT_TUNE_AGGRESSIVE);
        Prefers.put(KEY_VERBOSE_LOG, false);
        Prefers.put(KEY_FRAME_RATE, FRAME_RATE_SEAMLESS);
        Prefers.put(KEY_HLS_BITRATE, HLS_8_MBPS);
        applyRebufferPreset(PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT);
    }

    static void applyRebufferPreset(int profile) {
        if (profile == PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT) {
            Prefers.put(KEY_REBUFFER_MS, 3_000);
        } else if (profile == PlaybackPerformanceSetting.PROFILE_COMPATIBLE) {
            Prefers.put(KEY_REBUFFER_MS, 5_000);
        } else {
            Prefers.put(KEY_REBUFFER_MS, 10_000);
        }
    }

    private static int normalizeRebuffer(int value) {
        if (value <= 3_000) return 3_000;
        if (value <= 5_000) return 5_000;
        if (value <= 8_000) return 8_000;
        if (value <= 10_000) return 10_000;
        return 15_000;
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }
}
