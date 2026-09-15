package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HlsAdblockPipelineTest {

    @Test
    public void recognizesOnlyLoopbackCoreM3u8Proxy() {
        assertTrue(HlsAdblockPipeline.isCoreM3u8Proxy("http://127.0.0.1:9978/m3u8?url=x"));
        assertTrue(HlsAdblockPipeline.isCoreM3u8Proxy("http://localhost:9978/m3u8?url=x"));
        assertFalse(HlsAdblockPipeline.isCoreM3u8Proxy("https://example.com/m3u8?url=x"));
        assertFalse(HlsAdblockPipeline.isCoreM3u8Proxy("http://127.0.0.1:9978/mpv/playlist?id=1"));
    }

    @Test
    public void structuredMatchFinishesBeforeLegacyFallback() {
        String manifest = "#EXTM3U\n"
                + "#EXTINF:7.0,\nhttps://ads.example.com/ad.ts\n"
                + "#EXTINF:8.0,\nmain-1.ts\n"
                + "#EXTINF:8.0,\nmain-2.ts\n"
                + "#EXT-X-ENDLIST\n";
        HlsManifestCleaner.Rule rule = HlsManifestCleaner.Rule.builder()
                .hostSuffixes(List.of("ads.example.com"))
                .minimumSignals(1)
                .build();

        HlsAdblockPipeline.Outcome outcome = HlsAdblockPipeline.apply(
                "https://video.example.com/index.m3u8", manifest, List.of(rule), true);

        assertTrue(outcome.structured());
        assertFalse(outcome.manifest().contains("ad.ts"));
    }

    @Test
    public void exposesStructuredRuleCounts() {
        String manifest = "#EXTM3U\n"
                + "#EXTINF:7.0,\nhttps://ads.example.com/ad.ts\n"
                + "#EXTINF:8.0,\nmain-1.ts\n"
                + "#EXTINF:8.0,\nmain-2.ts\n"
                + "#EXTINF:8.0,\nmain-3.ts\n"
                + "#EXT-X-ENDLIST\n";
        HlsManifestCleaner.Rule rule = HlsManifestCleaner.Rule.builder()
                .id("rule-one")
                .hostSuffixes(List.of("ads.example.com"))
                .minimumSignals(1)
                .build();

        HlsAdblockPipeline.Outcome outcome = HlsAdblockPipeline.apply(
                "https://video.example.com/index.m3u8", manifest, List.of(rule), true);

        assertTrue(outcome.structured());
        assertEquals(Map.of("rule-one", 1L), outcome.ruleCounts());
    }
}
