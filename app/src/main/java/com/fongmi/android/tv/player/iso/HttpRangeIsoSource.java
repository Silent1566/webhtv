package com.fongmi.android.tv.player.iso;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import java.io.EOFException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class HttpRangeIsoSource implements RemoteIsoSource {

    private static final Pattern CONTENT_RANGE = Pattern.compile("bytes\\s+(\\d+)-(\\d+)/(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final int MAX_RETRIES = 2;

    private final Map<String, String> headers;
    private final OkHttpClient client;
    private final String url;
    private volatile Call activeCall;
    private volatile boolean closed;
    private volatile String validator;
    private volatile long sourceLength = -1;

    public HttpRangeIsoSource(String url, Map<String, String> headers) {
        this.url = url;
        this.headers = sanitize(headers);
        this.client = OkHttp.player().newBuilder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public synchronized long length() throws IOException {
        ensureOpen();
        if (sourceLength >= 0) return sourceLength;
        try (Response response = execute(0, 1)) {
            validateResponse(response, 0, 1);
            return sourceLength;
        }
    }

    @Override
    public int readAt(long offset, byte[] buffer, int bufferOffset, int length) throws IOException {
        ensureOpen();
        if (offset < 0 || bufferOffset < 0 || length < 0 || bufferOffset + length > buffer.length) throw new IndexOutOfBoundsException();
        long total = length();
        if (offset >= total || length == 0) return 0;
        int requested = (int) Math.min(length, total - offset);
        IOException failure = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            ensureOpen();
            try (Response response = execute(offset, requested)) {
                validateResponse(response, offset, requested);
                ResponseBody body = response.body();
                if (body == null) throw new EOFException("Empty ISO range body");
                int read = 0;
                while (read < requested) {
                    int count = body.byteStream().read(buffer, bufferOffset + read, requested - read);
                    if (count < 0) break;
                    read += count;
                }
                if (read <= 0) throw new EOFException("Empty ISO range response");
                return read;
            } catch (IsoSourceException e) {
                throw e;
            } catch (IOException e) {
                failure = e;
                if (attempt == MAX_RETRIES || closed) break;
            }
        }
        throw new IsoSourceException(IsoSourceException.Reason.NETWORK, "ISO range request failed", failure);
    }

    private Response execute(long offset, int length) throws IOException {
        Request.Builder builder = new Request.Builder().url(url)
                .header("Range", "bytes=" + offset + "-" + (offset + length - 1))
                .header("Accept-Encoding", "identity");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if ("Range".equalsIgnoreCase(entry.getKey()) || "Accept-Encoding".equalsIgnoreCase(entry.getKey())) continue;
            builder.header(entry.getKey(), entry.getValue());
        }
        Call call = client.newCall(builder.build());
        activeCall = call;
        try {
            return call.execute();
        } finally {
            if (activeCall == call) activeCall = null;
        }
    }

    private synchronized void validateResponse(Response response, long offset, int requested) throws IOException {
        int code = response.code();
        if (code == 401 || code == 403) throw new IsoSourceException(IsoSourceException.Reason.UNAUTHORIZED, code, "ISO link unauthorized or expired");
        if (code == 404) throw new IsoSourceException(IsoSourceException.Reason.NOT_FOUND, code, "ISO file not found");
        if (code == 416) throw new IsoSourceException(IsoSourceException.Reason.RANGE_INVALID, code, "ISO range rejected");
        if (code == 200) throw new IsoSourceException(IsoSourceException.Reason.RANGE_UNSUPPORTED, code, "ISO server ignored Range");
        if (code != 206) throw new IsoSourceException(IsoSourceException.Reason.NETWORK, code, "Unexpected ISO HTTP status " + code);
        String contentRange = response.header("Content-Range");
        Matcher matcher = CONTENT_RANGE.matcher(contentRange == null ? "" : contentRange.trim());
        if (!matcher.matches()) throw new IsoSourceException(IsoSourceException.Reason.RANGE_UNSUPPORTED, code, "Missing ISO Content-Range");
        long start = Long.parseLong(matcher.group(1));
        long end = Long.parseLong(matcher.group(2));
        long total = Long.parseLong(matcher.group(3));
        if (start != offset || end < start || end - start + 1 > requested || total <= 0) {
            throw new IsoSourceException(IsoSourceException.Reason.RANGE_INVALID, code, "Invalid ISO Content-Range");
        }
        if (sourceLength >= 0 && sourceLength != total) throw new IsoSourceException(IsoSourceException.Reason.SOURCE_CHANGED, "ISO length changed");
        sourceLength = total;
        String nextValidator = first(response.header("ETag"), response.header("Last-Modified"));
        if (!TextUtils.isEmpty(validator) && !TextUtils.isEmpty(nextValidator) && !validator.equals(nextValidator)) {
            throw new IsoSourceException(IsoSourceException.Reason.SOURCE_CHANGED, "ISO validator changed");
        }
        if (TextUtils.isEmpty(validator)) validator = nextValidator;
        SpiderDebug.log("iso-source", "range code=%d offset=%d length=%d total=%d validator=%s", code, offset, end - start + 1, total, TextUtils.isEmpty(validator) ? "none" : "present");
    }

    private void ensureOpen() throws IsoSourceException {
        if (closed) throw new IsoSourceException(IsoSourceException.Reason.CLOSED, "ISO source closed");
    }

    @Override
    public String validator() {
        return validator == null ? "" : validator;
    }

    @Override
    public void close() {
        closed = true;
        Call call = activeCall;
        if (call != null) call.cancel();
    }

    private static Map<String, String> sanitize(@Nullable Map<String, String> input) {
        Map<String, String> result = new LinkedHashMap<>();
        if (input == null) return result;
        for (Map.Entry<String, String> entry : input.entrySet()) {
            if (TextUtils.isEmpty(entry.getKey()) || entry.getValue() == null) continue;
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static String first(String first, String second) {
        return !TextUtils.isEmpty(first) ? first : second == null ? "" : second;
    }
}
