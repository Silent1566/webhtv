package com.fongmi.android.tv.bean;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class HistoryPlaybackTest {

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
