package com.fongmi.android.tv.setting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExoLoadControlPresetTest {

    @Test
    public void fixedProfilesUseAuditedBufferCapacity() {
        assertPreset(PlaybackPerformanceSetting.PROFILE_RECOMMENDED, 10, 2);
        assertPreset(PlaybackPerformanceSetting.PROFILE_AUTO, 10, 2);
        assertPreset(PlaybackPerformanceSetting.PROFILE_COMPATIBLE, 4, 1);
        assertPreset(PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT, 1, 1);
    }

    @Test
    public void migrationPreservesCustomLoadControlValues() {
        assertTrue(PlaybackPerformanceSetting.shouldMigrateExoLoadControl(PlaybackPerformanceSetting.PROFILE_RECOMMENDED));
        assertTrue(PlaybackPerformanceSetting.shouldMigrateExoLoadControl(PlaybackPerformanceSetting.PROFILE_COMPATIBLE));
        assertTrue(PlaybackPerformanceSetting.shouldMigrateExoLoadControl(PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT));
        assertTrue(PlaybackPerformanceSetting.shouldMigrateExoLoadControl(PlaybackPerformanceSetting.PROFILE_AUTO));
        assertFalse(PlaybackPerformanceSetting.shouldMigrateExoLoadControl(PlaybackPerformanceSetting.PROFILE_CUSTOM));
    }

    private static void assertPreset(int profile, int buffer, int bytesOption) {
        assertEquals(buffer, KernelPerformanceSetting.exoBufferForPreset(profile));
        assertEquals(bytesOption, KernelPerformanceSetting.exoBufferBytesOptionForPreset(profile));
        assertFalse(ExoPerformanceSetting.prioritizeTimeForPreset(profile));
    }
}
