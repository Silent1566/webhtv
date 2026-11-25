package com.fongmi.android.tv.server.process;

import com.fongmi.android.tv.api.config.VodSiteCatalog;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class CatalogInvalidateTest {
    private Map<String, Object> cache;
    private Map<String, Object> previous;
    @Before @SuppressWarnings("unchecked")
    public void snapshotActualCache() throws Exception {
        Field field = VodSiteCatalog.class.getDeclaredField("CACHE");
        field.setAccessible(true);
        cache = (Map<String, Object>) field.get(null);
        previous = new LinkedHashMap<>(cache);
        cache.clear();
    }
    @After public void restoreActualCache() {
        cache.clear();
        cache.putAll(previous);
    }
    @Test public void invalidateOnlyTheSelectedNamespace() {
        Object a = new Object(), b = new Object();
        cache.put("source-a", a);
        cache.put("source-b", b);
        VodSiteCatalog.invalidate(" source-a ");
        assertFalse(cache.containsKey("source-a"));
        assertSame(b, cache.get("source-b"));
        assertEquals(1, cache.size());
    }
    @Test public void blankAndUnknownKeysPreserveOtherCatalogs() {
        Object retained = new Object();
        cache.put("retained", retained);
        VodSiteCatalog.invalidate(null);
        VodSiteCatalog.invalidate("");
        VodSiteCatalog.invalidate("not-cached");
        assertSame(retained, cache.get("retained"));
        assertEquals(1, cache.size());
    }
    @Test public void legacyGlobalInvalidateStillClearsAll() {
        cache.put("source-a", new Object());
        cache.put("source-b", new Object());
        VodSiteCatalog.invalidate();
        assertTrue(cache.isEmpty());
    }
}
