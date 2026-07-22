package com.fongmi.android.tv.remote;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Backup;
import com.fongmi.android.tv.bean.SyncOptions;
import com.fongmi.android.tv.remote.RemoteModels.RemoteCommand;
import com.fongmi.android.tv.remote.RemoteModels.RemoteCommandResult;
import com.fongmi.android.tv.remote.RemoteModels.RemoteProfile;
import com.fongmi.android.tv.utils.LoginStateSync;
import com.fongmi.android.tv.utils.SyncFiles;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Path;
import com.google.gson.JsonObject;

import java.io.File;

public final class RemoteSyncTransfer {

    private RemoteSyncTransfer() {
    }

    public static RemoteCommandResult export(RemoteProfile profile, RemoteCommand command) {
        try {
            JsonObject payload = payload(command);
            SyncOptions options = options(payload);
            RemoteClient client = new RemoteClient(profile);
            String uploadBase = string(payload, "uploadBase");
            String completeUrl = string(payload, "completeUrl");
            if (TextUtils.isEmpty(uploadBase) || TextUtils.isEmpty(completeUrl)) return RemoteCommandResult.failure("Missing sync upload endpoint");
            SyncFiles.Archive archive = null;
            LoginStateSync.Archive loginArchive = null;
            try {
                String backup = Backup.create(options).toString();
                client.uploadSyncText(uploadBase, "backup", backup);
                if (options.isRemoteRelay()) client.uploadSyncText(uploadBase, "remoteRelay", RemoteStore.exportRelayConfig());
                if (SyncFiles.hasPaths(options)) {
                    archive = SyncFiles.createArchive(SyncFiles.getPaths(options));
                    if (archive != null) client.uploadSyncFile(uploadBase, "syncFiles", archive.getFile());
                }
                if (options.isLoginState()) {
                    loginArchive = LoginStateSync.createArchive();
                    if (loginArchive != null) client.uploadSyncFile(uploadBase, "loginStateFiles", loginArchive.getFile());
                }
                JsonObject manifest = new JsonObject();
                manifest.add("options", App.gson().toJsonTree(options));
                manifest.addProperty("syncId", string(payload, "syncId"));
                manifest.addProperty("syncFiles", archive == null ? 0 : archive.getCount());
                manifest.addProperty("loginStateFiles", loginArchive == null ? 0 : loginArchive.getCount());
                client.uploadSyncText(uploadBase, "manifest", App.gson().toJson(manifest));
                client.completeSync(completeUrl, true, "Export complete", manifest);
                return RemoteCommandResult.success("Export complete", manifest);
            } finally {
                if (archive != null) Path.clear(archive.getFile());
                if (loginArchive != null) Path.clear(loginArchive.getFile());
            }
        } catch (Throwable e) {
            SpiderDebug.log("remote", "sync export failed error=%s", e.getMessage());
            notifyComplete(profile, command, false, e.getMessage());
            return RemoteCommandResult.failure(e.getMessage());
        }
    }

    public static RemoteCommandResult restore(RemoteProfile profile, RemoteCommand command) {
        File syncFiles = null;
        File loginStateFiles = null;
        try {
            JsonObject payload = payload(command);
            JsonObject downloads = payload.has("downloads") && payload.get("downloads").isJsonObject() ? payload.getAsJsonObject("downloads") : new JsonObject();
            SyncOptions options = options(payload);
            RemoteClient client = new RemoteClient(profile);
            String backup = downloads.has("backup") ? client.downloadSyncText(downloads.get("backup").getAsString()) : "";
            if (TextUtils.isEmpty(backup)) throw new IllegalStateException("Missing sync backup");
            String remoteRelay = downloads.has("remoteRelay") ? client.downloadSyncText(downloads.get("remoteRelay").getAsString()) : "";
            if (downloads.has("syncFiles")) syncFiles = client.downloadSyncFile(downloads.get("syncFiles").getAsString(), "webhtv-remote-sync-", ".zip");
            if (downloads.has("loginStateFiles")) loginStateFiles = client.downloadSyncFile(downloads.get("loginStateFiles").getAsString(), "webhtv-remote-login-", ".zip");
            int syncCount = syncFiles == null ? 0 : SyncFiles.restoreArchive(syncFiles);
            int loginCount = loginStateFiles == null ? 0 : LoginStateSync.restoreArchive(loginStateFiles);
            Backup.objectFrom(backup).restore(options, true);
            if (options.isRemoteRelay()) RemoteStore.importRelayConfig(remoteRelay);
            JsonObject result = new JsonObject();
            result.addProperty("syncFiles", syncCount);
            result.addProperty("loginStateFiles", loginCount);
            client.completeSync(string(payload, "completeUrl"), true, "Restore complete", result);
            return RemoteCommandResult.success("Restore complete", result);
        } catch (Throwable e) {
            SpiderDebug.log("remote", "sync restore failed error=%s", e.getMessage());
            notifyComplete(profile, command, false, e.getMessage());
            return RemoteCommandResult.failure(e.getMessage());
        } finally {
            Path.clear(syncFiles);
            Path.clear(loginStateFiles);
        }
    }

    private static void notifyComplete(RemoteProfile profile, RemoteCommand command, boolean ok, String message) {
        try {
            String completeUrl = string(payload(command), "completeUrl");
            if (!TextUtils.isEmpty(completeUrl)) new RemoteClient(profile).completeSync(completeUrl, ok, message, null);
        } catch (Throwable ignored) {
        }
    }

    private static JsonObject payload(RemoteCommand command) {
        return command == null || command.payload == null ? new JsonObject() : command.payload;
    }

    private static SyncOptions options(JsonObject payload) {
        if (payload != null && payload.has("options")) return SyncOptions.objectFrom(payload.get("options").toString());
        return SyncOptions.defaults();
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        return object.get(key).getAsString().trim();
    }
}
