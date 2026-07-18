package com.fongmi.android.tv.player.lyrics;

import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class LrcLibClient {

    private static final String TAG = "lyrics";
    private static final String BASE = "https://lrclib.net/api";
    private static final String USER_AGENT = "WebHTV Lyrics/1.0 (https://github.com/fongmi)";
    private static final Type LIST_TYPE = new TypeToken<List<Entry>>() {}.getType();
    private static final OkHttpClient CLIENT = OkHttp.client()
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    public List<Entry> findCandidates(LyricsRequest request) {
        List<Entry> entries = new ArrayList<>();
        add(entries, exact(request, true));
        add(entries, exact(request, false));
        entries.addAll(search(request, true));
        if (!TextUtils.isEmpty(request.getArtist())) entries.addAll(search(request, false));
        for (String keyword : request.searchKeywords()) entries.addAll(search(keyword));
        return entries;
    }

    private Entry exact(LyricsRequest request, boolean withAlbum) {
        if (TextUtils.isEmpty(request.getTitle()) || request.getDurationSec() <= 0) return null;
        HttpUrl.Builder builder = HttpUrl.parse(BASE + "/get").newBuilder()
                .addQueryParameter("track_name", request.getTitle())
                .addQueryParameter("artist_name", request.getArtist())
                .addQueryParameter("duration", String.valueOf(request.getDurationSec()));
        if (withAlbum && !TextUtils.isEmpty(request.getAlbum())) builder.addQueryParameter("album_name", request.getAlbum());
        return get(builder.build().toString(), Entry.class);
    }

    private List<Entry> search(LyricsRequest request, boolean withArtist) {
        if (TextUtils.isEmpty(request.getTitle())) return new ArrayList<>();
        HttpUrl.Builder builder = HttpUrl.parse(BASE + "/search").newBuilder().addQueryParameter("track_name", request.getTitle());
        if (withArtist && !TextUtils.isEmpty(request.getArtist())) builder.addQueryParameter("artist_name", request.getArtist());
        List<Entry> result = get(builder.build().toString(), LIST_TYPE);
        return result == null ? new ArrayList<>() : result;
    }

    private List<Entry> search(String keyword) {
        if (TextUtils.isEmpty(keyword)) return new ArrayList<>();
        HttpUrl.Builder builder = HttpUrl.parse(BASE + "/search").newBuilder().addQueryParameter("q", keyword);
        List<Entry> result = get(builder.build().toString(), LIST_TYPE);
        return result == null ? new ArrayList<>() : result;
    }

    private void add(List<Entry> entries, Entry entry) {
        if (entry != null) entries.add(entry);
    }

    private <T> T get(String url, Type type) {
        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            return com.fongmi.android.tv.App.gson().fromJson(response.body().string(), type);
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "lrclib request failed url=%s error=%s", url, e.getMessage());
            return null;
        }
    }

    public static class Entry {
        @SerializedName("id")
        public int id;
        @SerializedName("trackName")
        public String trackName;
        @SerializedName("artistName")
        public String artistName;
        @SerializedName("albumName")
        public String albumName;
        @SerializedName("duration")
        public double duration;
        @SerializedName("instrumental")
        public boolean instrumental;
        @SerializedName("plainLyrics")
        public String plainLyrics;
        @SerializedName("syncedLyrics")
        public String syncedLyrics;
    }
}
