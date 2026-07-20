package com.fongmi.android.tv.player;

public record PlaybackRouteCapabilities(ObservedLeg observedLeg, UpstreamVisibility upstreamVisibility, ControlScope controlScope) {

    public static PlaybackRouteCapabilities resolve(PlaybackRoute.Resolution resolution) {
        PlaybackRoute.Resolution route = resolution == null ? PlaybackRoute.resolve(null) : resolution;
        return switch (route.route()) {
            case DIRECT_REMOTE_HTTP -> new PlaybackRouteCapabilities(ObservedLeg.PLAYER_TO_REMOTE_HTTP, UpstreamVisibility.REQUEST_LEVEL_ONLY, ControlScope.PLAYER_REQUEST_OPTIONS);
            case APP_LOCAL_SERVICE -> new PlaybackRouteCapabilities(ObservedLeg.APP_TO_OWNED_LOCAL_SERVICE, UpstreamVisibility.APP_SERVICE_PATH, ControlScope.APP_OWNED_SERVICE_CODE);
            case EXTERNAL_LOOPBACK_PROXY -> new PlaybackRouteCapabilities(ObservedLeg.APP_TO_LOCAL_ENDPOINT_ONLY, UpstreamVisibility.OPAQUE_EXTERNAL_PROCESS, ControlScope.NONE);
            default -> new PlaybackRouteCapabilities(ObservedLeg.SOURCE_SPECIFIC, UpstreamVisibility.UNKNOWN, ControlScope.NONE);
        };
    }

    public boolean externalUpstreamOpaque() {
        return upstreamVisibility == UpstreamVisibility.OPAQUE_EXTERNAL_PROCESS;
    }

    public String logSummary() {
        return "observedLeg=" + observedLeg.label() +
                " upstreamVisibility=" + upstreamVisibility.label() +
                " controlScope=" + controlScope.label();
    }

    public enum ObservedLeg {
        PLAYER_TO_REMOTE_HTTP("player-to-remote-http"),
        APP_TO_OWNED_LOCAL_SERVICE("app-to-owned-local-service"),
        APP_TO_LOCAL_ENDPOINT_ONLY("app-to-local-endpoint-only"),
        SOURCE_SPECIFIC("source-specific");

        private final String label;

        ObservedLeg(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum UpstreamVisibility {
        REQUEST_LEVEL_ONLY("request-level-only"),
        APP_SERVICE_PATH("app-service-path"),
        OPAQUE_EXTERNAL_PROCESS("opaque-external-process"),
        UNKNOWN("unknown");

        private final String label;

        UpstreamVisibility(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum ControlScope {
        PLAYER_REQUEST_OPTIONS("player-request-options"),
        APP_OWNED_SERVICE_CODE("app-owned-service-code"),
        NONE("none");

        private final String label;

        ControlScope(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
