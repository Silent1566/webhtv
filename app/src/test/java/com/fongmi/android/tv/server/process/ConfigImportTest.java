package com.fongmi.android.tv.server.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fongmi.android.tv.bean.Config;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ConfigImportTest {

    private static final String URL = "https://example.com/config.json";
    private static final String KEY = "9f1c8b0e-4a2d-4c6e-9d31-8a7b6c5d4e3f";

    @Test
    public void blankUrlIsRejected() {
        assertEquals("Missing url", rejection(params("url", "   ")));
        assertEquals("Missing url", rejection(params()));
    }

    @Test
    public void nonHttpSchemeIsRejected() {
        assertEquals("url must start with http:// or https://", rejection(params("url", "ftp://example.com/config.json")));
        assertEquals("url must start with http:// or https://", rejection(params("url", "file:///sdcard/config.json")));
        assertEquals("url must start with http:// or https://", rejection(params("url", "//example.com/config.json")));
    }

    @Test
    public void parsedUriRejectsHostlessUserinfoFragmentAndBadPort() {
        assertEquals("url contains invalid characters", rejection(params("url", "http://example.com/a b.json")));
        assertEquals("url contains invalid characters", rejection(params("url", "http://example.com/\tconfig.json")));
        assertEquals("url must contain a host", rejection(params("url", "http://")));
        assertEquals("url must contain a host", rejection(params("url", "http://:1234/config.json")));
        assertEquals("url must not contain credentials", rejection(params("url", "http://user:secret@example.com/config.json")));
        assertEquals("url must not contain a fragment", rejection(params("url", "http://example.com/config.json#token")));
        assertEquals("url port out of range", rejection(params("url", "http://example.com:70000/config.json")));
        assertEquals("url port out of range", rejection(params("url", "http://example.com:0/config.json")));
    }

    @Test
    public void validHttpUrlsKeepSchemeHostPortAndQuery() {
        assertEquals(0, ConfigImport.parse(params("url", "http://127.0.0.1:36227/config.json")).getType());
        assertEquals("https://example.com/config.json?token=1", ConfigImport.parse(params("url", " https://example.com/config.json?token=1 ")).getUrl());
        assertEquals("http://example.com:65535/config.json", ConfigImport.parse(params("url", "http://example.com:65535/config.json")).getUrl());
        assertEquals(1, ConfigImport.parse(params("url", "https://example.com/live.json", "type", "1")).getType());
    }

    @Test
    public void overlongUrlAndNameCapsHold() {
        String longUrl = "https://example.com/" + "a".repeat(ConfigImport.MAX_URL_LENGTH);
        assertEquals("url too long", rejection(params("url", longUrl)));
        String longName = "配".repeat(ConfigImport.MAX_NAME_LENGTH + 20);
        String capped = ConfigImport.parse(params("url", URL, "name", longName)).getName();
        assertEquals(ConfigImport.MAX_NAME_LENGTH, capped.length());
        assertFalse(capped.endsWith("\uFFFD"));
        assertEquals("混淆订阅测试", ConfigImport.parse(params("url", URL, "name", "  混淆订阅测试  ")).getName());
    }

    @Test
    public void invalidTypeIsRejected() {
        assertEquals("invalid type", rejection(params("url", URL, "type", "abc")));
        assertEquals("type must be 0, 1 or 2", rejection(params("url", URL, "type", "9")));
    }

    @Test
    public void existingConfigIsReusedAndOriginalNameIsPreserved() {
        FakeStore store = new FakeStore();
        Config existing = store.add(42, 0, URL, "原名称");
        ConfigImport.Result result = ConfigImport.importConfig(
                ConfigImport.parse(params("url", URL, "name", "新名称")), store);

        assertFalse(result.isCreated());
        assertSame(existing, result.getConfig());
        assertEquals(42, result.getConfig().getId());
        assertEquals("原名称", result.getConfig().getName());
        assertEquals(1, store.configs.size());
        assertEquals(0, store.created.get());
    }

    @Test
    public void concurrentImportsCreateOneRowWithOneCid() throws Exception {
        FakeStore store = new FakeStore();
        ConfigImport.Parsed parsed = ConfigImport.parse(params("url", URL, "name", "混淆订阅测试"));
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<ConfigImport.Result> results = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < threads; i++) {
            pool.execute(() -> {
                try {
                    start.await();
                    results.add(ConfigImport.importConfig(parsed, store));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertEquals("并发导入只能创建一行", 1, store.created.get());
        assertEquals(1, store.configs.size());
        assertEquals(threads, results.size());
        long first = results.get(0).getConfig().getId();
        for (ConfigImport.Result result : results) assertEquals(first, result.getConfig().getId());
        assertEquals(1, results.stream().filter(ConfigImport.Result::isCreated).count());
    }

    @Test
    public void dataContractCarriesInterfaceKey() {
        FakeStore store = new FakeStore();
        ConfigImport.Result result = ConfigImport.importConfig(
                ConfigImport.parse(params("url", URL, "name", "混淆订阅测试")), store);

        JsonObject data = ConfigImport.data(result, null);

        assertEquals(6, data.size());
        continue_contract(data, result);
    }

    private static void continue_contract(JsonObject data, ConfigImport.Result result) {
        assertEquals(result.getConfig().getId(), data.get("id").getAsInt());
        assertEquals(KEY, data.get("interfaceKey").getAsString());
        assertEquals(URL, data.get("url").getAsString());
        assertEquals("混淆订阅测试", data.get("name").getAsString());
        assertTrue(data.get("created").getAsBoolean());
        assertFalse(data.get("active").getAsBoolean());
    }

    @Test
    public void storeWithoutUsableIdentityFailsInsteadOfReturningZero() {
        for (String broken : new String[] {"zero-id", "no-key", "null-config"}) {
            FakeStore store = new FakeStore();
            ConfigImport.Result result = ConfigImport.importConfig(
                    ConfigImport.parse(params("url", URL)), store);
            if (broken.equals("zero-id")) result.getConfig().setId(0);
            if (broken.equals("no-key")) result.getConfig().setInterfaceKey("");
            if (broken.equals("null-config")) {
                try {
                    ConfigImport.data(null, null);
                    fail("null result must fail");
                } catch (IllegalStateException expected) {
                    continue;
                }
            }
            try {
                ConfigImport.data(result, null);
                fail(broken + " must fail");
            } catch (IllegalStateException expected) {
                assertNotNull(expected.getMessage());
            }
        }
    }

    @Test
    public void isActiveComparesCurrentConfigId() {
        Config imported = config(42, URL, "混淆订阅测试", KEY);
        assertTrue(ConfigImport.isActive(imported, config(42, URL, "混淆订阅测试", KEY)));
        assertFalse(ConfigImport.isActive(imported, config(43, "https://example.com/other.json", "", KEY)));
        assertFalse(ConfigImport.isActive(imported, config(0, URL, "", KEY)));
        assertFalse(ConfigImport.isActive(imported, null));
    }

    @Test
    public void writeGuardOnlyAllowsLocalPcPost() {
        assertEquals(405, rejectionCode(ConfigImport.writeGuard("GET", "127.0.0.1", headers(true), params())));
        assertEquals(403, rejectionCode(ConfigImport.writeGuard("POST", "192.168.1.20", headers(true), params())));
        assertEquals(403, rejectionCode(ConfigImport.writeGuard("POST", null, headers(true), params())));
        assertEquals(403, rejectionCode(ConfigImport.writeGuard("POST", "127.0.0.1", headers(false), params())));
        assertEquals(403, rejectionCode(ConfigImport.writeGuard("POST", "127.0.0.1", headers("0"), params())));
        Map<String, String> origin = headers(true);
        origin.put("origin", "https://evil.example");
        assertEquals(403, rejectionCode(ConfigImport.writeGuard("POST", "127.0.0.1", origin, params())));
        Map<String, String> referer = headers(true);
        referer.put("referer", "https://evil.example");
        assertEquals(403, rejectionCode(ConfigImport.writeGuard("POST", "127.0.0.1", referer, params())));
        Map<String, String> forwarded = headers(true);
        forwarded.put("x-forwarded-for", "10.0.0.1");
        assertEquals(403, rejectionCode(ConfigImport.writeGuard("POST", "127.0.0.1", forwarded, params())));
        assertEquals(403, rejectionCode(ConfigImport.writeGuard("POST", "127.0.0.1", headers(true), params("target", "http://10.0.0.1:9978"))));
        assertEquals(415, rejectionCode(ConfigImport.writeGuard("POST", "127.0.0.1", headers(true), params())));
    }

    @Test
    public void writeGuardAcceptsLoopbackVariantsWithPcMark() {
        for (String ip : new String[] {"127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "::ffff:127.0.0.1"}) {
            Map<String, String> head = headers(true);
            head.put("content-type", "application/x-www-form-urlencoded");
            assertEquals(ip, null, ConfigImport.writeGuard("POST", ip, head, params()));
        }
    }

    @Test
    public void bodyGuardRejectsUnknownOversizedAndChunkedBodies() {
        assertNull(ConfigImport.bodyGuard("GET", new LinkedHashMap<>()));
        assertEquals(411, rejectionCode(ConfigImport.bodyGuard("POST", headers(true))));
        Map<String, String> big = new LinkedHashMap<>();
        big.put("content-length", String.valueOf(ConfigImport.MAX_BODY_BYTES + 1));
        assertEquals(413, rejectionCode(ConfigImport.bodyGuard("POST", big)));
        Map<String, String> chunked = new LinkedHashMap<>();
        chunked.put("content-length", "10");
        chunked.put("transfer-encoding", "chunked");
        assertEquals(400, rejectionCode(ConfigImport.bodyGuard("POST", chunked)));
        Map<String, String> exact = new LinkedHashMap<>();
        exact.put("content-length", String.valueOf(ConfigImport.MAX_BODY_BYTES));
        assertNull(ConfigImport.bodyGuard("POST", exact));
    }

    @Test
    public void postFormBodyIsUsedOnlyWhenQueryMissesTheField() {
        Map<String, String> query = new HashMap<>();
        Map<String, String> fromBody = ConfigImport.withBody(query, "url=https%3A%2F%2Fexample.com%2Fconfig.json&name=%E6%B7%B7%E6%B7%86");
        assertEquals("https://example.com/config.json", fromBody.get("url"));
        assertEquals("混淆", fromBody.get("name"));
        Map<String, String> both = new HashMap<>();
        both.put("url", "https://example.com/query.json");
        assertEquals("https://example.com/query.json", ConfigImport.withBody(both, "url=https://example.com/body.json").get("url"));
        assertNull(ConfigImport.withBody(query, "not a form").get("url"));
        assertNull(ConfigImport.withBody(query, null).get("url"));
    }

    private static Map<String, String> headers(boolean pcMark) {
        Map<String, String> map = new LinkedHashMap<>();
        if (pcMark) map.put("x-webhtv-pc-import", "1");
        return map;
    }

    private static Map<String, String> headers(String mark) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("x-webhtv-pc-import", mark);
        return map;
    }

    private static int rejectionCode(ConfigImport.Rejection rejection) {
        assertNotNull("expected rejection", rejection);
        return rejection.getStatus();
    }

    private static String rejection(Map<String, String> params) {
        try {
            ConfigImport.parse(params);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        fail("expected rejection");
        return null;
    }

    private static Config config(int id, String url, String name, String key) {
        Config config = new Config();
        config.setId(id);
        config.setType(0);
        config.setUrl(url);
        config.setName(name);
        config.setInterfaceKey(key);
        return config;
    }

    private static Map<String, String> params(String... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return map;
    }

    private static final class FakeStore implements ConfigImport.Store {

        private final List<Config> configs = new ArrayList<>();
        private final AtomicInteger created = new AtomicInteger();
        private int sequence = 100;

        private Config add(int id, int type, String url, String name) {
            Config config = config(id, url, name, KEY);
            config.setType(type);
            configs.add(config);
            return config;
        }

        @Override
        public synchronized Config find(String url, int type) {
            for (Config config : configs) if (config.getType() == type && url.equals(config.getUrl())) return config;
            return null;
        }

        @Override
        public Config create(int type, String url, String name) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            created.incrementAndGet();
            Config config = config(0, url, name, KEY);
            config.setType(type);
            synchronized (this) {
                config.setId(++sequence);
                configs.add(config);
            }
            return config;
        }
    }
}
