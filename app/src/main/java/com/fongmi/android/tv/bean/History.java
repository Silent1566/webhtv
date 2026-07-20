package com.fongmi.android.tv.bean;

import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Diffable;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
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
        item.scale = scale;
        item.cid = cid;
        item.typeName = typeName;
        item.area = area;
        item.actor = actor;
        item.director = director;
        item.year = year;
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

    public static List<History> get(int cid) {
        return AppDatabase.get().getHistoryDao().find(cid);
    }

    public static History find(String key) {
        return AppDatabase.get().getHistoryDao().find(VodConfig.getCid(), key);
    }

    public static History findPlayback(String key, String vodName, List<Flag> flags) {
        return findPlayback(key, Collections.singletonList(vodName), flags);
    }

    public static History findPlayback(String key, List<String> vodNames, List<Flag> flags) {
        History history = find(key);
        if (history != null) return history;
        if (vodNames != null) {
            for (String vodName : vodNames) {
                if (vodName == null || vodName.isEmpty()) continue;
                history = findPlaybackCandidate(key, findByName(vodName), flags);
                if (history != null) return history.cid(VodConfig.getCid());
            }
        }
        return null;
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
        if (items == null || items.isEmpty()) return null;
        for (History item : items) if (canResume(item) && matchesAnyEpisode(item, flags)) return copyForPlaybackKey(item, key);
        for (History item : items) if (canResume(item)) return copyForPlaybackKey(item, key);
        return copyForPlaybackKey(items.get(0), key);
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
        String episodeUrl = item.getEpisodeUrl();
        if (!episodeUrl.isEmpty() && episodeUrl.equals(episode.getUrl())) return true;
        String remarks = item.getVodRemarks();
        return !remarks.isEmpty() && (remarks.equalsIgnoreCase(episode.getName()) || remarks.equals(episode.getDisplayName()));
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

    public int getScale() {
        return scale;
    }

    public void setScale(int scale) {
        this.scale = scale;
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
        return VodConfig.get().getSite(getSiteKey()).getDisplayName();
    }

    public String getSiteKey() {
        return getKey().split(AppDatabase.SYMBOL)[0];
    }

    public String getVodId() {
        return getKey().split(AppDatabase.SYMBOL)[1];
    }

    public Flag getFlag() {
        return Flag.create(getVodFlag());
    }

    public Episode getEpisode() {
        return Episode.create(getVodRemarks(), getEpisodeUrl());
    }

    public int getSiteVisible() {
        return TextUtils.isEmpty(getSiteName()) ? View.GONE : View.VISIBLE;
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
        if (!force && TextUtils.equals(getKey(), item.getKey())) return false;
        if (!force && TextUtils.equals(getKey(), item.playbackSourceKey)) return false;
        if (force) return true;
        if (getDuration() <= 0 || item.getDuration() <= 0) return false;
        return Math.abs(getDuration() - item.getDuration()) <= TimeUnit.MINUTES.toMillis(10);
    }

    private History copyTo(History item) {
        if (getOpening() > 0) item.setOpening(getOpening());
        if (getEnding() > 0) item.setEnding(getEnding());
        if (getSpeed() != 1) item.setSpeed(getSpeed());
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

    public History save() {
        History before = find(getKey());
        boolean notify = recommendationIdentityChanged(before, this);
        updateTime = System.currentTimeMillis();
        AppDatabase.get().getHistoryDao().insertOrUpdate(this);
        if (notify) notifyChanged();
        return this;
    }

    public History delete() {
        boolean deleted = AppDatabase.get().getHistoryDao().delete(VodConfig.getCid(), getKey()) > 0;
        AppDatabase.get().getTrackDao().delete(getKey());
        if (deleted) notifyChanged();
        return this;
    }

    private static void notifyChanged() {
        App.post(HISTORY_REFRESH, HISTORY_REFRESH_DEBOUNCE);
    }

    private static boolean recommendationIdentityChanged(History before, History after) {
        if (after == null || TextUtils.isEmpty(after.getVodName())) return false;
        if (before == null) return true;
        return before.getCid() != after.getCid()
                || !TextUtils.equals(before.getKey(), after.getKey())
                || !TextUtils.equals(before.getVodName(), after.getVodName());
    }

    public void findEpisode(List<Flag> flags) {
        if (flags.isEmpty()) return;
        setVodFlag(flags.get(0).getFlag());
        if (!flags.get(0).getEpisodes().isEmpty()) {
            Episode episode = flags.get(0).getEpisodes().get(0);
            setVodRemarks(episode.getName());
            setEpisodeUrl(episode.getUrl());
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
        return getVodName().equals(other.getVodName()) && getVodPic().equals(other.getVodPic()) && getWallPic().equals(other.getWallPic()) && getCreateTime() == other.getCreateTime();
    }
}
