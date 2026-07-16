package com.fongmi.android.tv.bean;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TmdbMatchCacheTest {

    @Test
    public void titleScopedCacheSeparatesSameSiteAndVodId() {
        TmdbMatchCache cache = new TmdbMatchCache();

        cache.put("玩偶|虎斑2", "shared", "云秀行（真彩）", item(100, "云秀行"));
        cache.put("玩偶|虎斑2", "shared", "千香（真彩）", item(200, "千香"));

        assertEquals(100, cache.find("玩偶|虎斑2", "shared", "云秀行（真彩）").getTmdbId());
        assertEquals(200, cache.find("玩偶|虎斑2", "shared", "千香（真彩）").getTmdbId());
    }

    @Test
    public void titleScopedFindSkipsConflictingLegacyCache() {
        TmdbMatchCache cache = new TmdbMatchCache();

        cache.put("玩偶|虎斑2", "shared", item(200, "千香"));

        assertNull(cache.find("玩偶|虎斑2", "shared", "云秀行（真彩）"));
        assertEquals(200, cache.find("玩偶|虎斑2", "shared", "千香（真彩）").getTmdbId());
    }

    @Test
    public void titleScopedFindFallsBackAcrossSites() {
        TmdbMatchCache cache = new TmdbMatchCache();

        cache.put("site-a", "vod-a", "庆余年 第二季", item(100, "庆余年"));

        assertEquals(100, cache.find("site-b", "vod-b", "庆余年 第二季").getTmdbId());
    }

    @Test
    public void titleScopedFallbackIsRemovedWhenSameTitleConflicts() {
        TmdbMatchCache cache = new TmdbMatchCache();

        cache.put("site-a", "vod-a", "重名剧", item(100, "重名剧"));
        cache.put("site-b", "vod-b", "重名剧", item(200, "重名剧"));

        assertNull(cache.find("site-c", "vod-c", "重名剧"));
        assertEquals(100, cache.find("site-a", "vod-a", "重名剧").getTmdbId());
        assertEquals(200, cache.find("site-b", "vod-b", "重名剧").getTmdbId());
    }

    private static TmdbItem item(int id, String title) {
        return new TmdbItem(id, "tv", title, "", "", "", "");
    }
}
