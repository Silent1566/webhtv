package com.fongmi.android.tv.api.loader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import com.github.catvod.Init;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = 28, application = Application.class, shadows = {
        QuickJsModuleCacheTest.HostLoader.class, PyLoaderLifecycleTest.PythonFactory.class})
public class PyLoaderLifecycleTest {

    private static final String NS = "bridge";

    @Before
    public void setUp() {
        Init.set(RuntimeEnvironment.getApplication());
        PythonFactory.spiders.clear();
        PythonFactory.constructions.set(0);
    }

    @Test
    public void clearDoesNotWaitForOldInitializationOrReturnItsLateSpider() throws Exception {
        BaseLoader base = ReflectionHelpers.callConstructor(BaseLoader.class);
        PyLoader previous = pyOwner(base);
        var initializing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var oldSpider = fixture("old.py");
        oldSpider.duringInit = () -> {
            initializing.countDown();
            await(release);
        };
        var newSpider = fixture("fresh.py");
        var worker = Executors.newSingleThreadExecutor();
        Future<?> cleanup = null;
        try {
            Future<Spider> late = worker.submit(() -> find(base, "same", "old.py"));
            await(initializing);
            cleanup = base.clear("pending-py-fixture");
            try {
                cleanup.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new AssertionError("Python clear waited for a still-initializing Spider", e);
            }
            assertNotSame(previous, pyOwner(base));
            assertEquals("switching generations must not construct another Python bridge", 1, PythonFactory.constructions.get());
            assertSame(newSpider, find(base, "same", "fresh.py"));
            assertEquals(0, oldSpider.destroyed.get());

            release.countDown();
            assertTrue(late.get(10, TimeUnit.SECONDS) instanceof SpiderNull);
            assertEquals(1, oldSpider.destroyed.get());
            assertTrue(previous.activeKeys().isEmpty());
            assertSame(newSpider, find(base, "same", "fresh.py"));
            assertEquals(1, newSpider.initialized.get());
            assertEquals(0, newSpider.destroyed.get());
        } finally {
            release.countDown();
            worker.shutdown();
            assertTrue(worker.awaitTermination(15, TimeUnit.SECONDS));
            if (cleanup != null) cleanup.get(15, TimeUnit.SECONDS);
        }
    }

    @Test
    public void retiredOwnerStopsServingBeforeCleanupWithoutRemovingCurrentKeysOrProxy() throws Exception {
        BaseLoader base = ReflectionHelpers.callConstructor(BaseLoader.class);
        PyLoader previous = pyOwner(base);
        var destroying = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var oldSpider = fixture("old-cached.py");
        oldSpider.duringDestroy = () -> {
            destroying.countDown();
            await(release);
        };
        var newSpider = fixture("fresh-after-clear.py");
        assertSame(oldSpider, find(base, "old", "old-cached.py"));
        base.setRecent(NS, "old", "old-cached.py", "");
        assertEquals("old-cached.py", base.proxy(Map.of("do", "py"))[0]);
        Future<?> cleanup = base.clear("cached-py-fixture");
        try {
            await(destroying);
            assertNotSame("old owner must be replaced before its queued destroy completes", previous, pyOwner(base));
            previous.setRecent(NS, "old");
            assertNull(previous.proxy(Map.of()));
            assertTrue(previous.getSpider(NS, "old", "old-cached.py", "{}") instanceof SpiderNull);
            assertEquals(Set.of("old"), base.activePluginKeys());

            assertSame(newSpider, find(base, "current", "fresh-after-clear.py"));
            base.setRecent(NS, "current", "fresh-after-clear.py", "");
            assertEquals("fresh-after-clear.py", base.proxy(Map.of("do", "py"))[0]);
            assertEquals(Set.of("old", "current"), base.activePluginKeys());
        } finally {
            release.countDown();
            cleanup.get(15, TimeUnit.SECONDS);
        }
        assertEquals(1, oldSpider.destroyed.get());
        assertEquals(0, newSpider.destroyed.get());
        assertEquals(Set.of("current"), base.activePluginKeys());
        assertEquals("fresh-after-clear.py", base.proxy(Map.of("do", "py"))[0]);
    }

    private static PyLoader pyOwner(BaseLoader base) {
        return ReflectionHelpers.getField(base, "pyLoader");
    }

    private static Spider find(BaseLoader base, String key, String api) {
        return base.getSpider(NS, key, api, "{}", "");
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue("barrier did not open", latch.await(15, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static FakeSpider fixture(String api) {
        var spider = new FakeSpider(api);
        PythonFactory.spiders.put(api, spider);
        return spider;
    }

    private static final class FakeSpider extends com.fongmi.chaquo.Spider {
        private final String api;
        final AtomicInteger initialized = new AtomicInteger();
        final AtomicInteger destroyed = new AtomicInteger();
        Runnable duringInit = () -> {};
        Runnable duringDestroy = () -> {};

        FakeSpider(String api) {
            super(null, null, api);
            this.api = api;
        }

        @Override
        public void init(Context context, String extend) {
            initialized.incrementAndGet();
            duringInit.run();
        }

        @Override
        public void destroy() {
            duringDestroy.run();
            destroyed.incrementAndGet();
        }

        @Override
        public Object[] proxy(Map<String, String> params) {
            return new Object[]{api};
        }
    }

    @Implements(com.fongmi.chaquo.Loader.class)
    public static class PythonFactory {
        static final Map<String, FakeSpider> spiders = new ConcurrentHashMap<>();
        static final AtomicInteger constructions = new AtomicInteger();

        @Implementation
        protected void __constructor__() {
            constructions.incrementAndGet();
        }

        @Implementation
        protected com.fongmi.chaquo.Spider spider(String api) {
            return spiders.get(api);
        }
    }
}
