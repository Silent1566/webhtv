package com.fongmi.android.tv.web.ext;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Backup;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.Util;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class WebHomeExtensionSourceStore {

    private static final String KEY = Backup.PREF_WEB_HOME_EXTENSION_SOURCES;
    public static final String SOURCE_TYPE_FILE = "file";
    public static final String SOURCE_TYPE_LINK = "link";
    public static final String SOURCE_TYPE_CODE = "code";
    private static final Type TYPE = new TypeToken<List<Entry>>() {
    }.getType();

    public static synchronized List<Entry> list() {
        return read();
    }

    public static Entry draft(String name, String raw, boolean enabled, String siteKey) {
        Entry entry = new Entry();
        entry.name = name == null ? "" : name.trim();
        entry.raw = raw == null ? "" : raw.trim();
        entry.enabled = enabled;
        entry.siteKey = siteKey == null ? "" : siteKey.trim();
        entry.updatedAt = System.currentTimeMillis();
        return entry;
    }

    public static synchronized void saveRawJson(String json) {
        if (TextUtils.isEmpty(json)) {
            write(new ArrayList<>());
            return;
        }
        List<Entry> items = App.gson().fromJson(json, TYPE);
        if (items == null) items = new ArrayList<>();
        for (Entry item : items) {
            item.normalize();
            parse(item.getRaw());
        }
        write(items);
    }

    public static synchronized List<Entry> enabledEntries() {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : read()) if (entry.isEnabled() && !TextUtils.isEmpty(entry.getRaw())) result.add(entry);
        return result;
    }

    public static synchronized int enabledCount() {
        int count = 0;
        for (Entry entry : read()) if (entry.isEnabled() && !TextUtils.isEmpty(entry.getRaw())) count++;
        return count;
    }

    public static synchronized int enabledCount(String siteKey) {
        int count = 0;
        for (Entry entry : read()) if (entry.isEnabled() && !TextUtils.isEmpty(entry.getRaw()) && entry.matches(siteKey)) count++;
        return count;
    }

    public static synchronized void save(String id, String raw, boolean enabled) {
        save(id, raw, enabled, "");
    }

    public static synchronized void save(String id, String raw, boolean enabled, String siteKey) {
        String value = normalizeRaw(raw);
        parse(value);
        List<Entry> items = read();
        Entry target = null;
        if (TextUtils.isEmpty(id)) {
            for (Entry item : items) {
                if (!TextUtils.equals(item.getRaw(), value)) continue;
                target = item;
                break;
            }
        } else {
            for (Entry item : items) {
                if (!TextUtils.equals(item.getId(), id)) continue;
                target = item;
                break;
            }
        }
        if (target == null) {
            target = new Entry();
            target.id = "user_" + Util.md5(value + ":" + System.currentTimeMillis());
            items.add(target);
        }
        target.raw = value;
        target.name = title(value);
        target.siteKey = normalizeSiteKey(siteKey, target.siteKey);
        target.enabled = enabled;
        target.updatedAt = System.currentTimeMillis();
        write(items);
    }

    public static synchronized void saveCode(String id, String name, String code, boolean enabled) {
        saveCode(id, name, code, enabled, "");
    }

    public static synchronized void saveCode(String id, String name, String code, boolean enabled, String siteKey) {
        String value = code == null ? "" : code.trim();
        if (TextUtils.isEmpty(value)) throw new IllegalArgumentException("empty");
        saveCodeObject(id, name, WebHomeExtension.RUN_AT_END, "", value, enabled, siteKey, SOURCE_TYPE_CODE);
    }

    public static synchronized void saveCodeMeta(String id, String name, String extensionId, String runAt, String match, boolean enabled, String siteKey) {
        saveCodeMeta(id, name, runAt, match, "", enabled, siteKey, SOURCE_TYPE_CODE);
    }

    public static synchronized void saveCodeMeta(String id, String name, String runAt, String match, String code, boolean enabled, String siteKey, String sourceType) {
        Entry source = null;
        for (Entry item : read()) {
            if (!TextUtils.equals(item.getId(), id)) continue;
            source = item;
            break;
        }
        String value = TextUtils.isEmpty(code) ? code(source) : code.trim();
        if (TextUtils.isEmpty(value)) throw new IllegalArgumentException("empty");
        saveCodeObject(id, name, runAt, match, value, enabled, siteKey, sourceType(sourceType, source));
    }

    public static synchronized void saveFile(String id, String name, String code, boolean enabled, String siteKey) {
        String value = code == null ? "" : code.trim();
        if (TextUtils.isEmpty(value)) throw new IllegalArgumentException("empty");
        saveCodeObject(id, name, WebHomeExtension.RUN_AT_END, "", value, enabled, siteKey, SOURCE_TYPE_FILE);
    }

    public static synchronized void saveLink(String id, String name, String runAt, String url, String match, boolean enabled, String siteKey) {
        String link = url == null ? "" : url.trim();
        if (TextUtils.isEmpty(link)) throw new IllegalArgumentException("empty");
        List<Entry> items = read();
        Entry target = null;
        for (Entry item : items) {
            if (!TextUtils.equals(item.getId(), id)) continue;
            target = item;
            break;
        }
        if (target == null) {
            target = new Entry();
            target.id = "user_" + Util.md5(link + ":" + System.currentTimeMillis());
            items.add(target);
        }
        target.raw = rawLink(target.id, name, runAt, link, match);
        target.name = title(target.raw);
        target.siteKey = normalizeSiteKey(siteKey, target.siteKey);
        target.enabled = enabled;
        target.updatedAt = System.currentTimeMillis();
        write(items);
    }

    private static void saveCodeObject(String id, String name, String runAt, String match, String code, boolean enabled, String siteKey, String sourceType) {
        List<Entry> items = read();
        Entry target = null;
        for (Entry item : items) {
            if (!TextUtils.equals(item.getId(), id)) continue;
            target = item;
            break;
        }
        if (target == null) {
            target = new Entry();
            target.id = "user_" + Util.md5(code + ":" + System.currentTimeMillis());
            items.add(target);
        }
        target.raw = rawCode(target.id, name, runAt, match, code, sourceType(sourceType, target));
        target.name = title(target.raw);
        target.siteKey = normalizeSiteKey(siteKey, target.siteKey);
        target.enabled = enabled;
        target.updatedAt = System.currentTimeMillis();
        write(items);
    }

    public static synchronized void saveForm(String id, String name, String extensionId, String runAt, String jsUrl, String match, boolean enabled, String siteKey) {
        saveLink(id, name, runAt, jsUrl, match, enabled, siteKey);
    }

    public static String rawLink(String id, String name, String runAt, String url, String match) {
        String link = url == null ? "" : url.trim();
        if (TextUtils.isEmpty(link)) throw new IllegalArgumentException("empty");
        return linkObject(id, name, runAt, link, match).toString();
    }

    public static String rawCode(String id, String name, String runAt, String match, String code, String sourceType) {
        String value = code == null ? "" : code.trim();
        if (TextUtils.isEmpty(value)) throw new IllegalArgumentException("empty");
        return codeObject(id, name, runAt, match, value, sourceType).toString();
    }

    public static synchronized void setEnabled(String id, boolean enabled) {
        List<Entry> items = read();
        for (Entry item : items) {
            if (!TextUtils.equals(item.getId(), id)) continue;
            item.enabled = enabled;
            item.updatedAt = System.currentTimeMillis();
            write(items);
            return;
        }
    }

    public static synchronized void remove(String id) {
        List<Entry> items = read();
        if (items.removeIf(item -> TextUtils.equals(item.getId(), id))) write(items);
    }

    public static JsonElement parse(String raw) {
        String value = normalizeRaw(raw);
        if (looksLikeJson(value)) return Json.parse(value);
        return new JsonPrimitive(value);
    }

    public static boolean isCodeSource(Entry entry) {
        return !TextUtils.isEmpty(code(entry));
    }

    public static String sourceType(Entry entry) {
        return sourceType("", entry);
    }

    public static String link(Entry entry) {
        try {
            JsonElement element = parse(entry.getRaw());
            if (!element.isJsonObject()) return entry.getRaw();
            JsonObject object = element.getAsJsonObject();
            if (object.has("js") && object.get("js").isJsonArray() && object.getAsJsonArray("js").size() > 0) return object.getAsJsonArray("js").get(0).getAsString();
            for (String key : new String[]{"manifestUrl", "manifest", "sourceUrl", "url"}) {
                if (!object.has(key) || !object.get(key).isJsonPrimitive()) continue;
                String value = object.get(key).getAsString().trim();
                if (!TextUtils.isEmpty(value)) return value;
            }
            return "";
        } catch (Throwable e) {
            return entry == null ? "" : entry.getRaw();
        }
    }

    public static String code(Entry entry) {
        try {
            JsonElement element = parse(entry.getRaw());
            if (!element.isJsonObject()) return "";
            JsonObject object = element.getAsJsonObject();
            if (!object.has("code") || !object.get("code").isJsonPrimitive()) return "";
            return object.get("code").getAsString();
        } catch (Throwable e) {
            return "";
        }
    }

    public static String normalizeRaw(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (TextUtils.isEmpty(value)) throw new IllegalArgumentException("empty");
        return value;
    }

    private static List<Entry> read() {
        List<Entry> items = new ArrayList<>();
        try {
            List<Entry> restored = App.gson().fromJson(Prefers.getString(KEY), TYPE);
            if (restored != null) items.addAll(restored);
        } catch (Throwable ignored) {
        }
        for (Entry item : items) item.normalize();
        return items;
    }

    private static void write(List<Entry> items) {
        Prefers.put(KEY, App.gson().toJson(items));
    }

    private static JsonObject linkObject(String id, String name, String runAt, String link, String match) {
        JsonObject object = new JsonObject();
        object.addProperty("id", id == null ? "" : id.trim());
        object.addProperty("name", TextUtils.isEmpty(name) ? "Local extension" : name.trim());
        object.addProperty("runAt", TextUtils.isEmpty(runAt) ? WebHomeExtension.RUN_AT_END : runAt.trim());
        object.addProperty("sourceType", SOURCE_TYPE_LINK);
        List<String> matches = matchList(match);
        if (!matches.isEmpty()) object.add("cspKeyRegex", App.gson().toJsonTree(matches));
        if (WebHomeExtension.isScriptUrl(link)) object.add("js", App.gson().toJsonTree(List.of(link)));
        else object.addProperty("manifestUrl", link);
        return object;
    }

    private static JsonObject codeObject(String id, String name, String runAt, String match, String code, String sourceType) {
        JsonObject object = new JsonObject();
        object.addProperty("id", id == null ? "" : id.trim());
        object.addProperty("name", TextUtils.isEmpty(name) ? "Local extension" : name.trim());
        object.addProperty("runAt", TextUtils.isEmpty(runAt) ? WebHomeExtension.RUN_AT_END : runAt.trim());
        object.addProperty("sourceType", sourceType(sourceType, null));
        List<String> matches = matchList(match);
        if (!matches.isEmpty()) object.add("cspKeyRegex", App.gson().toJsonTree(matches));
        object.addProperty("code", code);
        return object;
    }

    private static List<String> matchList(String match) {
        List<String> result = new ArrayList<>();
        if (TextUtils.isEmpty(match)) return result;
        for (String item : match.split("\\r?\\n")) {
            String value = item == null ? "" : item.trim();
            if (!TextUtils.isEmpty(value) && !result.contains(value)) result.add(value);
        }
        return result;
    }

    private static String title(String raw) {
        try {
            if (looksLikeJson(raw)) {
                JsonElement element = Json.parse(raw);
                if (element.isJsonObject()) return title(element.getAsJsonObject(), raw);
                if (element.isJsonArray()) return "Manifest JSON";
            }
        } catch (Throwable ignored) {
        }
        String value = raw;
        int query = value.indexOf('?');
        if (query > 0) value = value.substring(0, query);
        int slash = value.lastIndexOf('/');
        if (slash >= 0 && slash < value.length() - 1) value = value.substring(slash + 1);
        return TextUtils.isEmpty(value) ? raw : value;
    }

    private static String title(JsonObject object, String fallback) {
        for (String key : new String[]{"name", "id", "manifestUrl", "manifest", "sourceUrl", "url"}) {
            if (!object.has(key) || !object.get(key).isJsonPrimitive()) continue;
            String value = object.get(key).getAsString().trim();
            if (!TextUtils.isEmpty(value)) return title(value);
        }
        return fallback.length() > 32 ? "Manifest JSON" : fallback;
    }

    private static boolean looksLikeJson(String value) {
        return value.startsWith("{") || value.startsWith("[");
    }

    private static String sourceType(String requested, Entry fallback) {
        if (SOURCE_TYPE_FILE.equals(requested) || SOURCE_TYPE_LINK.equals(requested) || SOURCE_TYPE_CODE.equals(requested)) return requested;
        try {
            if (fallback == null) return SOURCE_TYPE_CODE;
            JsonElement element = parse(fallback.getRaw());
            if (!element.isJsonObject()) return SOURCE_TYPE_LINK;
            JsonObject object = element.getAsJsonObject();
            String type = object.has("sourceType") && object.get("sourceType").isJsonPrimitive() ? object.get("sourceType").getAsString().trim() : "";
            if (SOURCE_TYPE_FILE.equals(type) || SOURCE_TYPE_LINK.equals(type) || SOURCE_TYPE_CODE.equals(type)) return type;
            if (object.has("code")) return SOURCE_TYPE_CODE;
            return SOURCE_TYPE_LINK;
        } catch (Throwable e) {
            return SOURCE_TYPE_LINK;
        }
    }

    private static String normalizeSiteKey(String siteKey, String fallback) {
        String value = siteKey == null ? "" : siteKey.trim();
        return TextUtils.isEmpty(value) ? (fallback == null ? "" : fallback.trim()) : value;
    }

    public static class Entry {
        private String id = "";
        private String name = "";
        private String raw = "";
        private String siteKey = "";
        private boolean enabled = true;
        private long updatedAt;

        private void normalize() {
            raw = raw == null ? "" : raw.trim();
            siteKey = siteKey == null ? "" : siteKey.trim();
            if (TextUtils.isEmpty(id)) id = "user_" + Util.md5(raw);
            if (TextUtils.isEmpty(name) && !TextUtils.isEmpty(raw)) name = title(raw);
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return TextUtils.isEmpty(name) ? getRaw() : name;
        }

        public String getRaw() {
            return raw == null ? "" : raw;
        }

        public String getSiteKey() {
            return siteKey == null ? "" : siteKey;
        }

        public boolean matches(String key) {
            return TextUtils.isEmpty(siteKey) || TextUtils.equals(siteKey, key);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setDraftEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getUpdatedAt() {
            return updatedAt;
        }
    }
}
