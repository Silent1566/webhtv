package androidx.media3.mpvplayer;

import androidx.media3.common.Player;

final class MpvPlaybackState {

    private MpvPlaybackState() {
    }

    static int resolveAfterCachePoll(int currentState, boolean fileLoaded, boolean playbackRestarted, boolean stopping, boolean pausedForCache) {
        if (!fileLoaded || !playbackRestarted || stopping || currentState == Player.STATE_IDLE || currentState == Player.STATE_ENDED) return currentState;
        return pausedForCache ? Player.STATE_BUFFERING : Player.STATE_READY;
    }
}
