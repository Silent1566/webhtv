package com.fongmi.android.tv.player.engine;

import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.SimpleBasePlayer;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.mpvplayer.MpvHlsProxy;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.player.cache.PlaybackDiskBufferStore;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.player.PlaybackResourceClassifier;
import com.fongmi.android.tv.player.PlaybackRoute;
import com.fongmi.android.tv.player.ijk.IjkBufferPolicy;
import com.fongmi.android.tv.player.ijk.IjkDecodePressurePolicy;
import com.fongmi.android.tv.player.ijk.IjkRealtimeRecoveryPolicy;
import com.fongmi.android.tv.setting.IjkPerformanceSetting;
import com.fongmi.android.tv.setting.PlaybackPerformanceCatalog;
import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.IjkTimedText;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;

@UnstableApi
class IjkSimplePlayer extends SimpleBasePlayer implements IjkPlayerListener {

    private static final long STATE_REFRESH_INTERVAL_MS = 1000;
    private static final long SUBTITLE_REFRESH_INTERVAL_MS = 250;

    private static final Commands COMMANDS = new Commands.Builder()
            .add(COMMAND_PLAY_PAUSE)
            .add(COMMAND_PREPARE)
            .add(COMMAND_STOP)
            .add(COMMAND_RELEASE)
            .add(COMMAND_SET_REPEAT_MODE)
            .add(COMMAND_GET_CURRENT_MEDIA_ITEM)
            .add(COMMAND_GET_TIMELINE)
            .add(COMMAND_GET_METADATA)
            .add(COMMAND_SET_MEDIA_ITEM)
            .add(COMMAND_CHANGE_MEDIA_ITEMS)
            .add(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .add(COMMAND_SEEK_TO_DEFAULT_POSITION)
            .add(COMMAND_GET_VOLUME)
            .add(COMMAND_SET_VOLUME)
            .add(COMMAND_SET_SPEED_AND_PITCH)
            .add(COMMAND_SET_VIDEO_SURFACE)
            .add(COMMAND_GET_TRACKS)
            .build();

    private final IjkMediaPlayer ijk;
    private final MpvHlsProxy hlsProxy;
    private final Runnable stateRefreshRunnable;
    private MediaItem mediaItem;
    private SurfaceHolder surfaceHolder;
    private Surface surface;
    private Object videoOutput;
    /** TextureView 的 SurfaceTexture 就绪晚于 setPlayer；首绑拿不到时记下，等 prepared/重绑点补绑。 */
    private TextureView pendingTextureView;
    private PlaybackParameters playbackParameters;
    private PlaybackException playerError;
    private Tracks currentTracks;
    private Format selectedVideoFormat;
    private Format selectedAudioFormat;
    private VideoSize videoSize;
    private IjkSubtitleTrack subtitleTrack;
    private CueGroup currentCues;
    private Future<?> subtitleLoad;
    private int playbackState;
    private int bufferingPercent;
    private long bufferingPositionMs;
    private int decode;
    private int subtitleSerial;
    private long pendingSeekPositionMs;
    private long pendingSeekRequestedAtMs;
    private boolean playWhenReady;
    private boolean loading;
    private boolean repeatOne;
    private boolean ownsSurface;
    private boolean currentDash;
    private volatile boolean resourceObservationActive;
    private volatile PlaybackResourceClassifier.Classification resourceClassification;
    private volatile String currentPlayableUrl;
    private volatile IjkBufferPolicy.Config automaticInputBufferConfig;
    private volatile IjkBufferPolicy.Config appliedInputBufferConfig;
    private volatile IjkDecodePressurePolicy.Config automaticDecodeControlConfig;
    private volatile IjkDecodePressurePolicy.Config appliedDecodeControlConfig;
    private IjkPlayerEngine.ErrorSnapshot lastErrorSnapshot;
    private volatile IjkPlayerEngine.OpenStage openStage;
    private volatile int lastHttpStatus;
    private volatile long lastNativeOffset;
    private volatile boolean longUrlProxied;
    private boolean prepared;
    private boolean newlyRenderedFirstFrame;
    private boolean renderedFirstFrameSeen;
    private float volume;
    private final com.fongmi.android.tv.player.PlaybackDiagnosticCollector diagnostics =
            new com.fongmi.android.tv.player.PlaybackDiagnosticCollector("ijk", "packaged-IjkMediaPlayer");
    private String diagnosticTrace = "none";
    private long lastDiagnosticMs;

    void setDiagnosticTrace(String trace) { diagnosticTrace = trace; }

    private void diagnosticEvent(String stage, int what, int extra, boolean error) {
        diagnostics.emit(diagnostics.context(), "ijk.event", "IMediaPlayer.Listener", "ijk-instance; callback-media-unconfirmed", e -> e
                .severity(error ? "error" : "info").observed("stage", stage).observed("value", what).observed("errorCode", extra)
                .observed("state", playbackState).observed("playWhenReady", playWhenReady)
                .unknown("physicalVideo", com.github.catvod.crawler.diagnostics.DiagnosticEvent.Status.NOT_OBSERVABLE)
                .unknown("audibility", com.github.catvod.crawler.diagnostics.DiagnosticEvent.Status.NOT_OBSERVABLE));
    }

    IjkSimplePlayer(int decode) {
        super(Looper.getMainLooper());
        this.decode = decode;
        ijk = new IjkMediaPlayer();
        ijk.setLogEnabled(SpiderDebug.isEnabled());
        // AAR 内核没有 setListener 聚合方法，逐一注册回调。
        ijk.setOnPreparedListener(this);
        ijk.setOnCompletionListener(this);
        ijk.setOnErrorListener(this);
        ijk.setOnInfoListener(this);
        ijk.setOnBufferingUpdateListener(this);
        ijk.setOnVideoSizeChangedListener(this);
        ijk.setOnTimedTextListener(this);
        ijk.setOnNativeInvokeListener(this::onNativeInvoke);
        hlsProxy = new MpvHlsProxy(PlayerSetting.IJK);
        stateRefreshRunnable = this::refreshPlaybackState;
        playbackParameters = PlaybackParameters.DEFAULT;
        currentTracks = Tracks.EMPTY;
        videoSize = VideoSize.UNKNOWN;
        subtitleTrack = IjkSubtitleTrack.EMPTY;
        currentCues = CueGroup.EMPTY_TIME_ZERO;
        playbackState = Player.STATE_IDLE;
        pendingSeekPositionMs = C.TIME_UNSET;
        pendingSeekRequestedAtMs = C.TIME_UNSET;
        playWhenReady = true;
        volume = 1f;
        automaticInputBufferConfig = IjkBufferPolicy.safeInitialConfig();
        appliedInputBufferConfig = IjkBufferPolicy.safeInitialConfig();
        automaticDecodeControlConfig = IjkDecodePressurePolicy.automaticInitialConfig();
        appliedDecodeControlConfig = IjkDecodePressurePolicy.automaticInitialConfig();
        lastErrorSnapshot = IjkPlayerEngine.ErrorSnapshot.none();
        resetOpenDiagnostics();
    }

    @Override
    protected State getState() {
        int state = playbackState;
        boolean isLoading = loading && state != Player.STATE_IDLE && state != Player.STATE_ENDED;
        boolean firstFrameEvent = newlyRenderedFirstFrame;
        newlyRenderedFirstFrame = false;
        State.Builder builder = new State.Builder()
                .setAvailableCommands(COMMANDS)
                .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .setPlaybackState(state)
                .setIsLoading(isLoading)
                .setPlayerError(playerError)
                .setRepeatMode(repeatOne ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF)
                .setPlaybackParameters(playbackParameters)
                .setVideoSize(videoSize)
                .setCurrentCues(currentCues)
                .setNewlyRenderedFirstFrame(firstFrameEvent)
                .setVolume(volume)
                .setPlaylist(mediaItem == null ? ImmutableList.of() : ImmutableList.of(mediaItemData()))
                .setCurrentMediaItemIndex(mediaItem == null ? C.INDEX_UNSET : 0);
        if (mediaItem != null) {
            long duration = duration();
            long position = position();
            long buffered = bufferedPosition(position, duration);
            builder.setContentPositionMs(isPlayingInternal() ? PositionSupplier.getExtrapolating(position, playbackParameters.speed) : PositionSupplier.getConstant(position));
            builder.setContentBufferedPositionMs(PositionSupplier.getConstant(buffered));
            builder.setTotalBufferedDurationMs(PositionSupplier.getConstant(Math.max(0, buffered - position)));
        }
        return builder.build();
    }

    private MediaItemData mediaItemData() {
        long duration = duration();
        IjkStreamScenePolicy.Decision scene = streamSceneDecision(duration);
        MediaItemData.Builder builder = new MediaItemData.Builder(mediaItem.mediaId)
                .setMediaItem(mediaItem)
                .setMediaMetadata(mediaItem.mediaMetadata)
                .setDurationUs(duration == C.TIME_UNSET ? C.TIME_UNSET : duration * 1000)
                .setIsSeekable(scene.seekable())
                .setIsDynamic(scene.dynamic())
                .setManifest(scene.timelineSnapshot())
                .setTracks(currentTracks);
        if (scene.authoritative() && scene.live()) {
            MediaItem.LiveConfiguration.Builder live =
                    new MediaItem.LiveConfiguration.Builder();
            if (scene.targetOffsetMs() != C.TIME_UNSET) {
                live.setTargetOffsetMs(scene.targetOffsetMs());
            }
            builder.setLiveConfiguration(live.build());
        }
        return builder.build();
    }

    Tracks getCurrentTracksSnapshot() {
        return currentTracks;
    }

    @Nullable
    Format getSelectedVideoFormatSnapshot() {
        return selectedVideoFormat;
    }

    @Nullable
    Format getSelectedAudioFormatSnapshot() {
        return selectedAudioFormat;
    }

    String getVideoCodecInfoSnapshot() {
        return codecName("video");
    }

    String getAudioCodecInfoSnapshot() {
        return codecName("audio");
    }

    /** 从 getMediaMeta() 的流元数据里取指定类型的 codec 名（AAR 内核无 getVideoCodecInfo）。 */
    private String codecName(String type) {
        try {
            android.os.Bundle bundle = ijk.getMediaMeta();
            if (bundle == null) return "";
            tv.danmaku.ijk.media.player.IjkMediaMeta meta =
                    tv.danmaku.ijk.media.player.IjkMediaMeta.parse(bundle);
            if (meta == null || meta.mStreams == null) return "";
            for (tv.danmaku.ijk.media.player.IjkMediaMeta.IjkStreamMeta stream : meta.mStreams) {
                if (type.equals(stream.mType) && !TextUtils.isEmpty(stream.mCodecName)) {
                    return stream.mCodecName;
                }
            }
        } catch (Throwable error) {
            // meta 不可用时静默降级
        }
        return "";
    }

    int getVideoDecoderSnapshot() {
        try {
            return ijk.getVideoDecoder();
        } catch (Throwable error) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("ijk", "decoder fact unavailable type=%s", error.getClass().getSimpleName());
            return IjkMediaPlayer.FFP_PROPV_DECODER_UNKNOWN;
        }
    }

    IjkPlayerEngine.ErrorSnapshot getLastErrorSnapshot() {
        return lastErrorSnapshot;
    }

    IjkPlayerEngine.DropRateSnapshot getDropRateSnapshot() {
        try {
            float rate = ijk.getDropFrameRate();
            float decodeFps = ijk.getVideoDecodeFramesPerSecond();
            boolean available = prepared
                    && renderedFirstFrameSeen
                    && Float.isFinite(rate)
                    && rate >= 0f
                    && Float.isFinite(decodeFps)
                    && decodeFps > 0f;
            int permille = available
                    ? (int) Math.max(0,
                    Math.min(10_000, Math.round(rate * 1_000f)))
                    : 0;
            return new IjkPlayerEngine.DropRateSnapshot(
                    available, permille);
        } catch (Throwable error) {
            if (SpiderDebug.isEnabled()) {
                SpiderDebug.log(
                        "ijk-runtime-profile",
                        "drop-rate unavailable errorType=%s action=keep-unknown",
                        error.getClass().getSimpleName());
            }
            return IjkPlayerEngine.DropRateSnapshot.unknown();
        }
    }

    long getTcpSpeedSnapshot() {
        try {
            return Math.max(0, ijk.getTcpSpeed());
        } catch (Throwable error) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("ijk", "tcp speed fact unavailable type=%s", error.getClass().getSimpleName());
            return 0;
        }
    }

    long getBitrateSnapshot() {
        try {
            return Math.max(0, ijk.getBitRate());
        } catch (Throwable error) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("ijk", "bitrate fact unavailable type=%s", error.getClass().getSimpleName());
            return 0;
        }
    }

    Long getLiveLagLowerBoundSnapshot() {
        MpvHlsProxy.LiveLagSnapshot snapshot = hlsProxy.liveLagSnapshot(
                getNativeBufferedDurationSnapshot());
        return snapshot.known() ? snapshot.lowerBoundMs() : null;
    }

    IjkRealtimeRecoveryPolicy.QueueSnapshot getRealtimeQueueSnapshot() {
        try {
            Tracks tracks = currentTracks;
            return new IjkRealtimeRecoveryPolicy.QueueSnapshot(
                    true,
                    tracks.containsType(C.TRACK_TYPE_AUDIO),
                    tracks.containsType(C.TRACK_TYPE_VIDEO),
                    ijk.getAudioCachedDuration(),
                    ijk.getVideoCachedDuration(),
                    ijk.getAudioCachedBytes(),
                    ijk.getVideoCachedBytes(),
                    ijk.getAudioCachedPackets(),
                    ijk.getVideoCachedPackets());
        } catch (Throwable error) {
            if (SpiderDebug.isEnabled()) {
                SpiderDebug.log("ijk-realtime",
                        "queue facts unavailable errorType=%s action=keep-unknown",
                        error.getClass().getSimpleName());
            }
            return IjkRealtimeRecoveryPolicy.QueueSnapshot.unknown();
        }
    }

    /** 是否真的出过画面（MEDIA_INFO_VIDEO_RENDERING_START）。有声音但一直没这个回调即为黑屏。 */
    boolean hasRenderedFirstFrame() {
        return renderedFirstFrameSeen;
    }

    IjkDecodePressurePolicy.DecodeSnapshot getDecodePressureSnapshot() {
        try {
            float decodeFps = ijk.getVideoDecodeFramesPerSecond();
            float outputFps = ijk.getVideoOutputFramesPerSecond();
            return new IjkDecodePressurePolicy.DecodeSnapshot(
                    true, decodeFps, outputFps);
        } catch (Throwable error) {
            if (SpiderDebug.isEnabled()) {
                SpiderDebug.log("ijk-decode",
                        "fps facts unavailable errorType=%s action=keep-unknown",
                        error.getClass().getSimpleName());
            }
            return IjkDecodePressurePolicy.DecodeSnapshot.unknown();
        }
    }

    private long getNativeBufferedDurationSnapshot() {
        try {
            long audio = Math.max(0, ijk.getAudioCachedDuration());
            long video = Math.max(0, ijk.getVideoCachedDuration());
            Tracks tracks = currentTracks;
            return IjkBufferedDurationPolicy.resolve(
                    tracks.containsType(C.TRACK_TYPE_AUDIO),
                    tracks.containsType(C.TRACK_TYPE_VIDEO),
                    audio,
                    video);
        } catch (Throwable error) {
            if (SpiderDebug.isEnabled()) {
                SpiderDebug.log("ijk-buffer",
                        "buffer-duration unavailable errorType=%s action=keep-unknown",
                        error.getClass().getSimpleName());
            }
            return 0;
        }
    }

    void setDecode(int decode) {
        this.decode = decode;
    }

    void stageAutomaticInputBufferConfig(IjkBufferPolicy.Config config) {
        automaticInputBufferConfig = config == null
                ? IjkBufferPolicy.safeInitialConfig() : config;
    }

    IjkBufferPolicy.Config getAppliedInputBufferConfig() {
        return appliedInputBufferConfig;
    }

    void stageAutomaticDecodeControlConfig(
            IjkDecodePressurePolicy.Config config) {
        automaticDecodeControlConfig = config == null
                ? IjkDecodePressurePolicy.automaticInitialConfig() : config;
    }

    IjkDecodePressurePolicy.Config getAppliedDecodeControlConfig() {
        return appliedDecodeControlConfig;
    }

    @Override
    protected ListenableFuture<?> handleSetMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
        clearSubtitles();
        invalidateResourceObservation();
        mediaItem = mediaItems.isEmpty() ? null : mediaItems.get(0);
        setPendingSeek(mediaItem != null && startPositionMs > 0 ? startPositionMs : C.TIME_UNSET);
        playbackState = mediaItem == null ? Player.STATE_IDLE : Player.STATE_IDLE;
        loading = false;
        currentTracks = Tracks.EMPTY;
        selectedVideoFormat = null;
        selectedAudioFormat = null;
        playerError = null;
        prepared = false;
        bufferingPercent = 0;
        bufferingPositionMs = 0;
        newlyRenderedFirstFrame = false;
        renderedFirstFrameSeen = false;
        lastErrorSnapshot = IjkPlayerEngine.ErrorSnapshot.none();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleAddMediaItems(int index, List<MediaItem> mediaItems) {
        clearSubtitles();
        invalidateResourceObservation();
        mediaItem = mediaItems.isEmpty() ? null : mediaItems.get(0);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleReplaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems) {
        clearSubtitles();
        if (mediaItems.isEmpty()) invalidateResourceObservation();
        mediaItem = mediaItems.isEmpty() ? null : mediaItems.get(0);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleRemoveMediaItems(int fromIndex, int toIndex) {
        clearSubtitles();
        invalidateResourceObservation();
        mediaItem = null;
        playbackState = Player.STATE_IDLE;
        loading = false;
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handlePrepare() {
        openCurrent();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
        this.playWhenReady = playWhenReady;
        hlsProxy.setPlaybackPaused(!playWhenReady);
        if (playbackState == Player.STATE_READY) {
            if (playWhenReady) ijk.start();
            else ijk.pause();
        }
        if (!playWhenReady) requestPreload(Math.max(0, position()));
        diagnosticEvent("play-when-ready", playWhenReady ? 1 : 0, 0, false);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleStop() {
        diagnostics.end("stop-request");
        stopInternal(true);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleRelease() {
        diagnostics.end("release-request");
        if (currentDash) {
            releaseDashSafely();
            return Futures.immediateVoidFuture();
        }
        stopInternal(false);
        hlsProxy.release();
        ijk.release();
        return Futures.immediateVoidFuture();
    }

    private void releaseDashSafely() {
        stopStateRefresh();
        loading = false;
        playWhenReady = false;
        currentTracks = Tracks.EMPTY;
        selectedVideoFormat = null;
        selectedAudioFormat = null;
        videoSize = VideoSize.UNKNOWN;
        try {
            ijk.resetListeners();
            clearVideoOutput();
            ijk.stop();
        } catch (Throwable e) {
            SpiderDebug.log("ijk",
                    "dash switch stop failed errorType=%s action=continue-release",
                    e.getClass().getSimpleName());
        }
        SpiderDebug.log("ijk", "dash switch deferred release scheduled");
        Task.schedule(() -> {
            try {
                ijk.release();
                SpiderDebug.log("ijk", "dash switch deferred release complete");
            } catch (Throwable e) {
                SpiderDebug.log("ijk",
                        "dash switch deferred release failed errorType=%s action=release-proxy",
                        e.getClass().getSimpleName());
            } finally {
                hlsProxy.release();
            }
        }, 800, TimeUnit.MILLISECONDS);
    }

    @Override
    protected ListenableFuture<?> handleSetRepeatMode(int repeatMode) {
        repeatOne = repeatMode == Player.REPEAT_MODE_ONE;
        ijk.setLooping(repeatOne);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSeek(int mediaItemIndex, long positionMs, int seekCommand) {
        if (positionMs == C.TIME_UNSET) positionMs = 0;
        bufferingPercent = 0;
        bufferingPositionMs = Math.max(0, positionMs);
        setPendingSeek(positionMs > 0 ? positionMs : C.TIME_UNSET);
        if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
            ijk.seekTo(positionMs);
        }
        updateCurrentCues(positionMs);
        requestPreload(positionMs);
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetPlaybackParameters(PlaybackParameters playbackParameters) {
        this.playbackParameters = playbackParameters;
        ijk.setSpeed(playbackParameters.speed);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetVolume(float volume, int volumeOperationType) {
        this.volume = volume;
        ijk.setVolume(volume, volume);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetVideoOutput(Object videoOutput) {
        this.videoOutput = videoOutput;
        setVideoOutput(videoOutput);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleClearVideoOutput(@Nullable Object videoOutput) {
        if (videoOutput == null || videoOutput == this.videoOutput) {
            this.videoOutput = null;
            clearVideoOutput();
        }
        return Futures.immediateVoidFuture();
    }

    @Override
    public void onPrepared(IMediaPlayer mp) {
        com.fongmi.android.tv.player.PlaybackSwitchLog.log("ijk", "PREPARED OK stage=prepared url=%s",
                currentPlayableUrl == null ? "null" : currentPlayableUrl.substring(0, Math.min(200, currentPlayableUrl.length())));
        diagnosticEvent("prepared; not physical output", 0, 0, false);
        prepared = true;
        advanceOpenStage(IjkPlayerEngine.OpenStage.PREPARED);
        playbackState = Player.STATE_READY;
        loading = false;
        playerError = null;
        refreshTracks();
        if (pendingSeekPositionMs != C.TIME_UNSET) {
            ijk.seekTo(pendingSeekPositionMs);
            updateCurrentCues(pendingSeekPositionMs);
            pendingSeekPositionMs = C.TIME_UNSET;
        } else {
            updateCurrentCues(position());
        }
        requestPreload(Math.max(0, position()));
        rebindVideoOutput("prepared");
        if (playWhenReady) ijk.start();
        invalidateState();
        startStateRefresh();
    }

    @Override
    public void onCompletion(IMediaPlayer mp) {
        diagnostics.end("completion");
        setPendingSeek(C.TIME_UNSET);
        playbackState = Player.STATE_ENDED;
        loading = false;
        currentCues = CueGroup.EMPTY_TIME_ZERO;
        stopStateRefresh();
        invalidateState();
    }

    @Override
    public boolean onError(IMediaPlayer mp, int what, int extra) {
        diagnosticEvent(openStage.label(), what, extra, true);
        lastErrorSnapshot = new IjkPlayerEngine.ErrorSnapshot(
                what,
                extra,
                prepared,
                openStage,
                lastHttpStatus,
                lastNativeOffset,
                longUrlProxied);
        setPendingSeek(C.TIME_UNSET);
        playbackState = Player.STATE_IDLE;
        loading = false;
        stopStateRefresh();
        clearSubtitles();
        playerError = new PlaybackException(
                "IJK playback failed stage=" + openStage.label(),
                null,
                IjkErrorMappingPolicy.resolve(
                        what, extra, prepared, openStage, lastHttpStatus));
        SpiderDebug.log("ijk",
                "error what=%d extra=%d mapped=%d decode=%d prepared=%s stage=%s http=%d offset=%d longProxy=%s action=notify",
                what,
                extra,
                playerError.errorCode,
                decode,
                prepared,
                openStage.label(),
                lastHttpStatus,
                lastNativeOffset,
                longUrlProxied);
        dumpNativeLogTail();
        if (BuildConfig.DEBUG) Log.e("WebHTV-IJK",
                "error what=" + what + " extra=" + extra
                        + " stage=" + openStage.label()
                        + " http=" + lastHttpStatus
                        + " uri=" + summarizeUri());
        prepared = false;
        invalidateState();
        return true;
    }

    /**
     * IJK native 层的报错（avformat/解码器初始化等）没有 Java 回调，只落在本进程
     * logcat 缓冲区。失败路径调用本方法把相关日志 dump 进 SpiderDebug（ijk-native 行），
     * 调试日志即可直接给出 FFmpeg/ijkplayer 的真实错误。仅在失败时调用；读取失败或
     * 0 行时静默跳过，不影响播放流程。仅读本进程缓冲（--pid），不触其他应用日志。
     */
    private static void dumpNativeLogTail() {
        if (!SpiderDebug.isEnabled()) return;
        Process process = null;
        try {
            // 不做关键词过滤：FFmpeg 的关键报错（如 "Server returned 5XX"、"Connection
            // refused"、"could not find codec parameters"）不含 ijk/ffp/hls 等词，
            // 按词过滤会把它漏掉（598 前的日志因此丢失了真实报错）。-t 200 取最近的
            // 200 行（含失败瞬间），SpiderDebug 输出层会自行脱敏 URL。
            process = Runtime.getRuntime().exec(new String[]{
                    "logcat", "-d", "-t", "200",
                    "--pid", String.valueOf(android.os.Process.myPid())});
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));
            String line;
            int dumped = 0;
            while ((line = reader.readLine()) != null && dumped < 200) {
                SpiderDebug.log("ijk-native", "%s", line);
                dumped++;
            }
            SpiderDebug.log("ijk", "native log dump lines=%d", dumped);
        } catch (Throwable t) {
            SpiderDebug.log("ijk-native", "dump failed: %s", t.toString());
        } finally {
            if (process != null) process.destroy();
        }
    }

    @Override
    public boolean onInfo(IMediaPlayer mp, int what, int extra) {
        diagnosticEvent(what == IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START ? "video-rendering-start"
                : what == IMediaPlayer.MEDIA_INFO_AUDIO_RENDERING_START ? "audio-rendering-start" : "info", what, extra, false);
        if (what == IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) diagnostics.evidence(diagnostics.context(), true, 3);
        if (what == IMediaPlayer.MEDIA_INFO_AUDIO_RENDERING_START) diagnostics.evidence(diagnostics.context(), false, 3);
        if (what == IMediaPlayer.MEDIA_INFO_OPEN_INPUT) {
            advanceOpenStage(IjkPlayerEngine.OpenStage.INPUT_OPENED);
        } else if (what == IMediaPlayer.MEDIA_INFO_FIND_STREAM_INFO) {
            advanceOpenStage(IjkPlayerEngine.OpenStage.STREAM_INFO);
        } else if (what == IMediaPlayer.MEDIA_INFO_COMPONENT_OPEN) {
            advanceOpenStage(IjkPlayerEngine.OpenStage.COMPONENT_OPENED);
        }
        if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
            loading = true;
            playbackState = Player.STATE_BUFFERING;
            startStateRefresh();
        } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END
                || what == IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
            loading = false;
            playbackState = Player.STATE_READY;
            startStateRefresh();
        }
        if (what == IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START
                && !renderedFirstFrameSeen) {
            renderedFirstFrameSeen = true;
            newlyRenderedFirstFrame = true;
            advanceOpenStage(IjkPlayerEngine.OpenStage.FIRST_FRAME);
        }
        if (what == IMediaPlayer.MEDIA_INFO_OPEN_INPUT || what == IMediaPlayer.MEDIA_INFO_FIND_STREAM_INFO
                || what == IMediaPlayer.MEDIA_INFO_COMPONENT_OPEN
                || what == IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START
                || what == IMediaPlayer.MEDIA_INFO_BUFFERING_START || what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {
            String infoName = what == IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START ? "FIRST_FRAME"
                    : what == IMediaPlayer.MEDIA_INFO_OPEN_INPUT ? "OPEN_INPUT"
                    : what == IMediaPlayer.MEDIA_INFO_FIND_STREAM_INFO ? "FIND_STREAM_INFO"
                    : what == IMediaPlayer.MEDIA_INFO_COMPONENT_OPEN ? "COMPONENT_OPEN"
                    : what == IMediaPlayer.MEDIA_INFO_BUFFERING_START ? "BUFFERING_START" : "BUFFERING_END";
            com.fongmi.android.tv.player.PlaybackSwitchLog.log("ijk", "info=%s stage=%s http=%d firstFrame=%s",
                    infoName, openStage.label(), lastHttpStatus, renderedFirstFrameSeen);
        }
        if (SpiderDebug.isEnabled()
                && (what == IMediaPlayer.MEDIA_INFO_OPEN_INPUT
                || what == IMediaPlayer.MEDIA_INFO_FIND_STREAM_INFO
                || what == IMediaPlayer.MEDIA_INFO_COMPONENT_OPEN)) {
            SpiderDebug.log("ijk",
                    "open stage=%s info=%d extra=%d http=%d longProxy=%s",
                    openStage.label(), what, extra, lastHttpStatus,
                    longUrlProxied);
        }
        invalidateState();
        return false;
    }

    private boolean onNativeInvoke(int event, Bundle bundle) {
        int httpStatus = bundleNumber(bundle,
                IjkMediaPlayer.OnNativeInvokeListener.ARG_HTTP_CODE, 0);
        int nativeError = bundleNumber(bundle,
                IjkMediaPlayer.OnNativeInvokeListener.ARG_ERROR, 0);
        int retry = bundleNumber(bundle,
                IjkMediaPlayer.OnNativeInvokeListener.ARG_RETRY_COUNTER, 0);
        long offset = bundleLong(bundle,
                IjkMediaPlayer.OnNativeInvokeListener.ARG_OFFSET, -1);
        if (event == IjkMediaPlayer.OnNativeInvokeListener.EVENT_WILL_HTTP_OPEN) {
            advanceOpenStage(IjkPlayerEngine.OpenStage.HTTP_OPENING);
        } else if (event
                == IjkMediaPlayer.OnNativeInvokeListener.EVENT_DID_HTTP_OPEN) {
            if (httpStatus > 0) lastHttpStatus = httpStatus;
            advanceOpenStage(IjkPlayerEngine.OpenStage.HTTP_OPENED);
        } else if (event
                == IjkMediaPlayer.OnNativeInvokeListener.EVENT_DID_HTTP_SEEK) {
            if (httpStatus > 0) lastHttpStatus = httpStatus;
            if (offset >= 0) lastNativeOffset = offset;
        } else if (event
                == IjkMediaPlayer.OnNativeInvokeListener.EVENT_WILL_HTTP_SEEK
                && offset >= 0) {
            lastNativeOffset = offset;
        }
        if (SpiderDebug.isEnabled()) {
            diagnostics.emit("input.open", "ijk-native-invoke", e -> e.observed("value", event)
                    .observed("stage", openStage.label()).observed("httpCode", httpStatus > 0 ? httpStatus : null)
                    .observed("errorCode", nativeError).observed("rangeStart", offset < 0 ? null : offset));
            SpiderDebug.log("ijk",
                    "native event=%d stage=%s http=%d error=%d offset=%d retry=%d",
                    event,
                    openStage.label(),
                    httpStatus,
                    nativeError,
                    offset,
                    retry);
        }
        return false;
    }

    @Override
    public void onBufferingUpdate(IMediaPlayer mp, int percent) {
        bufferingPercent = percent;
        invalidateState();
    }

    /** AAR 内核没有 long 版缓冲回调，保留方法兼容内部调用，由 int 百分比路径驱动。 */
    public void onBufferingUpdate(IMediaPlayer mp, long positionMs) {
        bufferingPositionMs = Math.max(0, positionMs);
        invalidateState();
    }

    @Override
    public void onVideoSizeChanged(IMediaPlayer mp, int width, int height, int sarNum, int sarDen) {
        diagnostics.emit("video.output.format", "ijk-video-size", e -> e.observed("width", width).observed("height", height));
        videoSize = new VideoSize(width, height);
        refreshTracks();
        invalidateState();
    }

    @Override
    public void onTimedText(IMediaPlayer mp, IjkTimedText text) {
    }

    private void openCurrent() {
        if (mediaItem == null || mediaItem.localConfiguration == null) return;
        diagnostics.begin(diagnosticTrace, "foreground");
        lastDiagnosticMs = 0;
        try {
            playbackState = Player.STATE_BUFFERING;
            loading = true;
            playerError = null;
            prepared = false;
            newlyRenderedFirstFrame = false;
            renderedFirstFrameSeen = false;
            lastErrorSnapshot = IjkPlayerEngine.ErrorSnapshot.none();
            resetOpenDiagnostics();
            ijk.reset();
            startSubtitleLoad(mediaItem);
            hlsProxy.clear();
            invalidateResourceObservation();
            ijk.setWakeMode(App.get(), PowerManager.PARTIAL_WAKE_LOCK);
            Uri sourceUri = mediaItem.localConfiguration.uri;
            Map<String, String> headers = ExoUtil.extractHeaders(mediaItem);
            String playableUrl = sourceUri.toString();
            resourceClassification = PlaybackResourceClassifier.classifyRequest(playableUrl, mediaItem.localConfiguration.mimeType, mediaItem.localConfiguration.mimeType);
            resourceObservationActive = true;
            boolean dash = isLikelyDash(mediaItem, playableUrl);
            currentDash = dash;
            // 标记本条是否改写为应用内本地代理 URL（/mpv/index.m3u8?s=N 等带 query）。
            // 594/595/596 三份真机日志证实：代理 playlist/分片均正常返回、数据在流，
            // IJK 仍在 prepare 阶段 what=-10000 extra=0 失败，与 format 选项组合无关
            // （无选项 / +allowed_extensions / +iformat=hls 三种配置同样失败）。
            // 失败点在 native 层，靠失败路径的 logcat dump（ijk-native 行）定位。
            boolean localProxy = false;
            com.fongmi.android.tv.player.PlaybackSwitchLog.log("ijk", "open begin dash=%s decode=%d mime=%s url=%s",
                    dash, decode, mediaItem.localConfiguration.mimeType,
                    playableUrl == null ? "null" : playableUrl.substring(0, Math.min(300, playableUrl.length())));
            // B 站等 DASH 线路走本地代理：proxyDash 保留 SegmentBase indexRange，
            // 只把 BaseURL 改写为本地 dash-item 代理（上游 blbl 同方案，ffmpeg7
            // dashdec 自己解析 sidx 并按 Range 取分片）。不能直连或用 data:/落盘绕过代理。
            if (dash) {
                playableUrl = hlsProxy.proxyDash(
                        playableUrl, headers,
                        PlaybackDiskBufferStore.mediaKey(mediaItem));
                SpiderDebug.log("ijk", "proxy action=enabled mode=dash");
            } else if (shouldProxyHls(mediaItem, playableUrl)) {
                playableUrl = hlsProxy.proxy(
                        playableUrl, headers,
                        PlaybackDiskBufferStore.mediaKey(mediaItem));
                localProxy = true;
                SpiderDebug.log("ijk", "proxy action=enabled mode=hls");
            } else {
                IjkLongUrlPolicy.Decision longUrl =
                        IjkLongUrlPolicy.evaluate(playableUrl);
                if (longUrl.proxyRequired()) {
                    playableUrl = hlsProxy.proxyFile(
                            playableUrl,
                            headers,
                            PlaybackDiskBufferStore.mediaKey(mediaItem));
                    longUrlProxied = true;
                    localProxy = true;
                    SpiderDebug.log("ijk",
                            "proxy action=enabled mode=file-long-url nativeBytes=%d",
                            longUrl.nativeBytes());
                }
            }
            SpiderDebug.log("ijk",
                    "open dash=%s decode=%d mime=%s headers=%d",
                    dash,
                    decode,
                    mediaItem.localConfiguration.mimeType,
                    headers.size());
            currentPlayableUrl = playableUrl;
            com.fongmi.android.tv.player.PlaybackSwitchLog.log("ijk", "proxy resolved dash=%s finalUrl=%s",
                    dash, playableUrl == null ? "null" : playableUrl.substring(0, Math.min(300, playableUrl.length())));
            configureOptions(sourceUri, dash, headers, localProxy);
            bindVideoOutput();
            ijk.setDataSource(App.get(), Uri.parse(playableUrl), headers);
            com.fongmi.android.tv.player.PlaybackSwitchLog.log("ijk", "setDataSource done url=%s",
                    playableUrl == null ? "null" : playableUrl.substring(0, Math.min(200, playableUrl.length())));
            advanceOpenStage(IjkPlayerEngine.OpenStage.SOURCE_SET);
            ijk.setAudioStreamType(AudioManager.STREAM_MUSIC);
            ijk.setScreenOnWhilePlaying(true);
            ijk.setLooping(repeatOne);
            ijk.setSpeed(playbackParameters.speed);
            ijk.prepareAsync();
            com.fongmi.android.tv.player.PlaybackSwitchLog.log("ijk", "prepareAsync posted");
            rebindVideoOutput("prepare");
            invalidateState();
            startStateRefresh();
        } catch (Throwable e) {
            com.fongmi.android.tv.player.PlaybackSwitchLog.log("ijk", "OPEN EXCEPTION stage=%s type=%s message=%s",
                    openStage.label(), e.getClass().getSimpleName(), e.getMessage());
            diagnostics.error(diagnostics.context(), "ijk", "open:" + openStage.label(), e);
            playerError = new PlaybackException(
                    "IJK open failed",
                    e,
                    PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
            SpiderDebug.log("ijk",
                    "open failed errorType=%s stage=%s http=%d longProxy=%s action=notify",
                    e.getClass().getSimpleName(),
                    openStage.label(),
                    lastHttpStatus,
                    longUrlProxied);
            dumpNativeLogTail();
            lastErrorSnapshot = new IjkPlayerEngine.ErrorSnapshot(
                    IMediaPlayer.MEDIA_ERROR_IO,
                    0,
                    prepared,
                    openStage,
                    lastHttpStatus,
                    lastNativeOffset,
                    longUrlProxied);
            playbackState = Player.STATE_IDLE;
            loading = false;
            clearSubtitles();
            prepared = false;
            stopStateRefresh();
            invalidateState();
        }
    }

    private void stopInternal(boolean resetState) {
        try {
            if (playbackState != Player.STATE_IDLE) ijk.stop();
        } catch (Throwable ignored) {
        }
        ijk.reset();
        hlsProxy.clear();
        invalidateResourceObservation();
        currentDash = false;
        loading = false;
        bufferingPercent = 0;
        bufferingPositionMs = 0;
        currentTracks = Tracks.EMPTY;
        selectedVideoFormat = null;
        selectedAudioFormat = null;
        videoSize = VideoSize.UNKNOWN;
        clearSubtitles();
        prepared = false;
        newlyRenderedFirstFrame = false;
        renderedFirstFrameSeen = false;
        lastErrorSnapshot = IjkPlayerEngine.ErrorSnapshot.none();
        resetOpenDiagnostics();
        if (resetState) playbackState = Player.STATE_IDLE;
        stopStateRefresh();
    }

    private void startStateRefresh() {
        App.removeCallbacks(stateRefreshRunnable);
        App.post(stateRefreshRunnable, subtitleTrack.isEmpty() ? STATE_REFRESH_INTERVAL_MS : SUBTITLE_REFRESH_INTERVAL_MS);
    }

    private void invalidateResourceObservation() {
        resourceObservationActive = false;
        resourceClassification = null;
        currentPlayableUrl = null;
    }

    private void stopStateRefresh() {
        App.removeCallbacks(stateRefreshRunnable);
    }

    private void refreshPlaybackState() {
        if (mediaItem == null || playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED || playerError != null) return;
        updateCurrentCues(position());
        requestPreload(Math.max(0, position()));
        invalidateState();
        diagnosticSnapshot();
        startStateRefresh();
    }

    private void startSubtitleLoad(MediaItem item) {
        clearSubtitles();
        if (item.localConfiguration == null || item.localConfiguration.subtitleConfigurations.isEmpty()) return;
        int serial = ++subtitleSerial;
        List<MediaItem.SubtitleConfiguration> configs = item.localConfiguration.subtitleConfigurations;
        Map<String, String> headers = ExoUtil.extractHeaders(item);
        subtitleLoad = Task.submit(() -> {
            IjkSubtitleTrack loaded = IjkSubtitleTrack.load(configs, headers);
            App.post(() -> {
                if (serial != subtitleSerial || mediaItem != item) return;
                subtitleLoad = null;
                subtitleTrack = loaded;
                updateCurrentCues(position());
                invalidateState();
                if (playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED) startStateRefresh();
            });
        });
    }

    private void clearSubtitles() {
        subtitleSerial++;
        if (subtitleLoad != null) subtitleLoad.cancel(true);
        subtitleLoad = null;
        subtitleTrack = IjkSubtitleTrack.EMPTY;
        currentCues = CueGroup.EMPTY_TIME_ZERO;
    }

    private boolean updateCurrentCues(long positionMs) {
        CueGroup next = subtitleTrack.getCueGroup(positionMs);
        if (currentCues.cues.equals(next.cues)) return false;
        currentCues = next;
        return true;
    }

    private void diagnosticSnapshot() {
        if (!com.fongmi.android.tv.player.PlaybackDiagnosticCollector.enabled()) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastDiagnosticMs < 5000) return;
        lastDiagnosticMs = now;
        diagnostics.baseline();
        diagnostics.emit("ijk.runtime", "ijk-public-properties", e -> {
            e.observed("state", playbackState).observed("positionMs", position()).observed("durationMs", duration() == C.TIME_UNSET ? null : duration())
                    .observed("playWhenReady", playWhenReady).observed("volume", volume).observed("speed", playbackParameters.speed)
                    .observed("videoSelected", prepared ? selectedVideoFormat != null : null)
                    .observed("audioSelected", prepared ? selectedAudioFormat != null : null)
                    .unknown("audioOutput", com.github.catvod.crawler.diagnostics.DiagnosticEvent.Status.NOT_OBSERVABLE)
                    .observed("nativeHook", "native-hook-required: IJK AudioTrack/OpenSL write/head/actual route");
            if (prepared) e.observed("decoderName", getVideoCodecInfoSnapshot()).observed("decode", ijk.getVideoDecoder());
        });
        if (prepared) {
            diagnostics.emit("video.output.summary", "ijk-public-properties", e -> e
                    .observed("frameRate", ijk.getVideoOutputFramesPerSecond()).observed("metricScope", "IJK video-output-fps; not display refresh")
                    .observed("bytes", ijk.getVideoCachedBytes()));
            diagnostics.emit("audio.decoder.output", "ijk-public-properties", e -> e.observed("decoderName", getAudioCodecInfoSnapshot())
                    .observed("bytes", ijk.getAudioCachedBytes()).observed("metricScope", "cached input bytes; not accepted output")
                    .unknown("audibility", com.github.catvod.crawler.diagnostics.DiagnosticEvent.Status.NOT_OBSERVABLE));
        }
        com.fongmi.android.tv.player.SystemAudioDiagnosticCollector.snapshot(diagnostics, diagnostics.context());
    }

    private void requestPreload(long positionMs) {
        if (playWhenReady) hlsProxy.preloadAround(positionMs);
        else hlsProxy.preloadWhilePaused(positionMs);
    }

    private void setVideoOutput(Object output) {
        // 切换内核（例如 B 站线路 EXO -> IJK）时 PlayerView 会重挂输出，若此刻
        // SurfaceHolder/SurfaceTexture 已被销毁，getHolder()/new Surface(texture)
        // 可能抛异常；不在这里兜住会让整次切换以异常告终（用户看到的“切 IJK 报错”）。
        // 与 bindVideoOutput() 一致的口径：失败只记日志、保留当前输出，交给后续重绑点补。
        try {
            detachSurfaceHolder();
            if (output instanceof SurfaceView view) {
                setSurfaceHolder(view.getHolder());
            } else if (output instanceof TextureView view) {
                SurfaceTexture texture = view.getSurfaceTexture();
                releaseOwnedSurface();
                if (texture != null) {
                    surface = new Surface(texture);
                    ownsSurface = true;
                    pendingTextureView = null;
                } else {
                    // PlayerView 在 TextureView 就绪前就调 setPlayer：此时拿不到 SurfaceTexture，
                    // 只能记下视图，等 prepared/重绑点补绑，否则整场黑屏有声。
                    surface = null;
                    ownsSurface = false;
                    pendingTextureView = view;
                }
            } else if (output instanceof SurfaceHolder holder) {
                setSurfaceHolder(holder);
            } else if (output instanceof Surface s) {
                releaseOwnedSurface();
                surface = s;
                ownsSurface = false;
            }
            bindVideoOutput();
        } catch (Throwable e) {
            SpiderDebug.log("ijk",
                    "set video output failed errorType=%s action=keep-current",
                    e.getClass().getSimpleName());
        }
    }

    private void setSurfaceHolder(SurfaceHolder holder) {
        surfaceHolder = holder;
        surfaceHolder.addCallback(surfaceCallback);
        surface = surfaceHolder.getSurface();
        ownsSurface = false;
    }

    private boolean bindVideoOutput() {
        try {
            if (surfaceHolder != null) {
                surface = surfaceHolder.getSurface();
                if (surface != null && surface.isValid()) {
                    ijk.setDisplay(surfaceHolder);
                    return true;
                }
            } else if (surface != null && surface.isValid()) {
                ijk.setSurface(surface);
                return true;
            }
        } catch (Throwable e) {
            SpiderDebug.log("ijk",
                    "bind surface failed errorType=%s action=keep-current",
                    e.getClass().getSimpleName());
        }
        return false;
    }

    /**
     * Re-issues the current surface once the native video output exists. {@link #openCurrent()} binds
     * before {@code setDataSource}, while the native renderer is created during prepare; without a
     * second bind a valid surface can stay unregistered, and the renderer then emits zero frames
     * while decoding and audio continue normally (sound with no picture).
     */
    private void rebindVideoOutput(String reason) {
        if (pendingTextureView != null) {
            SurfaceTexture texture = pendingTextureView.getSurfaceTexture();
            if (texture != null) {
                releaseOwnedSurface();
                surface = new Surface(texture);
                ownsSurface = true;
                pendingTextureView = null;
                SpiderDebug.log("ijk", "texture view became available reason=%s", reason);
            }
        }
        boolean hasHolder = surfaceHolder != null;
        boolean hasSurface = surface != null && surface.isValid();
        if (!hasHolder && !hasSurface) return;
        String action = bindVideoOutput() ? "rebound" : "none";
        SpiderDebug.log("ijk",
                "surface rebind reason=%s holder=%s valid=%s action=%s",
                reason, hasHolder, hasSurface, action);
    }

    private void clearVideoOutput() {
        detachSurfaceHolder();
        releaseOwnedSurface();
        surface = null;
        pendingTextureView = null;
        try {
            ijk.setDisplay(null);
        } catch (Throwable ignored) {
        }
        try {
            ijk.setSurface(null);
        } catch (Throwable ignored) {
        }
    }

    private void detachSurfaceHolder() {
        if (surfaceHolder == null) return;
        try {
            surfaceHolder.removeCallback(surfaceCallback);
        } catch (Throwable ignored) {
        }
        surfaceHolder = null;
    }

    private void releaseOwnedSurface() {
        if (ownsSurface && surface != null) surface.release();
        ownsSurface = false;
    }

    private void configureOptions(Uri uri, boolean dash) {
        configureOptions(uri, dash, null, false);
    }

    private void configureOptions(Uri uri, boolean dash, java.util.Map<String, String> headers) {
        configureOptions(uri, dash, headers, false);
    }

    private void configureOptions(Uri uri, boolean dash, java.util.Map<String, String> headers, boolean localProxy) {
        String url = uri.toString();
        boolean automaticBuffer = PlaybackPerformanceSetting.hasAutomaticOptions(
                PlayerSetting.IJK,
                PlaybackPerformanceCatalog.IJK_BUFFER,
                PlaybackPerformanceCatalog.IJK_WATER);
        IjkBufferOptionPolicy.Decision inputBuffer =
                IjkBufferOptionPolicy.resolve(
                        automaticBuffer,
                        automaticInputBufferConfig,
                        url,
                        IjkPerformanceSetting.getScene(),
                        IjkPerformanceSetting.getBufferMb(),
                        PlayerSetting.getBufferBytes(PlayerSetting.IJK),
                        IjkPerformanceSetting.getFirstWaterMs(),
                        IjkPerformanceSetting.getNextWaterMs(),
                        IjkPerformanceSetting.getLastWaterMs());
        appliedInputBufferConfig = inputBuffer.config();
        if (PlaybackPerformanceSetting.isOverridden(
                PlayerSetting.IJK,
                PlaybackPerformanceCatalog.IJK_BUFFER)) {
            appliedInputBufferConfig = new IjkBufferPolicy.Config(
                    IjkPerformanceSetting.getBufferMb(),
                    appliedInputBufferConfig.firstWaterMs(),
                    appliedInputBufferConfig.nextWaterMs(),
                    appliedInputBufferConfig.lastWaterMs());
            inputBuffer = IjkBufferOptionPolicy.withConfig(
                    inputBuffer,
                    appliedInputBufferConfig,
                    PlayerSetting.getBufferBytes(PlayerSetting.IJK));
        }
        if (PlaybackPerformanceSetting.isOverridden(
                PlayerSetting.IJK,
                PlaybackPerformanceCatalog.IJK_WATER)) {
            appliedInputBufferConfig = new IjkBufferPolicy.Config(
                    appliedInputBufferConfig.bufferMb(),
                    IjkPerformanceSetting.getFirstWaterMs(),
                    IjkPerformanceSetting.getNextWaterMs(),
                    IjkPerformanceSetting.getLastWaterMs());
            inputBuffer = IjkBufferOptionPolicy.withConfig(
                    inputBuffer,
                    appliedInputBufferConfig,
                    PlayerSetting.getBufferBytes(PlayerSetting.IJK));
        }
        boolean automaticPictureQueue = PlaybackPerformanceSetting.isAuto(
                PlayerSetting.IJK,
                PlaybackPerformanceCatalog.IJK_PICTURE_QUEUE);
        boolean automaticSoftTune = PlaybackPerformanceSetting.isAuto(
                PlayerSetting.IJK,
                PlaybackPerformanceCatalog.IJK_SOFT_TUNE);
        appliedDecodeControlConfig = IjkDecodePressurePolicy.prepareConfig(
                automaticPictureQueue || automaticSoftTune,
                automaticDecodeControlConfig,
                decode == PlayerEngine.SOFT,
                IjkPerformanceSetting.getPictureQueue(),
                configuredSoftTuneMode());
        appliedDecodeControlConfig = new IjkDecodePressurePolicy.Config(
                automaticPictureQueue
                        ? appliedDecodeControlConfig.pictureQueue()
                        : IjkPerformanceSetting.getPictureQueue(),
                automaticSoftTune
                        ? appliedDecodeControlConfig.tuneMode()
                        : decode == PlayerEngine.SOFT
                        ? configuredSoftTuneMode()
                        : IjkDecodePressurePolicy.TuneMode.OFF);
        SpiderDebug.log("ijk-buffer",
                "action=prepare mode=%s bufferMb=%d maxBufferBytes=%d firstMs=%d nextMs=%d lastMs=%d realtime=%s infbuf=%s",
                automaticBuffer ? "automatic" : "fixed",
                appliedInputBufferConfig.bufferMb(),
                inputBuffer.maxBufferBytes(),
                appliedInputBufferConfig.firstWaterMs(),
                appliedInputBufferConfig.nextWaterMs(),
                appliedInputBufferConfig.lastWaterMs(),
                inputBuffer.realtime(), inputBuffer.infiniteBuffer());
        if (dash) {
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "iformat", "dash");
            // 仅 DASH（B 站等）需要：blbl 实测配置，放宽扩展名并把 UA/Referer/Cookie
            // 以 format 选项透传给 dashdec 的分片 Range 子请求。
            // 关键：headers 拼串绝不能对普通线路生效——它会干扰 ffmpeg7 的 hls/mov
            // 解复用器探测，正是「瓜子等普通线路切 IJK 失败回退 EXO」的根因。
            // DASH 分支保持与上游一致；普通线路仅在 localProxy 分支放宽扩展名（见下）。
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "allowed_extensions", "ALL");
            applyHeaderFormatOptions(headers);
        } else {
            // 非 DASH（普通 HLS/mp4，含本地代理）。
            // 真机 native 日志（598）首次给出真实报错：
            //   detected format mpegts extension none mismatches allowed extensions in url ...
            //   Error when loading first segment ...
            //   Invalid data found when processing input   → what=-10000 extra=0
            // 根因：FFmpeg 7.x 的 hls demuxer 新增分片扩展名严格校验（extension_picky，默认 1）。
            // 它有两道判定：A=URL 是否在 allowed_extensions；F=探测格式(mpegts)的硬编码扩展名
            // 列表(ts,m2t,m2ts,mts,mpg,...)是否匹配 URL。本地代理把分片改写成 /mpv/item?... 形式，
            // 没有任何扩展名 → F 判定恒为 0，即使 allowed_extensions=ALL 只绕过 A 判定，
            // 仍会以 !(matchA & matchF) 失败（这正是 595/596 设 ALL 无效的原因）。
            // 关闭该校验即可，仅作用于本路 IJK 的 hls demuxer 实例：
            // DASH 分支、网盘代理、MPV/EXO/系统内核均不受影响；对带正常扩展名的 URL 无行为变化。
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "extension_picky", 0L);
            if (localProxy) {
                // 代理 URL 同时放开 file 协议的安全扩展名检查（http 场景为兜底）。
                ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "allowed_extensions", "ALL");
            }
        }
        configureSoftDecodeOptions(appliedDecodeControlConfig.tuneMode());
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_timeout", -1);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "fastseek");
        // SegmentBase MP4 relies on HTTP byte-range seeks for sidx/moof access.
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", dash ? 1 : 0);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", IjkPerformanceSetting.isAccurateSeek() ? 1 : 0);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", IjkPerformanceSetting.getFrameDropValue());
        // blbl FFmpeg7 单库的 max-buffer-size 合法范围是 [0, 15728640]（15MB）。
        // 超限设置会失败并回落默认值（同为 15MB），行为不变；这里显式钳制，
        // 消除真机日志中的 "Value ... out of range / Error setting option" 噪音。
        long ijkMaxBufferBytes = Math.min(inputBuffer.maxBufferBytes(), 15L * 1024L * 1024L);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", ijkMaxBufferBytes);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering",
                dash ? 0 : (IjkPerformanceSetting.isPacketBuffering() ? 1 : 0));
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "first-high-water-mark-ms", appliedInputBufferConfig.firstWaterMs());
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "next-high-water-mark-ms", appliedInputBufferConfig.nextWaterMs());
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "last-high-water-mark-ms", appliedInputBufferConfig.lastWaterMs());
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", decode);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-hevc", decode);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-all-videos", decode);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", decode);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", decode);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", IjkMediaPlayer.SDL_FCC_RV32);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "reconnect", IjkPerformanceSetting.isReconnect() ? 1 : 0);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "soundtouch", 1);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "subtitle", 1);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "video-pictq-size", appliedDecodeControlConfig.pictureQueue());
        SpiderDebug.log("ijk-decode",
                "action=prepare mode=%s requestedDecode=%s pictureQueue=%d tune=%s",
                automaticPictureQueue || automaticSoftTune
                        ? "automatic" : "fixed",
                decode == PlayerEngine.SOFT ? "software" : "hardware",
                appliedDecodeControlConfig.pictureQueue(),
                appliedDecodeControlConfig.tuneMode().label());
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "protocol_whitelist", "async,cache,crypto,file,http,https,pipe,rtmp,rtp,tcp,tls,udp,data,ijkinject,ijklongurl,ijksegment,ijkhttphook,ijklivehook,ijktcphook,ijkurlhook,ijkmediadatasource");
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "infbuf", inputBuffer.infiniteBuffer() ? 1 : 0);
        applyProbeOptions();
        applyRtspOptions(url);
    }

    /**
     * blbl 同款：把 UA/Referer/Cookie/整组 headers 写成 format 选项。
     * ffmpeg7 的 hls/dash 解复用器发起嵌套子请求（分片、AES KEY、子清单）时，
     * 会从解复用器的 format 选项继承请求头；仅靠 setDataSource 的 headers Map
     * 在部分嵌套路径上不落地，表现为子请求 403/风控拦截，整个线路起播失败。
     */
    private void applyHeaderFormatOptions(java.util.Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return;
        String ua = null;
        String referer = null;
        String cookies = null;
        StringJoiner raw = new StringJoiner("\r\n");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String name = entry.getKey() == null ? "" : entry.getKey().trim();
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (name.isEmpty() || value.isEmpty()) continue;
            String lower = name.toLowerCase(Locale.US);
            switch (lower) {
                case "user-agent" -> ua = value;
                case "referer" -> referer = value;
                case "cookie" -> cookies = value;
                default -> raw.add(name + ": " + value);
            }
        }
        if (ua != null) {
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", ua);
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user-agent", ua);
        }
        if (referer != null) ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "referer", referer);
        if (cookies != null) ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "cookies", cookies);
        String extra = raw.toString();
        if (!extra.isEmpty()) ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "headers", extra + "\r\n");
    }

    private void configureSoftDecodeOptions(
            IjkDecodePressurePolicy.TuneMode tuneMode) {
        IjkDecodePressurePolicy.TuneMode mode = tuneMode == null
                ? IjkDecodePressurePolicy.TuneMode.OFF : tuneMode;
        if (mode == IjkDecodePressurePolicy.TuneMode.OFF) return;
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "fast", mode.fast());
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC,
                "skip_loop_filter", mode.skipLoopFilter());
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC,
                "skip_frame", mode.skipFrame());
    }

    private IjkDecodePressurePolicy.TuneMode configuredSoftTuneMode() {
        return switch (IjkPerformanceSetting.getSoftTuneMode()) {
            case IjkPerformanceSetting.SOFT_TUNE_AGGRESSIVE ->
                    IjkDecodePressurePolicy.TuneMode.AGGRESSIVE;
            case IjkPerformanceSetting.SOFT_TUNE_MILD ->
                    IjkDecodePressurePolicy.TuneMode.MILD;
            default -> IjkDecodePressurePolicy.TuneMode.OFF;
        };
    }

    private void applyProbeOptions() {
        if (IjkPerformanceSetting.getProbeMode() == IjkPerformanceSetting.PROBE_FAST) {
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 512_000);
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 2_000_000);
        } else if (IjkPerformanceSetting.getProbeMode() == IjkPerformanceSetting.PROBE_FULL) {
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 5_000_000);
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 10_000_000);
        }
    }

    private void applyRtspOptions(String url) {
        if (!url.toLowerCase(Locale.US).startsWith("rtsp")) return;
        if (IjkPerformanceSetting.getRtspTransport() == IjkPerformanceSetting.RTSP_TCP) {
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_transport", "tcp");
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_flags", "prefer_tcp");
        } else if (IjkPerformanceSetting.getRtspTransport() == IjkPerformanceSetting.RTSP_UDP) {
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_transport", "udp");
        }
    }

    private boolean shouldProxyHls(MediaItem item, String uri) {
        if (!isLikelyHls(item, uri) || TextUtils.isEmpty(uri)) return false;
        Uri parsed = Uri.parse(uri);
        String scheme = parsed.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
        return !"/mpv/index.m3u8".equals(parsed.getPath()) && !"/mpv/item".equals(parsed.getPath());
    }

    private boolean isLikelyHls(MediaItem item, String uri) {
        if (item.localConfiguration != null) {
            String mimeType = item.localConfiguration.mimeType;
            if (MimeTypes.APPLICATION_M3U8.equals(mimeType)
                    || "application/vnd.apple.mpegurl".equalsIgnoreCase(mimeType)
                    || "application/x-mpegurl".equalsIgnoreCase(mimeType)
                    || "hls".equalsIgnoreCase(mimeType)) {
                return true;
            }
        }
        String lower = uri == null ? "" : uri.toLowerCase(Locale.US);
        return lower.contains("m3u8");
    }

    private boolean isLikelyDash(MediaItem item, String uri) {
        if (item.localConfiguration != null) {
            String mimeType = item.localConfiguration.mimeType;
            if (MimeTypes.APPLICATION_MPD.equals(mimeType)
                    || "application/dash+xml".equalsIgnoreCase(mimeType)
                    || "dash".equalsIgnoreCase(mimeType)) {
                return true;
            }
        }
        String lower = uri == null ? "" : uri.toLowerCase(Locale.US);
        return lower.contains(".mpd") || lower.contains("type=mpd") || lower.contains("format=mpd");
    }

    private final SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            surface = holder.getSurface();
            bindVideoOutput();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            surface = holder.getSurface();
            bindVideoOutput();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            surface = null;
            try {
                ijk.setDisplay(null);
            } catch (Throwable ignored) {
            }
        }
    };

    private long duration() {
        long duration = safeDuration();
        return duration > 0 ? duration : C.TIME_UNSET;
    }

    PlaybackResourceClassifier.Classification getResourceClassification() {
        PlaybackResourceClassifier.Classification current = resourceClassification;
        PlaybackResourceClassifier.Classification proxy = resourceObservationActive
                ? hlsProxy.resourceClassification() : null;
        if (proxy != null) current = PlaybackResourceClassifier.merge(current, proxy);
        return current;
    }

    IjkStreamScenePolicy.Decision getStreamSceneDecision() {
        return streamSceneDecision(duration());
    }

    private IjkStreamScenePolicy.Decision streamSceneDecision(long durationMs) {
        return IjkStreamScenePolicy.resolve(
                getResourceClassification(), durationMs, SystemClock.elapsedRealtime());
    }

    PlaybackRoute.Resolution getPlaybackRouteResolution() {
        return PlaybackRoute.resolve(currentPlayableUrl);
    }

    private long safeDuration() {
        try {
            return ijk.getDuration();
        } catch (Throwable ignored) {
            return C.TIME_UNSET;
        }
    }

    private long position() {
        try {
            long actual = Math.max(0, ijk.getCurrentPosition());
            if (pendingSeekPositionMs == C.TIME_UNSET) return actual;
            boolean reached = Math.abs(actual - pendingSeekPositionMs) <= 1500;
            boolean expired = pendingSeekRequestedAtMs != C.TIME_UNSET && SystemClock.elapsedRealtime() - pendingSeekRequestedAtMs >= 15_000;
            if (reached || expired) {
                setPendingSeek(C.TIME_UNSET);
                return actual;
            }
            return pendingSeekPositionMs;
        } catch (Throwable ignored) {
            return pendingSeekPositionMs == C.TIME_UNSET ? 0 : pendingSeekPositionMs;
        }
    }

    private void setPendingSeek(long positionMs) {
        pendingSeekPositionMs = positionMs;
        pendingSeekRequestedAtMs = positionMs == C.TIME_UNSET ? C.TIME_UNSET : SystemClock.elapsedRealtime();
    }

    private long bufferedPosition(long position, long duration) {
        return IjkBufferedDurationPolicy.bufferedPosition(
                position, duration, bufferingPercent,
                getNativeBufferedDurationSnapshot(), bufferingPositionMs);
    }

    private boolean isPlayingInternal() {
        try {
            return playbackState == Player.STATE_READY && ijk.isPlaying();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** AAR 内核 track 元数据快照（来自 IjkMediaMeta.mStreams），替代旧扩展 ITrackInfo。 */
    private static final class IjkStreamSnapshot {
        final int index;
        final int trackType;
        final String language;
        final String codecName;
        final int width;
        final int height;
        final float fps;
        final int bitrate;
        final int channelCount;

        IjkStreamSnapshot(tv.danmaku.ijk.media.player.IjkMediaMeta.IjkStreamMeta meta) {
            this.index = meta.mIndex;
            this.trackType = trackTypeOf(meta.mType);
            this.language = meta.mLanguage;
            this.codecName = meta.mCodecName;
            this.width = meta.mWidth;
            this.height = meta.mHeight;
            this.fps = meta.mFpsDen > 0 ? (float) meta.mFpsNum / meta.mFpsDen : 0f;
            this.bitrate = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, meta.mBitrate));
            this.channelCount = Long.bitCount(meta.mChannelLayout);
        }

        private static int trackTypeOf(String type) {
            if ("video".equals(type)) return ITrackInfo.MEDIA_TRACK_TYPE_VIDEO;
            if ("audio".equals(type)) return ITrackInfo.MEDIA_TRACK_TYPE_AUDIO;
            if ("timedtext".equals(type)) return ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT;
            return ITrackInfo.MEDIA_TRACK_TYPE_UNKNOWN;
        }
    }

    private List<IjkStreamSnapshot> trackSnapshots() {
        try {
            android.os.Bundle bundle = ijk.getMediaMeta();
            if (bundle == null) return null;
            tv.danmaku.ijk.media.player.IjkMediaMeta meta =
                    tv.danmaku.ijk.media.player.IjkMediaMeta.parse(bundle);
            if (meta == null || meta.mStreams == null || meta.mStreams.isEmpty()) return null;
            List<IjkStreamSnapshot> list = new java.util.ArrayList<>();
            for (tv.danmaku.ijk.media.player.IjkMediaMeta.IjkStreamMeta stream : meta.mStreams) {
                list.add(new IjkStreamSnapshot(stream));
            }
            return list;
        } catch (Throwable error) {
            SpiderDebug.log("ijk", "media meta parse failed type=%s", error.getClass().getSimpleName());
            return null;
        }
    }

    private void refreshTracks() {
        try {
            List<IjkStreamSnapshot> infos = trackSnapshots();
            if (infos == null || infos.isEmpty()) {
                currentTracks = Tracks.EMPTY;
                selectedVideoFormat = null;
                selectedAudioFormat = null;
                return;
            }
            List<Tracks.Group> groups = new java.util.ArrayList<>();
            int selectedVideoStream = selectedStream(ITrackInfo.MEDIA_TRACK_TYPE_VIDEO);
            int selectedAudioStream = selectedStream(ITrackInfo.MEDIA_TRACK_TYPE_AUDIO);
            Format actualVideo = null;
            Format actualAudio = null;
            boolean selectedVideo = false;
            boolean selectedAudio = false;
            boolean selectedText = false;
            int index = 0;
            for (IjkStreamSnapshot info : infos) {
                int type = mediaTrackType(info.trackType);
                if (type == C.TRACK_TYPE_UNKNOWN) continue;
                boolean selected = switch (type) {
                    case C.TRACK_TYPE_VIDEO -> !selectedVideo;
                    case C.TRACK_TYPE_AUDIO -> !selectedAudio;
                    case C.TRACK_TYPE_TEXT -> !selectedText;
                    default -> false;
                };
                if (selected) {
                    if (type == C.TRACK_TYPE_VIDEO) selectedVideo = true;
                    else if (type == C.TRACK_TYPE_AUDIO) selectedAudio = true;
                    else if (type == C.TRACK_TYPE_TEXT) selectedText = true;
                }
                Format format = buildFormat(info, type, ++index);
                if (SpiderDebug.isEnabled()) {
                    boolean actualSelected = type == C.TRACK_TYPE_VIDEO ? info.index == selectedVideoStream
                            : type == C.TRACK_TYPE_AUDIO && info.index == selectedAudioStream;
                    diagnostics.emit("media.tracks", "ijk-track-info", e -> e.observed("trackId", info.index)
                            .observed("trackType", type).observed("selected", type == C.TRACK_TYPE_TEXT ? null : actualSelected)
                            .observed("mime", format.sampleMimeType).observed("codecs", format.codecs)
                            .observed("sampleRate", format.sampleRate > 0 ? format.sampleRate : null)
                            .observed("channels", format.channelCount > 0 ? format.channelCount : null));
                    if (type == C.TRACK_TYPE_VIDEO) diagnostics.evidence(diagnostics.context(), true, 0);
                    if (type == C.TRACK_TYPE_AUDIO) diagnostics.evidence(diagnostics.context(), false, 0);
                }
                if (type == C.TRACK_TYPE_VIDEO && info.index == selectedVideoStream) actualVideo = format;
                if (type == C.TRACK_TYPE_AUDIO && info.index == selectedAudioStream) actualAudio = format;
                TrackGroup group = new TrackGroup("ijk:" + type + ":" + index, format);
                groups.add(new Tracks.Group(group, false, new int[]{C.FORMAT_HANDLED}, new boolean[]{selected}));
            }
            currentTracks = groups.isEmpty() ? Tracks.EMPTY : new Tracks(groups);
            selectedVideoFormat = actualVideo;
            selectedAudioFormat = actualAudio;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("ijk", "tracks refreshed count=%d groups=%d", infos.size(), groups.size());
        } catch (Throwable e) {
            currentTracks = Tracks.EMPTY;
            selectedVideoFormat = null;
            selectedAudioFormat = null;
            SpiderDebug.log("ijk", "tracks refresh failed type=%s", e.getClass().getSimpleName());
        }
    }

    private int selectedStream(int type) {
        try {
            return ijk.getSelectedTrack(type);
        } catch (Throwable error) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("ijk", "selected track fact unavailable type=%s", error.getClass().getSimpleName());
            return -1;
        }
    }

    private int mediaTrackType(int ijkType) {
        return switch (ijkType) {
            case ITrackInfo.MEDIA_TRACK_TYPE_VIDEO -> C.TRACK_TYPE_VIDEO;
            case ITrackInfo.MEDIA_TRACK_TYPE_AUDIO -> C.TRACK_TYPE_AUDIO;
            case ITrackInfo.MEDIA_TRACK_TYPE_SUBTITLE -> C.TRACK_TYPE_TEXT;
            default -> C.TRACK_TYPE_UNKNOWN;
        };
    }

    private Format buildFormat(IjkStreamSnapshot info, int type, int index) {
        String codec = info.codecName;
        Format.Builder builder = new Format.Builder()
                .setId(type + ":" + index)
                .setLabel(trackLabel(type, index))
                .setCodecs(TextUtils.isEmpty(codec) ? null : codec)
                .setLanguage(TextUtils.isEmpty(info.language) ? null : info.language)
                .setSampleMimeType(sampleMimeType(type, codec));
        if (type == C.TRACK_TYPE_VIDEO) {
            int width = info.width > 0 ? info.width : videoSize.width;
            int height = info.height > 0 ? info.height : videoSize.height;
            if (width > 0) builder.setWidth(width);
            if (height > 0) builder.setHeight(height);
            if (info.fps > 0) builder.setFrameRate(info.fps);
        } else if (type == C.TRACK_TYPE_AUDIO) {
            if (info.channelCount > 0) builder.setChannelCount(info.channelCount);
        }
        int bitrate = info.bitrate;
        if (bitrate <= 0 && type == C.TRACK_TYPE_VIDEO) bitrate = safeIntBitrate(ijk.getBitRate());
        if (bitrate > 0) builder.setAverageBitrate(bitrate);
        return builder.build();
    }

    private int safeIntBitrate(long bitrate) {
        return bitrate > 0 && bitrate <= Integer.MAX_VALUE ? (int) bitrate : 0;
    }

    private String trackLabel(int type, int index) {
        return switch (type) {
            case C.TRACK_TYPE_VIDEO -> "Video " + index;
            case C.TRACK_TYPE_AUDIO -> "Audio " + index;
            case C.TRACK_TYPE_TEXT -> "Subtitle " + index;
            default -> "Track " + index;
        };
    }

    private String sampleMimeType(int type, String codec) {
        String value = codec == null ? "" : codec.toLowerCase(Locale.US);
        if (type == C.TRACK_TYPE_TEXT) {
            if (value.contains("pgs") || value.contains("hdmv")) return MimeTypes.APPLICATION_PGS;
            if (value.contains("dvd") || value.contains("vobsub")) return MimeTypes.APPLICATION_VOBSUB;
            if (value.contains("dvb")) return MimeTypes.APPLICATION_DVBSUBS;
            if (value.contains("ass") || value.contains("ssa")) return MimeTypes.TEXT_SSA;
            if (value.contains("webvtt") || value.contains("vtt")) return MimeTypes.TEXT_VTT;
            if (value.contains("srt") || value.contains("subrip")) return MimeTypes.APPLICATION_SUBRIP;
            if (value.contains("ttml")) return MimeTypes.APPLICATION_TTML;
            return TextUtils.isEmpty(value) ? MimeTypes.TEXT_UNKNOWN : MimeTypes.BASE_TYPE_TEXT + "/" + value;
        }
        if (type == C.TRACK_TYPE_AUDIO) {
            if (value.contains("aac")) return MimeTypes.AUDIO_AAC;
            if (value.contains("ac3")) return MimeTypes.AUDIO_AC3;
            if (value.contains("eac3") || value.contains("e-ac-3")) return MimeTypes.AUDIO_E_AC3;
            if (value.contains("opus")) return MimeTypes.AUDIO_OPUS;
            if (value.contains("vorbis")) return MimeTypes.AUDIO_VORBIS;
            if (value.contains("flac")) return MimeTypes.AUDIO_FLAC;
            if (value.contains("mp3") || value.contains("mpeg")) return MimeTypes.AUDIO_MPEG;
            return TextUtils.isEmpty(value) ? null : MimeTypes.BASE_TYPE_AUDIO + "/" + value;
        }
        if (value.contains("hevc") || value.contains("h265")) return MimeTypes.VIDEO_H265;
        if (value.contains("h264") || value.contains("avc")) return MimeTypes.VIDEO_H264;
        if (value.contains("av1")) return MimeTypes.VIDEO_AV1;
        if (value.contains("vp9")) return MimeTypes.VIDEO_VP9;
        if (value.contains("vp8")) return MimeTypes.VIDEO_VP8;
        if (value.contains("mpeg2")) return MimeTypes.VIDEO_MPEG2;
        return TextUtils.isEmpty(value) ? null : MimeTypes.BASE_TYPE_VIDEO + "/" + value;
    }

    private synchronized void resetOpenDiagnostics() {
        openStage = IjkPlayerEngine.OpenStage.NONE;
        lastHttpStatus = 0;
        lastNativeOffset = -1;
        longUrlProxied = false;
    }

    private synchronized void advanceOpenStage(
            IjkPlayerEngine.OpenStage stage) {
        if (stage != null && stage.ordinal() > openStage.ordinal()) {
            openStage = stage;
        }
    }

    private static int bundleNumber(Bundle bundle, String key, int fallback) {
        Object value = bundle == null ? null : bundle.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static long bundleLong(Bundle bundle, String key, long fallback) {
        Object value = bundle == null ? null : bundle.get(key);
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private String summarizeUri() {
        if (mediaItem == null || mediaItem.localConfiguration == null) return "";
        Uri uri = mediaItem.localConfiguration.uri;
        String host = uri.getHost();
        String path = uri.getPath();
        StringBuilder builder = new StringBuilder();
        builder.append(uri.getScheme()).append("://");
        builder.append(TextUtils.isEmpty(host) ? "unknown" : host);
        if (uri.getPort() > 0) builder.append(':').append(uri.getPort());
        if (!TextUtils.isEmpty(path)) builder.append(path.length() > 48 ? path.substring(0, 48) + "..." : path);
        builder.append(" len=").append(uri.toString().length());
        return builder.toString();
    }

}
