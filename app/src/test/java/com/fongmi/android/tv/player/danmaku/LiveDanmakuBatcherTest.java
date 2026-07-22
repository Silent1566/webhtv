package com.fongmi.android.tv.player.danmaku;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LiveDanmakuBatcherTest {

    @Test
    public void coalescesManyRequestsIntoOnePriorityOrderedBatch() throws Exception {
        LiveDanmakuBuffer buffer = new LiveDanmakuBuffer(8, 2);
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicInteger callbacks = new AtomicInteger();
        AtomicReference<List<LiveDanmakuMessage>> result = new AtomicReference<>();
        LiveDanmakuBatcher batcher = new LiveDanmakuBatcher(buffer, (generation, messages) -> {
            callbacks.incrementAndGet();
            result.set(messages);
            delivered.countDown();
        }, 10L, 8, () -> 1L);
        buffer.reset(3L);
        batcher.reset(3L);
        buffer.offer(message(3L, "one", LiveDanmakuMessage.Type.NORMAL));
        buffer.offer(message(3L, "priority", LiveDanmakuMessage.Type.SUPER_CHAT));
        buffer.offer(message(3L, "two", LiveDanmakuMessage.Type.NORMAL));

        batcher.requestDrain(3L);
        batcher.requestDrain(3L);
        batcher.requestDrain(3L);

        assertTrue(delivered.await(1L, TimeUnit.SECONDS));
        assertEquals(1, callbacks.get());
        assertEquals(List.of("priority", "one", "two"), result.get().stream().map(LiveDanmakuMessage::text).toList());
        batcher.release();
    }

    private static LiveDanmakuMessage message(long generation, String text, LiveDanmakuMessage.Type type) {
        return new LiveDanmakuMessage(type, text, 0xFFFFFFFF, 1L, generation);
    }
}
