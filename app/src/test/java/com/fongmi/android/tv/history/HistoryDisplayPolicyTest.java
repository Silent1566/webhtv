package com.fongmi.android.tv.history;

import com.fongmi.android.tv.bean.History;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class HistoryDisplayPolicyTest {

    @Test
    public void aggregationUsesMediaTypeAndTmdbId() {
        History movie = history("movie", 42, 100, "movie-key");
        History tv = history("tv", 42, 200, "tv-key");

        List<History> result = HistoryDisplayPolicy.project(List.of(movie, tv), true);

        assertEquals(2, result.size());
        assertEquals("tv-key", result.get(0).getKey());
        assertEquals("movie-key", result.get(1).getKey());
    }

    @Test
    public void aggregationKeepsMostRecentlyPlayedMember() {
        History older = history("tv", 88, 100, "old-key");
        History newer = history("tv", 88, 300, "new-key");

        List<History> result = HistoryDisplayPolicy.project(List.of(older, newer), true);

        assertEquals(1, result.size());
        assertEquals("new-key", result.get(0).getKey());
    }

    @Test
    public void recordsWithoutStableTmdbIdentityStaySeparate() {
        History first = history("", 88, 100, "first-key");
        History second = history("", 88, 200, "second-key");

        List<History> result = HistoryDisplayPolicy.project(List.of(first, second), true);

        assertEquals(2, result.size());
        assertEquals("second-key", result.get(0).getKey());
    }

    @Test
    public void aggregationDisabledOnlySortsByCreateTime() {
        History older = history("tv", 88, 100, "old-key");
        History newer = history("tv", 88, 300, "new-key");

        List<History> result = HistoryDisplayPolicy.project(List.of(older, newer), false);

        assertEquals(2, result.size());
        assertEquals("new-key", result.get(0).getKey());
        assertEquals("old-key", result.get(1).getKey());
    }

    @Test
    public void playbackCopyPreservesProgressAndMarksCrossSource() {
        History source = history("tv", 88, 100, "old-site@@@old-vod@@@2");
        source.setCid(2);
        source.setPosition(120_000);
        source.setDuration(300_000);

        History result = source.forPlaybackKey("new-site@@@new-vod@@@1", 1);

        assertEquals("new-site@@@new-vod@@@1", result.getKey());
        assertEquals(1, result.getCid());
        assertEquals(120_000, result.getPosition());
        assertEquals(300_000, result.getDuration());
        org.junit.Assert.assertTrue(result.isCrossSourcePlayback());
    }

    private static History history(String mediaType, int tmdbId, long createTime, String key) {
        History history = new History();
        history.setKey(key);
        history.setMediaType(mediaType);
        history.setTmdbId(tmdbId);
        history.setCreateTime(createTime);
        return history;
    }
}
