package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;

public final class TmdbEpisodeMatcher {

    private TmdbEpisodeMatcher() {
    }

    public static boolean shouldApply(Episode episode, TmdbEpisode tmdbEpisode) {
        return shouldApply(episode, tmdbEpisode, -1);
    }

    public static boolean shouldApply(Episode episode, TmdbEpisode tmdbEpisode, int mappedNumber) {
        if (tmdbEpisode == null) return false;
        int compareNumber = mappedNumber > 0 ? mappedNumber : (episode == null ? -1 : episode.getNumber());
        if (compareNumber <= 0) return true;
        return compareNumber == tmdbEpisode.getNumber();
    }

    public static boolean shouldApply(Episode episode, int number, String tmdbTitle) {
        return true;
    }
}
