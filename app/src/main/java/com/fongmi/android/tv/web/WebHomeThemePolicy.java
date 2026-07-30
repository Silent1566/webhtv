package com.fongmi.android.tv.web;

import java.net.URI;

/** Minimal, origin-bound capability policy for untrusted global themes. */
public final class WebHomeThemePolicy {

    private WebHomeThemePolicy() {
    }

    public static boolean allowsMethod(String method) {
        return switch (method == null ? "" : method) {
            case "vod.home", "vod.category", "player.playVod", "app.search", "app.openVod", "app.openSetting",
                    "ui.getViewport", "navigation.back", "navigation.reload" -> true;
            default -> false;
        };
    }

    public static boolean allowsMessage(String expectedOrigin, String sourceOrigin, boolean isMainFrame) {
        if (!isMainFrame || expectedOrigin == null || sourceOrigin == null) return false;
        try {
            URI expected = URI.create(expectedOrigin);
            URI actual = URI.create(sourceOrigin);
            if (!isOrigin(expected) || !isOrigin(actual)) return false;
            int expectedPort = expected.getPort() >= 0 ? expected.getPort() : 443;
            int actualPort = actual.getPort() >= 0 ? actual.getPort() : 443;
            return "https".equalsIgnoreCase(expected.getScheme())
                    && expected.getHost().equalsIgnoreCase(actual.getHost())
                    && expectedPort == actualPort
                    && expected.getScheme().equalsIgnoreCase(actual.getScheme());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isOrigin(URI uri) {
        String path = uri.getPath();
        return uri.getScheme() != null && uri.getHost() != null && uri.getUserInfo() == null
                && uri.getQuery() == null && uri.getFragment() == null
                && (path == null || path.isEmpty() || "/".equals(path));
    }
}
