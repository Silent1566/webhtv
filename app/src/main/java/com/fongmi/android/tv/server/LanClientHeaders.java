package com.fongmi.android.tv.server;

import java.util.LinkedHashMap;
import java.util.Map;

/** Headers a first-party client must send so a paired target can identify it. */
public final class LanClientHeaders {

    private LanClientHeaders() {
    }

    /**
     * Always sends the device id (so a refusal can say "not paired" instead of "unknown"), and the
     * credential only when this device holds one for that target.
     */
    public static Map<String, String> of(String deviceUuid, String credential) {
        Map<String, String> headers = new LinkedHashMap<>();
        String device = trim(deviceUuid);
        if (device.isEmpty()) return headers;
        headers.put(LanActionGate.HEADER_DEVICE, device);
        String secret = trim(credential);
        if (!secret.isEmpty()) headers.put(LanActionGate.HEADER_CREDENTIAL, secret);
        return headers;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
