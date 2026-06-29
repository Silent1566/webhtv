package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.HorizontalScrollView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.VerticalGridView;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.request.transition.Transition;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.DanmakuApi;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.CustomTarget;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.model.SearchProgress;
import com.fongmi.android.tv.playback.PlaybackEventCollector;
import com.fongmi.android.tv.player.IntroSkipPlayback;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.lut.LutPreset;
import com.fongmi.android.tv.player.lut.LutStore;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.service.IntroSkipService;
import com.fongmi.android.tv.service.PersonalRecommendationService;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.PlayerButtonSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.subtitle.SubtitlePlaybackSession;
import com.fongmi.android.tv.ui.adapter.ArrayAdapter;
import com.fongmi.android.tv.ui.adapter.BackdropAdapter;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.adapter.ParseAdapter;
import com.fongmi.android.tv.ui.adapter.PartAdapter;
import com.fongmi.android.tv.ui.adapter.QualityAdapter;
import com.fongmi.android.tv.ui.adapter.QuickAdapter;
import com.fongmi.android.tv.ui.custom.CustomKeyDownVod;
import com.fongmi.android.tv.ui.custom.CustomMovement;
import com.fongmi.android.tv.ui.custom.CustomSeekView;
import com.fongmi.android.tv.ui.custom.PlayerOsdController;
import com.fongmi.android.tv.ui.dialog.ContentDialog;
import com.fongmi.android.tv.ui.dialog.DanmakuDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeListDialog;
import com.fongmi.android.tv.ui.dialog.QuickSearchDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleManualSearchDialog;
import com.fongmi.android.tv.ui.dialog.TmdbSearchDialog;
import com.fongmi.android.tv.ui.dialog.TitleDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.ui.helper.EpisodeDisplayPolicy;
import com.fongmi.android.tv.ui.helper.TmdbNavigation;
import com.fongmi.android.tv.utils.AudioUtil;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.EpisodeTitleFormatter;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PushParser;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.crawler.SpiderDebug;
import com.github.bassaer.library.MDColor;
import com.google.gson.JsonObject;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class VideoActivity extends PlaybackActivity implements CustomKeyDownVod.Listener, TrackDialog.Listener, ArrayAdapter.OnClickListener, FlagAdapter.OnClickListener, EpisodeAdapter.OnClickListener, QualityAdapter.OnClickListener, QuickAdapter.OnClickListener, ParseAdapter.OnClickListener, Clock.Callback, SubtitlePlaybackSession.Host {

    private static final int SHORT_DRAMA_SCALE = 0; // 0=原始(适合TV), 4=裁剪(适合手机)
    private static final int TMDB_DETAIL_LOAD_TIMEOUT = 8000;
    private static final int TMDB_OVERVIEW_ROW_GAP_DP = 12;
    private static final int TMDB_OVERVIEW_BOTTOM_GUARD_DP = 6;
    private static final int OMDB_FULL_RATING_TEXT_MAX_LENGTH = 20;
    private static final String EXTRA_TMDB_PLAY_FLAG = "tmdb_play_flag";
    private static final String EXTRA_TMDB_PLAY_EPISODE_NAME = "tmdb_play_episode_name";
    private static final String EXTRA_TMDB_PLAY_EPISODE_URL = "tmdb_play_episode_url";

    private ActivityVideoBinding mBinding;
    private ViewGroup.LayoutParams mFrameParams;
    private Observer<Result> mObserveDetail;
    private Observer<Result> mObservePlayer;
    private Observer<Result> mObserveSearch;
    private Observer<SearchProgress> mObserveSearchProgress;
    private EpisodeAdapter mEpisodeAdapter;
    private EpisodeAdapter mEpisodeGridAdapter;
    private QualityAdapter mQualityAdapter;
    private ArrayAdapter mArrayAdapter;
    private ParseAdapter mParseAdapter;
    private QuickAdapter mQuickAdapter;
    private FlagAdapter mFlagAdapter;
    private PartAdapter mPartAdapter;
    private BackdropAdapter mBackdropAdapter;
    private Map<String, View> mActionButtons;
    private QuickSearchDialog mQuickSearchDialog;
    private PlayerOsdController mOsd;
    private final IntroSkipPlayback mIntroSkipPlayback = new IntroSkipPlayback();
    private final SubtitlePlaybackSession subtitlePlaybackSession = new SubtitlePlaybackSession(this);
    private CustomKeyDownVod mKeyDown;
    private SiteViewModel mViewModel;
    private List<String> mBroken;
    private History mHistory;
    private boolean fullscreen;
    private boolean initAuto;
    private boolean autoMode;
    private boolean revealManualSearch;
    private boolean quickSearchDialogClosed;
    private boolean useParse;
    private boolean detailRequested;
    private boolean detailHealthRecorded;
    private boolean playHealthRecorded;
    private boolean episodeGridMode;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mTmdbDetailTimeout;
    private boolean mTmdbDetailLoading;
    private boolean mTmdbDetailRevealed;
    private boolean mTmdbDetailFieldsApplied;
    private int mPersonalRecommendationGeneration;

    // TMDB 模式相关字段
    private com.fongmi.android.tv.ui.helper.TmdbUIAdapter mTmdbUIAdapter;
    private com.fongmi.android.tv.ui.custom.TmdbHeaderView mTmdbHeaderView;
    private Vod mVod;
    private boolean mTmdbAutoDialogShown;
    private int mTmdbDialogGeneration;
    private Runnable mR4;
    private Runnable mBackdropRunnable;
    private int mCurrentBackdropPage = 0;
    private Clock mClock;
    private View mFocus1;
    private PersonalRecommendationService.RecommendationPage mNativePersonalTmdbPage;
    private PersonalRecommendationService.RecommendationPage mNativePersonalDoubanPage;
    private PersonalRecommendationService.RecommendationPage mNativePersonalAiPage;
    private ArrayObjectAdapter mTmdbRecommendationsObjectAdapter;
    private ArrayObjectAdapter mPersonalTmdbObjectAdapter;
    private ArrayObjectAdapter mPersonalDoubanObjectAdapter;
    private ArrayObjectAdapter mPersonalAiObjectAdapter;
    private boolean mTmdbRecommendationsLoading;
    private boolean mPersonalTmdbLoading;
    private boolean mPersonalDoubanLoading;
    private boolean mPersonalAiLoading;
    private boolean mNativePersonalTmdbLoading;
    private boolean mNativePersonalDoubanLoading;
    private final Map<String, java.util.List<String[]>> mTmdbOmdbRatingCache = Collections.synchronizedMap(new HashMap<>());
    private final Set<String> mTmdbOmdbRatingLoading = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, PersonalRecommendationService.DoubanRating> mTmdbDoubanRatingCache = Collections.synchronizedMap(new HashMap<>());
    private final Set<String> mTmdbDoubanRatingLoading = Collections.synchronizedSet(new HashSet<>());
    private View mFocus2;
    private Result mPendingDetail;
    private Result mPendingPlayer;
    private String mContextWallUrl;
    private String mContextWallLockedUrl;
    private String playHealthKey;
    private long detailStartTime;
    private long playerStartTime;
    private boolean pendingLutImport;

    private final ActivityResultLauncher<Intent> mLutDir = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        LutStore.setUserDir(result.getData().getData(), result.getData().getFlags());
        Notify.show(R.string.lut_directory_selected);
        mBinding.lutQuick.refreshList();
        if (pendingLutImport) {
            pendingLutImport = false;
            chooseLutFile();
        }
    });

    private final ActivityResultLauncher<Intent> mLutFile = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        String path = FileChooser.getPathFromUri(result.getData().getData());
        if (TextUtils.isEmpty(path)) {
            Notify.show(R.string.lut_import_failed);
            return;
        }
        Task.execute(() -> {
            try {
                LutPreset preset = LutStore.importFile(path);
                App.post(() -> {
                    Notify.show(R.string.lut_imported);
                    mBinding.lutQuick.selectImported(preset, player(), mBinding.exo, this::onLutChanged);
                });
            } catch (Exception e) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "import failed path=%s error=%s", path, e.getMessage());
                App.post(() -> Notify.show(Notify.getError(R.string.lut_import_failed, e)));
            }
        });
    });

    public static void push(FragmentActivity activity, String text) {
        PushParser.Parsed push = PushParser.fromText(text);
        Uri uri = Uri.parse(push.getUrl());
        if (FileChooser.isValid(activity, uri)) file(activity, FileChooser.getPathFromUri(uri), push.getTitle());
        else startPush(activity, push);
    }

    public static void file(FragmentActivity activity, String path) {
        file(activity, path, "");
    }

    private static void file(FragmentActivity activity, String path, String title) {
        if (TextUtils.isEmpty(path)) return;
        PushParser.Parsed push = PushParser.of("file://" + path, TextUtils.isEmpty(title) ? new File(path).getName() : title);
        start(activity, SiteApi.PUSH, push.getId(), push.getName());
    }

    public static void cast(Activity activity, History history) {
        start(activity, history.getSiteKey(), history.getVodId(), history.getVodName(), history.getVodPic(), null, false, true, (TmdbItem) null, history.getWallPic());
    }

    public static void collect(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null, true, false);
    }

    public static void collect(Activity activity, String key, String id, String name, String pic, String wallPic) {
        start(activity, key, id, name, pic, null, true, false, (TmdbItem) null, wallPic);
    }

    private static boolean canOpenLegacyTmdbDetail(String key, boolean cast) {
        if (cast || TextUtils.isEmpty(key)) return false;
        if (SiteApi.PUSH.equals(key)) return isTmdbSiteEnabled(key);
        return !AudioUtil.isAudioSiteEnabled(key) && !isShortDramaSiteEnabled(key) && isTmdbSiteEnabled(key);
    }

    private static boolean isTmdbSiteEnabled(String key) {
        Site site = VodConfig.get().getSite(key);
        return Setting.isTmdbSiteEnabled(key, site == null ? "" : site.getName());
    }

    private static boolean isShortDramaSiteEnabled(String key) {
        Site site = VodConfig.get().getSite(key);
        return Setting.isShortDramaSiteEnabled(key, site == null ? "" : site.getName());
    }

    private static boolean shouldOpenLegacyTmdbDetail(String key, boolean cast) {
        int mode = Setting.getDetailOpenMode();
        return canOpenLegacyTmdbDetail(key, cast) && Setting.isTmdbDetailPage() && Setting.isStandaloneTmdbDetailMode(mode);
    }

    public static void start(Activity activity, String url) {
        startPush(activity, PushParser.fromText(url));
    }

    private static void startPush(Activity activity, PushParser.Parsed push) {
        if (dispatchToContentHandler(activity, push.getUrl(), push.getTitle())) return;
        start(activity, SiteApi.PUSH, push.getId(), push.getName());
    }

    private static boolean dispatchToContentHandler(Activity activity, String url) {
        return dispatchToContentHandler(activity, url, "");
    }

    private static boolean dispatchToContentHandler(Activity activity, String url, String title) {
        return com.fongmi.android.tv.content.ContentDispatcher.dispatchUrl(activity, url, title);
    }

    public static void start(Activity activity, String key, String id, String name) {
        start(activity, key, id, name, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark) {
        start(activity, key, id, name, pic, mark, false, false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, String wallPic) {
        start(activity, key, id, name, pic, mark, false, false, (TmdbItem) null, wallPic);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, String wallPic, String content) {
        start(activity, key, id, name, pic, mark, false, false, wallPic, content);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast) {
        start(activity, key, id, name, pic, mark, collect, cast, (String) null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast, String wallPic) {
        start(activity, key, id, name, pic, mark, collect, cast, wallPic, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast, String wallPic, String content) {
        start(activity, key, id, name, pic, mark, collect, cast, (TmdbItem) null, wallPic, content);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast, com.fongmi.android.tv.bean.TmdbItem tmdbItem) {
        start(activity, key, id, name, pic, mark, collect, cast, tmdbItem, null, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast, com.fongmi.android.tv.bean.TmdbItem tmdbItem, String wallPic) {
        start(activity, key, id, name, pic, mark, collect, cast, tmdbItem, wallPic, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast, com.fongmi.android.tv.bean.TmdbItem tmdbItem, String wallPic, String content) {
        long launch = System.currentTimeMillis();
        SpiderDebug.log("video-flow", "launch request key=%s id=%s name=%s collect=%s cast=%s", key, id, name, collect, cast);
        ImgUtil.preload(activity, pic);
        if (Setting.isPlaybackArtworkWall() && !TextUtils.isEmpty(wallPic) && !TextUtils.equals(wallPic, pic)) ImgUtil.preload(activity, wallPic);
        if (dispatchToContentHandler(activity, key, id, name, pic, mark, cast)) {
            SpiderDebug.log("video-flow", "dispatched to content handler key=%s", key);
            return;
        }
        if (tmdbItem == null && shouldOpenLegacyTmdbDetail(key, cast)) {
            TmdbDetailActivity.start(activity, key, id, name, pic, mark, null, Setting.getDetailOpenMode());
            return;
        }
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("launchTime", launch);
        intent.putExtra("tmdbMode", tmdbItem != null);
        intent.putExtra("tmdbItem", tmdbItem);
        intent.putExtra("collect", collect);
        intent.putExtra("cast", cast);
        intent.putExtra("mark", mark);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("wallPic", wallPic);
        intent.putExtra("content", content);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        activity.startActivity(intent);
        SpiderDebug.log("video-flow", "launch dispatched cost=%dms key=%s id=%s", System.currentTimeMillis() - launch, key, id);
    }

    public static void startWithTmdb(Activity activity, String key, String id, String name, String pic, String mark, com.fongmi.android.tv.bean.TmdbItem tmdbItem) {
        start(activity, key, id, name, pic, mark, false, false, tmdbItem);
    }

    public static void startDirect(Activity activity, String key, String id, String name, String pic) {
        startDirect(activity, key, id, name, pic, null);
    }

    public static void startDirect(Activity activity, String key, String id, String name, String pic, String mark) {
        if (AudioActivity.startSite(activity, key, id, name, pic, mark)) return;
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("collect", false);
        intent.putExtra("cast", false);
        intent.putExtra("mark", mark);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        activity.startActivity(intent);
    }

    public static void startDirectTmdb(Activity activity, String key, String id, String name, String pic, String mark, ArrayList<String> episodeTitles, TmdbItem item, Vod tmdbVod) {
        startDirectTmdb(activity, key, id, name, pic, mark, episodeTitles, item, tmdbVod, null, null, null);
    }

    public static void startDirectTmdb(Activity activity, String key, String id, String name, String pic, String mark, ArrayList<String> episodeTitles, TmdbItem item, Vod tmdbVod, String playFlag, String playEpisodeName, String playEpisodeUrl) {
        if (AudioActivity.startSite(activity, key, id, name, pic, mark)) return;
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("tmdbMode", item != null);
        intent.putExtra("tmdbItem", item);
        intent.putExtra("collect", false);
        intent.putExtra("cast", false);
        intent.putExtra("mark", mark);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        intent.putStringArrayListExtra("tmdb_episode_titles", episodeTitles);
        putIntentPlaybackSelection(intent, playFlag, playEpisodeName, playEpisodeUrl);
        putTmdbVod(intent, tmdbVod);
        activity.startActivity(intent);
    }

    private static void putIntentPlaybackSelection(Intent intent, String playFlag, String playEpisodeName, String playEpisodeUrl) {
        if (!TextUtils.isEmpty(playFlag)) intent.putExtra(EXTRA_TMDB_PLAY_FLAG, playFlag);
        if (!TextUtils.isEmpty(playEpisodeName)) intent.putExtra(EXTRA_TMDB_PLAY_EPISODE_NAME, playEpisodeName);
        if (!TextUtils.isEmpty(playEpisodeUrl)) intent.putExtra(EXTRA_TMDB_PLAY_EPISODE_URL, playEpisodeUrl);
    }

    private static void putTmdbVod(Intent intent, Vod vod) {
        if (vod == null) return;
        intent.putExtra("tmdb_vod_title", vod.getName());
        intent.putExtra("tmdb_vod_content", vod.getContent());
        intent.putExtra("tmdb_vod_pic", vod.getPic());
        intent.putExtra("tmdb_vod_year", vod.getYear());
        intent.putExtra("tmdb_vod_area", vod.getArea());
        intent.putExtra("tmdb_vod_type", vod.getTypeName());
        intent.putExtra("tmdb_vod_director", vod.getDirector());
        intent.putExtra("tmdb_vod_actor", vod.getActor());
        intent.putExtra("tmdb_vod_remark", vod.getRemarks());
    }

    private static boolean dispatchToContentHandler(Activity activity, String key, String id, String name, String pic, String mark, boolean cast) {
        return !cast && com.fongmi.android.tv.content.ContentDispatcher.dispatchSite(activity, key, id, name, pic, mark);
    }

    private boolean isCast() {
        return getIntent().getBooleanExtra("cast", false);
    }

    private String getName() {
        return Objects.toString(getIntent().getStringExtra("name"), "");
    }

    private String getPic() {
        return Objects.toString(getIntent().getStringExtra("pic"), "");
    }

    private String getTmdbVodPic() {
        return Objects.toString(getIntent().getStringExtra("tmdb_vod_pic"), "");
    }

    private String getWallPic() {
        return Objects.toString(getIntent().getStringExtra("wallPic"), "");
    }

    private String getContent() {
        return Objects.toString(getIntent().getStringExtra("content"), "");
    }

    private String getMark() {
        return Objects.toString(getIntent().getStringExtra("mark"), "");
    }

    private String getIntentPlaybackFlag() {
        return Objects.toString(getIntent().getStringExtra(EXTRA_TMDB_PLAY_FLAG), "");
    }

    private String getIntentPlaybackEpisodeName() {
        return Objects.toString(getIntent().getStringExtra(EXTRA_TMDB_PLAY_EPISODE_NAME), "");
    }

    private String getIntentPlaybackEpisodeUrl() {
        return Objects.toString(getIntent().getStringExtra(EXTRA_TMDB_PLAY_EPISODE_URL), "");
    }

    private String getKey() {
        return Objects.toString(getIntent().getStringExtra("key"), "");
    }

    private String getId() {
        return Objects.toString(getIntent().getStringExtra("id"), "");
    }

    private String getHistoryKey() {
        return getKey().concat(AppDatabase.SYMBOL).concat(getId()).concat(AppDatabase.SYMBOL) + VodConfig.getCid();
    }

    private Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    private Flag getFlag() {
        return mFlagAdapter.getActivated();
    }

    private Episode getEpisode() {
        return mEpisodeAdapter.getActivated();
    }

    private boolean isTmdbMode() {
        return getIntent().getBooleanExtra("tmdbMode", false);
    }

    private boolean isTmdbSourceEnabled() {
        if (isTmdbMode()) return true;
        if (!Setting.isTmdbMode(Setting.getDetailOpenMode())) return false;
        if (!Setting.isTmdbEnabled()) return false;
        Site site = getSite();
        return Setting.isTmdbSiteEnabled(site == null ? getKey() : site.getKey(), site == null ? "" : site.getName());
    }

    private com.fongmi.android.tv.bean.TmdbItem getTmdbItem() {
        return (com.fongmi.android.tv.bean.TmdbItem) getIntent().getSerializableExtra("tmdbItem");
    }

    private String getOsdTitle() {
        return EpisodeTitleFormatter.buildPlaybackTitle(getPlaybackName(), getCurrentEpisodeTitle());
    }

    private String getPlaybackName() {
        CharSequence name = mBinding == null || mBinding.name == null ? "" : mBinding.name.getText();
        return TextUtils.isEmpty(name) ? getName() : name.toString();
    }

    private String getCurrentEpisodeTitle() {
        return mEpisodeAdapter == null || mEpisodeAdapter.getItemCount() == 0 ? "" : getEpisodeTitle(getEpisode());
    }

    private String getEpisodeTitle(Episode episode) {
        return episode == null ? "" : EpisodeAdapter.getTitle(episode);
    }

    private CharSequence getPlaybackControlTitle() {
        return getPlaybackControlTitle(mEpisodeAdapter == null || mEpisodeAdapter.getItemCount() == 0 ? null : getEpisode());
    }

    private CharSequence getPlaybackControlTitle(Episode episode) {
        String name = getPlaybackName();
        String title = getEpisodeTitle(episode);
        return TextUtils.isEmpty(title) || TextUtils.equals(name, title) ? name : getString(R.string.detail_title, name, title);
    }

    private int getScale() {
        return mHistory != null && mHistory.getScale() != -1 ? mHistory.getScale() : PlayerSetting.getScale();
    }

    private boolean isReplay() {
        return Setting.getReset() == 1;
    }

    private boolean isFromCollect() {
        return getIntent().getBooleanExtra("collect", false);
    }

    private long getLaunchTime() {
        return getIntent().getLongExtra("launchTime", 0);
    }

    private long getLaunchCost(long now) {
        long launchTime = getLaunchTime();
        return launchTime <= 0 ? 0 : now - launchTime;
    }

    @Override
    protected ViewBinding getBinding() {
        long start = System.currentTimeMillis();
        mBinding = ActivityVideoBinding.inflate(getLayoutInflater());
        SpiderDebug.log("video-flow", "inflate cost=%dms sinceLaunch=%dms", System.currentTimeMillis() - start, getLaunchCost(start));
        return mBinding;
    }

    @Override
    protected PlaybackService.NavigationCallback getNavigationCallback() {
        return mNavigationCallback;
    }

    @Override
    protected PlayerView getExoView() {
        return mBinding.exo;
    }

    @Override
    protected CustomSeekView getSeekView() {
        return mBinding.control.seek;
    }

    @Override
    protected void onServiceConnected() {
        SpiderDebug.log("video-flow", "service ready sinceLaunch=%dms key=%s id=%s", getLaunchCost(System.currentTimeMillis()), getKey(), getId());
        player().setDanmakuController(mBinding.exo.getDanmakuController());
        setPlayerKernel();
        setDecode();
        setLut();
        if (!detailRequested) checkId();
        if (mPendingDetail != null) {
            Result result = mPendingDetail;
            mPendingDetail = null;
            setDetail(result);
        }
        if (mPendingPlayer != null) {
            Result result = mPendingPlayer;
            mPendingPlayer = null;
            setPlayer(result);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        String oldKey = getKey();
        String oldId = getId();
        super.onNewIntent(intent);
        String key = Objects.toString(intent.getStringExtra("key"), "");
        String id = Objects.toString(intent.getStringExtra("id"), "");
        if (TextUtils.isEmpty(id) || (id.equals(oldId) && key.equals(oldKey))) return;
        saveHistory();
        getIntent().putExtras(intent);
        resetDetailForNewIntent();
        checkId();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        long start = System.currentTimeMillis();
        SpiderDebug.log("video-flow", "initView start sinceLaunch=%dms key=%s id=%s", getLaunchCost(start), getKey(), getId());
        mTmdbDetailTimeout = this::showTmdbDetailFallback;
        initTmdbMode();
        super.initView(savedInstanceState);
        SpiderDebug.log("video-flow", "initView after playback cost=%dms", System.currentTimeMillis() - start);
        mFrameParams = mBinding.video.getLayoutParams();
        mClock = Clock.create(mBinding.widget.clock);
        mClock.start();
        mKeyDown = CustomKeyDownVod.create(this);
        mObserveDetail = this::setDetail;
        mObservePlayer = this::setPlayer;
        mObserveSearch = this::setSearch;
        mObserveSearchProgress = this::setSearchProgress;
        mBroken = new ArrayList<>();
        mR1 = this::hideControl;
        mR2 = this::updateFocus;
        mR4 = this::showEmpty;
        SpiderDebug.log("video-flow", "initView state ready cost=%dms", System.currentTimeMillis() - start);
        checkCast();
        SpiderDebug.log("video-flow", "initView preview ready cost=%dms", System.currentTimeMillis() - start);
        setRecyclerView();
        setShortDisplay();
        mOsd = new PlayerOsdController(mBinding.osd.getRoot(), mBinding.osd.osdTopLeft, mBinding.osd.osdTopRight, mBinding.osd.osdBottomLeft, mBinding.osd.osdBottomRight, mBinding.osd.osdDiagnostics, mBinding.osd.osdMiniProgress, new PlayerOsdController.Source() {
            @Override
            public PlayerManager getPlayer() {
                return service() == null ? null : player();
            }

            @Override
            public String getTitle() {
                return getOsdTitle();
            }
        }, 14f);
        SpiderDebug.log("video-flow", "initView recycler ready cost=%dms", System.currentTimeMillis() - start);
        setVideoView();
        SpiderDebug.log("video-flow", "initView video view ready cost=%dms", System.currentTimeMillis() - start);
        setViewModel();
        checkId();
        SpiderDebug.log("video-flow", "initView end cost=%dms sinceLaunch=%dms", System.currentTimeMillis() - start, getLaunchCost(System.currentTimeMillis()));
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.keep.setOnClickListener(view -> onKeep());
        mBinding.search.setOnClickListener(view -> onSearch());
        mBinding.video.setOnClickListener(view -> onVideo());
        mBinding.change1.setOnClickListener(view -> onChange());
        mBinding.tmdbRematch.setOnClickListener(view -> showManualTmdbMatchDialog());
        mBinding.content.setOnClickListener(view -> onContent());
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        mBinding.control.action.speed.setUpListener(this::onSpeedAdd);
        mBinding.control.action.speed.setDownListener(this::onSpeedSub);
        mBinding.control.action.ending.setUpListener(this::onEndingAdd);
        mBinding.control.action.ending.setDownListener(this::onEndingSub);
        mBinding.control.action.opening.setUpListener(this::onOpeningAdd);
        mBinding.control.action.opening.setDownListener(this::onOpeningSub);
        mBinding.control.action.text.setUpListener(this::onSubtitleClick);
        mBinding.control.action.text.setDownListener(this::onSubtitleClick);
        mBinding.control.action.next.setOnClickListener(view -> checkNext());
        mBinding.control.action.prev.setOnClickListener(view -> checkPrev());
        mBinding.control.action.episodes.setOnClickListener(view -> onEpisodes());
        mBinding.episodeReverse.setOnClickListener(view -> onRevSort());
        mBinding.episodeViewMode.setOnClickListener(view -> toggleEpisodeViewMode());
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.actionQuality.setOnClickListener(view -> onQuality());
        mBinding.control.action.lut.setOnClickListener(view -> onLut());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.reset.setOnClickListener(view -> onReset());
        mBinding.control.action.title.setOnClickListener(view -> onTitle());
        mBinding.control.action.player.setOnClickListener(view -> onPlayerKernel());
        mBinding.control.action.player.setOnLongClickListener(view -> onPlayerKernelLong());
        mBinding.control.action.decode.setOnClickListener(view -> onDecode());
        mBinding.control.action.ending.setOnClickListener(view -> onEnding());
        mBinding.control.action.repeat.setOnClickListener(view -> onRepeat());
        mBinding.control.action.change2.setOnClickListener(view -> onChange());
        mBinding.control.action.fullscreen.setOnClickListener(view -> onFullscreen());
        mBinding.control.action.danmaku.setOnClickListener(view -> onDanmaku());
        mBinding.control.action.opening.setOnClickListener(view -> onOpening());
        mBinding.shortDisplay.setOnClickListener(view -> onShortDisplay());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.action.reset.setOnLongClickListener(view -> onResetToggle());
        mBinding.control.action.ending.setOnLongClickListener(view -> onEndingReset());
        mBinding.control.action.opening.setOnLongClickListener(view -> onOpeningReset());
        setActionFocusScroll();
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
        mBinding.flag.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mFlagAdapter.getItemCount() > 0) onItemClick(mFlagAdapter.get(position));
            }
        });
        mBinding.episode.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (child != null && mBinding.video != mFocus1) mFocus1 = child.itemView;
            }
        });
        mBinding.episode.setOnKeyListener((view, keyCode, event) -> onEpisodeKey(event));
        mBinding.episodeGrid.setOnKeyListener((view, keyCode, event) -> onEpisodeKey(event));
        mBinding.array.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                int count = mEpisodeAdapter.getItemCount();
                if (count > getEpisodeSegmentSize(count) && position > 1) scrollToEpisode(mArrayAdapter.getStart(position));
            }
        });
    }

    private void setActionFocusScroll() {
        HorizontalScrollView scroll = mBinding.control.action.getRoot();
        if (scroll.getChildCount() == 0 || !(scroll.getChildAt(0) instanceof ViewGroup group)) return;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            child.setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus) scroll.post(() -> scroll.smoothScrollTo(Math.max(0, view.getLeft() - ResUtil.dp2px(24)), 0));
            });
        }
    }

    private void setRecyclerView() {
        mBinding.flag.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.flag.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.flag.setAdapter(mFlagAdapter = new FlagAdapter(this));
        mBinding.episode.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.episode.setVerticalSpacing(ResUtil.dp2px(8));
        mBinding.episode.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.episode.setAdapter(mEpisodeAdapter = new EpisodeAdapter(this, this::onEpisodeLongClick));
        mEpisodeAdapter.setColumn(1); // 横向滚动，固定1列
        int episodeGridSpan = getEpisodeGridSpanCount();
        mBinding.episodeGrid.setItemAnimator(null);
        mBinding.episodeGrid.setLayoutManager(new GridLayoutManager(this, episodeGridSpan));
        mBinding.episodeGrid.setAdapter(mEpisodeGridAdapter = new EpisodeAdapter(this, this::onEpisodeLongClick));
        mEpisodeGridAdapter.setGridMode(true);
        mEpisodeGridAdapter.setVerticalGridMode(true);
        mEpisodeGridAdapter.setColumn(episodeGridSpan);
        mBinding.quality.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.quality.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.quality.setAdapter(mQualityAdapter = new QualityAdapter(this));
        mBinding.array.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.array.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.array.setAdapter(mArrayAdapter = new ArrayAdapter(this));
        mBinding.part.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.part.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.part.setAdapter(mPartAdapter = new PartAdapter(item -> initSearch(item, false)));
        mBinding.quick.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.quick.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.quick.setAdapter(mQuickAdapter = new QuickAdapter(this));
        mBinding.control.parse.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.control.parse.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.control.parse.setAdapter(mParseAdapter = new ParseAdapter(this));
        mParseAdapter.addAll(VodConfig.get().getParses());
        // TMDB 相关 GridView 初始化
        setupTmdbGridViews();
    }

    private void setupTmdbGridViews() {
        mBinding.tmdbCast.setHorizontalSpacing(ResUtil.dp2px(12));
        mBinding.tmdbCast.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.tmdbPhotos.setHorizontalSpacing(ResUtil.dp2px(12));
        mBinding.tmdbPhotos.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.tmdbCrew.setHorizontalSpacing(ResUtil.dp2px(12));
        mBinding.tmdbCrew.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.tmdbRecommendations.setHorizontalSpacing(ResUtil.dp2px(12));
        mBinding.tmdbRecommendations.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.tmdbPersonalTmdbRecommendations.setHorizontalSpacing(ResUtil.dp2px(12));
        mBinding.tmdbPersonalTmdbRecommendations.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.tmdbPersonalDoubanRecommendations.setHorizontalSpacing(ResUtil.dp2px(12));
        mBinding.tmdbPersonalDoubanRecommendations.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.tmdbPersonalAiRecommendations.setHorizontalSpacing(ResUtil.dp2px(12));
        mBinding.tmdbPersonalAiRecommendations.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        attachRecommendationLazyLoader(mBinding.tmdbRecommendations, RecommendationRow.RECOMMENDATIONS);
        attachRecommendationLazyLoader(mBinding.tmdbPersonalTmdbRecommendations, RecommendationRow.PERSONAL_TMDB);
        attachRecommendationLazyLoader(mBinding.tmdbPersonalDoubanRecommendations, RecommendationRow.PERSONAL_DOUBAN);
    }

    private void setVideoView() {
        mBinding.control.action.danmaku.setVisibility(DanmakuSetting.isLoad() ? View.VISIBLE : View.GONE);
        mBinding.control.action.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
        setupActionButtons();
        setPlayer();
    }

    private void setPlayer() {
        mBinding.control.action.player.setText(service() == null ? ResUtil.getStringArray(R.array.select_player)[PlayerSetting.getPlayer()] : player().getPlayerText());
    }

    private void setupActionButtons() {
        mActionButtons = new HashMap<>();
        addActionButton(PlayerButtonSetting.NEXT, mBinding.control.action.next);
        addActionButton(PlayerButtonSetting.PREV, mBinding.control.action.prev);
        addActionButton(PlayerButtonSetting.EPISODES, mBinding.control.action.episodes);
        addActionButton(PlayerButtonSetting.RESET, mBinding.control.action.reset);
        addActionButton(PlayerButtonSetting.CHANGE, mBinding.control.action.change2);
        addActionButton(PlayerButtonSetting.FULLSCREEN, mBinding.control.action.fullscreen);
        addActionButton(PlayerButtonSetting.PLAYER, mBinding.control.action.player);
        addActionButton(PlayerButtonSetting.DECODE, mBinding.control.action.decode);
        addActionButton(PlayerButtonSetting.SPEED, mBinding.control.action.speed);
        addActionButton(PlayerButtonSetting.SCALE, mBinding.control.action.scale);
        addActionButton(PlayerButtonSetting.LUT, mBinding.control.action.lut);
        addActionButton(PlayerButtonSetting.TEXT, mBinding.control.action.text);
        addActionButton(PlayerButtonSetting.AUDIO, mBinding.control.action.audio);
        addActionButton(PlayerButtonSetting.VIDEO, mBinding.control.action.video);
        addActionButton(PlayerButtonSetting.OPENING, mBinding.control.action.opening);
        addActionButton(PlayerButtonSetting.ENDING, mBinding.control.action.ending);
        addActionButton(PlayerButtonSetting.DANMAKU, mBinding.control.action.danmaku);
        addActionButton(PlayerButtonSetting.TITLE, mBinding.control.action.title);
        addActionButton(PlayerButtonSetting.REPEAT, mBinding.control.action.repeat);
        PlayerButtonSetting.applyOrder(mBinding.control.action.container, mActionButtons);
    }

    private void addActionButton(String id, View view) {
        mActionButtons.put(id, view);
    }

    private void applyActionButtonVisibility() {
        if (mActionButtons != null) PlayerButtonSetting.applyVisibility(mActionButtons);
    }

    private int getEpisodeColumn() {
        return mEpisodeAdapter == null ? 8 : EpisodeAdapter.getColumn(mEpisodeAdapter.getItems());
    }

    private int getEpisodeGridSpanCount() {
        int width = Math.max(ResUtil.dp2px(320), ResUtil.getScreenWidth() - ResUtil.dp2px(48));
        return Math.max(2, Math.min(6, width / ResUtil.dp2px(280)));
    }

    private void setDecode() {
        mBinding.control.action.decode.setText(player().getDecodeText());
    }

    private void setPlayerKernel() {
        mBinding.control.action.player.setText(player().getPlayerText());
    }

    private void setScale(int scale) {
        if (mHistory != null) mHistory.setScale(scale);
        if (SiteApi.PUSH.equals(getKey())) PlayerSetting.putScale(scale);
        applyResizeMode(scale);
        mBinding.exo.post(() -> applyResizeMode(scale));
        mBinding.control.action.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    private void setLut() {
        mBinding.control.action.lut.setText(player().getLutText());
    }

    private void onLutChanged() {
        setLut();
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observeForever(mObserveDetail);
        mViewModel.getPlayer().observeForever(mObservePlayer);
        mViewModel.getSearch().observeForever(mObserveSearch);
        mViewModel.getSearchProgress().observeForever(mObserveSearchProgress);
    }

    private void checkCast() {
        if (isCast() && !isFullscreen()) enterFullscreen();
        else mBinding.progressLayout.showProgress();
    }

    private void checkId() {
        if (detailRequested) return;
        detailRequested = true;
        if (getId().startsWith("push://")) getIntent().putExtra("key", SiteApi.PUSH).putExtra("id", getId().substring(7));
        if (getId().isEmpty() || getId().startsWith("msearch:")) setEmpty(false);
        else getDetail();
    }

    private void resetDetailForNewIntent() {
        detailRequested = false;
        detailHealthRecorded = false;
        playHealthRecorded = false;
        revealManualSearch = false;
        episodeGridMode = false;
        mPendingDetail = null;
        mPendingPlayer = null;
        mVod = null;
        mTmdbAutoDialogShown = false;
        mTmdbDetailLoading = false;
        mTmdbDetailRevealed = false;
        mPersonalRecommendationGeneration++;
        mTmdbDialogGeneration++;
        App.removeCallbacks(mR4);
        App.removeCallbacks(mTmdbDetailTimeout);
        stopBackdropAutoScroll();
        if (mViewModel != null) mViewModel.stopSearch();
        if (mBroken != null) mBroken.clear();
        clearDetailAdapters();
        clearTmdbDetailViews();
        mBinding.scroll.scrollTo(0, 0);
        mBinding.name.setText(getName());
        mBinding.widget.title.setText(getName());
        mBinding.video.requestFocus();
        updateNavigationKey();
        if (service() != null) {
            player().reset();
            player().stop();
        }
        mBinding.progressLayout.showProgress();
    }

    private void clearDetailAdapters() {
        if (mFlagAdapter != null) mFlagAdapter.clear();
        if (mEpisodeAdapter != null) {
            mEpisodeAdapter.setUseTmdbCard(false);
            mEpisodeAdapter.clear();
        }
        if (mEpisodeGridAdapter != null) {
            mEpisodeGridAdapter.setUseTmdbCard(false);
            mEpisodeGridAdapter.clear();
        }
        if (mArrayAdapter != null) mArrayAdapter.clear();
        if (mQualityAdapter != null) mQualityAdapter.addAll(Result.empty());
        if (mPartAdapter != null) mPartAdapter.clear();
        if (mQuickAdapter != null) mQuickAdapter.clear();
        mBinding.episodeContainer.setVisibility(View.GONE);
        mBinding.episodeHeader.setVisibility(View.GONE);
        mBinding.episodeReverse.setVisibility(View.GONE);
        mBinding.episodeViewMode.setVisibility(View.GONE);
        mBinding.episodeLoadingIndicator.setVisibility(View.GONE);
        mBinding.quality.setVisibility(View.GONE);
    }

    private void clearTmdbDetailViews() {
        clearTmdbGrid(mBinding.tmdbCast, R.id.tmdbCastLabel);
        clearTmdbGrid(mBinding.tmdbPhotos, R.id.tmdbPhotosLabel);
        clearTmdbGrid(mBinding.tmdbCrew, R.id.tmdbCrewLabel);
        clearTmdbGrid(mBinding.tmdbRecommendations, R.id.tmdbRecommendationsLabel);
        clearNativePersonalRecommendations();
        mBinding.tmdbOverview.setText("");
        mBinding.tmdbOverview.setVisibility(View.GONE);
        mTmdbDetailFieldsApplied = false;
        View ratingsLabel = mBinding.getRoot().findViewById(R.id.tmdbOmdbRatingsLabel);
        View ratings = mBinding.getRoot().findViewById(R.id.tmdbOmdbRatings);
        if (ratingsLabel != null) ratingsLabel.setVisibility(View.GONE);
        if (ratings != null) ratings.setVisibility(View.GONE);
        setTmdbRematchVisible(false);
    }

    private void clearTmdbGrid(RecyclerView grid, int labelId) {
        grid.setAdapter(null);
        grid.setVisibility(View.GONE);
        View label = mBinding.getRoot().findViewById(labelId);
        if (label != null) label.setVisibility(View.GONE);
    }

    private void getDetail() {
        detailStartTime = System.currentTimeMillis();
        detailHealthRecorded = false;
        SpiderDebug.log("video-flow", "detail start key=%s id=%s name=%s", getKey(), getId(), getName());
        mViewModel.detailContent(getKey(), getId());
    }

    private void getDetail(Vod item) {
        revealManualSearch = false;
        if (!isAutoMode()) mViewModel.stopSearch();
        saveHistory();
        getIntent().putExtra("key", item.getSiteKey());
        getIntent().putExtra("pic", item.getPic());
        getIntent().putExtra("id", item.getId());
        mBinding.scroll.scrollTo(0, 0);
        mClock.setCallback(null);
        updateNavigationKey();
        subtitlePlaybackSession.stop(this);
        player().reset();
        player().stop();
        getDetail();
    }

    private void setDetail(Result result) {
        long cost = System.currentTimeMillis() - detailStartTime;
        SpiderDebug.log("video-flow", "detail finish cost=%dms empty=%s msg=%s", cost, result.getList().isEmpty(), result.getMsg());
        recordDetailHealth(result, cost);
        if (service() == null) {
            mPendingDetail = result;
            SpiderDebug.log("video-flow", "detail pending service key=%s id=%s", getKey(), getId());
            return;
        }
        if (result.getList().isEmpty()) setEmpty(result.hasMsg());
        else setDetail(result.getVod());
        Notify.show(result.getMsg());
    }

    private void setEmpty(boolean finish) {
        if (isFromCollect() || finish) {
            finish();
        } else if (getName().isEmpty()) {
            showEmpty();
        } else {
            mBinding.name.setText(getName());
            App.post(mR4, 10000);
            checkSearch(false);
        }
    }

    private void showEmpty() {
        mBinding.progressLayout.showEmpty();
    }

    private void setDetail(Vod item) {
        mVod = item;
        mTmdbAutoDialogShown = false;
        mTmdbDetailFieldsApplied = false;
        item.checkPic(getPic());
        item.checkName(getName());
        boolean loadTmdbDetail = shouldLoadTmdbDetail();
        item.checkContent(getContent());
        setOriginalEnhancedActionVisibility(loadTmdbDetail && Setting.isOriginalEnhancedDetailPage());
        if (isIntentTmdbPlayback()) com.fongmi.android.tv.utils.TmdbEpisodeSorter.sort(item);
        applyTmdbEpisodeTitles(item);
        setTmdbRematchVisible(loadTmdbDetail);
        // 非 TMDB：立即揭开，全部内容一次性出现；TMDB：继续停在 loading，等富集完成再揭开
        if (!loadTmdbDetail) mBinding.progressLayout.showContent();
        mBinding.name.setText(item.getName());
        mFlagAdapter.addAll(item.getFlags());
        mBinding.video.requestFocus();
        App.removeCallbacks(mR4);
        checkHistory(item);
        checkFlag(item);
        checkKeepImg();
        setText(item);
        updateKeep();
        if (loadTmdbDetail) hideNativePersonalRecommendations();
        else loadNativePersonalRecommendations(item);
        if (loadTmdbDetail) showTmdbDetailLoading();

        // TMDB 增强：自动匹配并增强 Vod
        if (mTmdbUIAdapter != null && mTmdbUIAdapter.isReady()) {
            com.fongmi.android.tv.bean.TmdbItem tmdbItem = getTmdbItem();
            if (tmdbItem != null) {
                mTmdbUIAdapter.load(tmdbItem, item);
            } else {
                mTmdbUIAdapter.autoMatch(item.getName(), item);
            }
        }
    }

    private boolean shouldLoadTmdbDetail() {
        return mTmdbUIAdapter != null && mTmdbUIAdapter.isReady();
    }

    private void setOriginalEnhancedActionVisibility(boolean hide) {
        mBinding.shortDisplay.setVisibility(hide ? View.GONE : View.VISIBLE);
        mBinding.change1.setVisibility(hide ? View.GONE : View.VISIBLE);
    }

    private void setTmdbRematchVisible(boolean visible) {
        mBinding.tmdbRematch.setVisibility(visible ? View.VISIBLE : View.GONE);
        mBinding.change1.setNextFocusRightId(visible ? R.id.tmdbRematch : R.id.change1);
    }

    private void showTmdbDetailLoading() {
        mTmdbDetailLoading = true;
        mTmdbDetailRevealed = false;
        // 全屏 loading：隐藏全部内容（含视频窗口），只留转圈，等 TMDB 富集完成或超时再一次性揭开
        if (!mBinding.progressLayout.isProgress()) mBinding.progressLayout.showProgress();
        // setText 等内容填充可能把 remark/actor 等子视图改回 VISIBLE，强制压回隐藏避免泄漏
        mBinding.progressLayout.hideContent();
        App.removeCallbacks(mTmdbDetailTimeout);
        App.post(mTmdbDetailTimeout, TMDB_DETAIL_LOAD_TIMEOUT);
        SpiderDebug.log("tmdb-tv", "detail loading show (full-screen progress)");
    }

    // TMDB 数据成功返回：揭开内容（仅一次）并应用 TMDB 字段（每次都应用）
    private void finishTmdbDetail() {
        revealTmdbDetail();
        suppressTmdbNativeTextFields();
        if (mTmdbDetailFieldsApplied) return;
        mTmdbDetailFieldsApplied = true;
        applyTmdbDetailFields();
    }

    // 揭开全屏 loading、一次性显示全部内容，幂等（超时或数据到达都会调用，只执行一次）
    private void revealTmdbDetail() {
        if (mTmdbDetailRevealed) return;
        mTmdbDetailRevealed = true;
        mTmdbDetailLoading = false;
        App.removeCallbacks(mTmdbDetailTimeout);
        mBinding.progressLayout.showContent();
        SpiderDebug.log("tmdb-tv", "detail loading reveal (show content)");
    }

    private void applyTmdbDetailFields() {
        // 去掉集数、演员、导演；简介按钮默认隐藏（仅简介显示不全时再显示）
        suppressTmdbNativeTextFields();
        mBinding.content.setVisibility(View.GONE);
        setTmdbRematchVisible(true);

        // 年份、地区、类型取 TMDB
        if (mTmdbUIAdapter != null) {
            String year = mTmdbUIAdapter.getYear();
            String area = mTmdbUIAdapter.getArea();
            String genres = mTmdbUIAdapter.getGenresText();
            if (!TextUtils.isEmpty(year)) setText(mBinding.year, R.string.detail_year, year);
            if (!TextUtils.isEmpty(area)) setText(mBinding.area, R.string.detail_area, area);
            if (!TextUtils.isEmpty(genres)) setText(mBinding.type, R.string.detail_type, genres);
        }

        // 简介移到站源行下方显示（内容来自已 enrich 的 content tag）
        Object desc = mBinding.content.getTag();
        String overview = desc == null ? "" : desc.toString();
        if (!TextUtils.isEmpty(overview)) {
            mBinding.tmdbOverview.setMaxLines(Integer.MAX_VALUE);
            mBinding.tmdbOverview.setText(getString(R.string.detail_content, overview));
            mBinding.tmdbOverview.setVisibility(View.VISIBLE);
            // 布局完成后检测是否截断，截断则显示简介按钮
            mBinding.tmdbOverview.post(this::updateTmdbOverviewButton);
        } else {
            mBinding.tmdbOverview.setVisibility(View.GONE);
        }

        // 简介按钮默认隐藏，焦点先把视频右移到收藏（按钮显示时再修正）
        mBinding.video.setNextFocusRightId(R.id.keep);
    }

    private void updateTmdbOverviewButton() {
        if (isFinishing() || isDestroyed()) return;
        TextView view = mBinding.tmdbOverview;
        android.text.Layout layout = view.getLayout();
        if (layout != null) {
            int maxLines = getTmdbOverviewMaxLines(view, layout);
            if (view.getMaxLines() != maxLines) {
                view.setMaxLines(maxLines);
                view.post(this::updateTmdbOverviewButton);
                return;
            }
        }
        boolean truncated = isTextTruncated(view);
        mBinding.content.setVisibility(truncated ? View.VISIBLE : View.GONE);
        mBinding.video.setNextFocusRightId(truncated ? R.id.content : R.id.keep);
    }

    private int getTmdbOverviewMaxLines(TextView view, android.text.Layout layout) {
        int rowTop = mBinding.row2.getTop();
        int viewTop = view.getTop();
        int verticalPadding = view.getCompoundPaddingTop() + view.getCompoundPaddingBottom();
        int available = rowTop - viewTop - verticalPadding - ResUtil.dp2px(TMDB_OVERVIEW_ROW_GAP_DP + TMDB_OVERVIEW_BOTTOM_GUARD_DP);
        int maxLines = 0;
        for (int i = 0; i < layout.getLineCount(); i++) {
            if (layout.getLineBottom(i) > available) break;
            maxLines = i + 1;
        }
        return Math.max(1, maxLines);
    }

    private boolean isTextTruncated(TextView view) {
        android.text.Layout layout = view.getLayout();
        if (layout == null) return false;
        int lines = layout.getLineCount();
        if (lines <= 0) return false;
        return layout.getEllipsisCount(lines - 1) > 0;
    }

    private void showTmdbDetailFallback() {
        if (mTmdbDetailRevealed) return;
        SpiderDebug.log("tmdb-tv", "detail loading overlay timeout fallback");
        revealTmdbDetail();
        suppressTmdbNativeTextFields();
        finishEpisodeLoading();
    }

    private void setText(Vod item) {
        mBinding.content.setTag(item.getContent());
        setText(mBinding.year, R.string.detail_year, item.getYear());
        setText(mBinding.area, R.string.detail_area, item.getArea());
        setText(mBinding.type, R.string.detail_type, item.getTypeName());
        setText(mBinding.site, R.string.detail_site, getSite().getName());
        setText(mBinding.director, R.string.detail_director, item.getDirector());
        setText(mBinding.actor, R.string.detail_actor, item.getActor());
        setText(mBinding.remark, 0, item.getRemarks());
    }

    private void setText(TextView view, int resId, String text) {
        if (TextUtils.isEmpty(text) && !TextUtils.isEmpty(view.getText())) return;
        view.setText(Sniffer.buildClickable(resId > 0 ? getString(resId, text) : text, this::clickableSpan), TextView.BufferType.SPANNABLE);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        view.setLinkTextColor(MDColor.YELLOW_500);
        CustomMovement.bind(view);
    }

    private ClickableSpan clickableSpan(Result result) {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                VodActivity.start(getActivity(), getKey(), result);
                setRedirect(true);
            }
        };
    }

    private void applyTmdbEpisodeTitles(Vod vod) {
        Map<Integer, String> titles = getEpisodeTitles();
        if (vod == null || titles.isEmpty() || vod.getFlags() == null) return;
        for (Flag flag : vod.getFlags()) {
            for (Episode episode : flag.getEpisodes()) {
                String title = titles.get(episode.getNumber());
                if (TextUtils.isEmpty(title)) continue;
                String displayName = EpisodeTitleFormatter.withSourceFileSize(episode.getName(), EpisodeTitleFormatter.formatTmdbTitle(episode.getNumber(), title), Setting.isTmdbEpisodeFileSize());
                if (TextUtils.equals(episode.getDisplayName(), displayName)) continue;
                episode.setDisplayName(displayName);
            }
        }
    }

    private Map<Integer, String> getEpisodeTitles() {
        Map<Integer, String> titles = new HashMap<>();
        ArrayList<String> values = getIntent().getStringArrayListExtra("tmdb_episode_titles");
        if (values == null) return titles;
        for (String value : values) {
            String[] parts = value.split("\t", 2);
            if (parts.length != 2 || TextUtils.isEmpty(parts[1])) continue;
            try {
                titles.put(Integer.parseInt(parts[0]), parts[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        return titles;
    }

    private boolean isIntentTmdbPlayback() {
        ArrayList<String> values = getIntent().getStringArrayListExtra("tmdb_episode_titles");
        return values != null && !values.isEmpty();
    }

    private void getPlayer(Flag flag, Episode episode) {
        mBinding.widget.title.setText(getPlaybackControlTitle(episode));
        playerStartTime = System.currentTimeMillis();
        beginPlayHealth();
        SpiderDebug.log("video-flow", "player start key=%s flag=%s episode=%s url=%s", getKey(), flag.getFlag(), episode.getName(), episode.getUrl());
        mViewModel.playerContent(getKey(), flag.getFlag(), episode.getUrl());
        mBinding.widget.title.setSelected(true);
        updateHistory(episode);
        showProgress();
    }

    private void setPlayer(Result result) {
        if (isFinishing() || isDestroyed()) return;
        SpiderDebug.log("video-flow", "player finish cost=%dms useParse=%s multi=%s msg=%s", System.currentTimeMillis() - playerStartTime, result.shouldUseParse(), result.getUrl().isMulti(), result.getMsg());
        if (service() == null) {
            mPendingPlayer = result;
            SpiderDebug.log("video-flow", "player pending service key=%s id=%s", getKey(), getId());
            return;
        }
        if (!canApplyPlayerResult()) {
            SpiderDebug.log("video-flow", "drop player result before detail ready key=%s id=%s", getKey(), getId());
            return;
        }
        mQualityAdapter.addAll(result);
        mQualityAdapter.setPosition(mQualityAdapter.getPosition());
        setUseParse(result.shouldUseParse());
        setQualityVisible(result.getUrl().isMulti());
        if (result.hasArtwork() && !shouldKeepPushArtwork()) setArtwork(result.getArtwork());
        if (result.hasDesc()) mBinding.content.setTag(result.getDesc());
        if (result.hasPosition()) mHistory.setPosition(result.getPosition());
        mBinding.control.parse.setVisibility(isUseParse() ? View.VISIBLE : View.GONE);
        if (redirectToContentHandler(result)) return;
        startPlayer(getHistoryKey(), result, isUseParse(), getSite().getTimeout(), buildMetadata());
        subtitlePlaybackSession.onPlaybackStarted(this, result);
        if (DanmakuApi.canSearch()) DanmakuApi.search(mHistory.getVodName(), getEpisode().getName(), danmaku -> {
            if (DanmakuSetting.isSpiderFirst() && !result.getDanmaku().isEmpty()) player().addDanmaku(danmaku);
            else player().setDanmaku(danmaku);
        });
    }

    private boolean redirectToContentHandler(Result result) {
        boolean handled = com.fongmi.android.tv.content.ContentDispatcher.dispatchResult(this, getHistoryKey(), getKey(), getFlag().getFlag(), mHistory.getVodName(), mHistory.getVodPic(), mEpisodeAdapter.getItems(), mEpisodeAdapter.getPosition(), result, getSite().getTimeout());
        if (handled) finish();
        return handled;
    }

    private boolean canApplyPlayerResult() {
        return mFlagAdapter != null && mFlagAdapter.getItemCount() > 0 && mEpisodeAdapter != null && mEpisodeAdapter.getItemCount() > 0;
    }

    private void recordDetailHealth(Result result, long cost) {
        if (detailHealthRecorded) return;
        detailHealthRecorded = true;
        boolean success = result != null && !result.getList().isEmpty();
        String error = result == null ? "" : result.hasMsg() ? result.getMsg() : success ? "" : "empty";
        SiteHealthStore.recordDetail(getKey(), success, cost, error);
    }

    private void beginPlayHealth() {
        playHealthKey = getKey();
        playHealthRecorded = false;
    }

    private void recordPlayHealth(boolean success, String error) {
        if (playHealthRecorded) return;
        playHealthRecorded = true;
        SiteHealthStore.recordPlay(TextUtils.isEmpty(playHealthKey) ? getKey() : playHealthKey, success, error);
    }

    @Override
    public void onItemClick(Flag item) {
        if (mFlagAdapter.getItemCount() == 0 || item.isSelected()) return;
        mFlagAdapter.setSelected(item);
        mBinding.flag.setSelectedPosition(mFlagAdapter.indexOf(item));
        notifyItemChanged(mBinding.flag, mFlagAdapter);
        setEpisodeAdapter(item.getEpisodes());
        setQualityVisible(false);
        seamless(item);
    }

    private void setEpisodeAdapter(List<Episode> items) {
        setEpisodeAdapter(items, true);
    }

    private void setEpisodeAdapter(List<Episode> items, boolean scrollToCurrent) {
        boolean isEmpty = items.isEmpty();
        boolean hasMultiple = items.size() > 1;
        boolean tmdbMode = isTmdbSourceEnabled();
        boolean tmdbAdapterReady = mTmdbUIAdapter != null && mTmdbUIAdapter.isReady();
        boolean tmdbAdapterLoaded = mTmdbUIAdapter != null && mTmdbUIAdapter.isLoaded();
        boolean waitTmdbEpisodes = EpisodeDisplayPolicy.shouldWaitForTmdbEpisodes(tmdbMode, mTmdbDetailLoading, tmdbAdapterReady, tmdbAdapterLoaded, items);
        boolean showTmdbEpisodeChrome = EpisodeDisplayPolicy.shouldShowTmdbEpisodeChrome(tmdbMode, waitTmdbEpisodes, items);
        boolean useTmdbCards = EpisodeDisplayPolicy.shouldUseTmdbEpisodeCards(tmdbMode, items);
        mBinding.episodeContainer.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        mBinding.control.action.episodes.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);

        if (showTmdbEpisodeChrome && hasMultiple) episodeGridMode = Setting.getTmdbEpisodeGridMode();
        if (!showTmdbEpisodeChrome || !hasMultiple) episodeGridMode = false;
        mBinding.episodeHeader.setVisibility(showTmdbEpisodeChrome && !isEmpty ? View.VISIBLE : View.GONE);
        mBinding.episodeReverse.setVisibility(showTmdbEpisodeChrome && hasMultiple ? View.VISIBLE : View.GONE);
        mBinding.episodeViewMode.setVisibility(showTmdbEpisodeChrome && hasMultiple ? View.VISIBLE : View.GONE);
        updateEpisodeFallbackStillUrl();
        mEpisodeAdapter.setUseTmdbCard(useTmdbCards);
        mEpisodeGridAdapter.setUseTmdbCard(useTmdbCards);
        applyActionButtonVisibility();
        mEpisodeAdapter.addAll(items);
        mEpisodeGridAdapter.addAll(items);

        applyEpisodeViewMode(false);

        // 控制加载指示器和选集列表的显示
        if (waitTmdbEpisodes) {
            mBinding.episodeLoadingIndicator.setVisibility(View.VISIBLE);
            setEpisodeContentVisible(false);
        } else {
            mBinding.episodeLoadingIndicator.setVisibility(View.GONE);
            setEpisodeContentVisible(true);
        }

        setArrayAdapter(items.size());
        updateFocus();
        if (scrollToCurrent) scrollToCurrentEpisode();
        setR2Callback();
        // 延迟刷新一次，确保焦点状态正确初始化
        mBinding.episode.post(() -> {
            if (mEpisodeAdapter != null) mEpisodeAdapter.notifyDataSetChanged();
            if (mEpisodeGridAdapter != null) mEpisodeGridAdapter.notifyDataSetChanged();
        });
    }

    // TMDB 加载结束后兜底：若仍卡在剧集加载指示器（电影无集数、未匹配到、获取失败等），
    // 隐藏指示器并以普通文本模式揭开选集列表，避免「正在加载剧集信息...」永久停留
    private void finishEpisodeLoading() {
        if (mBinding.episodeLoadingIndicator.getVisibility() != View.VISIBLE) return;
        episodeGridMode = false;
        mEpisodeAdapter.setUseTmdbCard(false);
        mEpisodeGridAdapter.setUseTmdbCard(false);
        applyEpisodeViewMode(false);
        mBinding.episodeHeader.setVisibility(View.GONE);
        mBinding.episodeReverse.setVisibility(View.GONE);
        mBinding.episodeViewMode.setVisibility(View.GONE);
        mBinding.episodeLoadingIndicator.setVisibility(View.GONE);
        setEpisodeContentVisible(true);
        if (mEpisodeAdapter != null) mEpisodeAdapter.notifyDataSetChanged();
        if (mEpisodeGridAdapter != null) mEpisodeGridAdapter.notifyDataSetChanged();
        SpiderDebug.log("tmdb-tv", "episode loading finished without tmdb episodes, reveal plain list");
    }

    private void refreshEpisodeTitles() {
        if (mEpisodeAdapter == null || mFlagAdapter == null || mFlagAdapter.getItemCount() == 0) return;
        int position = mEpisodeAdapter.getSelectedPosition();
        setEpisodeAdapter(getFlag().getEpisodes(), false);
        if (position != RecyclerView.NO_POSITION) scrollToEpisode(position);
    }

    private void seamless(Flag flag) {
        Episode episode = flag.find(mHistory.getEpisode(), getMark().isEmpty());
        setQualityVisible(episode != null && episode.isSelected() && mQualityAdapter.getItemCount() > 1);
        if (episode == null || episode.isSelected()) return;
        selectEpisode(episode, false);
    }

    @Override
    public void onItemClick(Episode item) {
        if (shouldEnterFullscreen(item)) return;
        selectEpisode(item, true);
    }

    private void onEpisodeLongClick(Episode item) {
        com.fongmi.android.tv.ui.dialog.EpisodeDetailDialog.show(this, item, getSite());
    }

    private void selectEpisode(Episode item, boolean scrollToEpisode) {
        int oldPosition = mEpisodeAdapter.getSelectedPosition();
        mFlagAdapter.toggle(item);
        int newPosition = mEpisodeAdapter.indexOf(item);
        if (newPosition == RecyclerView.NO_POSITION) newPosition = mEpisodeAdapter.getSelectedPosition();
        mEpisodeAdapter.notifySelectionChanged(oldPosition, newPosition);
        if (mEpisodeGridAdapter != null) mEpisodeGridAdapter.notifySelectionChanged(oldPosition, newPosition);
        boolean episodeFocused = isEpisodeListFocused() || isEpisodeGridFocused();
        SpiderDebug.log("video-episode", "select old=%s new=%s focus=%s scroll=%s name=%s", oldPosition, newPosition, episodeFocused, scrollToEpisode, item.getName());
        if (scrollToEpisode && !episodeFocused) scrollToEpisode(newPosition);
        if (isFullscreen()) Notify.show(getString(R.string.play_ready, item.getName()));
        onRefresh();
    }

    private void setQualityVisible(boolean visible) {
        mBinding.quality.setVisibility(visible ? View.VISIBLE : View.GONE);
        mBinding.control.action.actionQuality.setVisibility(visible ? View.VISIBLE : View.GONE);
        updateActionQuality(mViewModel.getPlayer().getValue());
        updateFocus();
        updateEpisodeWindow();
        setR2Callback();
    }

    private void updateActionQuality(Result result) {
        String name = getQualityName(result, result == null ? 0 : result.getUrl().getPosition());
        mBinding.control.action.actionQuality.setText(TextUtils.isEmpty(name) ? getString(R.string.detail_quality) : getString(R.string.detail_quality) + " " + name);
    }

    private String[] getQualityItems(Result result) {
        int count = result.getUrl().getValues().size();
        String[] items = new String[count];
        for (int i = 0; i < count; i++) items[i] = getQualityName(result, i);
        return items;
    }

    private String getQualityName(Result result, int position) {
        if (result == null || position < 0 || position >= result.getUrl().getValues().size()) return "";
        String name = result.getUrl().n(position);
        return TextUtils.isEmpty(name) ? String.valueOf(position + 1) : name;
    }

    private void onQuality() {
        Result result = mViewModel.getPlayer().getValue();
        if (result == null || !result.getUrl().isMulti()) return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.detail_quality)
                .setSingleChoiceItems(getQualityItems(result), result.getUrl().getPosition(), (dialog, which) -> {
                    dialog.dismiss();
                    changeQuality(result, which);
                })
                .show();
    }

    private void changeQuality(Result result, int position) {
        if (result == null || !result.getUrl().isMulti()) return;
        if (result.getUrl().getPosition() == position) {
            updateActionQuality(result);
            return;
        }
        mQualityAdapter.setPosition(position);
        updateActionQuality(result);
        onItemClick(result);
    }

    @Override
    public void onItemClick(Result result) {
        updateActionQuality(result);
        beginPlayHealth();
        startPlayer(getHistoryKey(), result, isUseParse(), getSite().getTimeout(), buildMetadata());
        subtitlePlaybackSession.onPlaybackStarted(this, result);
    }

    private void reverseEpisode(boolean scroll) {
        mFlagAdapter.reverse();
        setEpisodeAdapter(getFlag().getEpisodes(), scroll);
        if (scroll) scrollToCurrentEpisode();
        else scrollToFirstEpisode();
    }

    private void toggleEpisodeViewMode() {
        episodeGridMode = !episodeGridMode;
        Setting.putTmdbEpisodeGridMode(episodeGridMode);
        applyEpisodeViewMode(true);
    }

    private void applyEpisodeViewMode(boolean scrollToCurrent) {
        if (mEpisodeAdapter == null || mEpisodeGridAdapter == null) return;
        int spanCount = getEpisodeGridSpanCount();
        RecyclerView.LayoutManager layoutManager = mBinding.episodeGrid.getLayoutManager();
        if (layoutManager instanceof GridLayoutManager gridLayoutManager && gridLayoutManager.getSpanCount() != spanCount) {
            gridLayoutManager.setSpanCount(spanCount);
        }
        mBinding.episode.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.episode.setVerticalSpacing(ResUtil.dp2px(8));
        mEpisodeAdapter.setGridMode(false);
        mEpisodeAdapter.setVerticalGridMode(false);
        mEpisodeAdapter.setColumn(1);
        mEpisodeGridAdapter.setGridMode(true);
        mEpisodeGridAdapter.setVerticalGridMode(true);
        mEpisodeGridAdapter.setColumn(spanCount);
        mBinding.episodeViewMode.setText(episodeGridMode ? R.string.detail_episode_view_list : R.string.detail_episode_view_grid);
        updateEpisodeReverseText();
        updateFocus();
        setEpisodeContentVisible(mBinding.episodeLoadingIndicator.getVisibility() != View.VISIBLE);
        if (scrollToCurrent) scrollToCurrentEpisode();
    }

    private void updateEpisodeReverseText() {
        if (mHistory == null) return;
        mBinding.episodeReverse.setText(mHistory.isRevSort() ? R.string.detail_episode_forward : R.string.detail_episode_reverse);
    }

    private void setEpisodeContentVisible(boolean visible) {
        if (!visible) {
            mBinding.episode.setVisibility(View.INVISIBLE);
            mBinding.episode.setAlpha(0f);
            mBinding.episodeGrid.setVisibility(View.GONE);
            return;
        }
        if (episodeGridMode) {
            mBinding.episode.setVisibility(View.GONE);
            mBinding.episode.setAlpha(1f);
            mBinding.episodeGrid.setVisibility(View.VISIBLE);
            mBinding.episodeGrid.setAlpha(1f);
        } else {
            mBinding.episode.setVisibility(View.VISIBLE);
            mBinding.episode.setAlpha(1f);
            mBinding.episodeGrid.setVisibility(View.GONE);
        }
    }

    private View getActiveEpisodeContentView() {
        return episodeGridMode ? mBinding.episodeGrid : mBinding.episode;
    }

    private void scrollToCurrentEpisode() {
        scrollToEpisode(mEpisodeAdapter.getPosition());
    }

    private void scrollToFirstEpisode() {
        scrollToEpisode(0, true);
    }

    private void scrollToEpisode(int position) {
        scrollToEpisode(position, false);
    }

    private void scrollToEpisode(int position, boolean requestFocus) {
        if (position < 0 || position >= mEpisodeAdapter.getItemCount()) return;
        if (episodeGridMode) {
            mBinding.episodeGrid.post(() -> {
                updateEpisodeWindowNow();
                RecyclerView.LayoutManager layoutManager = mBinding.episodeGrid.getLayoutManager();
                if (layoutManager instanceof GridLayoutManager gridLayoutManager) {
                    gridLayoutManager.scrollToPositionWithOffset(position, 0);
                } else {
                    mBinding.episodeGrid.scrollToPosition(position);
                }
                if (!requestFocus) return;
                mBinding.episodeGrid.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    RecyclerView.ViewHolder holder = mBinding.episodeGrid.findViewHolderForAdapterPosition(position);
                    if (holder != null) holder.itemView.requestFocus();
                    else mBinding.episodeGrid.requestFocus();
                });
            });
        } else {
            mBinding.episode.post(() -> {
                updateEpisodeWindowNow();
                mBinding.episode.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    mBinding.episode.setSelectedPosition(position);
                    if (requestFocus) mBinding.episode.requestFocus();
                });
            });
        }
    }

    private void updateEpisodeWindow() {
        if (mEpisodeAdapter == null || mEpisodeAdapter.getItemCount() == 0) return;
        mBinding.episode.post(this::updateEpisodeWindowNow);
    }

    private void updateEpisodeWindowNow() {
        // HorizontalGridView不需要设置固定高度
        // 已改为横向滚动，让其自动适应内容高度
    }

    private int getEpisodeWindowHeight() {
        int column = Math.max(1, EpisodeAdapter.getColumn(mEpisodeAdapter.getItems()));
        int totalRows = Math.max(1, (mEpisodeAdapter.getItemCount() + column - 1) / column);
        int rowHeight = ResUtil.dp2px(40);
        int spacing = mBinding.episode.getVerticalSpacing();
        int maxRows = getEpisodeMaxRows(rowHeight, spacing);
        int rows = Math.min(totalRows, maxRows);
        return rowHeight * rows + spacing * Math.max(0, rows - 1) + mBinding.episode.getPaddingTop() + mBinding.episode.getPaddingBottom();
    }

    private int getEpisodeMaxRows(int rowHeight, int spacing) {
        int legacyRows = ResUtil.getScreenHeight() < ResUtil.dp2px(560) ? 2 : 3;
        int available = getEpisodeAvailableHeight();
        if (available <= 0) return legacyRows;
        int content = Math.max(0, available - mBinding.episode.getPaddingTop() - mBinding.episode.getPaddingBottom());
        int rows = (content + spacing) / (rowHeight + spacing);
        return Math.max(legacyRows, rows);
    }

    private int getEpisodeAvailableHeight() {
        int height = mBinding.scroll.getHeight();
        if (height <= 0) return 0;
        int available = height - mBinding.scroll.getPaddingTop() - mBinding.scroll.getPaddingBottom();
        ViewGroup.LayoutParams episodeParams = mBinding.episode.getLayoutParams();
        if (episodeParams instanceof ViewGroup.MarginLayoutParams margins) available -= margins.topMargin + margins.bottomMargin;
        for (int i = 0; i < mBinding.scroll.getChildCount(); i++) {
            View child = mBinding.scroll.getChildAt(i);
            if (child == mBinding.episode || child.getVisibility() == View.GONE) continue;
            available -= child.getMeasuredHeight();
            ViewGroup.LayoutParams params = child.getLayoutParams();
            if (params instanceof ViewGroup.MarginLayoutParams margins) available -= margins.topMargin + margins.bottomMargin;
        }
        return available;
    }

    @Override
    public void onItemClick(Parse item) {
        setParse(item);
        onRefresh();
    }

    private void setParse(Parse item) {
        VodConfig.get().setParse(item);
        notifyItemChanged(mBinding.control.parse, mParseAdapter);
    }

    private void setArrayAdapter(int size) {
        int segment = getEpisodeSegmentSize(size);
        List<String> items = new ArrayList<>();
        items.add(getString(R.string.play_reverse));
        items.add(getString(mHistory.getRevPlayText()));
        mBinding.array.setVisibility(size > 1 ? View.VISIBLE : View.GONE);
        if (mHistory.isRevSort()) for (int i = size; i > 0; i -= segment) items.add(i + "-" + Math.max(i - segment + 1, 1));
        else for (int i = 0; i < size; i += segment) items.add((i + 1) + "-" + Math.min(i + segment, size));
        mArrayAdapter.setSegmentSize(segment);
        mArrayAdapter.addAll(items);
        updateFocus();
    }

    private int getEpisodeSegmentSize(int size) {
        return size <= 60 ? 20 : 40;
    }

    private int findFocusDown(int index) {
        List<Integer> orders = getEpisodeFocusOrders();
        for (int i = 0; i < orders.size(); i++) if (i > index) if (isVisible(findViewById(orders.get(i)))) return orders.get(i);
        return 0;
    }

    private int findFocusUp(int index) {
        List<Integer> orders = getEpisodeFocusOrders();
        for (int i = orders.size() - 1; i >= 0; i--) if (i < index) if (isVisible(findViewById(orders.get(i)))) return orders.get(i);
        return 0;
    }

    private List<Integer> getEpisodeFocusOrders() {
        return Arrays.asList(R.id.flag, R.id.quality, R.id.array, R.id.episodeReverse, R.id.episodeViewMode, R.id.episode, R.id.episodeGrid, R.id.part, R.id.quick);
    }

    private void updateFocus() {
        mArrayAdapter.setNextFocus(findFocusUp(2), findFocusDown(2));
        mEpisodeAdapter.setNextFocusUp(findFocusUp(5));
        mFlagAdapter.setNextFocusDown(findFocusDown(0));
        mEpisodeAdapter.setNextFocusDown(findFocusDown(5));
        if (mEpisodeGridAdapter != null) {
            mEpisodeGridAdapter.setNextFocusUp(findFocusUp(6));
            mEpisodeGridAdapter.setNextFocusDown(findFocusDown(6));
        }
        updateEpisodeHeaderFocus();
        mPartAdapter.setNextFocus(findFocusUp(7), findFocusDown(7));
        mQuickAdapter.setNextFocus(findFocusUp(8), findFocusDown(8));
        int searchDown = isVisible(mBinding.quick) ? R.id.quick : isVisible(mBinding.part) ? R.id.part : findFocusDown(-1);
        mBinding.search.setNextFocusDownId(searchDown == 0 ? View.NO_ID : searchDown);
    }

    private void updateEpisodeHeaderFocus() {
        int up = findFocusUp(3);
        int down = findFocusDown(4);
        mBinding.episodeReverse.setNextFocusUpId(up == 0 ? View.NO_ID : up);
        mBinding.episodeReverse.setNextFocusDownId(down == 0 ? View.NO_ID : down);
        mBinding.episodeReverse.setNextFocusRightId(R.id.episodeViewMode);
        mBinding.episodeViewMode.setNextFocusUpId(up == 0 ? View.NO_ID : up);
        mBinding.episodeViewMode.setNextFocusDownId(down == 0 ? View.NO_ID : down);
        mBinding.episodeViewMode.setNextFocusLeftId(R.id.episodeReverse);
    }

    private boolean isEpisodeListFocused() {
        return isFocusInside(mBinding.episode);
    }

    private boolean isEpisodeGridFocused() {
        return isFocusInside(mBinding.episodeGrid);
    }

    private boolean isFocusInside(View view) {
        View focus = getCurrentFocus();
        while (focus != null) {
            if (focus == view) return true;
            ViewParent parent = focus.getParent();
            focus = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private boolean onEpisodeKey(KeyEvent event) {
        if (!KeyUtil.isActionDown(event) || !KeyUtil.isUpKey(event)) return false;
        RecyclerView episodeView = episodeGridMode ? mBinding.episodeGrid : mBinding.episode;
        RecyclerView.ViewHolder holder = episodeView.findContainingViewHolder(getCurrentFocus());
        if (holder == null) return false;
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return false;
        if (episodeGridMode) {
            RecyclerView.LayoutManager layoutManager = mBinding.episodeGrid.getLayoutManager();
            int spanCount = layoutManager instanceof GridLayoutManager gridLayoutManager ? gridLayoutManager.getSpanCount() : getEpisodeGridSpanCount();
            if (position >= spanCount) return false;
        } else if (position != 0) {
            return false;
        }
        int target = findFocusUp(episodeGridMode ? 6 : 5);
        if (target == 0) return false;
        View view = findViewById(target);
        if (view == null || view.getVisibility() != View.VISIBLE) return false;
        view.requestFocus();
        return true;
    }

    @Override
    public void onRevSort() {
        mHistory.setRevSort(!mHistory.isRevSort());
        reverseEpisode(false);
    }

    @Override
    public void onRevPlay(TextView view) {
        mHistory.setRevPlay(!mHistory.isRevPlay());
        view.setText(mHistory.getRevPlayText());
        Notify.show(mHistory.getRevPlayHint());
    }

    private boolean shouldEnterFullscreen(Episode item) {
        boolean enter = !isFullscreen() && item.isSelected();
        if (enter) enterFullscreen();
        return enter;
    }

    private void enterFullscreen() {
        mFocus1 = getCurrentFocus();
        mBinding.video.requestFocus();
        mBinding.video.setForeground(null);
        mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        mBinding.flag.setSelectedPosition(mFlagAdapter.getPosition());
        mKeyDown.setFull(true);
        setFullscreen(true);
        mFocus2 = null;
    }

    private void exitFullscreen() {
        mBinding.video.setForeground(ResUtil.getDrawable(R.drawable.selector_video));
        mBinding.video.setLayoutParams(mFrameParams);
        getFocus1().requestFocus();
        mKeyDown.setFull(false);
        setFullscreen(false);
        mFocus2 = null;
        hideInfo();
    }

    private void onContent() {
        if (mBinding.content.getTag() == null) return;
        ContentDialog.create().content(mBinding.content.getTag().toString()).show(this);
    }

    private void onSearch() {
        String keyword = mBinding.name.getText().toString();
        if (TextUtils.isEmpty(keyword)) return;
        initSearch(keyword, false);
    }

    private void onShortDisplay() {
        Setting.putCompactEpisodeTitle(!Setting.isCompactEpisodeTitle());
        setShortDisplay();
        refreshEpisodeTitles();
    }

    private void setShortDisplay() {
        mBinding.shortDisplay.setSelected(Setting.isCompactEpisodeTitle());
    }

    private void onKeep() {
        Keep keep = Keep.find(getHistoryKey());
        Notify.show(keep != null ? R.string.keep_del : R.string.keep_add);
        if (keep != null) keep.delete();
        else createKeep();
        checkKeepImg();
    }

    private void onVideo() {
        if (!isFullscreen()) enterFullscreen();
    }

    private void onChange() {
        checkSearch(true);
    }

    private void onFullscreen() {
        boolean exit = isFullscreen();
        if (exit) exitFullscreen();
        else enterFullscreen();
        showControl(exit ? mBinding.control.action.fullscreen : mBinding.control.action.player);
    }

    private void onEpisodes() {
        if (mFlagAdapter.getItemCount() == 0 || mEpisodeAdapter.getItemCount() < 2) return;
        hideControl();
        Flag flag = getFlag();
        boolean tmdbCard = flag != null && EpisodeDisplayPolicy.shouldUseTmdbEpisodeCards(isTmdbSourceEnabled(), flag.getEpisodes());
        EpisodeListDialog.create().flags(mFlagAdapter.getItems()).tmdbCard(tmdbCard).show(this);
    }

    private void onRepeat() {
        player().setRepeatOne(!player().isRepeatOne());
        mBinding.control.action.repeat.setSelected(player().isRepeatOne());
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        mBinding.control.action.repeat.setSelected(player().isRepeatOne());
    }

    private void checkNext() {
        checkNext(true);
    }

    private void checkNext(boolean notify) {
        if (mHistory.isRevPlay()) onPrev(notify);
        else onNext(notify);
    }

    private void checkPrev() {
        if (mHistory.isRevPlay()) onNext(true);
        else onPrev(true);
    }

    private void onNext(boolean notify) {
        Episode item = mEpisodeAdapter.getNext();
        if (!item.isSelected()) onItemClick(item);
        else if (notify) Notify.show(mHistory.isRevPlay() ? R.string.error_play_prev : R.string.error_play_next);
    }

    private void onPrev(boolean notify) {
        Episode item = mEpisodeAdapter.getPrev();
        if (!item.isSelected()) onItemClick(item);
        else if (notify) Notify.show(mHistory.isRevPlay() ? R.string.error_play_next : R.string.error_play_prev);
    }

    private void onScale() {
        int index = getScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        setScale(index == array.length - 1 ? 0 : ++index);
    }

    private void onLut() {
        mBinding.lutQuick.toggle(player(), mBinding.exo, this::onLutChanged, new com.fongmi.android.tv.ui.custom.LutQuickPanel.ImportCallback() {
            @Override
            public void onImportLut() {
                onLutImport();
            }

            @Override
            public void onSelectLutDir() {
                onLutDir();
            }
        });
        focusLutQuickIfVisible();
    }

    private void focusLutQuickIfVisible() {
        mBinding.lutQuick.post(this::focusLutQuickContent);
        mBinding.lutQuick.postDelayed(this::focusLutQuickContent, 220);
        mBinding.lutQuick.postDelayed(this::focusLutQuickContent, 420);
    }

    private boolean focusLutQuickContent() {
        if (!isVisible(mBinding.lutQuick)) return false;
        View focus = getCurrentFocus();
        RecyclerView recycler = findRecyclerView(mBinding.lutQuick);
        if (focus != null && isChildOf(mBinding.lutQuick, focus) && focus != recycler) return true;
        if (mBinding.lutQuick.focusSelectedEntry()) return true;
        if (focusRecyclerItem(recycler)) return true;
        return focusFirstChild(mBinding.lutQuick);
    }

    private RecyclerView findRecyclerView(View view) {
        if (view instanceof RecyclerView recycler) return recycler;
        if (!(view instanceof ViewGroup group)) return null;
        for (int i = 0; i < group.getChildCount(); i++) {
            RecyclerView recycler = findRecyclerView(group.getChildAt(i));
            if (recycler != null) return recycler;
        }
        return null;
    }

    private boolean focusRecyclerItem(RecyclerView recycler) {
        return focusRecyclerPosition(recycler, 0);
    }

    private boolean focusRecyclerPosition(RecyclerView recycler, int position) {
        if (recycler == null || recycler.getVisibility() != View.VISIBLE || !recycler.isEnabled()) return false;
        RecyclerView.Adapter<?> adapter = recycler.getAdapter();
        if (adapter == null || adapter.getItemCount() <= 0) return false;
        if (position < 0 || position >= adapter.getItemCount()) return false;
        recycler.scrollToPosition(position);
        RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(position);
        if (holder != null && focusFirstChild(holder.itemView)) return true;
        for (int i = 0; i < recycler.getChildCount(); i++) {
            View child = recycler.getChildAt(i);
            if (recycler.getChildAdapterPosition(child) == position && focusFirstChild(child)) return true;
        }
        recycler.post(() -> {
            RecyclerView.ViewHolder next = recycler.findViewHolderForAdapterPosition(position);
            if (next != null) {
                focusFirstChild(next.itemView);
                return;
            }
            for (int i = 0; i < recycler.getChildCount(); i++) {
                View child = recycler.getChildAt(i);
                if (recycler.getChildAdapterPosition(child) == position) {
                    focusFirstChild(child);
                    return;
                }
            }
        });
        return true;
    }

    private boolean focusFirstChild(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE || !view.isEnabled()) return false;
        if (view instanceof RecyclerView recycler) return focusRecyclerItem(recycler);
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                if (focusFirstChild(group.getChildAt(i))) return true;
            }
        }
        if (view.isFocusable() && view.requestFocus()) return true;
        return false;
    }

    private boolean isChildOf(ViewGroup parent, View child) {
        for (View view = child; view != null; ) {
            if (view == parent) return true;
            if (!(view.getParent() instanceof View next)) return false;
            view = next;
        }
        return false;
    }

    private void onLutImport() {
        if (!LutStore.hasUserDir()) {
            pendingLutImport = true;
            chooseLutDir();
            return;
        }
        chooseLutFile();
    }

    private void onLutDir() {
        pendingLutImport = false;
        chooseLutDir();
    }

    private void chooseLutFile() {
        FileChooser.from(mLutFile).show("*/*", new String[]{"application/octet-stream", "text/*", "image/*", "*/*"});
    }

    private void chooseLutDir() {
        FileChooser.from(mLutDir).showDirectory();
    }

    private void onSpeed() {
        mBinding.control.action.speed.setText(player().addSpeed());
        saveDefaultSpeed();
        setR1Callback();
    }

    private void onSpeedAdd() {
        mBinding.control.action.speed.setText(player().addSpeed(0.25f));
        saveDefaultSpeed();
        setR1Callback();
    }

    private void onSpeedSub() {
        mBinding.control.action.speed.setText(player().subSpeed(0.25f));
        saveDefaultSpeed();
        setR1Callback();
    }

    private boolean onSpeedLong() {
        mBinding.control.action.speed.setText(player().toggleSpeed());
        saveDefaultSpeed();
        setR1Callback();
        return true;
    }

    private void saveDefaultSpeed() {
        PlayerSetting.putDefaultSpeed(player().getSpeed());
        mHistory.setSpeed(player().getSpeed());
    }

    private void onReset() {
        if (isReplay()) onReplay();
        else onRefresh();
    }

    private void onReplay() {
        mHistory.setPosition(C.TIME_UNSET);
        if (player().isEmpty()) onRefresh();
        else player().setMediaItem();
    }

    private void onRefresh() {
        saveHistory();
        subtitlePlaybackSession.stop(this);
        player().stop();
        player().clear();
        mClock.setCallback(null);
        if (mFlagAdapter.getItemCount() == 0) return;
        if (mEpisodeAdapter.getItemCount() == 0) return;
        getPlayer(getFlag(), getEpisode());
    }

    private boolean onResetToggle() {
        Setting.putReset(Math.abs(Setting.getReset() - 1));
        mBinding.control.action.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
        return true;
    }

    private void onOpening() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetOpening(position, duration)) setOpening(position);
    }

    private void onOpeningAdd() {
        setOpening(Math.max(0, Math.max(0, mHistory.getOpening()) + 1000));
    }

    private void onOpeningSub() {
        setOpening(Math.max(0, Math.max(0, mHistory.getOpening()) - 1000));
    }

    private boolean onOpeningReset() {
        setOpening(0);
        return true;
    }

    private void setOpening(long opening) {
        mHistory.setOpening(opening);
        mBinding.control.action.opening.setText(opening <= 0 ? getString(R.string.play_op) : Util.timeMs(mHistory.getOpening()));
    }

    private void onEnding() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetEnding(position, duration)) setEnding(duration - position);
    }

    private void onEndingAdd() {
        setEnding(Math.max(0, Math.max(0, mHistory.getEnding()) + 1000));
    }

    private void onEndingSub() {
        setEnding(Math.max(0, Math.max(0, mHistory.getEnding()) - 1000));
    }

    private boolean onEndingReset() {
        setEnding(0);
        return true;
    }

    private void setEnding(long ending) {
        mHistory.setEnding(ending);
        mBinding.control.action.ending.setText(ending <= 0 ? getString(R.string.play_ed) : Util.timeMs(mHistory.getEnding()));
    }

    private void onChoose() {
        String[] kernel = ResUtil.getStringArray(R.array.select_player_kernel);
        String[] items = Arrays.copyOf(kernel, kernel.length + 1);
        items[kernel.length] = "外调";
        new androidx.appcompat.app.AlertDialog.Builder(this).setItems(items, (dialog, which) -> {
            if (which < kernel.length) {
                player().switchPlayerManually(which);
                setPlayer();
                setDecode();
            } else {
                PlayerHelper.choose(this, player().getUrl(), player().getHeaders(), player().isVod(), player().getPosition(), mBinding.widget.title.getText());
                setRedirect(true);
            }
        }).show();
    }

    private void onPlayerKernel() {
        mClock.setCallback(null);
        onChoose();
    }

    private boolean onPlayerKernelLong() {
        onPlayerKernel();
        return true;
    }

    private void onDecode() {
        mClock.setCallback(null);
        player().toggleDecode();
        setDecode();
    }

    private void onTrack(View view) {
        TrackDialog.create().type(Integer.parseInt(view.getTag().toString())).player(player()).search(this::showSubtitleSearch).show(this);
        hideControl();
    }

    private void onTitle() {
        TitleDialog.create().player(player()).show(this);
        hideControl();
    }

    private void onDanmaku() {
        DanmakuDialog.create().player(player()).show(this);
        hideControl();
    }

    private void onToggle() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else showControl(getFocus2());
    }

    private void showProgress() {
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
        hideCenter();
        hideError();
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
    }

    private void showError(String text) {
        mBinding.widget.error.setVisibility(View.VISIBLE);
        mBinding.widget.text.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.text.setText("");
    }

    private void showInfo() {
        showTopInfo();
        mBinding.widget.center.setVisibility(View.VISIBLE);
        mBinding.widget.duration.setText(player().getDurationTime());
        mBinding.widget.position.setText(player().getPositionTime(0));
    }

    private void showTopInfo() {
        mBinding.widget.top.setVisibility(View.VISIBLE);
        mBinding.widget.size.setText(player().getSizeText());
    }

    private void hideInfo() {
        mBinding.widget.top.setVisibility(View.GONE);
        mBinding.widget.center.setVisibility(View.GONE);
    }

    private void showControl(View view) {
        showTopInfo();
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        if (mOsd != null) mOsd.setControlsVisible(true);
        view.requestFocus();
        setR1Callback();
    }

    private void hideControl() {
        mBinding.control.getRoot().setVisibility(View.GONE);
        if (mOsd != null) mOsd.setControlsVisible(false);
        if (player().isPlaying()) mBinding.widget.top.setVisibility(View.GONE);
        App.removeCallbacks(mR1);
    }

    private void hideCenter() {
        mBinding.widget.action.setImageResource(R.drawable.ic_widget_play);
        mBinding.widget.center.setVisibility(View.GONE);
        if (isGone(mBinding.control.getRoot())) mBinding.widget.top.setVisibility(View.GONE);
    }

    private void setR1Callback() {
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void setR2Callback() {
        App.post(mR2, 500);
    }

    private void setArtwork(String url) {
        if (mHistory != null) mHistory.setVodPic(url);
        loadArtwork(url);
        setContextWall(getContextWall());
        updateEpisodeFallbackStillUrl();
    }

    private void setArtwork() {
        if (mHistory == null) return;
        setArtwork(mHistory.getVodPic());
    }

    private void loadArtwork(String url) {
        ImgUtil.load(this, url, new CustomTarget<>() {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                mBinding.exo.setDefaultArtwork(resource);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                mBinding.exo.setDefaultArtwork(errorDrawable);
            }
        });
    }

    private String getContextWall() {
        if (!TextUtils.isEmpty(getWallPic())) return getWallPic();
        return mHistory == null ? "" : mHistory.getWallPic();
    }

    private String lockContextWall(String url) {
        String wall = Objects.toString(url, "");
        if (mContextWallLockedUrl == null && !TextUtils.isEmpty(wall)) mContextWallLockedUrl = wall;
        return mContextWallLockedUrl == null ? wall : mContextWallLockedUrl;
    }

    private void setContextWall(String url) {
        if (!Setting.isPlaybackArtworkWall()) {
            mContextWallUrl = "";
            hideContextWall();
            return;
        }
        String wall = lockContextWall(url);
        if (TextUtils.isEmpty(wall)) {
            mContextWallUrl = "";
            hideContextWall();
            return;
        }
        if (Objects.equals(mContextWallUrl, wall)) return;
        mContextWallUrl = wall;
        resetContextWallAlpha();
        if (isGone(mBinding.contextWall)) {
            mBinding.contextWall.setBackgroundColor(0xFF000000);
            mBinding.contextWall.setVisibility(View.VISIBLE);
        }
        ImgUtil.load(this, wall, new CustomTarget<>() {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                if (!Objects.equals(mContextWallUrl, wall)) return;
                resetContextWallAlpha();
                mBinding.contextWall.setBackgroundColor(0x00000000);
                mBinding.contextWall.setImageDrawable(resource);
                mBinding.contextWall.setVisibility(View.VISIBLE);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                if (!Objects.equals(mContextWallUrl, wall)) return;
                mContextWallUrl = "";
                hideContextWall();
            }
        });
    }

    private void resetContextWallAlpha() {
        mBinding.contextWall.animate().cancel();
        mBinding.contextWall.setAlpha(1f);
    }

    private void hideContextWall() {
        resetContextWallAlpha();
        mBinding.contextWall.setImageDrawable(null);
        mBinding.contextWall.setBackgroundColor(0x00000000);
        mBinding.contextWall.setVisibility(View.GONE);
    }

    private void setPartAdapter() {
        mPartAdapter.clear();
        mBinding.part.setVisibility(View.GONE);
        updateFocus();
    }

    private void checkFlag(Vod item) {
        boolean empty = item.getFlags().isEmpty();
        mBinding.flag.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            startFlow();
        } else {
            onItemClick(mHistory.getFlag());
            if (mHistory.isRevSort()) reverseEpisode(true);
        }
    }

    private void checkHistory(Vod item) {
        mHistory = History.find(getHistoryKey());
        mHistory = mHistory == null ? createHistory(item) : mHistory;
        if (!TextUtils.isEmpty(getWallPic())) mHistory.setWallPic(getWallPic());
        if (!TextUtils.isEmpty(getMark())) mHistory.setVodRemarks(getMark());
        applyIntentPlaybackSelection(item);
        if (Setting.isIncognito() && mHistory.getKey().equals(getHistoryKey())) mHistory.delete();
        mBinding.control.action.opening.setText(mHistory.getOpening() <= 0 ? getString(R.string.play_op) : Util.timeMs(mHistory.getOpening()));
        mBinding.control.action.ending.setText(mHistory.getEnding() <= 0 ? getString(R.string.play_ed) : Util.timeMs(mHistory.getEnding()));
        mBinding.control.action.speed.setText(player().setSpeed(PlayerSetting.getDefaultSpeed()));
        mHistory.setSpeed(player().getSpeed());
        mHistory.setVodName(item.getName());
        PlaybackEventCollector.get().updateHistory(mHistory);
        setArtwork(getInitialArtwork(item));
        setScale(getScale());
        setPartAdapter();
    }

    private boolean shouldKeepPushArtwork() {
        return SiteApi.PUSH.equals(getKey()) && !TextUtils.isEmpty(getPic());
    }

    private String getInitialArtwork(Vod item) {
        return shouldKeepPushArtwork() ? getPic() : item.getPic();
    }

    private void applySearchArtwork(Vod item) {
        String pic = getSearchArtworkPic();
        if (!TextUtils.isEmpty(pic)) item.setPic(pic);
    }

    private String getSearchArtworkPic() {
        if (!TextUtils.isEmpty(getPic())) return getPic();
        if (mHistory != null && !TextUtils.isEmpty(mHistory.getVodPic())) return mHistory.getVodPic();
        return "";
    }

    private void updateEpisodeFallbackStillUrl() {
        String url = getEpisodeFallbackStillUrl();
        if (mEpisodeAdapter != null) mEpisodeAdapter.setFallbackStillUrl(url);
        if (mEpisodeGridAdapter != null) mEpisodeGridAdapter.setFallbackStillUrl(url);
    }

    private String getEpisodeFallbackStillUrl() {
        if (!TextUtils.isEmpty(getPic())) return getPic();
        if (!TextUtils.isEmpty(getTmdbVodPic())) return getTmdbVodPic();
        if (mVod != null && !TextUtils.isEmpty(mVod.getPic())) return mVod.getPic();
        return mHistory == null ? "" : mHistory.getVodPic();
    }

    private boolean hasInitialPreview() {
        return !getName().isEmpty() || !getPic().isEmpty() || !getWallPic().isEmpty();
    }

    private void showInitialPreview() {
        mBinding.progressLayout.showContent();
        mBinding.name.setText(getName());
        if (!getContent().isEmpty()) mBinding.content.setTag(getContent());
        if (!getPic().isEmpty()) setArtwork(getPic());
        else if (!getWallPic().isEmpty()) setContextWall(getWallPic());
        mBinding.video.requestFocus();
    }

    private History createHistory(Vod item) {
        History history = new History();
        history.setKey(getHistoryKey());
        history.setCid(VodConfig.getCid());
        history.setVodName(item.getName());
        history.setVodPic(getInitialArtwork(item));
        history.setWallPic(getWallPic());
        history.findEpisode(item.getFlags());
        return history;
    }

    private void applyIntentPlaybackSelection(Vod item) {
        String playFlag = getIntentPlaybackFlag();
        String playName = getIntentPlaybackEpisodeName();
        String playUrl = getIntentPlaybackEpisodeUrl();
        if (TextUtils.isEmpty(playFlag) && TextUtils.isEmpty(playName) && TextUtils.isEmpty(playUrl)) return;
        Flag flag = findIntentPlaybackFlag(item.getFlags(), playFlag, playUrl);
        if (flag == null) return;
        Episode episode = findIntentPlaybackEpisode(flag, playName, playUrl);
        boolean sameFlag = TextUtils.equals(mHistory.getVodFlag(), flag.getFlag());
        boolean sameEpisode = episode != null && episode.matches(mHistory.getEpisode());
        if (!sameFlag || (episode != null && !sameEpisode)) {
            mHistory.setPosition(C.TIME_UNSET);
            mHistory.setDuration(C.TIME_UNSET);
        }
        mHistory.setVodFlag(flag.getFlag());
        if (episode == null) return;
        mHistory.setVodRemarks(episode.getName());
        mHistory.setEpisodeUrl(episode.getUrl());
    }

    private Flag findIntentPlaybackFlag(List<Flag> flags, String playFlag, String playUrl) {
        if (flags == null || flags.isEmpty()) return null;
        for (Flag flag : flags) if (!TextUtils.isEmpty(playFlag) && TextUtils.equals(playFlag, flag.getFlag())) return flag;
        if (!TextUtils.isEmpty(playUrl)) {
            for (Flag flag : flags) for (Episode episode : flag.getEpisodes()) if (TextUtils.equals(playUrl, episode.getUrl())) return flag;
        }
        return null;
    }

    private Episode findIntentPlaybackEpisode(Flag flag, String playName, String playUrl) {
        if (flag == null || flag.getEpisodes().isEmpty()) return null;
        if (!TextUtils.isEmpty(playUrl)) {
            for (Episode episode : flag.getEpisodes()) if (TextUtils.equals(playUrl, episode.getUrl())) return episode;
        }
        return TextUtils.isEmpty(playName) ? null : flag.find(playName, true);
    }

    private void saveHistory() {
        saveHistory(false);
    }

    private void saveHistory(boolean exit) {
        android.util.Log.d("VideoActivity", "saveHistory: exit=" + exit + " mHistory=" + (mHistory != null) +
            " canSave=" + (mHistory != null ? mHistory.canSave() : "null") +
            " incognito=" + Setting.isIncognito());
        if (mHistory == null || Setting.isIncognito()) return;
        if (exit && isOwner()) {
            updatePlaybackHistoryPosition();
            mHistory.setCreateTime(System.currentTimeMillis());
        }
        if (exit && service() != null) PlaybackEventCollector.get().onStop(player());
        if (!mHistory.canSave()) return;
        History history = mHistory.copy();
        Task.execute(() -> {
            history.merge().save();
            android.util.Log.d("VideoActivity", "saveHistory: saved! key=" + history.getKey());
            if (exit) RefreshEvent.history();
        });
    }

    private void syncHistory() {
        if (mHistory == null || Setting.isIncognito()) return;
        History history = mHistory.copy();
        Task.execute(history::save);
    }

    private void updateHistory(Episode item) {
        boolean sameEpisode = item.matches(mHistory.getEpisode());
        boolean sameFlag = TextUtils.equals(mHistory.getVodFlag(), getFlag().getFlag());
        if (!sameEpisode || !sameFlag) mIntroSkipPlayback.reset();
        if ((!sameEpisode || !sameFlag) && service() != null) {
            updatePlaybackHistoryPosition();
            PlaybackEventCollector.get().onStop(player());
        }
        mHistory.setPosition(sameEpisode ? mHistory.getPosition() : C.TIME_UNSET);
        if (!sameEpisode) mHistory.setDuration(C.TIME_UNSET);
        mHistory.setVodFlag(getFlag().getFlag());
        mHistory.setVodRemarks(item.getName());
        mHistory.setEpisodeUrl(item.getUrl());
        PlaybackEventCollector.get().updateHistory(mHistory);
    }

    private void checkKeepImg() {
        mBinding.keep.setCompoundDrawablesWithIntrinsicBounds(Keep.find(getHistoryKey()) == null ? R.drawable.ic_detail_keep_off : R.drawable.ic_detail_keep_on, 0, 0, 0);
    }

    private void createKeep() {
        Keep keep = new Keep();
        keep.setKey(getHistoryKey());
        keep.setCid(VodConfig.getCid());
        keep.setVodPic(mHistory.getVodPic());
        keep.setVodName(mHistory.getVodName());
        keep.setSiteName(getSite().getName());
        keep.setCreateTime(System.currentTimeMillis());
        keep.save();
    }

    private void updateKeep() {
        Keep keep = Keep.find(getHistoryKey());
        if (keep != null) {
            keep.setVodName(mHistory.getVodName());
            keep.setVodPic(mHistory.getVodPic());
            keep.save();
        }
    }

    private void updateVod(Vod item) {
        mVod = item;
        boolean id = !item.getId().isEmpty();
        boolean pic = !item.getPic().isEmpty();
        boolean name = !item.getName().isEmpty();
        if (id) getIntent().putExtra("id", item.getId());
        if (id) mHistory.replace(getHistoryKey());
        if (name) mHistory.setVodName(item.getName());
        if (name) mBinding.name.setText(item.getName());
        updateFlag(getFlag(), item.getFlags());
        mBinding.widget.title.setText(getPlaybackControlTitle());
        if (pic) setArtwork(item.getPic());
        if (pic || name) setMetadata();
        if (pic || name) syncHistory();
        if (pic || name) updateKeep();
        if (id) updateNavigationKey();
        if (name) setPartAdapter();
        PlaybackEventCollector.get().updateHistory(mHistory);
        setText(item);
        if (shouldUseTmdbLayout()) suppressTmdbNativeTextFields();
    }

    private boolean shouldUseTmdbLayout() {
        return mTmdbUIAdapter != null && mTmdbUIAdapter.isReady();
    }

    private void suppressTmdbNativeTextFields() {
        mBinding.remark.setVisibility(View.GONE);
        mBinding.actor.setVisibility(View.GONE);
        mBinding.director.setVisibility(View.GONE);
    }

    private void updateFlag(Flag activated, List<Flag> items) {
        items.forEach(item -> mFlagAdapter.getItems().stream()
                .filter(item::equals).findFirst().ifPresentOrElse(target -> {
                    target.mergeEpisodes(item.getEpisodes(), mHistory.isRevSort());
                    if (target.equals(activated)) {
                        boolean useTmdbCard = EpisodeDisplayPolicy.shouldUseTmdbEpisodeCards(isTmdbSourceEnabled(), item.getEpisodes());

                        if (useTmdbCard && mBinding.episodeLoadingIndicator.getVisibility() == View.VISIBLE) {
                            // TMDB数据加载完成，执行淡入动画
                            setEpisodeAdapter(target.getEpisodes());
                            mBinding.episodeLoadingIndicator.setVisibility(View.GONE);
                            setEpisodeContentVisible(true);
                            View episodeView = getActiveEpisodeContentView();
                            episodeView.setAlpha(0f);
                            episodeView.animate()
                                    .alpha(1f)
                                    .setDuration(300)
                                    .start();
                        } else {
                            // 普通更新或初始加载
                            setEpisodeAdapter(target.getEpisodes());
                        }
                    }
                }, () -> mFlagAdapter.add(item)));
    }

    private final PlaybackService.NavigationCallback mNavigationCallback = new PlaybackService.NavigationCallback() {
        @Override
        public void onNext() {
            checkNext();
        }

        @Override
        public void onPrev() {
            checkPrev();
        }

        @Override
        public void onStop() {
            finish();
        }

        @Override
        public void onReplay() {
            VideoActivity.this.onReplay();
        }
    };

    @Override
    protected String getPlaybackKey() {
        return getHistoryKey();
    }

    @Override
    protected void onPrepare() {
        android.util.Log.d("VideoActivity", "onPrepare: setting Clock callback");
        setPlayerKernel();
        setDecode();
        setLut();
        setPosition();
        mClock.setCallback(this);
        requestIntroSkipPlan();
    }

    @Override
    protected void onPlayerRebuilt() {
        setPlayerKernel();
        setDecode();
    }

    @Override
    protected void onTracksChanged() {
        setTrackVisible();
        mClock.setCallback(this);
    }

    @Override
    protected void onTitlesChanged() {
        setTitleVisible();
    }

    @Override
    protected void onError(String msg) {
        recordPlayHealth(false, msg);
        subtitlePlaybackSession.stop(this);
        Track.delete(player().getKey());
        mClock.setCallback(null);
        player().resetTrack();
        player().reset();
        player().stop();
        showError(msg);
        startFlow();
    }

    @Override
    protected void onReclaim() {
        if (!canApplyPlayerResult()) return;
        Result result = mViewModel.getPlayer().getValue();
        if (result != null) setPlayer(result);
    }

    @Override
    protected void onStateChanged(int state) {
        switch (state) {
            case Player.STATE_BUFFERING:
                showProgress();
                break;
            case Player.STATE_READY:
                recordPlayHealth(true, "");
                hideProgress();
                player().reset();
                applyShortDramaMode();
                requestIntroSkipPlan();
                applyAutoIntroSkip();
                break;
            case Player.STATE_ENDED:
                checkEnded(true);
                break;
        }
    }

    @Override
    protected void onPlayingChanged(boolean isPlaying) {
        if (isPlaying) {
            hideCenter();
        } else if (isPaused()) {
            if (isFullscreen()) showInfo();
            else hideInfo();
        }
    }

    @Override
    protected void onSizeChanged(VideoSize size) {
        applyResizeMode(getScale());
        mBinding.widget.size.setText(player().getSizeText());
    }

    @Override
    protected void onSurfaceAttached() {
        applyResizeMode(getScale());
    }

    @Override
    public void onSubtitleClick() {
        SubtitleDialog.create().view(mBinding.exo.getSubtitleView()).search(() -> SubtitleManualSearchDialog.show(this, subtitlePlaybackSession, this)).show(this);
        App.post(this::hideControl, 100);
    }

    private void showSubtitleSearch() {
        SubtitleManualSearchDialog.show(this, subtitlePlaybackSession, this);
    }

    @Override
    public void onTimeChanged(long time) {
        android.util.Log.d("VideoActivity", "onTimeChanged: isOwner=" + isOwner() + " mHistory=" + (mHistory != null));
        if (!isOwner()) return;
        long position, duration;
        mHistory.setCreateTime(time);
        updatePlaybackHistoryPosition();
        position = mHistory.getPosition();
        duration = mHistory.getDuration();
        android.util.Log.d("VideoActivity", "onTimeChanged: position=" + position + " duration=" + duration + " canSave=" + mHistory.canSave());
        PlaybackEventCollector.get().onProgress(mHistory, player());
        if (mHistory.canSave() && mHistory.canSync()) syncHistory();
        if (applyAutoIntroSkip()) return;
        if (mHistory.getEnding() > 0 && duration > 0 && mHistory.getEnding() + position >= duration) {
            checkEnded(false);
        }
    }

    private void updatePlaybackHistoryPosition() {
        if (mHistory == null) return;
        long position = player().getPosition();
        long duration = player().getDuration();
        if (position <= 0 || duration <= 0) return;
        mHistory.setPosition(position);
        mHistory.setDuration(duration);
        PlaybackEventCollector.get().updateHistory(mHistory);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (isRedirect()) return;
        if (event.getType() == RefreshEvent.Type.DETAIL) getDetail();
        else if (event.getType() == RefreshEvent.Type.PLAYER) onRefresh();
        else if (event.getType() == RefreshEvent.Type.VOD) {
            if (!isCurrentVodEvent(event.getVod())) {
                SpiderDebug.log("tmdb-tv", "drop stale vod event current=%s/%s event=%s/%s", getKey(), getId(), event.getVod() == null ? "" : event.getVod().getSiteKey(), event.getVod() == null ? "" : event.getVod().getId());
                return;
            }
            updateVod(event.getVod());
            // 绑定 TMDB 数据到 UI
            bindTmdbData();
            // 未匹配到 TMDB 数据：直接揭开原版 UI
            if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded()) {
                revealTmdbDetail();
                loadNativePersonalRecommendations(event.getVod());
                if (shouldShowAutoTmdbMatchDialog(event.getVod())) showManualTmdbMatchDialog();
            }
            // TMDB 加载已结束：若仍卡在剧集加载指示器（电影无集数、未匹配、获取失败等），揭开原版选集列表
            finishEpisodeLoading();
        }
        else if (event.getType() == RefreshEvent.Type.SUBTITLE) player().setSub(Sub.from(event.getPath()));
        else if (event.getType() == RefreshEvent.Type.DANMAKU) player().setDanmaku(Danmaku.from(event.getPath()));
        else if (event.getType() == RefreshEvent.Type.HISTORY) refreshPersonalRecommendationsForHistory();
    }

    private boolean isCurrentVodEvent(Vod item) {
        if (item == null) return false;
        String id = item.getId();
        String siteKey = item.getSiteKey();
        if (!TextUtils.isEmpty(id) && !TextUtils.equals(id, getId())) return false;
        return TextUtils.isEmpty(siteKey) || TextUtils.equals(siteKey, getKey());
    }

    private void loadNativePersonalRecommendations(Vod item) {
        int generation = ++mPersonalRecommendationGeneration;
        if (!Setting.isPersonalRecommendation()) {
            clearNativePersonalRecommendations();
            return;
        }
        clearNativePersonalRecommendations();
        Task.execute(() -> {
            PersonalRecommendationService.RecommendationPages recommendations = PersonalRecommendationService.RecommendationPages.empty();
            try {
                recommendations = new PersonalRecommendationService().loadPage(item, null, null, 0, PersonalRecommendationService.DEFAULT_PAGE_SIZE);
            } catch (Throwable e) {
                SpiderDebug.log("personal-rec", "tv native core failed error=%s", e.getMessage());
            }
            PersonalRecommendationService.RecommendationPages loaded = recommendations;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != mPersonalRecommendationGeneration) return;
                bindNativePersonalRecommendations(loaded);
            });
        });
        Task.execute(() -> {
            PersonalRecommendationService.RecommendationPage page;
            try {
                page = new PersonalRecommendationService().loadAiPage(item, null, PersonalRecommendationService.DEFAULT_PAGE_SIZE);
            } catch (Throwable e) {
                SpiderDebug.log("personal-rec", "tv native ai failed error=%s", e.getMessage());
                page = PersonalRecommendationService.RecommendationPage.empty("");
            }
            PersonalRecommendationService.RecommendationPage loaded = page;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != mPersonalRecommendationGeneration) return;
                bindNativePersonalAiRecommendation(loaded);
            });
        });
    }

    private void bindNativePersonalRecommendations(PersonalRecommendationService.RecommendationPages recommendations) {
        mNativePersonalTmdbPage = recommendations == null ? PersonalRecommendationService.RecommendationPage.empty("") : recommendations.getTmdb();
        mNativePersonalDoubanPage = recommendations == null ? PersonalRecommendationService.RecommendationPage.empty("") : recommendations.getDouban();
        boolean hasTmdb = bindRecommendationGrid(mBinding.tmdbPersonalTmdbRecommendations, mBinding.tmdbPersonalTmdbRecommendationsLabel, mNativePersonalTmdbPage.getItems(), RecommendationRow.PERSONAL_TMDB);
        boolean hasDouban = bindRecommendationGrid(mBinding.tmdbPersonalDoubanRecommendations, mBinding.tmdbPersonalDoubanRecommendationsLabel, mNativePersonalDoubanPage.getItems(), RecommendationRow.PERSONAL_DOUBAN);
        boolean hasAi = mNativePersonalAiPage != null && !mNativePersonalAiPage.getItems().isEmpty();
        setNativePersonalRecommendationFocus(hasTmdb, hasDouban, hasAi);
    }

    private void bindNativePersonalAiRecommendation(PersonalRecommendationService.RecommendationPage page) {
        mNativePersonalAiPage = page == null ? PersonalRecommendationService.RecommendationPage.empty("") : page;
        boolean hasTmdb = mNativePersonalTmdbPage != null && !mNativePersonalTmdbPage.getItems().isEmpty();
        boolean hasDouban = mNativePersonalDoubanPage != null && !mNativePersonalDoubanPage.getItems().isEmpty();
        boolean hasAi = bindRecommendationGrid(mBinding.tmdbPersonalAiRecommendations, mBinding.tmdbPersonalAiRecommendationsLabel, mNativePersonalAiPage.getItems(), RecommendationRow.PERSONAL_AI);
        setNativePersonalRecommendationFocus(hasTmdb, hasDouban, hasAi);
    }

    private boolean bindRecommendationGrid(HorizontalGridView grid, View label, List<TmdbItem> items) {
        return bindRecommendationGrid(grid, label, items, RecommendationRow.NONE);
    }

    private boolean bindRecommendationGrid(HorizontalGridView grid, View label, List<TmdbItem> items, RecommendationRow row) {
        if (items == null || items.isEmpty()) {
            grid.setVisibility(View.GONE);
            label.setVisibility(View.GONE);
            rememberRecommendationAdapter(row, null);
            if (row == RecommendationRow.PERSONAL_AI) showAiRecommendationReason(null, false);
            return false;
        }
        ArrayObjectAdapter adapter = new ArrayObjectAdapter(
            row == RecommendationRow.PERSONAL_AI
                    ? new com.fongmi.android.tv.ui.presenter.TmdbRecommendationPresenter(this::onTmdbRecommendationClick, this::onAiRecommendationLongClick, this::onAiRecommendationFocus)
                    : new com.fongmi.android.tv.ui.presenter.TmdbRecommendationPresenter(this::onTmdbRecommendationClick)
        );
        adapter.addAll(0, items);
        grid.setAdapter(new ItemBridgeAdapter(adapter));
        rememberRecommendationAdapter(row, adapter);
        grid.setVisibility(View.VISIBLE);
        label.setVisibility(View.VISIBLE);
        return true;
    }

    private void hideNativePersonalRecommendations() {
        mPersonalRecommendationGeneration++;
        clearNativePersonalRecommendations();
    }

    private void clearNativePersonalRecommendations() {
        mNativePersonalTmdbPage = null;
        mNativePersonalDoubanPage = null;
        mNativePersonalAiPage = null;
        mNativePersonalTmdbLoading = false;
        mNativePersonalDoubanLoading = false;
        mPersonalTmdbObjectAdapter = null;
        mPersonalDoubanObjectAdapter = null;
        mPersonalAiObjectAdapter = null;
        mBinding.tmdbPersonalTmdbRecommendations.setVisibility(View.GONE);
        mBinding.tmdbPersonalTmdbRecommendationsLabel.setVisibility(View.GONE);
        mBinding.tmdbPersonalDoubanRecommendations.setVisibility(View.GONE);
        mBinding.tmdbPersonalDoubanRecommendationsLabel.setVisibility(View.GONE);
        mBinding.tmdbPersonalAiRecommendations.setVisibility(View.GONE);
        mBinding.tmdbPersonalAiRecommendationsLabel.setVisibility(View.GONE);
        showAiRecommendationReason(null, false);
        setNativePersonalRecommendationFocus(false, false, false);
    }

    private boolean onAiRecommendationLongClick(TmdbItem item) {
        com.fongmi.android.tv.ui.dialog.AiRecommendationInfoDialog.show(this, item);
        return true;
    }

    private void onAiRecommendationFocus(TmdbItem item, boolean focused) {
        showAiRecommendationReason(item, focused);
    }

    private void showAiRecommendationReason(TmdbItem item, boolean focused) {
        if (!focused || item == null || TextUtils.isEmpty(item.getOverview())) {
            mBinding.tmdbPersonalAiReason.setVisibility(View.GONE);
            mBinding.tmdbPersonalAiReason.setText("");
            return;
        }
        mBinding.tmdbPersonalAiReason.setText(getString(R.string.ai_recommendation_reason_preview, item.getOverview()));
        mBinding.tmdbPersonalAiReason.setVisibility(View.VISIBLE);
    }

    private void setNativePersonalRecommendationFocus(boolean hasTmdb, boolean hasDouban, boolean hasAi) {
        int next = hasTmdb ? R.id.tmdbPersonalTmdbRecommendations : hasDouban ? R.id.tmdbPersonalDoubanRecommendations : hasAi ? R.id.tmdbPersonalAiRecommendations : R.id.flag;
        setDetailButtonsNextFocus(next);
        mBinding.tmdbPersonalTmdbRecommendations.setNextFocusDownId(hasDouban ? R.id.tmdbPersonalDoubanRecommendations : hasAi ? R.id.tmdbPersonalAiRecommendations : R.id.flag);
        mBinding.tmdbPersonalDoubanRecommendations.setNextFocusDownId(hasAi ? R.id.tmdbPersonalAiRecommendations : R.id.flag);
        mBinding.tmdbPersonalAiRecommendations.setNextFocusDownId(R.id.flag);
    }

    private void attachRecommendationLazyLoader(HorizontalGridView grid, RecommendationRow row) {
        grid.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                RecyclerView.Adapter<?> adapter = parent.getAdapter();
                if (adapter == null || position < 0 || adapter.getItemCount() - position > 4) return;
                loadMoreRecommendationRow(row);
            }
        });
    }

    private void loadMoreRecommendationRow(RecommendationRow row) {
        if (row == RecommendationRow.RECOMMENDATIONS) {
            if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded() || mTmdbRecommendationsLoading || !mTmdbUIAdapter.hasMoreRecommendations()) return;
            mTmdbRecommendationsLoading = true;
            mTmdbUIAdapter.loadMoreRecommendations(changed -> {
                mTmdbRecommendationsLoading = false;
                if (changed) appendLeanbackItems(mTmdbRecommendationsObjectAdapter, mTmdbUIAdapter.getRecommendations());
            });
            return;
        }
        if (mTmdbUIAdapter != null && mTmdbUIAdapter.isLoaded()) {
            loadMoreTmdbPersonalRecommendationRow(row);
        } else {
            loadMoreNativePersonalRecommendations(row == RecommendationRow.PERSONAL_TMDB);
        }
    }

    private void loadMoreTmdbPersonalRecommendationRow(RecommendationRow row) {
        if (row == RecommendationRow.PERSONAL_TMDB) {
            if (mPersonalTmdbLoading || !mTmdbUIAdapter.hasMorePersonalTmdbRecommendations()) return;
            mPersonalTmdbLoading = true;
            mTmdbUIAdapter.loadMorePersonalTmdbRecommendations(changed -> {
                mPersonalTmdbLoading = false;
                if (changed) appendLeanbackItems(mPersonalTmdbObjectAdapter, mTmdbUIAdapter.getPersonalTmdbRecommendations());
            });
        } else if (row == RecommendationRow.PERSONAL_DOUBAN) {
            if (mPersonalDoubanLoading || !mTmdbUIAdapter.hasMorePersonalDoubanRecommendations()) return;
            mPersonalDoubanLoading = true;
            mTmdbUIAdapter.loadMorePersonalDoubanRecommendations(changed -> {
                mPersonalDoubanLoading = false;
                if (changed) appendLeanbackItems(mPersonalDoubanObjectAdapter, mTmdbUIAdapter.getPersonalDoubanRecommendations());
            });
        } else if (row == RecommendationRow.PERSONAL_AI) {
            if (mPersonalAiLoading || !mTmdbUIAdapter.hasMorePersonalAiRecommendations()) return;
            mPersonalAiLoading = true;
            mTmdbUIAdapter.loadMorePersonalAiRecommendations(changed -> {
                mPersonalAiLoading = false;
                if (changed) appendLeanbackItems(mPersonalAiObjectAdapter, mTmdbUIAdapter.getPersonalAiRecommendations());
            });
        }
    }

    private void loadMoreNativePersonalRecommendations(boolean tmdb) {
        PersonalRecommendationService.RecommendationPage page = tmdb ? mNativePersonalTmdbPage : mNativePersonalDoubanPage;
        if (page == null || !page.hasMore() || (tmdb ? mNativePersonalTmdbLoading : mNativePersonalDoubanLoading) || mVod == null) return;
        int generation = mPersonalRecommendationGeneration;
        if (tmdb) mNativePersonalTmdbLoading = true;
        else mNativePersonalDoubanLoading = true;
        Task.execute(() -> {
            PersonalRecommendationService.RecommendationPage nextPage;
            try {
                nextPage = tmdb
                        ? new PersonalRecommendationService().loadTmdbPage(mVod, null, null, page.getNextOffset(), PersonalRecommendationService.DEFAULT_PAGE_SIZE)
                        : new PersonalRecommendationService().loadDoubanPage(mVod, page.getNextOffset(), PersonalRecommendationService.DEFAULT_PAGE_SIZE);
            } catch (Throwable e) {
                SpiderDebug.log("personal-rec", "tv native load more failed tmdb=%s error=%s", tmdb, e.getMessage());
                nextPage = page;
            }
            PersonalRecommendationService.RecommendationPage loadedPage = nextPage;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != mPersonalRecommendationGeneration) return;
                if (tmdb) {
                    mNativePersonalTmdbLoading = false;
                    mNativePersonalTmdbPage = loadedPage;
                    appendLeanbackItems(mPersonalTmdbObjectAdapter, loadedPage.getItems());
                } else {
                    mNativePersonalDoubanLoading = false;
                    mNativePersonalDoubanPage = loadedPage;
                    appendLeanbackItems(mPersonalDoubanObjectAdapter, loadedPage.getItems());
                }
            });
        });
    }

    private void appendLeanbackItems(ArrayObjectAdapter adapter, List<TmdbItem> items) {
        if (adapter == null || items == null || items.size() <= adapter.size()) return;
        adapter.addAll(adapter.size(), new ArrayList<>(items.subList(adapter.size(), items.size())));
    }

    private void refreshPersonalRecommendationsForHistory() {
        if (!Setting.isPersonalRecommendation() || mVod == null || mTmdbDetailLoading) return;
        if (mTmdbUIAdapter != null && mTmdbUIAdapter.isLoaded()) {
            mTmdbUIAdapter.refreshPersonalRecommendations(changed -> {
                if (!changed) return;
                bindRecommendationGrid(mBinding.tmdbPersonalTmdbRecommendations, mBinding.tmdbPersonalTmdbRecommendationsLabel, mTmdbUIAdapter.getPersonalTmdbRecommendations(), RecommendationRow.PERSONAL_TMDB);
                bindRecommendationGrid(mBinding.tmdbPersonalDoubanRecommendations, mBinding.tmdbPersonalDoubanRecommendationsLabel, mTmdbUIAdapter.getPersonalDoubanRecommendations(), RecommendationRow.PERSONAL_DOUBAN);
            });
        } else {
            loadNativePersonalRecommendations(mVod);
        }
    }

    private void refreshTmdbPersonalAiRecommendations() {
        if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded()) return;
        boolean hasAi = bindRecommendationGrid(mBinding.tmdbPersonalAiRecommendations, mBinding.tmdbPersonalAiRecommendationsLabel, mTmdbUIAdapter.getPersonalAiRecommendations(), RecommendationRow.PERSONAL_AI);
        if (mBinding.tmdbPersonalDoubanRecommendations.getVisibility() == View.VISIBLE) {
            mBinding.tmdbPersonalDoubanRecommendations.setNextFocusDownId(hasAi ? R.id.tmdbPersonalAiRecommendations : R.id.flag);
        } else if (mBinding.tmdbPersonalTmdbRecommendations.getVisibility() == View.VISIBLE) {
            mBinding.tmdbPersonalTmdbRecommendations.setNextFocusDownId(hasAi ? R.id.tmdbPersonalAiRecommendations : R.id.flag);
        }
        mBinding.tmdbPersonalAiRecommendations.setNextFocusDownId(R.id.flag);
    }

    private void rememberRecommendationAdapter(RecommendationRow row, ArrayObjectAdapter adapter) {
        if (row == RecommendationRow.RECOMMENDATIONS) mTmdbRecommendationsObjectAdapter = adapter;
        else if (row == RecommendationRow.PERSONAL_TMDB) mPersonalTmdbObjectAdapter = adapter;
        else if (row == RecommendationRow.PERSONAL_DOUBAN) mPersonalDoubanObjectAdapter = adapter;
        else if (row == RecommendationRow.PERSONAL_AI) mPersonalAiObjectAdapter = adapter;
    }

    private enum RecommendationRow {
        NONE, RECOMMENDATIONS, PERSONAL_TMDB, PERSONAL_DOUBAN, PERSONAL_AI
    }

    private void setDetailButtonsNextFocus(int next) {
        mBinding.content.setNextFocusDownId(next);
        mBinding.keep.setNextFocusDownId(next);
        mBinding.change1.setNextFocusDownId(next);
        mBinding.tmdbRematch.setNextFocusDownId(next);
    }

    private void bindTmdbData() {
        if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded()) return;

        boolean hasTmdbContent = false;
        View lastVisibleGrid = null;

        // 演员
        java.util.List<com.fongmi.android.tv.bean.TmdbPerson> cast = mTmdbUIAdapter.getCast();
        if (!cast.isEmpty()) {
            androidx.leanback.widget.ArrayObjectAdapter castAdapter = new androidx.leanback.widget.ArrayObjectAdapter(
                new com.fongmi.android.tv.ui.presenter.TmdbCastPresenter(this::onTmdbPersonClick)
            );
            castAdapter.addAll(0, cast);
            mBinding.tmdbCast.setAdapter(new androidx.leanback.widget.ItemBridgeAdapter(castAdapter));
            mBinding.tmdbCast.setVisibility(View.VISIBLE);
            View castLabel = mBinding.getRoot().findViewById(R.id.tmdbCastLabel);
            if (castLabel != null) castLabel.setVisibility(View.VISIBLE);
            if (lastVisibleGrid == null) setDetailButtonsNextFocus(R.id.tmdbCast);
            lastVisibleGrid = mBinding.tmdbCast;
            hasTmdbContent = true;
        } else {
            mBinding.tmdbCast.setVisibility(View.GONE);
            View castLabel = mBinding.getRoot().findViewById(R.id.tmdbCastLabel);
            if (castLabel != null) castLabel.setVisibility(View.GONE);
        }

        // 剧照
        java.util.List<String> photos = mTmdbUIAdapter.getPhotos();
        if (!photos.isEmpty()) {
            androidx.leanback.widget.ArrayObjectAdapter photosAdapter = new androidx.leanback.widget.ArrayObjectAdapter(
                new com.fongmi.android.tv.ui.presenter.TmdbPhotoPresenter(this::onTmdbPhotoClick)
            );
            photosAdapter.addAll(0, photos);
            mBinding.tmdbPhotos.setAdapter(new androidx.leanback.widget.ItemBridgeAdapter(photosAdapter));
            mBinding.tmdbPhotos.setVisibility(View.VISIBLE);
            View photosLabel = mBinding.getRoot().findViewById(R.id.tmdbPhotosLabel);
            if (photosLabel != null) photosLabel.setVisibility(View.VISIBLE);
            if (lastVisibleGrid == null) setDetailButtonsNextFocus(R.id.tmdbPhotos);

            // 动态设置上一个Grid的nextFocusDown
            if (lastVisibleGrid != null) {
                lastVisibleGrid.setNextFocusDownId(R.id.tmdbPhotos);
            }
            lastVisibleGrid = mBinding.tmdbPhotos;
            if (!hasTmdbContent) hasTmdbContent = true;
        } else {
            mBinding.tmdbPhotos.setVisibility(View.GONE);
            View photosLabel = mBinding.getRoot().findViewById(R.id.tmdbPhotosLabel);
            if (photosLabel != null) photosLabel.setVisibility(View.GONE);
        }

        // 主创团队
        java.util.List<com.fongmi.android.tv.bean.TmdbPerson> creators = mTmdbUIAdapter.getCreators();
        if (!creators.isEmpty()) {
            androidx.leanback.widget.ArrayObjectAdapter crewAdapter = new androidx.leanback.widget.ArrayObjectAdapter(
                new com.fongmi.android.tv.ui.presenter.TmdbCastPresenter(this::onTmdbPersonClick)
            );
            crewAdapter.addAll(0, creators);
            mBinding.tmdbCrew.setAdapter(new androidx.leanback.widget.ItemBridgeAdapter(crewAdapter));
            mBinding.tmdbCrew.setVisibility(View.VISIBLE);
            View crewLabel = mBinding.getRoot().findViewById(R.id.tmdbCrewLabel);
            if (crewLabel != null) crewLabel.setVisibility(View.VISIBLE);
            if (lastVisibleGrid == null) setDetailButtonsNextFocus(R.id.tmdbCrew);

            // 动态设置上一个Grid的nextFocusDown
            if (lastVisibleGrid != null) {
                lastVisibleGrid.setNextFocusDownId(R.id.tmdbCrew);
            }
            lastVisibleGrid = mBinding.tmdbCrew;
            if (!hasTmdbContent) hasTmdbContent = true;
        } else {
            mBinding.tmdbCrew.setVisibility(View.GONE);
            View crewLabel = mBinding.getRoot().findViewById(R.id.tmdbCrewLabel);
            if (crewLabel != null) crewLabel.setVisibility(View.GONE);
        }

        // 猜你喜欢
        java.util.List<com.fongmi.android.tv.bean.TmdbItem> recommendations = mTmdbUIAdapter.getRecommendations();
        if (bindRecommendationGrid(mBinding.tmdbRecommendations, mBinding.tmdbRecommendationsLabel, recommendations, RecommendationRow.RECOMMENDATIONS)) {
            if (lastVisibleGrid == null) setDetailButtonsNextFocus(R.id.tmdbRecommendations);
            if (lastVisibleGrid != null) lastVisibleGrid.setNextFocusDownId(R.id.tmdbRecommendations);
            lastVisibleGrid = mBinding.tmdbRecommendations;
            if (!hasTmdbContent) hasTmdbContent = true;
        }

        // 个性推荐 · TMDB
        java.util.List<com.fongmi.android.tv.bean.TmdbItem> personalTmdbRecommendations = mTmdbUIAdapter.getPersonalTmdbRecommendations();
        if (bindRecommendationGrid(mBinding.tmdbPersonalTmdbRecommendations, mBinding.tmdbPersonalTmdbRecommendationsLabel, personalTmdbRecommendations, RecommendationRow.PERSONAL_TMDB)) {
            if (lastVisibleGrid == null) setDetailButtonsNextFocus(R.id.tmdbPersonalTmdbRecommendations);
            if (lastVisibleGrid != null) lastVisibleGrid.setNextFocusDownId(R.id.tmdbPersonalTmdbRecommendations);
            lastVisibleGrid = mBinding.tmdbPersonalTmdbRecommendations;
            if (!hasTmdbContent) hasTmdbContent = true;
        }

        // 个性推荐 · 豆瓣
        java.util.List<com.fongmi.android.tv.bean.TmdbItem> personalDoubanRecommendations = mTmdbUIAdapter.getPersonalDoubanRecommendations();
        if (bindRecommendationGrid(mBinding.tmdbPersonalDoubanRecommendations, mBinding.tmdbPersonalDoubanRecommendationsLabel, personalDoubanRecommendations, RecommendationRow.PERSONAL_DOUBAN)) {
            if (lastVisibleGrid == null) setDetailButtonsNextFocus(R.id.tmdbPersonalDoubanRecommendations);
            if (lastVisibleGrid != null) lastVisibleGrid.setNextFocusDownId(R.id.tmdbPersonalDoubanRecommendations);
            lastVisibleGrid = mBinding.tmdbPersonalDoubanRecommendations;
            if (!hasTmdbContent) hasTmdbContent = true;
        }

        // 个性推荐 · 智能
        java.util.List<com.fongmi.android.tv.bean.TmdbItem> personalAiRecommendations = mTmdbUIAdapter.getPersonalAiRecommendations();
        if (bindRecommendationGrid(mBinding.tmdbPersonalAiRecommendations, mBinding.tmdbPersonalAiRecommendationsLabel, personalAiRecommendations, RecommendationRow.PERSONAL_AI)) {
            if (lastVisibleGrid == null) setDetailButtonsNextFocus(R.id.tmdbPersonalAiRecommendations);
            if (lastVisibleGrid != null) lastVisibleGrid.setNextFocusDownId(R.id.tmdbPersonalAiRecommendations);
            lastVisibleGrid = mBinding.tmdbPersonalAiRecommendations;
            if (!hasTmdbContent) hasTmdbContent = true;
        }

        // 设置最后一个Grid的nextFocusDown到flag
        if (lastVisibleGrid != null) {
            lastVisibleGrid.setNextFocusDownId(R.id.flag);
        }

        // 如果没有TMDB内容，确保按钮焦点指向flag
        if (!hasTmdbContent) {
            mBinding.content.setNextFocusDownId(R.id.flag);
            mBinding.keep.setNextFocusDownId(R.id.flag);
            mBinding.change1.setNextFocusDownId(R.id.flag);
        }

        SpiderDebug.log("tmdb-tv", "绑定完成: 演员=%d 剧照=%d 主创=%d 推荐=%d 个性TMDB=%d 个性豆瓣=%d 个性智能=%d", cast.size(), photos.size(), creators.size(), recommendations.size(), personalTmdbRecommendations.size(), personalDoubanRecommendations.size(), personalAiRecommendations.size());

        // TMDB / OMDB 多来源评分（TMDB / IMDb / 烂番茄 / Metacritic 等）
        bindTmdbOmdbRatings();

        // 设置背景幻灯片
        setupBackdropSlideshow(photos);

        // TMDB 数据全部绑定完成，揭开遮罩并应用 TMDB 字段
        finishTmdbDetail();
        requestIntroSkipPlan();
    }

    /**
     * 绑定多来源评分（TMDB / IMDb / 烂番茄 / Metacritic 等）。
     * TMDB 匹配成功时优先显示 TMDB 分，OMDB 可用时再追加 IMDb 等外部来源。
     */
    private void bindTmdbOmdbRatings() {
        View label = mBinding.getRoot().findViewById(R.id.tmdbOmdbRatingsLabel);
        ViewGroup container = mBinding.getRoot().findViewById(R.id.tmdbOmdbRatings);
        if (container == null || mTmdbUIAdapter == null) {
            hideTmdbRatingChips(label, container);
            return;
        }

        com.google.gson.JsonObject detail = mTmdbUIAdapter.getTmdbDetail();
        if (detail == null) {
            hideTmdbRatingChips(label, container);
            SpiderDebug.log("tmdb-omdb", "跳过：detail 为空");
            return;
        }

        java.util.List<String[]> baseChips = buildTmdbRatingChips();
        fetchTmdbDoubanRating();

        com.google.gson.JsonObject externalIds = detail.has("external_ids") && !detail.get("external_ids").isJsonNull()
                ? detail.getAsJsonObject("external_ids") : null;
        if (externalIds == null || !externalIds.has("imdb_id") || externalIds.get("imdb_id").isJsonNull()) {
            renderTmdbRatingChips(label, container, baseChips);
            SpiderDebug.log("tmdb-omdb", "跳过：无 imdb_id，detail keys=%s", detail.keySet());
            return;
        }
        String imdbId = externalIds.get("imdb_id").getAsString();
        if (TextUtils.isEmpty(imdbId)) {
            renderTmdbRatingChips(label, container, baseChips);
            return;
        }

        com.fongmi.android.tv.bean.TmdbConfig tmdbConfig = com.fongmi.android.tv.bean.TmdbConfig.objectFrom(Setting.getTmdbConfig());
        String omdbApiKey = tmdbConfig.getOmdbApiKey();
        if (TextUtils.isEmpty(omdbApiKey)) {
            renderTmdbRatingChips(label, container, baseChips);
            SpiderDebug.log("tmdb-omdb", "跳过：未配置 OMDB API Key");
            return;
        }

        String cacheKey = omdbRatingCacheKey(imdbId, omdbApiKey);
        java.util.List<String[]> cached = mTmdbOmdbRatingCache.get(cacheKey);
        if (cached != null) {
            container.setTag(cacheKey);
            renderTmdbRatingChips(label, container, mergeRatingChips(baseChips, cached));
            return;
        }
        if (mTmdbOmdbRatingLoading.contains(cacheKey)) {
            container.setTag(cacheKey);
            renderTmdbRatingChips(label, container, baseChips);
            SpiderDebug.log("tmdb-omdb", "跳过：请求进行中 imdbId=%s", imdbId);
            return;
        }

        container.setTag(cacheKey);
        renderTmdbRatingChips(label, container, baseChips);
        mTmdbOmdbRatingLoading.add(cacheKey);
        SpiderDebug.log("tmdb-omdb", "开始请求 imdbId=%s", imdbId);
        fetchTmdbOmdbRatings(imdbId, omdbApiKey, cacheKey, label, container);
    }

    private boolean shouldShowAutoTmdbMatchDialog(Vod item) {
        if (item == null || mTmdbAutoDialogShown) return false;
        if (!Setting.isTmdbMatchDialog() || getTmdbItem() != null) return false;
        if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isReady()) return false;
        mTmdbAutoDialogShown = true;
        return true;
    }

    private void showManualTmdbMatchDialog() {
        if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isReady()) {
            Notify.show(R.string.detail_tmdb_need_key);
            return;
        }
        if (!isTmdbSourceEnabled()) {
            Notify.show(R.string.detail_tmdb_site_disabled);
            return;
        }
        String query = getTmdbSearchQuery();
        if (TextUtils.isEmpty(query)) {
            Notify.show(R.string.detail_tmdb_empty);
            return;
        }
        Notify.show(R.string.detail_tmdb_searching);
        int generation = ++mTmdbDialogGeneration;
        Task.execute(() -> {
            try {
                List<TmdbItem> items = mTmdbUIAdapter.search(query, mVod);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != mTmdbDialogGeneration) return;
                    showTmdbMatchDialog(query, items);
                });
            } catch (Throwable e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != mTmdbDialogGeneration) return;
                    Notify.show(TextUtils.isEmpty(e.getMessage()) ? getString(R.string.detail_tmdb_empty) : e.getMessage());
                });
            }
        });
    }

    private void showTmdbMatchDialog(String query, List<TmdbItem> items) {
        TmdbSearchDialog.create(this)
                .title(getString(R.string.detail_tmdb_match_title))
                .query(query)
                .items(items)
                .listener(this::applyManualTmdb)
                .searchListener(this::searchTmdb)
                .show();
    }

    private void searchTmdb(String keyword, TmdbSearchDialog dialog) {
        if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isReady()) return;
        dialog.loading();
        int generation = ++mTmdbDialogGeneration;
        Task.execute(() -> {
            try {
                List<TmdbItem> items = mTmdbUIAdapter.search(keyword, mVod);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != mTmdbDialogGeneration) return;
                    dialog.updateItems(items);
                });
            } catch (Throwable e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != mTmdbDialogGeneration) return;
                    dialog.updateItems(new ArrayList<>());
                    Notify.show(TextUtils.isEmpty(e.getMessage()) ? getString(R.string.detail_tmdb_empty) : e.getMessage());
                });
            }
        });
    }

    private void applyManualTmdb(TmdbItem item) {
        if (mTmdbUIAdapter == null || mVod == null || item == null) return;
        mTmdbDialogGeneration++;
        showTmdbDetailLoading();
        mTmdbUIAdapter.load(item, mVod);
        Notify.show(R.string.detail_tmdb_match_saved);
    }

    private String getTmdbSearchQuery() {
        if (mTmdbUIAdapter != null && mTmdbUIAdapter.getTmdbItem() != null && !TextUtils.isEmpty(mTmdbUIAdapter.getTmdbItem().getTitle())) {
            return mTmdbUIAdapter.getTmdbItem().getTitle();
        }
        String name = mVod != null && !TextUtils.isEmpty(mVod.getName()) ? mVod.getName() : getName();
        return mTmdbUIAdapter == null ? name : mTmdbUIAdapter.cleanSearchQuery(name);
    }

    private void fetchTmdbOmdbRatings(String imdbId, String omdbApiKey, String cacheKey, View label, ViewGroup container) {
        Task.execute(() -> {
            try {
                String url = "https://www.omdbapi.com/?i=" + imdbId + "&apikey=" + omdbApiKey;
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                        .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                        .build();
                okhttp3.Request request = new okhttp3.Request.Builder().url(url).build();
                okhttp3.Response response = client.newCall(request).execute();
                if (!response.isSuccessful() || response.code() != 200 || response.body() == null) {
                    SpiderDebug.log("tmdb-omdb", "请求失败 code=%d", response.code());
                    return;
                }

                String json = response.body().string();
                com.google.gson.JsonObject jsonObj = new com.google.gson.JsonParser().parse(json).getAsJsonObject();
                if (jsonObj.has("Response") && "False".equals(jsonObj.get("Response").getAsString())) {
                    SpiderDebug.log("tmdb-omdb", "返回 Response=False");
                    return;
                }

                java.util.List<String[]> omdbChips = buildOmdbRatingChips(jsonObj);
                SpiderDebug.log("tmdb-omdb", "评分卡片数=%d", omdbChips.size());
                if (omdbChips.isEmpty()) return;
                mTmdbOmdbRatingCache.put(cacheKey, omdbChips);

                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    if (!cacheKey.equals(container.getTag())) return;
                    renderTmdbRatingChips(label, container, mergeRatingChips(buildTmdbRatingChips(), omdbChips));
                });
            } catch (Exception e) {
                SpiderDebug.log("tmdb-omdb", "获取失败: %s", e.getMessage());
            } finally {
                mTmdbOmdbRatingLoading.remove(cacheKey);
            }
        });
    }

    private void hideTmdbRatingChips(View label, ViewGroup container) {
        if (label != null) label.setVisibility(View.GONE);
        if (container == null) return;
        container.setVisibility(View.GONE);
        container.setTag(null);
        container.removeAllViews();
    }

    private String omdbRatingCacheKey(String imdbId, String omdbApiKey) {
        return imdbId + "|" + omdbApiKey;
    }

    private void renderTmdbRatingChips(View label, ViewGroup container, java.util.List<String[]> chips) {
        if (container == null) return;
        container.removeAllViews();
        if (chips == null || chips.isEmpty()) {
            if (label != null) label.setVisibility(View.GONE);
            container.setVisibility(View.GONE);
            return;
        }
        for (String[] chip : chips) {
            container.addView(createOmdbRatingChip(chip[0], chip[1], chip[2]));
        }
        if (label != null) label.setVisibility(View.VISIBLE);
        container.setVisibility(View.VISIBLE);
    }

    private java.util.List<String[]> buildTmdbRatingChips() {
        java.util.List<String[]> chips = new java.util.ArrayList<>();
        if (mTmdbUIAdapter == null) return chips;
        String tmdbRating = mTmdbUIAdapter.getRatingText();
        if (!TextUtils.isEmpty(tmdbRating)) {
            chips.add(new String[]{"TMDB", tmdbRating + "/10", "#21D07A"});
        }
        PersonalRecommendationService.DoubanRating doubanRating = mTmdbDoubanRatingCache.get(currentTmdbDoubanRatingKey());
        if (doubanRating != null && !doubanRating.isEmpty()) {
            String rating = formatRating(doubanRating.getRating());
            if (!TextUtils.isEmpty(rating)) chips.add(new String[]{"豆瓣", rating + "/10", "#00B51D"});
        }
        return chips;
    }

    private void fetchTmdbDoubanRating() {
        if (mTmdbUIAdapter == null || mTmdbUIAdapter.getTmdbItem() == null) return;
        String title = currentTmdbDoubanTitle();
        if (TextUtils.isEmpty(title)) return;
        String mediaType = currentTmdbMediaType();
        int year = parseTmdbYear(mTmdbUIAdapter.getYear());
        String cacheKey = tmdbDoubanRatingCacheKey(title, mediaType, year);
        if (mTmdbDoubanRatingCache.containsKey(cacheKey) || !mTmdbDoubanRatingLoading.add(cacheKey)) return;
        Task.execute(() -> {
            PersonalRecommendationService.DoubanRating rating = PersonalRecommendationService.DoubanRating.empty();
            try {
                rating = new PersonalRecommendationService().loadDoubanRating(title, mediaType, year);
            } catch (Throwable e) {
                SpiderDebug.log("tmdb-douban", "评分获取失败 title=%s error=%s", title, e.getMessage());
            }
            mTmdbDoubanRatingCache.put(cacheKey, rating);
            mTmdbDoubanRatingLoading.remove(cacheKey);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (!cacheKey.equals(currentTmdbDoubanRatingKey())) return;
                bindTmdbOmdbRatings();
            });
        });
    }

    private String currentTmdbDoubanRatingKey() {
        return tmdbDoubanRatingCacheKey(currentTmdbDoubanTitle(), currentTmdbMediaType(), parseTmdbYear(mTmdbUIAdapter == null ? "" : mTmdbUIAdapter.getYear()));
    }

    private String currentTmdbDoubanTitle() {
        TmdbItem item = mTmdbUIAdapter == null ? null : mTmdbUIAdapter.getTmdbItem();
        return item == null ? "" : item.getTitle();
    }

    private String currentTmdbMediaType() {
        TmdbItem item = mTmdbUIAdapter == null ? null : mTmdbUIAdapter.getTmdbItem();
        return item == null ? "" : item.getMediaType();
    }

    private String tmdbDoubanRatingCacheKey(String title, String mediaType, int year) {
        return nullToEmpty(mediaType) + "|" + nullToEmpty(title).toLowerCase(Locale.ROOT) + "|" + year;
    }

    private int parseTmdbYear(String year) {
        if (TextUtils.isEmpty(year)) return 0;
        try {
            return Integer.parseInt(year);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String formatRating(double rating) {
        return rating <= 0 ? "" : String.format(Locale.US, "%.1f", rating);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private java.util.List<String[]> mergeRatingChips(java.util.List<String[]> baseChips, java.util.List<String[]> extraChips) {
        java.util.List<String[]> chips = new java.util.ArrayList<>();
        if (baseChips != null) chips.addAll(baseChips);
        if (extraChips != null) chips.addAll(extraChips);
        return chips;
    }

    /**
     * 从 OMDB 响应组装评分卡片数据：每项为 {平台名, 评分文本, 颜色}。
     */
    private java.util.List<String[]> buildOmdbRatingChips(com.google.gson.JsonObject jsonObj) {
        java.util.List<String[]> chips = new java.util.ArrayList<>();

        String imdbRating = optOmdbString(jsonObj, "imdbRating");
        if (!TextUtils.isEmpty(imdbRating)) {
            String votes = optOmdbString(jsonObj, "imdbVotes");
            String text = buildImdbRatingText(imdbRating, votes);
            chips.add(new String[]{"IMDB", text, "#F5C518"});
        }

        if (jsonObj.has("Ratings") && jsonObj.get("Ratings").isJsonArray()) {
            for (com.google.gson.JsonElement el : jsonObj.getAsJsonArray("Ratings")) {
                if (!el.isJsonObject()) continue;
                com.google.gson.JsonObject rating = el.getAsJsonObject();
                String source = optOmdbString(rating, "Source");
                String value = optOmdbString(rating, "Value");
                if (TextUtils.isEmpty(source) || TextUtils.isEmpty(value)) continue;
                if ("Internet Movie Database".equals(source)) continue;
                if ("Rotten Tomatoes".equals(source)) chips.add(new String[]{"烂番茄", value, "#FA320A"});
                else if ("Metacritic".equals(source)) chips.add(new String[]{"Metacritic", value, "#FFCC33"});
                else chips.add(new String[]{source, value, "#21D07A"});
            }
        }

        String metascore = optOmdbString(jsonObj, "Metascore");
        boolean hasMetacritic = false;
        for (String[] chip : chips) if ("Metacritic".equals(chip[0])) hasMetacritic = true;
        if (!TextUtils.isEmpty(metascore) && !hasMetacritic) {
            chips.add(new String[]{"Metascore", metascore + "/100", "#FFCC33"});
        }

        return chips;
    }

    private String optOmdbString(com.google.gson.JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
        String value = obj.get(key).getAsString();
        return (TextUtils.isEmpty(value) || "N/A".equals(value)) ? "" : value.trim();
    }

    private String buildImdbRatingText(String rating, String votes) {
        if (TextUtils.isEmpty(votes)) return rating;
        String fullText = rating + " (" + votes + ")";
        if (fullText.length() <= OMDB_FULL_RATING_TEXT_MAX_LENGTH) return fullText;
        String compactVotes = compactOmdbVoteCount(votes);
        return rating + " (" + (TextUtils.isEmpty(compactVotes) ? votes : compactVotes) + ")";
    }

    private String compactOmdbVoteCount(String votes) {
        if (TextUtils.isEmpty(votes)) return "";
        String digits = votes.replaceAll("[^0-9]", "");
        if (TextUtils.isEmpty(digits)) return "";
        try {
            long count = Long.parseLong(digits);
            if (count >= 1_000_000_000L) return formatOmdbCompactCount(count / 1_000_000_000d, "B");
            if (count >= 1_000_000L) return formatOmdbCompactCount(count / 1_000_000d, "M");
            if (count >= 1_000L) return formatOmdbCompactCount(count / 1_000d, "K");
        } catch (NumberFormatException ignored) {
            return "";
        }
        return votes;
    }

    private String formatOmdbCompactCount(double value, String suffix) {
        String text = String.format(Locale.US, "%.1f", value);
        if (text.endsWith(".0")) text = text.substring(0, text.length() - 2);
        return text + suffix;
    }

    /**
     * 创建多来源评分卡片：平台名在上，评分在下。
     */
    private View createOmdbRatingChip(String platform, String value, String color) {
        androidx.appcompat.widget.LinearLayoutCompat chip = new androidx.appcompat.widget.LinearLayoutCompat(this);
        chip.setOrientation(androidx.appcompat.widget.LinearLayoutCompat.VERTICAL);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setMinimumWidth(ResUtil.dp2px(120));
        chip.setPadding(ResUtil.dp2px(16), ResUtil.dp2px(10), ResUtil.dp2px(16), ResUtil.dp2px(10));

        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(0x26FFFFFF);
        background.setCornerRadius(ResUtil.dp2px(8));
        chip.setBackground(background);

        TextView platformView = new TextView(this);
        platformView.setText(platform);
        platformView.setTextColor(0xFF9AA7B4);
        platformView.setTextSize(13);
        platformView.setGravity(android.view.Gravity.CENTER);
        platformView.setSingleLine(true);
        platformView.setIncludeFontPadding(false);
        platformView.setMinWidth(ResUtil.dp2px(56));
        chip.addView(platformView);

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(android.graphics.Color.parseColor(color));
        valueView.setTextSize(17);
        valueView.setTypeface(null, android.graphics.Typeface.BOLD);
        valueView.setGravity(android.view.Gravity.CENTER);
        valueView.setSingleLine(true);
        valueView.setIncludeFontPadding(false);
        androidx.appcompat.widget.LinearLayoutCompat.LayoutParams valueParams =
                new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = ResUtil.dp2px(4);
        valueView.setLayoutParams(valueParams);
        chip.addView(valueView);

        androidx.appcompat.widget.LinearLayoutCompat.LayoutParams params =
                new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(ResUtil.dp2px(12));
        chip.setLayoutParams(params);
        return chip;
    }

    private void setupBackdropSlideshow(java.util.List<String> photos) {
        if (photos == null || photos.isEmpty()) {
            if (mBinding.backdropPager != null) {
                mBinding.backdropPager.setVisibility(View.GONE);
            }
            return;
        }

        // 初始化适配器
        if (mBackdropAdapter == null) {
            mBackdropAdapter = new BackdropAdapter();
            if (mBinding.backdropPager != null) {
                mBinding.backdropPager.setAdapter(mBackdropAdapter);
                mBinding.backdropPager.setOffscreenPageLimit(1);
            }
        }

        // 设置图片数据
        mBackdropAdapter.setItems(photos);
        if (mBinding.backdropPager != null) {
            mBinding.backdropPager.setVisibility(View.VISIBLE);
        }

        // 启动自动轮播
        startBackdropAutoScroll();

        SpiderDebug.log("backdrop", "背景幻灯片启动: %d张剧照", photos.size());
    }

    private void startBackdropAutoScroll() {
        stopBackdropAutoScroll();

        if (mBackdropAdapter == null || mBackdropAdapter.getItemCount() == 0) return;

        mBackdropRunnable = new Runnable() {
            @Override
            public void run() {
                if (mBackdropAdapter == null || mBackdropAdapter.getItemCount() == 0) return;

                mCurrentBackdropPage++;
                if (mCurrentBackdropPage >= mBackdropAdapter.getItemCount()) {
                    mCurrentBackdropPage = 0;
                }

                if (mBinding.backdropPager != null) {
                    mBinding.backdropPager.setCurrentItem(mCurrentBackdropPage, true);
                }

                App.post(mBackdropRunnable, 5000); // 5秒切换一次
            }
        };

        App.post(mBackdropRunnable, 5000);
    }

    private void stopBackdropAutoScroll() {
        if (mBackdropRunnable != null) {
            App.removeCallbacks(mBackdropRunnable);
            mBackdropRunnable = null;
        }
    }

    private void onTmdbPersonClick(com.fongmi.android.tv.bean.TmdbPerson person) {
        if (person == null) return;
        com.fongmi.android.tv.ui.dialog.TmdbPersonDialog.show(this, person, getSite());
    }

    private void onTmdbPhotoClick(String url, int position) {
        if (TextUtils.isEmpty(url)) return;
        java.util.List<String> photos = mTmdbUIAdapter.getPhotos();
        com.fongmi.android.tv.ui.dialog.PhotoViewerDialog.show(this, photos, position, null);
    }

    private void onTmdbRecommendationClick(com.fongmi.android.tv.bean.TmdbItem item) {
        TmdbNavigation.open(this, item, getSite());
    }

    private void requestIntroSkipPlan() {
        if (!Setting.isAutoSkipIntroOutro() || player() == null) {
            mIntroSkipPlayback.reset();
            return;
        }
        IntroSkipService.Query query = buildIntroSkipQuery();
        if (query == null) return;
        mIntroSkipPlayback.request(query, this::applyAutoIntroSkip);
    }

    private boolean applyAutoIntroSkip() {
        if (!Setting.isAutoSkipIntroOutro() || player() == null) return false;
        return mIntroSkipPlayback.apply(player(), () -> checkEnded(false));
    }

    private IntroSkipService.Query buildIntroSkipQuery() {
        TmdbItem item = getIntroSkipTmdbItem();
        if (item == null || item.getTmdbId() <= 0) return null;
        Episode episode = getEpisode();
        int season = 0;
        int number = 0;
        if (item.isTv()) {
            TmdbEpisode tmdbEpisode = episode == null ? null : episode.getTmdbEpisode();
            season = tmdbEpisode == null ? 1 : tmdbEpisode.getSeasonNumber();
            number = tmdbEpisode == null ? (episode == null ? 0 : episode.getNumber()) : tmdbEpisode.getNumber();
            if (season <= 0 || number <= 0) return null;
        }
        long duration = player() == null ? 0 : Math.max(0, player().getDuration());
        return new IntroSkipService.Query(item.getTmdbId(), getIntroSkipImdbId(), item.getMediaType(), season, number, duration);
    }

    private TmdbItem getIntroSkipTmdbItem() {
        TmdbItem item = mTmdbUIAdapter == null ? null : mTmdbUIAdapter.getTmdbItem();
        return item == null ? getTmdbItem() : item;
    }

    private String getIntroSkipImdbId() {
        JsonObject detail = mTmdbUIAdapter == null ? null : mTmdbUIAdapter.getTmdbDetail();
        JsonObject externalIds = detail != null && detail.has("external_ids") && !detail.get("external_ids").isJsonNull()
                ? detail.getAsJsonObject("external_ids") : null;
        if (externalIds == null || !externalIds.has("imdb_id") || externalIds.get("imdb_id").isJsonNull()) return "";
        return externalIds.get("imdb_id").getAsString();
    }

    private void setPosition() {
        if (mHistory != null) player().seekTo(Math.max(mHistory.getOpening(), mHistory.getPosition()));
    }

    private void checkEnded(boolean notify) {
        checkNext(notify);
    }

    private void setTrackVisible() {
        mBinding.control.action.text.setVisibility(player().haveTrack(C.TRACK_TYPE_TEXT) || player().isVod() ? View.VISIBLE : View.GONE);
        mBinding.control.action.audio.setVisibility(player().haveTrack(C.TRACK_TYPE_AUDIO) ? View.VISIBLE : View.GONE);
        mBinding.control.action.video.setVisibility(player().haveTrack(C.TRACK_TYPE_VIDEO) ? View.VISIBLE : View.GONE);
        applyActionButtonVisibility();
    }

    private void setTitleVisible() {
        mBinding.control.action.title.setVisibility(player().haveTitle() ? View.VISIBLE : View.GONE);
        applyActionButtonVisibility();
    }

    private MediaMetadata buildMetadata() {
        String title = mHistory.getVodName();
        String episode = getEpisode().getName();
        boolean empty = episode.isEmpty() || title.equals(episode);
        String artist = empty ? "" : episode;
        return PlayerManager.buildMetadata(title, artist, mHistory.getVodPic());
    }

    private void setMetadata() {
        player().setMetadata(buildMetadata());
    }

    private void startFlow() {
        if (!PlayerSetting.isAutoChange()) return;
        if (!getSite().isChangeable()) return;
        if (isUseParse()) checkParse();
        else checkFlag();
    }

    private void checkParse() {
        int position = mParseAdapter.getPosition();
        boolean last = position == mParseAdapter.getItemCount() - 1;
        boolean pass = position == 0 || last;
        if (last) initParse();
        if (pass) checkFlag();
        else nextParse(position);
    }

    private void initParse() {
        if (mParseAdapter.getItemCount() == 0) return;
        setParse(mParseAdapter.first());
    }

    private void checkFlag() {
        int position = isGone(mBinding.flag) ? -1 : mFlagAdapter.getPosition();
        if (position == mFlagAdapter.getItemCount() - 1) checkSearch(false);
        else nextFlag(position);
    }

    private void checkSearch(boolean force) {
        if (!force && !PlayerSetting.isAutoChange()) return;
        if (mQuickAdapter.getItemCount() == 0) initSearch(mBinding.name.getText().toString(), true);
        else if (isAutoMode() || force) nextSite();
    }

    private void initSearch(String keyword, boolean auto) {
        setAutoMode(auto);
        setInitAuto(auto);
        revealManualSearch = !auto;
        startSearch(keyword);
        mBinding.part.setTag(keyword);
    }

    private boolean isPass(Site item) {
        if (isAutoMode() && !item.isChangeable()) return false;
        return item.isSearchable();
    }

    private void startSearch(String keyword) {
        mQuickAdapter.clear();
        mBinding.quick.setVisibility(View.GONE);
        dismissQuickSearchDialog();
        quickSearchDialogClosed = false;
        if (!isInitAuto()) {
            revealManualSearch = false;
            showQuickSearchDialog(new ArrayList<>());
        }
        updateFocus();
        List<Site> sites = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) if (isPass(site)) sites.add(site);
        SiteHealthStore.sortSites(sites);
        mViewModel.searchContent(sites, keyword, true);
    }

    private void setSearch(Result result) {
        List<Vod> items = result.getList();
        items.removeIf(this::mismatch);
        mQuickAdapter.addAll(items);
        mBinding.quick.setVisibility(View.GONE);
        updateFocus();
        if (!isInitAuto() && !items.isEmpty()) {
            showQuickSearchDialog(items);
        }
        if (isInitAuto() && PlayerSetting.isAutoChange()) nextSite();
        if (items.isEmpty()) return;
        App.removeCallbacks(mR4);
    }

    private void setSearchProgress(SearchProgress progress) {
        if (progress == null || isInitAuto()) return;
        showQuickSearchDialog(new ArrayList<>());
        if (mQuickSearchDialog != null) mQuickSearchDialog.setProgress(progress.current(), progress.total(), progress.finished());
    }

    private void showQuickSearchDialog(List<Vod> items) {
        if (quickSearchDialogClosed) return;
        if (mQuickSearchDialog != null) {
            mQuickSearchDialog.addAll(items);
            return;
        }
        QuickSearchDialog dialog = QuickSearchDialog.create().listener(this).items(items);
        dialog.dismissListener(d -> {
            if (mQuickSearchDialog != dialog) return;
            mQuickSearchDialog = null;
            quickSearchDialogClosed = true;
        });
        mQuickSearchDialog = dialog;
        dialog.show(this);
    }

    private void dismissQuickSearchDialog() {
        QuickSearchDialog dialog = mQuickSearchDialog;
        mQuickSearchDialog = null;
        if (dialog != null) dialog.dismissAllowingStateLoss();
    }

    @Override
    public void onItemClick(Vod item) {
        setAutoMode(false);
        applySearchArtwork(item);
        getDetail(item);
    }

    private boolean mismatch(Vod item) {
        if (getId().equals(item.getId())) return true;
        if (mBroken.contains(item.getId())) return true;
        String keyword = Objects.toString(mBinding.part.getTag(), "");
        if (isAutoMode()) return !item.getName().equals(keyword);
        else return !item.getName().contains(keyword);
    }

    private void nextParse(int position) {
        Parse parse = mParseAdapter.get(position + 1);
        Notify.show(getString(R.string.play_switch_parse, parse.getName()));
        onItemClick(parse);
    }

    private void nextFlag(int position) {
        Flag flag = mFlagAdapter.get(position + 1);
        Notify.show(getString(R.string.play_switch_flag, flag.getFlag()));
        onItemClick(flag);
    }

    private void nextSite() {
        if (mQuickAdapter.getItemCount() == 0) return;
        int position = mQuickAdapter.getBestPosition();
        Vod item = mQuickAdapter.get(position);
        Notify.show(getString(R.string.play_switch_site, item.getSiteName()));
        mQuickAdapter.remove(position);
        mBroken.add(getId());
        setInitAuto(false);
        applySearchArtwork(item);
        getDetail(item);
    }

    private void onPaused() {
        controller().pause();
    }

    private void onPlay() {
        if (mHistory != null && isEnded()) controller().seekTo(mHistory.getOpening());
        if (!player().isEmpty() && isIdle()) controller().prepare();
        controller().play();
    }

    private boolean onSeekBack() {
        controller().seekBack();
        return true;
    }

    private boolean onSeekForward() {
        controller().seekForward();
        return true;
    }

    private boolean isFullscreen() {
        return fullscreen;
    }

    private void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
        mBinding.control.action.fullscreen.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        mBinding.control.action.fullscreen.setText(R.string.play_fullscreen);
        applyActionButtonVisibility();
    }

    private void initTmdbMode() {
        // TMDB 模式：通过全局开关或 Intent 参数启用
        if (!isTmdbSourceEnabled()) return;

        mTmdbUIAdapter = new com.fongmi.android.tv.ui.helper.TmdbUIAdapter(this);
        if (!mTmdbUIAdapter.isReady()) {
            SpiderDebug.log("TMDB 增强已启用，但配置未就绪（需要 API Key）");
            return;
        }
        mTmdbUIAdapter.setPersonalAiUpdateListener(this::refreshTmdbPersonalAiRecommendations);
        com.fongmi.android.tv.bean.TmdbItem tmdbItem = getTmdbItem();
        if (tmdbItem != null) {
            SpiderDebug.log("TMDB 模式: 使用传入的 TmdbItem");
        }
    }

    private void applyShortDramaMode() {
        Site site = getSite();
        if (!Setting.isShortDramaSiteEnabled(site == null ? getKey() : site.getKey(), site == null ? "" : site.getName())) return;
        if (!isFullscreen()) enterFullscreen();
        // 优先使用用户手动设置的格式，如果没有设置过则使用短剧默认格式
        int scale = (mHistory != null && mHistory.getScale() != -1) ? mHistory.getScale() : SHORT_DRAMA_SCALE;
        setPreviewScale(scale);
        hideInfo();
    }

    private void setPreviewScale(int scale) {
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        if (scale < 0 || scale >= array.length) return;
        if (mHistory != null) mHistory.setScale(scale);
        mBinding.exo.setResizeMode(scale);
        mBinding.control.action.scale.setText(array[scale]);
    }

    private boolean isInitAuto() {
        return initAuto;
    }

    private void setInitAuto(boolean initAuto) {
        this.initAuto = initAuto;
    }

    private boolean isAutoMode() {
        return autoMode;
    }

    private void setAutoMode(boolean autoMode) {
        this.autoMode = autoMode;
    }

    public boolean isUseParse() {
        return useParse;
    }

    public void setUseParse(boolean useParse) {
        this.useParse = useParse;
    }

    private View getFocus1() {
        return mFocus1 == null || mFocus1.getVisibility() != View.VISIBLE ? mBinding.video : mFocus1;
    }

    private View getFocus2() {
        return mFocus2 == null || mFocus2.getVisibility() != View.VISIBLE || mFocus2 == mBinding.control.action.opening || mFocus2 == mBinding.control.action.ending ? mBinding.control.action.next : mFocus2;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isActionUp(event) && KeyUtil.isBackKey(event) && mBinding.lutQuick.hideIfVisible()) return true;
        if (isVisible(mBinding.lutQuick)) return dispatchLutQuickKey(event);
        if (isFullscreen() && KeyUtil.isMenuKey(event)) {
            if (Setting.getFullscreenMenuKey() == 1) onEpisodes();
            else onToggle();
        }
        if (isVisible(mBinding.control.getRoot())) setR1Callback();
        if (isVisible(mBinding.control.getRoot())) mFocus2 = getCurrentFocus();
        if (onEpisodeKey(event)) return true;
        if (handleEpisodeLongPress(event)) return true;
        if (isFullscreen() && isGone(mBinding.control.getRoot()) && mKeyDown.hasEvent(event) && service() != null) return mKeyDown.onKeyDown(event);
        if (KeyUtil.isMediaFastForward(event)) return onSeekForward();
        if (KeyUtil.isMediaRewind(event)) return onSeekBack();
        return super.dispatchKeyEvent(event);
    }

    private boolean dispatchLutQuickKey(KeyEvent event) {
        if (KeyUtil.isEnterKey(event)) return dispatchLutQuickEnter(event);
        if (isLutQuickDirectionKey(event)) return dispatchLutQuickDirection(event);
        if (KeyUtil.isActionDown(event)) focusLutQuickContent();
        boolean handled = super.dispatchKeyEvent(event);
        if (KeyUtil.isActionDown(event)) {
            View focus = getCurrentFocus();
            if (focus == null || !isChildOf(mBinding.lutQuick, focus)) focusLutQuickContent();
        }
        return true;
    }

    private boolean isLutQuickDirectionKey(KeyEvent event) {
        return KeyUtil.isUpKey(event) || KeyUtil.isDownKey(event) || KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event);
    }

    private boolean dispatchLutQuickDirection(KeyEvent event) {
        if (!KeyUtil.isActionDown(event)) return true;
        RecyclerView recycler = findRecyclerView(mBinding.lutQuick);
        View focus = getCurrentFocus();
        if (recycler != null && (focus == recycler || isChildOf(recycler, focus)) && moveLutQuickRecycler(recycler, event)) return true;
        if (focus == null || !isChildOf(mBinding.lutQuick, focus) || focus == recycler) {
            focusLutQuickContent();
            focus = getCurrentFocus();
        }
        if (focus != null && isChildOf(mBinding.lutQuick, focus) && moveLutQuickFocus(focus, event)) return true;
        if (recycler != null && KeyUtil.isDownKey(event) && focusRecyclerItem(recycler)) return true;
        focusLutQuickContent();
        return true;
    }

    private boolean moveLutQuickRecycler(RecyclerView recycler, KeyEvent event) {
        if (!KeyUtil.isUpKey(event) && !KeyUtil.isDownKey(event)) return false;
        RecyclerView.Adapter<?> adapter = recycler.getAdapter();
        if (adapter == null || adapter.getItemCount() <= 0) return false;
        int current = getRecyclerFocusPosition(recycler);
        if (current == RecyclerView.NO_POSITION) return mBinding.lutQuick.focusSelectedEntry();
        int next = current + (KeyUtil.isDownKey(event) ? 1 : -1);
        if (next < 0 || next >= adapter.getItemCount()) return false;
        return focusRecyclerPosition(recycler, next);
    }

    private int getRecyclerFocusPosition(RecyclerView recycler) {
        View child = getRecyclerDirectChild(recycler, getCurrentFocus());
        return child == null ? RecyclerView.NO_POSITION : recycler.getChildAdapterPosition(child);
    }

    private View getRecyclerDirectChild(RecyclerView recycler, View focus) {
        for (View view = focus; view != null && view != recycler; ) {
            if (view.getParent() == recycler) return view;
            if (!(view.getParent() instanceof View next)) return null;
            view = next;
        }
        return null;
    }

    private boolean moveLutQuickFocus(View focus, KeyEvent event) {
        List<View> focusables = new ArrayList<>();
        collectLutQuickFocusables(mBinding.lutQuick, focusables);
        View target = findLutQuickFocusTarget(focus, focusables, event);
        return target != null && target.requestFocus();
    }

    private void collectLutQuickFocusables(View view, List<View> focusables) {
        if (view == null || view.getVisibility() != View.VISIBLE || !view.isEnabled()) return;
        if (view instanceof RecyclerView recycler) {
            for (int i = 0; i < recycler.getChildCount(); i++) collectLutQuickFocusables(recycler.getChildAt(i), focusables);
            return;
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) collectLutQuickFocusables(group.getChildAt(i), focusables);
            return;
        }
        if (view.isFocusable()) focusables.add(view);
    }

    private View findLutQuickFocusTarget(View focus, List<View> focusables, KeyEvent event) {
        Rect current = new Rect();
        if (focus == null || !focus.getGlobalVisibleRect(current)) return null;
        View target = null;
        long bestScore = Long.MAX_VALUE;
        for (View item : focusables) {
            if (item == focus) continue;
            Rect candidate = new Rect();
            if (!item.getGlobalVisibleRect(candidate) || !isLutQuickFocusCandidate(current, candidate, event)) continue;
            long score = scoreLutQuickFocusCandidate(current, candidate, event);
            if (score < bestScore) {
                bestScore = score;
                target = item;
            }
        }
        return target;
    }

    private boolean isLutQuickFocusCandidate(Rect current, Rect candidate, KeyEvent event) {
        int dx = candidate.centerX() - current.centerX();
        int dy = candidate.centerY() - current.centerY();
        if (KeyUtil.isLeftKey(event)) return dx < 0 && isSameFocusRow(current, candidate);
        if (KeyUtil.isRightKey(event)) return dx > 0 && isSameFocusRow(current, candidate);
        if (KeyUtil.isUpKey(event)) return dy < 0;
        if (KeyUtil.isDownKey(event)) return dy > 0;
        return false;
    }

    private boolean isSameFocusRow(Rect current, Rect candidate) {
        return Math.abs(candidate.centerY() - current.centerY()) <= Math.max(current.height(), candidate.height());
    }

    private long scoreLutQuickFocusCandidate(Rect current, Rect candidate, KeyEvent event) {
        long dx = Math.abs(candidate.centerX() - current.centerX());
        long dy = Math.abs(candidate.centerY() - current.centerY());
        long primary = KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event) ? dx : dy;
        long secondary = KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event) ? dy : dx;
        return primary * 1000 + secondary;
    }

    private boolean dispatchLutQuickEnter(KeyEvent event) {
        if (KeyUtil.isActionDown(event)) {
            focusLutQuickContent();
            return true;
        }
        if (!KeyUtil.isActionUp(event)) return true;
        View focus = getCurrentFocus();
        if (focus == null || !isChildOf(mBinding.lutQuick, focus) || focus instanceof RecyclerView) {
            if (!focusLutQuickContent()) return true;
            focus = getCurrentFocus();
        }
        if (focus != null && isChildOf(mBinding.lutQuick, focus) && focus.isEnabled()) focus.performClick();
        return true;
    }

    @Override
    public void onSeeking(long time) {
        mBinding.widget.center.setVisibility(View.VISIBLE);
        mBinding.widget.duration.setText(player().getDurationTime());
        mBinding.widget.position.setText(player().getPositionTime(time));
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        hideProgress();
    }

    @Override
    public void onSeekEnd(long time) {
        mKeyDown.reset();
        seekTo(time);
    }

    @Override
    public void onSpeedUp() {
        if (!player().isPlaying()) return;
        mBinding.widget.speed.setVisibility(View.VISIBLE);
        mBinding.widget.speed.startAnimation(ResUtil.getAnim(R.anim.forward));
        mBinding.control.action.speed.setText(player().setSpeed(PlayerSetting.getSpeed()));
        saveDefaultSpeed();
    }

    @Override
    public void onSpeedEnd() {
        mBinding.widget.speed.clearAnimation();
        mBinding.widget.speed.setVisibility(View.GONE);
        mBinding.control.action.speed.setText(player().getSpeedText());
        mHistory.setSpeed(player().getSpeed());
    }

    @Override
    public void onKeyUp() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetOpening(position, duration)) {
            showControl(mBinding.control.action.opening);
        } else if (player().canSetEnding(position, duration)) {
            showControl(mBinding.control.action.ending);
        } else {
            showControl(getFocus2());
        }
    }

    @Override
    public void onKeyDown() {
        showControl(getFocus2());
    }

    @Override
    public void onKeyCenter() {
        if (player().isPlaying()) onPaused();
        else if (player().isEmpty()) onRefresh();
        else onPlay();
        hideControl();
    }

    private boolean handleEpisodeLongPress(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_DPAD_CENTER && event.getKeyCode() != KeyEvent.KEYCODE_ENTER) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (!event.isLongPress()) return false;
        View focused = getCurrentFocus();
        if (focused == null) return false;
        // 检查焦点是否在选集卡片上
        if (focused.getId() != R.id.cardContainer) return false;
        RecyclerView episodeView = episodeGridMode ? mBinding.episodeGrid : mBinding.episode;
        RecyclerView.ViewHolder holder = episodeView.findContainingViewHolder(focused);
        if (holder == null) return false;
        int pos = holder.getBindingAdapterPosition();
        if (pos >= 0 && pos < mEpisodeAdapter.getItemCount()) {
            Episode item = mEpisodeAdapter.getItems().get(pos);
            onEpisodeLongClick(item);
            return true;
        }
        return false;
    }

    @Override
    public void onSingleTap() {
        if (isFullscreen()) onToggle();
    }

    @Override
    public void onDoubleTap() {
        if (isFullscreen()) onKeyCenter();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == 1001) PlayerHelper.onExternalResult(data, service()::dispatchNext, controller()::seekTo);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mClock.stop().start();
        if (mOsd != null) {
            mOsd.setDiagnosticsVisible(PlayerSetting.isOsdDiagnostics());
            mOsd.start();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mOsd != null) mOsd.stop();
        if (PlayerSetting.isBackgroundOff()) mClock.stop();
    }

    @Override
    protected void onBackInvoked() {
        if (mBinding.lutQuick.hideIfVisible()) {
            return;
        } else if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isVisible(mBinding.widget.center)) {
            hideCenter();
        } else if (isFullscreen()) {
            exitFullscreen();
        } else {
            mViewModel.stopSearch();
            if (isTaskRoot()) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            super.onBackInvoked();
        }
    }

    @Override
    protected void onDestroy() {
        subtitlePlaybackSession.stop(this);
        mClock.release();
        saveHistory(true);
        DanmakuApi.cancel();
        stopBackdropAutoScroll();
        dismissQuickSearchDialog();
        RefreshEvent.keep();
        App.removeCallbacks(mR1, mR2, mR4);
        App.removeCallbacks(mTmdbDetailTimeout);
        if (mOsd != null) mOsd.release();
        mViewModel.getResult().removeObserver(mObserveDetail);
        mViewModel.getPlayer().removeObserver(mObservePlayer);
        mViewModel.getSearch().removeObserver(mObserveSearch);
        mViewModel.getSearchProgress().removeObserver(mObserveSearchProgress);
        SiteHealthStore.flush();
        super.onDestroy();
    }

    @Override
    public String getSubtitlePlaybackKey() {
        return getHistoryKey();
    }

    @Override
    public Site getSubtitleSite() {
        return getSite();
    }

    @Override
    public Vod getSubtitleVod() {
        return mVod;
    }

    @Override
    public Episode getSubtitleEpisode() {
        return getEpisode();
    }

    @Override
    public TmdbItem getSubtitleTmdbItem() {
        TmdbItem item = mTmdbUIAdapter == null ? null : mTmdbUIAdapter.getTmdbItem();
        return item == null ? getTmdbItem() : item;
    }

    @Override
    public TmdbEpisode getSubtitleTmdbEpisode() {
        Episode episode = getEpisode();
        return episode == null ? null : episode.getTmdbEpisode();
    }

    @Override
    public PlayerManager getSubtitlePlayer() {
        return player();
    }

    @Override
    public boolean isSubtitleHostActive() {
        return !isFinishing() && !isDestroyed() && service() != null && player() != null && !player().isReleased() && !player().isEmpty() && isOwner();
    }
}
