package com.fongmi.android.tv.web;

import android.app.Activity;
import android.content.res.Configuration;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.ui.activity.SearchActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.utils.Json;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Native implementation for the minimal, origin-bound global theme protocol. */
final class WebHomeThemeBridge {

    static final int MAX_PAGE = 10_000;

    private final HomeWebController controller;
    private final Activity activity;

    WebHomeThemeBridge(HomeWebController controller, Activity activity) {
        this.controller = controller;
        this.activity = activity;
    }

    String invoke(String method, JsonObject payload, BooleanSupplier active) throws Exception {
        if (active == null || !active.getAsBoolean()) throw new IllegalStateException("SOURCE_CHANGED");
        if (!WebHomeThemePolicy.allowsMethod(method)) throw new SecurityException("PERMISSION_DENIED");
        JsonObject safe = payload == null ? new JsonObject() : payload;
        return switch (method) {
            case "vod.home" -> vodHome(safe);
            case "vod.category" -> vodCategory(safe);
            case "player.playVod" -> playVod(safe, active);
            case "app.search" -> search(safe, active);
            case "app.openVod" -> openVod(active);
            case "app.openSetting" -> openSetting(active);
            case "ui.getViewport" -> controller.getViewportJson();
            case "navigation.back" -> back(active);
            case "navigation.reload" -> reload(active);
            default -> throw new SecurityException("PERMISSION_DENIED");
        };
    }

    private String vodHome(JsonObject payload) throws Exception {
        Site site = activeSite(payload);
        Result result = SiteApi.homeContent(site);
        return WebHomeVodContract.home(site, result, Util.isLeanback(), isLandscape(), suggestedColumns()).toString();
    }

    private String vodCategory(JsonObject payload) throws Exception {
        Site site = activeSite(payload);
        String typeId = limited(Json.safeString(payload, "typeId"), 256);
        if (TextUtils.isEmpty(typeId)) throw new IllegalArgumentException("typeId is required");
        int page = positiveInt(payload, "page", 1);
        boolean filter = booleanValue(payload, "filter", false);
        HashMap<String, String> extend = extend(payload);
        Result result = SiteApi.categoryContent(site.getKey(), typeId, String.valueOf(page), filter, extend);
        return WebHomeVodContract.category(site, typeId, page, filter, extend, result,
                Util.isLeanback(), isLandscape(), suggestedColumns()).toString();
    }

    private String playVod(JsonObject payload, BooleanSupplier active) {
        Site site = activeSite(payload);
        String vodId = limited(Json.safeString(payload, "vodId"), 2048);
        if (TextUtils.isEmpty(vodId)) throw new IllegalArgumentException("vodId is required");
        String title = limited(Json.safeString(payload, "title"), 512);
        String siteKey = site.getKey();
        postIfActive(active, () -> {
            Site current = controller.getContentSite();
            if (current == null || !siteKey.equals(current.getKey())) return;
            controller.prepareNativePlayback(() -> {
                if (active.getAsBoolean()) VideoActivity.start(activity, siteKey, vodId, title);
            });
        });
        return "{}";
    }

    private String search(JsonObject payload, BooleanSupplier active) {
        String keyword = limited(Json.safeString(payload, "keyword"), 256);
        if (TextUtils.isEmpty(keyword)) throw new IllegalArgumentException("keyword is required");
        postIfActive(active, () -> SearchActivity.start(activity, keyword, null, "", ""));
        return "{}";
    }

    private Site activeSite(JsonObject payload) {
        Site site = controller.getContentSite();
        if (site == null || TextUtils.isEmpty(site.getKey())) throw new IllegalStateException("No active VOD source");
        String requested = limited(Json.safeString(payload, "siteKey"), 256);
        if (!TextUtils.isEmpty(requested) && !requested.equals(site.getKey())) {
            throw new SecurityException("Cross-source VOD access is not allowed");
        }
        return site;
    }

    private HashMap<String, String> extend(JsonObject payload) {
        HashMap<String, String> result = new HashMap<>();
        JsonElement element = payload.get("extend");
        if (element == null || !element.isJsonObject()) return result;
        int count = 0;
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (count++ >= 32 || entry.getValue() == null || !entry.getValue().isJsonPrimitive()) continue;
            String key = limited(entry.getKey(), 64);
            String value = limited(entry.getValue().getAsString(), 512);
            if (!TextUtils.isEmpty(key)) result.put(key, value);
        }
        return result;
    }

    static int positiveInt(JsonObject payload, String key, int fallback) {
        if (payload == null || !payload.has(key)) return fallback;
        try {
            int value = payload.get(key).getAsInt();
            if (value < 1 || value > MAX_PAGE) throw new IllegalArgumentException(key + " is out of range");
            return value;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(key + " must be an integer", e);
        }
    }

    private boolean booleanValue(JsonObject payload, String key, boolean fallback) {
        try {
            return payload.has(key) ? payload.get(key).getAsBoolean() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean isLandscape() {
        return App.get().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private int suggestedColumns() {
        if (Util.isLeanback()) return 6;
        return isLandscape() ? 5 : 3;
    }

    private String openVod(BooleanSupplier active) {
        postIfActive(active, controller::openVod);
        return "{}";
    }

    private String openSetting(BooleanSupplier active) {
        postIfActive(active, controller::openSetting);
        return "{}";
    }

    private String back(BooleanSupplier active) {
        postIfActive(active, controller::handleBack);
        return "{}";
    }

    private String reload(BooleanSupplier active) {
        postIfActive(active, controller::reload);
        return "{}";
    }

    private static void postIfActive(BooleanSupplier active, Runnable action) {
        App.post(() -> {
            if (active.getAsBoolean()) action.run();
        });
    }

    private static String limited(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
