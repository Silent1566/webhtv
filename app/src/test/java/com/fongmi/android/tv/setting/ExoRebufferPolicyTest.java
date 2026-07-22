package com.fongmi.android.tv.setting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExoRebufferPolicyTest {

    @Test
    public void fixedProfilesUseIndependentRecoveryThresholds() {
        assertEquals(3_000, ExoPerformanceSetting.rebufferForPreset(PlaybackPerformanceSetting.PROFILE_RECOMMENDED));
        assertEquals(3_000, ExoPerformanceSetting.rebufferForPreset(PlaybackPerformanceSetting.PROFILE_AUTO));
        assertEquals(5_000, ExoPerformanceSetting.rebufferForPreset(PlaybackPerformanceSetting.PROFILE_COMPATIBLE));
        assertEquals(2_000, ExoPerformanceSetting.rebufferForPreset(PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT));
    }

    @Test
    public void fixedProfilesCanRecoverWhenByteCapacityIsReached() {
        assertFalse(ExoPerformanceSetting.prioritizeTimeForPreset(PlaybackPerformanceSetting.PROFILE_RECOMMENDED));
        assertFalse(ExoPerformanceSetting.prioritizeTimeForPreset(PlaybackPerformanceSetting.PROFILE_COMPATIBLE));
        assertFalse(ExoPerformanceSetting.prioritizeTimeForPreset(PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT));
        assertFalse(ExoPerformanceSetting.prioritizeTimeForPreset(PlaybackPerformanceSetting.PROFILE_AUTO));
    }

    @Test
    public void migrationUpdatesOnlyNonCustomProfiles() {
        assertTrue(PlaybackPerformanceSetting.shouldMigrateExoRebuffer(PlaybackPerformanceSetting.PROFILE_RECOMMENDED));
        assertTrue(PlaybackPerformanceSetting.shouldMigrateExoRebuffer(PlaybackPerformanceSetting.PROFILE_COMPATIBLE));
        assertTrue(PlaybackPerformanceSetting.shouldMigrateExoRebuffer(PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT));
        assertTrue(PlaybackPerformanceSetting.shouldMigrateExoRebuffer(PlaybackPerformanceSetting.PROFILE_AUTO));
        assertFalse(PlaybackPerformanceSetting.shouldMigrateExoRebuffer(PlaybackPerformanceSetting.PROFILE_CUSTOM));
    }
}
