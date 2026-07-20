package com.fongmi.android.tv.player.exo;

import androidx.media3.common.Format;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ExoPlaybackDiagnosticsTest {

    @Test
    public void bitrateUsesBestAvailableFormatField() {
        assertEquals(9_000_000, ExoPlaybackDiagnostics.formatBitrate(new Format.Builder().setAverageBitrate(9_000_000).setPeakBitrate(12_000_000).build()));
        assertEquals(12_000_000, ExoPlaybackDiagnostics.formatBitrate(new Format.Builder().setPeakBitrate(12_000_000).build()));
        assertEquals(0, ExoPlaybackDiagnostics.formatBitrate(null));
    }

    @Test
    public void bitrateSourceDistinguishesUnknownAndMissing() {
        assertEquals("average", ExoPlaybackDiagnostics.bitrateSource(new Format.Builder().setAverageBitrate(1).build()));
        assertEquals("peak", ExoPlaybackDiagnostics.bitrateSource(new Format.Builder().setPeakBitrate(1).build()));
        assertEquals("unknown", ExoPlaybackDiagnostics.bitrateSource(new Format.Builder().build()));
        assertEquals("missing", ExoPlaybackDiagnostics.bitrateSource(null));
    }

    @Test
    public void capacityAndRangeEstimatesUseBitrateWithoutOverflow() {
        assertEquals(10_737, ExoPlaybackDiagnostics.capacityDurationMs(128L * 1024 * 1024, 100_000_000));
        assertEquals(125_000_000, ExoPlaybackDiagnostics.estimateBytes(100_000_000, 10_000));
        assertEquals(0, ExoPlaybackDiagnostics.capacityDurationMs(128, 0));
        assertEquals(0, ExoPlaybackDiagnostics.estimateBytes(0, 10_000));
    }
}
