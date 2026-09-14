package com.fongmi.android.tv.theme;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/** Contract checks for the low-API stage-C binding path. */
public class ThemeControllerSourceTest {

    @Test
    public void controllerKeepsSemanticAndPlayerBindingsSeparate() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/theme/ThemeController.java");
        assertTrue(source.contains("applyPlayerControls(view, tokens)"));
        assertTrue(source.contains("DefaultTimeBar"));
        assertTrue(source.contains("setPlayedColor(tokens.primary())"));
        assertTrue(source.contains("playerIconColors(tokens)"));
        assertTrue(source.contains("isPlayerRoot"));
        assertTrue(source.contains("tokens.surface()"));
        assertTrue(source.contains("tokens.onSurfaceVariant()"));
    }

    @Test
    public void bothActivityTargetsBindAfterDynamicChildrenAreInflated() throws Exception {
        String mobile = read("app/src/mobile/java/com/fongmi/android/tv/ui/base/BaseActivity.java");
        String leanback = read("app/src/leanback/java/com/fongmi/android/tv/ui/base/BaseActivity.java");
        assertTrue(mobile.contains("ThemeController.applyNightMode(this)"));
        assertTrue(mobile.contains("ThemeController.dynamicColor(this)"));
        assertTrue(mobile.contains("initEvent();\n        // Some detail/player controls"));
        assertTrue(leanback.contains("ThemeController.applyNightMode(this)"));
        assertTrue(leanback.contains("initEvent();\n        // Some detail/player controls"));
    }

    @Test
    public void wallpaperScrimIsASeparateNonInteractiveLayer() throws Exception {
        String wall = read("app/src/main/res/layout/view_wall.xml");
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/custom/CustomWallView.java");
        assertTrue(wall.contains("@+id/themeScrim"));
        assertTrue(wall.contains("android:clickable=\"false\""));
        assertTrue(wall.contains("android:focusable=\"false\""));
        assertTrue(source.contains("applyThemeScrim()"));
        assertTrue(source.contains("ThemeController.wallpaperScrim"));
    }

    @Test
    public void playerControlRootsAreExplicitForMobileLayouts() throws Exception {
        for (String file : new String[]{
                "app/src/mobile/res/layout/view_control_vod.xml",
                "app/src/mobile/res/layout/view_control_live.xml"}) {
            assertTrue(read(file).contains("@+id/playerControlRoot"));
        }
    }

    private String read(String path) throws Exception {
        Path root = Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
        return new String(Files.readAllBytes(root.resolve(path)), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
