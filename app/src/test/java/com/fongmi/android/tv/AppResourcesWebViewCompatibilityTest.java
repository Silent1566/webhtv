package com.fongmi.android.tv;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AppResourcesWebViewCompatibilityTest {

    @Test
    public void applicationResourcesKeepTheAssetManagerUsedByWebView() throws Exception {
        String source = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "App.java")));

        assertTrue("Application resources should start from the framework-owned Resources instance",
                source.contains("Resources resources = super.getResources();"));
        assertTrue("Application language changes should update that Resources instance in place",
                source.contains("resources.updateConfiguration("));
        assertTrue("Application language should still derive its locale from the existing language wrapper",
                source.contains("Setting.wrapLanguage(getBaseContext()).getResources().getConfiguration()"));
        assertFalse("Application must not replace Resources with a configuration context",
                source.contains("resources = Setting.wrapLanguage(getBaseContext()).getResources();"));
    }

    @Test
    public void applicationResourcesUpdateIsSerializedAndFailSafe() throws Exception {
        String source = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "App.java")));

        assertTrue("Application resources lookups are reachable from background threads, so the language update must run under a lock",
                source.contains("synchronized (resourcesLock) {"));
        assertTrue("A failed language update must not propagate out of getResources into every resource consumer",
                source.contains("} catch (Throwable e) {"));
        assertTrue("Application must still return the framework-owned Resources when the language update fails",
                source.contains("return resources;"));
        int invalidate = source.indexOf("public void invalidateResources() {");
        assertTrue("Resource cache invalidation must remain the single place that drops the cache", invalidate > 0);
        assertTrue("Resource cache invalidation must take the same lock as the update path",
                source.indexOf("synchronized (resourcesLock) {", invalidate) > invalidate);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }
}
