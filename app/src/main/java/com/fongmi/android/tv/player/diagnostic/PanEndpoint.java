package com.fongmi.android.tv.player.diagnostic;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PanEndpoint {

    private final String playbackUrl;
    private final String upstreamUrl;
    private final Map<String, String> upstreamHeaders;
    private final PanProvider provider;
    private final int configuredThreads;

    PanEndpoint(String playbackUrl, String upstreamUrl, Map<String, String> upstreamHeaders, PanProvider provider, int configuredThreads) {
        this.playbackUrl = playbackUrl;
        this.upstreamUrl = upstreamUrl;
        this.upstreamHeaders = Collections.unmodifiableMap(new LinkedHashMap<>(upstreamHeaders));
        this.provider = provider;
        this.configuredThreads = configuredThreads;
    }

    public String playbackUrl() {
        return playbackUrl;
    }

    public String upstreamUrl() {
        return upstreamUrl;
    }

    public Map<String, String> upstreamHeaders() {
        return upstreamHeaders;
    }

    public PanProvider provider() {
        return provider;
    }

    public int configuredThreads() {
        return configuredThreads;
    }

    public boolean hasDirectUpstream() {
        return upstreamUrl != null && !upstreamUrl.isEmpty();
    }

    public String upstreamHost() {
        return host(upstreamUrl);
    }

    public String playbackHost() {
        return host(playbackUrl);
    }

    public String redactedSummary() {
        return "provider=" + provider.label()
                + " playbackHost=" + playbackHost()
                + " upstreamHost=" + upstreamHost()
                + " headers=" + upstreamHeaders.size()
                + " configuredThreads=" + configuredThreads;
    }

    @Override
    public String toString() {
        return "PanEndpoint{" + redactedSummary() + "}";
    }

    private static String host(String url) {
        if (url == null || url.isEmpty()) return "";
        try {
            String host = URI.create(url).getHost();
            return host == null ? "" : host;
        } catch (RuntimeException e) {
            return "";
        }
    }
}
