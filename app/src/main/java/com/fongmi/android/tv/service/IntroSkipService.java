package com.fongmi.android.tv.service;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

public class IntroSkipService {

    private static final String PROVIDER_INTRO_DB = "IntroDB";
    private static final String PROVIDER_THE_INTRO_DB = "TheIntroDB";
    private static final String INTRO_DB_SEGMENTS = "https://api.introdb.app/segments";
    private static final String THE_INTRO_DB_MEDIA = "https://api.theintrodb.org/v3/media";
    private static final long TIMEOUT_MS = TimeUnit.SECONDS.toMillis(5);
    private static final int MAX_CACHE = 128;
    private static final Map<String, IntroSkipPlan> CACHE = Collections.synchronizedMap(new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, IntroSkipPlan> eldest) {
            return size() > MAX_CACHE;
        }
    });

    public IntroSkipPlan load(@NonNull Query query) {
        if (!query.hasLookupKey()) return IntroSkipPlan.empty();
        String key = query.cacheKey();
        IntroSkipPlan cached = CACHE.get(key);
        if (cached != null) {
            SpiderDebug.log("intro-skip", "cache hit key=%s", key);
            return cached;
        }
        SpiderDebug.log("intro-skip", "query start tmdbId=%d imdbId=%s mediaType=%s season=%d episode=%d durationMs=%d", query.tmdbId, query.imdbId, query.mediaType, query.season, query.episode, query.durationMs);
        IntroSkipPlan plan = loadRemote(query);
        CACHE.put(key, plan);
        SpiderDebug.log("intro-skip", "query done openings=%d endings=%d", plan.getOpenings().size(), plan.getEndings().size());
        return plan;
    }

    private IntroSkipPlan loadRemote(Query query) {
        List<Future<IntroSkipPlan>> futures = new ArrayList<>();
        ExecutorCompletionService<IntroSkipPlan> completion = new ExecutorCompletionService<>(Task.largeExecutor());
        if (!isEmpty(query.imdbId) && query.season > 0 && query.episode > 0) {
            futures.add(completion.submit(() -> fetchIntroDb(query)));
        }
        if (query.tmdbId > 0) {
            futures.add(completion.submit(() -> fetchTheIntroDb(query)));
        }
        if (futures.isEmpty()) return IntroSkipPlan.empty();

        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        List<IntroSkipPlan> plans = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            long waitMs = Math.max(1, deadline - System.currentTimeMillis());
            try {
                Future<IntroSkipPlan> future = completion.poll(waitMs, TimeUnit.MILLISECONDS);
                if (future != null) plans.add(future.get());
            } catch (Throwable e) {
                SpiderDebug.log("intro-skip", "provider failed error=%s", e.getMessage());
            }
        }
        for (Future<IntroSkipPlan> future : futures) if (!future.isDone()) future.cancel(true);
        return IntroSkipPlan.merge(plans);
    }

    private IntroSkipPlan fetchIntroDb(Query query) {
        HttpUrl url = HttpUrl.parse(INTRO_DB_SEGMENTS).newBuilder()
                .addQueryParameter("imdb_id", query.imdbId)
                .addQueryParameter("season", String.valueOf(query.season))
                .addQueryParameter("episode", String.valueOf(query.episode))
                .build();
        return fetch(url, PROVIDER_INTRO_DB, query.durationMs);
    }

    private IntroSkipPlan fetchTheIntroDb(Query query) {
        HttpUrl.Builder builder = HttpUrl.parse(THE_INTRO_DB_MEDIA).newBuilder()
                .addQueryParameter("tmdb_id", String.valueOf(query.tmdbId));
        if (query.isTv() && query.season > 0 && query.episode > 0) {
            builder.addQueryParameter("season", String.valueOf(query.season));
            builder.addQueryParameter("episode", String.valueOf(query.episode));
        }
        if (query.durationMs > 0) builder.addQueryParameter("duration_ms", String.valueOf(query.durationMs));
        return fetch(builder.build(), PROVIDER_THE_INTRO_DB, query.durationMs);
    }

    private IntroSkipPlan fetch(HttpUrl url, String provider, long durationMs) {
        long start = System.currentTimeMillis();
        SpiderDebug.log("intro-skip", "%s request url=%s", provider, url.toString());
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = OkHttp.client(TIMEOUT_MS).newCall(request).execute()) {
            if (response.body() == null || !response.isSuccessful()) {
                SpiderDebug.log("intro-skip", "%s http=%d empty=%s url=%s", provider, response.code(), response.body() == null, url.toString());
                return IntroSkipPlan.empty();
            }
            String body = response.body().string();
            IntroSkipPlan plan = PROVIDER_INTRO_DB.equals(provider)
                    ? parseIntroDb(body, durationMs)
                    : parseTheIntroDb(body, durationMs);
            SpiderDebug.log("intro-skip", "%s loaded openings=%d endings=%d cost=%dms url=%s", provider, plan.getOpenings().size(), plan.getEndings().size(), System.currentTimeMillis() - start, url.toString());
            return plan;
        } catch (Throwable e) {
            SpiderDebug.log("intro-skip", "%s failed error=%s cost=%dms url=%s", provider, e.getMessage(), System.currentTimeMillis() - start, url.toString());
            return IntroSkipPlan.empty();
        }
    }

    public static IntroSkipPlan parseIntroDb(String body, long durationMs) {
        JsonObject object = parseObject(body);
        if (object == null) return IntroSkipPlan.empty();
        List<Segment> segments = new ArrayList<>();
        addIntroDbSegment(segments, object, "recap", Segment.Kind.RECAP, durationMs);
        addIntroDbSegment(segments, object, "intro", Segment.Kind.INTRO, durationMs);
        addIntroDbSegment(segments, object, "outro", Segment.Kind.OUTRO, durationMs);
        return IntroSkipPlan.from(segments);
    }

    public static IntroSkipPlan parseTheIntroDb(String body, long durationMs) {
        JsonObject object = parseObject(body);
        if (object == null) return IntroSkipPlan.empty();
        List<Segment> segments = new ArrayList<>();
        addTheIntroDbSegments(segments, object, "recap", Segment.Kind.RECAP, durationMs);
        addTheIntroDbSegments(segments, object, "intro", Segment.Kind.INTRO, durationMs);
        addTheIntroDbSegments(segments, object, "credits", Segment.Kind.OUTRO, durationMs);
        return IntroSkipPlan.from(segments);
    }

    private static JsonObject parseObject(String body) {
        try {
            JsonElement element = JsonParser.parseString(body);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Throwable e) {
            return null;
        }
    }

    private static void addIntroDbSegment(List<Segment> segments, JsonObject root, String key, Segment.Kind kind, long durationMs) {
        JsonObject object = object(root, key);
        if (object == null) return;
        Long start = millis(object, "start_ms", "start_sec");
        Long end = millis(object, "end_ms", "end_sec");
        Segment segment = Segment.create(kind, PROVIDER_INTRO_DB, start, end, durationMs, number(object, "confidence", 0.5), integer(object, "submission_count", 0));
        if (segment != null) segments.add(segment);
    }

    private static void addTheIntroDbSegments(List<Segment> segments, JsonObject root, String key, Segment.Kind kind, long durationMs) {
        JsonArray array = array(root, key);
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            Long start = millis(object, "start_ms", null);
            Long end = millis(object, "end_ms", null);
            Segment segment = Segment.create(kind, PROVIDER_THE_INTRO_DB, start, end, durationMs, 0.5, 0);
            if (segment != null) segments.add(segment);
        }
    }

    private static JsonObject object(JsonObject root, String key) {
        if (root == null || !root.has(key) || root.get(key).isJsonNull() || !root.get(key).isJsonObject()) return null;
        return root.getAsJsonObject(key);
    }

    private static JsonArray array(JsonObject root, String key) {
        if (root == null || !root.has(key) || root.get(key).isJsonNull() || !root.get(key).isJsonArray()) return new JsonArray();
        return root.getAsJsonArray(key);
    }

    private static Long millis(JsonObject object, String msKey, String secKey) {
        Long ms = longValue(object, msKey);
        if (ms != null) return ms;
        if (secKey == null) return null;
        Double sec = doubleValue(object, secKey);
        return sec == null ? null : Math.round(sec * 1000.0);
    }

    private static Long longValue(JsonObject object, String key) {
        Double value = doubleValue(object, key);
        return value == null ? null : Math.round(value);
    }

    private static Double doubleValue(JsonObject object, String key) {
        try {
            if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) return null;
            return object.get(key).getAsDouble();
        } catch (Throwable e) {
            return null;
        }
    }

    private static double number(JsonObject object, String key, double fallback) {
        Double value = doubleValue(object, key);
        return value == null ? fallback : value;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        Long value = longValue(object, key);
        return value == null ? fallback : value.intValue();
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class Query {

        private final int tmdbId;
        private final String imdbId;
        private final String mediaType;
        private final int season;
        private final int episode;
        private final long durationMs;

        public Query(int tmdbId, String imdbId, String mediaType, int season, int episode, long durationMs) {
            this.tmdbId = tmdbId;
            this.imdbId = imdbId == null ? "" : imdbId.trim();
            this.mediaType = mediaType == null ? "" : mediaType.trim().toLowerCase(Locale.ROOT);
            this.season = season;
            this.episode = episode;
            this.durationMs = durationMs;
        }

        public boolean hasLookupKey() {
            return tmdbId > 0 || (!isEmpty(imdbId) && season > 0 && episode > 0);
        }

        public boolean isTv() {
            return "tv".equals(mediaType) || season > 0 || episode > 0;
        }

        public String cacheKey() {
            long durationBucket = durationMs <= 0 ? 0 : TimeUnit.MILLISECONDS.toSeconds(durationMs);
            return tmdbId + "|" + imdbId + "|" + mediaType + "|" + season + "|" + episode + "|" + durationBucket;
        }
    }

    public static final class IntroSkipPlan {

        private static final IntroSkipPlan EMPTY = new IntroSkipPlan(new ArrayList<>(), new ArrayList<>());
        private final List<Segment> openings;
        private final List<Segment> endings;

        private IntroSkipPlan(List<Segment> openings, List<Segment> endings) {
            this.openings = openings;
            this.endings = endings;
        }

        public static IntroSkipPlan empty() {
            return EMPTY;
        }

        private static IntroSkipPlan from(List<Segment> segments) {
            List<Segment> openings = new ArrayList<>();
            List<Segment> endings = new ArrayList<>();
            for (Segment segment : segments) {
                if (segment.isOpening()) addDeduped(openings, segment);
                else addDeduped(endings, segment);
            }
            sort(openings);
            sort(endings);
            return openings.isEmpty() && endings.isEmpty() ? EMPTY : new IntroSkipPlan(openings, endings);
        }

        public static IntroSkipPlan merge(List<IntroSkipPlan> plans) {
            List<Segment> segments = new ArrayList<>();
            if (plans != null) {
                for (IntroSkipPlan plan : plans) {
                    if (plan == null) continue;
                    segments.addAll(plan.openings);
                    segments.addAll(plan.endings);
                }
            }
            return from(segments);
        }

        public boolean isEmpty() {
            return openings.isEmpty() && endings.isEmpty();
        }

        public List<Segment> getOpenings() {
            return new ArrayList<>(openings);
        }

        public List<Segment> getEndings() {
            return new ArrayList<>(endings);
        }

        private static void addDeduped(List<Segment> segments, Segment segment) {
            for (int i = 0; i < segments.size(); i++) {
                Segment existing = segments.get(i);
                if (!existing.overlaps(segment)) continue;
                if (segment.score() > existing.score()) segments.set(i, segment);
                return;
            }
            segments.add(segment);
        }

        private static void sort(List<Segment> segments) {
            segments.sort(Comparator.comparingLong(Segment::getStartMs));
        }
    }

    public static final class Segment {

        public enum Kind {
            INTRO,
            RECAP,
            OUTRO
        }

        private final Kind kind;
        private final String provider;
        private final long startMs;
        private final long endMs;
        private final double confidence;
        private final int submissionCount;

        private Segment(Kind kind, String provider, long startMs, long endMs, double confidence, int submissionCount) {
            this.kind = kind;
            this.provider = provider;
            this.startMs = startMs;
            this.endMs = endMs;
            this.confidence = confidence;
            this.submissionCount = submissionCount;
        }

        private static Segment create(Kind kind, String provider, Long startMs, Long endMs, long durationMs, double confidence, int submissionCount) {
            if (kind == null) return null;
            long start = startMs == null && kind != Kind.OUTRO ? 0 : startMs == null ? -1 : startMs;
            long end = endMs == null ? -1 : endMs;
            if (start < 0) return null;
            if (durationMs > 0) {
                if (start >= durationMs) return null;
                if (end < 0 && kind == Kind.OUTRO) end = durationMs;
                if (end > durationMs) end = durationMs;
            }
            if (end >= 0 && end <= start) return null;
            if (end < 0 && kind != Kind.OUTRO) return null;
            return new Segment(kind, provider, start, end, Math.max(0, confidence), Math.max(0, submissionCount));
        }

        public Kind getKind() {
            return kind;
        }

        public String getProvider() {
            return provider;
        }

        public long getStartMs() {
            return startMs;
        }

        public long getEndMs() {
            return endMs;
        }

        public boolean isOpening() {
            return kind == Kind.INTRO || kind == Kind.RECAP;
        }

        public boolean isEnding() {
            return kind == Kind.OUTRO;
        }

        private double score() {
            return confidence * 100.0 + submissionCount * 2.0 + (PROVIDER_INTRO_DB.equals(provider) ? 1.0 : 0.0);
        }

        private boolean overlaps(Segment other) {
            if (other == null) return false;
            if (Math.abs(startMs - other.startMs) <= 3000 && (endMs < 0 || other.endMs < 0 || Math.abs(endMs - other.endMs) <= 5000)) return true;
            if (endMs < 0 || other.endMs < 0) return false;
            long overlap = Math.min(endMs, other.endMs) - Math.max(startMs, other.startMs);
            if (overlap <= 0) return false;
            long shorter = Math.min(endMs - startMs, other.endMs - other.startMs);
            return shorter > 0 && overlap >= shorter * 0.6;
        }
    }
}
