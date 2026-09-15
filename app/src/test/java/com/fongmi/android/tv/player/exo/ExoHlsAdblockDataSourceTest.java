package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ExoHlsAdblockDataSourceTest {

    @Test
    public void identifiesOnlyHlsManifestRequests() {
        assertTrue(ExoHlsAdblockDataSource.isManifestUrl("https://example.test/live/index.m3u8"));
        assertTrue(ExoHlsAdblockDataSource.isManifestUrl("https://example.test/live/playlist.m3u8?token=1"));
        assertFalse(ExoHlsAdblockDataSource.isManifestUrl("https://example.test/live/segment.ts"));
        assertFalse(ExoHlsAdblockDataSource.isManifestUrl("https://example.test/live/key.bin"));
    }

    @Test
    public void changedOutcomeProducesPlaybackNoticeText() {
        String manifest = "#EXTM3U\n#EXTINF:5.0,\nad/001.ts\n#EXTINF:8.0,\nvideo/002.ts\n#EXTINF:8.0,\nvideo/003.ts\n#EXTINF:8.0,\nvideo/004.ts\n#EXT-X-ENDLIST\n";
        ExoHlsAdblockDataSource.Result result = ExoHlsAdblockDataSource.cleanForTest(
                "https://example.test/live/index.m3u8", manifest);

        assertTrue(result.changed());
        assertTrue("Unexpected notice: [" + result.notice() + "]", result.notice().startsWith("已跳过 "));
    }
}
