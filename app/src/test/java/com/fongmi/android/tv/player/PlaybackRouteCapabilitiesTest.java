package com.fongmi.android.tv.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackRouteCapabilitiesTest {

    @Test
    public void directRemoteOnlyClaimsRequestLevelVisibility() {
        PlaybackRouteCapabilities capabilities = PlaybackRouteCapabilities.resolve(PlaybackRoute.resolve("https://cdn.example.com/movie.mkv"));

        assertEquals(PlaybackRouteCapabilities.ObservedLeg.PLAYER_TO_REMOTE_HTTP, capabilities.observedLeg());
        assertEquals(PlaybackRouteCapabilities.UpstreamVisibility.REQUEST_LEVEL_ONLY, capabilities.upstreamVisibility());
        assertEquals(PlaybackRouteCapabilities.ControlScope.PLAYER_REQUEST_OPTIONS, capabilities.controlScope());
        assertFalse(capabilities.externalUpstreamOpaque());
    }

    @Test
    public void appOwnedPortOnlyClaimsItsServicePath() {
        try (PlaybackRouteRegistry.Registration ignored = PlaybackRouteRegistry.registerAppService(7788, PlaybackRouteRegistry.AppOwner.HLS_PROXY)) {
            PlaybackRouteCapabilities capabilities = PlaybackRouteCapabilities.resolve(PlaybackRoute.resolve("http://127.0.0.1:7788/mpv/index.m3u8"));

            assertEquals(PlaybackRouteCapabilities.ObservedLeg.APP_TO_OWNED_LOCAL_SERVICE, capabilities.observedLeg());
            assertEquals(PlaybackRouteCapabilities.UpstreamVisibility.APP_SERVICE_PATH, capabilities.upstreamVisibility());
            assertEquals(PlaybackRouteCapabilities.ControlScope.APP_OWNED_SERVICE_CODE, capabilities.controlScope());
        }
    }

    @Test
    public void externalLoopbackMakesOpaqueBoundaryExplicit() {
        PlaybackRoute.Resolution route = PlaybackRoute.resolve("http://127.0.0.1:8899/video?token=secret");
        PlaybackRouteCapabilities capabilities = PlaybackRouteCapabilities.resolve(route);
        String summary = route.logSummary();

        assertEquals(PlaybackRouteCapabilities.ObservedLeg.APP_TO_LOCAL_ENDPOINT_ONLY, capabilities.observedLeg());
        assertEquals(PlaybackRouteCapabilities.UpstreamVisibility.OPAQUE_EXTERNAL_PROCESS, capabilities.upstreamVisibility());
        assertEquals(PlaybackRouteCapabilities.ControlScope.NONE, capabilities.controlScope());
        assertTrue(capabilities.externalUpstreamOpaque());
        assertTrue(summary.contains("observedLeg=app-to-local-endpoint-only"));
        assertTrue(summary.contains("upstreamVisibility=opaque-external-process"));
        assertFalse(summary.contains("8899"));
        assertFalse(summary.contains("secret"));
    }

    @Test
    public void nonHttpSourceDoesNotClaimUpstreamControl() {
        PlaybackRouteCapabilities capabilities = PlaybackRouteCapabilities.resolve(PlaybackRoute.resolve("file:///storage/emulated/0/movie.mkv"));

        assertEquals(PlaybackRouteCapabilities.ObservedLeg.SOURCE_SPECIFIC, capabilities.observedLeg());
        assertEquals(PlaybackRouteCapabilities.UpstreamVisibility.UNKNOWN, capabilities.upstreamVisibility());
        assertEquals(PlaybackRouteCapabilities.ControlScope.NONE, capabilities.controlScope());
    }
}
