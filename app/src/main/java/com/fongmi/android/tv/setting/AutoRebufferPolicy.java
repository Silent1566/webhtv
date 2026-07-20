package com.fongmi.android.tv.setting;

final class AutoRebufferPolicy {

    static final int DEFAULT_REBUFFER_MS = 3_000;
    static final int DEFAULT_START_BUFFER_MS = 1_500;
    private static final long CLEAN_SESSION_MIN_POSITION_MS = 180_000;
    private static final int CLEAN_SESSIONS_TO_LOWER = 2;

    private AutoRebufferPolicy() {
    }

    static Result resolve(int currentMs, int cleanStreak, int rebufferCount, long rebufferTotalMs, long positionMs, long mediaBitrate, long bandwidthEstimate) {
        int current = normalize(currentMs);
        double ratio = ratio(bandwidthEstimate, mediaBitrate);
        if (rebufferCount >= 3 || rebufferTotalMs >= 15_000 || (ratio > 0 && ratio < 1.15)) return new Result(8_000, 0);
        if (rebufferCount >= 2 || rebufferTotalMs >= 8_000 || (ratio > 0 && ratio < 1.35)) return new Result(Math.max(current, 5_000), 0);
        if (rebufferCount >= 1 || (ratio > 0 && ratio < 2.00)) return new Result(Math.max(current, 3_000), 0);
        boolean clean = positionMs >= CLEAN_SESSION_MIN_POSITION_MS && ratio >= 2.00;
        if (!clean) return new Result(current, 0);
        int nextStreak = cleanStreak + 1;
        if (nextStreak < CLEAN_SESSIONS_TO_LOWER) return new Result(current, nextStreak);
        return new Result(lower(current), 0);
    }

    static int normalize(int value) {
        if (value <= 2_000) return 2_000;
        if (value <= 3_000) return 3_000;
        if (value <= 5_000) return 5_000;
        return 8_000;
    }

    static int startBufferMs(int rebufferMs) {
        return switch (normalize(rebufferMs)) {
            case 8_000 -> 5_000;
            case 5_000 -> 3_000;
            default -> DEFAULT_START_BUFFER_MS;
        };
    }

    private static int lower(int value) {
        return switch (normalize(value)) {
            case 8_000 -> 5_000;
            case 5_000 -> 3_000;
            default -> 2_000;
        };
    }

    private static double ratio(long bandwidthEstimate, long mediaBitrate) {
        if (bandwidthEstimate <= 0 || mediaBitrate <= 0) return 0;
        return (double) bandwidthEstimate / (double) mediaBitrate;
    }

    record Result(int rebufferMs, int cleanStreak) {
    }
}
