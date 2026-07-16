package com.fongmi.android.tv.player.engine;

public record PlayerCacheState(
        boolean available,
        boolean enabled,
        boolean idle,
        boolean underrun,
        boolean bofCached,
        boolean eofCached,
        int bufferingState,
        long cacheDurationMs,
        long cacheEndMs,
        long readerPositionMs,
        long forwardBytes,
        long totalBytes,
        long fileBytes,
        long rawInputBytesPerSecond,
        long maxBytes,
        long maxBackBytes,
        int cacheSeconds,
        int readaheadSeconds) {

    private static final PlayerCacheState EMPTY = new PlayerCacheState(false, false, false, false, false, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    public static PlayerCacheState empty() {
        return EMPTY;
    }
}
