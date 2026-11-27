package com.fongmi.android.tv.server;

import com.fongmi.android.tv.bean.Device;
import com.github.catvod.net.OkHttp;

import java.io.IOException;
import java.util.Map;

import okhttp3.FormBody;
import okhttp3.Response;

/**
 * First-party client side of the LAN gate: attaches identity headers and performs the one-time
 * pairing exchange. Callers use {@link #headers(String)} with the target device uuid.
 */
public final class LanClient {

    private LanClient() {
    }

    /** Headers for a request to the target; empty when this device has no id yet. */
    public static Map<String, String> headers(String targetUuid) {
        return LanClientHeaders.of(Device.identityUuid(), LanSenderCredentialStore.credentialFor(targetUuid));
    }

    public static boolean paired(String targetUuid) {
        return !LanSenderCredentialStore.credentialFor(targetUuid).isEmpty();
    }

    /**
     * Exchanges the code shown on the target for a credential and stores it.
     *
     * @return the stored credential, or null when the target refused (its body says why).
     */
    public static String pair(String targetBaseUrl, String targetUuid, String code) throws IOException {
        String base = targetBaseUrl == null ? "" : targetBaseUrl.replaceAll("/+$", "");
        String device = Device.identityUuid();
        if (base.isEmpty() || device.isEmpty() || code == null || code.trim().isEmpty()) return null;
        Map<String, String> headers = LanClientHeaders.of(device, "");
        String url = base + "/lan/pair?code=" + java.net.URLEncoder.encode(code.trim(), "UTF-8");
        try (Response response = OkHttp.newCall(url, headers, new FormBody.Builder().build()).execute()) {
            String body = response.body() == null ? "" : response.body().string().trim();
            if (!response.isSuccessful() || body.isEmpty()) return null;
            try {
                LanSenderCredentialStore.get().put(targetUuid, body);
            } catch (Exception storageFailure) {
                // A credential we cannot persist would work once and fail later; report failure now.
                return null;
            }
            return body;
        }
    }

    /** Test seam: forgets the cached store so the next call reloads from disk. */
    static void resetForTest() {
        LanSenderCredentialStore.reset();
    }
}
