package com.fongmi.android.tv.player.exo;

import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;

final class ExoLoadControlPolicy {

    private static final int MIN_BUFFER_MS = 15_000;
    private static final int MAX_BUFFER_MS = 30_000;

    private ExoLoadControlPolicy() {
    }

    static BufferDurations resolve(int profile, int bufferLevel) {
        return switch (profile) {
            case PlaybackPerformanceSetting.PROFILE_RECOMMENDED, PlaybackPerformanceSetting.PROFILE_AUTO -> new BufferDurations(30_000, 60_000);
            case PlaybackPerformanceSetting.PROFILE_COMPATIBLE -> new BufferDurations(20_000, 60_000);
            case PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT -> new BufferDurations(15_000, 30_000);
            default -> custom(bufferLevel);
        };
    }

    private static BufferDurations custom(int bufferLevel) {
        int level = Math.clamp(bufferLevel, 1, 10);
        int minBufferMs = MIN_BUFFER_MS + (level - 1) * (MAX_BUFFER_MS - MIN_BUFFER_MS) / 9;
        return new BufferDurations(minBufferMs, minBufferMs * 2);
    }

    record BufferDurations(int minBufferMs, int maxBufferMs) {
    }
}
