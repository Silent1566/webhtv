package com.fongmi.android.tv.service;

import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbItem;
import org.junit.Rule;
import org.junit.Before;
import org.junit.After;
import com.fongmi.android.tv.App;
import com.google.gson.Gson;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import static org.junit.Assert.*;

public class TmdbServiceCalendarCacheTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private Object oldApp;
    @Before public void initializeGsonWithoutAndroidLifecycle() throws Exception {
        Class<?> type=Class.forName("sun.misc.Unsafe"); Field singleton=type.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        Object app=type.getMethod("allocateInstance",Class.class).invoke(singleton.get(null),App.class);
        Field gson=App.class.getDeclaredField("gson"); gson.setAccessible(true); gson.set(app,new Gson());
        Field instance=App.class.getDeclaredField("instance"); instance.setAccessible(true); oldApp=instance.get(null); instance.set(null,app);
    }
    @After public void restoreApp() throws Exception { Field instance=App.class.getDeclaredField("instance"); instance.setAccessible(true); instance.set(null,oldApp); }
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-26T00:00:00Z"),ZoneOffset.UTC);
    private final TmdbItem item = new TmdbItem(123,"tv","Title","","","","");
    private final String body = "{\"season_number\":0,\"episodes\":[{\"season_number\":0,\"episode_number\":1,\"air_date\":null}],\"credits\":{\"cast\":[]},\"images\":{}}";
    private TmdbConfig config(MockWebServer server) { return TmdbConfig.objectFrom("{\"apiBase\":\""+server.url("/3")+"\",\"apiAuto\":false,\"apiKey\":\"secret\",\"language\":\"zh-CN\",\"languageConfigured\":true}"); }
    private File seed(TmdbService service,TmdbConfig config,long age) throws Exception {
        byte[] digest=MessageDigest.getInstance("MD5").digest(service.seasonCacheKey(item,0,config).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex=new StringBuilder(); for(byte b:digest) hex.append(String.format("%02x",b&255));
        File dir=new File(temp.getRoot(),"tmdb"); assertTrue(dir.isDirectory() || dir.mkdirs());
        File file=new File(dir,"season_"+hex+".json"); Files.write(file.toPath(),body.getBytes(StandardCharsets.UTF_8)); assertTrue(file.setLastModified(clock.millis()-age)); return file;
    }
    @Test public void networkAndFreshCacheKeepFullBodyOriginalTimestampAndSpecialsUrl() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start(); server.enqueue(new MockResponse.Builder().body(body).build());
            TmdbService service=new TmdbService(temp.getRoot(),clock); TmdbConfig config=config(server);
            TmdbService.CalendarSeasonResult network=service.seasonForCalendar(item,0,config,false);
            assertEquals(TmdbService.CalendarOrigin.NETWORK,network.origin); assertEquals(clock.millis(),network.cachedAtMillis);
            String path=server.takeRequest().getTarget(); assertTrue(path.startsWith("/3/tv/123/season/0?")); assertTrue(path.contains("append_to_response="));
            TmdbService.CalendarSeasonResult cached=service.seasonForCalendar(item,0,config,false);
            assertEquals(TmdbService.CalendarOrigin.FRESH_CACHE,cached.origin); assertEquals(network.cachedAtMillis,cached.cachedAtMillis);
            assertTrue(cached.body.has("credits")); assertTrue(cached.body.has("images")); assertEquals(1,server.getRequestCount());
            assertEquals(cached.body,service.season(item,0,config)); assertEquals(1,server.getRequestCount());
        }
    }
    @Test public void threeDayFreshBoundaryAndRefreshBypass() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start(); TmdbService service=new TmdbService(temp.getRoot(),clock); TmdbConfig config=config(server);
            seed(service,config,TimeUnit.DAYS.toMillis(3)); assertEquals(TmdbService.CalendarOrigin.FRESH_CACHE,service.seasonForCalendar(item,0,config,false).origin);
            server.enqueue(new MockResponse.Builder().body(body).build()); assertEquals(TmdbService.CalendarOrigin.NETWORK,service.seasonForCalendar(item,0,config,true).origin); assertEquals(1,server.getRequestCount());
        }
    }
    @Test public void staleWithinThirtyDaysCarriesFailureAndOldSeasonStillUsesUnlimitedStale() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start(); TmdbService service=new TmdbService(temp.getRoot(),clock); TmdbConfig config=config(server);
            long age=TimeUnit.DAYS.toMillis(30); File file=seed(service,config,age); server.enqueue(new MockResponse.Builder().code(503).body("bad").build());
            TmdbService.CalendarSeasonResult stale=service.seasonForCalendar(item,0,config,false);
            assertEquals(TmdbService.CalendarOrigin.STALE_CACHE,stale.origin); assertEquals(clock.millis()-age,stale.cachedAtMillis); assertTrue(stale.sanitizedError.contains("503")); assertFalse(stale.sanitizedError.contains("secret"));
            assertEquals(clock.millis()-age,file.lastModified());
            assertTrue(file.setLastModified(clock.millis()-age-1)); server.enqueue(new MockResponse.Builder().code(503).build());
            assertThrows(Exception.class,()->service.seasonForCalendar(item,0,config,false));
            server.enqueue(new MockResponse.Builder().code(503).build()); assertTrue(service.season(item,0,config).has("episodes"));
        }
    }
    @Test public void noCacheFailureAndMalformedBodyCannotBecomeSuccess() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start(); TmdbService service=new TmdbService(temp.getRoot(),clock); TmdbConfig config=config(server);
            server.enqueue(new MockResponse.Builder().code(503).build()); assertThrows(Exception.class,()->service.seasonForCalendar(item,0,config,false));
            server.enqueue(new MockResponse.Builder().body("[]").build()); assertThrows(Exception.class,()->service.seasonForCalendar(item,0,config,false));
        }
    }
    @Test public void rateLimitKeepsCooldownWithStaleAndWithoutAndParsesHttpDate() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start(); TmdbService service=new TmdbService(temp.getRoot(),clock); TmdbConfig config=config(server);
            File file=seed(service,config,TimeUnit.DAYS.toMillis(4)); server.enqueue(new MockResponse.Builder().code(429).addHeader("Retry-After","120").build());
            TmdbService.CalendarSeasonResult stale=service.seasonForCalendar(item,0,config,true);
            assertEquals(TmdbService.CalendarOrigin.STALE_CACHE,stale.origin); assertEquals(clock.millis()+120000,stale.retryAtMillis); assertTrue(stale.sanitizedError.contains("429"));
            assertTrue(file.delete()); server.enqueue(new MockResponse.Builder().code(429).build());
            assertEquals(clock.millis()+60000,assertThrows(TmdbService.RateLimitException.class,()->service.seasonForCalendar(item,0,config,true)).retryAtMillis);
            server.enqueue(new MockResponse.Builder().code(429).addHeader("Retry-After","Thu, 26 Nov 2026 00:02:00 GMT").build());
            assertEquals(clock.millis()+120000,assertThrows(TmdbService.RateLimitException.class,()->service.seasonForCalendar(item,0,config,true)).retryAtMillis);
        }
    }
}
