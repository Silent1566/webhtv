package com.fongmi.android.tv.player.danmaku;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LiveDanmakuBufferTest {

    @Test
    public void rejectsMessagesFromOldGeneration() {
        LiveDanmakuBuffer buffer = new LiveDanmakuBuffer(2, 1);
        buffer.reset(2L);

        assertEquals(LiveDanmakuBuffer.OfferResult.STALE, buffer.offer(message(1L, "old", LiveDanmakuMessage.Type.NORMAL)));
        assertEquals(0, buffer.size());
    }

    @Test
    public void dropsOldestNormalMessageWhenCapacityIsReached() {
        LiveDanmakuBuffer buffer = new LiveDanmakuBuffer(2, 1);
        buffer.reset(1L);
        buffer.offer(message(1L, "one", LiveDanmakuMessage.Type.NORMAL));
        buffer.offer(message(1L, "two", LiveDanmakuMessage.Type.NORMAL));

        assertEquals(LiveDanmakuBuffer.OfferResult.DROPPED_OLDEST, buffer.offer(message(1L, "three", LiveDanmakuMessage.Type.NORMAL)));
        assertEquals(List.of("two", "three"), buffer.drain(10, 1L).stream().map(LiveDanmakuMessage::text).toList());
    }

    @Test
    public void drainsPriorityBeforeNormalWithoutMixingCapacities() {
        LiveDanmakuBuffer buffer = new LiveDanmakuBuffer(2, 1);
        buffer.reset(1L);
        buffer.offer(message(1L, "normal", LiveDanmakuMessage.Type.NORMAL));
        buffer.offer(message(1L, "priority", LiveDanmakuMessage.Type.SUPER_CHAT));

        assertEquals(List.of("priority", "normal"), buffer.drain(2, 1L).stream().map(LiveDanmakuMessage::text).toList());
        assertEquals(0, buffer.size());
    }

    @Test
    public void tracksOnlineSeparatelyAndClearsOnGenerationReset() {
        LiveDanmakuBuffer buffer = new LiveDanmakuBuffer(2, 1);
        buffer.reset(4L);

        assertTrue(buffer.updateOnline(4L, 888L));
        assertFalse(buffer.updateOnline(3L, 999L));
        assertEquals(888L, buffer.latestOnline());
        buffer.reset(5L);
        assertEquals(-1L, buffer.latestOnline());
    }

    @Test
    public void discardsPausedBacklogWithoutInvalidatingCurrentGeneration() {
        LiveDanmakuBuffer buffer = new LiveDanmakuBuffer(2, 1);
        buffer.reset(6L);
        buffer.offer(message(6L, "before pause", LiveDanmakuMessage.Type.NORMAL));

        buffer.discardPending();

        assertEquals(0, buffer.size());
        assertEquals(LiveDanmakuBuffer.OfferResult.QUEUED, buffer.offer(message(6L, "after resume", LiveDanmakuMessage.Type.NORMAL)));
    }

    @Test
    public void dropsExpiredMessagesAndReportsAggregateStats() {
        LiveDanmakuBuffer buffer = new LiveDanmakuBuffer(2, 1, 100L, 500L);
        buffer.reset(1L);
        buffer.offer(new LiveDanmakuMessage(LiveDanmakuMessage.Type.NORMAL, "expired", 0xFFFFFFFF, 10L, 1L));
        buffer.offer(new LiveDanmakuMessage(LiveDanmakuMessage.Type.NORMAL, "fresh", 0xFFFFFFFF, 150L, 1L));
        buffer.offer(new LiveDanmakuMessage(LiveDanmakuMessage.Type.SUPER_CHAT, "priority", 0xFFFFFFFF, 10L, 1L));

        assertEquals(List.of("priority", "fresh"), buffer.drain(3, 200L).stream().map(LiveDanmakuMessage::text).toList());
        LiveDanmakuBuffer.Snapshot snapshot = buffer.snapshot();
        assertEquals(1L, snapshot.droppedExpired());
        assertEquals(2L, snapshot.drained());
        assertEquals(3, snapshot.highWaterMark());
    }

    private static LiveDanmakuMessage message(long generation, String text, LiveDanmakuMessage.Type type) {
        return new LiveDanmakuMessage(type, text, 0xFFFFFFFF, 1L, generation);
    }
}
