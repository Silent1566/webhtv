package com.fongmi.android.tv.player.lyrics;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class KugouClient {

    private static final String TAG = "lyrics";
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36";
    private static final int MIN_SCORE = 58;
    private static final byte[] KRC_KEY = new byte[]{0x40, 0x47, 0x61, 0x77, 0x5E, 0x32, 0x74, 0x47, 0x51, 0x36, 0x31, 0x2D, (byte) 0xCE, (byte) 0xD2, 0x6E};
    private static final Pattern KRC_LINE = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");
    private static final Pattern KRC_WORD = Pattern.compile("<(-?\\d+),(-?\\d+),-?\\d+>([^<]*)");
    private static final OkHttpClient CLIENT = OkHttp.client()
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    public LyricsResult find(LyricsRequest request) {
        List<LyricsResult> results = findAll(request, 1);
        return results.isEmpty() ? null : results.get(0);
    }

    public List<LyricsResult> findAll(LyricsRequest request, int limit) {
        ArrayList<LyricsResult> results = new ArrayList<>();
        for (Entry entry : ranked(request)) {
            LyricsResult result = toResult(entry);
            if (result == null) continue;
            results.add(result);
            if (results.size() >= Math.max(1, limit)) break;
        }
        return results;
    }

    private LyricsResult toResult(Entry entry) {
        for (Candidate candidate : lyricCandidates(entry)) {
            String krc = downloadKrc(candidate);
            if (LyricsParser.hasTimedLine(krc)) return new LyricsResult("Kugou KRC", candidate.songOr(entry.name), candidate.singerOr(entry.artist), entry.album, krc, candidate.durationOr(entry.durationSec) * 1000L, true, score(entry, candidate, true));
            String lrc = downloadLrc(candidate);
            if (LyricsParser.hasTimedLine(lrc)) return new LyricsResult("Kugou", candidate.songOr(entry.name), candidate.singerOr(entry.artist), entry.album, lrc, candidate.durationOr(entry.durationSec) * 1000L, true, score(entry, candidate, false));
        }
        return null;
    }

    private List<Entry> ranked(LyricsRequest request) {
        ArrayList<Entry> ranked = new ArrayList<>();
        for (Entry entry : search(request)) {
            entry.score = score(request, entry);
            if (entry.score >= MIN_SCORE) ranked.add(entry);
        }
        ranked.sort(Comparator.comparingInt((Entry entry) -> entry.score).reversed());
        return ranked;
    }

    private List<Entry> search(LyricsRequest request) {
        List<Entry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String keyword : request.searchKeywords()) search(entries, seen, request, keyword);
        return entries;
    }

    private void search(List<Entry> entries, Set<String> seen, LyricsRequest request, String keyword) {
        HttpUrl url = HttpUrl.parse("http://mobilecdn.kugou.com/api/v3/search/song").newBuilder()
                .addQueryParameter("format", "json")
                .addQueryParameter("keyword", keyword)
                .addQueryParameter("page", "1")
                .addQueryParameter("pagesize", "8")
                .addQueryParameter("api_ver", "1")
                .addQueryParameter("area_code", "1")
                .addQueryParameter("correct", "1")
                .addQueryParameter("plat", "2")
                .addQueryParameter("tag", "1")
                .addQueryParameter("sver", "5")
                .addQueryParameter("showtype", "10")
                .addQueryParameter("version", "8990")
                .build();
        try {
            JSONObject object = new JSONObject(get(url.toString()));
            JSONObject data = object.optJSONObject("data");
            JSONArray array = data == null ? null : data.optJSONArray("info");
            if (array == null) return;
            for (int i = 0; i < array.length(); i++) addEntry(entries, seen, array.optJSONObject(i));
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "kugou search failed title=%s error=%s", request.getTitle(), e.getMessage());
        }
    }

    private void addEntry(List<Entry> entries, Set<String> seen, JSONObject item) {
        Entry entry = parseEntry(item);
        if (entry != null && seen.add(entry.hash)) entries.add(entry);
        JSONArray group = item == null ? null : item.optJSONArray("group");
        if (group == null) return;
        for (int i = 0; i < group.length(); i++) {
            Entry child = parseEntry(group.optJSONObject(i));
            if (child != null && seen.add(child.hash)) entries.add(child);
        }
    }

    private Entry parseEntry(JSONObject item) {
        if (item == null) return null;
        Entry entry = new Entry();
        entry.hash = clean(item.optString("hash"));
        entry.name = clean(first(item, "songname_original", "songname"));
        entry.artist = clean(item.optString("singername"));
        entry.album = clean(item.optString("album_name"));
        entry.durationSec = item.optInt("duration", 0);
        if (TextUtils.isEmpty(entry.name)) entry.name = titleFromFileName(item.optString("filename"));
        if (TextUtils.isEmpty(entry.artist)) entry.artist = artistFromFileName(item.optString("filename"));
        return !TextUtils.isEmpty(entry.hash) && !TextUtils.isEmpty(entry.name) ? entry : null;
    }

    private List<Candidate> lyricCandidates(Entry entry) {
        ArrayList<Candidate> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        searchLyrics(results, seen, lyricSearchUrl("http://lyrics.kugou.com/search", entry, "pc"));
        searchLyrics(results, seen, lyricSearchUrl("http://krcs.kugou.com/search", entry, "mobi"));
        results.sort(Comparator.comparingInt((Candidate candidate) -> candidate.score).reversed());
        return results;
    }

    private HttpUrl lyricSearchUrl(String base, Entry entry, String client) {
        return HttpUrl.parse(base).newBuilder()
                .addQueryParameter("ver", "1")
                .addQueryParameter("man", "yes")
                .addQueryParameter("client", client)
                .addQueryParameter("keyword", entry.displayKeyword())
                .addQueryParameter("duration", String.valueOf(entry.durationSec * 1000))
                .addQueryParameter("hash", entry.hash)
                .build();
    }

    private void searchLyrics(List<Candidate> results, Set<String> seen, HttpUrl url) {
        try {
            JSONObject object = new JSONObject(get(url.toString()));
            JSONArray array = object.optJSONArray("candidates");
            if (array == null) return;
            for (int i = 0; i < array.length(); i++) {
                Candidate candidate = parseCandidate(array.optJSONObject(i));
                if (candidate == null || !seen.add(candidate.key())) continue;
                results.add(candidate);
            }
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "kugou lyric search failed error=%s", e.getMessage());
        }
    }

    private Candidate parseCandidate(JSONObject item) {
        if (item == null) return null;
        Candidate candidate = new Candidate();
        candidate.id = clean(first(item, "download_id", "id"));
        candidate.accessKey = clean(item.optString("accesskey"));
        candidate.song = clean(item.optString("song"));
        candidate.singer = clean(item.optString("singer"));
        candidate.durationMs = item.optLong("duration", 0);
        candidate.score = item.optInt("score", 0) + (item.optInt("krctype", 0) == 1 ? 10 : 0);
        if (TextUtils.isEmpty(candidate.id) || TextUtils.isEmpty(candidate.accessKey)) return null;
        return candidate;
    }

    private String downloadKrc(Candidate candidate) {
        String content = download(candidate, "krc");
        if (TextUtils.isEmpty(content)) return "";
        try {
            byte[] bytes = Base64.decode(content, Base64.DEFAULT);
            return krcToEnhancedLrc(decodeKrc(bytes));
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "kugou krc decode failed id=%s error=%s", candidate.id, e.getMessage());
            return "";
        }
    }

    private String downloadLrc(Candidate candidate) {
        String content = download(candidate, "lrc");
        if (TextUtils.isEmpty(content)) return "";
        try {
            return new String(Base64.decode(content, Base64.DEFAULT), StandardCharsets.UTF_8).replace("\uFEFF", "");
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "kugou lrc decode failed id=%s error=%s", candidate.id, e.getMessage());
            return "";
        }
    }

    private String download(Candidate candidate, String format) {
        HttpUrl url = HttpUrl.parse("http://lyrics.kugou.com/download").newBuilder()
                .addQueryParameter("ver", "1")
                .addQueryParameter("client", "pc")
                .addQueryParameter("id", candidate.id)
                .addQueryParameter("accesskey", candidate.accessKey)
                .addQueryParameter("fmt", format)
                .addQueryParameter("charset", "utf8")
                .build();
        try {
            JSONObject object = new JSONObject(get(url.toString()));
            return object.optInt("error_code", 0) == 0 ? object.optString("content") : "";
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "kugou download failed id=%s fmt=%s error=%s", candidate.id, format, e.getMessage());
            return "";
        }
    }

    private String decodeKrc(byte[] bytes) throws Exception {
        if (bytes == null || bytes.length <= 4) return "";
        int offset = startsWithKrc(bytes) ? 4 : 0;
        byte[] encrypted = new byte[bytes.length - offset];
        System.arraycopy(bytes, offset, encrypted, 0, encrypted.length);
        for (int i = 0; i < encrypted.length; i++) encrypted[i] = (byte) (encrypted[i] ^ KRC_KEY[i % KRC_KEY.length]);
        try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(encrypted)); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
            return output.toString(StandardCharsets.UTF_8.name()).replace("\uFEFF", "");
        }
    }

    private boolean startsWithKrc(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == 'k' && bytes[1] == 'r' && bytes[2] == 'c' && bytes[3] == '1';
    }

    private String krcToEnhancedLrc(String krc) {
        if (TextUtils.isEmpty(krc)) return "";
        StringBuilder builder = new StringBuilder();
        for (String raw : krc.replace("\r", "").split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            Matcher lineMatcher = KRC_LINE.matcher(line);
            if (!lineMatcher.find()) continue;
            long lineStart = parseLong(lineMatcher.group(1));
            String lineContent = lineMatcher.group(3);
            StringBuilder words = new StringBuilder();
            Matcher wordMatcher = KRC_WORD.matcher(lineContent);
            while (wordMatcher.find()) {
                String word = wordMatcher.group(3);
                if (TextUtils.isEmpty(word)) continue;
                words.append('<').append(Math.max(0, parseLong(wordMatcher.group(1)))).append(',').append(Math.max(0, parseLong(wordMatcher.group(2)))).append('>').append(word);
            }
            if (words.length() > 0) builder.append(formatTime(lineStart)).append(words).append('\n');
        }
        return builder.toString();
    }

    private int score(LyricsRequest request, Entry entry) {
        return LyricsMatcher.matchScore(request, entry.name, entry.artist, entry.durationSec);
    }

    private int score(Entry entry, Candidate candidate, boolean word) {
        int score = entry.score + Math.min(Math.max(candidate.score, 0), 20);
        score += durationScore(entry.durationSec, candidate.durationSec());
        return word ? score + 8 : score;
    }

    private int textScore(String wanted, String actual, int exact, int contains, int mismatch) {
        String a = LyricsMatcher.normalize(wanted);
        String b = LyricsMatcher.normalize(actual);
        if (TextUtils.isEmpty(a)) return 0;
        if (TextUtils.isEmpty(b)) return mismatch / 2;
        if (a.equals(b)) return exact;
        if (a.contains(b) || b.contains(a)) return contains;
        return mismatch;
    }

    private int durationScore(int wantedSec, int actualSec) {
        if (wantedSec <= 0 || actualSec <= 0) return 0;
        int delta = Math.abs(wantedSec - actualSec);
        if (delta <= 2) return 24;
        if (delta <= 5) return 20;
        if (delta <= 10) return 14;
        if (delta <= 20) return 4;
        if (delta <= 40) return -18;
        return -40;
    }

    private String get(String url) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://www.kugou.com/")
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return "";
            return response.body().string();
        }
    }

    private String first(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.optString(key, "").trim();
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private String titleFromFileName(String fileName) {
        String[] split = splitFileName(fileName);
        return split == null ? "" : split[1];
    }

    private String artistFromFileName(String fileName) {
        String[] split = splitFileName(fileName);
        return split == null ? "" : split[0];
    }

    private String[] splitFileName(String fileName) {
        String value = clean(fileName);
        int index = value.indexOf(" - ");
        if (index <= 0 || index >= value.length() - 3) return null;
        return new String[]{value.substring(0, index).trim(), value.substring(index + 3).trim()};
    }

    private String clean(String text) {
        return Uri.decode(text == null ? "" : text)
                .replace("\\\\u0026", "&")
                .replace("\\u0026", "&")
                .replace("&nbsp;", " ")
                .replaceAll("<[^>]+>", "")
                .trim();
    }

    private String formatTime(long timeMs) {
        long minute = timeMs / 60000;
        long second = timeMs % 60000;
        return String.format(Locale.US, "[%02d:%02d.%03d]", minute, second / 1000, second % 1000);
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static class Entry {
        private String hash;
        private String name;
        private String artist;
        private String album;
        private int durationSec;
        private int score;

        private String displayKeyword() {
            return TextUtils.isEmpty(artist) ? name : artist + " - " + name;
        }
    }

    private static class Candidate {
        private String id;
        private String accessKey;
        private String song;
        private String singer;
        private long durationMs;
        private int score;

        private String key() {
            return id + "|" + accessKey;
        }

        private String songOr(String fallback) {
            return TextUtils.isEmpty(song) ? fallback : song;
        }

        private String singerOr(String fallback) {
            return TextUtils.isEmpty(singer) ? fallback : singer;
        }

        private int durationSec() {
            return durationMs <= 0 ? 0 : (int) Math.round(durationMs / 1000.0);
        }

        private int durationOr(int fallbackSec) {
            int duration = durationSec();
            return duration > 0 ? duration : fallbackSec;
        }
    }
}
