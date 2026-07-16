package com.fongmi.android.tv.remote;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.remote.RemoteModels.BindCodeResponse;
import com.fongmi.android.tv.remote.RemoteModels.ClaimResponse;
import com.fongmi.android.tv.remote.RemoteModels.CommandDetailResponse;
import com.fongmi.android.tv.remote.RemoteModels.CommandResponse;
import com.fongmi.android.tv.remote.RemoteModels.DevicesResponse;
import com.fongmi.android.tv.remote.RemoteModels.PollResponse;
import com.fongmi.android.tv.remote.RemoteModels.RegisterResponse;
import com.fongmi.android.tv.remote.RemoteModels.RemoteBindGrant;
import com.fongmi.android.tv.remote.RemoteModels.RemoteCommandResult;
import com.fongmi.android.tv.remote.RemoteModels.RemoteGroup;
import com.fongmi.android.tv.remote.RemoteModels.RemoteProfile;
import com.fongmi.android.tv.remote.RemoteModels.ServerCapabilities;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class RemoteClient {

    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(12);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final MediaType ZIP = MediaType.parse("application/zip");

    private final RemoteProfile profile;

    public RemoteClient(RemoteProfile profile) {
        this.profile = profile;
    }

    public ServerCapabilities capabilities() throws IOException {
        return App.gson().fromJson(requestJson("GET", "/api/server/capabilities", null), ServerCapabilities.class);
    }

    public RegisterResponse register() throws IOException {
        ensureDeviceIdentity(profile);
        Device device = Device.get();
        JsonObject body = baseDeviceBody();
        body.addProperty("name", device.getName());
        body.addProperty("role", "app");
        body.addProperty("type", device.getType());
        body.addProperty("appVersion", BuildConfig.VERSION_NAME);
        body.add("capabilities", App.gson().toJsonTree(appCapabilities()));
        JsonArray groups = new JsonArray();
        if (profile.groups != null) {
            for (RemoteGroup group : profile.groups) {
                if (group != null && !TextUtils.isEmpty(group.groupToken)) {
                    JsonObject item = new JsonObject();
                    item.addProperty("groupToken", group.groupToken);
                    groups.add(item);
                }
            }
        }
        body.add("groups", groups);
        RegisterResponse response = App.gson().fromJson(requestJson("POST", "/api/device/register", body), RegisterResponse.class);
        if (response != null) {
            if (!TextUtils.isEmpty(response.deviceToken)) profile.deviceToken = response.deviceToken;
            else if (!TextUtils.isEmpty(response.deviceSecret)) profile.deviceToken = response.deviceSecret;
            if (!TextUtils.isEmpty(response.deviceId)) profile.deviceId = response.deviceId;
        }
        ensureDeviceIdentity(profile);
        return response;
    }

    public BindCodeResponse createBindCode(RemoteBindGrant grant) throws IOException {
        ensureDeviceIdentity(profile);
        JsonObject body = baseDeviceBody();
        body.addProperty("grantId", grant.grantId);
        body.addProperty("bindGrantToken", grant.bindGrantToken);
        return App.gson().fromJson(requestJson("POST", "/api/device/bind-code", body), BindCodeResponse.class);
    }

    public ClaimResponse claim(String code, String groupToken, String alias) throws IOException {
        ensureDeviceIdentity(profile);
        JsonObject body = new JsonObject();
        body.addProperty("code", code == null ? "" : code.trim());
        if (!TextUtils.isEmpty(groupToken)) body.addProperty("groupToken", groupToken);
        if (!TextUtils.isEmpty(alias)) body.addProperty("alias", alias.trim());
        return App.gson().fromJson(requestJson("POST", "/api/groups/claim", body), ClaimResponse.class);
    }

    public DevicesResponse listDevices(RemoteGroup group) throws IOException {
        if (group == null || TextUtils.isEmpty(group.groupToken)) throw new IOException("Missing group token");
        return App.gson().fromJson(requestJson("GET", "/api/devices", null, group.groupToken), DevicesResponse.class);
    }

    public CommandResponse createCommand(RemoteGroup group, String targetDeviceId, String type, JsonObject payload) throws IOException {
        if (group == null || TextUtils.isEmpty(group.groupToken)) throw new IOException("Missing group token");
        JsonObject body = new JsonObject();
        body.addProperty("targetDeviceId", targetDeviceId);
        body.addProperty("type", type);
        body.add("payload", payload == null ? new JsonObject() : payload);
        return App.gson().fromJson(requestJson("POST", "/api/commands", body, group.groupToken), CommandResponse.class);
    }

    public CommandDetailResponse getCommand(RemoteGroup group, String commandId) throws IOException {
        if (group == null || TextUtils.isEmpty(group.groupToken)) throw new IOException("Missing group token");
        return App.gson().fromJson(requestJson("GET", "/api/commands/" + commandId, null, group.groupToken), CommandDetailResponse.class);
    }

    public JsonObject createSync(RemoteGroup group, String sourceDeviceId, String targetDeviceId, JsonObject options) throws IOException {
        if (group == null || TextUtils.isEmpty(group.groupToken)) throw new IOException("Missing group token");
        JsonObject body = new JsonObject();
        body.addProperty("sourceDeviceId", sourceDeviceId);
        body.addProperty("targetDeviceId", targetDeviceId);
        body.add("options", options == null ? new JsonObject() : options);
        return requestJson("POST", "/api/sync/create", body, group.groupToken);
    }

    public JsonObject getSync(RemoteGroup group, String syncId) throws IOException {
        if (group == null || TextUtils.isEmpty(group.groupToken)) throw new IOException("Missing group token");
        if (TextUtils.isEmpty(syncId)) throw new IOException("Missing sync id");
        return requestJson("GET", "/api/sync/" + syncId, null, group.groupToken);
    }

    public PollResponse poll() throws IOException {
        ensureDeviceIdentity(profile);
        JsonObject body = baseDeviceBody();
        JsonArray groups = new JsonArray();
        if (profile.groups != null) {
            for (RemoteGroup group : profile.groups) {
                if (group != null && !TextUtils.isEmpty(group.groupToken)) {
                    JsonObject item = new JsonObject();
                    item.addProperty("groupToken", group.groupToken);
                    groups.add(item);
                }
            }
        }
        body.add("groups", groups);
        return App.gson().fromJson(requestJson("POST", "/api/device/poll", body), PollResponse.class);
    }

    public Request webSocketRequest() {
        ensureDeviceIdentity(profile);
        return authedRequest(webSocketUrl()).build();
    }

    public String webSocketHello() {
        ensureDeviceIdentity(profile);
        Device device = Device.get();
        JsonObject body = baseDeviceBody();
        body.addProperty("messageType", "hello");
        body.addProperty("name", device.getName());
        body.addProperty("role", "app");
        body.addProperty("type", device.getType());
        body.addProperty("appVersion", BuildConfig.VERSION_NAME);
        body.add("capabilities", App.gson().toJsonTree(appCapabilities()));
        JsonArray groups = new JsonArray();
        if (profile.groups != null) {
            for (RemoteGroup group : profile.groups) {
                if (group != null && !TextUtils.isEmpty(group.groupToken)) {
                    JsonObject item = new JsonObject();
                    item.addProperty("groupToken", group.groupToken);
                    groups.add(item);
                }
            }
        }
        body.add("groups", groups);
        return App.gson().toJson(body);
    }

    public void commandResult(String commandId, RemoteCommandResult result) throws IOException {
        if (TextUtils.isEmpty(commandId)) return;
        ensureDeviceIdentity(profile);
        JsonObject body = baseDeviceBody();
        body.addProperty("ok", result != null && result.ok);
        body.addProperty("accepted", result != null && result.accepted);
        body.addProperty("message", result == null ? "" : result.message);
        if (result != null && result.data != null) body.add("data", result.data);
        requestJson("POST", "/api/commands/" + commandId + "/result", body);
    }

    public JsonObject uploadSyncText(String url, String part, String text) throws IOException {
        return uploadSync(url, part, RequestBody.create(text == null ? "" : text, JSON));
    }

    public JsonObject uploadSyncFile(String url, String part, File file) throws IOException {
        if (file == null || !file.isFile()) return new JsonObject();
        return uploadSync(url, part, RequestBody.create(ZIP, file));
    }

    public File downloadSyncFile(String url, String prefix, String suffix) throws IOException {
        ensureDeviceIdentity(profile);
        File file = File.createTempFile(prefix, suffix, com.github.catvod.utils.Path.cache());
        Request request = authedRequest(url).get().build();
        try (Response response = OkHttp.client(TIMEOUT).newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code() + (body == null ? "" : ": " + body.string()));
            if (body == null) throw new IOException("Empty sync part");
            try (java.io.InputStream input = body.byteStream(); java.io.FileOutputStream output = new java.io.FileOutputStream(file)) {
                byte[] buffer = new byte[128 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            }
        }
        return file;
    }

    public String downloadSyncText(String url) throws IOException {
        ensureDeviceIdentity(profile);
        Request request = authedRequest(url).get().build();
        try (Response response = OkHttp.client(TIMEOUT).newCall(request).execute()) {
            ResponseBody body = response.body();
            String text = body == null ? "" : body.string();
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code() + (TextUtils.isEmpty(text) ? "" : ": " + text));
            return text;
        }
    }

    public JsonObject completeSync(String url, boolean ok, String message, JsonObject data) throws IOException {
        JsonObject body = baseDeviceBody();
        body.addProperty("ok", ok);
        body.addProperty("message", message == null ? "" : message);
        if (data != null) body.add("data", data);
        return requestAbsoluteJson(url, body);
    }

    private JsonObject baseDeviceBody() {
        JsonObject body = new JsonObject();
        body.addProperty("deviceId", profile.deviceId);
        body.addProperty("deviceToken", profile.deviceToken);
        body.addProperty("deviceSecret", profile.deviceToken);
        return body;
    }

    private JsonObject uploadSync(String url, String part, RequestBody body) throws IOException {
        Request request = authedRequest(url.endsWith("/" + part) ? url : url + "/" + part).post(body).build();
        try (Response response = OkHttp.client(TIMEOUT).newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String text = responseBody == null ? "" : responseBody.string();
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code() + (TextUtils.isEmpty(text) ? "" : ": " + text));
            if (TextUtils.isEmpty(text)) return new JsonObject();
            JsonObject object = App.gson().fromJson(text, JsonObject.class);
            return object == null ? new JsonObject() : object;
        }
    }

    private JsonObject requestAbsoluteJson(String url, JsonObject payload) throws IOException {
        Request request = authedRequest(url).post(RequestBody.create(payload == null ? "{}" : App.gson().toJson(payload), JSON)).build();
        try (Response response = OkHttp.client(TIMEOUT).newCall(request).execute()) {
            ResponseBody body = response.body();
            String text = body == null ? "" : body.string();
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code() + (TextUtils.isEmpty(text) ? "" : ": " + text));
            if (TextUtils.isEmpty(text)) return new JsonObject();
            JsonObject object = App.gson().fromJson(text, JsonObject.class);
            return object == null ? new JsonObject() : object;
        }
    }

    private Request.Builder authedRequest(String url) {
        Request.Builder builder = new Request.Builder().url(url);
        if (!TextUtils.isEmpty(profile.serverOrigin)) builder.header("x-webhtv-origin", profile.serverOrigin);
        if (!TextUtils.isEmpty(profile.deviceId)) builder.header("x-device-id", profile.deviceId);
        if (!TextUtils.isEmpty(profile.deviceToken)) builder.header("x-device-token", profile.deviceToken);
        return builder;
    }

    private String webSocketUrl() {
        String origin = profile.serverOrigin == null ? "" : profile.serverOrigin.trim();
        if (origin.startsWith("https://")) return "wss://" + origin.substring(8) + "/api/device/ws";
        if (origin.startsWith("http://")) return "ws://" + origin.substring(7) + "/api/device/ws";
        return origin + "/api/device/ws";
    }

    private JsonObject requestJson(String method, String path, JsonObject payload) throws IOException {
        return requestJson(method, path, payload, "");
    }

    private JsonObject requestJson(String method, String path, JsonObject payload, String groupToken) throws IOException {
        Request.Builder builder = new Request.Builder().url(profile.serverOrigin + path);
        if (!TextUtils.isEmpty(profile.serverOrigin)) builder.header("x-webhtv-origin", profile.serverOrigin);
        if (!TextUtils.isEmpty(profile.deviceId)) builder.header("x-device-id", profile.deviceId);
        if (!TextUtils.isEmpty(profile.deviceToken)) builder.header("x-device-token", profile.deviceToken);
        if (!TextUtils.isEmpty(groupToken)) builder.header("x-group-token", groupToken);
        if ("POST".equals(method)) builder.post(RequestBody.create(payload == null ? "{}" : App.gson().toJson(payload), JSON));
        else builder.get();
        try (Response response = OkHttp.client(TIMEOUT).newCall(builder.build()).execute()) {
            ResponseBody body = response.body();
            String text = body == null ? "" : body.string();
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code() + (TextUtils.isEmpty(text) ? "" : ": " + text));
            if (TextUtils.isEmpty(text)) return new JsonObject();
            JsonObject object = App.gson().fromJson(text, JsonObject.class);
            return object == null ? new JsonObject() : object;
        }
    }

    private static void ensureDeviceIdentity(RemoteProfile profile) {
        if (TextUtils.isEmpty(profile.deviceToken)) profile.deviceToken = RemoteTokens.randomCapability("dtk");
        profile.deviceId = RemoteTokens.deviceId(profile.serverOrigin, profile.deviceToken);
    }

    private static RemoteModels.RemoteCapabilities appCapabilities() {
        RemoteModels.RemoteCapabilities capabilities = new RemoteModels.RemoteCapabilities();
        capabilities.configManage = true;
        capabilities.remoteSync = true;
        capabilities.pushAction = true;
        capabilities.recentLog = true;
        capabilities.webSocket = true;
        return capabilities;
    }
}
