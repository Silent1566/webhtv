package com.fongmi.android.tv.setting;

import androidx.media3.common.C;

import com.github.catvod.utils.Prefers;

public final class ExoPerformanceSetting {

    public static final int CODEC_QUEUE_AUTO = 0;
    public static final int CODEC_QUEUE_ASYNC = 1;
    public static final int CODEC_QUEUE_SYNC = 2;
    public static final int FRAME_RATE_OFF = 0;
    public static final int FRAME_RATE_SEAMLESS = 1;

    private static final String KEY_CODEC_QUEUE_MODE = "perf_exo_codec_queue_mode";
    private static final String KEY_FRAME_RATE_MODE = "perf_exo_frame_rate_mode";
    private static final String KEY_START_BUFFER_MS = "perf_exo_start_buffer_ms";
    private static final String KEY_REBUFFER_MS = "perf_exo_rebuffer_ms";
    private static final String KEY_PRIORITIZE_TIME = "perf_exo_prioritize_time";
    private static final String KEY_AUTO_REBUFFER_MS = "perf_exo_auto_rebuffer_ms";
    private static final String KEY_AUTO_CLEAN_STREAK = "perf_exo_auto_clean_streak";
    private static volatile int autoSessionRebufferMs = AutoRebufferPolicy.DEFAULT_REBUFFER_MS;

    private ExoPerformanceSetting() {
    }

    public static int getCodecQueueMode() {
        if (!Prefers.getPrefers().contains(KEY_CODEC_QUEUE_MODE)) return PlaybackPerformanceSetting.isCodecAsyncQueueingEnabled() ? CODEC_QUEUE_ASYNC : CODEC_QUEUE_SYNC;
        return clamp(Prefers.getInt(KEY_CODEC_QUEUE_MODE, CODEC_QUEUE_AUTO), CODEC_QUEUE_AUTO, CODEC_QUEUE_SYNC);
    }

    public static void putCodecQueueMode(int value) {
        Prefers.put(KEY_CODEC_QUEUE_MODE, clamp(value, CODEC_QUEUE_AUTO, CODEC_QUEUE_SYNC));
        PlaybackPerformanceSetting.markCustom();
    }

    public static String getCodecQueueText() {
        return switch (getCodecQueueMode()) {
            case CODEC_QUEUE_ASYNC -> "异步";
            case CODEC_QUEUE_SYNC -> "同步";
            default -> "自动";
        };
    }

    public static int getFrameRateMode() {
        return clamp(Prefers.getInt(KEY_FRAME_RATE_MODE, FRAME_RATE_SEAMLESS), FRAME_RATE_OFF, FRAME_RATE_SEAMLESS);
    }

    public static void putFrameRateMode(int value) {
        Prefers.put(KEY_FRAME_RATE_MODE, clamp(value, FRAME_RATE_OFF, FRAME_RATE_SEAMLESS));
        PlaybackPerformanceSetting.markCustom();
    }

    public static int getFrameRateStrategy() {
        return switch (getFrameRateMode()) {
            case FRAME_RATE_OFF -> C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF;
            default -> C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS;
        };
    }

    public static String getFrameRateText() {
        return switch (getFrameRateMode()) {
            case FRAME_RATE_OFF -> "关闭";
            default -> "仅无缝";
        };
    }

    public static int getStartBufferMs() {
        return normalizeStart(Prefers.getInt(KEY_START_BUFFER_MS, startBufferForPreset(PlaybackPerformanceSetting.PROFILE_RECOMMENDED)));
    }

    public static void putStartBufferMs(int value) {
        Prefers.put(KEY_START_BUFFER_MS, normalizeStart(value));
        PlaybackPerformanceSetting.markCustom();
    }

    public static int nextStartBufferMs() {
        return switch (getStartBufferMs()) {
            case 500 -> 1_000;
            case 1_000 -> 1_500;
            case 1_500 -> 2_000;
            case 2_000 -> 3_000;
            default -> 500;
        };
    }

    public static int getRebufferMs() {
        if (PlaybackPerformanceSetting.isAuto(PlayerSetting.EXO)) return AutoRebufferPolicy.normalize(autoSessionRebufferMs);
        return normalizeRebuffer(Prefers.getInt(KEY_REBUFFER_MS, rebufferForPreset(PlaybackPerformanceSetting.PROFILE_RECOMMENDED)));
    }

    public static void putRebufferMs(int value) {
        Prefers.put(KEY_REBUFFER_MS, normalizeRebuffer(value));
        PlaybackPerformanceSetting.markCustom();
    }

    public static int nextRebufferMs() {
        return switch (getRebufferMs()) {
            case 1_000 -> 2_000;
            case 2_000 -> 3_000;
            case 3_000 -> 5_000;
            case 5_000 -> 8_000;
            case 8_000 -> 10_000;
            case 10_000 -> 15_000;
            default -> 1_000;
        };
    }

    public static boolean isPrioritizeTime() {
        return Prefers.getBoolean(KEY_PRIORITIZE_TIME, prioritizeTimeForPreset(PlaybackPerformanceSetting.PROFILE_RECOMMENDED));
    }

    public static void putPrioritizeTime(boolean value) {
        Prefers.put(KEY_PRIORITIZE_TIME, value);
        PlaybackPerformanceSetting.markCustom();
    }

    public static void applyRecommended() {
        Prefers.put(KEY_CODEC_QUEUE_MODE, CODEC_QUEUE_AUTO);
        Prefers.put(KEY_FRAME_RATE_MODE, FRAME_RATE_SEAMLESS);
        applyStartBufferPreset(PlaybackPerformanceSetting.PROFILE_RECOMMENDED);
        applyRebufferPreset(PlaybackPerformanceSetting.PROFILE_RECOMMENDED);
        applyPrioritizeTimePreset(PlaybackPerformanceSetting.PROFILE_RECOMMENDED);
    }

    public static void applyAuto() {
        Prefers.put(KEY_CODEC_QUEUE_MODE, CODEC_QUEUE_AUTO);
        Prefers.put(KEY_FRAME_RATE_MODE, FRAME_RATE_SEAMLESS);
        applyStartBufferPreset(PlaybackPerformanceSetting.PROFILE_AUTO);
        applyRebufferPreset(PlaybackPerformanceSetting.PROFILE_AUTO);
        applyPrioritizeTimePreset(PlaybackPerformanceSetting.PROFILE_AUTO);
        resetAutoAdaptiveValues();
    }

    public static void recordAutoSession(int rebufferCount, long rebufferTotalMs, long positionMs, long mediaBitrate, long bandwidthEstimate) {
        if (!PlaybackPerformanceSetting.isAuto(PlayerSetting.EXO)) return;
        AutoRebufferPolicy.Result result = AutoRebufferPolicy.resolve(getAutoRebufferMs(), Prefers.getInt(KEY_AUTO_CLEAN_STREAK), rebufferCount, rebufferTotalMs, positionMs, mediaBitrate, bandwidthEstimate);
        Prefers.put(KEY_AUTO_REBUFFER_MS, result.rebufferMs());
        Prefers.put(KEY_AUTO_CLEAN_STREAK, result.cleanStreak());
    }

    public static void beginAutoSession() {
        autoSessionRebufferMs = getAutoRebufferMs();
    }

    public static int getAutoSessionRebufferMs() {
        return AutoRebufferPolicy.normalize(autoSessionRebufferMs);
    }

    public static int getAutoSessionStartBufferMs() {
        return AutoRebufferPolicy.startBufferMs(autoSessionRebufferMs);
    }

    static int getAutoRebufferMs() {
        return AutoRebufferPolicy.normalize(Prefers.getInt(KEY_AUTO_REBUFFER_MS, AutoRebufferPolicy.DEFAULT_REBUFFER_MS));
    }

    private static void resetAutoAdaptiveValues() {
        Prefers.put(KEY_AUTO_REBUFFER_MS, AutoRebufferPolicy.DEFAULT_REBUFFER_MS);
        Prefers.put(KEY_AUTO_CLEAN_STREAK, 0);
        autoSessionRebufferMs = AutoRebufferPolicy.DEFAULT_REBUFFER_MS;
    }

    public static void applyCompatible() {
        Prefers.put(KEY_CODEC_QUEUE_MODE, CODEC_QUEUE_SYNC);
        Prefers.put(KEY_FRAME_RATE_MODE, FRAME_RATE_OFF);
        applyStartBufferPreset(PlaybackPerformanceSetting.PROFILE_COMPATIBLE);
        applyRebufferPreset(PlaybackPerformanceSetting.PROFILE_COMPATIBLE);
        applyPrioritizeTimePreset(PlaybackPerformanceSetting.PROFILE_COMPATIBLE);
    }

    public static void applyLightweight() {
        Prefers.put(KEY_CODEC_QUEUE_MODE, CODEC_QUEUE_AUTO);
        Prefers.put(KEY_FRAME_RATE_MODE, FRAME_RATE_SEAMLESS);
        applyStartBufferPreset(PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT);
        applyRebufferPreset(PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT);
        applyPrioritizeTimePreset(PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT);
    }

    static void applyStartBufferPreset(int profile) {
        Prefers.put(KEY_START_BUFFER_MS, startBufferForPreset(profile));
    }

    static int startBufferForPreset(int profile) {
        return switch (profile) {
            case PlaybackPerformanceSetting.PROFILE_COMPATIBLE -> 2_000;
            case PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT -> 1_000;
            default -> 1_500;
        };
    }

    static void applyRebufferPreset(int profile) {
        Prefers.put(KEY_REBUFFER_MS, rebufferForPreset(profile));
    }

    static int rebufferForPreset(int profile) {
        return switch (profile) {
            case PlaybackPerformanceSetting.PROFILE_COMPATIBLE -> 5_000;
            case PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT -> 2_000;
            default -> 3_000;
        };
    }

    static void applyPrioritizeTimePreset(int profile) {
        Prefers.put(KEY_PRIORITIZE_TIME, prioritizeTimeForPreset(profile));
    }

    static boolean prioritizeTimeForPreset(int profile) {
        return false;
    }

    private static int normalizeStart(int value) {
        if (value <= 500) return 500;
        if (value <= 1_000) return 1_000;
        if (value <= 1_500) return 1_500;
        if (value <= 2_000) return 2_000;
        return 3_000;
    }

    private static int normalizeRebuffer(int value) {
        if (value <= 1_000) return 1_000;
        if (value <= 2_000) return 2_000;
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
