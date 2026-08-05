package com.fongmi.android.tv.bean;

import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Diffable;
import com.fongmi.android.tv.history.HistoryDisplayPolicy;
import com.fongmi.android.tv.player.VideoAspectMode;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Entity
public class History implements Diffable<History> {

    private static final long HISTORY_REFRESH_DEBOUNCE = 500;
    private static final Runnable HISTORY_REFRESH = RefreshEvent::history;
    private static final long NEAR_END_MIN_MS = TimeUnit.SECONDS.toMillis(5);
    private static final long NEAR_END_MAX_MS = TimeUnit.SECONDS.toMillis(30);

    @NonNull
    @PrimaryKey
    @SerializedName("key")
    private String key;
    @SerializedName("vodPic")
    private String vodPic;
    @SerializedName("wallPic")
    private String wallPic;
    @SerializedName("vodName")
    private String vodName;
    @SerializedName("vodFlag")
    private String vodFlag;
    @SerializedName("vodRemarks")
    private String vodRemarks;
    @SerializedName("episodeUrl")
    private String episodeUrl;
    @SerializedName("revSort")
    private boolean revSort;
    @SerializedName("revPlay")
    private boolean revPlay;
    @SerializedName("createTime")
    private long createTime;
    @SerializedName("opening")
    private long opening;
    @SerializedName("ending")
    private long ending;
    @SerializedName("position")
    private long position;
    @SerializedName("duration")
    private long duration;
    @SerializedName("speed")
    private float speed;
    @SerializedName("speedOverride")
    @ColumnInfo(defaultValue = "0")
    private boolean speedOverride;
    @SerializedName("scale")
    private int scale;
    @SerializedName("cid")
    private int cid;
    @SerializedName("typeName")
    private String typeName;
    @SerializedName("area")
    private String area;
    @SerializedName("actor")
    private String actor;
    @SerializedName("director")
    private String director;
    @SerializedName("year")
    private String year;
    @SerializedName("tmdbId")
    @ColumnInfo(defaultValue = "0")
    private int tmdbId;
    @SerializedName("mediaType")
    @ColumnInfo(defaultValue = "")
    private String mediaType;
    @SerializedName("legacyKey")
    @ColumnInfo(defaultValue = "")
    private String legacyKey;
    @SerializedName("tmdbSeasonNumber")
    @ColumnInfo(defaultValue = "0")
    private int tmdbSeasonNumber;
    @SerializedName("tmdbEpisodeNumber")
    @ColumnInfo(defaultValue = "0")
    private int tmdbEpisodeNumber;

    private transient int player = PlayerSetting.NONE;
    private transient long updateTime;
    private transient String playbackSourceKey;

    public History() {
        this.speed = 1;
        this.scale = -1;
        this.ending = C.TIME_UNSET;
        this.opening = C.TIME_UNSET;
        this.position = C.TIME_UNSET;
        this.duration = C.TIME_UNSET;
    }

    public History copy() {
        History item = new History();
        item.key = key;
        item.vodPic = vodPic;
        item.wallPic = wallPic;
        item.vodName = vodName;
        item.vodFlag = vodFlag;
        item.vodRemarks = vodRemarks;
        item.episodeUrl = episodeUrl;
        item.revSort = revSort;
        item.revPlay = revPlay;
        item.createTime = createTime;
        item.opening = opening;
        item.ending = ending;
        item.position = position;
        item.duration = duration;
        item.speed = speed;
        item.speedOverride = speedOverride;
        item.scale = scale;
        item.cid = cid;
        item.typeName = typeName;
        item.area = area;
        item.actor = actor;
        item.director = director;
        item.year = year;
        item.tmdbId = tmdbId;
        item.mediaType = mediaType;
        item.legacyKey = legacyKey;
        item.tmdbSeasonNumber = tmdbSeasonNumber;
        item.tmdbEpisodeNumber = tmdbEpisodeNumber;
        item.player = player;
        item.updateTime = updateTime;
        item.playbackSourceKey = playbackSourceKey;
        return item;
    }

    public static History objectFrom(String str) {
        return App.gson().fromJson(str, History.class);
    }

    public static List<History> arrayFrom(String str) {
        Type listType = TypeToken.getParameterized(List.class, History.class).getType();
        List<History> items = App.gson().fromJson(str, listType);
        return items == null ? Collections.emptyList() : items;
    }

    public static List<History> get() {
        return get(VodConfig.getCid());
    }

    public static List<History> getForDisplay() {
        if (!Setting.isGlobalHistoryEnabled()) return get();
        return HistoryDisplayPolicy.project(AppDatabase.get().getHistoryDao().findAll(), Setting.isHistoryAggregationEffective());
    }

    public static List<History> getAll() {
        return HistoryDisplayPolicy.project(AppDatabase.get().getHistoryDao().findAll(), Setting.isHistoryAggregationEffective());
    }

    public static List<History> get(int cid) {
        List<History> items = AppDatabase.get().getHistoryDao().find(cid);
        return Setting.isHistoryAggregationEffective() ? HistoryDisplayPolicy.project(items, true) : items;
    }

    public static History find(String key) {
        return AppDatabase.get().getHistoryDao().find(VodConfig.getCid(), key);
    }

    public static History find(int cid, String key) {
        return AppDatabase.get().getHistoryDao().find(cid, key);
    }

    public static History findPlayback(String key, String vodName, List<Flag> flags) {
        return findPlayback(key, Collections.singletonList(vodName), flags);
    }

    public static History findPlayback(String key, List<String> vodNames, List<Flag> flags) {
        return findPlayback(key, vodNames, flags, 0, "", -1);
    }

    /**
     * @param explicitTmdbItem 调用方（如从 TMDB 详情进入播放）已知的完整 TMDB 身份，优先用它跨源查找，
     *                         避免依赖尚未完成的异步匹配缓存，也避免电影与剧集数字 ID 相同时串记录。
     */
    public static History findPlayback(String key, List<String> vodNames, List<Flag> flags, TmdbItem explicitTmdbItem) {
        return findPlayback(key, vodNames, flags, explicitTmdbItem, -1);
    }

    /**
     * Season-aware playback lookup. A non-negative expectedSeason prevents a progress record from another
     * season (or an unknown-season legacy record from another source key) from being reused.
     */
    public static History findPlayback(String key, List<String> vodNames, List<Flag> flags, TmdbItem explicitTmdbItem, int expectedSeason) {
        int tmdbId = explicitTmdbItem == null ? 0 : explicitTmdbItem.getTmdbId();
        String mediaType = explicitTmdbItem == null ? "" : explicitTmdbItem.getMediaType();
        return findPlayback(key, vodNames, flags, tmdbId, mediaType, expectedSeason);
    }

    private static History findPlayback(String key, List<String> vodNames, List<Flag> flags, int explicitTmdbId, String explicitMediaType, int expectedSeason) {
        // 聚合模式：优先按完整 TMDB 身份跨源查找最新历史，命中则复用该记录并切换到当前 key。
        History aggregated = findPlaybackByTmdb(key, vodNames, flags, explicitTmdbId, explicitMediaType, expectedSeason);
        if (aggregated != null) return aggregated;
        History history = find(key);
        if (isSeasonEligible(history, key, expectedSeason)) return history;
        if (vodNames != null) {
            for (String vodName : vodNames) {
                if (vodName == null || vodName.isEmpty()) continue;
                history = findPlaybackCandidate(key, findByName(vodName), flags, expectedSeason);
                if (history != null) return history.cid(VodConfig.getCid());
            }
        }
        return null;
    }

    /**
     * 聚合模式下按 (mediaType, tmdbId) 跨源查找历史。
     * 优先用调用方传入的完整身份，其次从 key/剧名解析。
     * 命中当前 key 自身记录时直接返回；否则走 findPlaybackCandidate 做跨源剧集匹配
     * （按剧集名/URL 对齐源 B 的线路与集数），仅复用可续播的进度。
     */
    private static History findPlaybackByTmdb(String key, List<String> vodNames, List<Flag> flags, int explicitTmdbId, String explicitMediaType, int expectedSeason) {
        if (!Setting.isHistoryAggregationEffective()) return null;
        TmdbIdentity identity = explicitTmdbId > 0 && !normalizeMediaType(explicitMediaType).isEmpty()
                ? new TmdbIdentity(explicitTmdbId, normalizeMediaType(explicitMediaType))
                : resolveTmdbIdentity(key, vodNames);
        if (identity == null) return null;
        List<History> list = AppDatabase.get().getHistoryDao().findByTmdbIdentity(VodConfig.getCid(), identity.mediaType(), identity.tmdbId());
        if (list.isEmpty()) return null;
        // 整剧级统一进度：不再因当前 key 命中自身记录就直接返回该源旧进度，
        // 统一交给 findPlaybackCandidate 在全部同剧记录中选「最近可续播」的那条
        // （list 已按 createTime DESC 排序），使回到任一源都续到全剧最新进度。
        return findPlaybackCandidate(key, list, flags, expectedSeason);
    }

    /**
     * 从 key（siteKey@@@vodId）与候选剧名解析完整 TMDB 身份。
     * 优先读当前 key 已存历史记录里的 tmdbId/mediaType（来自 DB，可靠），
     * 未命中再回退到 TmdbMatchCache 按 siteKey/vodId/名称查询（异步匹配可能尚未完成）。
     */
    private static TmdbIdentity resolveTmdbIdentity(String key, List<String> vodNames) {
        if (TextUtils.isEmpty(key)) return null;
        History existing = find(key);
        if (existing != null && existing.getTmdbId() > 0 && !normalizeMediaType(existing.getMediaType()).isEmpty()) {
            return new TmdbIdentity(existing.getTmdbId(), normalizeMediaType(existing.getMediaType()));
        }
        String[] parts = key.split(AppDatabase.SYMBOL);
        if (parts.length < 2) return null;
        String siteKey = parts[0];
        String vodId = parts[1];
        TmdbMatchCache cache = Setting.getTmdbMatchCache();
        if (vodNames != null) {
            for (String vodName : vodNames) {
                TmdbItem item = cache.find(siteKey, vodId, vodName);
                TmdbIdentity identity = tmdbIdentity(item);
                if (identity != null) return identity;
            }
        }
        TmdbItem item = cache.find(siteKey, vodId);
        return tmdbIdentity(item);
    }

    private static TmdbIdentity tmdbIdentity(TmdbItem item) {
        if (item == null || item.getTmdbId() <= 0) return null;
        String mediaType = normalizeMediaType(item.getMediaType());
        return mediaType.isEmpty() ? null : new TmdbIdentity(item.getTmdbId(), mediaType);
    }

    private static String normalizeMediaType(String value) {
        if (value == null) return "";
        String mediaType = value.trim().toLowerCase(Locale.ROOT);
        return "movie".equals(mediaType) || "tv".equals(mediaType) ? mediaType : "";
    }

    private record TmdbIdentity(int tmdbId, String mediaType) {
    }

    public static List<History> findByName(String name) {
        try {
            return AppDatabase.get().getHistoryDao().findByName(VodConfig.getCid(), name);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public static void delete(int cid) {
        if (AppDatabase.get().getHistoryDao().delete(cid) > 0) notifyChanged();
    }

    public static void deleteForDisplay() {
        if (Setting.isGlobalHistoryEnabled()) {
            List<History> items = AppDatabase.get().getHistoryDao().findAll();
            int deleted = AppDatabase.get().getHistoryDao().delete();
            for (History item : items) AppDatabase.get().getTrackDao().delete(item.getKey());
            if (deleted > 0) notifyChanged();
        } else {
            delete(VodConfig.getCid());
        }
    }

    public static void sync(List<History> targets) {
        targets.forEach(target -> {
            if (!target.canMergeByName()) {
                target.cid(VodConfig.getCid()).save();
                return;
            }
            List<History> items = findByName(target.getVodName());
            if (items.isEmpty()) target.cid(VodConfig.getCid()).save();
            else {
                long latestTime = items.stream().mapToLong(History::getCreateTime).max().orElse(0L);
                if (target.getCreateTime() > latestTime) target.cid(VodConfig.getCid()).merge(items, true).save();
            }
        });
    }

    static History findPlaybackCandidate(String key, List<History> items, List<Flag> flags) {
        return findPlaybackCandidate(key, items, flags, -1);
    }

    static History findPlaybackCandidate(String key, List<History> items, List<Flag> flags, int expectedSeason) {
        if (items == null || items.isEmpty()) return null;
        for (History item : items) {
            if (isSeasonEligible(item, key, expectedSeason) && canResume(item) && matchesAnyEpisode(item, flags)) {
                return copyForPlaybackKey(item, key);
            }
        }
        for (History item : items) {
            if (isSeasonEligible(item, key, expectedSeason) && canResume(item)) return copyForPlaybackKey(item, key);
        }
        for (History item : items) {
            if (isSeasonEligible(item, key, expectedSeason)) return copyForPlaybackKey(item, key);
        }
        return null;
    }

    private static boolean isSeasonEligible(History item, String requestedKey, int expectedSeason) {
        if (item == null) return false;
        if (expectedSeason < 0) return true;
        int savedSeason = item.getTmdbSeasonNumber();
        boolean hasKnownSeason = savedSeason > 0 || (savedSeason == 0 && item.getTmdbEpisodeNumber() > 0);
        if (hasKnownSeason) return savedSeason == expectedSeason;
        return TextUtils.equals(requestedKey, item.getKey());
    }

    private static boolean canResume(History item) {
        return item != null && item.getPosition() > 0;
    }

    private static History copyForPlaybackKey(History item, String key) {
        History copy = item.copy();
        if (key != null && !key.isEmpty() && !TextUtils.equals(key, item.getKey())) {
            copy.playbackSourceKey = item.getKey();
            copy.setKey(key);
        }
        return copy;
    }

    private static boolean matchesAnyEpisode(History item, List<Flag> flags) {
        if (item == null || flags == null || flags.isEmpty()) return false;
        for (Flag flag : flags) {
            if (flag == null || flag.getEpisodes() == null) continue;
            for (Episode episode : flag.getEpisodes()) if (matchesEpisode(item, episode)) return true;
        }
        return false;
    }

    private static boolean matchesEpisode(History item, Episode episode) {
        if (episode == null) return false;
        Episode saved = item.getEpisode();
        if (item.getTmdbEpisodeNumber() > 0 && episode.getTmdbEpisode() != null && episode.getTmdbEpisode().getNumber() > 0) {
            return episode.matchesNumber(saved);
        }
        String episodeUrl = item.getEpisodeUrl();
        if (!episodeUrl.isEmpty() && episodeUrl.equals(episode.getUrl())) return true;
        String remarks = item.getVodRemarks();
        boolean match = !remarks.isEmpty() && (remarks.equalsIgnoreCase(episode.getName()) || remarks.equals(episode.getDisplayName()));
        if (!match) {
            // 跨源同剧集名格式常不同（如「第2集」与「2. 众人在燕歌坊遇刺客」），严格比对会失败，
            // 退化到源自身旧记录。此处所有候选同属一个 tmdbId（同一部剧），集号即可靠同集判据，
            // 故统一交给 Episode.matchesNumber：优先使用已绑定的 TMDB 标准集号，旧记录再回退源集名提取。
            match = episode.matchesNumber(saved);
        }
        return match;
    }

    @NonNull
    public String getKey() {
        return key;
    }

    public void setKey(@NonNull String key) {
        this.key = key;
    }

    public String getVodPic() {
        return vodPic;
    }

    public void setVodPic(String vodPic) {
        this.vodPic = vodPic;
    }

    public String getWallPic() {
        return wallPic == null ? "" : wallPic;
    }

    public void setWallPic(String wallPic) {
        this.wallPic = wallPic;
    }

    public String getVodName() {
        return vodName;
    }

    public void setVodName(String vodName) {
        this.vodName = vodName;
    }

    public String getVodFlag() {
        return vodFlag;
    }

    public void setVodFlag(String vodFlag) {
        this.vodFlag = vodFlag;
    }

    public String getVodRemarks() {
        return vodRemarks == null ? "" : vodRemarks;
    }

    public void setVodRemarks(String vodRemarks) {
        this.vodRemarks = vodRemarks;
    }

    public String getEpisodeUrl() {
        return episodeUrl == null ? "" : episodeUrl;
    }

    public void setEpisodeUrl(String episodeUrl) {
        this.episodeUrl = episodeUrl;
    }

    public boolean isRevSort() {
        return revSort;
    }

    public void setRevSort(boolean revSort) {
        this.revSort = revSort;
    }

    public boolean isRevPlay() {
        return revPlay;
    }

    public void setRevPlay(boolean revPlay) {
        this.revPlay = revPlay;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public long getOpening() {
        return opening;
    }

    public void setOpening(long opening) {
        this.opening = opening;
    }

    public long getEnding() {
        return ending;
    }

    public void setEnding(long ending) {
        this.ending = ending;
    }

    public long getPosition() {
        return position;
    }

    public void setPosition(long position) {
        this.position = position;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public boolean getSpeedOverride() {
        return speedOverride;
    }

    public void setSpeedOverride(boolean speedOverride) {
        this.speedOverride = speedOverride;
    }

    public boolean hasUserSpeed() {
        return speedOverride || (speed > 0 && Math.abs(speed - 1.0f) > 0.001f);
    }

    public float getPlaybackSpeed(float defaultSpeed) {
        return hasUserSpeed() && speed > 0 ? speed : defaultSpeed;
    }

    public void setUserSpeed(float speed) {
        this.speed = speed;
        this.speedOverride = true;
    }

    public int getScale() {
        return scale == -1 ? -1 : VideoAspectMode.sanitize(scale);
    }

    public void setScale(int scale) {
        this.scale = VideoAspectMode.sanitize(scale);
    }

    public int getPlayer() {
        return player;
    }

    public int getPlayerOrDefault() {
        return PlayerSetting.resolvePlayer(player);
    }

    public boolean hasPlayer() {
        return PlayerSetting.isPlayer(player);
    }

    public void setPlayer(int player) {
        this.player = PlayerSetting.sanitizePlayer(player);
    }

    public int getCid() {
        return cid;
    }

    public void setCid(int cid) {
        this.cid = cid;
    }

    public History cid(int cid) {
        setCid(cid);
        return this;
    }

    public String getTypeName() {
        return typeName == null ? "" : typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public String getArea() {
        return area == null ? "" : area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public String getActor() {
        return actor == null ? "" : actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getDirector() {
        return director == null ? "" : director;
    }

    public void setDirector(String director) {
        this.director = director;
    }

    public String getYear() {
        return year == null ? "" : year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public int getTmdbId() {
        return tmdbId;
    }

    public void setTmdbId(int tmdbId) {
        this.tmdbId = tmdbId;
    }

    public String getMediaType() {
        return mediaType == null ? "" : mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getLegacyKey() {
        return legacyKey == null ? "" : legacyKey;
    }

    public void setLegacyKey(String legacyKey) {
        this.legacyKey = legacyKey;
    }

    public int getTmdbSeasonNumber() {
        return tmdbSeasonNumber;
    }

    public void setTmdbSeasonNumber(int tmdbSeasonNumber) {
        this.tmdbSeasonNumber = Math.max(-1, tmdbSeasonNumber);
    }

    public int getTmdbEpisodeNumber() {
        return tmdbEpisodeNumber;
    }

    public void setTmdbEpisodeNumber(int tmdbEpisodeNumber) {
        this.tmdbEpisodeNumber = Math.max(0, tmdbEpisodeNumber);
        if (this.tmdbEpisodeNumber == 0) this.tmdbSeasonNumber = 0;
    }

    public boolean setTmdbEpisodePosition(Episode episode) {
        TmdbEpisode tmdbEpisode = episode == null ? null : episode.getTmdbEpisode();
        int episodeNumber = tmdbEpisode == null ? 0 : tmdbEpisode.getNumber();
        int seasonNumber = tmdbEpisode == null ? 0 : tmdbEpisode.getSeasonNumber();
        return setTmdbEpisodePosition(seasonNumber, episodeNumber);
    }

    public boolean setTmdbEpisodePosition(int seasonNumber, int episodeNumber) {
        episodeNumber = Math.max(0, episodeNumber);
        seasonNumber = episodeNumber == 0 ? 0 : Math.max(-1, seasonNumber);
        if (tmdbSeasonNumber == seasonNumber && tmdbEpisodeNumber == episodeNumber) return false;
        tmdbSeasonNumber = seasonNumber;
        tmdbEpisodeNumber = episodeNumber;
        return true;
    }

    /**
     * 仅在本记录对应字段为空时补齐富集元数据（题材/地区/演员/主创/年份）。
     * 用于老记录重新播放时逐步补全，供观影报告统计使用。避免用空值覆盖已有数据。
     *
     * @return 是否有任一字段被补齐
     */
    public boolean enrichMeta(String typeName, String area, String actor, String director, String year) {
        boolean changed = false;
        if (getTypeName().isEmpty() && !android.text.TextUtils.isEmpty(typeName)) { this.typeName = typeName; changed = true; }
        if (getArea().isEmpty() && !android.text.TextUtils.isEmpty(area)) { this.area = area; changed = true; }
        if (getActor().isEmpty() && !android.text.TextUtils.isEmpty(actor)) { this.actor = actor; changed = true; }
        if (getDirector().isEmpty() && !android.text.TextUtils.isEmpty(director)) { this.director = director; changed = true; }
        if (getYear().isEmpty() && !android.text.TextUtils.isEmpty(year)) { this.year = year; changed = true; }
        return changed;
    }

    public String getSiteName() {
        if (getCid() == VodConfig.getCid()) {
            if (SiteApi.PUSH.equals(getSiteKey())) return "";
            Site site = VodConfig.get().getSite(getSiteKey());
            if (!site.isEmpty()) return site.getDisplayName();
        }
        return ResUtil.getString(R.string.history_other_config);
    }

    public boolean isCurrentSourceAvailable() {
        if (getCid() != VodConfig.getCid()) return false;
        return SiteApi.PUSH.equals(getSiteKey()) || !VodConfig.get().getSite(getSiteKey()).isEmpty();
    }

    public String getSiteKey() {
        return getKey().split(AppDatabase.SYMBOL)[0];
    }

    public String getVodId() {
        String[] parts = Objects.toString(getKey(), "").split(AppDatabase.SYMBOL);
        return parts.length > 1 ? parts[1] : "";
    }

    public Flag getFlag() {
        return Flag.create(getVodFlag());
    }

    public Episode getEpisode() {
        Episode episode = Episode.create(getVodRemarks(), getEpisodeUrl());
        if (getTmdbEpisodeNumber() > 0) {
            episode.setTmdbEpisode(new TmdbEpisode(getTmdbEpisodeNumber(), "", "", "", "", 0, 0, 0, getTmdbSeasonNumber()));
        }
        return episode;
    }

    /**
     * 是否为跨源聚合复制出的播放记录（key 已切到当前源，但进度沿用自 playbackSourceKey 指向的源）。
     * 跨源时线路名与剧集 URL 必然不同，判断"是否同一集"应改用集名而非线路/URL。
     */
    public History forPlaybackKey(String key, int cid) {
        History copy = copy();
        copy.playbackSourceKey = getKey();
        copy.setKey(key);
        copy.setCid(cid);
        return copy;
    }

    public boolean isCrossSourcePlayback() {
        return !TextUtils.isEmpty(playbackSourceKey);
    }

    public int getSiteVisible() {
        return TextUtils.isEmpty(getSiteName()) ? View.GONE : View.VISIBLE;
    }

    public boolean hasPlaybackTime() {
        return getPosition() >= 0 && getDuration() > 0;
    }

    public String getPlaybackTimeText() {
        if (!hasPlaybackTime()) return "";
        long duration = Math.max(0, getDuration());
        long position = Math.max(0, Math.min(getPosition(), duration));
        return Util.timeMs(position) + " / " + Util.timeMs(duration);
    }

    public int getRevPlayText() {
        return isRevPlay() ? R.string.play_backward : R.string.play_forward;
    }

    public int getRevPlayHint() {
        return isRevPlay() ? R.string.play_backward_hint : R.string.play_forward_hint;
    }

    private boolean isPushHistory() {
        return key != null && key.startsWith(SiteApi.PUSH + AppDatabase.SYMBOL);
    }

    private boolean canMergeByName() {
        return !isPushHistory();
    }

    boolean shouldMerge(History item, boolean force) {
        if (!canMergeByName() || !item.canMergeByName()) return false;
        // 聚合模式下跨源合并由 tmdbId 统一接管：列表按 TMDB 身份折叠（HistoryDisplayPolicy），
        // 续播按 tmdbId 跨源找最新进度（findPlaybackByTmdb）。二者都要求各源记录保留在库中。
        // 而遗留的 name-merge 会 copyTo(this).delete() 物理删掉同名记录，且 delete() 在聚合模式下
        // 会按 tmdbId 级联删除（见 delete()），导致同剧的 TMDB 记录与原生记录互相覆盖、进度丢失。
        // 因此聚合生效时禁用 name-merge，改由 tmdbId 聚合处理，避免破坏跨源续播所需的多源记录。
        if (Setting.isHistoryAggregationEffective()) return false;
        if (!force && TextUtils.equals(getKey(), item.getKey())) return false;
        if (!force && TextUtils.equals(getKey(), item.playbackSourceKey)) return false;
        if (force) return true;
        if (getDuration() <= 0 || item.getDuration() <= 0) return false;
        return Math.abs(getDuration() - item.getDuration()) <= TimeUnit.MINUTES.toMillis(10);
    }

    private History copyTo(History item) {
        if (getOpening() > 0) item.setOpening(getOpening());
        if (getEnding() > 0) item.setEnding(getEnding());
        if (hasUserSpeed()) item.setUserSpeed(getSpeed());
        return this;
    }

    public boolean canSave() {
        return getPosition() > 0;
    }

    public boolean isNearEnding() {
        if (getPosition() <= 0 || getDuration() <= 0) return false;
        long threshold = Math.min(NEAR_END_MAX_MS, Math.max(NEAR_END_MIN_MS, getDuration() / 100));
        long remaining = getDuration() - getPosition();
        return remaining >= 0 && remaining <= threshold;
    }

    public void resetPlaybackPosition() {
        setPosition(C.TIME_UNSET);
        setDuration(C.TIME_UNSET);
    }

    public boolean canSync() {
        return System.currentTimeMillis() - getUpdateTime() > 5000;
    }

    public History merge() {
        merge(false);
        return this;
    }

    private History merge(boolean force) {
        if (!canMergeByName()) return this;
        return merge(findByName(getVodName()), force);
    }

    private History merge(List<History> items, boolean force) {
        for (History item : items) if (item.shouldMerge(this, force)) item.copyTo(this).delete();
        return this;
    }

    /**
     * 将历史记录的 key 迁移到新值。
     * 仅在 key 实际变化时删除旧 key，避免同 key 先删后写失败导致历史消失。
     * 迁移后立即 save 新 key，缩短「旧已删、新未写」窗口。
     */
    public void replace(String key) {
        if (TextUtils.isEmpty(key) || TextUtils.equals(getKey(), key)) return;
        String previous = getKey();
        setKey(key);
        if (!TextUtils.isEmpty(previous)) {
            AppDatabase.get().getHistoryDao().delete(VodConfig.getCid(), previous);
            AppDatabase.get().getTrackDao().delete(previous);
        }
        save();
    }

    public History save(int cid) {
        return cid(cid).merge(true).save();
    }

    private void enrichTmdbId() {
        if (tmdbId > 0 || !Setting.isHistoryAggregationEffective()) return;
        String siteKey = getSiteKey();
        String vodId = getVodId();
        if (TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(vodId)) return;
        TmdbItem item = Setting.getTmdbMatchCache().find(siteKey, vodId, getVodName());
        if (item != null && item.getTmdbId() > 0) {
            this.tmdbId = item.getTmdbId();
            this.mediaType = item.getMediaType();
        }
    }

    public History save() {
        enrichTmdbId();
        History before = find(getKey());
        boolean notify = recommendationSignalsChanged(before, this);
        updateTime = System.currentTimeMillis();
        AppDatabase.get().getHistoryDao().insertOrUpdate(this);
        if (notify) notifyChanged();
        return this;
    }

    public History delete() {
        return deleteRelated(false);
    }

    public History deleteDisplayItem() {
        return deleteRelated(Setting.isGlobalHistoryEnabled());
    }

    private History deleteRelated(boolean global) {
        boolean deleted;
        List<History> relatedItems = Collections.emptyList();
        String identity = HistoryDisplayPolicy.tmdbIdentity(this);
        if (!identity.isEmpty() && Setting.isHistoryAggregationEffective()) {
            String mediaType = identity.substring(0, identity.indexOf(':'));
            relatedItems = global
                    ? AppDatabase.get().getHistoryDao().findByTmdbIdentity(mediaType, getTmdbId())
                    : AppDatabase.get().getHistoryDao().findByTmdbIdentity(getCid(), mediaType, getTmdbId());
        }
        if (!relatedItems.isEmpty()) {
            deleted = true;
            for (History item : relatedItems) {
                AppDatabase.get().getHistoryDao().delete(item.getCid(), item.getKey());
                AppDatabase.get().getTrackDao().delete(item.getKey());
            }
        } else {
            deleted = AppDatabase.get().getHistoryDao().delete(getCid(), getKey()) > 0;
            AppDatabase.get().getTrackDao().delete(getKey());
        }
        if (deleted) notifyChanged();
        return this;
    }

    private static void notifyChanged() {
        App.post(HISTORY_REFRESH, HISTORY_REFRESH_DEBOUNCE);
    }

    static boolean recommendationSignalsChanged(History before, History after) {
        if (after == null || TextUtils.isEmpty(after.getVodName())) return false;
        if (before == null) return true;
        return before.getCid() != after.getCid()
                || !Objects.equals(before.getKey(), after.getKey())
                || !Objects.equals(before.getVodName(), after.getVodName())
                || !Objects.equals(before.getTypeName(), after.getTypeName())
                || !Objects.equals(before.getArea(), after.getArea())
                || !Objects.equals(before.getActor(), after.getActor())
                || !Objects.equals(before.getDirector(), after.getDirector())
                || !Objects.equals(before.getYear(), after.getYear());
    }

    public void findEpisode(List<Flag> flags) {
        if (flags.isEmpty()) return;
        setVodFlag(flags.get(0).getFlag());
        if (!flags.get(0).getEpisodes().isEmpty()) {
            Episode episode = flags.get(0).getEpisodes().get(0);
            setVodRemarks(episode.getName());
            setEpisodeUrl(episode.getUrl());
            setTmdbEpisodePosition(episode);
        }
        if (!canMergeByName()) return;
        for (History item : findByName(getVodName())) {
            if (getPosition() > 0) break;
            for (Flag flag : flags) {
                Episode episode = flag.find(item.getEpisode(), true);
                if (episode == null) continue;
                item.copyTo(this);
                setVodFlag(flag.getFlag());
                setPosition(item.getPosition());
                setVodRemarks(episode.getName());
                setEpisodeUrl(episode.getUrl());
                if (episode.getTmdbEpisode() != null) setTmdbEpisodePosition(episode);
                else {
                    setTmdbSeasonNumber(item.getTmdbSeasonNumber());
                    setTmdbEpisodeNumber(item.getTmdbEpisodeNumber());
                }
                break;
            }
        }
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof History it)) return false;
        return Objects.equals(getKey(), it.getKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getKey());
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }

    @Override
    public boolean isSameItem(History other) {
        return equals(other);
    }

    @Override
    public boolean isSameContent(History other) {
        return other != null
                && Objects.equals(getVodName(), other.getVodName())
                && Objects.equals(getVodPic(), other.getVodPic())
                && Objects.equals(getWallPic(), other.getWallPic())
                && Objects.equals(getVodFlag(), other.getVodFlag())
                && Objects.equals(getVodRemarks(), other.getVodRemarks())
                && Objects.equals(getEpisodeUrl(), other.getEpisodeUrl())
                && getPosition() == other.getPosition()
                && getDuration() == other.getDuration()
                && getCreateTime() == other.getCreateTime();
    }
}
