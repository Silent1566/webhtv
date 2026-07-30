package com.fongmi.android.tv.web;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WebHomeThemePolicyTest {

    @Test
    public void remoteTheme_allowsOnlyHomeDataCurrentSourcePlaybackAndControlledNavigation() {
        assertTrue(WebHomeThemePolicy.allowsMethod("vod.home"));
        assertTrue(WebHomeThemePolicy.allowsMethod("vod.category"));
        assertTrue(WebHomeThemePolicy.allowsMethod("player.playVod"));
        assertTrue(WebHomeThemePolicy.allowsMethod("app.search"));
        assertTrue(WebHomeThemePolicy.allowsMethod("app.openVod"));
        assertTrue(WebHomeThemePolicy.allowsMethod("app.openSetting"));
        assertTrue(WebHomeThemePolicy.allowsMethod("ui.getViewport"));
        assertTrue(WebHomeThemePolicy.allowsMethod("navigation.back"));
        assertTrue(WebHomeThemePolicy.allowsMethod("navigation.reload"));

        assertFalse(WebHomeThemePolicy.allowsMethod("net.request"));
        assertFalse(WebHomeThemePolicy.allowsMethod("net.resourceUrl"));
        assertFalse(WebHomeThemePolicy.allowsMethod("player.playUrl"));
        assertFalse(WebHomeThemePolicy.allowsMethod("player.playVodInline"));
        assertFalse(WebHomeThemePolicy.allowsMethod("cache.get"));
        assertFalse(WebHomeThemePolicy.allowsMethod("cache.set"));
        assertFalse(WebHomeThemePolicy.allowsMethod("cache.del"));
        assertFalse(WebHomeThemePolicy.allowsMethod("device.info"));
        assertFalse(WebHomeThemePolicy.allowsMethod("site.info"));
        assertFalse(WebHomeThemePolicy.allowsMethod("config.info"));
        assertFalse(WebHomeThemePolicy.allowsMethod("ext.info"));
        assertFalse(WebHomeThemePolicy.allowsMethod("pan.play"));
    }

    @Test
    public void remoteTheme_acceptsMessagesOnlyFromItsMainFrameAndExactOrigin() {
        String expected = "https://theme.example:443";

        assertTrue(WebHomeThemePolicy.allowsMessage(expected, "https://theme.example", true));
        assertFalse(WebHomeThemePolicy.allowsMessage(expected, "https://theme.example", false));
        assertFalse(WebHomeThemePolicy.allowsMessage(expected, "https://other.example", true));
        assertFalse(WebHomeThemePolicy.allowsMessage(expected, "http://theme.example", true));
        assertFalse(WebHomeThemePolicy.allowsMessage(expected, "https://theme.example/path", true));
        assertFalse(WebHomeThemePolicy.allowsMessage("data:text/html,theme", "data:text/html,theme", true));
        assertFalse(WebHomeThemePolicy.allowsMessage(expected, "not an origin", true));
    }
}
