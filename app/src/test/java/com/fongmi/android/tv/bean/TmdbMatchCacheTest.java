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

    private static TmdbItem item(int id, String title) {
        return new TmdbItem(id, "tv", title, "", "", "", "");
    }
}
