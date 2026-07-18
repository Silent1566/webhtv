package com.fongmi.android.tv.player.lyrics;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AudioPlaylistStore {

    private static final String KEY_PLAYLISTS = "audio_playlists";
    private static final String KEY_ACTIVE = "audio_playlist_active";
    private static final String KEY_METADATA = "audio_playlist_metadata";
    private static final String DEFAULT_NAME = "默认歌单";
    private static final int MAX_METADATA = 500;
    private static final Type LIST_TYPE = new TypeToken<List<Playlist>>() {
    }.getType();
    private static final Type METADATA_TYPE = new TypeToken<LinkedHashMap<String, Metadata>>() {
    }.getType();

    private AudioPlaylistStore() {
    }

    public static synchronized List<Playlist> list() {
        try {
            List<Playlist> items = App.gson().fromJson(Prefers.getString(KEY_PLAYLISTS), LIST_TYPE);
            return normalize(items);
        } catch (Exception e) {
            return normalize((List<Playlist>) null);
        }
    }

    public static synchronized Playlist active() {
        String id = Prefers.getString(KEY_ACTIVE);
        List<Playlist> items = list();
        for (Playlist item : items) if (TextUtils.equals(item.id, id)) return item;
        Playlist first = items.get(0);
        Prefers.put(KEY_ACTIVE, first.id);
        return first;
    }

    public static synchronized Playlist create(String name) {
        List<Playlist> items = list();
        Playlist item = new Playlist();
        item.id = UUID.randomUUID().toString();
        item.name = TextUtils.isEmpty(name) ? DEFAULT_NAME : name.trim();
        item.items = new ArrayList<>();
        items.add(item);
        save(items);
        Prefers.put(KEY_ACTIVE, item.id);
        return item;
    }

    public static synchronized void setActive(String id) {
        if (TextUtils.isEmpty(id)) return;
        for (Playlist item : list()) {
            if (!TextUtils.equals(item.id, id)) continue;
            Prefers.put(KEY_ACTIVE, id);
            return;
        }
    }

    public static synchronized void upsertItem(Entry entry) {
        if (entry == null || TextUtils.isEmpty(entry.url)) return;
        Playlist playlist = active();
        boolean updated = false;
        for (int i = 0; i < playlist.items.size(); i++) {
            Entry current = playlist.items.get(i);
            if (!TextUtils.equals(current.url, entry.url)) continue;
            playlist.items.set(i, normalize(entry));
            updated = true;
            break;
        }
        if (!updated) playlist.items.add(normalize(entry));
        upsertPlaylist(playlist);
    }

    public static synchronized void removeItem(String url) {
        if (TextUtils.isEmpty(url)) return;
        Playlist playlist = active();
        playlist.items.removeIf(item -> TextUtils.equals(item.url, url));
        upsertPlaylist(playlist);
    }

    public static synchronized void upsertPlaylist(Playlist playlist) {
        if (playlist == null) return;
        List<Playlist> items = list();
        Playlist normalized = normalize(playlist);
        for (int i = 0; i < items.size(); i++) {
            if (!TextUtils.equals(items.get(i).id, normalized.id)) continue;
            items.set(i, normalized);
            save(items);
            return;
        }
        items.add(normalized);
        save(items);
    }

    public static synchronized Metadata getMetadata(String url) {
        if (TextUtils.isEmpty(url)) return null;
        return getMetadata().get(url);
    }

    public static synchronized void putMetadata(String url, String title, String artist) {
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(title)) return;
        LinkedHashMap<String, Metadata> items = getMetadata();
        Metadata item = items.get(url);
        if (item == null) item = new Metadata();
        item.title = title.trim();
        if (!TextUtils.isEmpty(artist)) item.artist = artist.trim();
        item.updatedAt = System.currentTimeMillis();
        items.put(url, item);
        trimMetadata(items);
        Prefers.put(KEY_METADATA, App.gson().toJson(items));
    }

    private static LinkedHashMap<String, Metadata> getMetadata() {
        try {
            Map<String, Metadata> source = App.gson().fromJson(Prefers.getString(KEY_METADATA), METADATA_TYPE);
            LinkedHashMap<String, Metadata> result = new LinkedHashMap<>();
            if (source != null) {
                for (Map.Entry<String, Metadata> entry : source.entrySet()) {
                    if (TextUtils.isEmpty(entry.getKey()) || entry.getValue() == null || TextUtils.isEmpty(entry.getValue().title)) continue;
                    result.put(entry.getKey(), entry.getValue());
                }
            }
            return result;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static void trimMetadata(LinkedHashMap<String, Metadata> items) {
        while (items.size() > MAX_METADATA) {
            String oldestKey = null;
            long oldestTime = Long.MAX_VALUE;
            for (Map.Entry<String, Metadata> entry : items.entrySet()) {
                long updatedAt = entry.getValue() == null ? 0 : entry.getValue().updatedAt;
                if (oldestKey != null && updatedAt >= oldestTime) continue;
                oldestKey = entry.getKey();
                oldestTime = updatedAt;
            }
            if (oldestKey == null) return;
            items.remove(oldestKey);
        }
    }

    private static void save(List<Playlist> items) {
        Prefers.put(KEY_PLAYLISTS, App.gson().toJson(normalize(items)));
    }

    private static List<Playlist> normalize(List<Playlist> items) {
        List<Playlist> result = new ArrayList<>();
        if (items != null) {
            for (Playlist item : items) {
                Playlist normalized = normalize(item);
                if (normalized != null) result.add(normalized);
            }
        }
        if (result.isEmpty()) {
            Playlist item = new Playlist();
            item.id = UUID.randomUUID().toString();
            item.name = DEFAULT_NAME;
            item.items = new ArrayList<>();
            result.add(item);
        }
        return result;
    }

    private static Playlist normalize(Playlist item) {
        if (item == null) return null;
        if (TextUtils.isEmpty(item.id)) item.id = UUID.randomUUID().toString();
        if (TextUtils.isEmpty(item.name)) item.name = DEFAULT_NAME;
        if (item.items == null) item.items = new ArrayList<>();
        List<Entry> entries = new ArrayList<>();
        for (Entry entry : item.items) {
            Entry normalized = normalize(entry);
            if (normalized != null) entries.add(normalized);
        }
        item.items = entries;
        return item;
    }

    private static Entry normalize(Entry entry) {
        if (entry == null || TextUtils.isEmpty(entry.url)) return null;
        if (TextUtils.isEmpty(entry.name)) entry.name = entry.title;
        if (TextUtils.isEmpty(entry.title)) entry.title = entry.name;
        return entry;
    }

    public static class Playlist {
        public String id = "";
        public String name = "";
        public List<Entry> items = new ArrayList<>();
    }

    public static class Entry {
        public String name = "";
        public String url = "";
        public String playFlag = "";
        public String title = "";
        public String artist = "";
        public String pic = "";
        public String lyrics = "";
    }

    public static class Metadata {
        public String title = "";
        public String artist = "";
        public long updatedAt;
    }
}
