package com.fongmi.android.tv.player.diagnostic;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PanBenchmarkPlanTest {

    @Test
    public void defaultsToEightButCanIncludeCurrentHigherValue() {
        assertEquals(List.of(1, 2, 4, 8), PanBenchmarkPlan.defaultSweep(32));
        assertEquals(List.of(1, 2, 4, 8, 32), PanBenchmarkPlan.sweep(8, 32, true));
    }

    @Test
    public void acceptsArbitraryUserValueUpTo256() {
        assertEquals(List.of(1, 7, 57, 256), PanBenchmarkPlan.sanitizeThreads(List.of(57, 7, 999, 1)));
        assertEquals(256, PanBenchmarkPlan.normalizeThreads(999));
    }

    @Test
    public void highConcurrencyReceivesEnoughPerWorkerBudget() {
        long budget = PanBenchmarkPlan.roundBudgetBytes(100_000_000L, 256, PanBenchmarkPlan.Mode.STANDARD);
        assertTrue(budget >= 256L * 4L * 1024L * 1024L);
    }

    @Test
    public void standardBudgetTracksRequiredBitrate() {
        long budget = PanBenchmarkPlan.roundBudgetBytes(100_000_000L, 8, PanBenchmarkPlan.Mode.STANDARD);
        assertEquals(125_000_000L, budget);
    }
}
