package com.fongmi.android.tv.player.exo;

import static androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.ts.TsExtractor;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import java.io.File;
import java.net.URI;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class MediaSourceFactory implements MediaSource.Factory {

    private static final int CACHE_SPACE_PERCENT = 80;
    private static final String CONCAT_SOURCE_SEPARATOR = "***";
    private static final String CONCAT_SOURCE_SEPARATOR_REGEX = "\\*\\*\\*";
    private static final String CONCAT_DURATION_SEPARATOR = "|||";
    private static final String CONCAT_DURATION_SEPARATOR_REGEX = "\\|\\|\\|";

    private static StandaloneDatabaseProvider databaseProvider;
    private static Cache cache;

    private final DefaultMediaSourceFactory defaultMediaSourceFactory;
    private OkHttpDataSource.Factory httpDataSourceFactory;
    private DataSource.Factory dataSourceFactory;
    private ExtractorsFactory extractorsFactory;

    public MediaSourceFactory() {
        defaultMediaSourceFactory = new DefaultMediaSourceFactory(getDataSourceFactory(), getExtractorsFactory()).setLoadOnlySelectedTracks(PlaybackPerformanceSetting.isLoadOnlySelectedTracksEnabled());
    }

    static DataSource.Factory createUpstreamDataSourceFactory(Map<String, String> headers) {
        OkHttpDataSource.Factory factory = new OkHttpDataSource.Factory(OkHttp.player());
        applyHeaders(factory, headers);
        return new DefaultDataSource.Factory(App.get(), LocalProxyRangeDataSource.wrap(factory));
    }

    static synchronized Cache getCache() {
        if (cache != null) return cache;
        File dir = Path.exoCache();
        return cache = new SimpleCache(dir, new LeastRecentlyUsedCacheEvictor(getMaxCacheSize(dir)), getDatabaseProvider());
    }

    private static StandaloneDatabaseProvider getDatabaseProvider() {
        if (databaseProvider == null) databaseProvider = new StandaloneDatabaseProvider(App.get());
        return databaseProvider;
    }

    private static long getMaxCacheSize(File dir) {
        long usedBytes = FileUtil.getDirectorySize(dir);
        long availableBytes = Math.max(0, FileUtil.getAvailableStorageSpace(dir));
        long storageBudget = (usedBytes + availableBytes) * CACHE_SPACE_PERCENT / 100;
        return Math.min(PreloadSetting.getPreloadSizeBytes(PlayerSetting.EXO), storageBudget);
    }

    static boolean isConcatenatingUrl(String url) {
        return url != null && url.contains(CONCAT_SOURCE_SEPARATOR) && url.contains(CONCAT_DURATION_SEPARATOR);
    }

    @NonNull
    @Override
    public MediaSource.Factory setDrmSessionManagerProvider(@NonNull DrmSessionManagerProvider drmSessionManagerProvider) {
        return this;
    }

    @NonNull
    @Override
    public MediaSource.Factory setLoadErrorHandlingPolicy(@NonNull LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
        return this;
    }

    @NonNull
    @Override
    public @C.ContentType int[] getSupportedTypes() {
        return defaultMediaSourceFactory.getSupportedTypes();
    }

    @NonNull
    @Override
    public MediaSource createMediaSource(@NonNull MediaItem mediaItem) {
        applyHeaders(getHttpDataSourceFactory(), ExoUtil.extractHeaders(mediaItem));
        String url = mediaItem.requestMetadata.mediaUri != null ? mediaItem.requestMetadata.mediaUri.toString() : "";
        if (isConcatenatingUrl(url)) return createConcatenatingMediaSource(mediaItem, url);
        else return defaultMediaSourceFactory.createMediaSource(mediaItem);
    }

    private MediaSource createConcatenatingMediaSource(MediaItem mediaItem, String url) {
        ConcatenatingMediaSource2.Builder builder = new ConcatenatingMediaSource2.Builder();
        for (String split : url.split(CONCAT_SOURCE_SEPARATOR_REGEX)) {
            String[] info = split.split(CONCAT_DURATION_SEPARATOR_REGEX);
            if (info.length >= 2) builder.add(defaultMediaSourceFactory.createMediaSource(mediaItem.buildUpon().setUri(UrlUtil.uri(info[0])).build()), Long.parseLong(info[1]));
        }
        return builder.build();
    }

    private ExtractorsFactory getExtractorsFactory() {
        if (extractorsFactory == null) extractorsFactory = new DefaultExtractorsFactory().setTsExtractorFlags(FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS).setTsExtractorTimestampSearchBytes(TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * 10);
        return extractorsFactory;
    }

    private DataSource.Factory getDataSourceFactory() {
        if (dataSourceFactory == null) dataSourceFactory = getCacheDataSource(new DefaultDataSource.Factory(App.get(), LocalProxyRangeDataSource.wrap(getHttpDataSourceFactory())));
        return dataSourceFactory;
    }

    private CacheDataSource.Factory getCacheDataSource(DataSource.Factory upstreamFactory) {
        return new CacheDataSource.Factory().setCache(getCache()).setUpstreamDataSourceFactory(upstreamFactory).setCacheWriteDataSinkFactory(null).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    private OkHttpDataSource.Factory getHttpDataSourceFactory() {
        if (httpDataSourceFactory == null) httpDataSourceFactory = new OkHttpDataSource.Factory(OkHttp.player());
        return httpDataSourceFactory;
    }

    private static void applyHeaders(OkHttpDataSource.Factory factory, Map<String, String> headers) {
        Map<String, String> sanitized = sanitizeHeaders(headers);
        String userAgent = removeUserAgentHeader(sanitized);
        factory.setUserAgent(userAgent).setDefaultRequestProperties(sanitized);
    }

    static Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        if (headers == null || headers.isEmpty()) return sanitized;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            String key = entry.getKey().trim();
            if (key.isEmpty()) continue;
            sanitized.put(key, entry.getValue().trim());
        }
        return sanitized;
    }

    static String removeUserAgentHeader(Map<String, String> headers) {
        String userAgent = null;
        Iterator<Map.Entry<String, String>> iterator = headers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            if (!"User-Agent".equalsIgnoreCase(entry.getKey())) continue;
            String value = entry.getValue().trim();
            if (!value.isEmpty()) userAgent = value;
            iterator.remove();
        }
        return userAgent;
    }

    static boolean isLocalProxyUrl(String url) {
        return LocalProxyRangeDataSource.isLocalProxyUrl(url);
    }

    private static boolean isHls(MediaItem mediaItem, String url) {
        String mimeType = mediaItem.localConfiguration == null ? null : mediaItem.localConfiguration.mimeType;
        if (MimeTypes.APPLICATION_M3U8.equals(mimeType)) return true;
        return isHlsUrl(url);
    }

    public static boolean isHlsUrl(String url) {
        String lower = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (lower.contains("m3u8") || lower.contains("type=hls") || lower.contains("format=hls")) return true;
        String path = getUrlPath(lower);
        return path.endsWith("/live.php") || path.contains("/live/");
    }

    private static String getUrlPath(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path != null) return path;
        } catch (IllegalArgumentException ignored) {
        }
        int end = url.length();
        int query = url.indexOf('?');
        int fragment = url.indexOf('#');
        if (query >= 0) end = Math.min(end, query);
        if (fragment >= 0) end = Math.min(end, fragment);
        return url.substring(0, end);
    }

}
