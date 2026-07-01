package com.fongmi.android.tv.ui.helper;

public final class EpisodeCardPolicy {

    private EpisodeCardPolicy() {
    }

    public static boolean shouldShowCard(boolean useTmdbCard, boolean hasTmdbEpisode, boolean hasFallbackStill) {
        return useTmdbCard && (hasTmdbEpisode || hasFallbackStill);
    }
}
