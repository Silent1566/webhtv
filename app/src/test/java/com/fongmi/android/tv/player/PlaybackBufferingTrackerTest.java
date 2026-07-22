package com.fongmi.android.tv.player;

import androidx.media3.common.Player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PlaybackBufferingTrackerTest {

    @Test
    public void startupBufferingIsMeasuredButNotCountedAsRebuffer() {
        PlaybackBufferingTracker tracker = new PlaybackBufferingTracker();

        PlaybackBufferingTracker.Event start = tracker.update(true, false, 1_000, Player.STATE_BUFFERING, 0, 500, true);
        assertNull(tracker.update(true, false, 1_100, Player.STATE_BUFFERING, 0, 700, true));
        PlaybackBufferingTracker.Event end = tracker.update(false, true, 1_450, Player.STATE_READY, 0, 2_000, false);

        assertEquals(PlaybackBufferingTracker.Phase.STARTUP, start.phase());
        assertEquals(0, start.rebufferCount());
        assertEquals(450, end.durationMs());
        assertEquals(0, end.rebufferCount());
        assertEquals(0, end.rebufferTotalMs());
    }

    @Test
    public void bufferingAfterStartupCountsOnceAndAccumulatesDuration() {
        PlaybackBufferingTracker tracker = new PlaybackBufferingTracker();

        PlaybackBufferingTracker.Event firstStart = tracker.update(true, true, 2_000, Player.STATE_BUFFERING, 10_000, 200, true);
        assertNull(tracker.update(true, true, 2_100, Player.STATE_BUFFERING, 10_000, 300, true));
        PlaybackBufferingTracker.Event firstEnd = tracker.update(false, true, 2_600, Player.STATE_READY, 10_000, 3_000, false);
        PlaybackBufferingTracker.Event secondStart = tracker.update(true, true, 4_000, Player.STATE_BUFFERING, 20_000, 100, true);
        PlaybackBufferingTracker.Event secondEnd = tracker.update(false, true, 4_250, Player.STATE_IDLE, 20_000, 0, false);

        assertEquals(PlaybackBufferingTracker.Phase.REBUFFER, firstStart.phase());
        assertEquals(1, firstStart.rebufferCount());
        assertEquals(600, firstEnd.durationMs());
        assertEquals(600, firstEnd.rebufferTotalMs());
        assertEquals(2, secondStart.rebufferCount());
        assertEquals(250, secondEnd.durationMs());
        assertEquals(850, secondEnd.rebufferTotalMs());
        assertEquals(Player.STATE_IDLE, secondEnd.playbackState());
    }

    @Test
    public void bufferingPhaseDoesNotChangeWhileIntervalIsActive() {
        PlaybackBufferingTracker tracker = new PlaybackBufferingTracker();

        tracker.update(true, false, 100, Player.STATE_BUFFERING, 0, 0, true);
        PlaybackBufferingTracker.Event end = tracker.update(false, true, 300, Player.STATE_READY, 0, 1_000, false);

        assertEquals(PlaybackBufferingTracker.Phase.STARTUP, end.phase());
        assertEquals(0, end.rebufferCount());
    }

    @Test
    public void resetClearsActiveIntervalAndTotals() {
        PlaybackBufferingTracker tracker = new PlaybackBufferingTracker();
        tracker.update(true, true, 100, Player.STATE_BUFFERING, 0, 0, true);
        tracker.reset();

        assertNull(tracker.update(false, true, 500, Player.STATE_READY, 0, 0, false));
        PlaybackBufferingTracker.Event start = tracker.update(true, true, 600, Player.STATE_BUFFERING, 0, 0, true);

        assertEquals(1, start.rebufferCount());
        assertEquals(0, start.rebufferTotalMs());
    }
}
