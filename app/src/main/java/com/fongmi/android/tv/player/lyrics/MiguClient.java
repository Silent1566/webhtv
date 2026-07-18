package com.fongmi.android.tv.player.lyrics;

import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MiguClient {

    private static final String TAG = "lyrics";
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; U; Android 11.0.0; zh-cn; MI 11 Build/OPR1.170623.032) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Mobile Safari/534.30";
    private static final String DEVICE_ID = "963B7AA0D21511ED807EE5846EC87D20";
    private static final String SIGNATURE_MD5 = "6cdc72a439cef99a3418d2a78aa28c73";
    private static final String APP_ID = "yyapp2d16148780a1dcc7408e06336b98cfd50";
    private static final long DELTA = 2654435769L;
    private static final long[] MRC_KEY = new long[]{
            27303562373562475L,
            18014862372307051L,
            22799692160172081L,
            34058940340699235L,
            30962724186095721L,
            27303523720101991L,
            27303523720101998L,
            31244139033526382L,
            28992395054481524L,
    };
    private static final int MIN_SCORE = 58;
    private static final Pattern MRC_LINE = Pattern.compile("^\\s*\\[(\\d+),\\d+](.*)$");
    private static final Pattern MRC_WORD_TAG = Pattern.compile("\\((\\d+),(\\d+)\\)");
    private static final Pattern MRC_WORD_ALL = Pattern.compile("\\(\\d+,\\d+\\)");
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
        Lyric lyric = lyric(entry);
        boolean word = LyricsParser.hasTimedLine(lyric.word);
        String text = word ? lyric.word : lyric.lrc;
        if (!LyricsParser.hasTimedLine(text)) return null;
        text = LyricsParser.mergeTimedText(text, lyric.trans);
        return new LyricsResult(word ? "Migu MRC" : "Migu", entry.name, entry.artist, entry.album, text, entry.durationSec * 1000L, true, entry.score + (word ? 8 : 0));
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
        String time = String.valueOf(System.currentTimeMillis());
        HttpUrl url = HttpUrl.parse("https://jadeite.migu.cn/music_search/v3/search/searchAll").newBuilder()
                .addQueryParameter("isCorrect", "0")
                .addQueryParameter("isCopyright", "1")
                .addQueryParameter("searchSwitch", "{\"song\":1,\"album\":0,\"singer\":0,\"tagSong\":1,\"mvSong\":0,\"bestShow\":1,\"songlist\":0,\"lyricSong\":0}")
                .addQueryParameter("pageSize", "8")
                .addQueryParameter("text", keyword)
                .addQueryParameter("pageNo", "1")
                .addQueryParameter("sort", "0")
                .addQueryParameter("sid", "USS")
                .build();
        try {
            JSONObject object = new JSONObject(get(url.toString(), searchHeaders(keyword, time)));
            if (!"000000".equals(object.optString("code"))) return;
            JSONObject data = object.optJSONObject("songResultData");
            JSONArray array = data == null ? null : data.optJSONArray("resultList");
            if (array == null) return;
            for (int i = 0; i < array.length(); i++) addEntries(entries, seen, array.opt(i));
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "migu search failed title=%s error=%s", request.getTitle(), e.getMessage());
        }
    }

    private void addEntries(List<Entry> entries, Set<String> seen, Object item) {
        if (item instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) addEntries(entries, seen, array.opt(i));
            return;
        }
        if (!(item instanceof JSONObject object)) return;
        Entry entry = parseEntry(object);
        if (entry != null && seen.add(entry.key())) entries.add(entry);
    }

    private Entry parseEntry(JSONObject object) {
        Entry entry = new Entry();
        entry.id = clean(object.optString("songId"));
        entry.copyrightId = clean(object.optString("copyrightId"));
        entry.name = clean(first(object, "name", "songName"));
        entry.artist = artists(object.optJSONArray("singerList"));
        entry.album = clean(object.optString("album"));
        entry.durationSec = durationSec(object.opt("duration"));
        entry.lrcUrl = clean(first(object, "lrcUrl", "lyricUrl"));
        entry.mrcUrl = clean(object.optString("mrcurl"));
        entry.trcUrl = clean(object.optString("trcUrl"));
        if (TextUtils.isEmpty(entry.id) || TextUtils.isEmpty(entry.name)) return null;
        return entry;
    }

    private Lyric lyric(Entry entry) {
        Lyric lyric = new Lyric();
        Entry target = ensureLyricUrls(entry);
        if (!TextUtils.isEmpty(target.mrcUrl)) lyric.word = mrcToEnhancedLrc(getText(target.mrcUrl, true));
        if (!LyricsParser.hasTimedLine(lyric.word) && !TextUtils.isEmpty(target.lrcUrl)) lyric.lrc = getText(target.lrcUrl, false);
        if (!TextUtils.isEmpty(target.trcUrl)) lyric.trans = getText(target.trcUrl, false);
        return lyric;
    }

    private Entry ensureLyricUrls(Entry entry) {
        if (!TextUtils.isEmpty(entry.mrcUrl) || !TextUtils.isEmpty(entry.lrcUrl)) return entry;
        if (TextUtils.isEmpty(entry.copyrightId)) return entry;
        try {
            HttpUrl url = HttpUrl.parse("https://app.c.nf.migu.cn/MIGUM3.0/v1.0/content/resourceinfo.do").newBuilder()
                    .addQueryParameter("copyrightId", entry.copyrightId)
                    .addQueryParameter("resourceType", "2")
                    .build();
            JSONObject object = new JSONObject(get(url.toString(), lyricHeaders()));
            JSONArray array = object.optJSONArray("resource");
            JSONObject data = array == null || array.length() == 0 ? null : array.optJSONObject(0);
            if (data != null) {
                entry.lrcUrl = clean(first(data, "lrcUrl", "lyricUrl"));
                entry.mrcUrl = clean(first(data, "mrcUrl", "mrcurl"));
                entry.trcUrl = clean(data.optString("trcUrl"));
            }
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "migu lyric info failed id=%s error=%s", entry.copyrightId, e.getMessage());
        }
        return entry;
    }

    private String getText(String url, boolean mrc) {
        try {
            String text = get(url, lyricHeaders());
            return mrc ? decryptMrc(text) : text;
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "migu lyric text failed url=%s error=%s", url, e.getMessage());
            return "";
        }
    }

    private String mrcToEnhancedLrc(String mrc) {
        if (TextUtils.isEmpty(mrc)) return "";
        StringBuilder lineLrc = new StringBuilder();
        StringBuilder wordLrc = new StringBuilder();
        for (String raw : mrc.replace("\r", "").split("\n")) {
            Matcher lineMatcher = MRC_LINE.matcher(raw.trim());
            if (!lineMatcher.find()) continue;
            long lineStart = parseLong(lineMatcher.group(1));
            String words = lineMatcher.group(2);
            String text = MRC_WORD_ALL.matcher(words).replaceAll("").trim();
            if (!TextUtils.isEmpty(text)) lineLrc.append(formatTime(lineStart)).append(text).append('\n');
            String enhanced = parseMrcWords(words, lineStart);
            if (!TextUtils.isEmpty(enhanced)) wordLrc.append(formatTime(lineStart)).append(enhanced).append('\n');
        }
        return wordLrc.length() > 0 ? wordLrc.toString() : lineLrc.toString();
    }

    private String parseMrcWords(String words, long lineStart) {
        StringBuilder builder = parseMrcWords(words, lineStart, true);
        if (builder.length() == 0) builder = parseMrcWords(words, lineStart, false);
        return builder.toString();
    }

    private StringBuilder parseMrcWords(String words, long lineStart, boolean tagAfterWord) {
        StringBuilder builder = new StringBuilder();
        Matcher matcher = MRC_WORD_TAG.matcher(words == null ? "" : words);
        int cursor = 0;
        while (matcher.find()) {
            String text = tagAfterWord ? words.substring(cursor, matcher.start()) : nextText(words, matcher.end());
            cursor = matcher.end();
            text = clean(text);
            if (TextUtils.isEmpty(text)) continue;
            long start = Math.max(0, parseLong(matcher.group(1)) - lineStart);
            long duration = Math.max(0, parseLong(matcher.group(2)));
            builder.append('<').append(start).append(',').append(duration).append('>').append(text);
        }
        return builder;
    }

    private String nextText(String words, int start) {
        Matcher next = MRC_WORD_TAG.matcher(words == null ? "" : words);
        if (!next.find(start)) return words == null ? "" : words.substring(start);
        return words.substring(start, next.start());
    }

    private String decryptMrc(String text) {
        if (TextUtils.isEmpty(text) || text.length() < 32) return text;
        try {
            long[] data = toLongArray(text.trim());
            if (data.length == 0) return "";
            teaDecrypt(data);
            ByteArrayOutputStream output = new ByteArrayOutputStream(data.length * 8);
            for (long value : data) writeLong(output, value);
            return new String(output.toByteArray(), StandardCharsets.UTF_16LE)
                    .replace("\u0000", "")
                    .replace("\uFEFF", "");
        } catch (Exception e) {
            return text;
        }
    }

    private long[] toLongArray(String text) {
        int length = text.length() / 16;
        long[] data = new long[length];
        for (int i = 0; i < length; i++) data[i] = Long.parseUnsignedLong(text.substring(i * 16, i * 16 + 16), 16);
        return data;
    }

    private void teaDecrypt(long[] data) {
        int length = data.length;
        long y = data[0];
        long sum = (6L + 52L / length) * DELTA;
        while (sum != 0) {
            int e = (int) ((sum >>> 2) & 3);
            for (int p = length - 1; p > 0; p--) {
                long z = data[p - 1];
                y = data[p] - (((y ^ sum) + (z ^ MRC_KEY[(p & 3) ^ e])) ^ (((z >> 5) ^ (y << 2)) + ((y >> 3) ^ (z << 4))));
                data[p] = y;
            }
            long z = data[length - 1];
            y = data[0] - (((MRC_KEY[e] ^ z) + (y ^ sum)) ^ (((z >> 5) ^ (y << 2)) + ((y >> 3) ^ (z << 4))));
            data[0] = y;
            sum -= DELTA;
        }
    }

    private void writeLong(ByteArrayOutputStream output, long value) {
        for (int i = 0; i < 8; i++) output.write((byte) (value >> (i * 8)));
    }

    private Map<String, String> searchHeaders(String keyword, String time) {
        return Map.of(
                "uiVersion", "A_music_3.6.1",
                "deviceId", DEVICE_ID,
                "timestamp", time,
                "sign", md5(keyword + SIGNATURE_MD5 + APP_ID + DEVICE_ID + time),
                "channel", "0146921");
    }

    private Map<String, String> lyricHeaders() {
        return Map.of(
                "Referer", "https://app.c.nf.migu.cn/",
                "channel", "0146921");
    }

    private String get(String url, Map<String, String> headers) throws Exception {
        Request.Builder builder = new Request.Builder().url(url).header("User-Agent", USER_AGENT);
        for (Map.Entry<String, String> entry : headers.entrySet()) builder.header(entry.getKey(), entry.getValue());
        try (Response response = CLIENT.newCall(builder.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) return "";
            return response.body().string();
        }
    }

    private int score(LyricsRequest request, Entry entry) {
        return LyricsMatcher.matchScore(request, entry.name, entry.artist, entry.durationSec);
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

    private String artists(JSONArray array) {
        List<String> names = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject artist = array.optJSONObject(i);
                String name = artist == null ? clean(array.optString(i)) : clean(first(artist, "name", "singerName"));
                if (!TextUtils.isEmpty(name)) names.add(name);
            }
        }
        return TextUtils.join(" / ", names);
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

    private int durationSec(Object value) {
        if (value == null) return 0;
        String text = String.valueOf(value).trim();
        if (text.contains(":")) {
            String[] parts = text.split(":");
            int total = 0;
            for (String part : parts) total = total * 60 + parseInt(part);
            return total;
        }
        long duration = parseLong(text);
        if (duration > 10000) return (int) Math.round(duration / 1000.0);
        return (int) Math.max(0, duration);
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
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
        long second = timeMs % 60000;
        return String.format(Locale.US, "[%02d:%02d.%03d]", minute, second / 1000, second % 1000);
    }

    private String md5(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("MD5").digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) builder.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            return builder.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static class Entry {
        private String id;
        private String copyrightId;
        private String name;
        private String artist;
        private String album;
        private int durationSec;
        private String lrcUrl;
        private String mrcUrl;
        private String trcUrl;
        private int score;

        private String key() {
            return TextUtils.isEmpty(copyrightId) ? id : copyrightId;
        }
    }

    private static class Lyric {
        private String word;
        private String lrc;
        private String trans;
    }
}
