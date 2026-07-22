package com.fongmi.android.tv.player;

import com.fongmi.android.tv.setting.PreloadSetting;
import java.net.URI;

public enum PlaybackRoute {

    DIRECT_REMOTE_HTTP,
    APP_LOCAL_SERVICE,
    EXTERNAL_LOOPBACK_PROXY,
    OTHER;

    public static PlaybackRoute classify(String url) {
        return resolve(url).route();
    }

    public static Resolution resolve(String url) {
        if (url == null || url.isBlank()) return new Resolution(OTHER, Owner.UNKNOWN, Evidence.EMPTY_URL, Confidence.UNKNOWN, "none", false);
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return new Resolution(OTHER, Owner.UNKNOWN, Evidence.NON_HTTP_SCHEME, Confidence.UNKNOWN, safeScheme(scheme), false);
            String host = uri.getHost();
            if (host == null || host.isBlank()) return new Resolution(OTHER, Owner.UNKNOWN, Evidence.INVALID_HTTP_URL, Confidence.UNKNOWN, safeScheme(scheme), false);
            if (!isLoopback(host)) return new Resolution(DIRECT_REMOTE_HTTP, Owner.REMOTE_ORIGIN, Evidence.REMOTE_HOST, Confidence.CONFIRMED, safeScheme(scheme), false);
            PlaybackRouteRegistry.AppOwner appOwner = PlaybackRouteRegistry.findAppOwner(uri.getPort());
            if (appOwner != null) return new Resolution(APP_LOCAL_SERVICE, owner(appOwner), Evidence.REGISTERED_APP_PORT, Confidence.CONFIRMED, safeScheme(scheme), true);
            return new Resolution(EXTERNAL_LOOPBACK_PROXY, Owner.EXTERNAL_OR_UNKNOWN_LOOPBACK, Evidence.UNREGISTERED_LOOPBACK_PORT, Confidence.INFERRED, safeScheme(scheme), true);
        } catch (IllegalArgumentException e) {
            return new Resolution(OTHER, Owner.UNKNOWN, Evidence.INVALID_URL, Confidence.UNKNOWN, "other", false);
        }
    }

    public int effectivePreloadThreads(int requestedThreads) {
        int requested = Math.min(Math.max(requestedThreads, PreloadSetting.MIN_THREADS), PreloadSetting.MAX_THREADS);
        return switch (this) {
            case DIRECT_REMOTE_HTTP, APP_LOCAL_SERVICE -> requested;
            case EXTERNAL_LOOPBACK_PROXY, OTHER -> PreloadSetting.MIN_THREADS;
        };
    }

    private static boolean isLoopback(String host) {
        if (host == null) return false;
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "[::1]".equals(host)
                || "0:0:0:0:0:0:0:1".equals(host);
    }

    private static Owner owner(PlaybackRouteRegistry.AppOwner owner) {
        return switch (owner) {
            case MAIN_SERVER -> Owner.APP_MAIN_SERVER;
            case HLS_PROXY -> Owner.APP_HLS_PROXY;
        };
    }

    private static String safeScheme(String scheme) {
        if ("http".equalsIgnoreCase(scheme)) return "http";
        if ("https".equalsIgnoreCase(scheme)) return "https";
        return scheme == null ? "none" : "other";
    }

    public enum Owner {
        REMOTE_ORIGIN("remote-origin"),
        APP_MAIN_SERVER("app-main-server"),
        APP_HLS_PROXY("app-hls-proxy"),
        EXTERNAL_OR_UNKNOWN_LOOPBACK("external-or-unknown-loopback"),
        UNKNOWN("unknown");

        private final String label;

        Owner(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Evidence {
        REMOTE_HOST("remote-host"),
        REGISTERED_APP_PORT("registered-app-port"),
        UNREGISTERED_LOOPBACK_PORT("unregistered-loopback-port"),
        NON_HTTP_SCHEME("non-http-scheme"),
        INVALID_HTTP_URL("invalid-http-url"),
        INVALID_URL("invalid-url"),
        EMPTY_URL("empty-url");

        private final String label;

        Evidence(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Confidence {
        CONFIRMED("confirmed"),
        INFERRED("inferred"),
        UNKNOWN("unknown");

        private final String label;

        Confidence(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Resolution(PlaybackRoute route, Owner owner, Evidence evidence, Confidence confidence, String scheme, boolean loopback) {

        public String logSummary() {
            return "route=" + route +
                    " owner=" + owner.label() +
                    " evidence=" + evidence.label() +
                    " confidence=" + confidence.label() +
                    " scheme=" + scheme +
                    " loopback=" + loopback +
                    " " + PlaybackRouteCapabilities.resolve(this).logSummary();
        }
    }
}
