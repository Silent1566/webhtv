package com.fongmi.android.tv.player.danmaku;

import android.os.SystemClock;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

public final class LiveDanmakuBatcher {

    public static final long DEFAULT_BATCH_DELAY_MS = 50L;
    public static final int DEFAULT_BATCH_SIZE = 32;

    private final LiveDanmakuBuffer buffer;
    private final Listener listener;
    private final ScheduledExecutorService scheduler;
    private final long batchDelayMs;
    private final int batchSize;
    private final LongSupplier clock;
    private ScheduledFuture<?> drainFuture;
    private long generation = -1L;
    private boolean released;

    public LiveDanmakuBatcher(LiveDanmakuBuffer buffer, Listener listener) {
        this(buffer, listener, DEFAULT_BATCH_DELAY_MS, DEFAULT_BATCH_SIZE, SystemClock::elapsedRealtime);
    }

    LiveDanmakuBatcher(LiveDanmakuBuffer buffer, Listener listener, long batchDelayMs, int batchSize) {
        this(buffer, listener, batchDelayMs, batchSize, SystemClock::elapsedRealtime);
    }

    LiveDanmakuBatcher(LiveDanmakuBuffer buffer, Listener listener, long batchDelayMs, int batchSize, LongSupplier clock) {
        this.buffer = buffer;
        this.listener = listener;
        this.batchDelayMs = Math.max(0L, batchDelayMs);
        this.batchSize = Math.max(1, batchSize);
        this.clock = clock;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "live-danmaku-batch");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized void reset(long generation) {
        if (released) return;
        cancelLocked();
        this.generation = generation;
    }

    public synchronized void clear() {
        cancelLocked();
        generation = -1L;
    }

    public synchronized void requestDrain(long generation) {
        if (released || generation != this.generation || drainFuture != null) return;
        scheduleLocked(generation);
    }

    public synchronized void release() {
        if (released) return;
        released = true;
        cancelLocked();
        generation = -1L;
        scheduler.shutdownNow();
    }

    private void scheduleLocked(long expectedGeneration) {
        drainFuture = scheduler.schedule(() -> drain(expectedGeneration), batchDelayMs, TimeUnit.MILLISECONDS);
    }

    private void drain(long expectedGeneration) {
        synchronized (this) {
            if (released || generation != expectedGeneration) return;
            drainFuture = null;
        }
        List<LiveDanmakuMessage> batch = buffer.drain(batchSize, clock.getAsLong());
        if (!batch.isEmpty()) listener.onBatch(expectedGeneration, batch);
        synchronized (this) {
            if (!released && generation == expectedGeneration && buffer.size() > 0 && drainFuture == null) scheduleLocked(expectedGeneration);
        }
    }

    private void cancelLocked() {
        if (drainFuture != null) drainFuture.cancel(false);
        drainFuture = null;
    }

    public interface Listener {

        void onBatch(long generation, List<LiveDanmakuMessage> messages);
    }
}
