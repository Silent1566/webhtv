package com.fongmi.android.tv.setting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvRebufferPolicyTest {

    @Test
    public void fixedProfilesUseOneToThreeSecondRecoveryRange() {
        assertEquals(2_000, MpvPerformanceSetting.rebufferForPreset(PlaybackPerformanceSetting.PROFILE_AUTO));
        assertEquals(2_000, MpvPerformanceSetting.rebufferForPreset(PlaybackPerformanceSetting.PROFILE_RECOMMENDED));
        assertEquals(3_000, MpvPerformanceSetting.rebufferForPreset(PlaybackPerformanceSetting.PROFILE_COMPATIBLE));
        assertEquals(1_000, MpvPerformanceSetting.rebufferForPreset(PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT));
    }

    @Test
    public void customRangeKeepsOnlySupportedRecoverySteps() {
        assertEquals(1_000, MpvPerformanceSetting.normalizeRebuffer(500));
        assertEquals(2_000, MpvPerformanceSetting.normalizeRebuffer(1_500));
        assertEquals(3_000, MpvPerformanceSetting.normalizeRebuffer(2_500));
        assertEquals(5_000, MpvPerformanceSetting.normalizeRebuffer(4_000));
        assertEquals(8_000, MpvPerformanceSetting.normalizeRebuffer(7_000));
        assertEquals(10_000, MpvPerformanceSetting.normalizeRebuffer(9_000));
        assertEquals(15_000, MpvPerformanceSetting.normalizeRebuffer(12_000));
    }

    @Test
    public void migrationUpdatesOnlyNonCustomProfiles() {
        assertTrue(PlaybackPerformanceSetting.shouldMigrateMpvRebuffer(PlaybackPerformanceSetting.PROFILE_AUTO));
        assertTrue(PlaybackPerformanceSetting.shouldMigrateMpvRebuffer(PlaybackPerformanceSetting.PROFILE_RECOMMENDED));
        assertTrue(PlaybackPerformanceSetting.shouldMigrateMpvRebuffer(PlaybackPerformanceSetting.PROFILE_COMPATIBLE));
        assertTrue(PlaybackPerformanceSetting.shouldMigrateMpvRebuffer(PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT));
        assertFalse(PlaybackPerformanceSetting.shouldMigrateMpvRebuffer(PlaybackPerformanceSetting.PROFILE_CUSTOM));
    }
}
