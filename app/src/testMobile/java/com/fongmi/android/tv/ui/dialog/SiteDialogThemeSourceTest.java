package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SiteDialogThemeSourceTest {
    @Test
    public void siteDialogUsesThemeResolvedSurfaceWithoutOverwritingIt() throws Exception {
        String dialog = read("app/src/mobile/java/com/fongmi/android/tv/ui/dialog/SiteDialog.java");
        String layout = read("app/src/mobile/res/layout/dialog_site.xml");
        assertFalse("site selection must not force the static light dialog theme", dialog.contains("ThemeOverlay_WebHTV_LightDialog"));
        assertTrue("site selection should use the theme-aware dialog base", dialog.contains("return builder().setView(getBinding().getRoot());"));
        assertFalse("site dialog must not replace the themed surface with the seed color", dialog.contains("binding.getRoot().setBackgroundColor("));
        assertTrue("site dialog root should resolve the activity surface color", layout.contains("android:background=\"?attr/colorSurfaceContainer\""));
        assertFalse("site dialog root must not hard-code the legacy white drawable", layout.contains("@drawable/shape_shell_proxy_dialog"));
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
