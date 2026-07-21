package com.fongmi.android.tv.player.danmaku;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LiveDanmakuPipelineLoadTest {

    private static final int[] RATES_PER_SECOND = {10, 30, 100, 300};
    private static final int TICKS_PER_SECOND = 20;
    private static final long TICK_MS = 1_000L / TICKS_PER_SECOND;

    @Test
    public void productionPipelineHandlesTargetRatesWithoutAppQueueLoss() throws Exception {
        List<LoadResult> results = new ArrayList<>();
        for (int rate : RATES_PER_SECOND) results.add(runRate(rate));

        for (LoadResult result : results) {
            assertEquals("received rate=" + result.rate, result.rate, result.received);
            assertEquals("parsed rate=" + result.rate, result.rate, result.parsed);
            assertEquals("delivered rate=" + result.rate, result.rate, result.delivered);
            assertEquals("overflow rate=" + result.rate, 0L, result.buffer.droppedOverflow());
            assertEquals("expired rate=" + result.rate, 0L, result.buffer.droppedExpired());
            assertEquals("stale rate=" + result.rate, 0L, result.buffer.droppedStale());
            assertEquals("pending rate=" + result.rate, 0, result.buffer.normalPending() + result.buffer.priorityPending());
            assertTrue("batch cap rate=" + result.rate, result.maxBatchSize <= LiveDanmakuBatcher.DEFAULT_BATCH_SIZE);
            assertTrue("bounded high-water rate=" + result.rate,
                    result.buffer.highWaterMark() <= LiveDanmakuBuffer.DEFAULT_NORMAL_CAPACITY + LiveDanmakuBuffer.DEFAULT_PRIORITY_CAPACITY);
        }
    }

    @Test
    public void configuredBatchBudgetExceedsThreeHundredMessagesPerSecond() {
        long batchesPerSecond = 1_000L / LiveDanmakuBatcher.DEFAULT_BATCH_DELAY_MS;
        long drainBudgetPerSecond = batchesPerSecond * LiveDanmakuBatcher.DEFAULT_BATCH_SIZE;
        int messagesPerWindowAtPeak = (int) Math.ceil(300d * LiveDanmakuBatcher.DEFAULT_BATCH_DELAY_MS / 1_000d);

        assertEquals(640L, drainBudgetPerSecond);
        assertEquals(15, messagesPerWindowAtPeak);
        assertTrue(LiveDanmakuBuffer.DEFAULT_NORMAL_CAPACITY >= messagesPerWindowAtPeak * 8);
    }

    private LoadResult runRate(int rate) throws Exception {
        MockWebServer server = new MockWebServer();
        CountDownLatch serverOpen = new CountDownLatch(1);
        server.enqueue(new MockResponse.Builder().webSocketUpgrade(new ClosingWebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                serverOpen.countDown();
                Thread emitter = new Thread(() -> emitRate(webSocket, rate), "danmaku-load-" + rate);
                emitter.setDaemon(true);
                emitter.start();
            }
        }).build());
        server.start();

        LiveDanmakuBuffer buffer = new LiveDanmakuBuffer();
        CountDownLatch clientOpen = new CountDownLatch(1);
        CountDownLatch delivered = new CountDownLatch(rate);
        AtomicInteger received = new AtomicInteger();
        AtomicInteger parsed = new AtomicInteger();
        AtomicInteger deliveredCount = new AtomicInteger();
        AtomicInteger maxBatchSize = new AtomicInteger();
        List<Integer> batchSizes = new CopyOnWriteArrayList<>();
        LiveDanmakuBatcher batcher = new LiveDanmakuBatcher(buffer, (generation, messages) -> {
            batchSizes.add(messages.size());
            maxBatchSize.accumulateAndGet(messages.size(), Math::max);
            deliveredCount.addAndGet(messages.size());
            for (int i = 0; i < messages.size(); i++) delivered.countDown();
        }, LiveDanmakuBatcher.DEFAULT_BATCH_DELAY_MS, LiveDanmakuBatcher.DEFAULT_BATCH_SIZE, System::currentTimeMillis);
        LiveDanmakuWebSocketSession session = new LiveDanmakuWebSocketSession(new LiveDanmakuWebSocketSession.Listener() {
            @Override
            public void onStateChanged(LiveDanmakuWebSocketSession.State state, long generation, String url, int code, String detail) {
                if (state == LiveDanmakuWebSocketSession.State.CONNECTING) {
                    buffer.reset(generation);
                    batcher.reset(generation);
                } else if (state == LiveDanmakuWebSocketSession.State.OPEN) {
                    clientOpen.countDown();
                }
            }

            @Override
            public void onMessage(long generation, String text) {
                received.incrementAndGet();
                LiveDanmakuParser.Result result = LiveDanmakuParser.parse(text, generation, System.currentTimeMillis());
                if (!result.isAccepted() || result.kind() != LiveDanmakuParser.Kind.MESSAGE) return;
                parsed.incrementAndGet();
                if (buffer.offer(result.message()) != LiveDanmakuBuffer.OfferResult.STALE) batcher.requestDrain(generation);
            }
        });

        try {
            session.connect(server.url("/load").toString().replaceFirst("^http", "ws"));
            assertTrue("server open rate=" + rate, serverOpen.await(5L, TimeUnit.SECONDS));
            assertTrue("client open rate=" + rate, clientOpen.await(5L, TimeUnit.SECONDS));
            assertTrue("delivery complete rate=" + rate, delivered.await(5L, TimeUnit.SECONDS));
            LiveDanmakuBuffer.Snapshot snapshot = buffer.snapshot();
            System.out.printf("danmaku-load rate=%d received=%d parsed=%d delivered=%d batches=%d maxBatch=%d highWater=%d overflow=%d expired=%d%n",
                    rate, received.get(), parsed.get(), deliveredCount.get(), batchSizes.size(), maxBatchSize.get(), snapshot.highWaterMark(), snapshot.droppedOverflow(), snapshot.droppedExpired());
            return new LoadResult(rate, received.get(), parsed.get(), deliveredCount.get(), maxBatchSize.get(), snapshot);
        } finally {
            session.stop("load_complete");
            session.release();
            batcher.release();
            server.close();
        }
    }

    private static void emitRate(WebSocket webSocket, int rate) {
        int sent = 0;
        try {
            for (int tick = 1; tick <= TICKS_PER_SECOND; tick++) {
                int target = rate * tick / TICKS_PER_SECOND;
                while (sent < target) {
                    String type = sent % 20 == 19 ? "superChat" : "chat";
                    if (!webSocket.send("{\"type\":\"" + type + "\",\"message\":\"stress-" + sent + "\",\"color\":\"#FFFFFF\"}")) return;
                    sent++;
                }
                Thread.sleep(TICK_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private abstract static class ClosingWebSocketListener extends WebSocketListener {

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            webSocket.close(code, reason);
        }
    }

    private record LoadResult(int rate, int received, int parsed, int delivered, int maxBatchSize, LiveDanmakuBuffer.Snapshot buffer) {
    }
}
