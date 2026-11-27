package com.fongmi.android.tv.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Loopback mutual-TLS channel with an explicit peer certificate pin.
 *
 * <p>Not wired into the app or the existing HTTP listener. It proves the transport the pairing
 * flow will rely on; the platform trust store is deliberately not consulted.
 */
public final class LanSecureChannel {

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final int MAX_LINE = 512;

    private LanSecureChannel() {
    }

    /** SHA-256 of the DER certificate, or null when the platform lacks the digest. */
    public static String pinOf(byte[] der) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(der));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Server side: presents its own certificate, completes the handshake and only then hands the
     * socket to the caller. A peer whose certificate is not pinned never reaches application data.
     */
    public static void serve(int port, KeyStore keys, char[] password, String expectedPin, Listener listener) throws Exception {
        SSLContext context = context(keys, password, expectedPin);
        try (java.net.ServerSocket raw = new java.net.ServerSocket()) {
            raw.setReuseAddress(true);
            raw.bind(new InetSocketAddress("127.0.0.1", port), 1);
            try (Socket accepted = raw.accept();
                 SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket(accepted, "loopback", port, true)) {
                configure(socket, false);
                socket.startHandshake();
                listener.accepted(socket, readLine(socket.getInputStream()));
            }
        }
    }

    /** Client side: trusts only the pinned server certificate. */
    public static String call(int port, KeyStore keys, char[] password, String expectedPin, String line) throws Exception {
        SSLContext context = context(keys, password, expectedPin);
        try (Socket raw = new Socket()) {
            raw.connect(new InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT_MS);
            try (SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket(raw, "loopback", port, true)) {
                configure(socket, true);
                socket.startHandshake();
                writeLine(socket.getOutputStream(), line);
                return readLine(socket.getInputStream());
            }
        }
    }

    public interface Listener {
        void accepted(SSLSocket socket, String line) throws Exception;
    }

    public static String readLine(InputStream input) throws IOException {
        StringBuilder value = new StringBuilder();
        int c;
        while ((c = input.read()) != -1 && c != '\n') {
            value.append((char) c);
            if (value.length() > MAX_LINE) throw new IOException("line too long");
        }
        return value.toString().trim();
    }

    public static void writeLine(OutputStream output, String line) throws IOException {
        output.write((line + "\n").getBytes(StandardCharsets.US_ASCII));
        output.flush();
    }

    private static void configure(SSLSocket socket, boolean client) throws IOException {
        socket.setUseClientMode(client);
        socket.setSoTimeout(READ_TIMEOUT_MS);
        javax.net.ssl.SSLParameters parameters = socket.getSSLParameters();
        parameters.setNeedClientAuth(!client);
        parameters.setEndpointIdentificationAlgorithm(null);
        socket.setSSLParameters(parameters);
    }

    private static SSLContext context(KeyStore keys, char[] password, String expectedPin) throws Exception {
        KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(keys, password);
        TrustManager[] trust = {new PinnedPin(expectedPin)};
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(managers.getKeyManagers(), trust, null);
        return context;
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format("%02x", value & 0xff));
        return out.toString();
    }

    /** Rejects unless the presented leaf certificate matches the expected pin. */
    private static final class PinnedPin implements X509TrustManager {

        private final String expectedPin;

        private PinnedPin(String expectedPin) {
            this.expectedPin = expectedPin;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            verify(chain);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            verify(chain);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        private void verify(X509Certificate[] chain) throws CertificateException {
            if (chain == null || chain.length == 0) throw new CertificateException("missing peer certificate");
            try {
                String actual = pinOf(chain[0].getEncoded());
                if (expectedPin == null || !expectedPin.equals(actual)) throw new CertificateException("peer pin mismatch");
            } catch (CertificateException e) {
                throw e;
            } catch (Exception e) {
                throw new CertificateException("peer certificate unreadable", e);
            }
        }
    }
}