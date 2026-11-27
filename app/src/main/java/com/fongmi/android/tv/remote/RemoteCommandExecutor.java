package com.fongmi.android.tv.remote;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.remote.RemoteModels.RemoteCommand;
import com.fongmi.android.tv.remote.RemoteModels.RemoteCommandResult;
import com.fongmi.android.tv.remote.RemoteModels.RemoteGroup;
import com.fongmi.android.tv.remote.RemoteModels.RemoteProfile;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.DebugLogStore;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class RemoteCommandExecutor {

    private RemoteCommandExecutor() {
    }

    public static RemoteCommandResult execute(RemoteProfile profile, RemoteCommand command) {
        try {
            if (profile == null || command == null) return RemoteCommandResult.failure("Empty command");
            if ("remote.profile.addGroup".equals(command.type)) return addGroup(profile, command);
            if (!validateGroup(profile, command)) return RemoteCommandResult.failure("Command group is not trusted by this device");
            if ("device.status".equals(command.type)) return status(profile);
            if ("config.list".equals(command.type)) return RemoteConfigOps.list();
            if ("config.upsert".equals(command.type)) return RemoteConfigOps.upsert(command.payload);
            if ("config.use".equals(command.type)) return RemoteConfigOps.use(command.payload);
            if ("config.delete".equals(command.type)) return RemoteConfigOps.delete(command.payload);
            if ("config.sites".equals(command.type)) return RemoteConfigOps.sites(command.payload);
            if ("config.home".equals(command.type)) return RemoteConfigOps.home(command.payload);
            if ("remoteSync.export".equals(command.type)) return startSyncExport(profile, command);
            if ("remoteSync.restore".equals(command.type)) return startSyncRestore(profile, command);
            if ("action.search".equals(command.type)) return search(command.payload);
            if ("action.push".equals(command.type)) return push(command.payload);
            if ("player.command".equals(command.type)) return player(command);
            if ("log.recent".equals(command.type) || "device.log.recent".equals(command.type)) return recentLog(command.payload);
            return RemoteCommandResult.failure("Unsupported command: " + command.type);
        } catch (Throwable e) {
            return RemoteCommandResult.failure(e.getMessage());
        }
    }

    /** 遥控：解析并执行一条播放/导航命令；未识别的动作直接回失败，不做兜底猜测。 */
    private static RemoteCommandResult player(RemoteCommand command) {
        RemotePlayerCommand.Parsed parsed = RemotePlayerCommand.parse(command.payload);
        if (parsed == null) return RemoteCommandResult.failure("Unsupported player action");
        if (!RemotePlayerOps.run(parsed)) return RemoteCommandResult.failure("No active screen to receive the command");
        return RemoteCommandResult.success(parsed.action.wire, null);
    }

    private static RemoteCommandResult addGroup(RemoteProfile profile, RemoteCommand command) {
        JsonObject payload = command.payload == null ? new JsonObject() : command.payload;
        String groupId = string(payload, "groupId");
        String groupToken = string(payload, "groupToken");
        String groupTokenHash = string(payload, "groupTokenHash");
        String grantId = string(payload, "grantId");
        String bindGrantToken = string(payload, "bindGrantToken");
        if (TextUtils.isEmpty(groupToken) || TextUtils.isEmpty(grantId) || TextUtils.isEmpty(bindGrantToken)) return RemoteCommandResult.failure("Missing bootstrap payload");
        if (!RemoteStore.consumeBindGrant(profile.serverOrigin, grantId, bindGrantToken)) return RemoteCommandResult.failure("Bind grant is invalid or expired");
        if (TextUtils.isEmpty(groupId)) groupId = RemoteTokens.groupId(profile.serverOrigin, groupToken);
        if (TextUtils.isEmpty(groupTokenHash)) groupTokenHash = RemoteTokens.groupTokenHash(profile.serverOrigin, groupToken);
        RemoteGroup group = new RemoteGroup();
        group.groupId = groupId;
        group.groupToken = groupToken;
        group.groupTokenHash = groupTokenHash;
        group.name = TextUtils.isEmpty(string(payload, "alias")) ? "Remote group" : string(payload, "alias");
        RemoteStore.upsertGroup(profile.serverOrigin, group);
        return RemoteCommandResult.success("Group added", App.gson().toJsonTree(group));
    }

    private static boolean validateGroup(RemoteProfile profile, RemoteCommand command) {
        String hash = command.groupTokenHash;
        if (TextUtils.isEmpty(hash) && command.payload != null) hash = string(command.payload, "groupTokenHash");
        return RemoteStore.hasGroupTokenHash(profile.serverOrigin, hash);
    }

    private static RemoteCommandResult status(RemoteProfile profile) {
        JsonObject data = new JsonObject();
        data.add("device", App.gson().toJsonTree(Device.get()));
        data.addProperty("appVersion", BuildConfig.VERSION_NAME);
        data.addProperty("serverOrigin", profile.serverOrigin);
        data.addProperty("groupCount", profile.groups == null ? 0 : profile.groups.size());
        data.addProperty("appForeground", App.activity() != null);
        data.addProperty("debugLog", DebugLogStore.isEnabled());
        data.addProperty("debugLogLines", DebugLogStore.size());
        return RemoteCommandResult.success("", data);
    }

    private static RemoteCommandResult startSyncExport(RemoteProfile profile, RemoteCommand command) {
        Task.execute(() -> RemoteSyncTransfer.export(profile, command));
        return RemoteCommandResult.accepted("Sync export started");
    }

    private static RemoteCommandResult startSyncRestore(RemoteProfile profile, RemoteCommand command) {
        Task.execute(() -> RemoteSyncTransfer.restore(profile, command));
        return RemoteCommandResult.accepted("Sync restore started");
    }

    private static RemoteCommandResult search(JsonObject payload) {
        if (App.activity() == null) return RemoteCommandResult.failure("App is not open");
        String word = first(payload, "word", "keyword", "text", "q");
        if (TextUtils.isEmpty(word)) return RemoteCommandResult.failure("Missing search keyword");
        App.post(() -> ServerEvent.search(word));
        return RemoteCommandResult.success("Search posted", null);
    }

    private static RemoteCommandResult push(JsonObject payload) {
        if (App.activity() == null) return RemoteCommandResult.failure("App is not open");
        String url = first(payload, "url", "text");
        if (TextUtils.isEmpty(url)) return RemoteCommandResult.failure("Missing push URL");
        App.post(() -> ServerEvent.push(url));
        return RemoteCommandResult.success("Push posted", null);
    }

    private static RemoteCommandResult recentLog(JsonObject payload) {
        int limit = Math.max(1, Math.min(number(payload, "limit", 200), 2000));
        String[] lines = DebugLogStore.text().split("\\n");
        JsonArray array = new JsonArray();
        int start = Math.max(0, lines.length - limit);
        for (int i = start; i < lines.length; i++) if (!TextUtils.isEmpty(lines[i])) array.add(lines[i]);
        JsonObject data = new JsonObject();
        data.add("lines", array);
        data.addProperty("count", array.size());
        data.addProperty("enabled", DebugLogStore.isEnabled());
        return RemoteCommandResult.success("", data);
    }

    private static String first(JsonObject object, String... keys) {
        for (String key : keys) {
            String value = string(object, key);
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key)) return "";
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString().trim();
    }

    private static int number(JsonObject object, String key, int fallback) {
        try {
            if (object == null || !object.has(key)) return fallback;
            return object.get(key).getAsInt();
        } catch (Throwable e) {
            return fallback;
        }
    }
}
