package com.fongmi.android.tv.player.lyrics;

import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NeteaseClient {

    private static final String TAG = "lyrics";
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36";
    private static final String EAPI_BASE = "https://interface3.music.163.com/eapi";
    private static final String EAPI_LYRIC_PATH = "/api/song/lyric/v1";
    private static final String EAPI_MATCH_PATH = "/api/search/match/new";
    private static final String EAPI_KEY = "e82ckenh8dichen8";
    private static final int MIN_SCORE = 58;
    private static final Pattern YRC_LINE = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");
    private static final Pattern YRC_WORD = Pattern.compile("\\((\\d+),(\\d+),\\d+\\)([^()]*)");
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
        Lyric lyric = lyric(entry.id);
        String text = !TextUtils.isEmpty(lyric.yrc) ? yrcToEnhancedLrc(lyric.yrc) : "";
        boolean yrcTimed = LyricsParser.hasTimedLine(text);
        if (!yrcTimed) text = lyric.lrc;
        if (!LyricsParser.hasTimedLine(text)) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "netease lyric empty id=%s name=%s artist=%s yrc=%d yrcTimed=%s lrc=%d", entry.id, entry.name, entry.artist, safeLength(lyric.yrc), yrcTimed, safeLength(lyric.lrc));
            return null;
        }
        text = LyricsParser.mergeTimedText(text, auxToLrc(lyric.trans), auxToLrc(lyric.roma));
        return new LyricsResult("Netease", entry.name, entry.artist, entry.album, text, entry.durationSec * 1000L, true, entry.score);
    }

    private List<Entry> ranked(LyricsRequest request) {
        ArrayList<Entry> ranked = new ArrayList<>();
        for (Entry entry : search(request)) {
            entry.score = score(request, entry);
            if (entry.score >= MIN_SCORE) ranked.add(entry);
        }
        ranked.sort(Comparator.comparingInt((Entry entry) -> entry.score).reversed());
        if (SpiderDebug.isEnabled()) logRanked(request, ranked);
        return ranked;
    }

    private List<Entry> search(LyricsRequest request) {
        List<Entry> entries = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        searchMatch(entries, seen, request);
        for (String keyword : keywords(request)) searchCloud(entries, seen, request, keyword);
        for (String keyword : keywords(request)) {
            if (!entries.isEmpty()) break;
            searchLegacy(entries, seen, request, keyword);
        }
        return entries;
    }

    private void searchMatch(List<Entry> entries, Set<Long> seen, LyricsRequest request) {
        int before = entries.size();
        try {
            JSONObject song = new JSONObject()
                    .put("title", request.getTitle())
                    .put("album", request.getAlbum())
                    .put("artist", request.getArtist())
                    .put("duration", request.getDurationSec())
                    .put("persistId", request.contentSignature());
            JSONObject object = new JSONObject(postEapi(EAPI_MATCH_PATH, new JSONObject().put("songs", new JSONArray().put(song).toString())));
            JSONObject result = object.optJSONObject("result");
            JSONArray array = result == null ? null : result.optJSONArray("songs");
            if (array == null) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "netease match raw=0 added=0 no-array code=%d", object.optInt("code", -1));
                return;
            }
            for (int i = 0; i < array.length(); i++) addEntry(entries, seen, parseEntry(array.optJSONObject(i), request, false, null));
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "netease match raw=%d added=%d", array.length(), entries.size() - before);
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "netease match failed title=%s error=%s", request.getTitle(), e.getMessage());
        }
    }

    private void searchCloud(List<Entry> entries, Set<Long> seen, LyricsRequest request, String keyword) {
        int before = entries.size();
        HttpUrl url = HttpUrl.parse("https://music.163.com/api/cloudsearch/pc").newBuilder()
                .addQueryParameter("s", keyword)
                .addQueryParameter("type", "1")
                .addQueryParameter("limit", "8")
                .addQueryParameter("offset", "0")
                .addQueryParameter("total", "true")
                .build();
        try {
            JSONObject object = new JSONObject(get(url.toString()));
            JSONObject result = object.optJSONObject("result");
            JSONArray array = result == null ? null : result.optJSONArray("songs");
            if (array == null) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "netease cloud keyword=%s raw=0 added=0 no-array code=%d", keyword, object.optInt("code", -1));
                return;
            }
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                Entry entry = parseEntry(item, request, false, null);
                addEntry(entries, seen, entry);
                Entry origin = parseOriginEntry(item, request, entry);
                addEntry(entries, seen, origin);
            }
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "netease cloud keyword=%s raw=%d added=%d", keyword, array.length(), entries.size() - before);
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "netease cloud failed title=%s error=%s", request.getTitle(), e.getMessage());
        }
    }

    private void searchLegacy(List<Entry> entries, Set<Long> seen, LyricsRequest request, String keyword) {
        int before = entries.size();
        HttpUrl url = HttpUrl.parse("https://music.163.com/api/search/get/web").newBuilder()
                .addQueryParameter("s", keyword)
                .addQueryParameter("type", "1")
                .addQueryParameter("limit", "8")
                .addQueryParameter("offset", "0")
                .build();
        try {
            JSONObject object = new JSONObject(get(url.toString()));
            JSONObject result = object.optJSONObject("result");
            JSONArray array = result == null ? null : result.optJSONArray("songs");
            if (array == null) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "netease legacy keyword=%s raw=0 added=0 no-array code=%d", keyword, object.optInt("code", -1));
                return;
            }
            for (int i = 0; i < array.length(); i++) addEntry(entries, seen, parseEntry(array.optJSONObject(i), request, false, null));
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "netease legacy keyword=%s raw=%d added=%d", keyword, array.length(), entries.size() - before);
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "netease legacy failed title=%s error=%s", request.getTitle(), e.getMessage());
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
        return (entry.origin ? 18 : 0) + LyricsMatcher.matchScore(request, entry.name, entry.artist, entry.durationSec);
    }

    private Lyric lyric(long id) {
        Lyric eapi = lyricFromEapi(id);
        if (LyricsParser.hasTimedLine(eapi.yrc) || LyricsParser.hasTimedLine(eapi.lrc)) return eapi;
        return lyricFromLegacy(id);
    }

    private Lyric lyricFromEapi(long id) {
        try {
            JSONObject object = new JSONObject()
                    .put("id", id)
                    .put("cp", false)
                    .put("tv", 0)
                    .put("lv", 0)
                    .put("rv", 0)
                    .put("kv", 0)
                    .put("yv", 0)
                    .put("ytv", 0)
                    .put("yrv", 0);
            Lyric lyric = parseLyric(new JSONObject(postEapi(EAPI_LYRIC_PATH, object)));
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "netease eapi lyric id=%s yrc=%d lrc=%d trans=%d roma=%d", id, safeLength(lyric.yrc), safeLength(lyric.lrc), safeLength(lyric.trans), safeLength(lyric.roma));
            return lyric;
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "netease eapi lyric failed id=%s error=%s", id, e.getMessage());
            return new Lyric();
        }
    }

    private Lyric lyricFromLegacy(long id) {
        Lyric lyric = new Lyric();
        HttpUrl url = HttpUrl.parse("https://music.163.com/api/song/lyric").newBuilder()
                .addQueryParameter("id", String.valueOf(id))
                .addQueryParameter("lv", "1")
                .addQueryParameter("kv", "1")
                .addQueryParameter("tv", "-1")
                .addQueryParameter("yv", "1")
                .addQueryParameter("ytv", "1")
                .addQueryParameter("yrv", "1")
                .build();
        try {
            JSONObject object = new JSONObject(get(url.toString()));
            JSONObject yrc = object.optJSONObject("yrc");
            JSONObject lrc = object.optJSONObject("lrc");
            JSONObject trans = firstObject(object, "tlyric", "ytlrc");
            JSONObject roma = firstObject(object, "romalrc", "yromalrc");
            lyric.yrc = yrc == null ? "" : yrc.optString("lyric");
            lyric.lrc = lrc == null ? "" : lrc.optString("lyric");
            lyric.trans = trans == null ? "" : trans.optString("lyric");
            lyric.roma = roma == null ? "" : roma.optString("lyric");
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "netease legacy lyric id=%s yrc=%d lrc=%d trans=%d roma=%d", id, safeLength(lyric.yrc), safeLength(lyric.lrc), safeLength(lyric.trans), safeLength(lyric.roma));
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "netease lyric failed id=%s error=%s", id, e.getMessage());
        }
        return lyric;
    }

    private Lyric parseLyric(JSONObject object) {
        Lyric lyric = new Lyric();
        JSONObject yrc = object.optJSONObject("yrc");
        JSONObject lrc = object.optJSONObject("lrc");
        JSONObject trans = firstObject(object, "ytlrc", "tlyric");
        JSONObject roma = firstObject(object, "yromalrc", "romalrc");
        lyric.yrc = yrc == null ? "" : yrc.optString("lyric");
        lyric.lrc = lrc == null ? "" : lrc.optString("lyric");
        lyric.trans = trans == null ? "" : trans.optString("lyric");
        lyric.roma = roma == null ? "" : roma.optString("lyric");
        return lyric;
    }

    private String yrcToEnhancedLrc(String yrc) {
        StringBuilder builder = new StringBuilder();
        for (String raw : (yrc == null ? "" : yrc).replace("\r", "").split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            Matcher lineMatcher = YRC_LINE.matcher(line);
            if (!lineMatcher.find()) continue;
            long lineStart = parseLong(lineMatcher.group(1));
            long lineDuration = parseLong(lineMatcher.group(2));
            String lineContent = lineMatcher.group(3);
            StringBuilder words = new StringBuilder();
            Matcher wordMatcher = YRC_WORD.matcher(lineContent);
            while (wordMatcher.find()) {
                String word = wordMatcher.group(3);
                if (TextUtils.isEmpty(word)) continue;
                long start = normalizeWordStart(parseLong(wordMatcher.group(1)), lineStart, lineDuration);
                long duration = parseLong(wordMatcher.group(2));
                words.append('<').append(start).append(',').append(Math.max(0, duration)).append('>').append(word);
            }
            if (words.length() > 0) builder.append(formatTime(lineStart)).append(words).append('\n');
        }
        return builder.toString();
    }

    private String auxToLrc(String lyric) {
        if (TextUtils.isEmpty(lyric)) return "";
        if (LyricsParser.hasTimedLine(lyric)) return lyric;
        StringBuilder builder = new StringBuilder();
        for (String raw : lyric.replace("\r", "").split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            Matcher lineMatcher = YRC_LINE.matcher(line);
            if (!lineMatcher.find()) continue;
            String text = cleanYrcText(lineMatcher.group(3));
            if (!TextUtils.isEmpty(text)) builder.append(formatTime(parseLong(lineMatcher.group(1)))).append(text).append('\n');
        }
        return builder.toString();
    }

    private String cleanYrcText(String text) {
        StringBuilder builder = new StringBuilder();
        Matcher matcher = YRC_WORD.matcher(text == null ? "" : text);
        while (matcher.find()) builder.append(matcher.group(3));
        return clean(builder.length() > 0 ? builder.toString() : text);
    }

    private long normalizeWordStart(long start, long lineStart, long lineDuration) {
        if (lineStart > 2000 && start >= lineStart && (lineDuration <= 0 || start <= lineStart + lineDuration + 500)) return Math.max(0, start - lineStart);
        return Math.max(0, start);
    }

    private List<String> keywords(LyricsRequest request) {
        return request.searchKeywords();
    }

    private Entry parseEntry(JSONObject item, LyricsRequest request, boolean origin, Entry parent) {
        if (item == null) return null;
        Entry entry = new Entry();
        entry.id = item.optLong("id");
        entry.name = clean(item.optString("name"));
        entry.artist = artists(firstArray(item, "ar", "artists"));
        JSONObject album = firstObjectAllowEmpty(item, "al", "album");
        entry.album = album == null ? "" : clean(album.optString("name"));
        entry.durationSec = durationSec(firstLong(item, "dt", "duration"));
        entry.origin = origin;
        entry.parentId = parent == null ? 0 : parent.id;
        if (entry.durationSec <= 0 && request.getDurationSec() > 0) entry.durationSec = request.getDurationSec();
        return entry;
    }

    private Entry parseOriginEntry(JSONObject item, LyricsRequest request, Entry parent) {
        if (item == null) return null;
        JSONObject origin = item.optJSONObject("originSongSimpleData");
        if (origin == null) return null;
        Entry entry = new Entry();
        entry.id = origin.optLong("songId");
        entry.name = clean(origin.optString("name"));
        entry.artist = artists(origin.optJSONArray("artists"));
        JSONObject album = origin.optJSONObject("albumMeta");
        entry.album = album == null ? "" : clean(album.optString("name"));
        entry.durationSec = request.getDurationSec() > 0 ? request.getDurationSec() : parent == null ? 0 : parent.durationSec;
        entry.origin = true;
        entry.parentId = parent == null ? 0 : parent.id;
        return entry;
    }

    private void addEntry(List<Entry> entries, Set<Long> seen, Entry entry) {
        if (entry == null || entry.id <= 0 || TextUtils.isEmpty(entry.name) || !seen.add(entry.id)) return;
        entries.add(entry);
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
                .header("Referer", "https://music.163.com/")
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return "";
            return response.body().string();
        }
    }

    private String postEapi(String path, JSONObject object) throws Exception {
        String text = object.toString();
        String digest = md5("nobody" + path + "use" + text + "md5forencrypt");
        String data = path + "-36cd479b6b5-" + text + "-36cd479b6b5-" + digest;
        FormBody body = new FormBody.Builder().add("params", aesEcbHex(data)).build();
        Request request = new Request.Builder()
                .url(eapiUrl(path))
                .post(body)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://music.163.com/")
                .header("Origin", "https://music.163.com")
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return "";
            return response.body().string();
        }
    }

    private String eapiUrl(String path) {
        return EAPI_BASE + path.replaceFirst("^/api", "");
    }

    private String aesEcbHex(String text) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(EAPI_KEY.getBytes(StandardCharsets.UTF_8), "AES"));
        return hex(cipher.doFinal(text.getBytes(StandardCharsets.UTF_8))).toUpperCase(Locale.ROOT);
    }

    private String md5(String text) throws Exception {
        return hex(MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) builder.append(String.format(Locale.US, "%02x", value & 0xff));
        return builder.toString();
    }

    private String artists(JSONArray array) {
        List<String> names = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject artist = array.optJSONObject(i);
                String name = artist == null ? "" : clean(artist.optString("name"));
                if (!TextUtils.isEmpty(name)) names.add(name);
            }
        }
        return TextUtils.join(" / ", names);
    }

    private JSONObject firstObject(JSONObject object, String... keys) {
        for (String key : keys) {
            JSONObject value = object.optJSONObject(key);
            if (value != null && !TextUtils.isEmpty(value.optString("lyric"))) return value;
        }
        return null;
    }

    private JSONObject firstObjectAllowEmpty(JSONObject object, String... keys) {
        if (object == null) return null;
        for (String key : keys) {
            JSONObject value = object.optJSONObject(key);
            if (value != null) return value;
        }
        return null;
    }

    private JSONArray firstArray(JSONObject object, String... keys) {
        if (object == null) return null;
        for (String key : keys) {
            JSONArray value = object.optJSONArray(key);
            if (value != null) return value;
        }
        return null;
    }

    private long firstLong(JSONObject object, String... keys) {
        if (object == null) return 0;
        for (String key : keys) {
            long value = object.optLong(key, 0);
            if (value > 0) return value;
        }
        return 0;
    }

    private int durationSec(long duration) {
        if (duration <= 0) return 0;
        return duration > 10000 ? Math.round(duration / 1000f) : (int) Math.round(duration);
    }

    private String clean(String text) {
        return Uri.decode(text == null ? "" : text)
                .replace("&nbsp;", " ")
                .replaceAll("<[^>]+>", "")
                .trim();
    }

    private int safeLength(String text) {
        return text == null ? 0 : text.length();
    }

    private void logRanked(LyricsRequest request, List<Entry> ranked) {
        StringBuilder builder = new StringBuilder();
        int count = Math.min(ranked == null ? 0 : ranked.size(), 5);
        for (int i = 0; i < count; i++) {
            Entry item = ranked.get(i);
            if (i > 0) builder.append(", ");
            builder.append(item.score).append(':').append(item.name).append('/').append(item.artist).append('/').append(item.durationSec);
            if (item.origin) builder.append("/origin").append(item.parentId > 0 ? '@' + String.valueOf(item.parentId) : "");
        }
        SpiderDebug.log(TAG, "netease ranked title=%s artist=%s count=%d top=[%s]", request.getTitle(), request.getArtist(), ranked == null ? 0 : ranked.size(), builder);
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
        private long id;
        private String name;
        private String artist;
        private String album;
        private int durationSec;
        private int score;
        private boolean origin;
        private long parentId;
    }

    private static class Lyric {
        private String yrc;
        private String lrc;
        private String trans;
        private String roma;
    }
}
