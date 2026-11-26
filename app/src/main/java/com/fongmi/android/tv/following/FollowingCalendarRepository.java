package com.fongmi.android.tv.following;

import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.service.TmdbService;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.TmdbLanguagePolicy;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static com.fongmi.android.tv.following.FollowingCalendarPolicy.*;

public final class FollowingCalendarRepository implements AutoCloseable {
    public interface Callback { void onSnapshot(CalendarSnapshot snapshot); }
    public interface SeasonReader {
        TmdbService.CalendarSeasonResult read(TmdbItem item, int season, TmdbConfig config, boolean refresh) throws Exception;
    }

    public static final class CalendarSnapshot {
        public final Map<String, SeasonSchedule> schedules;
        public final int totalSeasons, completedSeasons, failedSeasons;
        public final boolean loading;
        public final long retryAtMillis, generation;
        private final List<Following> items;
        private CalendarSnapshot(Batch batch) {
            items = batch.items; schedules = Collections.unmodifiableMap(new LinkedHashMap<>(batch.schedules));
            totalSeasons = batch.total; completedSeasons = batch.completed; failedSeasons = batch.failed;
            loading = batch.running > 0 || !batch.queue.isEmpty(); retryAtMillis = batch.retryAt; generation = batch.generation;
        }
        public CalendarProjection project(LocalDate today) { return FollowingCalendarPolicy.project(items, schedules, today); }
    }

    private final SeasonReader reader;
    private final Supplier<TmdbConfig> configs;
    private final ExecutorService executor;
    private final Clock clock;
    private long generation, retryAtMillis;
    private boolean closed;
    private String scope = "";
    private Batch current;

    public FollowingCalendarRepository() {
        this(new TmdbService()::seasonForCalendar, () -> TmdbConfig.objectFrom(Setting.getTmdbConfig()),
                Executors.newFixedThreadPool(2), Clock.systemUTC());
    }

    public FollowingCalendarRepository(SeasonReader reader, Supplier<TmdbConfig> configs, ExecutorService executor, Clock clock) {
        this.reader = reader; this.configs = configs; this.executor = executor; this.clock = clock;
    }

    /** Callbacks run on the caller/worker thread; the owner posts immutable snapshots to its UI thread. */
    public synchronized void load(List<Following> snapshot, boolean refresh, Callback callback) {
        if (closed) return;
        TmdbConfig config = configs.get().sanitize();
        String nextScope = digest(config.getApiBase() + "|" + config.isApiAuto() + "|" + config.getApiKey() + "|" + config.getAccessToken()
                + "|" + TmdbLanguagePolicy.requestLanguage(config, Setting.getLanguage()) + "|" + config.getCredentialScopeEpoch());
        List<Following> items = new ArrayList<>();
        StringBuilder signature = new StringBuilder(nextScope);
        for (Following item : snapshot) {
            if (item == null || item.deletedAt > 0) continue;
            Following copy = item.copy(); items.add(copy);
            signature.append('|').append(copy.identityKey).append(':').append(copy.tmdbId).append(':').append(copy.mediaType)
                    .append(':').append(copy.trackedSeason).append(':').append(copy.watchedSeason).append(':').append(copy.watchedEpisode)
                    .append(':').append(copy.vodName).append(':').append(copy.vodPic).append(':').append(copy.seasonReleasedEpisodes).append(':').append(copy.seasonTotalEpisodes);
        }
        String key = digest(signature.toString());
        if (current != null && current.key.equals(key) && (!refresh || current.running > 0 || !current.queue.isEmpty())) {
            current.callback = callback; emit(current); return;
        }
        cancel();
        if (!scope.equals(nextScope)) { scope = nextScope; retryAtMillis = 0; }
        Batch batch = new Batch(generation, key, Collections.unmodifiableList(items), config, refresh, callback);
        current = batch;
        Map<String, Task> tasks = new LinkedHashMap<>();
        for (Following item : items) {
            if (localReason(item) != null) continue;
            if (!config.isReady()) {
                batch.schedules.put(item.identityKey, unavailable(item.trackedSeason, LocalReason.NO_CONFIG, "", 0)); continue;
            }
            String requestKey = item.tmdbId + ":" + item.trackedSeason;
            Task task = tasks.get(requestKey);
            if (task == null) { task = new Task(item); tasks.put(requestKey, task); }
            task.identities.add(item.identityKey);
            batch.schedules.put(item.identityKey, unavailable(item.trackedSeason, LocalReason.LOADING, "", 0));
        }
        batch.queue.addAll(tasks.values()); batch.total = tasks.size();
        if (retryAtMillis > clock.millis()) stopQueue(batch, retryAtMillis);
        pump(batch); emit(batch);
    }

    public synchronized void cancel() {
        generation++;
        if (current == null) return;
        current.callback = null; current.queue.clear();
        for (Future<?> future : current.futures) future.cancel(true);
        current = null;
    }

    @Override public synchronized void close() {
        if (closed) return;
        cancel(); closed = true; executor.shutdownNow();
    }

    private void pump(Batch batch) {
        while (current == batch && batch.running < 2 && !batch.queue.isEmpty() && batch.retryAt <= clock.millis()) {
            Task task = batch.queue.remove(); batch.running++;
            batch.futures.add(executor.submit(() -> fetch(batch, task)));
        }
    }

    private void fetch(Batch batch, Task task) {
        SeasonSchedule schedule;
        try {
            TmdbService.CalendarSeasonResult response = reader.read(FollowingMetadataClient.tmdbItem(task.item), task.item.trackedSeason, batch.config, batch.refresh);
            schedule = parse(response, task.item.trackedSeason);
        } catch (Exception error) {
            long retry = error instanceof TmdbService.RateLimitException ? ((TmdbService.RateLimitException) error).retryAtMillis : 0;
            schedule = unavailable(task.item.trackedSeason, retry > 0 ? LocalReason.RATE_LIMITED : LocalReason.FAILED,
                    safeError(error.getMessage(), batch.config), retry);
        }
        synchronized (this) {
            if (closed || current != batch || batch.generation != generation) return;
            batch.running--; batch.completed++;
            if (schedule.unavailableReason != null || !schedule.sanitizedError.isEmpty()) batch.failed++;
            for (String identity : task.identities) batch.schedules.put(identity, schedule);
            if (schedule.retryAtMillis > clock.millis()) {
                retryAtMillis = Math.max(retryAtMillis, schedule.retryAtMillis); stopQueue(batch, retryAtMillis);
            }
            pump(batch); emit(batch);
        }
    }

    private void stopQueue(Batch batch, long retry) {
        batch.retryAt = Math.max(batch.retryAt, retry);
        while (!batch.queue.isEmpty()) {
            Task task = batch.queue.remove();
            for (String identity : task.identities) batch.schedules.put(identity, unavailable(task.item.trackedSeason, LocalReason.RATE_LIMITED, "HTTP 429", retry));
        }
    }

    private void emit(Batch batch) {
        if (current == batch && batch.callback != null && batch.generation == generation) batch.callback.onSnapshot(new CalendarSnapshot(batch));
    }

    private static SeasonSchedule parse(TmdbService.CalendarSeasonResult result, int requestedSeason) {
        JsonObject body = result.body;
        if (body == null || !body.has("episodes") || !body.get("episodes").isJsonArray())
            return unavailable(requestedSeason, LocalReason.INVALID_DATA, result.sanitizedError, result.retryAtMillis);
        List<Episode> episodes = new ArrayList<>();
        JsonArray array = body.getAsJsonArray("episodes");
        for (JsonElement value : array) {
            if (!value.isJsonObject()) { episodes.add(new Episode(requestedSeason, 0, "", null)); continue; }
            JsonObject episode = value.getAsJsonObject();
            episodes.add(new Episode(integer(episode, "season_number"), integer(episode, "episode_number"), string(episode,"name"), string(episode,"air_date")));
        }
        return new SeasonSchedule(integer(body,"season_number"), episodes, Source.valueOf(result.origin.name()),
                result.cachedAtMillis, result.sanitizedError, result.retryAtMillis, null);
    }

    private static int integer(JsonObject object, String key) {
        try { return object.get(key).getAsBigDecimal().intValueExact(); } catch (RuntimeException e) { return -1; }
    }
    private static String string(JsonObject object, String key) {
        try { return object.get(key).getAsString(); } catch (RuntimeException e) { return null; }
    }
    private static SeasonSchedule unavailable(int season, LocalReason reason, String error, long retry) {
        return new SeasonSchedule(season, Collections.emptyList(), Source.NONE, 0, error, retry, reason);
    }
    private static String safeError(String error, TmdbConfig config) {
        String safe = TmdbService.redactMessage(error);
        for (String secret : new String[]{config.getApiKey(), config.getAccessToken()}) if (secret != null && !secret.isEmpty()) safe = safe.replace(secret, "<redacted>");
        return safe;
    }
    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255)); return out.toString();
        } catch (Exception e) { throw new IllegalStateException("Calendar scope unavailable"); }
    }

    private static final class Task {
        final Following item;
        final List<String> identities = new ArrayList<>();
        Task(Following item) { this.item = item; }
    }
    private static final class Batch {
        final long generation;
        final String key;
        final List<Following> items;
        final TmdbConfig config;
        final boolean refresh;
        final Map<String, SeasonSchedule> schedules = new LinkedHashMap<>();
        final Queue<Task> queue = new ArrayDeque<>();
        final List<Future<?>> futures = new ArrayList<>();
        Callback callback;
        int total, running, completed, failed;
        long retryAt;
        Batch(long generation, String key, List<Following> items, TmdbConfig config, boolean refresh, Callback callback) {
            this.generation = generation; this.key = key; this.items = items; this.config = config; this.refresh = refresh; this.callback = callback;
        }
    }
}
