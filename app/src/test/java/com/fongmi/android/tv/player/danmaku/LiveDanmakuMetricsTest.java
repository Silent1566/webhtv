package com.fongmi.android.tv.player.danmaku;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LiveDanmakuMetricsTest {

    @Test
    public void aggregatesAndResetsIntervalCountersWithoutMessageContent() {
        LiveDanmakuMetrics metrics = new LiveDanmakuMetrics();
        metrics.onState(LiveDanmakuWebSocketSession.State.OPEN, 101, 100L);
        metrics.onFrame();
        metrics.onFrame();
        metrics.onParse(LiveDanmakuParser.parse("{\"type\":\"chat\",\"message\":\"secret text\"}", 1L, 1L), 1_000L);
        metrics.onParse(LiveDanmakuParser.parse("bad", 1L, 1L), 2_000L);
        metrics.onOffer(LiveDanmakuBuffer.OfferResult.DROPPED_OLDEST);
        metrics.onBatch(3, 7L);

        LiveDanmakuMetrics.Snapshot first = metrics.snapshotAndReset(200L);
        LiveDanmakuMetrics.Snapshot second = metrics.snapshotAndReset(300L);

        assertEquals(2L, first.received());
        assertEquals(1L, first.parsed());
        assertEquals(1L, first.invalid());
        assertEquals(1L, first.normal());
        assertEquals(1L, first.overflow());
        assertEquals(3L, first.batchedMessages());
        assertEquals(100L, first.openDurationMs());
        assertEquals(0L, second.received());
        assertEquals(200L, second.openDurationMs());
    }
}
