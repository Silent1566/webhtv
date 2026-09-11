package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class MediaSourceFactoryTest {

    @Test
    public void sanitizeHeaders_trimsKeysAndValues() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(" Referer ", " https://movie.douban.com ");
        headers.put(" ", "ignored");
        headers.put("Accept", null);

        Map<String, String> sanitized = MediaSourceFactory.sanitizeHeaders(headers);

        assertEquals(1, sanitized.size());
        assertEquals("https://movie.douban.com", sanitized.get("Referer"));
    }

    @Test
    public void removeUserAgentHeader_extractsCaseInsensitively() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("referer", "https://example.test");
        headers.put("user-agent", " Custom UA ");

        String userAgent = MediaSourceFactory.removeUserAgentHeader(headers);

        assertEquals("Custom UA", userAgent);
        assertEquals(1, headers.size());
        assertNull(headers.get("user-agent"));
        assertEquals("https://example.test", headers.get("referer"));
    }

    @Test
    public void isHlsUrl_recognizesLiveAliases() {
        assertTrue(MediaSourceFactory.isHlsUrl("https://example.test/play?type=hls&id=1"));
        assertTrue(MediaSourceFactory.isHlsUrl("https://example.test/live/stream?id=1"));
        assertTrue(MediaSourceFactory.isHlsUrl("https://example.test/tv/live.php?id=1"));
        assertFalse(MediaSourceFactory.isHlsUrl("https://example.test/video.mp4"));
    }

    @Test
    public void cacheNamespace_separatesEquivalentUrlsWithDifferentCredentials() {
        Map<String, String> first = new LinkedHashMap<>();
        first.put("Cookie", "sid=one");
        Map<String, String> second = new LinkedHashMap<>();
        second.put("Cookie", "sid=two");

        assertNotEquals(MediaSourceFactory.cacheNamespace(first), MediaSourceFactory.cacheNamespace(second));
        assertEquals(MediaSourceFactory.cacheNamespace(first), MediaSourceFactory.cacheNamespace(Map.of("cookie", "sid=one")));
    }

    @Test
    public void cacheKeyFactory_keepsCredentialValuesOutOfTheKey() {
        Map<String, String> headers = Map.of("Authorization", "Bearer secret-token");
        String key = MediaSourceFactory.cacheKey(headers, "https://example.test/video.mp4");

        assertFalse(key.contains("secret-token"));
        assertTrue(key.endsWith("|https://example.test/video.mp4"));
    }

    @Test
    public void cacheKey_withoutHeaders_preservesExistingUrlAndCustomKeys() {
        String url = "https://example.test/video.mp4?signature=one";

        assertEquals(url, MediaSourceFactory.cacheKey(Map.of(), url));
        assertEquals("custom-key", MediaSourceFactory.cacheKey(null, "custom-key"));
    }

    @Test
    public void cacheNamespace_ignoredEmptyUserAgent_hasNoCacheCost() {
        assertEquals(MediaSourceFactory.cacheNamespace(Map.of()),
                MediaSourceFactory.cacheNamespace(Map.of(" User-Agent ", "  ")));
    }

    @Test
    public void cacheNamespace_headerDelimiters_cannotImpersonateOtherCredentials() {
        // A cache hit precedes HTTP header validation, so even an invalid header must not
        // alias a valid authenticated entry before OkHttp has a chance to reject it.
        Map<String, String> injected = Map.of("Authorization", "Bearer one\nx-session=two");
        Map<String, String> separate = Map.of("Authorization", "Bearer one", "X-Session", "two");

        assertNotEquals(MediaSourceFactory.cacheNamespace(injected),
                MediaSourceFactory.cacheNamespace(separate));
    }

    @Test
    public void cacheNamespace_malformedUnicode_cannotAliasValidHeaders() {
        assertNotEquals(MediaSourceFactory.cacheNamespace(Map.of("Authorization", "Bearer \uD800")),
                MediaSourceFactory.cacheNamespace(Map.of("Authorization", "Bearer ?")));
        assertNotEquals(MediaSourceFactory.cacheNamespace(Map.of("\u212A", "value")),
                MediaSourceFactory.cacheNamespace(Map.of("k", "value")));
    }

    @Test
    public void cacheNamespace_caseVariantHeaders_doesNotDiscardCredentials() {
        Map<String, String> first = new LinkedHashMap<>();
        first.put("COOKIE", "sid=one");
        first.put("cookie", "stable=value");
        Map<String, String> second = new LinkedHashMap<>(first);
        second.put("COOKIE", "sid=two");

        // Both variants survive Media3's case-sensitive HashMap before OkHttp applies
        // case-insensitive header replacement. Hashing only the last input entry is unsafe.
        assertNotEquals(MediaSourceFactory.cacheNamespace(first),
                MediaSourceFactory.cacheNamespace(second));
    }

    @Test
    public void cacheNamespace_headerOrderAndSpelling_doNotCauseCacheMisses() {
        Map<String, String> first = new LinkedHashMap<>();
        first.put(" Referer ", " https://example.test/show ");
        first.put("User-Agent", " Player UA ");
        first.put("Authorization", "Bearer one");
        Map<String, String> second = new LinkedHashMap<>();
        second.put("authorization", "Bearer one");
        second.put("user-agent", "Player UA");
        second.put("referer", "https://example.test/show");

        assertEquals(MediaSourceFactory.cacheNamespace(first),
                MediaSourceFactory.cacheNamespace(second));
        assertEquals(3, first.size());
        assertEquals(" Player UA ", first.get("User-Agent"));
    }

    @Test
    public void cacheKey_signedUrlsRemainDistinct() {
        Map<String, String> headers = Map.of("Authorization", "Bearer one");
        assertNotEquals(MediaSourceFactory.cacheKey(headers, "https://example.test/a.ts?sig=one"),
                MediaSourceFactory.cacheKey(headers, "https://example.test/a.ts?sig=two"));
    }

}
