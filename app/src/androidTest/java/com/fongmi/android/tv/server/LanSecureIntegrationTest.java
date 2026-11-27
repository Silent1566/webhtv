package com.fongmi.android.tv.server;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.event.ServerEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static org.junit.Assert.*;

/** Exercises the real AndroidKeyStore identity, TLS listener and production request facade. */
public class LanSecureIntegrationTest {
    private final AtomicInteger pushes = new AtomicInteger();
    private final AtomicInteger settings = new AtomicInteger();
    private final OkHttpClient timeouts = new OkHttpClient.Builder().build();
    private final Map<String, Map<String, ?>> saved = new HashMap<>();
    private String base;
    private String pin;
    private String id;

    @Before public void setUp() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        for (String name : new String[]{"lan_peer_registry", "lan_sender_credentials"}) {
            SharedPreferences prefs = app.getSharedPreferences(name, Context.MODE_PRIVATE);
            saved.put(name, new HashMap<>(prefs.getAll()));
            assertTrue(prefs.edit().clear().commit());
        }
        LanPeers.reset();
        LanSenderCredentialStore.reset();
        Server.get().start();
        long until = SystemClock.elapsedRealtime() + 15000;
        while (Server.get().getLanPort() == 0 && SystemClock.elapsedRealtime() < until) SystemClock.sleep(50);
        assertTrue("secure listener must become ready", Server.get().getLanPort() > 0);
        base = "http://127.0.0.1:" + (Server.get().getLanPort() - 1000);
        pin = Server.get().getLanFingerprint();
        id = Device.identityUuid();
        EventBus.getDefault().register(this);
    }

    @After public void tearDown() {
        EventBus.getDefault().unregister(this);
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        for (Map.Entry<String, Map<String, ?>> group : saved.entrySet()) {
            SharedPreferences.Editor edit = app.getSharedPreferences(group.getKey(), Context.MODE_PRIVATE).edit().clear();
            for (Map.Entry<String, ?> entry : group.getValue().entrySet()) {
                if (entry.getValue() instanceof String) edit.putString(entry.getKey(), (String) entry.getValue());
                else if (entry.getValue() instanceof Boolean) edit.putBoolean(entry.getKey(), (Boolean) entry.getValue());
            }
            assertTrue(edit.commit());
        }
        LanPeers.reset();
        LanSenderCredentialStore.reset();
    }

    @Subscribe public void onServerEvent(ServerEvent event) {
        if (event.type() == ServerEvent.Type.PUSH) pushes.incrementAndGet();
        if (event.type() == ServerEvent.Type.SETTING) settings.incrementAndGet();
    }

    @Test public void aStalledTlsHandshakeCannotBlockAnotherAuthorizedConnection() throws Exception {
        try (Socket stalled = new Socket("127.0.0.1", Server.get().getLanPort())) {
            long start = SystemClock.elapsedRealtime();
            try (Response response = pinned().newCall(new Request.Builder().url(tls("/lan/info")).build()).execute()) {
                assertEquals(200, response.code());
                assertEquals(id, response.body().string());
            }
            assertTrue("second TLS connection must not wait for the stalled accept handshake timeout", SystemClock.elapsedRealtime() - start < 4000);
        }
    }

    @Test public void plaintextSecretsAndUnauthorizedUploadsAreRejectedBeforeReadingBody() throws Exception {
        int httpPort = Server.get().getLanPort() - 1000;
        String denied = raw(httpPort, "POST /lan/pair?code=12345678 HTTP/1.1\r\nHost: localhost\r\nContent-Length: 1000000\r\nConnection: close\r\n\r\n", null);
        assertTrue("plaintext rejected without consuming declared body", denied.startsWith("HTTP/1.1 403"));
        try (Response response = pinned().newCall(new Request.Builder().url(tls("/action?do=apk"))
                .post(RequestBody.create(new byte[0], MediaType.get("application/octet-stream"))).build()).execute()) {
            assertEquals(403, response.code());
        }
        assertEquals(0, pushes.get());
    }

    @Test public void correctPairingEnablesOnlyItsDeviceAndRevocationSurvivesReload() throws Exception {
        assertFalse(LanClient.paired(id));
        assertFalse(LanClient.pair(base, id, pin, "12345678").success());
        LanPeers.get().setEnabled(true);
        String code = LanPeers.get().issuePairingCode();
        String wrong = pin.charAt(0) == '0' ? "1" + pin.substring(1) : "0" + pin.substring(1);
        assertFalse("wrong certificate pin must fail before code exchange", LanClient.pair(base, id, wrong, code).success());
        assertTrue("right pin still uses the unconsumed code", LanClient.pair(base, id, pin, code).success());
        assertTrue(LanClient.paired(id));
        for (int i = 0; i < 16; i++) {
            try (Response response = LanClient.newCall(timeouts, base + "/action?do=push", id,
                    new FormBody.Builder().add("url", "http://127.0.0.1/kn17-test.mp4").build()).execute()) {
                assertEquals(200, response.code());
            }
        }
        assertEquals(16, pushes.get());
        LanPeers.reset();
        LanSenderCredentialStore.reset();
        try (Response response = LanClient.newCall(timeouts, base + "/action?do=push", id,
                new FormBody.Builder().add("url", "http://127.0.0.1/kn17-test.mp4").build()).execute()) {
            assertEquals("reloaded paired registry authorizes", 200, response.code());
        }
        assertTrue(LanPeers.get().revoke(id));
        LanPeers.reset();
        try (Response response = LanClient.newCall(timeouts, base + "/action?do=push", id,
                new FormBody.Builder().add("url", "http://127.0.0.1/kn17-test.mp4").build()).execute()) {
            assertEquals(403, response.code());
        }
        assertEquals(17, pushes.get());
    }

    @Test public void gatedSelectorCannotBeReplacedByTheBodyAndSensitiveWritesNeedPermission() throws Exception {
        LanPeers.get().setEnabled(true);
        assertTrue(LanClient.pair(base, id, pin, LanPeers.get().issuePairingCode()).success());
        try (Response response = LanClient.newCall(timeouts, base + "/action?do=push", id,
                new FormBody.Builder().add("do", "setting").add("text", "ignored").build()).execute()) {
            assertEquals(400, response.code());
        }
        try (Response response = LanClient.newCall(timeouts, base + "/action?do=setting", id,
                new FormBody.Builder().add("text", "ignored").build()).execute()) {
            assertEquals(403, response.code());
        }
        LanPeerStore.setLocalWriteEnabled(true);
        try (Response response = LanClient.newCall(timeouts, base + "/action?do=setting", id,
                new FormBody.Builder().add("text", "kn17-verified").build()).execute()) {
            assertEquals(200, response.code());
        }
        assertEquals(1, settings.get());
        assertEquals(0, pushes.get());
    }

    @Test public void authenticatedBackupAndApkUploadReachTheirRealHandlers() throws Exception {
        LanPeers.get().setEnabled(true);
        LanPeerStore.setLocalWriteEnabled(true);
        assertTrue(LanClient.pair(base, id, pin, LanPeers.get().issuePairingCode()).success());
        String options = "{\"config\":false,\"spider\":false,\"search\":false,\"history\":false,"
                + "\"keep\":false,\"follow\":false,\"webHome\":false,\"settings\":false,\"loginState\":false}";
        try (Response response = LanClient.newCall(timeouts, base + "/action?do=sync&mode=1&type=backup", id,
                new FormBody.Builder().add("options", options).add("backup", "{}").build()).execute()) {
            assertEquals("authenticated empty backup is accepted without touching user data", 200, response.code());
        }
        Context fixture = InstrumentationRegistry.getInstrumentation().getContext();
        java.io.File apk = new java.io.File(fixture.getPackageCodePath());
        okhttp3.MultipartBody body = new okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("name", "kn17-test.apk").addFormDataPart("size", String.valueOf(apk.length()))
                .addFormDataPart("apk", "kn17-test.apk", RequestBody.create(apk, MediaType.get("application/vnd.android.package-archive")))
                .build();
        try (Response response = LanClient.newCall(timeouts, base + "/action?do=apk", id, body).execute()) {
            assertEquals("authenticated multipart APK accepted", 200, response.code());
            assertEquals("APK received", response.body().string());
        }
    }

    @Test public void crossDeviceProductionFacadeExchange() throws Exception {
        android.os.Bundle args = InstrumentationRegistry.getArguments();
        String role = args.getString("lanRole", "none");
        org.junit.Assume.assumeFalse("cross-device role is supplied by the bounded acceptance runner", "none".equals(role));
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        if ("receiver".equals(role)) {
            java.io.File ready = new java.io.File(app.getCacheDir(), "kn17-receiver-ready.json");
            java.io.File done = new java.io.File(app.getCacheDir(), "kn17-receiver-done");
            done.delete();
            LanPeers.get().setEnabled(true);
            LanPeerStore.setLocalWriteEnabled(true);
            org.json.JSONObject details = new org.json.JSONObject();
            details.put("id", id);
            details.put("pin", pin);
            details.put("port", Server.get().getLanPort());
            details.put("code", LanPeers.get().issuePairingCode());
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(ready)) {
                output.write(details.toString().getBytes(StandardCharsets.UTF_8));
            }
            try {
                long deadline = SystemClock.elapsedRealtime() + 100000;
                while (!done.exists() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
                assertTrue("sender must finish within the pairing window", done.exists());
                assertEquals("push traversed two real Android runtimes", 1, pushes.get());
                assertTrue("paired registry written on actual receiver", LanPeers.get().partners().size() == 1);
            } finally {
                ready.delete();
                done.delete();
            }
        } else {
            String target = args.getString("lanTarget");
            String targetId = args.getString("lanReceiverId");
            String verified = args.getString("lanReceiverPin");
            String code = args.getString("lanCode");
            assertNotNull(target);
            assertFalse(LanClient.paired(targetId));
            assertEquals("public certificate probe sends no application secrets", verified, LanClient.discoverFingerprint(target));
            if ("sender-ui".equals(role)) {
                java.util.concurrent.CountDownLatch pairedUi = new java.util.concurrent.CountDownLatch(1);
                android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
                android.content.Intent intent = new android.content.Intent();
                intent.setClassName(app, "com.fongmi.android.tv.ui.activity.HomeActivity");
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                androidx.fragment.app.FragmentActivity activity = (androidx.fragment.app.FragmentActivity) instrumentation.startActivitySync(intent);
                Device receiver = new Device(); receiver.setUuid(targetId); receiver.setIp(target); receiver.setName("KN17 receiver");
                instrumentation.runOnMainSync(() -> com.fongmi.android.tv.ui.dialog.LanRemotePairDialog.show(activity, receiver, pairedUi::countDown));
                assertTrue("explicit fingerprint confirmation and code must complete pairing", pairedUi.await(85, java.util.concurrent.TimeUnit.SECONDS));
            } else {
                assertTrue("user-verified receiver pairs over pinned TLS", LanClient.pair(target, targetId, verified, code).success());
            }
            LanSenderCredentialStore.reset();
            assertTrue("sender credential and pin reload together", LanClient.paired(targetId));
            try (Response response = LanClient.newCall(timeouts, target + "/action?do=push", targetId,
                    new FormBody.Builder().add("url", "http://127.0.0.1/kn17-cross-device.mp4").build()).execute()) {
                assertEquals(200, response.code());
            }
            String emptyOptions = "{\"config\":false,\"spider\":false,\"search\":false,\"history\":false,"
                    + "\"keep\":false,\"follow\":false,\"webHome\":false,\"settings\":false,\"loginState\":false}";
            try (Response response = LanClient.newCall(timeouts, target + "/action?do=sync&mode=1&type=backup", targetId,
                    new FormBody.Builder().add("options", emptyOptions).add("backup", "{}").build()).execute()) {
                assertEquals(200, response.code());
            }
            Context fixture = InstrumentationRegistry.getInstrumentation().getContext();
            java.io.File apk = new java.io.File(fixture.getPackageCodePath());
            okhttp3.MultipartBody upload = new okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
                    .addFormDataPart("name", "kn17-cross.apk").addFormDataPart("size", String.valueOf(apk.length()))
                    .addFormDataPart("apk", "kn17-cross.apk", RequestBody.create(apk, MediaType.get("application/vnd.android.package-archive"))).build();
            try (Response response = LanClient.newCall(timeouts, target + "/action?do=apk", targetId, upload).execute()) {
                assertEquals(200, response.code());
                assertEquals("APK received", response.body().string());
            }
        }
    }

    private OkHttpClient pinned() throws Exception {
        return LanClient.pinnedClient("127.0.0.1", pin, 5000, 5000, 5000, 10000);
    }
    private String tls(String path) { return "https://127.0.0.1:" + Server.get().getLanPort() + path; }

    private static String raw(int port, String header, byte[] body) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(header.getBytes(StandardCharsets.US_ASCII));
            if (body != null) socket.getOutputStream().write(body);
            socket.getOutputStream().flush();
            InputStream input = socket.getInputStream();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int value;
            while ((value = input.read()) != -1 && value != '\n' && output.size() < 200) output.write(value);
            return output.toString("US-ASCII");
        }
    }
}
