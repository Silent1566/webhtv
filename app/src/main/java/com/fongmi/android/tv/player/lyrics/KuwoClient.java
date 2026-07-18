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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class KuwoClient {

    private static final String TAG = "lyrics";
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36";
    private static final byte[] NEW_LYRIC_KEY = "yeelion".getBytes(Charset.forName("US-ASCII"));
    private static final int MIN_SCORE = 58;
    private static final Pattern LRC_LINE = Pattern.compile("^(\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?])(.*)$");
    private static final Pattern LRCX_WORD = Pattern.compile("<(-?\\d+),(-?\\d+)>([^<]*)");
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
        String lyrics = lyric(entry.id);
        if (!LyricsParser.hasTimedLine(lyrics)) return null;
        return new LyricsResult("Kuwo", entry.name, entry.artist, entry.album, lyrics, entry.durationSec * 1000L, true, entry.score);
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
        for (String query : keywords(request)) search(entries, seen, request, query);
        return entries;
    }

    private void search(List<Entry> entries, Set<String> seen, LyricsRequest request, String query) {
        HttpUrl url = HttpUrl.parse("https://search.kuwo.cn/r.s").newBuilder()
                .addQueryParameter("all", query)
                .addQueryParameter("ft", "music")
                .addQueryParameter("newsearch", "1")
                .addQueryParameter("itemset", "web_2013")
                .addQueryParameter("client", "kt")
                .addQueryParameter("pn", "0")
                .addQueryParameter("rn", "8")
                .addQueryParameter("rformat", "json")
                .addQueryParameter("encoding", "utf8")
                .build();
        try {
            JSONObject object = new JSONObject(get(url.toString(), Map.of("Referer", "https://www.kuwo.cn/")));
            JSONArray array = object.optJSONArray("abslist");
            if (array == null) return;
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                Entry entry = new Entry();
                entry.id = id(item);
                entry.name = clean(first(item, "SONGNAME", "NAME"));
                entry.artist = clean(first(item, "ARTIST", "AARTIST"));
                entry.album = clean(item.optString("ALBUM"));
                entry.durationSec = parseInt(item.optString("DURATION"));
                if (!TextUtils.isEmpty(entry.id) && !TextUtils.isEmpty(entry.name) && seen.add(entry.id)) entries.add(entry);
            }
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "kuwo search failed title=%s error=%s", request.getTitle(), e.getMessage());
        }
    }

    private Entry best(LyricsRequest request, List<Entry> entries) {
        Entry best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Entry entry : entries) {
            int score = score(request, entry);
            if (score <= bestScore) continue;
            entry.score = score;
            best = entry;
            bestScore = score;
        }
        return best != null && bestScore >= MIN_SCORE ? best : null;
    }

    private int score(LyricsRequest request, Entry entry) {
        return LyricsMatcher.matchScore(request, entry.name, entry.artist, entry.durationSec);
    }

    private List<String> keywords(LyricsRequest request) {
        return request.searchKeywords();
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

    private String lyric(String id) {
        String newer = lyricFromNewLyric(id);
        if (LyricsParser.hasTimedLine(newer)) return newer;
        String mobi = lyricFromMobi(id);
        if (LyricsParser.hasTimedLine(mobi)) return mobi;
        return lyricFromOpenApi(id);
    }

    private String lyricFromNewLyric(String id) {
        String query = buildNewLyricQuery(id, true);
        try {
            byte[] bytes = getBytes("http://newlyric.kuwo.cn/newlyric.lrc?" + query, Map.of("Referer", "https://www.kuwo.cn/"));
            return normalizeNewLyric(decodeNewLyric(bytes, true));
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "kuwo newlyric failed id=%s error=%s", id, e.getMessage());
            return "";
        }
    }

    private String buildNewLyricQuery(String id, boolean lrcx) {
        String params = "user=12345,web,web,web&requester=localhost&req=1&rid=MUSIC_" + id + (lrcx ? "&lrcx=1" : "");
        byte[] bytes = params.getBytes(Charset.forName("UTF-8"));
        byte[] output = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) output[i] = (byte) (bytes[i] ^ NEW_LYRIC_KEY[i % NEW_LYRIC_KEY.length]);
        return Base64.encodeToString(output, Base64.NO_WRAP);
    }

    private String decodeNewLyric(byte[] bytes, boolean lrcx) throws Exception {
        if (bytes == null || bytes.length < 16) return "";
        String header = new String(bytes, 0, Math.min(bytes.length, 10), Charset.forName("UTF-8"));
        if (!"tp=content".equals(header)) return "";
        int offset = bodyOffset(bytes);
        if (offset < 0 || offset >= bytes.length) return "";
        byte[] inflated;
        try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(bytes, offset, bytes.length - offset)); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
            inflated = output.toByteArray();
        }
        if (!lrcx) return new String(inflated, Charset.forName("GB18030"));
        byte[] encrypted = Base64.decode(new String(inflated, Charset.forName("US-ASCII")).trim(), Base64.DEFAULT);
        byte[] decoded = new byte[encrypted.length];
        for (int i = 0; i < encrypted.length; i++) decoded[i] = (byte) (encrypted[i] ^ NEW_LYRIC_KEY[i % NEW_LYRIC_KEY.length]);
        return new String(decoded, Charset.forName("GB18030")).replace("\uFEFF", "");
    }

    private int bodyOffset(byte[] bytes) {
        for (int i = 0; i + 3 < bytes.length; i++) {
            if (bytes[i] == '\r' && bytes[i + 1] == '\n' && bytes[i + 2] == '\r' && bytes[i + 3] == '\n') return i + 4;
        }
        return -1;
    }

    private String normalizeNewLyric(String lrc) {
        if (TextUtils.isEmpty(lrc) || !lrc.contains("<")) return lrc;
        int offset = 1;
        int offset2 = 1;
        StringBuilder builder = new StringBuilder();
        for (String raw : lrc.replace("\r", "").split("\n")) {
            String line = raw.trim();
            if (line.startsWith("[kuwo:")) {
                int start = line.indexOf(':') + 1;
                int end = line.indexOf(']', start);
                String value = end > start ? line.substring(start, end) : "";
                int split = value.indexOf("][");
                if (split >= 0) value = value.substring(0, split);
                try {
                    int parsed = Integer.parseInt(value.trim(), 8);
                    offset = Math.max(1, parsed / 10);
                    offset2 = Math.max(1, parsed % 10);
                } catch (Exception ignored) {
                }
                continue;
            }
            Matcher lineMatcher = LRC_LINE.matcher(line);
            if (!lineMatcher.find()) {
                builder.append(raw).append('\n');
                continue;
            }
            ArrayList<NewWord> words = parseNewWords(lineMatcher.group(5), offset, offset2);
            if (words.isEmpty()) {
                builder.append(raw).append('\n');
                continue;
            }
            builder.append(lineMatcher.group(1));
            for (NewWord word : words) builder.append('<').append(word.startMs).append(',').append(word.durationMs()).append('>').append(word.text);
            builder.append('\n');
        }
        return builder.toString();
    }

    private ArrayList<NewWord> parseNewWords(String text, int offset, int offset2) {
        ArrayList<NewWord> words = new ArrayList<>();
        Matcher matcher = LRCX_WORD.matcher(text == null ? "" : text);
        while (matcher.find()) {
            String value = matcher.group(3);
            if (TextUtils.isEmpty(value) || value.trim().isEmpty()) continue;
            long a = parseLong(matcher.group(1));
            long b = parseLong(matcher.group(2));
            long start = Math.round(Math.abs((a + b) / (offset * 2.0)));
            long end = start + Math.round(Math.abs((a - b) / (offset2 * 2.0)));
            NewWord previous = words.isEmpty() ? null : words.get(words.size() - 1);
            if (previous != null && start < previous.endMs) previous.endMs = Math.max(previous.startMs, start);
            words.add(new NewWord(start, Math.max(start, end), value));
        }
        return words;
    }

    private String lyricFromMobi(String id) {
        String query = "type=lyric&req=2&lrcx=1&rid=" + id + "&songname=&artist=&corp=kuwo&fromchannel=bodian";
        HttpUrl url = HttpUrl.parse("http://mlyric.kuwo.cn/mobi.s").newBuilder()
                .addQueryParameter("f", "bodian")
                .addQueryParameter("q", Base64.encodeToString(query.getBytes(), Base64.NO_WRAP))
                .addQueryParameter("uid", "-1")
                .addQueryParameter("token", "")
                .build();
        try {
            JSONObject object = new JSONObject(get(url.toString(), Map.of("Referer", "https://www.kuwo.cn/")));
            JSONObject data = object.optJSONObject("data");
            String content = data == null ? "" : data.optString("content");
            if (TextUtils.isEmpty(content)) return "";
            return normalizeLrcx(new String(Base64.decode(content, Base64.DEFAULT)));
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "kuwo mobi lyric failed id=%s error=%s", id, e.getMessage());
            return "";
        }
    }

    private String normalizeLrcx(String lrcx) {
        if (TextUtils.isEmpty(lrcx) || !lrcx.contains("<")) return lrcx;
        ArrayList<Row> rows = new ArrayList<>();
        for (String raw : lrcx.replace("\r", "").split("\n")) rows.add(new Row(raw));
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (!row.hasWords()) {
                builder.append(row.raw).append('\n');
                continue;
            }
            long lineDuration = nextTime(rows, i) - row.timeMs;
            builder.append(row.timeTag);
            for (int j = 0; j < row.words.size(); j++) {
                Word word = row.words.get(j);
                long start = word.startMs();
                long duration = duration(row, j, lineDuration);
                builder.append('<').append(start).append(',').append(duration).append('>').append(word.text);
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private long duration(Row row, int index, long lineDuration) {
        Word word = row.words.get(index);
        long start = word.startMs();
        long duration = word.durationMs();
        if (duration <= 0 && index + 1 < row.words.size()) duration = Math.max(0, row.words.get(index + 1).startMs() - start);
        if (lineDuration > start) duration = duration > 0 ? Math.min(duration, lineDuration - start) : lineDuration - start;
        return Math.min(Math.max(duration, 240), 3000);
    }

    private long nextTime(List<Row> rows, int index) {
        for (int i = index + 1; i < rows.size(); i++) if (rows.get(i).timeMs >= 0) return rows.get(i).timeMs;
        return -1;
    }

    private String lyricFromOpenApi(String id) {
        HttpUrl url = HttpUrl.parse("https://www.kuwo.cn/openapi/v1/www/lyric/getlyric").newBuilder()
                .addQueryParameter("musicId", id)
                .addQueryParameter("httpsStatus", "1")
                .addQueryParameter("reqId", UUID.randomUUID().toString())
                .build();
        try {
            JSONObject object = new JSONObject(get(url.toString(), Map.of("Referer", "https://www.kuwo.cn/")));
            JSONObject data = object.optJSONObject("data");
            JSONArray array = data == null ? null : data.optJSONArray("lrclist");
            if (array == null || array.length() == 0) return "";
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String text = clean(item.optString("lineLyric"));
                long time = Math.round(parseDouble(item.optString("time")) * 1000);
                if (TextUtils.isEmpty(text)) continue;
                builder.append(formatTime(time)).append(text).append('\n');
            }
            return builder.toString();
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "kuwo lyric failed id=%s error=%s", id, e.getMessage());
            return "";
        }
    }

    private String get(String url, Map<String, String> headers) throws Exception {
        Request.Builder builder = new Request.Builder().url(url).header("User-Agent", USER_AGENT);
        for (Map.Entry<String, String> entry : headers.entrySet()) builder.header(entry.getKey(), entry.getValue());
        try (Response response = CLIENT.newCall(builder.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) return "";
            return response.body().string();
        }
    }

    private byte[] getBytes(String url, Map<String, String> headers) throws Exception {
        Request.Builder builder = new Request.Builder().url(url).header("User-Agent", USER_AGENT);
        for (Map.Entry<String, String> entry : headers.entrySet()) builder.header(entry.getKey(), entry.getValue());
        try (Response response = CLIENT.newCall(builder.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) return new byte[0];
            return response.body().bytes();
        }
    }

    private String id(JSONObject item) {
        String value = first(item, "DC_TARGETID", "MUSICRID", "MP3RID");
        int index = value.lastIndexOf('_');
        return index >= 0 ? value.substring(index + 1) : value;
    }

    private String first(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.optString(key, "").trim();
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private String clean(String text) {
        return Uri.decode(text == null ? "" : text)
                .replace("\\\\u0026", "&")
                .replace("\\u0026", "&")
                .replace("&nbsp;", " ")
                .replaceAll("<[^>]+>", "")
                .trim();
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private String formatTime(long timeMs) {
        long minute = timeMs / 60000;
        double second = (timeMs % 60000) / 1000.0;
        return String.format(Locale.US, "[%02d:%05.2f]", minute, second);
    }

    private long parseTime(Matcher matcher) {
        long minute = parseLong(matcher.group(2));
        long second = parseLong(matcher.group(3));
        String fraction = matcher.group(4);
        long millis = 0;
        if (!TextUtils.isEmpty(fraction)) {
            if (fraction.length() == 1) millis = parseLong(fraction) * 100;
            else if (fraction.length() == 2) millis = parseLong(fraction) * 10;
            else millis = parseLong(fraction.substring(0, 3));
        }
        return minute * 60_000 + second * 1000 + millis;
    }

    private static class Entry {
        private String id;
        private String name;
        private String artist;
        private String album;
        private int durationSec;
        private int score;
    }

    private class Row {

        private final String raw;
        private final String timeTag;
        private final long timeMs;
        private final ArrayList<Word> words;

        private Row(String raw) {
            this.raw = raw == null ? "" : raw;
            Matcher line = LRC_LINE.matcher(this.raw.trim());
            if (!line.find()) {
                timeTag = "";
                timeMs = -1;
                words = new ArrayList<>();
                return;
            }
            timeTag = line.group(1);
            timeMs = parseTime(line);
            words = parseWords(line.group(5));
        }

        private boolean hasWords() {
            return !words.isEmpty();
        }

        private ArrayList<Word> parseWords(String text) {
            ArrayList<Word> items = new ArrayList<>();
            Matcher matcher = LRCX_WORD.matcher(text == null ? "" : text);
            while (matcher.find()) {
                String value = matcher.group(3);
                if (TextUtils.isEmpty(value) || value.trim().isEmpty()) continue;
                items.add(new Word(parseLong(matcher.group(1)), parseLong(matcher.group(2)), value));
            }
            return items;
        }
    }

    private static class Word {

        private final long start;
        private final long value;
        private final String text;

        private Word(long start, long value, String text) {
            this.start = start;
            this.value = value;
            this.text = text == null ? "" : text;
        }

        private long startMs() {
            return Math.max(0, Math.round((start + value) / 6.0));
        }

        private long durationMs() {
            return Math.max(0, Math.round((start - value) / 6.0));
        }
    }

    private static class NewWord {

        private final long startMs;
        private long endMs;
        private final String text;

        private NewWord(long startMs, long endMs, String text) {
            this.startMs = Math.max(0, startMs);
            this.endMs = Math.max(this.startMs, endMs);
            this.text = text == null ? "" : text;
        }

        private long durationMs() {
            return Math.max(0, endMs - startMs);
        }
    }
}
