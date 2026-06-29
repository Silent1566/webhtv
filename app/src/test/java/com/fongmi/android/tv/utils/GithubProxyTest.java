package com.fongmi.android.tv.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GithubProxyTest {

    @Test
    public void applyPrefixesGithubRawUrl() {
        String url = "https://raw.githubusercontent.com/FGBLH/GHK/refs/heads/main/a.json";

        assertEquals("https://ghfast.top/" + url, GithubProxy.apply(url, "https://ghfast.top/"));
    }

    @Test
    public void applyPrefixesGithubReleaseDownloadUrl() {
        String url = "https://github.com/Silent1566/webhtv/releases/latest/download/mobile-arm64_v8a.apk";

        assertEquals("https://ghfast.top/" + url, GithubProxy.apply(url, "https://ghfast.top/"));
    }

    @Test
    public void applyLeavesGithubPageUrlAlone() {
        String url = "https://github.com/Silent1566/webhtv";

        assertEquals(url, GithubProxy.apply(url, "https://ghfast.top/"));
    }

    @Test
    public void applyLeavesNonGithubUrlAlone() {
        String url = "https://example.com/a.json";

        assertEquals(url, GithubProxy.apply(url, "https://ghfast.top/"));
    }

    @Test
    public void applyDoesNotDoubleProxy() {
        String url = "https://ghfast.top/https://raw.githubusercontent.com/FGBLH/GHK/refs/heads/main/a.json";

        assertEquals(url, GithubProxy.apply(url, "https://ghfast.top/"));
    }

    @Test
    public void normalizeConfigKeepsFirstValidSources() {
        assertEquals("https://ghfast.top/\nhttps://99z.top/", GithubProxy.normalizeConfig("bad\nhttps://ghfast.top\nhttps://99z.top/"));
    }
}
