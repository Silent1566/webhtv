package com.fongmi.android.tv.player.exo;

import com.fongmi.android.tv.player.PlaybackTrace;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlaybackAnalyticsTraceTest {

    @After
    public void tearDown() {
        PlaybackAnalyticsListener.reset();
    }

    @Test
    public void analyticsSessionKeepsManagerTrace() {
        PlaybackAnalyticsListener.beginSession("p-abc-1");

        assertEquals("p-abc-1", PlaybackAnalyticsListener.getPlaybackTraceId());
    }

    @Test
    public void invalidTraceFallsBackToNone() {
        PlaybackAnalyticsListener.beginSession("movie-token-url");

        assertEquals(PlaybackTrace.NONE, PlaybackAnalyticsListener.getPlaybackTraceId());
    }
}
