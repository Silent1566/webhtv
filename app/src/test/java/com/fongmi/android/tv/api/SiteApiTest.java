package com.fongmi.android.tv.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SiteApiTest {

    @Test
    public void resolvePushPlayerUrlStripsLocalTitleSuffix() {
        String id = "file:///storage/emulated/0/AIUI/S60217-19165505-3.mp4|本地视频：01";

        assertEquals("file:///storage/emulated/0/AIUI/S60217-19165505-3.mp4", SiteApi.resolvePushPlayerUrl(id));
    }

    @Test
    public void resolvePushPlayerUrlStripsLocalIdentifierSuffix() {
        String id = "file:///storage/emulated/0/AIUI/S60217-19165505-3.mp4|01";

        assertEquals("file:///storage/emulated/0/AIUI/S60217-19165505-3.mp4", SiteApi.resolvePushPlayerUrl(id));
    }

    @Test
    public void resolvePushPlayerUrlKeepsHeaderSuffix() {
        String id = "https://cdn.test/movie.mp4|User-Agent=WebHTV";

        assertEquals(id, SiteApi.resolvePushPlayerUrl(id));
    }

    @Test
    public void isLocalFileUrlAcceptsFileSchemeCaseInsensitively() {
        assertTrue(SiteApi.isLocalFileUrl("file:///storage/emulated/0/Download/movie.mp4"));
        assertTrue(SiteApi.isLocalFileUrl("FILE:///storage/emulated/0/Download/movie.mp4"));
    }

    @Test
    public void isLocalFileUrlRejectsRemoteUrl() {
        assertFalse(SiteApi.isLocalFileUrl("https://cdn.test/movie.mp4"));
    }
}
