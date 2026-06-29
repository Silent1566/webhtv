package com.fongmi.android.tv.ui.activity;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.ChangeBounds;
import androidx.transition.TransitionManager;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.request.transition.Transition;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.DanmakuApi;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.CastVideo;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.CustomTarget;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.playback.PlaybackEventCollector;
import com.fongmi.android.tv.player.IntroSkipPlayback;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.lut.LutPreset;
import com.fongmi.android.tv.player.lut.LutSetting;
import com.fongmi.android.tv.player.lut.LutStore;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.service.PersonalRecommendationService;
import com.fongmi.android.tv.service.IntroSkipService;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.PlayerButtonSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.subtitle.SubtitlePlaybackSession;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.EpisodeGroupAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.adapter.ParseAdapter;
import com.fongmi.android.tv.ui.adapter.QualityAdapter;
import com.fongmi.android.tv.ui.adapter.QuickAdapter;
import com.fongmi.android.tv.ui.adapter.TmdbRecommendationAdapter;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.custom.CustomKeyDown;
import com.fongmi.android.tv.ui.custom.CustomMovement;
import com.fongmi.android.tv.ui.custom.CustomSeekView;
import com.fongmi.android.tv.ui.custom.PlayerOsdController;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.dialog.CastDialog;
import com.fongmi.android.tv.ui.dialog.ControlDialog;
import com.fongmi.android.tv.ui.dialog.DanmakuDialog;
import com.fongmi.android.tv.ui.dialog.DisplayDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeGridDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeListDialog;
import com.fongmi.android.tv.ui.dialog.InfoDialog;
import com.fongmi.android.tv.ui.dialog.LutPanelDialog;
import com.fongmi.android.tv.ui.dialog.QuickSearchDialog;
import com.fongmi.android.tv.ui.dialog.ReceiveDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleManualSearchDialog;
import com.fongmi.android.tv.ui.dialog.TmdbSearchDialog;
import com.fongmi.android.tv.ui.dialog.TitleDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.ui.dialog.VideoContentDialog;
import com.fongmi.android.tv.ui.helper.DetailThemeVisibility;
import com.fongmi.android.tv.ui.helper.EpisodeDisplayPolicy;
import com.fongmi.android.tv.ui.helper.EpisodeRangePolicy;
import com.fongmi.android.tv.ui.helper.TmdbNavigation;
import com.fongmi.android.tv.utils.AudioUtil;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.EpisodeTitleCompact;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.EpisodeTitleFormatter;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PiP;
import com.fongmi.android.tv.utils.PushParser;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Timer;
import com.fongmi.android.tv.utils.Traffic;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.crawler.SpiderDebug;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.gson.JsonObject;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class VideoActivity extends PlaybackActivity implements Clock.Callback, CustomKeyDown.Listener, TrackDialog.Listener, ControlDialog.Listener, FlagAdapter.OnClickListener, EpisodeAdapter.OnClickListener, EpisodeGroupAdapter.OnClickListener, QualityAdapter.OnClickListener, QuickAdapter.OnClickListener, ParseAdapter.OnClickListener, CastDialog.Listener, InfoDialog.Listener, SubtitlePlaybackSession.Host {

    private static final int SHORT_DRAMA_SCALE = 4;
    private static final int SHORT_DRAMA_EDGE_MARGIN_DP = 12;
    private static final int FUSION_PLAYER_TOP_MARGIN_DP = 72;
    private static final int FUSION_PLAYER_SIDE_MARGIN_DP = 16;
    private static final int FUSION_PLAYER_HEIGHT_DP = 252;
    private static final int FUSION_PLAYER_BOTTOM_GAP_DP = 14;
    private static final int EPISODE_CARD_HEIGHT_DP = 190;
    private static final int EPISODE_CARD_VERTICAL_MARGIN_DP = 12;
    private static final String EXTRA_TMDB_PLAY_FLAG = "tmdb_play_flag";
    private static final String EXTRA_TMDB_PLAY_EPISODE_NAME = "tmdb_play_episode_name";
    private static final String EXTRA_TMDB_PLAY_EPISODE_URL = "tmdb_play_episode_url";
    private static final String EXTRA_TMDB_DETAIL_THEME = "tmdb_detail_theme";
    private static final int TMDB_TABLET_PLAYER_MIN_WIDTH_DP = 440;
    private static final int TMDB_TABLET_PLAYER_MAX_WIDTH_DP = 640;
    private static final int TMDB_TABLET_PLAYER_SIDE_MARGIN_DP = 24;
    private static final int TMDB_TABLET_PLAYER_GUTTER_DP = 16;
    private static final int TMDB_TABLET_PLAYER_TOP_MARGIN_DP = 16;
    private static final int TMDB_TABLET_SUMMARY_MIN_WIDTH_DP = 280;

    private ActivityVideoBinding mBinding;
    private ViewGroup.LayoutParams mFrameParams;
    private int mFrameHeight;
    private Observer<Result> mObserveDetail;
    private Observer<Result> mObservePlayer;
    private Observer<Result> mObserveSearch;
    private EpisodeAdapter mEpisodeAdapter;
    private EpisodeGroupAdapter mEpisodeGroupAdapter;
    private SpaceItemDecoration mEpisodeDecoration;
    private QualityAdapter mQualityAdapter;
    private QuickAdapter mQuickAdapter;
    private QuickSearchDialog mQuickSearchDialog;
    private ParseAdapter mParseAdapter;
    private TmdbRecommendationAdapter mPersonalTmdbRecommendationAdapter;
    private TmdbRecommendationAdapter mPersonalDoubanRecommendationAdapter;
    private TmdbRecommendationAdapter mPersonalAiRecommendationAdapter;
    private PersonalRecommendationService.RecommendationPage mNativePersonalTmdbPage;
    private PersonalRecommendationService.RecommendationPage mNativePersonalDoubanPage;
    private PersonalRecommendationService.RecommendationPage mNativePersonalAiPage;
    private Map<String, View> mActionButtons;
    private SiteViewModel mViewModel;
    private FlagAdapter mFlagAdapter;
    private PlayerOsdController mOsd;
    private final IntroSkipPlayback mIntroSkipPlayback = new IntroSkipPlayback();
    private final SubtitlePlaybackSession subtitlePlaybackSession = new SubtitlePlaybackSession(this);
    private ValueAnimator mAnimator;
    private CustomKeyDown mKeyDown;
    private List<String> mBroken;
    private History mHistory;
    private boolean fullscreen;
    private boolean initAuto;
    private boolean autoMode;
    private boolean revealManualSearch;
    private boolean useParse;
    private boolean rotate;
    private boolean detailHealthRecorded;
    private boolean playHealthRecorded;
    private boolean mNativePersonalTmdbLoading;
    private boolean mNativePersonalDoubanLoading;
    private boolean mEpisodeGridMode = Setting.getTmdbEpisodeGridMode();
    private int mEpisodeSpanCount;
    private int mEpisodeBottomInset;
    private int mEpisodeMaxHeight;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private Clock mClock;
    private PiP mPiP;
    private String mContextWallUrl;
    private String mContextWallLockedUrl;
    private String playHealthKey;
    private long detailStartTime;
    private long playerStartTime;
    private final List<ShortDramaControlItem> mShortDramaControlItems = new ArrayList<>();
    private ViewGroup mShortDramaControlDock;
    private boolean shortDramaControlsDocked;

    // TMDB 模式相关字段
    private com.fongmi.android.tv.ui.helper.TmdbUIAdapter mTmdbUIAdapter;
    private com.fongmi.android.tv.ui.custom.TmdbHeaderView mTmdbHeaderView;
    private Vod mVod;
    private boolean mTmdbContentLoaded = false;
    private boolean mTmdbFallbackToNative = false;
    private boolean mTmdbControlsMoved = false;
    private boolean mTmdbAutoDialogShown = false;
    private boolean mFusionChromeApplied = false;
    private boolean mTmdbTabletLayoutApplied = false;
    private MaterialButton mFusionThemeButton;
    private View mFusionPlayerBottomSpacer;
    private int mTmdbDialogGeneration;
    private int mPersonalRecommendationGeneration;
    private final List<TmdbMovedView> mTmdbMovedViews = new ArrayList<>();
    private boolean pendingLutImport;
    private boolean skipPausePiP;
    private RelativeLayout.LayoutParams mDefaultFrameParams;

    private final ActivityResultLauncher<Intent> mLutDir = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        LutStore.setUserDir(result.getData().getData(), result.getData().getFlags());
        Notify.show(R.string.lut_directory_selected);
        if (hasLutQuick()) mBinding.lutQuick.refreshList();
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
                    if (isFullscreen() && hasLutQuick()) mBinding.lutQuick.selectImported(preset, player(), mBinding.exo, this::onLutChanged);
                    else onLutSelected(preset);
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
        start(activity, history.getSiteKey(), history.getVodId(), history.getVodName(), history.getVodPic(), null, history.getWallPic());
    }

    public static void collect(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null, true);
    }

    public static void collect(Activity activity, String key, String id, String name, String pic, String wallPic) {
        start(activity, key, id, name, pic, null, true, (TmdbItem) null, wallPic);
    }

    private static boolean canOpenLegacyTmdbDetail(String key) {
        if (TextUtils.isEmpty(key)) return false;
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

    private static boolean shouldOpenLegacyTmdbDetail(String key) {
        int mode = Setting.getDetailOpenMode();
        return canOpenLegacyTmdbDetail(key) && Setting.isTmdbDetailPage() && Setting.isStandaloneTmdbDetailMode(mode);
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
        start(activity, key, id, name, pic, mark, false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, String wallPic) {
        start(activity, key, id, name, pic, mark, false, (TmdbItem) null, wallPic);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, String wallPic, String content) {
        start(activity, key, id, name, pic, mark, false, wallPic, content);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect) {
        start(activity, key, id, name, pic, mark, collect, (TmdbItem) null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, String wallPic) {
        start(activity, key, id, name, pic, mark, collect, (TmdbItem) null, wallPic, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, String wallPic, String content) {
        start(activity, key, id, name, pic, mark, collect, (TmdbItem) null, wallPic, content);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, com.fongmi.android.tv.bean.TmdbItem tmdbItem) {
        start(activity, key, id, name, pic, mark, collect, tmdbItem, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, com.fongmi.android.tv.bean.TmdbItem tmdbItem, String wallPic) {
        start(activity, key, id, name, pic, mark, collect, tmdbItem, wallPic, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, com.fongmi.android.tv.bean.TmdbItem tmdbItem, String wallPic, String content) {
        ImgUtil.preload(activity, pic);
        if (Setting.isPlaybackArtworkWall() && !TextUtils.isEmpty(wallPic) && !TextUtils.equals(wallPic, pic)) ImgUtil.preload(activity, wallPic);
        if (dispatchToContentHandler(activity, key, id, name, pic, mark)) return;
        if (tmdbItem == null && shouldOpenLegacyTmdbDetail(key)) {
            TmdbDetailActivity.start(activity, key, id, name, pic, mark, null, Setting.getDetailOpenMode());
            return;
        }
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("tmdbMode", tmdbItem != null);
        intent.putExtra("tmdbItem", tmdbItem);
        intent.putExtra("collect", collect);
        intent.putExtra("mark", mark);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("wallPic", wallPic);
        intent.putExtra("content", content);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        activity.startActivity(intent);
    }

    public static void startWithTmdb(Activity activity, String key, String id, String name, String pic, String mark, com.fongmi.android.tv.bean.TmdbItem tmdbItem) {
        start(activity, key, id, name, pic, mark, false, tmdbItem);
    }

    public static void startDirect(Activity activity, String key, String id, String name, String pic) {
        startDirect(activity, key, id, name, pic, null);
    }

    public static void startDirect(Activity activity, String key, String id, String name, String pic, String mark) {
        if (AudioActivity.startSite(activity, key, id, name, pic, mark)) return;
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("collect", false);
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
        intent.putExtra(EXTRA_TMDB_DETAIL_THEME, Setting.getTmdbDetailTheme());
        intent.putExtra("tmdbItem", item);
        intent.putExtra("collect", false);
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

    private static boolean dispatchToContentHandler(Activity activity, String key, String id, String name, String pic, String mark) {
        return com.fongmi.android.tv.content.ContentDispatcher.dispatchSite(activity, key, id, name, pic, mark);
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
        if (mFlagAdapter != null && !mFlagAdapter.isEmpty()) {
            List<Episode> items = getFlag().getEpisodes();
            for (Episode item : items) if (item.isSelected()) return item;
            if (!items.isEmpty()) return items.get(0);
        }
        return mEpisodeAdapter.isEmpty() ? new Episode() : mEpisodeAdapter.getActivated();
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

    private boolean shouldUseTmdbEpisodeCards(List<Episode> items) {
        return EpisodeDisplayPolicy.shouldUseTmdbEpisodeCards(isTmdbSourceEnabled(), items);
    }

    private boolean hasTmdbDetailAdapter() {
        return isTmdbSourceEnabled() && mTmdbHeaderView != null && mTmdbUIAdapter != null && mTmdbUIAdapter.isReady();
    }

    private boolean shouldUseTmdbDetailLayout() {
        return hasTmdbDetailAdapter() && !mTmdbFallbackToNative;
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
        return mEpisodeAdapter == null || mEpisodeAdapter.isEmpty() ? "" : getEpisodeTitle(getEpisode());
    }

    private String getEpisodeTitle(Episode episode) {
        return episode == null ? "" : EpisodeAdapter.getTitle(episode);
    }

    private CharSequence getPlaybackControlTitle() {
        return getPlaybackControlTitle(mEpisodeAdapter == null || mEpisodeAdapter.isEmpty() ? null : getEpisode());
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

    private boolean isAutoRotate() {
        return Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
    }

    private boolean isLand() {
        return mBinding.getRoot().getTag().equals("land");
    }

    private boolean isPort() {
        return mBinding.getRoot().getTag().equals("port");
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityVideoBinding.inflate(getLayoutInflater());
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
        player().setDanmakuController(mBinding.exo.getDanmakuController());
        player().setDanmakuEnabled(DanmakuSetting.isShow());
        setPlayerKernel();
        setDecode();
        setLut();
        checkLand();
        checkId();
    }

    @Override
    protected void onPlayerRebuilt() {
        setPlayerKernel();
        setDecode();
        setLut();
        refreshControlDialog();
    }

    private void refreshControlDialog() {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof ControlDialog dialog) dialog.setPlayer();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        String oldId = getId();
        super.onNewIntent(intent);
        String id = Objects.toString(intent.getStringExtra("id"), "");
        if (TextUtils.isEmpty(id) || id.equals(oldId)) return;
        mBinding.swipeLayout.setRefreshing(true);
        saveHistory();
        getIntent().putExtras(intent);
        setOrient();
        checkId();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.getRoot(), (v, insets) -> setStatusBar(insets));
        mKeyDown = CustomKeyDown.create(this, mBinding.exo);
        mFrameParams = mBinding.video.getLayoutParams();
        mFrameHeight = mFrameParams.height;
        if (mFrameParams instanceof RelativeLayout.LayoutParams params) mDefaultFrameParams = new RelativeLayout.LayoutParams(params);
        mBinding.swipeLayout.setEnabled(false);
        mObserveDetail = this::setDetail;
        mObservePlayer = this::setPlayer;
        mObserveSearch = this::setSearch;
        mBroken = new ArrayList<>();
        mClock = Clock.create();
        mClock.start();
        android.util.Log.d("VideoActivity", "Clock started in initView");
        mR1 = this::hideControl;
        mR2 = this::setTraffic;
        mR3 = this::setOrient;
        mR4 = this::showEmpty;
        mPiP = new PiP();
        checkDanmakuImg();
        setRecyclerView();
        mOsd = new PlayerOsdController(mBinding.osd.getRoot(), mBinding.osd.osdTopLeft, mBinding.osd.osdTopRight, mBinding.osd.osdBottomLeft, mBinding.osd.osdBottomRight, mBinding.osd.osdDiagnostics, mBinding.osd.osdMiniProgress, new PlayerOsdController.Source() {
            @Override
            public PlayerManager getPlayer() {
                return service() == null ? null : player();
            }

            @Override
            public String getTitle() {
                return getOsdTitle();
            }
        }, 12f);
        setVideoView();
        setViewModel();
        initTmdbMode();
        setShortDisplay();
        if (hasInitialPreview()) showInitialPreview();
        else {
            android.util.Log.d("VideoActivity", "onCreate - 调用 showProgress()");
            mBinding.progressLayout.showProgress();
        }
        showProgress();
        setAnimator();
        if (isShortDramaSource()) enterShortDramaFullscreen();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.name.setOnClickListener(view -> onName());
        mBinding.more.setOnClickListener(view -> onMore());
        mBinding.shortDisplay.setOnClickListener(view -> onShortDisplay());
        mBinding.search.setOnClickListener(view -> onSearch());
        mBinding.castAction.setOnClickListener(view -> onCast());
        mBinding.settingAction.setOnClickListener(view -> onSetting());
        mBinding.actor.setOnClickListener(view -> onActor());
        mBinding.content.setOnClickListener(view -> onContent());
        mBinding.reverse.setOnClickListener(view -> onReverse());
        if (mBinding.episodeViewMode != null) mBinding.episodeViewMode.setOnClickListener(view -> toggleEpisodeViewMode());
        mBinding.director.setOnClickListener(view -> onDirector());
        mBinding.name.setOnLongClickListener(view -> onChange());
        mBinding.content.setOnLongClickListener(view -> onCopy());
        mBinding.control.back.setOnClickListener(view -> onBack());
        mBinding.control.cast.setOnClickListener(view -> onCast());
        mBinding.control.info.setOnClickListener(view -> onInfo());
        mBinding.control.keep.setOnClickListener(view -> onKeep());
        mBinding.control.display.setOnClickListener(view -> onDisplay());
        mBinding.control.osdDiagnostics.setOnClickListener(view -> onOsdDiagnostics());
        mBinding.control.play.setOnClickListener(view -> checkPlay());
        mBinding.control.next.setOnClickListener(view -> checkNext());
        mBinding.control.prev.setOnClickListener(view -> checkPrev());
        mBinding.control.setting.setOnClickListener(view -> onSetting());
        mBinding.control.title.setOnLongClickListener(view -> onChange());
        mBinding.control.right.lock.setOnClickListener(view -> onLock());
        mBinding.control.right.rotate.setOnClickListener(view -> onRotate());
        mBinding.control.fullscreen.setOnClickListener(view -> onFullscreen());
        mBinding.control.danmaku.setOnClickListener(view -> onDanmakuShow());
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.actionQuality.setOnClickListener(view -> onQuality());
        mBinding.control.action.lut.setOnClickListener(view -> onLut());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.reset.setOnClickListener(view -> onReset());
        mBinding.control.action.title.setOnClickListener(view -> onTitle());
        mBinding.control.action.player.setOnClickListener(view -> onPlayerKernel());
        mBinding.control.action.player.setOnLongClickListener(view -> onPlayerKernelLong());
        mBinding.control.action.prev.setOnClickListener(view -> checkPrev());
        mBinding.control.action.next.setOnClickListener(view -> checkNext());
        mBinding.control.action.decode.setOnClickListener(view -> onDecode());
        mBinding.control.action.ending.setOnClickListener(view -> onEnding());
        mBinding.control.action.repeat.setOnClickListener(view -> onRepeat());
        mBinding.control.action.opening.setOnClickListener(view -> onOpening());
        mBinding.control.action.danmaku.setOnClickListener(view -> onDanmaku());
        mBinding.control.action.episodes.setOnClickListener(view -> onEpisodes());
        mBinding.control.action.text.setOnLongClickListener(view -> onTextLong());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.action.reset.setOnLongClickListener(view -> onResetToggle());
        mBinding.control.action.ending.setOnLongClickListener(view -> onEndingReset());
        mBinding.control.action.opening.setOnLongClickListener(view -> onOpeningReset());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
        mBinding.control.action.getRoot().setOnTouchListener(this::onActionTouch);
        mBinding.swipeLayout.setOnRefreshListener(this::onSwipeRefresh);
    }

    private WindowInsetsCompat setStatusBar(WindowInsetsCompat insets) {
        int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
        int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
        ViewGroup.LayoutParams lp = mBinding.statusBar.getLayoutParams();
        lp.height = top;
        mBinding.statusBar.setLayoutParams(lp);
        setEpisodeBottomInset(bottom);
        return insets;
    }

    private void setEpisodeBottomInset(int bottom) {
        mEpisodeBottomInset = bottom;
        int padding = ResUtil.dp2px(12);
        mBinding.episode.setPaddingRelative(mBinding.episode.getPaddingStart(), mBinding.episode.getPaddingTop(), mBinding.episode.getPaddingEnd(), padding);
        mBinding.episode.post(this::updateEpisodeViewportHeight);
    }

    private void updateEpisodeViewportHeight() {
        if (mBinding.episode.getVisibility() != View.VISIBLE) return;
        int limit = ResUtil.isPad() || ResUtil.isLand(this) ? ResUtil.dp2px(328) : ResUtil.dp2px(280);
        // The episode list lives inside a scroll container, so capping it by the
        // current on-screen remainder can collapse the viewport to a single row
        // when the section is laid out below the fold. Keep a stable cap here
        // and let the parent page handle the rest of the scrolling.
        int height = limit;
        if (isTmdbEpisodeCardMode()) height = Math.max(height, getEpisodeCardMinHeight());
        if (height <= 0 || height == mEpisodeMaxHeight) return;
        mEpisodeMaxHeight = height;
        mBinding.episode.setMaxHeight(height);
        mBinding.episode.requestLayout();
    }

    private boolean isTmdbEpisodeCardMode() {
        return mEpisodeAdapter != null && !mEpisodeAdapter.isEmpty() && mEpisodeAdapter.isUsingTmdbCard();
    }

    private int getEpisodeCardMinHeight() {
        return ResUtil.dp2px(EPISODE_CARD_HEIGHT_DP + EPISODE_CARD_VERTICAL_MARGIN_DP);
    }

    private void setRecyclerView() {
        mBinding.flag.setHasFixedSize(true);
        mBinding.flag.setItemAnimator(null);
        mBinding.flag.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.flag.setAdapter(mFlagAdapter = new FlagAdapter(this));
        mBinding.quick.setAdapter(mQuickAdapter = new QuickAdapter(this));
        mBinding.tmdbPersonalTmdbRecommendations.setHasFixedSize(true);
        mBinding.tmdbPersonalTmdbRecommendations.setItemAnimator(null);
        mBinding.tmdbPersonalTmdbRecommendations.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.tmdbPersonalTmdbRecommendations.setAdapter(mPersonalTmdbRecommendationAdapter = new TmdbRecommendationAdapter());
        mPersonalTmdbRecommendationAdapter.setOnItemClickListener(this::onPersonalRecommendationClick);
        mBinding.tmdbPersonalTmdbRecommendations.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dx > 0 && isNearRecommendationRowEnd(recyclerView)) loadMoreNativePersonalRecommendations(true);
            }
        });
        mBinding.tmdbPersonalDoubanRecommendations.setHasFixedSize(true);
        mBinding.tmdbPersonalDoubanRecommendations.setItemAnimator(null);
        mBinding.tmdbPersonalDoubanRecommendations.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.tmdbPersonalDoubanRecommendations.setAdapter(mPersonalDoubanRecommendationAdapter = new TmdbRecommendationAdapter());
        mPersonalDoubanRecommendationAdapter.setOnItemClickListener(this::onPersonalRecommendationClick);
        mBinding.tmdbPersonalDoubanRecommendations.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dx > 0 && isNearRecommendationRowEnd(recyclerView)) loadMoreNativePersonalRecommendations(false);
            }
        });
        mBinding.tmdbPersonalAiRecommendations.setHasFixedSize(true);
        mBinding.tmdbPersonalAiRecommendations.setItemAnimator(null);
        mBinding.tmdbPersonalAiRecommendations.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.tmdbPersonalAiRecommendations.setAdapter(mPersonalAiRecommendationAdapter = new TmdbRecommendationAdapter());
        mPersonalAiRecommendationAdapter.setOnItemClickListener(this::onPersonalRecommendationClick);
        mPersonalAiRecommendationAdapter.setOnItemLongClickListener(item -> {
            com.fongmi.android.tv.ui.dialog.AiRecommendationInfoDialog.show(this, item);
            return true;
        });
        mBinding.episodeGroup.setHasFixedSize(true);
        mBinding.episodeGroup.setItemAnimator(null);
        mBinding.episodeGroup.setAdapter(mEpisodeGroupAdapter = new EpisodeGroupAdapter(this));
        mEpisodeSpanCount = getEpisodeSpanCount();
        mBinding.episode.setNestedScrollingEnabled(false);
        mBinding.episode.setHasFixedSize(false);
        mBinding.episode.setItemAnimator(null);
        mBinding.episode.setLayoutManager(new GridLayoutManager(this, mEpisodeSpanCount));
        mBinding.episode.addItemDecoration(mEpisodeDecoration = new SpaceItemDecoration(mEpisodeSpanCount, 8));
        mBinding.episode.setAdapter(mEpisodeAdapter = new EpisodeAdapter(this, ViewType.GRID));
        installEpisodeLongPressFallback();
        mBinding.episode.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                syncEpisodeGroupByScroll();
            }
        });
        mBinding.quality.setHasFixedSize(true);
        mBinding.quality.setItemAnimator(null);
        mBinding.quality.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.quality.setAdapter(mQualityAdapter = new QualityAdapter(this));
        mBinding.control.parse.setHasFixedSize(true);
        mBinding.control.parse.setItemAnimator(null);
        mBinding.control.parse.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.control.parse.setAdapter(mParseAdapter = new ParseAdapter(this, ViewType.DARK));
    }

    private void installEpisodeLongPressFallback() {
        Handler handler = new Handler(Looper.getMainLooper());
        int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        mBinding.episode.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            private Runnable pending;
            private float downX;
            private float downY;

            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent event) {
                handle(event);
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent event) {
                handle(event);
            }

            private void handle(MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getX();
                        downY = event.getY();
                        View child = mBinding.episode.findChildViewUnder(downX, downY);
                        if (child == null || mEpisodeAdapter == null) return;
                        int position = mBinding.episode.getChildAdapterPosition(child);
                        if (position == RecyclerView.NO_POSITION || position >= mEpisodeAdapter.getItems().size()) return;
                        pending = () -> {
                            if (mEpisodeAdapter == null) return;
                            int current = mBinding.episode.getChildAdapterPosition(child);
                            if (current == RecyclerView.NO_POSITION || current >= mEpisodeAdapter.getItems().size()) return;
                            EpisodeAdapter.showTitlePopup(child, mEpisodeAdapter.getItems().get(current));
                        };
                        handler.postDelayed(pending, ViewConfiguration.getLongPressTimeout());
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getX() - downX) > slop || Math.abs(event.getY() - downY) > slop) clear();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        clear();
                        break;
                    default:
                        break;
                }
            }

            private void clear() {
                if (pending == null) return;
                handler.removeCallbacks(pending);
                pending = null;
            }
        });
    }

    private int getEpisodeGridSpanCount() {
        if (ResUtil.isPad()) return ResUtil.isLand(this) ? 4 : 3;
        return ResUtil.isLand(this) ? 3 : 2;
    }

    private int getEpisodeSpanCount() {
        if (ResUtil.isLand(this)) return 6;
        return ResUtil.isPad() ? 6 : 4;
    }

    private void setVideoView() {
        mBinding.control.action.danmaku.setVisibility(DanmakuSetting.isLoad() ? View.VISIBLE : View.GONE);
        mBinding.control.action.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
        setupActionButtons();
        mBinding.video.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> mPiP.update(this, view));
        setPlayer();
    }

    private void setPlayer() {
        mBinding.control.action.player.setText(service() == null ? ResUtil.getStringArray(R.array.select_player)[PlayerSetting.getPlayer()] : player().getPlayerText());
    }

    private void setupActionButtons() {
        mActionButtons = new HashMap<>();
        addActionButton(PlayerButtonSetting.PLAYER, mBinding.control.action.player);
        addActionButton(PlayerButtonSetting.DECODE, mBinding.control.action.decode);
        addActionButton(PlayerButtonSetting.SPEED, mBinding.control.action.speed);
        addActionButton(PlayerButtonSetting.SCALE, mBinding.control.action.scale);
        addActionButton(PlayerButtonSetting.LUT, mBinding.control.action.lut);
        addActionButton(PlayerButtonSetting.RESET, mBinding.control.action.reset);
        addActionButton(PlayerButtonSetting.REPEAT, mBinding.control.action.repeat);
        addActionButton(PlayerButtonSetting.TEXT, mBinding.control.action.text);
        addActionButton(PlayerButtonSetting.AUDIO, mBinding.control.action.audio);
        addActionButton(PlayerButtonSetting.VIDEO, mBinding.control.action.video);
        addActionButton(PlayerButtonSetting.OPENING, mBinding.control.action.opening);
        addActionButton(PlayerButtonSetting.ENDING, mBinding.control.action.ending);
        addActionButton(PlayerButtonSetting.DANMAKU, mBinding.control.action.danmaku);
        addActionButton(PlayerButtonSetting.TITLE, mBinding.control.action.title);
        addActionButton(PlayerButtonSetting.PREV, mBinding.control.action.prev);
        addActionButton(PlayerButtonSetting.NEXT, mBinding.control.action.next);
        addActionButton(PlayerButtonSetting.EPISODES, mBinding.control.action.episodes);
        PlayerButtonSetting.applyOrder(mBinding.control.action.container, mActionButtons);
    }

    private void addActionButton(String id, View view) {
        mActionButtons.put(id, view);
    }

    private void applyActionButtonVisibility() {
        if (mActionButtons != null) PlayerButtonSetting.applyVisibility(mActionButtons);
    }

    private void setVideoView(boolean isInPictureInPictureMode) {
        if (isInPictureInPictureMode) {
            mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        } else {
            mBinding.video.setLayoutParams(mFrameParams);
            restoreContextWall();
        }
    }

    private void setAnimator() {
        mAnimator = new ValueAnimator();
        mAnimator.setInterpolator(new DecelerateInterpolator());
        mAnimator.addUpdateListener(animation -> {
            if (isLand() || isFullscreen() || isInPictureInPictureMode() || mFusionChromeApplied) return;
            mFrameParams.height = (int) animation.getAnimatedValue();
            mBinding.video.setLayoutParams(mFrameParams);
        });
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

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observeForever(mObserveDetail);
        mViewModel.getPlayer().observeForever(mObservePlayer);
        mViewModel.getSearch().observeForever(mObserveSearch);
    }

    private void checkId() {
        if (getId().startsWith("push://")) getIntent().putExtra("key", SiteApi.PUSH).putExtra("id", getId().substring(7));
        if (getId().isEmpty() || getId().startsWith("msearch:")) setEmpty(false);
        else getDetail();
    }

    private void checkLand() {
        if (isPort() && ResUtil.isLand(this)) enterFullscreen();
    }

    private void getDetail() {
        detailStartTime = System.currentTimeMillis();
        detailHealthRecorded = false;
        SpiderDebug.log("video-flow", "detail start key=%s id=%s name=%s", getKey(), getId(), getName());

        // 显示加载指示器
        mBinding.progressLayout.showProgress();

        mViewModel.detailContent(getKey(), getId());
    }

    private void getDetail(Vod item) {
        revealManualSearch = false;
        if (!isAutoMode()) mViewModel.stopSearch();
        saveHistory();
        getIntent().putExtra("key", item.getSiteKey());
        getIntent().putExtra("pic", item.getPic());
        getIntent().putExtra("id", item.getId());
        mBinding.swipeLayout.setRefreshing(true);
        mBinding.swipeLayout.setEnabled(false);
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
        mBinding.swipeLayout.setRefreshing(false);
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
        showError(getString(R.string.error_detail));
        mBinding.swipeLayout.setEnabled(true);
        mBinding.progressLayout.showEmpty();
    }

    private void setDetail(Vod item) {
        mVod = item;
        item.checkPic(getPic());
        item.checkName(getName());
        item.checkContent(getContent());
        boolean tmdbMode = hasTmdbDetailAdapter();
        mTmdbFallbackToNative = false;
        mTmdbContentLoaded = false;
        mTmdbAutoDialogShown = false;
        setOriginalEnhancedActionVisibility(tmdbMode && Setting.isOriginalEnhancedDetailPage());
        if (tmdbMode) {
            hideTmdbHeader();
            setNativeDetailInfoVisible(false);
            applyTmdbTabletVideoLayoutIfNeeded();
            mBinding.quick.setVisibility(View.GONE);
            mBinding.search.setVisibility(View.GONE);
            if (mBinding.videoShadow != null) mBinding.videoShadow.setVisibility(View.GONE);
            mBinding.progressLayout.showProgress();
        } else {
            restoreDefaultVideoLayout();
            restoreFlagAndEpisodeFromTmdb();
            setNativeDetailInfoVisible(true);
            mBinding.search.setVisibility(View.VISIBLE);
            if (mBinding.videoShadow != null) mBinding.videoShadow.setVisibility(View.VISIBLE);
            android.util.Log.d("VideoActivity", "setDetail - 调用 showContent()");
            mBinding.progressLayout.showContent();
        }

        // 显示内容容器（默认隐藏以显示加载指示器）
        ViewGroup scrollContainer = (ViewGroup) mBinding.scroll.getChildAt(0);
        if (!tmdbMode) scrollContainer.setVisibility(View.VISIBLE);

        // TMDB 集数处理：排序和应用标题
        if (isIntentTmdbPlayback()) com.fongmi.android.tv.utils.TmdbEpisodeSorter.sort(item);
        applyTmdbEpisodeTitles(item);

        // TMDB 模式下隐藏原生标题
        if (tmdbMode) {
            mBinding.name.setVisibility(View.GONE);
        } else {
            mBinding.name.setText(item.getName());
            mBinding.name.setVisibility(View.VISIBLE);
        }
        mFlagAdapter.addAll(item.getFlags());
        App.removeCallbacks(mR4);
        checkHistory(item);
        checkFlag(item);
        checkKeepImg();
        updateTmdbKeepState();
        setText(item);
        updateKeep();
        if (tmdbMode) hideNativePersonalRecommendations();
        else loadNativePersonalRecommendations(item);

        // TMDB 增强：全局开关启用或 Intent 传入 TmdbItem 时触发
        if (mTmdbUIAdapter != null && mTmdbUIAdapter.isReady()) {
            com.fongmi.android.tv.bean.TmdbItem tmdbItem = getTmdbItem();
            if (tmdbItem != null) {
                // 直接使用传入的 TmdbItem
                mTmdbUIAdapter.load(tmdbItem, item);
            } else {
                // 自动搜索匹配
                mTmdbUIAdapter.autoMatch(item.getName(), item);
            }
        }
    }

    private void setText(Vod item) {
        setText(mBinding.site, R.string.detail_site, getSite().getName());

        // 非 TMDB 模式才填充原生字段
        // 基于 TMDB 开关和配置是否就绪
        boolean tmdbMode = shouldUseTmdbDetailLayout();
        if (!tmdbMode) {
            restoreDefaultVideoLayout();
            setNativeDetailInfoVisible(true);
            setText(mBinding.director, R.string.detail_director, item.getDirector());
            setText(mBinding.actor, R.string.detail_actor, item.getActor());
            setText(mBinding.remark, 0, item.getRemarks());
            setOther(mBinding.other, item);
            setText(mBinding.content, 0, item.getContent());
        } else {
            applyTmdbTabletVideoLayoutIfNeeded();
            bindTmdbTabletTopSummary(item);
        }
        applyFusionNativeTextColors();
    }

    private boolean shouldUseTmdbTabletWideLayout() {
        return canUseTmdbTabletWideLayout() && !isFullscreen() && !isInPictureInPictureMode();
    }

    private boolean canUseTmdbTabletWideLayout() {
        return isLand() && ResUtil.isPad() && shouldUseTmdbDetailLayout() && !Setting.isFusionDetailPage() && mDefaultFrameParams != null;
    }

    private void applyTmdbTabletVideoLayoutIfNeeded() {
        if (!canUseTmdbTabletWideLayout()) {
            restoreDefaultVideoLayout();
            return;
        }
        if (isFullscreen() || isInPictureInPictureMode()) return;
        int side = ResUtil.dp2px(TMDB_TABLET_PLAYER_SIDE_MARGIN_DP);
        int gutter = ResUtil.dp2px(TMDB_TABLET_PLAYER_GUTTER_DP);
        int minPlayerWidth = ResUtil.dp2px(TMDB_TABLET_PLAYER_MIN_WIDTH_DP);
        int maxPlayerWidth = ResUtil.dp2px(TMDB_TABLET_PLAYER_MAX_WIDTH_DP);
        int minSummaryWidth = ResUtil.dp2px(TMDB_TABLET_SUMMARY_MIN_WIDTH_DP);
        int screenWidth = ResUtil.getScreenWidth(this);
        int available = screenWidth - side * 2 - gutter;
        if (available < minPlayerWidth + minSummaryWidth) {
            restoreDefaultVideoLayout();
            return;
        }
        int playerWidth = Math.min(maxPlayerWidth, Math.round(screenWidth * 0.48f));
        playerWidth = Math.max(minPlayerWidth, Math.min(playerWidth, available - minSummaryWidth));
        int playerHeight = playerWidth * 9 / 16;

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(playerWidth, playerHeight);
        params.addRule(RelativeLayout.BELOW, R.id.statusBar);
        params.setMargins(side, ResUtil.dp2px(TMDB_TABLET_PLAYER_TOP_MARGIN_DP), gutter, 0);
        mFrameParams = params;
        mFrameHeight = playerHeight;
        mTmdbTabletLayoutApplied = true;
        mBinding.video.setLayoutParams(params);
    }

    private void restoreDefaultVideoLayout() {
        if (!mTmdbTabletLayoutApplied || mFusionChromeApplied || mDefaultFrameParams == null) return;
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(mDefaultFrameParams);
        if (sameFrameParams(mFrameParams, params)) {
            mTmdbTabletLayoutApplied = false;
            return;
        }
        mFrameParams = params;
        mFrameHeight = params.height;
        mTmdbTabletLayoutApplied = false;
        if (isFullscreen() || isInPictureInPictureMode()) return;
        mBinding.video.setLayoutParams(params);
    }

    private boolean sameFrameParams(ViewGroup.LayoutParams current, RelativeLayout.LayoutParams target) {
        if (!(current instanceof RelativeLayout.LayoutParams params)) return false;
        return params.width == target.width
                && params.height == target.height
                && params.leftMargin == target.leftMargin
                && params.topMargin == target.topMargin
                && params.rightMargin == target.rightMargin
                && params.bottomMargin == target.bottomMargin;
    }

    private void bindTmdbTabletTopSummary(Vod item) {
        if (!shouldUseTmdbDetailLayout()) return;
        setNativeDetailInfoVisible(false);
        if (!shouldUseTmdbTabletWideLayout()) return;
        setPlainText(mBinding.name, item.getName());
        setPlainText(mBinding.remark, item.getRemarks());
        setPlainText(mBinding.site, getString(R.string.detail_site, getSite().getName()));
        setOther(mBinding.other, item);
        mBinding.director.setVisibility(View.GONE);
        mBinding.actor.setVisibility(View.GONE);
        mBinding.contentLayout.setVisibility(View.GONE);
    }

    private void setPlainText(TextView view, String text) {
        String value = Objects.toString(text, "");
        view.setText(value);
        view.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void setNativeDetailInfoVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        mBinding.name.setVisibility(visibility);
        mBinding.remark.setVisibility(visibility);
        mBinding.site.setVisibility(visibility);
        mBinding.other.setVisibility(visibility);
        mBinding.director.setVisibility(visibility);
        mBinding.actor.setVisibility(visibility);
        mBinding.contentLayout.setVisibility(visibility);
    }

    private void setOriginalEnhancedActionVisibility(boolean hide) {
        mBinding.shortDisplay.setVisibility(hide ? View.GONE : View.VISIBLE);
    }

    private void setText(TextView view, int resId, String text) {
        if (TextUtils.isEmpty(text) && !TextUtils.isEmpty(view.getText())) return;
        view.setText(Sniffer.buildClickable(resId > 0 ? getString(resId, text) : text, this::clickableSpan), TextView.BufferType.SPANNABLE);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        if (view == mBinding.content) setContentVisible();
        view.setLinkTextColor(Setting.isFusionDetailPage() && isFusionLightTheme() ? 0xFF1D8F5A : Color.WHITE);
        CustomMovement.bind(view);
    }

    private void setContentVisible() {
        // TMDB 模式下不显示原生简介区域
        boolean tmdbMode = shouldUseTmdbDetailLayout();
        if (tmdbMode) {
            mBinding.contentLayout.setVisibility(View.GONE);
        } else {
            mBinding.contentLayout.setVisibility(mBinding.content.getVisibility());
        }
    }

    private ClickableSpan clickableSpan(Result result) {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                FolderActivity.start(getActivity(), getKey(), result);
                ((TextView) view).setMaxLines(Integer.MAX_VALUE);
                setRedirect(true);
            }
        };
    }

    private void setOther(TextView view, Vod item) {
        StringBuilder sb = new StringBuilder();
        if (!item.getYear().isEmpty()) sb.append(getString(R.string.detail_year, item.getYear())).append("  ");
        if (!item.getArea().isEmpty()) sb.append(getString(R.string.detail_area, item.getArea())).append("  ");
        if (!item.getTypeName().isEmpty()) sb.append(getString(R.string.detail_type, item.getTypeName())).append("  ");
        view.setVisibility(sb.length() == 0 ? View.GONE : View.VISIBLE);
        view.setText(Util.substring(sb.toString(), 2));
    }

    private void applyTmdbEpisodeTitles(Vod vod) {
        java.util.Map<Integer, String> titles = getEpisodeTitles();
        android.util.Log.d("VideoActivity", "applyTmdbEpisodeTitles - 集数标题数量: " + titles.size());
        if (vod == null || titles.isEmpty() || vod.getFlags() == null) return;
        for (Flag flag : vod.getFlags()) {
            for (Episode episode : flag.getEpisodes()) {
                String title = titles.get(episode.getNumber());
                if (android.text.TextUtils.isEmpty(title)) continue;
                String displayName = EpisodeTitleFormatter.withSourceFileSize(episode.getName(), EpisodeTitleFormatter.formatTmdbTitle(episode.getNumber(), title), Setting.isTmdbEpisodeFileSize());
                if (android.text.TextUtils.equals(episode.getDisplayName(), displayName)) continue;
                episode.setDisplayName(displayName);
                android.util.Log.d("VideoActivity", "应用标题: " + episode.getNumber() + " -> " + title);
            }
        }
    }

    private java.util.Map<Integer, String> getEpisodeTitles() {
        java.util.Map<Integer, String> titles = new java.util.HashMap<>();
        java.util.ArrayList<String> values = getIntent().getStringArrayListExtra("tmdb_episode_titles");
        android.util.Log.d("VideoActivity", "getEpisodeTitles - values: " + (values != null ? values.size() : "null"));
        if (values == null) return titles;
        for (String value : values) {
            String[] parts = value.split("\t", 2);
            if (parts.length != 2 || android.text.TextUtils.isEmpty(parts[1])) continue;
            try {
                titles.put(Integer.parseInt(parts[0]), parts[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        return titles;
    }

    private boolean isIntentTmdbPlayback() {
        java.util.ArrayList<String> values = getIntent().getStringArrayListExtra("tmdb_episode_titles");
        boolean result = values != null && !values.isEmpty();
        android.util.Log.d("VideoActivity", "isIntentTmdbPlayback: " + result);
        return result;
    }

    private void getPlayer(Flag flag, Episode episode) {
        mBinding.control.title.setText(getPlaybackControlTitle(episode));
        playerStartTime = System.currentTimeMillis();
        beginPlayHealth();
        SpiderDebug.log("video-flow", "player start key=%s flag=%s episode=%s url=%s", getKey(), flag.getFlag(), episode.getName(), episode.getUrl());
        mViewModel.playerContent(getKey(), flag.getFlag(), episode.getUrl());
        mBinding.control.title.setSelected(true);
        updateHistory(episode);
        showProgress();
    }

    private void setPlayer(Result result) {
        if (isFinishing() || isDestroyed()) return;
        SpiderDebug.log("video-flow", "player finish cost=%dms useParse=%s multi=%s msg=%s", System.currentTimeMillis() - playerStartTime, result.shouldUseParse(), result.getUrl().isMulti(), result.getMsg());
        mQualityAdapter.addAll(result);
        mQualityAdapter.setPosition(mQualityAdapter.getPosition());
        setUseParse(result.shouldUseParse());
        mBinding.swipeLayout.setRefreshing(false);
        setQualityVisible(result.getUrl().isMulti());
        if (result.hasArtwork() && !shouldKeepPushArtwork()) setArtwork(result.getArtwork());
        if (result.hasPosition()) mHistory.setPosition(result.getPosition());
        if (result.hasDesc()) setText(mBinding.content, 0, result.getDesc());
        mBinding.control.parse.setVisibility(isUseParse() ? View.VISIBLE : View.GONE);
        if (redirectToAudioIfNeeded(result)) return;
        startPlayer(getHistoryKey(), result, isUseParse(), getSite().getTimeout(), buildMetadata());
        subtitlePlaybackSession.onPlaybackStarted(this, result);
        if (DanmakuApi.canSearch()) DanmakuApi.search(mHistory.getVodName(), getEpisode().getName(), danmaku -> {
            if (DanmakuSetting.isSpiderFirst() && !result.getDanmaku().isEmpty()) player().addDanmaku(danmaku);
            else player().setDanmaku(danmaku);
        });
    }

    private boolean redirectToAudioIfNeeded(Result result) {
        List<Episode> episodes = getCurrentEpisodeItems();
        boolean handled = com.fongmi.android.tv.content.ContentDispatcher.dispatchResult(this, getHistoryKey(), getKey(), getFlag().getFlag(), mHistory.getVodName(), mHistory.getVodPic(), episodes, getSelectedEpisodePosition(episodes), result, getSite().getTimeout());
        if (handled) finish();
        return handled;
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
        if (item.isSelected()) return;
        mFlagAdapter.setSelected(item);
        scrollToPosition(mBinding.flag, mFlagAdapter.getPosition());
        setEpisodeAdapter(item.getEpisodes());
        scrollEpisodeToSelected();
        setQualityVisible(false);
        seamless(item);
    }

    @Override
    public void onItemClick(Episode item) {
        if (shouldEnterFullscreen(item)) return;
        mFlagAdapter.toggle(item);
        setEpisodeAdapter(getFlag().getEpisodes());
        if (isFullscreen()) Notify.show(getString(R.string.play_ready, item.getName()));
        onRefresh();
    }

    @Override
    public void onItemClick(EpisodeGroupAdapter.Group item) {
        mEpisodeGroupAdapter.setSelected(item);
        if (shouldUseEpisodeRangePaging(getCurrentEpisodeItems())) {
            setEpisodeItems(getCurrentEpisodeItems());
            mBinding.episode.post(() -> scrollEpisodeToPosition(0));
        } else {
            scrollEpisodeToPosition(item.start);
        }
        scrollToPosition(mBinding.episodeGroup, mEpisodeGroupAdapter.getPosition());
    }

    @Override
    public void onItemClick(Result result) {
        updateActionQuality(result);
        beginPlayHealth();
        startPlayer(getHistoryKey(), result, isUseParse(), getSite().getTimeout(), buildMetadata());
        subtitlePlaybackSession.onPlaybackStarted(this, result);
    }

    @Override
    public void onItemClick(Vod item) {
        setAutoMode(false);
        applySearchArtwork(item);
        getDetail(item);
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

    private void setEpisodeAdapter(List<Episode> items) {
        int size = items.size();
        boolean useTmdbCard = shouldUseTmdbEpisodeCards(items);
        mEpisodeAdapter.setUseTmdbCard(useTmdbCard);
        mBinding.control.action.episodes.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.action.next.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.action.prev.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        applyActionButtonVisibility();
        mBinding.control.next.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.prev.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        mBinding.reverse.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        boolean showViewMode = useTmdbCard && size > 1;
        if (showViewMode) mEpisodeGridMode = Setting.getTmdbEpisodeGridMode();
        if (!showViewMode) mEpisodeGridMode = true;
        updateEpisodeViewModeButton();
        if (mBinding.episodeViewMode != null) mBinding.episodeViewMode.setVisibility(showViewMode ? View.VISIBLE : View.GONE);
        mBinding.episode.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.more.setVisibility(View.GONE);
        List<EpisodeGroupAdapter.Group> groups = EpisodeGroupAdapter.build(size, getSelectedEpisodePosition(items), mHistory != null && mHistory.isRevSort());
        mEpisodeGroupAdapter.addAll(groups);
        updateEpisodeGroupVisibility();
        setEpisodeItems(items);
        mBinding.episode.post(this::updateEpisodeViewportHeight);
    }

    private void updateEpisodeGroupVisibility() {
        if (mEpisodeGroupAdapter == null) return;
        boolean visible = EpisodeDisplayPolicy.shouldShowEpisodeGroup(mEpisodeGroupAdapter.getItemCount(), shouldUseTmdbDetailLayout());
        mBinding.episodeGroup.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void syncEpisodeGroupByScroll() {
        if (shouldUseEpisodeRangePaging(getCurrentEpisodeItems())) return;
        RecyclerView.LayoutManager manager = mBinding.episode.getLayoutManager();
        if (!(manager instanceof GridLayoutManager)) return;
        int position = getEpisodeGroupSyncPosition((GridLayoutManager) manager);
        if (position == RecyclerView.NO_POSITION) return;
        selectEpisodeGroupByPosition(position);
    }

    private int getEpisodeGroupSyncPosition(GridLayoutManager manager) {
        if (!mBinding.episode.canScrollVertically(1) && mBinding.episode.canScrollVertically(-1)) {
            return manager.findLastVisibleItemPosition();
        }
        return manager.findFirstVisibleItemPosition();
    }

    private void selectEpisodeGroupByPosition(int position) {
        if (mEpisodeGroupAdapter == null || mEpisodeGroupAdapter.isEmpty()) return;
        int current = mEpisodeGroupAdapter.getPosition();
        List<EpisodeGroupAdapter.Group> groups = mEpisodeGroupAdapter.getItems();
        for (int i = 0; i < groups.size(); i++) {
            EpisodeGroupAdapter.Group group = groups.get(i);
            if (position < group.start || position >= group.end) continue;
            if (i != current) {
                mEpisodeGroupAdapter.setSelected(group);
                mBinding.episodeGroup.scrollToPosition(i);
            }
            return;
        }
    }

    private void scrollEpisodeToPosition(int position) {
        RecyclerView.LayoutManager manager = mBinding.episode.getLayoutManager();
        if (manager instanceof GridLayoutManager) {
            int rowStart = getEpisodeRowStart((GridLayoutManager) manager, position);
            int offset = rowStart >= ((GridLayoutManager) manager).getSpanCount() ? -ResUtil.dp2px(4) : 0;
            ((GridLayoutManager) manager).scrollToPositionWithOffset(rowStart, offset);
        }
        else mBinding.episode.scrollToPosition(position);
    }

    private void scrollEpisodeToSelected() {
        mBinding.episode.post(() -> scrollEpisodeToPosition(mEpisodeAdapter.getPosition()));
    }

    private int getEpisodeRowStart(GridLayoutManager manager, int position) {
        int span = Math.max(1, manager.getSpanCount());
        return Math.max(0, position - position % span);
    }

    private void setEpisodeItems(List<Episode> items) {
        boolean useTmdbCard = shouldUseTmdbEpisodeCards(items);
        List<Episode> displayItems = getEpisodeDisplayItems(items);
        if (!useTmdbCard) mEpisodeGridMode = true;
        updateEpisodeFallbackStillUrl();
        mEpisodeAdapter.setUseTmdbCard(useTmdbCard);
        mEpisodeAdapter.setViewType(useTmdbCard && !mEpisodeGridMode ? ViewType.HORI : ViewType.GRID);
        updateEpisodeLayout(displayItems, useTmdbCard);
        mEpisodeAdapter.addAll(displayItems);
        if (shouldUseEpisodeRangePaging(items)) scrollToPosition(mBinding.episodeGroup, mEpisodeGroupAdapter.getPosition());
        else selectEpisodeGroupByPosition(mEpisodeAdapter.getPosition());
        updateEpisodeViewModeButton();
        mBinding.episode.post(this::updateEpisodeViewportHeight);
    }

    private void updateEpisodeLayout(List<Episode> items) {
        updateEpisodeLayout(items, shouldUseTmdbEpisodeCards(items));
    }

    private void updateEpisodeLayout(List<Episode> items, boolean useTmdbCard) {
        if (useTmdbCard && !mEpisodeGridMode) {
            RecyclerView.LayoutManager manager = mBinding.episode.getLayoutManager();
            if (!(manager instanceof LinearLayoutManager) || manager instanceof GridLayoutManager || ((LinearLayoutManager) manager).getOrientation() != LinearLayoutManager.HORIZONTAL) {
                mBinding.episode.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            }
            updateEpisodeDecoration(new SpaceItemDecoration(8));
            return;
        }
        int span = getEpisodeSpan(items, useTmdbCard);
        mEpisodeSpanCount = span;
        RecyclerView.LayoutManager manager = mBinding.episode.getLayoutManager();
        if (!(manager instanceof GridLayoutManager) || ((GridLayoutManager) manager).getSpanCount() != mEpisodeSpanCount) {
            mBinding.episode.setLayoutManager(new GridLayoutManager(this, mEpisodeSpanCount));
        }
        updateEpisodeDecoration(new SpaceItemDecoration(mEpisodeSpanCount, 8));
    }

    private void updateEpisodeDecoration(SpaceItemDecoration decoration) {
        if (mEpisodeDecoration != null) mBinding.episode.removeItemDecoration(mEpisodeDecoration);
        mBinding.episode.addItemDecoration(mEpisodeDecoration = decoration);
    }

    private int getEpisodeSpan(List<Episode> items) {
        return getEpisodeSpan(items, shouldUseTmdbEpisodeCards(items));
    }

    private int getEpisodeSpan(List<Episode> items, boolean useTmdbCard) {
        if (useTmdbCard) return getEpisodeGridSpanCount();
        EpisodeTitleCompact.apply(items);
        int maxLen = 0;
        for (Episode item : items) maxLen = Math.max(maxLen, item.getDisplayName().length());
        if (maxLen >= 12) return PlayerSetting.getEpisodeColumn();
        int ideal = maxLen >= 10 ? 130 : maxLen >= 7 ? 104 : 80;
        int width = mBinding.episode.getWidth() > 0 ? mBinding.episode.getWidth() : ResUtil.getScreenWidth(this) - ResUtil.dp2px(32);
        int span = width / ResUtil.dp2px(ideal);
        return Math.max(2, Math.min(getEpisodeSpanCount(), span));
    }

    private List<Episode> getEpisodeDisplayItems(List<Episode> items) {
        EpisodeGroupAdapter.Group group = getSelectedEpisodeGroup();
        if (!shouldUseEpisodeRangePaging(items) || group == null) return items;
        return EpisodeRangePolicy.slice(items, new EpisodeRangePolicy.Range(group.name, group.start, group.end, group.selected));
    }

    private boolean shouldUseEpisodeRangePaging(List<Episode> items) {
        return shouldUseTmdbDetailLayout() && items != null && mEpisodeGroupAdapter != null && mEpisodeGroupAdapter.getItemCount() > 1;
    }

    private EpisodeGroupAdapter.Group getSelectedEpisodeGroup() {
        if (mEpisodeGroupAdapter == null || mEpisodeGroupAdapter.isEmpty()) return null;
        int position = Math.max(0, Math.min(mEpisodeGroupAdapter.getPosition(), mEpisodeGroupAdapter.getItemCount() - 1));
        return mEpisodeGroupAdapter.getItems().get(position);
    }

    private List<Episode> getCurrentEpisodeItems() {
        if (mFlagAdapter != null && !mFlagAdapter.isEmpty()) return getFlag().getEpisodes();
        return mEpisodeAdapter == null ? List.of() : mEpisodeAdapter.getItems();
    }

    private int getSelectedEpisodePosition(List<Episode> items) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).isSelected()) return i;
        return 0;
    }

    private void syncSelectedEpisode(Flag flag) {
        if (flag == null || mHistory == null) return;
        Episode episode = flag.find(mHistory.getEpisode(), false);
        if (episode != null) flag.toggle(true, episode);
    }

    private int getEpisodeCount() {
        return mFlagAdapter == null || mFlagAdapter.isEmpty() ? mEpisodeAdapter.getItemCount() : getFlag().getEpisodes().size();
    }

    private void seamless(Flag flag) {
        Episode episode = flag.find(mHistory.getEpisode(), getMark().isEmpty());
        setQualityVisible(episode != null && episode.isSelected() && mQualityAdapter.getItemCount() > 1);
        if (episode == null || episode.isSelected()) return;
        mHistory.setVodRemarks(episode.getName());
        mHistory.setEpisodeUrl(episode.getUrl());
        onItemClick(episode);
    }

    private void setQualityVisible(boolean visible) {
        mBinding.qualityText.setVisibility(visible ? View.VISIBLE : View.GONE);
        mBinding.quality.setVisibility(visible ? View.VISIBLE : View.GONE);
        mBinding.control.action.actionQuality.setVisibility(visible ? View.VISIBLE : View.GONE);
        updateActionQuality(mViewModel.getPlayer().getValue());
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

    private void reverseEpisode(boolean scroll) {
        mFlagAdapter.reverse();
        setEpisodeAdapter(getFlag().getEpisodes());
        if (scroll) scrollEpisodeToSelected();
    }

    private void onName() {
        String name = mBinding.name.getText().toString();
        Notify.show(getString(R.string.detail_search, name));
        showQuickSearch(name);
        initSearch(name, false);
    }

    private void onSearch() {
        onName();
    }

    private void onShortDisplay() {
        Setting.putCompactEpisodeTitle(!Setting.isCompactEpisodeTitle());
        setShortDisplay();
        refreshEpisodeTitles();
    }

    private void setShortDisplay() {
        mBinding.shortDisplay.setSelected(Setting.isCompactEpisodeTitle());
    }

    private void onMore() {
        syncSelectedEpisode(getFlag());
        List<Episode> episodes = getFlag().getEpisodes();
        EpisodeGridDialog.create().reverse(mHistory.isRevSort()).episodes(episodes).tmdbCard(shouldUseTmdbEpisodeCards(episodes)).show(this);
    }

    private void onActor() {
        mBinding.actor.setMaxLines(mBinding.actor.getMaxLines() == 1 ? Integer.MAX_VALUE : 1);
    }

    private void onDirector() {
        mBinding.director.setMaxLines(mBinding.director.getMaxLines() == 1 ? Integer.MAX_VALUE : 1);
    }

    private void onContent() {
        CharSequence content = mBinding.content.getText();
        if (TextUtils.isEmpty(content)) return;
        VideoContentDialog.create().content(content).show(this);
    }

    private void showQuickSearch(String keyword) {
        mQuickSearchDialog = QuickSearchDialog.create()
                .title(getString(R.string.detail_search, keyword))
                .listener(this)
                .items(mQuickAdapter.getItems());
        mQuickSearchDialog.show(this);
    }

    private void onReverse() {
        mHistory.setRevSort(!mHistory.isRevSort());
        reverseEpisode(false);
    }

    private void toggleEpisodeViewMode() {
        if (mBinding.episodeViewMode == null || mBinding.episodeViewMode.getVisibility() != View.VISIBLE) {
            onEpisodes();
            return;
        }
        mEpisodeGridMode = !mEpisodeGridMode;
        Setting.putTmdbEpisodeGridMode(mEpisodeGridMode);
        setEpisodeItems(getCurrentEpisodeItems());
        scrollToPosition(mBinding.episode, mEpisodeAdapter.getPosition());
    }

    private void updateEpisodeViewModeButton() {
        if (mBinding.episodeViewMode == null) return;
        boolean switchToList = mEpisodeGridMode;
        mBinding.episodeViewMode.setImageResource(switchToList ? R.drawable.ic_site_list : R.drawable.ic_site_grid);
        mBinding.episodeViewMode.setContentDescription(getString(switchToList ? R.string.detail_episode_view_list_action : R.string.detail_episode_view_grid_action));
        applyTmdbPlaybackControlColors();
    }

    private boolean onChange() {
        checkSearch(true);
        return true;
    }

    private boolean onCopy() {
        Util.copy(mBinding.content.getText().toString());
        return true;
    }

    private void onBack() {
        if (isFullscreen() && isShortDramaSource()) finishShortDrama();
        else if (isFullscreen()) exitFullscreen();
        else finishPlayback();
    }

    private void onCast() {
        CastDialog.create().history(mHistory).video(new CastVideo(mBinding.name.getText().toString(), player().getUrl(), player().getPosition(), player().getHeaders())).fm(true).show(this);
    }

    private void onInfo() {
        InfoDialog.create().title(mBinding.control.title.getText()).headers(player().getHeaders()).url(player().getUrl()).show(this);
    }

    private void onKeep() {
        Keep keep = Keep.find(getHistoryKey());
        Notify.show(keep != null ? R.string.keep_del : R.string.keep_add);
        if (keep != null) keep.delete();
        else createKeep();
        checkKeepImg();
        updateTmdbKeepState();
    }

    private void onDisplay() {
        DisplayDialog.showPlayerOsd(this, () -> {
            if (mOsd != null) mOsd.start();
        });
    }

    private void checkPlay() {
        setR1Callback();
        if (player().isPlaying()) onPaused();
        else if (player().isEmpty()) onRefresh();
        else onPlay();
    }

    private void checkNext() {
        checkNext(true);
    }

    private void checkNext(boolean notify) {
        setR1Callback();
        Episode item = getAdjacentEpisode(1);
        if (!item.isSelected()) onItemClick(item);
        else if (notify) Notify.show(R.string.error_play_next);
    }

    private void checkPrev() {
        setR1Callback();
        Episode item = getAdjacentEpisode(-1);
        if (!item.isSelected()) onItemClick(item);
        else Notify.show(R.string.error_play_prev);
    }

    private Episode getAdjacentEpisode(int offset) {
        List<Episode> items = mFlagAdapter == null || mFlagAdapter.isEmpty() ? mEpisodeAdapter.getItems() : getFlag().getEpisodes();
        if (items.isEmpty()) return new Episode();
        int position = getSelectedEpisodePosition(items) + offset;
        position = Math.max(0, Math.min(position, items.size() - 1));
        return items.get(position);
    }

    private void onSetting() {
        ControlDialog.create().parent(mBinding).history(mHistory).parse(isUseParse()).player(player()).show(this);
    }

    private void onLock() {
        setLock(!isLock());
        setRequestedOrientation(getLockOrient());
        mKeyDown.setLock(isLock());
        checkLockImg();
        showControl();
    }

    private void onRotate() {
        setR1Callback();
        setRotate(!isRotate());
        setRequestedOrientation(ResUtil.isLand(this) ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    private void onFullscreen() {
        if (isFullscreen()) exitFullscreen();
        else enterFullscreen();
        showControl();
    }

    private void onTrack(View view) {
        TrackDialog.create().type(Integer.parseInt(view.getTag().toString())).player(player()).search(this::showSubtitleSearch).show(this);
        hideControl();
    }

    @Override
    public void onTrackPanel(int type) {
        TrackDialog.create().type(type).player(player()).search(this::showSubtitleSearch).show(this);
    }

    private void onTitle() {
        TitleDialog.create().player(player()).show(this);
        hideControl();
    }

    @Override
    public void onTitlePanel() {
        TitleDialog.create().player(player()).show(this);
    }

    private void onDanmaku() {
        DanmakuDialog.create().player(player()).show(this);
        hideControl();
    }

    @Override
    public void onDanmakuPanel() {
        DanmakuDialog.create().player(player()).show(this);
    }

    private void onDanmakuShow() {
        DanmakuSetting.putShow(!DanmakuSetting.isShow());
        checkDanmakuImg();
        showDanmaku();
    }

    private void onRepeat() {
        player().setRepeatOne(!player().isRepeatOne());
        mBinding.control.action.repeat.setSelected(player().isRepeatOne());
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        mBinding.control.action.repeat.setSelected(player().isRepeatOne());
    }

    private void onScale() {
        int index = getScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        if (mKeyDown.getScale() != 1.0f) mKeyDown.resetScale();
        else setScale(index == array.length - 1 ? 0 : ++index);
        setR1Callback();
    }

    private void onLut() {
        if (hasLutQuick()) {
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
        }
        else LutPanelDialog.create().player(player()).show(this);
        setR1Callback();
    }

    @Override
    public void onLutPanel() {
        if (isFullscreen() && hasLutQuick()) onLut();
        else LutPanelDialog.create().player(player()).show(this);
    }

    private boolean hasLutQuick() {
        return mBinding.lutQuick != null;
    }

    private void onLutChanged() {
        setLut();
    }

    @Override
    public void onLutImport() {
        if (!LutStore.hasUserDir()) {
            pendingLutImport = true;
            chooseLutDir();
            return;
        }
        chooseLutFile();
    }

    @Override
    public void onLutDir() {
        pendingLutImport = false;
        chooseLutDir();
    }

    private void chooseLutFile() {
        skipPausePiP = true;
        FileChooser.from(mLutFile).show("*/*", new String[]{"application/octet-stream", "text/*", "image/*", "*/*"});
    }

    private void chooseLutDir() {
        skipPausePiP = true;
        FileChooser.from(mLutDir).showDirectory();
    }

    @Override
    public void onLutSelected(LutPreset preset) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-ui", "activity select preset=%s enabledBefore=%s current=%s", preset == null ? "original" : preset.getId(), LutSetting.isEnabled(), LutSetting.getPresetId());
        LutSetting.select(preset);
        if (preset == null) player().applyLut(true);
        else player().applyLutPreview(true);
        setLut();
        setR1Callback();
    }

    private void onSpeed() {
        mBinding.control.action.speed.setText(player().addSpeed());
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
        if (mFlagAdapter.isEmpty()) return;
        if (mEpisodeAdapter.isEmpty()) return;
        getPlayer(getFlag(), getEpisode());
    }

    private boolean onResetToggle() {
        Setting.putReset(Math.abs(Setting.getReset() - 1));
        mBinding.control.action.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
        return true;
    }

    private void onDecode() {
        mClock.setCallback(null);
        player().toggleDecode();
        setR1Callback();
        setDecode();
    }

    private void onEnding() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetEnding(position, duration)) setEnding(duration - position);
        setR1Callback();
    }

    private boolean onEndingReset() {
        setR1Callback();
        setEnding(0);
        return true;
    }

    private void setEnding(long ending) {
        mHistory.setEnding(ending);
        mBinding.control.action.ending.setText(ending <= 0 ? getString(R.string.play_ed) : Util.timeMs(mHistory.getEnding()));
    }

    private void onOpening() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetOpening(position, duration)) setOpening(position);
        setR1Callback();
    }

    private boolean onOpeningReset() {
        setR1Callback();
        setOpening(0);
        return true;
    }

    private void setOpening(long opening) {
        mHistory.setOpening(opening);
        mBinding.control.action.opening.setText(opening <= 0 ? getString(R.string.play_op) : Util.timeMs(mHistory.getOpening()));
    }

    private void onEpisodes() {
        if (mFlagAdapter == null || mFlagAdapter.isEmpty() || mHistory == null) return;
        Flag flag = getFlag();
        syncSelectedEpisode(flag);
        EpisodeListDialog.create().flags(mFlagAdapter.getItems()).reverse(mHistory.isRevSort()).tmdbCard(shouldUseTmdbEpisodeCards(flag.getEpisodes())).show(this);
    }

    private void onChoose() {
        String[] kernel = ResUtil.getStringArray(R.array.select_player_kernel);
        String[] items = new String[kernel.length + 1];
        System.arraycopy(kernel, 0, items, 0, kernel.length);
        items[kernel.length] = "外调";
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setItems(items, (dialog, which) -> {
            if (which < kernel.length) {
                player().switchPlayerManually(which);
                setPlayer();
                setDecode();
            } else {
                PlayerHelper.choose(this, player().getUrl(), player().getHeaders(), player().isVod(), player().getPosition(), mBinding.control.title.getText());
                setRedirect(true);
            }
        }).show();
    }

    private void onPlayerKernel() {
        mClock.setCallback(null);
        onChoose();
        setR1Callback();
    }

    private boolean onPlayerKernelLong() {
        onPlayerKernel();
        return true;
    }

    private boolean onTextLong() {
        if (!player().haveTrack(C.TRACK_TYPE_TEXT)) return false;
        onSubtitleClick();
        return true;
    }

    private boolean onActionTouch(View v, MotionEvent e) {
        setR1Callback();
        return false;
    }

    private void onSwipeRefresh() {
        if (mBinding.progressLayout.isEmpty()) getDetail();
        else onRefresh();
    }

    private boolean shouldEnterFullscreen(Episode item) {
        boolean enter = !isFullscreen() && item.isSelected();
        if (enter) enterFullscreen();
        return enter;
    }

    private void enterFullscreen() {
        if (isFullscreen()) return;
        setFullscreen(true);
        if (isLand() && !player().isPortrait()) setTransition();
        mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        setRequestedOrientation(player().isPortrait() ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        mBinding.control.title.setVisibility(View.VISIBLE);
        setSizeText();
        setRotate(player().isPortrait());
        mKeyDown.resetScale();
        App.post(mR3, 2000);
        hideControl();
    }

    private void exitFullscreen() {
        if (!isFullscreen()) return;
        setFullscreen(false);
        if (isLand() && !player().isPortrait()) setTransition();
        setRequestedOrientation(isPort() ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        mBinding.episodeGroup.postDelayed(() -> scrollToPosition(mBinding.episodeGroup, mEpisodeGroupAdapter.getPosition()), 100);
        mBinding.episode.postDelayed(this::scrollEpisodeToSelected, 100);
        mBinding.control.title.setVisibility(View.INVISIBLE);
        setSizeText();
        mBinding.video.setLayoutParams(mFrameParams);
        mKeyDown.resetScale();
        App.post(mR3, 2000);
        setRotate(false);
        hideControl();
    }

    private void setTransition() {
        ChangeBounds transition = new ChangeBounds();
        transition.setDuration(150);
        ViewGroup parent = (ViewGroup) mBinding.video.getParent();
        TransitionManager.beginDelayedTransition(parent, transition);
    }

    private int getLockOrient() {
        if (isLock()) {
            return ResUtil.getScreenOrientation(this);
        } else if (isRotate()) {
            return ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT;
        } else if (isPort() && isAutoRotate()) {
            return ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
        } else {
            return ResUtil.isLand(this) ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT;
        }
    }

    private void showProgress() {
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
        App.post(mR2, 0);
        hideError();
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR2);
        Traffic.reset();
    }

    private void showError(String text) {
        mBinding.widget.error.setVisibility(View.VISIBLE);
        mBinding.widget.error.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.error.setText("");
    }

    private void showDanmaku() {
        player().setDanmakuEnabled(DanmakuSetting.isShow());
    }

    private void hideDanmaku() {
        player().setDanmakuEnabled(false);
    }

    private void showControl() {
        if (service() == null || isInPictureInPictureMode()) return;
        setOsdSuppressed(true);
        boolean shortDrama = isShortDramaSource();
        hideWidgetOverlay();
        mBinding.control.danmaku.setVisibility(isLock() || !player().haveDanmaku() ? View.GONE : View.VISIBLE);
        mBinding.control.setting.setVisibility(mHistory == null || isFullscreen() ? View.GONE : View.VISIBLE);
        mBinding.control.right.getRoot().setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.right.rotate.setVisibility(isFullscreen() && !isLock() ? View.VISIBLE : View.GONE);
        mBinding.control.fullscreen.setVisibility(isLock() || shortDrama ? View.GONE : View.VISIBLE);
        mBinding.control.keep.setVisibility(mHistory == null ? View.GONE : View.VISIBLE);
        mBinding.control.osdDiagnostics.setVisibility(PlayerSetting.isOsdDiagnostics() && !player().isEmpty() ? View.VISIBLE : View.GONE);
        mBinding.control.osdDiagnostics.setAlpha(mOsd != null && mOsd.isDiagnosticsVisible() ? 1f : 0.72f);
        mBinding.control.parse.setVisibility(isFullscreen() && isUseParse() ? View.VISIBLE : View.GONE);
        mBinding.control.action.getRoot().setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.right.lock.setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.info.setVisibility(player().isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.control.cast.setVisibility(isFullscreen() && mHistory != null && !player().isEmpty() ? View.VISIBLE : View.GONE);
        mBinding.control.display.setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.center.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.bottom.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.back.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.top.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        syncShortDramaControlLayout(shortDrama);
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        if (mOsd != null) mOsd.setControlsVisible(true);
        checkFullscreenImg();
        setR1Callback();
    }

    private void hideControl() {
        mBinding.control.getRoot().setVisibility(View.GONE);
        if (mOsd != null) mOsd.setControlsVisible(false);
        App.removeCallbacks(mR1);
        setOsdSuppressed(false);
    }

    private void setOsdSuppressed(boolean suppressed) {
        if (mOsd != null) mOsd.setSuppressed(suppressed);
    }

    private void onOsdDiagnostics() {
        if (mOsd == null) return;
        mOsd.toggleDiagnostics();
        hideControl();
    }

    private void hideWidgetOverlay() {
        mBinding.widget.seek.setVisibility(View.GONE);
        mBinding.widget.speed.clearAnimation();
        mBinding.widget.speed.setVisibility(View.GONE);
        mBinding.widget.bright.setVisibility(View.GONE);
        mBinding.widget.volume.setVisibility(View.GONE);
    }

    private void hideSheet() {
        getSupportFragmentManager().getFragments().stream().filter(fragment -> fragment instanceof BottomSheetDialogFragment).map(fragment -> (BottomSheetDialogFragment) fragment).forEach(BottomSheetDialogFragment::dismiss);
    }

    private void setTraffic() {
        Traffic.setSpeed(mBinding.progress.traffic);
        App.post(mR2, 1000);
    }

    private void setOrient() {
        if (isPort() && isAutoRotate()) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        if (isLand() && isAutoRotate()) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE);
    }

    private void setR1Callback() {
        App.post(mR1, Constant.INTERVAL_HIDE);
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
        if (!Setting.isPlaybackArtworkWall() && !Setting.isFusionDetailPage()) {
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

    private void restoreContextWall() {
        if (!Setting.isPlaybackArtworkWall()) return;
        String wall = getContextWall();
        if (TextUtils.isEmpty(wall)) {
            hideContextWall();
        } else if (Objects.equals(mContextWallUrl, wall) && mBinding.contextWall.getDrawable() != null) {
            resetContextWallAlpha();
            mBinding.contextWall.setBackgroundColor(Color.TRANSPARENT);
            mBinding.contextWall.setVisibility(View.VISIBLE);
        } else {
            mContextWallUrl = "";
            setContextWall(wall);
        }
    }

    private void hideContextWall() {
        resetContextWallAlpha();
        mBinding.contextWall.setImageDrawable(null);
        mBinding.contextWall.setBackgroundColor(0x00000000);
        mBinding.contextWall.setVisibility(View.GONE);
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
        if (mEpisodeAdapter != null) mEpisodeAdapter.setFallbackStillUrl(getEpisodeFallbackStillUrl());
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
        setText(mBinding.content, 0, getContent());
        if (!getPic().isEmpty()) setArtwork(getPic());
        else if (!getWallPic().isEmpty()) setContextWall(getWallPic());
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

    private void checkControl() {
        if (isVisible(mBinding.control.getRoot())) showControl();
    }

    private void checkKeepImg() {
        mBinding.control.keep.setImageResource(Keep.find(getHistoryKey()) == null ? R.drawable.ic_control_keep_off : R.drawable.ic_control_keep_on);
        updateTmdbKeepState();
    }

    private void checkLockImg() {
        mBinding.control.right.lock.setImageResource(isLock() ? R.drawable.ic_control_lock_on : R.drawable.ic_control_lock_off);
    }

    private void checkFullscreenImg() {
        mBinding.control.fullscreen.setImageResource(isFullscreen() ? R.drawable.ic_control_fullscreen_exit : R.drawable.ic_control_fullscreen);
    }

    private void checkDanmakuImg() {
        mBinding.control.danmaku.setImageResource(DanmakuSetting.isShow() ? R.drawable.ic_control_danmaku_on : R.drawable.ic_control_danmaku_off);
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
        mBinding.control.title.setText(getPlaybackControlTitle());
        if (pic) setArtwork(item.getPic());
        if (pic || name) setMetadata();
        if (pic || name) syncHistory();
        if (pic || name) updateKeep();
        if (id) updateNavigationKey();
        PlaybackEventCollector.get().updateHistory(mHistory);
        setText(item);

        // TMDB 模式：数据加载完成后填充头部面板
        if (mTmdbHeaderView != null) {
            boolean loaded = mTmdbUIAdapter != null && mTmdbUIAdapter.isLoaded();
            android.util.Log.d("VideoActivity", "updateVod - TMDB isLoaded=" + loaded);
            if (loaded) {
                mTmdbFallbackToNative = false;
                hideNativePersonalRecommendations();
                moveFlagAndEpisodeToTmdb();
                mBinding.progressLayout.showContent();
                mTmdbHeaderView.bind(mTmdbUIAdapter);
                styleTmdbSourceInFlagTitle();
                applyTmdbPlaybackControlColors();
                applyFusionPlayerBelowSpacing();
                updateTmdbKeepState();
                requestIntroSkipPlan();
            } else {
                android.util.Log.d("VideoActivity", "TMDB 加载失败，回退到原生详情");
                showNativeDetailFallback(item);
                if (shouldShowAutoTmdbMatchDialog(item)) showManualTmdbMatchDialog();
            }
        }
    }

    private void updateFlag(Flag activated, List<Flag> items) {
        items.forEach(item -> mFlagAdapter.getItems().stream()
                .filter(item::equals).findFirst().ifPresentOrElse(target -> {
                    target.mergeEpisodes(item.getEpisodes(), mHistory.isRevSort());
                    if (target.equals(activated)) setEpisodeAdapter(target.getEpisodes());
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

        @Override
        public void onAudio() {
            moveTaskToBack(true);
            setAudioOnly(true);
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
    protected void onTracksChanged() {
        updateAudioOnlyState();
        setTrackVisible();
        mClock.setCallback(this);
    }

    private void updateAudioOnlyState() {
        if (service() == null) return;
        setAudioOnly(player().haveTrack(C.TRACK_TYPE_AUDIO) && !player().haveTrack(C.TRACK_TYPE_VIDEO));
    }

    @Override
    protected void onTitlesChanged() {
        setTitleVisible();
    }

    @Override
    protected void onError(String msg) {
        recordPlayHealth(false, msg);
        subtitlePlaybackSession.stop(this);
        mBinding.swipeLayout.setEnabled(true);
        Track.delete(player().getKey());
        mClock.setCallback(null);
        player().resetTrack();
        player().reset();
        player().stop();
        showError(msg);
        startFlow();
    }

    @Override
    protected void onReload(String msg) {
        if (PlayerManager.RELOAD_LUT_WARMUP.equals(msg)) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-ui", "auto refresh after lut warmup playback failure key=%s episode=%s", getKey(), getEpisode() == null ? null : getEpisode().getName());
            onRefresh();
            return;
        }
        super.onReload(msg);
    }

    @Override
    protected void onReclaim() {
        Result result = mViewModel.getPlayer().getValue();
        if (result != null) setPlayer(result);
    }

    @Override
    protected void onStateChanged(int state) {
        switch (state) {
            case Player.STATE_BUFFERING:
                // TMDB 模式下，如果内容已经加载完成，不再显示 spinner
                if (!shouldUseTmdbDetailLayout() || !isTmdbContentLoaded()) {
                    showProgress();
                }
                break;
            case Player.STATE_READY:
                recordPlayHealth(true, "");
                hideProgress();
                checkControl();
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
            mPiP.update(this, true);
            mBinding.control.play.setImageResource(androidx.media3.ui.R.drawable.exo_icon_pause);
        } else if (isPaused()) {
            mPiP.update(this, false);
            mBinding.control.play.setImageResource(androidx.media3.ui.R.drawable.exo_icon_play);
        }
    }

    @Override
    protected void onSizeChanged(VideoSize size) {
        mPiP.update(this, size.width, size.height, getScale());
        setSizeText();
        updateVideoHeight();
        applyResizeMode(getScale());
        checkOrientation();
    }

    @Override
    protected void onSurfaceAttached() {
        applyResizeMode(getScale());
    }

    @Override
    public void onSubtitleClick() {
        SubtitleDialog.create().view(mBinding.exo.getSubtitleView()).search(() -> SubtitleManualSearchDialog.show(this, subtitlePlaybackSession, this)).show(this);
        hideControl();
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
    public void onCastEvent(CastEvent event) {
        if (isRedirect()) return;
        ReceiveDialog.create().event(event).show(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (isRedirect()) return;
        if (event.getType() == RefreshEvent.Type.DETAIL) getDetail();
        else if (event.getType() == RefreshEvent.Type.PLAYER) onRefresh();
        else if (event.getType() == RefreshEvent.Type.VOD) updateVod(event.getVod());
        else if (event.getType() == RefreshEvent.Type.HISTORY) refreshPersonalRecommendationsForHistory();
        else if (event.getType() == RefreshEvent.Type.SUBTITLE) player().setSub(Sub.from(event.getPath()));
        else if (event.getType() == RefreshEvent.Type.DANMAKU) player().setDanmaku(Danmaku.from(event.getPath()));
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

    private void checkOrientation() {
        if (isFullscreen() && !isRotate() && player().isPortrait()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
            setRotate(true);
        } else if (isFullscreen() && isRotate() && player().isLandscape()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE);
            setRotate(false);
        }
    }

    private void updateVideoHeight() {
        if (isLand() || isFullscreen() || isInPictureInPictureMode()) return;
        if (mFrameHeight <= 0 || mFrameParams.height == mFrameHeight) return;
        mFrameParams.height = mFrameHeight;
        mBinding.video.setLayoutParams(mFrameParams);
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

    private void setSizeText() {
        String text = player().getSizeText();
        boolean hasTitle = !TextUtils.isEmpty(mBinding.control.title.getText());
        mBinding.control.title.setVisibility(hasTitle ? View.VISIBLE : View.INVISIBLE);
        mBinding.control.size.setText(text);
        mBinding.control.size.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
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
        if (mParseAdapter.isEmpty()) return;
        setParse(mParseAdapter.first());
    }

    private void checkFlag() {
        int position = isGone(mBinding.flag) ? -1 : mFlagAdapter.getPosition();
        if (position == mFlagAdapter.getItemCount() - 1) checkSearch(false);
        else nextFlag(position);
    }

    private void checkSearch(boolean force) {
        if (!force && !PlayerSetting.isAutoChange()) return;
        if (mQuickAdapter.isEmpty()) initSearch(mBinding.name.getText().toString(), true);
        else if (isAutoMode() || force) nextSite();
    }

    private void initSearch(String keyword, boolean auto) {
        setAutoMode(auto);
        setInitAuto(auto);
        revealManualSearch = !auto;
        startSearch(keyword);
    }

    private boolean isPass(Site item) {
        if (isAutoMode() && !item.isChangeable()) return false;
        return item.isSearchable();
    }

    private void startSearch(String keyword) {
        mQuickAdapter.clear();
        mBinding.quick.setVisibility(View.GONE);
        if (isQuickSearchVisible()) mQuickSearchDialog.clear();
        List<Site> sites = new ArrayList<>();
        for (Site item : VodConfig.get().getSites()) if (isPass(item)) sites.add(item);
        SiteHealthStore.sortSites(sites);
        mViewModel.searchContent(sites, keyword, true);
    }

    private void setSearch(Result result) {
        List<Vod> items = result.getList();
        items.removeIf(this::mismatch);
        boolean showQuick = !shouldUseTmdbDetailLayout() && !isQuickSearchVisible();
        mBinding.quick.setVisibility(showQuick ? View.VISIBLE : View.GONE);
        mQuickAdapter.addAll(items);
        if (isQuickSearchVisible()) mQuickSearchDialog.addAll(items);
        if (showQuick && revealManualSearch && !items.isEmpty()) {
            revealManualSearch = false;
            mBinding.quick.post(() -> mBinding.scroll.smoothScrollTo(0, mBinding.quick.getTop()));
        } else if (revealManualSearch && !items.isEmpty()) {
            revealManualSearch = false;
        }
        if (isInitAuto() && PlayerSetting.isAutoChange()) nextSite();
        if (items.isEmpty()) return;
        App.removeCallbacks(mR4);
    }

    private boolean isQuickSearchVisible() {
        return mQuickSearchDialog != null && mQuickSearchDialog.isActive();
    }

    private boolean mismatch(Vod item) {
        if (getId().equals(item.getId())) return true;
        if (mBroken.contains(item.getId())) return true;
        String keyword = mBinding.name.getText().toString();
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
        if (mQuickAdapter.isEmpty()) return;
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

    private boolean isFullscreen() {
        return fullscreen;
    }

    private void setFullscreen(boolean fullscreen) {
        Util.toggleFullscreen(this, this.fullscreen = fullscreen);
        updateFusionThemeButtonVisibility();
    }

    private boolean isShortDramaSource() {
        Site site = getSite();
        return Setting.isShortDramaSiteEnabled(site == null ? getKey() : site.getKey(), site == null ? "" : site.getName());
    }

    private boolean isTmdbContentLoaded() {
        return mTmdbContentLoaded;
    }

    private void initTmdbMode() {
        // TMDB 模式：通过全局开关或 Intent 参数启用
        if (!isTmdbSourceEnabled()) return;

        mTmdbUIAdapter = new com.fongmi.android.tv.ui.helper.TmdbUIAdapter(this);
        if (!mTmdbUIAdapter.isReady()) {
            SpiderDebug.log("TMDB 增强已启用，但配置未就绪（需要 API Key）");
            return;
        }
        mTmdbUIAdapter.setPersonalAiUpdateListener(() -> {
            if (mTmdbHeaderView != null && mTmdbUIAdapter != null && mTmdbUIAdapter.isLoaded() && !mTmdbFallbackToNative) {
                mTmdbHeaderView.refreshPersonalAiRecommendations();
            }
        });

        // 注入 TMDB 风格头部面板
        ViewGroup scrollContainer = (ViewGroup) mBinding.scroll.getChildAt(0);
        mTmdbHeaderView = new com.fongmi.android.tv.ui.custom.TmdbHeaderView(this, scrollContainer);
        mTmdbHeaderView.setDetailThemeMode(getFusionDetailThemeMode());
        mTmdbHeaderView.inflate();
        mTmdbHeaderView.setActionListener(new com.fongmi.android.tv.ui.custom.TmdbHeaderView.ActionListener() {
            @Override
            public void onChangeSource() {
                onChange();
            }

            @Override
            public void onRematch() {
                showManualTmdbMatchDialog();
            }

            @Override
            public void onKeep() {
                VideoActivity.this.onKeep();
            }
        });

        // 设置图片加载完成监听器
        mTmdbHeaderView.setOnImagesLoadedListener(new com.fongmi.android.tv.ui.custom.TmdbHeaderView.OnImagesLoadedListener() {
            @Override
            public void onImagesLoaded() {
                // TMDB 内容加载完成，设置标记并隐藏进度条
                android.util.Log.d("VideoActivity", "TMDB 内容加载完成，隐藏进度条");
                if (Setting.isFusionDetailPage() && mTmdbUIAdapter != null && mTmdbUIAdapter.getTmdbItem() != null) {
                    setContextWall(mTmdbUIAdapter.getTmdbItem().getBackdropUrl());
                }
                mTmdbContentLoaded = true;
                hideProgress();
            }
        });

        // TMDB 模式下：隐藏原生详情信息（但保持容器可见，因为 TMDB 内容也在里面）
        setNativeDetailInfoVisible(false);
        mBinding.quick.setVisibility(View.GONE);
        mBinding.search.setVisibility(View.GONE);
        if (mBinding.videoShadow != null) mBinding.videoShadow.setVisibility(View.GONE);  // 隐藏播放器下方的阴影

        if (Setting.isFusionDetailPage()) {
            applyFusionDetailChrome();
        } else {
            mBinding.scroll.setBackgroundColor(com.fongmi.android.tv.ui.custom.TmdbHeaderView.getThemeBackgroundColor());
            applyTmdbTabletVideoLayoutIfNeeded();
        }

        // 移除 nativeContentContainer 的 padding，避免空白间隔
        if (mBinding.nativeContentContainer != null) mBinding.nativeContentContainer.setPadding(0, 0, 0, 0);
    }

    private void applyFusionDetailChrome() {
        if (mFusionChromeApplied) return;
        RelativeLayout chromeRoot = getFusionChromeRoot();
        if (chromeRoot == null) return;
        mFusionChromeApplied = true;
        applyFusionThemeSurface();

        applyFusionBackdropLayout();

        RelativeLayout.LayoutParams videoParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, ResUtil.dp2px(FUSION_PLAYER_HEIGHT_DP));
        videoParams.addRule(RelativeLayout.BELOW, R.id.statusBar);
        videoParams.setMargins(ResUtil.dp2px(FUSION_PLAYER_SIDE_MARGIN_DP), ResUtil.dp2px(FUSION_PLAYER_TOP_MARGIN_DP), ResUtil.dp2px(FUSION_PLAYER_SIDE_MARGIN_DP), 0);
        mFrameParams = videoParams;
        mBinding.video.setLayoutParams(videoParams);
        setFusionPlayerBottomGap();
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.BLACK);
        background.setCornerRadius(ResUtil.dp2px(20));
        mBinding.video.setBackground(background);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) mBinding.video.setClipToOutline(true);

        ensureFusionThemeButton();
        updateFusionThemeButton();
    }

    private void applyFusionBackdropLayout() {
        RelativeLayout.LayoutParams wallParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        wallParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        wallParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        wallParams.addRule(RelativeLayout.ALIGN_PARENT_START);
        wallParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        mBinding.contextWall.setLayoutParams(wallParams);
        mBinding.statusBar.setBackgroundColor(Color.TRANSPARENT);
        if (mBinding.videoContextScrim != null) {
            RelativeLayout.LayoutParams scrimParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
            scrimParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            scrimParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            scrimParams.addRule(RelativeLayout.ALIGN_PARENT_START);
            scrimParams.addRule(RelativeLayout.ALIGN_PARENT_END);
            mBinding.videoContextScrim.setLayoutParams(scrimParams);
            mBinding.videoContextScrim.setVisibility(View.VISIBLE);
            applyContextWallScrimTheme();
        }
    }

    private void setFusionPlayerBottomGap() {
        ensureFusionPlayerBottomSpacer();
        if (mFusionPlayerBottomSpacer == null) return;
        ViewGroup.LayoutParams params = mBinding.swipeLayout.getLayoutParams();
        if (!(params instanceof RelativeLayout.LayoutParams layoutParams)) return;
        layoutParams.addRule(RelativeLayout.BELOW, mFusionPlayerBottomSpacer.getId());
        layoutParams.topMargin = 0;
        mBinding.swipeLayout.setLayoutParams(layoutParams);
    }

    private void ensureFusionPlayerBottomSpacer() {
        if (mFusionPlayerBottomSpacer != null) return;
        RelativeLayout chromeRoot = getFusionChromeRoot();
        if (chromeRoot == null) return;
        mFusionPlayerBottomSpacer = new View(this);
        mFusionPlayerBottomSpacer.setId(View.generateViewId());
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, ResUtil.dp2px(FUSION_PLAYER_BOTTOM_GAP_DP));
        params.addRule(RelativeLayout.BELOW, R.id.video);
        chromeRoot.addView(mFusionPlayerBottomSpacer, params);
    }

    private void ensureFusionThemeButton() {
        if (mFusionThemeButton != null) return;
        RelativeLayout chromeRoot = getFusionChromeRoot();
        if (chromeRoot == null) return;
        mFusionThemeButton = new MaterialButton(this);
        mFusionThemeButton.setAllCaps(false);
        mFusionThemeButton.setMinHeight(ResUtil.dp2px(40));
        mFusionThemeButton.setMinWidth(ResUtil.dp2px(126));
        mFusionThemeButton.setTextSize(14);
        mFusionThemeButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        mFusionThemeButton.setCornerRadius(ResUtil.dp2px(24));
        mFusionThemeButton.setStrokeWidth(ResUtil.dp2px(1));
        mFusionThemeButton.setPadding(ResUtil.dp2px(16), 0, ResUtil.dp2px(16), 0);
        mFusionThemeButton.setOnClickListener(view -> cycleFusionTheme());
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(48));
        params.addRule(RelativeLayout.ALIGN_PARENT_END);
        params.addRule(RelativeLayout.BELOW, R.id.statusBar);
        params.setMargins(0, ResUtil.dp2px(14), ResUtil.dp2px(24), 0);
        chromeRoot.addView(mFusionThemeButton, params);
        updateFusionThemeButtonVisibility();
    }

    private RelativeLayout getFusionChromeRoot() {
        if (mBinding.video.getParent() instanceof RelativeLayout parent) return parent;
        if (mBinding.getRoot() instanceof RelativeLayout root) return root;
        return null;
    }

    private void cycleFusionTheme() {
        int theme = isFusionLightTheme() ? 2 : 1;
        Setting.putTmdbDetailTheme(theme);
        getIntent().putExtra(EXTRA_TMDB_DETAIL_THEME, theme);
        applyFusionThemeSurface();
        updateFusionThemeButton();
        if (mTmdbHeaderView != null && mTmdbUIAdapter != null && mTmdbUIAdapter.isLoaded()) {
            mTmdbHeaderView.bind(mTmdbUIAdapter);
            styleTmdbSourceInFlagTitle();
            applyTmdbPlaybackControlColors();
            applyFusionPlayerBelowSpacing();
        }
    }

    private void applyFusionPlayerBelowSpacing() {
        if (!Setting.isFusionDetailPage() || mTmdbHeaderView == null || mTmdbHeaderView.getHeaderRoot() == null) return;
        View actions = mTmdbHeaderView.getHeaderRoot().findViewById(R.id.tmdbActionsScroll);
        if (actions == null || !(actions.getLayoutParams() instanceof ViewGroup.MarginLayoutParams params)) return;
        params.topMargin = 0;
        actions.setLayoutParams(params);
    }

    private void applyFusionThemeSurface() {
        boolean light = isFusionLightTheme();
        mBinding.getRoot().setBackgroundColor(light ? 0xFFF3F6F9 : 0xFF0F141A);
        mBinding.scroll.setBackgroundColor(Color.TRANSPARENT);
        mBinding.swipeLayout.setBackgroundColor(Color.TRANSPARENT);
        mBinding.progressLayout.setBackgroundColor(Color.TRANSPARENT);
        if (mBinding.nativeContentContainer != null) mBinding.nativeContentContainer.setBackgroundColor(Color.TRANSPARENT);
        applyContextWallScrimTheme();
        syncFusionHeaderTheme();
        if (mFlagAdapter != null) mFlagAdapter.setTmdbLight(isTmdbPlaybackLightTheme());
        applyFusionNativeTextColors();
        styleTmdbSourceInFlagTitle();
        applyTmdbPlaybackControlColors();
    }

    private void applyContextWallScrimTheme() {
        if (mBinding.videoContextScrim == null) return;
        boolean light = Setting.isFusionDetailPage() && isFusionLightTheme();
        mBinding.videoContextScrim.setBackgroundResource(light ? R.drawable.shape_video_context_scrim_light : R.drawable.shape_video_context_scrim);
    }

    private void applyFusionNativeTextColors() {
        if (!Setting.isFusionDetailPage() || mBinding.nativeContentContainer == null) return;
        tintFusionNativeTextTree(mBinding.nativeContentContainer, isFusionLightTheme());
    }

    private void tintFusionNativeTextTree(View view, boolean light) {
        if (view instanceof RecyclerView) return;
        if (view instanceof TextView textView) {
            textView.setTextColor(light ? 0xFF12202D : 0xFFFFFFFF);
            textView.setLinkTextColor(light ? 0xFF1D8F5A : Color.WHITE);
            if (light) textView.setShadowLayer(0, 0, 0, 0);
            else textView.setShadowLayer(ResUtil.dp2px(2), 0, ResUtil.dp2px(1), 0xB0000000);
        }
        if (!(view instanceof ViewGroup group)) return;
        for (int i = 0; i < group.getChildCount(); i++) tintFusionNativeTextTree(group.getChildAt(i), light);
    }

    private void applyTmdbPlaybackControlColors() {
        if (mTmdbHeaderView == null || mTmdbHeaderView.getHeaderRoot() == null) return;
        boolean light = isTmdbPlaybackLightTheme();
        int color = tmdbPlaybackControlColor(light);
        if (mFlagAdapter != null) mFlagAdapter.setTmdbLight(light);
        tintFusionPlaybackTextTree(mBinding.flagTitleBar, color, light);
        tintFusionPlaybackTextTree(mBinding.episodeTitleBar, color, light);
        tintFusionPlaybackText(mBinding.qualityText, color, light);
        tintFusionPlaybackIcon(mBinding.reverse, color);
        tintFusionPlaybackIcon(mBinding.episodeViewMode, color);
        tintFusionPlaybackIcon(mBinding.more, color);
    }

    private boolean isTmdbPlaybackLightTheme() {
        return mTmdbHeaderView == null ? isFusionLightTheme() : mTmdbHeaderView.isCurrentDetailLightTheme();
    }

    private int tmdbPlaybackControlColor(boolean light) {
        if (mTmdbHeaderView != null) return mTmdbHeaderView.getFusionSectionTitleColor();
        return light ? 0xFF12202D : Color.WHITE;
    }

    private void tintFusionPlaybackTextTree(View view, int color, boolean light) {
        if (view == null || view instanceof RecyclerView) return;
        if (view instanceof TextView textView) tintFusionPlaybackText(textView, color, light);
        if (!(view instanceof ViewGroup group)) return;
        for (int i = 0; i < group.getChildCount(); i++) tintFusionPlaybackTextTree(group.getChildAt(i), color, light);
    }

    private void tintFusionPlaybackText(TextView textView, int color, boolean light) {
        if (textView == null) return;
        textView.setTextColor(color);
        textView.setLinkTextColor(light ? 0xFF1D8F5A : Color.WHITE);
        if (light) textView.setShadowLayer(0, 0, 0, 0);
        else textView.setShadowLayer(ResUtil.dp2px(2), 0, ResUtil.dp2px(1), 0xB0000000);
    }

    private void tintFusionPlaybackIcon(View view, int color) {
        if (view instanceof ImageView imageView) imageView.setColorFilter(color);
    }

    private void updateFusionThemeButton() {
        if (mFusionThemeButton == null) return;
        boolean light = isFusionLightTheme();
        mFusionThemeButton.setText(fusionThemeLabel());
        mFusionThemeButton.setTextColor(light ? 0xFF12202D : 0xFFE9F0F5);
        mFusionThemeButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(light ? 0xE6E7EDF3 : 0xCC252A32));
        mFusionThemeButton.setStrokeColor(android.content.res.ColorStateList.valueOf(light ? 0x33424B57 : 0x42FFFFFF));
        updateFusionThemeButtonVisibility();
    }

    private void updateFusionThemeButtonVisibility() {
        if (mFusionThemeButton == null) return;
        boolean show = DetailThemeVisibility.showFusionThemeButton(Setting.isFusionDetailPage(), isFullscreen(), isInPictureInPictureMode());
        mFusionThemeButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private String fusionThemeLabel() {
        return isFusionLightTheme() ? getString(R.string.detail_theme_light) : getString(R.string.detail_theme_dark);
    }

    private boolean isFusionLightTheme() {
        return Setting.resolveTmdbDetailLightTheme(getFusionDetailThemeMode(), isSystemNight());
    }

    private int getFusionDetailThemeMode() {
        return Setting.getTmdbDetailTheme() == 1 ? 1 : 2;
    }

    private void syncFusionHeaderTheme() {
        if (mTmdbHeaderView != null) mTmdbHeaderView.setDetailThemeMode(getFusionDetailThemeMode());
    }

    private boolean isSystemNight() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private void hideTmdbHeader() {
        if (mTmdbHeaderView == null || mTmdbHeaderView.getHeaderRoot() == null) return;
        mTmdbHeaderView.getHeaderRoot().setVisibility(View.GONE);
    }

    private void showNativeDetailFallback(Vod item) {
        mTmdbFallbackToNative = true;
        mTmdbContentLoaded = true;
        hideTmdbHeader();
        restoreFlagAndEpisodeFromTmdb();
        updateEpisodeGroupVisibility();
        restoreDefaultVideoLayout();
        setNativeDetailInfoVisible(true);
        setOriginalEnhancedActionVisibility(false);
        mBinding.search.setVisibility(View.VISIBLE);
        if (mBinding.videoShadow != null) mBinding.videoShadow.setVisibility(View.VISIBLE);
        setText(item);
        mBinding.progressLayout.showContent();
        loadNativePersonalRecommendations(item);
        hideProgress();
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
                SpiderDebug.log("personal-rec", "mobile native core failed error=%s", e.getMessage());
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
                SpiderDebug.log("personal-rec", "mobile native ai failed error=%s", e.getMessage());
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
        bindNativePersonalRecommendationRow(mBinding.tmdbPersonalTmdbRecommendationsLabel, mBinding.tmdbPersonalTmdbRecommendations, mPersonalTmdbRecommendationAdapter, mNativePersonalTmdbPage.getItems());
        bindNativePersonalRecommendationRow(mBinding.tmdbPersonalDoubanRecommendationsLabel, mBinding.tmdbPersonalDoubanRecommendations, mPersonalDoubanRecommendationAdapter, mNativePersonalDoubanPage.getItems());
    }

    private void bindNativePersonalAiRecommendation(PersonalRecommendationService.RecommendationPage page) {
        mNativePersonalAiPage = page == null ? PersonalRecommendationService.RecommendationPage.empty("") : page;
        bindNativePersonalRecommendationRow(mBinding.tmdbPersonalAiRecommendationsLabel, mBinding.tmdbPersonalAiRecommendations, mPersonalAiRecommendationAdapter, mNativePersonalAiPage.getItems());
    }

    private void bindNativePersonalRecommendationRow(View label, View recycler, TmdbRecommendationAdapter adapter, List<TmdbItem> items) {
        if (items != null && !items.isEmpty()) {
            adapter.setItems(items);
            label.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.VISIBLE);
        } else {
            adapter.setItems(new ArrayList<>());
            label.setVisibility(View.GONE);
            recycler.setVisibility(View.GONE);
        }
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
        if (mPersonalTmdbRecommendationAdapter != null) mPersonalTmdbRecommendationAdapter.setItems(new ArrayList<>());
        if (mPersonalDoubanRecommendationAdapter != null) mPersonalDoubanRecommendationAdapter.setItems(new ArrayList<>());
        if (mPersonalAiRecommendationAdapter != null) mPersonalAiRecommendationAdapter.setItems(new ArrayList<>());
        mBinding.tmdbPersonalTmdbRecommendationsLabel.setVisibility(View.GONE);
        mBinding.tmdbPersonalTmdbRecommendations.setVisibility(View.GONE);
        mBinding.tmdbPersonalDoubanRecommendationsLabel.setVisibility(View.GONE);
        mBinding.tmdbPersonalDoubanRecommendations.setVisibility(View.GONE);
        mBinding.tmdbPersonalAiRecommendationsLabel.setVisibility(View.GONE);
        mBinding.tmdbPersonalAiRecommendations.setVisibility(View.GONE);
    }

    private void onPersonalRecommendationClick(TmdbItem item) {
        TmdbNavigation.open(this, item, getSite());
    }

    private void refreshPersonalRecommendationsForHistory() {
        if (!Setting.isPersonalRecommendation() || mVod == null) return;
        if (mTmdbHeaderView != null && mTmdbUIAdapter != null && mTmdbUIAdapter.isLoaded() && !mTmdbFallbackToNative) {
            mTmdbHeaderView.refreshPersonalRecommendations();
        } else if (mTmdbFallbackToNative || !shouldUseTmdbDetailLayout()) {
            loadNativePersonalRecommendations(mVod);
        }
    }

    private boolean isNearRecommendationRowEnd(RecyclerView recyclerView) {
        RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
        RecyclerView.LayoutManager manager = recyclerView.getLayoutManager();
        if (adapter == null || !(manager instanceof LinearLayoutManager)) return false;
        int lastVisible = ((LinearLayoutManager) manager).findLastVisibleItemPosition();
        return lastVisible >= 0 && adapter.getItemCount() - lastVisible <= 4;
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
                SpiderDebug.log("personal-rec", "native load more failed tmdb=%s error=%s", tmdb, e.getMessage());
                nextPage = page;
            }
            PersonalRecommendationService.RecommendationPage loadedPage = nextPage;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != mPersonalRecommendationGeneration) return;
                if (tmdb) {
                    mNativePersonalTmdbLoading = false;
                    mNativePersonalTmdbPage = loadedPage;
                    mPersonalTmdbRecommendationAdapter.appendItems(loadedPage.getItems());
                } else {
                    mNativePersonalDoubanLoading = false;
                    mNativePersonalDoubanPage = loadedPage;
                    mPersonalDoubanRecommendationAdapter.appendItems(loadedPage.getItems());
                }
            });
        });
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
        mTmdbFallbackToNative = false;
        mTmdbContentLoaded = false;
        hideTmdbHeader();
        if (mBinding.videoShadow != null) mBinding.videoShadow.setVisibility(View.GONE);
        mBinding.progressLayout.showProgress();
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

    private void updateTmdbKeepState() {
        if (mTmdbHeaderView != null) mTmdbHeaderView.setKeepSelected(Keep.find(getHistoryKey()) != null);
    }

    private void moveFlagAndEpisodeToTmdb() {
        // 将站源、线路和选集移到 TMDB 头部的 playback controls 容器中
        if (mTmdbHeaderView == null) return;

        View tmdbRoot = mTmdbHeaderView.getHeaderRoot();
        if (tmdbRoot == null) return;

        ViewGroup playbackControls = tmdbRoot.findViewById(com.fongmi.android.tv.R.id.tmdbPlaybackControls);
        if (playbackControls == null) return;

        // 移除旧内容
        playbackControls.removeAllViews();
        setTmdbFlagStyle(true);
        moveTmdbSourceToFlagTitle(tmdbRoot);

        for (View view : getTmdbMovableViews()) {
            if (view == null) continue;
            rememberTmdbMovedView(view);
            ViewGroup parent = (ViewGroup) view.getParent();
            if (parent != null) parent.removeView(view);
            playbackControls.addView(view);
        }
        mTmdbControlsMoved = true;
        updateEpisodeGroupVisibility();
        mTmdbHeaderView.refreshTheme();
        applyFusionThemeSurface();
    }

    private void restoreFlagAndEpisodeFromTmdb() {
        if (!mTmdbControlsMoved) return;
        setTmdbFlagStyle(false);
        for (TmdbMovedView item : mTmdbMovedViews) {
            ViewGroup parent = (ViewGroup) item.view.getParent();
            if (parent != null) parent.removeView(item.view);
            item.parent.addView(item.view, Math.min(item.index, item.parent.getChildCount()), item.layoutParams);
        }
        mTmdbControlsMoved = false;
        updateEpisodeGroupVisibility();
    }

    private void moveTmdbSourceToFlagTitle(View tmdbRoot) {
        View source = tmdbRoot.findViewById(R.id.tmdbFusionSource);
        if (source == null) source = mBinding.flagTitleBar.findViewById(R.id.tmdbFusionSource);
        if (source == null) return;
        rememberTmdbMovedView(source);
        if (source.getParent() instanceof ViewGroup parent) parent.removeView(source);
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(ResUtil.dp2px(12));
        source.setLayoutParams(params);
        mBinding.flagTitleBar.addView(source);
        styleTmdbSourceInFlagTitle();
    }

    private void styleTmdbSourceInFlagTitle() {
        View source = mBinding.flagTitleBar.findViewById(R.id.tmdbFusionSource);
        if (!(source instanceof TextView textView)) return;
        boolean light = isTmdbPlaybackLightTheme();
        int titleColor = tmdbPlaybackControlColor(light);
        textView.setAlpha(1f);
        textView.setTextColor(titleColor);
        textView.setLinkTextColor(titleColor);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, mBinding.flagText.getTextSize());
        textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        textView.setSingleLine(true);
        textView.setMaxWidth(ResUtil.dp2px(260));
        if (light) textView.setShadowLayer(0, 0, 0, 0);
        else if (isLightText(titleColor)) textView.setShadowLayer(ResUtil.dp2px(2), 0, ResUtil.dp2px(1), 0xB0000000);
        else textView.setShadowLayer(0, 0, 0, 0);
    }

    private boolean isLightText(int color) {
        return Color.red(color) + Color.green(color) + Color.blue(color) > 384;
    }

    private void setTmdbFlagStyle(boolean enabled) {
        mFlagAdapter.setTmdbLight(isTmdbPlaybackLightTheme());
        mFlagAdapter.setTmdbStyle(enabled);
        mBinding.flag.setAdapter(null);
        mBinding.flag.setAdapter(mFlagAdapter);
        scrollToPosition(mBinding.flag, mFlagAdapter.getPosition());
    }

    private void rememberTmdbMovedView(View view) {
        if (view == null) return;
        for (TmdbMovedView item : mTmdbMovedViews) if (item.view == view) return;
        if (view.getParent() instanceof ViewGroup) mTmdbMovedViews.add(new TmdbMovedView(view));
    }

    private View[] getTmdbMovableViews() {
        return new View[]{
                mBinding.flagTitleBar,
                mBinding.flag,
                mBinding.qualityText,
                mBinding.quality,
                mBinding.episodeTitleBar,
                mBinding.episodeGroup,
                mBinding.episode,
        };
    }

    private void applyShortDramaMode() {
        if (!isShortDramaSource()) return;
        enterShortDramaFullscreen();
        setShortDramaScale();
        mBinding.exo.postDelayed(this::setShortDramaScale, 250);
        hideControl();
    }

    private void enterShortDramaFullscreen() {
        if (!isFullscreen()) {
            setFullscreen(true);
            mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
            setRequestedOrientation(isPort() ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
            mKeyDown.resetScale();
        }
        setShortDramaScale();
        hideControl();
        ViewCompat.requestApplyInsets(mBinding.getRoot());
    }

    private void setShortDramaScale() {
        int scale = (mHistory != null && mHistory.getScale() != -1) ? getScale() : SHORT_DRAMA_SCALE;
        mBinding.exo.setResizeMode(scale);
        mBinding.control.action.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    private void finishShortDrama() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        finish();
    }

    private void syncShortDramaControlLayout(boolean shortDrama) {
        if (!shortDrama) {
            restoreShortDramaControls();
            return;
        }
        dockShortDramaControls();
        mBinding.control.action.getRoot().setVisibility(View.GONE);
        mBinding.control.info.setVisibility(View.GONE);
        if (mShortDramaControlDock != null) mShortDramaControlDock.setVisibility(isLock() ? View.GONE : View.VISIBLE);
    }

    private void dockShortDramaControls() {
        ViewGroup dock = getShortDramaControlDock();
        if (shortDramaControlsDocked) return;
        for (ShortDramaControlItem item : getShortDramaControlItems()) {
            ViewGroup parent = (ViewGroup) item.view.getParent();
            if (parent != null) parent.removeView(item.view);
            dock.addView(item.view, item.layoutParams);
        }
        shortDramaControlsDocked = true;
    }

    private void restoreShortDramaControls() {
        if (!shortDramaControlsDocked) return;
        for (ShortDramaControlItem item : getShortDramaControlItems()) {
            ViewGroup parent = (ViewGroup) item.view.getParent();
            if (parent != null) parent.removeView(item.view);
            item.parent.addView(item.view, Math.min(item.index, item.parent.getChildCount()), item.layoutParams);
        }
        if (mShortDramaControlDock != null && mShortDramaControlDock.getParent() instanceof ViewGroup) {
            ((ViewGroup) mShortDramaControlDock.getParent()).removeView(mShortDramaControlDock);
        }
        shortDramaControlsDocked = false;
    }

    private ViewGroup getShortDramaControlDock() {
        ViewGroup right = mBinding.control.right.getRoot();
        if (mShortDramaControlDock == null) {
            LinearLayoutCompat dock = new LinearLayoutCompat(this);
            dock.setGravity(android.view.Gravity.CENTER);
            dock.setOrientation(LinearLayoutCompat.VERTICAL);
            mShortDramaControlDock = dock;
        }
        if (mShortDramaControlDock.getParent() != right) {
            int index = Math.min(right.indexOfChild(mBinding.control.right.lock) + 1, right.getChildCount());
            right.addView(mShortDramaControlDock, index, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return mShortDramaControlDock;
    }

    private List<ShortDramaControlItem> getShortDramaControlItems() {
        if (mShortDramaControlItems.isEmpty()) {
            for (View view : getShortDramaControlViews()) {
                if (view.getParent() instanceof ViewGroup) mShortDramaControlItems.add(new ShortDramaControlItem(view));
            }
        }
        return mShortDramaControlItems;
    }

    private View[] getShortDramaControlViews() {
        return new View[]{
                mBinding.control.danmaku,
                mBinding.control.cast,
                mBinding.control.keep,
                mBinding.control.action.episodes,
                mBinding.control.setting,
        };
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

    public boolean isRotate() {
        return rotate;
    }

    public void setRotate(boolean rotate) {
        this.rotate = rotate;
        if (fullscreen && !rotate) setPadding(mBinding.control.getRoot());
        else noPadding(mBinding.control.getRoot());
    }

    private void notifyItemChanged(RecyclerView view, RecyclerView.Adapter<?> adapter) {
        view.post(() -> adapter.notifyItemRangeChanged(0, adapter.getItemCount()));
    }

    private void scrollToPosition(RecyclerView view, int position) {
        view.post(() -> {
            RecyclerView.Adapter<?> adapter = view.getAdapter();
            if (adapter == null || position < 0 || position >= adapter.getItemCount()) return;
            view.scrollToPosition(position);
        });
    }

    @Override
    public void onCasted() {
        subtitlePlaybackSession.stop(this);
        player().stop();
    }

    @Override
    public void onScale(int tag) {
        mKeyDown.resetScale();
        setScale(tag);
    }

    @Override
    public void onEpisodeColumn(int column) {
        PlayerSetting.putEpisodeColumn(column);
        refreshEpisodeTitles();
    }

    @Override
    public void onCompactEpisodeTitleChanged() {
        refreshEpisodeTitles();
    }

    private void refreshEpisodeTitles() {
        if (mEpisodeAdapter == null) return;
        if ((mFlagAdapter == null || mFlagAdapter.isEmpty()) && !shouldUseEpisodeRangePaging(getCurrentEpisodeItems())) {
            updateEpisodeLayout(mEpisodeAdapter.getItems());
            mEpisodeAdapter.notifyItemRangeChanged(0, mEpisodeAdapter.getItemCount());
        } else {
            setEpisodeItems(getCurrentEpisodeItems());
        }
        scrollEpisodeToSelected();
        mBinding.episode.post(this::updateEpisodeViewportHeight);
    }

    @Override
    public void onParse(Parse item) {
        onItemClick(item);
    }

    @Override
    public void onSpeedUp() {
        if (!player().isPlaying()) return;
        mBinding.widget.speed.setVisibility(View.VISIBLE);
        mBinding.widget.speed.startAnimation(ResUtil.getAnim(R.anim.forward));
        mBinding.control.action.speed.setText(player().setSpeed(PlayerSetting.getSpeed()));
    }

    @Override
    public void onSpeedEnd() {
        mBinding.widget.speed.clearAnimation();
        mBinding.control.action.speed.setText(player().setSpeed(PlayerSetting.getDefaultSpeed()));
        mHistory.setSpeed(player().getSpeed());
    }

    @Override
    public void onBright(int progress) {
        mBinding.widget.bright.setVisibility(View.VISIBLE);
        mBinding.widget.brightProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_low);
        else if (progress < 70) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_medium);
        else mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_high);
    }

    @Override
    public void onVolume(int progress) {
        mBinding.widget.volume.setVisibility(View.VISIBLE);
        mBinding.widget.volumeProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_low);
        else if (progress < 70) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_medium);
        else mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_high);
    }

    @Override
    public void onFlingUp() {
        if (getEpisodeCount() == 1) onRefresh();
        else checkNext();
    }

    @Override
    public void onFlingDown() {
        if (getEpisodeCount() == 1) onRefresh();
        else checkPrev();
    }

    @Override
    public void onSeeking(long time) {
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        mBinding.widget.time.setText(player().getPositionTime(time));
        mBinding.widget.seek.setVisibility(View.VISIBLE);
        hideProgress();
    }

    @Override
    public void onSeekEnd(long time) {
        seekTo(time);
    }

    @Override
    public void onSingleTap() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else showControl();
    }

    @Override
    public void onDoubleTap() {
        if (isLock()) return;
        if (!isFullscreen()) {
            enterFullscreen();
        } else if (player().isPlaying()) {
            showControl();
            onPaused();
        } else {
            hideControl();
            onPlay();
        }
    }

    @Override
    public void onTouchEnd() {
        mBinding.widget.seek.setVisibility(View.GONE);
        mBinding.widget.speed.setVisibility(View.GONE);
        mBinding.widget.bright.setVisibility(View.GONE);
        mBinding.widget.volume.setVisibility(View.GONE);
    }

    @Override
    public void onShare(CharSequence title) {
        PlayerHelper.share(this, player().getUrl(), player().getHeaders(), title);
        setRedirect(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == 1001) PlayerHelper.onExternalResult(data, service()::dispatchNext, controller()::seekTo);
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            preparePiP("userLeaveHint");
        } else {
            requestPiP("userLeaveHint");
        }
    }

    @Override
    public boolean onPictureInPictureRequested() {
        return requestPiP("systemRequest");
    }

    private boolean preparePiP(String reason) {
        if (isRedirect() || isPlaybackExiting()) return false;
        if (service() == null || !player().haveTrack(C.TRACK_TYPE_VIDEO)) return false;
        mPiP.update(this, player().getVideoWidth(), player().getVideoHeight(), getScale());
        return true;
    }

    private boolean requestPiP(String reason) {
        if (!preparePiP(reason)) return false;
        if (isLock()) App.post(this::onLock, 500);
        return enterPiP(reason);
    }

    private boolean enterPiP(String reason) {
        if (service() == null || !player().haveTrack(C.TRACK_TYPE_VIDEO)) return false;
        return mPiP.enter(this, player().getVideoWidth(), player().getVideoHeight(), getScale());
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        updateFusionThemeButtonVisibility();
        if (!isFullscreen()) setVideoView(isInPictureInPictureMode);
        if (isInPictureInPictureMode) {
            hideControl();
            hideDanmaku();
            hideSheet();
        } else {
            showDanmaku();
            restoreContextWall();
            if (isStop()) finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        restoreContextWall();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (isAutoRotate() && isPort() && newConfig.orientation == Configuration.ORIENTATION_PORTRAIT && !isRotate() && !isLock()) exitFullscreen();
        if (isAutoRotate() && isPort() && newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) enterFullscreen();
        if (!isFullscreen()) {
            applyTmdbTabletVideoLayoutIfNeeded();
            if (mVod != null) bindTmdbTabletTopSummary(mVod);
        }
        if (isFullscreen()) Util.hideSystemUI(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (isFullscreen() && hasFocus) Util.hideSystemUI(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        android.util.Log.d("VideoActivity", "onStart: calling mClock.stop().start()");
        mClock.stop().start();
        if (mOsd != null) mOsd.start();
        setAudioOnly(false);
        setStop(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mOsd != null) mOsd.stop();
        if (PlayerSetting.isBackgroundOff()) mClock.stop();
        if (!isAudioOnly()) setStop(true);
    }

    @Override
    protected void onBackInvoked() {
        if (hasLutQuick() && mBinding.lutQuick.hideIfVisible()) {
            return;
        } else if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isFullscreen() && isShortDramaSource()) {
            finishShortDrama();
        } else if (isFullscreen() && !isLock()) {
            exitFullscreen();
        } else if (!isLock()) {
            mViewModel.stopSearch();
            markPlaybackExiting();
            stopPlayback();
            if (isTaskRoot()) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            super.onBackInvoked();
        }
    }

    @Override
    protected void onDestroy() {
        subtitlePlaybackSession.stop(this);
        mClock.release();
        saveHistory(true);
        Timer.get().reset();
        DanmakuApi.cancel();
        RefreshEvent.keep();
        App.removeCallbacks(mR1, mR2, mR3, mR4);
        if (mOsd != null) mOsd.release();
        mViewModel.getResult().removeObserver(mObserveDetail);
        mViewModel.getPlayer().removeObserver(mObservePlayer);
        mViewModel.getSearch().removeObserver(mObserveSearch);
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

    private static class ShortDramaControlItem {
        private final View view;
        private final ViewGroup parent;
        private final ViewGroup.LayoutParams layoutParams;
        private final int index;

        private ShortDramaControlItem(View view) {
            this.view = view;
            this.parent = (ViewGroup) view.getParent();
            this.layoutParams = view.getLayoutParams();
            this.index = parent.indexOfChild(view);
        }
    }

    private static class TmdbMovedView {
        private final View view;
        private final ViewGroup parent;
        private final ViewGroup.LayoutParams layoutParams;
        private final int index;

        private TmdbMovedView(View view) {
            this.view = view;
            this.parent = (ViewGroup) view.getParent();
            this.layoutParams = view.getLayoutParams();
            this.index = parent.indexOfChild(view);
        }
    }
}
