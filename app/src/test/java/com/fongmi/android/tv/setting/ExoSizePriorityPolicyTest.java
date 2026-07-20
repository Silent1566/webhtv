package com.fongmi.android.tv.setting;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExoSizePriorityPolicyTest {

    @Test
    public void presetsStrictlyRespectTargetBufferCapacity() {
        assertFalse(ExoPerformanceSetting.prioritizeTimeForPreset(PlaybackPerformanceSetting.PROFILE_RECOMMENDED));
        assertFalse(ExoPerformanceSetting.prioritizeTimeForPreset(PlaybackPerformanceSetting.PROFILE_COMPATIBLE));
        assertFalse(ExoPerformanceSetting.prioritizeTimeForPreset(PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT));
    }

    @Test
    public void migrationUpdatesOnlyNonCustomProfiles() {
        assertTrue(PlaybackPerformanceSetting.shouldMigrateExoSizePriority(PlaybackPerformanceSetting.PROFILE_RECOMMENDED));
        assertTrue(PlaybackPerformanceSetting.shouldMigrateExoSizePriority(PlaybackPerformanceSetting.PROFILE_COMPATIBLE));
        assertTrue(PlaybackPerformanceSetting.shouldMigrateExoSizePriority(PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT));
        assertTrue(PlaybackPerformanceSetting.shouldMigrateExoSizePriority(PlaybackPerformanceSetting.PROFILE_AUTO));
        assertFalse(PlaybackPerformanceSetting.shouldMigrateExoSizePriority(PlaybackPerformanceSetting.PROFILE_CUSTOM));
    }
}
