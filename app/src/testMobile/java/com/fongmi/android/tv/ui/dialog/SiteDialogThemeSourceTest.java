package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SiteDialogThemeSourceTest {
    @Test
    public void siteDialogAppliesDynamicColorProgrammatically() throws Exception {
        String dialog = read("app/src/mobile/java/com/fongmi/android/tv/ui/dialog/SiteDialog.java");
        assertFalse("site selection must not force the static light dialog theme", dialog.contains("ThemeOverlay_WebHTV_LightDialog"));
        assertTrue("site selection should use the theme-aware dialog base", dialog.contains("return builder().setView(getBinding().getRoot());"));
        assertTrue("site dialog must apply dynamic color to root view", dialog.contains("binding.getRoot().setBackgroundColor(color)"));
        assertTrue("site dialog must read dynamic color from settings", dialog.contains("Setting.getDynamicColor()"));
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
