package com.fongmi.android.tv.player.iso;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

final class IsoPlaybackSession {

    private static final int DISC_SIGNATURE_OFFSET = 16 * 2048;
    private static final int DISC_SIGNATURE_LENGTH = 64 * 1024;

    private final AtomicBoolean closed = new AtomicBoolean();
    private final IsoPageCache source;
    private final long id;

    IsoPlaybackSession(long id, String url, Map<String, String> headers) {
        this.id = id;
        this.source = new IsoPageCache(new HttpRangeIsoSource(url, headers));
    }

    long id() {
        return id;
    }

    long length() throws IOException {
        ensureOpen();
        return source.length();
    }

    int readAt(long offset, ByteBuffer target, int length) throws IOException {
        ensureOpen();
        int wanted = Math.min(length, target.remaining());
        byte[] data = new byte[wanted];
        int read = source.readAt(offset, data, 0, wanted);
        if (read > 0) target.put(data, 0, read);
        return read;
    }

    boolean hasDiscImageSignature() throws IOException {
        ensureOpen();
        byte[] data = new byte[DISC_SIGNATURE_LENGTH];
        int read = source.readAt(DISC_SIGNATURE_OFFSET, data, 0, data.length);
        return contains(data, read, "CD001") || contains(data, read, "NSR02") || contains(data, read, "NSR03");
    }

    private static boolean contains(byte[] data, int length, String value) {
        if (length <= 0) return false;
        byte[] needle = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        for (int i = 0; i <= length - needle.length; i++) {
            int j = 0;
            while (j < needle.length && data[i + j] == needle[j]) j++;
            if (j == needle.length) return true;
        }
        return false;
    }

    void close() {
        if (closed.compareAndSet(false, true)) source.close();
    }

    private void ensureOpen() throws IsoSourceException {
        if (closed.get()) throw new IsoSourceException(IsoSourceException.Reason.CLOSED, "ISO session closed");
    }
}
