package androidx.media3.mpvplayer;

final class MpvStartupBufferPolicy {

    static final String CACHE_PAUSE = "yes";
    static final String CACHE_PAUSE_INITIAL = "no";

    private MpvStartupBufferPolicy() {
    }

    static boolean shouldApplyPerformanceOverlay(boolean performanceOptionsPriority) {
        return performanceOptionsPriority;
    }
}
