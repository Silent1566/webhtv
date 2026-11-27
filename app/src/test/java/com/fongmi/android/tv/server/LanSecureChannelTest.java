package com.fongmi.android.tv.server;

import org.junit.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LanSecureChannelTest {

    private static final Path WORK = Path.of(System.getProperty("kn17.channel.dir", "build/kn17-channel"));

    @Test
    public void pinnedPeersReachTheHandlerAndAnUnpinnedPeerNeverDoes() throws Exception {
        KeyStore server = load("a");
        KeyStore client = load("b");
        KeyStore intruder = load("attacker");
        String serverPin = LanSecureChannel.pinOf(Files.readAllBytes(WORK.resolve("a.der")));
        String clientPin = LanSecureChannel.pinOf(Files.readAllBytes(WORK.resolve("b.der")));
        String intruderPin = LanSecureChannel.pinOf(Files.readAllBytes(WORK.resolve("attacker.der")));
        assertNotEquals(serverPin, intruderPin);

        CompletableFuture<String> seen = new CompletableFuture<>();
        int port = freePort();
        Thread ta = serverThread(port, server, clientPin, seen);
        ta.start();
        Thread.sleep(250);
        assertEquals("ack", LanSecureChannel.call(port, client, pass(), serverPin, "hello"));
        assertEquals("hello", seen.get(5, TimeUnit.SECONDS));
        ta.join(5000);

        CompletableFuture<Boolean> blocked = new CompletableFuture<>();
        int badPort = freePort();
        Thread tb = new Thread(() -> {
            try {
                LanSecureChannel.serve(badPort, server, pass(), clientPin, (socket, line) -> fail("unpinned peer reached the handler"));
                blocked.complete(false);
            } catch (Exception expected) {
                blocked.complete(true);
            }
        }, "kn17-unpinned");
        tb.setDaemon(true);
        tb.start();
        Thread.sleep(250);
        try {
            LanSecureChannel.call(badPort, intruder, pass(), serverPin, "hello");
            fail("unpinned peer completed the exchange");
        } catch (Exception expected) {
            assertTrue(blocked.get(5, TimeUnit.SECONDS));
        }
        tb.join(5000);
    }

    private static Thread serverThread(int port, KeyStore keys, String clientPin, CompletableFuture<String> seen) {
        Thread thread = new Thread(() -> {
            try {
                LanSecureChannel.serve(port, keys, pass(), clientPin, (socket, line) -> {
                    seen.complete(line);
                    LanSecureChannel.writeLine(socket.getOutputStream(), "ack");
                });
            } catch (Exception e) {
                seen.completeExceptionally(e);
            }
        }, "kn17-pinned");
        thread.setDaemon(true);
        return thread;
    }

    private static char[] pass() {
        return "changeit".toCharArray();
    }

    private static KeyStore load(String name) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(WORK.resolve(name + ".p12"))) {
            store.load(input, pass());
        }
        return store;
    }

    private static int freePort() throws Exception {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }
}
