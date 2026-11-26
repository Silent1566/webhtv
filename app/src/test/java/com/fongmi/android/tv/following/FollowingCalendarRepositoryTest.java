package com.fongmi.android.tv.following;

import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.service.TmdbService;
import com.google.gson.JsonParser;
import org.junit.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static com.fongmi.android.tv.following.FollowingCalendarPolicy.*;

public class FollowingCalendarRepositoryTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-26T00:00:00Z"), ZoneOffset.UTC);
    private static TmdbConfig config(String key) { return TmdbConfig.objectFrom("{\"apiKey\":\"" + key + "\",\"language\":\"zh-CN\",\"languageConfigured\":true}"); }
    private static TmdbService.CalendarSeasonResult result(String name, long retryAt) {
        return new TmdbService.CalendarSeasonResult(JsonParser.parseString("{\"season_number\":0,\"episodes\":[{\"season_number\":0,\"episode_number\":1,\"name\":\"" + name + "\",\"air_date\":null}]}").getAsJsonObject(),
                retryAt == 0 ? TmdbService.CalendarOrigin.NETWORK : TmdbService.CalendarOrigin.STALE_CACHE, 100, retryAt == 0 ? "" : "HTTP 429", retryAt);
    }
    private static Following item(String id, int tmdb) { Following f = FollowingCalendarPolicyTest.item(id, 0); f.tmdbId = tmdb; return f; }

    @Test public void mergesSeasonRequestsCapsQueueAndReusesInFlightForRepeatedLoad() {
        ManualExecutor executor = new ManualExecutor(); AtomicInteger requests = new AtomicInteger();
        List<FollowingCalendarRepository.CalendarSnapshot> events = new ArrayList<>();
        FollowingCalendarRepository repo = new FollowingCalendarRepository((i,s,c,r) -> { requests.incrementAndGet(); return result("ok",0); }, () -> config("key"), executor, clock);
        List<Following> items = Arrays.asList(item("a",1), item("alias",1), item("b",2), item("c",3));
        repo.load(items, false, events::add); repo.load(items, false, events::add); repo.load(items, true, events::add);
        assertEquals(2, executor.queue.size());
        executor.runNext(); assertEquals(2, executor.queue.size());
        executor.runAll(); assertEquals(3, requests.get());
        FollowingCalendarRepository.CalendarSnapshot last = events.get(events.size()-1);
        assertEquals(3,last.totalSeasons); assertEquals(3,last.completedSeasons); assertFalse(last.loading);
        assertEquals(4,last.project(LocalDate.of(2026,11,26)).undated.size());
        assertThrows(UnsupportedOperationException.class, () -> last.schedules.clear()); repo.close();
    }

    @Test public void partialFailureDoesNotLoseSuccessAndNeverWritesInput() {
        ManualExecutor executor = new ManualExecutor(); List<FollowingCalendarRepository.CalendarSnapshot> events = new ArrayList<>();
        Following a = item("a",1), b = item("b",2);
        FollowingCalendarRepository repo = new FollowingCalendarRepository((i,s,c,r) -> { if(i.getTmdbId()==2) throw new Exception("offline ?api_key=secret"); return result("ok",0); }, () -> config("key"), executor, clock);
        repo.load(Arrays.asList(a,b),true,events::add); executor.runNext();
        assertEquals(1,events.get(events.size()-1).project(LocalDate.of(2026,11,26)).undated.size());
        executor.runAll(); FollowingCalendarRepository.CalendarSnapshot last = events.get(events.size()-1);
        assertEquals(1,last.failedSeasons); assertEquals(1,last.project(LocalDate.of(2026,11,26)).undated.size());
        assertFalse(last.schedules.get("b").sanitizedError.contains("secret"));
        assertEquals(0,b.failureCount); assertEquals("",b.lastError); assertEquals(0,a.lastCheckedAt); repo.close();
    }

    @Test public void stale429AndException429BothStopUnstartedQueueAndEnforceCooldown() {
        for (boolean stale : new boolean[]{true,false}) {
            ManualExecutor executor = new ManualExecutor(); AtomicInteger requests = new AtomicInteger(); List<FollowingCalendarRepository.CalendarSnapshot> events = new ArrayList<>();
            long retry = clock.millis()+60000;
            FollowingCalendarRepository repo = new FollowingCalendarRepository((i,s,c,r) -> { requests.incrementAndGet(); if(stale) return result("cached",retry); throw new TmdbService.RateLimitException(retry); }, () -> config("key"), executor, clock);
            List<Following> items = Arrays.asList(item("a",1),item("b",2),item("c",3),item("d",4));
            repo.load(items,true,events::add); executor.runAll();
            assertEquals(2,requests.get()); FollowingCalendarRepository.CalendarSnapshot last = events.get(events.size()-1);
            assertEquals(retry,last.retryAtMillis); assertFalse(last.loading);
            assertEquals(stale ? 2 : 0,last.project(LocalDate.of(2026,11,26)).undated.size());
            repo.load(items,true,events::add); executor.runAll(); assertEquals(2,requests.get()); repo.close();
        }
    }

    @Test public void cancellationStopsQueueAndIgnoresInterruptResistantLateResult() throws Exception {
        CountDownLatch entered = new CountDownLatch(2), release = new CountDownLatch(1), returned = new CountDownLatch(2);
        AtomicInteger requests = new AtomicInteger(), callbacks = new AtomicInteger();
        FollowingCalendarRepository repo = new FollowingCalendarRepository((i,s,c,r) -> {
            requests.incrementAndGet(); entered.countDown(); boolean done = false;
            while (!done) try { release.await(); done = true; } catch (InterruptedException ignored) { }
            returned.countDown(); return result("late",0);
        }, () -> config("key"), Executors.newFixedThreadPool(2), clock);
        repo.load(Arrays.asList(item("a",1),item("b",2),item("c",3)),true,s -> callbacks.incrementAndGet());
        assertTrue(entered.await(5,TimeUnit.SECONDS)); repo.cancel(); int before = callbacks.get(); release.countDown();
        assertTrue(returned.await(5,TimeUnit.SECONDS)); repo.close(); assertEquals(2,requests.get()); assertEquals(before,callbacks.get());
    }

    @Test public void configAndTrackedSeasonChangesInvalidateOldGeneration() {
        ManualExecutor executor = new ManualExecutor(); TmdbConfig[] current = { config("one") }; AtomicInteger calls = new AtomicInteger(); List<FollowingCalendarRepository.CalendarSnapshot> events = new ArrayList<>();
        FollowingCalendarRepository repo = new FollowingCalendarRepository((i,s,c,r) -> { calls.incrementAndGet(); return result(c.getApiKey(),0); }, () -> current[0], executor, clock);
        repo.load(Collections.singletonList(item("a",1)),false,events::add);
        current[0] = config("two"); repo.load(Collections.singletonList(item("a",1)),false,events::add); executor.runAll();
        assertEquals(1,calls.get()); assertEquals("two",events.get(events.size()-1).project(LocalDate.of(2026,11,26)).undated.get(0).name);
        Following changed = item("a",1); changed.trackedSeason = 1; repo.load(Collections.singletonList(changed),false,events::add); executor.runAll(); assertEquals(2,calls.get()); repo.close();
    }

    @Test public void noIdentityOrCredentialsIsLocalOnlyAndInputSnapshotIsCopied() {
        ManualExecutor executor = new ManualExecutor(); List<FollowingCalendarRepository.CalendarSnapshot> events = new ArrayList<>();
        FollowingCalendarRepository repo = new FollowingCalendarRepository((i,s,c,r) -> { fail("must not request"); return null; }, () -> config(""), executor, clock);
        Following local = item("local",0), official = item("official",1);
        repo.load(Arrays.asList(local,official),false,events::add); local.vodName="changed";
        CalendarProjection projection = events.get(events.size()-1).project(LocalDate.of(2026,11,26));
        assertEquals(0,executor.queue.size()); assertEquals(2,projection.localRecords.size());
        assertEquals("Title",projection.localRecords.get(0).title); assertEquals(LocalReason.NO_CONFIG,projection.localRecords.get(1).reason); repo.close();
    }

    static class ManualExecutor extends AbstractExecutorService {
        final Queue<Runnable> queue = new ArrayDeque<>(); boolean closed;
        public void execute(Runnable r) { queue.add(r); }
        void runNext() { queue.remove().run(); }
        void runAll() { while(!queue.isEmpty()) runNext(); }
        public void shutdown() { closed=true; }
        public List<Runnable> shutdownNow() { closed=true; List<Runnable> rest=new ArrayList<>(queue); queue.clear(); return rest; }
        public boolean isShutdown() { return closed; }
        public boolean isTerminated() { return closed && queue.isEmpty(); }
        public boolean awaitTermination(long time,TimeUnit unit) { return isTerminated(); }
    }
}
