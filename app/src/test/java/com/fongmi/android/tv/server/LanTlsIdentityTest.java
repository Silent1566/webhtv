package com.fongmi.android.tv.server;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import static org.junit.Assert.*;

public class LanTlsIdentityTest {
    @Rule public final TemporaryFolder temp = new TemporaryFolder();

    @Test public void probeAndWrongPinNeverReachHandlerButVerifiedPinDoes() throws Exception {
        KeyStore keys = LanSecureChannelTest.generate(temp.getRoot().toPath(), "receiver");
        String realPin = LanSecureChannel.pinOf(keys.getCertificate("receiver").getEncoded());
        KeyManagerFactory manager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        manager.init(keys, "changeit".toCharArray());
        SSLContext serverContext = SSLContext.getInstance("TLS");
        serverContext.init(manager.getKeyManagers(), null, null);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (SSLServerSocket server = (SSLServerSocket) serverContext.getServerSocketFactory()
                .createServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setEnabledProtocols(new String[]{"TLSv1.2"});
            server.setSoTimeout(5000);
            String host = server.getInetAddress().getHostAddress();
            int port = server.getLocalPort();
            Future<Integer> probe = accept(executor, server);
            assertEquals(realPin, LanTlsIdentity.probe(host, port));
            assertEquals(-1, (int) probe.get(5, TimeUnit.SECONDS));

            Future<Integer> rejected = accept(executor, server);
            try (SSLSocket client = connect(host, port, "0".repeat(64))) {
                assertThrows(javax.net.ssl.SSLException.class, client::startHandshake);
            }
            assertEquals(-1, (int) rejected.get(5, TimeUnit.SECONDS));

            Future<Integer> accepted = accept(executor, server);
            try (SSLSocket client = connect(host, port, realPin)) {
                client.startHandshake();
                assertTrue(LanTlsIdentity.matchesHostAndPin(host, host, realPin, client.getSession()));
                assertFalse(LanTlsIdentity.matchesHostAndPin(host, "not-the-host", realPin, client.getSession()));
                client.getOutputStream().write(65);
                client.getOutputStream().flush();
            }
            assertEquals(65, (int) accepted.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private static Future<Integer> accept(ExecutorService executor, SSLServerSocket server) {
        return executor.submit(() -> {
            try (SSLSocket socket = (SSLSocket) server.accept()) {
                socket.setSoTimeout(5000);
                try {
                    socket.startHandshake();
                    return socket.getInputStream().read();
                } catch (java.io.IOException e) {
                    return -1;
                }
            }
        });
    }

    private static SSLSocket connect(String host, int port, String pin) throws Exception {
        Socket raw = new Socket();
        raw.connect(new InetSocketAddress(host, port), 5000);
        SSLSocket socket = (SSLSocket) LanTlsIdentity.clientContext(LanTlsIdentity.pinned(pin))
                .getSocketFactory().createSocket(raw, host, port, true);
        socket.setEnabledProtocols(new String[]{"TLSv1.2"});
        socket.setSoTimeout(5000);
        return socket;
    }
}
