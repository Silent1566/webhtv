package com.fongmi.android.tv.setting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ExoStartBufferPolicyTest {

    @Test
    public void recommendedProfileKeepsFastStartThreshold() {
        assertEquals(1_500, ExoPerformanceSetting.startBufferForPreset(PlaybackPerformanceSetting.PROFILE_RECOMMENDED));
        assertEquals(1_500, ExoPerformanceSetting.startBufferForPreset(PlaybackPerformanceSetting.PROFILE_AUTO));
    }

    @Test
    public void otherProfilesKeepIndependentStartThresholds() {
        assertEquals(2_000, ExoPerformanceSetting.startBufferForPreset(PlaybackPerformanceSetting.PROFILE_COMPATIBLE));
        assertEquals(1_000, ExoPerformanceSetting.startBufferForPreset(PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT));
    }
}
