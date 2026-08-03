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
    public void playbackEpisodeMatchAcceptsRefreshedUrlForSameEpisode() {
        Episode saved = Episode.create("第9集", "old-url");
        Episode refreshed = Episode.create("第9集", "new-url");

        assertTrue(refreshed.matchesPlayback(saved));
    }

    @Test
    public void ordinaryEpisodeMatchStaysUrlStrictWhilePlaybackResumeIsTolerant() {
        Episode saved = Episode.create("第9集", "old-url");
        Episode refreshed = Episode.create("第9集", "new-url");

        assertFalse(refreshed.matches(saved));
        assertTrue(refreshed.matchesPlayback(saved));
    }

    @Test
    public void playbackEpisodeMatchRejectsBlankIdentityWithDifferentUrls() {
        Episode saved = Episode.create("", "old-url");
        Episode unrelated = Episode.create("", "new-url");

        assertFalse(unrelated.matchesPlayback(saved));
    }

    @Test
    public void playbackEpisodeMatchAcceptsSameEpisodeNumberAcrossLabels() {
        Episode saved = Episode.create("第9集", "old-url");
        Episode refreshed = Episode.create("[277.1MB] 9. xxx", "new-url");

        assertTrue(refreshed.matchesPlayback(saved));
    }

    @Test
    public void playbackEpisodeMatchRejectsSameTmdbNumberFromDifferentSeasons() {
        Episode firstSeason = Episode.create("第2集", "old-url");
        firstSeason.setTmdbEpisode(new TmdbEpisode(2, "", "", "", "", 0, 0, 101, 1));
        Episode secondSeason = Episode.create("第2集", "new-url");
        secondSeason.setTmdbEpisode(new TmdbEpisode(2, "", "", "", "", 0, 0, 202, 2));

        assertFalse(secondSeason.matches(firstSeason));
        assertFalse(secondSeason.matchesPlayback(firstSeason));
    }

    @Test
    public void playbackEpisodeMatchRejectsSpecialAndRegularSeasonWithSameNumber() {
        Episode special = Episode.create("特别篇第2集", "special-url");
        special.setTmdbEpisode(new TmdbEpisode(2, "", "", "", "", 0, 0, 100, 0));
        Episode regular = Episode.create("第2集", "regular-url");
        regular.setTmdbEpisode(new TmdbEpisode(2, "", "", "", "", 0, 0, 200, 1));

        assertFalse(regular.matches(special));
        assertFalse(regular.matchesPlayback(special));
    }

    @Test
    public void playbackEpisodeMatchAllowsUnknownSeasonWhenTmdbEpisodeNumberMatches() {
        Episode unknownSeason = Episode.create("源站第2集", "old-url");
        unknownSeason.setTmdbEpisode(new TmdbEpisode(2, "", "", "", "", 0, 0, 0, -1));
        Episode knownSeason = Episode.create("第2集", "new-url");
        knownSeason.setTmdbEpisode(new TmdbEpisode(2, "", "", "", "", 0, 0, 202, 2));

        assertTrue(knownSeason.matchesPlayback(unknownSeason));
    }

    @Test
    public void playbackEpisodeMatchRejectsDifferentEpisode() {
        Episode saved = Episode.create("第9集", "old-url");
        Episode different = Episode.create("第10集", "new-url");

        assertFalse(different.matchesPlayback(saved));
    }

    @Test
    public void findPlaybackCandidatePrefersBoundTmdbEpisodeNumber() {
        History wrong = history("site-a@@vod@@1", "武神主宰", "第19集", "old-url-19", 90_000, 300_000);
        History expected = history("site-b@@vod@@1", "武神主宰", "第20集", "old-url-20", 120_000, 300_000);
        Episode current = Episode.create("源站编号203", "new-url-20");
        current.setTmdbEpisode(new TmdbEpisode(20, "众人在燕歌坊遇刺客", "", "", "", 0, 0));

        History result = History.findPlaybackCandidate("site-c@@vod@@1", List.of(wrong, expected), List.of(flag(current)));

        assertEquals("第20集", result.getVodRemarks());
        assertEquals(120_000, result.getPosition());
    }

    @Test
    public void findPlaybackCandidateRejectsMatchingLabelFromDifferentTmdbSeason() {
        History wrongSeason = history("site-a@@vod@@1", "示例剧", "第2集", "old-url", 90_000, 300_000);
        wrongSeason.setTmdbSeasonNumber(1);
        wrongSeason.setTmdbEpisodeNumber(2);
        History expected = history("site-b@@vod@@1", "示例剧", "第2集", "new-url", 120_000, 300_000);
        expected.setTmdbSeasonNumber(2);
        expected.setTmdbEpisodeNumber(2);
        Episode current = Episode.create("第2集", "target-url");
        current.setTmdbEpisode(new TmdbEpisode(2, "", "", "", "", 0, 0, 202, 2));

        History result = History.findPlaybackCandidate("site-c@@vod@@1", List.of(wrongSeason, expected), List.of(flag(current)));

        assertEquals(120_000, result.getPosition());
    }

    @Test
    public void flagFindPrefersTmdbPositionOverConflictingUrl() {
        Episode wrongSeason = Episode.create("第2集", "shared-url");
        wrongSeason.setTmdbEpisode(new TmdbEpisode(2, "", "", "", "", 0, 0, 101, 1));
        Episode expected = Episode.create("第2集", "expected-url");
        expected.setTmdbEpisode(new TmdbEpisode(2, "", "", "", "", 0, 0, 202, 2));
        Episode target = Episode.create("第2集", "shared-url");
        target.setTmdbEpisode(new TmdbEpisode(2, "", "", "", "", 0, 0, 202, 2));

        assertEquals(expected, flag(wrongSeason, expected).find(target, true));
    }

    @Test
    public void flagFindRejectsSingleBoundEpisodeFromDifferentTmdbSeason() {
        Episode wrongSeason = Episode.create("第2集", "shared-url");
        wrongSeason.setTmdbEpisode(new TmdbEpisode(2, "", "", "", "", 0, 0, 101, 1));
        Episode target = Episode.create("第2集", "shared-url");
        target.setTmdbEpisode(new TmdbEpisode(2, "", "", "", "", 0, 0, 202, 2));

        assertEquals(null, flag(wrongSeason).find(target, true));
    }

    @Test
    public void flagFindUsesExactUnboundUrlBeforeExtractedCandidateNumber() {
        Episode extractedNumber = Episode.create("第2集", "other-url");
        Episode exactUrl = Episode.create("特别篇", "shared-url");
        Episode target = Episode.create("第2集", "shared-url");
        target.setTmdbEpisode(new TmdbEpisode(2, "", "", "", "", 0, 0, 202, 2));

        assertEquals(exactUrl, flag(extractedNumber, exactUrl).find(target, true));
    }

    @Test
    public void canonicalTmdbPositionCanBeStoredWithoutBindingSourceEpisodeMetadata() {
        History history = history("site@@vod@@1", "示例剧", "源站编号203", "url-20", 120_000, 300_000);
        Episode sourceEpisode = Episode.create("源站编号203", "url-20");

        assertTrue(history.setTmdbEpisodePosition(2, 20));

        assertEquals(2, history.getTmdbSeasonNumber());
        assertEquals(20, history.getTmdbEpisodeNumber());
        assertEquals(null, sourceEpisode.getTmdbEpisode());
    }

    @Test
    public void tmdbEpisodePositionSurvivesCopyAndClearsWhenEpisodeChanges() {
        History history = history("site@@vod@@1", "示例剧", "源站编号203", "url-20", 120_000, 300_000);
        Episode episode = Episode.create("源站编号203", "url-20");
        episode.setTmdbEpisode(new TmdbEpisode(20, "标准标题", "", "", "", 0, 0, 200, 2));

        assertTrue(history.setTmdbEpisodePosition(episode));

        History copy = history.copy();
        assertEquals(2, copy.getTmdbSeasonNumber());
        assertEquals(20, copy.getTmdbEpisodeNumber());
        assertEquals(20, copy.getEpisode().getTmdbEpisode().getNumber());

        copy.setTmdbEpisodePosition(Episode.create("下一集", "url-21"));
        assertEquals(0, copy.getTmdbSeasonNumber());
        assertEquals(0, copy.getTmdbEpisodeNumber());
    }

    @Test
    public void tmdbEpisodePositionPreservesUnknownSeason() {
        History history = history("site@@vod@@1", "示例剧", "第2集", "url-2", 120_000, 300_000);
        Episode episode = Episode.create("第2集", "url-2");
        episode.setTmdbEpisode(new TmdbEpisode(2, "", "", "", "", 0, 0, 200, -1));

        history.setTmdbEpisodePosition(episode);

        assertEquals(-1, history.getTmdbSeasonNumber());
        assertEquals(-1, history.getEpisode().getTmdbEpisode().getSeasonNumber());
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

    @Test
    public void findPlaybackCandidateRejectsOtherSeasonWhenTargetSeasonIsKnownButEpisodesAreUnbound() {
        History firstSeason = history("site-a@@vod@@season1", "示例剧", "第5集", "old-url", 90_000, 300_000);
        firstSeason.setTmdbSeasonNumber(1);
        firstSeason.setTmdbEpisodeNumber(5);
        Episode current = Episode.create("第5集", "season2-url");

        History result = History.findPlaybackCandidate(
                "site-b@@vod@@season2", List.of(firstSeason), List.of(flag(current)), 2);

        assertEquals(null, result);
    }

    @Test
    public void findPlaybackCandidateAllowsSameSeasonAcrossSourcesBeforeCurrentEpisodesAreBound() {
        History secondSeason = history("site-a@@vod@@season2", "示例剧", "第5集", "old-url", 90_000, 300_000);
        secondSeason.setTmdbSeasonNumber(2);
        secondSeason.setTmdbEpisodeNumber(5);
        Episode current = Episode.create("第5集", "new-url");

        History result = History.findPlaybackCandidate(
                "site-b@@vod@@season2", List.of(secondSeason), List.of(flag(current)), 2);

        assertEquals(90_000, result.getPosition());
        assertEquals(2, result.getTmdbSeasonNumber());
    }

    @Test
    public void findPlaybackCandidateDoesNotCrossSourceCopyLegacyUnknownSeasonWhenTargetSeasonIsKnown() {
        History legacy = history("site-a@@vod@@legacy", "示例剧", "第5集", "old-url", 90_000, 300_000);
        Episode current = Episode.create("第5集", "new-url");

        History result = History.findPlaybackCandidate(
                "site-b@@vod@@season2", List.of(legacy), List.of(flag(current)), 2);

        assertEquals(null, result);
    }

    @Test
    public void findPlaybackCandidateRejectsRegularSeasonWhenTargetIsSpecials() {
        History regular = history("site-a@@vod@@season1", "示例剧", "第5集", "old-url", 90_000, 300_000);
        regular.setTmdbSeasonNumber(1);
        regular.setTmdbEpisodeNumber(5);
        Episode current = Episode.create("特别篇第5集", "special-url");

        History result = History.findPlaybackCandidate(
                "site-b@@vod@@specials", List.of(regular), List.of(flag(current)), 0);

        assertEquals(null, result);
    }

    @Test
    public void findPlaybackCandidateAllowsSpecialSeasonAcrossSources() {
        History special = history("site-a@@vod@@specials", "示例剧", "特别篇第5集", "old-url", 90_000, 300_000);
        special.setTmdbSeasonNumber(0);
        special.setTmdbEpisodeNumber(5);
        Episode current = Episode.create("特别篇第5集", "new-url");

        History result = History.findPlaybackCandidate(
                "site-b@@vod@@specials", List.of(special), List.of(flag(current)), 0);

        assertEquals(90_000, result.getPosition());
        assertEquals(0, result.getTmdbSeasonNumber());
    }

    @Test
    public void findPlaybackCandidateKeepsSameKeyLegacyCompatibilityWhenTargetSeasonIsKnown() {
        History legacy = history("site-a@@vod@@season2", "示例剧", "第5集", "old-url", 90_000, 300_000);
        Episode current = Episode.create("第5集", "new-url");

        History result = History.findPlaybackCandidate(
                "site-a@@vod@@season2", List.of(legacy), List.of(flag(current)), 2);

        assertEquals(90_000, result.getPosition());
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
