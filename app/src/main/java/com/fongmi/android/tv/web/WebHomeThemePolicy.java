package com.fongmi.android.tv.web;

import java.net.URI;
import java.util.Set;

/** Minimal, origin-bound capability policy for untrusted global themes. */
public final class WebHomeThemePolicy {

    private WebHomeThemePolicy() {
    }

    public static boolean allowsMethod(String method) {
        return switch (method == null ? "" : method) {
            case "vod.home", "vod.category", "player.playVod", "app.search", "app.openVod", "app.openSite", "app.openSetting",
                    "ui.getViewport", "navigation.back", "navigation.reload" -> true;
            default -> false;
        };
    }

    public static boolean allowsMethod(WebThemePage page, Set<String> permissions, String method) {
        if (page == null || method == null) return false;
        if ("theme.info".equals(method) || "ui.getViewport".equals(method)
                || "navigation.back".equals(method) || "navigation.reload".equals(method)) return true;
        if (page == WebThemePage.HOME) {
            return switch (method) {
                case "vod.home" -> has(permissions, "vod.home");
                case "vod.category" -> has(permissions, "vod.category");
                case "navigation.openDetail" -> has(permissions, "navigation.openDetail");
                case "app.search" -> has(permissions, "app.search");
                case "app.openVod" -> has(permissions, "app.openVod");
                case "app.openSite" -> has(permissions, "app.openSite");
                case "app.openSetting" -> has(permissions, "app.openSetting");
                default -> false;
            };
        }
        if (page == WebThemePage.DETAIL) {
            return switch (method) {
                case "vod.detail" -> has(permissions, "vod.detail");
                case "favorite.status" -> has(permissions, "favorite.read");
                case "favorite.set" -> has(permissions, "favorite.write");
                case "history.item" -> has(permissions, "history.read");
                case "player.playVod" -> has(permissions, "player.playVod");
                case "app.search" -> has(permissions, "app.search");
                case "person.open" -> has(permissions, "person.open");
                case "image.preview" -> has(permissions, "image.preview");
                case "image.save" -> has(permissions, "image.save");
                case "recommendation.open" -> has(permissions, "recommendation.open");
                case "recommendation.info" -> has(permissions, "recommendation.info");
                case "recommendation.feedback" -> has(permissions, "recommendation.feedback");
                case "external.open" -> has(permissions, "external.open");
                case "episode.info" -> has(permissions, "episode.info");
                case "navigation.openNativeDetail" -> true;
                default -> false;
            };
        }
        return false;
    }

    static boolean allowsPermission(WebThemePage page, String permission) {
        if (page == null || permission == null) return false;
        return switch (page) {
            case HOME -> switch (permission) {
                case "vod.home", "vod.category", "navigation.openDetail",
                        "app.search", "app.openVod", "app.openSite", "app.openSetting" -> true;
                default -> false;
            };
            case DETAIL -> switch (permission) {
                case "vod.detail", "favorite.read", "favorite.write", "history.read",
                        "player.playVod", "app.search", "person.open", "image.preview", "image.save",
                        "recommendation.open", "recommendation.info", "recommendation.feedback",
                        "external.open", "episode.info" -> true;
                default -> false;
            };
        };
    }

    private static boolean has(Set<String> permissions, String permission) {
        return permissions != null && permissions.contains(permission);
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
