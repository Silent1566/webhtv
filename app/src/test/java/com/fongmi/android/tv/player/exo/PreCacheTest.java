package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PreCacheTest {

    @Test
    public void canPreCache_allowsRegularHttpMedia() {
        assertTrue(PreCache.canPreCache("https", "https://cdn.example.test/movie.mkv"));
        assertTrue(PreCache.canPreCache("http", "http://cdn.example.test/movie.mp4"));
    }

    @Test
    public void canPreCache_skipsLocalProxyMedia() {
        assertFalse(PreCache.canPreCache("http", "http://127.0.0.1:9978/proxy?do=js"));
        assertFalse(PreCache.canPreCache("http", "http://localhost:9978/proxy?siteKey=drive"));
        assertFalse(PreCache.canPreCache("http", "http://[::1]:9978/proxy?do=py"));
        assertFalse(PreCache.canPreCache("http", "http://127.0.0.1:5000/proxy/1_4213_0_0"));
    }

    @Test
    public void canPreCache_skipsNonHttpAndConcatenatingMedia() {
        assertFalse(PreCache.canPreCache("file", "file:///sdcard/movie.mkv"));
        assertFalse(PreCache.canPreCache("https", "https://a.test/1.mp4|||1000***https://b.test/2.mp4|||1000"));
    }
}
