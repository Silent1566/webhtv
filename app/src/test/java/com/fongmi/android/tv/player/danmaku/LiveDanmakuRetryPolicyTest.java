package com.fongmi.android.tv.player.danmaku;

import org.junit.Test;

import java.io.IOException;
import java.net.ProtocolException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLHandshakeException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LiveDanmakuRetryPolicyTest {

    @Test
    public void retriesTemporaryCloseCodesOnly() {
        assertTrue(LiveDanmakuRetryPolicy.shouldRetryClose(1001));
        assertTrue(LiveDanmakuRetryPolicy.shouldRetryClose(1006));
        assertTrue(LiveDanmakuRetryPolicy.shouldRetryClose(1013));
        assertFalse(LiveDanmakuRetryPolicy.shouldRetryClose(1000));
        assertFalse(LiveDanmakuRetryPolicy.shouldRetryClose(1002));
        assertFalse(LiveDanmakuRetryPolicy.shouldRetryClose(1008));
    }

    @Test
    public void separatesTemporaryIoFromPolicyAndTlsFailures() {
        assertTrue(LiveDanmakuRetryPolicy.shouldRetryFailure(new IOException("reset"), -1));
        assertTrue(LiveDanmakuRetryPolicy.shouldRetryFailure(new IOException("busy"), 503));
        assertTrue(LiveDanmakuRetryPolicy.shouldRetryFailure(new IOException("limited"), 429));
        assertFalse(LiveDanmakuRetryPolicy.shouldRetryFailure(new IOException("forbidden"), 403));
        assertFalse(LiveDanmakuRetryPolicy.shouldRetryFailure(new ProtocolException("bad upgrade"), -1));
        assertFalse(LiveDanmakuRetryPolicy.shouldRetryFailure(new SSLHandshakeException("cert"), -1));
        assertFalse(LiveDanmakuRetryPolicy.shouldRetryFailure(new IOException(new CertificateException("cert")), -1));
    }

    @Test
    public void fullJitterStaysWithinAttemptCapAndGlobalMaximum() {
        assertEquals(250L, LiveDanmakuRetryPolicy.nextDelayMs(0, 0d));
        assertEquals(1_000L, LiveDanmakuRetryPolicy.nextDelayMs(0, 1d));
        assertEquals(2_000L, LiveDanmakuRetryPolicy.nextDelayMs(1, 1d));
        assertEquals(30_000L, LiveDanmakuRetryPolicy.nextDelayMs(20, 1d));
    }
}
