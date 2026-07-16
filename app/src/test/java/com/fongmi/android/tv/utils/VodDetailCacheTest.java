package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.Vod;

import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class VodDetailCacheTest {

    @Test
    public void putReturnsEmptyKeyForNullVod() {
        assertTrue(VodDetailCache.put(null).isEmpty());
    }

    @Test
    public void takeReturnsCachedVodOnce() {
        Vod vod = new Vod();
        vod.setId("movie-1");
        vod.setName("Movie One");

        String key = VodDetailCache.put(vod);

        assertTrue(!key.isEmpty());
        assertSame(vod, VodDetailCache.take(key));
        assertNull(VodDetailCache.take(key));
    }

    @Test
    public void takeReturnsNullForBlankOrUnknownKey() {
        assertNull(VodDetailCache.take(""));
        assertNull(VodDetailCache.take("missing"));
    }
}
