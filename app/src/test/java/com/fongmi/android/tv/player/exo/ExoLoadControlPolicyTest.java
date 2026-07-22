package com.fongmi.android.tv.player.exo;

import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ExoLoadControlPolicyTest {

    @Test
    public void fixedProfilesUseAuditedBufferDurations() {
        assertDurations(PlaybackPerformanceSetting.PROFILE_RECOMMENDED, 30_000, 60_000);
        assertDurations(PlaybackPerformanceSetting.PROFILE_AUTO, 30_000, 60_000);
        assertDurations(PlaybackPerformanceSetting.PROFILE_COMPATIBLE, 20_000, 60_000);
        assertDurations(PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT, 15_000, 30_000);
    }

    @Test
    public void customLevelScalesFromFifteenToThirtySeconds() {
        assertDurations(PlaybackPerformanceSetting.PROFILE_CUSTOM, 1, 15_000, 30_000);
        assertDurations(PlaybackPerformanceSetting.PROFILE_CUSTOM, 4, 20_000, 40_000);
        assertDurations(PlaybackPerformanceSetting.PROFILE_CUSTOM, 10, 30_000, 60_000);
    }

    private static void assertDurations(int profile, int minMs, int maxMs) {
        assertDurations(profile, 1, minMs, maxMs);
    }

    private static void assertDurations(int profile, int level, int minMs, int maxMs) {
        ExoLoadControlPolicy.BufferDurations durations = ExoLoadControlPolicy.resolve(profile, level);
        assertEquals(minMs, durations.minBufferMs());
        assertEquals(maxMs, durations.maxBufferMs());
    }
}
