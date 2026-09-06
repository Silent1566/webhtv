package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SiteDialogThemeSourceTest {
    @Test
    public void siteDialogThemeUsesDynamicColors() throws Exception {
        String styles = read("app/src/main/res/values/styles.xml");
        assertFalse("dialog theme must not hard-code white background", styles.contains("android:colorBackground\">@color/white"));
        assertFalse("dialog theme must not hard-code white colorSurface", styles.contains("colorSurface\">@color/white"));
        assertTrue("dialog theme must use dynamic colorSurfaceContainer", styles.contains("colorSurface\">?attr/colorSurfaceContainer"));
        assertTrue("dialog theme must use dynamic windowBackground", styles.contains("windowBackground\">?attr/colorSurfaceContainer"));
    }

    @Test
    public void everyActivityAppliesThemeChangesImmediately() throws Exception {
        for (String flavor : new String[]{"mobile", "leanback"}) {
            String base = read("app/src/" + flavor + "/java/com/fongmi/android/tv/ui/base/BaseActivity.java");
            assertTrue("theme changes must recreate activities in " + flavor,
                    base.contains("event.getType() == RefreshEvent.Type.LANGUAGE || event.getType() == RefreshEvent.Type.THEME"));
        }
    }

    private String read(String path) throws Exception {
        Path root = Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
        return new String(Files.readAllBytes(root.resolve(path)), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
