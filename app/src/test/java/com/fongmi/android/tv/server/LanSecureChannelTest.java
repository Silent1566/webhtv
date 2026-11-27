package com.fongmi.android.tv.server;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LanSecureChannelTest {

    @Rule public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void pinnedPeersReachTheHandlerAndAnUnpinnedPeerNeverDoes() throws Exception {
        KeyStore server = load("a");
        KeyStore client = load("b");
        KeyStore intruder = load("attacker");
        String serverPin = LanSecureChannel.pinOf(server.getCertificate("a").getEncoded());
        String clientPin = LanSecureChannel.pinOf(client.getCertificate("b").getEncoded());
        String intruderPin = LanSecureChannel.pinOf(intruder.getCertificate("attacker").getEncoded());
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

    private KeyStore load(String name) throws Exception {
        return generate(temp.getRoot().toPath(), name);
    }

    static KeyStore generate(Path directory, String name) throws Exception {
        Path storePath = directory.resolve(name + ".p12");
        String executable = System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool";
        String keytool = Paths.get(System.getProperty("java.home"), "bin", executable).toString();
        Process process = new ProcessBuilder(keytool, "-genkeypair", "-noprompt", "-alias", name,
                "-keyalg", "RSA", "-keysize", "2048", "-validity", "2", "-dname", "CN=" + name,
                "-storetype", "PKCS12", "-storepass", "changeit", "-keystore", storePath.toString())
                .redirectErrorStream(true).start();
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("keytool fixture timed out");
        }
        assertEquals("keytool fixture failed", 0, process.exitValue());
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(storePath)) {
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
