package com.fongmi.android.tv.player.lyrics;

import android.icu.text.Transliterator;
import android.text.TextUtils;

final class ChineseText {

    private static final Transliterator TRADITIONAL_TO_SIMPLIFIED = createSimplifier();

    private ChineseText() {
    }

    static String toSimplified(String text) {
        String value = text == null ? "" : text.trim();
        if (TextUtils.isEmpty(value) || containsKana(value) || TRADITIONAL_TO_SIMPLIFIED == null) return value;
        try {
            synchronized (TRADITIONAL_TO_SIMPLIFIED) {
                return TRADITIONAL_TO_SIMPLIFIED.transliterate(value).trim();
            }
        } catch (Throwable e) {
            return value;
        }
    }

    private static Transliterator createSimplifier() {
        try {
            return Transliterator.getInstance("Traditional-Simplified");
        } catch (Throwable e) {
            return null;
        }
    }

    private static boolean containsKana(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u3040' && c <= '\u30ff') return true;
        }
        return false;
    }
}
