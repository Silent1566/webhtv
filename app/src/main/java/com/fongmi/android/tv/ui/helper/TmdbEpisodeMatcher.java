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
        // 如果没有有效的映射编号，放行（原有逻辑已经决定了这个 tmdbEpisode）
        if (mappedNumber <= 0) return true;
        // 如果源文件有编号，检查它是否与 TMDB 编号一致（这是真正的匹配检查）
        // 忽略 mappedNumber，因为它可能是错误的中间计算结果
        if (episode != null && episode.getNumber() > 0) {
            return episode.getNumber() == tmdbEpisode.getNumber();
        }
        // 源文件没编号，依赖映射编号
        return mappedNumber == tmdbEpisode.getNumber();
    }

    public static boolean shouldApply(Episode episode, int number, String tmdbTitle) {
        return true;
    }
}
