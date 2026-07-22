package com.fongmi.android.tv.setting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PreloadDefaultsPolicyTest {

    @Test
    public void allFixedProfilesUseConservativePreloadDefaults() {
        for (int profile : new int[]{PlaybackPerformanceSetting.PROFILE_AUTO, PlaybackPerformanceSetting.PROFILE_RECOMMENDED, PlaybackPerformanceSetting.PROFILE_COMPATIBLE, PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT}) {
            assertEquals(1, KernelPerformanceSetting.preloadThreadsForPreset(profile));
            assertEquals(20, KernelPerformanceSetting.preloadTimeForPreset(profile));
        }
    }

    @Test
    public void migrationPreservesCustomProfileValues() {
        assertTrue(PlaybackPerformanceSetting.shouldMigratePreloadDefaults(PlaybackPerformanceSetting.PROFILE_RECOMMENDED));
        assertTrue(PlaybackPerformanceSetting.shouldMigratePreloadDefaults(PlaybackPerformanceSetting.PROFILE_COMPATIBLE));
        assertTrue(PlaybackPerformanceSetting.shouldMigratePreloadDefaults(PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT));
        assertTrue(PlaybackPerformanceSetting.shouldMigratePreloadDefaults(PlaybackPerformanceSetting.PROFILE_AUTO));
        assertFalse(PlaybackPerformanceSetting.shouldMigratePreloadDefaults(PlaybackPerformanceSetting.PROFILE_CUSTOM));
    }
}
