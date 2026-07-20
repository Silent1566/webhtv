package com.fongmi.android.tv.player.exo;

import com.fongmi.android.tv.player.PlaybackRoute;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoPreloadPolicyTest {

    @Test
    public void startsWithConservativeSingleThreadBaseline() {
        AutoPreloadPolicy.Decision decision = evaluate(new AutoPreloadPolicy(), 0, PlaybackRoute.DIRECT_REMOTE_HTTP, 8_000, 10, 0, 0, false);
        assertEquals(1, decision.threads());
        assertEquals(20_000, decision.durationMs());
        assertTrue(decision.enabled());
    }

    @Test
    public void moderateHeadroomUsesShortDegradedRange() {
        AutoPreloadPolicy.Decision decision = evaluate(new AutoPreloadPolicy(), 0, PlaybackRoute.DIRECT_REMOTE_HTTP, 10_000, 10, 15, 0, false);
        assertEquals(1, decision.threads());
        assertEquals(10_000, decision.durationMs());
    }

    @Test
    public void disruptionPausesThenResumesSingleThread() {
        AutoPreloadPolicy policy = new AutoPreloadPolicy();
        policy.disrupt(1_000);
        assertFalse(evaluate(policy, 10_999, PlaybackRoute.DIRECT_REMOTE_HTTP, 20_000, 10, 30, 0, false).enabled());
        assertEquals(1, evaluate(policy, 11_000, PlaybackRoute.DIRECT_REMOTE_HTTP, 20_000, 10, 30, 0, false).threads());
    }

    @Test
    public void fastModeRequiresSustainedBufferAndBandwidthHeadroom() {
        AutoPreloadPolicy policy = new AutoPreloadPolicy();
        assertEquals(1, evaluate(policy, 0, PlaybackRoute.DIRECT_REMOTE_HTTP, 20_000, 10, 30, 0, false).threads());
        assertEquals(1, evaluate(policy, 29_999, PlaybackRoute.DIRECT_REMOTE_HTTP, 20_000, 10, 30, 0, false).threads());
        AutoPreloadPolicy.Decision fast = evaluate(policy, 30_000, PlaybackRoute.DIRECT_REMOTE_HTTP, 20_000, 10, 30, 0, false);
        assertEquals(2, fast.threads());
        assertEquals(30_000, fast.durationMs());
    }

    @Test
    public void externalLoopbackNeverExceedsOneThread() {
        AutoPreloadPolicy policy = new AutoPreloadPolicy();
        evaluate(policy, 0, PlaybackRoute.EXTERNAL_LOOPBACK_PROXY, 30_000, 10, 50, 0, false);
        assertEquals(1, evaluate(policy, 60_000, PlaybackRoute.EXTERNAL_LOOPBACK_PROXY, 30_000, 10, 50, 0, false).threads());
    }

    @Test
    public void weakBandwidthImmediatelyPausesPreload() {
        AutoPreloadPolicy policy = new AutoPreloadPolicy();
        assertFalse(evaluate(policy, 0, PlaybackRoute.DIRECT_REMOTE_HTTP, 20_000, 10, 11, 0, false).enabled());
    }

    @Test
    public void fastModeFallsBackBeforePlaybackIsAtRisk() {
        AutoPreloadPolicy policy = new AutoPreloadPolicy();
        evaluate(policy, 0, PlaybackRoute.DIRECT_REMOTE_HTTP, 20_000, 10, 30, 0, false);
        assertEquals(2, evaluate(policy, 30_000, PlaybackRoute.DIRECT_REMOTE_HTTP, 20_000, 10, 30, 0, false).threads());
        assertEquals(1, evaluate(policy, 35_000, PlaybackRoute.DIRECT_REMOTE_HTTP, 11_999, 10, 30, 0, false).threads());
    }

    private static AutoPreloadPolicy.Decision evaluate(AutoPreloadPolicy policy, long nowMs, PlaybackRoute route, long bufferedMs, long bitrateMbps, long bandwidthMbps, int rebufferCount, boolean loading) {
        return policy.evaluate(nowMs, route, bufferedMs, bitrateMbps * 1_000_000, bandwidthMbps * 1_000_000, rebufferCount, loading);
    }
}
