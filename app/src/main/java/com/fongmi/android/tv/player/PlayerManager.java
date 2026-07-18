package com.fongmi.android.tv.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.MediaEdition;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.effect.ColorLut;
import androidx.media3.ui.danmaku.DanmakuConfig;
import androidx.media3.ui.danmaku.DanmakuController;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.player.engine.ExoPlayerEngine;
import com.fongmi.android.tv.player.engine.IjkPlayerEngine;
import com.fongmi.android.tv.player.engine.MpvPlayerEngine;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.player.engine.PlayerCacheState;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.engine.SystemPlayerEngine;
import com.fongmi.android.tv.player.exo.TrackUtil;
import com.fongmi.android.tv.player.lut.DynamicLutEffect;
import com.fongmi.android.tv.player.lut.LutEffectFactory;
import com.fongmi.android.tv.player.lut.LutEligibility;
import com.fongmi.android.tv.player.lut.LutPreset;
import com.fongmi.android.tv.player.lut.LutSetting;
import com.fongmi.android.tv.player.lut.LutStore;
import com.fongmi.android.tv.player.lut.MpvLutShader;
import com.fongmi.android.tv.player.lut.MpvLutShaderFactory;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.subtitle.RealtimeSubtitleController;
import com.fongmi.android.tv.utils.LocalProxyDebug;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlayerManager implements ParseCallback {

    public static final String RELOAD_LUT_WARMUP = "__webhtv_lut_warmup_reload__";
    static final int FALLBACK_NONE = 0;
    static final int FALLBACK_DECODE = 1;
    static final int FALLBACK_PLAYER = 2;

    private static final long LOCAL_PROXY_READY_TIMEOUT_MS = 5000;
    private static final long LOCAL_PROXY_RETRY_DELAY_MS = 1000;
    private static final long HARD_DECODE_SWITCH_RETRY_DELAY_MS = 1200;
    private static final int LOCAL_PROXY_MAX_RETRY = 2;
    private static final int PLAYER_COUNT = PlayerSetting.MPV + 1;
    private static final int LUT_WARMUP_RECOVERED_ERROR_REFRESH_THRESHOLD = 3;
    private static final long DANMAKU_FORCE_RELOAD_DEBOUNCE_MS = 10000;
    private static final float[] SPEED_PRESETS = new float[]{0.5f, 0.75f, 1f, 1.2f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 5f};
    private static final DecimalFormat SPEED_FORMAT = new DecimalFormat("0.##x");
    private static final Pattern HTTP_STATUS = Pattern.compile("(?i)(?:response code|http status|http error)\\D+(\\d{3})");

    private final Runnable runnable;
    private final Callback callback;
    private final DynamicLutEffect dynamicLutEffect;
    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;
    private final BroadcastReceiver noisyReceiver;
    private DanmakuController danmakuController;
    private PlayerEngine engine;
    private VideoSize videoSize;
    private ParseJob parseJob;
    private PlaySpec spec;
    private Player player;
    private TrackSelectionParameters realtimeSubtitleTrackSelection;
    private String currentDanmakuUrl;
    private String currentDanmakuKey;
    private String loadingDanmakuKey;
    private long danmakuLoadStartedAtMs;
    private long pendingSwitchPositionMs = C.TIME_UNSET;
    private float pendingSwitchSpeed = 1f;
    private boolean danmakuLoadInProgress;
    private boolean pendingSwitchRepeat;
    private boolean pendingSwitchRestore;
    private boolean audioFocusHeld;
    private boolean noisyReceiverRegistered;
    private boolean resumeOnAudioFocusGain;
    private Object audioFocusRequest;

    private boolean initTrack;
    private boolean videoEffectsActive;
    private boolean videoEffectsDirty;
    private boolean parseHealthRecorded;
    private boolean lutAppliedForItem;
    private boolean lutApplyInProgress;
    private boolean lutPipelineReadyForItem;
    private boolean lutPipelinePrepareInProgress;
    private boolean pendingLutPreview;
    private boolean waitingLutBeforePlay;
    private boolean playWhenReady = true;
    private boolean lutWarmupRecoveryActive;
    private boolean lutWarmupRefreshRequested;
    private boolean lutWarmupReloadPreviewPending;
    private boolean hardDecodeSwitchRetryArmed;
    private boolean lutAllowed = true;
    private boolean manualPlayerSwitchPending;
    private int playerType;
    private int localProxyRetry;
    private int prepareSeq;
    private int lutApplySeq;
    private long parseHealthStartedAt;
    private boolean[] playerFallbackTried;
    private int lutWarmupRecoveredErrors;

    public PlayerManager(Callback callback) {
        this.runnable = this::onPlaybackTimeout;
        this.dynamicLutEffect = new DynamicLutEffect();
        this.audioFocusChangeListener = this::onNativeAudioFocusChanged;
        this.noisyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || !AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) return;
                onNativeAudioBecomingNoisy();
            }
        };
        this.playerType = PlayerSetting.getPlayer();
        this.playerFallbackTried = new boolean[PLAYER_COUNT];
        this.engine = buildEngine(playerType, PlayerEngine.HARD);
        this.player = engine.getPlayer();
        this.callback = callback;
    }

    public void release() {
        prepareSeq++;
        lutApplySeq++;
        player.removeListener(listener);
        App.removeCallbacks(runnable);
        stopNativeAudioSession();
        if (engine == null) return;
        engine.release();
        engine = null;
        player = null;
        realtimeSubtitleTrackSelection = null;
        videoEffectsActive = false;
        videoEffectsDirty = false;
        lutAppliedForItem = false;
        lutApplyInProgress = false;
        lutPipelineReadyForItem = false;
        lutPipelinePrepareInProgress = false;
        pendingLutPreview = false;
        waitingLutBeforePlay = false;
        lutWarmupReloadPreviewPending = false;
        clearLutWarmupRecovery();
        clearDanmakuState();
    }

    private void resetLutRuntimeState(String reason, boolean clearEngineEffects) {
        lutApplySeq++;
        if (clearEngineEffects && engine != null && engine.supportsNativeLut()) {
            safeSetNativeLut(null, reason + "_reset");
        } else if (clearEngineEffects && engine != null && videoEffectsActive) {
            try {
                engine.setVideoEffects(Collections.emptyList());
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "clear effects before reset reason=%s", reason);
            } catch (Throwable e) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "clear effects before reset failed reason=%s error=%s", reason, causeChain(e));
            }
        }
        dynamicLutEffect.clear();
        videoEffectsActive = false;
        videoEffectsDirty = false;
        lutAppliedForItem = false;
        lutApplyInProgress = false;
        lutPipelineReadyForItem = false;
        lutPipelinePrepareInProgress = false;
        pendingLutPreview = false;
        waitingLutBeforePlay = false;
        lutWarmupReloadPreviewPending = false;
        clearLutWarmupRecovery();
    }

    public Player getPlayer() {
        return player;
    }

    public Tracks getCurrentTracks() {
        return engine.getCurrentTracks();
    }

    public List<MediaEdition> getCurrentMediaEditions() {
        return engine.getCurrentMediaEditions();
    }

    public MediaItem getCurrentMediaItem() {
        return player.getCurrentMediaItem();
    }

    public int getPlaybackState() {
        return player.getPlaybackState();
    }

    public boolean isPlaying() {
        return player.isPlaying();
    }

    public boolean isReleased() {
        return player == null;
    }

    public String getUrl() {
        return spec != null ? spec.getUrl() : null;
    }

    public String getKey() {
        return spec != null ? spec.getKey() : null;
    }

    public List<Danmaku> getDanmakus() {
        return spec != null ? spec.getDanmakus() : null;
    }

    public Sub getSelectedSubtitleSub() {
        return spec == null ? null : findSelectedSubtitleSub(spec.getSubs(), getCurrentTracks());
    }

    public List<Sub> getSubtitleSubs() {
        return spec == null || spec.getSubs() == null ? Collections.emptyList() : spec.getSubs();
    }

    public MediaMetadata getMetadata() {
        return spec != null ? spec.getMetadata() : null;
    }

    public Map<String, String> getHeaders() {
        return spec == null || spec.getHeaders() == null ? new HashMap<>() : spec.getHeaders();
    }

    public float getSpeed() {
        return player.getPlaybackParameters().speed;
    }

    public boolean isEmpty() {
        return spec == null || TextUtils.isEmpty(spec.getUrl());
    }

    public boolean isPortrait() {
        return getVideoHeight() > getVideoWidth();
    }

    public boolean isLandscape() {
        return getVideoWidth() > getVideoHeight();
    }

    public boolean isLive() {
        return engine.isLive();
    }

    public boolean isVod() {
        return engine.isVod();
    }

    public boolean haveTrack(int type) {
        return engine.haveTrack(type);
    }

    public boolean haveTitle() {
        return engine.haveTitle();
    }

    public boolean haveDanmaku() {
        return getDanmakus() != null && getDanmakus().stream().anyMatch(Danmaku::isSelected);
    }

    public boolean canSetOpening(long position, long duration) {
        return position > 0 && duration > 0 && position <= Constant.getOpEdLimit(duration);
    }

    public boolean canSetEnding(long position, long duration) {
        return position > 0 && duration > 0 && duration - position <= Constant.getOpEdLimit(duration);
    }

    public int getVideoWidth() {
        return videoSize == null ? 0 : videoSize.width;
    }

    public int getVideoHeight() {
        return videoSize == null ? 0 : videoSize.height;
    }

    public long getPosition() {
        return player.getCurrentPosition();
    }

    public long getBufferedDuration() {
        return Math.max(0, player.getBufferedPosition() - getPosition());
    }

    public int getBufferedPercentage() {
        return player.getBufferedPercentage();
    }

    public boolean isLoading() {
        return player.isLoading();
    }

    public String getSizeText() {
        int width = getVideoWidth();
        int height = getVideoHeight();
        if (width <= 0 || height <= 0) {
            Format format = getVideoFormat();
            if (format != null) {
                if (width <= 0) width = format.width;
                if (height <= 0) height = format.height;
            }
        }
        return width <= 0 || height <= 0 ? "" : width + " x " + height;
    }

    public String getVideoParamsText() {
        StringBuilder builder = new StringBuilder();
        append(builder, "分辨率", getSizeText());
        Format video = getSelectedFormat(C.TRACK_TYPE_VIDEO);
        Format audio = getSelectedFormat(C.TRACK_TYPE_AUDIO);
        if (video != null) {
            if (video.frameRate > 0) append(builder, "帧率", String.format(Locale.getDefault(), "%.2f fps", video.frameRate));
            append(builder, "视频编码", firstText(video.codecs, video.sampleMimeType, video.containerMimeType));
            append(builder, "视频码率", formatBitrate(video.averageBitrate > 0 ? video.averageBitrate : video.peakBitrate));
        }
        if (audio != null) {
            append(builder, "音频编码", firstText(audio.codecs, audio.sampleMimeType, audio.containerMimeType));
            append(builder, "采样率", audio.sampleRate > 0 ? audio.sampleRate + " Hz" : "");
            append(builder, "声道", audio.channelCount > 0 ? String.valueOf(audio.channelCount) : "");
            append(builder, "音频码率", formatBitrate(audio.averageBitrate > 0 ? audio.averageBitrate : audio.peakBitrate));
        }
        append(builder, "解码", getDecodeText());
        append(builder, "倍速", getSpeedText());
        append(builder, "时长", getDurationTime());
        return builder.toString();
    }

    public Format getVideoFormat() {
        return engine.getVideoFormat();
    }

    public PlayerCacheState getCacheState() {
        return engine == null ? PlayerCacheState.empty() : engine.getCacheState();
    }

    public String getRenderDiagnostics() {
        return engine == null ? "" : engine.getRenderDiagnostics();
    }

    public String getRuntimeDiagnostics() {
        return engine == null ? "" : engine.getRuntimeDiagnostics();
    }

    public long getDroppedFrames() {
        return engine == null ? 0 : engine.getDroppedFrames();
    }

    public boolean supportsSubtitleStyle() {
        return engine != null && engine.supportsSubtitleStyle();
    }

    public boolean supportsSecondarySubtitle() {
        return engine != null && engine.supportsSecondarySubtitle();
    }

    public boolean isSecondarySubtitleSelected(Format format) {
        return engine != null && engine.isSecondarySubtitleSelected(format);
    }

    public String getAudioPassThroughText() {
        if (!PlayerSetting.isAudioPassThrough()) return "关";
        if (!isMpv()) return "开";
        String codecs = engine == null ? "" : engine.getAudioSpdifCodecs();
        return TextUtils.isEmpty(codecs) ? "开/PCM" : "开/" + codecs;
    }

    public void setSubtitleStyle(float textSize, float position) {
        if (engine != null) engine.setSubtitleStyle(textSize, position);
    }

    public String getSpeedText() {
        return SPEED_FORMAT.format(getSpeed());
    }

    public String getDecodeText() {
        return engine.getDecodeText();
    }

    public boolean isHardDecode() {
        return engine.isHard();
    }

    public String getPlayerText() {
        return getPlayerText(playerType);
    }

    public int getPlayerType() {
        return playerType;
    }

    public String getLutText() {
        return LutSetting.getButtonText();
    }

    public void setLutAllowed(boolean allowed) {
        if (lutAllowed == allowed) return;
        lutAllowed = allowed;
        if (!allowed) resetLutRuntimeState("lut_disallowed", true);
    }

    public String getLutUnavailableReason() {
        return LutEligibility.getUnavailableReason(engine, spec);
    }

    public boolean isIjk() {
        return playerType == PlayerSetting.IJK;
    }

    public boolean useNativeVideoOutput() {
        return PlayerSetting.useNativeVideoOutput(playerType);
    }

    private String getPlayerText(int type) {
        String[] items = ResUtil.getStringArray(R.array.select_player_kernel);
        return type >= 0 && type < items.length ? items[type] : items[PlayerSetting.EXO];
    }

    private Format getSelectedFormat(int type) {
        return getSelectedFormat(getCurrentTracks(), type);
    }

    static Format getSelectedFormat(Tracks tracks, int type) {
        if (tracks == null || tracks.isEmpty()) return null;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != type) continue;
            for (int i = 0; i < group.length; i++) {
                if (group.isTrackSelected(i)) return group.getTrackFormat(i);
            }
        }
        return null;
    }

    static Sub findSelectedSubtitleSub(List<Sub> subs, Tracks tracks) {
        Sub selected = findSubtitleSub(subs, getSelectedFormat(tracks, C.TRACK_TYPE_TEXT));
        if (selected != null || hasTrack(tracks, C.TRACK_TYPE_TEXT)) return selected;
        return firstSubtitleSub(subs);
    }

    static Sub findSubtitleSub(List<Sub> subs, Format format) {
        if (subs == null || format == null) return null;
        Sub mimeLanguageMatch = null;
        for (Sub sub : subs) {
            if (sub == null) continue;
            if (!TextUtils.isEmpty(format.label) && TextUtils.equals(format.label, sub.getName()) && mimeMatches(sub, format)) return sub;
            if (TextUtils.isEmpty(format.label) && mimeMatches(sub, format) && languageMatches(sub, format)) {
                if (mimeLanguageMatch != null) return null;
                mimeLanguageMatch = sub;
            }
        }
        return mimeLanguageMatch;
    }

    private static boolean hasTrack(Tracks tracks, int type) {
        if (tracks == null || tracks.isEmpty()) return false;
        for (Tracks.Group group : tracks.getGroups()) if (group.getType() == type && group.length > 0) return true;
        return false;
    }

    private static Sub firstSubtitleSub(List<Sub> subs) {
        if (subs == null) return null;
        for (Sub sub : subs) if (sub != null && !TextUtils.isEmpty(sub.getUrl())) return sub;
        return null;
    }

    private static boolean mimeMatches(Sub sub, Format format) {
        return TextUtils.isEmpty(format.sampleMimeType) || TextUtils.isEmpty(sub.getFormat()) || TextUtils.equals(format.sampleMimeType, sub.getFormat());
    }

    private static boolean languageMatches(Sub sub, Format format) {
        return TextUtils.isEmpty(format.language) || TextUtils.isEmpty(sub.getLang()) || TextUtils.equals(format.language, sub.getLang());
    }

    private static void append(StringBuilder builder, String name, String value) {
        if (TextUtils.isEmpty(value)) return;
        builder.append(name).append(" : ").append(value).append("\n");
    }

    private static String firstText(String... values) {
        for (String value : values) if (!TextUtils.isEmpty(value)) return value;
        return "";
    }

    private static String formatBitrate(int bitrate) {
        if (bitrate <= 0) return "";
        return bitrate >= 1_000_000 ? String.format(Locale.getDefault(), "%.2f Mbps", bitrate / 1_000_000f) : bitrate / 1000 + " Kbps";
    }

    public boolean isMpv() {
        return playerType == PlayerSetting.MPV;
    }

    public boolean isExo() {
        return playerType == PlayerSetting.EXO;
    }

    public boolean isNativePlayer() {
        return !isExo();
    }

    public String getPositionTime(long delta) {
        long time = Math.max(0, Math.min(getPosition() + delta, Math.max(0, getDuration())));
        return Util.timeMs(time);
    }

    public long getDuration() {
        return player.getDuration();
    }

    public String getDurationTime() {
        return Util.timeMs(Math.max(0, getDuration()));
    }

    public void setSub(Sub sub) {
        if (sub == null || spec == null) return;
        Track.delete(getKey(), C.TRACK_TYPE_TEXT);
        engine.resetTrack(C.TRACK_TYPE_TEXT);
        spec.setSub(sub);
        restartCurrentItemWithState();
    }

    public void setFormat(String format) {
        if (spec != null) spec.setFormat(format);
        setMediaItem();
    }

    public void setTitle(MediaEdition edition) {
        if (edition == null) return;
        if (isMpv() && engine.selectEdition(edition)) return;
        if (spec != null) spec.setUrl(spec.getUri().buildUpon().fragment("edition=" + edition.index).build().toString());
        if (engine.selectEdition(edition)) return;
        setMediaItem();
        seekTo(0);
    }

    public static MediaMetadata buildMetadata(String title, String artist, String artUri) {
        Uri artwork = TextUtils.isEmpty(artUri) ? null : Uri.parse(artUri);
        return new MediaMetadata.Builder().setTitle(title).setArtist(artist).setArtworkUri(artwork).build();
    }

    public void setMetadata(MediaMetadata data) {
        if (spec != null) spec.setMetadata(data);
        engine.setMetadata(data);
    }

    public void setDanmakuController(DanmakuController controller) {
        danmakuController = controller;
        danmakuController.setOkHttpClient(OkHttp.player());
        danmakuController.setConfig(DanmakuSetting.getConfig());
        danmakuController.setListener(new DanmakuController.Listener() {
            @Override
            public void onLoadCompleted(Uri uri, int count) {
                logDanmakuLoad("completed", uri, count, null);
                finishDanmakuLoad(uri);
            }

            @Override
            public void onLoadError(Uri uri, IOException error) {
                logDanmakuLoad("error", uri, -1, error);
                finishDanmakuLoad(uri);
            }
        });
    }

    public void setDanmakuConfig(DanmakuConfig config) {
        danmakuController.setConfig(config);
    }

    public void setDanmakuEnabled(boolean enabled) {
        danmakuController.setEnabled(enabled);
    }

    public void sendDanmaku(String text) {
        danmakuController.sendNow(text);
    }

    public String setSpeed(float speed) {
        if (!player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)) return getSpeedText();
        RealtimeSubtitleController realtime = RealtimeSubtitleController.get();
        if (Math.abs(speed - 1f) > 0.001f && (realtime.isEnabled() || realtime.isPreparing())) {
            realtime.disable();
            Notify.show(R.string.subtitle_realtime_speed_disabled);
        }
        player.setPlaybackParameters(player.getPlaybackParameters().withSpeed(speed));
        return getSpeedText();
    }

    public String addSpeed() {
        return setSpeed(nextPresetSpeed());
    }

    public String addSpeed(float value) {
        return setSpeed(Math.min(getSpeed() + value, 5));
    }

    public String subSpeed(float value) {
        return setSpeed(Math.max(getSpeed() - value, 0.25f));
    }

    public String toggleSpeed() {
        return setSpeed(getSpeed() == 1 ? PlayerSetting.getSpeed() : 1);
    }

    private float nextPresetSpeed() {
        float speed = getSpeed();
        for (float preset : SPEED_PRESETS) if (speed < preset - 0.01f) return preset;
        return SPEED_PRESETS[0];
    }

    public void setTrack(List<Track> tracks) {
        if (!tracks.isEmpty()) engine.setTrack(tracks);
    }

    public void setSecondarySubtitleTrack(Track track) {
        if (engine != null) engine.setSecondarySubtitleTrack(track);
    }

    public void play() {
        startNativeAudioSession(true);
        player.play();
    }

    public void pause() {
        player.pause();
        stopNativeAudioSession();
    }

    public void stop() {
        stopNativeAudioSession();
        engine.stop();
        stopParse();
    }

    public void clearMediaItems() {
        stopNativeAudioSession();
        player.clearMediaItems();
    }

    public boolean isRepeatOne() {
        return engine.isRepeatOne();
    }

    public void setRepeatOne(boolean repeat) {
        engine.setRepeatOne(repeat);
    }

    public void seekTo(long time) {
        player.seekTo(time);
    }

    public long getTextOffsetMs() {
        if (player.isCommandAvailable(Player.COMMAND_GET_TEXT_OFFSET)) return player.getTextOffsetMs();
        return 0;
    }

    public void setTextOffsetMs(long offsetMs) {
        if (player.isCommandAvailable(Player.COMMAND_SET_TEXT_OFFSET)) player.setTextOffsetMs(offsetMs);
    }

    public long getAudioOffsetMs() {
        if (player.isCommandAvailable(Player.COMMAND_GET_AUDIO_OFFSET)) return player.getAudioOffsetMs();
        return 0;
    }

    public void setAudioOffsetMs(long offsetMs) {
        if (player.isCommandAvailable(Player.COMMAND_SET_AUDIO_OFFSET)) player.setAudioOffsetMs(offsetMs);
    }

    public void reset() {
        App.removeCallbacks(runnable);
        localProxyRetry = 0;
        resetPlayerFallback();
        hardDecodeSwitchRetryArmed = false;
        clearPendingSwitchRestore();
    }

    public void clear() {
        prepareSeq++;
        lutApplySeq++;
        spec = null;
        clearPendingSwitchRestore();
        clearDanmakuState();
        lutAppliedForItem = false;
        lutApplyInProgress = false;
        lutPipelineReadyForItem = false;
        lutPipelinePrepareInProgress = false;
        parseHealthRecorded = false;
        parseHealthStartedAt = 0;
        pendingLutPreview = false;
        waitingLutBeforePlay = false;
        clearLutWarmupRecovery();
    }

    public void resetTrack() {
        engine.resetTrack();
    }

public void resetTrack(int type) {
        engine.resetTrack(type);
    }

    public void disableSubtitleTrackForRealtime() {
        if (player == null || engine == null) return;
        if (isExo() && realtimeSubtitleTrackSelection == null) realtimeSubtitleTrackSelection = player.getTrackSelectionParameters();
        setTrack(Collections.singletonList(Track.disabled(C.TRACK_TYPE_TEXT, "")));
    }

    public void restoreSubtitleTrackAfterRealtime() {
        if (player == null || engine == null) {
            realtimeSubtitleTrackSelection = null;
            return;
        }
        if (isExo() && realtimeSubtitleTrackSelection != null) player.setTrackSelectionParameters(realtimeSubtitleTrackSelection);
        realtimeSubtitleTrackSelection = null;
    }

    public void rebuildAudioPipeline() {
        if (!isExo() || spec == null || spec.getUrl() == null || engine == null || player == null) return;
        PlaySpec target = spec;
        long position = isLive() ? C.TIME_UNSET : Math.max(0, getPosition());
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        ++prepareSeq;
        App.removeCallbacks(runnable);
        rebuildPlayer();
        playWhenReady = wasPlayWhenReady;
        setDanmakus(target.getDanmakus());
        prepareLutPipeline();
        initTrack = false;
        waitingLutBeforePlay = false;
        applySubtitleStyle();
        engine.start(target.checkUa(), position, wasPlayWhenReady);
        if (speed != 1f) setSpeed(speed);
        setRepeatOne(repeat);
        App.post(runnable, Constant.TIMEOUT_PLAY);
        callback.onPrepare();
    }

    public void restoreVideoTrack() {
        if (engine != null) engine.restoreVideoTrack();
    }

    public void toggleDecode() {
        int next = engine.isHard() ? PlayerEngine.SOFT : PlayerEngine.HARD;
        boolean resetVideoSurface = playerType == PlayerSetting.EXO && next == PlayerEngine.HARD;
        hardDecodeSwitchRetryArmed = next == PlayerEngine.HARD;
        engine.setDecode(next);
        rebuildPlayer(resetVideoSurface);
        setMediaItem();
    }

    public void switchDecode(PlaySpec freshSpec, long position, float speed, boolean repeat) {
        if (engine == null || player == null || freshSpec == null) return;
        int next = engine.isHard() ? PlayerEngine.SOFT : PlayerEngine.HARD;
        boolean resetVideoSurface = playerType == PlayerSetting.EXO && next == PlayerEngine.HARD;
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        prepareSeq++;
        resetLutRuntimeState("switch_decode_fresh", true);
        stopNativeAudioSession();
        engine.release();
        spec = freshSpec;
        hardDecodeSwitchRetryArmed = next == PlayerEngine.HARD;
        engine = buildEngine(playerType, next);
        player = engine.getPlayer();
        playWhenReady = wasPlayWhenReady;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch decode fresh decode=%d position=%d spec=%s", next, position, debugSpec());
        callback.onPlayerRebuild(player, resetVideoSurface);
        setMediaItem(Constant.TIMEOUT_PLAY);
        if (position > 0) seekTo(position);
        if (speed != 1f) setSpeed(speed);
        setRepeatOne(repeat);
    }

    public void switchDecode(Result result, String key, MediaMetadata metadata, boolean useParse, long position, float speed, boolean repeat) {
        if (engine == null || player == null || result == null || result.hasMsg() || result.getRealUrl().isEmpty()) return;
        int next = engine.isHard() ? PlayerEngine.SOFT : PlayerEngine.HARD;
        boolean resetVideoSurface = playerType == PlayerSetting.EXO && next == PlayerEngine.HARD;
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        prepareSeq++;
        resetLutRuntimeState("switch_decode_result", true);
        stopNativeAudioSession();
        stopParse();
        engine.release();
        hardDecodeSwitchRetryArmed = next == PlayerEngine.HARD;
        engine = buildEngine(playerType, next);
        player = engine.getPlayer();
        playWhenReady = wasPlayWhenReady;
        callback.onPlayerRebuild(player, resetVideoSurface);
        if (result.needParse() || useParse) {
            pendingSwitchRestore = true;
            pendingSwitchPositionMs = position;
            pendingSwitchSpeed = speed;
            pendingSwitchRepeat = repeat;
            spec = PlaySpec.fromParse(result, key, metadata, useParse);
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch decode fresh parse decode=%d position=%d useParse=%s spec=%s", next, position, useParse, debugSpec());
            parseJob = ParseJob.create(this).start(result, useParse);
        } else {
            spec = PlaySpec.from(result, key, metadata);
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch decode fresh result decode=%d position=%d spec=%s", next, position, debugSpec());
            setMediaItem(Constant.TIMEOUT_PLAY);
            if (position > 0) seekTo(position);
            if (speed != 1f) setSpeed(speed);
            setRepeatOne(repeat);
        }
    }

    public void togglePlayer() {
        switchPlayerManually(PlayerSetting.nextPlayer(playerType));
    }

    public void switchPlayer(int type) {
        switchPlayer(type, true, false);
    }

    public void switchPlayerManually(int type) {
        switchPlayer(type, true, true);
    }

    public void switchPlayer(int type, PlaySpec freshSpec, long position, float speed, boolean repeat) {
        if (engine == null || player == null || freshSpec == null) return;
        type = PlayerSetting.sanitizePlayer(type);
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        int decode = engine.getDecode();
        prepareSeq++;
        resetLutRuntimeState("switch_player_fresh", true);
        stopNativeAudioSession();
        engine.release();
        playerType = type;
        PlayerSetting.putPlayer(type);
        spec = freshSpec;
        playWhenReady = wasPlayWhenReady;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch player fresh type=%d position=%d spec=%s", type, position, debugSpec());
        engine = buildEngine(playerType, decode);
        player = engine.getPlayer();
        callback.onPlayerRebuild(player, false);
        setMediaItem(Constant.TIMEOUT_PLAY);
        if (position > 0) seekTo(position);
        if (speed != 1f) setSpeed(speed);
        setRepeatOne(repeat);
    }

    public void switchPlayer(int type, Result result, String key, MediaMetadata metadata, boolean useParse, long position, float speed, boolean repeat) {
        if (engine == null || player == null || result == null || result.hasMsg() || result.getRealUrl().isEmpty()) return;
        type = PlayerSetting.sanitizePlayer(type);
        manualPlayerSwitchPending = true;
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        int decode = engine.getDecode();
        prepareSeq++;
        resetLutRuntimeState("switch_player_result", true);
        stopNativeAudioSession();
        stopParse();
        engine.release();
        playerType = type;
        PlayerSetting.putPlayer(type);
        engine = buildEngine(playerType, decode);
        player = engine.getPlayer();
        playWhenReady = wasPlayWhenReady;
        callback.onPlayerRebuild(player, false);
        if (result.needParse() || useParse) {
            pendingSwitchRestore = true;
            pendingSwitchPositionMs = position;
            pendingSwitchSpeed = speed;
            pendingSwitchRepeat = repeat;
            spec = PlaySpec.fromParse(result, key, metadata, useParse);
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch player fresh parse type=%d position=%d useParse=%s spec=%s", type, position, useParse, debugSpec());
            parseJob = ParseJob.create(this).start(result, useParse);
        } else {
            spec = PlaySpec.from(result, key, metadata);
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch player fresh result type=%d position=%d spec=%s", type, position, debugSpec());
            setMediaItem(Constant.TIMEOUT_PLAY);
            if (position > 0) seekTo(position);
            if (speed != 1f) setSpeed(speed);
            setRepeatOne(repeat);
        }
    }

    private void switchPlayer(int type, boolean persist) {
        switchPlayer(type, persist, false);
    }

    private void switchPlayer(int type, boolean persist, boolean manual) {
        if (engine == null || player == null) return;
        type = PlayerSetting.sanitizePlayer(type);
        type = manual ? resolveManualPlayer(type) : resolveAvailablePlayer(type);
        if (type == playerType) return;
        resetPlayerFallback();
        manualPlayerSwitchPending = manual;
        switchEngine(type, persist, true, true);
    }

    private void switchEngine(int type, boolean persist, boolean preserveState, boolean notifyPrepare) {
        int decode = engine.getDecode();
        switchEngine(type, persist, preserveState, notifyPrepare, decode);
    }

    private void switchEngine(int type, boolean persist, boolean preserveState, boolean notifyPrepare, int decode) {
        long position = preserveState ? getPosition() : 0;
        float speed = preserveState ? getSpeed() : 1f;
        boolean repeat = preserveState && isRepeatOne();
        boolean wasPlayWhenReady = preserveState && player != null ? player.getPlayWhenReady() : playWhenReady;
        switchEngine(type, persist, notifyPrepare, decode, position, speed, repeat, wasPlayWhenReady);
    }

    private void switchEngine(int type, boolean persist, boolean notifyPrepare, int decode, long position, float speed, boolean repeat, boolean wasPlayWhenReady) {
        prepareSeq++;
        resetLutRuntimeState("switch_player", true);
        stopNativeAudioSession();
        engine.release();
        playerType = type;
        if (persist) {
            PlayerSetting.putPlayer(type);
        }
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch player type=%d persist=%s position=%d spec=%s", type, persist, position, debugSpec());
        engine = buildEngine(playerType, sanitizeDecode(decode));
        player = engine.getPlayer();
        callback.onPlayerRebuild(player, false);
        if (spec == null || spec.getUrl() == null) return;
        this.playWhenReady = wasPlayWhenReady;
        if (reparseForPlayerSwitch(position, speed, repeat)) return;
        if (notifyPrepare) setMediaItem(Constant.TIMEOUT_PLAY);
        else setMediaItemNow(Constant.TIMEOUT_PLAY, false);
        if (position > 0) seekTo(position);
        if (speed != 1f) setSpeed(speed);
        setRepeatOne(repeat);
    }

    private void rebuildPlayer() {
        rebuildPlayer(false);
    }

    private void rebuildPlayer(boolean resetVideoSurface) {
        stopNativeAudioSession();
        player = engine.rebuild(listener);
        videoEffectsActive = false;
        videoEffectsDirty = false;
        lutAppliedForItem = false;
        lutApplyInProgress = false;
        lutPipelineReadyForItem = false;
        lutPipelinePrepareInProgress = false;
        pendingLutPreview = false;
        waitingLutBeforePlay = false;
        callback.onPlayerRebuild(player, resetVideoSurface);
    }

    private PlayerEngine buildEngine(int type, int decode) {
        return switch (type) {
            case PlayerSetting.IJK -> new IjkPlayerEngine(decode, listener);
            case PlayerSetting.SYSTEM -> new SystemPlayerEngine(decode, listener);
            case PlayerSetting.MPV -> new MpvPlayerEngine(decode, listener);
            default -> new ExoPlayerEngine(decode, listener);
        };
    }

    public void browse(PlaySpec spec) {
        reset();
        clear();
        stopParse();
        start(spec, Constant.TIMEOUT_PLAY);
    }

    public void start(PlaySpec spec, long timeout) {
        start(spec, timeout, true);
    }

    public void start(PlaySpec spec, long timeout, boolean playWhenReady) {
        clearPendingSwitchRestore();
        this.spec = spec;
        this.playWhenReady = playWhenReady;
        manualPlayerSwitchPending = false;
        localProxyRetry = 0;
        resetPlayerFallback();
        hardDecodeSwitchRetryArmed = false;
        clearDanmakuState();
        setMediaItem(timeout);
    }

    public void parse(String key, Result result, boolean useParse, MediaMetadata metadata) {
        parse(key, result, useParse, metadata, true);
    }

    public void parse(String key, Result result, boolean useParse, MediaMetadata metadata, boolean playWhenReady) {
        stopParse();
        clearPendingSwitchRestore();
        spec = PlaySpec.fromParse(result, key, metadata, useParse);
        this.playWhenReady = playWhenReady;
        manualPlayerSwitchPending = false;
        localProxyRetry = 0;
        parseHealthStartedAt = System.currentTimeMillis();
        parseHealthRecorded = false;
        resetPlayerFallback();
        hardDecodeSwitchRetryArmed = false;
        clearDanmakuState();
        parseJob = ParseJob.create(this).start(result, useParse);
    }

    private void stopParse() {
        if (parseJob != null) parseJob.stop();
        parseJob = null;
    }

    private boolean reparseForPlayerSwitch(long position, float speed, boolean repeat) {
        if (spec == null || !spec.canReparse() || !spec.isParseSource()) return false;
        Result result = spec.getParseResult();
        boolean useParse = spec.isParseUseParse();
        MediaMetadata metadata = spec.getMetadata();
        String key = spec.getKey();
        pendingSwitchRestore = true;
        pendingSwitchPositionMs = position;
        pendingSwitchSpeed = speed;
        pendingSwitchRepeat = repeat;
        stopParse();
        if (spec.isParseSource()) {
            spec = PlaySpec.fromParse(result, key, metadata, useParse);
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch player reparse type=%d position=%d useParse=%s spec=%s", playerType, position, useParse, debugSpec());
            parseJob = ParseJob.create(this).start(result, useParse);
        } else {
            refreshDirectForPlayerSwitch(result, key, metadata);
        }
        return true;
    }

    private void refreshDirectForPlayerSwitch(Result result, String key, MediaMetadata metadata) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch player refresh direct type=%d key=%s flag=%s url=%s", playerType, key, result.getFlag(), summarizeUrl(result.getUrl().v()));
        Task.execute(() -> {
            try {
                Result refreshed = SiteApi.playerContent(key, result.getFlag(), result.getUrl().v(), playerType);
                App.post(() -> startRefreshedSwitchResult(refreshed, key, metadata));
            } catch (Throwable e) {
                App.post(() -> {
                    clearPendingSwitchRestore();
                    callback.onError(e.getMessage());
                });
            }
        });
    }

    private void startRefreshedSwitchResult(Result result, String key, MediaMetadata metadata) {
        if (result == null || result.hasMsg() || result.getRealUrl().isEmpty()) {
            clearPendingSwitchRestore();
            callback.onError(result == null ? ResUtil.getString(R.string.error_play_url) : result.hasMsg() ? result.getMsg() : ResUtil.getString(R.string.error_play_url));
            return;
        }
        if (result.needParse()) {
            spec = PlaySpec.fromParse(result, key, metadata, false);
            parseJob = ParseJob.create(this).start(result, false);
            return;
        }
        spec = PlaySpec.from(result, key, metadata);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch player refreshed direct spec=%s", debugSpec());
        setMediaItem(Constant.TIMEOUT_PLAY);
        restoreAfterSwitchReparse();
    }

    private void restoreAfterSwitchReparse() {
        if (!pendingSwitchRestore) return;
        long position = pendingSwitchPositionMs;
        float speed = pendingSwitchSpeed;
        boolean repeat = pendingSwitchRepeat;
        clearPendingSwitchRestore();
        if (position > 0) seekTo(position);
        if (speed != 1f) setSpeed(speed);
        setRepeatOne(repeat);
    }

    private void clearPendingSwitchRestore() {
        pendingSwitchRestore = false;
        pendingSwitchPositionMs = C.TIME_UNSET;
        pendingSwitchSpeed = 1f;
        pendingSwitchRepeat = false;
    }

    public void setMediaItem() {
        playWhenReady = player == null || player.getPlayWhenReady();
        setMediaItem(Constant.TIMEOUT_PLAY);
    }

    private void setMediaItem(long timeout) {
        if (spec == null || spec.getUrl() == null) return;
        if (!ensurePlayerAvailableForPlayback()) return;
        int seq = ++prepareSeq;
        if (rejectMpvDrmMedia()) return;
        if (LocalProxyDebug.shouldAwaitReady(spec.getUrl())) {
            awaitLocalProxyAndSetMediaItem(seq, timeout);
            return;
        }
        setMediaItemNow(timeout, true);
    }

    private void restartCurrentItemWithState() {
        if (spec == null || spec.getUrl() == null || engine == null || player == null) return;
        if (player.getCurrentMediaItem() == null || player.getPlaybackState() == Player.STATE_IDLE) {
            setMediaItem();
            return;
        }
        if (!ensurePlayerAvailableForPlayback()) return;
        long position = Math.max(0, player.getCurrentPosition());
        boolean playWhenReady = player.getPlayWhenReady();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "restart media item for subtitle position=%d play=%s spec=%s", position, playWhenReady, debugSpec());
        App.removeCallbacks(runnable);
        currentDanmakuUrl = null;
        setDanmakus(spec.getDanmakus());
        initTrack = false;
        waitingLutBeforePlay = false;
        engine.restart(spec.checkUa(), position, playWhenReady);
        App.post(runnable, Constant.TIMEOUT_PLAY);
    }

    private void awaitLocalProxyAndSetMediaItem(int seq, long timeout) {
        PlaySpec target = spec;
        String url = target.getUrl();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "local proxy await start seq=%d timeout=%d spec=%s", seq, timeout, debugSpec());
        Task.execute(() -> {
            boolean ready = LocalProxyDebug.awaitReady(url, LOCAL_PROXY_READY_TIMEOUT_MS);
            App.post(() -> {
                if (seq != prepareSeq || spec != target || engine == null) {
                    if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "local proxy await skip seq=%d current=%d ready=%s", seq, prepareSeq, ready);
                    return;
                }
                if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "local proxy await done seq=%d ready=%s spec=%s", seq, ready, debugSpec());
                setMediaItemNow(timeout, true);
            });
        });
    }

    private void setMediaItemNow(long timeout, boolean notifyPrepare) {
        if (spec == null || spec.getUrl() == null || engine == null) return;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "setMediaItem timeout=%d notify=%s spec=%s", timeout, notifyPrepare, debugSpec());
        App.removeCallbacks(runnable);
        setDanmakus(spec.getDanmakus());
        prepareLutPipeline();
        initTrack = false;
        waitingLutBeforePlay = false;
        applySubtitleStyle();
        engine.start(spec.checkUa(), playWhenReady);
        startNativeAudioSession(playWhenReady);
        App.post(runnable, timeout);
        if (notifyPrepare) callback.onPrepare();
    }

    private void applySubtitleStyle() {
        if (engine != null) engine.setSubtitleStyle(PlayerSetting.getSubtitleTextSize(), PlayerSetting.getSubtitlePosition());
    }

    private void startNativeAudioSession(boolean shouldPlay) {
        if (!shouldPlay || !isNativePlayer()) return;
        requestNativeAudioFocus();
        registerNoisyReceiver();
    }

    private void stopNativeAudioSession() {
        unregisterNoisyReceiver();
        abandonNativeAudioFocus();
        resumeOnAudioFocusGain = false;
    }

    private void requestNativeAudioFocus() {
        AudioManager manager = audioManager();
        if (manager == null || audioFocusHeld) return;
        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusApi26.request(manager, audioFocusChangeListener);
            result = audioFocusRequest == null ? AudioManager.AUDIOFOCUS_REQUEST_FAILED : AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        } else {
            result = manager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        audioFocusHeld = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (!audioFocusHeld && SpiderDebug.isEnabled()) SpiderDebug.log("player", "native audio focus request denied type=%d", playerType);
    }

    private void abandonNativeAudioFocus() {
        if (!audioFocusHeld) return;
        AudioManager manager = audioManager();
        if (manager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) AudioFocusApi26.abandon(manager, audioFocusRequest);
            else manager.abandonAudioFocus(audioFocusChangeListener);
        }
        audioFocusRequest = null;
        audioFocusHeld = false;
    }

    private void registerNoisyReceiver() {
        if (noisyReceiverRegistered) return;
        try {
            App.get().registerReceiver(noisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
            noisyReceiverRegistered = true;
        } catch (Throwable e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "register noisy receiver failed error=%s", causeChain(e));
        }
    }

    private void unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return;
        try {
            App.get().unregisterReceiver(noisyReceiver);
        } catch (Throwable e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "unregister noisy receiver failed error=%s", causeChain(e));
        }
        noisyReceiverRegistered = false;
    }

    private void onNativeAudioBecomingNoisy() {
        if (!isNativePlayer() || player == null) return;
        boolean wasPlaying = player.isPlaying();
        player.pause();
        stopNativeAudioSession();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "native audio noisy pause type=%d wasPlaying=%s", playerType, wasPlaying);
    }

    private void onNativeAudioFocusChanged(int focusChange) {
        if (!isNativePlayer() || player == null) return;
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnAudioFocusGain) {
                    resumeOnAudioFocusGain = false;
                    startNativeAudioSession(true);
                    player.play();
                }
            }
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                resumeOnAudioFocusGain = player.isPlaying();
                player.pause();
            }
            case AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnAudioFocusGain = false;
                player.pause();
                stopNativeAudioSession();
            }
        }
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "native audio focus changed type=%d change=%d resume=%s", playerType, focusChange, resumeOnAudioFocusGain);
    }

    private AudioManager audioManager() {
        return (AudioManager) App.get().getSystemService(Context.AUDIO_SERVICE);
    }

    private static final class AudioFocusApi26 {

        private static Object request(AudioManager manager, AudioManager.OnAudioFocusChangeListener listener) {
            android.media.AudioAttributes attributes = new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build();
            android.media.AudioFocusRequest request = new android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener(listener)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(true)
                    .build();
            return manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ? request : null;
        }

        private static void abandon(AudioManager manager, Object request) {
            if (request instanceof android.media.AudioFocusRequest) manager.abandonAudioFocusRequest((android.media.AudioFocusRequest) request);
        }
    }

    private boolean rejectMpvDrmMedia() {
        if (!isMpv() || spec == null || spec.getDrm() == null) return false;
        App.removeCallbacks(runnable);
        clearPendingSwitchRestore();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "reject drm for mpv spec=%s drm=%s", debugSpec(), spec.getDrm().getType());
        callback.onError(ResUtil.getString(R.string.error_play_mpv_drm_unsupported));
        return true;
    }

    private void prepareLutPipeline() {
        lutApplySeq++;
        lutAppliedForItem = false;
        lutApplyInProgress = false;
        lutPipelineReadyForItem = false;
        lutPipelinePrepareInProgress = false;
        pendingLutPreview = false;
        dynamicLutEffect.clear();
        clearLutWarmupRecovery();
        if (videoEffectsDirty) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "rebuild clean player before media item reason=prepare spec=%s", debugSpec());
            rebuildPlayer();
        }
        clearVideoEffects("prepare");
    }

    private boolean shouldPrepareLutBeforePlay() {
        return false;
    }

    public void applyLut(boolean notify) {
        applyLut(notify, false);
    }

    public void applyLutPreview(boolean notify) {
        applyLut(notify, true);
    }

    private void applyLut(boolean notify, boolean preview) {
        if (engine == null) return;
        int seq = ++lutApplySeq;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "request seq=%d notify=%s preview=%s enabled=%s preset=%s state=%s videoFormat=%s tracksEmpty=%s active=%s dirty=%s applied=%s applying=%s pendingPreview=%s", seq, notify, preview, LutSetting.isEnabled(), LutSetting.getPresetId(), stateName(player.getPlaybackState()), engine.getVideoFormat(), engine.getCurrentTracks() == null || engine.getCurrentTracks().isEmpty(), videoEffectsActive, videoEffectsDirty, lutAppliedForItem, lutApplyInProgress, pendingLutPreview);
        if (!lutAllowed) {
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects("disallowed");
            completeLutBeforePlay("disallowed");
            return;
        }
        if (!LutSetting.isEnabled()) {
            if (waitingLutBeforePlay && shouldWaitForVideoFormat()) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "wait video format before neutral start reason=off state=%s spec=%s", stateName(player.getPlaybackState()), debugSpec());
                return;
            }
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects("off");
            completeLutBeforePlay("off");
            return;
        }
        LutPreset preset = LutStore.find(LutSetting.getPresetId());
        if (preset == null) {
            if (waitingLutBeforePlay && shouldWaitForVideoFormat()) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "wait video format before neutral start reason=missing state=%s spec=%s", stateName(player.getPlaybackState()), debugSpec());
                return;
            }
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects("missing");
            completeLutBeforePlay("missing");
            if (notify) Notify.show(R.string.lut_missing);
            return;
        }
        String reason = getLutUnavailableReason();
        if (!TextUtils.isEmpty(reason)) {
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects(reason);
            completeLutBeforePlay(reason);
            if (notify) Notify.show(reason);
            return;
        }
        if (shouldWaitForLutFormat()) {
            lutAppliedForItem = false;
            lutApplyInProgress = false;
            if (notify || preview) pendingLutPreview = preview;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "wait video format notify=%s preview=%s state=%s spec=%s", notify, preview, stateName(player.getPlaybackState()), debugSpec());
            return;
        }
        if (notify || preview) pendingLutPreview = preview;
        if (engine.supportsNativeLut()) {
            applyNativeLut(seq, preset, notify, preview);
            return;
        }
        if (!ensureLutPipelineReadyForCurrentItem("request")) {
            return;
        }
        lutAppliedForItem = false;
        lutApplyInProgress = true;
        pendingLutPreview = false;
        int strength = LutSetting.getStrength();
        int previewSeconds = LutSetting.getPreviewSeconds();
        Task.execute(() -> {
            long start = System.currentTimeMillis();
            try {
                ColorLut colorLut = LutEffectFactory.createColorLut(preset, strength);
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "create dynamic preset=%s format=%s strength=%d preview=%s seconds=%d cost=%dms", preset.getId(), preset.getFormat(), strength, preview, previewSeconds, System.currentTimeMillis() - start);
                App.post(() -> applyLutColor(seq, colorLut, notify, preview, previewSeconds));
            } catch (Throwable e) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "create failed preset=%s strength=%d error=%s", preset.getId(), strength, causeChain(e));
                App.post(() -> {
                    if (seq != lutApplySeq || engine == null) return;
                    lutApplyInProgress = false;
                    setNeutralVideoEffects("error");
                    completeLutBeforePlay("error");
                    if (notify) Notify.show(R.string.lut_apply_failed);
                });
            }
        });
    }

    private void applyNativeLut(int seq, LutPreset preset, boolean notify, boolean preview) {
        lutAppliedForItem = false;
        lutApplyInProgress = true;
        pendingLutPreview = false;
        int strength = LutSetting.getStrength();
        int previewSeconds = LutSetting.getPreviewSeconds();
        long previewStartMs = preview && player != null ? Math.max(0, player.getCurrentPosition()) : 0;
        Task.execute(() -> {
            long start = System.currentTimeMillis();
            try {
                MpvLutShader shader = MpvLutShaderFactory.create(preset, strength, preview, previewStartMs, previewSeconds);
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-mpv", "create shader preset=%s format=%s strength=%d preview=%s start=%d seconds=%d cost=%dms", preset.getId(), preset.getFormat(), strength, preview, previewStartMs, previewSeconds, System.currentTimeMillis() - start);
                App.post(() -> applyNativeLutShader(seq, shader, notify, preview));
            } catch (Throwable e) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-mpv", "create shader failed preset=%s strength=%d error=%s", preset.getId(), strength, causeChain(e));
                App.post(() -> {
                    if (seq != lutApplySeq || engine == null) return;
                    lutApplyInProgress = false;
                    setNeutralVideoEffects("native_error");
                    completeLutBeforePlay("native_error");
                    if (notify) Notify.show(R.string.lut_apply_failed);
                });
            }
        });
    }

    private void applyNativeLutShader(int seq, MpvLutShader shader, boolean notify, boolean preview) {
        if (seq != lutApplySeq || engine == null) return;
        String reason = getLutUnavailableReason();
        if (!TextUtils.isEmpty(reason)) {
            lutApplyInProgress = false;
            setNeutralVideoEffects(reason);
            completeLutBeforePlay(reason);
            if (notify) Notify.show(reason);
            return;
        }
        if (safeSetNativeLut(shader, preview ? "preview_native" : "apply_native")) {
            lutAppliedForItem = true;
            pendingLutPreview = false;
            if (preview) scheduleNativeLutPreviewCommit(seq);
        } else {
            lutAppliedForItem = false;
        }
        lutApplyInProgress = false;
        completeLutBeforePlay(preview ? "preview_native" : "apply_native");
    }

    private void scheduleNativeLutPreviewCommit(int seq) {
        int delayMs = Math.max(1, LutSetting.getPreviewSeconds()) * 1000 + MpvLutShaderFactory.PREVIEW_SLIDE_MS;
        App.post(() -> {
            if (seq != lutApplySeq || engine == null || !engine.supportsNativeLut()) return;
            if (!lutAllowed || !LutSetting.isEnabled()) return;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-mpv", "commit preview shader seq=%d delay=%d", seq, delayMs);
            applyLut(false, false);
        }, delayMs);
    }

    private void applyLutColor(int seq, ColorLut colorLut, boolean notify, boolean preview, int previewSeconds) {
        if (seq != lutApplySeq || engine == null) return;
        String reason = getLutUnavailableReason();
        if (!TextUtils.isEmpty(reason)) {
            dynamicLutEffect.clear();
            lutApplyInProgress = false;
            setNeutralVideoEffects(reason);
            completeLutBeforePlay(reason);
            if (notify) Notify.show(reason);
            return;
        }
        if (shouldWaitForLutFormat()) {
            lutAppliedForItem = false;
            lutApplyInProgress = false;
            if (notify || preview) pendingLutPreview = preview;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "wait video format before set effects preview=%s spec=%s", preview, debugSpec());
            return;
        }
        dynamicLutEffect.set(colorLut, preview, previewSeconds);
        if (safeSetVideoEffects(dynamicLutEffect.effects(), preview ? "preview_dynamic" : "apply_dynamic")) {
            lutAppliedForItem = true;
            pendingLutPreview = false;
        } else {
            lutAppliedForItem = false;
        }
        lutApplyInProgress = false;
        completeLutBeforePlay(preview ? "preview" : "apply");
    }

    private void applyLutForCurrentItem() {
        if (engine == null) return;
        if (!lutAllowed) {
            if (lutAppliedForItem && !videoEffectsActive && !waitingLutBeforePlay) return;
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects("auto_disallowed");
            completeLutBeforePlay("auto_disallowed");
            return;
        }
        if (!LutSetting.isEnabled()) {
            if (lutAppliedForItem && !videoEffectsActive && !waitingLutBeforePlay) return;
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects("auto_off");
            completeLutBeforePlay("auto_off");
            return;
        }
        String reason = getLutUnavailableReason();
        if (!TextUtils.isEmpty(reason)) {
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects(reason);
            completeLutBeforePlay(reason);
            return;
        }
        if (shouldWaitForLutFormat()) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "wait video format before auto apply state=%s spec=%s", stateName(player.getPlaybackState()), debugSpec());
            return;
        }
        if (lutApplyInProgress) return;
        if (lutAppliedForItem) return;
        if (!ensureLutPipelineReadyForCurrentItem("auto")) return;
        boolean preview = pendingLutPreview || lutWarmupReloadPreviewPending;
        lutWarmupReloadPreviewPending = false;
        applyLut(false, preview);
    }

    private void completeLutBeforePlay(String reason) {
        if (!waitingLutBeforePlay || player == null) return;
        waitingLutBeforePlay = false;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "start playback after lut decision reason=%s active=%s dirty=%s", reason, videoEffectsActive, videoEffectsDirty);
        player.play();
    }

    private boolean ensureLutPipelineReadyForCurrentItem(String reason) {
        if (lutPipelineReadyForItem) return true;
        if (lutPipelinePrepareInProgress) return false;
        if (!canWarmLutPipeline()) return true;
        if (shouldWaitForVideoFormat()) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "wait video format before pipeline warmup reason=%s state=%s spec=%s", reason, stateName(player.getPlaybackState()), debugSpec());
            return false;
        }
        String unavailable = getLutUnavailableReason();
        if (!TextUtils.isEmpty(unavailable)) return true;
        return prepareLutPipelineForCurrentItem(reason);
    }

    private boolean prepareLutPipelineForCurrentItem(String reason) {
        if (spec == null || engine == null || player == null) return true;
        lutPipelinePrepareInProgress = true;
        lutAppliedForItem = false;
        lutApplyInProgress = false;
        dynamicLutEffect.clear();
        long position = Math.max(0, getPosition());
        boolean playWhenReady = player.getPlayWhenReady();
        float speed = getSpeed();
        if (!safeSetVideoEffects(dynamicLutEffect.effects(), reason + "_prepare_dynamic_passthrough")) {
            lutPipelinePrepareInProgress = false;
            return true;
        }
        lutPipelineReadyForItem = true;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "prepare current item with effects reason=%s position=%d play=%s spec=%s", reason, position, playWhenReady, debugSpec());
        startLutWarmupRecovery();
        engine.restart(spec.checkUa(), position, playWhenReady);
        if (speed != 1f) setSpeed(speed);
        lutPipelinePrepareInProgress = false;
        return false;
    }

    private void startLutWarmupRecovery() {
        lutWarmupRecoveryActive = true;
        lutWarmupRefreshRequested = false;
        lutWarmupRecoveredErrors = 0;
    }

    private void clearLutWarmupRecovery() {
        lutWarmupRecoveryActive = false;
        lutWarmupRefreshRequested = false;
        lutWarmupRecoveredErrors = 0;
    }

    private boolean retryLutWarmupByRefresh(String reason) {
        if (!lutWarmupRecoveryActive || lutWarmupRefreshRequested || !LutSetting.isEnabled()) return false;
        lutWarmupRefreshRequested = true;
        lutWarmupRecoveryActive = false;
        lutWarmupReloadPreviewPending = true;
        App.removeCallbacks(runnable);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "request playback refresh after warmup failure reason=%s errors=%d spec=%s", reason, lutWarmupRecoveredErrors, debugSpec());
        callback.onReload(RELOAD_LUT_WARMUP);
        return true;
    }

    private boolean retryLutWarmupByRefresh(PlayerEngine.ErrorAction action, PlaybackException e) {
        if (!lutWarmupRecoveryActive || lutWarmupRefreshRequested || !LutSetting.isEnabled()) return false;
        if (action == PlayerEngine.ErrorAction.RECOVERED && ++lutWarmupRecoveredErrors < LUT_WARMUP_RECOVERED_ERROR_REFRESH_THRESHOLD) return false;
        if (action != PlayerEngine.ErrorAction.RECOVERED && action != PlayerEngine.ErrorAction.FATAL && action != PlayerEngine.ErrorAction.RELOAD) return false;
        return retryLutWarmupByRefresh("error_" + e.errorCode + "_" + action);
    }

    private boolean shouldWaitForLutFormat() {
        if (engine == null || !LutSetting.isEnabled()) return false;
        return shouldWaitForVideoFormat();
    }

    private boolean shouldWaitForVideoFormat() {
        if (engine == null) return false;
        Format currentFormat = engine.getVideoFormat();
        if (isUsableVideoFormat(currentFormat)) return false;
        Tracks tracks = engine.getCurrentTracks();
        if (tracks == null || tracks.isEmpty()) return true;
        boolean hasVideo = false;
        boolean hasUsableFormat = false;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
            hasVideo = true;
            for (int i = 0; i < group.length; i++) {
                if (isUsableVideoFormat(group.getTrackFormat(i))) {
                    hasUsableFormat = true;
                    break;
                }
            }
            if (hasUsableFormat) break;
        }
        return hasVideo && !hasUsableFormat;
    }

    private boolean isUsableVideoFormat(Format format) {
        if (format == null) return false;
        return !TextUtils.isEmpty(format.sampleMimeType) || !TextUtils.isEmpty(format.codecs) || format.colorInfo != null || format.width > 0 || format.height > 0;
    }

    private void clearVideoEffects(String reason) {
        dynamicLutEffect.clear();
        if (engine != null && engine.supportsNativeLut()) {
            safeSetNativeLut(null, reason);
            return;
        }
        safeSetVideoEffects(Collections.emptyList(), reason);
    }

    private void setNeutralVideoEffects(String reason) {
        dynamicLutEffect.clear();
        if (engine != null && engine.supportsNativeLut()) {
            safeSetNativeLut(null, reason);
            return;
        }
        if (canKeepWarmNeutralEffects()) safeSetVideoEffects(dynamicLutEffect.effects(), reason + "_dynamic_passthrough");
        else clearVideoEffects(reason);
    }

    private boolean canKeepWarmNeutralEffects() {
        if (!LutSetting.isEnabled()) return false;
        if (!canWarmLutPipeline()) return false;
        if (shouldWaitForVideoFormat()) return false;
        return TextUtils.isEmpty(getLutUnavailableReason());
    }

    private boolean canWarmLutPipeline() {
        if (!lutAllowed) return false;
        if (engine == null || !engine.supportsVideoEffects()) return false;
        if (spec != null && spec.getDrm() != null) return false;
        if (PlayerSetting.isTunnel()) return false;
        if (engine.getDecode() == PlayerEngine.SOFT) return false;
        if (PlayerSetting.isVideoPrefer()) return false;
        return true;
    }

    private boolean safeSetVideoEffects(List<Effect> effects, String reason) {
        if (engine == null) return false;
        boolean empty = effects == null || effects.isEmpty();
        if (empty && !videoEffectsActive) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "skip clear effects reason=%s", reason);
            return false;
        }
        try {
            engine.setVideoEffects(empty ? Collections.emptyList() : effects);
            if (empty) {
                videoEffectsActive = false;
            } else {
                videoEffectsActive = true;
                videoEffectsDirty = true;
            }
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "set effects=%d reason=%s active=%s dirty=%s", empty ? 0 : effects.size(), reason, videoEffectsActive, videoEffectsDirty);
            return true;
        } catch (Throwable e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "set effects failed reason=%s error=%s", reason, causeChain(e));
            return false;
        }
    }

    private boolean safeSetNativeLut(MpvLutShader shader, String reason) {
        if (engine == null || !engine.supportsNativeLut()) return false;
        try {
            engine.setNativeLutShader(shader);
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-mpv", "set shader=%s reason=%s", shader == null ? "none" : shader.diagnostics(), reason);
            return true;
        } catch (Throwable e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-mpv", "set shader failed reason=%s error=%s", reason, causeChain(e));
            return false;
        }
    }

    private void setDanmakus(List<Danmaku> items) {
        setDanmaku(items == null || items.isEmpty() ? Danmaku.empty() : items.get(0));
    }

    public void setDanmaku(Danmaku item) {
        setDanmaku(item, false);
    }

    public void reloadDanmaku(Danmaku item) {
        setDanmaku(item, true);
    }

    private void setDanmaku(Danmaku item, boolean force) {
        if (danmakuController == null) return;
        if (item.isEmpty()) {
            if (spec != null) spec.setDanmaku(item);
            if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "clear current=%s", summarizeUrl(currentDanmakuUrl));
            if (currentDanmakuUrl != null) danmakuController.clearItems();
            clearDanmakuState();
            return;
        }
        String url = item.getRealUrl();
        String key = normalizeDanmakuKey(url);
        if (!force && TextUtils.equals(currentDanmakuUrl, url)) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "skip same url=%s", summarizeUrl(url));
            return;
        }
        if (force && shouldSkipForcedDanmakuReload(key)) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "skip duplicate reload key=%s url=%s", summarizeUrl(key), summarizeUrl(url));
            return;
        }
        if (spec != null) spec.setDanmaku(item);
        if (force && currentDanmakuUrl != null) danmakuController.clearItems();
        currentDanmakuUrl = url;
        currentDanmakuKey = key;
        loadingDanmakuKey = key;
        danmakuLoadStartedAtMs = SystemClock.elapsedRealtime();
        danmakuLoadInProgress = true;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "%s name=%s url=%s key=%s", force ? "reload" : "load", item.getName(), summarizeUrl(url), summarizeUrl(key));
        danmakuController.setDataSource(Uri.parse(url));
    }

    private boolean shouldSkipForcedDanmakuReload(String key) {
        if (TextUtils.isEmpty(key) || !TextUtils.equals(currentDanmakuKey, key) || danmakuLoadStartedAtMs <= 0) return false;
        if (danmakuLoadInProgress && (TextUtils.isEmpty(loadingDanmakuKey) || TextUtils.equals(loadingDanmakuKey, key))) return true;
        long elapsed = SystemClock.elapsedRealtime() - danmakuLoadStartedAtMs;
        return elapsed >= 0 && elapsed < DANMAKU_FORCE_RELOAD_DEBOUNCE_MS;
    }

    private void finishDanmakuLoad(Uri uri) {
        String key = normalizeDanmakuKey(uri == null ? "" : uri.toString());
        if (!TextUtils.isEmpty(loadingDanmakuKey) && !TextUtils.equals(loadingDanmakuKey, key)) return;
        danmakuLoadInProgress = false;
        loadingDanmakuKey = null;
    }

    private void clearDanmakuState() {
        currentDanmakuUrl = null;
        currentDanmakuKey = null;
        loadingDanmakuKey = null;
        danmakuLoadStartedAtMs = 0;
        danmakuLoadInProgress = false;
    }

    private void logDanmakuLoad(String event, Uri uri, int count, IOException error) {
        if (!SpiderDebug.isEnabled()) return;
        long elapsed = danmakuLoadStartedAtMs <= 0 ? -1 : SystemClock.elapsedRealtime() - danmakuLoadStartedAtMs;
        if (error == null) {
            SpiderDebug.log("danmaku", "load %s count=%d elapsed=%dms url=%s", event, count, elapsed, summarizeUrl(uri == null ? "" : uri.toString()));
        } else {
            SpiderDebug.log("danmaku", "load %s elapsed=%dms url=%s error=%s", event, elapsed, summarizeUrl(uri == null ? "" : uri.toString()), error.getMessage());
        }
    }

    private static String normalizeDanmakuKey(String url) {
        if (TextUtils.isEmpty(url)) return "";
        String value = url.trim();
        try {
            Uri uri = Uri.parse(value);
            String nested = getNestedDanmakuUrl(uri);
            return TextUtils.isEmpty(nested) ? value : normalizeDanmakuKey(nested);
        } catch (Throwable e) {
            return value;
        }
    }

    private static String getNestedDanmakuUrl(Uri uri) {
        if (uri == null) return "";
        String path = uri.getPath();
        if (TextUtils.isEmpty(path) || !path.endsWith("/danmaku")) return "";
        return uri.getQueryParameter("url");
    }

    public void addDanmaku(Danmaku item) {
        if (danmakuController == null || item.isEmpty()) return;
        if (spec != null) spec.addDanmaku(item);
        if (currentDanmakuUrl == null) setDanmaku(item);
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        if (!TextUtils.isEmpty(from)) Notify.show(ResUtil.getString(R.string.parse_from, from));
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "parseSuccess from=%s url=%s headers=%s", from, summarizeUrl(url), headers == null ? 0 : headers.size());
        recordParseHealth(true, "");
        if (headers != null) headers.remove(HttpHeaders.RANGE);
        if (spec != null) spec.setHeaders(headers);
        if (spec != null) spec.setUrl(url);
        setMediaItem(Constant.TIMEOUT_PLAY);
        restoreAfterSwitchReparse();
    }

    @Override
    public void onParseError() {
        recordParseHealth(false, ResUtil.getString(R.string.error_play_parse));
        clearPendingSwitchRestore();
        callback.onError(ResUtil.getString(R.string.error_play_parse));
    }

    private void recordParseHealth(boolean success, String error) {
        if (parseHealthRecorded || spec == null) return;
        parseHealthRecorded = true;
        long cost = parseHealthStartedAt <= 0 ? 0 : System.currentTimeMillis() - parseHealthStartedAt;
        SiteHealthStore.recordParse(spec.getKey(), success, cost, error);
    }

    private String debugSpec() {
        if (spec == null) return "null";
        return "key=" + spec.getKey() +
                ", url=" + summarizeUrl(spec.getUrl()) +
                ", format=" + spec.getFormat() +
                ", headers=" + (spec.getHeaders() == null ? 0 : spec.getHeaders().size()) +
                ", subs=" + (spec.getSubs() == null ? 0 : spec.getSubs().size()) +
                ", danmakus=" + (spec.getDanmakus() == null ? 0 : spec.getDanmakus().size());
    }

    private static String summarizeUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        int port = uri.getPort();
        String path = uri.getPath();
        StringBuilder builder = new StringBuilder();
        builder.append(uri.getScheme()).append("://");
        builder.append(TextUtils.isEmpty(host) ? "unknown" : host);
        if (port > 0) builder.append(':').append(port);
        if (!TextUtils.isEmpty(path)) builder.append(path.length() > 48 ? path.substring(0, 48) + "..." : path);
        builder.append(" len=").append(url.length());
        return builder.toString();
    }

    private static String stateName(int state) {
        return switch (state) {
            case Player.STATE_IDLE -> "IDLE";
            case Player.STATE_BUFFERING -> "BUFFERING";
            case Player.STATE_READY -> "READY";
            case Player.STATE_ENDED -> "ENDED";
            default -> String.valueOf(state);
        };
    }

    private static String causeChain(Throwable error) {
        if (error == null) return "null";
        StringBuilder builder = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 8) {
            if (builder.length() > 0) builder.append(" <- ");
            builder.append(current.getClass().getName());
            if (!TextUtils.isEmpty(current.getMessage())) builder.append(": ").append(current.getMessage());
            current = current.getCause();
        }
        return builder.toString();
    }

    public interface Callback {

        void onPrepare();

        void onTracksChanged();

        void onTitlesChanged();

        default boolean onSourceHttpError(int statusCode, String msg) {
            return false;
        }

        void onError(String msg);

        void onReload(String msg);

        void onPlayerRebuild(Player newPlayer, boolean resetVideoSurface);
    }

    private final Player.Listener listener = new Player.Listener() {

        @Override
        public void onPlaybackStateChanged(int state) {
            if (state != Player.STATE_IDLE) App.removeCallbacks(runnable);
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "state=%s spec=%s", stateName(state), debugSpec());
            if (state == Player.STATE_READY) {
                manualPlayerSwitchPending = false;
                hardDecodeSwitchRetryArmed = false;
                clearLutWarmupRecovery();
                applyLutForCurrentItem();
            }
        }

        @Override
        public void onVideoSizeChanged(@NonNull VideoSize size) {
            videoSize = size;
            applyLutForCurrentItem();
        }

        @Override
        public void onTracksChanged(@NonNull Tracks tracks) {
            if (!tracks.isEmpty() && !initTrack) {
                List<Track> savedTracks = Track.find(getKey());
                setTrack(savedTracks);
                if (RealtimeSubtitleController.get().isEnabled()) disableSubtitleTrackForRealtime();
                if (PlayerSetting.isPreferAAC() && !TrackUtil.hasTrack(player, savedTracks, C.TRACK_TYPE_AUDIO)) TrackUtil.preferAAC(player);
                callback.onTracksChanged();
                initTrack = true;
            }
            applyLutForCurrentItem();
        }

        @Override
        public void onMediaEditionsChanged(@NonNull List<MediaEdition> editions) {
            callback.onTitlesChanged();
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException e) {
            App.removeCallbacks(runnable);
            PlayerEngine.ErrorAction action = engine.handleError(e);
            int statusCode = httpStatus(e);
            String errorMessage = engine.getErrorMessage(e);
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "error code=%d http=%d message=%s action=%s spec=%s cause=%s", e.errorCode, statusCode, e.getMessage(), action, debugSpec(), causeChain(e));
            LocalProxyDebug.dumpIfLocalFailure(spec == null ? null : spec.getUrl(), e);
            if (statusCode > 0 && callback.onSourceHttpError(statusCode, errorMessage)) return;
            if (retryLutFailure(e)) return;
            if (retryLutWarmupByRefresh(action, e)) return;
            if (action == PlayerEngine.ErrorAction.DECODE && retryHardDecodeSwitch(e)) return;
            if (action == PlayerEngine.ErrorAction.FATAL && retryLocalProxy(e)) return;
            if (shouldStopOnManualSwitchFailure(manualPlayerSwitchPending, action)) {
                callback.onError(errorMessage);
                return;
            }
            if (action == PlayerEngine.ErrorAction.RELOAD) {
                callback.onReload(errorMessage);
                return;
            }
            if (action == PlayerEngine.ErrorAction.RECOVERED) {
                if (spec != null) setDanmakus(spec.getDanmakus());
                return;
            }
            if (fallbackPlayback(e)) return;
            callback.onError(errorMessage);
        }
    };

    private void onPlaybackTimeout() {
        PlaybackException e = new PlaybackException(ResUtil.getString(R.string.error_play_timeout), null, PlaybackException.ERROR_CODE_TIMEOUT);
        if (retryLutWarmupByRefresh("timeout")) return;
        if (manualPlayerSwitchPending) {
            callback.onError(ResUtil.getString(R.string.error_play_timeout));
            return;
        }
        if (fallbackPlayback(e)) return;
        callback.onError(ResUtil.getString(R.string.error_play_timeout));
    }

    private boolean retryHardDecodeSwitch(PlaybackException e) {
        if (!hardDecodeSwitchRetryArmed || engine == null || player == null || spec == null || !engine.isHard()) return false;
        if (e.errorCode != PlaybackException.ERROR_CODE_DECODER_INIT_FAILED && e.errorCode != PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED && e.errorCode != PlaybackException.ERROR_CODE_DECODING_FAILED) return false;
        hardDecodeSwitchRetryArmed = false;
        int seq = ++prepareSeq;
        PlaySpec target = spec;
        long position = Math.max(0, getPosition());
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        App.removeCallbacks(runnable);
        resetLutRuntimeState("hard_decode_switch_retry", true);
        engine.release();
        engine = buildEngine(playerType, PlayerEngine.HARD);
        player = engine.getPlayer();
        callback.onPlayerRebuild(player, true);
        this.playWhenReady = wasPlayWhenReady;
        initTrack = false;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "hard decode switch retry delay=%d position=%d spec=%s cause=%s", HARD_DECODE_SWITCH_RETRY_DELAY_MS, position, debugSpec(), causeChain(e));
        App.post(() -> {
            if (seq != prepareSeq || spec != target || engine == null || player == null || !engine.isHard()) return;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "hard decode switch retry start position=%d spec=%s", position, debugSpec());
            setDanmakus(target.getDanmakus());
            initTrack = false;
            waitingLutBeforePlay = false;
            applySubtitleStyle();
            engine.start(target.checkUa(), position, wasPlayWhenReady);
            if (speed != 1f) setSpeed(speed);
            setRepeatOne(repeat);
            App.post(runnable, Constant.TIMEOUT_PLAY);
            callback.onPrepare();
        }, HARD_DECODE_SWITCH_RETRY_DELAY_MS);
        return true;
    }

    private boolean retryLutFailure(PlaybackException e) {
        if (!LutSetting.isEnabled()) return false;
        if (e.errorCode != PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED && e.errorCode != PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED) return false;
        App.removeCallbacks(runnable);
        LutSetting.select(null);
        lutAppliedForItem = true;
        lutApplyInProgress = false;
        pendingLutPreview = false;
        lutWarmupReloadPreviewPending = false;
        clearVideoEffects("lut_error_retry");
        Notify.show(R.string.lut_apply_failed);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "disable and retry after frame processing error code=%d spec=%s cause=%s", e.errorCode, debugSpec(), causeChain(e));
        if (spec != null) setMediaItem();
        return true;
    }

    private boolean retryLocalProxy(PlaybackException e) {
        if (spec == null || !LocalProxyDebug.isLocalProxyUrl(spec.getUrl())) return false;
        if (!LocalProxyDebug.isConnectionRefused(e)) return false;
        if (++localProxyRetry > LOCAL_PROXY_MAX_RETRY) return false;
        int attempt = localProxyRetry;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "local proxy retry schedule attempt=%d delay=%d spec=%s", attempt, LOCAL_PROXY_RETRY_DELAY_MS, debugSpec());
        App.removeCallbacks(runnable);
        App.post(() -> {
            if (spec == null || attempt != localProxyRetry) return;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "local proxy retry start attempt=%d spec=%s", attempt, debugSpec());
            setMediaItem();
        }, LOCAL_PROXY_RETRY_DELAY_MS);
        return true;
    }

    private boolean fallbackPlayback(PlaybackException e) {
        if (engine == null) return false;
        return switch (nextFallbackAction(PlayerSetting.getFailureFallback(), engine.getDecode())) {
            case FALLBACK_DECODE -> fallbackDecode(e);
            case FALLBACK_PLAYER -> fallbackPlayer(e);
            default -> false;
        };
    }

    private boolean fallbackDecode(PlaybackException e) {
        if (spec == null || spec.getUrl() == null || engine == null || !engine.isHard()) return false;
        SpiderDebug.log("player", "fallback decode player=%s from=hard to=soft spec=%s cause=%s", getPlayerText(playerType), debugSpec(), causeChain(e));
        App.removeCallbacks(runnable);
        localProxyRetry = 0;
        hardDecodeSwitchRetryArmed = false;
        switchEngine(playerType, false, true, true, PlayerEngine.SOFT);
        return true;
    }

    private boolean fallbackPlayer(PlaybackException e) {
        if (spec == null || spec.getUrl() == null || engine == null) return false;
        int next = nextFallbackPlayer();
        if (next == PlayerSetting.NONE) return false;
        String from = getPlayerText(playerType);
        String to = getPlayerText(next);
        SpiderDebug.log("player", "fallback player from=%s to=%s spec=%s cause=%s", from, to, debugSpec(), causeChain(e));
        App.removeCallbacks(runnable);
        localProxyRetry = 0;
        switchEngine(next, false, true, true, fallbackDecode(PlayerSetting.getFailureFallback(), playerType, next, engine.getDecode()));
        return true;
    }

    private boolean ensurePlayerAvailableForPlayback() {
        if (PlayerSetting.isPlayerAvailable(playerType)) return true;
        int from = playerType;
        int next = nextFallbackPlayer();
        if (next == PlayerSetting.NONE) {
            callback.onError(ResUtil.getString(R.string.error_play_ijk_unavailable));
            return false;
        }
        logUnavailablePlayer(from, next);
        switchEngine(next, false, false, true);
        return false;
    }

    private int nextFallbackPlayer() {
        markPlayerFallbackTried(playerType);
        int next = PlayerSetting.nextPlayer(playerType);
        while (next != playerType) {
            if (!isPlayerFallbackTried(next)) {
                markPlayerFallbackTried(next);
                if (PlayerSetting.isPlayerAvailable(next)) return next;
            }
            next = PlayerSetting.nextPlayer(next);
        }
        return PlayerSetting.NONE;
    }

    static int nextFallbackAction(int mode, int decode) {
        int currentDecode = sanitizeDecode(decode);
        return switch (mode) {
            case PlayerSetting.FALLBACK_DECODE_ONLY -> currentDecode == PlayerEngine.HARD ? FALLBACK_DECODE : FALLBACK_NONE;
            case PlayerSetting.FALLBACK_PLAYER_ONLY -> FALLBACK_PLAYER;
            case PlayerSetting.FALLBACK_DISABLED -> FALLBACK_NONE;
            default -> currentDecode == PlayerEngine.HARD ? FALLBACK_DECODE : FALLBACK_PLAYER;
        };
    }

    static int fallbackDecode(int mode, int from, int to, int decode) {
        int currentDecode = sanitizeDecode(decode);
        if (from == to || mode == PlayerSetting.FALLBACK_PLAYER_ONLY) return currentDecode;
        return PlayerEngine.HARD;
    }

    private static int sanitizeDecode(int decode) {
        return decode == PlayerEngine.SOFT ? PlayerEngine.SOFT : PlayerEngine.HARD;
    }

    private int resolveAvailablePlayer(int type) {
        if (PlayerSetting.isPlayerAvailable(type)) return type;
        int next = PlayerSetting.nextPlayer(type);
        while (next != type) {
            if (PlayerSetting.isPlayerAvailable(next)) {
                logUnavailablePlayer(type, next);
                return next;
            }
            next = PlayerSetting.nextPlayer(next);
        }
        return playerType;
    }

    private int resolveManualPlayer(int type) {
        if (PlayerSetting.isPlayerAvailable(type)) return type;
        SpiderDebug.log("player", "manual player unavailable type=%s package=%s", getPlayerText(type), App.get().getPackageName());
        Notify.show(ResUtil.getString(R.string.error_play_ijk_unavailable));
        return playerType;
    }

    private void logUnavailablePlayer(int from, int to) {
        SpiderDebug.log("player", "player unavailable from=%s to=%s package=%s", getPlayerText(from), getPlayerText(to), App.get().getPackageName());
    }

    private void resetPlayerFallback() {
        playerFallbackTried = new boolean[PLAYER_COUNT];
    }

    private void markPlayerFallbackTried(int type) {
        if (type >= 0 && type < playerFallbackTried.length) playerFallbackTried[type] = true;
    }

    private boolean isPlayerFallbackTried(int type) {
        return type >= 0 && type < playerFallbackTried.length && playerFallbackTried[type];
    }

    static boolean shouldStopOnManualSwitchFailure(boolean manualSwitchPending, PlayerEngine.ErrorAction action) {
        return manualSwitchPending && action != PlayerEngine.ErrorAction.RECOVERED;
    }

    static int httpStatus(Throwable error) {
        int depth = 0;
        for (Throwable cause = error; cause != null && depth++ < 8; cause = cause.getCause()) {
            if (cause instanceof HttpDataSource.InvalidResponseCodeException response) return response.responseCode;
            String message = cause.getMessage();
            if (TextUtils.isEmpty(message)) continue;
            Matcher matcher = HTTP_STATUS.matcher(message);
            if (!matcher.find()) continue;
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }
}
