package com.fongmi.android.tv.server;

import com.fongmi.android.tv.bean.Device;

import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.ConnectionSpec;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.TlsVersion;

public final class LanClient {
    private static final Pattern UUID = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final Pattern CREDENTIAL = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern CODE = Pattern.compile("[0-9]{8}");
    private static final MediaType TEXT = MediaType.get("text/plain");

    private LanClient() {
    }

    public static boolean paired(String targetUuid) {
        if (!validId(targetUuid)) return false;
        LanSenderCredentials store = LanSenderCredentialStore.get();
        synchronized (store) {
            return CREDENTIAL.matcher(store.get(targetUuid)).matches()
                    && !LanTlsIdentity.normalizePin(store.fingerprint(targetUuid)).isEmpty();
        }
    }

    public static String discoverFingerprint(String targetHttpBase) throws IOException {
        HttpUrl target = controlUrl(targetHttpBase, null, LanTlsPolicy.INFO_PATH);
        return LanTlsIdentity.probe(target.host(), target.port());
    }

    public static PairResult pair(String targetHttpBase, String targetUuid, String verifiedFingerprint, String code) {
        String device = Device.identityUuid();
        String pin = LanTlsIdentity.normalizePin(verifiedFingerprint);
        if (pin.isEmpty()) return failure("Verify the receiver fingerprint first");
        if (!validId(device) || !validId(targetUuid) || code == null || !CODE.matcher(code).matches()) {
            return failure("Invalid pairing details");
        }
        try {
            HttpUrl info = controlUrl(targetHttpBase, null, LanTlsPolicy.INFO_PATH);
            OkHttpClient client = pinnedClient(info.host(), pin, LanTlsPolicy.HEADER_TIMEOUT_MS,
                    LanTlsPolicy.HEADER_TIMEOUT_MS, LanTlsPolicy.HEADER_TIMEOUT_MS, LanTlsPolicy.READ_TIMEOUT_MS);
            try (Response response = client.newCall(new Request.Builder().url(info).build()).execute()) {
                if (response.code() != 200 || !targetUuid.equals(readAscii(response.body(), 128))) {
                    return failure("Receiver device identity did not match");
                }
            }
            HttpUrl exchange = info.newBuilder().encodedPath(LanTlsPolicy.PAIR_PATH).build();
            Request request = new Request.Builder().url(exchange)
                    .headers(Headers.of(LanClientHeaders.of(device, "")))
                    .post(RequestBody.create(code.getBytes(StandardCharsets.US_ASCII), TEXT)).build();
            try (Response response = client.newCall(request).execute()) {
                if (response.code() != 200) return failure("Receiver refused pairing");
                String credential = readAscii(response.body(), 43);
                if (!CREDENTIAL.matcher(credential).matches()) return failure("Invalid pairing response");
                try {
                    LanSenderCredentialStore.get().put(targetUuid, credential, pin);
                } catch (Exception ignored) {
                    return failure("Could not save receiver pairing");
                }
                return new PairResult(true, "Paired");
            }
        } catch (IOException | RuntimeException ignored) {
            return failure("Secure connection to receiver failed; verify its fingerprint");
        }
    }

    public static Call newCall(OkHttpClient timeoutProfile, String legacyActionUrl, String targetUuid, RequestBody body) throws IOException {
        HttpUrl target = controlUrl(legacyActionUrl, LanTlsPolicy.ACTION_PATH, LanTlsPolicy.ACTION_PATH);
        String device = Device.identityUuid();
        if (!validId(device) || !validId(targetUuid)) throw new IOException("LAN device identity unavailable");
        LanSenderCredentials store = LanSenderCredentialStore.get();
        String pin;
        String credential;
        synchronized (store) {
            pin = LanTlsIdentity.normalizePin(store.fingerprint(targetUuid));
            credential = store.get(targetUuid);
        }
        if (pin.isEmpty() || !CREDENTIAL.matcher(credential).matches()) throw new IOException("Pair with the receiver first");
        int connect = bound(timeoutProfile == null ? 0 : timeoutProfile.connectTimeoutMillis(), LanTlsPolicy.HEADER_TIMEOUT_MS);
        int read = bound(timeoutProfile == null ? 0 : timeoutProfile.readTimeoutMillis(), LanTlsPolicy.TRANSFER_TIMEOUT_MS);
        int write = bound(timeoutProfile == null ? 0 : timeoutProfile.writeTimeoutMillis(), LanTlsPolicy.TRANSFER_TIMEOUT_MS);
        int call = bound(timeoutProfile == null ? 0 : timeoutProfile.callTimeoutMillis(), LanTlsPolicy.TRANSFER_TIMEOUT_MS);
        OkHttpClient client = pinnedClient(target.host(), pin, connect, read, write, call);
        Map<String, String> headers = LanClientHeaders.of(device, credential);
        Request.Builder request = new Request.Builder().url(target).headers(Headers.of(headers));
        return client.newCall((body == null ? request.get() : request.post(body)).build());
    }

    static OkHttpClient pinnedClient(String host, String pin, int connect, int read, int write, int call) throws IOException {
        X509TrustManager trust = LanTlsIdentity.pinned(pin);
        try {
            return new OkHttpClient.Builder().sslSocketFactory(LanTlsIdentity.clientContext(trust).getSocketFactory(), trust)
                    .hostnameVerifier((actual, session) -> LanTlsIdentity.matchesHostAndPin(host, actual, pin, session))
                    .proxy(Proxy.NO_PROXY)
                    .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                    .connectionSpecs(Collections.singletonList(new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                            .tlsVersions(TlsVersion.TLS_1_2).build()))
                    .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
                    .connectTimeout(connect, TimeUnit.MILLISECONDS).readTimeout(read, TimeUnit.MILLISECONDS)
                    .writeTimeout(write, TimeUnit.MILLISECONDS).callTimeout(call, TimeUnit.MILLISECONDS).build();
        } catch (GeneralSecurityException e) {
            throw new IOException("Secure LAN transport unavailable");
        }
    }

    private static HttpUrl controlUrl(String value, String originalPath, String securePath) throws IOException {
        HttpUrl legacy = value == null ? null : HttpUrl.parse(value);
        if (legacy == null || !"http".equals(legacy.scheme()) || legacy.port() < LanTlsPolicy.FIRST_HTTP_PORT
                || legacy.port() > LanTlsPolicy.LAST_HTTP_PORT || !legacy.encodedUsername().isEmpty()
                || !legacy.encodedPassword().isEmpty() || legacy.fragment() != null
                || (originalPath == null ? !"/".equals(legacy.encodedPath()) || legacy.encodedQuery() != null
                    : !originalPath.equals(legacy.encodedPath()))) {
            throw new IOException("Invalid LAN receiver address");
        }
        return legacy.newBuilder().scheme("https").port(legacy.port() + LanTlsPolicy.PORT_OFFSET).encodedPath(securePath).build();
    }

    private static boolean validId(String value) {
        return value != null && !"0000000000000000".equals(value) && UUID.matcher(value).matches();
    }

    private static String readAscii(ResponseBody body, int max) throws IOException {
        if (body == null || body.contentLength() > max) throw new IOException("Invalid LAN response");
        InputStream stream = body.byteStream();
        byte[] bytes = new byte[max];
        int count = 0;
        int value;
        while ((value = stream.read()) != -1) {
            if (count == max || value < 33 || value > 126) throw new IOException("Invalid LAN response");
            bytes[count++] = (byte) value;
        }
        return new String(bytes, 0, count, StandardCharsets.US_ASCII);
    }

    private static int bound(int configured, int max) {
        return configured == 0 ? max : Math.min(configured, max);
    }

    private static PairResult failure(String message) {
        return new PairResult(false, message);
    }

    public static final class PairResult {
        public final boolean success;
        public final String message;

        private PairResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean success() { return success; }
        public String message() { return message; }
    }

    static void resetForTest() {
        LanSenderCredentialStore.reset();
    }
}
