package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.UrlUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Danmaku {

    @SerializedName("name")
    private String name;
    @SerializedName("url")
    private String url;
    @SerializedName(value = "source", alternate = {"from", "site", "provider", "platform"})
    private String source;
    @SerializedName(value = "apiSourceName", alternate = {"apiSource", "api_source", "api_source_name"})
    private String apiSourceName;

    private boolean selected;

    public static List<Danmaku> arrayFrom(String str) {
        if (TextUtils.isEmpty(str)) return Collections.emptyList();
        Type listType = TypeToken.getParameterized(List.class, Danmaku.class).getType();
        str = str.trim();
        try {
            return arrayFrom(JsonParser.parseString(str), listType);
        } catch (Exception e) {
            return filter(List.of(Danmaku.from(str)));
        }
    }

    private static List<Danmaku> arrayFrom(JsonElement element, Type listType) {
        if (element == null || element.isJsonNull()) return Collections.emptyList();
        if (element.isJsonArray()) return filter(App.gson().fromJson(element, listType));
        if (element.isJsonPrimitive()) return arrayFromPrimitive(element.getAsString(), listType);
        if (!element.isJsonObject()) return Collections.emptyList();
        JsonObject object = element.getAsJsonObject();
        for (String key : new String[]{"data", "list", "result", "results", "items", "danmakus", "danmaku"}) {
            if (object.has(key)) {
                List<Danmaku> items = arrayFrom(object.get(key), listType);
                if (!items.isEmpty()) return items;
            }
        }
        return filter(List.of(App.gson().fromJson(object, Danmaku.class)));
    }

    private static List<Danmaku> arrayFromPrimitive(String text, Type listType) {
        text = TextUtils.isEmpty(text) ? "" : text.trim();
        if (text.isEmpty()) return Collections.emptyList();
        if (text.startsWith("[") || text.startsWith("{")) return arrayFrom(JsonParser.parseString(text), listType);
        return filter(List.of(Danmaku.from(text)));
    }

    private static List<Danmaku> filter(List<Danmaku> items) {
        if (items == null) return Collections.emptyList();
        return items.stream().filter(item -> item != null && !item.isEmpty()).collect(Collectors.toCollection(ArrayList::new));
    }

    public static Danmaku from(String path) {
        Danmaku danmaku = new Danmaku();
        danmaku.setName(path);
        danmaku.setUrl(path);
        return danmaku;
    }

    public static Danmaku empty() {
        return new Danmaku();
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? getUrl() : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return TextUtils.isEmpty(url) ? "" : url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSourceName() {
        String explicit = getExplicitSourceName();
        return TextUtils.isEmpty(explicit) ? getFallbackSourceName() : explicit;
    }

    private String getExplicitSourceName() {
        String api = cleanSource(apiSourceName);
        String from = cleanSource(source);
        if (!TextUtils.isEmpty(api) && !TextUtils.isEmpty(from) && !api.equals(from)) return api + " · " + from;
        if (!TextUtils.isEmpty(from)) return from;
        return api;
    }

    private String getFallbackSourceName() {
        String text = getName();
        String from = sourceAfterKeyword(text, "来源");
        if (!TextUtils.isEmpty(from)) return from;
        from = sourceAfterKeyword(text, "from");
        if (!TextUtils.isEmpty(from)) return from;
        String bracket = sourceInBracket(text);
        if (!TextUtils.isEmpty(bracket)) return bracket;
        String prefix = sourcePrefix(text);
        return TextUtils.isEmpty(prefix) ? "默认" : prefix;
    }

    private static String sourceAfterKeyword(String text, String keyword) {
        if (TextUtils.isEmpty(text)) return "";
        String lower = text.toLowerCase();
        int index = lower.indexOf(keyword.toLowerCase());
        if (index == -1) return "";
        String source = text.substring(index + keyword.length()).trim();
        if (source.startsWith(":") || source.startsWith("：")) source = source.substring(1).trim();
        return cleanSource(trimSourceTail(source));
    }

    private static String sourceInBracket(String text) {
        if (TextUtils.isEmpty(text)) return "";
        String value = text.trim();
        if (!value.startsWith("[") && !value.startsWith("【")) return "";
        int end = value.startsWith("[") ? value.indexOf(']') : value.indexOf('】');
        if (end <= 1 || end > 18) return "";
        return cleanSource(value.substring(1, end));
    }

    private static String sourcePrefix(String text) {
        if (TextUtils.isEmpty(text)) return "";
        String value = text.trim();
        for (String delimiter : new String[]{"|", "｜", " · ", " / ", "：", ":"}) {
            int index = value.indexOf(delimiter);
            if (index <= 0 || index > 18) continue;
            String prefix = cleanSource(value.substring(0, index));
            if (looksLikeSource(prefix)) return prefix;
        }
        return "";
    }

    private static boolean looksLikeSource(String text) {
        if (TextUtils.isEmpty(text)) return false;
        String lower = text.toLowerCase();
        for (String key : new String[]{"源", "弹幕", "danmaku", "dandan", "bilibili", "b站", "哔哩", "腾讯", "爱奇艺", "优酷", "芒果", "acfun", "a站", "央视", "cctv"}) {
            if (lower.contains(key)) return true;
        }
        return false;
    }

    private static String trimSourceTail(String text) {
        if (TextUtils.isEmpty(text)) return "";
        String value = text.trim();
        for (String delimiter : new String[]{" · ", " - ", "|", "｜", "/", "，", ",", "；", ";"}) {
            int index = value.indexOf(delimiter);
            if (index > 0) value = value.substring(0, index).trim();
        }
        return value;
    }

    private static String cleanSource(String text) {
        if (TextUtils.isEmpty(text)) return "";
        String value = text.trim();
        if ("null".equalsIgnoreCase(value) || "unknown".equalsIgnoreCase(value)) return "";
        return value.length() > 18 ? value.substring(0, 18) : value;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isEmpty() {
        return getUrl().isEmpty();
    }

    public String getRealUrl() {
        return UrlUtil.convert(getUrl().startsWith("/") ? "file:/" + getUrl() : getUrl());
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Danmaku it)) return false;
        return getUrl().equals(it.getUrl());
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }
}
