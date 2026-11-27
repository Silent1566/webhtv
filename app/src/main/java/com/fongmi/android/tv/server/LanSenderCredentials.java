package com.fongmi.android.tv.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Encrypted per-target sender credentials and explicitly verified receiver fingerprints. */
public final class LanSenderCredentials {

    private static final int VERSION = 1;
    private static final int MAX_TARGETS = 64;
    private static final Pattern CREDENTIAL = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");

    private final LanPeerPersist.SecretBox box;
    private final LanPeerPersist.ValueStore store;
    private final Map<String, Target> targets = new LinkedHashMap<>();

    private static final class Target {
        final String credential;
        final String fingerprint;

        Target(String credential, String fingerprint) {
            this.credential = credential;
            this.fingerprint = fingerprint;
        }
    }

    public LanSenderCredentials(LanPeerPersist.SecretBox box, LanPeerPersist.ValueStore store) {
        this.box = box;
        this.store = store;
        try {
            targets.putAll(decode(store.read()));
        } catch (Exception ignored) {
            targets.clear();
        }
    }

    public synchronized String get(String target) {
        Target value = targets.get(target);
        return value == null ? "" : value.credential;
    }

    public synchronized String fingerprint(String target) {
        Target value = targets.get(target);
        return value == null ? "" : value.fingerprint;
    }

    public synchronized boolean isEmpty() {
        return targets.isEmpty();
    }

    /** Compatibility writes are deliberately pinless and require re-pairing on protected transports. */
    public synchronized void put(String target, String credential) throws Exception {
        if (target == null || target.trim().isEmpty() || credential == null || credential.trim().isEmpty()) return;
        putCandidate(target, credential, "");
    }

    public synchronized void put(String target, String credential, String verifiedFingerprint) throws Exception {
        if (verifiedFingerprint == null || !FINGERPRINT.matcher(verifiedFingerprint).matches()) {
            throw new IllegalArgumentException("Invalid receiver fingerprint");
        }
        putCandidate(target, credential, verifiedFingerprint);
    }

    private void putCandidate(String target, String credential, String fingerprint) throws Exception {
        target = target == null ? "" : target.trim();
        credential = credential == null ? "" : credential.trim();
        if (!LanPeerAuthorization.isValidDeviceId(target) || !validCredential(credential)) {
            throw new IllegalArgumentException("Invalid LAN sender credential");
        }
        if (!targets.containsKey(target) && targets.size() >= MAX_TARGETS) throw new IllegalArgumentException("Too many LAN targets");
        Map<String, Target> next = new LinkedHashMap<>(targets);
        next.put(target, new Target(credential, fingerprint));
        persist(next);
        targets.clear();
        targets.putAll(next);
    }

    public synchronized void remove(String target) throws Exception {
        if (!targets.containsKey(target)) return;
        Map<String, Target> next = new LinkedHashMap<>(targets);
        next.remove(target);
        persist(next);
        targets.clear();
        targets.putAll(next);
    }

    private void persist(Map<String, Target> next) throws Exception {
        if (next.isEmpty()) {
            store.write("");
            return;
        }
        if (next.size() > MAX_TARGETS) throw new IllegalArgumentException("Too many LAN targets");
        JsonArray entries = new JsonArray();
        for (Map.Entry<String, Target> entry : next.entrySet()) {
            JsonObject item = new JsonObject();
            item.addProperty("id", entry.getKey());
            item.addProperty("credential", entry.getValue().credential);
            if (!entry.getValue().fingerprint.isEmpty()) item.addProperty("fingerprint", entry.getValue().fingerprint);
            entries.add(item);
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.add("targets", entries);
        String plain = root.toString();
        if (plain.length() > LanPeerRegistry.MAX_PLAIN_CHARS) throw new IllegalArgumentException("LAN targets too large");
        store.write(LanPeerPersist.seal(box, plain));
    }

    private Map<String, Target> decode(String stored) {
        Map<String, Target> decoded = new LinkedHashMap<>();
        if (stored == null || stored.length() > LanPeerRegistry.MAX_SEALED_CHARS || stored.trim().isEmpty()) return decoded;
        try {
            String plain = box.open(stored);
            if (plain == null || plain.length() > LanPeerRegistry.MAX_PLAIN_CHARS) return decoded;
            JsonElement parsed = JsonParser.parseString(plain);
            if (!parsed.isJsonObject()) return decoded;
            JsonObject root = parsed.getAsJsonObject();
            if (!LanPeerRegistry.supportedVersion(root) || !root.has("targets") || !root.get("targets").isJsonArray()) return decoded;
            JsonArray entries = root.getAsJsonArray("targets");
            if (entries.size() > MAX_TARGETS) return decoded;
            for (JsonElement element : entries) {
                if (!element.isJsonObject()) continue;
                JsonObject item = element.getAsJsonObject();
                String id = text(item, "id");
                String credential = text(item, "credential");
                String fingerprint = text(item, "fingerprint");
                if (!LanPeerAuthorization.isValidDeviceId(id) || !validCredential(credential) || decoded.containsKey(id)) continue;
                if (item.has("fingerprint") && !FINGERPRINT.matcher(fingerprint).matches()) continue;
                decoded.put(id, new Target(credential, fingerprint));
            }
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
        return decoded;
    }

    private static boolean validCredential(String value) {
        if (value == null || !CREDENTIAL.matcher(value).matches()) return false;
        byte[] decoded = Base64.getUrlDecoder().decode(value);
        try {
            return decoded.length == 32 && Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(value);
        } finally {
            java.util.Arrays.fill(decoded, (byte) 0);
        }
    }

    private static String text(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
    }
}
