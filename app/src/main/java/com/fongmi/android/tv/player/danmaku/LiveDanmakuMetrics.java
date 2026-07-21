package com.fongmi.android.tv.player.danmaku;

import java.util.concurrent.atomic.AtomicLong;

public final class LiveDanmakuMetrics {

    private final AtomicLong received = new AtomicLong();
    private final AtomicLong parsed = new AtomicLong();
    private final AtomicLong invalid = new AtomicLong();
    private final AtomicLong normal = new AtomicLong();
    private final AtomicLong superChat = new AtomicLong();
    private final AtomicLong online = new AtomicLong();
    private final AtomicLong queued = new AtomicLong();
    private final AtomicLong overflow = new AtomicLong();
    private final AtomicLong stale = new AtomicLong();
    private final AtomicLong batches = new AtomicLong();
    private final AtomicLong batchedMessages = new AtomicLong();
    private final AtomicLong parseNanos = new AtomicLong();
    private final AtomicLong maxParseNanos = new AtomicLong();
    private final AtomicLong mainDelayMs = new AtomicLong();
    private final AtomicLong maxMainDelayMs = new AtomicLong();
    private final AtomicLong retryWaits = new AtomicLong();
    private volatile LiveDanmakuWebSocketSession.State state = LiveDanmakuWebSocketSession.State.IDLE;
    private volatile long stateSinceMs;
    private volatile long openSinceMs;
    private volatile int lastCode = -1;

    public void onFrame() {
        received.incrementAndGet();
    }

    public void onParse(LiveDanmakuParser.Result result, long elapsedNanos) {
        parseNanos.addAndGet(Math.max(0L, elapsedNanos));
        maxParseNanos.accumulateAndGet(Math.max(0L, elapsedNanos), Math::max);
        if (result == null || !result.isAccepted()) {
            invalid.incrementAndGet();
            return;
        }
        parsed.incrementAndGet();
        if (result.kind() == LiveDanmakuParser.Kind.ONLINE) {
            online.incrementAndGet();
        } else if (result.message().type() == LiveDanmakuMessage.Type.SUPER_CHAT) {
            superChat.incrementAndGet();
        } else {
            normal.incrementAndGet();
        }
    }

    public void onOffer(LiveDanmakuBuffer.OfferResult result) {
        if (result == LiveDanmakuBuffer.OfferResult.QUEUED) queued.incrementAndGet();
        else if (result == LiveDanmakuBuffer.OfferResult.DROPPED_OLDEST) {
            queued.incrementAndGet();
            overflow.incrementAndGet();
        } else stale.incrementAndGet();
    }

    public void onBatch(int size, long delayMs) {
        batches.incrementAndGet();
        batchedMessages.addAndGet(Math.max(0, size));
        mainDelayMs.addAndGet(Math.max(0L, delayMs));
        maxMainDelayMs.accumulateAndGet(Math.max(0L, delayMs), Math::max);
    }

    public void onState(LiveDanmakuWebSocketSession.State state, int code, long nowMs) {
        this.state = state;
        this.lastCode = code;
        stateSinceMs = nowMs;
        if (state == LiveDanmakuWebSocketSession.State.OPEN) openSinceMs = nowMs;
        if (state == LiveDanmakuWebSocketSession.State.RETRY_WAIT) retryWaits.incrementAndGet();
        if (state == LiveDanmakuWebSocketSession.State.STOPPED || state == LiveDanmakuWebSocketSession.State.RELEASED) openSinceMs = 0L;
    }

    public Snapshot snapshotAndReset(long nowMs) {
        long receivedCount = received.getAndSet(0L);
        long parsedCount = parsed.getAndSet(0L);
        long invalidCount = invalid.getAndSet(0L);
        long batchCount = batches.getAndSet(0L);
        long parseTotal = parseNanos.getAndSet(0L);
        long delayTotal = mainDelayMs.getAndSet(0L);
        return new Snapshot(
                state,
                Math.max(0L, nowMs - stateSinceMs),
                openSinceMs <= 0L ? 0L : Math.max(0L, nowMs - openSinceMs),
                lastCode,
                receivedCount,
                parsedCount,
                invalidCount,
                normal.getAndSet(0L),
                superChat.getAndSet(0L),
                online.getAndSet(0L),
                queued.getAndSet(0L),
                overflow.getAndSet(0L),
                stale.getAndSet(0L),
                batchCount,
                batchedMessages.getAndSet(0L),
                receivedCount == 0L ? 0L : parseTotal / receivedCount,
                maxParseNanos.getAndSet(0L),
                batchCount == 0L ? 0L : delayTotal / batchCount,
                maxMainDelayMs.getAndSet(0L),
                retryWaits.getAndSet(0L));
    }

    public record Snapshot(LiveDanmakuWebSocketSession.State state, long stateDurationMs, long openDurationMs, int lastCode, long received, long parsed, long invalid, long normal, long superChat, long online, long queued, long overflow, long stale, long batches, long batchedMessages, long averageParseNanos, long maxParseNanos, long averageMainDelayMs, long maxMainDelayMs, long retryWaits) {
    }
}
