package com.fongmi.android.tv.player.diagnostic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class PanNetworkDiagnosticRunnerTest {

    @Test
    public void explicitThreadSelectionIsSanitizedAndSorted() {
        assertEquals(List.of(1, 8, 12, 256), PanBenchmarkPlan.sanitizeThreads(List.of(12, 8, 1, 999)));
    }

    @Test
    public void explicitThreadSelectionDoesNotInjectFixedComparisonValues() {
        assertEquals(List.of(6, 12), PanBenchmarkPlan.sanitizeThreads(List.of(12, 6)));
    }

    @Test
    public void replacesThreadWithoutExposingOrDroppingOtherQueryParameters() {
        String value = PanNetworkDiagnosticRunner.withThread("http://127.0.0.1:9978/pvideo?thread=4&token=secret", 16);
        assertTrue(value.contains("thread=16"));
        assertTrue(value.contains("token=secret"));
    }

    @Test
    public void estimatesAverageBitrateFromFileAndDuration() {
        assertEquals(8_000_000L, PanNetworkDiagnosticRunner.requiredBitsPerSecond(1_000_000_000L, 1_000_000L, 0));
    }

    @Test
    public void directBaiduUrlUsesPlaybackHostForProviderRecognition() {
        PanEndpoint endpoint = PanEndpointParser.parse("https://d.pcs.baidu.com/file/example", Collections.emptyMap());
        assertEquals(PanProvider.BAIDU, endpoint.provider());
    }

    @Test
    public void formatsPerRoundElapsedTime() {
        assertEquals("8秒", PanNetworkDiagnosticRunner.formatDuration(7_600));
        assertEquals("1分05秒", PanNetworkDiagnosticRunner.formatDuration(65_000));
    }

    @Test
    public void diagnosticModesHaveBoundedPerRoundSamplingTime() {
        assertEquals(8_000L, PanBenchmarkPlan.roundTimeLimitMs(PanBenchmarkPlan.Mode.QUICK));
        assertEquals(10_000L, PanBenchmarkPlan.roundTimeLimitMs(PanBenchmarkPlan.Mode.STANDARD));
        assertEquals(12_000L, PanBenchmarkPlan.roundTimeLimitMs(PanBenchmarkPlan.Mode.DEEP));
        assertEquals(1, PanBenchmarkPlan.repeats(PanBenchmarkPlan.Mode.QUICK));
        assertEquals(2, PanBenchmarkPlan.repeats(PanBenchmarkPlan.Mode.STANDARD));
        assertEquals(3, PanBenchmarkPlan.repeats(PanBenchmarkPlan.Mode.DEEP));
        assertEquals(32, PanBenchmarkPlan.directConcurrency(256));
    }

    @Test
    public void measurementRoundsUseSeparatedRanges() {
        long length = 1_000_000_000L;
        long budget = 10_000_000L;
        long first = PanNetworkDiagnosticRunner.isolatedRangePosition(0, length, budget, 0, 3);
        long second = PanNetworkDiagnosticRunner.isolatedRangePosition(0, length, budget, 1, 3);
        long third = PanNetworkDiagnosticRunner.isolatedRangePosition(0, length, budget, 2, 3);
        assertTrue(second - first > budget);
        assertTrue(third - second > budget);
    }

    @Test
    public void transientRetryMovesToAnotherValidRange() {
        long length = 1_000_000_000L;
        long budget = 10_000_000L;
        long retry = PanNetworkDiagnosticRunner.retryRangePosition(500_000_000L, length, budget);
        assertTrue(retry != 500_000_000L);
        assertTrue(retry >= 0 && retry <= length - budget);
    }
}
