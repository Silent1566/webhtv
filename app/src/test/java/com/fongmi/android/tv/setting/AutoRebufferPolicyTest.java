package com.fongmi.android.tv.setting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AutoRebufferPolicyTest {

    @Test
    public void startsAtThreeSecondsWithoutEnoughHistory() {
        assertResult(3_000, 0, AutoRebufferPolicy.resolve(3_000, 0, 0, 0, 60_000, 10_000_000, 30_000_000));
    }

    @Test
    public void repeatedOrLongRebufferRaisesRecoveryQuickly() {
        assertResult(5_000, 0, AutoRebufferPolicy.resolve(3_000, 0, 2, 4_000, 300_000, 10_000_000, 20_000_000));
        assertResult(8_000, 0, AutoRebufferPolicy.resolve(3_000, 0, 3, 6_000, 300_000, 10_000_000, 20_000_000));
        assertResult(8_000, 0, AutoRebufferPolicy.resolve(3_000, 0, 1, 15_000, 300_000, 10_000_000, 20_000_000));
    }

    @Test
    public void lowBandwidthHeadroomRaisesRecovery() {
        assertResult(8_000, 0, AutoRebufferPolicy.resolve(3_000, 0, 0, 0, 300_000, 10_000_000, 11_000_000));
        assertResult(5_000, 0, AutoRebufferPolicy.resolve(3_000, 0, 0, 0, 300_000, 10_000_000, 13_000_000));
    }

    @Test
    public void recoveryOnlyLowersAfterTwoCleanSessions() {
        AutoRebufferPolicy.Result first = AutoRebufferPolicy.resolve(8_000, 0, 0, 0, 300_000, 10_000_000, 25_000_000);
        assertResult(8_000, 1, first);
        AutoRebufferPolicy.Result second = AutoRebufferPolicy.resolve(first.rebufferMs(), first.cleanStreak(), 0, 0, 300_000, 10_000_000, 25_000_000);
        assertResult(5_000, 0, second);
    }

    @Test
    public void startupBufferFollowsLockedSessionRecoveryLevel() {
        assertEquals(1_500, AutoRebufferPolicy.startBufferMs(2_000));
        assertEquals(1_500, AutoRebufferPolicy.startBufferMs(3_000));
        assertEquals(3_000, AutoRebufferPolicy.startBufferMs(5_000));
        assertEquals(5_000, AutoRebufferPolicy.startBufferMs(8_000));
    }

    private static void assertResult(int rebufferMs, int cleanStreak, AutoRebufferPolicy.Result result) {
        assertEquals(rebufferMs, result.rebufferMs());
        assertEquals(cleanStreak, result.cleanStreak());
    }
}
