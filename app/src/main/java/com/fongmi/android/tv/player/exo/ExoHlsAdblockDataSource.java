package com.fongmi.android.tv.player.exo;

import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.AdBlockStatsStore;
import com.fongmi.android.tv.utils.HlsAdblockNotice;
import com.fongmi.android.tv.utils.HlsAdblockPipeline;
import com.fongmi.android.tv.utils.HlsManifestCleaner;
import com.fongmi.android.tv.api.config.HlsRuleConfig;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.crawler.SpiderDebug;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ExoHlsAdblockDataSource implements DataSource {

    private static final String TAG = "exo-hls-adblock";
    private final DataSource upstream;
    private byte[] manifest;
    private int position;

    ExoHlsAdblockDataSource(DataSource upstream) {
        this.upstream = upstream;
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        upstream.addTransferListener(transferListener);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        if (!isManifestUrl(dataSpec.uri.toString())) return upstream.open(dataSpec);
        upstream.open(dataSpec);
        try {
            byte[] original = readAll();
            String text = new String(original, StandardCharsets.UTF_8);
            HlsAdblockPipeline.Outcome outcome = HlsAdblockPipeline.apply(
                    dataSpec.uri.toString(), text, HlsRuleConfig.getRules(), true);
            manifest = outcome.manifest().getBytes(StandardCharsets.UTF_8);
            position = 0;
            recordAndNotify(dataSpec.uri, outcome);
            if (SpiderDebug.isEnabled()) {
                SpiderDebug.log(TAG, "manifest bytes=%d rewritten=%d removed=%d structured=%s legacy=%s url=%s",
                        original.length, manifest.length, outcome.removedSegments(), outcome.structured(),
                        outcome.legacy(), dataSpec.uri);
            }
            return manifest.length;
        } catch (RuntimeException e) {
            close();
            throw e;
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (manifest == null) return upstream.read(buffer, offset, length);
        if (position >= manifest.length) return C.RESULT_END_OF_INPUT;
        int count = Math.min(length, manifest.length - position);
        System.arraycopy(manifest, position, buffer, offset, count);
        position += count;
        return count;
    }

    @Nullable
    @Override
    public Uri getUri() {
        return upstream.getUri();
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return upstream.getResponseHeaders();
    }

    @Override
    public void close() throws IOException {
        manifest = null;
        position = 0;
        upstream.close();
    }

    static boolean isManifestUrl(String url) {
        if (url == null) return false;
        int query = url.indexOf('?');
        int fragment = url.indexOf('#');
        int end = url.length();
        if (query >= 0) end = Math.min(end, query);
        if (fragment >= 0) end = Math.min(end, fragment);
        return url.substring(0, end).toLowerCase(Locale.ROOT).endsWith(".m3u8");
    }

    static Result cleanForTest(String url, String manifest) {
        HlsManifestCleaner.Rule rule = HlsManifestCleaner.Rule.builder()
                .id("test-ad-path")
                .segmentUrlPatterns(List.of("(^|/)ad/"))
                .build();
        HlsAdblockPipeline.Outcome outcome = HlsAdblockPipeline.apply(
                url, manifest, List.of(rule), true);
        return new Result(outcome.manifest(), outcome.structured() || outcome.legacy(), notice(outcome));
    }

    private byte[] readAll() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = upstream.read(buffer, 0, buffer.length)) != C.RESULT_END_OF_INPUT) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static void recordAndNotify(Uri uri, HlsAdblockPipeline.Outcome outcome) {
        if (!outcome.structured() && !outcome.legacy()) return;
        long fallbackCount = outcome.legacy() ? 1 : 0;
        AdBlockStatsStore.recordBlocks(uri.getHost(), outcome.ruleCounts(), fallbackCount);
        if (!HlsAdblockNotice.shouldNotify(uri.toString(), System.currentTimeMillis())) return;
        String message = notice(outcome);
        App.post(() -> Notify.show(message));
    }

    private static String notice(HlsAdblockPipeline.Outcome outcome) {
        int removed = outcome.removedSegments() > 0 ? outcome.removedSegments() : (outcome.legacy() ? 1 : 0);
        return outcome.structured() && outcome.removedDurationSec() > 0
                ? String.format(Locale.US, "已跳过 %d 个广告片段（%.1f 秒）", removed, outcome.removedDurationSec())
                : "已跳过 " + removed + " 个广告片段";
    }

    record Result(String manifest, boolean changed, String notice) {}

    static final class Factory implements DataSource.Factory {
        private final DataSource.Factory upstreamFactory;

        Factory(DataSource.Factory upstreamFactory) {
            this.upstreamFactory = upstreamFactory;
        }

        @Override
        public DataSource createDataSource() {
            return new ExoHlsAdblockDataSource(upstreamFactory.createDataSource());
        }
    }
}
