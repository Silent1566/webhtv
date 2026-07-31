package com.fongmi.android.tv.web;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;

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

    @Test
    public void v2Detail_requiresBothHostAndManifestPermission() {
        HashSet<String> permissions = new HashSet<>(Arrays.asList(
                "vod.detail", "favorite.read", "player.playVod", "app.search", "app.openSetting"));

        assertTrue(WebHomeThemePolicy.allowsMethod(WebThemePage.DETAIL, permissions, "theme.info"));
        assertTrue(WebHomeThemePolicy.allowsMethod(WebThemePage.DETAIL, permissions, "vod.detail"));
        assertTrue(WebHomeThemePolicy.allowsMethod(WebThemePage.DETAIL, permissions, "favorite.status"));
        assertTrue(WebHomeThemePolicy.allowsMethod(WebThemePage.DETAIL, permissions, "player.playVod"));
        assertTrue(WebHomeThemePolicy.allowsMethod(WebThemePage.DETAIL, permissions, "navigation.openNativeDetail"));
        assertTrue(WebHomeThemePolicy.allowsMethod(WebThemePage.DETAIL, permissions, "app.search"));

        assertFalse(WebHomeThemePolicy.allowsMethod(WebThemePage.DETAIL, permissions, "favorite.set"));
        assertFalse(WebHomeThemePolicy.allowsMethod(WebThemePage.DETAIL, permissions, "history.item"));
        assertFalse(WebHomeThemePolicy.allowsMethod(WebThemePage.DETAIL, permissions, "app.openSetting"));
        assertFalse(WebHomeThemePolicy.allowsMethod(WebThemePage.DETAIL, permissions, "net.request"));
    }

    @Test
    public void v2Home_cannotUseDetailCapabilities() {
        HashSet<String> permissions = new HashSet<>(Arrays.asList(
                "vod.home", "navigation.openDetail", "vod.detail", "player.playVod"));

        assertTrue(WebHomeThemePolicy.allowsMethod(WebThemePage.HOME, permissions, "vod.home"));
        assertTrue(WebHomeThemePolicy.allowsMethod(WebThemePage.HOME, permissions, "navigation.openDetail"));
        assertFalse(WebHomeThemePolicy.allowsMethod(WebThemePage.HOME, permissions, "app.search"));
        assertFalse(WebHomeThemePolicy.allowsMethod(WebThemePage.HOME, permissions, "app.openVod"));
        assertFalse(WebHomeThemePolicy.allowsMethod(WebThemePage.HOME, permissions, "app.openSetting"));
        assertFalse(WebHomeThemePolicy.allowsMethod(WebThemePage.HOME, permissions, "vod.detail"));
        assertFalse(WebHomeThemePolicy.allowsMethod(WebThemePage.HOME, permissions, "player.playVod"));

        permissions.add("app.search");
        permissions.add("app.openVod");
        permissions.add("app.openSetting");
        assertTrue(WebHomeThemePolicy.allowsMethod(WebThemePage.HOME, permissions, "app.search"));
        assertTrue(WebHomeThemePolicy.allowsMethod(WebThemePage.HOME, permissions, "app.openVod"));
        assertTrue(WebHomeThemePolicy.allowsMethod(WebThemePage.HOME, permissions, "app.openSetting"));
    }

    @Test
    public void v2CapabilitiesExposeOnlyPermissionsSupportedByTheCurrentPage() {
        assertTrue(WebHomeThemePolicy.allowsPermission(WebThemePage.HOME, "vod.home"));
        assertTrue(WebHomeThemePolicy.allowsPermission(WebThemePage.HOME, "app.openSetting"));
        assertFalse(WebHomeThemePolicy.allowsPermission(WebThemePage.HOME, "vod.detail"));
        assertFalse(WebHomeThemePolicy.allowsPermission(WebThemePage.HOME, "net.request"));

        assertTrue(WebHomeThemePolicy.allowsPermission(WebThemePage.DETAIL, "vod.detail"));
        assertTrue(WebHomeThemePolicy.allowsPermission(WebThemePage.DETAIL, "favorite.write"));
        assertFalse(WebHomeThemePolicy.allowsPermission(WebThemePage.DETAIL, "app.openSetting"));
        assertFalse(WebHomeThemePolicy.allowsPermission(null, "vod.home"));
    }
}
