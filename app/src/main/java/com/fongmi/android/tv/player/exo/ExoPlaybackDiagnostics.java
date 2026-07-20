package com.fongmi.android.tv.player.exo;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;

import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;
import com.github.catvod.crawler.SpiderDebug;

final class ExoPlaybackDiagnostics {

    private ExoPlaybackDiagnostics() {
    }

    static void logLoadControl(int profile, ExoLoadControlPolicy.BufferDurations durations, ExoBufferBudget.Budget budget, int startBufferMs, int rebufferMs, int backBufferMs, boolean prioritizeTime) {
        if (!SpiderDebug.isEnabled()) return;
        SpiderDebug.log("exo-buffer", "loadControl profile=%s min=%d max=%d start=%d rebuffer=%d back=%d requestedBytes=%d heapBudgetBytes=%d effectiveBytes=%d heapLimitBytes=%d reserveBytes=%d availableAfterReserveBytes=%d memoryClassMb=%d largeMemoryClassMb=%d largeHeap=%s lowRam=%s prioritizeTime=%s",
                profileName(profile), durations.minBufferMs(), durations.maxBufferMs(), startBufferMs, rebufferMs, backBufferMs,
                budget.requestedTargetBytes(), budget.heapBudgetBytes(), budget.effectiveTargetBytes(), budget.heapLimitBytes(), budget.reservedHeadroomBytes(), budget.availableAfterReserveBytes(),
                budget.memoryClassMb(), budget.largeMemoryClassMb(), budget.largeHeap(), budget.lowRamDevice(), prioritizeTime);
    }

    static void logDefaultLoadControl(int profile) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-buffer", "loadControl profile=%s mode=media3-default", profileName(profile));
    }

    static void logTrackFormats(@Nullable Format video, @Nullable Format audio, int effectiveCapacityBytes) {
        if (!SpiderDebug.isEnabled()) return;
        long videoBitrate = formatBitrate(video);
        long audioBitrate = formatBitrate(audio);
        long totalBitrate = safeAdd(videoBitrate, audioBitrate);
        SpiderDebug.log("exo-buffer", "tracks videoBitrate=%d videoSource=%s audioBitrate=%d audioSource=%s totalBitrate=%d effectiveCapacityBytes=%d estimatedCapacityDurationMs=%d",
                videoBitrate, bitrateSource(video), audioBitrate, bitrateSource(audio), totalBitrate, effectiveCapacityBytes, capacityDurationMs(effectiveCapacityBytes, totalBitrate));
    }

    static void logPreload(String format, Object... args) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-preload", format, args);
    }

    static int formatBitrate(@Nullable Format format) {
        if (format == null) return 0;
        if (format.averageBitrate > 0) return format.averageBitrate;
        if (format.peakBitrate > 0) return format.peakBitrate;
        if (format.bitrate > 0) return format.bitrate;
        return 0;
    }

    static String bitrateSource(@Nullable Format format) {
        if (format == null) return "missing";
        if (format.averageBitrate > 0) return "average";
        if (format.peakBitrate > 0) return "peak";
        if (format.bitrate > 0) return "bitrate";
        return "unknown";
    }

    static long combinedBitrate(@Nullable Format video, @Nullable Format audio) {
        return safeAdd(formatBitrate(video), formatBitrate(audio));
    }

    static long capacityDurationMs(long capacityBytes, long bitrateBitsPerSecond) {
        if (capacityBytes <= 0 || bitrateBitsPerSecond <= 0) return 0;
        long bits = capacityBytes > Long.MAX_VALUE / 8L ? Long.MAX_VALUE : capacityBytes * 8L;
        if (bits > Long.MAX_VALUE / 1_000L) return Long.MAX_VALUE;
        return bits * 1_000L / bitrateBitsPerSecond;
    }

    static long estimateBytes(long bitrateBitsPerSecond, long durationMs) {
        if (bitrateBitsPerSecond <= 0 || durationMs <= 0) return 0;
        if (bitrateBitsPerSecond > Long.MAX_VALUE / durationMs) return Long.MAX_VALUE;
        return bitrateBitsPerSecond * durationMs / 8_000L;
    }

    private static long safeAdd(long first, long second) {
        if (first <= 0) return Math.max(0, second);
        if (second <= 0) return first;
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }

    private static String profileName(int profile) {
        return switch (profile) {
            case PlaybackPerformanceSetting.PROFILE_AUTO -> "auto";
            case PlaybackPerformanceSetting.PROFILE_COMPATIBLE -> "compatible";
            case PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT -> "lightweight";
            case PlaybackPerformanceSetting.PROFILE_CUSTOM -> "custom";
            default -> "recommended";
        };
    }
}
