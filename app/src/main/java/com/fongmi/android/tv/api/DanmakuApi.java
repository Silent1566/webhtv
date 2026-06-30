package com.fongmi.android.tv.api;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Trans;

import java.util.List;
import java.util.function.Consumer;

import okhttp3.Call;
import okhttp3.Response;

public class DanmakuApi {

    private static final String TAG = DanmakuApi.class.getSimpleName();

    public static boolean canSearch() {
        return DanmakuSetting.isLoad() && DanmakuSetting.isAuto() && DanmakuSetting.hasValidApiUrl();
    }

    public static Call newCall(String name, String episode) {
        String url = DanmakuSetting.getValidApiUrl();
        if (TextUtils.isEmpty(url)) return null;
        OkHttp.cancel(TAG);
        name = Trans.t2s(name);
        episode = Trans.t2s(episode);
        try {
            if (url.contains("{name}") || url.contains("{episode}")) {
                return OkHttp.newCall(url.replace("{name}", Uri.encode(name)).replace("{episode}", Uri.encode(episode)), TAG);
            } else {
                url = getSearchUrl(url);
                if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "search name=%s episode=%s url=%s", name, episode, url);
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("name", name);
                params.put("episode", episode);
                return OkHttp.newCall(url, OkHttp.toBody(params), TAG);
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String getSearchUrl(String url) {
        Uri uri = Uri.parse(url);
        List<String> segments = uri.getPathSegments();
        if (!segments.isEmpty() && "danmaku".equalsIgnoreCase(segments.get(segments.size() - 1))) return url;
        if (segments.size() > 1) return url;
        return uri.buildUpon().appendPath("danmaku").build().toString();
    }

    public static List<Danmaku> arrayFrom(String body) {
        return normalize(Danmaku.arrayFrom(body));
    }

    private static List<Danmaku> normalize(List<Danmaku> items) {
        if (items.isEmpty()) return items;
        Uri api = Uri.parse(getSearchUrl(DanmakuSetting.getValidApiUrl()));
        if (!"https".equalsIgnoreCase(api.getScheme())) return items;
        for (Danmaku item : items) item.setUrl(normalizeUrl(api, item.getUrl()));
        return items;
    }

    private static String normalizeUrl(Uri api, String url) {
        if (TextUtils.isEmpty(url)) return url;
        try {
            Uri uri = Uri.parse(url);
            if (!"http".equalsIgnoreCase(uri.getScheme())) return url;
            if (!TextUtils.equals(api.getHost(), uri.getHost()) || api.getPort() != uri.getPort()) return url;
            String normalized = uri.buildUpon().scheme("https").build().toString();
            if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "normalize result url=%s", normalized);
            return normalized;
        } catch (Throwable e) {
            return url;
        }
    }

    public static void search(String name, String episode, Consumer<Danmaku> found) {
        Call call = newCall(name, episode);
        if (call == null) return;
        call.enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    arrayFrom(response.body().string()).stream().findFirst().ifPresent(item -> App.post(() -> found.accept(item)));
                } catch (Exception ignored) {
                }
            }
        });
    }

    public static void cancel() {
        OkHttp.cancel(TAG);
    }
}
