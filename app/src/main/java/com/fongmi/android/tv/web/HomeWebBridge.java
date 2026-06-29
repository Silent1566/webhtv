package com.fongmi.android.tv.web;

import android.app.Activity;
import android.text.TextUtils;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.drive.DriveCheckRequest;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.service.DriveCheckService;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.ui.activity.KeepActivity;
import com.fongmi.android.tv.ui.activity.LiveActivity;
import com.fongmi.android.tv.ui.activity.SearchActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.web.ext.WebHomeExtensionRegistry;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Prefers;
import com.google.gson.JsonObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class HomeWebBridge {

    private static final int INLINE_LIMIT = 12000;
    private static final int CHUNK_SIZE = 60000;
    private static final long INLINE_RESOLVE_TIMEOUT_SECONDS = 20;

    private final HomeWebController controller;
    private final Activity activity;
    private final WebView webView;
    private final Map<String, String> results;
    private final Map<String, CompletableFuture<String>> inlineResults;

    public HomeWebBridge(HomeWebController controller, Activity activity, WebView webView) {
        this.controller = controller;
        this.activity = activity;
        this.webView = webView;
        this.results = new ConcurrentHashMap<>();
        this.inlineResults = new ConcurrentHashMap<>();
    }

    @JavascriptInterface
    public void invoke(String requestId, String method, String payload) {
        Task.execute(() -> handle(requestId, method, WebCall.object(payload)));
    }

    @JavascriptInterface
    public void console(String level, String message) {
        controller.dispatchDebugConsole(level, message);
    }

    @JavascriptInterface
    public void network(String type, String method, String url, int status, long durationMs, String detail) {
        controller.dispatchDebugNetwork(type, method, url, status, durationMs, detail);
    }

    @JavascriptInterface
    public String resourceUrl(String url, String options) {
        JsonObject object = WebCall.object(options);
        StringBuilder builder = new StringBuilder(Server.get().getAddress("/webResource?url=")).append(encode(url));
        if (object.has("headers")) builder.append("&headers=").append(encode(object.get("headers").toString()));
        if ("include".equals(Json.safeString(object, "credentials"))) builder.append("&credentials=include");
        return builder.toString();
    }

    @JavascriptInterface
    public int resultLength(String id) {
        String result = results.get(id);
        return result == null ? 0 : result.length();
    }

    @JavascriptInterface
    public String resultChunk(String id, int start) {
        String result = results.get(id);
        if (result == null || start < 0 || start >= result.length()) return "";
        return result.substring(start, Math.min(start + CHUNK_SIZE, result.length()));
    }

    @JavascriptInterface
    public void clearResult(String id) {
        results.remove(id);
    }

    @JavascriptInterface
    public void inlineResult(String id, String payload) {
        CompletableFuture<String> future = inlineResults.remove(id);
        if (future != null) future.complete(payload);
    }

    private void handle(String requestId, String method, JsonObject payload) {
        try {
            SpiderDebug.log("webhome", "invoke method=%s payload=%s", method, payload);
            String result = switch (method) {
                case "net.request" -> WebCall.request(payload, controller);
                case "net.resourceUrl" -> quote(resourceUrl(Json.safeString(payload, "url"), payload.toString()));
                case "player.playUrl" -> playUrl(payload);
                case "player.playVod" -> playVod(payload);
                case "player.playVodInline" -> playVodInline(payload);
                case "player.preloadArtwork" -> preloadArtwork(payload);
                case "player.control" -> control(payload);
                case "player.status" -> WebCall.request(statusPayload());
                case "app.search" -> search(payload);
                case "app.openVod" -> openVod();
                case "app.openLive" -> openLive();
                case "app.openKeep" -> openKeep();
                case "app.openSetting" -> openSetting();
                case "app.history" -> history();
                case "pan.check" -> checkLinks(payload);
                case "pan.play" -> playPan(payload);
                case "cache.get" -> quote(Prefers.getString(cacheKey(payload)));
                case "cache.set" -> cacheSet(payload);
                case "cache.del" -> cacheDel(payload);
                case "device.info" -> device();
                case "site.info" -> site();
                case "config.info" -> config();
                case "ext.info" -> extInfo();
                case "ext.log" -> extLog(payload);
                case "ext.toast" -> extToast(payload);
                case "ui.setToolbar" -> setToolbar(payload);
                case "ui.setChrome" -> setChrome(payload);
                case "ui.restoreChrome" -> restoreChrome();
                case "ui.getViewport" -> controller.getViewportJson();
                case "navigation.back" -> back();
                case "navigation.reload" -> reload();
                default -> throw new IllegalArgumentException("Unknown method: " + method);
            };
            resolve(requestId, result);
        } catch (Throwable e) {
            reject(requestId, e.getMessage());
        }
    }

    private String playUrl(JsonObject payload) {
        String url = Json.safeString(payload, "url");
        String title = Json.safeString(payload, "title");
        String pic = Json.safeString(payload, "pic");
        String wall = wallPic(payload);
        String content = content(payload);
        if (payload.has("headers") || "include".equals(Json.safeString(payload, "credentials"))) url = resourceUrl(url, payload.toString());
        final String playUrl = url;
        final String playTitle = TextUtils.isEmpty(title) ? playUrl : title;
        final String playPic = pic;
        final String playWall = wall;
        final String playContent = content;
        SpiderDebug.log("webhome", "player.playUrl title=%s url=%s", playTitle, playUrl);
        App.post(() -> VideoActivity.start(activity, SiteApi.PUSH, playUrl, playTitle, playPic, null, playWall, playContent));
        return "{}";
    }

    private String playVod(JsonObject payload) {
        String siteKey = Json.safeString(payload, "siteKey");
        String vodId = Json.safeString(payload, "vodId");
        String title = Json.safeString(payload, "title");
        String pic = Json.safeString(payload, "pic");
        String wall = wallPic(payload);
        String content = content(payload);
        App.post(() -> VideoActivity.start(activity, siteKey, vodId, title, pic, null, wall, content));
        return "{}";
    }

    private String playVodInline(JsonObject payload) {
        String vodId = WebHomeInlineVodStore.put(payload, this::resolveInlineEpisode);
        String title = Json.safeString(payload, "title");
        if (TextUtils.isEmpty(title)) title = Json.safeString(payload, "vod_name");
        String pic = Json.safeString(payload, "pic");
        if (TextUtils.isEmpty(pic)) pic = Json.safeString(payload, "vod_pic");
        String mark = Json.safeString(payload, "mark");
        String wall = wallPic(payload);
        String content = content(payload);
        final String playTitle = TextUtils.isEmpty(title) ? vodId : title;
        final String playPic = pic;
        final String playMark = mark;
        final String playWall = wall;
        final String playContent = content;
        SpiderDebug.log("webhome", "player.playVodInline title=%s id=%s mark=%s", playTitle, vodId, playMark);
        App.post(() -> VideoActivity.start(activity, WebHomeInlineVodStore.KEY, vodId, playTitle, playPic, playMark, playWall, playContent));
        JsonObject result = new JsonObject();
        result.addProperty("siteKey", WebHomeInlineVodStore.KEY);
        result.addProperty("vodId", vodId);
        return result.toString();
    }

    private String preloadArtwork(JsonObject payload) {
        String pic = Json.safeString(payload, "pic");
        String wall = wallPic(payload);
        App.post(() -> {
            ImgUtil.preload(activity, pic);
            if (!TextUtils.isEmpty(wall) && !TextUtils.equals(wall, pic)) ImgUtil.preload(activity, wall);
        });
        return "{}";
    }

    private String wallPic(JsonObject payload) {
        return Json.safeString(payload, "wallPic");
    }

    private String content(JsonObject payload) {
        String content = Json.safeString(payload, "content");
        if (TextUtils.isEmpty(content)) content = Json.safeString(payload, "vod_content");
        if (TextUtils.isEmpty(content)) content = Json.safeString(payload, "desc");
        if (TextUtils.isEmpty(content)) content = Json.safeString(payload, "description");
        return content;
    }

    private JsonObject resolveInlineEpisode(JsonObject payload) throws Exception {
        String id = "inline_" + UUID.randomUUID().toString().replace("-", "");
        CompletableFuture<String> future = new CompletableFuture<>();
        inlineResults.put(id, future);
        String script = """
                (function(){
                  const id=%s;
                  const payload=%s;
                  const done=function(value){
                    try{fongmiBridge.inlineResult(id,JSON.stringify(value||{}));}catch(e){}
                  };
                  const fail=function(error){
                    const message=error&&error.message?error.message:String(error||'');
                    done({error:message});
                  };
                  try{
                    const resolver=window.__fmWebHomeInlineResolver||window.__fmYmvidResolveEpisode;
                    if(typeof resolver!=='function'){fail('inline resolver unavailable');return;}
                    Promise.resolve(resolver(payload)).then(done,fail);
                  }catch(e){
                    fail(e);
                  }
                })();
                """;
        script = String.format(Locale.ROOT, script, quote(id), payload == null ? "{}" : payload.toString());
        long start = System.currentTimeMillis();
        boolean lease = controller.beginInlineEvaluation();
        try {
            SpiderDebug.log("webhome-inline", "resolve start id=%s page=%s lease=%s", id, Json.safeString(payload, "pageUrl"), lease);
            eval(script);
            String result = future.get(INLINE_RESOLVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            JsonObject object = WebCall.object(result);
            String error = Json.safeString(object, "error");
            if (!TextUtils.isEmpty(error)) throw new IllegalStateException(error);
            SpiderDebug.log("webhome-inline", "resolve ok id=%s cost=%sms url=%s", id, System.currentTimeMillis() - start, Json.safeString(object, "url"));
            return object;
        } catch (Throwable e) {
            SpiderDebug.log("webhome-inline", "resolve failed id=%s cost=%sms error=%s", id, System.currentTimeMillis() - start, e.getMessage());
            if (e instanceof Exception) throw (Exception) e;
            if (e instanceof Error) throw (Error) e;
            throw new RuntimeException(e);
        } finally {
            inlineResults.remove(id);
            controller.endInlineEvaluation(lease);
        }
    }

    private String control(JsonObject payload) {
        PlaybackService service = Server.get().getService();
        String action = Json.safeString(payload, "action");
        if (service == null) return "{}";
        App.post(() -> {
            if ("play".equals(action)) service.player().play();
            else if ("pause".equals(action)) service.player().pause();
            else if ("stop".equals(action)) service.dispatchStop();
            else if ("prev".equals(action)) service.dispatchPrev();
            else if ("next".equals(action)) service.dispatchNext();
            else if ("loop".equals(action)) service.dispatchRepeat();
            else if ("replay".equals(action)) service.dispatchReplay();
        });
        return "{}";
    }

    private JsonObject statusPayload() {
        JsonObject payload = new JsonObject();
        payload.addProperty("url", Server.get().getAddress("/media"));
        payload.addProperty("responseType", "json");
        return payload;
    }

    private String search(JsonObject payload) {
        String keyword = Json.safeString(payload, "keyword");
        String pic = Json.safeString(payload, "pic");
        String wall = wallPic(payload);
        boolean direct = payload.has("direct") && payload.get("direct").getAsBoolean();
        App.post(() -> {
            if (direct) SearchActivity.direct(activity, keyword, null, pic, wall);
            else SearchActivity.start(activity, keyword, null, pic, wall);
        });
        return "{}";
    }

    private String openLive() {
        App.post(() -> LiveActivity.start(activity));
        return "{}";
    }

    private String openVod() {
        App.post(controller::openVod);
        return "{}";
    }

    private String openKeep() {
        App.post(() -> KeepActivity.start(activity));
        return "{}";
    }

    private String openSetting() {
        App.post(controller::openSetting);
        return "{}";
    }

    private String history() {
        return App.gson().toJson(History.get());
    }

    private String checkLinks(JsonObject payload) {
        if (!Setting.isDriveCheck()) throw new IllegalStateException("网盘检测未开启");
        DriveCheckRequest request = App.gson().fromJson(payload, DriveCheckRequest.class);
        if (request == null || request.getItems().isEmpty()) throw new IllegalArgumentException("items不能为空");
        SpiderDebug.log("webhome", "pan.check count=%s", request.getItems().size());
        return App.gson().toJson(DriveCheckService.get().check(request.getItems()));
    }

    private String playPan(JsonObject payload) {
        String url = Json.safeString(payload, "url");
        String title = Json.safeString(payload, "title");
        String type = Json.safeString(payload, "type");
        String pic = Json.safeString(payload, "pic");
        String wall = wallPic(payload);
        String content = content(payload);
        if (TextUtils.isEmpty(url)) throw new IllegalArgumentException("url不能为空");
        final String playUrl = stripPush(url.trim());
        final String playTitle = TextUtils.isEmpty(title) ? playUrl : title;
        final String playPic = pic;
        final String playWall = wall;
        final String playContent = content;
        SpiderDebug.log("webhome", "pan.play route=%s type=%s title=%s url=%s", SiteApi.PUSH, type, playTitle, playUrl);
        App.post(() -> VideoActivity.start(activity, SiteApi.PUSH, playUrl, playTitle, playPic, null, playWall, playContent));
        return "{}";
    }

    private String stripPush(String url) {
        return url.regionMatches(true, 0, "push://", 0, 7) ? url.substring(7) : url;
    }

    private String cacheSet(JsonObject payload) {
        Prefers.put(cacheKey(payload), Json.safeString(payload, "value"));
        return "{}";
    }

    private String cacheDel(JsonObject payload) {
        Prefers.remove(cacheKey(payload));
        return "{}";
    }

    private String cacheKey(JsonObject payload) {
        String rule = Json.safeString(payload, "rule");
        String key = Json.safeString(payload, "key");
        return "cache_" + (TextUtils.isEmpty(rule) ? "" : rule + "_") + key;
    }

    private String device() {
        JsonObject payload = new JsonObject();
        payload.addProperty("url", Server.get().getAddress("/device"));
        return WebCall.request(payload);
    }

    private String site() {
        Site site = VodConfig.get().getHome();
        JsonObject object = new JsonObject();
        object.addProperty("key", site.getKey());
        object.addProperty("name", site.getName());
        object.addProperty("homePage", site.getHomePage());
        object.addProperty("chromeMode", site.getChromeMode());
        object.add("webHomeChrome", site.getWebHomeChrome());
        object.addProperty("type", site.getType());
        object.add("header", App.gson().toJsonTree(site.getHeader()));
        return object.toString();
    }

    private String config() {
        JsonObject object = new JsonObject();
        object.addProperty("id", VodConfig.getCid());
        object.addProperty("url", VodConfig.getUrl());
        object.addProperty("desc", VodConfig.getDesc());
        object.addProperty("driveCheck", Setting.isDriveCheck());
        return object.toString();
    }

    private String extInfo() {
        JsonObject object = new JsonObject();
        Site site = VodConfig.get().getHome();
        object.addProperty("siteKey", site.getKey());
        object.addProperty("siteName", site.getName());
        object.addProperty("homePage", site.getHomePage());
        WebHomeExtensionRegistry.Snapshot snapshot = WebHomeExtensionRegistry.get().snapshot();
        object.addProperty("enabled", snapshot.enabled);
        object.addProperty("matched", snapshot.matchedCount);
        object.addProperty("ready", snapshot.readyCount);
        return object.toString();
    }

    private String extLog(JsonObject payload) {
        WebHomeExtensionRegistry.get().recordScriptLog(payload);
        SpiderDebug.log("webhome-ext", "script message=%s data=%s", Json.safeString(payload, "message"), payload.has("data") ? payload.get("data") : "");
        return "{}";
    }

    private String extToast(JsonObject payload) {
        String message = Json.safeString(payload, "message");
        if (!TextUtils.isEmpty(message)) App.post(() -> Notify.show(message));
        return "{}";
    }

    private String setToolbar(JsonObject payload) {
        boolean visible = !payload.has("visible") || payload.get("visible").getAsBoolean();
        App.post(() -> controller.setToolbar(visible));
        return "{}";
    }

    private String setChrome(JsonObject payload) {
        App.post(() -> controller.setChrome(payload));
        return "{}";
    }

    private String restoreChrome() {
        App.post(controller::restoreChrome);
        return "{}";
    }

    private String back() {
        App.post(controller::handleBack);
        return "{}";
    }

    private String reload() {
        App.post(controller::reload);
        return "{}";
    }

    private void resolve(String requestId, String data) {
        String payload = TextUtils.isEmpty(data) ? "null" : data;
        if (payload.length() > INLINE_LIMIT) {
            String resultId = requestId + "_" + System.nanoTime();
            results.put(resultId, payload);
            payload = "{\"__fmResultId\":" + quote(resultId) + "}";
        }
        eval("window.fongmiNative&&window.fongmiNative.resolve(" + quote(requestId) + "," + payload + ")");
    }

    private void reject(String requestId, String error) {
        eval("window.fongmiNative&&window.fongmiNative.reject(" + quote(requestId) + "," + quote(error) + ")");
    }

    private void eval(String script) {
        App.post(() -> webView.evaluateJavascript(script, null));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String quote(String text) {
        return App.gson().toJson(text == null ? "" : text);
    }
}
