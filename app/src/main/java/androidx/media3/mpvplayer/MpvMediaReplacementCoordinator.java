package androidx.media3.mpvplayer;

final class MpvMediaReplacementCoordinator {

    private long generation;
    private boolean prepareDeferred;
    private boolean ignoreEndFileBeforeStart;

    long begin(boolean reusingContext, boolean hadActiveMedia, boolean stopPending) {
        generation++;
        prepareDeferred = false;
        ignoreEndFileBeforeStart = reusingContext && hadActiveMedia && !stopPending;
        return generation;
    }

    long generation() {
        return generation;
    }

    boolean isCurrent(long expectedGeneration) {
        return generation == expectedGeneration;
    }

    boolean deferPrepare(boolean reusingContext, boolean stopPending) {
        prepareDeferred = reusingContext && stopPending;
        return prepareDeferred;
    }

    boolean resumeAfterStopAcknowledged() {
        boolean resume = prepareDeferred;
        prepareDeferred = false;
        return resume;
    }

    boolean resumeAfterTimeout() {
        boolean resume = resumeAfterStopAcknowledged();
        if (resume) ignoreEndFileBeforeStart = true;
        return resume;
    }

    boolean shouldIgnoreEndFile(boolean stopPending, boolean newLoadStarted) {
        if (stopPending || newLoadStarted || !ignoreEndFileBeforeStart) return false;
        ignoreEndFileBeforeStart = false;
        return true;
    }

    void onStartFile() {
        prepareDeferred = false;
        ignoreEndFileBeforeStart = false;
    }

    void cancelDeferredPrepare() {
        prepareDeferred = false;
    }

    void reset() {
        prepareDeferred = false;
        ignoreEndFileBeforeStart = false;
    }
}
