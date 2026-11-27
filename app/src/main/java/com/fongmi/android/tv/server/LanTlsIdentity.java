package com.fongmi.android.tv.server;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

final class LanTlsIdentity {
    static final String PROTOCOL = "TLSv1.2";
    private static final String PROVIDER = "AndroidKeyStore";
    private static final String ALIAS = "webhtv_lan_tls_receiver";
    private static final Pattern PIN = Pattern.compile("[0-9a-f]{64}");
    final SSLServerSocketFactory sockets;
    final String fingerprint;

    private LanTlsIdentity(SSLServerSocketFactory sockets, String fingerprint) {
        this.sockets = sockets;
        this.fingerprint = fingerprint;
    }

    static synchronized LanTlsIdentity load() throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance(PROVIDER);
        store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, PROVIDER);
            generator.initialize(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                    .setKeySize(2048)
                    // API 24–28 Conscrypt submits an already hashed TLS signature to NONEwithRSA.
                    .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256,
                            KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                    .setCertificateSubject(new X500Principal("CN=WebHTV LAN receiver"))
                    .build());
            generator.generateKeyPair();
        }
        if (!(store.getKey(ALIAS, null) instanceof PrivateKey) || !(store.getCertificate(ALIAS) instanceof X509Certificate)) {
            throw new GeneralSecurityException("LAN identity unavailable");
        }
        final PrivateKey key = (PrivateKey) store.getKey(ALIAS, null);
        final X509Certificate certificate = (X509Certificate) store.getCertificate(ALIAS);
        String fingerprint = fingerprint(certificate);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(new KeyManager[]{new X509KeyManager() {
            @Override public String[] getClientAliases(String type, Principal[] issuers) { return null; }
            @Override public String chooseClientAlias(String[] types, Principal[] issuers, Socket socket) { return null; }
            @Override public String[] getServerAliases(String type, Principal[] issuers) {
                return "RSA".equals(type) ? new String[]{ALIAS} : null;
            }
            @Override public String chooseServerAlias(String type, Principal[] issuers, Socket socket) {
                return "RSA".equals(type) ? ALIAS : null;
            }
            @Override public X509Certificate[] getCertificateChain(String alias) {
                return ALIAS.equals(alias) ? new X509Certificate[]{certificate} : null;
            }
            @Override public PrivateKey getPrivateKey(String alias) { return ALIAS.equals(alias) ? key : null; }
        }}, null, null);
        return new LanTlsIdentity(context.getServerSocketFactory(), fingerprint);
    }

    static String normalizePin(String value) {
        String pin = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return PIN.matcher(pin).matches() ? pin : "";
    }

    static X509TrustManager pinned(String expected) throws IOException {
        String pin = normalizePin(expected);
        if (pin.isEmpty()) throw new IOException("Receiver fingerprint verification required");
        return new ReceiverTrustManager() {
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                if (chain == null || chain.length == 0 || !pin.equals(fingerprint(chain[0]))) {
                    throw new CertificateException("Receiver certificate mismatch");
                }
            }
        };
    }

    static SSLContext clientContext(X509TrustManager trust) throws GeneralSecurityException {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{trust}, null);
        return context;
    }

    static boolean matchesHostAndPin(String expectedHost, String actualHost, String pin, SSLSession session) {
        if (!expectedHost.equalsIgnoreCase(actualHost)) return false;
        try {
            java.security.cert.Certificate[] chain = session.getPeerCertificates();
            return chain.length > 0 && chain[0] instanceof X509Certificate
                    && pin.equals(fingerprint((X509Certificate) chain[0]));
        } catch (SSLPeerUnverifiedException | CertificateException e) {
            return false;
        }
    }

    static String probe(String host, int port) throws IOException {
        AtomicReference<String> observed = new AtomicReference<>();
        X509TrustManager probe = new ReceiverTrustManager() {
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                if (chain != null && chain.length > 0) observed.set(fingerprint(chain[0]));
                throw new CertificateException("Unverified receiver certificate");
            }
        };
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), LanTlsPolicy.HEADER_TIMEOUT_MS);
            try (SSLSocket tls = (SSLSocket) clientContext(probe).getSocketFactory().createSocket(socket, host, port, true)) {
                tls.setEnabledProtocols(new String[]{PROTOCOL});
                tls.setSoTimeout(LanTlsPolicy.HEADER_TIMEOUT_MS);
                tls.startHandshake();
            }
        } catch (GeneralSecurityException | IOException e) {
            String pin = observed.get();
            if (pin != null) return pin;
        }
        throw new IOException("Could not read receiver fingerprint");
    }

    private static String fingerprint(X509Certificate certificate) throws CertificateException {
        String pin = LanSecureChannel.pinOf(certificate.getEncoded());
        if (pin == null) throw new CertificateException("Receiver fingerprint unavailable");
        return pin;
    }

    private abstract static class ReceiverTrustManager implements X509TrustManager {
        @Override public final void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("Client certificates not used");
        }
        @Override public final X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    }
}
