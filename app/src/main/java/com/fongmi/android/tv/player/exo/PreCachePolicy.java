package com.fongmi.android.tv.player.exo;

final class PreCachePolicy {

    static final long INITIAL_SAFE_BUFFER_MS = 5_000;
    static final long RECOVERY_SAFE_BUFFER_MS = 8_000;
    private static final long INITIAL_IDLE_FLOOR_MS = 2_000;
    private static final long RECOVERY_IDLE_FLOOR_MS = 3_000;
    private static final int CAPACITY_HEADROOM_PERCENT = 80;
    private static final int PRELOAD_CACHE_PERCENT = 50;
    private static final long MIN_CAPACITY_TARGET_MS = 1_000;
    private static final long UNKNOWN_BITRATE_BITS_PER_SECOND = 200_000_000L;

    private PreCachePolicy() {
    }

    static long safeBufferTargetMs(boolean recovery, long remainingMs, long bitrateBitsPerSecond, int capacityBytes) {
        long targetMs = recovery ? RECOVERY_SAFE_BUFFER_MS : INITIAL_SAFE_BUFFER_MS;
        if (remainingMs >= 0) targetMs = Math.min(targetMs, remainingMs);
        long capacityMs = capacityDurationMs(bitrateBitsPerSecond, capacityBytes);
        if (capacityMs > 0) {
            long usableCapacityMs = Math.max(MIN_CAPACITY_TARGET_MS, capacityMs * CAPACITY_HEADROOM_PERCENT / 100);
            targetMs = Math.min(targetMs, usableCapacityMs);
        }
        return Math.max(0, targetMs);
    }

    static boolean hasSafeBuffer(long bufferedDurationMs, boolean loading, long targetMs, boolean recovery) {
        if (bufferedDurationMs >= targetMs) return true;
        long idleFloorMs = recovery ? RECOVERY_IDLE_FLOOR_MS : INITIAL_IDLE_FLOOR_MS;
        return !loading && bufferedDurationMs >= Math.min(targetMs, idleFloorMs);
    }

    static long preloadLengthMs(long configuredLengthMs, long remainingMs, long bitrateBitsPerSecond, long cacheCapacityBytes) {
        long lengthMs = Math.max(0, configuredLengthMs);
        if (remainingMs >= 0) lengthMs = Math.min(lengthMs, remainingMs);
        if (cacheCapacityBytes <= 0) return 0;
        long byteBudget = cacheCapacityBytes / 100 * PRELOAD_CACHE_PERCENT;
        long estimatedBitrate = bitrateBitsPerSecond > 0 ? bitrateBitsPerSecond : UNKNOWN_BITRATE_BITS_PER_SECOND;
        long capacityMs = durationForBytesMs(byteBudget, estimatedBitrate);
        return Math.min(lengthMs, capacityMs);
    }

    private static long capacityDurationMs(long bitrateBitsPerSecond, int capacityBytes) {
        if (bitrateBitsPerSecond <= 0 || capacityBytes <= 0) return 0;
        return durationForBytesMs(capacityBytes, bitrateBitsPerSecond);
    }

    private static long durationForBytesMs(long bytes, long bitrateBitsPerSecond) {
        if (bytes <= 0 || bitrateBitsPerSecond <= 0) return 0;
        long capacityBits = bytes > Long.MAX_VALUE / 8L ? Long.MAX_VALUE : bytes * 8L;
        if (capacityBits > Long.MAX_VALUE / 1_000L) return Long.MAX_VALUE;
        return capacityBits * 1_000L / bitrateBitsPerSecond;
    }
}
