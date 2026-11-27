package com.fongmi.android.tv.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/** In-memory LAN policy. A device ID identifies an entry but does not authenticate it. */
public final class LanPeerAuthorization {

    private static final long PAIRING_TTL_MS = 120_000;
    private static final int PAIRING_ATTEMPTS = 5;
    private static final int CREDENTIAL_BYTES = 32;
    private static final int CREDENTIAL_LENGTH = 43;
    private static final String UNAVAILABLE_ANDROID_ID = "0000000000000000";
    private static final Pattern DEVICE_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final LongSupplier elapsedMillis;
    private final Map<String, byte[]> paired = new HashMap<>();
    private boolean enabled;
    private String pairingCode;
    private long issuedAt;
    private int attemptsLeft;

    public LanPeerAuthorization(LongSupplier elapsedMillis) {
        this.elapsedMillis = elapsedMillis;
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) pairingCode = null;
    }

    /** The receiver's local UI issues a code; the network caller cannot issue one. */
    public synchronized String issuePairingCode() {
        if (!enabled) return null;
        String next;
        do {
            next = String.format(Locale.ROOT, "%08d", RANDOM.nextInt(100_000_000));
        } while (next.equals(pairingCode));
        pairingCode = next;
        issuedAt = elapsedMillis.getAsLong();
        attemptsLeft = PAIRING_ATTEMPTS;
        return next;
    }

    public synchronized String pair(String uuid, String code) {
        if (!enabled || pairingCode == null) return null;
        long age = elapsedMillis.getAsLong() - issuedAt;
        if (age < 0 || age >= PAIRING_TTL_MS) {
            pairingCode = null;
            return null;
        }
        if (!isValidDeviceId(uuid) || !matchesCode(code)) {
            if (--attemptsLeft == 0) pairingCode = null;
            return null;
        }
        if (!paired.containsKey(uuid) && paired.size() >= LanPeerRegistry.MAX_PARTNERS) return null;
        pairingCode = null;
        byte[] secret = new byte[CREDENTIAL_BYTES];
        RANDOM.nextBytes(secret);
        try {
            String credential = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
            paired.put(uuid, digest(credential));
            return credential;
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    public synchronized boolean revoke(String uuid) {
        return paired.remove(uuid) != null;
    }

    /** Defensive copy of the digest registry, for persistence only. */
    public synchronized Map<String, byte[]> partners() {
        Map<String, byte[]> copy = new HashMap<>();
        for (Map.Entry<String, byte[]> entry : paired.entrySet()) copy.put(entry.getKey(), entry.getValue().clone());
        return copy;
    }

    /** Replaces the registry; empty, missing or oversized input clears existing authorizations. */
    public synchronized void restore(Map<String, byte[]> partners) {
        if (partners == null || partners.isEmpty() || partners.size() > LanPeerRegistry.MAX_PARTNERS) {
            paired.clear();
            return;
        }
        Map<String, byte[]> next = new HashMap<>();
        for (Map.Entry<String, byte[]> entry : partners.entrySet()) {
            if (!LanPeerRegistry.valid(entry.getKey(), entry.getValue())) continue;
            next.put(entry.getKey(), entry.getValue().clone());
        }
        paired.clear();
        paired.putAll(next);
    }

    public synchronized Decision authorize(String action, String uuid, String credential, boolean sensitiveEnabled) {
        if (action == null) return Decision.UNKNOWN_ACTION;
        boolean sensitive;
        switch (action) {
            case "file", "setting", "sync", "apk", "apk_url" -> sensitive = true;
            case "push", "cast", "control", "refresh", "search", "danmaku" -> sensitive = false;
            default -> { return Decision.UNKNOWN_ACTION; }
        }
        if (!enabled) return Decision.DISABLED;
        if (!isValidDeviceId(uuid) || credential == null || credential.length() != CREDENTIAL_LENGTH) return Decision.UNAUTHORIZED;
        byte[] expected = paired.get(uuid);
        if (expected == null || !MessageDigest.isEqual(expected, digest(credential))) return Decision.UNAUTHORIZED;
        if (sensitive && !sensitiveEnabled) return Decision.SENSITIVE_DISABLED;
        return Decision.ALLOW;
    }

    private boolean matchesCode(String code) {
        return code != null && code.length() == 8 && MessageDigest.isEqual(
                pairingCode.getBytes(StandardCharsets.US_ASCII), code.getBytes(StandardCharsets.UTF_8));
    }

    static boolean isValidDeviceId(String uuid) {
        return uuid != null && uuid.length() <= 128 && !UNAVAILABLE_ANDROID_ID.equals(uuid) && DEVICE_ID.matcher(uuid).matches();
    }

    private static byte[] digest(String credential) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(credential.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public enum Decision {
        ALLOW(200), DISABLED(403), UNAUTHORIZED(403), SENSITIVE_DISABLED(403), UNKNOWN_ACTION(400);

        private final int statusCode;

        Decision(int statusCode) {
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }
    }
}
