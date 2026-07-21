package com.fongmi.android.tv.player.mpv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MpvAutoOutputPolicyTest {

    @Test
    public void acceptsUltraWideFourKClassVideoOnTvHardwareDecode() {
        assertTrue(MpvAutoOutputPolicy.evaluate(3840, 1632, true, true, false, false, false).eligible());
    }

    @Test
    public void rejectsOrdinaryFullHdVideo() {
        assertFalse(MpvAutoOutputPolicy.evaluate(1920, 1080, true, true, false, false, false).eligible());
    }

    @Test
    public void rejectsFeaturesThatNeedGpuComposition() {
        assertFalse(MpvAutoOutputPolicy.evaluate(3840, 2160, true, true, true, false, false).eligible());
        assertFalse(MpvAutoOutputPolicy.evaluate(3840, 2160, true, true, false, true, false).eligible());
        assertFalse(MpvAutoOutputPolicy.evaluate(3840, 2160, true, true, false, false, true).eligible());
    }

    @Test
    public void evaluatesFourKBeforeTracksAreComplete() {
        assertTrue(MpvAutoOutputPolicy.canEvaluateWithoutTracks(3840, 1606, false));
    }

    @Test
    public void waitsForTracksWhenEarlyDecisionCouldLoseFeatures() {
        assertFalse(MpvAutoOutputPolicy.canEvaluateWithoutTracks(1920, 1080, false));
        assertFalse(MpvAutoOutputPolicy.canEvaluateWithoutTracks(3840, 2160, true));
    }

    @Test
    public void ignoresAutoSelectedEmbeddedSubtitleButHonorsExplicitSubtitleDemand() {
        assertFalse(MpvAutoOutputPolicy.requiresGpuSubtitle(false, false));
        assertTrue(MpvAutoOutputPolicy.requiresGpuSubtitle(true, false));
        assertTrue(MpvAutoOutputPolicy.requiresGpuSubtitle(false, true));
    }

    @Test
    public void keepsDirectOutputWhenNextItemRemainsEligible() {
        assertEquals(MpvAutoOutputPolicy.Transition.KEEP_SURFACE_DIRECT, MpvAutoOutputPolicy.transition(true, true));
    }

    @Test
    public void entersDirectOutputWhenGpuItemIsEligible() {
        assertEquals(MpvAutoOutputPolicy.Transition.ENTER_SURFACE_DIRECT, MpvAutoOutputPolicy.transition(true, false));
    }

    @Test
    public void leavesDirectOutputWhenNextItemIsNotEligible() {
        assertEquals(MpvAutoOutputPolicy.Transition.LEAVE_SURFACE_DIRECT, MpvAutoOutputPolicy.transition(false, true));
    }

    @Test
    public void keepsGpuOutputWhenNextItemIsNotEligible() {
        assertEquals(MpvAutoOutputPolicy.Transition.KEEP_GPU, MpvAutoOutputPolicy.transition(false, false));
    }
}
