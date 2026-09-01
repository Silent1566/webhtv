package com.fongmi.android.tv.ad.audio;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 记录音频/语音通道最近命中的区间，供广告反馈的归因通道回读。
 *
 * <p>音频子系统本身是「检测到就跳过」的实时链路，命中过后不留痕；而广告反馈是用户
 * 事后按下按钮才发起的，那时候候选早已消费完。没有这份记录，反馈侧的
 * {@code AudioIntervalFact} 只能永远是 {@code unavailable()} —— 实测就是这样：
 * 音频通道从未参与过任何一次归因。
 *
 * <p>只保留最近 {@value #MAX_ENTRIES} 条：反馈只关心用户刚看到的那段，保留整场播放
 * 的命中记录既无用又会随时长无界增长。
 *
 * <p>按 {@code sessionId} 分代作废：换集换源后上一集的命中不能用来解释这一集的区间。
 */
public final class AdAudioMatchLog {

    /** 一次广告插播通常只有个位数候选，16 条足够覆盖用户回看的范围。 */
    private static final int MAX_ENTRIES = 16;

    /**
     * 一次命中。
     *
     * @param ruleId  命中的规则 id；语音通道恒为 {@code SpeechAdSignalProvider.RULE_ID}
     * @param startMs 命中区间起点
     * @param endMs   命中区间终点
     */
    public record Match(String ruleId, long startMs, long endMs) {
    }

    private final Deque<Match> matches = new ArrayDeque<>();
    private long sessionId = Long.MIN_VALUE;

    /** 记录一次命中，{@code sessionId} 变化时先清空旧代。 */
    public synchronized void record(long sessionId, String ruleId, long startMs, long endMs) {
        if (ruleId == null || ruleId.isBlank()) return;
        if (this.sessionId != sessionId) {
            matches.clear();
            this.sessionId = sessionId;
        }
        if (matches.size() >= MAX_ENTRIES) matches.removeFirst();
        matches.addLast(new Match(ruleId, startMs, endMs));
    }

    /** 换集/换源时作废，避免把上一集的命中算到这一集头上。 */
    public synchronized void clear() {
        matches.clear();
        sessionId = Long.MIN_VALUE;
    }

    /**
     * 与 {@code [startMs, endMs)} 有重叠的命中规则 id，去重且保持命中顺序。
     *
     * <p>用重叠而非包含：用户框选的边界是手动的，与检测出的区间不会严格对齐。
     */
    public synchronized List<String> matchedRuleIds(long startMs, long endMs) {
        if (endMs <= startMs) return List.of();
        Set<String> ids = new LinkedHashSet<>();
        for (Match match : matches) {
            if (match.endMs() > startMs && match.startMs() < endMs) ids.add(match.ruleId());
        }
        return List.copyOf(ids);
    }

    /** 与区间重叠的命中次数，语音通道用它表达「命中几次」。 */
    public synchronized int hitCount(String ruleId, long startMs, long endMs) {
        if (ruleId == null || endMs <= startMs) return 0;
        int count = 0;
        for (Match match : matches) {
            if (!ruleId.equals(match.ruleId())) continue;
            if (match.endMs() > startMs && match.startMs() < endMs) count++;
        }
        return count;
    }

    /** 最近一次与区间重叠的命中起点，供起点推断使用；无则返回 -1。 */
    public synchronized long latestOverlappingStartMs(long endMs) {
        long best = -1L;
        for (Match match : matches) {
            if (match.startMs() < endMs && match.startMs() > best) best = match.startMs();
        }
        return best;
    }

    /** 当前保留的命中快照，供诊断与单测。 */
    public synchronized List<Match> snapshot() {
        return new ArrayList<>(matches);
    }
}
