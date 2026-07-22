package com.fongmi.android.tv.player.exo;

import android.net.Uri;

import androidx.media3.common.C;
import androidx.media3.common.PriorityTaskManager;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PriorityTaskDataSourceTest {

    @Before
    public void setUp() {
        PriorityTaskDataSource.resetDiagnostics();
    }

    @Test
    public void preloadCanReadWhilePlaybackConnectionIsOpenButIdle() throws Exception {
        PriorityTaskManager manager = new PriorityTaskManager();
        PriorityTaskDataSource preload = source(new StubDataSource(), manager, C.PRIORITY_PLAYBACK_PRELOAD, true);
        PriorityTaskDataSource playback = source(new StubDataSource(), manager, C.PRIORITY_PLAYBACK, false);
        preload.open(null);
        playback.open(null);

        assertEquals(C.RESULT_END_OF_INPUT, preload.read(new byte[1], 0, 1));

        playback.close();
        preload.close();
    }

    @Test
    public void preloadWaitsOnlyWhilePlaybackReadIsActive() throws Exception {
        PriorityTaskManager manager = new PriorityTaskManager();
        BlockingDataSource playbackUpstream = new BlockingDataSource();
        PriorityTaskDataSource preload = source(new StubDataSource(), manager, C.PRIORITY_PLAYBACK_PRELOAD, true);
        PriorityTaskDataSource playback = source(playbackUpstream, manager, C.PRIORITY_PLAYBACK, false);
        preload.open(null);
        playback.open(null);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch playbackCompleted = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        executor.execute(() -> {
            try {
                playback.read(new byte[1], 0, 1);
            } catch (IOException ignored) {
            } finally {
                playbackCompleted.countDown();
            }
        });
        assertTrue(playbackUpstream.readStarted.await(2, TimeUnit.SECONDS));
        executor.execute(() -> {
            try {
                preload.read(new byte[1], 0, 1);
            } catch (IOException ignored) {
            } finally {
                completed.countDown();
            }
        });

        assertFalse(completed.await(200, TimeUnit.MILLISECONDS));
        playbackUpstream.releaseRead.countDown();
        assertTrue(playbackCompleted.await(2, TimeUnit.SECONDS));
        assertTrue(completed.await(2, TimeUnit.SECONDS));
        PriorityTaskDataSource.DiagnosticSnapshot diagnostics = PriorityTaskDataSource.getDiagnosticSnapshot();
        assertEquals(1, diagnostics.waitCount());
        assertTrue(diagnostics.waitTotalMs() >= 50);
        playback.close();
        preload.close();
        executor.shutdownNow();
    }

    @Test
    public void upstreamIoFailureRemainsIoFailure() throws Exception {
        PriorityTaskManager manager = new PriorityTaskManager();
        PriorityTaskDataSource preload = source(new StubDataSource(new IOException("network failed")), manager, C.PRIORITY_PLAYBACK_PRELOAD, true);
        preload.open(null);
        try {
            preload.read(new byte[1], 0, 1);
            fail("Expected upstream failure");
        } catch (IOException e) {
            assertEquals("network failed", e.getMessage());
        } finally {
            preload.close();
        }
    }

    @Test
    public void closingPreloadUnblocksPriorityWait() throws Exception {
        PriorityTaskManager manager = new PriorityTaskManager();
        BlockingDataSource playbackUpstream = new BlockingDataSource();
        PriorityTaskDataSource preload = source(new StubDataSource(), manager, C.PRIORITY_PLAYBACK_PRELOAD, true);
        PriorityTaskDataSource playback = source(playbackUpstream, manager, C.PRIORITY_PLAYBACK, false);
        preload.open(null);
        playback.open(null);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch completed = new CountDownLatch(1);
        executor.execute(() -> {
            try {
                playback.read(new byte[1], 0, 1);
            } catch (IOException ignored) {
            }
        });
        assertTrue(playbackUpstream.readStarted.await(2, TimeUnit.SECONDS));
        executor.execute(() -> {
            try {
                preload.read(new byte[1], 0, 1);
            } catch (IOException ignored) {
            } finally {
                completed.countDown();
            }
        });

        assertFalse(completed.await(200, TimeUnit.MILLISECONDS));
        preload.close();
        assertTrue(completed.await(2, TimeUnit.SECONDS));
        assertEquals(1, PriorityTaskDataSource.getDiagnosticSnapshot().waitCount());
        playbackUpstream.releaseRead.countDown();
        playback.close();
        executor.shutdownNow();
    }

    private static PriorityTaskDataSource source(DataSource upstream, PriorityTaskManager manager, int priority, boolean waitWhenPreempted) {
        return new PriorityTaskDataSource(upstream, manager, priority, waitWhenPreempted);
    }

    private static final class StubDataSource implements DataSource {

        private final IOException readError;

        StubDataSource() {
            this(null);
        }

        StubDataSource(IOException readError) {
            this.readError = readError;
        }

        @Override
        public void addTransferListener(TransferListener transferListener) {
        }

        @Override
        public long open(DataSpec dataSpec) {
            return C.LENGTH_UNSET;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (readError != null) throw readError;
            return C.RESULT_END_OF_INPUT;
        }

        @Override
        public Uri getUri() {
            return null;
        }

        @Override
        public Map<String, List<String>> getResponseHeaders() {
            return Collections.emptyMap();
        }

        @Override
        public void close() {
        }
    }

    private static final class BlockingDataSource implements DataSource {

        private final CountDownLatch readStarted = new CountDownLatch(1);
        private final CountDownLatch releaseRead = new CountDownLatch(1);

        @Override
        public void addTransferListener(TransferListener transferListener) {
        }

        @Override
        public long open(DataSpec dataSpec) {
            return C.LENGTH_UNSET;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            readStarted.countDown();
            try {
                releaseRead.await();
                return C.RESULT_END_OF_INPUT;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
        }

        @Override
        public Uri getUri() {
            return null;
        }

        @Override
        public Map<String, List<String>> getResponseHeaders() {
            return Collections.emptyMap();
        }

        @Override
        public void close() {
        }
    }
}
