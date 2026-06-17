package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.CustomTarget;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.playback.PlaybackEventCollector;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SiteHealthStore;
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
import com.fongmi.android.tv.ui.dialog.ContentDialog;
import com.fongmi.android.tv.ui.dialog.DanmakuDialog;
import com.fongmi.android.tv.ui.dialog.DisplayDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleDialog;
import com.fongmi.android.tv.ui.dialog.TitleDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Traffic;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.crawler.SpiderDebug;
import com.github.bassaer.library.MDColor;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class VideoActivity extends PlaybackActivity implements CustomKeyDownVod.Listener, TrackDialog.Listener, ArrayAdapter.OnClickListener, FlagAdapter.OnClickListener, EpisodeAdapter.OnClickListener, QualityAdapter.OnClickListener, QuickAdapter.OnClickListener, ParseAdapter.OnClickListener, Clock.Callback {

    private static final int SHORT_DRAMA_SCALE = 4;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault());

    private ActivityVideoBinding mBinding;
    private ViewGroup.LayoutParams mFrameParams;
    private Observer<Result> mObserveDetail;
    private Observer<Result> mObservePlayer;
    private Observer<Result> mObserveSearch;
    private EpisodeAdapter mEpisodeAdapter;
    private QualityAdapter mQualityAdapter;
    private ArrayAdapter mArrayAdapter;
    private ParseAdapter mParseAdapter;
    private QuickAdapter mQuickAdapter;
    private FlagAdapter mFlagAdapter;
    private PartAdapter mPartAdapter;
    private BackdropAdapter mBackdropAdapter;
    private CustomKeyDownVod mKeyDown;
    private SiteViewModel mViewModel;
    private List<String> mBroken;
    private History mHistory;
    private boolean fullscreen;
    private boolean initAuto;
    private boolean autoMode;
    private boolean useParse;
    private boolean detailRequested;
    private boolean detailHealthRecorded;
    private boolean playHealthRecorded;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;

    // TMDB 模式相关字段
    private com.fongmi.android.tv.ui.helper.TmdbUIAdapter mTmdbUIAdapter;
    private com.fongmi.android.tv.ui.custom.TmdbHeaderView mTmdbHeaderView;
    private Runnable mR4;
    private Runnable mBackdropRunnable;
    private int mCurrentBackdropPage = 0;
    private Clock mClock;
    private View mFocus1;
    private View mFocus2;
    private Result mPendingDetail;
    private Result mPendingPlayer;
    private String mContextWallUrl;
    private String mContextWallLockedUrl;
    private String playHealthKey;
    private long detailStartTime;
    private long playerStartTime;

    public static void push(FragmentActivity activity, String text) {
        if (FileChooser.isValid(activity, Uri.parse(text))) file(activity, FileChooser.getPathFromUri(Uri.parse(text)));
        else start(activity, Sniffer.getUrl(text));
    }

    public static void file(FragmentActivity activity, String path) {
        if (TextUtils.isEmpty(path)) return;
        String name = new File(path).getName();
        start(activity, SiteApi.PUSH, "file://" + path, name);
    }

    public static void cast(Activity activity, History history) {
        start(activity, history.getSiteKey(), history.getVodId(), history.getVodName(), history.getVodPic(), null, false, true);
    }

    public static void collect(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null, true, false);
    }

    public static void collect(Activity activity, String key, String id, String name, String pic, String wallPic) {
        start(activity, key, id, name, pic, null, true, false, null, wallPic);
    }

    public static void start(Activity activity, String url) {
        if (dispatchToContentHandler(activity, url)) return;
        start(activity, SiteApi.PUSH, url, url);
    }

    private static boolean dispatchToContentHandler(Activity activity, String url) {
        return com.fongmi.android.tv.content.ContentDispatcher.dispatchUrl(activity, url, "");
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
        start(activity, key, id, name, pic, mark, false, false, null, wallPic);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast) {
        start(activity, key, id, name, pic, mark, collect, cast, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast, com.fongmi.android.tv.bean.TmdbItem tmdbItem) {
        start(activity, key, id, name, pic, mark, collect, cast, tmdbItem, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast, com.fongmi.android.tv.bean.TmdbItem tmdbItem, String wallPic) {
        long launch = System.currentTimeMillis();
        SpiderDebug.log("video-flow", "launch request key=%s id=%s name=%s collect=%s cast=%s", key, id, name, collect, cast);
        ImgUtil.preload(activity, pic);
        if (Setting.isPlaybackArtworkWall() && !TextUtils.isEmpty(wallPic) && !TextUtils.equals(wallPic, pic)) ImgUtil.preload(activity, wallPic);
        if (dispatchToContentHandler(activity, key, id, name, pic, mark, cast)) {
            SpiderDebug.log("video-flow", "dispatched to content handler key=%s", key);
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
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        activity.startActivity(intent);
        SpiderDebug.log("video-flow", "launch dispatched cost=%dms key=%s id=%s", System.currentTimeMillis() - launch, key, id);
    }

    public static void startWithTmdb(Activity activity, String key, String id, String name, String pic, String mark, com.fongmi.android.tv.bean.TmdbItem tmdbItem) {
        start(activity, key, id, name, pic, mark, false, false, tmdbItem);
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

    private String getWallPic() {
        return Objects.toString(getIntent().getStringExtra("wallPic"), "");
    }

    private String getMark() {
        return Objects.toString(getIntent().getStringExtra("mark"), "");
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

    private com.fongmi.android.tv.bean.TmdbItem getTmdbItem() {
        return (com.fongmi.android.tv.bean.TmdbItem) getIntent().getSerializableExtra("tmdbItem");
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
        String oldId = getId();
        super.onNewIntent(intent);
        String id = Objects.toString(intent.getStringExtra("id"), "");
        if (TextUtils.isEmpty(id) || id.equals(oldId)) return;
        getIntent().putExtras(intent);
        saveHistory();
        checkId();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        long start = System.currentTimeMillis();
        SpiderDebug.log("video-flow", "initView start sinceLaunch=%dms key=%s id=%s", getLaunchCost(start), getKey(), getId());
        if (!isCast() && hasInitialPreview()) showInitialPreview();
        super.initView(savedInstanceState);
        SpiderDebug.log("video-flow", "initView after playback cost=%dms", System.currentTimeMillis() - start);
        mFrameParams = mBinding.video.getLayoutParams();
        mClock = Clock.create(mBinding.widget.clock);
        mClock.start();
        mKeyDown = CustomKeyDownVod.create(this);
        mObserveDetail = this::setDetail;
        mObservePlayer = this::setPlayer;
        mObserveSearch = this::setSearch;
        mBroken = new ArrayList<>();
        mR1 = this::hideControl;
        mR2 = this::updateFocus;
        mR3 = this::setTraffic;
        mR4 = this::showEmpty;
        SpiderDebug.log("video-flow", "initView state ready cost=%dms", System.currentTimeMillis() - start);
        checkCast();
        SpiderDebug.log("video-flow", "initView preview ready cost=%dms", System.currentTimeMillis() - start);
        setRecyclerView();
        SpiderDebug.log("video-flow", "initView recycler ready cost=%dms", System.currentTimeMillis() - start);
        setVideoView();
        SpiderDebug.log("video-flow", "initView video view ready cost=%dms", System.currentTimeMillis() - start);
        setViewModel();
        initTmdbMode();
        checkId();
        SpiderDebug.log("video-flow", "initView end cost=%dms sinceLaunch=%dms", System.currentTimeMillis() - start, getLaunchCost(System.currentTimeMillis()));
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.keep.setOnClickListener(view -> onKeep());
        mBinding.video.setOnClickListener(view -> onVideo());
        mBinding.change1.setOnClickListener(view -> onChange());
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
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.reset.setOnClickListener(view -> onReset());
        mBinding.control.action.title.setOnClickListener(view -> onTitle());
        mBinding.control.action.display.setOnClickListener(view -> onDisplay());
        mBinding.control.action.player.setOnClickListener(view -> onChoose());
        mBinding.control.action.decode.setOnClickListener(view -> onDecode());
        mBinding.control.action.ending.setOnClickListener(view -> onEnding());
        mBinding.control.action.repeat.setOnClickListener(view -> onRepeat());
        mBinding.control.action.change2.setOnClickListener(view -> onChange());
        mBinding.control.action.fullscreen.setOnClickListener(view -> onFullscreen());
        mBinding.control.action.danmaku.setOnClickListener(view -> onDanmaku());
        mBinding.control.action.opening.setOnClickListener(view -> onOpening());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.action.reset.setOnLongClickListener(view -> onResetToggle());
        mBinding.control.action.ending.setOnLongClickListener(view -> onEndingReset());
        mBinding.control.action.opening.setOnLongClickListener(view -> onOpeningReset());
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
        mBinding.array.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mEpisodeAdapter.getItemCount() > 40 && position > 1) scrollToEpisode(mArrayAdapter.getStart(position));
            }
        });
    }

    private void setRecyclerView() {
        mBinding.flag.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.flag.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.flag.setAdapter(mFlagAdapter = new FlagAdapter(this));
        int episodeColumn = getEpisodeColumn();
        mBinding.episode.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.episode.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.episode.setAdapter(mEpisodeAdapter = new EpisodeAdapter(this, this::onEpisodeLongClick));
        mEpisodeAdapter.setColumn(1); // 横向滚动，固定1列
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
    }

    private void setVideoView() {
        mBinding.control.action.danmaku.setVisibility(DanmakuSetting.isLoad() ? View.VISIBLE : View.GONE);
        mBinding.control.action.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
        setPlayer();
    }

    private void setPlayer() {
        mBinding.control.action.player.setText(service() == null ? ResUtil.getStringArray(R.array.select_player)[PlayerSetting.getPlayer()] : player().getPlayerText());
    }

    private int getEpisodeColumn() {
        return mEpisodeAdapter == null ? 8 : EpisodeAdapter.getColumn(mEpisodeAdapter.getItems());
    }

    private void setDecode() {
        mBinding.control.action.decode.setText(player().getDecodeText());
    }

    private void setScale(int scale) {
        mHistory.setScale(scale);
        mBinding.exo.setResizeMode(scale);
        mBinding.control.action.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observeForever(mObserveDetail);
        mViewModel.getPlayer().observeForever(mObservePlayer);
        mViewModel.getSearch().observeForever(mObserveSearch);
    }

    private void checkCast() {
        if (isCast() && !isFullscreen()) enterFullscreen();
        else if (hasInitialPreview()) showInitialPreview();
        else mBinding.progressLayout.showProgress();
    }

    private void checkId() {
        if (detailRequested) return;
        detailRequested = true;
        if (getId().startsWith("push://")) getIntent().putExtra("key", SiteApi.PUSH).putExtra("id", getId().substring(7));
        if (getId().isEmpty() || getId().startsWith("msearch:")) setEmpty(false);
        else getDetail();
    }

    private void getDetail() {
        detailStartTime = System.currentTimeMillis();
        detailHealthRecorded = false;
        SpiderDebug.log("video-flow", "detail start key=%s id=%s name=%s", getKey(), getId(), getName());
        mViewModel.detailContent(getKey(), getId());
    }

    private void getDetail(Vod item) {
        getIntent().putExtra("key", item.getSiteKey());
        getIntent().putExtra("pic", item.getPic());
        getIntent().putExtra("id", item.getId());
        mBinding.scroll.scrollTo(0, 0);
        mClock.setCallback(null);
        updateNavigationKey();
        player().reset();
        player().stop();
        saveHistory();
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
        item.checkPic(getPic());
        item.checkName(getName());
        mBinding.progressLayout.showContent();
        mBinding.name.setText(item.getName());
        mFlagAdapter.addAll(item.getFlags());
        mBinding.video.requestFocus();
        App.removeCallbacks(mR4);
        checkHistory(item);
        checkFlag(item);
        checkKeepImg();
        setText(item);
        updateKeep();

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

    private void getPlayer(Flag flag, Episode episode) {
        mBinding.widget.title.setText(getString(R.string.detail_title, mBinding.name.getText(), episode.getName()));
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
        mQualityAdapter.addAll(result);
        setUseParse(result.shouldUseParse());
        setQualityVisible(result.getUrl().isMulti());
        result.getUrl().set(mQualityAdapter.getPosition());
        if (result.hasArtwork()) setArtwork(result.getArtwork());
        if (result.hasDesc()) mBinding.content.setTag(result.getDesc());
        if (result.hasPosition()) mHistory.setPosition(result.getPosition());
        mBinding.control.parse.setVisibility(isUseParse() ? View.VISIBLE : View.GONE);
        if (redirectToContentHandler(result)) return;
        startPlayer(getHistoryKey(), result, isUseParse(), getSite().getTimeout(), buildMetadata());
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
        mBinding.episode.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.control.action.episodes.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);

        // 先添加数据，让适配器检测是否有TMDB数据
        mEpisodeAdapter.addAll(items);

        // 横向滚动，所有模式都使用1列
        mEpisodeAdapter.setColumn(1);

        setArrayAdapter(items.size());
        updateFocus();
        if (scrollToCurrent) scrollToCurrentEpisode();
        setR2Callback();
        // 延迟刷新一次，确保焦点状态正确初始化
        mBinding.episode.post(() -> {
            if (mEpisodeAdapter != null) mEpisodeAdapter.notifyDataSetChanged();
        });
    }

    private void seamless(Flag flag) {
        Episode episode = flag.find(mHistory.getVodRemarks(), getMark().isEmpty());
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
        com.fongmi.android.tv.ui.dialog.EpisodeDetailDialog.show(this, item);
    }

    private void selectEpisode(Episode item, boolean scrollToEpisode) {
        int oldPosition = mEpisodeAdapter.getSelectedPosition();
        mFlagAdapter.toggle(item);
        int newPosition = mEpisodeAdapter.indexOf(item);
        if (newPosition == RecyclerView.NO_POSITION) newPosition = mEpisodeAdapter.getSelectedPosition();
        mEpisodeAdapter.notifySelectionChanged(oldPosition, newPosition);
        SpiderDebug.log("video-episode", "select old=%s new=%s focus=%s scroll=%s name=%s", oldPosition, newPosition, mBinding.episode.hasFocus(), scrollToEpisode, item.getName());
        if (scrollToEpisode && !mBinding.episode.hasFocus()) scrollToEpisode(newPosition);
        if (isFullscreen()) Notify.show(getString(R.string.play_ready, item.getName()));
        onRefresh();
    }

    private void setQualityVisible(boolean visible) {
        mBinding.quality.setVisibility(visible ? View.VISIBLE : View.GONE);
        updateFocus();
        updateEpisodeWindow();
        setR2Callback();
    }

    @Override
    public void onItemClick(Result result) {
        beginPlayHealth();
        startPlayer(getHistoryKey(), result, isUseParse(), getSite().getTimeout(), buildMetadata());
    }

    private void reverseEpisode(boolean scroll) {
        mFlagAdapter.reverse();
        setEpisodeAdapter(getFlag().getEpisodes(), scroll);
        if (scroll) scrollToCurrentEpisode();
        else scrollToFirstEpisode();
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
        mBinding.episode.post(() -> {
            updateEpisodeWindowNow();
            mBinding.episode.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                mBinding.episode.setSelectedPosition(position);
                if (requestFocus) mBinding.episode.requestFocus();
            });
        });
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
        List<String> items = new ArrayList<>();
        items.add(getString(R.string.play_reverse));
        items.add(getString(mHistory.getRevPlayText()));
        mBinding.array.setVisibility(size > 1 ? View.VISIBLE : View.GONE);
        if (mHistory.isRevSort()) for (int i = size; i > 0; i -= 40) items.add(i + "-" + Math.max(i - 39, 1));
        else for (int i = 0; i < size; i += 40) items.add((i + 1) + "-" + Math.min(i + 40, size));
        mArrayAdapter.addAll(items);
        updateFocus();
    }

    private int findFocusDown(int index) {
        List<Integer> orders = Arrays.asList(R.id.flag, R.id.quality, R.id.array, R.id.episode);
        for (int i = 0; i < orders.size(); i++) if (i > index) if (isVisible(findViewById(orders.get(i)))) return orders.get(i);
        return 0;
    }

    private int findFocusUp(int index) {
        List<Integer> orders = Arrays.asList(R.id.flag, R.id.quality, R.id.array, R.id.episode);
        for (int i = orders.size() - 1; i >= 0; i--) if (i < index) if (isVisible(findViewById(orders.get(i)))) return orders.get(i);
        return 0;
    }

    private void updateFocus() {
        mArrayAdapter.setNextFocus(findFocusUp(2), findFocusDown(2));
        mEpisodeAdapter.setNextFocusUp(findFocusUp(3));
        mFlagAdapter.setNextFocusDown(findFocusDown(0));
        mEpisodeAdapter.setNextFocusDown(findFocusDown(3));
    }

    private boolean onEpisodeKey(KeyEvent event) {
        if (!KeyUtil.isActionDown(event) || !KeyUtil.isUpKey(event)) return false;
        RecyclerView.ViewHolder holder = mBinding.episode.findContainingViewHolder(getCurrentFocus());
        if (holder == null) return false;
        int position = holder.getBindingAdapterPosition();
        int column = Math.max(1, EpisodeAdapter.getColumn(mEpisodeAdapter.getItems()));
        if (position == RecyclerView.NO_POSITION || position >= column) return false;
        int target = findFocusUp(3);
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
        if (isFullscreen()) exitFullscreen();
        else enterFullscreen();
        showControl(mBinding.control.action.fullscreen);
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

    private void onSpeed() {
        mBinding.control.action.speed.setText(player().addSpeed());
        mHistory.setSpeed(player().getSpeed());
    }

    private void onSpeedAdd() {
        mBinding.control.action.speed.setText(player().addSpeed(0.25f));
        mHistory.setSpeed(player().getSpeed());
    }

    private void onSpeedSub() {
        mBinding.control.action.speed.setText(player().subSpeed(0.25f));
        mHistory.setSpeed(player().getSpeed());
    }

    private boolean onSpeedLong() {
        mBinding.control.action.speed.setText(player().toggleSpeed());
        mHistory.setSpeed(player().getSpeed());
        return true;
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
        String[] items = new String[]{"EXO", "IJK", "外调"};
        new androidx.appcompat.app.AlertDialog.Builder(this).setItems(items, (dialog, which) -> {
            if (which == 0) {
                player().switchPlayer(PlayerSetting.EXO);
                setPlayer();
            } else if (which == 1) {
                player().switchPlayer(PlayerSetting.IJK);
                setPlayer();
            } else {
                PlayerHelper.choose(this, player().getUrl(), player().getHeaders(), player().isVod(), player().getPosition(), mBinding.widget.title.getText());
                setRedirect(true);
            }
        }).show();
    }

    private void onDecode() {
        mClock.setCallback(null);
        player().toggleDecode();
        setDecode();
    }

    private void onTrack(View view) {
        TrackDialog.create().type(Integer.parseInt(view.getTag().toString())).player(player()).show(this);
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

    private void onDisplay() {
        DisplayDialog.show(this, this::updateDisplaySettings);
    }

    private void onEpisodes() {
        EpisodeDialog.create().episodes(mEpisodeAdapter.getItems()).reverseAction(this::onRevSort).show(this);
    }

    private void showProgress() {
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
        updateProgressPanel();
        updateDisplayPanel();
        App.post(mR3, 0);
        hideCenter();
        hideError();
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR3);
        Traffic.reset();
        updateDisplayPanel();
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
        updateDisplayPanel();
    }

    private void showTopInfo() {
        mBinding.widget.top.setVisibility(View.VISIBLE);
        mBinding.widget.size.setText(player().getSizeText());
    }

    private void hideInfo() {
        mBinding.widget.top.setVisibility(View.GONE);
        mBinding.widget.center.setVisibility(View.GONE);
        updateDisplayPanel();
    }

    private void showControl(View view) {
        showTopInfo();
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        view.requestFocus();
        setR1Callback();
        updateDisplayPanel();
    }

    private void hideControl() {
        mBinding.control.getRoot().setVisibility(View.GONE);
        if (player().isPlaying()) mBinding.widget.top.setVisibility(View.GONE);
        App.removeCallbacks(mR1);
        updateDisplayPanel();
    }

    private void hideCenter() {
        mBinding.widget.action.setImageResource(R.drawable.ic_widget_play);
        mBinding.widget.center.setVisibility(View.GONE);
        if (isGone(mBinding.control.getRoot())) mBinding.widget.top.setVisibility(View.GONE);
    }

    private void setTraffic() {
        updateProgressPanel();
        if (PlayerSetting.isDisplayTraffic()) Traffic.setSpeed(mBinding.progress.traffic);
        App.post(mR3, 1000);
    }

    private void updateDisplaySettings() {
        updateProgressPanel();
        updateDisplayPanel();
    }

    private void updateProgressPanel() {
        boolean hasPlayer = service() != null && player() != null && !player().isEmpty();
        boolean showTitle = PlayerSetting.isDisplayTitle() && !TextUtils.isEmpty(mBinding.widget.title.getText());
        boolean showSize = hasPlayer && PlayerSetting.isDisplaySize() && !TextUtils.isEmpty(player().getSizeText());
        mBinding.progress.title.setText(mBinding.widget.title.getText());
        mBinding.progress.size.setText(showSize ? player().getSizeText() : "");
        mBinding.progress.title.setVisibility(showTitle ? View.VISIBLE : View.GONE);
        mBinding.progress.size.setVisibility(showSize ? View.VISIBLE : View.GONE);
        mBinding.progress.topLeft.setVisibility(showTitle || showSize ? View.VISIBLE : View.GONE);
        mBinding.progress.clock.setText(TIME_FORMAT.format(LocalDateTime.now()));
        mBinding.progress.clock.setVisibility(PlayerSetting.isDisplayTime() ? View.VISIBLE : View.GONE);
        mBinding.progress.bottomProgress.setVisibility(hasPlayer && PlayerSetting.isDisplayProgress() ? View.VISIBLE : View.GONE);
        mBinding.progress.traffic.setVisibility(PlayerSetting.isDisplayTraffic() ? View.VISIBLE : View.GONE);
        if (!hasPlayer) return;
        long position = Math.max(0, player().getPosition());
        long duration = Math.max(0, player().getDuration());
        mBinding.progress.position.setText(player().getPositionTime(0) + "/" + player().getDurationTime());
        mBinding.progress.bar.setProgress(duration > 0 ? (int) (position * mBinding.progress.bar.getMax() / duration) : 0);
    }

    private void updateDisplayPanel() {
        boolean hasPlayer = service() != null && player() != null && !player().isEmpty();
        boolean canShow = hasPlayer && isGone(mBinding.control.getRoot()) && isGone(mBinding.progress.getRoot()) && isGone(mBinding.widget.center) && isGone(mBinding.widget.error);
        boolean showTitle = canShow && PlayerSetting.isDisplayTitle() && !TextUtils.isEmpty(mBinding.widget.title.getText());
        boolean showSize = canShow && PlayerSetting.isDisplaySize() && !TextUtils.isEmpty(player().getSizeText());
        boolean showProgress = canShow && PlayerSetting.isDisplayProgress() && player().getDuration() > 0;
        boolean showMini = !showProgress && canShow && PlayerSetting.isDisplayMini() && player().getDuration() > 0;
        mBinding.widget.displayTitle.setText(mBinding.widget.title.getText());
        mBinding.widget.displaySize.setText(showSize ? player().getSizeText() : "");
        mBinding.widget.displayTitle.setVisibility(showTitle ? View.VISIBLE : View.GONE);
        mBinding.widget.displaySize.setVisibility(showSize ? View.VISIBLE : View.GONE);
        mBinding.widget.displayTopLeft.setVisibility(showTitle || showSize ? View.VISIBLE : View.GONE);
        mBinding.widget.displayClock.setText(TIME_FORMAT.format(LocalDateTime.now()));
        mBinding.widget.displayClock.setVisibility(canShow && PlayerSetting.isDisplayTime() ? View.VISIBLE : View.GONE);
        if (canShow && PlayerSetting.isDisplayTraffic()) Traffic.setSpeed(mBinding.widget.displayTraffic);
        else mBinding.widget.displayTraffic.setVisibility(View.GONE);
        mBinding.widget.displayBottomProgress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        mBinding.widget.displayMini.setVisibility(showMini ? View.VISIBLE : View.GONE);
        if (!showProgress && !showMini) return;
        long duration = Math.max(0, player().getDuration());
        long position = Math.max(0, Math.min(player().getPosition(), duration));
        int progress = duration > 0 ? (int) (position * mBinding.widget.displayBar.getMax() / duration) : 0;
        mBinding.widget.displayPosition.setText(player().getPositionTime(0) + "/" + player().getDurationTime());
        mBinding.widget.displayBar.setProgress(progress);
        mBinding.widget.displayMini.setProgress(progress);
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
        return getWallPic();
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
        if (!TextUtils.isEmpty(getMark())) mHistory.setVodRemarks(getMark());
        if (Setting.isIncognito() && mHistory.getKey().equals(getHistoryKey())) mHistory.delete();
        mBinding.control.action.opening.setText(mHistory.getOpening() <= 0 ? getString(R.string.play_op) : Util.timeMs(mHistory.getOpening()));
        mBinding.control.action.ending.setText(mHistory.getEnding() <= 0 ? getString(R.string.play_ed) : Util.timeMs(mHistory.getEnding()));
        mBinding.control.action.speed.setText(player().setSpeed(mHistory.getSpeed()));
        mHistory.setVodName(item.getName());
        PlaybackEventCollector.get().updateHistory(mHistory);
        setArtwork(item.getPic());
        setScale(getScale());
        setPartAdapter();
    }

    private boolean hasInitialPreview() {
        return !getName().isEmpty() || !getPic().isEmpty() || !getWallPic().isEmpty();
    }

    private void showInitialPreview() {
        mBinding.progressLayout.showContent();
        mBinding.name.setText(getName());
        if (!getPic().isEmpty()) setArtwork(getPic());
        else if (!getWallPic().isEmpty()) setContextWall(getWallPic());
        mBinding.video.requestFocus();
    }

    private History createHistory(Vod item) {
        History history = new History();
        history.setKey(getHistoryKey());
        history.setCid(VodConfig.getCid());
        history.setVodName(item.getName());
        history.findEpisode(item.getFlags());
        return history;
    }

    private void saveHistory() {
        saveHistory(false);
    }

    private void saveHistory(boolean exit) {
        android.util.Log.d("VideoActivity", "saveHistory: exit=" + exit + " mHistory=" + (mHistory != null) +
            " canSave=" + (mHistory != null ? mHistory.canSave() : "null") +
            " incognito=" + Setting.isIncognito());
        if (mHistory == null || Setting.isIncognito()) return;
        if (exit && isOwner()) updatePlaybackHistoryPosition();
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
        boolean sameEpisode = item.matchesName(mHistory.getEpisode());
        boolean sameFlag = TextUtils.equals(mHistory.getVodFlag(), getFlag().getFlag());
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
        boolean id = !item.getId().isEmpty();
        boolean pic = !item.getPic().isEmpty();
        boolean name = !item.getName().isEmpty();
        if (id) getIntent().putExtra("id", item.getId());
        if (id) mHistory.replace(getHistoryKey());
        if (name) mHistory.setVodName(item.getName());
        if (name) mBinding.name.setText(item.getName());
        if (name) mBinding.widget.title.setText(item.getName());
        updateFlag(getFlag(), item.getFlags());
        if (pic) setArtwork(item.getPic());
        if (pic || name) setMetadata();
        if (pic || name) syncHistory();
        if (pic || name) updateKeep();
        if (id) updateNavigationKey();
        if (name) setPartAdapter();
        PlaybackEventCollector.get().updateHistory(mHistory);
        setText(item);
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
    };

    @Override
    protected String getPlaybackKey() {
        return getHistoryKey();
    }

    @Override
    protected void onPrepare() {
        android.util.Log.d("VideoActivity", "onPrepare: setting Clock callback");
        setDecode();
        setPosition();
        mClock.setCallback(this);
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
        mBinding.widget.size.setText(player().getSizeText());
    }

    @Override
    public void onSubtitleClick() {
        SubtitleDialog.create().view(mBinding.exo.getSubtitleView()).show(this);
        App.post(this::hideControl, 100);
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
        if (mHistory.getEnding() > 0 && duration > 0 && mHistory.getEnding() + position >= duration) {
            checkEnded(false);
        }
    }

    private void updatePlaybackHistoryPosition() {
        if (mHistory == null) return;
        mHistory.setPosition(player().getPosition());
        mHistory.setDuration(player().getDuration());
        PlaybackEventCollector.get().updateHistory(mHistory);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (isRedirect()) return;
        if (event.getType() == RefreshEvent.Type.DETAIL) getDetail();
        else if (event.getType() == RefreshEvent.Type.PLAYER) onRefresh();
        else if (event.getType() == RefreshEvent.Type.VOD) {
            updateVod(event.getVod());
            // 绑定 TMDB 数据到 UI
            bindTmdbData();
        }
        else if (event.getType() == RefreshEvent.Type.SUBTITLE) player().setSub(Sub.from(event.getPath()));
        else if (event.getType() == RefreshEvent.Type.DANMAKU) player().setDanmaku(Danmaku.from(event.getPath()));
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

        // 推荐
        java.util.List<com.fongmi.android.tv.bean.TmdbItem> recommendations = mTmdbUIAdapter.getRecommendations();
        if (!recommendations.isEmpty()) {
            androidx.leanback.widget.ArrayObjectAdapter recommendationsAdapter = new androidx.leanback.widget.ArrayObjectAdapter(
                new com.fongmi.android.tv.ui.presenter.TmdbRecommendationPresenter(this::onTmdbRecommendationClick)
            );
            recommendationsAdapter.addAll(0, recommendations);
            mBinding.tmdbRecommendations.setAdapter(new androidx.leanback.widget.ItemBridgeAdapter(recommendationsAdapter));
            mBinding.tmdbRecommendations.setVisibility(View.VISIBLE);
            View recommendationsLabel = mBinding.getRoot().findViewById(R.id.tmdbRecommendationsLabel);
            if (recommendationsLabel != null) recommendationsLabel.setVisibility(View.VISIBLE);

            // 动态设置上一个Grid的nextFocusDown
            if (lastVisibleGrid != null) {
                lastVisibleGrid.setNextFocusDownId(R.id.tmdbRecommendations);
            }
            lastVisibleGrid = mBinding.tmdbRecommendations;
            if (!hasTmdbContent) hasTmdbContent = true;
        } else {
            mBinding.tmdbRecommendations.setVisibility(View.GONE);
            View recommendationsLabel = mBinding.getRoot().findViewById(R.id.tmdbRecommendationsLabel);
            if (recommendationsLabel != null) recommendationsLabel.setVisibility(View.GONE);
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

        SpiderDebug.log("tmdb-tv", "绑定完成: 演员=%d 剧照=%d 主创=%d 推荐=%d", cast.size(), photos.size(), creators.size(), recommendations.size());

        // 设置背景幻灯片
        setupBackdropSlideshow(photos);
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
        com.fongmi.android.tv.ui.dialog.TmdbPersonDialog.show(this, person);
    }

    private void onTmdbPhotoClick(String url, int position) {
        if (TextUtils.isEmpty(url)) return;
        java.util.List<String> photos = mTmdbUIAdapter.getPhotos();
        com.fongmi.android.tv.ui.dialog.PhotoViewerDialog.show(this, photos, position, null);
    }

    private void onTmdbRecommendationClick(com.fongmi.android.tv.bean.TmdbItem item) {
        if (item == null) return;
        // 搜索并打开推荐内容
        com.fongmi.android.tv.bean.Site site = VodConfig.get().getHome();
        if (site == null || site.isEmpty() || !site.isSearchable()) {
            com.fongmi.android.tv.ui.activity.SearchActivity.start(this, item.getTitle());
            return;
        }
        Notify.show(getString(R.string.detail_work_searching, item.getTitle()));
        com.fongmi.android.tv.utils.Task.execute(() -> {
            com.fongmi.android.tv.bean.Vod match = searchCurrentSite(item.getTitle(), site);
            runOnUiThread(() -> {
                if (match == null) {
                    Notify.show(getString(R.string.detail_work_global_searching, item.getTitle()));
                    com.fongmi.android.tv.ui.activity.SearchActivity.start(this, item.getTitle());
                    return;
                }
                VideoActivity.start(this, site.getKey(), match.getId(), match.getName(), match.getPic(), null);
            });
        });
    }

    private com.fongmi.android.tv.bean.Vod searchCurrentSite(String keyword, com.fongmi.android.tv.bean.Site site) {
        try {
            com.fongmi.android.tv.bean.Result result = SiteApi.searchContent(site, keyword, false, "1");
            return bestVod(result != null ? result.getList() : new java.util.ArrayList<>(), keyword);
        } catch (Throwable e) {
            return null;
        }
    }

    private com.fongmi.android.tv.bean.Vod bestVod(java.util.List<com.fongmi.android.tv.bean.Vod> items, String keyword) {
        if (items == null || items.isEmpty()) return null;
        com.fongmi.android.tv.bean.Vod best = null;
        int score = Integer.MIN_VALUE;
        for (com.fongmi.android.tv.bean.Vod item : items) {
            int current = scoreVod(item, keyword);
            if (current > score) {
                score = current;
                best = item;
            }
        }
        return score > 0 ? best : null;
    }

    private int scoreVod(com.fongmi.android.tv.bean.Vod item, String keyword) {
        if (item == null) return Integer.MIN_VALUE;
        String normalizedKeyword = normalizeTitle(keyword);
        String name = normalizeTitle(item.getName());
        if (name.isEmpty()) return Integer.MIN_VALUE;
        if (name.equals(normalizedKeyword)) return 300;
        if (name.contains(normalizedKeyword) || normalizedKeyword.contains(name)) return 220;
        String remarks = normalizeTitle(item.getRemarks());
        if (!remarks.isEmpty() && (remarks.contains(normalizedKeyword) || normalizedKeyword.contains(remarks))) return 120;
        return 0;
    }

    private String normalizeTitle(String text) {
        return TextUtils.isEmpty(text) ? "" : text.replaceAll("[\\s·•・._\\-/\\\\|()（）\\[\\]【】《》<>]+", "").trim().toLowerCase(java.util.Locale.ROOT);
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
    }

    private void setTitleVisible() {
        mBinding.control.action.title.setVisibility(player().haveTitle() ? View.VISIBLE : View.GONE);
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
        if (mQuickAdapter.getItemCount() == 0) initSearch(mBinding.name.getText().toString(), true);
        else if (isAutoMode() || force) nextSite();
    }

    private void initSearch(String keyword, boolean auto) {
        setAutoMode(auto);
        setInitAuto(auto);
        startSearch(keyword);
        mBinding.part.setTag(keyword);
    }

    private boolean isPass(Site item) {
        if (isAutoMode() && !item.isChangeable()) return false;
        return item.isSearchable();
    }

    private void startSearch(String keyword) {
        mQuickAdapter.clear();
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
        if (isInitAuto()) nextSite();
        if (items.isEmpty()) return;
        App.removeCallbacks(mR4);
    }

    @Override
    public void onItemClick(Vod item) {
        setAutoMode(false);
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
        mBinding.control.action.fullscreen.setText(fullscreen ? R.string.play_exit_fullscreen : R.string.play_fullscreen);
    }

    private void initTmdbMode() {
        // TMDB 模式：通过全局开关或 Intent 参数启用
        if (!isTmdbMode() && !Setting.isTmdbEnabled()) return;

        mTmdbUIAdapter = new com.fongmi.android.tv.ui.helper.TmdbUIAdapter(this);
        if (!mTmdbUIAdapter.isReady()) {
            SpiderDebug.log("TMDB 增强已启用，但配置未就绪（需要 API Key）");
            return;
        }
        com.fongmi.android.tv.bean.TmdbItem tmdbItem = getTmdbItem();
        if (tmdbItem != null) {
            SpiderDebug.log("TMDB 模式: 使用传入的 TmdbItem");
        }
    }

    private void applyShortDramaMode() {
        Site site = getSite();
        if (!Setting.isShortDramaSiteEnabled(site == null ? getKey() : site.getKey(), site == null ? "" : site.getName())) return;
        if (!isFullscreen()) enterFullscreen();
        setPreviewScale(SHORT_DRAMA_SCALE);
        hideInfo();
    }

    private void setPreviewScale(int scale) {
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        if (scale < 0 || scale >= array.length) return;
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
    }

    @Override
    public void onSpeedEnd() {
        mBinding.widget.speed.clearAnimation();
        mBinding.widget.speed.setVisibility(View.GONE);
        mBinding.control.action.speed.setText(player().setSpeed(mHistory.getSpeed()));
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
        ViewParent parent = focused.getParent();
        if (parent instanceof ViewGroup) {
            ViewGroup parentGroup = (ViewGroup) parent;
            ViewGroup grandParent = parentGroup.getParent() instanceof ViewGroup ? (ViewGroup) parentGroup.getParent() : null;
            if (grandParent != null) {
                int pos = mBinding.episode.getChildAdapterPosition(grandParent);
                if (pos >= 0 && pos < mEpisodeAdapter.getItemCount()) {
                    Episode item = mEpisodeAdapter.getItems().get(pos);
                    onEpisodeLongClick(item);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isDescendantOf(View child, ViewGroup parent) {
        if (child == parent) return true;
        View current = child;
        while (current != null) {
            if (current.getParent() == parent) return true;
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
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
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (PlayerSetting.isBackgroundOff()) mClock.stop();
    }

    @Override
    protected void onBackInvoked() {
        if (isVisible(mBinding.control.getRoot())) {
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
        mClock.release();
        saveHistory(true);
        DanmakuApi.cancel();
        stopBackdropAutoScroll();
        RefreshEvent.keep();
        App.removeCallbacks(mR1, mR2, mR3, mR4);
        mViewModel.getResult().removeObserver(mObserveDetail);
        mViewModel.getPlayer().removeObserver(mObservePlayer);
        mViewModel.getSearch().removeObserver(mObserveSearch);
        SiteHealthStore.flush();
        super.onDestroy();
    }
}
