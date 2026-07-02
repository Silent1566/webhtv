package com.fongmi.android.tv.gitcloud.provider;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.gitcloud.GitCloudException;
import com.fongmi.android.tv.gitcloud.GitHttpException;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

abstract class BaseGitProvider implements GitCloudProvider {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String TAG = "TV-GitCloud";

    protected JsonObject get(String url, String token) throws GitCloudException {
        return request("GET", url, token, null).object();
    }

    protected JsonArray getArray(String url, String token) throws GitCloudException {
        return request("GET", url, token, null).array();
    }

    protected JsonObject post(String url, String token, JsonObject payload) throws GitCloudException {
        return request("POST", url, token, payload).object();
    }

    protected JsonObject put(String url, String token, JsonObject payload) throws GitCloudException {
        return request("PUT", url, token, payload).object();
    }

    protected void delete(String url, String token) throws GitCloudException {
        request("DELETE", url, token, null);
    }

    protected HttpResult request(String method, String url, String token, JsonObject payload) throws GitCloudException {
        Request.Builder builder = new Request.Builder().url(url);
        headers(builder, token);
        RequestBody body = payload == null ? null : RequestBody.create(App.gson().toJson(payload), JSON);
        if ("POST".equals(method)) builder.post(body == null ? RequestBody.create(new byte[0]) : body);
        else if ("PUT".equals(method)) builder.put(body == null ? RequestBody.create(new byte[0]) : body);
        else if ("DELETE".equals(method)) {
            if (body == null) builder.delete();
            else builder.delete(body);
        }
        else builder.get();
        try (Response response = OkHttp.client().newCall(builder.build()).execute()) {
            ResponseBody responseBody = response.body();
            String text = responseBody == null ? "" : responseBody.string();
            debug(method, url, response.code(), jsonMessage(text));
            if (!response.isSuccessful()) throw new GitHttpException(response.code(), humanError(response.code(), text));
            return new HttpResult(text);
        } catch (GitCloudException e) {
            throw e;
        } catch (IOException e) {
            debug(method, url, 0, e.getMessage());
            throw new GitCloudException("网络请求失败：" + e.getMessage(), e);
        }
    }

    protected void headers(Request.Builder builder, String token) {
        builder.header("Accept", "application/json");
        if (!TextUtils.isEmpty(token)) builder.header("Authorization", "Bearer " + token);
    }

    protected String enc(String value) {
        return Uri.encode(value == null ? "" : value, null);
    }

    protected String encPath(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String[] parts = value.replaceAll("^/+", "").split("/");
        List<String> encoded = new ArrayList<>();
        for (String part : parts) if (!TextUtils.isEmpty(part)) encoded.add(enc(part));
        return TextUtils.join("/", encoded);
    }

    protected String str(JsonObject object, String key) {
        try {
            JsonElement element = object.get(key);
            return element == null || element.isJsonNull() ? "" : element.getAsString();
        } catch (Throwable e) {
            return "";
        }
    }

    protected boolean bool(JsonObject object, String key) {
        try {
            JsonElement element = object.get(key);
            return element != null && !element.isJsonNull() && element.getAsBoolean();
        } catch (Throwable e) {
            return false;
        }
    }

    protected long integer(JsonObject object, String key) {
        try {
            JsonElement element = object.get(key);
            return element == null || element.isJsonNull() ? 0 : element.getAsLong();
        } catch (Throwable e) {
            return 0;
        }
    }

    protected JsonObject obj(JsonObject object, String key) {
        try {
            JsonElement element = object.get(key);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (Throwable e) {
            return new JsonObject();
        }
    }

    protected JsonArray array(JsonObject object, String key) {
        try {
            JsonElement element = object.get(key);
            return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
        } catch (Throwable e) {
            return new JsonArray();
        }
    }

    private String humanError(int code, String body) {
        String message = jsonMessage(body);
        if (code == 401) return "token 无效或已过期";
        if (code == 403) return TextUtils.isEmpty(message) ? "token 权限不足" : "token 权限不足：" + message;
        if (code == 404) return "仓库不存在，或 token 没有访问该私有仓库";
        if (code == 409) return TextUtils.isEmpty(message) ? "远端内容有冲突，请先同步" : message;
        if (code == 422) return TextUtils.isEmpty(message) ? "请求参数不合法或仓库已存在" : message;
        return TextUtils.isEmpty(message) ? "Git 平台请求失败：" + code : message;
    }

    private String jsonMessage(String body) {
        try {
            JsonObject object = JsonParser.parseString(body).getAsJsonObject();
            String message = str(object, "message");
            if (TextUtils.isEmpty(message)) message = str(object, "errmsg");
            if (!TextUtils.isEmpty(message)) return message;
            JsonArray errors = array(object, "errors");
            if (errors.size() == 0) return "";
            List<String> values = new ArrayList<>();
            for (JsonElement item : errors) if (item.isJsonObject()) values.add(str(item.getAsJsonObject(), "message"));
            return TextUtils.join("；", values);
        } catch (Throwable e) {
            return "";
        }
    }

    private void debug(String method, String url, int code, String message) {
        String text = method + " " + url + " -> " + code;
        if (!TextUtils.isEmpty(message)) text += " " + abbreviate(message);
        debug(text);
    }

    protected void debug(String message) {
        if (!BuildConfig.DEBUG) return;
        String text = message == null ? "" : message;
        Log.d(TAG, text);
        SpiderDebug.log("git-cloud", text);
    }

    private String abbreviate(String value) {
        if (value == null) return "";
        String text = value.replace('\n', ' ').replace('\r', ' ');
        return text.length() > 180 ? text.substring(0, 180) + "..." : text;
    }

    protected static class HttpResult {
        final String text;

        HttpResult(String text) {
            this.text = text == null ? "" : text;
        }

        JsonObject object() throws GitCloudException {
            try {
                JsonElement element = JsonParser.parseString(text);
                return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
            } catch (Throwable e) {
                throw new GitCloudException("平台返回数据格式异常", e);
            }
        }

        JsonArray array() throws GitCloudException {
            try {
                JsonElement element = JsonParser.parseString(text);
                return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
            } catch (Throwable e) {
                throw new GitCloudException("平台返回数据格式异常", e);
            }
        }
    }
}
