package com.fongmi.android.tv.player.danmaku;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class LiveDanmakuBuffer {

    public static final int DEFAULT_NORMAL_CAPACITY = 128;
    public static final int DEFAULT_PRIORITY_CAPACITY = 16;
    public static final long DEFAULT_NORMAL_TTL_MS = 2_000L;
    public static final long DEFAULT_PRIORITY_TTL_MS = 8_000L;

    private final ArrayDeque<LiveDanmakuMessage> normal;
    private final ArrayDeque<LiveDanmakuMessage> priority;
    private final int normalCapacity;
    private final int priorityCapacity;
    private final long normalTtlMs;
    private final long priorityTtlMs;
    private long generation = -1L;
    private long latestOnline = -1L;
    private long offeredNormal;
    private long offeredPriority;
    private long onlineUpdates;
    private long droppedOverflow;
    private long droppedExpired;
    private long droppedStale;
    private long drained;
    private int highWaterMark;

    public LiveDanmakuBuffer() {
        this(DEFAULT_NORMAL_CAPACITY, DEFAULT_PRIORITY_CAPACITY, DEFAULT_NORMAL_TTL_MS, DEFAULT_PRIORITY_TTL_MS);
    }

    LiveDanmakuBuffer(int normalCapacity, int priorityCapacity) {
        this(normalCapacity, priorityCapacity, DEFAULT_NORMAL_TTL_MS, DEFAULT_PRIORITY_TTL_MS);
    }

    LiveDanmakuBuffer(int normalCapacity, int priorityCapacity, long normalTtlMs, long priorityTtlMs) {
        this.normalCapacity = Math.max(1, normalCapacity);
        this.priorityCapacity = Math.max(1, priorityCapacity);
        this.normalTtlMs = Math.max(1L, normalTtlMs);
        this.priorityTtlMs = Math.max(1L, priorityTtlMs);
        this.normal = new ArrayDeque<>(this.normalCapacity);
        this.priority = new ArrayDeque<>(this.priorityCapacity);
    }

    public synchronized void reset(long generation) {
        this.generation = generation;
        latestOnline = -1L;
        normal.clear();
        priority.clear();
        resetStatsLocked();
    }

    public synchronized void clear() {
        generation = -1L;
        latestOnline = -1L;
        normal.clear();
        priority.clear();
        resetStatsLocked();
    }

    public synchronized void discardPending() {
        normal.clear();
        priority.clear();
    }

    public synchronized OfferResult offer(LiveDanmakuMessage message) {
        if (message == null || message.generation() != generation) {
            droppedStale++;
            return OfferResult.STALE;
        }
        boolean highPriority = message.type() == LiveDanmakuMessage.Type.SUPER_CHAT;
        if (highPriority) offeredPriority++; else offeredNormal++;
        ArrayDeque<LiveDanmakuMessage> queue = highPriority ? priority : normal;
        int capacity = highPriority ? priorityCapacity : normalCapacity;
        boolean dropped = queue.size() >= capacity;
        if (dropped) {
            queue.pollFirst();
            droppedOverflow++;
        }
        queue.offerLast(message);
        highWaterMark = Math.max(highWaterMark, normal.size() + priority.size());
        return dropped ? OfferResult.DROPPED_OLDEST : OfferResult.QUEUED;
    }

    public synchronized boolean updateOnline(long generation, long online) {
        if (generation != this.generation || online < 0) return false;
        latestOnline = online;
        onlineUpdates++;
        return true;
    }

    public synchronized List<LiveDanmakuMessage> drain(int maxItems, long nowMs) {
        int limit = Math.max(0, maxItems);
        if (limit == 0 || (priority.isEmpty() && normal.isEmpty())) return List.of();
        List<LiveDanmakuMessage> result = new ArrayList<>(Math.min(limit, priority.size() + normal.size()));
        drainQueue(priority, priorityTtlMs, limit, nowMs, result);
        drainQueue(normal, normalTtlMs, limit, nowMs, result);
        drained += result.size();
        return result;
    }

    private void drainQueue(ArrayDeque<LiveDanmakuMessage> queue, long ttlMs, int limit, long nowMs, List<LiveDanmakuMessage> result) {
        while (result.size() < limit && !queue.isEmpty()) {
            LiveDanmakuMessage message = queue.removeFirst();
            long ageMs = nowMs - message.receivedAtMs();
            if (ageMs >= 0 && ageMs > ttlMs) {
                droppedExpired++;
            } else {
                result.add(message);
            }
        }
    }

    public synchronized int size() {
        return normal.size() + priority.size();
    }

    public synchronized int normalSize() {
        return normal.size();
    }

    public synchronized int prioritySize() {
        return priority.size();
    }

    public synchronized long latestOnline() {
        return latestOnline;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(generation, offeredNormal, offeredPriority, onlineUpdates, droppedOverflow, droppedExpired, droppedStale, drained, normal.size(), priority.size(), highWaterMark, latestOnline);
    }

    private void resetStatsLocked() {
        offeredNormal = 0L;
        offeredPriority = 0L;
        onlineUpdates = 0L;
        droppedOverflow = 0L;
        droppedExpired = 0L;
        droppedStale = 0L;
        drained = 0L;
        highWaterMark = 0;
    }

    public record Snapshot(long generation, long offeredNormal, long offeredPriority, long onlineUpdates, long droppedOverflow, long droppedExpired, long droppedStale, long drained, int normalPending, int priorityPending, int highWaterMark, long latestOnline) {
    }

    public enum OfferResult {
        QUEUED,
        DROPPED_OLDEST,
        STALE
    }
}
