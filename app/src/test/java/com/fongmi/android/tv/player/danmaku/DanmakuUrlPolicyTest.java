package com.fongmi.android.tv.player.danmaku;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DanmakuUrlPolicyTest {

    @Test
    public void classifiesStaticAndLiveSources() {
        assertEquals(DanmakuUrlPolicy.SourceType.STATIC, DanmakuUrlPolicy.classify("https://example.com/a.xml"));
        assertEquals(DanmakuUrlPolicy.SourceType.STATIC, DanmakuUrlPolicy.classify("file:///storage/emulated/0/a.xml"));
        assertEquals(DanmakuUrlPolicy.SourceType.STATIC, DanmakuUrlPolicy.classify("content://provider/a.xml"));
        assertEquals(DanmakuUrlPolicy.SourceType.LIVE, DanmakuUrlPolicy.classify("ws://127.0.0.1:5266/alllive/danmaku/ws"));
        assertEquals(DanmakuUrlPolicy.SourceType.LIVE, DanmakuUrlPolicy.classify("wss://example.com/live"));
    }

    @Test
    public void rejectsMissingHostsInvalidPortsAndUnknownSchemes() {
        assertEquals(DanmakuUrlPolicy.SourceType.UNSUPPORTED, DanmakuUrlPolicy.classify("ws:///live"));
        assertEquals(DanmakuUrlPolicy.SourceType.UNSUPPORTED, DanmakuUrlPolicy.classify("https://example.com:70000/a.xml"));
        assertEquals(DanmakuUrlPolicy.SourceType.UNSUPPORTED, DanmakuUrlPolicy.classify("wss://user:secret@example.com/live"));
        assertEquals(DanmakuUrlPolicy.SourceType.UNSUPPORTED, DanmakuUrlPolicy.classify("ftp://example.com/a.xml"));
        assertEquals(DanmakuUrlPolicy.SourceType.EMPTY, DanmakuUrlPolicy.classify("  "));
    }

    @Test
    public void upgradesSameAuthorityResultsFromHttpsApi() {
        assertEquals("https://example.com/danmaku.xml", DanmakuUrlPolicy.normalize("https://example.com/danmaku", "http://example.com/danmaku.xml"));
        assertEquals("wss://example.com/alllive/danmaku/ws?site=huya", DanmakuUrlPolicy.normalize("https://example.com/danmaku", "ws://example.com/alllive/danmaku/ws?site=huya"));
    }

    @Test
    public void doesNotUpgradeDifferentAuthorityOrLocalHttpApi() {
        assertEquals("ws://other.example.com/live", DanmakuUrlPolicy.normalize("https://example.com/danmaku", "ws://other.example.com/live"));
        assertEquals("ws://127.0.0.1:5266/live", DanmakuUrlPolicy.normalize("http://127.0.0.1:5266/danmaku", "ws://127.0.0.1:5266/live"));
    }

    @Test
    public void logSummaryKeepsRoutingEvidenceButRemovesSecrets() {
        String summary = DanmakuUrlPolicy.logSummary("wss://example.com/live?site=huya&room_id=859042&token=secret&auth=hidden&cookie=value&key=private");

        assertTrue(summary.contains("source=LIVE"));
        assertTrue(summary.contains("site=huya"));
        assertTrue(summary.contains("room_id=859042"));
        assertFalse(summary.contains("token"));
        assertFalse(summary.contains("secret"));
        assertFalse(summary.contains("auth"));
        assertFalse(summary.contains("cookie"));
        assertFalse(summary.contains("private"));
    }

    @Test
    public void recognizesLoopbackHosts() {
        assertTrue(DanmakuUrlPolicy.isLoopback("ws://127.0.0.1:5266/live"));
        assertTrue(DanmakuUrlPolicy.isLoopback("ws://localhost:5266/live"));
        assertFalse(DanmakuUrlPolicy.isLoopback("wss://example.com/live"));
    }

    @Test
    public void identifiesLoopbackAndPrivateLanHostsForDirectRouting() {
        assertTrue(DanmakuUrlPolicy.isDirectHost("127.0.0.1"));
        assertTrue(DanmakuUrlPolicy.isDirectHost("192.168.1.9"));
        assertTrue(DanmakuUrlPolicy.isDirectHost("172.16.8.2"));
        assertTrue(DanmakuUrlPolicy.isDirectHost("10.0.0.5"));
        assertTrue(DanmakuUrlPolicy.isDirectHost("fd12::1"));
        assertTrue(DanmakuUrlPolicy.isDirectHost("fe80::1"));
        assertFalse(DanmakuUrlPolicy.isDirectHost("172.32.0.1"));
        assertFalse(DanmakuUrlPolicy.isDirectHost("example.com"));
    }
}
