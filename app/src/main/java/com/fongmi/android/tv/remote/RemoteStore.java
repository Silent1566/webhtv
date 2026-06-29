package com.fongmi.android.tv.remote;

import android.content.Context;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.remote.RemoteModels.ClaimResponse;
import com.fongmi.android.tv.remote.RemoteModels.RemoteBindGrant;
import com.fongmi.android.tv.remote.RemoteModels.RemoteDevice;
import com.fongmi.android.tv.remote.RemoteModels.RemoteGroup;
import com.fongmi.android.tv.remote.RemoteModels.RemoteProfile;
import com.fongmi.android.tv.remote.RemoteModels.RemoteStoreFile;
import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Prefers;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class RemoteStore {

    private static final String KEY_STORE = "remote_trust_store";
    private static final String FILE_STORE = "WebHTV/RemoteTrust/profile.json";
    private static final long MAX_STORE_FILE_SIZE = 2L * 1024 * 1024;

    private static RemoteStoreFile cache;
    private static boolean loaded;

    private RemoteStore() {
    }

    public static synchronized RemoteStoreFile get() {
        if (!loaded) reloadFromFile();
        return ensure(cache);
    }

    public static synchronized void reloadFromFile() {
        loaded = true;
        RemoteStoreFile store = null;
        if (Setting.hasFileAccess()) {
            File file = file();
            if (file.exists() && file.length() > 0) {
                if (file.length() <= MAX_STORE_FILE_SIZE) {
                    store = parse(Path.read(file));
                    if (store != null) Prefers.put(KEY_STORE, App.gson().toJson(ensure(store)));
                } else {
                    SpiderDebug.log("remote", "skip oversized profile file size=%d", file.length());
                }
            }
        }
        if (store == null) store = parse(Prefers.getString(KEY_STORE));
        cache = ensure(store);
    }

    public static synchronized void save(RemoteStoreFile store) {
        cache = ensure(store);
        String json = App.gson().toJson(cache);
        Prefers.put(KEY_STORE, json);
        if (!Setting.hasFileAccess()) return;
        try {
            File target = file();
            File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
            Path.write(tmp, json.getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(target)) Path.move(tmp, target);
        } catch (Throwable e) {
            SpiderDebug.log("remote", "save profile file failed error=%s", e.getMessage());
        }
    }

    public static synchronized RemoteProfile prepareProfile(String serverUrl, boolean enabled, boolean keepOnline) {
        String origin = RemoteTokens.normalizeOrigin(serverUrl);
        if (TextUtils.isEmpty(origin)) throw new IllegalArgumentException(App.get().getString(R.string.remote_trust_server_required));
        RemoteStoreFile store = get();
        RemoteProfile profile = findProfile(store, origin);
        if (profile == null) {
            profile = new RemoteProfile();
            profile.serverOrigin = origin;
            store.profiles.add(profile);
        }
        profile.serverUrl = serverUrl.trim();
        profile.serverOrigin = origin;
        profile.enabled = enabled;
        profile.keepOnline = keepOnline;
        ensureProfile(profile);
        if (TextUtils.isEmpty(profile.deviceToken)) profile.deviceToken = RemoteTokens.randomCapability("dtk");
        profile.deviceId = RemoteTokens.deviceId(profile.serverOrigin, profile.deviceToken);
        profile.updatedAt = System.currentTimeMillis();
        save(store);
        return profile;
    }

    public static synchronized RemoteProfile firstProfile() {
        RemoteStoreFile store = get();
        return store.profiles.isEmpty() ? null : store.profiles.get(0);
    }

    public static synchronized RemoteProfile getProfileByOrigin(String serverOrigin) {
        return findProfile(get(), serverOrigin);
    }

    public static synchronized void upsertProfile(RemoteProfile profile) {
        if (profile == null || TextUtils.isEmpty(profile.serverOrigin)) return;
        RemoteStoreFile store = get();
        RemoteProfile current = findProfile(store, profile.serverOrigin);
        profile.updatedAt = System.currentTimeMillis();
        ensureProfile(profile);
        if (current == null) store.profiles.add(profile);
        else store.profiles.set(store.profiles.indexOf(current), profile);
        save(store);
    }

    public static synchronized void addBindGrant(String serverOrigin, RemoteBindGrant grant) {
        RemoteProfile profile = getProfileByOrigin(serverOrigin);
        if (profile == null || grant == null) return;
        ensureProfile(profile);
        profile.pendingBindGrants.add(grant);
        profile.updatedAt = System.currentTimeMillis();
        upsertProfile(profile);
    }

    public static synchronized boolean consumeBindGrant(String serverOrigin, String grantId, String bindGrantToken) {
        RemoteProfile profile = getProfileByOrigin(serverOrigin);
        if (profile == null) return false;
        ensureProfile(profile);
        boolean matched = false;
        long now = System.currentTimeMillis();
        for (RemoteBindGrant grant : profile.pendingBindGrants) {
            if (grant == null || grant.consumedAt > 0) continue;
            if (!TextUtils.equals(grant.grantId, grantId)) continue;
            if (!TextUtils.equals(grant.bindGrantToken, bindGrantToken)) continue;
            grant.consumedAt = now;
            matched = true;
            break;
        }
        if (matched) upsertProfile(profile);
        return matched;
    }

    public static synchronized void upsertGroup(String serverOrigin, RemoteGroup group) {
        RemoteProfile profile = getProfileByOrigin(serverOrigin);
        if (profile == null || group == null || TextUtils.isEmpty(group.groupId)) return;
        ensureProfile(profile);
        if (profile.groups == null) profile.groups = new ArrayList<>();
        group.updatedAt = System.currentTimeMillis();
        for (int i = 0; i < profile.groups.size(); i++) {
            RemoteGroup current = profile.groups.get(i);
            if (current != null && TextUtils.equals(current.groupId, group.groupId)) {
                profile.groups.set(i, group);
                upsertProfile(profile);
                return;
            }
        }
        profile.groups.add(group);
        upsertProfile(profile);
    }

    public static synchronized RemoteGroup upsertClaimGroup(String serverOrigin, ClaimResponse response, String alias) {
        if (response == null) return null;
        String groupToken = TextUtils.isEmpty(response.groupToken) ? response.familyToken : response.groupToken;
        if (TextUtils.isEmpty(groupToken)) return null;
        RemoteProfile profile = getProfileByOrigin(serverOrigin);
        if (profile == null) return null;
        ensureProfile(profile);
        String groupId = TextUtils.isEmpty(response.groupId) ? RemoteTokens.groupId(serverOrigin, groupToken) : response.groupId;
        String groupTokenHash = TextUtils.isEmpty(response.groupTokenHash) ? RemoteTokens.groupTokenHash(serverOrigin, groupToken) : response.groupTokenHash;
        RemoteGroup group = findGroup(profile, groupId);
        if (group == null) {
            group = new RemoteGroup();
            group.groupId = groupId;
            profile.groups.add(group);
        }
        group.groupToken = groupToken;
        group.groupTokenHash = groupTokenHash;
        if (TextUtils.isEmpty(group.name)) {
            if (!TextUtils.isEmpty(alias)) group.name = alias.trim();
            else if (response.device != null && !TextUtils.isEmpty(response.device.name)) group.name = response.device.name;
            else group.name = "Remote group";
        }
        if (response.device != null && !isLocalDevice(profile, response.device.deviceId)) upsertDevice(group, response.device);
        else if (!TextUtils.isEmpty(response.deviceId)) {
            RemoteDevice device = new RemoteDevice();
            device.deviceId = response.deviceId;
            device.name = response.deviceId;
            if (!isLocalDevice(profile, device.deviceId)) upsertDevice(group, device);
        }
        group.updatedAt = System.currentTimeMillis();
        profile.updatedAt = group.updatedAt;
        upsertProfile(profile);
        return group;
    }

    public static synchronized void upsertDevices(String serverOrigin, String groupId, List<RemoteDevice> devices) {
        RemoteProfile profile = getProfileByOrigin(serverOrigin);
        if (profile == null || TextUtils.isEmpty(groupId)) return;
        ensureProfile(profile);
        RemoteGroup group = findGroup(profile, groupId);
        if (group == null) return;
        if (devices == null) devices = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RemoteDevice device : devices) {
            if (device == null || TextUtils.isEmpty(device.deviceId)) continue;
            if (isLocalDevice(profile, device.deviceId)) continue;
            seen.add(device.deviceId);
            upsertDevice(group, device);
        }
        if (group.devices != null) {
            for (RemoteDevice device : group.devices) {
                if (device != null && !seen.contains(device.deviceId)) device.online = false;
            }
        }
        group.updatedAt = System.currentTimeMillis();
        profile.updatedAt = group.updatedAt;
        upsertProfile(profile);
    }

    public static synchronized boolean hasGroupTokenHash(String serverOrigin, String groupTokenHash) {
        if (TextUtils.isEmpty(groupTokenHash)) return false;
        RemoteProfile profile = getProfileByOrigin(serverOrigin);
        if (profile == null) return false;
        ensureProfile(profile);
        for (RemoteGroup group : profile.groups) {
            if (group != null && TextUtils.equals(group.groupTokenHash, groupTokenHash)) return true;
        }
        return false;
    }

    public static synchronized void removeProfile(String serverOrigin) {
        RemoteStoreFile store = get();
        for (Iterator<RemoteProfile> it = store.profiles.iterator(); it.hasNext(); ) {
            RemoteProfile profile = it.next();
            if (profile != null && TextUtils.equals(profile.serverOrigin, serverOrigin)) it.remove();
        }
        save(store);
    }

    public static synchronized boolean removeDevice(String serverOrigin, String groupId, String deviceId) {
        RemoteProfile profile = getProfileByOrigin(serverOrigin);
        if (profile == null || TextUtils.isEmpty(groupId) || TextUtils.isEmpty(deviceId)) return false;
        ensureProfile(profile);
        boolean removed = false;
        for (Iterator<RemoteGroup> groupIt = profile.groups.iterator(); groupIt.hasNext(); ) {
            RemoteGroup group = groupIt.next();
            if (group == null || !TextUtils.equals(group.groupId, groupId)) continue;
            if (group.devices != null) {
                for (Iterator<RemoteDevice> deviceIt = group.devices.iterator(); deviceIt.hasNext(); ) {
                    RemoteDevice device = deviceIt.next();
                    if (device != null && TextUtils.equals(device.deviceId, deviceId)) {
                        deviceIt.remove();
                        removed = true;
                    }
                }
            }
            if (group.devices == null || group.devices.isEmpty()) groupIt.remove();
            break;
        }
        if (removed) {
            profile.updatedAt = System.currentTimeMillis();
            upsertProfile(profile);
        }
        return removed;
    }

    public static synchronized void clear() {
        cache = new RemoteStoreFile();
        loaded = true;
        Prefers.remove(KEY_STORE);
        if (Setting.hasFileAccess()) {
            Path.clear(file());
            Path.clear(new File(file().getParentFile(), file().getName() + ".tmp"));
        }
    }

    public static synchronized String exportRelayConfig() {
        JsonObject root = new JsonObject();
        JsonArray profiles = new JsonArray();
        for (RemoteProfile profile : get().profiles) {
            if (profile == null || TextUtils.isEmpty(profile.serverOrigin)) continue;
            JsonObject item = new JsonObject();
            item.addProperty("serverUrl", TextUtils.isEmpty(profile.serverUrl) ? profile.serverOrigin : profile.serverUrl);
            item.addProperty("serverOrigin", profile.serverOrigin);
            item.addProperty("enabled", profile.enabled);
            item.addProperty("keepOnline", true);
            profiles.add(item);
        }
        root.add("profiles", profiles);
        return App.gson().toJson(root);
    }

    public static synchronized int importRelayConfig(String json) {
        if (TextUtils.isEmpty(json)) return 0;
        JsonObject root;
        try {
            root = App.gson().fromJson(json, JsonObject.class);
        } catch (Throwable e) {
            return 0;
        }
        JsonArray profiles = root != null && root.has("profiles") && root.get("profiles").isJsonArray() ? root.getAsJsonArray("profiles") : new JsonArray();
        int count = 0;
        for (int i = 0; i < profiles.size(); i++) {
            if (!profiles.get(i).isJsonObject()) continue;
            JsonObject item = profiles.get(i).getAsJsonObject();
            String serverUrl = string(item, "serverUrl");
            if (TextUtils.isEmpty(serverUrl)) serverUrl = string(item, "serverOrigin");
            String origin = RemoteTokens.normalizeOrigin(serverUrl);
            if (TextUtils.isEmpty(origin)) continue;
            boolean enabled = !item.has("enabled") || item.get("enabled").getAsBoolean();
            prepareProfile(serverUrl, enabled, true);
            count++;
        }
        if (count > 0) RemoteAgent.get().start();
        return count;
    }

    public static synchronized String summary(Context context) {
        RemoteStoreFile store = get();
        int profiles = 0;
        int groups = 0;
        int devices = 0;
        boolean enabled = false;
        boolean keepOnline = false;
        for (RemoteProfile profile : store.profiles) {
            if (profile == null) continue;
            profiles++;
            ensureProfile(profile);
            groups += profile.groups.size();
            for (RemoteGroup group : profile.groups) {
                if (group != null && group.devices != null) devices += group.devices.size();
            }
            enabled |= profile.enabled;
            keepOnline |= profile.enabled && profile.keepOnline;
        }
        if (profiles == 0) return context.getString(R.string.remote_trust_status_unbound);
        if (!enabled) return context.getString(R.string.setting_disable);
        String status = keepOnline ? context.getString(R.string.remote_trust_status_online) : context.getString(R.string.remote_trust_status_enabled);
        return context.getString(R.string.remote_trust_current_status_summary, status, groups, devices);
    }

    static boolean shouldStart(RemoteProfile profile) {
        if (profile == null || TextUtils.isEmpty(profile.serverOrigin)) return false;
        ensureProfile(profile);
        return profile.enabled || !profile.groups.isEmpty() || hasPendingGrant(profile);
    }

    static boolean hasPendingGrant(RemoteProfile profile) {
        ensureProfile(profile);
        for (RemoteBindGrant grant : profile.pendingBindGrants) if (grant != null && grant.consumedAt <= 0) return true;
        return false;
    }

    private static RemoteProfile findProfile(RemoteStoreFile store, String serverOrigin) {
        if (store == null || TextUtils.isEmpty(serverOrigin)) return null;
        ensure(store);
        for (RemoteProfile profile : store.profiles) {
            if (profile != null && TextUtils.equals(profile.serverOrigin, serverOrigin)) return profile;
        }
        return null;
    }

    private static RemoteGroup findGroup(RemoteProfile profile, String groupId) {
        if (profile == null || TextUtils.isEmpty(groupId)) return null;
        ensureProfile(profile);
        for (RemoteGroup group : profile.groups) {
            if (group != null && TextUtils.equals(group.groupId, groupId)) return group;
        }
        return null;
    }

    private static void upsertDevice(RemoteGroup group, RemoteDevice device) {
        if (group == null || device == null || TextUtils.isEmpty(device.deviceId)) return;
        if (group.devices == null) group.devices = new ArrayList<>();
        for (int i = 0; i < group.devices.size(); i++) {
            RemoteDevice current = group.devices.get(i);
            if (current != null && TextUtils.equals(current.deviceId, device.deviceId)) {
                group.devices.set(i, device);
                return;
            }
        }
        group.devices.add(device);
    }

    private static boolean isLocalDevice(RemoteProfile profile, String deviceId) {
        return profile != null && !TextUtils.isEmpty(profile.deviceId) && TextUtils.equals(profile.deviceId, deviceId);
    }

    private static RemoteStoreFile parse(String json) {
        if (TextUtils.isEmpty(json)) return null;
        try {
            return ensure(App.gson().fromJson(json, RemoteStoreFile.class));
        } catch (Throwable e) {
            return null;
        }
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        return object.get(key).getAsString().trim();
    }

    private static RemoteStoreFile ensure(RemoteStoreFile store) {
        if (store == null) store = new RemoteStoreFile();
        if (store.profiles == null) store.profiles = new ArrayList<>();
        for (RemoteProfile profile : store.profiles) ensureProfile(profile);
        return store;
    }

    private static void ensureProfile(RemoteProfile profile) {
        if (profile == null) return;
        if (profile.pendingBindGrants == null) profile.pendingBindGrants = new ArrayList<>();
        if (profile.groups == null) profile.groups = new ArrayList<>();
        for (RemoteGroup group : profile.groups) {
            if (group == null) continue;
            if (group.devices == null) group.devices = new ArrayList<>();
            for (Iterator<RemoteDevice> it = group.devices.iterator(); it.hasNext(); ) {
                RemoteDevice device = it.next();
                if (device == null || isLocalDevice(profile, device.deviceId)) it.remove();
            }
        }
    }

    private static File file() {
        return Path.root(FILE_STORE);
    }
}
