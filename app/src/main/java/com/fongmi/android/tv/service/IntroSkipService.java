package com.fongmi.android.tv.service;

import android.os.SystemClock;

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
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class IntroSkipService {

    private static final String PROVIDER_INTRO_DB = "IntroDB";
    private static final String PROVIDER_THE_INTRO_DB = "TheIntroDB";
    private static final String PROVIDER_TODB = "TODB";
    private static final String INTRO_DB_SEGMENTS = "https://api.introdb.app/segments";
    /** v2/v3 已被上游冻结并计划移除（theintrodb.org/openapi.yaml，2026-11），只留 v4。 */
    private static final String THE_INTRO_DB_MEDIA = "https://api.theintrodb.org/v4/media";
    private static final String TODB_METADATA = "https://playback.theotherdb.org/api/metadata";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    /** 一次要几个播放版本：实测 3 个够覆盖常见平台，再多只是重复段。 */
    private static final int TODB_PAGE_SIZE = 3;
    private static final long TIMEOUT_MS = TimeUnit.SECONDS.toMillis(5);
    private static final int MAX_CACHE = 128;
    /** 参考时长与本集时长的差值超过该值才做时间轴折算，避免为几百毫秒的抖动挪动片尾。 */
    private static final long TIME_BASE_TOLERANCE_MS = 2000;
    /** 片尾结束点落在参考结尾的这个范围内，视为「一直放到文件结束」。 */
    private static final long OPEN_END_TOLERANCE_MS = 5000;
    /** 参考时长与本集时长的最大容许差。超过即认为不是同一版本，放弃折算。 */
    private static final long MAX_TIME_BASE_DRIFT_MS = TimeUnit.MINUTES.toMillis(5);
    /** 起点差在此范围内视为两家在说同一段。 */
    private static final long SAME_START_TOLERANCE_MS = 3000;
    /** 外部时间轴超过一周即视为异常输入，不进入播放器。 */
    private static final long MAX_MEDIA_TIME_MS = TimeUnit.DAYS.toMillis(7);
    private static final int MAX_SUBMISSION_COUNT = 1_000_000;
    /**
     * 下集预告的字段名。只有 TheIntroDB 下发预告：IntroDB 的响应键集恒为
     * recap/intro/outro/post_credits，它那个 post_credits 是彩蛋而不是预告。
     * 按常见拼法都试一遍，命中不了只是没有预告段，不影响其余几类。
     */
    private static final String[] PREVIEW_KEYS = {"preview", "next_episode", "next_preview", "trailer"};
    /**
     * 缓存未折算的原始段，键只含剧集身份、不含时长。
     *
     * <p>时长不是查询条件（IntroDB 压根不收，TheIntroDB 可选），只是解读结果的参数，所以不该
     * 进键：进了键就意味着同一集在 onPrepare（时长还是 0）和 STATE_READY（时长已知）会各发
     * 一轮请求，HLS 时长抖动 1 秒也会穿透缓存，更没法预载还没开播、时长未知的邻集。
     */
    private static final Map<String, CacheEntry> CACHE = Collections.synchronizedMap(new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > MAX_CACHE;
        }
    });
    /** 固定数量的锁条带，避免按外部剧集 key 建无限增长的锁表。 */
    private static final Object[] CACHE_LOCKS = createCacheLocks();

    private static Object[] createCacheLocks() {
        Object[] locks = new Object[32];
        for (int i = 0; i < locks.length; i++) locks[i] = new Object();
        return locks;
    }

    public IntroSkipPlan load(@NonNull Query query) {
        return loadResult(query).getPlan();
    }

    /** 只把结果灌进缓存，不折算——用于预热邻集，此时时长还无从得知。 */
    public void preload(@NonNull Query query) {
        loadResult(query);
    }

    /** 返回计划以及本次是否完整，供播放层决定是否允许抑制后续重试。 */
    public LoadResult loadResult(@NonNull Query query) {
        if (!query.hasLookupKey()) return new LoadResult(IntroSkipPlan.empty(), true);
        String key = query.cacheKey();
        Object lock = CACHE_LOCKS[key.hashCode() & (CACHE_LOCKS.length - 1)];
        synchronized (lock) {
            CacheEntry cached = CACHE.get(key);
            if (cached != null && canUseCachedResponse(cached.durationMs, query.durationMs)) {
                IntroSkipPlan plan = IntroSkipPlan.from(cached.segments, query.durationMs);
                SpiderDebug.log("intro-skip", "cache hit key=%s segments=%d cachedDurationMs=%d durationMs=%d", key, cached.segments.size(), cached.durationMs, query.durationMs);
                return new LoadResult(plan, true);
            }
            if (cached != null) SpiderDebug.log("intro-skip", "cache refresh key=%s cachedDurationMs=%d durationMs=%d", key, cached.durationMs, query.durationMs);
            SpiderDebug.log("intro-skip", "query start tmdbId=%d imdbId=%s mediaType=%s season=%d episode=%d durationMs=%d", query.tmdbId, query.imdbId, query.mediaType, query.season, query.episode, query.durationMs);
            RemoteResult result = loadRemote(query);
            // 部分 provider 失败时不写入缓存，下一次请求仍能重试失败的一方。
            if (result.cacheable && (cached == null || shouldReplaceCachedResponse(cached.durationMs, query.durationMs))) {
                CACHE.put(key, new CacheEntry(result.segments, query.durationMs));
            }
            IntroSkipPlan plan = IntroSkipPlan.from(result.segments, query.durationMs);
            SpiderDebug.log("intro-skip", "query done key=%s segments=%d cacheable=%s durationMs=%d", key, result.segments.size(), result.cacheable, query.durationMs);
            return new LoadResult(plan, result.cacheable);
        }
    }

    public static final class LoadResult {

        private final IntroSkipPlan plan;
        private final boolean cacheable;

        private LoadResult(IntroSkipPlan plan, boolean cacheable) {
            this.plan = plan == null ? IntroSkipPlan.empty() : plan;
            this.cacheable = cacheable;
        }

        public IntroSkipPlan getPlan() {
            return plan;
        }

        public boolean isCacheable() {
            return cacheable;
        }
    }

    /** 一次远端查询的结果；cacheable=false 表示至少一家 provider 没有可靠完成。 */
    private static final class RemoteResult {

        private final List<RawSegment> segments;
        private final boolean cacheable;

        private RemoteResult(List<RawSegment> segments, boolean cacheable) {
            this.segments = segments == null ? Collections.emptyList() : segments;
            this.cacheable = cacheable;
        }
    }

    private static final class CacheEntry {

        private final List<RawSegment> segments;
        private final long durationMs;

        private CacheEntry(List<RawSegment> segments, long durationMs) {
            this.segments = segments == null ? Collections.emptyList() : segments;
            this.durationMs = Math.max(0, durationMs);
        }
    }

    private RemoteResult loadRemote(Query query) {
        List<Future<List<RawSegment>>> futures = new ArrayList<>();
        ExecutorCompletionService<List<RawSegment>> completion = new ExecutorCompletionService<>(Task.largeExecutor());
        if (!isEmpty(query.imdbId) && query.season > 0 && query.episode > 0) {
            futures.add(completion.submit(() -> fetchIntroDb(query)));
        }
        if (query.tmdbId > 0) {
            futures.add(completion.submit(() -> fetchTheIntroDb(query)));
            futures.add(completion.submit(() -> fetchTodb(query)));
        }
        // 没有可用的查询条件不是失败，是确定的「查不了」，可以缓存以免反复进来
        if (futures.isEmpty()) return new RemoteResult(Collections.emptyList(), true);

        long deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS;
        List<RawSegment> segments = new ArrayList<>();
        int completed = 0;
        boolean failed = false;
        for (int i = 0; i < futures.size(); i++) {
            long waitMs = deadline - SystemClock.elapsedRealtime();
            if (waitMs <= 0) {
                failed = true;
                break;
            }
            try {
                Future<List<RawSegment>> future = completion.poll(waitMs, TimeUnit.MILLISECONDS);
                if (future == null) {
                    failed = true; // 超时，剩下的也别等了
                    break;
                }
                List<RawSegment> part = future.get();
                completed++;
                if (part == null) {
                    failed = true; // 该家失败
                    continue;
                }
                segments.addAll(part);
            } catch (Throwable e) {
                failed = true;
                SpiderDebug.log("intro-skip", "provider failed error=%s", e.getMessage());
            }
        }
        for (Future<List<RawSegment>> future : futures) if (!future.isDone()) future.cancel(true);
        return new RemoteResult(segments, isCacheableResponse(futures.size(), completed, failed));
    }

    private List<RawSegment> fetchIntroDb(Query query) {
        HttpUrl url = HttpUrl.parse(INTRO_DB_SEGMENTS).newBuilder()
                .addQueryParameter("imdb_id", query.imdbId)
                .addQueryParameter("season", String.valueOf(query.season))
                .addQueryParameter("episode", String.valueOf(query.episode))
                .build();
        return fetchGet(url, PROVIDER_INTRO_DB);
    }

    /**
     * duration_ms 有就带、没有就不带：带上它服务端能挑更贴近本地片源的那一版标定，
     * 预载时（还没开播、时长未知）省掉即可，不因此放弃预载。
     */
    private List<RawSegment> fetchTheIntroDb(Query query) {
        HttpUrl.Builder builder = HttpUrl.parse(THE_INTRO_DB_MEDIA).newBuilder()
                .addQueryParameter("tmdb_id", String.valueOf(query.tmdbId));
        if (query.isTv() && query.season > 0 && query.episode > 0) {
            builder.addQueryParameter("season", String.valueOf(query.season));
            builder.addQueryParameter("episode", String.valueOf(query.episode));
        }
        if (query.durationMs > 0) builder.addQueryParameter("duration_ms", String.valueOf(query.durationMs));
        return fetchGet(builder.build(), PROVIDER_THE_INTRO_DB);
    }

    /**
     * TODB PlayBack：只认 TMDB 键，POST 一段 JSON。
     *
     * <p>一次返回多个播放版本（items），每个版本自带 marker 数组与 version_runtime（**秒**）。
     * 同一集在不同平台上标定不同，每个版本都产出，去重交给 {@link IntroSkipPlan#from}；
     * 参考时长按版本各带一份，它是唯一能「按平台版本标定」的源。
     */
    private List<RawSegment> fetchTodb(Query query) {
        JsonObject payload = new JsonObject();
        payload.addProperty("platform_type", query.isTv() ? "tmdb_id_tv" : "tmdb_id_movie");
        payload.addProperty("platform_value", String.valueOf(query.tmdbId));
        JsonArray includes = new JsonArray();
        includes.add("marker");
        payload.add("includes", includes);
        payload.addProperty("page", 1);
        payload.addProperty("page_size", TODB_PAGE_SIZE);
        // 电影没有季集号；TV 缺季集号时不猜（猜错等于把别的集标定缓存成这集的）。
        if (query.season > 0 && query.episode > 0) {
            payload.addProperty("season_number", query.season);
            payload.addProperty("episode_number", query.episode);
        }
        Request request = new Request.Builder()
                .url(TODB_METADATA)
                .post(RequestBody.create(payload.toString(), JSON))
                .build();
        return execute(request, PROVIDER_TODB);
    }

    private List<RawSegment> fetchGet(HttpUrl url, String provider) {
        return execute(new Request.Builder().url(url).get().build(), provider);
    }

    /** @return null 表示这家没答上来（超时/报错/非 2xx），调用方据此决定要不要缓存。 */
    private List<RawSegment> execute(Request request, String provider) {
        long start = SystemClock.elapsedRealtime();
        String url = request.url().toString();
        SpiderDebug.log("intro-skip", "%s request url=%s", provider, url);
        try (Response response = (PROVIDER_TODB.equals(provider) ? TodbHttpClient.get(TIMEOUT_MS) : OkHttp.client(TIMEOUT_MS)).newCall(request).execute()) {
            if (PROVIDER_TODB.equals(provider)) {
                SpiderDebug.log("intro-skip", "%s transport tls=%s protocol=%s", provider, response.handshake() == null ? "none" : response.handshake().tlsVersion(), response.protocol());
            }
            if (response.body() == null || !response.isSuccessful()) {
                SpiderDebug.log("intro-skip", "%s http=%d empty=%s url=%s", provider, response.code(), response.body() == null, url);
                // 404 是明确的「这一集没有数据」，可以缓存；其余状态码都可能是暂时的
                return response.code() == 404 ? Collections.emptyList() : null;
            }
            String body = response.body().string();
            if (parseObject(body) == null) {
                SpiderDebug.log("intro-skip", "%s invalid json url=%s", provider, url);
                return null;
            }
            List<RawSegment> raw = parseSegments(body, provider);
            SpiderDebug.log("intro-skip", "%s loaded segments=%d cost=%dms url=%s", provider, raw.size(), SystemClock.elapsedRealtime() - start, url);
            return raw;
        } catch (Throwable e) {
            SpiderDebug.log("intro-skip", "%s failed error=%s cost=%dms url=%s", provider, e.getMessage(), SystemClock.elapsedRealtime() - start, url);
            return null;
        }
    }

    private static List<RawSegment> parseSegments(String body, String provider) {
        switch (provider) {
            case PROVIDER_INTRO_DB: return parseIntroDbRaw(body);
            case PROVIDER_THE_INTRO_DB: return parseTheIntroDbRaw(body);
            case PROVIDER_TODB: return parseTodbRaw(body);
            default: return Collections.emptyList();
        }
    }

    /** 仅供测试/诊断：清空原始段缓存，保证一次查询真的走到网络。 */
    static void clearCacheForTest() {
        CACHE.clear();
    }

    public static IntroSkipPlan parseIntroDb(String body, long durationMs) {
        return IntroSkipPlan.from(parseIntroDbRaw(body), durationMs);
    }

    public static IntroSkipPlan parseTheIntroDb(String body, long durationMs) {
        return IntroSkipPlan.from(parseTheIntroDbRaw(body), durationMs);
    }

    public static IntroSkipPlan parseTodb(String body, long durationMs) {
        return IntroSkipPlan.from(parseTodbRaw(body), durationMs);
    }

    /**
     * IntroDB 的四个顶层字段是对象而不是数组：recap / intro / outro / post_credits。
     *
     * <p>{@code post_credits} 是片尾之后的彩蛋/附加内容，与「下集预告」是两种东西
     * （观众常常正相反：预告跳过、彩蛋留着），所以单独一类。这家实测响应键集里没有预告字段。
     */
    static List<RawSegment> parseIntroDbRaw(String body) {
        JsonObject object = parseObject(body);
        if (object == null) return Collections.emptyList();
        long reference = referenceDuration(object);
        List<RawSegment> segments = new ArrayList<>();
        addIntroDbSegment(segments, object, "recap", Segment.Kind.RECAP, reference);
        addIntroDbSegment(segments, object, "intro", Segment.Kind.INTRO, reference);
        addIntroDbSegment(segments, object, "outro", Segment.Kind.OUTRO, reference);
        addIntroDbSegment(segments, object, "post_credits", Segment.Kind.POST_CREDITS, reference);
        return segments;
    }

    /** TheIntroDB 各类型都是数组，且很多时间点是 null；null 原样交给解析层解释。 */
    static List<RawSegment> parseTheIntroDbRaw(String body) {
        JsonObject object = parseObject(body);
        if (object == null) return Collections.emptyList();
        long reference = referenceDuration(object);
        List<RawSegment> segments = new ArrayList<>();
        addTheIntroDbSegments(segments, object, "recap", Segment.Kind.RECAP, reference);
        addTheIntroDbSegments(segments, object, "intro", Segment.Kind.INTRO, reference);
        addTheIntroDbSegments(segments, object, "credits", Segment.Kind.OUTRO, reference);
        // 该家文档只列 intros/recaps/credits/previews，目前没观察到彩蛋键；
        // 认它只是为了将来出现时不被静默丢掉。
        addTheIntroDbSegments(segments, object, "post_credits", Segment.Kind.POST_CREDITS, reference);
        for (String key : PREVIEW_KEYS) addTheIntroDbSegments(segments, object, key, Segment.Kind.PREVIEW, reference);
        return segments;
    }

    /**
     * TODB PlayBack 的 markers。单位陷阱：time_start / time_end / version_runtime 都是**秒**。
     *
     * <p>{@code chapter} 是剧情章节标记而不是可跳过段，认进来会让播放器把正片当片尾跳掉，
     * 所以只认 {@link #kindFromWire} 认识的那几个。
     */
    static List<RawSegment> parseTodbRaw(String body) {
        JsonObject root = parseObject(body);
        if (root == null) return Collections.emptyList();
        List<RawSegment> segments = new ArrayList<>();
        JsonArray items = array(root, "items");
        for (int index = 0; index < items.size(); index++) {
            JsonElement element = items.get(index);
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            String version = text(item, "version_id");
            Long reference = secondsToMillis(doubleValue(item, "version_runtime"));
            long referenceMs = reference == null ? 0 : reference;
            JsonArray markers = array(item, "marker");
            for (int mark = 0; mark < markers.size(); mark++) {
                JsonElement rawMarker = markers.get(mark);
                if (!rawMarker.isJsonObject()) continue;
                JsonObject marker = rawMarker.getAsJsonObject();
                Segment.Kind kind = kindFromWire(text(marker, "marker_type"));
                if (kind == null) continue;
                Long startMs = secondsToMillis(doubleValue(marker, "time_start"));
                Long endMs = secondsToMillis(doubleValue(marker, "time_end"));
                if (startMs == null && endMs == null) continue;
                segments.add(new RawSegment(kind, PROVIDER_TODB, "todb|" + version + "|" + text(marker, "marker_id"),
                        startMs, endMs, referenceMs, number(marker, "confidence", 0.5), integer(marker, "submission_count", 0)));
            }
        }
        return segments;
    }

    /** 外部 marker_type / 字段名 → 片段类型；不认识的一律 null（含 chapter）。 */
    static Segment.Kind kindFromWire(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        switch (value) {
            case "recap":
            case "previously":
            case "previously_on":
                return Segment.Kind.RECAP;
            case "intro":
            case "opening":
            case "op":
                return Segment.Kind.INTRO;
            case "outro":
            case "credits":
            case "ending":
            case "ed":
                return Segment.Kind.OUTRO;
            case "preview":
            case "next_episode":
            case "next":
            case "trailer":
                return Segment.Kind.PREVIEW;
            case "post_credits":
            case "post_credit":
            case "postcredits":
            case "extras":
                return Segment.Kind.POST_CREDITS;
            case "advertisement":
            case "ad":
            case "ads":
                return Segment.Kind.ADVERTISEMENT;
            case "intermission":
            case "interval":
                return Segment.Kind.INTERMISSION;
            default:
                return null;
        }
    }

    /** id 类字段可能是数字也可能是字符串，统一成稳定文本。 */
    private static String text(JsonObject object, String key) {
        try {
            if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) return "";
            JsonElement element = object.get(key);
            return element.isJsonPrimitive() ? element.getAsString().trim() : "";
        } catch (Throwable e) {
            return "";
        }
    }

    /**
     * 数据源标定所用版本的总时长，用于把尾部段折算到本地片源的时间轴上。
     *
     * <p>字段名两家不统一、也可能整个缺失（返回 0 表示无从折算）。刻意不认裸 {@code duration}
     * 之类无单位后缀的键：TMDB 系接口用它表示「分钟」，当成秒会算出天量偏移，把所有尾部段
     * 静默抹掉，比拿不到基准更糟。解析时还不知道本集时长，量级校验推迟到折算时做，
     * 见 {@link Segment#plausibleReference}。
     */
    private static long referenceDuration(JsonObject root) {
        Long ms = longValue(root, "duration_ms");
        if (ms == null) ms = longValue(root, "runtime_ms");
        if (ms != null && ms > 0) return ms;
        Double sec = doubleValue(root, "duration_sec");
        if (sec == null) sec = doubleValue(root, "runtime_sec");
        Long millis = secondsToMillis(sec);
        return millis == null ? 0 : millis;
    }

    private static JsonObject parseObject(String body) {
        try {
            JsonElement element = JsonParser.parseString(body);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Throwable e) {
            return null;
        }
    }

    private static void addIntroDbSegment(List<RawSegment> segments, JsonObject root, String key, Segment.Kind kind, long referenceDurationMs) {
        JsonObject object = object(root, key);
        if (object == null) return;
        Long start = millis(object, "start_ms", "start_sec");
        Long end = millis(object, "end_ms", "end_sec");
        segments.add(new RawSegment(kind, PROVIDER_INTRO_DB, key, start, end, referenceDurationMs, number(object, "confidence", 0.5), integer(object, "submission_count", 0)));
    }

    private static void addTheIntroDbSegments(List<RawSegment> segments, JsonObject root, String key, Segment.Kind kind, long referenceDurationMs) {
        JsonArray array = array(root, key);
        for (int index = 0; index < array.size(); index++) {
            JsonElement element = array.get(index);
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            Long start = millis(object, "start_ms", null);
            Long end = millis(object, "end_ms", null);
            segments.add(new RawSegment(kind, PROVIDER_THE_INTRO_DB, key + "#" + index, start, end, referenceDurationMs, 0.5, 0));
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
        return secondsToMillis(sec);
    }

    private static Long longValue(JsonObject object, String key) {
        Double value = doubleValue(object, key);
        if (value == null || value < 0 || value > MAX_MEDIA_TIME_MS) return null;
        return Math.round(value);
    }

    private static Long secondsToMillis(Double seconds) {
        if (seconds == null || seconds < 0 || seconds > MAX_MEDIA_TIME_MS / 1000.0) return null;
        double millis = seconds * 1000.0;
        return Double.isFinite(millis) && millis <= MAX_MEDIA_TIME_MS ? Math.round(millis) : null;
    }

    private static Double doubleValue(JsonObject object, String key) {
        try {
            if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) return null;
            double value = object.get(key).getAsDouble();
            return Double.isFinite(value) ? value : null;
        } catch (Throwable e) {
            return null;
        }
    }

    private static double number(JsonObject object, String key, double fallback) {
        Double value = doubleValue(object, key);
        return clampConfidence(value == null ? fallback : value);
    }

    static double clampConfidence(double value) {
        if (!Double.isFinite(value)) return 0;
        return Math.max(0, Math.min(1, value));
    }

    /** 夹取而非 intValue()：外部数据超出 int 范围时截断会绕回小值甚至 0，静默改变去重排序。 */
    private static int integer(JsonObject object, String key, int fallback) {
        Long value = longValue(object, key);
        if (value == null) return fallback;
        return (int) Math.min(MAX_SUBMISSION_COUNT, value);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** 只有所有已发起的 provider 都可靠返回，结果才允许进入无 TTL 的内存缓存。 */
    static boolean isCacheableResponse(int providerCount, int completedCount, boolean failed) {
        return providerCount > 0 && providerCount == completedCount && !failed;
    }

    /** 未知时长的缓存可用于预载/查询，但不能阻止正式请求获取时长感知结果。 */
    static boolean canUseCachedResponse(long cachedDurationMs, long requestedDurationMs) {
        return requestedDurationMs <= 0 || cachedDurationMs > 0;
    }

    /** 只允许已知时长结果替换未知时长结果，避免预载响应覆盖正式响应。 */
    static boolean shouldReplaceCachedResponse(long cachedDurationMs, long incomingDurationMs) {
        return cachedDurationMs <= 0 && incomingDurationMs > 0;
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
            this.durationMs = Math.min(MAX_MEDIA_TIME_MS, Math.max(0, durationMs));
        }

        public boolean hasLookupKey() {
            return tmdbId > 0 || (!isEmpty(imdbId) && season > 0 && episode > 0);
        }

        public boolean isTv() {
            return "tv".equals(mediaType) || season > 0 || episode > 0;
        }

        /**
         * 只含剧集身份，不含时长。
         *
         * <p>时长不是查询条件（IntroDB 不收，TheIntroDB 可选），把它写进键会让同一集在
         * onPrepare（时长还是 0）和 STATE_READY（时长已知）落到两个桶各发一轮请求，也让预载
         * 永远命中不了——预载时那一集还没开播，时长无从得知。代价是首次请求若发生在预载阶段
         * 就没带上 duration_ms，拿不到服务端按本地片源挑的那一版标定；折算在客户端本来就要做
         * 一遍，这个损失可以接受，换来的是每集一次请求加预载真正生效。
         */
        public String cacheKey() {
            return tmdbId + "|" + imdbId + "|" + mediaType + "|" + season + "|" + episode;
        }

        public long getDurationMs() {
            return durationMs;
        }
    }

    /**
     * 解析产物，尚未按本集时长折算。缓存存的是这个，折算推迟到读取时按当次时长做，
     * 同一集换了播放源（时长不同）能复用同一份缓存。
     */
    static final class RawSegment {

        private final Segment.Kind kind;
        private final String provider;
        private final String identity;
        private final Long startMs;
        private final Long endMs;
        private final long referenceDurationMs;
        private final double confidence;
        private final int submissionCount;

        RawSegment(Segment.Kind kind, String provider, String identity, Long startMs, Long endMs, long referenceDurationMs, double confidence, int submissionCount) {
            this.kind = kind;
            this.provider = provider;
            this.identity = identity;
            this.startMs = startMs;
            this.endMs = endMs;
            this.referenceDurationMs = referenceDurationMs;
            this.confidence = confidence;
            this.submissionCount = submissionCount;
        }

        Segment resolve(long durationMs) {
            return Segment.create(kind, provider, identity, startMs, endMs, durationMs, referenceDurationMs, confidence, submissionCount);
        }
    }

    public static final class IntroSkipPlan {

        private static final IntroSkipPlan EMPTY = new IntroSkipPlan(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        private final List<Segment> openings;
        private final List<Segment> middles;
        private final List<Segment> endings;

        private IntroSkipPlan(List<Segment> openings, List<Segment> middles, List<Segment> endings) {
            this.openings = openings;
            this.middles = middles;
            this.endings = endings;
        }

        public static IntroSkipPlan empty() {
            return EMPTY;
        }

        static IntroSkipPlan from(List<RawSegment> raw, long durationMs) {
            List<Segment> segments = new ArrayList<>();
            if (raw != null) {
                for (RawSegment item : raw) {
                    Segment segment = item == null ? null : item.resolve(durationMs);
                    if (segment != null) segments.add(segment);
                }
            }
            return fromResolved(segments);
        }

        private static IntroSkipPlan fromResolved(List<Segment> segments) {
            List<Segment> openings = new ArrayList<>();
            List<Segment> middles = new ArrayList<>();
            List<Segment> endings = new ArrayList<>();
            for (Segment segment : segments) {
                if (segment.isOpening()) addDeduped(openings, segment);
                else if (segment.isMidRoll()) addDeduped(middles, segment);
                else addDeduped(endings, segment);
            }
            sort(openings);
            sort(middles);
            sort(endings);
            return openings.isEmpty() && middles.isEmpty() && endings.isEmpty()
                    ? EMPTY
                    : new IntroSkipPlan(openings, middles, endings);
        }

        /** 按时间先后给出全部段落（开头段、片中段、尾部段混排），供逐段判定使用。 */
        public List<Segment> getAll() {
            List<Segment> all = new ArrayList<>(openings);
            all.addAll(middles);
            all.addAll(endings);
            sort(all);
            return all;
        }

        public boolean isEmpty() {
            return openings.isEmpty() && middles.isEmpty() && endings.isEmpty();
        }

        /** 片中段（片内广告/中场休息）：既不是开头也不是尾部。 */
        public List<Segment> getMiddles() {
            return new ArrayList<>(middles);
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
                if (segment.better(existing)) segments.set(i, segment);
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
            OUTRO,
            PREVIEW,
            /** 片尾之后的彩蛋/附加内容（IntroDB 的 post_credits、TODB 的 post_credits）。 */
            POST_CREDITS,
            /** 片内广告位（TODB 的 advertisement）：位置任意，走绝对时间轴。 */
            ADVERTISEMENT,
            /** 中场休息/幕间（TODB 的 intermission）：走绝对时间轴。 */
            INTERMISSION;

            /** 正片之前：目标落点是本段结束。 */
            public boolean isOpening() {
                return this == INTRO || this == RECAP;
            }

            /** 片中段：与开头段/尾部段物理位置不同，去重必须分组。 */
            public boolean isMidRoll() {
                return this == ADVERTISEMENT || this == INTERMISSION;
            }

            /** 正片之后：时间轴按「距文件结尾的距离」理解。 */
            public boolean isEnding() {
                return this == OUTRO || this == PREVIEW || this == POST_CREDITS;
            }

            /** 去重分组：0 开头 / 1 片中 / 2 尾部，跨组永不合并。 */
            int dedupeGroup() {
                if (isEnding()) return 2;
                if (isMidRoll()) return 1;
                return 0;
            }
        }

        private final Kind kind;
        private final String provider;
        private final String identity;
        private final long startMs;
        private final long endMs;
        private final boolean openEnded;
        private final double confidence;
        private final int submissionCount;

        private Segment(Kind kind, String provider, String identity, long startMs, long endMs, boolean openEnded, double confidence, int submissionCount) {
            this.kind = kind;
            this.provider = provider;
            this.identity = isEmpty(identity) ? kind + "|" + provider + "|" + startMs + "|" + endMs : identity;
            this.startMs = startMs;
            this.endMs = endMs;
            this.openEnded = openEnded;
            this.confidence = confidence;
            this.submissionCount = submissionCount;
        }

        private static Segment create(Kind kind, String provider, String identity, Long startMs, Long endMs, long durationMs, long referenceDurationMs, double confidence, int submissionCount) {
            if (kind == null) return null;
            boolean trailing = kind.isEnding();
            long start = startMs == null && !trailing ? 0 : startMs == null ? -1 : startMs;
            long end = endMs == null ? -1 : endMs;
            if (start < 0) return null;
            if (endMs != null && end < 0) return null;
            if (trailing) return createTrailing(kind, provider, identity, start, end, durationMs, referenceDurationMs, confidence, submissionCount);
            if (durationMs > 0) {
                if (start >= durationMs) return null;
                if (end > durationMs) end = durationMs;
            }
            if (end <= start) return null;
            return new Segment(kind, provider, identity, start, end, false, Math.max(0, confidence), Math.max(0, submissionCount));
        }

        /**
         * 尾部段（片尾、下集预告）按「距文件结尾的偏移」理解，而不是照抄绝对时间戳。
         *
         * <p>片源普遍存在掐头、加站点前贴、删减，与数据源标定的参考版本时长对不上；此时绝对
         * 时间戳整体漂移，但「距结尾还有多久」基本稳定，按该距离折算才能对上本地时间轴。
         * 早先直接用参考时间戳，只要本地片源比参考版本短一点就命中 start >= durationMs，
         * 整段被丢弃——这正是片头能跳、片尾不跳的主因。参考时长缺失时无从折算，
         * 只能沿用原始时间戳。
         */
        private static Segment createTrailing(Kind kind, String provider, String identity, long start, long end, long durationMs, long referenceDurationMs, double confidence, int submissionCount) {
            long reference = plausibleReference(referenceDurationMs, durationMs);
            if (reference > 0 && end > reference && end - reference > OPEN_END_TOLERANCE_MS) return null;
            // openEnded 只能拿参考时间轴上的结尾去比。没有参考时长时（IntroDB 从不给）无从判断，
            // 一律按「有界」处理：错判成 openEnded 会让 seek 变成切集，把片尾之后的正片一起扔掉。
            boolean missingEnd = end < 0;
            boolean openEnded = reference > 0
                    && (missingEnd || reference >= end && reference - end <= OPEN_END_TOLERANCE_MS);
            if (reference > 0 && durationMs > 0 && !withinDistance(reference, durationMs, TIME_BASE_TOLERANCE_MS)) {
                long shift = durationMs - reference;
                start = safeAdd(start, shift);
                if (end >= 0) end = safeAdd(end, shift);
                if (start == Long.MIN_VALUE || end == Long.MIN_VALUE) return null;
            }
            if (durationMs > 0) {
                if (missingEnd || openEnded || end > durationMs) end = durationMs;
                if (start >= durationMs) return null;
            }
            if (start <= 0) return null; // 尾部段不可能从 0 开始，整集当片尾必是错配
            if (end >= 0 && end <= start) return null;
            return new Segment(kind, provider, identity, start, end, openEnded, Math.max(0, confidence), Math.max(0, submissionCount));
        }

        /**
         * 参考时长与本集时长必须足够接近，否则视为对不上同一版本，放弃折算。
         *
         * <p>折算是整段平移，基准差多少片尾就挪多少。掐头去尾、贴片、删减这类真实差异通常在
         * 几分钟量级；差到分钟数十倍（合并版、跨版本错配）时平移量本身就比片尾还长，算出来的
         * 落点落在正片里，跳过去等于把正片当片尾扔掉。宁可不折算保留原始时间戳。
         * 用减法而非乘法比较，避免 reference 接近 Long.MAX_VALUE 时溢出成负数骗过检查。
         */
        private static long plausibleReference(long reference, long durationMs) {
            if (reference <= 0) return 0;
            if (durationMs <= 0) return reference;
            boolean plausible = withinDistance(reference, durationMs, MAX_TIME_BASE_DRIFT_MS);
            if (!plausible) SpiderDebug.log("intro-skip", "reject reference=%d durationMs=%d", reference, durationMs);
            return plausible ? reference : 0;
        }

        static boolean withinDistance(long left, long right, long tolerance) {
            if (tolerance < 0) return false;
            long distance = left >= right ? left - right : right - left;
            return distance >= 0 && distance <= tolerance;
        }

        private static long safeAdd(long value, long delta) {
            if (delta > 0 && value > Long.MAX_VALUE - delta) return Long.MIN_VALUE;
            if (delta < 0 && value < Long.MIN_VALUE - delta) return Long.MIN_VALUE;
            return value + delta;
        }

        public Kind getKind() {
            return kind;
        }

        public String getProvider() {
            return provider;
        }

        /** 原始数据边界生成的稳定身份，不随本地片源时长折算而变化。 */
        public String getIdentity() {
            return identity;
        }

        public long getStartMs() {
            return startMs;
        }

        public long getEndMs() {
            return endMs;
        }

        /** 片尾是否一直延伸到文件结束——为真表示没有可 seek 的落点，只能按「本集看完」处理。 */
        public boolean isOpenEnded() {
            return openEnded;
        }

        public boolean isOpening() {
            return kind.isOpening();
        }

        /** 片中段（片内广告/中场休息）：有界、走绝对时间轴，「跳过」永远不会等于「本集看完」。 */
        public boolean isMidRoll() {
            return kind.isMidRoll();
        }

        public boolean isEnding() {
            return kind.isEnding();
        }

        private double score() {
            return confidence * 100.0 + submissionCount * 2.0 + (PROVIDER_INTRO_DB.equals(provider) ? 1.0 : 0.0);
        }

        /**
         * 两段被判为同一段时该保留哪一个。
         *
         * <p>有界优先于无界，先于分数比较：无界段没有 seek 落点，只能按「本集看完」处理，
         * 让它挤掉另一家给出的确切结束点，就把片尾之后的彩蛋、预告连同跳过能力一起丢了。
         */
        private boolean better(Segment other) {
            if (openEnded != other.openEnded) return !openEnded;
            return score() > other.score();
        }

        private boolean overlaps(Segment other) {
            if (other == null) return false;
            // 同组内允许跨类去重：两家对同一段的标注常不一致（一家算 recap 另一家算 intro），
            // 不合并会对同一段连跳两次。开头 / 片中 / 尾部三组之间不并——它们物理位置不同，
            // 一条 0–30s 的广告与一条 0–90s 的片头本来就可能是两回事。片尾与紧随其后的预告
            // 都在尾部组，靠起点差与重叠比例区分。
            if (kind.dedupeGroup() != other.kind.dedupeGroup()) return false;
            // 无界段的 end 已被拉到文件结尾，拿它算重叠会把紧随其后的段整个吞掉（片尾吞预告）。
            // 这种情况只按起点判断：起点隔得远就是两段不同的内容。
            if (openEnded || other.openEnded) return withinDistance(startMs, other.startMs, SAME_START_TOLERANCE_MS);
            if (withinDistance(startMs, other.startMs, SAME_START_TOLERANCE_MS)
                    && (endMs < 0 || other.endMs < 0 || withinDistance(endMs, other.endMs, 5000))) return true;
            if (endMs < 0 || other.endMs < 0) return false;
            long overlap = Math.min(endMs, other.endMs) - Math.max(startMs, other.startMs);
            if (overlap <= 0) return false;
            long shorter = Math.min(endMs - startMs, other.endMs - other.startMs);
            return shorter > 0 && overlap >= shorter * 0.6;
        }
    }
}
