package com.fongmi.android.tv.player;

public final class PlaybackBufferingTracker {

    private boolean buffering;
    private Phase activePhase;
    private long bufferingStartedAtMs;
    private int rebufferCount;
    private long rebufferTotalMs;

    public Event update(boolean isBuffering, boolean startupComplete, long nowMs, int playbackState, long positionMs, long forwardBufferedMs, boolean loading) {
        long now = Math.max(0, nowMs);
        long position = Math.max(0, positionMs);
        long forward = Math.max(0, forwardBufferedMs);
        if (isBuffering) {
            if (buffering) return null;
            buffering = true;
            activePhase = startupComplete ? Phase.REBUFFER : Phase.STARTUP;
            bufferingStartedAtMs = now;
            if (activePhase == Phase.REBUFFER) rebufferCount++;
            return new Event(Type.START, activePhase, 0, rebufferCount, rebufferTotalMs, position, forward, loading, playbackState);
        }
        if (!buffering) return null;
        long durationMs = Math.max(0, now - bufferingStartedAtMs);
        if (activePhase == Phase.REBUFFER) rebufferTotalMs += durationMs;
        Event event = new Event(Type.END, activePhase, durationMs, rebufferCount, rebufferTotalMs, position, forward, loading, playbackState);
        buffering = false;
        activePhase = null;
        bufferingStartedAtMs = 0;
        return event;
    }

    public void reset() {
        buffering = false;
        activePhase = null;
        bufferingStartedAtMs = 0;
        rebufferCount = 0;
        rebufferTotalMs = 0;
    }

    public int getRebufferCount() {
        return rebufferCount;
    }

    public long getRebufferTotalMs() {
        return rebufferTotalMs;
    }

    public enum Type {
        START("start"),
        END("end");

        private final String label;

        Type(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Phase {
        STARTUP("startup"),
        REBUFFER("rebuffer");

        private final String label;

        Phase(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Event(Type type, Phase phase, long durationMs, int rebufferCount, long rebufferTotalMs, long positionMs, long forwardBufferedMs, boolean loading, int playbackState) {
    }
}
