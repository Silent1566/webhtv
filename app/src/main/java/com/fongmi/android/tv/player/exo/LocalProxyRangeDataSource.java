package com.fongmi.android.tv.player.exo;

import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LocalProxyRangeDataSource implements DataSource {

    private static final long DEFAULT_CHUNK_SIZE_BYTES = 8L * 1024 * 1024;
    private static final Pattern CONTENT_RANGE = Pattern.compile("bytes\\s+(?:\\d+-\\d+|\\*)/(\\d+|\\*)", Pattern.CASE_INSENSITIVE);

    private final DataSource.Factory upstreamFactory;
    private final List<TransferListener> transferListeners;
    private final long chunkSize;

    private Map<String, List<String>> responseHeaders;
    private DataSource upstream;
    private DataSpec dataSpec;
    private Uri resolvedUri;
    private long bytesRemaining;
    private long chunkBytesRemaining;
    private long bytesRead;
    private boolean splitRanges;

    static DataSource.Factory wrap(DataSource.Factory upstreamFactory) {
        return () -> new LocalProxyRangeDataSource(upstreamFactory, DEFAULT_CHUNK_SIZE_BYTES);
    }

    LocalProxyRangeDataSource(DataSource.Factory upstreamFactory, long chunkSize) {
        if (chunkSize <= 0) throw new IllegalArgumentException("chunkSize must be positive");
        this.upstreamFactory = upstreamFactory;
        this.chunkSize = chunkSize;
        this.transferListeners = new ArrayList<>();
        this.responseHeaders = Collections.emptyMap();
        this.bytesRemaining = C.LENGTH_UNSET;
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        transferListeners.add(transferListener);
        if (upstream != null) upstream.addTransferListener(transferListener);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        this.dataSpec = dataSpec;
        this.bytesRead = 0;
        this.bytesRemaining = dataSpec.length;
        this.splitRanges = isLocalProxyUrl(dataSpec.uri.toString());
        if (!splitRanges) return openUpstream(dataSpec, true);

        openChunk();
        if (bytesRemaining == C.LENGTH_UNSET) {
            long resourceLength = getResourceLength(responseHeaders);
            if (resourceLength != C.LENGTH_UNSET) bytesRemaining = Math.max(0, resourceLength - dataSpec.position);
        }
        if (bytesRemaining != C.LENGTH_UNSET) chunkBytesRemaining = Math.min(chunkBytesRemaining, bytesRemaining);
        return bytesRemaining;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (!splitRanges) return upstream.read(buffer, offset, length);
        if (length == 0) return 0;
        if (bytesRemaining == 0) return C.RESULT_END_OF_INPUT;
        if (chunkBytesRemaining == 0 && !openNextChunk()) return C.RESULT_END_OF_INPUT;

        int readLength = (int) Math.min(length, chunkBytesRemaining);
        if (bytesRemaining != C.LENGTH_UNSET) readLength = (int) Math.min(readLength, bytesRemaining);
        int read = upstream.read(buffer, offset, readLength);
        if (read == C.RESULT_END_OF_INPUT) {
            bytesRemaining = 0;
            return C.RESULT_END_OF_INPUT;
        }
        bytesRead += read;
        chunkBytesRemaining -= read;
        if (bytesRemaining != C.LENGTH_UNSET) bytesRemaining -= read;
        return read;
    }

    @Nullable
    @Override
    public Uri getUri() {
        return resolvedUri;
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return responseHeaders;
    }

    @Override
    public void close() throws IOException {
        try {
            closeUpstream();
        } finally {
            dataSpec = null;
            resolvedUri = null;
            responseHeaders = Collections.emptyMap();
            bytesRemaining = C.LENGTH_UNSET;
            chunkBytesRemaining = 0;
            bytesRead = 0;
            splitRanges = false;
        }
    }

    private boolean openNextChunk() throws IOException {
        closeUpstream();
        if (bytesRemaining == 0) return false;
        openChunk();
        if (chunkBytesRemaining > 0) return true;
        bytesRemaining = 0;
        return false;
    }

    private void openChunk() throws IOException {
        long chunkLength = getChunkLength(bytesRemaining, chunkSize);
        DataSpec chunkSpec = dataSpec.subrange(bytesRead, chunkLength);
        long openedLength = openUpstream(chunkSpec, bytesRead == 0);
        chunkBytesRemaining = openedLength == C.LENGTH_UNSET ? chunkLength : Math.min(chunkLength, openedLength);
    }

    private long openUpstream(DataSpec request, boolean keepResponse) throws IOException {
        DataSource next = upstreamFactory.createDataSource();
        for (TransferListener listener : transferListeners) next.addTransferListener(listener);
        try {
            long length = next.open(request);
            upstream = next;
            resolvedUri = next.getUri();
            if (keepResponse) responseHeaders = next.getResponseHeaders();
            return length;
        } catch (IOException e) {
            next.close();
            throw e;
        }
    }

    private void closeUpstream() throws IOException {
        if (upstream == null) return;
        try {
            upstream.close();
        } finally {
            upstream = null;
        }
    }

    static long getChunkLength(long bytesRemaining, long chunkSize) {
        return bytesRemaining == C.LENGTH_UNSET ? chunkSize : Math.min(bytesRemaining, chunkSize);
    }

    static boolean isLocalProxyUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            URI uri = URI.create(url);
            if (!"http".equalsIgnoreCase(uri.getScheme())) return false;
            String path = uri.getPath();
            if (!"/proxy".equals(path) && (path == null || !path.startsWith("/proxy/"))) return false;
            return isLoopbackHost(uri.getHost());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isLoopbackHost(String host) {
        if (host == null || host.isEmpty()) return false;
        String value = host;
        if (value.startsWith("[") && value.endsWith("]")) value = value.substring(1, value.length() - 1);
        value = value.toLowerCase(Locale.ROOT);
        return "localhost".equals(value) || "127.0.0.1".equals(value) || "0.0.0.0".equals(value) || "::1".equals(value);
    }

    static long getResourceLength(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) return C.LENGTH_UNSET;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (!"Content-Range".equalsIgnoreCase(entry.getKey()) || entry.getValue() == null) continue;
            for (String value : entry.getValue()) {
                if (value == null) continue;
                Matcher matcher = CONTENT_RANGE.matcher(value.trim());
                if (!matcher.matches() || "*".equals(matcher.group(1))) continue;
                try {
                    return Long.parseLong(matcher.group(1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return C.LENGTH_UNSET;
    }
}
