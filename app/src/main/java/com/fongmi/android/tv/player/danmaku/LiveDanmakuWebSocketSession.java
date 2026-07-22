package com.fongmi.android.tv.player.danmaku;

import android.content.Context;

import androidx.annotation.Nullable;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public final class LiveDanmakuWebSocketSession {

    private static final int NORMAL_CLOSE_CODE = 1000;
    private static final long STABLE_RESET_MS = 30_000L;

    private final LiveDanmakuWebSocketClient client;
    private final LiveDanmakuNetworkMonitor networkMonitor;
    private final ScheduledExecutorService scheduler;
    private final Listener listener;
    private WebSocket webSocket;
    private String targetUrl;
    private State state = State.IDLE;
    private ScheduledFuture<?> retryFuture;
    private ScheduledFuture<?> stableFuture;
    private long generation;
    private int retryAttempt;
    private boolean released;

    public LiveDanmakuWebSocketSession(Listener listener) {
        this(null, listener);
    }

    public LiveDanmakuWebSocketSession(@Nullable Context context, Listener listener) {
        this.client = new LiveDanmakuWebSocketClient();
        this.listener = listener;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "live-danmaku-retry");
            thread.setDaemon(true);
            return thread;
        });
        this.networkMonitor = context == null ? null : new LiveDanmakuNetworkMonitor(context, () -> retryNow("network_available"));
        if (networkMonitor != null) networkMonitor.start();
    }

    public long connect(String url) {
        if (url == null || url.isBlank()) return generation();
        String sourceUrl = url.trim();
        WebSocket previous;
        long currentGeneration;
        synchronized (this) {
            if (released) return generation;
            if (sourceUrl.equals(targetUrl) && (state == State.CONNECTING || state == State.OPEN)) return generation;
            previous = webSocket;
            webSocket = null;
            cancelScheduledLocked();
            retryAttempt = 0;
            generation++;
            currentGeneration = generation;
            targetUrl = sourceUrl;
            state = State.CONNECTING;
        }
        closeSocket(previous, "replace");
        notifyState(State.CONNECTING, currentGeneration, sourceUrl, -1, "connect");
        openSocket(currentGeneration, sourceUrl);
        return currentGeneration;
    }

    public long stop(String reason) {
        WebSocket socket;
        long stoppedGeneration;
        String stoppedUrl;
        synchronized (this) {
            if (released) return generation;
            if (webSocket == null && targetUrl == null && (state == State.IDLE || state == State.STOPPED)) return generation;
            socket = webSocket;
            webSocket = null;
            stoppedUrl = targetUrl;
            targetUrl = null;
            cancelScheduledLocked();
            retryAttempt = 0;
            generation++;
            stoppedGeneration = generation;
            state = State.STOPPED;
        }
        closeSocket(socket, reason);
        notifyState(State.STOPPED, stoppedGeneration, stoppedUrl, NORMAL_CLOSE_CODE, safeReason(reason));
        return stoppedGeneration;
    }

    public void release() {
        WebSocket socket;
        long releasedGeneration;
        String releasedUrl;
        synchronized (this) {
            if (released) return;
            socket = webSocket;
            webSocket = null;
            releasedUrl = targetUrl;
            targetUrl = null;
            cancelScheduledLocked();
            generation++;
            releasedGeneration = generation;
            released = true;
            state = State.RELEASED;
        }
        closeSocket(socket, "release");
        if (networkMonitor != null) networkMonitor.stop();
        client.release();
        scheduler.shutdownNow();
        notifyState(State.RELEASED, releasedGeneration, releasedUrl, NORMAL_CLOSE_CODE, "release");
    }

    public void retryNow(String reason) {
        long retryGeneration;
        String sourceUrl;
        synchronized (this) {
            if (released || state != State.RETRY_WAIT || targetUrl == null) return;
            cancelRetryLocked();
            generation++;
            retryGeneration = generation;
            sourceUrl = targetUrl;
            state = State.CONNECTING;
        }
        notifyState(State.CONNECTING, retryGeneration, sourceUrl, -1, safeReason(reason));
        openSocket(retryGeneration, sourceUrl);
    }

    public synchronized void markMessageAccepted(long callbackGeneration) {
        if (released || generation != callbackGeneration || state != State.OPEN) return;
        retryAttempt = 0;
        cancelStableLocked();
    }

    public synchronized State state() {
        return state;
    }

    public synchronized long generation() {
        return generation;
    }

    private void openSocket(long callbackGeneration, String sourceUrl) {
        WebSocket created;
        try {
            created = client.newWebSocket(sourceUrl, new SessionListener(callbackGeneration, sourceUrl));
        } catch (Throwable error) {
            handleTermination(null, callbackGeneration, sourceUrl, -1, errorName(error), LiveDanmakuRetryPolicy.shouldRetryFailure(error, -1));
            return;
        }
        synchronized (this) {
            if (released || generation != callbackGeneration || state != State.CONNECTING) {
                created.cancel();
            } else if (webSocket == null) {
                webSocket = created;
            } else if (webSocket != created) {
                created.cancel();
            }
        }
    }

    private boolean markOpen(WebSocket socket, long callbackGeneration) {
        synchronized (this) {
            if (released || generation != callbackGeneration || state != State.CONNECTING) return false;
            if (webSocket != null && webSocket != socket) return false;
            webSocket = socket;
            state = State.OPEN;
            cancelStableLocked();
            stableFuture = scheduler.schedule(() -> resetAfterStable(callbackGeneration), STABLE_RESET_MS, TimeUnit.MILLISECONDS);
            return true;
        }
    }

    private boolean isCurrent(WebSocket socket, long callbackGeneration) {
        synchronized (this) {
            return !released && generation == callbackGeneration && socket == webSocket;
        }
    }

    private void handleTermination(WebSocket socket, long callbackGeneration, String sourceUrl, int code, String detail, boolean retryable) {
        long delayMs = -1L;
        State nextState;
        synchronized (this) {
            if (released || generation != callbackGeneration) return;
            if (webSocket != null && socket != webSocket) return;
            webSocket = null;
            cancelStableLocked();
            if (retryable && sourceUrl.equals(targetUrl)) {
                delayMs = LiveDanmakuRetryPolicy.nextDelayMs(retryAttempt++, ThreadLocalRandom.current().nextDouble());
                state = State.RETRY_WAIT;
                nextState = State.RETRY_WAIT;
                scheduleRetryLocked(callbackGeneration, sourceUrl, delayMs);
            } else {
                state = State.STOPPED;
                nextState = State.STOPPED;
            }
        }
        String stateDetail = delayMs < 0 ? detail : detail + " retry_ms=" + delayMs;
        notifyState(nextState, callbackGeneration, sourceUrl, code, stateDetail);
    }

    private void scheduleRetryLocked(long expectedGeneration, String sourceUrl, long delayMs) {
        cancelRetryLocked();
        retryFuture = scheduler.schedule(() -> runRetry(expectedGeneration, sourceUrl), delayMs, TimeUnit.MILLISECONDS);
    }

    private void runRetry(long expectedGeneration, String sourceUrl) {
        long retryGeneration;
        synchronized (this) {
            if (released || generation != expectedGeneration || state != State.RETRY_WAIT || !sourceUrl.equals(targetUrl)) return;
            retryFuture = null;
            generation++;
            retryGeneration = generation;
            state = State.CONNECTING;
        }
        notifyState(State.CONNECTING, retryGeneration, sourceUrl, -1, "retry");
        openSocket(retryGeneration, sourceUrl);
    }

    private synchronized void resetAfterStable(long expectedGeneration) {
        if (released || generation != expectedGeneration || state != State.OPEN) return;
        retryAttempt = 0;
        stableFuture = null;
    }

    private void cancelScheduledLocked() {
        cancelRetryLocked();
        cancelStableLocked();
    }

    private void cancelRetryLocked() {
        if (retryFuture != null) retryFuture.cancel(false);
        retryFuture = null;
    }

    private void cancelStableLocked() {
        if (stableFuture != null) stableFuture.cancel(false);
        stableFuture = null;
    }

    private static void closeSocket(WebSocket socket, String reason) {
        if (socket != null && !socket.close(NORMAL_CLOSE_CODE, safeReason(reason))) socket.cancel();
    }

    private void notifyState(State newState, long callbackGeneration, String sourceUrl, int code, String detail) {
        listener.onStateChanged(newState, callbackGeneration, sourceUrl, code, detail);
    }

    private static String safeReason(String reason) {
        if (reason == null || reason.isBlank()) return "stop";
        return reason.length() <= 48 ? reason : reason.substring(0, 48);
    }

    private static String errorName(Throwable error) {
        return error == null ? "unknown" : error.getClass().getSimpleName();
    }

    private final class SessionListener extends WebSocketListener {

        private final long callbackGeneration;
        private final String sourceUrl;

        private SessionListener(long callbackGeneration, String sourceUrl) {
            this.callbackGeneration = callbackGeneration;
            this.sourceUrl = sourceUrl;
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            if (markOpen(webSocket, callbackGeneration)) notifyState(State.OPEN, callbackGeneration, sourceUrl, response.code(), "open");
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            if (isCurrent(webSocket, callbackGeneration)) listener.onMessage(callbackGeneration, text);
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            if (isCurrent(webSocket, callbackGeneration)) webSocket.close(code, safeReason(reason));
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            handleTermination(webSocket, callbackGeneration, sourceUrl, code, safeReason(reason), LiveDanmakuRetryPolicy.shouldRetryClose(code));
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable throwable, @Nullable Response response) {
            int httpCode = response == null ? -1 : response.code();
            handleTermination(webSocket, callbackGeneration, sourceUrl, httpCode, errorName(throwable), LiveDanmakuRetryPolicy.shouldRetryFailure(throwable, httpCode));
        }
    }

    public enum State {
        IDLE,
        CONNECTING,
        OPEN,
        RETRY_WAIT,
        STOPPED,
        RELEASED
    }

    public interface Listener {

        void onStateChanged(State state, long generation, String url, int code, String detail);

        void onMessage(long generation, String text);
    }
}
