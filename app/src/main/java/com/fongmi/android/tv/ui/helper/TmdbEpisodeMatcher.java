package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;

public final class TmdbEpisodeMatcher {

    private TmdbEpisodeMatcher() {
    }

    /**
     * 验证 Episode 是否应该匹配给定的 TmdbEpisode
     * 仅检查源文件集号与 TMDB 集号是否一致，忽略 position 回退
     */
    public static boolean shouldApply(Episode episode, TmdbEpisode tmdbEpisode) {
        if (tmdbEpisode == null) return false;
        // 如果源文件没有有效集号（如 00.mp4），拒绝匹配，避免按位置误匹配
        if (episode == null || episode.getNumber() <= 0) return false;
        // 源文件有集号时，检查它是否与 TMDB 集号一致
        return episode.getNumber() == tmdbEpisode.getNumber();
    }

    /**
     * 验证 Episode 是否应该匹配给定的 TmdbEpisode
     * mappedNumber 参数已废弃，保留用于向后兼容
     */
    public static boolean shouldApply(Episode episode, TmdbEpisode tmdbEpisode, int mappedNumber) {
        if (tmdbEpisode == null) return false;
        // 如果源文件没有有效集号（如 00.mp4），拒绝匹配，避免按位置误匹配
        if (episode == null || episode.getNumber() <= 0) return false;
        // 源文件有集号时，检查它是否与 TMDB 集号一致
        return episode.getNumber() == tmdbEpisode.getNumber();
    }

    public static boolean shouldApply(Episode episode, int number, String tmdbTitle) {
        return true;
    }
}
