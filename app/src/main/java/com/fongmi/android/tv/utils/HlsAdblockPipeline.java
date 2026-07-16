package com.fongmi.android.tv.utils;

import androidx.media3.exoplayer.hls.playlist.HlsAdsParser;

import java.net.URI;
import java.util.List;

public final class HlsAdblockPipeline {

    private HlsAdblockPipeline() {}

    public static Outcome apply(String url, String manifest, List<HlsManifestCleaner.Rule> rules, boolean legacyFallback) {
        HlsManifestCleaner.Result clean = HlsManifestCleaner.clean(url, manifest, rules);
        if (clean.changed()) {
            return new Outcome(clean.manifest(), true, false, clean.removedSegments(), clean.removedDurationSec());
        }
        if (!legacyFallback || clean.fallback() || manifest == null || !manifest.contains("#EXT-X-ENDLIST")) {
            return new Outcome(manifest, false, false, 0, 0);
        }
        try {
            String filtered = HlsAdsParser.process(manifest);
            return new Outcome(filtered, false, !filtered.equals(manifest), 0, 0);
        } catch (Throwable ignored) {
            return new Outcome(manifest, false, false, 0, 0);
        }
    }

    public static boolean isCoreM3u8Proxy(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            boolean loopback = "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host);
            return loopback && "/m3u8".equals(uri.getPath());
        } catch (RuntimeException e) {
            return false;
        }
    }

    public record Outcome(String manifest, boolean structured, boolean legacy, int removedSegments, double removedDurationSec) {}
}
