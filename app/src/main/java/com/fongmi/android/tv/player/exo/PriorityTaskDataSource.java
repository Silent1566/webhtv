package com.fongmi.android.tv.player.exo;

import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.media3.common.PriorityTaskManager;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

final class PriorityTaskDataSource implements DataSource {

    private static final long PRIORITY_WAIT_MS = 50;
    private static final AtomicLong WAIT_COUNT = new AtomicLong();
    private static final AtomicLong WAIT_TOTAL_MS = new AtomicLong();

    private final DataSource upstream;
    private final PriorityTaskManager taskManager;
    private final AtomicBoolean registered;
    private final boolean waitWhenPreempted;
    private final int priority;

    PriorityTaskDataSource(DataSource upstream, PriorityTaskManager taskManager, int priority, boolean waitWhenPreempted) {
        this.upstream = upstream;
        this.taskManager = taskManager;
        this.priority = priority;
        this.waitWhenPreempted = waitWhenPreempted;
        this.registered = new AtomicBoolean();
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        upstream.addTransferListener(transferListener);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        if (!waitWhenPreempted) return runWithPriority(() -> upstream.open(dataSpec));
        register();
        try {
            waitForPriority();
            return upstream.open(dataSpec);
        } catch (IOException | RuntimeException e) {
            unregister();
            throw e;
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (!waitWhenPreempted) return runWithPriority(() -> upstream.read(buffer, offset, length));
        waitForPriority();
        return upstream.read(buffer, offset, length);
    }

    @Nullable
    @Override
    public Uri getUri() {
        return upstream.getUri();
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return upstream.getResponseHeaders();
    }

    @Override
    public void close() throws IOException {
        try {
            upstream.close();
        } finally {
            unregister();
        }
    }

    private void waitForPriority() throws IOException {
        long waitStartNs = 0;
        try {
            while (registered.get() && !taskManager.proceedNonBlocking(priority)) {
                if (waitStartNs == 0) waitStartNs = System.nanoTime();
                try {
                    Thread.sleep(PRIORITY_WAIT_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw interrupted(e);
                }
            }
            if (!registered.get()) throw interrupted(null);
        } finally {
            if (waitStartNs != 0) recordWait(waitStartNs);
        }
    }

    private <T> T runWithPriority(IoOperation<T> operation) throws IOException {
        taskManager.add(priority);
        try {
            taskManager.proceedOrThrow(priority);
            return operation.run();
        } finally {
            taskManager.remove(priority);
        }
    }

    private InterruptedIOException interrupted(@Nullable InterruptedException cause) {
        InterruptedIOException error = new InterruptedIOException("Preload priority wait cancelled");
        if (cause != null) error.initCause(cause);
        return error;
    }

    private void register() {
        if (registered.compareAndSet(false, true)) taskManager.add(priority);
    }

    private void unregister() {
        if (registered.compareAndSet(true, false)) taskManager.remove(priority);
    }

    static void resetDiagnostics() {
        WAIT_COUNT.set(0);
        WAIT_TOTAL_MS.set(0);
    }

    static DiagnosticSnapshot getDiagnosticSnapshot() {
        return new DiagnosticSnapshot(WAIT_COUNT.get(), WAIT_TOTAL_MS.get());
    }

    private static void recordWait(long waitStartNs) {
        WAIT_COUNT.incrementAndGet();
        WAIT_TOTAL_MS.addAndGet(Math.max(0, (System.nanoTime() - waitStartNs) / 1_000_000L));
    }

    record DiagnosticSnapshot(long waitCount, long waitTotalMs) {
    }

    private interface IoOperation<T> {

        T run() throws IOException;
    }

    static final class Factory implements DataSource.Factory {

        private final DataSource.Factory upstreamFactory;
        private final PriorityTaskManager taskManager;
        private final boolean waitWhenPreempted;
        private final int priority;

        Factory(DataSource.Factory upstreamFactory, PriorityTaskManager taskManager, int priority, boolean waitWhenPreempted) {
            this.upstreamFactory = upstreamFactory;
            this.taskManager = taskManager;
            this.priority = priority;
            this.waitWhenPreempted = waitWhenPreempted;
        }

        @Override
        public DataSource createDataSource() {
            return new PriorityTaskDataSource(upstreamFactory.createDataSource(), taskManager, priority, waitWhenPreempted);
        }
    }
}
