package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.title.MediaTitleParser;
import com.fongmi.android.tv.title.MediaTitleRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class EpisodeSeasonPolicy {

    private static final Pattern SPECIAL_SEASON = Pattern.compile("(?i)(?:第\\s*0\\s*[季部]|season\\s*0\\b|s0{1,2}(?:[-._\\s]*e[0-9]{1,3})?\\b|特别篇|特別篇|\\bspecials\\b)");

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
        if (sourceEpisodeCount <= 0 || firstSeason < 0 || seasons == null || seasons.size() <= 1 || seasonCounts == null) return false;
        int firstSeasonCount = Math.max(0, seasonCounts.getOrDefault(firstSeason, 0));
        return firstSeasonCount >= sourceEpisodeCount && !canSliceBySeasonCounts(sourceEpisodeCount, seasons, seasonCounts);
    }

    /**
     * Resolves only seasons that can be safely mapped to the current source line.
     * An empty result means the UI must keep the source episodes as one flat list.
     */
    public static List<Integer> resolveAvailableSeasons(
            List<Integer> sourceSeasonNumbers,
            int titleSeason,
            int firstSeason,
            List<Integer> tmdbSeasons,
            Map<Integer, Integer> seasonCounts) {
        if (sourceSeasonNumbers == null || sourceSeasonNumbers.isEmpty() || tmdbSeasons == null || tmdbSeasons.isEmpty()) return List.of();
        boolean hasAnyExplicitSeason = hasAnyExplicitSeason(sourceSeasonNumbers);
        if (hasAnyExplicitSeason) {
            if (!hasCompleteExplicitSeasonMapping(sourceSeasonNumbers, tmdbSeasons)) return List.of();
            List<Integer> available = new ArrayList<>();
            for (Integer season : tmdbSeasons) {
                if (sourceSeasonNumbers.contains(season)) available.add(season);
            }
            return List.copyOf(available);
        }
        if (titleSeason >= 0) return tmdbSeasons.contains(titleSeason) ? List.of(titleSeason) : List.of();
        if (tmdbSeasons.size() == 1) return List.of(tmdbSeasons.get(0));
        int sourceEpisodeCount = sourceSeasonNumbers.size();
        if (canSliceBySeasonCounts(sourceEpisodeCount, tmdbSeasons, seasonCounts)) return List.copyOf(tmdbSeasons);
        if (tmdbSeasons.contains(firstSeason) && shouldUseSingleSeasonEpisodeData(sourceEpisodeCount, firstSeason, tmdbSeasons, seasonCounts)) return List.of(firstSeason);
        return List.of();
    }

    public static int resolveSourceSeason(String... candidates) {
        if (candidates == null || candidates.length == 0) return -1;
        MediaTitleParser parser = new MediaTitleParser();
        for (String candidate : candidates) {
            if (candidate == null || candidate.trim().isEmpty()) continue;
            if (SPECIAL_SEASON.matcher(candidate).find()) return 0;
            int season = parser.parse(MediaTitleRequest.builder().rawTitle(candidate).build()).getSeasonNumber();
            if (season > 0) return season;
        }
        return -1;
    }

    public static List<Integer> episodeMetadataSeasonCandidates(int sourceSeason) {
        return sourceSeason >= 0 ? List.of(sourceSeason) : List.of(1, 0);
    }

    public static String episodePositionCacheKey(int season, String episodeName) {
        String name = episodeName == null ? "" : episodeName;
        if (season < 0 || name.isEmpty()) return name;
        String prefix = "S" + season + "|";
        return name.startsWith(prefix) ? name : prefix + name;
    }

    private static boolean hasAnyExplicitSeason(List<Integer> sourceSeasonNumbers) {
        for (Integer season : sourceSeasonNumbers) {
            if (season != null && season >= 0) return true;
        }
        return false;
    }

    /** Returns true only when every source episode declares a season present in TMDB metadata. */
    public static boolean hasCompleteExplicitSeasonMapping(List<Integer> sourceSeasonNumbers, List<Integer> tmdbSeasons) {
        if (sourceSeasonNumbers == null || sourceSeasonNumbers.isEmpty() || tmdbSeasons == null || tmdbSeasons.isEmpty()) return false;
        for (Integer season : sourceSeasonNumbers) {
            if (season == null || season < 0 || !tmdbSeasons.contains(season)) return false;
        }
        return true;
    }

    public static int linearEpisodeNumber(int sourceEpisodeNumber, int zeroBasedIndex) {
        // 文件名有明确集号时，直接使用它，不要被列表位置覆盖
        if (sourceEpisodeNumber > 0) return sourceEpisodeNumber;
        // 文件名无集号时，用列表位置推断（index + 1）
        return zeroBasedIndex >= 0 ? zeroBasedIndex + 1 : -1;
    }
}
