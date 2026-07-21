package com.fongmi.android.tv.player.danmaku;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LiveDanmakuWebSocketIntegrationTest {

    private static final long TIMEOUT_SECONDS = 10L;

    private final List<LiveDanmakuWebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final List<MockWebServer> servers = new CopyOnWriteArrayList<>();

    @After
    public void tearDown() throws IOException {
        for (LiveDanmakuWebSocketSession session : sessions) session.release();
        for (MockWebServer server : servers) server.close();
    }

    @Test
    public void opensReceivesTextAndCompletesNormalClose() throws Exception {
        MockWebServer server = serverWithSocket(new ClosingWebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                webSocket.send("hello");
                webSocket.close(1000, "complete");
            }
        });
        RecordingListener listener = new RecordingListener(1, 1);
        LiveDanmakuWebSocketSession session = session(listener);

        long generation = session.connect(webSocketUrl(server));

        assertTrue(listener.open.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertTrue(listener.messagesDone.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertTrue(listener.stopped.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals(List.of("hello"), listener.messages);
        assertEquals(List.of(generation), listener.messageGenerations);
        assertEquals(LiveDanmakuWebSocketSession.State.STOPPED, session.state());
        assertTrue(listener.states.contains(LiveDanmakuWebSocketSession.State.OPEN));
    }

    @Test
    public void deliversOneThousandMessageBurstWithoutConnectionLayerLoss() throws Exception {
        MockWebServer server = serverWithSocket(new ClosingWebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                for (int i = 0; i < 1_000; i++) assertTrue(webSocket.send("message-" + i));
            }
        });
        RecordingListener listener = new RecordingListener(1, 1_000);
        LiveDanmakuWebSocketSession session = session(listener);

        long generation = session.connect(webSocketUrl(server));

        assertTrue(listener.open.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertTrue(listener.messagesDone.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals(1_000, listener.messages.size());
        assertEquals("message-0", listener.messages.get(0));
        assertEquals("message-999", listener.messages.get(999));
        assertTrue(listener.messageGenerations.stream().allMatch(value -> value == generation));
    }

    @Test
    public void switchingRoomRejectsMessagesFromReplacedSocket() throws Exception {
        CountDownLatch sendLateMessage = new CountDownLatch(1);
        MockWebServer firstServer = serverWithSocket(new ClosingWebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Thread sender = new Thread(() -> {
                    try {
                        if (sendLateMessage.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) webSocket.send("old-room-late");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, "old-room-test-sender");
                sender.setDaemon(true);
                sender.start();
            }
        });
        MockWebServer secondServer = serverWithSocket(new ClosingWebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                webSocket.send("new-room");
            }
        });
        RecordingListener listener = new RecordingListener(2, 1);
        LiveDanmakuWebSocketSession session = session(listener);

        long firstGeneration = session.connect(webSocketUrl(firstServer));
        assertTrue(listener.open.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        long secondGeneration = session.connect(webSocketUrl(secondServer));
        assertTrue(secondGeneration > firstGeneration);
        assertTrue(listener.secondOpen.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertTrue(listener.messagesDone.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        sendLateMessage.countDown();
        Thread.sleep(250L);

        assertEquals(List.of("new-room"), listener.messages);
        assertEquals(List.of(secondGeneration), listener.messageGenerations);
    }

    @Test
    public void userStopPreventsReconnectAfterSocketFailure() throws Exception {
        MockWebServer server = serverWithSocket(new ClosingWebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                webSocket.cancel();
            }
        });
        RecordingListener listener = new RecordingListener(1, 0);
        LiveDanmakuWebSocketSession session = session(listener);

        session.connect(webSocketUrl(server));
        assertTrue(listener.retryWait.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        int requestCountAtStop = server.getRequestCount();
        session.stop("user_stop");
        Thread.sleep(1_250L);

        assertEquals(LiveDanmakuWebSocketSession.State.STOPPED, session.state());
        assertEquals(requestCountAtStop, server.getRequestCount());
    }

    @Test
    public void temporaryHandshakeFailureEntersRetryWait() throws Exception {
        MockWebServer server = server(new MockResponse.Builder().code(503).body("temporarily unavailable").build());
        RecordingListener listener = new RecordingListener(0, 0);
        LiveDanmakuWebSocketSession session = session(listener);

        session.connect(webSocketUrl(server));

        assertTrue(listener.retryWait.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertTrue(listener.states.contains(LiveDanmakuWebSocketSession.State.RETRY_WAIT));
        assertTrue(listener.retryDetail.contains("retry_ms="));
        session.stop("test_complete");
        assertFalse(listener.states.isEmpty());
    }

    private LiveDanmakuWebSocketSession session(RecordingListener listener) {
        LiveDanmakuWebSocketSession session = new LiveDanmakuWebSocketSession(listener);
        sessions.add(session);
        return session;
    }

    private MockWebServer serverWithSocket(WebSocketListener listener) throws IOException {
        return server(new MockResponse.Builder().webSocketUpgrade(listener).build());
    }

    private MockWebServer server(MockResponse response) throws IOException {
        MockWebServer server = new MockWebServer();
        server.enqueue(response);
        server.start();
        servers.add(server);
        return server;
    }

    private static String webSocketUrl(MockWebServer server) {
        return server.url("/live").toString().replaceFirst("^http", "ws");
    }

    private abstract static class ClosingWebSocketListener extends WebSocketListener {

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            webSocket.close(code, reason);
        }
    }

    private static final class RecordingListener implements LiveDanmakuWebSocketSession.Listener {

        private final CountDownLatch open;
        private final CountDownLatch secondOpen;
        private final CountDownLatch messagesDone;
        private final CountDownLatch retryWait = new CountDownLatch(1);
        private final CountDownLatch stopped = new CountDownLatch(1);
        private final List<LiveDanmakuWebSocketSession.State> states = new CopyOnWriteArrayList<>();
        private final List<String> messages = new CopyOnWriteArrayList<>();
        private final List<Long> messageGenerations = new CopyOnWriteArrayList<>();
        private volatile String retryDetail = "";
        private int openCount;

        private RecordingListener(int expectedOpenCount, int expectedMessages) {
            open = new CountDownLatch(Math.min(expectedOpenCount, 1));
            secondOpen = new CountDownLatch(expectedOpenCount >= 2 ? 1 : 0);
            messagesDone = new CountDownLatch(expectedMessages);
        }

        @Override
        public synchronized void onStateChanged(LiveDanmakuWebSocketSession.State state, long generation, String url, int code, String detail) {
            states.add(state);
            if (state == LiveDanmakuWebSocketSession.State.OPEN) {
                openCount++;
                open.countDown();
                if (openCount >= 2) secondOpen.countDown();
            } else if (state == LiveDanmakuWebSocketSession.State.RETRY_WAIT) {
                retryDetail = detail == null ? "" : detail;
                retryWait.countDown();
            } else if (state == LiveDanmakuWebSocketSession.State.STOPPED) {
                stopped.countDown();
            }
        }

        @Override
        public void onMessage(long generation, String text) {
            messageGenerations.add(generation);
            messages.add(text);
            messagesDone.countDown();
        }
    }
}
