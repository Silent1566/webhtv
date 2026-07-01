package com.fongmi.android.tv.ui.helper;

import java.util.List;
import java.util.Map;

public final class EpisodeSeasonPolicy {

    private EpisodeSeasonPolicy() {
    }

    public static boolean canSliceBySeasonCounts(int episodeCount, List<Integer> seasons, Map<Integer, Integer> seasonCounts) {
        if (episodeCount <= 0 || seasons == null || seasons.size() <= 1 || seasonCounts == null || seasonCounts.isEmpty()) return false;
        int total = 0;
        for (Integer season : seasons) {
            int count = Math.max(0, seasonCounts.getOrDefault(season, 0));
            if (count <= 0) return false;
            total += count;
            if (total > episodeCount) return false;
        }
        return total == episodeCount;
    }

    public static <T> List<T> sliceBySeasonCounts(List<T> episodes, List<Integer> seasons, Map<Integer, Integer> seasonCounts, int selectedSeason) {
        if (episodes == null || episodes.isEmpty()) return List.of();
        if (!canSliceBySeasonCounts(episodes.size(), seasons, seasonCounts)) return episodes;
        int start = 0;
        for (Integer season : seasons) {
            int count = Math.max(0, seasonCounts.getOrDefault(season, 0));
            int end = Math.min(episodes.size(), start + count);
            if (season == selectedSeason) return start < end ? episodes.subList(start, end) : List.of();
            start = end;
        }
        return episodes;
    }

    public static boolean shouldUseSingleSeasonEpisodeData(int sourceEpisodeCount, int firstSeason, List<Integer> seasons, Map<Integer, Integer> seasonCounts) {
        if (sourceEpisodeCount <= 0 || firstSeason <= 0 || seasons == null || seasons.size() <= 1 || seasonCounts == null) return false;
        int firstSeasonCount = Math.max(0, seasonCounts.getOrDefault(firstSeason, 0));
        return firstSeasonCount >= sourceEpisodeCount && !canSliceBySeasonCounts(sourceEpisodeCount, seasons, seasonCounts);
    }

    public static int linearEpisodeNumber(int sourceEpisodeNumber, int zeroBasedIndex) {
        if (sourceEpisodeNumber > 0 && (zeroBasedIndex < 0 || sourceEpisodeNumber >= zeroBasedIndex + 1)) return sourceEpisodeNumber;
        return zeroBasedIndex >= 0 ? zeroBasedIndex + 1 : sourceEpisodeNumber;
    }
}
