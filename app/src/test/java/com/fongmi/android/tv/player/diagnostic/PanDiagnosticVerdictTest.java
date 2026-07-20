package com.fongmi.android.tv.player.diagnostic;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PanDiagnosticVerdictTest {

    @Test
    public void attributesSlowProviderWhenBaselineIsFast() {
        PanDiagnosticVerdict.Result result = resolve(80, 200, 40, 39, 38, 4, 0);
        assertEquals(PanDiagnosticVerdict.Cause.UPSTREAM_PROVIDER, result.cause());
        assertEquals(PanDiagnosticVerdict.Confidence.HIGH, result.confidence());
    }

    @Test
    public void attributesExternalProxyLoss() {
        PanDiagnosticVerdict.Result result = resolve(80, 200, 160, 60, 58, 0, 0);
        assertEquals(PanDiagnosticVerdict.Cause.EXTERNAL_PROXY, result.cause());
    }

    @Test
    public void attributesAppDataSourceLoss() {
        PanDiagnosticVerdict.Result result = resolve(80, 200, 160, 150, 60, 0, 0);
        assertEquals(PanDiagnosticVerdict.Cause.APP_DATA_SOURCE, result.cause());
    }

    @Test
    public void largeEfficiencyLossIsNotRootCauseWhenCapacityRemainsSufficient() {
        PanDiagnosticVerdict.Result result = resolve(6, 200, 160, 68, 65, 0, 0);
        assertEquals(PanDiagnosticVerdict.Cause.SUFFICIENT, result.cause());
    }

    @Test
    public void refusesFalseCertaintyWhenEvidenceIsMissing() {
        PanDiagnosticVerdict.Result result = resolve(80, 0, 0, 0, 0, 3, 0);
        assertEquals(PanDiagnosticVerdict.Cause.INCONCLUSIVE, result.cause());
        assertEquals(PanDiagnosticVerdict.Confidence.LOW, result.confidence());
    }

    private static PanDiagnosticVerdict.Result resolve(long requiredMbps, long baselineMbps, long upstreamMbps,
                                                       long proxyMbps, long dataSourceMbps, int rebuffer, int dropped) {
        return PanDiagnosticVerdict.resolve(new PanDiagnosticVerdict.Input(mbps(requiredMbps), mbps(baselineMbps),
                mbps(upstreamMbps), mbps(upstreamMbps), mbps(proxyMbps), mbps(dataSourceMbps), rebuffer, dropped,
                PanDiagnosticVerdict.Confidence.HIGH));
    }

    private static long mbps(long value) {
        return value * 1_000_000L;
    }

    @Test
    public void singleConnectionLimitDoesNotPretendToProveProviderRootCause() {
        PanDiagnosticVerdict.Result result = resolve(79, 0, 2, 23, 74, 0, 0);
        assertEquals(PanDiagnosticVerdict.Cause.INCONCLUSIVE, result.cause());
        assertEquals(PanDiagnosticVerdict.Confidence.LOW, result.confidence());
    }

    @Test
    public void sameConcurrencyDirectComparisonCanAttributeGoLoss() {
        PanDiagnosticVerdict.Result result = resolveDetailed(80, 0, 8, 150, 55, 50, PanDiagnosticVerdict.Confidence.HIGH);
        assertEquals(PanDiagnosticVerdict.Cause.EXTERNAL_PROXY, result.cause());
        assertEquals(PanDiagnosticVerdict.Confidence.HIGH, result.confidence());
    }

    @Test
    public void stableDirectAndGoCanAttributeAppLoss() {
        PanDiagnosticVerdict.Result result = resolveDetailed(80, 0, 8, 150, 145, 55, PanDiagnosticVerdict.Confidence.HIGH);
        assertEquals(PanDiagnosticVerdict.Cause.APP_DATA_SOURCE, result.cause());
    }

    @Test
    public void quickSingleSampleCapsOtherwiseStrongConclusionToLowConfidence() {
        PanDiagnosticVerdict.Result result = resolveDetailed(80, 0, 8, 150, 145, 140, PanDiagnosticVerdict.Confidence.LOW);
        assertEquals(PanDiagnosticVerdict.Cause.SUFFICIENT, result.cause());
        assertEquals(PanDiagnosticVerdict.Confidence.LOW, result.confidence());
    }

    @Test
    public void reportsLayeredLossEvenWhenUpstreamCapacityIsAlsoInsufficient() {
        PanDiagnosticVerdict.Result result = resolveDetailed(79, 0, 20, 49, 14, 22, PanDiagnosticVerdict.Confidence.LOW);
        assertEquals(PanDiagnosticVerdict.Cause.MULTIPLE_BOTTLENECKS, result.cause());
        assertEquals(PanDiagnosticVerdict.Confidence.LOW, result.confidence());
    }

    @Test
    public void locatesInsufficientCapacityBeforeGoWithoutInventingProviderCause() {
        PanDiagnosticVerdict.Result result = resolveDetailed(80, 0, 20, 50, 45, 42, PanDiagnosticVerdict.Confidence.MEDIUM);
        assertEquals(PanDiagnosticVerdict.Cause.UPSTREAM_CAPACITY, result.cause());
        assertEquals(PanDiagnosticVerdict.Confidence.MEDIUM, result.confidence());
    }

    private static PanDiagnosticVerdict.Result resolveDetailed(long requiredMbps, long baselineMbps, long upstreamMbps,
                                                               long directMbps, long proxyMbps, long dataSourceMbps,
                                                               PanDiagnosticVerdict.Confidence evidence) {
        return PanDiagnosticVerdict.resolve(new PanDiagnosticVerdict.Input(mbps(requiredMbps), mbps(baselineMbps),
                mbps(upstreamMbps), mbps(directMbps), mbps(proxyMbps), mbps(dataSourceMbps), 0, 0, evidence));
    }
}
