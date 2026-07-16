package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.title.MediaTitleParser;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class TmdbMatchCache {

    private static final String TITLE_SCOPE = "__title__";

    private Map<String, Entry> items;

    public static TmdbMatchCache objectFrom(String str) {
        try {
            TmdbMatchCache cache = App.gson().fromJson(str, TmdbMatchCache.class);
            return cache == null ? new TmdbMatchCache() : cache;
        } catch (Exception e) {
            return new TmdbMatchCache();
        }
    }

    public TmdbMatchCache() {
        this.items = new HashMap<>();
    }

    public TmdbItem find(String siteKey, String vodId) {
        if (TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(vodId)) return null;
        Entry entry = getItems().get(key(siteKey, vodId));
        return entry == null ? null : entry.toItem();
    }

    public TmdbItem find(String siteKey, String vodId, String sourceTitle) {
        if (TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(vodId)) return null;
        if (TextUtils.isEmpty(sourceTitle)) return find(siteKey, vodId);
        Entry entry = getItems().get(key(siteKey, vodId, sourceTitle));
        if (entry != null) return entry.toItem();
        Entry legacy = getItems().get(key(siteKey, vodId));
        if (isCompatible(legacy, sourceTitle)) return legacy.toItem();
        Entry title = getItems().get(titleKey(sourceTitle));
        return isCompatible(title, sourceTitle) ? title.toItem() : null;
    }

    public void put(String siteKey, String vodId, TmdbItem item) {
        if (TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(vodId) || item == null || item.getTmdbId() <= 0) return;
        getItems().put(key(siteKey, vodId), Entry.from(item));
    }

    public void put(String siteKey, String vodId, String sourceTitle, TmdbItem item) {
        if (TextUtils.isEmpty(sourceTitle)) {
            put(siteKey, vodId, item);
            return;
        }
        if (TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(vodId) || item == null || item.getTmdbId() <= 0) return;
        Entry entry = Entry.from(item);
        getItems().put(key(siteKey, vodId, sourceTitle), entry);
        putTitle(sourceTitle, entry);
    }

    public Map<String, Entry> getItems() {
        if (items == null) items = new HashMap<>();
        return items;
    }

    private String key(String siteKey, String vodId) {
        return siteKey + AppDatabase.SYMBOL + vodId;
    }

    private String key(String siteKey, String vodId, String sourceTitle) {
        return key(siteKey, vodId) + AppDatabase.SYMBOL + sourceKey(sourceTitle);
    }

    private String titleKey(String sourceTitle) {
        String title = matchTitle(sourceTitle);
        return TextUtils.isEmpty(title) ? "" : TITLE_SCOPE + AppDatabase.SYMBOL + title;
    }

    private void putTitle(String sourceTitle, Entry entry) {
        String key = titleKey(sourceTitle);
        if (TextUtils.isEmpty(key) || entry == null) return;
        Entry cached = getItems().get(key);
        if (cached == null || sameTmdb(cached, entry)) {
            getItems().put(key, entry);
        } else {
            getItems().put(key, Entry.conflict(sourceTitle));
        }
    }

    private boolean sameTmdb(Entry first, Entry second) {
        if (first == null || second == null) return false;
        return first.tmdbId > 0 && first.tmdbId == second.tmdbId && normalize(first.mediaType).equals(normalize(second.mediaType));
    }

    private boolean isCompatible(Entry entry, String sourceTitle) {
        if (entry == null || entry.tmdbId <= 0 || TextUtils.isEmpty(sourceTitle)) return false;
        String source = matchTitle(sourceTitle);
        String cached = matchTitle(entry.title);
        return TextUtils.isEmpty(source) || (!TextUtils.isEmpty(cached) && cached.equals(source));
    }

    private String sourceKey(String sourceTitle) {
        return normalize(sourceTitle).replace(AppDatabase.SYMBOL, " ");
    }

    private String matchTitle(String text) {
        return normalize(new MediaTitleParser().cleanTitle(text));
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("[\\s·•:：\\-_/\\\\|()（）\\[\\]【】]+", "").trim().toLowerCase(Locale.ROOT);
    }

    public static class Entry {

        private int tmdbId;
        private String mediaType;
        private String title;
        private String subtitle;
        private String overview;
        private String posterUrl;
        private String backdropUrl;
        private String credit;
        private double rating;
        private String originalLanguage;
        private String originCountry;
        private String department;

        public static Entry conflict(String title) {
            Entry entry = new Entry();
            entry.tmdbId = -1;
            entry.mediaType = "";
            entry.title = title;
            return entry;
        }

        public static Entry from(TmdbItem item) {
            Entry entry = new Entry();
            entry.tmdbId = item.getTmdbId();
            entry.mediaType = item.getMediaType();
            entry.title = item.getTitle();
            entry.subtitle = item.getSubtitle();
            entry.overview = item.getOverview();
            entry.posterUrl = item.getPosterUrl();
            entry.backdropUrl = item.getBackdropUrl();
            entry.credit = item.getCredit();
            entry.rating = item.getRating();
            entry.originalLanguage = item.getOriginalLanguage();
            entry.originCountry = item.getOriginCountry();
            entry.department = item.getDepartment();
            return entry;
        }

        public TmdbItem toItem() {
            return new TmdbItem(tmdbId, mediaType, title, subtitle, overview, posterUrl, backdropUrl, credit, rating, originalLanguage, originCountry, null, department);
        }
    }
}
