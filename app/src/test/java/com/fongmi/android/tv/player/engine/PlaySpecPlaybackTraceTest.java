package com.fongmi.android.tv.player.engine;

import com.fongmi.android.tv.player.PlaybackRoute;
import com.fongmi.android.tv.player.PlaybackRouteRegistry;
import com.fongmi.android.tv.player.PlaybackTrace;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class PlaySpecPlaybackTraceTest {

    @Test
    public void formatRetryCopyKeepsTheSamePlaybackTrace() {
        PlaySpec original = PlaySpec.from("key", "https://example.com/video", Map.of(), null);
        original.setPlaybackTraceId("p-abc-1");

        PlaySpec retry = original.copyWithFormat("application/x-mpegURL");

        assertEquals("p-abc-1", retry.getPlaybackTraceId());
    }

    @Test
    public void invalidTraceCannotEnterPlaySpec() {
        PlaySpec spec = PlaySpec.from("key", "https://example.com/video", Map.of(), null);

        spec.setPlaybackTraceId("https://example.com/?token=secret");

        assertEquals(PlaybackTrace.NONE, spec.getPlaybackTraceId());
    }

    @Test
    public void routeResolutionUpdatesWithUrlAndSurvivesFormatCopy() {
        PlaySpec spec;
        try (PlaybackRouteRegistry.Registration ignored = PlaybackRouteRegistry.registerAppService(7788, PlaybackRouteRegistry.AppOwner.HLS_PROXY)) {
            spec = PlaySpec.from("key", "http://127.0.0.1:7788/mpv/index.m3u8", Map.of(), null);
            assertEquals(PlaybackRoute.Owner.APP_HLS_PROXY, spec.getPlaybackRoute().owner());
        }

        PlaySpec retry = spec.copyWithFormat("application/x-mpegURL");
        assertEquals(PlaybackRoute.Owner.APP_HLS_PROXY, retry.getPlaybackRoute().owner());

        spec.setUrl("https://cdn.example.com/video.mkv");
        assertEquals(PlaybackRoute.DIRECT_REMOTE_HTTP, spec.getPlaybackRoute().route());
    }

    @Test
    public void routeCanRefreshWhenLocalServiceStartsAfterSpecCreation() {
        PlaySpec spec = PlaySpec.from("key", "http://127.0.0.1:7799/video", Map.of(), null);
        assertEquals(PlaybackRoute.EXTERNAL_LOOPBACK_PROXY, spec.getPlaybackRoute().route());

        try (PlaybackRouteRegistry.Registration ignored = PlaybackRouteRegistry.registerAppService(7799, PlaybackRouteRegistry.AppOwner.MAIN_SERVER)) {
            spec.refreshPlaybackRoute();
            assertEquals(PlaybackRoute.Owner.APP_MAIN_SERVER, spec.getPlaybackRoute().owner());
        }
    }
}
