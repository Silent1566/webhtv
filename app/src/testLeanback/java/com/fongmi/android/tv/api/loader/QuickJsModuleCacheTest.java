package com.fongmi.android.tv.api.loader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.util.ReflectionHelpers.callInstanceMethod;
import static org.robolectric.util.ReflectionHelpers.getField;

import android.app.Application;

import com.fongmi.quickjs.crawler.Loader;
import com.fongmi.quickjs.crawler.Spider;
import com.fongmi.quickjs.utils.Module;
import com.github.catvod.utils.Asset;
import com.whl.quickjs.android.QuickJSLoader;
import com.whl.quickjs.wrapper.JSObject;
import com.whl.quickjs.wrapper.JSObjectCreator;
import com.whl.quickjs.wrapper.ModuleLoader;
import com.whl.quickjs.wrapper.QuickJSContext;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = 28, application = Application.class, shadows = {
        QuickJsModuleCacheTest.HostLoader.class, QuickJsModuleCacheTest.HostContext.class,
        QuickJsModuleCacheTest.Sources.class})
public class QuickJsModuleCacheTest {

    private static final String OLD = "assets/old-module.js";
    private static final String NEW = "assets/new-module.js";

    @Test
    public void oldCleanupDoesNotEvictNewEntryOrImportedSource() throws Exception {
        Module.get().clear();
        Sources.reads.clear();
        JsLoader previous = new JsLoader();
        JsLoader current = new JsLoader();
        Loader oldBridge = getField(previous, "loader");
        Loader newBridge = getField(current, "loader");
        assertEquals(OLD + "#1", loadEntry(oldBridge.spider(OLD, null)));

        var destroying = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var destroyed = new AtomicInteger();
        cache(previous, "old", new com.github.catvod.crawler.Spider() {
            @Override
            public void destroy() {
                destroying.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("cleanup was not released");
                    destroyed.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }
        });

        var worker = Executors.newSingleThreadExecutor();
        try {
            var cleanup = worker.submit(previous::clear);
            assertTrue("old cleanup did not reach Spider.destroy", destroying.await(10, TimeUnit.SECONDS));
            Spider first = newBridge.spider(NEW, null);
            cache(current, "new", first);
            assertEquals(NEW + "#1", loadEntry(first));
            assertEquals(NEW + "#1", loadImport(first, NEW));
            assertEquals(1, Sources.reads.get(NEW).get());

            release.countDown();
            cleanup.get(10, TimeUnit.SECONDS);
            assertEquals(1, destroyed.get());
            assertTrue(previous.activeKeys().isEmpty());
            assertEquals(Set.of("new"), current.activeKeys());

            Spider later = newBridge.spider(NEW, null);
            String entry = loadEntry(later);
            String imported = loadImport(later, NEW);
            assertEquals("old clear evicted the new loader's module", 1, Sources.reads.get(NEW).get());
            assertEquals(NEW + "#1", entry);
            assertEquals(entry, imported);
            assertEquals(OLD + "#2", loadEntry(oldBridge.spider(OLD, null)));
        } finally {
            release.countDown();
            worker.shutdownNow();
            Module.get().clear();
        }
    }

    private static void cache(JsLoader owner, String key, com.github.catvod.crawler.Spider spider) {
        ConcurrentHashMap<String, com.github.catvod.crawler.Spider> spiders = getField(owner, "spiders");
        spiders.put(key, spider);
    }

    private static String loadEntry(Spider spider) {
        callInstanceMethod(spider, "createCtx");
        callInstanceMethod(spider, "createObj");
        return context(spider).entry;
    }

    private static String loadImport(Spider spider, String name) {
        return new String(context(spider).loader.getModuleBytecode(name), StandardCharsets.UTF_8);
    }

    private static HostContext context(Spider spider) {
        QuickJSContext ctx = getField(spider, "ctx");
        return Shadow.extract(ctx);
    }

    @Implements(QuickJSLoader.class)
    public static class HostLoader {
        @Implementation
        protected static void init() {}
    }

    @Implements(QuickJSContext.class)
    public static class HostContext {
        private ModuleLoader loader;
        private String entry;

        @Implementation
        protected void __constructor__(JSObjectCreator creator) {}

        @Implementation
        protected static QuickJSContext create() {
            return Shadow.newInstance(QuickJSContext.class, new Class<?>[]{JSObjectCreator.class}, new Object[]{null});
        }

        @Implementation
        protected void setConsole(QuickJSContext.Console console) {}

        @Implementation
        protected Object evaluate(String source) { return null; }

        @Implementation
        protected JSObject getGlobalObject() { return ReflectionHelpers.createNullProxy(JSObject.class); }

        @Implementation
        protected Object getProperty(JSObject object, String name) { return null; }

        @Implementation
        protected void setModuleLoader(ModuleLoader loader) { this.loader = loader; }

        @Implementation
        protected byte[] compileModule(String source, String name) { return source.getBytes(StandardCharsets.UTF_8); }

        @Implementation
        protected Object evaluateModule(String source, String name) { entry = source; return null; }

        @Implementation
        protected Object evaluateModule(String source) { return null; }
    }

    @Implements(Asset.class)
    public static class Sources {
        static final ConcurrentHashMap<String, AtomicInteger> reads = new ConcurrentHashMap<>();

        @Implementation
        protected static String read(String name) {
            return name + "#" + reads.computeIfAbsent(name, key -> new AtomicInteger()).incrementAndGet();
        }
    }
}
