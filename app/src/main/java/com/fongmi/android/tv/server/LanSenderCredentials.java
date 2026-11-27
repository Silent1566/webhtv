package com.fongmi.android.tv.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Credentials this device presents to targets it has paired with, keyed by the target device id.
 *
 * <p>Pure JVM: sealing and value storage are injected. Reads never throw — unreadable storage is
 * treated as "not paired yet", so the caller simply gets denied by the target instead of crashing.
 */
public final class LanSenderCredentials {

    private static final int VERSION = 1;
    private static final int MAX_TARGETS = 64;

    private final LanPeerPersist.SecretBox box;
    private final LanPeerPersist.ValueStore store;
    private final Map<String, String> targets = new LinkedHashMap<>();

    public LanSenderCredentials(LanPeerPersist.SecretBox box, LanPeerPersist.ValueStore store) {
        this.box = box;
        this.store = store;
        targets.putAll(decode(store.read()));
    }

    public String get(String target) {
        String value = targets.get(target);
        return value == null ? "" : value;
    }

    public boolean isEmpty() {
        return targets.isEmpty();
    }

    public void put(String target, String credential) throws Exception {
        if (target == null || target.trim().isEmpty()) return;
        if (credential == null || credential.trim().isEmpty()) return;
        targets.put(target.trim(), credential.trim());
        persist();
    }

    public void remove(String target) throws Exception {
        if (targets.remove(target) != null) persist();
    }

    private void persist() throws Exception {
        if (targets.isEmpty()) {
            store.write("");
            return;
        }
        JsonArray entries = new JsonArray();
        for (Map.Entry<String, String> entry : targets.entrySet()) {
            JsonObject item = new JsonObject();
            item.addProperty("id", entry.getKey());
            item.addProperty("credential", entry.getValue());
            entries.add(item);
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.add("targets", entries);
        store.write(box.seal(root.toString()));
    }

    private Map<String, String> decode(String stored) {
        Map<String, String> decoded = new LinkedHashMap<>();
        if (stored == null || stored.trim().isEmpty()) return decoded;
        String plain;
        try {
            plain = box.open(stored);
        } catch (Throwable ignored) {
            return decoded;
        }
        try {
            JsonElement parsed = JsonParser.parseString(plain);
            if (!parsed.isJsonObject()) return decoded;
            JsonObject root = parsed.getAsJsonObject();
            if (!root.has("targets") || !root.get("targets").isJsonArray()) return decoded;
            JsonArray entries = root.getAsJsonArray("targets");
            if (entries.size() > MAX_TARGETS) return new LinkedHashMap<>();
            for (JsonElement element : entries) {
                if (!element.isJsonObject()) continue;
                JsonObject item = element.getAsJsonObject();
                String id = text(item, "id");
                String credential = text(item, "credential");
                if (id.isEmpty() || credential.isEmpty()) continue;
                decoded.put(id, credential);
            }
        } catch (Throwable ignored) {
            return new LinkedHashMap<>();
        }
        return decoded;
    }

    private static String text(JsonObject object, String name) {
        try {
            if (!object.has(name) || !object.get(name).isJsonPrimitive()) return "";
            return object.get(name).getAsString().trim();
        } catch (Throwable ignored) {
            return "";
        }
    }
}
