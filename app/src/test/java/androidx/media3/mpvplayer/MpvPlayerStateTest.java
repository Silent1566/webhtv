package androidx.media3.mpvplayer;

import static org.junit.Assert.assertEquals;

import androidx.media3.common.Player;

import org.junit.Test;

public class MpvPlayerStateTest {

    @Test
    public void cachePollRecoversReadyWhenPausedForCacheFalseEventWasMissed() {
        assertEquals(Player.STATE_READY,
                MpvPlaybackState.resolveAfterCachePoll(
                        Player.STATE_BUFFERING, true, true, false, false));
    }

    @Test
    public void cachePollKeepsBufferingWhileMpvIsActuallyPausedForCache() {
        assertEquals(Player.STATE_BUFFERING,
                MpvPlaybackState.resolveAfterCachePoll(
                        Player.STATE_READY, true, true, false, true));
    }

    @Test
    public void cachePollDoesNotClaimReadyBeforeFirstPlaybackRestart() {
        assertEquals(Player.STATE_BUFFERING,
                MpvPlaybackState.resolveAfterCachePoll(
                        Player.STATE_BUFFERING, true, false, false, false));
    }

    @Test
    public void cachePollDoesNotChangeStateWhileStopping() {
        assertEquals(Player.STATE_BUFFERING,
                MpvPlaybackState.resolveAfterCachePoll(
                        Player.STATE_BUFFERING, true, true, true, false));
    }

    @Test
    public void cachePollKeepsTerminalStates() {
        assertEquals(Player.STATE_IDLE,
                MpvPlaybackState.resolveAfterCachePoll(
                        Player.STATE_IDLE, true, true, false, false));
        assertEquals(Player.STATE_ENDED,
                MpvPlaybackState.resolveAfterCachePoll(
                        Player.STATE_ENDED, true, true, false, true));
    }
}
