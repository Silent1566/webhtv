package com.fongmi.android.tv.ui.helper;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EpisodeSeasonPolicyTest {

    @Test
    public void sliceBySeasonCounts_slicesOnlyWhenCountsExactlyCoverSourceEpisodes() {
        List<Integer> episodes = List.of(1, 2, 3, 4, 5, 6);
        List<Integer> seasons = List.of(1, 2);
        Map<Integer, Integer> counts = Map.of(1, 3, 2, 3);

        assertTrue(EpisodeSeasonPolicy.canSliceBySeasonCounts(episodes.size(), seasons, counts));
        assertEquals(List.of(4, 5, 6), EpisodeSeasonPolicy.sliceBySeasonCounts(episodes, seasons, counts, 2));
    }

    @Test
    public void sliceBySeasonCounts_keepsAllEpisodesWhenTmdbCountsWouldDropSourceEpisodes() {
        List<Integer> episodes = List.of(1, 2, 3, 4, 5, 6, 7);
        List<Integer> seasons = List.of(1, 2);
        Map<Integer, Integer> counts = Map.of(1, 3, 2, 3);

        assertFalse(EpisodeSeasonPolicy.canSliceBySeasonCounts(episodes.size(), seasons, counts));
        assertEquals(episodes, EpisodeSeasonPolicy.sliceBySeasonCounts(episodes, seasons, counts, 2));
    }

    @Test
    public void sliceBySeasonCounts_keepsAllEpisodesWhenSeasonCountIsMissing() {
        List<Integer> episodes = List.of(1, 2, 3, 4);
        List<Integer> seasons = List.of(1, 2);
        Map<Integer, Integer> counts = Map.of(1, 2);

        assertFalse(EpisodeSeasonPolicy.canSliceBySeasonCounts(episodes.size(), seasons, counts));
        assertEquals(episodes, EpisodeSeasonPolicy.sliceBySeasonCounts(episodes, seasons, counts, 2));
    }

    @Test
    public void shouldUseSingleSeasonEpisodeData_whenFirstTmdbSeasonCoversSourceEpisodes() {
        List<Integer> seasons = List.of(1, 2, 3);
        Map<Integer, Integer> counts = Map.of(1, 190, 2, 8, 3, 8);

        assertTrue(EpisodeSeasonPolicy.shouldUseSingleSeasonEpisodeData(161, 1, seasons, counts));
    }

    @Test
    public void shouldUseSingleSeasonEpisodeData_keepsExactMultiSeasonMapping() {
        List<Integer> seasons = List.of(1, 2, 3);
        Map<Integer, Integer> counts = Map.of(1, 40, 2, 40, 3, 40);

        assertFalse(EpisodeSeasonPolicy.shouldUseSingleSeasonEpisodeData(120, 1, seasons, counts));
    }

    @Test
    public void linearEpisodeNumber_usesListPositionWhenArcNumberWouldMoveEpisodeBackwards() {
        assertEquals(18, EpisodeSeasonPolicy.linearEpisodeNumber(1, 17));
    }

    @Test
    public void linearEpisodeNumber_keepsAbsoluteSourceNumberForPagedRanges() {
        assertEquals(41, EpisodeSeasonPolicy.linearEpisodeNumber(41, 0));
    }
}
