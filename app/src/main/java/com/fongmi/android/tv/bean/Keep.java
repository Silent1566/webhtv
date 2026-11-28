package com.fongmi.android.tv.bean;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.impl.Diffable;
import com.fongmi.android.tv.setting.SiteNameStore;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Entity
public class Keep implements Diffable<Keep> {

    @NonNull
    @PrimaryKey
    @SerializedName("key")
    private String key;
    @SerializedName("siteName")
    private String siteName;
    @SerializedName("vodName")
    private String vodName;
    @SerializedName("vodPic")
    private String vodPic;
    @SerializedName("createTime")
    private long createTime;
    @SerializedName("type")
    private int type;
    @SerializedName("cid")
    private int cid;

    // ── 收藏续播进度（v5.6.40）────────────────────────────────────────────
    // 收藏的剧集自身持有"看到第几集、播到哪"，不再依赖 History + ProgressVault 影子副本。
    // 清空历史不会影响收藏进度；收藏列表也能直接显示集数。全部带默认值以便 Room 加列迁移。
    @SerializedName("episodeRemarks")
    @ColumnInfo(defaultValue = "")
    private String episodeRemarks;
    @SerializedName("episodeUrl")
    @ColumnInfo(defaultValue = "")
    private String episodeUrl;
    @ColumnInfo(defaultValue = "0")
    private int episodeNumber;
    @ColumnInfo(defaultValue = "0")
    private long position;
    @ColumnInfo(defaultValue = "0")
    private long duration;
    @ColumnInfo(defaultValue = "")
    private String vodFlag;
    @ColumnInfo(defaultValue = "")
    private String mediaType;
    @ColumnInfo(defaultValue = "0")
    private int tmdbId;
    @ColumnInfo(defaultValue = "0")
    private int tmdbSeasonNumber;
    @ColumnInfo(defaultValue = "0")
    private int tmdbEpisodeNumber;
    @ColumnInfo(defaultValue = "0")
    private long progressTime;

    public static List<Keep> arrayFrom(String str) {
        Type listType = TypeToken.getParameterized(List.class, Keep.class).getType();
        List<Keep> items = App.gson().fromJson(str, listType);
        return items == null ? Collections.emptyList() : items;
    }

    public static Keep find(String key) {
        return find(VodConfig.getCid(), key);
    }

    public static Keep find(int cid, String key) {
        return AppDatabase.get().getKeepDao().find(cid, key);
    }

    public static boolean exist(String key) {
        return AppDatabase.get().getKeepDao().find(key) != null;
    }

    /**
     * 播放进度落库后镜像到收藏项（若该片被收藏）。History 保持独立权威，这里只维护收藏自身的续播副本。
     * key 形如 site@@@vod 或 site@@@vod@@@cid，后者去掉末段再查一次以兼容历史键格式。
     */
    public static void mirrorProgress(History history) {
        if (history == null || history.getPosition() <= 0) return;
        try {
            int cid = history.getCid();
            Keep keep = AppDatabase.get().getKeepDao().find(cid, history.getKey());
            if (keep == null && history.getKey() != null) {
                String key = history.getKey();
                int last = key.lastIndexOf(AppDatabase.SYMBOL);
                int prev = last < 0 ? -1 : key.lastIndexOf(AppDatabase.SYMBOL, last - 1);
                if (prev >= 0) keep = AppDatabase.get().getKeepDao().find(cid, key.substring(0, prev));
            }
            if (keep == null) return;
            if (keep.applyProgress(history)) keep.save();
        } catch (Throwable ignored) {
        }
    }

    /**
     * 从收藏自身的进度重建一个临时 History，供收藏入口在权威 History 已不存在时续播。
     * 仅当确实存过进度才返回非 null。
     */
    public static History resumeProgress(int cid, String lookupKey) {
        if (lookupKey == null || lookupKey.isEmpty()) return null;
        try {
            Keep keep = AppDatabase.get().getKeepDao().find(cid, lookupKey);
            if (keep == null || !keep.hasProgress()) return null;
            History history = new History();
            history.setKey(lookupKey);
            history.setCid(cid);
            history.setVodName(keep.getVodName());
            history.setVodPic(keep.getVodPic());
            history.setMediaType(keep.getMediaType());
            history.setVodFlag(keep.getVodFlag());
            history.setTmdbId(keep.getTmdbId());
            history.setTmdbSeasonNumber(keep.getTmdbSeasonNumber());
            history.setTmdbEpisodeNumber(keep.getTmdbEpisodeNumber());
            int number = keep.getEpisodeNumber() > 0 ? keep.getEpisodeNumber() : com.fongmi.android.tv.bean.Episode.create(keep.getEpisodeRemarks(), keep.getEpisodeUrl()).getNumber();
            if (number > 0) history.setVodRemarks(String.valueOf(number));
            else history.setVodRemarks(keep.getEpisodeRemarks());
            history.setEpisodeUrl(keep.getEpisodeUrl());
            history.setPosition(keep.getPosition());
            history.setDuration(keep.getDuration());
            // createTime 必须晚于删除墓碑，否则反建记录会被判为旧写入丢弃。
            history.setCreateTime(System.currentTimeMillis());
            return history;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void deleteAll() {
        AppDatabase.get().getKeepDao().delete();
    }

    public static void delete(int cid) {
        AppDatabase.get().getKeepDao().delete(cid);
    }

    public static void delete(String key) {
        AppDatabase.get().getKeepDao().delete(key);
    }

    public static List<Keep> getVod() {
        return AppDatabase.get().getKeepDao().getVod();
    }

    public static List<Keep> getLive() {
        return AppDatabase.get().getKeepDao().getLive();
    }

    public static void sync(List<Config> configs, List<Keep> targets) {
        targets.forEach(target -> configs.stream()
                .filter(config -> target.getCid() == config.getId()).findFirst()
                .ifPresent(config -> target.save(Config.find(config).getId())));
    }

    @NonNull
    public String getKey() {
        return key;
    }

    public void setKey(@NonNull String key) {
        this.key = key;
    }

    public String getSiteName() {
        if (getType() != 0 || !getKey().contains(AppDatabase.SYMBOL)) return siteName;
        return SiteNameStore.getDisplayName(getCid(), getSiteKey(), siteName);
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public String getVodName() {
        return vodName;
    }

    public void setVodName(String vodName) {
        this.vodName = vodName;
    }

    public String getVodPic() {
        return vodPic;
    }

    public void setVodPic(String vodPic) {
        this.vodPic = vodPic;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getCid() {
        return cid;
    }

    public void setCid(int cid) {
        this.cid = cid;
    }

    // ── 收藏进度访问器 ────────────────────────────────────────────────────
    public String getEpisodeRemarks() {
        return episodeRemarks == null ? "" : episodeRemarks;
    }

    public void setEpisodeRemarks(String episodeRemarks) {
        this.episodeRemarks = episodeRemarks;
    }

    public String getEpisodeUrl() {
        return episodeUrl == null ? "" : episodeUrl;
    }

    public void setEpisodeUrl(String episodeUrl) {
        this.episodeUrl = episodeUrl;
    }

    public int getEpisodeNumber() {
        return episodeNumber;
    }

    public void setEpisodeNumber(int episodeNumber) {
        this.episodeNumber = episodeNumber;
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

    public String getVodFlag() {
        return vodFlag == null ? "" : vodFlag;
    }

    public void setVodFlag(String vodFlag) {
        this.vodFlag = vodFlag;
    }

    public String getMediaType() {
        return mediaType == null ? "" : mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public int getTmdbId() {
        return tmdbId;
    }

    public void setTmdbId(int tmdbId) {
        this.tmdbId = tmdbId;
    }

    public int getTmdbSeasonNumber() {
        return tmdbSeasonNumber;
    }

    public void setTmdbSeasonNumber(int tmdbSeasonNumber) {
        this.tmdbSeasonNumber = tmdbSeasonNumber;
    }

    public int getTmdbEpisodeNumber() {
        return tmdbEpisodeNumber;
    }

    public void setTmdbEpisodeNumber(int tmdbEpisodeNumber) {
        this.tmdbEpisodeNumber = tmdbEpisodeNumber;
    }

    public long getProgressTime() {
        return progressTime;
    }

    public void setProgressTime(long progressTime) {
        this.progressTime = progressTime;
    }

    /** 是否已有可展示/续播的进度。 */
    public boolean hasProgress() {
        return episodeNumber > 0 || position > 0 || !getEpisodeRemarks().isEmpty() || !getEpisodeUrl().isEmpty();
    }

    /**
     * 用播放历史刷新收藏进度，返回是否真的发生变化（避免无谓写库）。
     * 只镜像续播所需的最小字段；历史本身仍独立存储，互不干扰。
     */
    public boolean applyProgress(History history) {
        if (history == null) return false;
        String remarks = history.getVodRemarks() == null ? "" : history.getVodRemarks();
        String url = history.getEpisodeUrl() == null ? "" : history.getEpisodeUrl();
        String flag = history.getVodFlag() == null ? "" : history.getVodFlag();
        String mediaType = history.getMediaType() == null ? "" : history.getMediaType();
        int number = history.getTmdbEpisodeNumber() > 0 ? history.getTmdbEpisodeNumber() : Episode.create(remarks, url).getNumber();
        long pos = Math.max(0, history.getPosition());
        long dur = Math.max(0, history.getDuration());
        boolean changed = !remarks.equals(getEpisodeRemarks()) || !url.equals(getEpisodeUrl())
                || number != episodeNumber || pos != position || dur != duration
                || !flag.equals(getVodFlag()) || !mediaType.equals(getMediaType())
                || history.getTmdbId() != tmdbId || history.getTmdbSeasonNumber() != tmdbSeasonNumber
                || history.getTmdbEpisodeNumber() != tmdbEpisodeNumber;
        if (!changed) return false;
        setEpisodeRemarks(remarks);
        setEpisodeUrl(url);
        setEpisodeNumber(number);
        setPosition(pos);
        setDuration(dur);
        setVodFlag(flag);
        setMediaType(mediaType);
        setTmdbId(history.getTmdbId());
        setTmdbSeasonNumber(history.getTmdbSeasonNumber());
        setTmdbEpisodeNumber(history.getTmdbEpisodeNumber());
        setProgressTime(System.currentTimeMillis());
        return true;
    }

    /** 收藏条目展示用的集数文案，例如「第03集」；无进度时返回空串。 */
    public String getProgressLabel() {
        if (episodeNumber > 0) return "第" + String.format("%02d", episodeNumber) + "集";
        String remarks = getEpisodeRemarks();
        if (remarks.isEmpty() || remarks.equals(getVodName())) return "";
        return remarks;
    }

    public String getSiteKey() {
        return getKey().split(AppDatabase.SYMBOL)[0];
    }

    public String getVodId() {
        return getKey().split(AppDatabase.SYMBOL)[1];
    }

    public void save(int cid) {
        setCid(cid);
        save();
    }

    public void save() {
        AppDatabase.get().getKeepDao().insertOrUpdate(this);
    }

    public Keep delete() {
        AppDatabase.get().getKeepDao().delete(getCid(), getKey());
        return this;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Keep it)) return false;
        return Objects.equals(getKey(), it.getKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getKey());
    }

    @Override
    public boolean isSameItem(Keep other) {
        return equals(other);
    }

    @Override
    public boolean isSameContent(Keep other) {
        return getVodName().equals(other.getVodName()) && getVodPic().equals(other.getVodPic()) && getCreateTime() == other.getCreateTime();
    }
}
