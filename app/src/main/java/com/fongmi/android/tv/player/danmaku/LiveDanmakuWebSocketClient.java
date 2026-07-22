package com.fongmi.android.tv.player.danmaku;

import com.github.catvod.net.OkHttp;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public final class LiveDanmakuWebSocketClient {

    private static final long CONNECT_TIMEOUT_SECONDS = 10L;
    private static final long HANDSHAKE_TIMEOUT_SECONDS = 20L;

    private final ExecutorService executor;
    private final ConnectionPool connectionPool;
    private final OkHttpClient client;
    private boolean released;

    public LiveDanmakuWebSocketClient() {
        AtomicInteger threadNumber = new AtomicInteger();
        executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "live-danmaku-ws-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        Dispatcher dispatcher = new Dispatcher(executor);
        dispatcher.setMaxRequests(4);
        dispatcher.setMaxRequestsPerHost(2);
        connectionPool = new ConnectionPool(2, 1L, TimeUnit.MINUTES);
        client = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(0L, TimeUnit.MILLISECONDS)
                .pingInterval(0L, TimeUnit.MILLISECONDS)
                .proxySelector(new LiveDanmakuProxySelector(OkHttp.selector()))
                .build();
    }

    public synchronized WebSocket newWebSocket(String url, WebSocketListener listener) {
        if (released) throw new IllegalStateException("WebSocket client is released");
        Request request = new Request.Builder().url(url).build();
        return client.newWebSocket(request, listener);
    }

    public synchronized void release() {
        if (released) return;
        released = true;
        client.dispatcher().cancelAll();
        connectionPool.evictAll();
        executor.shutdownNow();
    }
}
