package com.fongmi.android.tv.ad.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * 命中记录是音频通道能参与事后归因的前提：检测链路消费完候选就丢弃，而反馈是用户
 * 按下按钮才发起的。
 */
public class AdAudioMatchLogTest {

    private static final String SPEECH = "speech-keyword";

    @Test
    public void returnsRuleIdsOverlappingTheInterval() {
        AdAudioMatchLog log = new AdAudioMatchLog();
        log.record(1L, "probe-a", 10_000, 20_000);
        log.record(1L, "probe-b", 50_000, 60_000);

        assertEquals(List.of("probe-a"), log.matchedRuleIds(15_000, 25_000));
        assertEquals(List.of("probe-b"), log.matchedRuleIds(55_000, 70_000));
        assertTrue("区间之外不该有命中", log.matchedRuleIds(30_000, 40_000).isEmpty());
    }

    /** 用户框选的边界是手动的，与检测区间不会严格对齐，所以判据是重叠而非包含。 */
    @Test
    public void countsPartialOverlapAsMatch() {
        AdAudioMatchLog log = new AdAudioMatchLog();
        log.record(1L, "probe-a", 10_000, 20_000);

        assertEquals("尾部搭上一点也算", List.of("probe-a"), log.matchedRuleIds(19_999, 30_000));
        assertEquals("头部搭上一点也算", List.of("probe-a"), log.matchedRuleIds(0, 10_001));
        assertTrue("正好错开不算", log.matchedRuleIds(20_000, 30_000).isEmpty());
        assertTrue("正好错开不算", log.matchedRuleIds(0, 10_000).isEmpty());
    }

    @Test
    public void deduplicatesRepeatedRuleButKeepsHitCount() {
        AdAudioMatchLog log = new AdAudioMatchLog();
        log.record(1L, SPEECH, 10_000, 12_000);
        log.record(1L, SPEECH, 14_000, 16_000);
        log.record(1L, SPEECH, 18_000, 20_000);

        assertEquals("规则 id 去重", List.of(SPEECH), log.matchedRuleIds(0, 30_000));
        assertEquals("次数不去重", 3, log.hitCount(SPEECH, 0, 30_000));
        assertEquals("只数重叠的", 2, log.hitCount(SPEECH, 13_000, 30_000));
    }

    /** 换集换源后上一集的命中不能用来解释这一集的区间。 */
    @Test
    public void dropsPreviousSessionOnNewSessionId() {
        AdAudioMatchLog log = new AdAudioMatchLog();
        log.record(1L, "probe-a", 10_000, 20_000);
        log.record(2L, "probe-b", 50_000, 60_000);

        assertTrue("上一代的命中必须作废", log.matchedRuleIds(10_000, 25_000).isEmpty());
        assertEquals(List.of("probe-b"), log.matchedRuleIds(50_000, 60_000));
    }

    @Test
    public void clearDropsEverything() {
        AdAudioMatchLog log = new AdAudioMatchLog();
        log.record(1L, "probe-a", 10_000, 20_000);
        log.clear();

        assertTrue(log.matchedRuleIds(0, 100_000).isEmpty());
        assertTrue(log.snapshot().isEmpty());
    }

    /** 只保留最近若干条，否则长片会让记录随时长无界增长。 */
    @Test
    public void keepsOnlyTheMostRecentEntries() {
        AdAudioMatchLog log = new AdAudioMatchLog();
        for (int i = 0; i < 40; i++) {
            log.record(1L, "probe-" + i, i * 1_000L, i * 1_000L + 500L);
        }

        assertTrue("上界必须生效", log.snapshot().size() <= 16);
        assertTrue("最早的已被挤掉", log.matchedRuleIds(0, 1_000).isEmpty());
        assertEquals("最近的仍在", List.of("probe-39"), log.matchedRuleIds(39_000, 39_500));
    }

    /** 短按只有终点，起点取最近一次早于终点的命中起点。 */
    @Test
    public void reportsLatestStartBeforeTheGivenEnd() {
        AdAudioMatchLog log = new AdAudioMatchLog();
        log.record(1L, "probe-a", 10_000, 20_000);
        log.record(1L, "probe-b", 30_000, 40_000);

        assertEquals(30_000L, log.latestOverlappingStartMs(50_000));
        assertEquals("终点之后的命中不算", 10_000L, log.latestOverlappingStartMs(25_000));
        assertEquals("没有更早的命中时返回 -1", -1L, log.latestOverlappingStartMs(5_000));
    }

    @Test
    public void ignoresBlankRuleIdAndInvalidInterval() {
        AdAudioMatchLog log = new AdAudioMatchLog();
        log.record(1L, null, 10_000, 20_000);
        log.record(1L, "  ", 10_000, 20_000);

        assertTrue(log.snapshot().isEmpty());
        assertTrue("终点不晚于起点时无命中", log.matchedRuleIds(20_000, 10_000).isEmpty());
    }
}
