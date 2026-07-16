package com.fongmi.android.tv.player.iso;

import com.github.catvod.crawler.SpiderDebug;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class IsoPageCache implements RemoteIsoSource {

    public static final int DEFAULT_PAGE_SIZE = 4 * 1024 * 1024;
    public static final int DEFAULT_MAX_PAGES = 8;

    private final Map<Long, CompletableFuture<byte[]>> pending = new LinkedHashMap<>();
    private final LinkedHashMap<Long, byte[]> pages;
    private final ExecutorService prefetch;
    private final RemoteIsoSource source;
    private final int maxPages;
    private final int pageSize;
    private volatile boolean closed;
    private long hits;
    private long misses;

    public IsoPageCache(RemoteIsoSource source) {
        this(source, DEFAULT_PAGE_SIZE, DEFAULT_MAX_PAGES);
    }

    public IsoPageCache(RemoteIsoSource source, int pageSize, int maxPages) {
        this.source = source;
        this.pageSize = pageSize;
        this.maxPages = maxPages;
        this.prefetch = Executors.newSingleThreadExecutor(r -> new Thread(r, "iso-prefetch"));
        this.pages = new LinkedHashMap<>(maxPages, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, byte[]> eldest) {
                return size() > IsoPageCache.this.maxPages;
            }
        };
    }

    @Override
    public long length() throws IOException {
        return source.length();
    }

    @Override
    public int readAt(long offset, byte[] buffer, int bufferOffset, int length) throws IOException {
        if (closed) throw new IsoSourceException(IsoSourceException.Reason.CLOSED, "ISO cache closed");
        long total = length();
        if (offset >= total || length == 0) return 0;
        int remaining = (int) Math.min(length, total - offset);
        int written = 0;
        while (remaining > 0) {
            long pageIndex = offset / pageSize;
            int pageOffset = (int) (offset % pageSize);
            byte[] page = page(pageIndex);
            if (pageOffset >= page.length) break;
            int count = Math.min(remaining, page.length - pageOffset);
            System.arraycopy(page, pageOffset, buffer, bufferOffset + written, count);
            offset += count;
            written += count;
            remaining -= count;
            if (pageOffset + count == page.length) prefetch(pageIndex + 1);
        }
        return written;
    }

    private byte[] page(long index) throws IOException {
        CompletableFuture<byte[]> future;
        boolean owner = false;
        synchronized (this) {
            byte[] cached = pages.get(index);
            if (cached != null) {
                hits++;
                return cached;
            }
            misses++;
            future = pending.get(index);
            if (future == null) {
                future = new CompletableFuture<>();
                pending.put(index, future);
                owner = true;
            }
        }
        if (owner) {
            try {
                byte[] loaded = load(index);
                synchronized (this) {
                    pages.put(index, loaded);
                    pending.remove(index);
                }
                future.complete(loaded);
                logStats();
                return loaded;
            } catch (Throwable e) {
                synchronized (this) {
                    pending.remove(index);
                }
                future.completeExceptionally(e);
                if (e instanceof IOException io) throw io;
                throw new IOException(e);
            }
        }
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("ISO page wait interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) throw io;
            throw new IOException(cause);
        }
    }

    private byte[] load(long index) throws IOException {
        long offset = index * pageSize;
        long total = source.length();
        if (offset >= total) return new byte[0];
        int expected = (int) Math.min(pageSize, total - offset);
        byte[] data = new byte[expected];
        int read = 0;
        while (read < expected) {
            int count = source.readAt(offset + read, data, read, expected - read);
            if (count <= 0) break;
            read += count;
        }
        if (read == expected) return data;
        byte[] partial = new byte[read];
        System.arraycopy(data, 0, partial, 0, read);
        return partial;
    }

    private void prefetch(long index) {
        prefetch.execute(() -> {
            if (closed) return;
            synchronized (IsoPageCache.this) {
                if (pages.containsKey(index) || pending.containsKey(index)) return;
            }
            try {
                page(index);
            } catch (IOException ignored) {
            }
        });
    }

    private synchronized void logStats() {
        SpiderDebug.log("iso-cache", "pages=%d pending=%d hits=%d misses=%d", pages.size(), pending.size(), hits, misses);
    }

    @Override
    public String validator() {
        return source.validator();
    }

    @Override
    public void close() {
        closed = true;
        prefetch.shutdownNow();
        synchronized (this) {
            pages.clear();
            pending.clear();
        }
        source.close();
    }
}
