package com.fongmi.android.tv.server;

import java.util.Map;

/**
 * Maps a LAN authorization decision to the HTTP answer the request should get.
 *
 * <p>Denials are distinguishable in the body (switch off vs not paired vs local write disabled)
 * because the first-party client shows that text to the user; they all share 403 so nothing can
 * mistake one of them for a success.
 */
public final class LanActionGate {

    public static final String HEADER_DEVICE = "x-webhtv-device";
    public static final String HEADER_CREDENTIAL = "x-webhtv-credential";
    private static final int ALLOWED = 200;

    private LanActionGate() {
    }

    public static Verdict decide(LanPeerAuthorization.Decision decision) {
        if (decision == null) return new Verdict(403, "peer not authorized");
        switch (decision) {
            case ALLOW:
                return new Verdict(ALLOWED, "");
            case DISABLED:
                return new Verdict(403, "LAN access disabled");
            case SENSITIVE_DISABLED:
                return new Verdict(403, "local API write disabled");
            case UNKNOWN_ACTION:
                return new Verdict(400, "unknown action");
            default:
                return new Verdict(403, "peer not authorized");
        }
    }

    /** Case-insensitive HTTP field lookup with a trimmed value; missing fields read as empty. */
    public static String header(Map<String, String> headers, String name) {
        if (headers == null || name == null) return "";
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().trim().equalsIgnoreCase(name)) {
                String value = entry.getValue();
                return value == null ? "" : value.trim();
            }
        }
        return "";
    }

    /** A request without an action is a no-op probe, not an unknown action. */
    public static boolean hasAction(String action) {
        return action != null && !action.trim().isEmpty();
    }

    public static final class Verdict {

        private final int status;
        private final String message;

        Verdict(int status, String message) {
            this.status = status;
            this.message = message;
        }

        public int status() {
            return status;
        }

        public String message() {
            return message;
        }

        public boolean allowed() {
            return status == ALLOWED;
        }
    }
}
