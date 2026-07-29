package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.Episode;

import java.util.List;

public final class EpisodeDisplayPolicy {

    private EpisodeDisplayPolicy() {
    }

    public static boolean hasTmdbEpisodeData(List<Episode> items) {
        if (items == null || items.isEmpty()) return false;
        for (Episode item : items) {
            if (item != null && item.getTmdbEpisode() != null) return true;
        }
        return false;
    }

    public static boolean shouldUseTmdbEpisodeCards(boolean tmdbSourceEnabled, List<Episode> items) {
        return tmdbSourceEnabled && hasTmdbEpisodeData(items);
    }

    public static boolean shouldWaitForTmdbEpisodes(boolean tmdbSourceEnabled, boolean tmdbEpisodeEnrichmentPending, boolean tmdbAdapterReady, boolean tmdbEpisodeMetadataLoaded, List<Episode> items) {
        return tmdbSourceEnabled && tmdbEpisodeEnrichmentPending && tmdbAdapterReady && !tmdbEpisodeMetadataLoaded && items != null && !items.isEmpty() && !hasTmdbEpisodeData(items);
    }

    public static boolean shouldShowTmdbEpisodeChrome(boolean tmdbSourceEnabled, boolean waitingForTmdbEpisodes, List<Episode> items) {
        return tmdbSourceEnabled && (hasTmdbEpisodeData(items) || waitingForTmdbEpisodes);
    }

    public static boolean shouldShowEpisodeGroup(int groupCount, boolean tmdbDetailLayout) {
        return groupCount > 1;
    }
}
