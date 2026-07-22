package com.fongmi.android.tv.bean;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class HistoryPlaybackTest {

    @Test
    public void untouchedHistoryUsesPersonalDefaultSpeed() {
        History history = new History();

        assertFalse(history.hasUserSpeed());
        assertEquals(3.0f, history.getPlaybackSpeed(3.0f), 0.001f);
    }

    @Test
    public void explicitNormalSpeedOverridesPersonalDefault() {
        History history = new History();

        history.setUserSpeed(1.0f);

        assertTrue(history.hasUserSpeed());
        assertEquals(1.0f, history.getPlaybackSpeed(3.0f), 0.001f);
    }

    @Test
    public void manualSpeedOverrideIsScopedToItsHistoryRecord() {
        History changed = new History();
        History untouched = new History();
        changed.setUserSpeed(1.0f);

        assertEquals(1.0f, changed.getPlaybackSpeed(3.0f), 0.001f);
        assertEquals(4.0f, untouched.getPlaybackSpeed(4.0f), 0.001f);
    }

    @Test
    public void copiedHistoryPreservesExplicitNormalSpeed() {
        History history = new History();
        history.setUserSpeed(1.0f);

        History copy = history.copy();

        assertTrue(copy.hasUserSpeed());
        assertEquals(1.0f, copy.getPlaybackSpeed(3.0f), 0.001f);
    }

    @Test
    public void legacyHistoryInfersOnlyNonNormalSpeedAsOverride() {
        History normal = new History();
        normal.setSpeed(1.0f);
        History changed = new History();
        changed.setSpeed(2.0f);

        assertFalse(normal.hasUserSpeed());
        assertEquals(3.0f, normal.getPlaybackSpeed(3.0f), 0.001f);
        assertTrue(changed.hasUserSpeed());
        assertEquals(2.0f, changed.getPlaybackSpeed(3.0f), 0.001f);
    }

    @Test
    public void findPlaybackCandidateCopiesSyncedProgressToRequestedKey() {
        History synced = history("site@@vod@@1", "武神主宰", "第2集", "url-2", 120_000, 300_000);
        Flag flag = flag(Episode.create("第1集", "url-1"), Episode.create("第2集", "url-2"));

        History result = History.findPlaybackCandidate("site@@vod@@2", List.of(synced), List.of(flag));

        assertNotSame(synced, result);
        assertEquals("site@@vod@@2", result.getKey());
        assertEquals("第2集", result.getVodRemarks());
        assertEquals("url-2", result.getEpisodeUrl());
        assertEquals(120_000, result.getPosition());
        assertEquals(300_000, result.getDuration());
    }

    @Test
    public void findPlaybackCandidatePrefersResumableHistory() {
        History empty = history("site@@vod@@1", "武神主宰", "第1集", "url-1", 0, 300_000);
        History resumable = history("site@@vod@@old", "武神主宰", "第2集", "url-2", 90_000, 300_000);
        Flag flag = flag(Episode.create("第1集", "url-1"), Episode.create("第2集", "url-2"));

        History result = History.findPlaybackCandidate("site@@vod@@2", List.of(empty, resumable), List.of(flag));

        assertEquals("site@@vod@@2", result.getKey());
        assertEquals("第2集", result.getVodRemarks());
        assertEquals(90_000, result.getPosition());
    }

    @Test
    public void replaceSameKeyDoesNotChangeKey() {
        History history = history("site@@vod@@1", "片名", "第1集", "url-1", 10_000, 300_000);

        history.replace("site@@vod@@1");

        assertEquals("site@@vod@@1", history.getKey());
    }

    @Test
    public void shouldMergeDoesNotMatchWhenDurationMissing() {
        History current = history("site@@a@@1", "同名剧", "第1集", "url-1", 10_000, 0);
        History other = history("site@@b@@1", "同名剧", "第1集", "url-2", 20_000, 300_000);

        assertFalse(other.shouldMerge(current, false));
        assertTrue(other.shouldMerge(current, true));
    }

    @Test
    public void shouldMergeMatchesSimilarDurationAcrossSources() {
        History current = history("site@@a@@1", "同名剧", "第1集", "url-1", 10_000, 300_000);
        History other = history("site@@b@@1", "同名剧", "第1集", "url-2", 20_000, 305_000);

        assertTrue(other.shouldMerge(current, false));
    }

    @Test
    public void shouldMergeSkipsSameKeyUnlessForced() {
        History current = history("site@@a@@1", "同名剧", "第1集", "url-1", 10_000, 300_000);
        History other = history("site@@a@@1", "同名剧", "第1集", "url-1", 20_000, 300_000);

        assertFalse(other.shouldMerge(current, false));
        assertTrue(other.shouldMerge(current, true));
    }

    @Test
    public void recommendationSignalsChangedIncludesStableMetadataButIgnoresProgress() {
        History before = history("site@@vod@@1", "爱情有烟火", "第1集", "url-1", 10_000, 300_000);
        before.setTypeName("剧情");
        before.setArea("中国大陆");
        before.setActor("檀健次");
        before.setDirector("张开宙");
        before.setYear("2025");

        History progressOnly = before.copy();
        progressOnly.setPosition(120_000);
        assertFalse(History.recommendationSignalsChanged(before, progressOnly));

        History enriched = before.copy();
        enriched.setDirector("张开宙,另一导演");
        assertTrue(History.recommendationSignalsChanged(before, enriched));
    }

    @Test
    public void copiedPlaybackCandidateDoesNotMergeBackIntoSourceHistory() {
        History source = history("site@@vod@@1", "武神主宰", "第2集", "url-2", 120_000, 300_000);
        History copied = History.findPlaybackCandidate("site@@vod@@2", List.of(source), List.of(flag(Episode.create("第2集", "url-2"))));

        assertFalse(source.shouldMerge(copied.copy(), false));
    }

    @Test
    public void isSameContentDetectsEpisodeLabelChanges() {
        History original = history("site@@vod@@1", "武神主宰", "第2集", "url-2", 120_000, 300_000);
        History changed = original.copy();
        changed.setVodRemarks("第3集");

        assertFalse(original.isSameContent(changed));
    }

    @Test
    public void isSameContentDetectsPlaybackProgressChanges() {
        History original = history("site@@vod@@1", "武神主宰", "第2集", "url-2", 120_000, 300_000);

        History changedPosition = original.copy();
        changedPosition.setPosition(180_000);
        assertFalse(original.isSameContent(changedPosition));

        History changedDuration = original.copy();
        changedDuration.setDuration(360_000);
        assertFalse(original.isSameContent(changedDuration));
    }

    @Test
    public void isSameContentDetectsPlaybackRouteChanges() {
        History original = history("site@@vod@@1", "武神主宰", "第2集", "url-2", 120_000, 300_000);

        History changedFlag = original.copy();
        changedFlag.setVodFlag("备用线路");
        assertFalse(original.isSameContent(changedFlag));

        History changedEpisodeUrl = original.copy();
        changedEpisodeUrl.setEpisodeUrl("url-3");
        assertFalse(original.isSameContent(changedEpisodeUrl));
    }

    @Test
    public void playbackTimeIncludesZeroPositionWhenDurationIsKnown() {
        History history = history("site@@vod@@1", "片名", "第1集", "url-1", 0, 90_000);

        assertTrue(history.hasPlaybackTime());
        assertEquals("00:00 / 01:30", history.getPlaybackTimeText());
    }

    @Test
    public void playbackTimeHidesUnsetOrNegativeValues() {
        History unsetPosition = history("site@@vod@@1", "片名", "第1集", "url-1", -1, 90_000);
        History unsetDuration = history("site@@vod@@2", "片名", "第1集", "url-1", 0, -1);

        assertFalse(unsetPosition.hasPlaybackTime());
        assertFalse(unsetDuration.hasPlaybackTime());
        assertEquals("", unsetPosition.getPlaybackTimeText());
        assertEquals("", unsetDuration.getPlaybackTimeText());
    }

    @Test
    public void playbackTimeClampsPositionToDuration() {
        History history = history("site@@vod@@1", "片名", "第1集", "url-1", 120_000, 90_000);

        assertEquals("01:30 / 01:30", history.getPlaybackTimeText());
    }

    @Test
    public void shouldMergeStillDeduplicatesIndependentMatchingHistories() {
        History source = history("site@@vod@@1", "武神主宰", "第2集", "url-2", 120_000, 300_000);
        History independent = history("site@@vod@@2", "武神主宰", "第2集", "url-2", 180_000, 300_000);

        assertTrue(source.shouldMerge(independent, false));
    }

    private static History history(String key, String name, String remarks, String episodeUrl, long position, long duration) {
        History history = new History();
        history.setKey(key);
        history.setVodName(name);
        history.setVodRemarks(remarks);
        history.setEpisodeUrl(episodeUrl);
        history.setPosition(position);
        history.setDuration(duration);
        return history;
    }

    private static Flag flag(Episode... episodes) {
        Flag flag = new Flag("source");
        flag.getEpisodes().addAll(List.of(episodes));
        return flag;
    }
}
