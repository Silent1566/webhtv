package com.fongmi.android.tv.ui.helper;

import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.Objects;

public class TmdbMatchPolicy {

    private static final int SPLIT_SEASON_PENALTY = -240;
    private static final int NON_SPLIT_BONUS = 140;
    private static final int EXPLICIT_SPLIT_BONUS = 160;

    private TmdbMatchPolicy() {
    }

    public static int splitSeasonDetailScore(String sourceText, JsonObject detail) {
        boolean split = isSplitSeasonVariant(detailTitle(detail));
        if (!split) return allowsSplitSeasonVariant(sourceText) ? 0 : NON_SPLIT_BONUS;
        if (mentionsSplitSeason(sourceText)) return EXPLICIT_SPLIT_BONUS;
        return allowsSplitSeasonVariant(sourceText) ? 0 : SPLIT_SEASON_PENALTY;
    }

    public static boolean isUnwantedSplitSeasonVariant(String sourceText, JsonObject detail) {
        return isSplitSeasonVariant(detailTitle(detail)) && !allowsSplitSeasonVariant(sourceText);
    }

    static boolean isSplitSeasonVariant(String text) {
        String value = normalize(text);
        return value.contains("分季");
    }

    static boolean allowsSplitSeasonVariant(String sourceText) {
        return mentionsSplitSeason(sourceText) || mentionsExplicitSeason(sourceText);
    }

    private static boolean mentionsSplitSeason(String sourceText) {
        return normalize(sourceText).contains("分季");
    }

    private static boolean mentionsExplicitSeason(String sourceText) {
        String value = Objects.toString(sourceText, "");
        return value.matches("(?is).*(第\\s*[零〇一二三四五六七八九十两0-9]+\\s*[季部]|season\\s*[0-9]{1,2}|s[0-9]{1,2}(?:[-._\\s]*e[0-9]{1,3})?).*");
    }

    private static String detailTitle(JsonObject detail) {
        return string(detail, "name") + " " + string(detail, "original_name") + " " + string(detail, "title") + " " + string(detail, "original_title");
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        return object.get(key).getAsString();
    }

    private static String normalize(String text) {
        return Objects.toString(text, "").replaceAll("[\\s·•:：\\-_/\\\\|()（）\\[\\]【】]+", "").trim().toLowerCase(Locale.ROOT);
    }
}
