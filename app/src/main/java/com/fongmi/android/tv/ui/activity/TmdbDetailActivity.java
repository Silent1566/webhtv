package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.app.Dialog;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbMatchCache;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityTmdbDetailBinding;
import com.fongmi.android.tv.databinding.DialogTmdbEpisodeBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.player.IntroSkipPlayback;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.service.AiRecommendationService;
import com.fongmi.android.tv.service.PersonalRecommendationService;
import com.fongmi.android.tv.service.IntroSkipService;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.service.TmdbService;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.PlayerButtonSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.subtitle.SubtitlePlaybackSession;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.InlineEpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.TmdbEpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.TmdbPersonAdapter;
import com.fongmi.android.tv.ui.adapter.TmdbPhotoAdapter;
import com.fongmi.android.tv.ui.adapter.TmdbRailAdapter;
import com.fongmi.android.tv.ui.controller.VodPlayerControlController;
import com.fongmi.android.tv.ui.custom.CustomSeekView;
import com.fongmi.android.tv.ui.custom.EpisodeTitlePopup;
import com.fongmi.android.tv.ui.custom.PlayerGesture;
import com.fongmi.android.tv.ui.dialog.DanmakuDialog;
import com.fongmi.android.tv.ui.dialog.DisplayDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleManualSearchDialog;
import com.fongmi.android.tv.ui.dialog.TitleDialog;
import com.fongmi.android.tv.ui.dialog.TmdbSearchDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.ui.helper.DetailThemeVisibility;
import com.fongmi.android.tv.ui.helper.EpisodeRangePolicy;
import com.fongmi.android.tv.ui.helper.EpisodeSeasonPolicy;
import com.fongmi.android.tv.ui.helper.TmdbCinemaTheme;
import com.fongmi.android.tv.ui.helper.TmdbDetailLabels;
import com.fongmi.android.tv.ui.helper.TmdbEpisodeGridPolicy;
import com.fongmi.android.tv.ui.helper.TmdbMatchPolicy;
import com.fongmi.android.tv.ui.helper.TmdbRecommendationRows;
import com.fongmi.android.tv.utils.BatteryUtil;
import com.fongmi.android.tv.utils.Formatters;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.AudioUtil;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PiP;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.TmdbEpisodeSorter;
import com.fongmi.android.tv.utils.TmdbImageSelector;
import com.fongmi.android.tv.utils.TmdbImageSaver;
import com.fongmi.android.tv.utils.Traffic;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.utils.Clock;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.github.catvod.crawler.SpiderDebug;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TmdbDetailActivity extends PlaybackActivity implements TrackDialog.Listener, Clock.Callback, PlayerGesture.Listener, SubtitlePlaybackSession.Host {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault());
    private static final int FOCUS_STROKE = 0xFFFFD166;
    private static final int FOCUS_STROKE_DP = 3;
    private static final int CHIP_STROKE_DP = 1;
    private static final int PHOTO_PRELOAD_RADIUS = 2;
    private static final long BACKDROP_SLIDE_DELAY_MS = 10_000L;
    private static final long BACKDROP_SLIDE_RETRY_MS = 2_500L;
    private static final int SHORT_DRAMA_SCALE = 0;
    private static final int SHORT_DRAMA_FRAME_WIDTH = 9;
    private static final int SHORT_DRAMA_FRAME_HEIGHT = 16;
    private static final int INLINE_SIDE_CONTROL_MARGIN_DP = 4;
    private static final int INLINE_SIDE_CONTROL_FULLSCREEN_MARGIN_DP = 48;
    private static final long LEANBACK_FUSION_EXIT_DISPLAY_SUPPRESS_MS = 800;
    private static final float NORMAL_SPEED = 1.0f;
    private static final Pattern SOURCE_SEASON = Pattern.compile("(?i)(?:第\\s*([零〇一二三四五六七八九十两0-9]+)\\s*[季部]|season\\s*([0-9]{1,2})|s([0-9]{1,2})(?:[-._\\s]*e[0-9]{1,3})?)");

    private final TmdbService tmdbService = new TmdbService();
    private final IntroSkipPlayback introSkipPlayback = new IntroSkipPlayback();
    private final SubtitlePlaybackSession subtitlePlaybackSession = new SubtitlePlaybackSession(this);
    private final List<TmdbPerson> detailCastItems = new ArrayList<>();
    private final List<TmdbPerson> castItems = new ArrayList<>();
    private final List<TmdbPerson> creatorItems = new ArrayList<>();
    private final List<TmdbItem> relatedItems = new ArrayList<>();
    private final List<TmdbItem> personalTmdbItems = new ArrayList<>();
    private final List<TmdbItem> personalDoubanItems = new ArrayList<>();
    private final List<TmdbItem> personalAiItems = new ArrayList<>();
    private final Map<Integer, TmdbEpisode> tmdbEpisodes = new HashMap<>();
    private final List<Integer> seasonNumbers = new ArrayList<>();
    private final Map<Integer, Integer> seasonEpisodeCounts = new HashMap<>();
    private final Map<Integer, List<TmdbEpisode>> tmdbSeasonEpisodes = new HashMap<>();
    private final Map<Integer, List<TmdbPerson>> tmdbSeasonCast = new HashMap<>();
    private final Map<Integer, List<String>> tmdbSeasonPhotos = new HashMap<>();
    private final Set<Integer> loadingSeasons = new HashSet<>();
    private final Set<Integer> refreshedSingleSeasonProbes = new HashSet<>();
    private final Set<String> brokenSources = new HashSet<>();
    private final List<String> detailTmdbPhotos = new ArrayList<>();
    private final List<String> tmdbEpisodePhotos = new ArrayList<>();
    private final List<String> backdropSlideItems = new ArrayList<>();
    private final Set<String> backdropSlideFailures = new HashSet<>();
    private final Runnable backdropSlideNext = this::loadNextBackdropSlide;

    private ActivityTmdbDetailBinding binding;
    @androidx.annotation.Keep
    private ActivityTmdbDetailBinding mBinding;
    private Vod vod;
    private String sourceVodName;
    private History history;
    @androidx.annotation.Keep
    private History mHistory;
    private TmdbConfig tmdbConfig;
    private TmdbBundle activeTmdbBundle;
    private TmdbItem initialTmdbItem;
    private TmdbItem matchedTmdbItem;
    private JsonObject matchedTmdbDetail;
    private Flag selectedFlag;
    private Episode selectedEpisode;
    private TmdbEpisodeAdapter episodeAdapter;
    private TmdbPersonAdapter castAdapter;
    private TmdbPersonAdapter creatorAdapter;
    private TmdbPhotoAdapter episodePhotoAdapter;
    private TmdbRailAdapter relatedAdapter;
    private TmdbRailAdapter personalTmdbAdapter;
    private TmdbRailAdapter personalDoubanAdapter;
    private TmdbRailAdapter personalAiAdapter;
    private boolean overviewExpanded;
    private boolean useParse;
    private boolean inlineStarted;
    private boolean detailPlayerActive;
    private boolean autoPlayed;
    private boolean inlineFullscreen;
    private boolean inlineShortDramaMode;
    private boolean inlinePauseInfo;
    private boolean inlinePlaybackLoading;
    private boolean savingTmdbPhoto;
    private PlayerGesture inlineGestureDetector;
    private Clock inlineClock;
    private VodPlayerControlController inlineControlController;
    private InlineParseAdapter inlineParseAdapter;
    private PiP inlinePiP;
    private final Runnable inlineHideControls = this::hideInlineControlsIfIdle;
    private final Runnable inlineKeySeekEnd = this::onInlineKeySeekEnd;
    private Result pendingInlineResult;
    private Result currentInlineResult;
    private ViewGroup playerParent;
    private ViewGroup.LayoutParams playerLayoutParams;
    private ViewGroup inlinePiPParent;
    private ViewGroup.LayoutParams inlinePiPLayoutParams;
    private View detailControlRoot;
    private View detailActionRoot;
    private ViewGroup detailActionParent;
    private ViewGroup.LayoutParams detailActionLayoutParams;
    private int detailActionIndex;
    private View inlineControlFocus;
    private long lastInlineControlInteraction;
    private long inlineDisplaySuppressUntil;
    private long inlineKeySeekTime;
    private boolean inlineKeySpeedChanging;
    private float inlineGestureSpeed = 1.0f;
    private boolean inlineStartPositionApplied;
    private boolean inlinePiPLayout;
    private boolean inlinePiPLayoutRequested;
    private boolean inlinePiPSourceFrozen;
    private long inlineStartPosition = C.TIME_UNSET;
    private int selectedSeasonNumber = -1;
    private int playerIndex = -1;
    private int inlinePiPIndex = -1;
    private int requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private float inlinePiPTranslationZ;
    private int headerBarBasePaddingTop = -1;
    private int statusBarInsetTop;
    private int detailThemeMode;
    private int loadGeneration;
    private int inlinePlaybackGeneration;
    private int tmdbDialogGeneration;
    private int tmdbApplyGeneration;
    private int tmdbEpisodeDetailGeneration;
    private int sourceSearchGeneration;
    private int backdropSlideGeneration;
    private int backdropSlideIndex;
    private boolean episodeGridMode = Setting.getTmdbEpisodeGridMode();
    private boolean inlineEpisodeGridMode = true;
    private boolean episodeReverse;
    private boolean manualEpisodeRange;
    private boolean scrollEpisodeStartOnce;
    private int episodeRangeIndex;
    private int pendingEpisodeRangeFocus = -1;
    private boolean tmdbMediaLoading;
    private boolean lightTheme;
    private boolean backdropSlideLoading;
    private String backdropSlideTitle = "";
    private String backdropSlidePrimary = "";
    private ImageView backdropSlideVisibleView;
    private ImageView backdropSlideLoadingView;

    public static void start(Activity activity, String key, String id, String name, String pic, String mark) {
        start(activity, key, id, name, pic, mark, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, @Nullable TmdbItem tmdbItem) {
        start(activity, key, id, name, pic, mark, tmdbItem, Setting.getDetailOpenMode(), false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, @Nullable TmdbItem tmdbItem, int detailMode) {
        start(activity, key, id, name, pic, mark, tmdbItem, detailMode, false);
    }

    public static void startFusion(Activity activity, String key, String id, String name, String pic, String mark) {
        start(activity, key, id, name, pic, mark, null, Setting.DETAIL_OPEN_FUSION, false);
    }

    public static void startFusion(Activity activity, String key, String id, String name, String pic, String mark, @Nullable TmdbItem tmdbItem) {
        start(activity, key, id, name, pic, mark, tmdbItem, Setting.DETAIL_OPEN_FUSION, false);
    }

    public static void startCinema(Activity activity, String key, String id, String name, String pic, String mark) {
        start(activity, key, id, name, pic, mark, null, Setting.DETAIL_OPEN_CINEMA, false);
    }

    public static void startCinema(Activity activity, String key, String id, String name, String pic, String mark, @Nullable TmdbItem tmdbItem) {
        start(activity, key, id, name, pic, mark, tmdbItem, Setting.DETAIL_OPEN_CINEMA, false);
    }

    public static void startPlayback(Activity activity, String key, String id, String name, String pic, String mark, boolean fusion) {
        startPlayback(activity, key, id, name, pic, mark, fusion ? Setting.DETAIL_OPEN_FUSION : Setting.DETAIL_OPEN_ENHANCED);
    }

    public static void startPlayback(Activity activity, String key, String id, String name, String pic, String mark, int detailMode) {
        start(activity, key, id, name, pic, mark, null, detailMode, true);
    }

    private static void start(Activity activity, String key, String id, String name, String pic, String mark, @Nullable TmdbItem tmdbItem, int detailMode, boolean autoPlay) {
        if (!TextUtils.isEmpty(key) && !SiteApi.PUSH.equals(key) && AudioUtil.isAudioSiteEnabled(key)) {
            VideoActivity.startDirect(activity, key, id, name, pic, mark);
            return;
        }
        if (!TextUtils.isEmpty(key) && !SiteApi.PUSH.equals(key) && isShortDramaSiteEnabled(key)) {
            VideoActivity.startDirect(activity, key, id, name, pic, mark);
            return;
        }
        if (!TextUtils.isEmpty(key) && !SiteApi.PUSH.equals(key) && !isTmdbSiteEnabled(key)) {
            VideoActivity.startDirect(activity, key, id, name, pic, mark);
            return;
        }
        Intent intent = new Intent(activity, TmdbDetailActivity.class);
        boolean cinemaStyle = detailMode == Setting.DETAIL_OPEN_CINEMA;
        detailMode = normalizeDetailMode(detailMode);
        intent.putExtra("detail_mode", cinemaStyle ? Setting.DETAIL_OPEN_CINEMA : detailMode);
        intent.putExtra("fusion", detailMode == Setting.DETAIL_OPEN_FUSION);
        intent.putExtra("auto_play", autoPlay);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("mark", mark);
        putTmdbItem(intent, tmdbItem);
        activity.startActivity(intent);
    }

    private static int normalizeDetailMode(int detailMode) {
        return Setting.isTmdbMode(detailMode) ? detailMode : Setting.DETAIL_OPEN_ENHANCED;
    }

    private static void putTmdbItem(Intent intent, @Nullable TmdbItem item) {
        if (item == null || item.getTmdbId() <= 0 || TextUtils.isEmpty(item.getMediaType())) return;
        intent.putExtra("tmdb_id", item.getTmdbId());
        intent.putExtra("tmdb_media_type", item.getMediaType());
        intent.putExtra("tmdb_title", item.getTitle());
        intent.putExtra("tmdb_subtitle", item.getSubtitle());
        intent.putExtra("tmdb_overview", item.getOverview());
        intent.putExtra("tmdb_poster", item.getPosterUrl());
        intent.putExtra("tmdb_backdrop", item.getBackdropUrl());
        intent.putExtra("tmdb_credit", item.getCredit());
    }

    @Override
    protected androidx.viewbinding.ViewBinding getBinding() {
        return mBinding = binding = ActivityTmdbDetailBinding.inflate(getLayoutInflater());
    }

    @Override
    protected PlaybackService.NavigationCallback getNavigationCallback() {
        return mNavigationCallback;
    }

    @Override
    protected CustomSeekView getSeekView() {
        return inlineSeek();
    }

    @Override
    protected PlayerView getExoView() {
        return binding.exo;
    }

    @Override
    protected String getPlaybackKey() {
        return getHistoryKey();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        inflateMobileInlineControl();
        super.initView(savedInstanceState);
        tmdbConfig = TmdbConfig.objectFrom(Setting.getTmdbConfig());
        initialTmdbItem = getIntentTmdbItem();
        detailThemeMode = Setting.getTmdbDetailTheme();
        applyDetailEdgeToEdge();
        applySystemBarInsets();
        initPage();
        loadContent(null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        brokenSources.clear();
        resetDetailState();
        loadContent(null);
    }

    private void resetDetailState() {
        tmdbConfig = TmdbConfig.objectFrom(Setting.getTmdbConfig());
        initialTmdbItem = getIntentTmdbItem();
        vod = null;
        matchedTmdbItem = null;
        matchedTmdbDetail = null;
        history = null;
        mHistory = null;
        selectedFlag = null;
        selectedEpisode = null;
        resetEpisodeRange();
        inlineStarted = false;
        detailPlayerActive = false;
        autoPlayed = false;
        inlinePlaybackGeneration++;
        tmdbDialogGeneration++;
        tmdbApplyGeneration++;
        tmdbEpisodeDetailGeneration++;
        sourceSearchGeneration++;
        inlinePlaybackLoading = false;
        inlineStartPosition = C.TIME_UNSET;
        pendingInlineResult = null;
        currentInlineResult = null;
        activeTmdbBundle = null;
        useParse = false;
        detailTmdbPhotos.clear();
        tmdbEpisodePhotos.clear();
        personalTmdbItems.clear();
        personalDoubanItems.clear();
        personalAiItems.clear();
        if (service() != null) {
            subtitlePlaybackSession.stop(this);
            player().stop();
            player().clear();
        }
        binding.loading.setVisibility(View.VISIBLE);
        hideInlineLoading();
        binding.playerError.setVisibility(View.GONE);
        binding.playerControls.setVisibility(View.GONE);
        binding.detailControlHost.setVisibility(View.GONE);
        hideMobileFusionPlayerActionDock();
        binding.flagContainer.removeAllViews();
        binding.seasonContainer.removeAllViews();
        clearEpisodeRanges();
        if (episodeAdapter != null) episodeAdapter.setFallbackStillUrl(episodeFallbackStillUrl());
        episodeAdapter.setItems(List.of(), Map.of(), null);
        if (episodePhotoAdapter != null) episodePhotoAdapter.setItems(List.of());
        castAdapter.setItems(new ArrayList<>());
        creatorAdapter.setItems(new ArrayList<>());
        relatedAdapter.setItems(new ArrayList<>());
        personalTmdbAdapter.setItems(new ArrayList<>());
        personalDoubanAdapter.setItems(new ArrayList<>());
        personalAiAdapter.setItems(new ArrayList<>());
        showAiRecommendationReason(null, false);
        clearExternalLinks();
        binding.tmdbStatus.setVisibility(View.GONE);
        bindInitialArtwork();
    }

    private void initPage() {
        binding.play.setOnClickListener(view -> onPlay());
        binding.keep.setOnClickListener(view -> onKeep());
        binding.keepTop.setOnClickListener(view -> onKeep());
        binding.keepFusion.setOnClickListener(view -> onKeep());
        binding.rematch.setOnClickListener(view -> showManualTmdbMatchDialog());
        binding.rematchTop.setOnClickListener(view -> showManualTmdbMatchDialog());
        binding.rematchFusion.setOnClickListener(view -> showManualTmdbMatchDialog());
        binding.changeSource.setOnClickListener(view -> changeSource());
        binding.changeSourceDetail.setOnClickListener(view -> changeSource());
        binding.changeSource.setOnLongClickListener(view -> openGlobalSourceSearch());
        binding.changeSourceDetail.setOnLongClickListener(view -> openGlobalSourceSearch());
        binding.themeModeTop.setOnClickListener(view -> cycleThemeMode());
        binding.themeMode.setOnClickListener(view -> cycleThemeMode());
        binding.themeModeDetail.setOnClickListener(view -> cycleThemeMode());
        binding.episodeReverse.setOnClickListener(view -> toggleEpisodeReverse());
        binding.episodeViewMode.setOnClickListener(view -> toggleEpisodeViewMode());
        binding.overview.setOnClickListener(view -> toggleOverview());
        binding.overviewToggle.setOnClickListener(view -> toggleOverview());
        if (Util.isMobile()) binding.headerTitle.setText("");
        else binding.headerTitle.setText(detailModeTitle());
        binding.headerTitle.setVisibility(Util.isMobile() || !isCinemaMode() ? View.VISIBLE : View.INVISIBLE);
        binding.title.setText(getNameText());
        binding.subtitle.setText("");
        binding.sourceValue.setText(getString(R.string.detail_source_current, getKeyText()));
        binding.overviewToggle.setVisibility(View.GONE);
        binding.play.setText(R.string.detail_play_now);
        binding.keep.setText(R.string.keep);
        binding.playerPanel.setVisibility(isFusionMode() ? View.VISIBLE : View.GONE);
        binding.heroSpacer.setVisibility(isFusionMode() ? View.GONE : View.VISIBLE);
        binding.keepTop.setVisibility(View.GONE);
        binding.rematchTop.setVisibility(View.GONE);
        binding.headerBar.setVisibility(Util.isMobile() ? View.VISIBLE : View.GONE);
        updateDetailThemeButtonVisibility();
        binding.fusionActions.setVisibility(isFusionMode() ? View.VISIBLE : View.GONE);
        binding.detailActions.setVisibility(isFusionMode() ? View.GONE : View.VISIBLE);
        applyDetailTemplate();
        initFusionPlayer();
        binding.episodeEmpty.setText(R.string.detail_source_episode_empty);
        bindInitialArtwork();
        episodeAdapter = new TmdbEpisodeAdapter(new TmdbEpisodeAdapter.Listener() {
            @Override
            public void onItemClick(Episode episode) {
                cancelPendingInlinePlayback();
                selectedEpisode = episode;
                episodeAdapter.setSelected(episode);
                updatePlayLabel();
                onPlay();
            }

            @Override
            public void onItemLongClick(View anchor, Episode episode, int episodeNumber) {
                if (isFusionMode() && EpisodeTitlePopup.show(anchor, inlineEpisodeTitle(episode))) return;
                showTmdbEpisodeDetail(episode, episodeNumber);
            }
        });
        episodeAdapter.setNativeEnhanced(true);
        episodeAdapter.setOnFocusChangeListener(this::onDetailEpisodeFocusChange);
        episodeAdapter.setOnKeyListener(this::onDetailEpisodeKey);
        castAdapter = new TmdbPersonAdapter(this::loadPersonDetail);
        creatorAdapter = new TmdbPersonAdapter(this::loadPersonDetail);
        episodePhotoAdapter = new TmdbPhotoAdapter(this::showPhotoDialog);
        relatedAdapter = new TmdbRailAdapter(this::openRelatedItem);
        personalTmdbAdapter = new TmdbRailAdapter(this::openRelatedItem);
        personalDoubanAdapter = new TmdbRailAdapter(this::openRelatedItem);
        personalAiAdapter = new TmdbRailAdapter(this::openRelatedItem);
        personalAiAdapter.setOnItemLongClickListener(item -> {
            com.fongmi.android.tv.ui.dialog.AiRecommendationInfoDialog.show(this, item);
            return true;
        });
        personalAiAdapter.setOnItemFocusListener(this::showAiRecommendationReason);
        castAdapter.setCinema(isCinemaMode());
        creatorAdapter.setCinema(isCinemaMode());
        relatedAdapter.setCinema(isCinemaMode());
        personalTmdbAdapter.setCinema(isCinemaMode());
        personalDoubanAdapter.setCinema(isCinemaMode());
        personalAiAdapter.setCinema(isCinemaMode());
        setDetailAdaptersLight(resolveLightTheme());
        updateEpisodeLayoutManager();
        binding.episodeContainer.setItemAnimator(null);
        binding.episodeContainer.setHasFixedSize(false);
        binding.episodeContainer.setNestedScrollingEnabled(false);
        binding.episodeContainer.setAdapter(episodeAdapter);
        binding.episodePhotoList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.episodePhotoList.setNestedScrollingEnabled(false);
        binding.episodePhotoList.setAdapter(episodePhotoAdapter);
        binding.castList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.castList.setNestedScrollingEnabled(false);
        binding.castList.setAdapter(castAdapter);
        binding.creatorList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.creatorList.setNestedScrollingEnabled(false);
        binding.creatorList.setAdapter(creatorAdapter);
        binding.relatedList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.relatedList.setNestedScrollingEnabled(false);
        binding.relatedList.setAdapter(relatedAdapter);
        binding.personalTmdbList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.personalTmdbList.setNestedScrollingEnabled(false);
        binding.personalTmdbList.setAdapter(personalTmdbAdapter);
        binding.personalDoubanList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.personalDoubanList.setNestedScrollingEnabled(false);
        binding.personalDoubanList.setAdapter(personalDoubanAdapter);
        binding.personalAiList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.personalAiList.setNestedScrollingEnabled(false);
        binding.personalAiList.setAdapter(personalAiAdapter);
        applyDetailTheme();
    }

    private void applySystemBarInsets() {
        if (headerBarBasePaddingTop < 0) headerBarBasePaddingTop = binding.headerBar.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, (view, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            statusBarInsetTop = top;
            binding.headerBar.setPadding(
                    binding.headerBar.getPaddingLeft(),
                    headerBarBasePaddingTop + top,
                    binding.headerBar.getPaddingRight(),
                    binding.headerBar.getPaddingBottom()
            );
            if (!isCinemaMode()) setHeightDp(binding.heroSpacer, defaultHeroSpacerHeightDp());
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.root);
    }

    private void applyDetailEdgeToEdge() {
        Window window = getWindow();
        if (window == null) return;
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        WindowInsetsControllerCompat insets = WindowCompat.getInsetsController(window, window.getDecorView());
        boolean lightBars = lightTheme && !isFusionMode();
        insets.setAppearanceLightStatusBars(lightBars);
        insets.setAppearanceLightNavigationBars(lightBars);
    }

    private void initFusionPlayer() {
        inlineControlController = new VodPlayerControlController(new VodPlayerControlController.Host() {
            @Override
            public com.fongmi.android.tv.player.PlayerManager player() {
                return service() == null ? null : TmdbDetailActivity.this.player();
            }

            @Override
            public void showDanmakuDialog() {
                showInlineDanmaku();
            }

            @Override
            public void showPlayerInfoDialog() {
                showInlinePlayerInfo();
            }

            @Override
            public void onDanmakuStateChanged(boolean show) {
                setInlineDanmakuIcon(show);
            }
        });
        inlinePiP = new PiP();
        inlineClock = Clock.create();
        inlineClock.setCallback(this);
        inlineClock.start();
        inlineGestureDetector = PlayerGesture.create(this, binding.playerPanel, this);
        setupPlayerPanelFocusLayer();
        binding.playerPanel.setOnTouchListener(this::onInlineTouch);
        binding.playerPanel.setOnKeyListener(this::onInlinePanelKey);
        binding.playerPanel.setOnFocusChangeListener((view, focused) -> updatePlayerPanelFocus());
        binding.playerPanel.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (!inlinePiPSourceFrozen) inlinePiP.update(this, view);
        });
        setupInlineControlFocus();
        setupInlineFocusNavigation();
        binding.playerPrev.setOnClickListener(view -> checkInlinePrev());
        binding.playerNext.setOnClickListener(view -> checkInlineNext());
        binding.playerQuality.setOnClickListener(view -> showInlineQuality());
        binding.playerParse.setOnClickListener(view -> cycleInlineParse());
        binding.playerSpeed.setOnClickListener(view -> changeInlineSpeed());
        binding.playerSpeed.setOnLongClickListener(view -> resetInlineSpeed());
        binding.playerScale.setOnClickListener(view -> cycleInlineScale());
        binding.playerRefresh.setOnClickListener(view -> refreshInlinePlayback());
        binding.playerChangeSource.setOnClickListener(view -> changeSource());
        binding.playerRepeat.setOnClickListener(view -> toggleInlineRepeat());
        binding.playerDisplay.setOnClickListener(view -> showInlineDisplay());
        binding.playerDecode.setOnClickListener(view -> toggleInlineDecode());
        binding.playerTextTrack.setOnClickListener(this::showInlineTrack);
        binding.playerTextTrack.setOnLongClickListener(view -> showInlineSubtitle());
        binding.playerAudioTrack.setOnClickListener(this::showInlineTrack);
        binding.playerVideoTrack.setOnClickListener(this::showInlineTrack);
        binding.playerOpening.setOnClickListener(view -> setInlineOpeningFromPosition());
        binding.playerOpening.setOnLongClickListener(view -> resetInlineOpening());
        binding.playerEnding.setOnClickListener(view -> setInlineEndingFromPosition());
        binding.playerEnding.setOnLongClickListener(view -> resetInlineEnding());
        binding.playerDanmakuToggle.setOnClickListener(view -> toggleInlineDanmaku());
        binding.playerDanmaku.setOnClickListener(view -> showInlineDanmaku());
        binding.playerChapter.setOnClickListener(view -> showInlineTitle());
        binding.playerExternal.setOnClickListener(view -> showInlinePlayerChoice());
        binding.playerExternal.setOnLongClickListener(view -> showInlinePlayerChoice());
        binding.playerEpisodes.setOnClickListener(view -> showInlineEpisodes());
        binding.playerFullscreenAction.setOnClickListener(view -> toggleInlineFullscreen());
        binding.playerFullscreen.setOnClickListener(view -> toggleInlineFullscreen());
        binding.playerFullscreen.setFocusable(false);
        binding.playerFullscreen.setFocusableInTouchMode(false);
        if (!Util.isMobile()) binding.playerFullscreen.setVisibility(View.GONE);
        binding.playerCast.setOnClickListener(view -> onInlineCast());
        binding.playerInfo.setOnClickListener(view -> onInlineInfo());
        binding.playerControls.setOnTouchListener(this::onInlineControlTouch);
        setupMobileInlineControl();
        hideInlineControls();
        updateInlineButtons(false);
        focusInlinePlayerPanel();
    }

    private void setupMobileInlineControl() {
        if (!Util.isMobile()) {
            binding.detailControlHost.setVisibility(View.GONE);
            return;
        }
        inflateMobileInlineControl();
        binding.playerControls.setVisibility(View.GONE);
        detailControlView(R.id.back, View.class).setOnClickListener(view -> onInlineBack());
        detailControlView(R.id.play, View.class).setOnClickListener(view -> toggleInlinePlayback());
        detailControlView(R.id.prev, View.class).setOnClickListener(view -> checkInlinePrev());
        detailControlView(R.id.next, View.class).setOnClickListener(view -> checkInlineNext());
        detailControlView(R.id.fullscreen, View.class).setOnClickListener(view -> toggleInlineFullscreen());
        detailControlView(R.id.cast, View.class).setOnClickListener(view -> onInlineCast());
        detailControlView(R.id.info, View.class).setOnClickListener(view -> onInlineInfo());
        detailControlView(R.id.keep, View.class).setOnClickListener(view -> onKeep());
        detailControlView(R.id.setting, View.class).setOnClickListener(view -> showInlineDisplay());
        detailControlView(R.id.danmaku, View.class).setOnClickListener(view -> toggleInlineDanmaku());
        detailControlView(R.id.lock, View.class).setOnClickListener(view -> toggleInlineLock());
        detailControlView(R.id.rotate, View.class).setOnClickListener(view -> rotateInlineFullscreen());
        detailControlView(R.id.pip, View.class).setOnClickListener(view -> enterInlinePiP(true));
        detailActionView(R.id.player, View.class).setOnClickListener(view -> showInlinePlayerChoice());
        detailActionView(R.id.player, View.class).setOnLongClickListener(view -> showInlinePlayerChoice());
        detailActionView(R.id.decode, View.class).setOnClickListener(view -> toggleInlineDecode());
        detailActionView(R.id.speed, View.class).setOnClickListener(view -> changeInlineSpeed());
        detailActionView(R.id.speed, View.class).setOnLongClickListener(view -> resetInlineSpeed());
        detailActionView(R.id.scale, View.class).setOnClickListener(view -> cycleInlineScale());
        detailActionView(R.id.actionQuality, View.class).setOnClickListener(view -> showInlineQuality());
        detailActionView(R.id.reset, View.class).setOnClickListener(view -> refreshInlinePlayback());
        detailActionView(R.id.repeat, View.class).setOnClickListener(view -> toggleInlineRepeat());
        detailActionView(R.id.text, View.class).setOnClickListener(this::showInlineTrack);
        detailActionView(R.id.text, View.class).setOnLongClickListener(view -> showInlineSubtitle());
        detailActionView(R.id.audio, View.class).setOnClickListener(this::showInlineTrack);
        detailActionView(R.id.video, View.class).setOnClickListener(this::showInlineTrack);
        detailActionView(R.id.opening, View.class).setOnClickListener(view -> setInlineOpeningFromPosition());
        detailActionView(R.id.opening, View.class).setOnLongClickListener(view -> resetInlineOpening());
        detailActionView(R.id.ending, View.class).setOnClickListener(view -> setInlineEndingFromPosition());
        detailActionView(R.id.ending, View.class).setOnLongClickListener(view -> resetInlineEnding());
        detailActionView(R.id.danmaku, View.class).setOnClickListener(view -> showInlineDanmaku());
        detailActionView(R.id.chapter, View.class).setOnClickListener(view -> showInlineTitle());
        detailActionView(R.id.episodes, View.class).setOnClickListener(view -> showInlineEpisodes());
        setupMobileInlineParse();
        detailControlRoot.setOnTouchListener(this::onInlineControlTouch);
    }

    private void setupMobileInlineParse() {
        RecyclerView parse = detailControlView(R.id.parse, RecyclerView.class);
        parse.setHasFixedSize(true);
        parse.setItemAnimator(null);
        parse.setAdapter(inlineParseAdapter = new InlineParseAdapter());
    }

    private void inflateMobileInlineControl() {
        if (!Util.isMobile() || detailControlRoot != null) return;
        detailControlRoot = getLayoutInflater().inflate(R.layout.view_control_vod_tmdb, binding.detailControlHost, false);
        binding.detailControlHost.removeAllViews();
        binding.detailControlHost.addView(detailControlRoot);
        detailActionRoot = detailControlRoot.findViewById(R.id.action);
        detailActionParent = (ViewGroup) detailActionRoot.getParent();
        detailActionLayoutParams = detailActionRoot.getLayoutParams();
        detailActionIndex = detailActionParent.indexOfChild(detailActionRoot);
    }

    private <T extends View> T detailControlView(int id, Class<T> type) {
        return type.cast(detailControlRoot.findViewById(id));
    }

    private <T extends View> T detailActionView(int id, Class<T> type) {
        return type.cast(detailActionRoot.findViewById(id));
    }

    private View inlineControlsView() {
        return Util.isMobile() ? binding.detailControlHost : binding.playerControls;
    }

    private CustomSeekView inlineSeek() {
        return Util.isMobile() && detailControlRoot != null ? detailControlView(R.id.seek, CustomSeekView.class) : binding.seek;
    }

    private void setupInlineFocusNavigation() {
        if (Util.isMobile()) return;
        View timeBar = inlineSeek().findViewById(R.id.timeBar);
        if (timeBar != null) {
            timeBar.setNextFocusUpId(R.id.playerFullscreenAction);
            timeBar.setNextFocusRightId(R.id.timeBar);
        }
        binding.playerFullscreenAction.setNextFocusLeftId(R.id.playerRepeat);
        binding.playerFullscreenAction.setNextFocusDownId(R.id.timeBar);
        binding.playerRepeat.setNextFocusRightId(R.id.playerFullscreenAction);
        binding.playerExternal.setNextFocusUpId(R.id.playerFullscreenAction);
        binding.playerDecode.setNextFocusUpId(R.id.playerFullscreenAction);
        binding.playerDisplay.setNextFocusUpId(R.id.playerFullscreenAction);
        binding.playerEpisodes.setNextFocusUpId(R.id.playerFullscreenAction);
    }

    private void setupInlineControlFocus() {
        setupInlineControl(binding.playerCast);
        setupInlineControl(binding.playerInfo);
        setupInlineControl(binding.playerFullscreenAction);
        setupInlineControl(binding.playerPrev);
        setupInlineControl(binding.playerNext);
        setupInlineControl(binding.playerExternal);
        setupInlineControl(binding.playerDecode);
        setupInlineControl(binding.playerSpeed);
        setupInlineControl(binding.playerScale);
        setupInlineControl(binding.playerRefresh);
        setupInlineControl(binding.playerChangeSource);
        setupInlineControl(binding.playerRepeat);
        setupInlineControl(binding.playerDisplay);
        setupInlineControl(binding.playerQuality);
        setupInlineControl(binding.playerParse);
        setupInlineControl(binding.playerTextTrack);
        setupInlineControl(binding.playerAudioTrack);
        setupInlineControl(binding.playerVideoTrack);
        setupInlineControl(binding.playerOpening);
        setupInlineControl(binding.playerEnding);
        setupInlineControl(binding.playerDanmakuToggle);
        setupInlineControl(binding.playerDanmaku);
        setupInlineControl(binding.playerChapter);
        setupInlineControl(binding.playerEpisodes);
    }

    private void setupInlineControl(View view) {
        view.setClickable(true);
        view.setFocusable(true);
        view.setOnFocusChangeListener((control, focused) -> {
            if (focused) inlineControlFocus = control;
            updatePlayerPanelFocus();
        });
    }

    private boolean hasFocusedChild(View view) {
        if (view == null) return false;
        if (view.hasFocus()) return true;
        if (!(view instanceof ViewGroup group)) return false;
        for (int i = 0; i < group.getChildCount(); i++) if (hasFocusedChild(group.getChildAt(i))) return true;
        return false;
    }

    private boolean onInlineTouch(View view, MotionEvent event) {
        if (!inlineStarted || !isInlinePlayerMode() || service() == null || player() == null || player().isEmpty()) return false;
        if (inlineGestureDetector != null) inlineGestureDetector.onTouchEvent(event);
        return true;
    }

    @Override
    public void onSeeking(long time) {
        if (!isInlinePlayerMode() || service() == null || player() == null || player().isEmpty()) return;
        inlinePauseInfo = false;
        binding.gestureAction.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        binding.gestureTime.setText(player().getPositionTime(time));
        binding.gestureDuration.setText(player().getDurationTime());
        binding.gestureSeek.setVisibility(View.VISIBLE);
        hideInlineControls();
        updateInlineDisplayPanel();
    }

    private void showInlinePauseInfo() {
        if (!isInlinePlayerMode() || service() == null || player() == null || player().isEmpty()) return;
        if (Util.isMobile()) {
            hideInlinePauseInfo();
            return;
        }
        inlinePauseInfo = true;
        binding.gestureAction.setImageResource(R.drawable.ic_widget_play);
        binding.gestureTime.setText(player().getPositionTime(0));
        binding.gestureDuration.setText(player().getDurationTime());
        binding.gestureSeek.setVisibility(View.VISIBLE);
        updateInlineDisplayPanel();
    }

    private void hideInlinePauseInfo() {
        inlinePauseInfo = false;
        binding.gestureSeek.setVisibility(View.GONE);
        updateInlineDisplayPanel();
    }

    @Override
    public void onSeekEnd(long time) {
        if (!isInlinePlayerMode() || controller() == null || service() == null || player() == null || player().isEmpty()) return;
        seekTo(time);
    }

    @Override
    public void onSpeedUp() {
        if (!isInlinePlayerMode() || service() == null || player() == null || player().isEmpty() || !player().isPlaying()) return;
        inlineGestureSpeed = player().getSpeed();
        binding.gestureSpeed.setVisibility(View.VISIBLE);
        binding.gestureSpeed.startAnimation(ResUtil.getAnim(R.anim.forward));
        setInlineSpeed(PlayerSetting.getSpeed());
        hideInlineControls();
    }

    @Override
    public void onSpeedEnd() {
        binding.gestureSpeed.clearAnimation();
        if (!isInlinePlayerMode() || service() == null || player() == null || player().isEmpty()) return;
        float speed = history == null ? inlineGestureSpeed : history.getSpeed();
        setInlineSpeed(speed);
    }

    @Override
    public void onBright(int progress) {
        binding.gestureBright.setVisibility(View.VISIBLE);
        binding.gestureBrightProgress.setProgress(progress);
        if (progress < 35) binding.gestureBrightIcon.setImageResource(R.drawable.ic_widget_bright_low);
        else if (progress < 70) binding.gestureBrightIcon.setImageResource(R.drawable.ic_widget_bright_medium);
        else binding.gestureBrightIcon.setImageResource(R.drawable.ic_widget_bright_high);
    }

    @Override
    public void onVolume(int progress) {
        binding.gestureVolume.setVisibility(View.VISIBLE);
        binding.gestureVolumeProgress.setProgress(progress);
        if (progress < 35) binding.gestureVolumeIcon.setImageResource(R.drawable.ic_widget_volume_low);
        else if (progress < 70) binding.gestureVolumeIcon.setImageResource(R.drawable.ic_widget_volume_medium);
        else binding.gestureVolumeIcon.setImageResource(R.drawable.ic_widget_volume_high);
    }

    @Override
    public void onFlingUp() {
        if (!isInlinePlayerMode() || selectedFlag == null || selectedFlag.getEpisodes() == null) return;
        if (selectedFlag.getEpisodes().size() == 1) refreshInlinePlayback();
        else checkInlineNext();
    }

    @Override
    public void onFlingDown() {
        if (!isInlinePlayerMode() || selectedFlag == null || selectedFlag.getEpisodes() == null) return;
        if (selectedFlag.getEpisodes().size() == 1) refreshInlinePlayback();
        else checkInlinePrev();
    }

    @Override
    public void onSingleTap() {
        if (!inlineStarted) onPlay();
        else toggleInlineControls();
    }

    @Override
    public void onDoubleTap() {
        if (!inlineStarted) {
            onPlay();
        } else if (isLock()) {
            showInlineControls(true, false);
        } else if (!inlineFullscreen) {
            enterInlineFullscreen();
        } else {
            toggleInlinePlayback();
        }
    }

    @Override
    public void onTouchEnd() {
        hideInlineGestureOverlays();
    }

    private void hideInlineGestureOverlays() {
        if (binding == null) return;
        inlinePauseInfo = false;
        hideInlineGestureViews();
        updateInlineDisplayPanel();
    }

    private void hideInlineGestureViews() {
        binding.gestureSeek.setVisibility(View.GONE);
        binding.gestureSpeed.setVisibility(View.GONE);
        binding.gestureBright.setVisibility(View.GONE);
        binding.gestureVolume.setVisibility(View.GONE);
    }

    private boolean onInlineControlTouch(View view, MotionEvent event) {
        if (isInlineControlsVisible()) touchInlineControls();
        return false;
    }

    private boolean onInlinePanelKey(View view, int keyCode, KeyEvent event) {
        if (!KeyUtil.isEnterKey(event)) return false;
        if (KeyUtil.isActionUp(event)) onInlinePanelConfirm();
        return true;
    }

    private void onInlinePanelConfirm() {
        if (!isInlinePlayerMode()) return;
        if (!inlineStarted) {
            onPlay();
        } else if (isLock() && inlineFullscreen) {
            showInlineControls(true, false);
        } else if (isInlineControlsVisible()) {
            hideInlineControls();
        } else if (inlineFullscreen) {
            toggleInlinePlayback();
        } else {
            showInlineControls(true);
        }
    }

    private void cycleThemeMode() {
        detailThemeMode = Setting.nextTmdbDetailTheme(detailThemeMode);
        Setting.putTmdbDetailTheme(detailThemeMode);
        applyDetailTheme();
        if (vod != null) {
            bindMeta();
            renderFlagSelection();
            renderSeasonSelection();
            renderEpisodes();
        }
    }

    private void applyDetailTheme() {
        lightTheme = resolveLightTheme();
        applyDetailEdgeToEdge();
        ThemeColors colors = currentThemeColors();
        applyBackdropSurface(colors);
        binding.backdropFill.setAlpha(backdropSlideAlpha());
        binding.backdrop.setAlpha(backdropSlideAlpha());
        binding.backdropShade.setBackground(isCinemaMode() ? cinemaBackdropShade() : colorDrawable(colors.backdropShade));
        setCard(binding.contentPanel, colors.panel, colors.line);
        setPlayerCard(colors);
        setCard(binding.tmdbPanel, colors.panel, colors.line);
        applyTemplateCardChrome(colors);
        tintTextTree(binding.getRoot(), colors);
        setDetailActionButton(binding.keep, colors);
        setDetailActionButton(binding.keepTop, colors);
        setDetailActionButton(binding.keepFusion, colors);
        setDetailActionButton(binding.rematch, colors);
        setDetailActionButton(binding.rematchTop, colors);
        setDetailActionButton(binding.rematchFusion, colors);
        setDetailActionButton(binding.changeSource, colors);
        setDetailActionButton(binding.changeSourceDetail, colors);
        setDetailActionButton(binding.themeModeTop, colors);
        setDetailActionButton(binding.themeMode, colors);
        setDetailActionButton(binding.themeModeDetail, colors);
        setButton(binding.episodeReverse, colors.control, colors.line, colors.primary);
        setButton(binding.episodeViewMode, colors.control, colors.line, colors.primary);
        setButton(binding.play, colors.play, colors.play, 0xFFFFFFFF);
        binding.headerTitle.setTextColor(colors.primary);
        binding.title.setTextColor(colors.primary);
        binding.subtitle.setTextColor(colors.secondary);
        styleSourceValue();
        binding.overview.setTextColor(colors.body);
        binding.overviewToggle.setTextColor(colors.accent);
        binding.episodeEmpty.setTextColor(colors.secondary);
        binding.tmdbStatus.setTextColor(colors.secondary);
        binding.personalAiReason.setTextColor(isCinemaMode() ? 0xE6FFFFFF : colors.secondary);
        if (isCinemaMode()) binding.personalAiReason.setShadowLayer(3f, 0f, 1.5f, 0xCC000000);
        else binding.personalAiReason.setShadowLayer(0f, 0f, 0f, 0x00000000);
        tintTmdbSectionTitles(colors);
        binding.themeModeTop.setText(themeModeLabel());
        binding.themeMode.setText(themeModeLabel());
        binding.themeModeDetail.setText(themeModeLabel());
        updateDetailThemeButtonVisibility();
        tintInlineGestureOverlay();
        if (isInlinePlayerMode()) {
            binding.playerError.setTextColor(0xFFFFFFFF);
            binding.playerTitle.setTextColor(0xFFFFFFFF);
            tintInlineControl(inlineControlsView());
            tintInlineDisplay();
            tintInlineLoading();
        }
        if (episodeAdapter != null) {
            episodeAdapter.setLight(lightTheme);
            episodeAdapter.setActiveStrokeColor(colors.accent);
        }
        if (episodePhotoAdapter != null) episodePhotoAdapter.setLight(lightTheme);
        setDetailAdaptersLight(lightTheme);
        if (isCinemaMode()) scheduleBackdropSlide(BACKDROP_SLIDE_DELAY_MS);
    }

    private void styleSourceValue() {
        int titleColor = binding.flagTitle.getCurrentTextColor();
        binding.sourceValue.setAlpha(1f);
        binding.sourceValue.setTextColor(titleColor);
        binding.sourceValue.setLinkTextColor(titleColor);
        binding.sourceValue.setTextSize(TypedValue.COMPLEX_UNIT_PX, binding.flagTitle.getTextSize());
        binding.sourceValue.setTypeface(binding.flagTitle.getTypeface());
    }

    private void setDetailAdaptersLight(boolean light) {
        if (castAdapter != null) castAdapter.setLight(light);
        if (creatorAdapter != null) creatorAdapter.setLight(light);
        if (relatedAdapter != null) relatedAdapter.setLight(light);
        if (personalTmdbAdapter != null) personalTmdbAdapter.setLight(light);
        if (personalDoubanAdapter != null) personalDoubanAdapter.setLight(light);
        if (personalAiAdapter != null) personalAiAdapter.setLight(light);
    }

    private void tintTmdbSectionTitles(ThemeColors colors) {
        TextView[] titles = {
                binding.episodePhotoTitle,
                binding.castTitle,
                binding.creatorTitle,
                binding.relatedTitle,
                binding.personalTmdbTitle,
                binding.personalDoubanTitle,
                binding.personalAiTitle,
                binding.externalLinksTitle
        };
        int color = isCinemaMode() ? 0xFFFFFFFF : colors.primary;
        for (TextView title : titles) {
            title.setTextColor(color);
            if (isCinemaMode()) title.setShadowLayer(3f, 0f, 1.5f, 0xCC000000);
            else title.setShadowLayer(0f, 0f, 0f, 0x00000000);
        }
    }

    private void updateDetailThemeButtonVisibility() {
        if (binding == null) return;
        boolean mobile = Util.isMobile();
        boolean pictureInPicture = isInPictureInPictureMode();
        boolean showMobileButton = DetailThemeVisibility.showMobileThemeButton(mobile, inlineFullscreen, inlinePiPLayout, pictureInPicture);
        boolean showLargeScreenButton = DetailThemeVisibility.showLargeScreenThemeButton(mobile, inlineFullscreen, inlinePiPLayout, pictureInPicture);
        binding.themeModeTop.setVisibility(showMobileButton ? View.VISIBLE : View.GONE);
        binding.themeMode.setVisibility(showLargeScreenButton ? View.VISIBLE : View.GONE);
        binding.themeModeDetail.setVisibility(showLargeScreenButton ? View.VISIBLE : View.GONE);
    }

    private void applyTemplateCardChrome(ThemeColors colors) {
        if (isCinemaMode()) {
            binding.contentPanel.setCardBackgroundColor(0x00000000);
            binding.contentPanel.setStrokeWidth(0);
            binding.tmdbPanel.setCardBackgroundColor(0x00000000);
            binding.tmdbPanel.setStrokeWidth(0);
        } else {
            binding.contentPanel.setCardBackgroundColor(colors.panel);
            binding.contentPanel.setStrokeColor(colors.line);
            binding.contentPanel.setStrokeWidth(ResUtil.dp2px(1));
            binding.tmdbPanel.setCardBackgroundColor(colors.panel);
            binding.tmdbPanel.setStrokeColor(colors.line);
            binding.tmdbPanel.setStrokeWidth(ResUtil.dp2px(1));
        }
    }

    private void applyDetailTemplate() {
        if (isCinemaMode()) applyCinemaDetailTemplate();
        else applyDefaultDetailTemplate();
    }

    private void applyDefaultDetailTemplate() {
        setPaddingDp(binding.pageContent, 0, 0, 0, 28);
        setHeightDp(binding.heroSpacer, defaultHeroSpacerHeightDp());
        setWidthMatch(binding.contentPanel);
        setWidthMatch(binding.tmdbSection);
        setMarginsDp(binding.contentPanel, 16, 0, 16, 0);
        setMarginsDp(binding.playerPanel, 16, isFusionMode() ? 22 : 14, 16, isFusionMode() ? 20 : 16);
        setMarginsDp(binding.tmdbSection, 16, 16, 16, 0);
        binding.contentPanel.setRadius(ResUtil.dp2px(20));
        binding.tmdbPanel.setRadius(ResUtil.dp2px(20));
        setPaddingDp(binding.contentInner, 16, 16, 16, 16);
        binding.heroRow.setOrientation(LinearLayout.HORIZONTAL);
        binding.heroRow.setGravity(0);
        binding.posterCard.setVisibility(View.VISIBLE);
        LinearLayout.LayoutParams posterParams = new LinearLayout.LayoutParams(ResUtil.dp2px(112), ResUtil.dp2px(160));
        binding.posterCard.setLayoutParams(posterParams);
        binding.posterCard.setRadius(ResUtil.dp2px(16));
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoParams.setMarginStart(ResUtil.dp2px(14));
        binding.detailInfo.setLayoutParams(infoParams);
        setWidthMatch(binding.detailActions);
        setWidthMatch(binding.flagHeader);
        setWidthMatch(binding.flagScroll);
        binding.title.setTextSize(28f);
        binding.overview.setTextSize(13f);
        setHeightDp(binding.episodePhotoList, 124);
        setHeightDp(binding.castList, 180);
        setHeightDp(binding.creatorList, 180);
        setHeightDp(binding.relatedList, 262);
        setHeightDp(binding.personalTmdbList, 262);
        setHeightDp(binding.personalDoubanList, 262);
        setHeightDp(binding.personalAiList, 262);
    }

    private int defaultHeroSpacerHeightDp() {
        if (isFusionMode()) return 0;
        if (!Util.isMobile()) return 102;
        int insetDp = Math.round(statusBarInsetTop / getResources().getDisplayMetrics().density);
        return Math.max(72, 102 - Math.min(insetDp, 30));
    }

    private void applyCinemaDetailTemplate() {
        boolean compact = isCompactWidth();
        int side = ResUtil.dp2px(compact ? 18 : 56);
        int topWidth = compact ? ViewGroup.LayoutParams.MATCH_PARENT : Math.min(ResUtil.dp2px(760), (int) (getResources().getDisplayMetrics().widthPixels * 0.54f));
        setPaddingDp(binding.pageContent, 0, 0, 0, 44);
        setHeightDp(binding.heroSpacer, compact ? 50 : 28);
        setWidthMatch(binding.contentPanel);
        setWidthMatch(binding.tmdbSection);
        setMarginsPx(binding.contentPanel, side, compact ? 6 : 18, side, 0);
        setMarginsPx(binding.tmdbSection, side, compact ? 22 : 24, side, 0);
        binding.contentPanel.setRadius(0);
        binding.tmdbPanel.setRadius(0);
        setPaddingDp(binding.contentInner, 0, 0, 0, 0);
        binding.heroRow.setOrientation(LinearLayout.HORIZONTAL);
        binding.heroRow.setGravity(compact ? 0 : android.view.Gravity.CENTER_VERTICAL);
        binding.posterCard.setVisibility(View.GONE);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(topWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoParams.setMarginStart(0);
        binding.detailInfo.setLayoutParams(infoParams);
        setWidthPx(binding.detailActions, topWidth);
        setWidthMatch(binding.flagHeader);
        setWidthMatch(binding.flagScroll);
        binding.title.setTextSize(compact ? 38f : 44f);
        binding.subtitle.setTextSize(compact ? 13f : 14f);
        binding.overview.setTextSize(compact ? 14.5f : 16f);
        binding.overview.setLineSpacing(ResUtil.dp2px(compact ? 4 : 3), 1f);
        setHeightDp(binding.episodePhotoList, compact ? 128 : 160);
        setHeightDp(binding.castList, compact ? 112 : 112);
        setHeightDp(binding.creatorList, compact ? 112 : 112);
        setHeightDp(binding.relatedList, compact ? 184 : 190);
        setHeightDp(binding.personalTmdbList, compact ? 184 : 190);
        setHeightDp(binding.personalDoubanList, compact ? 184 : 190);
        setHeightDp(binding.personalAiList, compact ? 184 : 190);
        if (castAdapter != null) castAdapter.setCinema(true);
        if (creatorAdapter != null) creatorAdapter.setCinema(true);
        if (relatedAdapter != null) relatedAdapter.setCinema(true);
        if (personalTmdbAdapter != null) personalTmdbAdapter.setCinema(true);
        if (personalDoubanAdapter != null) personalDoubanAdapter.setCinema(true);
        if (personalAiAdapter != null) personalAiAdapter.setCinema(true);
    }

    private boolean isCompactWidth() {
        return getResources().getConfiguration().smallestScreenWidthDp < 600;
    }

    private void setPaddingDp(View view, int left, int top, int right, int bottom) {
        view.setPadding(ResUtil.dp2px(left), ResUtil.dp2px(top), ResUtil.dp2px(right), ResUtil.dp2px(bottom));
    }

    private void setMarginsDp(View view, int left, int top, int right, int bottom) {
        setMarginsPx(view, ResUtil.dp2px(left), ResUtil.dp2px(top), ResUtil.dp2px(right), ResUtil.dp2px(bottom));
    }

    private void setMarginsPx(View view, int left, int top, int right, int bottom) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams marginParams)) return;
        marginParams.setMargins(left, top, right, bottom);
        view.setLayoutParams(marginParams);
    }

    private void setHeightDp(View view, int height) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = ResUtil.dp2px(height);
        view.setLayoutParams(params);
    }

    private void setWidthPx(View view, int width) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = width;
        view.setLayoutParams(params);
    }

    private void setWidthMatch(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        view.setLayoutParams(params);
    }

    private Drawable colorDrawable(int color) {
        return new android.graphics.drawable.ColorDrawable(color);
    }

    private Drawable cinemaBackdropShade() {
        if (lightTheme) return cinemaLightBackdropShade();
        boolean compact = isCompactWidth();
        GradientDrawable horizontal = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, compact ? new int[]{
                0xEC090B0F, 0xD6090B0F, 0x78090B0F, 0x42090B0F, 0x96090B0F
        } : new int[]{
                0xF2090B0F, 0xDC090B0F, 0x82090B0F, 0x48090B0F, 0xA6090B0F
        });
        GradientDrawable vertical = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, compact ? new int[]{
                0x10090B0F, 0x26090B0F, 0xA6090B0F, 0xE6090B0F
        } : new int[]{
                0x12090B0F, 0x2D090B0F, 0xB8090B0F, 0xF0090B0F
        });
        return new LayerDrawable(new Drawable[]{horizontal, vertical});
    }

    private Drawable cinemaLightBackdropShade() {
        boolean compact = isCompactWidth();
        GradientDrawable horizontal = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, compact ? new int[]{
                0xB8F4F7FA, 0x99F4F7FA, 0x55F4F7FA, 0x24F4F7FA, 0x70F4F7FA
        } : new int[]{
                0x99F4F7FA, 0x80F4F7FA, 0x40F4F7FA, 0x1AF4F7FA, 0x55F4F7FA
        });
        GradientDrawable vertical = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, compact ? new int[]{
                0x0AF4F7FA, 0x18F4F7FA, 0x55F4F7FA, 0x99F4F7FA
        } : new int[]{
                0x04F4F7FA, 0x0FF4F7FA, 0x3DF4F7FA, 0x70F4F7FA
        });
        return new LayerDrawable(new Drawable[]{horizontal, vertical});
    }

    private void tintInlineControl(View view) {
        if (view instanceof TextView textView) textView.setTextColor(0xFFFFFFFF);
        if (!(view instanceof ViewGroup group)) return;
        for (int i = 0; i < group.getChildCount(); i++) tintInlineControl(group.getChildAt(i));
    }

    private void tintInlineDisplay() {
        binding.playerDisplayTitle.setTextColor(0xFFFFFFFF);
        binding.playerDisplaySize.setTextColor(0xFFFFFFFF);
        binding.playerDisplayClock.setTextColor(0xFFFFFFFF);
        binding.playerDisplayPosition.setTextColor(0xFFFFFFFF);
        binding.playerDisplayTraffic.setTextColor(0xFFFFFFFF);
    }

    private void tintInlineLoading() {
        binding.playerProgressTraffic.setTextColor(0xFFFFFFFF);
        binding.playerProgressTraffic.setAlpha(1f);
    }

    private void tintInlineGestureOverlay() {
        tintInlineControl(binding.gestureSeek);
        tintInlineControl(binding.gestureBright);
        tintInlineControl(binding.gestureVolume);
        binding.gestureBrightProgress.setProgressTintList(ColorStateList.valueOf(0xFFFFFFFF));
        binding.gestureBrightProgress.setProgressBackgroundTintList(ColorStateList.valueOf(0x66FFFFFF));
        binding.gestureVolumeProgress.setProgressTintList(ColorStateList.valueOf(0xFFFFFFFF));
        binding.gestureVolumeProgress.setProgressBackgroundTintList(ColorStateList.valueOf(0x66FFFFFF));
    }

    private boolean resolveLightTheme() {
        return Setting.resolveTmdbDetailLightTheme(detailThemeMode, isSystemNight());
    }

    private boolean isSystemNight() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private int themeModeLabel() {
        return lightTheme ? R.string.detail_theme_light : R.string.detail_theme_dark;
    }

    private void setCard(MaterialCardView card, int background, int stroke) {
        card.setCardBackgroundColor(background);
        card.setStrokeColor(stroke);
    }

    private void setPlayerCard(ThemeColors colors) {
        if (!isInlinePlayerMode()) return;
        binding.playerPanel.setCardBackgroundColor(0xFF000000);
        binding.playerPanel.setRadius(inlineFullscreen || inlinePiPLayout ? 0 : ResUtil.dp2px(20));
        updatePlayerPanelFocus(colors);
    }

    private void updatePlayerPanelFocus() {
        updatePlayerPanelFocus(lightTheme ? ThemeColors.light() : ThemeColors.dark());
    }

    private void updatePlayerPanelFocus(ThemeColors colors) {
        if (!isInlinePlayerMode()) return;
        if (inlineFullscreen || inlinePiPLayout) {
            binding.playerPanel.setStrokeColor(0x00000000);
            binding.playerPanel.setStrokeWidth(0);
            return;
        }
        boolean focused = binding.playerPanel.hasFocus() && !hasFocusedChild(inlineControlsView());
        binding.playerPanel.setStrokeColor(focused ? FOCUS_STROKE : colors.line);
        binding.playerPanel.setStrokeWidth(ResUtil.dp2px(focused ? FOCUS_STROKE_DP : CHIP_STROKE_DP));
    }

    private boolean isLeanbackFusionPlayerPanel() {
        return Util.isLeanback() && isFusionMode();
    }

    private void setupPlayerPanelFocusLayer() {
        if (!isLeanbackFusionPlayerPanel()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) binding.playerPanel.setDefaultFocusHighlightEnabled(false);
        binding.playerPanel.setRippleColor(ColorStateList.valueOf(0x00000000));
    }

    private void focusInlinePlayerPanel() {
        if (!isInlinePlayerMode()) return;
        binding.playerPanel.post(() -> {
            if (!isFinishing() && binding.playerPanel.getVisibility() == View.VISIBLE && !inlineFullscreen) binding.playerPanel.requestFocus();
        });
    }

    private void setDetailActionButton(MaterialButton button, ThemeColors colors) {
        if (lightTheme) {
            int[][] states = new int[][]{
                    new int[]{android.R.attr.state_selected},
                    new int[]{}
            };
            button.setAlpha(1f);
            button.setBackgroundTintList(new ColorStateList(states, new int[]{colors.chipActive, 0xFFFFFFFF}));
            button.setTextColor(new ColorStateList(states, new int[]{colors.primary, colors.primary}));
            button.setIconTint(new ColorStateList(states, new int[]{colors.primary, colors.primary}));
            button.setOnFocusChangeListener(null);
            applyButtonFocus(button, colors.lineStrong, button.hasFocus());
            button.setOnFocusChangeListener((view, focused) -> applyButtonFocus(button, colors.lineStrong, focused));
            return;
        }
        setButton(button, colors.control, colors.line, colors.primary);
    }

    private void setButton(MaterialButton button, int background, int stroke, int text) {
        button.setBackgroundTintList(ColorStateList.valueOf(background));
        button.setTextColor(text);
        button.setIconTint(ColorStateList.valueOf(text));
        button.setOnFocusChangeListener(null);
        applyButtonFocus(button, stroke, button.hasFocus());
        button.setOnFocusChangeListener((view, focused) -> applyButtonFocus(button, stroke, focused));
    }

    private void applyButtonFocus(MaterialButton button, int stroke, boolean focused) {
        button.setStrokeWidth(ResUtil.dp2px(focused ? FOCUS_STROKE_DP : CHIP_STROKE_DP));
        button.setStrokeColor(ColorStateList.valueOf(focused ? FOCUS_STROKE : stroke));
    }

    private void tintTextTree(View view, ThemeColors colors) {
        if (view instanceof RecyclerView) return;
        if (view instanceof TextView textView && !(view instanceof MaterialButton)) {
            textView.setTextColor(colors.primary);
        }
        if (!(view instanceof ViewGroup group)) return;
        for (int i = 0; i < group.getChildCount(); i++) tintTextTree(group.getChildAt(i), colors);
    }

    private void loadContent(@Nullable TmdbBundle reusableBundle) {
        int generation = ++loadGeneration;
        Task.execute(() -> {
            boolean tmdbAllowed = isTmdbAllowedForCurrentSite();
            Future<TmdbLoadResult> tmdbFuture = reusableBundle == null && tmdbConfig.isReady() && tmdbAllowed ? Task.executor().submit(this::loadTmdbResult) : null;
            Vod loadedVod = null;
            String error = null;
            try {
                Result result = SiteApi.detailContent(getKeyText(), getIdText());
                if (result != null && !result.getList().isEmpty()) {
                    loadedVod = result.getVod();
                    if (loadedVod != null && loadedVod.getSite() == null) {
                        loadedVod.setSite(VodConfig.get().getSite(getKeyText()));
                    }
                }
            } catch (Throwable e) {
                error = e.getMessage();
            }

            Vod finalVod = loadedVod;
            String finalError = error;
            runOnAliveUi(() -> {
                if (generation != loadGeneration) return;
                applyLoaded(finalVod, reusableBundle, new ArrayList<>(), finalError, false);
            });
            if (finalVod == null || tmdbFuture == null) {
                if (tmdbFuture != null) tmdbFuture.cancel(true);
                return;
            }
            try {
                TmdbLoadResult result = tmdbFuture.get();
                if (result != null && result.bundle() == null && finalVod != null) {
                    String query = cleanTmdbSearchQuery(finalVod.getName());
                    logTmdbMatch("基础匹配未命中，使用站源详情继续消歧：片名=%s，清洗后=%s，年份=%s，演员=%s，导演=%s，简介长度=%d",
                            finalVod.getName(), query, finalVod.getYear(), finalVod.getActor(), finalVod.getDirector(), finalVod.getContent().length());
                    TmdbBundle bundle = chooseTmdbBundle(result.searchItems(), query, finalVod);
                    List<TmdbItem> matchedSearchItems = result.searchItems();
                    if (bundle == null) {
                        SplitYearSearch split = searchSplitYearTmdbItems(query, finalVod);
                        if (split != null) {
                            bundle = chooseTmdbBundle(split.items(), split.query(), finalVod);
                            matchedSearchItems = split.items();
                        }
                    }
                    result = new TmdbLoadResult(bundle, matchedSearchItems);
                }
                TmdbLoadResult finalResult = result;
                runOnAliveUi(() -> {
                    if (generation != loadGeneration || vod == null) return;
                    applyTmdbResult(finalResult);
                });
            } catch (Throwable ignored) {
            }
        });
    }

    private boolean canTouchUi() {
        return !isFinishing() && !isDestroyed();
    }

    private void runOnAliveUi(Runnable runnable) {
        runOnUiThread(() -> {
            if (!canTouchUi()) return;
            runnable.run();
        });
    }

    private boolean isTmdbRequestCurrent(int generation, @Nullable TmdbItem item) {
        return generation == loadGeneration && isSameTmdbItem(item, matchedTmdbItem);
    }

    private boolean isSameTmdbItem(@Nullable TmdbItem first, @Nullable TmdbItem second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        return first.getTmdbId() == second.getTmdbId() && TextUtils.equals(first.getMediaType(), second.getMediaType());
    }

    private boolean isInlinePlaybackRequestCurrent(int generation, String key, String flag, String episodeUrl) {
        return generation == inlinePlaybackGeneration
                && TextUtils.equals(key, getKeyText())
                && selectedFlag != null
                && TextUtils.equals(flag, selectedFlag.getFlag())
                && selectedEpisode != null
                && TextUtils.equals(episodeUrl, selectedEpisode.getUrl());
    }

    private void cancelPendingInlinePlayback() {
        if (!inlinePlaybackLoading) return;
        inlinePlaybackGeneration++;
        inlinePlaybackLoading = false;
        hideInlineLoading();
        updateInlineDisplayPanel();
    }

    private void applyLoaded(Vod loadedVod, TmdbBundle bundle, List<TmdbItem> searchItems, String error) {
        applyLoaded(loadedVod, bundle, searchItems, error, true);
    }

    private void applyLoaded(Vod loadedVod, TmdbBundle bundle, List<TmdbItem> searchItems, String error, boolean allowMatchDialog) {
        binding.loading.setVisibility(View.GONE);
        if (loadedVod == null) {
            if (!TextUtils.isEmpty(error)) Notify.show(error);
            tryAutoChangeSource();
            return;
        }
        vod = loadedVod;
        brokenSources.clear();
        sourceVodName = loadedVod.getName();
        TmdbEpisodeSorter.sort(vod);
        applyTmdbBundle(bundle);
        if (bundle != null) saveTmdbMatch(bundle.item());
        enrichVod();
        initHistory();
        bindPage();
        focusInlinePlayerPanel();
        maybeAutoPlayInline();
        if (bundle != null) loadTmdbMediaBlocks(bundle);
        if (allowMatchDialog && shouldShowAutoTmdbMatchDialog(bundle)) showTmdbMatchDialog(searchItems);
    }

    private TmdbLoadResult loadTmdbResult() {
        TmdbBundle tmdbBundle = null;
        List<TmdbItem> searchItems = new ArrayList<>();
        try {
            if (initialTmdbItem != null) {
                tmdbBundle = loadTmdbBundle(initialTmdbItem);
            } else {
                TmdbItem match = getCachedTmdbMatch();
                if (match != null) {
                    try {
                        tmdbBundle = loadTmdbBundle(match);
                        if (TmdbMatchPolicy.isUnwantedSplitSeasonVariant(getNameText(), tmdbBundle.detail())) {
                            logTmdbMatch("缓存匹配跳过：当前标题=%s，缓存标题=%s，TMDB=%d 是分季变体", getNameText(), match.getTitle(), match.getTmdbId());
                            match = null;
                            tmdbBundle = null;
                        }
                    } catch (Throwable ignored) {
                        match = null;
                        tmdbBundle = null;
                    }
                }
                if (match == null) {
                    String query = getTmdbSearchQuery();
                    searchItems = searchTmdbItems(query, getNameText());
                    logTmdbMatch("搜索完成：原始标题=%s，实际搜索词=%s，返回数量=%d", getNameText(), query, searchItems.size());
                    match = chooseTmdbMatch(searchItems, query, null);
                    if (match == null) {
                        SplitYearSearch split = searchSplitYearTmdbItems(query, null);
                        if (split != null) {
                            searchItems = split.items();
                            match = chooseTmdbMatch(searchItems, split.query(), null);
                        }
                    }
                }
                if (match != null && tmdbBundle == null) {
                    tmdbBundle = loadTmdbBundle(match);
                    if (TmdbMatchPolicy.isUnwantedSplitSeasonVariant(getNameText(), tmdbBundle.detail())) {
                        logTmdbMatch("自动匹配跳过：当前标题=%s，候选标题=%s，TMDB=%d 是分季变体", getNameText(), match.getTitle(), match.getTmdbId());
                        tmdbBundle = null;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return new TmdbLoadResult(tmdbBundle, searchItems);
    }

    private void applyTmdbResult(TmdbLoadResult result) {
        TmdbBundle bundle = result == null ? null : result.bundle();
        applyTmdbBundle(bundle);
        if (bundle != null) saveTmdbMatch(bundle.item());
        enrichVod();
        bindBackdrop();
        bindHeader();
        bindMeta();
        bindRatings();
        bindOverview();
        renderSeasonSelection();
        renderEpisodes();
        bindTmdbSection();
        focusInlinePlayerPanel();
        if (bundle != null) loadTmdbMediaBlocks(bundle);
        if (shouldShowAutoTmdbMatchDialog(bundle)) showTmdbMatchDialog(result == null ? List.of() : result.searchItems());
    }

    private TmdbBundle loadTmdbBundle(TmdbItem item) throws Exception {
        JsonObject detail = tmdbService.detail(item, tmdbConfig);
        List<Integer> seasons = new ArrayList<>();
        Map<Integer, Integer> seasonCounts = new HashMap<>();
        Map<Integer, List<TmdbEpisode>> seasonEpisodes = new HashMap<>();
        Map<Integer, List<TmdbPerson>> seasonCast = new HashMap<>();
        Map<Integer, List<String>> seasonPhotos = new HashMap<>();
        if ("tv".equalsIgnoreCase(item.getMediaType())) {
            seasonCounts = seasonEpisodeCounts(detail);
            seasons.addAll(seasonCounts.keySet());
        }
        return new TmdbBundle(item, detail, List.of(), List.of(), List.of(), List.of(), seasons, seasonCounts, seasonEpisodes, seasonCast, seasonPhotos);
    }

    private void loadTmdbMediaBlocks(TmdbBundle bundle) {
        if (bundle == null || bundle.item() == null || bundle.detail() == null) return;
        int generation = loadGeneration;
        Vod currentVod = vod;
        tmdbMediaLoading = true;
        bindTmdbSection();
        loadTmdbPersonalAiCache(bundle, currentVod, generation);
        Task.execute(() -> {
            List<TmdbPerson> cast = new ArrayList<>();
            List<TmdbPerson> creators = new ArrayList<>();
            List<String> photos = new ArrayList<>();
            List<TmdbItem> related = new ArrayList<>();
            List<TmdbItem> personalTmdb = new ArrayList<>();
            List<TmdbItem> personalDouban = new ArrayList<>();
            try {
                cast = tmdbService.cast(bundle.detail(), tmdbConfig);
            } catch (Throwable ignored) {
            }
            try {
                creators = tmdbService.creators(bundle.detail(), tmdbConfig);
            } catch (Throwable ignored) {
            }
            try {
                photos = tmdbService.photos(bundle.detail(), tmdbConfig, preferLandscapeBackground());
            } catch (Throwable ignored) {
            }
            try {
                List<TmdbItem> recommendations = tmdbService.recommendations(bundle.detail(), tmdbConfig);
                List<TmdbItem> similar = tmdbService.similar(bundle.detail(), tmdbConfig);
                related = TmdbRecommendationRows.rankedRelated(bundle.detail(), recommendations, similar);
            } catch (Throwable ignored) {
            }
            try {
                if (Setting.isPersonalRecommendation()) {
                    PersonalRecommendationService service = new PersonalRecommendationService(tmdbService, tmdbConfig);
                    PersonalRecommendationService.RecommendationPages pages = service.loadPage(currentVod, bundle.item(), bundle.detail(), 0, PersonalRecommendationService.DEFAULT_PAGE_SIZE);
                    personalTmdb = TmdbRecommendationRows.personalTmdb(pages.getTmdb().getItems(), related);
                    personalDouban = TmdbRecommendationRows.personalDouban(pages.getDouban().getItems(), related, personalTmdb);
                }
            } catch (Throwable ignored) {
            }
            List<TmdbPerson> finalCast = cast;
            List<TmdbPerson> finalCreators = creators;
            List<String> finalPhotos = photos;
            List<TmdbItem> finalRelated = related;
            List<TmdbItem> finalPersonalTmdb = personalTmdb;
            List<TmdbItem> finalPersonalDouban = personalDouban;
            runOnAliveUi(() -> {
                if (generation != loadGeneration || bundle.item() != matchedTmdbItem) return;
                tmdbMediaLoading = false;
                detailCastItems.clear();
                detailCastItems.addAll(finalCast);
                creatorItems.clear();
                creatorItems.addAll(finalCreators);
                relatedItems.clear();
                relatedItems.addAll(finalRelated);
                personalTmdbItems.clear();
                personalTmdbItems.addAll(finalPersonalTmdb);
                personalDoubanItems.clear();
                personalDoubanItems.addAll(finalPersonalDouban);
                detailTmdbPhotos.clear();
                detailTmdbPhotos.addAll(finalPhotos);
                refreshBackdropSlideshow();
                bindSeasonTmdbMedia(tmdbEpisodeDataSeason(selectedFlag == null ? null : selectedFlag.getEpisodes()));
                bindTmdbSection();
                loadTmdbPersonalAi(bundle, currentVod, finalRelated, finalPersonalTmdb, finalPersonalDouban, generation);
            });
        });
    }

    private void loadTmdbPersonalAiCache(TmdbBundle bundle, Vod currentVod, int generation) {
        if (!Setting.isPersonalRecommendation() || bundle == null || bundle.item() == null) return;
        Task.execute(() -> {
            try {
                long start = System.currentTimeMillis();
                PersonalRecommendationService service = new PersonalRecommendationService(tmdbService, tmdbConfig);
                AiRecommendationService.CachedPage cached = service.loadCachedAiPage(currentVod, bundle.item(), PersonalRecommendationService.DEFAULT_PAGE_SIZE);
                if (!cached.hasItems()) return;
                List<TmdbItem> cachedAi = TmdbRecommendationRows.personalAi(cached.getPage().getItems(), List.of(), List.of(), List.of());
                SpiderDebug.log("tmdb", "detail personal ai early cache hit exact=%s resolved=%s cost=%dms count=%d title=%s", cached.isExact(), cached.isResolved(), System.currentTimeMillis() - start, cachedAi.size(), bundle.item().getTitle());
                applyTmdbPersonalAi(bundle, cachedAi, generation, false);
            } catch (Throwable e) {
                SpiderDebug.log("tmdb", "detail personal ai early cache failed error=%s", e.getMessage());
            }
        });
    }

    private void loadTmdbPersonalAi(TmdbBundle bundle, Vod currentVod, List<TmdbItem> related, List<TmdbItem> personalTmdb, List<TmdbItem> personalDouban, int generation) {
        if (!Setting.isPersonalRecommendation() || bundle == null || bundle.item() == null) return;
        Task.execute(() -> {
            long start = System.currentTimeMillis();
            PersonalRecommendationService service = new PersonalRecommendationService(tmdbService, tmdbConfig);
            AiRecommendationService.CachedPage cached = service.loadCachedAiPage(currentVod, bundle.item(), PersonalRecommendationService.DEFAULT_PAGE_SIZE);
            if (cached.hasItems()) {
                List<TmdbItem> cachedAi = TmdbRecommendationRows.personalAi(cached.getPage().getItems(), related, personalTmdb, personalDouban);
                SpiderDebug.log("tmdb", "detail personal ai cache hit exact=%s resolved=%s count=%d title=%s", cached.isExact(), cached.isResolved(), cachedAi.size(), bundle.item().getTitle());
                applyTmdbPersonalAi(bundle, cachedAi, generation, false);
            }
            PersonalRecommendationService.RecommendationPage page = cached.getPage();
            String mode = "cache";
            try {
                if (!cached.hasItems() || !cached.isExact() || !cached.isResolved()) {
                    mode = cached.isExact() ? "resolve-cache" : "refresh";
                    page = cached.isExact()
                            ? service.resolveCachedAiPage(currentVod, bundle.item(), PersonalRecommendationService.DEFAULT_PAGE_SIZE)
                            : service.refreshAiPage(currentVod, bundle.item(), PersonalRecommendationService.DEFAULT_PAGE_SIZE);
                }
            } catch (Throwable e) {
                SpiderDebug.log("tmdb", "detail personal ai failed error=%s", e.getMessage());
            }
            List<TmdbItem> personalAi = TmdbRecommendationRows.personalAi(page.getItems(), related, personalTmdb, personalDouban);
            SpiderDebug.log("tmdb", "detail personal ai async mode=%s cost=%dms count=%d title=%s", mode, System.currentTimeMillis() - start, personalAi.size(), bundle.item().getTitle());
            applyTmdbPersonalAi(bundle, personalAi, generation, !cached.hasItems());
        });
    }

    private void applyTmdbPersonalAi(TmdbBundle bundle, List<TmdbItem> items, int generation, boolean allowEmpty) {
        runOnAliveUi(() -> {
            if (generation != loadGeneration || bundle == null || bundle.item() != matchedTmdbItem) return;
            if (!allowEmpty && (items == null || items.isEmpty())) return;
            if (TmdbRecommendationRows.sameDisplayList(personalAiItems, items)) return;
            personalAiItems.clear();
            if (items != null) personalAiItems.addAll(items);
            bindTmdbSection();
        });
    }

    private void applyTmdbBundle(TmdbBundle bundle) {
        activeTmdbBundle = bundle;
        matchedTmdbItem = bundle == null ? null : bundle.item();
        matchedTmdbDetail = bundle == null ? null : bundle.detail();
        detailCastItems.clear();
        if (bundle != null) detailCastItems.addAll(bundle.cast());
        castItems.clear();
        castItems.addAll(detailCastItems);
        creatorItems.clear();
        if (bundle != null) creatorItems.addAll(bundle.creators());
        detailTmdbPhotos.clear();
        if (bundle != null) detailTmdbPhotos.addAll(bundle.photos());
        tmdbEpisodePhotos.clear();
        tmdbEpisodePhotos.addAll(detailTmdbPhotos);
        relatedItems.clear();
        if (bundle != null) relatedItems.addAll(bundle.related());
        personalTmdbItems.clear();
        personalDoubanItems.clear();
        personalAiItems.clear();
        tmdbEpisodes.clear();
        seasonNumbers.clear();
        if (bundle != null) seasonNumbers.addAll(bundle.seasons());
        seasonEpisodeCounts.clear();
        if (bundle != null) seasonEpisodeCounts.putAll(bundle.seasonCounts());
        tmdbSeasonEpisodes.clear();
        if (bundle != null) tmdbSeasonEpisodes.putAll(bundle.seasonEpisodes());
        tmdbSeasonCast.clear();
        if (bundle != null) tmdbSeasonCast.putAll(bundle.seasonCast());
        tmdbSeasonPhotos.clear();
        if (bundle != null) tmdbSeasonPhotos.putAll(bundle.seasonPhotos());
        loadingSeasons.clear();
        refreshedSingleSeasonProbes.clear();
        tmdbMediaLoading = false;
    }

    private void showTmdbMatchDialog(List<TmdbItem> items) {
        showTmdbMatchDialog(items, true);
    }

    private boolean shouldShowAutoTmdbMatchDialog(TmdbBundle bundle) {
        return Setting.isTmdbMatchDialog() && canMatchTmdb() && bundle == null && initialTmdbItem == null;
    }

    private void showTmdbMatchDialog(List<TmdbItem> items, boolean skippable) {
        if (!canTouchUi() || !canMatchTmdb()) return;
        TmdbSearchDialog.create(this)
                .title(getString(R.string.detail_tmdb_match_title))
                .query(getTmdbSearchQuery())
                .items(items)
                .listener(this::applyManualTmdb)
                .searchListener(this::searchTmdb)
                .skipListener(skippable ? this::onPlay : null)
                .show();
    }

    private void showManualTmdbMatchDialog() {
        if (!tmdbConfig.isReady()) {
            Notify.show(getString(R.string.detail_tmdb_need_key));
            return;
        }
        if (!isTmdbAllowedForCurrentSite()) {
            Notify.show(R.string.detail_tmdb_site_disabled);
            return;
        }
        binding.loading.setVisibility(View.VISIBLE);
        int generation = loadGeneration;
        int dialogGeneration = ++tmdbDialogGeneration;
        String query = getTmdbSearchQuery();
        String fallback = getNameText();
        Task.execute(() -> {
            try {
                List<TmdbItem> items = searchTmdbItems(query, fallback);
                runOnAliveUi(() -> {
                    if (generation != loadGeneration || dialogGeneration != tmdbDialogGeneration) return;
                    binding.loading.setVisibility(View.GONE);
                    showTmdbMatchDialog(items, false);
                });
            } catch (Throwable e) {
                runOnAliveUi(() -> {
                    if (generation != loadGeneration || dialogGeneration != tmdbDialogGeneration) return;
                    binding.loading.setVisibility(View.GONE);
                    Notify.show(TextUtils.isEmpty(e.getMessage()) ? getString(R.string.detail_tmdb_empty) : e.getMessage());
                });
            }
        });
    }

    private TmdbItem getCachedTmdbMatch() {
        if (!isTmdbAllowedForCurrentSite()) return null;
        return Setting.getTmdbMatchCache().find(getKeyText(), getIdText());
    }

    private boolean canMatchTmdb() {
        return tmdbConfig != null && tmdbConfig.isReady() && isTmdbAllowedForCurrentSite();
    }

    private boolean isTmdbAllowedForCurrentSite() {
        if (tmdbConfig == null) return false;
        Site site = getCurrentSite();
        String key = site == null || site.isEmpty() ? getKeyText() : site.getKey();
        String name = site == null || site.isEmpty() ? getKeyText() : site.getName();
        return tmdbConfig.isSiteEnabled(key, name);
    }

    private void saveTmdbMatch(TmdbItem item) {
        if (item == null || item.getTmdbId() <= 0) return;
        TmdbMatchCache cache = Setting.getTmdbMatchCache();
        cache.put(getKeyText(), getIdText(), item);
        Setting.putTmdbMatchCache(cache);
    }

    private String getTmdbSearchQuery() {
        if (matchedTmdbItem != null && !TextUtils.isEmpty(matchedTmdbItem.getTitle())) return matchedTmdbItem.getTitle();
        if (vod != null && !TextUtils.isEmpty(vod.getName())) return cleanTmdbSearchQuery(vod.getName());
        return cleanTmdbSearchQuery(getNameText());
    }

    private void searchTmdb(String keyword, TmdbSearchDialog dialog) {
        dialog.loading();
        int generation = loadGeneration;
        int dialogGeneration = ++tmdbDialogGeneration;
        Task.execute(() -> {
            try {
                String query = cleanTmdbSearchQuery(keyword);
                List<TmdbItem> items = searchTmdbItems(query, keyword);
                runOnAliveUi(() -> {
                    if (generation != loadGeneration || dialogGeneration != tmdbDialogGeneration) return;
                    dialog.updateItems(items);
                });
            } catch (Throwable e) {
                runOnAliveUi(() -> {
                    if (generation != loadGeneration || dialogGeneration != tmdbDialogGeneration) return;
                    dialog.updateItems(new ArrayList<>());
                    Notify.show(TextUtils.isEmpty(e.getMessage()) ? getString(R.string.detail_tmdb_empty) : e.getMessage());
                });
            }
        });
    }

    private void applyManualTmdb(TmdbItem item) {
        binding.loading.setVisibility(View.VISIBLE);
        int generation = loadGeneration;
        int applyGeneration = ++tmdbApplyGeneration;
        tmdbDialogGeneration++;
        sourceSearchGeneration++;
        Task.execute(() -> {
            try {
                TmdbBundle bundle = loadTmdbBundle(item);
                runOnAliveUi(() -> {
                    if (generation != loadGeneration || applyGeneration != tmdbApplyGeneration || vod == null) return;
                    binding.loading.setVisibility(View.GONE);
                    applyTmdbBundle(bundle);
                    saveTmdbMatch(item);
                    enrichVod();
                    bindPage();
                    loadTmdbMediaBlocks(bundle);
                    Notify.show(R.string.detail_tmdb_match_saved);
                });
            } catch (Throwable e) {
                runOnAliveUi(() -> {
                    if (generation != loadGeneration || applyGeneration != tmdbApplyGeneration) return;
                    binding.loading.setVisibility(View.GONE);
                    Notify.show(TextUtils.isEmpty(e.getMessage()) ? getString(R.string.detail_tmdb_empty) : e.getMessage());
                });
            }
        });
    }

    private void enrichVod() {
        if (matchedTmdbItem != null) {
            if (!TextUtils.isEmpty(matchedTmdbItem.getTitle())) vod.setName(matchedTmdbItem.getTitle());
            if (!TextUtils.isEmpty(matchedTmdbItem.getPosterUrl())) vod.setPic(matchedTmdbItem.getPosterUrl());
        }
        String overview = tmdbOverview();
        if (!TextUtils.isEmpty(overview)) vod.setContent(overview);
        if (matchedTmdbDetail == null) return;
        String poster = tmdbService.image(tmdbConfig.getImageBase(), string(matchedTmdbDetail, "poster_path"));
        if (!TextUtils.isEmpty(poster)) {
            vod.setPic(poster);
        } else if ((TextUtils.isEmpty(vod.getPic()) || vod.getPic().startsWith("data:")) && matchedTmdbItem != null) {
            vod.setPic(matchedTmdbItem.getPosterUrl());
        }
        if (TextUtils.isEmpty(vod.getDirector())) {
            String director = firstCrew("Director");
            if (!TextUtils.isEmpty(director)) vod.setDirector(director);
        }
    }

    private void bindPage() {
        binding.contentPanel.setVisibility(View.VISIBLE);
        bindBackdrop();
        bindHeader();
        bindMeta();
        bindRatings();
        bindOverview();
        bindSource();
        bindFlags();
        bindTmdbSection();
        updateKeepState();
        requestDetailPlayFocus();
    }

    private void requestDetailPlayFocus() {
        if (Util.isMobile() || isInlinePlayerMode() || binding.play.getVisibility() != View.VISIBLE) return;
        binding.play.post(() -> {
            if (!isFinishing() && binding != null && binding.play.getVisibility() == View.VISIBLE && !isInlinePlayerMode()) binding.play.requestFocus();
        });
    }

    private void bindInitialArtwork() {
        String initialTmdbPoster = getTmdbPosterText();
        ImgUtil.load(getNameText(), TextUtils.isEmpty(initialTmdbPoster) ? getPicText() : TmdbImageSelector.originalUrl(initialTmdbPoster), binding.poster);
        bindBackdropImage(getNameText(), getBackdropText(), getPicText());
    }

    private void bindBackdrop() {
        boolean wallpaperBackdrop = useAppWallpaperBackdrop();
        bindBackdropImage(vod.getName(), wallpaperBackdrop ? "" : tmdbBackdropUrl(), wallpaperBackdrop ? "" : vod.getPic());
        episodeAdapter.setFallbackStillUrl(episodeFallbackStillUrl());
        ImgUtil.load(vod.getName(), tmdbPosterUrl(), binding.poster);
    }

    private String tmdbPosterUrl() {
        String fallback = coalesce(
                matchedTmdbItem == null ? "" : matchedTmdbItem.getPosterUrl(),
                getTmdbPosterText(),
                vod == null ? "" : vod.getPic(),
                getPicText());
        return TmdbImageSelector.poster(matchedTmdbDetail, tmdbConfig == null ? "" : tmdbConfig.getImageBase(), fallback);
    }

    private void bindBackdropImage(String title, String backdrop, String fallback) {
        boolean hasBackdrop = !TextUtils.isEmpty(backdrop) && !TextUtils.equals(backdrop, fallback);
        String image = hasBackdrop ? backdrop : fallback;
        refreshBackdropSurface();
        binding.hero.setVisibility(TextUtils.isEmpty(image) && !useAppWallpaperBackdrop() ? View.GONE : View.VISIBLE);
        cancelBackdropSlideRequest();
        if (TextUtils.isEmpty(image)) {
            ImgUtil.clear(binding.backdropFill);
            ImgUtil.clear(binding.backdrop);
            binding.backdrop.setVisibility(View.INVISIBLE);
            backdropSlideVisibleView = binding.backdropFill;
            bindBackdropSlideSource(title, "");
            return;
        }
        showStaticBackdropImage(title, image);
        bindBackdropSlideSource(title, image);
    }

    private void showStaticBackdropImage(String title, String image) {
        String highResImage = highResTmdbImage(image);
        backdropSlideVisibleView = binding.backdropFill;
        binding.backdropFill.setVisibility(View.VISIBLE);
        binding.backdropFill.setAlpha(backdropSlideAlpha());
        binding.backdropFill.setScaleType(backdropScaleType());
        binding.backdropFill.setTag(R.id.image, highResImage);
        binding.backdrop.setVisibility(View.INVISIBLE);
        ImgUtil.clear(binding.backdrop);
        loadBackdropImage(title, highResImage, binding.backdropFill);
    }

    private void loadBackdropImage(String title, String image, ImageView target) {
        try {
            String highResImage = highResTmdbImage(image);
            Object model = ImgUtil.getUrl(highResImage);
            if (model == null) {
                ImgUtil.clear(target);
                return;
            }
            target.setTag(R.id.image, highResImage);
            com.bumptech.glide.RequestBuilder<Drawable> request = Glide.with(target)
                    .load(model)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .dontAnimate();
            request = shouldCropBackdrop() ? request.centerCrop() : request.fitCenter();
            request.into(target);
        } catch (Throwable e) {
            ImgUtil.load(title, highResTmdbImage(image), target, shouldCropBackdrop());
        }
    }

    private void bindBackdropSlideSource(String title, String image) {
        backdropSlideTitle = TextUtils.isEmpty(title) ? "" : title;
        backdropSlidePrimary = highResTmdbImage(image);
        refreshBackdropSlideshow();
    }

    private void refreshBackdropSlideshow() {
        String current = currentBackdropSlideImage();
        cancelBackdropSlideRequest();
        backdropSlideItems.clear();
        backdropSlideFailures.clear();
        addBackdropSlideItem(backdropSlidePrimary);
        for (String photo : detailTmdbPhotos) addBackdropSlideItem(photo);
        if (TextUtils.isEmpty(backdropSlidePrimary) && !backdropSlideItems.isEmpty()) {
            backdropSlidePrimary = backdropSlideItems.get(0);
            current = backdropSlidePrimary;
            binding.hero.setVisibility(View.VISIBLE);
            showStaticBackdropImage(backdropSlideTitle, backdropSlidePrimary);
        }
        backdropSlideIndex = Math.max(0, backdropSlideItems.indexOf(current));
        scheduleBackdropSlide(BACKDROP_SLIDE_DELAY_MS);
    }

    private void addBackdropSlideItem(String url) {
        String highResUrl = highResTmdbImage(url);
        if (TextUtils.isEmpty(highResUrl) || backdropSlideItems.contains(highResUrl)) return;
        backdropSlideItems.add(highResUrl);
    }

    private String currentBackdropSlideImage() {
        Object tag = visibleBackdropSlideView().getTag(R.id.image);
        return tag instanceof String ? (String) tag : backdropSlidePrimary;
    }

    private void scheduleBackdropSlide(long delayMillis) {
        App.removeCallbacks(backdropSlideNext);
        if (!canRunBackdropSlideshow()) return;
        App.post(backdropSlideNext, delayMillis);
    }

    private boolean canRunBackdropSlideshow() {
        return binding != null && canTouchUi() && Setting.isTmdbDetailBackdropSlide() && backdropSlideItems.size() > 1 && binding.hero.getVisibility() == View.VISIBLE;
    }

    private void loadNextBackdropSlide() {
        if (!canRunBackdropSlideshow() || backdropSlideLoading) return;
        int next = nextBackdropSlideIndex();
        if (next < 0 || next >= backdropSlideItems.size()) return;
        String url = highResTmdbImage(backdropSlideItems.get(next));
        Object model = ImgUtil.getUrl(url);
        if (model == null) {
            onBackdropSlideFailed(++backdropSlideGeneration, next, url);
            return;
        }
        int generation = ++backdropSlideGeneration;
        ImageView targetView = hiddenBackdropSlideView();
        prepareBackdropSlideTarget(targetView);
        backdropSlideLoading = true;
        backdropSlideLoadingView = targetView;
        try {
            com.bumptech.glide.RequestBuilder<Drawable> request = Glide.with(this)
                    .load(model)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .dontAnimate();
            request = shouldCropBackdrop() ? request.centerCrop() : request.fitCenter();
            request.listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                            targetView.post(() -> onBackdropSlideFailed(generation, next, url));
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            targetView.post(() -> onBackdropSlideReady(generation, next, url, targetView));
                            return false;
                        }
                    })
                    .into(targetView);
        } catch (Throwable ignored) {
            onBackdropSlideFailed(generation, next, url);
        }
    }

    private void prepareBackdropSlideTarget(ImageView targetView) {
        targetView.setVisibility(View.INVISIBLE);
        targetView.setAlpha(backdropSlideAlpha());
        targetView.setScaleType(backdropScaleType());
        ImgUtil.clear(targetView);
    }

    private ImageView.ScaleType backdropScaleType() {
        return shouldCropBackdrop() ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER;
    }

    private void onBackdropSlideReady(int generation, int index, String url, ImageView targetView) {
        if (!isBackdropSlideRequestCurrent(generation, index, url) || targetView != backdropSlideLoadingView) return;
        ImageView oldView = visibleBackdropSlideView();
        backdropSlideLoadingView = null;
        backdropSlideLoading = false;
        backdropSlideFailures.remove(url);
        backdropSlideIndex = index;
        backdropSlideVisibleView = targetView;
        targetView.setTag(R.id.image, url);
        targetView.setAlpha(backdropSlideAlpha());
        targetView.setVisibility(View.VISIBLE);
        if (oldView != targetView) {
            oldView.setVisibility(View.INVISIBLE);
            ImgUtil.clear(oldView);
        }
        scheduleBackdropSlide(BACKDROP_SLIDE_DELAY_MS);
    }

    private ImageView visibleBackdropSlideView() {
        return backdropSlideVisibleView == binding.backdrop ? binding.backdrop : binding.backdropFill;
    }

    private ImageView hiddenBackdropSlideView() {
        return visibleBackdropSlideView() == binding.backdrop ? binding.backdropFill : binding.backdrop;
    }

    private float backdropSlideAlpha() {
        return isCinemaMode() && !lightTheme ? 0.9f : 1f;
    }

    private int nextBackdropSlideIndex() {
        int count = backdropSlideItems.size();
        if (count <= 1) return -1;
        for (int offset = 1; offset < count; offset++) {
            int index = (backdropSlideIndex + offset) % count;
            String url = backdropSlideItems.get(index);
            if (!backdropSlideFailures.contains(url)) return index;
        }
        backdropSlideFailures.clear();
        return (backdropSlideIndex + 1) % count;
    }

    private boolean isBackdropSlideRequestCurrent(int generation, int index, String url) {
        return generation == backdropSlideGeneration && canRunBackdropSlideshow() && index >= 0 && index < backdropSlideItems.size() && TextUtils.equals(url, backdropSlideItems.get(index));
    }

    private void onBackdropSlideFailed(int generation, int index, String url) {
        if (!isBackdropSlideRequestCurrent(generation, index, url)) return;
        backdropSlideLoading = false;
        clearBackdropSlideLoadingView();
        backdropSlideFailures.add(url);
        backdropSlideIndex = index;
        scheduleBackdropSlide(BACKDROP_SLIDE_RETRY_MS);
    }

    private void cancelBackdropSlideRequest() {
        App.removeCallbacks(backdropSlideNext);
        backdropSlideGeneration++;
        backdropSlideLoading = false;
        clearBackdropSlideLoadingView();
    }

    private void clearBackdropSlideLoadingView() {
        ImageView targetView = backdropSlideLoadingView;
        backdropSlideLoadingView = null;
        if (targetView == null || targetView == visibleBackdropSlideView()) return;
        targetView.setVisibility(View.INVISIBLE);
        ImgUtil.clear(targetView);
    }

    private String tmdbBackdropUrl() {
        if (matchedTmdbDetail != null) {
            List<String> backgrounds = TmdbImageSelector.backgrounds(matchedTmdbDetail, tmdbConfig.getImageBase(), tmdbConfig.getBackdropBase(), preferLandscapeBackground(), 1);
            if (!backgrounds.isEmpty()) return backgrounds.get(0);
        }
        String backdrop = matchedTmdbItem != null ? matchedTmdbItem.getBackdropUrl() : "";
        if (TextUtils.isEmpty(backdrop) && matchedTmdbDetail != null) {
            backdrop = tmdbService.image(tmdbConfig.getBackdropBase(), string(matchedTmdbDetail, "backdrop_path"));
        }
        return TmdbImageSelector.originalUrl(backdrop);
    }

    private boolean preferLandscapeBackground() {
        return !Util.isMobile() || ResUtil.isPad() || ResUtil.getScreenWidth(this) >= ResUtil.getScreenHeight(this);
    }

    private boolean shouldCropBackdrop() {
        return true;
    }

    private ThemeColors currentThemeColors() {
        ThemeColors colors = lightTheme ? ThemeColors.light() : ThemeColors.dark();
        return isCinemaMode() ? ThemeColors.cinema(lightTheme) : colors;
    }

    private void refreshBackdropSurface() {
        applyBackdropSurface(currentThemeColors());
    }

    private void applyBackdropSurface(ThemeColors colors) {
        int backdropBackground = useAppWallpaperBackdrop() ? Color.TRANSPARENT : backdropFallbackBackground(colors);
        binding.root.setBackgroundColor(backdropBackground);
        binding.hero.setBackgroundColor(backdropBackground);
        binding.backdropFill.setBackgroundColor(backdropBackground);
        binding.backdrop.setBackgroundColor(backdropBackground);
    }

    private boolean useAppWallpaperBackdrop() {
        return vod != null && matchedTmdbDetail == null;
    }

    private int backdropFallbackBackground(ThemeColors colors) {
        return isPlayerMode() ? 0xFF0F141A : colors.background;
    }

    private String episodeFallbackStillUrl() {
        String poster = tmdbPosterUrl();
        if (!TextUtils.isEmpty(poster)) return poster;
        if (vod != null && !TextUtils.isEmpty(vod.getPic())) return vod.getPic();
        String backdrop = tmdbBackdropUrl();
        if (!TextUtils.isEmpty(backdrop)) return backdrop;
        return getPicText();
    }

    private void bindHeader() {
        overviewExpanded = false;
        binding.title.setText(vod.getName());
        binding.subtitle.setText(buildSubtitle());
        binding.sourceValue.setText(getString(R.string.detail_source_current, getSiteName()));
    }

    private void bindMeta() {
        binding.metaContainer.removeAllViews();
        addMetaChip(getMediaTypeLabel());
        addMetaChip(firstGenre());
        addMetaChip(firstCountry());
        addMetaChip(certificationLabel());
    }

    private void bindRatings() {
        String key = ratingDisplayKey();
        binding.ratingsContainer.setTag(key);
        binding.ratingsContainer.removeAllViews();
        binding.ratingsContainer.setVisibility(View.GONE);
        String tmdb = tmdbRatingValue();
        if (!TextUtils.isEmpty(tmdb)) addRatingChip(key, "TMDB", tmdb + "/10", 0xFF21D07A);
        fetchDoubanRating(key);
        fetchOmdbRating(key);
        bindExternalLinks();
    }

    private void bindExternalLinks() {
        clearExternalLinks();
        if (matchedTmdbDetail == null) return;
        int count = 0;
        String mediaType = matchedTmdbItem != null && "tv".equalsIgnoreCase(matchedTmdbItem.getMediaType()) ? "tv" : "movie";
        int tmdbId = matchedTmdbItem == null ? 0 : matchedTmdbItem.getTmdbId();
        if (tmdbId > 0) count += addExternalLink("TMDB", "https://www.themoviedb.org/" + mediaType + "/" + tmdbId);
        String imdb = string(object(matchedTmdbDetail, "external_ids"), "imdb_id");
        if (!TextUtils.isEmpty(imdb)) count += addExternalLink("IMDB", "https://www.imdb.com/title/" + imdb);
        String title = ratingTitle();
        if (!TextUtils.isEmpty(title)) count += addExternalLink("豆瓣", "https://search.douban.com/movie/subject_search?search_text=" + android.net.Uri.encode(title));
        String query = externalSearchQuery(title);
        if (!TextUtils.isEmpty(query)) {
            count += addExternalLink("烂番茄", "https://www.rottentomatoes.com/search?search=" + android.net.Uri.encode(query));
            count += addExternalLink("Metacritic", "https://www.metacritic.com/search/" + android.net.Uri.encode(query) + "/");
        }
        binding.externalLinksTitle.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        binding.externalLinksContainer.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
    }

    private void clearExternalLinks() {
        binding.externalLinksContainer.removeAllViews();
        binding.externalLinksTitle.setVisibility(View.GONE);
        binding.externalLinksContainer.setVisibility(View.GONE);
    }

    private String externalSearchQuery(String title) {
        if (TextUtils.isEmpty(title)) return "";
        int year = ratingYear();
        return year <= 0 || title.contains(String.valueOf(year)) ? title : title + " " + year;
    }

    private int addExternalLink(String name, String url) {
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(url)) return 0;
        ThemeColors colors = currentThemeColors();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(ResUtil.dp2px(12), ResUtil.dp2px(10), ResUtil.dp2px(12), ResUtil.dp2px(10));
        row.setClickable(true);
        row.setFocusable(true);
        GradientDrawable background = new GradientDrawable();
        background.setColor(isCinemaMode() ? TmdbCinemaTheme.palette(lightTheme).ratingChip() : colors.chip);
        background.setCornerRadius(ResUtil.dp2px(10));
        background.setStroke(ResUtil.dp2px(1), colors.line);
        row.setBackground(background);
        row.setOnClickListener(view -> openExternalLink(url));

        TextView label = new TextView(this);
        label.setText(name);
        label.setTextColor(isCinemaMode() ? 0xFFFFFFFF : colors.primary);
        label.setTextSize(14f);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_open);
        icon.setColorFilter(isCinemaMode() ? 0xCCFFFFFF : colors.secondary);
        row.addView(icon, new LinearLayout.LayoutParams(ResUtil.dp2px(20), ResUtil.dp2px(20)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (binding.externalLinksContainer.getChildCount() > 0) params.topMargin = ResUtil.dp2px(8);
        binding.externalLinksContainer.addView(row, params);
        return 1;
    }

    private void openExternalLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (Throwable e) {
            Notify.show("无法打开链接");
        }
    }

    private void addRatingChip(String key, String platform, String value, int color) {
        if (TextUtils.isEmpty(platform) || TextUtils.isEmpty(value)) return;
        if (!(binding.ratingsContainer.getTag() instanceof String) || !key.equals(binding.ratingsContainer.getTag())) return;
        TextView existing = findRatingChip(platform);
        if (existing != null) {
            existing.setText(platform + "  " + value);
            existing.setTextColor(color);
            return;
        }
        TextView chip = new TextView(this);
        chip.setTag(platform);
        chip.setText(platform + "  " + value);
        chip.setTextColor(color);
        chip.setTextSize(12f);
        chip.setTypeface(null, android.graphics.Typeface.BOLD);
        chip.setIncludeFontPadding(false);
        chip.setSingleLine(true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(ResUtil.dp2px(10), ResUtil.dp2px(7), ResUtil.dp2px(10), ResUtil.dp2px(7));
        ThemeColors colors = lightTheme ? ThemeColors.light() : ThemeColors.dark();
        GradientDrawable background = new GradientDrawable();
        background.setColor(isCinemaMode() ? TmdbCinemaTheme.palette(lightTheme).ratingChip() : colors.chip);
        background.setCornerRadius(ResUtil.dp2px(8));
        background.setStroke(ResUtil.dp2px(1), colors.line);
        chip.setBackground(background);
        FlexboxLayout.LayoutParams params = new FlexboxLayout.LayoutParams(FlexboxLayout.LayoutParams.WRAP_CONTENT, FlexboxLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, ResUtil.dp2px(8), ResUtil.dp2px(8));
        chip.setLayoutParams(params);
        binding.ratingsContainer.addView(chip);
        binding.ratingsContainer.setVisibility(View.VISIBLE);
    }

    private TextView findRatingChip(String platform) {
        for (int i = 0; i < binding.ratingsContainer.getChildCount(); i++) {
            View child = binding.ratingsContainer.getChildAt(i);
            if (child instanceof TextView text && platform.equals(child.getTag())) return text;
        }
        return null;
    }

    private void fetchDoubanRating(String key) {
        String title = ratingTitle();
        if (TextUtils.isEmpty(title)) return;
        String mediaType = matchedTmdbItem == null ? "" : matchedTmdbItem.getMediaType();
        int year = ratingYear();
        Task.execute(() -> {
            try {
                PersonalRecommendationService.DoubanRating rating = new PersonalRecommendationService().loadDoubanRating(title, mediaType, year);
                if (rating == null || rating.isEmpty()) return;
                String value = String.format(Locale.US, "%.1f/10", rating.getRating());
                runOnAliveUi(() -> addRatingChip(key, "豆瓣", value, 0xFF00B51D));
            } catch (Throwable ignored) {
            }
        });
    }

    private void fetchOmdbRating(String key) {
        String imdb = string(object(matchedTmdbDetail, "external_ids"), "imdb_id");
        String omdbApiKey = tmdbConfig == null ? "" : tmdbConfig.getOmdbApiKey();
        if (TextUtils.isEmpty(imdb) || TextUtils.isEmpty(omdbApiKey)) return;
        Task.execute(() -> {
            try {
                String url = "https://www.omdbapi.com/?i=" + imdb + "&apikey=" + omdbApiKey;
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                        .connectTimeout(8, TimeUnit.SECONDS)
                        .readTimeout(8, TimeUnit.SECONDS)
                        .build();
                okhttp3.Request request = new okhttp3.Request.Builder().url(url).build();
                try (okhttp3.Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) return;
                    JsonObject body = JsonParser.parseString(response.body().string()).getAsJsonObject();
                    if ("False".equalsIgnoreCase(string(body, "Response"))) return;
                    runOnAliveUi(() -> addOmdbRatingChips(key, body));
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private void addOmdbRatingChips(String key, JsonObject body) {
        String imdbRating = string(body, "imdbRating");
        if (!TextUtils.isEmpty(imdbRating) && !"N/A".equalsIgnoreCase(imdbRating)) addRatingChip(key, "IMDB", imdbRating + "/10", 0xFFF5C518);
        for (JsonElement element : array(body, "Ratings")) {
            if (!element.isJsonObject()) continue;
            JsonObject rating = element.getAsJsonObject();
            String source = string(rating, "Source");
            String value = string(rating, "Value");
            if (TextUtils.isEmpty(value) || "N/A".equalsIgnoreCase(value)) continue;
            if ("Rotten Tomatoes".equals(source)) addRatingChip(key, "烂番茄", value, 0xFFFA320A);
            else if ("Metacritic".equals(source)) addRatingChip(key, "Metacritic", value, 0xFFFFCC33);
        }
    }

    private String ratingDisplayKey() {
        int tmdbId = matchedTmdbItem == null ? 0 : matchedTmdbItem.getTmdbId();
        return tmdbId + "|" + ratingTitle() + "|" + ratingYear();
    }

    private String tmdbRatingValue() {
        if (matchedTmdbDetail == null || !matchedTmdbDetail.has("vote_average") || matchedTmdbDetail.get("vote_average").isJsonNull()) return "";
        double value = matchedTmdbDetail.get("vote_average").getAsDouble();
        return value <= 0 ? "" : String.format(Locale.US, "%.1f", value);
    }

    private String ratingTitle() {
        return coalesce(matchedTmdbItem == null ? "" : matchedTmdbItem.getTitle(), vod == null ? "" : vod.getName(), getNameText());
    }

    private int ratingYear() {
        String year = yearLabel();
        if (TextUtils.isEmpty(year) || year.length() < 4) return 0;
        try {
            return Integer.parseInt(year.substring(0, 4));
        } catch (Throwable e) {
            return 0;
        }
    }

    private void bindOverview() {
        String overview = displayOverview();
        binding.overview.setText(overview);
        binding.overview.setVisibility(TextUtils.isEmpty(overview) ? View.GONE : View.VISIBLE);
        if (TextUtils.isEmpty(overview)) {
            binding.overviewToggle.setVisibility(View.GONE);
            return;
        }
        if (shouldShowFullOverview()) {
            overviewExpanded = true;
            binding.overviewToggle.setVisibility(View.GONE);
            binding.overview.setMaxLines(Integer.MAX_VALUE);
            binding.overview.setEllipsize(null);
            return;
        }
        applyOverviewState();
        binding.overview.post(() -> {
            if (isOverviewOverflowing()) {
                binding.overviewToggle.setVisibility(View.VISIBLE);
                applyOverviewState();
            } else {
                binding.overviewToggle.setVisibility(View.GONE);
                binding.overview.setMaxLines(Integer.MAX_VALUE);
                binding.overview.setEllipsize(null);
            }
        });
    }

    private List<TmdbItem> searchTmdbItems(String query, String fallback) throws Exception {
        List<TmdbItem> items = tmdbService.search(query, tmdbConfig);
        logTmdbMatch("TMDB 搜索：搜索词=%s，结果数=%d", query, items.size());
        String fallbackQuery = Objects.toString(fallback, "").trim();
        if (!items.isEmpty() || TextUtils.isEmpty(fallbackQuery) || query.equals(fallbackQuery)) return items;
        List<TmdbItem> fallbackItems = tmdbService.search(fallbackQuery, tmdbConfig);
        logTmdbMatch("TMDB 搜索回退：清洗后无结果，原始词=%s，结果数=%d", fallbackQuery, fallbackItems.size());
        return fallbackItems;
    }

    @Nullable
    private SplitYearSearch searchSplitYearTmdbItems(String keyword, @Nullable Vod sourceVod) throws Exception {
        SplitYearQuery split = splitYearQuery(keyword, sourceVod);
        if (split == null) return null;
        List<TmdbItem> items = tmdbService.search(split.query(), tmdbConfig);
        logTmdbMatch("TMDB 拆年搜索：原始词=%s，拆分标题=%s，年份=%d，结果数=%d", keyword, split.query(), split.year(), items.size());
        return items.isEmpty() ? null : new SplitYearSearch(split.query(), split.year(), items);
    }

    private void toggleOverview() {
        if (binding.overviewToggle.getVisibility() != View.VISIBLE) return;
        overviewExpanded = !overviewExpanded;
        applyOverviewState();
    }

    private void applyOverviewState() {
        binding.overview.setMaxLines(overviewExpanded ? Integer.MAX_VALUE : 5);
        binding.overview.setEllipsize(overviewExpanded ? null : TextUtils.TruncateAt.END);
        binding.overviewToggle.setText(overviewExpanded ? R.string.detail_collapse : R.string.detail_expand);
    }

    private boolean shouldShowFullOverview() {
        return isCinemaMode();
    }

    private boolean isOverviewOverflowing() {
        if (binding.overview.getLineCount() > 5) return true;
        android.text.Layout layout = binding.overview.getLayout();
        return layout != null && layout.getLineCount() >= 5 && layout.getEllipsisCount(layout.getLineCount() - 1) > 0;
    }

    private void bindSource() {
        boolean hasFlags = vod != null && vod.getFlags() != null && !vod.getFlags().isEmpty();
        binding.flagHeader.setVisibility(hasFlags ? View.VISIBLE : View.GONE);
        binding.flagScroll.setVisibility(hasFlags ? View.VISIBLE : View.GONE);
    }

    private void bindFlags() {
        binding.flagContainer.removeAllViews();
        List<Flag> flags = vod.getFlags();
        boolean hasFlags = flags != null && !flags.isEmpty();
        if (!hasFlags) {
            binding.episodeTitle.setVisibility(View.GONE);
            binding.episodeContainer.setVisibility(View.GONE);
            binding.seasonScroll.setVisibility(View.GONE);
            clearEpisodeRanges();
            binding.episodeEmpty.setVisibility(View.VISIBLE);
            updatePlayLabel();
            return;
        }
        Flag currentFlag = findInitialFlag(flags);
        selectedFlag = currentFlag;
        selectedEpisode = null;
        selectedSeasonNumber = -1;
        resetEpisodeRange();
        for (Flag flag : flags) {
            MaterialButton button = createChipButton(flag.getShow());
            setChipState(button, flag.equals(currentFlag));
            button.setOnKeyListener((view, keyCode, event) -> onDetailFlagKey(keyCode, event));
            button.setOnClickListener(view -> {
                cancelPendingInlinePlayback();
                selectedFlag = flag;
                selectedEpisode = null;
                selectedSeasonNumber = -1;
                resetEpisodeRange();
                renderFlagSelection();
                renderEpisodes();
                if (isFusionMode()) onPlay();
            });
            binding.flagContainer.addView(button);
        }
        renderFlagSelection();
        renderEpisodes();
    }

    private void renderFlagSelection() {
        List<Flag> flags = vod.getFlags();
        for (int i = 0; i < binding.flagContainer.getChildCount() && i < flags.size(); i++) {
            View child = binding.flagContainer.getChildAt(i);
            if (child instanceof MaterialButton button) {
                setChipState(button, flags.get(i).equals(selectedFlag));
            }
        }
    }

    private void renderEpisodes() {
        List<Episode> episodes = selectedFlag == null ? null : selectedFlag.getEpisodes();
        boolean hasEpisodes = episodes != null && !episodes.isEmpty();
        binding.episodeHeader.setVisibility(hasEpisodes ? View.VISIBLE : View.GONE);
        binding.episodeContainer.setVisibility(hasEpisodes ? View.VISIBLE : View.GONE);
        binding.episodeEmpty.setVisibility(hasEpisodes ? View.GONE : View.VISIBLE);
        if (!hasEpisodes) {
            binding.seasonScroll.setVisibility(View.GONE);
            clearEpisodeRanges();
            binding.episodeSkeleton.setVisibility(View.GONE);
            episodeAdapter.setItems(List.of(), Map.of(), null);
            updatePlayLabel();
            return;
        }
        if (selectedEpisode == null) {
            String remarks = history != null ? history.getVodRemarks() : "";
            selectedEpisode = findEpisodeByUrl(history == null ? "" : history.getEpisodeUrl(), selectedFlag.getEpisodes());
            if (selectedEpisode == null) selectedEpisode = selectedFlag.find(remarks, getMarkText().isEmpty());
            if (selectedEpisode == null) selectedEpisode = episodes.get(0);
        }
        if (selectedSeasonNumber < 0) selectedSeasonNumber = seasonForEpisode(selectedEpisode, episodes);
        List<Episode> visibleEpisodes = visibleEpisodes(episodes);
        renderSeasonSelection();
        bindSeasonEpisodes(episodes);
        refreshCurrentHistoryEpisodeTitle();
        Map<Episode, Integer> episodeNumbers = episodeNumbers(visibleEpisodes, episodes);
        List<Episode> displayEpisodes = new ArrayList<>(visibleEpisodes);
        if (episodeReverse) Collections.reverse(displayEpisodes);
        List<EpisodeRangePolicy.Range> episodeRanges = EpisodeRangePolicy.build(displayEpisodes.size(), displayEpisodes.indexOf(selectedEpisode), episodeReverse);
        episodeRangeIndex = resolveEpisodeRangeIndex(episodeRanges);
        renderEpisodeRanges(episodeRanges);
        List<Episode> pagedDisplayEpisodes = episodeRanges.size() > 1 ? EpisodeRangePolicy.slice(displayEpisodes, episodeRanges.get(episodeRangeIndex)) : displayEpisodes;
        binding.episodeReverse.setText(episodeReverse ? R.string.detail_episode_forward : R.string.detail_episode_reverse);
        if (shouldForceAdaptiveEpisodeGrid()) episodeGridMode = true;
        binding.episodeViewMode.setVisibility(shouldForceAdaptiveEpisodeGrid() ? View.GONE : View.VISIBLE);
        updateEpisodeViewModeButton();
        int spanCount = episodeSpanCount();
        episodeAdapter.setMode(episodeGridMode ? TmdbEpisodeAdapter.Mode.GRID : TmdbEpisodeAdapter.Mode.LIST);
        episodeAdapter.setGridSpanCount(spanCount);
        updateEpisodeLayoutManager(spanCount);
        updateEpisodeViewport(pagedDisplayEpisodes.size(), spanCount);
        episodeAdapter.setFallbackStillUrl(episodeFallbackStillUrl());
        episodeAdapter.setItems(pagedDisplayEpisodes, tmdbEpisodes, episodeNumbers, selectedEpisode);
        updateEpisodeSkeleton();
        scrollEpisodeToSelected();
        updatePlayLabel();
        bindTmdbSection();
    }

    private int resolveEpisodeRangeIndex(List<EpisodeRangePolicy.Range> ranges) {
        if (ranges == null || ranges.isEmpty()) {
            episodeRangeIndex = 0;
            manualEpisodeRange = false;
            return episodeRangeIndex;
        }
        if (manualEpisodeRange) episodeRangeIndex = Math.max(0, Math.min(episodeRangeIndex, ranges.size() - 1));
        else episodeRangeIndex = EpisodeRangePolicy.selectedPosition(ranges);
        return episodeRangeIndex;
    }

    private void renderEpisodeRanges(List<EpisodeRangePolicy.Range> ranges) {
        binding.episodeRangeContainer.removeAllViews();
        boolean hasRanges = ranges != null && ranges.size() > 1;
        binding.episodeRangeScroll.setVisibility(hasRanges ? View.VISIBLE : View.GONE);
        if (!hasRanges) return;
        for (int i = 0; i < ranges.size(); i++) {
            EpisodeRangePolicy.Range range = ranges.get(i);
            MaterialButton button = createChipButton(range.label());
            setChipState(button, i == episodeRangeIndex);
            int index = i;
            button.setOnClickListener(view -> {
                cancelPendingInlinePlayback();
                manualEpisodeRange = true;
                episodeRangeIndex = index;
                renderEpisodes();
            });
            setEpisodeRangeFocusChange(button, index);
            button.setOnKeyListener((view, keyCode, event) -> onDetailEpisodeRangeKey(keyCode, event));
            binding.episodeRangeContainer.addView(button);
        }
        View selected = binding.episodeRangeContainer.getChildAt(episodeRangeIndex);
        if (selected != null) binding.episodeRangeScroll.post(() -> binding.episodeRangeScroll.smoothScrollTo(Math.max(0, selected.getLeft() - ResUtil.dp2px(12)), 0));
        restoreEpisodeRangeFocus();
    }

    private void setEpisodeRangeFocusChange(MaterialButton button, int index) {
        ThemeColors colors = lightTheme ? ThemeColors.light() : ThemeColors.dark();
        button.setOnFocusChangeListener((view, focused) -> {
            applyChipFocus(button, index == episodeRangeIndex, focused, colors);
            if (!focused || index == episodeRangeIndex) return;
            cancelPendingInlinePlayback();
            manualEpisodeRange = true;
            episodeRangeIndex = index;
            pendingEpisodeRangeFocus = index;
            renderEpisodes();
        });
    }

    private void restoreEpisodeRangeFocus() {
        if (pendingEpisodeRangeFocus < 0) return;
        int target = pendingEpisodeRangeFocus;
        pendingEpisodeRangeFocus = -1;
        binding.episodeRangeContainer.post(() -> {
            if (binding == null || target >= binding.episodeRangeContainer.getChildCount()) return;
            View child = binding.episodeRangeContainer.getChildAt(target);
            if (child != null) child.requestFocus();
        });
    }

    private void clearEpisodeRanges() {
        binding.episodeRangeScroll.setVisibility(View.GONE);
        binding.episodeRangeContainer.removeAllViews();
        pendingEpisodeRangeFocus = -1;
    }

    private void resetEpisodeRange() {
        episodeRangeIndex = 0;
        manualEpisodeRange = false;
        pendingEpisodeRangeFocus = -1;
    }

    private void scrollEpisodeToSelected() {
        if (selectedEpisode == null || episodeAdapter == null) return;
        binding.episodeContainer.post(() -> {
            if (scrollEpisodeStartOnce) {
                scrollEpisodeStartOnce = false;
                scrollEpisodeToPosition(0, ResUtil.dp2px(8));
                return;
            }
            if (selectedEpisode == null) return;
            int position = episodeAdapter.getPosition(selectedEpisode);
            if (position < 0) {
                scrollEpisodeToPosition(0, ResUtil.dp2px(8));
                return;
            }
            scrollEpisodeToPosition(position, ResUtil.dp2px(12));
        });
    }

    private void scrollEpisodeToPosition(int position, int offset) {
        RecyclerView.LayoutManager layoutManager = binding.episodeContainer.getLayoutManager();
        if (layoutManager instanceof GridLayoutManager gridLayoutManager) {
            gridLayoutManager.scrollToPositionWithOffset(position, offset);
        } else if (layoutManager instanceof LinearLayoutManager linearLayoutManager) {
            linearLayoutManager.scrollToPositionWithOffset(position, offset);
        } else {
            binding.episodeContainer.scrollToPosition(position);
        }
    }

    private void onDetailEpisodeFocusChange(View view, boolean focused) {
        if (!focused || binding == null || !episodeGridMode) return;
        int position = binding.episodeContainer.getChildAdapterPosition(view);
        if (position == RecyclerView.NO_POSITION) return;
        binding.scroll.post(() -> alignDetailEpisodeFocusedRow(view, position));
    }

    private boolean onDetailFlagKey(int keyCode, KeyEvent event) {
        if (!KeyUtil.isDownKey(event)) return false;
        if (!KeyUtil.isActionDown(event)) return true;
        if (focusDetailEpisodeRangeButton()) return true;
        return focusDetailEpisode();
    }

    private boolean onDetailEpisodeRangeKey(int keyCode, KeyEvent event) {
        if (!KeyUtil.isUpKey(event) && !KeyUtil.isDownKey(event)) return false;
        if (!KeyUtil.isActionDown(event)) return true;
        if (KeyUtil.isUpKey(event)) return focusDetailFlagButton();
        return focusDetailEpisode();
    }

    private boolean onDetailEpisodeKey(View view, int keyCode, KeyEvent event) {
        if (!KeyUtil.isUpKey(event) && !KeyUtil.isDownKey(event)) return false;
        if (!KeyUtil.isActionDown(event)) return true;
        int position = binding.episodeContainer.getChildAdapterPosition(view);
        if (position == RecyclerView.NO_POSITION) return true;
        int span = detailEpisodeSpanCount();
        if (KeyUtil.isUpKey(event)) {
            if (position < span) {
                if (focusDetailEpisodeRangeButton()) return true;
                return focusDetailFlagButton();
            }
            return focusDetailEpisode(position - span);
        }
        int target = position + span;
        if (target >= episodeAdapter.getItemCount()) return true;
        return focusDetailEpisode(target);
    }

    private boolean focusDetailFlagButton() {
        if (binding == null || binding.flagContainer.getChildCount() == 0) return false;
        int target = 0;
        List<Flag> flags = vod == null ? List.of() : vod.getFlags();
        for (int i = 0; i < flags.size() && i < binding.flagContainer.getChildCount(); i++) {
            if (flags.get(i).equals(selectedFlag)) {
                target = i;
                break;
            }
        }
        View child = binding.flagContainer.getChildAt(target);
        if (child == null) return false;
        binding.flagScroll.post(() -> {
            child.requestFocus();
            binding.flagScroll.smoothScrollTo(Math.max(0, child.getLeft() - ResUtil.dp2px(12)), 0);
        });
        return true;
    }

    private boolean focusDetailEpisodeRangeButton() {
        if (binding == null || binding.episodeRangeScroll.getVisibility() != View.VISIBLE || binding.episodeRangeContainer.getChildCount() == 0) return false;
        int target = Math.max(0, Math.min(episodeRangeIndex, binding.episodeRangeContainer.getChildCount() - 1));
        View child = binding.episodeRangeContainer.getChildAt(target);
        if (child == null) return false;
        binding.episodeRangeScroll.post(() -> {
            child.requestFocus();
            binding.episodeRangeScroll.smoothScrollTo(Math.max(0, child.getLeft() - ResUtil.dp2px(12)), 0);
        });
        return true;
    }

    private boolean focusDetailEpisode() {
        if (episodeAdapter == null || episodeAdapter.getItemCount() == 0) return false;
        int position = Math.max(0, episodeAdapter.getPosition(selectedEpisode));
        return focusDetailEpisode(position);
    }

    private boolean focusDetailEpisode(int position) {
        if (binding == null || episodeAdapter == null || episodeAdapter.getItemCount() == 0) return false;
        int target = Math.max(0, Math.min(position, episodeAdapter.getItemCount() - 1));
        int span = detailEpisodeSpanCount();
        int rowStart = Math.max(0, target - target % span);
        binding.episodeContainer.post(() -> {
            binding.episodeContainer.stopScroll();
            scrollEpisodeToPosition(rowStart, ResUtil.dp2px(8));
            binding.episodeContainer.postDelayed(() -> {
                RecyclerView.ViewHolder holder = binding.episodeContainer.findViewHolderForAdapterPosition(target);
                if (holder == null) {
                    binding.episodeContainer.requestFocus();
                    return;
                }
                holder.itemView.requestFocus();
                alignDetailEpisodeFocusedRow(holder.itemView, target);
            }, 80);
        });
        return true;
    }

    private int detailEpisodeSpanCount() {
        if (binding == null) return Math.max(1, episodeSpanCount());
        RecyclerView.LayoutManager layoutManager = binding.episodeContainer.getLayoutManager();
        if (layoutManager instanceof GridLayoutManager gridLayoutManager) return Math.max(1, gridLayoutManager.getSpanCount());
        return Math.max(1, episodeSpanCount());
    }

    private void alignDetailEpisodeFocusedRow(View focusedView, int position) {
        if (binding == null) return;
        RecyclerView.LayoutManager layoutManager = binding.episodeContainer.getLayoutManager();
        if (!(layoutManager instanceof GridLayoutManager gridLayoutManager)) return;
        int span = Math.max(1, gridLayoutManager.getSpanCount());
        int rowStart = Math.max(0, position - position % span);
        View rowView = focusedView;
        RecyclerView.ViewHolder holder = binding.episodeContainer.findViewHolderForAdapterPosition(rowStart);
        if (holder != null) rowView = holder.itemView;
        int targetY = binding.episodeContainer.getTop() + rowView.getTop() - ResUtil.dp2px(8);
        binding.scroll.scrollTo(0, Math.max(0, targetY));
    }

    private void toggleEpisodeReverse() {
        episodeReverse = !episodeReverse;
        resetEpisodeRange();
        scrollEpisodeStartOnce = episodeReverse;
        renderEpisodes();
    }

    private void toggleEpisodeViewMode() {
        if (shouldForceAdaptiveEpisodeGrid()) return;
        episodeGridMode = !episodeGridMode;
        Setting.putTmdbEpisodeGridMode(episodeGridMode);
        renderEpisodes();
    }

    private void updateEpisodeViewModeButton() {
        boolean switchToList = episodeGridMode;
        binding.episodeViewMode.setText(switchToList ? R.string.detail_episode_view_list : R.string.detail_episode_view_grid);
        binding.episodeViewMode.setIconResource(switchToList ? R.drawable.ic_site_list : R.drawable.ic_site_grid);
        binding.episodeViewMode.setContentDescription(getString(switchToList ? R.string.detail_episode_view_list_action : R.string.detail_episode_view_grid_action));
    }

    private boolean shouldForceAdaptiveEpisodeGrid() {
        return !Util.isMobile();
    }

    private void updateEpisodeLayoutManager() {
        updateEpisodeLayoutManager(episodeSpanCount());
    }

    private void updateEpisodeLayoutManager(int spanCount) {
        RecyclerView.LayoutManager current = binding.episodeContainer.getLayoutManager();
        if (episodeGridMode) {
            if (current instanceof GridLayoutManager grid && grid.getSpanCount() == spanCount) return;
            binding.episodeContainer.setPadding(0, 0, 0, 0);
            binding.episodeContainer.setLayoutManager(new GridLayoutManager(this, spanCount));
        } else {
            if (current instanceof LinearLayoutManager linear && linear.getOrientation() == LinearLayoutManager.HORIZONTAL) return;
            binding.episodeContainer.setPadding(0, 0, ResUtil.dp2px(8), 0);
            binding.episodeContainer.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        }
    }

    private void updateEpisodeViewport(int itemCount, int spanCount) {
        TmdbEpisodeGridPolicy.Layout layout = TmdbEpisodeGridPolicy.layout(
                episodeGridMode,
                itemCount,
                spanCount,
                ResUtil.dp2px(nativeEnhancedEpisodeCardHeightDp() + TmdbEpisodeGridPolicy.NATIVE_GRID_CARD_BOTTOM_MARGIN_DP));
        ViewGroup.LayoutParams params = binding.episodeContainer.getLayoutParams();
        if (params.height != layout.heightPx()) {
            params.height = layout.heightPx();
            binding.episodeContainer.setLayoutParams(params);
        }
        binding.episodeContainer.setNestedScrollingEnabled(layout.nestedScrolling());
    }

    private void updateEpisodeLayoutForCurrentItems() {
        if (episodeAdapter == null || binding == null) return;
        int spanCount = episodeSpanCount();
        episodeAdapter.setGridSpanCount(spanCount);
        updateEpisodeLayoutManager(spanCount);
        updateEpisodeViewport(episodeAdapter.getItemCount(), spanCount);
    }

    private int episodeSpanCount() {
        return nativeEnhancedInlineEpisodeSpanCount();
    }

    private int nativeEnhancedEpisodeCardHeightDp() {
        return TmdbEpisodeGridPolicy.nativeGridCardHeightDp(getResources().getConfiguration().smallestScreenWidthDp < 600);
    }

    private Map<Episode, Integer> episodeNumbers(List<Episode> visibleEpisodes, List<Episode> allEpisodes) {
        Map<Episode, Integer> numbers = new HashMap<>();
        for (int i = 0; i < visibleEpisodes.size(); i++) {
            Episode episode = visibleEpisodes.get(i);
            EpisodePosition position = episodePosition(episode, allEpisodes);
            numbers.put(episode, position.number() > 0 ? position.number() : i + 1);
        }
        return numbers;
    }

    private void renderSeasonSelection() {
        boolean hasSeasons = seasonNumbers.size() > 1 && !usesSingleTmdbSeasonEpisodeData(selectedFlag == null ? null : selectedFlag.getEpisodes());
        binding.seasonScroll.setVisibility(hasSeasons ? View.VISIBLE : View.GONE);
        binding.seasonContainer.removeAllViews();
        if (!hasSeasons) return;
        for (Integer season : seasonNumbers) {
            MaterialButton button = createChipButton(getString(R.string.detail_season_format, season));
            setChipState(button, season == selectedSeasonNumber);
            button.setOnClickListener(view -> {
                cancelPendingInlinePlayback();
                selectedSeasonNumber = season;
                List<Episode> visibleEpisodes = visibleEpisodes(selectedFlag.getEpisodes());
                selectedEpisode = visibleEpisodes.isEmpty() ? null : visibleEpisodes.get(0);
                resetEpisodeRange();
                renderSeasonSelection();
                fetchSeasonIfNeeded(tmdbEpisodeDataSeason(selectedFlag == null ? null : selectedFlag.getEpisodes()));
                renderEpisodes();
            });
            binding.seasonContainer.addView(button);
        }
    }

    private void bindSeasonEpisodes(List<Episode> sourceEpisodes) {
        tmdbEpisodes.clear();
        int tmdbSeason = tmdbEpisodeDataSeason(sourceEpisodes);
        List<TmdbEpisode> episodes = tmdbSeasonEpisodes.get(tmdbSeason);
        if (episodes != null) {
            for (TmdbEpisode episode : episodes) tmdbEpisodes.put(episode.getNumber(), episode);
        }
        bindTmdbEpisodes(sourceEpisodes, tmdbSeason);
        bindSeasonTmdbMedia(tmdbSeason);
        fetchSeasonIfNeeded(tmdbSeason);
        refreshFirstSeasonIfStaleSplit(sourceEpisodes);
    }

    private void bindTmdbEpisodes(List<Episode> sourceEpisodes, int tmdbSeason) {
        if (sourceEpisodes == null || sourceEpisodes.isEmpty()) return;
        for (Episode episode : sourceEpisodes) {
            EpisodePosition position = episodePosition(episode, sourceEpisodes);
            if (position.season() == tmdbSeason) episode.setTmdbEpisode(tmdbEpisodes.get(position.number()));
        }
    }

    private int tmdbEpisodeDataSeason(List<Episode> sourceEpisodes) {
        if (usesSingleTmdbSeasonEpisodeData(sourceEpisodes)) return firstSeasonNumber(matchedTmdbDetail);
        return selectedSeasonNumber;
    }

    private boolean usesSingleTmdbSeasonEpisodeData(List<Episode> sourceEpisodes) {
        if (sourceEpisodes == null || sourceEpisodes.isEmpty() || hasExplicitSeasonNumbers(sourceEpisodes)) return false;
        int titleSeason = sourceTitleSeasonNumber();
        if (seasonNumbers.contains(titleSeason)) return false;
        return EpisodeSeasonPolicy.shouldUseSingleSeasonEpisodeData(sourceEpisodes.size(), firstSeasonNumber(matchedTmdbDetail), seasonNumbers, seasonEpisodeCounts);
    }

    private void fetchSeasonIfNeeded(int seasonNumber) {
        fetchSeasonIfNeeded(seasonNumber, false);
    }

    private void fetchSeasonIfNeeded(int seasonNumber, boolean refresh) {
        if (seasonNumber < 0 || (!refresh && tmdbSeasonEpisodes.containsKey(seasonNumber)) || loadingSeasons.contains(seasonNumber) || matchedTmdbItem == null || !"tv".equalsIgnoreCase(matchedTmdbItem.getMediaType()) || !canMatchTmdb()) return;
        int generation = loadGeneration;
        TmdbItem item = matchedTmdbItem;
        JsonObject detail = matchedTmdbDetail;
        TmdbConfig config = tmdbConfig;
        loadingSeasons.add(seasonNumber);
        updateEpisodeSkeleton();
        Task.execute(() -> {
            try {
                JsonObject season = tmdbService.season(item, seasonNumber, config, detail, refresh);
                List<TmdbEpisode> episodes = tmdbService.episodes(season, config, item.getTmdbId(), seasonNumber);
                List<TmdbPerson> cast = tmdbService.seasonCast(season, config);
                List<String> photos = tmdbService.seasonPhotos(season, config);
                runOnAliveUi(() -> {
                    if (!isTmdbRequestCurrent(generation, item)) return;
                    loadingSeasons.remove(seasonNumber);
                    tmdbSeasonEpisodes.put(seasonNumber, episodes);
                    seasonEpisodeCounts.put(seasonNumber, episodes.size());
                    tmdbSeasonCast.put(seasonNumber, cast);
                    tmdbSeasonPhotos.put(seasonNumber, photos);
                    if (seasonNumber == tmdbEpisodeDataSeason(selectedFlag == null ? null : selectedFlag.getEpisodes()) || usesSingleTmdbSeasonEpisodeData(selectedFlag == null ? null : selectedFlag.getEpisodes())) renderEpisodes();
                });
            } catch (Throwable ignored) {
                runOnAliveUi(() -> {
                    if (!isTmdbRequestCurrent(generation, item)) return;
                    loadingSeasons.remove(seasonNumber);
                    updateEpisodeSkeleton();
                });
            }
        });
    }

    private void refreshFirstSeasonIfStaleSplit(List<Episode> sourceEpisodes) {
        if (sourceEpisodes == null || sourceEpisodes.isEmpty() || hasExplicitSeasonNumbers(sourceEpisodes)) return;
        int firstSeason = firstSeasonNumber(matchedTmdbDetail);
        if (firstSeason <= 0 || refreshedSingleSeasonProbes.contains(firstSeason) || loadingSeasons.contains(firstSeason)) return;
        int expectedCount = Math.max(0, seasonEpisodeCounts.getOrDefault(firstSeason, 0));
        List<TmdbEpisode> cachedEpisodes = tmdbSeasonEpisodes.get(firstSeason);
        int cachedCount = cachedEpisodes == null ? 0 : cachedEpisodes.size();
        int neededCount = expectedCount > 0 ? Math.min(expectedCount, sourceEpisodes.size()) : sourceEpisodes.size();
        if (cachedCount >= neededCount) return;
        refreshedSingleSeasonProbes.add(firstSeason);
        fetchSeasonIfNeeded(firstSeason, true);
    }

    private void updateEpisodeSkeleton() {
        int tmdbSeason = tmdbEpisodeDataSeason(selectedFlag == null ? null : selectedFlag.getEpisodes());
        boolean loading = tmdbSeason >= 0 && loadingSeasons.contains(tmdbSeason);
        binding.episodeSkeleton.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void bindSeasonTmdbMedia(int seasonNumber) {
        castItems.clear();
        List<TmdbPerson> seasonCast = tmdbSeasonCast.get(seasonNumber);
        castItems.addAll(seasonCast == null || seasonCast.isEmpty() ? detailCastItems : seasonCast);

        tmdbEpisodePhotos.clear();
        List<String> seasonPhotos = tmdbSeasonPhotos.get(seasonNumber);
        tmdbEpisodePhotos.addAll(seasonPhotos == null || seasonPhotos.isEmpty() ? detailTmdbPhotos : seasonPhotos);
    }

    private boolean hasExplicitSeasonNumbers(List<Episode> episodes) {
        if (episodes == null) return false;
        for (Episode episode : episodes) if (sourceSeasonNumber(episode) > 0) return true;
        return false;
    }

    private int sourceEpisodeNumber(Episode episode) {
        return episode == null ? -1 : episode.getNumber();
    }

    private int sourceSeasonNumber(Episode episode) {
        if (episode == null) return -1;
        return sourceSeasonNumber(episode.getName());
    }

    private int sourceTitleSeasonNumber() {
        int number = sourceSeasonNumber(sourceVodName);
        if (number > 0) return number;
        number = sourceSeasonNumber(getNameText());
        if (number > 0) return number;
        return vod == null ? -1 : sourceSeasonNumber(vod.getName());
    }

    private int sourceSeasonNumber(String text) {
        if (TextUtils.isEmpty(text)) return -1;
        Matcher matcher = SOURCE_SEASON.matcher(text);
        while (matcher.find()) {
            int number = normalizeSourceNumber(firstNonEmptyGroup(matcher, 1, 2, 3));
            if (number > 0) return number;
        }
        return -1;
    }

    private String firstNonEmptyGroup(Matcher matcher, int... groups) {
        if (matcher == null || groups == null) return "";
        for (int group : groups) {
            String value = matcher.group(group);
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private int normalizeSourceNumber(String value) {
        if (TextUtils.isEmpty(value)) return -1;
        value = value.trim();
        try {
            if (value.matches("\\d+")) return Integer.parseInt(value.replaceFirst("^0+(?!$)", ""));
        } catch (Exception ignored) {
            return -1;
        }
        int number = parseSmallChineseNumber(value);
        return number > 0 ? number : -1;
    }

    private int parseSmallChineseNumber(String value) {
        if (TextUtils.isEmpty(value)) return 0;
        value = value.replace("两", "二").replace("零", "").replace("〇", "");
        if (value.matches("[一二三四五六七八九]")) return chineseDigit(value.charAt(0));
        int tenIndex = value.indexOf("十");
        if (tenIndex >= 0) {
            int tens = tenIndex == 0 ? 1 : chineseDigit(value.charAt(tenIndex - 1));
            int ones = tenIndex == value.length() - 1 ? 0 : chineseDigit(value.charAt(tenIndex + 1));
            return tens * 10 + ones;
        }
        return 0;
    }

    private int chineseDigit(char value) {
        return switch (value) {
            case '一' -> 1;
            case '二' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            default -> 0;
        };
    }

    private List<Episode> visibleEpisodes(List<Episode> episodes) {
        if (episodes == null || episodes.isEmpty()) return List.of();
        if (usesSingleTmdbSeasonEpisodeData(episodes)) return episodes;
        if (seasonNumbers.size() <= 1 || selectedSeasonNumber < 0) return episodes;
        boolean hasExplicitSeasons = hasExplicitSeasonNumbers(episodes);
        int titleSeason = sourceTitleSeasonNumber();
        if (!hasExplicitSeasons && seasonNumbers.contains(titleSeason)) return selectedSeasonNumber == titleSeason ? episodes : List.of();
        if (hasExplicitSeasons) {
            List<Episode> visible = new ArrayList<>();
            for (Episode episode : episodes) if (sourceSeasonNumber(episode) == selectedSeasonNumber) visible.add(episode);
            if (!visible.isEmpty()) return visible;
        }
        return EpisodeSeasonPolicy.sliceBySeasonCounts(episodes, seasonNumbers, seasonEpisodeCounts, selectedSeasonNumber);
    }

    private int seasonForEpisode(Episode episode, List<Episode> episodes) {
        if (seasonNumbers.isEmpty()) return -1;
        if (seasonNumbers.size() == 1) return seasonNumbers.get(0);
        if (usesSingleTmdbSeasonEpisodeData(episodes)) return firstSeasonNumber(matchedTmdbDetail);
        int sourceSeason = sourceSeasonNumber(episode);
        if (seasonNumbers.contains(sourceSeason)) return sourceSeason;
        int titleSeason = sourceTitleSeasonNumber();
        if (!hasExplicitSeasonNumbers(episodes) && seasonNumbers.contains(titleSeason)) return titleSeason;
        if (selectedSeasonNumber > 0 && sourceEpisodeNumber(episode) > 0) return selectedSeasonNumber;
        int index = episode == null ? -1 : episodes.indexOf(episode);
        if (index < 0) return firstSeasonNumber(matchedTmdbDetail);
        if (!EpisodeSeasonPolicy.canSliceBySeasonCounts(episodes.size(), seasonNumbers, seasonEpisodeCounts)) return selectedSeasonNumber > 0 ? selectedSeasonNumber : firstSeasonNumber(matchedTmdbDetail);
        int start = 0;
        for (int i = 0; i < seasonNumbers.size(); i++) {
            Integer season = seasonNumbers.get(i);
            int count = Math.max(0, seasonEpisodeCounts.getOrDefault(season, 0));
            if (count <= 0) continue;
            int end = i == seasonNumbers.size() - 1 ? episodes.size() : start + count;
            if (index >= start && index < end) return season;
            start += count;
        }
        return seasonNumbers.get(seasonNumbers.size() - 1);
    }

    private void bindTmdbSection() {
        if (!isTmdbAllowedForCurrentSite()) {
            binding.tmdbSection.setVisibility(View.GONE);
            showAiRecommendationReason(null, false);
            return;
        }
        boolean hasCast = !castItems.isEmpty();
        boolean hasCreators = !creatorItems.isEmpty();
        boolean hasPhotos = !tmdbEpisodePhotos.isEmpty();
        boolean hasRelated = !relatedItems.isEmpty();
        boolean hasPersonalTmdb = !personalTmdbItems.isEmpty();
        boolean hasPersonalDouban = !personalDoubanItems.isEmpty();
        boolean hasPersonalAi = !personalAiItems.isEmpty();
        boolean hasExternalLinks = binding.externalLinksContainer.getChildCount() > 0;
        binding.tmdbSection.setVisibility(hasPhotos || hasCast || hasCreators || hasRelated || hasPersonalTmdb || hasPersonalDouban || hasPersonalAi || hasExternalLinks || matchedTmdbDetail != null || canMatchTmdb() ? View.VISIBLE : View.GONE);

        binding.episodePhotoTitle.setVisibility(hasPhotos ? View.VISIBLE : View.GONE);
        binding.episodePhotoList.setVisibility(hasPhotos ? View.VISIBLE : View.GONE);
        episodePhotoAdapter.setItems(tmdbEpisodePhotos);

        setTopMargin(binding.castTitle, hasPhotos ? 20 : 0);
        binding.castTitle.setVisibility(hasCast ? View.VISIBLE : View.GONE);
        binding.castList.setVisibility(hasCast ? View.VISIBLE : View.GONE);
        castAdapter.setItems(castItems);

        setTopMargin(binding.creatorTitle, hasCast ? 20 : hasPhotos ? 20 : 0);
        binding.creatorTitle.setVisibility(hasCreators ? View.VISIBLE : View.GONE);
        binding.creatorList.setVisibility(hasCreators ? View.VISIBLE : View.GONE);
        creatorAdapter.setItems(creatorItems);

        setTopMargin(binding.relatedTitle, hasPhotos || hasCast || hasCreators ? 20 : 0);
        binding.relatedTitle.setVisibility(hasRelated ? View.VISIBLE : View.GONE);
        binding.relatedList.setVisibility(hasRelated ? View.VISIBLE : View.GONE);
        relatedAdapter.setItems(relatedItems);

        setTopMargin(binding.personalTmdbTitle, hasPhotos || hasCast || hasCreators || hasRelated ? 20 : 0);
        binding.personalTmdbTitle.setVisibility(hasPersonalTmdb ? View.VISIBLE : View.GONE);
        binding.personalTmdbList.setVisibility(hasPersonalTmdb ? View.VISIBLE : View.GONE);
        personalTmdbAdapter.setItems(personalTmdbItems);

        setTopMargin(binding.personalDoubanTitle, hasPhotos || hasCast || hasCreators || hasRelated || hasPersonalTmdb ? 20 : 0);
        binding.personalDoubanTitle.setVisibility(hasPersonalDouban ? View.VISIBLE : View.GONE);
        binding.personalDoubanList.setVisibility(hasPersonalDouban ? View.VISIBLE : View.GONE);
        personalDoubanAdapter.setItems(personalDoubanItems);

        setTopMargin(binding.personalAiTitle, hasPhotos || hasCast || hasCreators || hasRelated || hasPersonalTmdb || hasPersonalDouban ? 20 : 0);
        binding.personalAiTitle.setVisibility(hasPersonalAi ? View.VISIBLE : View.GONE);
        binding.personalAiList.setVisibility(hasPersonalAi ? View.VISIBLE : View.GONE);
        personalAiAdapter.setItems(personalAiItems);
        if (!hasPersonalAi) showAiRecommendationReason(null, false);

        setTopMargin(binding.externalLinksTitle, hasPhotos || hasCast || hasCreators || hasRelated || hasPersonalTmdb || hasPersonalDouban || hasPersonalAi ? 20 : 0);
        binding.externalLinksTitle.setVisibility(hasExternalLinks ? View.VISIBLE : View.GONE);
        binding.externalLinksContainer.setVisibility(hasExternalLinks ? View.VISIBLE : View.GONE);

        if (!tmdbConfig.isReady()) {
            binding.tmdbStatus.setVisibility(View.VISIBLE);
            binding.tmdbStatus.setText(R.string.detail_tmdb_need_key);
        } else if (!isTmdbAllowedForCurrentSite()) {
            binding.tmdbStatus.setVisibility(View.VISIBLE);
            binding.tmdbStatus.setText(R.string.detail_tmdb_site_disabled);
        } else if (!hasPhotos && !hasCast && !hasCreators && !hasRelated && !hasPersonalTmdb && !hasPersonalDouban && !hasPersonalAi && !hasExternalLinks) {
            binding.tmdbStatus.setVisibility(View.VISIBLE);
            binding.tmdbStatus.setText(R.string.detail_tmdb_empty);
        } else {
            binding.tmdbStatus.setVisibility(View.GONE);
        }
    }

    private void showAiRecommendationReason(TmdbItem item, boolean focused) {
        if (binding == null) return;
        String reason = item == null ? "" : item.getOverview();
        if (!focused || TextUtils.isEmpty(reason)) {
            binding.personalAiReason.setText("");
            binding.personalAiReason.setVisibility(View.GONE);
            return;
        }
        binding.personalAiReason.setText(getString(R.string.ai_recommendation_reason_preview, reason));
        binding.personalAiReason.setVisibility(View.VISIBLE);
        scrollAiRecommendationReasonIntoView();
    }

    private void scrollAiRecommendationReasonIntoView() {
        binding.personalAiReason.post(() -> {
            if (binding == null || binding.personalAiReason.getVisibility() != View.VISIBLE) return;
            Rect rect = new Rect(0, 0, binding.personalAiReason.getWidth(), binding.personalAiReason.getHeight());
            binding.scroll.offsetDescendantRectToMyCoords(binding.personalAiReason, rect);
            int viewportTop = binding.scroll.getScrollY();
            int viewportBottom = viewportTop + binding.scroll.getHeight() - binding.scroll.getPaddingBottom();
            int padding = ResUtil.dp2px(12);
            int bottomGap = rect.bottom - viewportBottom + padding;
            if (bottomGap > 0) {
                binding.scroll.smoothScrollBy(0, bottomGap);
            } else if (rect.top < viewportTop) {
                binding.scroll.smoothScrollBy(0, rect.top - viewportTop - padding);
            }
        });
    }

    private void initHistory() {
        history = History.find(getHistoryKey());
        if (history == null) {
            history = new History();
            history.setKey(getHistoryKey());
            history.setCid(VodConfig.getCid());
            history.setVodName(vod.getName());
            history.findEpisode(vod.getFlags());
        }
        if (!TextUtils.isEmpty(getMarkText())) history.setVodRemarks(getMarkText());
        resetInitialPlaybackSpeed();
        syncDanmakuCompatHistory();
        updatePlayLabel();
    }

    private void resetInitialPlaybackSpeed() {
        if (history != null) history.setSpeed(NORMAL_SPEED);
    }

    private void updatePlayLabel() {
        if (selectedEpisode != null) {
            boolean canResume = history != null && isHistoryEpisode(selectedEpisode, history) && history.getPosition() > 0;
            binding.play.setText(canResume ? getString(R.string.detail_play_resume, historyEpisodeTitle(selectedEpisode)) : getString(R.string.detail_play_now));
            return;
        }
        boolean hasResume = history != null && history.getPosition() > 0 && !TextUtils.isEmpty(history.getVodRemarks());
        binding.play.setText(hasResume ? getString(R.string.detail_play_resume, history.getVodRemarks()) : getString(R.string.detail_play_now));
    }

    private void onPlay() {
        if (vod == null) return;
        saveInlineHistory();
        updateInlineHistory(selectedEpisode);
        if (isFusionMode()) playInline();
        else if (isPlayerMode()) playDetailFullscreen();
        else playDefaultPlayback();
    }

    private void playDefaultPlayback() {
        VideoActivity.startDirectTmdb(this, getKeyText(), getIdText(), playbackHistoryName(), playbackHistoryPic(), playbackMark(), selectedTmdbEpisodeTitles(), playbackTmdbItem(), playbackTmdbVod(), playbackFlag(), playbackEpisodeName(), playbackEpisodeUrl());
    }

    private String playbackMark() {
        if (selectedEpisode != null && !TextUtils.isEmpty(selectedEpisode.getName())) return selectedEpisode.getName();
        return getMarkText();
    }

    private String playbackFlag() {
        return selectedFlag == null ? "" : selectedFlag.getFlag();
    }

    private String playbackEpisodeName() {
        return selectedEpisode == null ? playbackMark() : selectedEpisode.getName();
    }

    private String playbackEpisodeUrl() {
        return selectedEpisode == null ? "" : selectedEpisode.getUrl();
    }

    private void setTopMargin(View view, int dp) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams marginParams)) return;
        marginParams.topMargin = ResUtil.dp2px(dp);
        view.setLayoutParams(marginParams);
    }

    private void showTmdbEpisodeDetail(Episode episode, int episodeNumber) {
        if (matchedTmdbItem == null || !"tv".equalsIgnoreCase(matchedTmdbItem.getMediaType()) || selectedSeasonNumber < 0 || episodeNumber <= 0 || !canMatchTmdb()) {
            Notify.show(R.string.detail_tmdb_empty);
            return;
        }
        binding.loading.setVisibility(View.VISIBLE);
        int generation = loadGeneration;
        int detailGeneration = ++tmdbEpisodeDetailGeneration;
        int displaySeasonNumber = selectedSeasonNumber;
        int seasonNumber = tmdbEpisodeDataSeason(selectedFlag == null ? null : selectedFlag.getEpisodes());
        TmdbItem item = matchedTmdbItem;
        JsonObject baseDetail = matchedTmdbDetail;
        TmdbConfig config = tmdbConfig;
        Task.execute(() -> {
            try {
                JsonObject detail = tmdbService.episode(item, seasonNumber, episodeNumber, config, baseDetail);
                List<String> photos = tmdbService.episodePhotos(detail, config);
                List<TmdbPerson> guests = tmdbService.episodeGuests(detail, config);
                runOnAliveUi(() -> {
                    if (!isTmdbRequestCurrent(generation, item) || detailGeneration != tmdbEpisodeDetailGeneration) return;
                    binding.loading.setVisibility(View.GONE);
                    if (displaySeasonNumber != selectedSeasonNumber) return;
                    showTmdbEpisodeDialog(episode, episodeNumber, detail, photos, guests);
                });
            } catch (Throwable e) {
                runOnAliveUi(() -> {
                    if (!isTmdbRequestCurrent(generation, item) || detailGeneration != tmdbEpisodeDetailGeneration) return;
                    binding.loading.setVisibility(View.GONE);
                    if (displaySeasonNumber != selectedSeasonNumber) return;
                    Notify.show(TextUtils.isEmpty(e.getMessage()) ? getString(R.string.detail_tmdb_empty) : e.getMessage());
                });
            }
        });
    }

    private void showTmdbEpisodeDialog(Episode episode, int episodeNumber, JsonObject detail, List<String> photos, List<TmdbPerson> guests) {
        DialogTmdbEpisodeBinding dialogBinding = DialogTmdbEpisodeBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setView(dialogBinding.getRoot()).create();
        ThemeColors colors = lightTheme ? ThemeColors.light() : ThemeColors.dark();
        dialogBinding.panel.setCardBackgroundColor(colors.panel);
        dialogBinding.panel.setStrokeColor(colors.line);
        tintTextTree(dialogBinding.getRoot(), colors);
        dialogBinding.title.setText(episodeDetailTitle(episode, episodeNumber, detail));
        dialogBinding.meta.setText(episodeMeta(detail));
        dialogBinding.meta.setVisibility(TextUtils.isEmpty(dialogBinding.meta.getText()) ? View.GONE : View.VISIBLE);
        String overview = string(detail, "overview");
        if (TextUtils.isEmpty(overview)) overview = episode == null ? "" : episode.getDesc();
        dialogBinding.overview.setText(TextUtils.isEmpty(overview) ? getString(R.string.detail_tmdb_empty) : overview);
        String crew = episodeCrew(detail);
        dialogBinding.crewTitle.setVisibility(TextUtils.isEmpty(crew) ? View.GONE : View.VISIBLE);
        dialogBinding.crew.setText(crew);
        dialogBinding.crew.setVisibility(TextUtils.isEmpty(crew) ? View.GONE : View.VISIBLE);
        String still = photos.isEmpty() ? "" : photos.get(0);
        dialogBinding.still.setVisibility(TextUtils.isEmpty(still) ? View.GONE : View.VISIBLE);
        if (!TextUtils.isEmpty(still)) ImgUtil.load(episodeDetailTitle(episode, episodeNumber, detail), still, dialogBinding.still);

        TmdbPhotoAdapter photoAdapter = new TmdbPhotoAdapter((position, url) -> showPhotoDialog(position, url, photos));
        photoAdapter.setLight(lightTheme);
        photoAdapter.setItems(photos);
        dialogBinding.photoList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        dialogBinding.photoList.setNestedScrollingEnabled(false);
        dialogBinding.photoList.setAdapter(photoAdapter);
        dialogBinding.photoTitle.setVisibility(photos.isEmpty() ? View.GONE : View.VISIBLE);
        dialogBinding.photoList.setVisibility(photos.isEmpty() ? View.GONE : View.VISIBLE);

        TmdbPersonAdapter guestAdapter = new TmdbPersonAdapter(this::loadPersonDetail);
        guestAdapter.setItems(guests);
        dialogBinding.guestList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        dialogBinding.guestList.setNestedScrollingEnabled(false);
        dialogBinding.guestList.setAdapter(guestAdapter);
        dialogBinding.guestTitle.setVisibility(guests.isEmpty() ? View.GONE : View.VISIBLE);
        dialogBinding.guestList.setVisibility(guests.isEmpty() ? View.GONE : View.VISIBLE);
        dialogBinding.close.setOnClickListener(view -> dialog.dismiss());
        setButton(dialogBinding.close, colors.control, colors.line, colors.primary);

        dialog.show();
        dialogBinding.close.post(dialogBinding.close::requestFocus);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setDimAmount(0.56f);
            int width = getResources().getDisplayMetrics().widthPixels;
            window.setLayout((int) (width * (width >= 1200 ? 0.78f : 0.94f)), WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showPhotoDialog(int position, String url) {
        if (TextUtils.isEmpty(url)) return;
        showPhotoDialog(position, url, new ArrayList<>(tmdbEpisodePhotos));
    }

    private void showPhotoDialog(int position, String url, List<String> sourcePhotos) {
        if (TextUtils.isEmpty(url)) return;
        List<String> photos = new ArrayList<>(sourcePhotos);
        if (photos.isEmpty()) photos.add(url);
        int start = position >= 0 && position < photos.size() ? position : Math.max(0, photos.indexOf(url));
        int[] current = new int[]{Math.max(0, start)};

        ImageView image = new ImageView(this);
        image.setFocusable(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(0xFF000000);
        image.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ProgressBar progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(ResUtil.dp2px(38), ResUtil.dp2px(38), android.view.Gravity.CENTER);
        progress.setLayoutParams(progressParams);

        Dialog dialog = new Dialog(this);
        int[] request = new int[]{0};
        int[] photoOrientation = new int[]{ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED};
        FrameLayout content = new FrameLayout(this);
        content.setBackgroundColor(0xFF000000);
        content.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(image);
        content.addView(progress);
        View photoActions = createPhotoActions(dialog, image, progress, photos, current, request, photoOrientation);
        content.addView(photoActions);
        int originalOrientation = getRequestedOrientation();
        boolean wasFullscreen = Util.isFullscreen(this);

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);
        dialog.setOnDismissListener(instance -> {
            setRequestedOrientation(originalOrientation);
            if (!wasFullscreen) Util.toggleFullscreen(this, false);
        });
        GestureDetector photoGesture = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent event) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent event) {
                if (photos.size() <= 1) {
                    dialog.dismiss();
                    return true;
                }
                float x = event.getX();
                int width = image.getWidth();
                if (x < width * 0.33f) {
                    showPhotoAt(image, progress, photos, current, request, photoOrientation, -1);
                } else if (x > width * 0.67f) {
                    showPhotoAt(image, progress, photos, current, request, photoOrientation, 1);
                } else {
                    dialog.dismiss();
                }
                return true;
            }

            @Override
            public boolean onFling(MotionEvent down, MotionEvent up, float velocityX, float velocityY) {
                if (photos.size() <= 1 || down == null || up == null) return false;
                float distanceX = up.getX() - down.getX();
                if (Math.abs(distanceX) < ResUtil.dp2px(48) || Math.abs(velocityX) < 120f) return false;
                showPhotoAt(image, progress, photos, current, request, photoOrientation, distanceX < 0 ? 1 : -1);
                return true;
            }

            @Override
            public void onLongPress(MotionEvent event) {
                if (Util.isMobile()) showPhotoActionDialog(photos.get(current[0]));
            }
        });
        image.setOnTouchListener((view, event) -> photoGesture.onTouchEvent(event));
        dialog.setOnKeyListener((instance, keyCode, event) -> {
            if (!KeyUtil.isActionUp(event)) return false;
            if (photoActions.hasFocus()) return keyCode == KeyEvent.KEYCODE_BACK && dialogCancel(dialog);
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                savePhoto(photos.get(current[0]), null);
                return true;
            }
            if (KeyUtil.isLeftKey(event)) {
                showPhotoAt(image, progress, photos, current, request, photoOrientation, -1);
                return true;
            }
            if (KeyUtil.isRightKey(event)) {
                showPhotoAt(image, progress, photos, current, request, photoOrientation, 1);
                return true;
            }
            if (KeyUtil.isEnterKey(event)) {
                dialog.dismiss();
                return true;
            }
            return false;
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.black);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            Util.hideSystemUI(window);
        }
        image.requestFocus();
        loadPhotoImage(image, progress, photos.get(current[0]), request, photoOrientation);
        preloadPhotoNeighbors(photos, current[0]);
    }

    private View createPhotoActions(Dialog dialog, ImageView image, ProgressBar progress, List<String> photos, int[] current, int[] request, int[] photoOrientation) {
        if (Util.isLeanback()) return createPhotoRemoteActions(dialog, image, progress, photos, current, request, photoOrientation);
        return createPhotoMobileActions(photos, current, photoOrientation);
    }

    private View createPhotoMobileActions(List<String> photos, int[] current, int[] photoOrientation) {
        FrameLayout actions = new FrameLayout(this);
        actions.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        MaterialButton rotate = createPhotoButton(R.string.detail_image_rotate, R.drawable.ic_control_rotate);
        FrameLayout.LayoutParams rotateParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(44), Gravity.TOP | Gravity.START);
        rotateParams.setMargins(ResUtil.dp2px(24), ResUtil.dp2px(28), 0, 0);
        rotate.setLayoutParams(rotateParams);
        rotate.setOnClickListener(view -> togglePhotoOrientation(photoOrientation));
        actions.addView(rotate);

        MaterialButton save = createPhotoButton(R.string.detail_image_save, R.drawable.ic_tmdb_download);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(44), Gravity.TOP | Gravity.END);
        params.setMargins(0, ResUtil.dp2px(28), ResUtil.dp2px(24), 0);
        save.setLayoutParams(params);
        save.setOnClickListener(view -> savePhoto(photos.get(current[0]), save));
        actions.addView(save);

        return actions;
    }

    private View createPhotoRemoteActions(Dialog dialog, ImageView image, ProgressBar progress, List<String> photos, int[] current, int[] request, int[] photoOrientation) {
        if (image.getId() == View.NO_ID) image.setId(View.generateViewId());
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(ResUtil.dp2px(10), ResUtil.dp2px(8), ResUtil.dp2px(10), ResUtil.dp2px(8));
        bar.setBackground(createPhotoPanelBackground());
        bar.setElevation(ResUtil.dp2px(8));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(64), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.setMargins(0, 0, 0, ResUtil.dp2px(32));
        bar.setLayoutParams(params);

        MaterialButton previous = createPhotoButton(R.string.detail_image_previous, 0);
        MaterialButton save = createPhotoButton(R.string.detail_image_save, R.drawable.ic_tmdb_download);
        MaterialButton next = createPhotoButton(R.string.detail_image_next, 0);
        MaterialButton close = createPhotoButton(R.string.detail_image_close, 0);
        previous.setOnClickListener(view -> showPhotoAt(image, progress, photos, current, request, photoOrientation, -1));
        save.setOnClickListener(view -> savePhoto(photos.get(current[0]), save));
        next.setOnClickListener(view -> showPhotoAt(image, progress, photos, current, request, photoOrientation, 1));
        close.setOnClickListener(view -> dialog.dismiss());
        addPhotoBarButton(bar, previous, image);
        addPhotoBarButton(bar, save, image);
        addPhotoBarButton(bar, next, image);
        addPhotoBarButton(bar, close, image);
        image.setNextFocusDownId(save.getId());
        return bar;
    }

    private void addPhotoBarButton(LinearLayout bar, MaterialButton button, ImageView image) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(46));
        params.setMargins(ResUtil.dp2px(5), 0, ResUtil.dp2px(5), 0);
        button.setLayoutParams(params);
        button.setNextFocusUpId(image.getId());
        bar.addView(button);
    }

    private MaterialButton createPhotoButton(int text, int icon) {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        if (button.getId() == View.NO_ID) button.setId(View.generateViewId());
        button.setText(text);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(ResUtil.dp2px(14), 0, ResUtil.dp2px(14), 0);
        button.setCornerRadius(ResUtil.dp2px(23));
        button.setTextColor(0xFFFFFFFF);
        button.setIconTint(ColorStateList.valueOf(0xFFFFFFFF));
        if (icon != 0) {
            button.setIconResource(icon);
            button.setIconPadding(ResUtil.dp2px(6));
        }
        button.setBackgroundTintList(ColorStateList.valueOf(0x22FFFFFF));
        button.setRippleColor(ColorStateList.valueOf(0x33FFFFFF));
        applyPhotoButtonFocus(button, false);
        button.setOnFocusChangeListener((view, focused) -> applyPhotoButtonFocus(button, focused));
        return button;
    }

    private GradientDrawable createPhotoPanelBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xB3000000);
        drawable.setCornerRadius(ResUtil.dp2px(28));
        drawable.setStroke(ResUtil.dp2px(1), 0x26FFFFFF);
        return drawable;
    }

    private void applyPhotoButtonFocus(MaterialButton button, boolean focused) {
        button.setBackgroundTintList(ColorStateList.valueOf(focused ? 0x33FFFFFF : 0x18FFFFFF));
        button.setStrokeWidth(ResUtil.dp2px(focused ? FOCUS_STROKE_DP : CHIP_STROKE_DP));
        button.setStrokeColor(ColorStateList.valueOf(focused ? FOCUS_STROKE : 0x4DFFFFFF));
    }

    private void showPhotoActionDialog(String url) {
        new MaterialAlertDialogBuilder(this)
                .setItems(new CharSequence[]{getString(R.string.detail_image_save)}, (dialog, which) -> savePhoto(url, null))
                .show();
    }

    private void savePhoto(String url, @Nullable MaterialButton button) {
        if (TextUtils.isEmpty(url) || savingTmdbPhoto) return;
        savingTmdbPhoto = true;
        if (button != null) button.setEnabled(false);
        Notify.show(R.string.detail_image_saving);
        TmdbImageSaver.save(this, highResTmdbImage(url), new TmdbImageSaver.Callback() {
            @Override
            public void success(String name) {
                finishPhotoSave(button);
                Notify.show(getString(R.string.detail_image_save_success, name));
            }

            @Override
            public void error(String message) {
                finishPhotoSave(button);
                String prefix = getString(R.string.detail_image_save_failed);
                Notify.show(TextUtils.isEmpty(message) || prefix.equals(message) ? prefix : prefix + "\n" + message);
            }
        });
    }

    private void finishPhotoSave(@Nullable MaterialButton button) {
        savingTmdbPhoto = false;
        if (button != null) button.setEnabled(true);
    }

    private boolean dialogCancel(Dialog dialog) {
        dialog.dismiss();
        return true;
    }

    private void showPhotoAt(ImageView image, ProgressBar progress, List<String> photos, int[] current, int[] request, int[] photoOrientation, int direction) {
        if (photos.isEmpty()) return;
        int next = (current[0] + direction + photos.size()) % photos.size();
        if (next == current[0]) return;
        current[0] = next;
        loadPhotoImage(image, progress, photos.get(current[0]), request, photoOrientation);
        preloadPhotoNeighbors(photos, current[0]);
    }

    private void loadPhotoImage(ImageView image, ProgressBar progress, String url, int[] request, int[] photoOrientation) {
        int token = ++request[0];
        progress.setVisibility(View.VISIBLE);
        try {
            Glide.with(image)
                    .load(ImgUtil.getUrl(highResTmdbImage(url)))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .fitCenter()
                    .into(new CustomTarget<Drawable>() {
                        @Override
                        public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                            if (token != request[0]) return;
                            image.setImageDrawable(resource);
                            progress.setVisibility(View.GONE);
                            applyPhotoOrientation(resource, photoOrientation);
                        }

                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                            if (token != request[0]) return;
                            if (image.getDrawable() == null && errorDrawable != null) image.setImageDrawable(errorDrawable);
                            progress.setVisibility(View.GONE);
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                        }
                    });
        } catch (Throwable e) {
            progress.setVisibility(View.GONE);
            Notify.show(R.string.detail_tmdb_empty);
        }
    }

    private void preloadPhotoNeighbors(List<String> photos, int current) {
        if (photos.size() <= 1) return;
        List<Integer> positions = new ArrayList<>();
        for (int offset = 1; offset <= PHOTO_PRELOAD_RADIUS && offset < photos.size(); offset++) {
            int next = (current + offset) % photos.size();
            int previous = (current - offset + photos.size()) % photos.size();
            if (!positions.contains(next)) positions.add(next);
            if (!positions.contains(previous)) positions.add(previous);
        }
        for (Integer position : positions) {
            String url = photos.get(position);
            if (TextUtils.isEmpty(url)) continue;
            Glide.with(this).load(ImgUtil.getUrl(highResTmdbImage(url))).diskCacheStrategy(DiskCacheStrategy.ALL).preload();
        }
    }

    private void applyPhotoOrientation(Drawable resource, int[] photoOrientation) {
        if (!Util.isMobile() || resource == null) return;
        int width = resource.getIntrinsicWidth();
        int height = resource.getIntrinsicHeight();
        if (width <= 0 || height <= 0) return;
        int target = width >= height ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT;
        if (photoOrientation[0] == target) return;
        photoOrientation[0] = target;
        setRequestedOrientation(target);
    }

    private void togglePhotoOrientation(int[] photoOrientation) {
        if (!Util.isMobile()) return;
        int current = getRequestedOrientation();
        int actual = getResources().getConfiguration().orientation;
        int target = actual == Configuration.ORIENTATION_PORTRAIT
                || current == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                || current == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                || current == ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        photoOrientation[0] = target;
        setRequestedOrientation(target);
    }

    private String highResTmdbImage(String url) {
        return TmdbImageSelector.originalUrl(url);
    }

    private String episodeDetailTitle(Episode episode, int episodeNumber, JsonObject detail) {
        String name = string(detail, "name");
        if (TextUtils.isEmpty(name)) {
            TmdbEpisode tmdbEpisode = tmdbEpisodes.get(episodeNumber);
            name = tmdbEpisode == null ? "" : tmdbEpisode.getTitle();
        }
        if (TextUtils.isEmpty(name) && episode != null) name = episode.getName();
        return getString(R.string.detail_episode_detail_title, episodeNumber, name);
    }

    private String episodeMeta(JsonObject detail) {
        List<String> parts = new ArrayList<>();
        String date = string(detail, "air_date");
        if (!TextUtils.isEmpty(date)) parts.add(date);
        if (detail != null && detail.has("runtime") && !detail.get("runtime").isJsonNull()) {
            int runtime = detail.get("runtime").getAsInt();
            if (runtime > 0) parts.add(getString(R.string.detail_runtime_format, runtime));
        }
        if (detail != null && detail.has("vote_average") && !detail.get("vote_average").isJsonNull()) {
            double vote = detail.get("vote_average").getAsDouble();
            if (vote > 0) parts.add(getString(R.string.detail_score, String.format(Locale.US, "%.1f", vote)));
        }
        return TextUtils.join(" · ", parts);
    }

    private String episodeCrew(JsonObject detail) {
        Map<String, List<String>> jobs = new LinkedHashMap<>();
        for (JsonElement element : array(detail, "crew")) {
            if (!element.isJsonObject()) continue;
            JsonObject person = element.getAsJsonObject();
            String job = string(person, "job", "department");
            String name = string(person, "name");
            if (TextUtils.isEmpty(job) || TextUtils.isEmpty(name)) continue;
            List<String> names = jobs.computeIfAbsent(job, key -> new ArrayList<>());
            if (!names.contains(name) && names.size() < 4) names.add(name);
            if (jobs.size() >= 6) break;
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : jobs.entrySet()) {
            if (!entry.getValue().isEmpty()) lines.add(entry.getKey() + ": " + TextUtils.join(" / ", entry.getValue()));
        }
        return TextUtils.join("\n", lines);
    }

    private ArrayList<String> selectedTmdbEpisodeTitles() {
        Map<Integer, String> titles = new LinkedHashMap<>();
        for (Map.Entry<Integer, TmdbEpisode> entry : tmdbEpisodes.entrySet()) {
            if (!TextUtils.isEmpty(entry.getValue().getTitle())) titles.put(entry.getKey(), entry.getValue().getTitle());
        }
        List<TmdbEpisode> episodes = tmdbSeasonEpisodes.get(tmdbEpisodeDataSeason(selectedFlag == null ? null : selectedFlag.getEpisodes()));
        if (episodes != null) {
            for (TmdbEpisode episode : episodes) {
                if (!TextUtils.isEmpty(episode.getTitle())) titles.put(episode.getNumber(), episode.getTitle());
            }
        }
        ArrayList<String> result = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : titles.entrySet()) result.add(entry.getKey() + "\t" + entry.getValue());
        return result;
    }

    private Episode findEpisodeByUrl(String url, List<Episode> episodes) {
        if (TextUtils.isEmpty(url) || episodes == null) return null;
        for (Episode episode : episodes) if (url.equals(episode.getUrl())) return episode;
        return null;
    }

    private boolean isHistoryEpisode(Episode episode, History item) {
        if (episode == null || item == null) return false;
        if (!TextUtils.isEmpty(item.getEpisodeUrl()) && item.getEpisodeUrl().equals(episode.getUrl())) return true;
        return episode.matchesName(item.getEpisode()) || episode.getDisplayName().equals(item.getVodRemarks()) || historyEpisodeTitle(episode).equals(item.getVodRemarks());
    }

    private String historyEpisodeTitle(Episode episode) {
        int number = episodeNumberForHistory(episode);
        String label = number > 0 ? String.valueOf(number) : episode.getDisplayName();
        String title = tmdbEpisodeTitle(number);
        return TmdbEpisodeAdapter.formatTitle(label, episode.getName(), title);
    }

    private String tmdbEpisodeTitle(int number) {
        return tmdbEpisodeTitle(tmdbEpisodeDataSeason(selectedFlag == null ? null : selectedFlag.getEpisodes()), number);
    }

    private String tmdbEpisodeTitle(int seasonNumber, int number) {
        if (number <= 0) return "";
        TmdbEpisode tmdbEpisode = seasonNumber == selectedSeasonNumber || seasonNumber < 0 ? tmdbEpisodes.get(number) : null;
        if (tmdbEpisode != null && !TextUtils.isEmpty(tmdbEpisode.getTitle())) return tmdbEpisode.getTitle();
        List<TmdbEpisode> episodes = tmdbSeasonEpisodes.get(seasonNumber < 0 ? selectedSeasonNumber : seasonNumber);
        if (episodes == null) return "";
        for (TmdbEpisode episode : episodes) {
            if (episode.getNumber() == number && !TextUtils.isEmpty(episode.getTitle())) return episode.getTitle();
        }
        return "";
    }

    private void refreshCurrentHistoryEpisodeTitle() {
        if (selectedEpisode == null || history == null || Setting.isIncognito()) return;
        History saved = History.find(getHistoryKey());
        if (saved == null || !isHistoryEpisode(selectedEpisode, saved)) return;
        String title = historyEpisodeTitle(selectedEpisode);
        if (TextUtils.isEmpty(title) || title.equals(saved.getVodRemarks())) return;
        saved.setVodName(playbackHistoryName());
        saved.setVodPic(playbackHistoryPic());
        saved.setVodRemarks(title);
        saved.setEpisodeUrl(selectedEpisode.getUrl());
        saved.save();
        history = saved;
        syncDanmakuCompatHistory();
        RefreshEvent.history();
    }

    private int episodeNumberForHistory(Episode episode) {
        if (episode == null || selectedFlag == null || selectedFlag.getEpisodes() == null) return -1;
        EpisodePosition position = episodePosition(episode, selectedFlag.getEpisodes());
        return position.number();
    }

    private String playbackHistoryName() {
        return coalesce(matchedTmdbItem == null ? "" : matchedTmdbItem.getTitle(), vod == null ? "" : vod.getName(), getNameText());
    }

    private String playbackHistoryPic() {
        return coalesce(matchedTmdbItem == null ? "" : matchedTmdbItem.getPosterUrl(), matchedTmdbItem == null ? "" : matchedTmdbItem.getBackdropUrl(), vod == null ? "" : vod.getPic(), getPicText());
    }

    private boolean isFusionMode() {
        return getDetailMode() == Setting.DETAIL_OPEN_FUSION || getIntent().getBooleanExtra("fusion", false);
    }

    private boolean isPlayerMode() {
        return getDetailMode() == Setting.DETAIL_OPEN_PLAYER;
    }

    private boolean isCinemaMode() {
        return getIntent().getIntExtra("detail_mode", Setting.getDetailOpenMode()) == Setting.DETAIL_OPEN_CINEMA || Setting.isTmdbCinemaStyle();
    }

    private int getDetailMode() {
        if (getIntent().hasExtra("detail_mode")) return normalizeDetailMode(getIntent().getIntExtra("detail_mode", Setting.DETAIL_OPEN_ENHANCED));
        return getIntent().getBooleanExtra("fusion", false) ? Setting.DETAIL_OPEN_FUSION : Setting.DETAIL_OPEN_ENHANCED;
    }

    private int detailModeTitle() {
        if (isFusionMode()) return R.string.setting_detail_open_fusion;
        if (isPlayerMode()) return R.string.setting_detail_open_player;
        return R.string.setting_detail_open_enhanced;
    }

    private boolean isAutoPlayMode() {
        return getIntent().getBooleanExtra("auto_play", false);
    }

    private boolean isInlinePlayerMode() {
        return isFusionMode() || detailPlayerActive;
    }

    private void maybeAutoPlayInline() {
        if ((!isFusionMode() && !isAutoPlayMode()) || autoPlayed) return;
        autoPlayed = true;
        binding.playerPanel.post(this::onPlay);
    }

    private void playDetailFullscreen() {
        if (selectedFlag == null || selectedEpisode == null) return;
        detailPlayerActive = true;
        binding.playerError.setTextColor(0xFFFFFFFF);
        binding.playerTitle.setTextColor(0xFFFFFFFF);
        tintInlineControl(inlineControlsView());
        setPlayerCard(lightTheme ? ThemeColors.light() : ThemeColors.dark());
        ensureInlineDanmakuController();
        binding.playerPanel.setVisibility(View.VISIBLE);
        enterInlineFullscreen();
        playInline();
    }

    private void playInline() {
        if (selectedFlag == null || selectedEpisode == null) return;
        inlinePlaybackLoading = true;
        int generation = ++inlinePlaybackGeneration;
        String key = getKeyText();
        String flag = selectedFlag.getFlag();
        String episodeUrl = selectedEpisode.getUrl();
        stopInlinePlayerForReload();
        showInlineLoading();
        updateInlineDisplayPanel();
        updateInlineTitle();
        Task.execute(() -> {
            try {
                Result result = SiteApi.playerContent(key, flag, episodeUrl);
                runOnAliveUi(() -> {
                    if (!isInlinePlaybackRequestCurrent(generation, key, flag, episodeUrl)) return;
                    startInlinePlayer(result);
                });
            } catch (Throwable e) {
                String message = e.getMessage();
                runOnAliveUi(() -> {
                    if (!isInlinePlaybackRequestCurrent(generation, key, flag, episodeUrl)) return;
                    showInlineError(TextUtils.isEmpty(message) ? getString(R.string.error_play_url) : message);
                });
            }
        });
    }

    private void stopInlinePlayerForReload() {
        subtitlePlaybackSession.stop(this);
        inlineStartPosition = C.TIME_UNSET;
        inlineStartPositionApplied = false;
        pendingInlineResult = null;
        currentInlineResult = null;
        introSkipPlayback.reset();
        if (service() == null || player() == null || player().isEmpty()) {
            updateInlineButtons(false);
            return;
        }
        player().stop();
        player().clear();
        updateInlineButtons(false);
    }

    private void startInlinePlayer(Result result) {
        currentInlineResult = result;
        useParse = result.shouldUseParse();
        if (result.hasPosition() && history != null) history.setPosition(result.getPosition());
        if (result.hasDesc() && !hasTmdbOverview()) {
            vod.setContent(result.getDesc());
            bindOverview();
        }
        if (service() == null || controller() == null) {
            pendingInlineResult = result;
            return;
        }
        inlinePlaybackLoading = false;
        inlineStarted = true;
        pendingInlineResult = null;
        hideInlineControls();
        resetInlineShortDramaMode();
        updateInlineTitle();
        updateInlineButtons(false);
        player().stop();
        player().clear();
        inlineStartPosition = getInlineResumePosition();
        inlineStartPositionApplied = false;
        player().switchPlayer(PlayerSetting.getPlayer());
        updateInlineHistoryPlayer();
        setInlineSpeed(history == null ? NORMAL_SPEED : history.getSpeed());
        updateInlineButtons(false);
        Site site = getCurrentSite();
        ensureInlineDanmakuController();
        startPlayer(getHistoryKey(), result, useParse, site == null ? 0 : site.getTimeout(), buildMetadata());
        subtitlePlaybackSession.onPlaybackStarted(this, result);
        searchInlineDanmaku(result);
        binding.playerPanel.requestFocus();
    }

    private void searchInlineDanmaku(Result result) {
        if (!DanmakuApi.canSearch() || history == null || selectedEpisode == null) return;
        DanmakuApi.search(playbackHistoryName(), historyEpisodeTitle(selectedEpisode), danmaku -> applyInlineDanmaku(result, danmaku));
    }

    private void applyInlineDanmaku(Result result, Danmaku danmaku) {
        if (isFinishing() || isDestroyed() || service() == null || player() == null || !inlineStarted || result != currentInlineResult || !isOwner()) return;
        if (DanmakuSetting.isSpiderFirst() && !result.getDanmaku().isEmpty()) player().addDanmaku(danmaku);
        else player().setDanmaku(danmaku);
        refreshInlineDanmakuButtons();
    }

    private void refreshInlineDanmakuButtons() {
        if (!isInlinePlayerMode() || service() == null || player() == null || player().isEmpty()) return;
        updateInlineButtons(player().isPlaying());
    }

    private void showInlineError(String text) {
        inlinePlaybackLoading = false;
        hideInlineLoading();
        binding.playerError.setText(text);
        binding.playerError.setVisibility(View.VISIBLE);
        updateInlineDisplayPanel();
    }

    private void showInlineLoading() {
        binding.playerError.setVisibility(View.GONE);
        binding.playerProgress.setVisibility(View.VISIBLE);
        tintInlineLoading();
        updateInlineLoadingTraffic();
        hideInlineControls();
    }

    private void hideInlineLoading() {
        binding.playerProgress.setVisibility(View.GONE);
        binding.playerProgressTraffic.setVisibility(View.GONE);
        Traffic.reset();
    }

    private void updateInlineLoadingTraffic() {
        if (binding == null || binding.playerProgress.getVisibility() != View.VISIBLE) return;
        Traffic.setSpeed(binding.playerProgressTraffic);
        tintInlineLoading();
    }

    private void toggleInlinePlayback() {
        if (!isInlinePlayerMode()) return;
        if (controller() == null || service() == null || player().isEmpty()) {
            onPlay();
            return;
        }
        if (player().isPlaying()) controller().pause();
        else controller().play();
        setInlineHideCallback();
    }

    private void toggleInlineControls() {
        if (!isInlinePlayerMode() || !inlineStarted) return;
        if (inlineControlsView().getVisibility() == View.VISIBLE) hideInlineControls();
        else showInlineControls(true, false);
    }

    private void showInlineControls(boolean show) {
        showInlineControls(show, true);
    }

    private void showInlineControls(boolean show, boolean focus) {
        if (!isInlinePlayerMode() || !inlineStarted) return;
        if (!show) {
            hideInlineControls();
            return;
        }
        updateInlineTitle();
        updateInlineButtons(service() != null && player() != null && !player().isEmpty() && player().isPlaying());
        inlineControlsView().setVisibility(View.VISIBLE);
        if (focus || !Util.isMobile()) focusInlineDefaultControl();
        touchInlineControls();
        updateInlineDisplayPanel();
    }

    private void hideInlineControls() {
        if (binding == null) return;
        boolean hadControlFocus = hasFocusedChild(inlineControlsView());
        inlineControlsView().setVisibility(View.GONE);
        hideMobileFusionPlayerActionDock();
        App.removeCallbacks(inlineHideControls);
        if (hadControlFocus) binding.playerPanel.requestFocus();
        updateInlineDisplayPanel();
    }

    private void prepareInlinePlayerTransition() {
        if (binding == null) return;
        inlinePauseInfo = false;
        App.removeCallbacks(inlineHideControls);
        inlineControlsView().setVisibility(View.GONE);
        hideMobileFusionPlayerActionDock();
        hideInlineGestureViews();
        hideInlineDisplayPanel();
        View focus = getCurrentFocus();
        if (focus != null && isDescendant(binding.playerPanel, focus)) focus.clearFocus();
        binding.playerPanel.requestFocus();
    }

    private void hideInlineDisplayPanel() {
        binding.playerDisplayTitle.setVisibility(View.GONE);
        binding.playerDisplaySize.setVisibility(View.GONE);
        binding.playerDisplayTopLeft.setVisibility(View.GONE);
        binding.playerDisplayClock.setVisibility(View.GONE);
        binding.playerDisplayTraffic.setVisibility(View.GONE);
        binding.playerDisplayBottomProgress.setVisibility(View.GONE);
        binding.playerDisplayMini.setVisibility(View.GONE);
    }

    private boolean suppressInlineDisplayForLeanbackFusionExit() {
        if (!Util.isLeanback() || !isFusionMode()) return false;
        inlineDisplaySuppressUntil = Math.max(inlineDisplaySuppressUntil, SystemClock.uptimeMillis() + LEANBACK_FUSION_EXIT_DISPLAY_SUPPRESS_MS);
        hideInlineDisplayPanel();
        return true;
    }

    private boolean isInlineDisplaySuppressed() {
        return SystemClock.uptimeMillis() < inlineDisplaySuppressUntil;
    }

    private void hideInlineControlsIfIdle() {
        if (!Util.isMobile() && hasFocusedChild(inlineControlsView())) {
            App.post(inlineHideControls, Constant.INTERVAL_HIDE);
            return;
        }
        long idle = SystemClock.uptimeMillis() - lastInlineControlInteraction;
        if (idle < Constant.INTERVAL_HIDE) {
            App.post(inlineHideControls, Constant.INTERVAL_HIDE - idle);
            return;
        }
        hideInlineControls();
    }

    private void setInlineHideCallback() {
        touchInlineControls();
    }

    private void touchInlineControls() {
        lastInlineControlInteraction = SystemClock.uptimeMillis();
        App.removeCallbacks(inlineHideControls);
        App.post(inlineHideControls, Constant.INTERVAL_HIDE);
    }

    private void focusInlineDefaultControl() {
        if (!Util.isMobile()) {
            inlineControlsView().post(() -> {
                if (isInlineControlsVisible()) binding.playerFullscreenAction.requestFocus();
            });
            return;
        }
        if (hasFocusedChild(inlineControlsView())) return;
        inlineControlsView().post(() -> {
            if (isInlineControlsVisible() && !hasFocusedChild(inlineControlsView())) getInlineControlFocus().requestFocus();
        });
    }

    private View getInlineControlFocus() {
        if (!Util.isMobile()) return binding.playerFullscreenAction;
        if (inlineControlFocus != null && isVisibleInHierarchy(inlineControlFocus) && inlineControlFocus.isEnabled()) return inlineControlFocus;
        if (Util.isMobile()) return detailControlView(R.id.play, View.class);
        return binding.playerFullscreenAction;
    }

    private void rememberInlineControlFocus() {
        View focus = getCurrentFocus();
        if (focus != null && isDescendant((ViewGroup) inlineControlsView(), focus)) inlineControlFocus = focus;
    }

    private boolean isVisibleInHierarchy(View view) {
        for (View current = view; current != null; current = current.getParent() instanceof View parent ? parent : null) {
            if (current.getVisibility() != View.VISIBLE) return false;
        }
        return true;
    }

    private boolean isDescendant(ViewGroup parent, View child) {
        if (parent == null || child == null) return false;
        if (parent == child) return true;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View view = parent.getChildAt(i);
            if (view == child) return true;
            if (view instanceof ViewGroup group && isDescendant(group, child)) return true;
        }
        return false;
    }

    private boolean isInlineControlsVisible() {
        return binding != null && inlineControlsView().getVisibility() == View.VISIBLE;
    }

    private void updateInlineButtons(boolean playing) {
        if (!isInlinePlayerMode() || inlineControlController == null) {
            setInlineDecodeText(getString(R.string.play_decode_idle));
            return;
        }
        boolean hasPlayer = service() != null && !player().isEmpty();
        setInlineSpeedText(service() == null || player().isEmpty() ? getString(R.string.play_speed) : player().getSpeedText());
        setInlineDecodeText(inlineDecodeText(hasPlayer));
        binding.playerExternal.setText(service() == null ? getString(R.string.play_exo) : player().getPlayerText());
        binding.playerScale.setText(scaleLabel());
        binding.playerQuality.setText(qualityLabel());
        binding.playerParse.setText(parseLabel());
        binding.playerOpening.setText(inlineOpeningLabel());
        binding.playerEnding.setText(inlineEndingLabel());
        inlineControlController.updateSize(binding.playerSize, inlineFullscreen);
        int episodeCount = selectedFlag == null || selectedFlag.getEpisodes() == null ? 0 : selectedFlag.getEpisodes().size();
        boolean hasPrev = hasAdjacentEpisode(-1);
        boolean hasNext = hasAdjacentEpisode(1);
        boolean hasTitle = hasPlayer && player().haveTitle();
        setButtonEnabled(binding.playerPrev, hasPrev);
        setButtonEnabled(binding.playerNext, hasNext);
        boolean inlineQuality = canChangeInlineQuality();
        boolean inlineVideoTrackAsQuality = isInlineVideoTrackAsQuality();
        setButtonEnabled(binding.playerQuality, inlineQuality);
        setButtonEnabled(binding.playerParse, useParse && !VodConfig.get().getParses().isEmpty());
        setButtonEnabled(binding.playerSpeed, hasPlayer);
        setButtonEnabled(binding.playerScale, hasPlayer);
        setButtonEnabled(binding.playerRefresh, hasPlayer);
        setButtonEnabled(binding.playerChangeSource, vod != null);
        setButtonEnabled(binding.playerRepeat, hasPlayer);
        setButtonEnabled(binding.playerDisplay, hasPlayer);
        setButtonEnabled(binding.playerDecode, hasPlayer);
        setButtonEnabled(binding.playerTextTrack, hasPlayer);
        setButtonEnabled(binding.playerAudioTrack, hasPlayer);
        setButtonEnabled(binding.playerVideoTrack, hasPlayer);
        setButtonEnabled(binding.playerOpening, hasPlayer);
        setButtonEnabled(binding.playerEnding, hasPlayer);
        setButtonEnabled(binding.playerDanmaku, hasPlayer && inlineControlController.hasDanmakuControl());
        setButtonEnabled(binding.playerDanmakuToggle, hasPlayer && inlineControlController.hasDanmakuControl());
        setButtonEnabled(binding.playerExternal, hasPlayer);
        setButtonEnabled(binding.playerChapter, hasTitle);
        setButtonEnabled(binding.playerEpisodes, episodeCount > 0);
        setButtonEnabled(binding.playerCast, hasPlayer && hasInlineCast());
        setButtonEnabled(binding.playerInfo, hasPlayer && hasInlineInfo());
        setButtonEnabled(binding.playerFullscreenAction, hasPlayer);
        setButtonEnabled(binding.playerFullscreen, hasPlayer);
        binding.playerCast.setVisibility(hasInlineCast() ? View.VISIBLE : View.GONE);
        binding.playerInfo.setVisibility(hasInlineInfo() ? View.VISIBLE : View.GONE);
        binding.playerActionRow.setVisibility(View.VISIBLE);
        binding.playerDanmakuToggle.setVisibility(View.GONE);
        binding.playerEpisodes.setVisibility(episodeCount < 2 ? View.GONE : View.VISIBLE);
        binding.playerQuality.setVisibility(inlineQuality ? View.VISIBLE : View.GONE);
        binding.playerVideoTrack.setVisibility(hasPlayer && player().haveTrack(C.TRACK_TYPE_VIDEO) && !inlineVideoTrackAsQuality ? View.VISIBLE : View.GONE);
        binding.playerParse.setVisibility(useParse && !VodConfig.get().getParses().isEmpty() ? View.VISIBLE : View.GONE);
        binding.playerDanmaku.setVisibility(hasPlayer && inlineControlController.hasDanmakuControl() ? View.VISIBLE : View.GONE);
        binding.playerChapter.setVisibility(hasTitle ? View.VISIBLE : View.GONE);
        binding.playerRepeat.setSelected(hasPlayer && player().isRepeatOne());
        setInlineFullscreenIcon();
        updateMobileInlineButtons(playing, hasPlayer, hasPrev, hasNext, hasTitle);
        applyInlinePlayerButtonSettings();
        updateInlineDisplayPanel();
    }

    private void applyInlinePlayerButtonSettings() {
        PlayerButtonSetting.applyOrder((ViewGroup) binding.playerActionRow.getChildAt(0), inlinePlayerButtonMap());
        if (!Util.isMobile() || detailActionRoot == null) return;
        PlayerButtonSetting.applyOrder(detailActionRoot.findViewById(R.id.container), mobileInlinePlayerButtonMap());
    }

    private Map<String, View> inlinePlayerButtonMap() {
        Map<String, View> buttons = new LinkedHashMap<>();
        buttons.put(PlayerButtonSetting.FULLSCREEN, binding.playerFullscreenAction);
        buttons.put(PlayerButtonSetting.NEXT, binding.playerNext);
        buttons.put(PlayerButtonSetting.PREV, binding.playerPrev);
        buttons.put(PlayerButtonSetting.EPISODES, binding.playerEpisodes);
        buttons.put(PlayerButtonSetting.RESET, binding.playerRefresh);
        buttons.put(PlayerButtonSetting.CHANGE, binding.playerChangeSource);
        buttons.put(PlayerButtonSetting.PLAYER, binding.playerExternal);
        buttons.put(PlayerButtonSetting.DECODE, binding.playerDecode);
        buttons.put(PlayerButtonSetting.SPEED, binding.playerSpeed);
        buttons.put(PlayerButtonSetting.SCALE, binding.playerScale);
        buttons.put(PlayerButtonSetting.TEXT, binding.playerTextTrack);
        buttons.put(PlayerButtonSetting.AUDIO, binding.playerAudioTrack);
        buttons.put(PlayerButtonSetting.VIDEO, binding.playerVideoTrack);
        buttons.put(PlayerButtonSetting.OPENING, binding.playerOpening);
        buttons.put(PlayerButtonSetting.ENDING, binding.playerEnding);
        buttons.put(PlayerButtonSetting.DANMAKU, binding.playerDanmaku);
        buttons.put(PlayerButtonSetting.TITLE, binding.playerChapter);
        buttons.put(PlayerButtonSetting.REPEAT, binding.playerRepeat);
        return buttons;
    }

    private Map<String, View> mobileInlinePlayerButtonMap() {
        Map<String, View> buttons = new LinkedHashMap<>();
        buttons.put(PlayerButtonSetting.PLAYER, detailActionView(R.id.player, View.class));
        buttons.put(PlayerButtonSetting.DECODE, detailActionView(R.id.decode, View.class));
        buttons.put(PlayerButtonSetting.SPEED, detailActionView(R.id.speed, View.class));
        buttons.put(PlayerButtonSetting.SCALE, detailActionView(R.id.scale, View.class));
        buttons.put(PlayerButtonSetting.RESET, detailActionView(R.id.reset, View.class));
        buttons.put(PlayerButtonSetting.REPEAT, detailActionView(R.id.repeat, View.class));
        buttons.put(PlayerButtonSetting.TEXT, detailActionView(R.id.text, View.class));
        buttons.put(PlayerButtonSetting.AUDIO, detailActionView(R.id.audio, View.class));
        buttons.put(PlayerButtonSetting.VIDEO, detailActionView(R.id.video, View.class));
        buttons.put(PlayerButtonSetting.OPENING, detailActionView(R.id.opening, View.class));
        buttons.put(PlayerButtonSetting.ENDING, detailActionView(R.id.ending, View.class));
        buttons.put(PlayerButtonSetting.DANMAKU, detailActionView(R.id.danmaku, View.class));
        buttons.put(PlayerButtonSetting.TITLE, detailActionView(R.id.chapter, View.class));
        buttons.put(PlayerButtonSetting.EPISODES, detailActionView(R.id.episodes, View.class));
        return buttons;
    }

    private void updateMobileInlineButtons(boolean playing, boolean hasPlayer, boolean hasPrev, boolean hasNext, boolean hasTitle) {
        if (!Util.isMobile()) return;
        updateMobileInlineSideControlMargins();
        boolean locked = isLock();
        TextView title = detailControlView(R.id.title, TextView.class);
        TextView size = detailControlView(R.id.size, TextView.class);
        View action = detailActionRoot;
        CharSequence titleText = inlineTitleText();
        title.setText(titleText);
        title.setVisibility(!locked && hasPlayer && !TextUtils.isEmpty(titleText) ? View.VISIBLE : View.INVISIBLE);
        inlineControlController.updateSize(size, inlineFullscreen);
        updateMobileInlineControlStatus(hasPlayer);
        detailControlView(R.id.play, ImageView.class).setImageResource(playing ? androidx.media3.ui.R.drawable.exo_icon_pause : androidx.media3.ui.R.drawable.exo_icon_play);
        detailControlView(R.id.lock, ImageView.class).setImageResource(locked ? R.drawable.ic_control_lock_on : R.drawable.ic_control_lock_off);
        detailActionView(R.id.speed, TextView.class).setText(binding.playerSpeed.getText());
        detailActionView(R.id.player, TextView.class).setText(binding.playerExternal.getText());
        detailActionView(R.id.decode, TextView.class).setText(binding.playerDecode.getText());
        detailActionView(R.id.scale, TextView.class).setText(binding.playerScale.getText());
        detailActionView(R.id.actionQuality, TextView.class).setText(binding.playerQuality.getText());
        detailActionView(R.id.opening, TextView.class).setText(binding.playerOpening.getText());
        detailActionView(R.id.ending, TextView.class).setText(binding.playerEnding.getText());
        setButtonEnabled(detailControlView(R.id.prev, View.class), hasPrev);
        setButtonEnabled(detailControlView(R.id.next, View.class), hasNext);
        setButtonEnabled(detailControlView(R.id.fullscreen, View.class), hasPlayer);
        setButtonEnabled(detailControlView(R.id.danmaku, View.class), hasPlayer && inlineControlController.hasDanmakuControl());
        setButtonEnabled(detailControlView(R.id.lock, View.class), hasPlayer);
        setButtonEnabled(detailControlView(R.id.rotate, View.class), hasPlayer);
        setButtonEnabled(detailControlView(R.id.pip, View.class), canShowInlinePiP(hasPlayer, locked));
        setButtonEnabled(detailActionView(R.id.player, View.class), hasPlayer);
        setButtonEnabled(detailActionView(R.id.decode, View.class), hasPlayer);
        setButtonEnabled(detailActionView(R.id.speed, View.class), hasPlayer);
        setButtonEnabled(detailActionView(R.id.scale, View.class), hasPlayer);
        boolean inlineQuality = canChangeInlineQuality();
        boolean inlineVideoTrackAsQuality = isInlineVideoTrackAsQuality();
        setButtonEnabled(detailActionView(R.id.actionQuality, View.class), inlineQuality);
        setButtonEnabled(detailActionView(R.id.reset, View.class), hasPlayer);
        setButtonEnabled(detailActionView(R.id.repeat, View.class), hasPlayer);
        setButtonEnabled(detailActionView(R.id.text, View.class), hasPlayer);
        setButtonEnabled(detailActionView(R.id.audio, View.class), hasPlayer);
        setButtonEnabled(detailActionView(R.id.video, View.class), hasPlayer);
        setButtonEnabled(detailActionView(R.id.opening, View.class), hasPlayer);
        setButtonEnabled(detailActionView(R.id.ending, View.class), hasPlayer);
        setButtonEnabled(detailActionView(R.id.danmaku, View.class), hasPlayer && inlineControlController.hasDanmakuControl());
        setButtonEnabled(detailActionView(R.id.chapter, View.class), hasTitle);
        setButtonEnabled(detailActionView(R.id.episodes, View.class), selectedFlag != null && selectedFlag.getEpisodes() != null && !selectedFlag.getEpisodes().isEmpty());
        detailControlView(R.id.top, View.class).setVisibility(locked ? View.GONE : View.VISIBLE);
        detailControlView(R.id.center, View.class).setVisibility(locked ? View.GONE : View.VISIBLE);
        detailControlView(R.id.bottom, View.class).setVisibility(locked ? View.GONE : View.VISIBLE);
        detailControlView(R.id.back, View.class).setVisibility(locked ? View.GONE : View.VISIBLE);
        detailControlView(R.id.fullscreen, View.class).setVisibility(locked ? View.GONE : View.VISIBLE);
        detailControlView(R.id.lock, View.class).setVisibility(inlineFullscreen ? View.VISIBLE : View.GONE);
        detailControlView(R.id.rotate, View.class).setVisibility(inlineFullscreen && !locked && !inlineShortDramaMode ? View.VISIBLE : View.GONE);
        detailControlView(R.id.pip, View.class).setVisibility(canShowInlinePiP(hasPlayer, locked) ? View.VISIBLE : View.GONE);
        detailControlView(R.id.prev, View.class).setVisibility(!locked && hasPrev ? View.VISIBLE : View.GONE);
        detailControlView(R.id.next, View.class).setVisibility(!locked && hasNext ? View.VISIBLE : View.GONE);
        detailControlView(R.id.cast, View.class).setVisibility(!locked && hasInlineCast() ? View.VISIBLE : View.GONE);
        detailControlView(R.id.info, View.class).setVisibility(!locked && hasInlineInfo() ? View.VISIBLE : View.GONE);
        detailControlView(R.id.setting, View.class).setVisibility(!locked && hasPlayer ? View.VISIBLE : View.GONE);
        detailControlView(R.id.danmaku, View.class).setVisibility(!locked && hasPlayer && inlineControlController.hasDanmakuControl() ? View.VISIBLE : View.GONE);
        detailControlView(R.id.parse, RecyclerView.class).setVisibility(!locked && inlineFullscreen && useParse && !VodConfig.get().getParses().isEmpty() ? View.VISIBLE : View.GONE);
        if (inlineParseAdapter != null) inlineParseAdapter.notifyDataSetChanged();
        detailActionView(R.id.text, View.class).setVisibility(hasPlayer && (player().haveTrack(C.TRACK_TYPE_TEXT) || player().isVod()) ? View.VISIBLE : View.GONE);
        detailActionView(R.id.audio, View.class).setVisibility(hasPlayer && player().haveTrack(C.TRACK_TYPE_AUDIO) ? View.VISIBLE : View.GONE);
        detailActionView(R.id.video, View.class).setVisibility(hasPlayer && player().haveTrack(C.TRACK_TYPE_VIDEO) && !inlineVideoTrackAsQuality ? View.VISIBLE : View.GONE);
        detailActionView(R.id.opening, View.class).setVisibility(hasPlayer ? View.VISIBLE : View.GONE);
        detailActionView(R.id.ending, View.class).setVisibility(hasPlayer ? View.VISIBLE : View.GONE);
        detailActionView(R.id.danmaku, View.class).setVisibility(hasPlayer && inlineControlController.hasDanmakuControl() ? View.VISIBLE : View.GONE);
        detailActionView(R.id.chapter, View.class).setVisibility(hasTitle ? View.VISIBLE : View.GONE);
        detailActionView(R.id.actionQuality, View.class).setVisibility(inlineQuality ? View.VISIBLE : View.GONE);
        detailActionView(R.id.repeat, View.class).setSelected(hasPlayer && player().isRepeatOne());
        hideMobileFusionPlayerActionDock();
        action.setVisibility(inlineFullscreen && !locked ? View.VISIBLE : View.GONE);
        inlineControlController.updateDanmakuState();
    }

    private void hideMobileFusionPlayerActionDock() {
        if (!Util.isMobile() || detailActionRoot == null || binding.mobileFusionPlayerActionDock == null) return;
        restoreMobileInlinePlayerAction();
        binding.mobileFusionPlayerActionDock.setVisibility(View.GONE);
    }

    private void restoreMobileInlinePlayerAction() {
        if (detailActionRoot == null || detailActionParent == null || detailActionRoot.getParent() == detailActionParent) return;
        if (detailActionRoot.getParent() instanceof ViewGroup parent) parent.removeView(detailActionRoot);
        int index = Math.min(Math.max(detailActionIndex, 0), detailActionParent.getChildCount());
        detailActionParent.addView(detailActionRoot, index, detailActionLayoutParams);
    }

    private CharSequence inlineDecodeText(boolean hasPlayer) {
        return hasPlayer ? player().getDecodeText() : getString(R.string.play_decode_idle);
    }

    private void setInlineDecodeText(CharSequence text) {
        binding.playerDecode.setText(text);
        if (Util.isMobile() && detailActionRoot != null) detailActionView(R.id.decode, TextView.class).setText(text);
    }

    private void updateMobileInlineControlStatus(boolean hasPlayer) {
        if (!Util.isMobile()) return;
        View batteryInfo = detailControlView(R.id.batteryInfo, View.class);
        boolean showBattery = hasPlayer && inlineFullscreen && !isLock();
        batteryInfo.setVisibility(showBattery ? View.VISIBLE : View.GONE);
        if (!showBattery) return;
        updateMobileInlineControlTime();
        updateMobileInlineBatteryIcon();
    }

    private void updateMobileInlineControlTime() {
        if (!Util.isMobile() || detailControlRoot == null) return;
        detailControlView(R.id.time, TextView.class).setText(LocalDateTime.now().format(Formatters.TIME));
    }

    private void updateMobileInlineBatteryIcon() {
        int level = BatteryUtil.getLevel(this);
        ImageView battery = detailControlView(R.id.battery, ImageView.class);
        if (level < 0) {
            battery.setVisibility(View.GONE);
            return;
        }
        battery.setVisibility(View.VISIBLE);
        battery.setImageResource(BatteryUtil.getIcon(level));
    }

    private void showInlineDisplay() {
        DisplayDialog.show(this, () -> {
            updateInlineButtons(service() != null && player() != null && !player().isEmpty() && player().isPlaying());
            updateInlineDisplayPanel();
        });
    }

    private void updateInlineDisplayPanel() {
        if (binding == null) return;
        if (isInPictureInPictureMode() || isInlineDisplaySuppressed()) {
            hideInlineDisplayPanel();
            return;
        }
        boolean hasPlayer = isInlinePlayerMode() && service() != null && player() != null && !player().isEmpty();
        boolean centerVisible = binding.gestureSeek.getVisibility() == View.VISIBLE;
        boolean canShow = hasPlayer && inlineControlsView().getVisibility() != View.VISIBLE && binding.playerProgress.getVisibility() != View.VISIBLE && binding.playerError.getVisibility() != View.VISIBLE;
        boolean showTitle = canShow && (inlinePauseInfo || PlayerSetting.isDisplayTitle()) && !TextUtils.isEmpty(inlineTitleText());
        boolean showSize = canShow && (inlinePauseInfo || PlayerSetting.isDisplaySize()) && !TextUtils.isEmpty(player().getSizeText());
        boolean showProgress = !centerVisible && canShow && PlayerSetting.isDisplayProgress() && player().getDuration() > 0;
        boolean showMini = !centerVisible && canShow && PlayerSetting.isDisplayMini() && player().getDuration() > 0;
        binding.playerDisplayTitle.setText(inlineTitleText());
        binding.playerDisplaySize.setText(showSize ? player().getSizeText() : "");
        tintInlineDisplay();
        binding.playerDisplayTitle.setVisibility(showTitle ? View.VISIBLE : View.GONE);
        binding.playerDisplaySize.setVisibility(showSize ? View.VISIBLE : View.GONE);
        binding.playerDisplayTopLeft.setVisibility(showTitle || showSize ? View.VISIBLE : View.GONE);
        binding.playerDisplayClock.setText(TIME_FORMAT.format(LocalDateTime.now()));
        binding.playerDisplayClock.setVisibility(canShow && (inlinePauseInfo || PlayerSetting.isDisplayTime()) ? View.VISIBLE : View.GONE);
        if (!centerVisible && canShow && PlayerSetting.isDisplayTraffic()) Traffic.setSpeed(binding.playerDisplayTraffic);
        else binding.playerDisplayTraffic.setVisibility(View.GONE);
        binding.playerDisplayBottomProgress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        binding.playerDisplayMini.setVisibility(showMini ? View.VISIBLE : View.GONE);
        if (!showProgress && !showMini) return;
        long duration = Math.max(0, player().getDuration());
        long position = Math.max(0, Math.min(player().getPosition(), duration));
        int progress = duration > 0 ? (int) (position * binding.playerDisplayBar.getMax() / duration) : 0;
        binding.playerDisplayPosition.setText(player().getPositionTime(0) + "/" + player().getDurationTime());
        binding.playerDisplayBar.setProgress(progress);
        binding.playerDisplayMini.setProgress(position, duration);
    }

    private void setButtonEnabled(View button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.36f);
    }

    private boolean canShowInlinePiP(boolean hasPlayer, boolean locked) {
        return hasPlayer && !locked && !inlineFullscreen && player().haveTrack(C.TRACK_TYPE_VIDEO) && !PiP.noPiP();
    }

    private void setInlineFullscreenIcon() {
        binding.playerFullscreenAction.setText(inlineFullscreen ? R.string.play_exit_fullscreen : R.string.play_fullscreen);
        binding.playerFullscreen.setImageResource(inlineFullscreen ? R.drawable.ic_control_fullscreen_exit : R.drawable.ic_control_fullscreen);
        if (Util.isMobile()) detailControlView(R.id.fullscreen, ImageView.class).setImageResource(inlineFullscreen ? R.drawable.ic_control_fullscreen_exit : R.drawable.ic_control_fullscreen);
    }

    private void setInlineDanmakuIcon(boolean show) {
        binding.playerDanmakuToggle.setImageResource(show ? R.drawable.ic_control_danmaku_on : R.drawable.ic_control_danmaku_off);
        if (Util.isMobile()) detailControlView(R.id.danmaku, ImageView.class).setImageResource(show ? R.drawable.ic_control_danmaku_on : R.drawable.ic_control_danmaku_off);
    }

    private CharSequence inlineTitleText() {
        return binding.playerTitle.getText();
    }

    protected boolean hasInlineCast() {
        return hasInlineMedia() && hasClass("com.fongmi.android.tv.ui.dialog.CastDialog") && hasClass("com.fongmi.android.tv.bean.CastVideo");
    }

    protected boolean hasInlineInfo() {
        return hasInlineMedia();
    }

    protected void onInlineCast() {
        if (!hasInlineCast()) return;
        try {
            Class<?> videoClass = Class.forName("com.fongmi.android.tv.bean.CastVideo");
            Object video = videoClass.getConstructor(String.class, String.class, long.class, Map.class).newInstance(inlineTitleText().toString(), player().getUrl(), player().getPosition(), player().getHeaders());
            Class<?> dialogClass = Class.forName("com.fongmi.android.tv.ui.dialog.CastDialog");
            Object dialog = dialogClass.getMethod("create").invoke(null);
            if (history != null) dialogClass.getMethod("history", History.class).invoke(dialog, history);
            dialog = dialogClass.getMethod("video", videoClass).invoke(dialog, video);
            dialog = dialogClass.getMethod("fm", boolean.class).invoke(dialog, true);
            dialogClass.getMethod("show", androidx.fragment.app.FragmentActivity.class).invoke(dialog, this);
        } catch (Throwable e) {
            Notify.show(TextUtils.isEmpty(e.getMessage()) ? getString(R.string.error_play_url) : e.getMessage());
        }
    }

    protected void onInlineInfo() {
        if (!hasInlineInfo()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(inlineTitleText())
                .setMessage(buildInlineInfoText())
                .setPositiveButton(R.string.dialog_positive, null)
                .show();
    }

    private boolean hasInlineMedia() {
        return service() != null && player() != null && !player().isEmpty() && !TextUtils.isEmpty(player().getUrl());
    }

    private boolean hasClass(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String buildInlineInfoText() {
        StringBuilder builder = new StringBuilder();
        appendInlineInfo(builder, "URL", trimDataUrl(player().getUrl()));
        appendInlineInfo(builder, "Headers", buildInlineHeaders(player().getHeaders()));
        appendInlineInfo(builder, "Params", player().getVideoParamsText());
        return builder.length() == 0 ? "-" : builder.toString();
    }

    private String trimDataUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        return url.startsWith("data") ? url.substring(0, Math.min(url.length(), 128)).concat("...") : url;
    }

    private String buildInlineHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        for (String key : headers.keySet()) builder.append(key).append(" : ").append(headers.get(key)).append("\n");
        return Util.substring(builder.toString());
    }

    private void appendInlineInfo(StringBuilder builder, String label, String value) {
        if (TextUtils.isEmpty(value)) return;
        if (builder.length() > 0) builder.append("\n\n");
        builder.append(label).append("\n").append(value);
    }

    protected boolean showInlinePlayerInfo() {
        return false;
    }

    protected CharSequence getInlinePlayerTitle() {
        return binding.playerTitle.getText();
    }

    protected History getInlineHistory() {
        return history;
    }

    private boolean hasAdjacentEpisode(int direction) {
        if (selectedFlag == null || selectedEpisode == null || selectedFlag.getEpisodes() == null) return false;
        int index = selectedFlag.getEpisodes().indexOf(selectedEpisode);
        int next = index + direction;
        return index >= 0 && next >= 0 && next < selectedFlag.getEpisodes().size();
    }

    private void updateInlineTitle() {
        if (!isInlinePlayerMode()) return;
        String title = playbackHistoryName();
        String episode = selectedEpisode != null ? inlineEpisodeTitle(selectedEpisode) : "";
        binding.playerTitle.setText(TextUtils.isEmpty(episode) || TextUtils.equals(episode, title) ? title : title + "  " + episode);
    }

    private String qualityLabel() {
        if (currentInlineResult == null || !currentInlineResult.getUrl().isMulti()) return getString(R.string.detail_quality);
        int position = currentInlineResult.getUrl().getPosition();
        String name = currentInlineResult.getUrl().n(position);
        return TextUtils.isEmpty(name) ? getString(R.string.detail_quality) + " " + (position + 1) : name;
    }

    private boolean canChangeInlineQuality() {
        return hasInlineUrlQuality() || isInlineVideoTrackAsQuality();
    }

    private boolean hasInlineUrlQuality() {
        return currentInlineResult != null && currentInlineResult.getUrl().isMulti();
    }

    private boolean isInlineVideoTrackAsQuality() {
        return !hasInlineUrlQuality() && service() != null && player() != null && !player().isEmpty() && player().haveTrack(C.TRACK_TYPE_VIDEO);
    }

    private String qualityLabel(int position) {
        if (currentInlineResult == null) return getString(R.string.detail_quality);
        String name = currentInlineResult.getUrl().n(position);
        return TextUtils.isEmpty(name) ? getString(R.string.detail_quality) + " " + (position + 1) : name;
    }

    private String parseLabel() {
        String name = VodConfig.get().getParse().getName();
        return TextUtils.isEmpty(name) ? getString(R.string.parse) : name;
    }

    private int getInlineScale() {
        return history != null && history.getScale() != -1 ? history.getScale() : PlayerSetting.getScale();
    }

    private String scaleLabel() {
        return scaleLabel(getInlineScale());
    }

    private String scaleLabel(int scale) {
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        int index = Math.max(0, Math.min(scale, array.length - 1));
        return array.length == 0 ? getString(R.string.play_scale) : array[index];
    }

    private void setInlineScaleText(CharSequence text) {
        binding.playerScale.setText(text);
        if (Util.isMobile() && detailActionRoot != null) detailActionView(R.id.scale, TextView.class).setText(text);
    }

    private void setInlineScale(int scale) {
        if (history != null) history.setScale(scale);
        binding.exo.setResizeMode(scale);
        setInlineScaleText(scaleLabel(scale));
    }

    private void setInlinePreviewScale(int scale) {
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        if (scale < 0 || scale >= array.length) return;
        binding.exo.setResizeMode(scale);
        setInlineScaleText(array[scale]);
    }

    private void showInlineQuality() {
        if (!canChangeInlineQuality()) return;
        if (!hasInlineUrlQuality()) {
            TrackDialog.create().type(C.TRACK_TYPE_VIDEO).player(player()).search(this::showSubtitleSearch).show(this);
            return;
        }
        int count = currentInlineResult.getUrl().getValues().size();
        String[] labels = new String[count];
        for (int i = 0; i < count; i++) labels[i] = qualityLabel(i);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.detail_quality)
                .setSingleChoiceItems(labels, currentInlineResult.getUrl().getPosition(), (dialog, which) -> {
                    dialog.dismiss();
                    changeInlineQuality(which);
                })
                .show();
    }

    private void changeInlineQuality(int position) {
        if (!canChangeInlineQuality() || currentInlineResult.getUrl().getPosition() == position) return;
        saveInlineHistory();
        currentInlineResult.getUrl().set(position);
        updateInlineButtons(service() != null && player() != null && !player().isEmpty() && player().isPlaying());
        startInlinePlayer(currentInlineResult);
    }

    private void cycleInlineParse() {
        List<Parse> parses = VodConfig.get().getParses();
        if (!useParse || parses.isEmpty()) return;
        Parse current = VodConfig.get().getParse();
        int index = parses.indexOf(current);
        Parse next = parses.get(index < 0 || index == parses.size() - 1 ? 0 : index + 1);
        changeInlineParse(next);
    }

    private void changeInlineParse(Parse next) {
        if (!useParse || next == null || next.isSelected()) return;
        VodConfig.get().setParse(next);
        if (inlineParseAdapter != null) inlineParseAdapter.notifyDataSetChanged();
        Notify.show(getString(R.string.play_switch_parse, next.getName()));
        playInline();
    }

    private final class InlineParseAdapter extends RecyclerView.Adapter<InlineParseAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView text = new TextView(parent.getContext(), null, 0, R.style.Control);
            text.setGravity(Gravity.CENTER);
            return new ViewHolder(text);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Parse item = VodConfig.get().getParses().get(position);
            holder.text.setText(item.getName());
            holder.text.setSelected(item.isSelected());
            holder.text.setOnClickListener(view -> changeInlineParse(item));
        }

        @Override
        public int getItemCount() {
            return VodConfig.get().getParses().size();
        }

        private final class ViewHolder extends RecyclerView.ViewHolder {

            private final TextView text;

            private ViewHolder(@NonNull TextView text) {
                super(text);
                this.text = text;
            }
        }
    }

    private void changeInlineSpeed() {
        if (service() == null || player().isEmpty()) return;
        setInlineSpeedText(player().addSpeed());
        if (history != null) history.setSpeed(player().getSpeed());
    }

    private void setInlineSpeed(float speed) {
        if (service() == null || player() == null || player().isReleased()) return;
        setInlineSpeedText(player().setSpeed(normalizeInlineSpeed(speed)));
    }

    private float normalizeInlineSpeed(float speed) {
        return speed >= 0.25f && speed <= 5.0f ? speed : 1.0f;
    }

    private void setInlineSpeedText(CharSequence text) {
        binding.playerSpeed.setText(text);
        if (Util.isMobile() && detailActionRoot != null) detailActionView(R.id.speed, TextView.class).setText(text);
    }

    private boolean resetInlineSpeed() {
        if (service() == null || player().isEmpty()) return false;
        setInlineSpeedText(player().toggleSpeed());
        if (history != null) history.setSpeed(player().getSpeed());
        return true;
    }

    private void refreshInlinePlayback() {
        if (selectedFlag == null || selectedEpisode == null) return;
        if (history != null) history.setPosition(C.TIME_UNSET);
        playInline();
    }

    private void cycleInlineScale() {
        if (service() == null || player().isEmpty()) return;
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        if (array.length == 0) return;
        int scale = getInlineScale();
        setInlineScale(scale >= array.length - 1 ? 0 : scale + 1);
    }

    private void toggleInlineDecode() {
        if (service() == null || player().isEmpty()) return;
        player().toggleDecode();
        setInlineDecodeText(inlineDecodeText(true));
    }

    private boolean showInlinePlayerChoice() {
        if (service() == null || player().isEmpty()) return false;
        String[] kernels = ResUtil.getStringArray(R.array.select_player_kernel);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.player_kernel).setItems(kernels, (dialog, which) -> switchInlinePlayer(which)).show();
        return true;
    }

    private void switchInlinePlayer(int playerType) {
        if (service() == null || player().isEmpty()) return;
        player().switchPlayerManually(playerType);
        updateInlineHistoryPlayer();
        syncInlineHistory();
        binding.playerExternal.setText(player().getPlayerText());
        setInlineDecodeText(inlineDecodeText(true));
        updateInlineButtons(false);
    }

    private void showInlineTrack(View view) {
        if (service() == null || player().isEmpty()) return;
        TrackDialog.create().type(Integer.parseInt(view.getTag().toString())).player(player()).search(this::showSubtitleSearch).show(this);
    }

    private boolean showInlineSubtitle() {
        if (service() == null || player().isEmpty() || !player().haveTrack(C.TRACK_TYPE_TEXT)) return false;
        onSubtitleClick();
        return true;
    }

    private void showInlineDanmaku() {
        if (service() == null || player().isEmpty()) return;
        DanmakuDialog.create().player(player()).show(this);
    }

    private void showInlineTitle() {
        if (service() == null || player().isEmpty() || !player().haveTitle()) return;
        TitleDialog.create().player(player()).show(this);
    }

    private void toggleInlineDanmaku() {
        if (inlineControlController == null) return;
        inlineControlController.onDanmakuButton();
    }

    private void toggleInlineRepeat() {
        if (service() == null || player().isEmpty()) return;
        player().setRepeatOne(!player().isRepeatOne());
        updateInlineButtons(player().isPlaying());
    }

    private void setInlineOpeningFromPosition() {
        if (history == null || service() == null || player().isEmpty()) return;
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetOpening(position, duration)) setInlineOpening(position);
        setInlineHideCallback();
    }

    private boolean resetInlineOpening() {
        setInlineOpening(0);
        setInlineHideCallback();
        return true;
    }

    private void setInlineOpening(long opening) {
        if (history == null) return;
        history.setOpening(opening);
        updateInlineOpeningEndingText();
        syncInlineHistory();
    }

    private void setInlineEndingFromPosition() {
        if (history == null || service() == null || player().isEmpty()) return;
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetEnding(position, duration)) setInlineEnding(duration - position);
        setInlineHideCallback();
    }

    private boolean resetInlineEnding() {
        setInlineEnding(0);
        setInlineHideCallback();
        return true;
    }

    private void setInlineEnding(long ending) {
        if (history == null) return;
        history.setEnding(ending);
        updateInlineOpeningEndingText();
        syncInlineHistory();
    }

    private void updateInlineOpeningEndingText() {
        binding.playerOpening.setText(inlineOpeningLabel());
        binding.playerEnding.setText(inlineEndingLabel());
        if (!Util.isMobile() || detailActionRoot == null) return;
        detailActionView(R.id.opening, TextView.class).setText(binding.playerOpening.getText());
        detailActionView(R.id.ending, TextView.class).setText(binding.playerEnding.getText());
    }

    private String inlineOpeningLabel() {
        return history != null && history.getOpening() > 0 ? Util.timeMs(history.getOpening()) : getString(R.string.play_op);
    }

    private String inlineEndingLabel() {
        return history != null && history.getEnding() > 0 ? Util.timeMs(history.getEnding()) : getString(R.string.play_ed);
    }

    private void onInlineBack() {
        if (inlineFullscreen) backFromInlineFullscreen();
        else {
            prepareInlinePlayerTransition();
            finish();
        }
    }

    private void backFromInlineFullscreen() {
        exitInlineFullscreen();
    }

    private void finishPlaybackToHome() {
        prepareInlinePlayerTransition();
        saveInlineHistory();
        startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    private void openInlineExternal() {
        if (service() == null || player().isEmpty()) return;
        PlayerHelper.choose(this, player().getUrl(), player().getHeaders(), player().isVod(), player().getPosition(), binding.playerTitle.getText());
        setRedirect(true);
    }

    private void showInlineEpisodes() {
        if (selectedFlag == null || selectedFlag.getEpisodes() == null || selectedFlag.getEpisodes().isEmpty()) return;
        if (!Util.isMobile() || Setting.isStandaloneTmdbDetailMode(getDetailMode())) {
            showNativeEnhancedInlineEpisodes();
            return;
        }
        ThemeColors colors = lightTheme ? ThemeColors.light() : ThemeColors.dark();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(colors.panel);
        background.setCornerRadius(ResUtil.dp2px(22));
        background.setStroke(ResUtil.dp2px(1), colors.line);
        content.setBackground(background);
        content.setPadding(ResUtil.dp2px(12), ResUtil.dp2px(8), ResUtil.dp2px(12), ResUtil.dp2px(8));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = new TextView(this);
        title.setText(R.string.detail_episode);
        title.setTextColor(colors.primary);
        title.setTextSize(18f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialButton mode = createInlineEpisodeModeButton();
        header.addView(mode, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(36)));
        content.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        HorizontalScrollView pageScroll = new HorizontalScrollView(this);
        pageScroll.setHorizontalScrollBarEnabled(false);
        pageScroll.setVisibility(View.GONE);
        LinearLayout pageRow = new LinearLayout(this);
        pageRow.setOrientation(LinearLayout.HORIZONTAL);
        pageScroll.addView(pageRow, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams pageParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(48));
        pageParams.topMargin = ResUtil.dp2px(6);
        content.addView(pageScroll, pageParams);
        RecyclerView recycler = new RecyclerView(this);
        recycler.setClipToPadding(false);
        updateInlineEpisodeLayoutManager(recycler);
        int height = Math.min(ResUtil.dp2px(620), (int) (ResUtil.getScreenHeight(this) * 0.78f));
        LinearLayout.LayoutParams recyclerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        recyclerParams.topMargin = ResUtil.dp2px(8);
        content.addView(recycler, recyclerParams);

        AlertDialog[] holder = new AlertDialog[1];
        InlineEpisodeAdapter adapter = new InlineEpisodeAdapter(new InlineEpisodeAdapter.Listener() {
            @Override
            public void onItemClick(Episode episode) {
                if (holder[0] != null) holder[0].dismiss();
                selectInlineEpisode(episode);
            }

            @Override
            public boolean onItemLongClick(MaterialButton button, Episode episode) {
                Notify.show(inlineEpisodeTitle(episode));
                return true;
            }
        });
        adapter.setLight(lightTheme);
        recycler.setAdapter(adapter);
        final int[] pageIndex = {0};
        final boolean[] manualPage = {false};
        final List<TextView> pageButtons = new ArrayList<>();
        final Runnable[] render = new Runnable[1];
        final java.util.function.BiConsumer<Integer, Boolean> showPage = (index, focusEpisode) -> {
            List<Episode> ordered = orderedInlineEpisodes();
            List<EpisodeRangePolicy.Range> ranges = EpisodeRangePolicy.build(ordered.size(), ordered.indexOf(selectedEpisode), episodeReverse);
            if (ranges.isEmpty()) {
                adapter.setItems(List.of(), selectedEpisode, inlineEpisodeTitles(selectedFlag.getEpisodes()));
                return;
            }
            pageIndex[0] = Math.max(0, Math.min(index, ranges.size() - 1));
            List<Episode> pageItems = ranges.size() > 1 ? EpisodeRangePolicy.slice(ordered, ranges.get(pageIndex[0])) : ordered;
            adapter.setItems(pageItems, selectedEpisode, inlineEpisodeTitles(selectedFlag.getEpisodes()));
            for (int i = 0; i < pageButtons.size(); i++) applyEpisodeDialogPageState(pageButtons.get(i), i == pageIndex[0], pageButtons.get(i).hasFocus());
            if (!focusEpisode) return;
            int selected = pageItems.indexOf(selectedEpisode);
            if (selected < 0) selected = 0;
            int focus = selected;
            recycler.post(() -> {
                recycler.scrollToPosition(focus);
                recycler.post(() -> {
                    RecyclerView.ViewHolder viewHolder = recycler.findViewHolderForAdapterPosition(focus);
                    if (viewHolder != null) viewHolder.itemView.requestFocus();
                });
            });
        };
        render[0] = () -> {
            List<Episode> ordered = orderedInlineEpisodes();
            pageRow.removeAllViews();
            pageButtons.clear();
            List<EpisodeRangePolicy.Range> ranges = EpisodeRangePolicy.build(ordered.size(), ordered.indexOf(selectedEpisode), episodeReverse);
            pageScroll.setVisibility(ranges.size() > 1 ? View.VISIBLE : View.GONE);
            if (ranges.isEmpty()) return;
            if (!manualPage[0]) pageIndex[0] = EpisodeRangePolicy.selectedPosition(ranges);
            pageIndex[0] = Math.max(0, Math.min(pageIndex[0], ranges.size() - 1));
            for (int i = 0; i < ranges.size(); i++) {
                TextView button = createEpisodeDialogPageButton(ranges.get(i).label(), i == pageIndex[0]);
                int page = i;
                button.setOnClickListener(view -> {
                    manualPage[0] = true;
                    showPage.accept(page, true);
                });
                button.setOnFocusChangeListener((view, focused) -> {
                    applyEpisodeDialogPageState(button, page == pageIndex[0], focused);
                    if (focused && page != pageIndex[0]) {
                        manualPage[0] = true;
                        showPage.accept(page, false);
                    }
                });
                button.setOnKeyListener((view, keyCode, event) -> moveEpisodeDialogPageFocus(pageButtons, pageScroll, page, keyCode, event, target -> {
                    manualPage[0] = true;
                    showPage.accept(target, false);
                }));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ResUtil.dp2px(136), ResUtil.dp2px(34));
                params.setMargins(0, ResUtil.dp2px(5), ResUtil.dp2px(12), ResUtil.dp2px(5));
                pageRow.addView(button, params);
                pageButtons.add(button);
            }
            showPage.accept(pageIndex[0], true);
        };
        mode.setOnClickListener(view -> {
            inlineEpisodeGridMode = !inlineEpisodeGridMode;
            updateInlineEpisodeLayoutManager(recycler);
            updateInlineEpisodeModeButton(mode);
            showPage.accept(pageIndex[0], true);
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(content)
                .create();
        holder[0] = dialog;
        dialog.setOnShowListener(value -> render[0].run());
        if (!canTouchUi()) return;
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int width = ResUtil.getScreenWidth(this);
            window.setLayout(Math.min(ResUtil.dp2px(720), (int) (width * 0.92f)), WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showNativeEnhancedInlineEpisodes() {
        NativeEnhancedInlineEpisodeLayout layout = nativeEnhancedInlineEpisodeLayout();
        NestedScrollView scroll = new NestedScrollView(this);
        scroll.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.setPadding(layout.paddingHorizontal(), layout.paddingTop(), layout.paddingHorizontal(), layout.paddingBottom());
        GradientDrawable background = new GradientDrawable();
        background.setColor(0x66111820);
        background.setCornerRadius(0);
        scroll.setBackground(background);
        scroll.addView(panel, new NestedScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView flagTitle = createNativeEnhancedInlineSectionTitle(R.string.detail_flag);
        panel.addView(flagTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        HorizontalScrollView flagScroll = new HorizontalScrollView(this);
        flagScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout flagRow = new LinearLayout(this);
        flagRow.setOrientation(LinearLayout.HORIZONTAL);
        flagScroll.addView(flagRow, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams flagParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flagParams.topMargin = ResUtil.dp2px(12);
        panel.addView(flagScroll, flagParams);

        TextView episodeTitle = createNativeEnhancedInlineSectionTitle(R.string.detail_episode);
        LinearLayout.LayoutParams episodeTitleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        episodeTitleParams.topMargin = ResUtil.dp2px(22);
        panel.addView(episodeTitle, episodeTitleParams);

        HorizontalScrollView pageScroll = new HorizontalScrollView(this);
        pageScroll.setHorizontalScrollBarEnabled(false);
        pageScroll.setVisibility(View.GONE);
        LinearLayout pageRow = new LinearLayout(this);
        pageRow.setOrientation(LinearLayout.HORIZONTAL);
        pageScroll.addView(pageRow, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams pageParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pageParams.topMargin = ResUtil.dp2px(12);
        panel.addView(pageScroll, pageParams);

        RecyclerView recycler = new RecyclerView(this);
        recycler.setClipToPadding(false);
        recycler.setNestedScrollingEnabled(false);
        updateNativeEnhancedInlineEpisodeLayoutManager(recycler, layout.spanCount());
        TmdbEpisodeAdapter adapter = new TmdbEpisodeAdapter(new TmdbEpisodeAdapter.Listener() {
            @Override
            public void onItemClick(Episode episode) {
                Dialog dialog = (Dialog) panel.getTag();
                if (dialog != null) dialog.dismiss();
                selectInlineEpisode(episode);
            }

            @Override
            public void onItemLongClick(View anchor, Episode episode, int episodeNumber) {
                if (EpisodeTitlePopup.show(anchor, inlineEpisodeTitle(episode))) return;
                showTmdbEpisodeDetail(episode, episodeNumber);
            }
        });
        adapter.setLight(lightTheme);
        adapter.setActiveStrokeColor(0xFF2AA46B);
        adapter.setNativeEnhanced(true);
        recycler.setAdapter(adapter);
        LinearLayout.LayoutParams recyclerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        recyclerParams.topMargin = ResUtil.dp2px(10);
        panel.addView(recycler, recyclerParams);

        final int[] pageIndex = {0};
        final List<TextView> flagButtons = new ArrayList<>();
        final List<TextView> pageButtons = new ArrayList<>();
        final Runnable[] render = new Runnable[1];
        View.OnKeyListener flagKeyListener = (view, keyCode, event) -> {
            if (!KeyUtil.isDownKey(event)) return false;
            if (!KeyUtil.isActionDown(event)) return true;
            if (focusNativeEnhancedInlinePageButton(pageButtons, pageScroll, pageIndex[0])) return true;
            return focusNativeEnhancedInlineEpisode(scroll, recycler, adapter, layout.spanCount());
        };
        adapter.setOnKeyListener((view, keyCode, event) -> {
            if (!KeyUtil.isUpKey(event) && !KeyUtil.isDownKey(event)) return false;
            if (!KeyUtil.isActionDown(event)) return true;
            int position = recycler.getChildAdapterPosition(view);
            if (position == RecyclerView.NO_POSITION) return true;
            if (KeyUtil.isUpKey(event)) {
                if (position < layout.spanCount()) {
                    if (focusNativeEnhancedInlinePageButton(pageButtons, pageScroll, pageIndex[0])) return true;
                    return focusNativeEnhancedInlineFlagButton(flagButtons, flagScroll, selectedFlag);
                }
                return focusNativeEnhancedInlineEpisode(scroll, recycler, adapter, position - layout.spanCount(), layout.spanCount());
            }
            int target = position + layout.spanCount();
            if (target >= adapter.getItemCount()) return true;
            return focusNativeEnhancedInlineEpisode(scroll, recycler, adapter, target, layout.spanCount());
        });
        final java.util.function.BiConsumer<Integer, Boolean> showPage = (index, focusEpisode) -> {
            List<Episode> ordered = orderedInlineEpisodes();
            List<EpisodeRangePolicy.Range> ranges = EpisodeRangePolicy.build(ordered.size(), ordered.indexOf(selectedEpisode), episodeReverse);
            if (ranges.isEmpty()) {
                adapter.setItems(List.of(), tmdbEpisodes, Map.of(), selectedEpisode);
                return;
            }
            pageIndex[0] = Math.max(0, Math.min(index, ranges.size() - 1));
            List<Episode> pageItems = EpisodeRangePolicy.slice(ordered, ranges.get(pageIndex[0]));
            adapter.setMode(TmdbEpisodeAdapter.Mode.GRID);
            adapter.setGridSpanCount(layout.spanCount());
            adapter.setFallbackStillUrl(episodeFallbackStillUrl());
            adapter.setItems(pageItems, tmdbEpisodes, episodeNumbers(pageItems, selectedFlag.getEpisodes()), selectedEpisode);
            for (int i = 0; i < pageButtons.size(); i++) {
                applyNativeEnhancedInlineChipState(pageButtons.get(i), i == pageIndex[0], pageButtons.get(i).hasFocus());
            }
            if (!focusEpisode) return;
            int selected = pageItems.indexOf(selectedEpisode);
            if (selected < 0) selected = 0;
            focusNativeEnhancedInlineEpisode(scroll, recycler, adapter, selected, layout.spanCount());
        };
        Runnable renderFlags = () -> {
            flagRow.removeAllViews();
            flagButtons.clear();
            List<Flag> flags = vod == null ? List.of() : vod.getFlags();
            for (Flag flag : flags) {
                TextView button = createNativeEnhancedInlineChipButton(flag.getShow());
                applyNativeEnhancedInlineChipState(button, flag.equals(selectedFlag), false);
                button.setOnClickListener(view -> switchNativeEnhancedInlineFlag(flag, render));
                button.setOnFocusChangeListener((view, focused) -> applyNativeEnhancedInlineChipState(button, flag.equals(selectedFlag), focused));
                button.setOnKeyListener(flagKeyListener);
                flagRow.addView(button);
                flagButtons.add(button);
            }
        };
        render[0] = () -> {
            renderFlags.run();
            List<Episode> ordered = orderedInlineEpisodes();
            pageRow.removeAllViews();
            pageButtons.clear();
            List<EpisodeRangePolicy.Range> ranges = EpisodeRangePolicy.build(ordered.size(), ordered.indexOf(selectedEpisode), episodeReverse);
            pageScroll.setVisibility(ranges.size() > 1 ? View.VISIBLE : View.GONE);
            if (ranges.isEmpty()) return;
            int selectedPage = EpisodeRangePolicy.selectedPosition(ranges);
            for (int i = 0; i < ranges.size(); i++) {
                TextView button = createNativeEnhancedInlineChipButton(ranges.get(i).label());
                applyNativeEnhancedInlineChipState(button, i == selectedPage, false);
                int page = i;
                button.setOnClickListener(view -> {
                    showPage.accept(page, false);
                    focusNativeEnhancedInlinePageButton(pageButtons, pageScroll, page);
                });
                button.setOnFocusChangeListener((view, focused) -> {
                    applyNativeEnhancedInlineChipState(button, page == pageIndex[0], focused);
                    if (focused && page != pageIndex[0]) showPage.accept(page, false);
                });
                button.setOnKeyListener((view, keyCode, event) -> {
                    if (KeyUtil.isDownKey(event)) {
                        if (!KeyUtil.isActionDown(event)) return true;
                        return focusNativeEnhancedInlineEpisode(scroll, recycler, adapter, layout.spanCount());
                    }
                    if (KeyUtil.isUpKey(event)) {
                        if (!KeyUtil.isActionDown(event)) return true;
                        return focusNativeEnhancedInlineFlagButton(flagButtons, flagScroll, selectedFlag);
                    }
                    return moveEpisodeDialogPageFocus(pageButtons, pageScroll, page, keyCode, event, target -> showPage.accept(target, false));
                });
                pageRow.addView(button);
                pageButtons.add(button);
            }
            showPage.accept(selectedPage, true);
        };

        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setView(scroll).create();
        panel.setTag(dialog);
        dialog.setOnShowListener(value -> render[0].run());
        if (!canTouchUi()) return;
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.getDecorView().setPadding(0, 0, 0, 0);
            window.setDimAmount(0.08f);
            window.setGravity(layout.gravity());
            window.setLayout(layout.windowWidth(), WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    private TextView createNativeEnhancedInlineSectionTitle(int resId) {
        TextView title = new TextView(this);
        title.setText(resId);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setIncludeFontPadding(false);
        return title;
    }

    private TextView createNativeEnhancedInlineChipButton(String text) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setTextSize(14f);
        button.setFocusable(true);
        button.setFocusableInTouchMode(false);
        button.setClickable(true);
        button.setMinWidth(ResUtil.dp2px(64));
        button.setBackgroundResource(R.drawable.shape_video_item);
        ColorStateList textColors = ContextCompat.getColorStateList(this, R.color.selector_video_text);
        if (textColors != null) button.setTextColor(textColors);
        button.setPadding(ResUtil.dp2px(12), ResUtil.dp2px(8), ResUtil.dp2px(12), ResUtil.dp2px(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(34));
        params.setMargins(0, 0, ResUtil.dp2px(8), 0);
        button.setLayoutParams(params);
        return button;
    }

    private void applyNativeEnhancedInlineChipState(TextView button, boolean selected, boolean focused) {
        button.setSelected(selected);
        button.setActivated(selected);
        button.refreshDrawableState();
    }

    private void switchNativeEnhancedInlineFlag(Flag flag, Runnable[] render) {
        if (flag == null || flag.equals(selectedFlag)) return;
        cancelPendingInlinePlayback();
        selectedFlag = flag;
        selectedEpisode = null;
        selectedSeasonNumber = -1;
        resetEpisodeRange();
        if (selectedFlag.getEpisodes() != null && !selectedFlag.getEpisodes().isEmpty()) selectedEpisode = selectedFlag.find(history == null ? "" : history.getVodRemarks(), getMarkText().isEmpty());
        if (selectedEpisode == null && selectedFlag.getEpisodes() != null && !selectedFlag.getEpisodes().isEmpty()) selectedEpisode = selectedFlag.getEpisodes().get(0);
        if (selectedEpisode != null) selectedSeasonNumber = seasonForEpisode(selectedEpisode, selectedFlag.getEpisodes());
        if (render != null && render[0] != null) render[0].run();
    }

    private boolean focusNativeEnhancedInlineFlagButton(List<TextView> buttons, HorizontalScrollView scroll, Flag flag) {
        if (buttons.isEmpty()) return false;
        int target = 0;
        List<Flag> flags = vod == null ? List.of() : vod.getFlags();
        for (int i = 0; i < flags.size() && i < buttons.size(); i++) {
            if (flags.get(i).equals(flag)) {
                target = i;
                break;
            }
        }
        TextView button = buttons.get(target);
        scroll.post(() -> {
            if (button.getParent() == null) return;
            button.requestFocus();
            scroll.smoothScrollTo(Math.max(0, button.getLeft() - ResUtil.dp2px(12)), 0);
        });
        return true;
    }

    private boolean focusNativeEnhancedInlinePageButton(List<TextView> buttons, HorizontalScrollView scroll, int position) {
        if (buttons.isEmpty() || scroll.getVisibility() != View.VISIBLE) return false;
        int target = Math.max(0, Math.min(position, buttons.size() - 1));
        TextView button = buttons.get(target);
        scroll.post(() -> {
            if (button.getParent() == null) return;
            button.requestFocus();
            scroll.smoothScrollTo(Math.max(0, button.getLeft() - ResUtil.dp2px(12)), 0);
        });
        return true;
    }

    private boolean focusNativeEnhancedInlineEpisode(NestedScrollView scroll, RecyclerView recycler, TmdbEpisodeAdapter adapter, int spanCount) {
        if (adapter.getItemCount() == 0) return false;
        int focus = Math.max(0, adapter.getPosition(selectedEpisode));
        return focusNativeEnhancedInlineEpisode(scroll, recycler, adapter, focus, spanCount);
    }

    private boolean focusNativeEnhancedInlineEpisode(NestedScrollView scroll, RecyclerView recycler, TmdbEpisodeAdapter adapter, int position, int spanCount) {
        if (adapter.getItemCount() == 0) return false;
        int focus = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        recycler.post(() -> {
            recycler.scrollToPosition(focus);
            recycler.post(() -> {
                RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(focus);
                if (holder != null) {
                    holder.itemView.requestFocus();
                    scroll.post(() -> alignNativeEnhancedInlineEpisodeRow(scroll, recycler, focus, spanCount));
                } else {
                    recycler.requestFocus();
                }
            });
        });
        return true;
    }

    private void alignNativeEnhancedInlineEpisodeRow(NestedScrollView scroll, RecyclerView recycler, int position, int spanCount) {
        int span = Math.max(1, spanCount);
        int rowStart = Math.max(0, position - position % span);
        if (rowStart == 0) {
            scroll.scrollTo(0, 0);
            return;
        }
        RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(rowStart);
        if (holder == null) return;
        int targetY = recycler.getTop() + holder.itemView.getTop() - ResUtil.dp2px(8);
        scroll.scrollTo(0, Math.max(0, targetY));
    }

    private boolean moveEpisodeDialogPageFocus(List<TextView> buttons, HorizontalScrollView scroll, int position, int keyCode, KeyEvent event, java.util.function.IntConsumer showPage) {
        if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        int target = position + (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
        if (target < 0 || target >= buttons.size()) return true;
        TextView button = buttons.get(target);
        showPage.accept(target);
        button.requestFocus();
        scroll.post(() -> scroll.smoothScrollTo(Math.max(0, button.getLeft() - ResUtil.dp2px(12)), 0));
        return true;
    }

    private Map<Episode, String> inlineEpisodeTitles(List<Episode> episodes) {
        Map<Episode, String> titles = new HashMap<>();
        if (episodes == null) return titles;
        for (Episode episode : episodes) titles.put(episode, inlineEpisodeTitle(episode, episodes));
        return titles;
    }

    private String inlineEpisodeTitle(Episode episode) {
        return inlineEpisodeTitle(episode, selectedFlag == null ? null : selectedFlag.getEpisodes());
    }

    private String inlineEpisodeTitle(Episode episode, List<Episode> episodes) {
        EpisodePosition position = episodePosition(episode, episodes);
        String title = tmdbEpisodeTitle(position.season(), position.number());
        if (TextUtils.isEmpty(title)) return EpisodeAdapter.getTitle(episode);
        return TmdbEpisodeAdapter.getTitle(episode, position.number(), title);
    }

    private EpisodePosition episodePosition(Episode episode, List<Episode> episodes) {
        int index = episode == null || episodes == null ? -1 : episodes.indexOf(episode);
        int sourceNumber = sourceEpisodeNumber(episode);
        if (usesSingleTmdbSeasonEpisodeData(episodes)) return new EpisodePosition(firstSeasonNumber(matchedTmdbDetail), linearEpisodeNumber(sourceNumber, index));
        int sourceSeason = sourceSeasonNumber(episode);
        if (useSourceEpisodeNumber(sourceNumber, sourceSeason)) {
            int season = seasonNumbers.contains(sourceSeason) ? sourceSeason : selectedSeasonNumber;
            if (season < 0 && seasonNumbers.size() == 1) season = seasonNumbers.get(0);
            return new EpisodePosition(season, seasonNumbers.size() <= 1 ? linearEpisodeNumber(sourceNumber, index) : sourceNumber);
        }
        if (index < 0) return new EpisodePosition(selectedSeasonNumber, -1);
        if (seasonNumbers.size() <= 1 || selectedSeasonNumber < 0) return new EpisodePosition(selectedSeasonNumber, linearEpisodeNumber(sourceNumber, index));
        int titleSeason = sourceTitleSeasonNumber();
        if (!hasExplicitSeasonNumbers(episodes) && seasonNumbers.contains(titleSeason)) return new EpisodePosition(titleSeason, index + 1);
        if (!EpisodeSeasonPolicy.canSliceBySeasonCounts(episodes.size(), seasonNumbers, seasonEpisodeCounts)) return new EpisodePosition(selectedSeasonNumber, index + 1);
        int start = 0;
        for (int i = 0; i < seasonNumbers.size(); i++) {
            Integer season = seasonNumbers.get(i);
            int count = Math.max(0, seasonEpisodeCounts.getOrDefault(season, 0));
            if (count <= 0) continue;
            int end = i == seasonNumbers.size() - 1 ? episodes.size() : Math.min(episodes.size(), start + count);
            if (index >= start && index < end) return new EpisodePosition(season, index - start + 1);
            start += count;
        }
        return new EpisodePosition(selectedSeasonNumber, index + 1);
    }

    private boolean useSourceEpisodeNumber(int sourceNumber, int sourceSeason) {
        if (sourceNumber <= 0) return false;
        if (seasonNumbers.size() <= 1) return true;
        if (seasonNumbers.contains(sourceSeason)) return true;
        if (selectedSeasonNumber < 0) return true;
        int count = seasonEpisodeCounts.getOrDefault(selectedSeasonNumber, 0);
        return count <= 0 || sourceNumber <= count;
    }

    private int linearEpisodeNumber(int sourceNumber, int index) {
        return EpisodeSeasonPolicy.linearEpisodeNumber(sourceNumber, index);
    }

    private List<Episode> orderedInlineEpisodes() {
        List<Episode> ordered = new ArrayList<>(selectedFlag == null || selectedFlag.getEpisodes() == null ? List.of() : selectedFlag.getEpisodes());
        if (episodeReverse) Collections.reverse(ordered);
        return ordered;
    }

    private TextView createEpisodeDialogPageButton(String text, boolean selected) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextSize(15f);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setGravity(android.view.Gravity.CENTER);
        button.setFocusable(true);
        button.setFocusableInTouchMode(false);
        button.setClickable(true);
        button.setMinWidth(ResUtil.dp2px(136));
        button.setPadding(ResUtil.dp2px(18), 0, ResUtil.dp2px(18), 0);
        applyEpisodeDialogPageState(button, selected, false);
        return button;
    }

    private void applyEpisodeDialogPageState(TextView button, boolean selected, boolean focused) {
        ThemeColors colors = lightTheme ? ThemeColors.light() : ThemeColors.dark();
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(ResUtil.dp2px(6));
        if (focused) {
            background.setColor(lightTheme ? 0x1A2196F3 : 0x552196F3);
            background.setStroke(ResUtil.dp2px(3), 0xFF0077FF);
            button.setTextColor(lightTheme ? colors.primary : 0xFFFFFFFF);
        } else if (selected) {
            background.setColor(lightTheme ? 0x1F20B866 : 0x332196F3);
            background.setStroke(ResUtil.dp2px(2), lightTheme ? colors.accent : 0xFF2196F3);
            button.setTextColor(lightTheme ? colors.accent : 0xFF85C7FF);
        } else {
            background.setColor(0x00000000);
            button.setTextColor(lightTheme ? colors.secondary : 0xFFC6D0D9);
        }
        button.setSelected(selected);
        button.setBackground(background);
    }

    private void applyEpisodeDialogIconState(ImageView icon, boolean focused) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(ResUtil.dp2px(6));
        if (focused) {
            background.setColor(0x552196F3);
            background.setStroke(ResUtil.dp2px(3), 0xFF0077FF);
        } else {
            background.setColor(0x00000000);
        }
        icon.setBackground(background);
    }

    private MaterialButton createInlineEpisodeModeButton() {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setMinWidth(ResUtil.dp2px(72));
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setTextSize(13f);
        button.setGravity(Gravity.CENTER);
        button.setPadding(ResUtil.dp2px(10), 0, ResUtil.dp2px(12), 0);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setIconPadding(ResUtil.dp2px(4));
        button.setIconSize(ResUtil.dp2px(16));
        button.setFocusable(true);
        button.setFocusableInTouchMode(false);
        button.setClickable(true);
        updateInlineEpisodeModeButton(button);
        button.setOnFocusChangeListener((view, focused) -> applyInlineEpisodeModeButtonState(button, focused));
        return button;
    }

    private void updateInlineEpisodeModeButton(MaterialButton button) {
        boolean switchToList = inlineEpisodeGridMode;
        button.setText(switchToList ? R.string.detail_episode_view_list : R.string.detail_episode_view_grid);
        button.setIconResource(switchToList ? R.drawable.ic_site_list : R.drawable.ic_site_grid);
        button.setContentDescription(getString(switchToList ? R.string.detail_episode_view_list_action : R.string.detail_episode_view_grid_action));
        applyInlineEpisodeModeButtonState(button, button.hasFocus());
    }

    private void applyInlineEpisodeModeButtonState(MaterialButton button, boolean focused) {
        ThemeColors colors = lightTheme ? ThemeColors.light() : ThemeColors.dark();
        int text = focused ? (lightTheme ? colors.primary : 0xFFFFFFFF) : colors.primary;
        button.setTextColor(text);
        button.setIconTint(ColorStateList.valueOf(text));
        button.setBackgroundTintList(ColorStateList.valueOf(focused ? (lightTheme ? 0x1A2196F3 : 0x552196F3) : colors.control));
        button.setStrokeColor(ColorStateList.valueOf(focused ? 0xFF0077FF : colors.lineStrong));
        button.setStrokeWidth(ResUtil.dp2px(focused ? 2 : 1));
    }

    private void updateInlineEpisodeModeIcon(ImageView icon) {
        boolean switchToList = inlineEpisodeGridMode;
        icon.setImageResource(switchToList ? R.drawable.ic_site_list : R.drawable.ic_site_grid);
        icon.setContentDescription(getString(switchToList ? R.string.detail_episode_view_list_action : R.string.detail_episode_view_grid_action));
        icon.setColorFilter(0xFFEAF2F8);
    }

    private void updateInlineEpisodeLayoutManager(RecyclerView recycler) {
        RecyclerView.LayoutManager current = recycler.getLayoutManager();
        if (inlineEpisodeGridMode) {
            int spanCount = inlineEpisodeSpanCount();
            if (current instanceof GridLayoutManager grid && grid.getSpanCount() == spanCount) return;
            recycler.setLayoutManager(new GridLayoutManager(this, spanCount));
        } else {
            if (current instanceof LinearLayoutManager linear && !(current instanceof GridLayoutManager) && linear.getOrientation() == LinearLayoutManager.VERTICAL) return;
            recycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        }
    }

    private void updateNativeEnhancedInlineEpisodeLayoutManager(RecyclerView recycler, int spanCount) {
        RecyclerView.LayoutManager current = recycler.getLayoutManager();
        if (current instanceof GridLayoutManager grid && grid.getSpanCount() == spanCount) return;
        recycler.setLayoutManager(new GridLayoutManager(this, spanCount));
    }

    private NativeEnhancedInlineEpisodeLayout nativeEnhancedInlineEpisodeLayout() {
        int span = nativeEnhancedInlineEpisodeSpanCount();
        if (!Util.isMobile()) {
            return new NativeEnhancedInlineEpisodeLayout(span, 48, 34, 26, Gravity.CENTER, WindowManager.LayoutParams.MATCH_PARENT);
        }
        return new NativeEnhancedInlineEpisodeLayout(span, 36, 28, 24, Gravity.CENTER, WindowManager.LayoutParams.MATCH_PARENT);
    }

    private int nativeEnhancedInlineEpisodeSpanCount() {
        return TmdbEpisodeGridPolicy.nativeEnhancedSpanCount(Util.isMobile(), ResUtil.isPad(), ResUtil.isLand(this), getResources().getConfiguration().screenWidthDp);
    }

    private static class NativeEnhancedInlineEpisodeLayout {
        private final int spanCount;
        private final int paddingHorizontalDp;
        private final int paddingTopDp;
        private final int paddingBottomDp;
        private final int gravity;
        private final int windowWidth;

        private NativeEnhancedInlineEpisodeLayout(int spanCount, int paddingHorizontalDp, int paddingTopDp, int paddingBottomDp, int gravity, int windowWidth) {
            this.spanCount = spanCount;
            this.paddingHorizontalDp = paddingHorizontalDp;
            this.paddingTopDp = paddingTopDp;
            this.paddingBottomDp = paddingBottomDp;
            this.gravity = gravity;
            this.windowWidth = windowWidth;
        }

        private int spanCount() {
            return spanCount;
        }

        private int paddingHorizontal() {
            return ResUtil.dp2px(paddingHorizontalDp);
        }

        private int paddingTop() {
            return ResUtil.dp2px(paddingTopDp);
        }

        private int paddingBottom() {
            return ResUtil.dp2px(paddingBottomDp);
        }

        private int gravity() {
            return gravity;
        }

        private int windowWidth() {
            return windowWidth;
        }
    }

    private int inlineEpisodeSpanCount() {
        int width = ResUtil.getScreenWidth(this);
        if (width >= ResUtil.dp2px(1200)) return 5;
        if (width >= ResUtil.dp2px(720)) return 4;
        return 3;
    }

    private void selectInlineEpisode(Episode episode) {
        cancelPendingInlinePlayback();
        selectedEpisode = episode;
        selectedSeasonNumber = seasonForEpisode(episode, selectedFlag.getEpisodes());
        resetEpisodeRange();
        renderSeasonSelection();
        fetchSeasonIfNeeded(selectedSeasonNumber);
        renderEpisodes();
        updatePlayLabel();
        onPlay();
    }

    private void toggleInlineFullscreen() {
        if (service() == null || player().isEmpty()) return;
        if (inlineFullscreen) exitInlineFullscreen();
        else enterInlineFullscreen();
    }

    private void enterInlinePiP(boolean force) {
        if (!canEnterInlinePiP()) return;
        if (!force && !PlayerSetting.isBackgroundPiP()) return;
        hideInlineControls();
        hideInlineGestureOverlays();
        if (inlinePiP != null) inlinePiP.update(this, binding.playerPanel);
        inlinePiPLayoutRequested = !inlineFullscreen;
        boolean entered = inlinePiP != null && inlinePiP.enter(this, player().getVideoWidth(), player().getVideoHeight(), getInlineScale(), force);
        if (!entered) inlinePiPLayoutRequested = false;
    }

    private boolean canEnterInlinePiP() {
        return isInlinePlayerMode() && inlineStarted && service() != null && player() != null && !player().isEmpty() && player().haveTrack(C.TRACK_TYPE_VIDEO) && !PiP.noPiP();
    }

    private void toggleInlineLock() {
        if (!Util.isMobile() || !inlineFullscreen) return;
        setLock(!isLock());
        if (inlineGestureDetector != null) inlineGestureDetector.setLock(isLock());
        if (isLock()) {
            setRequestedOrientation(ResUtil.getScreenOrientation(this));
            hideInlineGestureOverlays();
        } else {
            setInlineFullscreenOrientation();
        }
        showInlineControls(true, false);
    }

    private void rotateInlineFullscreen() {
        if (!Util.isMobile() || !inlineFullscreen || isLock()) return;
        touchInlineControls();
        setRequestedOrientation(ResUtil.isLand(this) ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        scheduleMobileInlineSideControlMarginUpdate();
    }

    private void enterInlineFullscreen() {
        if (inlineFullscreen || inlinePiPLayout || isInPictureInPictureMode()) return;
        inlineFullscreen = true;
        updateDetailThemeButtonVisibility();
        requestedOrientation = getRequestedOrientation();
        playerParent = (ViewGroup) binding.playerPanel.getParent();
        playerLayoutParams = copyInlinePlayerLayoutParams(binding.playerPanel.getLayoutParams());
        playerIndex = playerParent.indexOfChild(binding.playerPanel);
        playerParent.removeView(binding.playerPanel);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        binding.root.addView(binding.playerPanel, params);
        binding.playerPanel.setTranslationZ(32f);
        binding.playerPanel.setVisibility(View.VISIBLE);
        binding.playerPanel.setRadius(0);
        updatePlayerPanelFocus();
        setInlineFullscreenIcon();
        boolean playing = service() != null && !player().isEmpty() && player().isPlaying();
        updateInlineButtons(playing);
        hideInlineControls();
        updateInlineDisplayPanel();
        binding.playerPanel.requestFocus();
        Util.toggleFullscreen(this, true);
        setInlineFullscreenOrientation();
        scheduleMobileInlineSideControlMarginUpdate();
    }

    private void applyInlineShortDramaMode() {
        if (!isShortDramaSource()) {
            resetInlineShortDramaMode();
            return;
        }
        if (inlinePiPLayout || isInPictureInPictureMode()) return;
        if (!inlineFullscreen) enterInlineFullscreen();
        if (!shouldUseInlineShortDramaMode()) {
            resetInlineShortDramaMode();
            setInlineFullscreenOrientation();
            return;
        }
        inlineShortDramaMode = true;
        setInlineFullscreenOrientation();
        setInlineShortDramaVideoFrame(!shouldUseShortDramaPortrait());
        setInlinePreviewScale(SHORT_DRAMA_SCALE);
        hideInlineControls();
    }

    private void resetInlineShortDramaMode() {
        boolean restoreScale = inlineShortDramaMode;
        inlineShortDramaMode = false;
        setInlineShortDramaVideoFrame(false);
        if (restoreScale && inlineStarted) setInlineScale(getInlineScale());
    }

    private void setInlineFullscreenOrientation() {
        if (!inlineFullscreen) return;
        int orientation = getInlineFullscreenOrientation();
        if (getRequestedOrientation() != orientation) setRequestedOrientation(orientation);
    }

    private int getInlineFullscreenOrientation() {
        if (shouldUseInlineShortDramaMode()) return shouldUseShortDramaPortrait() ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
        boolean portrait = service() != null && !player().isEmpty() && player().isPortrait();
        return portrait ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
    }

    private boolean shouldUseInlineShortDramaMode() {
        if (!isShortDramaSource()) return false;
        if (!hasInlineVideoSize()) return true;
        return player().isPortrait();
    }

    private boolean shouldUseShortDramaPortrait() {
        return Util.isMobile() && !ResUtil.isPad();
    }

    private boolean hasInlineVideoSize() {
        return service() != null && player() != null && !player().isEmpty() && player().getVideoWidth() > 0 && player().getVideoHeight() > 0;
    }

    private void setInlineShortDramaVideoFrame(boolean enabled) {
        if (binding == null) return;
        if (!enabled) {
            setInlineVideoFrame(binding.exo, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            setInlineVideoFrame(binding.danmaku, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            return;
        }
        int width = binding.playerPanel.getWidth();
        int height = binding.playerPanel.getHeight();
        if (width <= 0 || height <= 0) {
            binding.playerPanel.post(() -> {
                if (inlineShortDramaMode && inlineFullscreen && binding != null) setInlineShortDramaVideoFrame(true);
            });
            return;
        }
        int frameWidth;
        int frameHeight;
        if (width * SHORT_DRAMA_FRAME_HEIGHT > height * SHORT_DRAMA_FRAME_WIDTH) {
            frameHeight = height;
            frameWidth = Math.max(1, height * SHORT_DRAMA_FRAME_WIDTH / SHORT_DRAMA_FRAME_HEIGHT);
        } else {
            frameWidth = width;
            frameHeight = Math.max(1, width * SHORT_DRAMA_FRAME_HEIGHT / SHORT_DRAMA_FRAME_WIDTH);
        }
        setInlineVideoFrame(binding.exo, frameWidth, frameHeight);
        setInlineVideoFrame(binding.danmaku, frameWidth, frameHeight);
    }

    private void setInlineVideoFrame(View view, int width, int height) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.gravity = Gravity.CENTER;
        view.setLayoutParams(params);
    }

    private ViewGroup.LayoutParams copyInlinePlayerLayoutParams(ViewGroup.LayoutParams params) {
        if (params instanceof LinearLayout.LayoutParams layoutParams) return new LinearLayout.LayoutParams(layoutParams);
        if (params instanceof FrameLayout.LayoutParams layoutParams) return new FrameLayout.LayoutParams(layoutParams);
        if (params instanceof ViewGroup.MarginLayoutParams layoutParams) return new ViewGroup.MarginLayoutParams(layoutParams);
        return params == null ? null : new ViewGroup.LayoutParams(params);
    }

    private ViewGroup.LayoutParams embeddedInlinePlayerLayoutParams(ViewGroup parent, ViewGroup.LayoutParams fallback) {
        if (parent instanceof LinearLayout) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(252));
            params.setMargins(ResUtil.dp2px(16), ResUtil.dp2px(isFusionMode() ? 22 : 14), ResUtil.dp2px(16), ResUtil.dp2px(isFusionMode() ? 20 : 16));
            return params;
        }
        return copyInlinePlayerLayoutParams(fallback);
    }

    private void restoreEmbeddedInlinePlayerLayout() {
        if (binding == null || inlineFullscreen || inlinePiPLayout) return;
        if (!(binding.playerPanel.getParent() instanceof ViewGroup parent)) return;
        ViewGroup.LayoutParams params = embeddedInlinePlayerLayoutParams(parent, binding.playerPanel.getLayoutParams());
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = ResUtil.dp2px(252);
        if (params instanceof ViewGroup.MarginLayoutParams marginParams) {
            marginParams.setMargins(ResUtil.dp2px(16), ResUtil.dp2px(isFusionMode() ? 22 : 14), ResUtil.dp2px(16), ResUtil.dp2px(isFusionMode() ? 20 : 16));
        }
        binding.playerPanel.setLayoutParams(params);
        requestEmbeddedInlinePlayerLayout(parent);
        layoutEmbeddedInlinePageContent(parent);
    }

    private void requestEmbeddedInlinePlayerLayout(ViewGroup parent) {
        binding.playerPanel.forceLayout();
        binding.exo.forceLayout();
        binding.danmaku.forceLayout();
        parent.forceLayout();
        parent.requestLayout();
        binding.pageContent.forceLayout();
        binding.pageContent.requestLayout();
        binding.scroll.forceLayout();
        binding.scroll.requestLayout();
        binding.root.requestLayout();
    }

    private void layoutEmbeddedInlinePageContent(ViewGroup parent) {
        if (parent != binding.pageContent || binding.scroll.getWidth() <= 0) return;
        // Some TV back paths keep the old fullscreen measurement until the next traversal.
        int width = binding.scroll.getWidth();
        int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        binding.pageContent.measure(widthSpec, heightSpec);
        int height = Math.max(binding.pageContent.getMeasuredHeight(), binding.scroll.getHeight());
        binding.pageContent.layout(0, 0, width, height);
    }

    private void restoreInlineDetailScrollAfterOverlay() {
        if (binding == null || !isInlinePlayerMode()) return;
        binding.scroll.scrollTo(0, 0);
    }

    private void restoreInlinePlayerPanelAfterOverlay() {
        restoreEmbeddedInlinePlayerLayout();
        restoreInlineDetailScrollAfterOverlay();
        setInlineVideoFrame(binding.exo, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        setInlineVideoFrame(binding.danmaku, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        binding.playerPanel.setTranslationZ(0f);
        binding.playerPanel.setVisibility(View.VISIBLE);
        setPlayerCard(lightTheme ? ThemeColors.light() : ThemeColors.dark());
        binding.playerPanel.requestLayout();
        binding.exo.requestLayout();
        View surface = binding.exo.getVideoSurfaceView();
        if (surface != null) surface.requestLayout();
        binding.danmaku.requestLayout();
        binding.scroll.requestLayout();
    }

    private void scheduleInlinePlayerPanelRestoreAfterOverlay() {
        if (binding == null) return;
        binding.playerPanel.post(() -> {
            restoreEmbeddedInlinePlayerLayout();
            restoreInlineDetailScrollAfterOverlay();
        });
        binding.root.postDelayed(() -> {
            restoreEmbeddedInlinePlayerLayout();
            restoreInlineDetailScrollAfterOverlay();
        }, 180);
    }

    private void exitInlineFullscreen() {
        if (!inlineFullscreen) return;
        boolean suppressDisplay = suppressInlineDisplayForLeanbackFusionExit();
        prepareInlinePlayerTransition();
        inlineFullscreen = false;
        setInlineLock(false);
        ((ViewGroup) binding.playerPanel.getParent()).removeView(binding.playerPanel);
        if (playerParent != null && playerLayoutParams != null) {
            int index = playerIndex < 0 || playerIndex > playerParent.getChildCount() ? playerParent.getChildCount() : playerIndex;
            playerParent.addView(binding.playerPanel, index, embeddedInlinePlayerLayoutParams(playerParent, playerLayoutParams));
            binding.playerPanel.setLayoutParams(embeddedInlinePlayerLayoutParams(playerParent, playerLayoutParams));
        }
        resetInlineShortDramaMode();
        restoreInlinePlayerPanelAfterOverlay();
        setInlineFullscreenIcon();
        boolean playing = service() != null && !player().isEmpty() && player().isPlaying();
        updateInlineButtons(playing);
        if (!suppressDisplay) updateInlineDisplayPanel();
        Util.toggleFullscreen(this, false);
        updateMobileInlineSideControlMargins();
        setRequestedOrientation(requestedOrientation);
        focusInlinePlayerPanel();
        playerParent = null;
        playerLayoutParams = null;
        playerIndex = -1;
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        updateDetailThemeButtonVisibility();
        restoreEmbeddedInlinePlayerLayout();
        restoreInlineDetailScrollAfterOverlay();
        scheduleInlinePlayerPanelRestoreAfterOverlay();
    }

    private void enterInlinePiPLayout() {
        if (inlinePiPLayout || inlineFullscreen || binding == null) return;
        inlinePiPParent = (ViewGroup) binding.playerPanel.getParent();
        inlinePiPLayoutParams = copyInlinePlayerLayoutParams(binding.playerPanel.getLayoutParams());
        inlinePiPIndex = inlinePiPParent.indexOfChild(binding.playerPanel);
        inlinePiPTranslationZ = binding.playerPanel.getTranslationZ();
        inlinePiPLayout = true;
        updateDetailThemeButtonVisibility();
        // Keep the original source rect so PiP exits back to the inline card.
        inlinePiPSourceFrozen = true;
        inlinePiPParent.removeView(binding.playerPanel);
        binding.root.addView(binding.playerPanel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        binding.playerPanel.setTranslationZ(32f);
        binding.playerPanel.setVisibility(View.VISIBLE);
        binding.playerPanel.setRadius(0);
        updatePlayerPanelFocus();
        hideInlineDisplayPanel();
    }

    private void exitInlinePiPLayout() {
        if (!inlinePiPLayout || binding == null) return;
        ((ViewGroup) binding.playerPanel.getParent()).removeView(binding.playerPanel);
        if (inlinePiPParent != null && inlinePiPLayoutParams != null) {
            int index = inlinePiPIndex < 0 || inlinePiPIndex > inlinePiPParent.getChildCount() ? inlinePiPParent.getChildCount() : inlinePiPIndex;
            inlinePiPParent.addView(binding.playerPanel, index, embeddedInlinePlayerLayoutParams(inlinePiPParent, inlinePiPLayoutParams));
            binding.playerPanel.setLayoutParams(embeddedInlinePlayerLayoutParams(inlinePiPParent, inlinePiPLayoutParams));
        }
        inlinePiPLayout = false;
        restoreInlinePlayerPanelAfterOverlay();
        binding.playerPanel.setTranslationZ(inlinePiPTranslationZ);
        updateInlineDisplayPanel();
        focusInlinePlayerPanel();
        inlinePiPParent = null;
        inlinePiPLayoutParams = null;
        inlinePiPIndex = -1;
        inlinePiPTranslationZ = 0f;
        binding.playerPanel.post(() -> {
            if (binding == null) return;
            inlinePiPSourceFrozen = false;
            if (inlinePiP != null) inlinePiP.update(TmdbDetailActivity.this, binding.playerPanel);
        });
        updateDetailThemeButtonVisibility();
    }

    private void scheduleMobileInlineSideControlMarginUpdate() {
        updateMobileInlineSideControlMargins();
        if (binding != null && detailControlRoot != null) detailControlView(R.id.danmaku, View.class).postDelayed(this::updateMobileInlineSideControlMargins, 350);
    }

    private void updateMobileInlineSideControlMargins() {
        if (!Util.isMobile() || detailControlRoot == null) return;
        View danmaku = detailControlView(R.id.danmaku, View.class);
        View danmakuParent = danmaku.getParent() instanceof View ? (View) danmaku.getParent() : danmaku;
        int margin = ResUtil.dp2px(inlineFullscreen && ResUtil.isLand(this) ? INLINE_SIDE_CONTROL_FULLSCREEN_MARGIN_DP : INLINE_SIDE_CONTROL_MARGIN_DP);
        setStartMargin(danmakuParent, margin);
        setEndMargin(detailControlView(R.id.right, View.class), margin);
    }

    private void setStartMargin(View view, int margin) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams marginParams)) return;
        marginParams.setMarginStart(margin);
        view.setLayoutParams(marginParams);
    }

    private void setEndMargin(View view, int margin) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams marginParams)) return;
        marginParams.setMarginEnd(margin);
        view.setLayoutParams(marginParams);
    }

    private void setInlineLock(boolean lock) {
        setLock(lock);
        if (inlineGestureDetector != null) inlineGestureDetector.setLock(lock);
    }

    private void closeDetailFullscreenPlayer() {
        saveInlineHistory();
        inlinePlaybackGeneration++;
        inlinePlaybackLoading = false;
        introSkipPlayback.reset();
        subtitlePlaybackSession.stop(this);
        hideInlineControls();
        if (service() != null) {
            player().stop();
            player().clear();
        }
        hideInlineLoading();
        binding.playerError.setVisibility(View.GONE);
        updateInlineDisplayPanel();
        binding.playerPanel.setVisibility(View.GONE);
        inlineStarted = false;
        detailPlayerActive = false;
        pendingInlineResult = null;
        currentInlineResult = null;
        useParse = false;
        updateInlineButtons(false);
        DanmakuApi.cancel();
        updatePlayLabel();
    }

    private void playAdjacentEpisode(int direction) {
        playAdjacentEpisode(direction, true);
    }

    private boolean playAdjacentEpisode(int direction, boolean notify) {
        if (selectedFlag == null || selectedEpisode == null || selectedFlag.getEpisodes() == null) return false;
        List<Episode> episodes = selectedFlag.getEpisodes();
        int index = episodes.indexOf(selectedEpisode);
        int next = index + direction;
        while (index >= 0 && next >= 0 && next < episodes.size() && isSameEpisodeSlot(selectedEpisode, episodes.get(next), episodes)) next += direction;
        if (index < 0 || next < 0 || next >= episodes.size()) {
            if (notify) Notify.show(direction > 0 ? R.string.error_play_next : R.string.error_play_prev);
            return false;
        }
        cancelPendingInlinePlayback();
        selectedEpisode = episodes.get(next);
        selectedSeasonNumber = seasonForEpisode(selectedEpisode, episodes);
        resetEpisodeRange();
        renderEpisodes();
        onPlay();
        return true;
    }

    private boolean isSameEpisodeSlot(Episode left, Episode right, List<Episode> episodes) {
        EpisodePosition leftPosition = episodePosition(left, episodes);
        EpisodePosition rightPosition = episodePosition(right, episodes);
        return leftPosition.number() > 0
                && leftPosition.number() == rightPosition.number()
                && leftPosition.season() == rightPosition.season();
    }

    private void checkInlineNext() {
        checkInlineNext(true);
    }

    private void checkInlineNext(boolean notify) {
        if (history != null && history.isRevPlay()) onInlinePrev(notify);
        else onInlineNext(notify);
    }

    private void checkInlinePrev() {
        if (history != null && history.isRevPlay()) onInlineNext(true);
        else onInlinePrev(true);
    }

    private void onInlineNext(boolean notify) {
        if (playAdjacentEpisode(1, false)) return;
        if (notify) Notify.show(history != null && history.isRevPlay() ? R.string.error_play_prev : R.string.error_play_next);
    }

    private void onInlinePrev(boolean notify) {
        if (playAdjacentEpisode(-1, false)) return;
        if (notify) Notify.show(history != null && history.isRevPlay() ? R.string.error_play_next : R.string.error_play_prev);
    }

    private void checkInlineEnded(boolean notify) {
        checkInlineNext(notify);
    }

    private MediaMetadata buildMetadata() {
        String title = playbackHistoryName();
        String episode = selectedEpisode != null ? historyEpisodeTitle(selectedEpisode) : "";
        String artist = TextUtils.isEmpty(episode) || title.equals(episode) ? "" : episode;
        return new MediaMetadata.Builder().setTitle(title).setArtist(artist).setArtworkUri(android.net.Uri.parse(playbackHistoryPic())).build();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (binding != null && isInlineControlsVisible()) touchInlineControls();
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (handleInlineKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    private boolean handleInlineKey(KeyEvent event) {
        if (!isInlinePlayerMode() || !inlineStarted) return false;
        if (KeyUtil.isBackKey(event) && binding.gestureSeek.getVisibility() == View.VISIBLE) {
            if (KeyUtil.isActionUp(event)) hideInlineGestureOverlays();
            return true;
        }
        if (KeyUtil.isBackKey(event) && isLock() && inlineFullscreen) {
            if (KeyUtil.isActionUp(event)) showInlineControls(true, false);
            return true;
        }
        if (KeyUtil.isBackKey(event) && Util.isLeanback() && inlineFullscreen) {
            if (KeyUtil.isActionUp(event)) backFromInlineFullscreen();
            return true;
        }
        if (KeyUtil.isBackKey(event) && isInlineControlsVisible()) {
            if (KeyUtil.isActionUp(event)) hideInlineControls();
            return true;
        }
        if (isInlineControlsVisible()) {
            rememberInlineControlFocus();
            setInlineHideCallback();
        }
        if (handleInlineSeekKey(event)) return true;
        if (KeyUtil.isMenuKey(event) && (inlineFullscreen || isInlineControlsVisible())) {
            if (isInlineControlsVisible()) hideInlineControls();
            else if (Setting.getFullscreenMenuKey() == 1) showInlineEpisodes();
            else showInlineControls(true);
            return true;
        }
        if (!inlineFullscreen || isInlineControlsVisible() || service() == null) return false;
        if (isLock()) {
            if (KeyUtil.isActionUp(event)) showInlineControls(true, false);
            return true;
        }
        if (KeyUtil.isEnterKey(event)) {
            if (KeyUtil.isActionUp(event)) toggleInlinePlayback();
            return true;
        }
        if (event.isLongPress() && KeyUtil.isUpKey(event)) {
            onSpeedUp();
            inlineKeySpeedChanging = true;
            return true;
        }
        if (!KeyUtil.isActionUp(event)) return false;
        if (KeyUtil.isUpKey(event)) {
            if (inlineKeySpeedChanging) onSpeedEnd();
            else showInlineControls(true);
            inlineKeySpeedChanging = false;
            return true;
        }
        if (KeyUtil.isDownKey(event)) {
            showInlineControls(true);
            return true;
        }
        return false;
    }

    private boolean handleInlineSeekKey(KeyEvent event) {
        if (!canInlineKeySeek(event)) return false;
        if (KeyUtil.isActionDown(event)) {
            inlineKeySeekTime += KeyUtil.isSeekBackKey(event) ? -Constant.INTERVAL_SEEK : Constant.INTERVAL_SEEK;
            onSeeking(inlineKeySeekTime);
            return true;
        }
        if (KeyUtil.isActionUp(event)) {
            if (inlineKeySeekTime == 0) inlineKeySeekTime = KeyUtil.isSeekBackKey(event) ? -Constant.INTERVAL_SEEK : Constant.INTERVAL_SEEK;
            App.post(inlineKeySeekEnd, 250);
            return true;
        }
        return true;
    }

    private boolean canInlineKeySeek(KeyEvent event) {
        if (!canInlineSeek() || !isInlineSeekKey(event)) return false;
        if (isInlineMediaSeekKey(event)) return true;
        return !isInlineControlsVisible() && (inlineFullscreen || getCurrentFocus() == binding.playerPanel);
    }

    private boolean canInlineSeek() {
        return isInlinePlayerMode() && inlineStarted && service() != null && controller() != null && player() != null && !player().isEmpty();
    }

    private boolean isInlineSeekKey(KeyEvent event) {
        return KeyUtil.isSeekBackKey(event) || KeyUtil.isSeekForwardKey(event);
    }

    private boolean isInlineMediaSeekKey(KeyEvent event) {
        return event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_REWIND || event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
    }

    private void onInlineKeySeekEnd() {
        long time = inlineKeySeekTime;
        inlineKeySeekTime = 0;
        onSeekEnd(time);
        hideInlineGestureOverlays();
    }

    @Override
    protected void onPrepare() {
        setInlineScale(getInlineScale());
        prepareInlineStartPosition();
        if (history != null && service() != null && !player().isEmpty()) setInlineSpeed(history.getSpeed());
    }

    private long getInlineResumePosition() {
        return history == null ? C.TIME_UNSET : Math.max(history.getOpening(), history.getPosition());
    }

    private long getInlineStartPosition() {
        return inlineStartPosition != C.TIME_UNSET ? inlineStartPosition : getInlineResumePosition();
    }

    private void prepareInlineStartPosition() {
        long position = getInlineStartPosition();
        if (position > 0 && service() != null && player() != null && !player().isEmpty()) player().seekTo(position);
    }

    private void applyInlineStartPosition() {
        if (inlineStartPositionApplied || history == null || controller() == null) return;
        long position = getInlineStartPosition();
        inlineStartPositionApplied = true;
        if (position > 0) controller().seekTo(position);
    }

    @Override
    protected void onServiceConnected() {
        ensureInlineDanmakuController();
        if (pendingInlineResult != null) startInlinePlayer(pendingInlineResult);
    }

    private void ensureInlineDanmakuController() {
        if (service() == null || inlineControlController == null) return;
        player().setDanmakuController(binding.exo.getDanmakuController());
        if (!Util.isMobile()) player().setDanmakuEnabled(true);
        else inlineControlController.applyDanmakuSetting();
    }

    @Override
    protected void onPlayingChanged(boolean isPlaying) {
        if (isPlaying) hideInlinePauseInfo();
        else if (isPaused()) showInlinePauseInfo();
        if (inlinePiP != null) inlinePiP.update(this, isPlaying);
        updateInlineButtons(isPlaying);
        updateInlineDisplayPanel();
    }

    @Override
    protected void onSizeChanged(VideoSize size) {
        updateInlineButtons(service() != null && !player().isEmpty() && player().isPlaying());
        updateInlineDisplayPanel();
        if (inlineStarted && (isShortDramaSource() || inlineShortDramaMode)) applyInlineShortDramaMode();
    }

    @Override
    protected void onStateChanged(int state) {
        if (!isInlinePlayerMode()) return;
        if (state == Player.STATE_BUFFERING) showInlineLoading();
        if (state == Player.STATE_READY) {
            hideInlineLoading();
            hideInlineControls();
            player().reset();
            applyInlineStartPosition();
            updateInlineButtons(player().isPlaying());
            applyInlineShortDramaMode();
            requestIntroSkipPlan();
            applyAutoIntroSkip();
        }
        if (state == Player.STATE_ENDED) checkInlineEnded(true);
        updateInlineDisplayPanel();
    }

    @Override
    protected void onTracksChanged() {
        updateInlineButtons(service() != null && !player().isEmpty() && player().isPlaying());
        updateInlineDisplayPanel();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        scheduleMobileInlineSideControlMarginUpdate();
        if (!inlineFullscreen) updateEpisodeLayoutForCurrentItems();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        enterInlinePiP(false);
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        updateDetailThemeButtonVisibility();
        if (!isInlinePlayerMode()) return;
        if (isInPictureInPictureMode) {
            hideInlineControls();
            hideInlineGestureOverlays();
            if (inlinePiPLayoutRequested) enterInlinePiPLayout();
            return;
        }
        exitInlinePiPLayout();
        inlinePiPLayoutRequested = false;
        updateInlineButtons(service() != null && player() != null && !player().isEmpty() && player().isPlaying());
        updateInlineDisplayPanel();
        updateDetailThemeButtonVisibility();
    }

    @Override
    protected void onError(String msg) {
        showInlineError(msg);
    }

    @Override
    public void onSubtitleClick() {
        SubtitleDialog.create().view(binding.exo.getSubtitleView()).search(this::showSubtitleSearch).show(this);
    }

    private void showSubtitleSearch() {
        SubtitleManualSearchDialog.show(this, subtitlePlaybackSession, this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == 1001) PlayerHelper.onExternalResult(data, service()::dispatchNext, controller()::seekTo);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (!inlineStarted || service() == null || player() == null || player().isEmpty() || !isOwner()) return;
        if (event.getType() == RefreshEvent.Type.DANMAKU) {
            player().setDanmaku(Danmaku.from(event.getPath()));
            refreshInlineDanmakuButtons();
        } else if (event.getType() == RefreshEvent.Type.SUBTITLE) {
            player().setSub(Sub.from(event.getPath()));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        scheduleBackdropSlide(BACKDROP_SLIDE_DELAY_MS);
    }

    @Override
    protected void onPause() {
        cancelBackdropSlideRequest();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        subtitlePlaybackSession.stop(this);
        if (inlinePiPLayout) exitInlinePiPLayout();
        if (inlineFullscreen) exitInlineFullscreen();
        cancelBackdropSlideRequest();
        App.removeCallbacks(inlineHideControls);
        App.removeCallbacks(inlineKeySeekEnd);
        EpisodeTitlePopup.dismiss();
        saveInlineHistory();
        if (inlineClock != null) inlineClock.release();
        DanmakuApi.cancel();
        super.onDestroy();
    }

    @Override
    public String getSubtitlePlaybackKey() {
        return getHistoryKey();
    }

    @Override
    public Site getSubtitleSite() {
        return getCurrentSite();
    }

    @Override
    public Vod getSubtitleVod() {
        return vod;
    }

    @Override
    public Episode getSubtitleEpisode() {
        return selectedEpisode;
    }

    @Override
    public TmdbItem getSubtitleTmdbItem() {
        return matchedTmdbItem == null ? initialTmdbItem : matchedTmdbItem;
    }

    @Override
    public TmdbEpisode getSubtitleTmdbEpisode() {
        return selectedEpisode == null ? null : selectedEpisode.getTmdbEpisode();
    }

    @Override
    public PlayerManager getSubtitlePlayer() {
        return player();
    }

    @Override
    public boolean isSubtitleHostActive() {
        return !isFinishing() && !isDestroyed() && service() != null && player() != null && !player().isReleased() && inlineStarted && currentInlineResult != null && isOwner();
    }

    @Override
    protected void onBackInvoked() {
        if (binding.gestureSeek.getVisibility() == View.VISIBLE) {
            hideInlineGestureOverlays();
            return;
        }
        if (isLock() && inlineFullscreen) {
            showInlineControls(true, false);
            return;
        }
        if (Util.isLeanback() && inlineFullscreen) {
            backFromInlineFullscreen();
            return;
        }
        if (isInlineControlsVisible()) {
            hideInlineControls();
            return;
        }
        if (inlineFullscreen) {
            backFromInlineFullscreen();
            return;
        }
        super.onBackInvoked();
    }

    private void saveInlineHistory() {
        if (!isInlinePlayerMode() || !inlineStarted || !isOwner() || history == null) return;
        updateInlineHistoryProgress();
        if (history.canSave() && !Setting.isIncognito()) {
            history.merge().save();
            RefreshEvent.history();
        }
    }

    private void syncInlineHistory() {
        updateInlineHistoryProgress();
        if (history != null && !Setting.isIncognito()) Task.execute(() -> history.save());
    }

    private void updateInlineHistoryProgress() {
        if (history == null || service() == null || player() == null || player().isReleased() || !isOwner()) {
            updateInlineHistoryPlayer();
            return;
        }
        updateInlineHistoryProgress(System.currentTimeMillis(), player().getPosition(), player().getDuration());
    }

    private void updateInlineHistoryProgress(long time, long position, long duration) {
        if (history == null) return;
        history.setCreateTime(time);
        if (position > 0) history.setPosition(position);
        if (duration > 0) history.setDuration(duration);
        updateInlineHistoryPlayer();
    }

    private void updateInlineHistoryPlayer() {
        if (history != null && service() != null && player() != null && !player().isReleased()) history.setPlayer(player().getPlayerType());
    }

    private void requestIntroSkipPlan() {
        if (!Setting.isAutoSkipIntroOutro() || player() == null) {
            introSkipPlayback.reset();
            return;
        }
        IntroSkipService.Query query = buildIntroSkipQuery();
        if (query == null) return;
        introSkipPlayback.request(query, this::applyAutoIntroSkip);
    }

    private boolean applyAutoIntroSkip() {
        if (!Setting.isAutoSkipIntroOutro() || player() == null) return false;
        return introSkipPlayback.apply(player(), () -> checkInlineEnded(false));
    }

    private IntroSkipService.Query buildIntroSkipQuery() {
        TmdbItem item = matchedTmdbItem;
        if (item == null || item.getTmdbId() <= 0) return null;
        int season = 0;
        int number = 0;
        if (item.isTv()) {
            TmdbEpisode tmdbEpisode = selectedEpisode == null ? null : selectedEpisode.getTmdbEpisode();
            season = tmdbEpisode != null && tmdbEpisode.getSeasonNumber() > 0 ? tmdbEpisode.getSeasonNumber() : selectedSeasonNumber;
            number = tmdbEpisode != null && tmdbEpisode.getNumber() > 0 ? tmdbEpisode.getNumber() : sourceEpisodeNumber(selectedEpisode);
            if (season <= 0 || number <= 0) return null;
        }
        long duration = player() == null ? 0 : Math.max(0, player().getDuration());
        return new IntroSkipService.Query(item.getTmdbId(), introSkipImdbId(), item.getMediaType(), season, number, duration);
    }

    private String introSkipImdbId() {
        JsonObject externalIds = object(matchedTmdbDetail, "external_ids");
        return string(externalIds, "imdb_id");
    }

    @Override
    public void onTimeChanged(long time) {
        if (!isInlinePlayerMode()) return;
        updateInlineLoadingTraffic();
        if (inlinePlaybackLoading || !isOwner() || history == null || service() == null || player() == null || player().isEmpty()) return;
        long position = player().getPosition();
        long duration = player().getDuration();
        boolean canUpdateProgress = inlineStartPositionApplied || getInlineStartPosition() <= 0;
        if (canUpdateProgress) {
            updateInlineHistoryProgress(time, position, duration);
        } else {
            history.setCreateTime(time);
            updateInlineHistoryPlayer();
        }
        if (canUpdateProgress && history.canSave() && history.canSync()) syncInlineHistory();
        if (canUpdateProgress && applyAutoIntroSkip()) return;
        if (canUpdateProgress && history.getEnding() > 0 && duration > 0 && history.getEnding() + position >= duration) checkInlineEnded(false);
        if (isInlineControlsVisible()) updateMobileInlineControlTime();
        updateInlineDisplayPanel();
    }

    private final PlaybackService.NavigationCallback mNavigationCallback = new PlaybackService.NavigationCallback() {
        @Override
        public void onNext() {
            checkInlineNext();
        }

        @Override
        public void onPrev() {
            checkInlinePrev();
        }

        @Override
        public void onStop() {
            saveInlineHistory();
        }

        @Override
        public void onReplay() {
            if (history != null) history.setPosition(C.TIME_UNSET);
            playInline();
        }
    };

    private void updateInlineHistory(Episode item) {
        if (selectedFlag == null || item == null) return;
        if (history == null) {
            history = new History();
            history.setKey(getHistoryKey());
            history.setCid(VodConfig.getCid());
        }
        boolean same = isHistoryEpisode(item, history);
        history.setPosition(same ? history.getPosition() : C.TIME_UNSET);
        history.setCid(VodConfig.getCid());
        history.setVodName(playbackHistoryName());
        history.setVodFlag(selectedFlag.getFlag());
        history.setVodRemarks(historyEpisodeTitle(item));
        history.setEpisodeUrl(item.getUrl());
        history.setVodPic(playbackHistoryPic());
        history.setSpeed(normalizeInlineSpeed(history.getSpeed()));
        syncDanmakuCompatHistory();
    }

    private void onKeep() {
        Keep keep = Keep.find(getHistoryKey());
        if (keep != null) keep.delete();
        else createKeep();
        updateKeepState();
    }

    private void createKeep() {
        Keep keep = new Keep();
        keep.setKey(getHistoryKey());
        keep.setCid(VodConfig.getCid());
        keep.setVodPic(vod != null ? vod.getPic() : getPicText());
        keep.setVodName(vod != null ? vod.getName() : getNameText());
        keep.setSiteName(getSiteName());
        keep.setCreateTime(System.currentTimeMillis());
        keep.save();
    }

    private void updateKeepState() {
        boolean kept = Keep.find(getHistoryKey()) != null;
        String text = getString(TmdbDetailLabels.keepLabel(kept));
        binding.keep.setSelected(kept);
        binding.keepTop.setSelected(kept);
        binding.keepFusion.setSelected(kept);
        binding.keep.setText(text);
        binding.keepTop.setText(text);
        binding.keepFusion.setText(text);
    }

    private void changeSource() {
        if (vod == null) return;
        String keyword = getSourceSearchKeyword();
        int generation = loadGeneration;
        int searchGeneration = ++sourceSearchGeneration;
        Notify.show(getString(R.string.detail_source_searching));
        Task.execute(() -> {
            SourceMatch match = searchChangeSource(keyword);
            runOnAliveUi(() -> {
                if (generation != loadGeneration || searchGeneration != sourceSearchGeneration || vod == null) return;
                if (match == null) {
                    Notify.show(R.string.detail_source_empty);
                    return;
                }
                Notify.show(getString(R.string.play_switch_site, match.vod().getSiteName()));
                switchSourceDetail(match.site(), match.vod(), matchedTmdbItem);
            });
        });
    }

    private void tryAutoChangeSource() {
        brokenSources.add(getKeyText());
        String keyword = getSourceSearchKeyword();
        int generation = loadGeneration;
        int searchGeneration = ++sourceSearchGeneration;
        Notify.show(getString(R.string.detail_source_searching));
        Task.execute(() -> {
            SourceMatch match = searchChangeSource(keyword);
            runOnAliveUi(() -> {
                if (generation != loadGeneration || searchGeneration != sourceSearchGeneration) return;
                if (match == null) {
                    Notify.show(R.string.detail_source_empty);
                    return;
                }
                Notify.show(getString(R.string.play_switch_site, match.vod().getSiteName()));
                switchSourceDetail(match.site(), match.vod(), matchedTmdbItem);
            });
        });
    }

    private boolean openGlobalSourceSearch() {
        String keyword = getSourceSearchKeyword();
        if (TextUtils.isEmpty(keyword)) return false;
        SearchActivity.direct(this, keyword);
        return true;
    }

    private String getSourceSearchKeyword() {
        if (vod != null && !TextUtils.isEmpty(vod.getName())) return vod.getName();
        String keyword = getTmdbSearchQuery();
        return TextUtils.isEmpty(keyword) ? getNameText() : keyword;
    }

    private void loadPersonDetail(TmdbPerson person) {
        if (!canMatchTmdb()) {
            Notify.show(getString(R.string.detail_tmdb_need_key));
            return;
        }
        TmdbPersonActivity.start(this, person, getKeyText(), getDetailMode());
    }

    private void openRelatedItem(TmdbItem item) {
        Site site = getCurrentSite();
        if (site == null || site.isEmpty() || !site.isSearchable()) {
            Notify.show(R.string.detail_site_not_searchable);
            return;
        }
        int generation = loadGeneration;
        int searchGeneration = ++sourceSearchGeneration;
        Notify.show(getString(R.string.detail_work_searching, item.getTitle()));
        Task.execute(() -> {
            Vod match = searchCurrentSite(item.getTitle(), site);
            runOnAliveUi(() -> {
                if (generation != loadGeneration || searchGeneration != sourceSearchGeneration) return;
                if (match == null) {
                    Notify.show(getString(R.string.detail_work_global_searching, item.getTitle()));
                    SearchActivity.direct(this, item.getTitle());
                    return;
                }
                openMatchedDetail(site, match, item);
            });
        });
    }

    private void openMatchedDetail(Site site, Vod match, TmdbItem item) {
        if (isFusionMode()) {
            switchSourceDetail(site, match, item, "");
            return;
        }
        Intent intent = new Intent(this, TmdbDetailActivity.class);
        int detailMode = getDetailMode();
        intent.putExtra("detail_mode", detailMode);
        intent.putExtra("fusion", detailMode == Setting.DETAIL_OPEN_FUSION);
        intent.putExtra("key", site.getKey());
        intent.putExtra("id", match.getId());
        intent.putExtra("name", match.getName());
        intent.putExtra("pic", match.getPic());
        intent.putExtra("mark", "");
        putTmdbItem(intent, item);
        startActivity(intent);
    }

    private void switchSourceDetail(Site site, Vod match, TmdbItem item) {
        switchSourceDetail(site, match, item, sourceSwitchMark());
    }

    private void switchSourceDetail(Site site, Vod match, TmdbItem item, String mark) {
        TmdbBundle reusableBundle = canReuseTmdbBundle(item) ? activeTmdbBundle : null;
        Intent intent = new Intent(getIntent());
        intent.putExtra("detail_mode", getDetailMode());
        intent.putExtra("fusion", isFusionMode());
        intent.putExtra("key", site.getKey());
        intent.putExtra("id", match.getId());
        intent.putExtra("name", match.getName());
        intent.putExtra("pic", match.getPic());
        intent.putExtra("mark", mark);
        putTmdbItem(intent, item);
        setIntent(intent);
        resetDetailState();
        loadContent(reusableBundle);
    }

    private String sourceSwitchMark() {
        if (selectedEpisode != null && !TextUtils.isEmpty(selectedEpisode.getName())) return selectedEpisode.getName();
        return getMarkText();
    }

    private boolean canReuseTmdbBundle(@Nullable TmdbItem item) {
        if (activeTmdbBundle == null) return false;
        if (item == null || item.getTmdbId() <= 0 || TextUtils.isEmpty(item.getMediaType())) return true;
        TmdbItem activeItem = activeTmdbBundle.item();
        return activeItem != null && activeItem.getTmdbId() == item.getTmdbId() && item.getMediaType().equals(activeItem.getMediaType());
    }

    private Vod searchCurrentSite(String keyword, Site site) {
        try {
            Result result = SiteApi.searchContent(site, keyword, false, "1");
            return bestVod(result != null ? result.getList() : List.of(), keyword);
        } catch (Throwable e) {
            return null;
        }
    }

    private SourceMatch searchChangeSource(String keyword) {
        ExecutorCompletionService<SourceMatch> completion = new ExecutorCompletionService<>(Task.searchExecutor());
        List<Future<SourceMatch>> futures = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) {
            if (isChangeSourceCandidate(site)) futures.add(completion.submit(() -> searchChangeSource(site, keyword)));
        }
        SourceMatch best = null;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Constant.TIMEOUT_SEARCH);
        try {
            for (int i = 0; i < futures.size(); i++) {
                long timeout = deadline - System.nanoTime();
                if (timeout <= 0) break;
                Future<SourceMatch> future = completion.poll(timeout, TimeUnit.NANOSECONDS);
                if (future == null) break;
                SourceMatch match = future.get();
                if (match == null) continue;
                if (best == null || match.score() > best.score()) best = match;
                if (match.score() >= 300) break;
            }
        } catch (Throwable ignored) {
        } finally {
            for (Future<SourceMatch> future : futures) future.cancel(true);
        }
        return best;
    }

    private SourceMatch searchChangeSource(Site site, String keyword) {
        int bestScore = Integer.MIN_VALUE;
        Vod best = null;
        try {
            Result result = SiteApi.searchContent(site, keyword, true, "1");
            for (Vod item : result != null ? result.getList() : List.<Vod>of()) {
                if (isSameSource(item, site)) continue;
                int score = scoreVod(item, keyword);
                if (score > bestScore) {
                    bestScore = score;
                    best = item;
                }
            }
        } catch (Throwable ignored) {
        }
        return bestScore > 0 ? new SourceMatch(site, best, bestScore) : null;
    }

    private boolean isChangeSourceCandidate(Site site) {
        if (site == null || site.isEmpty() || !site.isSearchable()) return false;
        if (!site.isChangeable()) return false;
        if (brokenSources.contains(site.getKey())) return false;
        return !site.getKey().equals(getKeyText());
    }

    private boolean isSameSource(Vod item, Site site) {
        if (item == null) return true;
        if (getIdText().equals(item.getId()) && getKeyText().equals(site.getKey())) return true;
        return false;
    }

    private Vod bestVod(List<Vod> items, String keyword) {
        if (items == null || items.isEmpty()) return null;
        Vod best = null;
        int score = Integer.MIN_VALUE;
        for (Vod item : items) {
            int current = scoreVod(item, keyword);
            if (current > score) {
                score = current;
                best = item;
            }
        }
        return score > 0 ? best : null;
    }

    private int scoreVod(Vod item, String keyword) {
        if (item == null) return Integer.MIN_VALUE;
        String normalizedKeyword = normalize(keyword);
        String name = normalize(item.getName());
        if (name.isEmpty()) return Integer.MIN_VALUE;
        if (name.equals(normalizedKeyword)) return 300;
        if (name.contains(normalizedKeyword) || normalizedKeyword.contains(name)) return 220;
        String remarks = normalize(item.getRemarks());
        if (!remarks.isEmpty() && (remarks.contains(normalizedKeyword) || normalizedKeyword.contains(remarks))) return 120;
        return 0;
    }

    private TmdbItem chooseTmdbMatch(List<TmdbItem> items, String keyword, @Nullable Vod sourceVod) {
        TmdbItem strict = chooseStrictTmdbMatch(items, keyword, sourceVod);
        if (strict != null || !Setting.isTmdbSmartMatch() || sourceVod == null) return strict;
        TmdbBundle bundle = chooseSmartTmdbBundle(items, keyword, sourceVod);
        return bundle == null ? null : bundle.item();
    }

    private TmdbItem chooseStrictTmdbMatch(List<TmdbItem> items, String keyword, @Nullable Vod sourceVod) {
        // 自动匹配必须保持保守：这里只允许“标题归一化后完全一致”的候选进入评分。
        // 如果站源详情或搜索词里能提取到年份，候选的 TMDB 年份也必须完全同年。
        // 源标题明确带季数时，允许用对应 TMDB 分季首播年匹配根剧集条目。
        // 演员、导演、简介里的主创信息只用于同名候选之间消歧，不允许用来放宽标题或年份规则。
        logTmdbMatch("开始自动匹配：关键词=%s，归一化=%s，站源年份=%d，站源季=%d，是否有站源详情=%s，候选原始数量=%d",
                keyword, normalize(keyword), sourceYear(keyword, sourceVod), sourceSeasonNumber(keyword, sourceVod), sourceVod != null, items == null ? 0 : items.size());
        List<TmdbCandidate> candidates = scoreTmdbCandidates(items, keyword, sourceVod);
        if (candidates.isEmpty()) {
            logTmdbMatch("自动匹配结束：没有严格标题/年份一致的候选，交给手动选择");
            return null;
        }
        if (candidates.size() == 1 && candidates.get(0).titleScore >= 210) {
            logTmdbMatch("自动匹配成功：只有一个严格候选，标题=%s，年份=%d，评分=%d",
                    candidates.get(0).item.getTitle(), tmdbItemYear(candidates.get(0).item), candidates.get(0).score);
            return candidates.get(0).item;
        }
        TmdbCandidate best = candidates.get(0);
        TmdbCandidate second = candidates.size() > 1 ? candidates.get(1) : null;
        int gap = second == null ? best.score : best.score - second.score;
        boolean accepted = best.score >= 360 && gap >= 50;
        logTmdbMatch("自动匹配判定：最佳=%s(%d年, 分=%d)，第二=%s，分差=%d，结果=%s",
                best.item.getTitle(), tmdbItemYear(best.item), best.score, second == null ? "无" : second.item.getTitle() + "(" + second.score + ")", gap, accepted ? "通过" : "不通过");
        return accepted ? best.item : null;
    }

    private TmdbBundle chooseTmdbBundle(List<TmdbItem> items, String keyword, Vod sourceVod) {
        // 走到这里说明仅靠搜索结果列表还不能稳定选出一个条目。
        // 此时只会对前几个“标题一致、年份一致”的候选拉取 TMDB 详情，
        // 再用站源演员、导演、简介中出现的完整姓名去命中 TMDB 演员/主创。
        // 这样可以解决同名剧缺少年份时的自动消歧，同时避免把《最佳女主角》误匹配成《主角》。
        logTmdbMatch("进入详情消歧：关键词=%s，站源演员=%s，站源导演=%s", keyword, sourceVod.getActor(), sourceVod.getDirector());
        List<TmdbCandidate> candidates = scoreTmdbCandidates(items, keyword, sourceVod);
        if (candidates.isEmpty()) {
            logTmdbMatch("详情消歧结束：没有可消歧候选");
            return Setting.isTmdbSmartMatch() ? chooseSmartTmdbBundle(items, keyword, sourceVod) : null;
        }
        TmdbCandidate best = null;
        TmdbCandidate second = null;
        int count = Math.min(6, candidates.size());
        logTmdbMatch("详情消歧开始拉取候选详情：候选数量=%d，拉取上限=%d", candidates.size(), count);
        for (int i = 0; i < count; i++) {
            TmdbCandidate candidate = candidates.get(i);
            try {
                TmdbBundle bundle = loadTmdbBundle(candidate.item);
                int peopleScore = hasSourcePeople(sourceVod) ? scoreTmdbPeople(bundle.detail(), sourceVod) : 0;
                int splitSeasonScore = TmdbMatchPolicy.splitSeasonDetailScore(tmdbMatchSourceText(keyword, sourceVod), bundle.detail());
                int score = candidate.score + peopleScore + splitSeasonScore;
                logTmdbMatch("详情消歧候选：标题=%s，年份=%d，基础分=%d，演员主创分=%d，分季分=%d，总分=%d",
                        candidate.item.getTitle(), tmdbItemYear(candidate.item), candidate.score, peopleScore, splitSeasonScore, score);
                TmdbCandidate scored = new TmdbCandidate(candidate.item, candidate.titleScore, score, bundle);
                if (best == null || scored.score > best.score) {
                    second = best;
                    best = scored;
                } else if (second == null || scored.score > second.score) {
                    second = scored;
                }
            } catch (Throwable ignored) {
                logTmdbMatch("详情消歧候选加载失败：标题=%s，错误=%s", candidate.item.getTitle(), ignored.getMessage());
            }
        }
        if (best == null || best.bundle == null) {
            logTmdbMatch("详情消歧结束：没有成功加载的候选详情");
            return Setting.isTmdbSmartMatch() ? chooseSmartTmdbBundle(items, keyword, sourceVod) : null;
        }
        int gap = second == null ? best.score : best.score - second.score;
        boolean accepted = best.score >= 420 && gap >= 50;
        logTmdbMatch("详情消歧判定：最佳=%s(%d年, 分=%d)，第二=%s，分差=%d，结果=%s",
                best.item.getTitle(), tmdbItemYear(best.item), best.score, second == null ? "无" : second.item.getTitle() + "(" + second.score + ")", gap, accepted ? "通过" : "不通过");
        if (accepted) return best.bundle;
        return Setting.isTmdbSmartMatch() ? chooseSmartTmdbBundle(items, keyword, sourceVod) : null;
    }

    private List<TmdbCandidate> scoreTmdbCandidates(List<TmdbItem> items, String keyword, @Nullable Vod sourceVod) {
        List<TmdbCandidate> candidates = new ArrayList<>();
        if (items == null || items.isEmpty()) return candidates;
        String normalized = normalize(keyword);
        int sourceYear = sourceYear(keyword, sourceVod);
        int sourceSeason = sourceSeasonNumber(keyword, sourceVod);
        for (TmdbItem item : items) {
            // 第一层过滤只看标题是否完全一致，不做 contains、相似度、首尾包含等近似判断。
            int titleScore = scoreTmdbTitle(item, normalized);
            if (titleScore <= 0) {
                logTmdbMatch("候选过滤：标题不一致，关键词=%s，候选=%s，候选年份=%d", keyword, item.getTitle(), tmdbItemYear(item));
                continue;
            }
            int itemYear = tmdbItemYear(item);
            boolean seasonYearMatched = false;
            // 第二层过滤看年份：只要站源侧有年份，TMDB 侧必须同年；源标题带季数时，可用对应季年份命中根剧集。
            if (sourceYear > 0 && itemYear != sourceYear) {
                seasonYearMatched = tmdbSeasonYearMatches(item, sourceSeason, sourceYear);
                if (!seasonYearMatched) {
                    logTmdbMatch("候选过滤：年份不一致，关键词=%s，候选=%s，站源年份=%d，TMDB年份=%d，站源季=%d", keyword, item.getTitle(), sourceYear, itemYear, sourceSeason);
                    continue;
                }
            }
            int yearScore = seasonYearMatched ? 120 : scoreTmdbYear(item, sourceYear);
            int seasonScore = seasonYearMatched ? 60 : 0;
            int typeScore = scoreTmdbMediaType(item, sourceVod);
            int creditScore = scoreTmdbPeople(item, sourceVod);
            int score = titleScore + yearScore + seasonScore + typeScore + creditScore;
            logTmdbMatch("候选保留：标题=%s，媒体=%s，年份=%d，站源季=%d，季年匹配=%s，标题分=%d，年份分=%d，季分=%d，类型分=%d，已有职员分=%d，总分=%d",
                    item.getTitle(), item.getMediaType(), itemYear, sourceSeason, seasonYearMatched, titleScore, yearScore, seasonScore, typeScore, creditScore, score);
            candidates.add(new TmdbCandidate(item, titleScore, score, null));
        }
        candidates.sort((a, b) -> Integer.compare(b.score, a.score));
        return candidates;
    }

    @Nullable
    private TmdbBundle chooseSmartTmdbBundle(List<TmdbItem> items, String keyword, @Nullable Vod sourceVod) {
        if (items == null || items.isEmpty()) return null;
        List<TmdbCandidate> candidates = scoreSmartTmdbCandidates(items, keyword, sourceVod);
        if (candidates.isEmpty()) {
            logTmdbMatch("智能匹配结束：没有可用候选");
            return null;
        }
        List<TmdbCandidate> scored = new ArrayList<>();
        int count = Math.min(6, candidates.size());
        logTmdbMatch("智能匹配开始拉取候选详情：关键词=%s，候选数量=%d，拉取上限=%d", keyword, candidates.size(), count);
        for (int i = 0; i < count; i++) {
            TmdbCandidate candidate = candidates.get(i);
            try {
                TmdbBundle bundle = loadTmdbBundle(candidate.item);
                int detailScore = scoreTmdbCountry(bundle.detail(), sourceVod)
                        + scoreTmdbPeople(bundle.detail(), sourceVod)
                        + TmdbMatchPolicy.splitSeasonDetailScore(tmdbMatchSourceText(keyword, sourceVod), bundle.detail());
                int score = candidate.score + detailScore;
                logTmdbMatch("智能匹配候选：标题=%s，媒体=%s，年份=%d，基础分=%d，详情分=%d，总分=%d",
                        candidate.item.getTitle(), candidate.item.getMediaType(), tmdbItemYear(candidate.item), candidate.score, detailScore, score);
                scored.add(new TmdbCandidate(candidate.item, candidate.titleScore, score, bundle));
            } catch (Throwable ignored) {
                logTmdbMatch("智能匹配候选加载失败：标题=%s，错误=%s", candidate.item.getTitle(), ignored.getMessage());
            }
        }
        if (scored.isEmpty()) {
            logTmdbMatch("智能匹配结束：没有成功加载详情的候选");
            return null;
        }
        scored.sort((a, b) -> Integer.compare(b.score, a.score));
        TmdbCandidate best = scored.get(0);
        TmdbCandidate second = scored.size() > 1 ? scored.get(1) : null;
        int sourceYear = sourceYear(keyword, sourceVod);
        int gap = second == null ? best.score : best.score - second.score;
        boolean accepted = best.score >= smartAcceptScore(sourceYear) && (gap >= 25 || isUniqueNewest(best, scored, sourceYear));
        logTmdbMatch("智能匹配判定：最佳=%s(%d年, 分=%d)，第二=%s，分差=%d，结果=%s",
                best.item.getTitle(), tmdbItemYear(best.item), best.score, second == null ? "无" : second.item.getTitle() + "(" + second.score + ")", gap, accepted ? "通过" : "不通过");
        return accepted ? best.bundle : null;
    }

    private List<TmdbCandidate> scoreSmartTmdbCandidates(List<TmdbItem> items, String keyword, @Nullable Vod sourceVod) {
        List<TmdbCandidate> candidates = new ArrayList<>();
        String normalized = normalize(keyword);
        int sourceYear = sourceYear(keyword, sourceVod);
        for (TmdbItem item : items) {
            int titleScore = scoreTmdbSmartTitle(item, normalized);
            if (titleScore <= 0) {
                logTmdbMatch("智能候选过滤：标题差异过大，关键词=%s，候选=%s，候选年份=%d", keyword, item.getTitle(), tmdbItemYear(item));
                continue;
            }
            int yearScore = scoreTmdbSmartYear(item, sourceYear);
            if (sourceYear > 0 && yearScore < 0) {
                logTmdbMatch("智能候选过滤：年份差异过大，关键词=%s，候选=%s，站源年份=%d，TMDB年份=%d", keyword, item.getTitle(), sourceYear, tmdbItemYear(item));
                continue;
            }
            int freshnessScore = sourceYear > 0 ? 0 : scoreTmdbFreshness(item);
            int typeScore = scoreTmdbMediaType(item, sourceVod);
            int creditScore = scoreTmdbPeople(item, sourceVod);
            int voteScore = scoreTmdbVote(item);
            int score = titleScore + yearScore + freshnessScore + typeScore + creditScore + voteScore;
            logTmdbMatch("智能候选保留：标题=%s，媒体=%s，年份=%d，标题分=%d，年份分=%d，近期分=%d，类型分=%d，已有职员分=%d，评分分=%d，总分=%d",
                    item.getTitle(), item.getMediaType(), tmdbItemYear(item), titleScore, yearScore, freshnessScore, typeScore, creditScore, voteScore, score);
            candidates.add(new TmdbCandidate(item, titleScore, score, null));
        }
        candidates.sort((a, b) -> Integer.compare(b.score, a.score));
        return candidates;
    }

    private int scoreTmdbSmartTitle(TmdbItem item, String normalizedKeyword) {
        int strict = scoreTmdbTitle(item, normalizedKeyword);
        if (strict > 0) return strict;
        int year = tmdbItemYear(item);
        String cleaned = year > 0 ? removeYearFromTitle(item.getTitle(), year) : cleanTmdbSearchQuery(item.getTitle());
        return normalize(cleaned).equals(normalizedKeyword) ? 280 : 0;
    }

    private int scoreTmdbSmartYear(TmdbItem item, int sourceYear) {
        if (sourceYear <= 0) return 0;
        int itemYear = tmdbItemYear(item);
        if (itemYear <= 0) return -20;
        int diff = Math.abs(itemYear - sourceYear);
        if (diff == 0) return 120;
        if (diff == 1) return 70;
        return -1;
    }

    private int scoreTmdbFreshness(TmdbItem item) {
        int year = tmdbItemYear(item);
        if (year <= 0) return 0;
        int age = LocalDateTime.now().getYear() - year;
        if (age <= 0) return 80;
        if (age == 1) return 70;
        if (age <= 3) return 55;
        if (age <= 5) return 45;
        if (age <= 10) return 30;
        return 15;
    }

    private int scoreTmdbVote(TmdbItem item) {
        Matcher matcher = Pattern.compile("评分\\s*([0-9](?:\\.[0-9])?)").matcher(item.getSubtitle());
        double vote = 0;
        if (matcher.find()) {
            try {
                vote = Double.parseDouble(matcher.group(1));
            } catch (Throwable ignored) {
            }
        }
        if (vote >= 8.0) return 20;
        if (vote >= 7.0) return 12;
        if (vote >= 6.0) return 5;
        return 0;
    }

    private int scoreTmdbCountry(JsonObject detail, @Nullable Vod sourceVod) {
        Set<String> preferred = preferredCountryCodes(sourceVod);
        Set<String> countries = tmdbCountryCodes(detail);
        if (countries.isEmpty()) return 0;
        boolean explicit = hasExplicitCountryPreference(sourceVod);
        for (String code : preferred) if (countries.contains(code)) return explicit ? 120 : 45;
        return explicit ? -60 : 0;
    }

    private Set<String> preferredCountryCodes(@Nullable Vod sourceVod) {
        Set<String> codes = explicitCountryCodes(sourceVod);
        if (!codes.isEmpty()) return codes;
        return Set.of("CN");
    }

    private boolean hasExplicitCountryPreference(@Nullable Vod sourceVod) {
        return !explicitCountryCodes(sourceVod).isEmpty();
    }

    private Set<String> explicitCountryCodes(@Nullable Vod sourceVod) {
        Set<String> codes = new HashSet<>();
        if (sourceVod == null) return codes;
        String text = normalize(sourceVod.getArea() + " " + sourceVod.getTypeName() + " " + sourceVod.getRemarks() + " " + sourceVod.getName());
        addCountryCode(codes, text, "CN", "中国", "中國", "大陆", "大陸", "内地", "內地", "国产", "國產", "华语", "華語");
        addCountryCode(codes, text, "HK", "香港", "港剧", "港劇");
        addCountryCode(codes, text, "TW", "台湾", "台灣", "台剧", "台劇");
        addCountryCode(codes, text, "JP", "日本", "日剧", "日劇", "日影", "日漫");
        addCountryCode(codes, text, "KR", "韩国", "韓國", "韩剧", "韓劇", "韩影", "韓影");
        addCountryCode(codes, text, "US", "美国", "美國", "美剧", "美劇", "欧美", "歐美");
        addCountryCode(codes, text, "GB", "英国", "英國", "英剧", "英劇");
        addCountryCode(codes, text, "TH", "泰国", "泰國", "泰剧", "泰劇");
        addCountryCode(codes, text, "IN", "印度");
        return codes;
    }

    private void addCountryCode(Set<String> codes, String text, String code, String... words) {
        for (String word : words) {
            if (!text.contains(normalize(word))) continue;
            codes.add(code);
            return;
        }
    }

    private Set<String> tmdbCountryCodes(JsonObject detail) {
        Set<String> codes = new HashSet<>();
        for (JsonElement element : array(detail, "origin_country")) {
            if (element.isJsonPrimitive()) codes.add(element.getAsString().toUpperCase(Locale.ROOT));
        }
        for (JsonElement element : array(detail, "production_countries")) {
            if (!element.isJsonObject()) continue;
            JsonObject country = element.getAsJsonObject();
            String code = string(country, "iso_3166_1").toUpperCase(Locale.ROOT);
            if (!TextUtils.isEmpty(code)) codes.add(code);
            String name = normalize(string(country, "name"));
            if (name.contains("china") || name.contains("中国") || name.contains("中國") || name.contains("大陆") || name.contains("大陸") || name.contains("内地") || name.contains("內地")) codes.add("CN");
        }
        return codes;
    }

    private int smartAcceptScore(int sourceYear) {
        return sourceYear > 0 ? 390 : 335;
    }

    private boolean isUniqueNewest(TmdbCandidate best, List<TmdbCandidate> candidates, int sourceYear) {
        if (sourceYear > 0 || best.titleScore < 300) return false;
        int bestYear = tmdbItemYear(best.item);
        if (bestYear <= 0) return false;
        for (TmdbCandidate candidate : candidates) {
            if (candidate == best) continue;
            int year = tmdbItemYear(candidate.item);
            if (year >= bestYear && candidate.score >= best.score - 10) return false;
        }
        return true;
    }

    private int scoreTmdbTitle(TmdbItem item, String normalizedKeyword) {
        // 标题分只给完全一致的结果。这里故意不支持“候选包含关键词”，防止短标题误吸附长标题。
        String title = normalize(item.getTitle());
        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(normalizedKeyword)) return 0;
        if (title.equals(normalizedKeyword)) return 300;
        return 0;
    }

    private int scoreTmdbYear(TmdbItem item, int sourceYear) {
        // 年份只允许同年加分；不同年已经在候选过滤阶段剔除，不做近似年份匹配。
        if (sourceYear <= 0) return 0;
        return tmdbItemYear(item) == sourceYear ? 120 : 0;
    }

    private int tmdbItemYear(TmdbItem item) {
        // 搜索结果里的年份通常在副标题中，例如“剧集 · 2026-05-10 · 评分 9.2”。
        // 如果副标题没有年份，再从标题兜底提取，兼容站源或接口返回的特殊格式。
        int year = firstYear(item.getSubtitle());
        return year > 0 ? year : firstYear(item.getTitle());
    }

    private boolean tmdbSeasonYearMatches(TmdbItem item, int seasonNumber, int sourceYear) {
        if (item == null || seasonNumber <= 0 || sourceYear <= 0 || !"tv".equalsIgnoreCase(item.getMediaType())) return false;
        try {
            JsonObject detail = tmdbService.detail(item, tmdbConfig);
            int seasonYear = tmdbSeasonYear(detail, seasonNumber);
            boolean matched = seasonYear == sourceYear;
            logTmdbMatch("候选季年%s：标题=%s，季=%d，站源年份=%d，TMDB季年份=%d",
                    matched ? "命中" : "未命中", item.getTitle(), seasonNumber, sourceYear, seasonYear);
            return matched;
        } catch (Throwable ignored) {
            logTmdbMatch("候选季年检查失败：标题=%s，季=%d，错误=%s", item.getTitle(), seasonNumber, ignored.getMessage());
            return false;
        }
    }

    private int tmdbSeasonYear(JsonObject detail, int seasonNumber) {
        for (JsonElement element : array(detail, "seasons")) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            if (!object.has("season_number") || object.get("season_number").isJsonNull()) continue;
            if (object.get("season_number").getAsInt() != seasonNumber) continue;
            int year = firstYear(string(object, "air_date"));
            return year > 0 ? year : firstYear(string(object, "name"));
        }
        return 0;
    }

    private int scoreTmdbMediaType(TmdbItem item, @Nullable Vod sourceVod) {
        // 类型只做轻量加减分，不作为放宽条件：标题/年份不一致的候选不会走到这里。
        if (sourceVod == null) return 0;
        String type = normalize(sourceVod.getTypeName() + " " + sourceVod.getRemarks());
        if (TextUtils.isEmpty(type)) return 0;
        boolean tv = type.contains("剧") || type.contains("集") || type.contains("连续") || type.contains("电视剧");
        boolean movie = type.contains("电影") || type.contains("影片");
        if (tv && "tv".equalsIgnoreCase(item.getMediaType())) return 20;
        if (movie && "movie".equalsIgnoreCase(item.getMediaType())) return 20;
        if ((tv && "movie".equalsIgnoreCase(item.getMediaType())) || (movie && "tv".equalsIgnoreCase(item.getMediaType()))) return -30;
        return 0;
    }

    private int scoreTmdbPeople(TmdbItem item, @Nullable Vod sourceVod) {
        // 部分入口会带有 credit 信息，此处只做低权重加分；主要的演员/主创消歧在详情拉取后完成。
        if (sourceVod == null) return 0;
        String text = normalize(sourceVod.getActor() + " " + sourceVod.getDirector() + " " + sourceVod.getContent());
        String credit = normalize(item.getCredit());
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(credit)) return 0;
        return text.contains(credit) || credit.contains(text) ? 80 : 0;
    }

    private int scoreTmdbPeople(JsonObject detail, Vod sourceVod) {
        // 用 TMDB 详情里的演员和主创完整姓名，去站源演员、导演、简介中查找。
        // 命中越多，说明同名候选越可能是同一部作品；但它只负责同名候选排序，不负责扩大候选范围。
        if (sourceVod == null) return 0;
        String source = normalize(sourceVod.getActor() + " " + sourceVod.getDirector() + " " + sourceVod.getContent());
        if (TextUtils.isEmpty(source)) return 0;
        int score = 0;
        int hits = 0;
        for (TmdbPerson person : tmdbService.cast(detail, tmdbConfig)) {
            if (hits >= 3) break;
            String name = normalize(person.getName());
            if (name.length() < 2 || !source.contains(name)) continue;
            logTmdbMatch("演员命中：%s，角色/说明=%s，加分=80", person.getName(), person.getSubtitle());
            score += 80;
            hits++;
        }
        for (TmdbPerson person : tmdbService.creators(detail, tmdbConfig)) {
            if (hits >= 4) break;
            String name = normalize(person.getName());
            if (name.length() < 2 || !source.contains(name)) continue;
            logTmdbMatch("主创命中：%s，职位=%s，加分=70", person.getName(), person.getSubtitle());
            score += 70;
            hits++;
        }
        return score;
    }

    private boolean hasSourcePeople(@Nullable Vod sourceVod) {
        // 没有演员、导演、简介时，无法做可靠的人名消歧，继续弹出手动选择更稳妥。
        if (sourceVod == null) return false;
        return !TextUtils.isEmpty(sourceVod.getActor()) || !TextUtils.isEmpty(sourceVod.getDirector()) || !TextUtils.isEmpty(sourceVod.getContent());
    }

    private String tmdbMatchSourceText(String keyword, @Nullable Vod sourceVod) {
        StringBuilder builder = new StringBuilder(Objects.toString(keyword, ""));
        if (sourceVod != null) {
            builder.append(' ').append(Objects.toString(sourceVod.getName(), ""));
            builder.append(' ').append(Objects.toString(sourceVod.getRemarks(), ""));
        }
        builder.append(' ').append(getNameText());
        return builder.toString();
    }

    private int sourceYear(String keyword, @Nullable Vod sourceVod) {
        // 年份优先取站源详情字段；如果站源没有年份，再从标题或搜索词里提取。
        // 这样网盘/站源标题里带“2026”的情况，也能参与严格同年判断。
        int year = sourceVod == null ? 0 : firstYear(sourceVod.getYear());
        if (year > 0) return year;
        year = sourceVod == null ? 0 : firstYear(sourceVod.getName());
        if (year > 0) return year;
        year = firstYear(getNameText());
        return year > 0 ? year : firstYear(keyword);
    }

    private int sourceSeasonNumber(String keyword, @Nullable Vod sourceVod) {
        int number = sourceVod == null ? -1 : sourceSeasonNumber(sourceVod.getName());
        if (number > 0) return number;
        number = sourceSeasonNumber(getNameText());
        return number > 0 ? number : sourceSeasonNumber(keyword);
    }

    @Nullable
    private SplitYearQuery splitYearQuery(String keyword, @Nullable Vod sourceVod) {
        int year = sourceYear(keyword, sourceVod);
        if (year <= 0) return null;
        List<String> sources = new ArrayList<>();
        if (!TextUtils.isEmpty(keyword)) sources.add(keyword);
        if (sourceVod != null && !TextUtils.isEmpty(sourceVod.getName())) sources.add(sourceVod.getName());
        if (!TextUtils.isEmpty(getNameText())) sources.add(getNameText());
        for (String source : sources) {
            if (firstYear(source) != year) continue;
            String query = removeYearFromTitle(source, year);
            if (TextUtils.isEmpty(query) || normalize(query).equals(normalize(source))) continue;
            logTmdbMatch("标题拆年：原始=%s，标题=%s，年份=%d", source, query, year);
            return new SplitYearQuery(query, year);
        }
        return null;
    }

    private String removeYearFromTitle(String text, int year) {
        String cleaned = Objects.toString(text, "").replaceAll("(?<!\\d)" + year + "(?!\\d)", " ");
        cleaned = cleaned.replaceAll("[\\[【「『(（]\\s*[\\]】」』)）]", " ");
        cleaned = cleaned.replaceAll("[._\\-+]+", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        cleaned = cleaned.replaceAll("^[\\s:：,，.。·|/\\\\]+|[\\s:：,，.。·|/\\\\]+$", "");
        return cleanTmdbSearchQuery(cleaned);
    }

    private int firstYear(String text) {
        // 只识别 1900-2099 的四位年份，避免把集数、清晰度、评分等数字误当年份。
        Matcher matcher = Pattern.compile("(19\\d{2}|20\\d{2})").matcher(Objects.toString(text, ""));
        while (matcher.find()) {
            int year = Integer.parseInt(matcher.group(1));
            if (year >= 1900 && year <= 2099) return year;
        }
        return 0;
    }

    private void logTmdbMatch(String format, Object... args) {
        SpiderDebug.log("tmdb-match", format, args);
    }

    private int firstSeasonNumber(JsonObject detail) {
        JsonArray seasons = array(detail, "seasons");
        int fallback = -1;
        for (JsonElement element : seasons) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            if (!object.has("season_number") || object.get("season_number").isJsonNull()) continue;
            int number = object.get("season_number").getAsInt();
            if (number > 0) return number;
            if (fallback == -1) fallback = number;
        }
        return fallback;
    }

    private Map<Integer, Integer> seasonEpisodeCounts(JsonObject detail) {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        JsonArray seasons = array(detail, "seasons");
        for (JsonElement element : seasons) addSeasonCount(counts, element, true);
        if (counts.isEmpty()) for (JsonElement element : seasons) addSeasonCount(counts, element, false);
        return counts;
    }

    private void addSeasonCount(Map<Integer, Integer> counts, JsonElement element, boolean regularOnly) {
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        if (!object.has("season_number") || object.get("season_number").isJsonNull()) return;
        int number = object.get("season_number").getAsInt();
        if (regularOnly && number <= 0) return;
        int count = object.has("episode_count") && !object.get("episode_count").isJsonNull() ? object.get("episode_count").getAsInt() : 0;
        counts.put(number, count);
    }

    private String normalize(String text) {
        return Objects.toString(text, "").replaceAll("[\\s·•:：\\-_/\\\\|()（）\\[\\]【】]+", "").trim().toLowerCase(Locale.ROOT);
    }

    private String cleanTmdbSearchQuery(String text) {
        String raw = Objects.toString(text, "").trim();
        if (TextUtils.isEmpty(raw)) return "";
        String cleaned = raw;
        // TMDB 搜索词清洗只负责去掉资源站常见附加信息，不改变后面的严格匹配规则。
        // 例如“怪奇物语 第五季 [臻彩]”会搜“怪奇物语”，但候选标题仍必须等于“怪奇物语”。
        cleaned = cleaned.replaceAll("(?i)\\.(mkv|mp4|avi|mov|wmv|flv|rmvb|ts|m2ts)$", "");
        cleaned = removeNoiseBrackets(cleaned);
        cleaned = cleaned.replaceAll("(?i)\\b(S\\d{1,2}|Season\\s*\\d{1,2})\\b", " ");
        cleaned = cleaned.replaceAll("第\\s*[一二三四五六七八九十百零〇两0-9]+\\s*[季部]", " ");
        cleaned = cleaned.replaceAll("第\\s*[一二三四五六七八九十百零〇两0-9]+\\s*[集话話]", " ");
        cleaned = cleaned.replaceAll("(全|共|更新至|更至|连载至|連載至)\\s*[0-9一二三四五六七八九十百零〇]+\\s*[集话話]", " ");
        cleaned = cleaned.replaceAll("(?i)\\b(4K|8K|1080P|2160P|720P|HDR|HDR10|DV|WEB[- ]?DL|BluRay|BDRip|Remux|HEVC|H\\.?265|H\\.?264|x265|x264|AAC|DTS|DDP?5?\\.?1|Atmos|NF|Netflix|AMZN|DSNP)\\b", " ");
        cleaned = cleaned.replaceAll("(国语版|国配版|普通话版|粤语版|台语版|闽南语版|原声版|配音版|中字版|字幕版|台版|台灣版|台湾版|港版|港澳版|大陆版|內地版|内地版|中国版|中國版|泰版|泰国版|泰國版|韩版|韩国版|韓國版|日版|日本版|美版|美国版|美國版|英版|英国版|英國版)", " ");
        cleaned = cleaned.replaceAll("(臻彩|高码|高码率|无水印|无台标|国语|国配|国粤|粤语|中字|字幕|内封|简繁|双语|官中|杜比|合集|全集|完结|未删减|加长版|修复版)", " ");
        cleaned = cleaned.replaceAll("[._\\-+]+", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        cleaned = cleaned.replaceAll("^[\\s:：,，.。·|/\\\\]+|[\\s:：,，.。·|/\\\\]+$", "");
        if (TextUtils.isEmpty(cleaned)) cleaned = raw;
        if (!raw.equals(cleaned)) logTmdbMatch("搜索词清洗：原始=%s，清洗后=%s", raw, cleaned);
        return cleaned;
    }

    private String removeNoiseBrackets(String text) {
        Matcher matcher = Pattern.compile("[\\[【「『(（]([^\\]】」』)）]{1,40})[\\]】」』)）]").matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String value = matcher.group(1);
            matcher.appendReplacement(buffer, isTmdbNoiseTag(value) ? " " : Matcher.quoteReplacement(matcher.group()));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private boolean isTmdbNoiseTag(String text) {
        String value = normalize(text);
        if (TextUtils.isEmpty(value)) return true;
        return value.matches("(?i).*(4k|8k|1080p|2160p|720p|hdr|hdr10|dv|webdl|bluray|bdrip|remux|hevc|h265|h264|x265|x264|aac|dts|ddp|atmos|nf|netflix|amzn|dsnp).*")
                || value.matches(".*(臻彩|高码|高码率|无水印|无台标|国语|国配|国粤|粤语|中字|字幕|内封|简繁|双语|官中|杜比|合集|全集|完结|未删减|加长版|修复版).*")
                || value.matches(".*(国语版|国配版|普通话版|粤语版|台语版|闽南语版|原声版|配音版|中字版|字幕版|台版|台灣版|台湾版|港版|港澳版|大陆版|內地版|内地版|中国版|中國版|泰版|泰国版|泰國版|韩版|韩国版|韓國版|日版|日本版|美版|美国版|美國版|英版|英国版|英國版).*");
    }

    private Flag findInitialFlag(List<Flag> flags) {
        String historyFlag = history != null ? history.getVodFlag() : "";
        for (Flag flag : flags) {
            if (!TextUtils.isEmpty(historyFlag) && historyFlag.equals(flag.getFlag())) return flag;
        }
        return flags.get(0);
    }

    private MaterialButton createChipButton(String text) {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(text);
        button.setCheckable(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setTextColor(getColor(android.R.color.white));
        button.setPadding(24, 12, 24, 12);
        FlexboxLayout.LayoutParams params = new FlexboxLayout.LayoutParams(FlexboxLayout.LayoutParams.WRAP_CONTENT, FlexboxLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 12, 12);
        button.setLayoutParams(params);
        return button;
    }

    private void setChipState(MaterialButton button, boolean selected) {
        ThemeColors colors = lightTheme ? ThemeColors.light() : ThemeColors.dark();
        button.setTextColor(colors.primary);
        button.setBackgroundColor(selected ? colors.chipActive : colors.chip);
        button.setOnFocusChangeListener(null);
        applyChipFocus(button, selected, button.hasFocus(), colors);
        button.setOnFocusChangeListener((view, focused) -> applyChipFocus(button, selected, focused, colors));
    }

    private void applyChipFocus(MaterialButton button, boolean selected, boolean focused, ThemeColors colors) {
        button.setStrokeWidth(ResUtil.dp2px(focused ? FOCUS_STROKE_DP : (selected ? 2 : CHIP_STROKE_DP)));
        button.setStrokeColor(ColorStateList.valueOf(focused ? FOCUS_STROKE : (selected ? colors.accent : colors.line)));
    }

    private void addMetaChip(String text) {
        if (TextUtils.isEmpty(text)) return;
        text = normalizeImdbBrand(text);
        ThemeColors colors = lightTheme ? ThemeColors.light() : ThemeColors.dark();
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextColor(colors.primary);
        chip.setTextSize(11f);
        chip.setPadding(16, 8, 16, 8);
        GradientDrawable background = new GradientDrawable();
        background.setColor(colors.chip);
        background.setCornerRadius(999f);
        background.setStroke(1, colors.line);
        chip.setBackground(background);
        FlexboxLayout.LayoutParams params = new FlexboxLayout.LayoutParams(FlexboxLayout.LayoutParams.WRAP_CONTENT, FlexboxLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 10, 10);
        chip.setLayoutParams(params);
        binding.metaContainer.addView(chip);
    }

    private String normalizeImdbBrand(String text) {
        if (TextUtils.isEmpty(text)) return text;
        if (text.regionMatches(true, 0, "imdb", 0, 4)) return "IMDB" + text.substring(4);
        return text.replace("IMDb", "IMDB");
    }

    private String buildSubtitle() {
        return TmdbDetailLabels.headerSubtitle(releaseDate());
    }

    private String releaseDate() {
        if (matchedTmdbDetail == null) return hasTmdbOverview() ? tmdbItemYear() : vod.getYear();
        return string(matchedTmdbDetail, "first_air_date", "release_date");
    }

    private TmdbItem playbackTmdbItem() {
        if (matchedTmdbItem == null) return null;
        return new TmdbItem(
                matchedTmdbItem.getTmdbId(),
                matchedTmdbItem.getMediaType(),
                TextUtils.isEmpty(vod.getName()) ? matchedTmdbItem.getTitle() : vod.getName(),
                buildSubtitle(),
                displayOverview(),
                TextUtils.isEmpty(matchedTmdbItem.getPosterUrl()) ? vod.getPic() : matchedTmdbItem.getPosterUrl(),
                matchedTmdbItem.getBackdropUrl(),
                matchedTmdbItem.getCredit());
    }

    private Vod playbackTmdbVod() {
        if (vod == null) return null;
        Vod item = new Vod();
        item.setName(playbackHistoryName());
        item.setContent(displayOverview());
        item.setPic(playbackHistoryPic());
        item.setYear(yearLabel());
        item.setArea(coalesce(firstCountry(), vod.getArea()));
        item.setTypeName(coalesce(firstGenre(), vod.getTypeName()));
        item.setDirector(coalesce(firstCrew("Director"), vod.getDirector()));
        item.setActor(coalesce(castNames(), vod.getActor()));
        item.setRemarks(coalesce(getMarkText(), vod.getRemarks()));
        return item;
    }

    private String castNames() {
        List<String> names = new ArrayList<>();
        for (TmdbPerson person : detailCastItems) {
            String name = person.getName();
            if (TextUtils.isEmpty(name) || names.contains(name)) continue;
            names.add(name);
            if (names.size() >= 8) break;
        }
        return TextUtils.join(" / ", names);
    }

    private String yearLabel() {
        String date = releaseDate();
        if (!TextUtils.isEmpty(date) && date.length() >= 4) return date.substring(0, 4);
        return vod == null ? "" : vod.getYear();
    }

    private String metaYear() {
        if (matchedTmdbDetail != null) return yearLabel();
        if (hasTmdbOverview()) return tmdbItemYear();
        return vod == null ? "" : vod.getYear();
    }

    private String ratingLabel() {
        if (matchedTmdbDetail == null || !matchedTmdbDetail.has("vote_average") || matchedTmdbDetail.get("vote_average").isJsonNull()) return "";
        return getString(R.string.detail_score, String.format(Locale.US, "%.1f", matchedTmdbDetail.get("vote_average").getAsDouble()));
    }

    private boolean hasTmdbOverview() {
        return !TextUtils.isEmpty(tmdbOverview());
    }

    private String displayOverview() {
        String overview = tmdbOverview();
        if (TextUtils.isEmpty(overview) && vod != null) overview = vod.getContent();
        return TextUtils.isEmpty(overview) ? "" : overview.trim();
    }

    private String tmdbOverview() {
        String overview = matchedTmdbDetail == null ? "" : tmdbService.translatedOverview(matchedTmdbDetail, tmdbConfig);
        if (TextUtils.isEmpty(overview) && matchedTmdbItem != null) overview = matchedTmdbItem.getOverview();
        return TextUtils.isEmpty(overview) ? "" : overview.trim();
    }

    private String tmdbItemYear() {
        if (matchedTmdbItem == null) return "";
        String subtitle = matchedTmdbItem.getSubtitle();
        return !TextUtils.isEmpty(subtitle) && subtitle.length() >= 4 ? subtitle.substring(0, 4) : "";
    }

    private String certificationLabel() {
        if (matchedTmdbDetail == null) return "";
        boolean tv = matchedTmdbItem != null && "tv".equalsIgnoreCase(matchedTmdbItem.getMediaType());
        JsonArray results = tv ? array(matchedTmdbDetail, "content_ratings", "results") : array(matchedTmdbDetail, "release_dates", "results");
        String region = regionFromLanguage(tmdbConfig == null ? "" : tmdbConfig.getLanguage());
        String value = certificationForRegion(results, region, tv);
        if (TextUtils.isEmpty(value)) value = certificationForRegion(results, "US", tv);
        if (TextUtils.isEmpty(value)) value = firstCertification(results, tv);
        return TmdbDetailLabels.certificationLabel(value);
    }

    private String certificationForRegion(JsonArray results, String region, boolean tv) {
        if (TextUtils.isEmpty(region)) return "";
        for (JsonElement element : results) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            if (!region.equalsIgnoreCase(string(object, "iso_3166_1"))) continue;
            return tv ? string(object, "rating") : firstReleaseCertification(object);
        }
        return "";
    }

    private String firstCertification(JsonArray results, boolean tv) {
        for (JsonElement element : results) {
            if (!element.isJsonObject()) continue;
            String value = tv ? string(element.getAsJsonObject(), "rating") : firstReleaseCertification(element.getAsJsonObject());
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private String firstReleaseCertification(JsonObject object) {
        for (JsonElement release : array(object, "release_dates")) {
            if (!release.isJsonObject()) continue;
            String value = string(release.getAsJsonObject(), "certification");
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private String regionFromLanguage(String language) {
        if (TextUtils.isEmpty(language)) return "";
        int separator = language.indexOf('-');
        return separator >= 0 && separator + 1 < language.length() ? language.substring(separator + 1).toUpperCase(Locale.ROOT) : "";
    }

    private String getMediaTypeLabel() {
        if (matchedTmdbItem == null) return getString(R.string.detail_media_unknown);
        return "tv".equalsIgnoreCase(matchedTmdbItem.getMediaType()) ? getString(R.string.detail_media_tv) : getString(R.string.detail_media_movie);
    }

    private String firstGenre() {
        JsonArray genres = array(matchedTmdbDetail, "genres");
        for (JsonElement element : genres) {
            if (element.isJsonObject()) return string(element.getAsJsonObject(), "name");
        }
        return "";
    }

    private String firstCountry() {
        JsonArray countries = array(matchedTmdbDetail, "production_countries");
        for (JsonElement element : countries) {
            if (element.isJsonObject()) return string(element.getAsJsonObject(), "name");
        }
        JsonArray origins = array(matchedTmdbDetail, "origin_country");
        for (JsonElement element : origins) {
            if (element.isJsonPrimitive()) return element.getAsString();
        }
        return "";
    }

    private String firstCrew(String job) {
        JsonArray crew = array(matchedTmdbDetail, "credits", "crew");
        for (JsonElement element : crew) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            if (job.equalsIgnoreCase(string(object, "job"))) return string(object, "name");
        }
        return "";
    }

    private JsonArray array(JsonObject object, String... keys) {
        JsonElement current = object;
        for (String key : keys) {
            if (current == null || !current.isJsonObject()) return new JsonArray();
            JsonObject currentObject = current.getAsJsonObject();
            if (!currentObject.has(key) || currentObject.get(key).isJsonNull()) return new JsonArray();
            current = currentObject.get(key);
        }
        return current != null && current.isJsonArray() ? current.getAsJsonArray() : new JsonArray();
    }

    private JsonObject object(JsonObject object, String... keys) {
        JsonElement current = object;
        for (String key : keys) {
            if (current == null || !current.isJsonObject()) return null;
            JsonObject currentObject = current.getAsJsonObject();
            if (!currentObject.has(key) || currentObject.get(key).isJsonNull()) return null;
            current = currentObject.get(key);
        }
        return current != null && current.isJsonObject() ? current.getAsJsonObject() : null;
    }

    private String string(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object != null && object.has(key) && !object.get(key).isJsonNull()) {
                String value = object.get(key).getAsString();
                if (!TextUtils.isEmpty(value)) return value.trim();
            }
        }
        return "";
    }

    private String coalesce(String... values) {
        for (String value : values) if (!TextUtils.isEmpty(value)) return value;
        return "";
    }

    private Site getCurrentSite() {
        Site site = vod != null ? vod.getSite() : null;
        if (site != null && !site.isEmpty()) return site;
        Site fallback = VodConfig.get().getSite(getKeyText());
        return fallback.isEmpty() ? null : fallback;
    }

    private static boolean isTmdbSiteEnabled(String key) {
        Site site = VodConfig.get().getSite(key);
        return Setting.isTmdbSiteEnabled(key, site == null ? "" : site.getName());
    }

    private static boolean isShortDramaSiteEnabled(String key) {
        Site site = VodConfig.get().getSite(key);
        return Setting.isShortDramaSiteEnabled(key, site == null ? "" : site.getName());
    }

    private boolean isShortDramaSource() {
        Site site = getCurrentSite();
        return Setting.isShortDramaSiteEnabled(site == null ? getKeyText() : site.getKey(), site == null ? "" : site.getName());
    }

    private String getSiteName() {
        Site site = getCurrentSite();
        return site == null ? getKeyText() : site.getName();
    }

    private String getHistoryKey() {
        return getKeyText() + AppDatabase.SYMBOL + getIdText() + AppDatabase.SYMBOL + VodConfig.getCid();
    }

    private void syncDanmakuCompatHistory() {
        mHistory = history;
    }

    @androidx.annotation.Keep
    private String getKey() {
        return getKeyText();
    }

    @androidx.annotation.Keep
    private String getId() {
        return getIdText();
    }

    @androidx.annotation.Keep
    private String getName() {
        return getNameText();
    }

    @androidx.annotation.Keep
    private String getPic() {
        return getPicText();
    }

    @androidx.annotation.Keep
    private String getMark() {
        return getMarkText();
    }

    @androidx.annotation.Keep
    private Site getSite() {
        Site site = getCurrentSite();
        return site == null ? new Site() : site;
    }

    @androidx.annotation.Keep
    private Flag getFlag() {
        return selectedFlag == null ? new Flag() : selectedFlag;
    }

    @androidx.annotation.Keep
    private Episode getEpisode() {
        return selectedEpisode == null ? new Episode() : selectedEpisode;
    }

    @androidx.annotation.Keep
    private History getHistory() {
        return mHistory == null ? history : mHistory;
    }

    private String getKeyText() {
        return Objects.toString(getIntent().getStringExtra("key"), "");
    }

    private String getIdText() {
        return Objects.toString(getIntent().getStringExtra("id"), "");
    }

    private String getNameText() {
        return Objects.toString(getIntent().getStringExtra("name"), "");
    }

    private String getPicText() {
        return Objects.toString(getIntent().getStringExtra("pic"), "");
    }

    private String getTmdbPosterText() {
        return TmdbImageSelector.originalUrl(Objects.toString(getIntent().getStringExtra("tmdb_poster"), ""));
    }

    private String getBackdropText() {
        return Objects.toString(getIntent().getStringExtra("tmdb_backdrop"), "");
    }

    private String getMarkText() {
        return Objects.toString(getIntent().getStringExtra("mark"), "");
    }

    private TmdbItem getIntentTmdbItem() {
        int tmdbId = getIntent().getIntExtra("tmdb_id", 0);
        String mediaType = Objects.toString(getIntent().getStringExtra("tmdb_media_type"), "");
        if (tmdbId <= 0 || TextUtils.isEmpty(mediaType)) return null;
        return new TmdbItem(
                tmdbId,
                mediaType,
                Objects.toString(getIntent().getStringExtra("tmdb_title"), ""),
                Objects.toString(getIntent().getStringExtra("tmdb_subtitle"), ""),
                Objects.toString(getIntent().getStringExtra("tmdb_overview"), ""),
                Objects.toString(getIntent().getStringExtra("tmdb_poster"), ""),
                Objects.toString(getIntent().getStringExtra("tmdb_backdrop"), ""),
                Objects.toString(getIntent().getStringExtra("tmdb_credit"), ""));
    }

    private record TmdbBundle(TmdbItem item, JsonObject detail, List<TmdbPerson> cast, List<TmdbPerson> creators, List<String> photos, List<TmdbItem> related, List<Integer> seasons, Map<Integer, Integer> seasonCounts, Map<Integer, List<TmdbEpisode>> seasonEpisodes, Map<Integer, List<TmdbPerson>> seasonCast, Map<Integer, List<String>> seasonPhotos) {
    }

    private record TmdbLoadResult(TmdbBundle bundle, List<TmdbItem> searchItems) {
    }

    private record SplitYearQuery(String query, int year) {
    }

    private record SplitYearSearch(String query, int year, List<TmdbItem> items) {
    }

    private record EpisodePosition(int season, int number) {
    }

    private record SourceMatch(Site site, Vod vod, int score) {
    }

    private record TmdbCandidate(TmdbItem item, int titleScore, int score, @Nullable TmdbBundle bundle) {
    }

    private record ThemeColors(int background, int panel, int control, int chip, int chipActive, int line, int lineStrong, int primary, int secondary, int muted, int body, int accent, int play, int backdropShade) {

        static ThemeColors dark() {
            return new ThemeColors(
                    0xFF0F141A,
                    0xA6141B23,
                    0xFF2B3743,
                    0x332B3743,
                    0x6630A86B,
                    0x26FFFFFF,
                    0x4DFFFFFF,
                    0xFFFFFFFF,
                    0xCCFFFFFF,
                    0x99FFFFFF,
                    0xE6FFFFFF,
                    0xFF7EE7A2,
                    0xFF2CC56F,
                    0x180F141A
            );
        }

        static ThemeColors light() {
            return new ThemeColors(
                    0xFFF3F6F9,
                    0xAFFFFFFF,
                    0xFFE7EDF3,
                    0xFFEAF0F5,
                    0xFFE5F7EC,
                    0x33424B57,
                    0x66424B57,
                    0xFF12202D,
                    0xCC12202D,
                    0x9912202D,
                    0xE612202D,
                    0xFF1D8F5A,
                    0xFF20B866,
                    0x00F7FAFC
            );
        }

        static ThemeColors cinema(boolean light) {
            TmdbCinemaTheme.Palette palette = TmdbCinemaTheme.palette(light);
            return new ThemeColors(
                    palette.background(),
                    palette.panel(),
                    palette.control(),
                    palette.chip(),
                    palette.chipActive(),
                    palette.line(),
                    palette.lineStrong(),
                    palette.primary(),
                    palette.secondary(),
                    palette.muted(),
                    palette.body(),
                    palette.accent(),
                    palette.play(),
                    palette.backdropShade()
            );
        }
    }
}
