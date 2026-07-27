package com.fongmi.android.tv.api.loader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.github.catvod.crawler.Spider;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class JarLoaderTest {

    @Test
    public void clearContinuesAfterSpiderDestroyFailure() throws Exception {
        JarLoader loader = new JarLoader();
        ConcurrentHashMap<String, Spider> spiders = map(loader, "spiders");
        ConcurrentHashMap<String, Object> locks = map(loader, "locks");
        AtomicInteger destroyed = new AtomicInteger();

        spiders.put("broken", new Spider() {
            @Override
            public void destroy() {
                destroyed.incrementAndGet();
                throw new NullPointerException("broken spider");
            }
        });
        spiders.put("healthy", new Spider() {
            @Override
            public void destroy() {
                destroyed.incrementAndGet();
            }
        });
        locks.put("lock", new Object());
        field("recent").set(loader, "recent");

        loader.clear();

        assertEquals(2, destroyed.get());
        assertTrue(spiders.isEmpty());
        assertTrue(locks.isEmpty());
        assertNull(field("recent").get(loader));
    }

    @SuppressWarnings("unchecked")
    private static <T> ConcurrentHashMap<String, T> map(JarLoader loader, String name) throws Exception {
        return (ConcurrentHashMap<String, T>) field(name).get(loader);
    }

    private static Field field(String name) throws Exception {
        Field field = JarLoader.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
