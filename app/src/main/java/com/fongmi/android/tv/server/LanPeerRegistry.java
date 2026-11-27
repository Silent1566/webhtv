package com.fongmi.android.tv.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wire format for the paired-device registry: {@code {"version":1,"devices":[{"id","key"}]}}.
 *
 * <p>Pure JVM. Decoding never throws and never trusts the payload: unknown versions, malformed
 * entries and illegal digests are dropped, and an oversized payload decodes to an empty registry.
 */
public final class LanPeerRegistry {

    public static final int MAX_PARTNERS = 64;
    private static final int VERSION = 1;
    private static final int DIGEST_BYTES = 32;
    private static final int DIGEST_HEX = DIGEST_BYTES * 2;

    private LanPeerRegistry() {
    }

    /** Empty registry encodes to an empty string so callers can skip persistence entirely. */
    public static String encode(Map<String, byte[]> partners) {
        if (partners == null || partners.isEmpty()) return "";
        JsonArray devices = new JsonArray();
        for (Map.Entry<String, byte[]> entry : partners.entrySet()) {
            if (!valid(entry.getKey(), entry.getValue())) continue;
            JsonObject device = new JsonObject();
            device.addProperty("id", entry.getKey());
            device.addProperty("key", hex(entry.getValue()));
            devices.add(device);
        }
        if (devices.size() == 0) return "";
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.add("devices", devices);
        return root.toString();
    }

    public static Map<String, byte[]> decode(String payload) {
        Map<String, byte[]> partners = new LinkedHashMap<>();
        if (payload == null || payload.trim().isEmpty()) return partners;
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(payload);
            if (!parsed.isJsonObject()) return partners;
            root = parsed.getAsJsonObject();
        } catch (Throwable ignored) {
            return partners;
        }
        if (root.has("version") && root.get("version").isJsonPrimitive() && root.get("version").getAsInt() != VERSION) return partners;
        if (!root.has("devices") || !root.get("devices").isJsonArray()) return partners;
        JsonArray devices = root.getAsJsonArray("devices");
        if (devices.size() > MAX_PARTNERS) return partners;
        for (JsonElement element : devices) {
            if (!element.isJsonObject()) continue;
            JsonObject device = element.getAsJsonObject();
            String id = text(device, "id");
            byte[] digest = unhex(text(device, "key"));
            if (!valid(id, digest) || partners.containsKey(id)) continue;
            partners.put(id, digest);
        }
        return partners;
    }

    static boolean valid(String id, byte[] digest) {
        return LanPeerAuthorization.isValidDeviceId(id) && digest != null && digest.length == DIGEST_BYTES;
    }

    private static String text(JsonObject object, String name) {
        try {
            if (!object.has(name) || !object.get(name).isJsonPrimitive()) return "";
            return object.get(name).getAsString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format("%02x", value & 0xff));
        return out.toString();
    }

    private static byte[] unhex(String text) {
        if (text == null || text.length() != DIGEST_HEX) return null;
        byte[] bytes = new byte[DIGEST_BYTES];
        for (int i = 0; i < DIGEST_BYTES; i++) {
            int high = Character.digit(text.charAt(i * 2), 16);
            int low = Character.digit(text.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) return null;
            bytes[i] = (byte) ((high << 4) | low);
        }
        return bytes;
    }
}
