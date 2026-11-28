package com.fongmi.android.tv.playback;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.db.AppDatabase;
import com.github.catvod.utils.Prefers;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 收藏续播进度保险库。
 * 观看历史可以被用户主动清空，但清空后从收藏夹（Keep）进入剧集时仍需要记住"看到第几集、进度在哪"。
 * 写入路径有两条：
 * 1. 播放时 History 落库成功后镜像一份轻量快照（异步）；
 * 2. 用户清空/删除历史时，把即将删除的、被收藏剧集的进度同步写入（deleteAllFromUser 入口调用）。
 * 路径 2 是确定性兜底：不依赖播放时异步任务是否落盘，也不依赖集号解析是否成功。
 */
public final class ProgressVault {

    private static final String PREF_KEY = "progress_vault";
    private static final Type TYPE = new com.google.gson.reflect.TypeToken<LinkedHashMap<String, Entry>>() {}.getType();
    private static final int MAX_ENTRIES = 800;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    /** 整表 load→改→save 必须原子：异步镜像与删除兜底同步写入会并发，无锁会互相覆盖整张表。 */
    private static final Object LOCK = new Object();

    private ProgressVault() {
    }

    /** Mirrors a successfully saved History row into the vault (async, never throws). */
    public static void record(History history) {
        if (history == null || history.getPosition() <= 0) return;
        EXECUTOR.execute(() -> recordSync(history));
    }

    /**
     * Synchronously mirrors a History row into the vault. Used both by the async mirror and by
     * the delete pipeline (History clear/delete must snapshot collected items before removal).
     */
    public static void recordSync(History history) {
        Entry entry = buildEntry(history);
        if (entry == null) return;
        String siteKey = safe(history.getSiteKey());
        String vodId = safe(history.getVodId());
        // 完整读改写加统一锁：避免异步 record() 与删除兜底 recordSync() 并发时，
        // 一方基于旧表读、另一方刚写入的删除前备份被随后的 save 覆盖。
        synchronized (LOCK) {
            try {
                Map<String, Entry> map = load();
                map.put(vaultKey(history.getCid(), siteKey, vodId), entry);
                prune(map);
                save(map);
            } catch (Throwable ignored) {
            }
        }
    }

    private static Entry buildEntry(History history) {
        if (history == null || history.getPosition() <= 0) return null;
        String siteKey = safe(history.getSiteKey());
        String vodId = safe(history.getVodId());
        if (TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(vodId)) return null;
        if (SiteApi.PUSH.equals(siteKey)) return null;
        // 集号允许解析失败：保留原始集名与集地址，恢复时按 集号 → 集名 → 集地址 的顺序定位。
        int episodeNumber = history.getTmdbEpisodeNumber();
        if (episodeNumber <= 0) episodeNumber = Episode.create(history.getVodRemarks(), history.getEpisodeUrl()).getNumber();
        Entry entry = new Entry();
        entry.vodName = safe(history.getVodName());
        entry.vodPic = safe(history.getVodPic());
        entry.vodRemarks = safe(history.getVodRemarks());
        entry.episodeUrl = safe(history.getEpisodeUrl());
        entry.vodFlag = safe(history.getVodFlag());
        entry.mediaType = safe(history.getMediaType());
        entry.episodeNumber = Math.max(0, episodeNumber);
        entry.seasonNumber = Math.max(0, history.getTmdbSeasonNumber());
        entry.tmdbId = Math.max(0, history.getTmdbId());
        entry.position = Math.max(0, history.getPosition());
        entry.duration = Math.max(0, history.getDuration());
        entry.updateTime = System.currentTimeMillis();
        return entry;
    }

    /**
     * Rebuilds a transient History from the vault when the authoritative row is gone.
     * {@code lookupKey} follows the playback key shape ("site@@@vod@@@cid" or "site@@@vod").
     */
    public static History resume(int cid, String lookupKey) {
        if (TextUtils.isEmpty(lookupKey)) return null;
        String[] parts = lookupKey.split(AppDatabase.SYMBOL);
        String siteKey = parts.length > 0 ? parts[0] : "";
        String vodId = parts.length > 1 ? parts[1] : "";
        if (TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(vodId)) return null;
        if (SiteApi.PUSH.equals(siteKey)) return null;
        Entry entry;
        synchronized (LOCK) {
            try {
                entry = load().get(vaultKey(cid, siteKey, vodId));
            } catch (Throwable e) {
                return null;
            }
        }
        if (entry == null || (entry.episodeNumber <= 0 && entry.position <= 0 && TextUtils.isEmpty(entry.episodeUrl))) return null;
        History history = new History();
        history.setKey(lookupKey);
        history.setCid(cid);
        history.setVodName(entry.vodName == null ? "" : entry.vodName);
        history.setVodPic(entry.vodPic == null ? "" : entry.vodPic);
        history.setMediaType(entry.mediaType == null ? "" : entry.mediaType);
        if (entry.tmdbId > 0) history.setTmdbId(entry.tmdbId);
        history.setTmdbSeasonNumber(Math.max(0, entry.seasonNumber));
        history.setTmdbEpisodeNumber(Math.max(0, entry.episodeNumber));
        // 定位优先级：纯数字集号（跨线路可按集号命中）→ 原始集名 → 集地址兜底。
        if (entry.episodeNumber > 0) history.setVodRemarks(String.valueOf(entry.episodeNumber));
        else history.setVodRemarks(entry.vodRemarks == null ? "" : entry.vodRemarks);
        history.setEpisodeUrl(entry.episodeUrl == null ? "" : entry.episodeUrl);
        history.setVodFlag(entry.vodFlag == null ? "" : entry.vodFlag);
        history.setPosition(Math.max(0, entry.position));
        history.setDuration(Math.max(0, entry.duration));
        // createTime 必须晚于删除墓碑，否则 History.save() 会把这条反建记录判为"删除前的旧写入"而丢弃。
        history.setCreateTime(System.currentTimeMillis());
        return history;
    }

    private static String vaultKey(int cid, String siteKey, String vodId) {
        return cid + AppDatabase.SYMBOL + siteKey + AppDatabase.SYMBOL + vodId;
    }

    /** Keeps the vault bounded: drops the oldest entries beyond {@link #MAX_ENTRIES}. */
    private static void prune(Map<String, Entry> map) {
        if (map.size() <= MAX_ENTRIES) return;
        LinkedHashMap<String, Entry> kept = new LinkedHashMap<>();
        map.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().updateTime, a.getValue().updateTime))
                .limit(MAX_ENTRIES)
                .forEach(entry -> kept.put(entry.getKey(), entry.getValue()));
        map.clear();
        map.putAll(kept);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static Map<String, Entry> load() {
        try {
            String json = Prefers.getString(PREF_KEY, "");
            if (TextUtils.isEmpty(json)) return new LinkedHashMap<>();
            Map<String, Entry> map = App.gson().fromJson(json, TYPE);
            return map == null ? new LinkedHashMap<>() : map;
        } catch (Throwable e) {
            return new LinkedHashMap<>();
        }
    }

    private static void save(Map<String, Entry> map) {
        try {
            Prefers.put(PREF_KEY, App.gson().toJson(map));
        } catch (Throwable ignored) {
        }
    }

    private static final class Entry {
        String vodName;
        String vodPic;
        String vodRemarks;
        String episodeUrl;
        String vodFlag;
        String mediaType;
        int episodeNumber;
        int seasonNumber;
        int tmdbId;
        long position;
        long duration;
        long updateTime;
    }
}
