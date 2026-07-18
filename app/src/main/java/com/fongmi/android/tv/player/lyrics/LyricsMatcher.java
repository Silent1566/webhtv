package com.fongmi.android.tv.player.lyrics;

import android.text.TextUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class LyricsMatcher {

    private static final int MIN_SCORE = 55;

    public LyricsResult best(LyricsRequest request, List<LrcLibClient.Entry> entries) {
        List<LyricsResult> results = all(request, entries, 1);
        return results.isEmpty() ? null : results.get(0);
    }

    public List<LyricsResult> all(LyricsRequest request, List<LrcLibClient.Entry> entries, int limit) {
        ArrayList<LyricsResult> results = new ArrayList<>();
        ArrayList<ScoredEntry> scored = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (LrcLibClient.Entry entry : entries) {
            if (entry == null || !seen.add(entry.id)) continue;
            int score = score(request, entry);
            if (score < MIN_SCORE) continue;
            scored.add(new ScoredEntry(entry, score));
        }
        scored.sort(Comparator.comparingInt((ScoredEntry item) -> item.score).reversed());
        for (ScoredEntry item : scored) {
            LyricsResult result = toResult(item.entry, item.score);
            if (result == null) continue;
            results.add(result);
            if (results.size() >= Math.max(1, limit)) break;
        }
        return results;
    }

    private LyricsResult toResult(LrcLibClient.Entry entry, int score) {
        String synced = clean(entry.syncedLyrics);
        String plain = clean(entry.plainLyrics);
        boolean hasSynced = !TextUtils.isEmpty(synced) && LyricsParser.hasTimedLine(synced);
        String lyrics = hasSynced ? synced : plain;
        if (TextUtils.isEmpty(lyrics)) return null;
        return new LyricsResult("LRCLIB", clean(entry.trackName), clean(entry.artistName), clean(entry.albumName), lyrics, Math.round(entry.duration * 1000), hasSynced, score);
    }

    private int score(LyricsRequest request, LrcLibClient.Entry entry) {
        if (entry.instrumental) return Integer.MIN_VALUE;
        boolean hasSynced = !TextUtils.isEmpty(entry.syncedLyrics) && LyricsParser.hasTimedLine(entry.syncedLyrics);
        boolean hasPlain = !TextUtils.isEmpty(entry.plainLyrics);
        if (!hasSynced && !hasPlain) return Integer.MIN_VALUE;
        int score = hasSynced ? 18 : 4;
        score += matchScore(request, entry.trackName, entry.artistName, entry.duration);
        if (!TextUtils.isEmpty(request.getAlbum()) && !TextUtils.isEmpty(entry.albumName)) score += relatedTextScore(request.getAlbum(), entry.albumName, 10, 5, 0);
        return score;
    }

    public static int matchScore(LyricsRequest request, String actualTitle, String actualArtist, double actualSec) {
        if (request == null) return 0;
        int titleScore = relatedTextScore(request.getTitle(), actualTitle, 72, 46, -72);
        int artistScore = TextUtils.isEmpty(request.getArtist()) ? 0 : relatedTextScore(request.getArtist(), actualArtist, 28, 14, -12);
        int timeScore = durationScore(request.getDurationSec(), actualSec);
        int score = titleScore + artistScore + timeScore + titleQualityAdjustment(actualTitle);
        if (titleScore >= 72 && timeScore >= 20) score += 8;
        return score;
    }

    public static boolean isAcceptableMatch(LyricsRequest request, LyricsResult result) {
        if (request == null || result == null || !result.isValid()) return false;
        String wanted = normalize(request.getTitle());
        String actual = normalize(result.getTrackName());
        if (TextUtils.isEmpty(wanted) || TextUtils.isEmpty(actual)) return false;
        int titleScore = relatedTextScore(request.getTitle(), result.getTrackName(), 72, 46, -72);
        if (titleScore < 30) return false;
        int score = matchScore(request, result.getTrackName(), result.getArtistName(), result.getDurationMs() / 1000.0);
        return titleScore >= 72 ? score >= 0 : score >= 30;
    }

    private static int relatedTextScore(String wanted, String actual, int exact, int related, int mismatch) {
        String a = normalize(wanted);
        String b = normalize(actual);
        if (TextUtils.isEmpty(a)) return 0;
        if (TextUtils.isEmpty(b)) return mismatch / 2;
        if (a.equals(b)) return exact;
        if (!a.contains(b) && !b.contains(a)) return mismatch;
        int shorter = Math.min(a.codePointCount(0, a.length()), b.codePointCount(0, b.length()));
        int longer = Math.max(a.codePointCount(0, a.length()), b.codePointCount(0, b.length()));
        if (shorter <= 1 || isSequenceOnly(a) || isSequenceOnly(b)) return mismatch;
        float ratio = shorter / (float) Math.max(1, longer);
        if (ratio >= 0.75f) return related;
        if (ratio >= 0.55f) return Math.max(18, related - 14);
        return mismatch / 2;
    }

    private static int durationScore(int wantedSec, double actualSec) {
        if (wantedSec <= 0 || actualSec <= 0) return 0;
        double delta = Math.abs(wantedSec - actualSec);
        if (delta <= 2) return 24;
        if (delta <= 5) return 20;
        if (delta <= 10) return 14;
        if (delta <= 20) return 4;
        if (delta <= 40) return -18;
        return -40;
    }

    private static int titleQualityAdjustment(String title) {
        String value = Normalizer.normalize(clean(title), Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT);
        if (TextUtils.isEmpty(value)) return -40;
        if (isSequenceOnly(value)) return -120;
        int score = 0;
        int length = value.codePointCount(0, value.length());
        if (containsAny(value, "合集", "歌单", "盘点", "排行榜", "热歌榜", "频道", "最好听", "宝藏歌曲", "首歌曲")) score -= 70;
        if (length > 36) score -= 36;
        else if (length > 28) score -= 20;
        return score;
    }

    private static boolean isSequenceOnly(String text) {
        return clean(text).matches("(?i)^(?:p\\s*)?\\d{1,4}$");
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) if (!TextUtils.isEmpty(value) && text.contains(value)) return true;
        return false;
    }

    public static String normalize(String text) {
        String value = clean(text).toLowerCase(Locale.ROOT);
        if (TextUtils.isEmpty(value)) return "";
        value = ChineseText.toSimplified(Normalizer.normalize(value, Normalizer.Form.NFKC));
        value = value.replaceAll("\\.[a-z0-9]{2,5}$", "");
        value = value.replaceAll("\\([^)]*\\)|\\[[^]]*]|（[^）]*）|【[^】]*】", "");
        value = value.replaceAll("(?i)(?<=[\\u4e00-\\u9fff])\\s*dj(?:[a-z0-9\\u4e00-\\u9fff]*版)?\\s*$", "");
        value = value.replaceAll("(?i)official|music video|video|audio|lyrics|lyric|mv|flac|mp3|lossless|tv size|short ver\\.?|full ver\\.?|opening|ending|op|ed|feat\\.?|featuring", "");
        value = value.replaceAll("(?i)(?:live|cover|remix|instrumental|karaoke|off vocal)(?: version| ver\\.?|版)?$", "");
        value = value.replaceAll("(?:现场版|翻唱版|伴奏版|纯音乐版|原唱版)$", "");
        value = value.replaceAll("(?i)tvアニメ|テレビアニメ|アニメ|オープニング|エンディング|主題歌|挿入歌", "");
        value = value.replaceAll("[\\s_\\-.,:;!?/\\\\|+~`'\"#@$%^&*=<>，。！？、·：；“”‘’《》〈〉]+", "");
        return value.trim();
    }

    private static String clean(String text) {
        return text == null ? "" : text.trim();
    }

    private static class ScoredEntry {
        private final LrcLibClient.Entry entry;
        private final int score;

        private ScoredEntry(LrcLibClient.Entry entry, int score) {
            this.entry = entry;
            this.score = score;
        }
    }
}
