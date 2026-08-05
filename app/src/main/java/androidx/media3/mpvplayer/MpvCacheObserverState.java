package androidx.media3.mpvplayer;

final class MpvCacheObserverState {

    enum Metric {
        DURATION,
        END,
        READER_POSITION,
        SPEED,
        BUFFERING_STATE,
        FORWARD_BYTES,
        TOTAL_BYTES,
        FILE_BYTES,
        IDLE,
        UNDERRUN,
        BOF,
        EOF
    }

    private int observedMask;

    boolean record(String property, Object value) {
        Metric metric = metricForProperty(property);
        if (metric == null || value == null) return false;
        int bit = bit(metric);
        boolean firstValue = (observedMask & bit) == 0;
        observedMask |= bit;
        return firstValue;
    }

    boolean hasObservedValues() {
        return observedMask != 0;
    }

    int observedCount() {
        return Integer.bitCount(observedMask);
    }

    void reset() {
        observedMask = 0;
    }

    private static int bit(Metric metric) {
        return 1 << metric.ordinal();
    }

    private static Metric metricForProperty(String property) {
        if (property == null) return null;
        return switch (property) {
            case "demuxer-cache-duration", "demuxer-cache-state/cache-duration" -> Metric.DURATION;
            case "demuxer-cache-time", "demuxer-cache-state/cache-end" -> Metric.END;
            case "demuxer-cache-state/reader-pts" -> Metric.READER_POSITION;
            case "cache-speed", "demuxer-cache-state/raw-input-rate" -> Metric.SPEED;
            case "cache-buffering-state" -> Metric.BUFFERING_STATE;
            case "demuxer-cache-state/fw-bytes" -> Metric.FORWARD_BYTES;
            case "demuxer-cache-state/total-bytes" -> Metric.TOTAL_BYTES;
            case "demuxer-cache-state/file-cache-bytes" -> Metric.FILE_BYTES;
            case "demuxer-cache-idle", "demuxer-cache-state/idle" -> Metric.IDLE;
            case "demuxer-cache-state/underrun" -> Metric.UNDERRUN;
            case "demuxer-cache-state/bof-cached" -> Metric.BOF;
            case "demuxer-cache-state/eof-cached" -> Metric.EOF;
            default -> null;
        };
    }
}