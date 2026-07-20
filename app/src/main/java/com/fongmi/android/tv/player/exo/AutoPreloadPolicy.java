package com.fongmi.android.tv.player.exo;

import com.fongmi.android.tv.player.PlaybackRoute;

final class AutoPreloadPolicy {

    static final int PAUSED_THREADS = 0;
    static final int NORMAL_THREADS = 1;
    static final int FAST_THREADS = 2;
    static final long DEGRADED_DURATION_MS = 10_000;
    static final long NORMAL_DURATION_MS = 20_000;
    static final long FAST_DURATION_MS = 30_000;
    private static final long NORMAL_BUFFER_MS = 8_000;
    private static final long FAST_BUFFER_MS = 20_000;
    private static final long FAST_FALLBACK_BUFFER_MS = 12_000;
    private static final long NORMAL_STABLE_MS = 20_000;
    private static final long RESUME_DELAY_MS = 10_000;
    private static final long WEAK_RESUME_DELAY_MS = 15_000;
    private static final long FAST_STABLE_MS = 30_000;
    private static final long FAST_COOLDOWN_MS = 60_000;
    private static final double PAUSE_RATIO = 1.15;
    private static final double RESUME_RATIO = 1.40;
    private static final double FAST_RATIO = 3.00;
    private static final double FAST_FALLBACK_RATIO = 2.00;

    private Mode mode = Mode.NORMAL;
    private long resumeAfterMs;
    private long fastBlockedUntilMs;
    private long stableSinceMs = Long.MIN_VALUE;
    private int lastRebufferCount;

    Decision evaluate(long nowMs, PlaybackRoute route, long bufferedMs, long mediaBitrate, long bandwidthEstimate, int rebufferCount, boolean loading) {
        if (rebufferCount > lastRebufferCount) disrupt(nowMs);
        lastRebufferCount = Math.max(lastRebufferCount, rebufferCount);
        double ratio = ratio(bandwidthEstimate, mediaBitrate);
        boolean knownRatio = ratio > 0;
        if ((loading && bufferedMs < PreCachePolicy.INITIAL_SAFE_BUFFER_MS) || (knownRatio && ratio < PAUSE_RATIO)) {
            pause(nowMs, WEAK_RESUME_DELAY_MS);
            return decision();
        }
        if (mode == Mode.PAUSED) {
            if (nowMs < resumeAfterMs || bufferedMs < NORMAL_BUFFER_MS || (knownRatio && ratio < RESUME_RATIO)) return decision();
            mode = Mode.NORMAL;
            stableSinceMs = nowMs;
        }
        if (mode == Mode.DEGRADED && bufferedMs >= FAST_FALLBACK_BUFFER_MS && (!knownRatio || ratio >= FAST_FALLBACK_RATIO)) {
            if (stableSinceMs == Long.MIN_VALUE) stableSinceMs = nowMs;
            if (nowMs - stableSinceMs >= NORMAL_STABLE_MS) {
                mode = Mode.NORMAL;
                stableSinceMs = nowMs;
            }
        }
        if (mode == Mode.FAST && (!supportsFast(route) || bufferedMs < FAST_FALLBACK_BUFFER_MS || (knownRatio && ratio < FAST_FALLBACK_RATIO))) {
            mode = Mode.NORMAL;
            stableSinceMs = nowMs;
        }
        if (mode == Mode.NORMAL && knownRatio && (bufferedMs < FAST_FALLBACK_BUFFER_MS || ratio < FAST_FALLBACK_RATIO)) {
            mode = Mode.DEGRADED;
            stableSinceMs = nowMs;
        }
        if (bufferedMs < NORMAL_BUFFER_MS || (knownRatio && ratio < RESUME_RATIO)) {
            stableSinceMs = Long.MIN_VALUE;
        } else if (stableSinceMs == Long.MIN_VALUE) {
            stableSinceMs = nowMs;
        }
        if (mode == Mode.NORMAL
                && supportsFast(route)
                && knownRatio
                && ratio >= FAST_RATIO
                && bufferedMs >= FAST_BUFFER_MS
                && nowMs >= fastBlockedUntilMs
                && stableSinceMs != Long.MIN_VALUE
                && nowMs - stableSinceMs >= FAST_STABLE_MS) {
            mode = Mode.FAST;
        }
        return decision();
    }

    void disrupt(long nowMs) {
        pause(nowMs, RESUME_DELAY_MS);
        fastBlockedUntilMs = Math.max(fastBlockedUntilMs, nowMs + FAST_COOLDOWN_MS);
    }

    private void pause(long nowMs, long delayMs) {
        mode = Mode.PAUSED;
        resumeAfterMs = Math.max(resumeAfterMs, nowMs + delayMs);
        stableSinceMs = Long.MIN_VALUE;
    }

    private Decision decision() {
        return switch (mode) {
            case PAUSED -> new Decision(PAUSED_THREADS, 0, "paused");
            case DEGRADED -> new Decision(NORMAL_THREADS, DEGRADED_DURATION_MS, "degraded");
            case FAST -> new Decision(FAST_THREADS, FAST_DURATION_MS, "fast");
            default -> new Decision(NORMAL_THREADS, NORMAL_DURATION_MS, "normal");
        };
    }

    private static boolean supportsFast(PlaybackRoute route) {
        return route == PlaybackRoute.DIRECT_REMOTE_HTTP || route == PlaybackRoute.APP_LOCAL_SERVICE;
    }

    private static double ratio(long bandwidthEstimate, long mediaBitrate) {
        if (bandwidthEstimate <= 0 || mediaBitrate <= 0) return 0;
        return (double) bandwidthEstimate / (double) mediaBitrate;
    }

    record Decision(int threads, long durationMs, String mode) {

        boolean enabled() {
            return threads > 0 && durationMs > 0;
        }
    }

    private enum Mode {
        PAUSED,
        DEGRADED,
        NORMAL,
        FAST
    }
}
