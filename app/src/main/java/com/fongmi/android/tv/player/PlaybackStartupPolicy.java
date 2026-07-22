package com.fongmi.android.tv.player;

public final class PlaybackStartupPolicy {

    private PlaybackStartupPolicy() {
    }

    public static Completion resolve(boolean ready, boolean readySignalsFirstVideoFrame, boolean hasVideo, boolean hasAudio) {
        if (!ready) return Completion.NONE;
        if (hasVideo && readySignalsFirstVideoFrame) return Completion.FIRST_FRAME;
        if (!hasVideo && hasAudio) return Completion.AUDIO_PLAYABLE;
        return Completion.NONE;
    }

    public enum Completion {
        NONE,
        FIRST_FRAME,
        AUDIO_PLAYABLE
    }
}
