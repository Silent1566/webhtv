package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class HomeSiteSwitchClearsTypesTest {

    @Test
    public void siteSwitchClearsOldTypeButtonsBeforeHomeReload() throws Exception {
        String home = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java");

        int clear = method(home, "private void clearCategoryContent()");
        int reload = method(home, "private void getVideo(boolean forceNative)");
        int setSite = method(home, "public void setSite(Site item)");
        int helper = method(home, "private void clearStaleSiteTypes()");

        assertTrue(clear >= 0);
        assertTrue(reload >= 0);
        assertTrue(setSite >= 0);
        assertTrue(helper > clear);
        assertTrue(helper < reload);
        assertTrue(home.substring(clear, helper).contains("clearStaleSiteTypes();"));
        String body = home.substring(helper, method(home, "private void updateToolbarVisibility(boolean visible)"));
        assertTrue(body.contains("mTypeAdapter.addAll(java.util.Collections.emptyList())"));
        assertTrue(body.contains("mPendingTypePosition = -1"));
        assertTrue(body.contains("mBinding.typeRecycler.setVisibility(View.GONE)"));
    }

    private static int method(String source, String signature) {
        return source.indexOf(signature);
    }

    private static String read(String relative) throws Exception {
        Path path = Path.of(relative);
        if (!Files.exists(path) && relative.startsWith("app/")) path = Path.of(relative.substring(4));
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
