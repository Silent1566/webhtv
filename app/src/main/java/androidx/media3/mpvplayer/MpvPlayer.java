package androidx.media3.mpvplayer;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Build;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.accessibility.CaptioningManager;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MediaEdition;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.SimpleBasePlayer;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;

import com.fongmi.android.tv.player.engine.PlayerCacheState;
import com.fongmi.android.tv.player.iso.IsoSessionManager;
import com.fongmi.android.tv.player.lut.MpvLutShader;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.MpvPerformanceSetting;
import com.github.catvod.crawler.SpiderDebug;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import is.xyz.mpv.MPVLib;

@UnstableApi
public final class MpvPlayer extends SimpleBasePlayer implements MPVLib.EventObserver, MPVLib.LogObserver {

    private static final String TAG = "TV-mpv";
    private static final String SIZE_TAG = "MPV_SIZE";
    private static final long STATE_REFRESH_INTERVAL_MS = 1000;
    private static final long END_FILE_VALIDATION_DELAY_MS = 800;
    private static final long LOAD_START_RETRY_DELAY_MS = 1000;
    private static final int MAX_LOAD_START_RETRIES = 2;
    private static final double SECONDS_TO_MS = 1000.0;
    private static final double DEFAULT_SUBTITLE_TEXT_SIZE_FRACTION = 0.0533;
    private static final double MICROSECONDS_TO_SECONDS = 1_000_000.0;
    private static final String CONCAT_SOURCE_SEPARATOR = "***";
    private static final String CONCAT_SOURCE_SEPARATOR_REGEX = "\\*\\*\\*";
    private static final String CONCAT_DURATION_SEPARATOR = "|||";
    private static final String CONCAT_DURATION_SEPARATOR_REGEX = "\\|\\|\\|";
    private static final String HLS_LOAD_OPTIONS = "demuxer=lavf,demuxer-lavf-format=hls,demuxer-lavf-probesize=10485760,demuxer-lavf-analyzeduration=5";
    private static final String DASH_LOAD_OPTIONS = "demuxer=lavf,demuxer-lavf-format=dash,demuxer-lavf-probesize=10485760,demuxer-lavf-analyzeduration=5";
    private static final int RECENT_LOG_LIMIT = 32;
    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_ORIGIN = "Origin";
    private static final Object NATIVE_CONTEXT_LOCK = new Object();
    @Nullable
    private static MpvPlayer nativeContextOwner;

    public static final String ERROR_HLS_PLAYBACK_FAILED = "MPV_HLS_PLAYBACK_FAILED";
    public static final String ERROR_LOAD_FAILED = "MPV_LOAD_FAILED";
    public static final String ERROR_NETWORK_FAILED = "MPV_NETWORK_FAILED";
    public static final String ERROR_DRM_UNSUPPORTED = "MPV_DRM_UNSUPPORTED";
    public static final String ERROR_UNEXPECTED_IMAGE = "MPV_UNEXPECTED_IMAGE";
    public static final String ERROR_NO_AV_DATA = "MPV_NO_AV_DATA";
    public static final String ERROR_INVALID_MEDIA_DATA = "MPV_INVALID_MEDIA_DATA";
    public static final String ERROR_DECODE_FAILED = "MPV_DECODE_FAILED";
    public static final String ERROR_VIDEO_OUTPUT_FAILED = "MPV_VIDEO_OUTPUT_FAILED";

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
            .add(COMMAND_GET_TEXT_OFFSET)
            .add(COMMAND_SET_TEXT_OFFSET)
            .add(COMMAND_GET_AUDIO_OFFSET)
            .add(COMMAND_SET_AUDIO_OFFSET)
            .add(COMMAND_SET_VIDEO_SURFACE)
            .add(COMMAND_GET_TRACKS)
            .build();

    private final Context context;
    private final MpvPlayerConfig config;
    private final Handler mainHandler;
    private final Runnable stateRefreshRunnable;
    private final Runnable endFileValidationRunnable;
    private final Runnable loadStartRetryRunnable;
    private final Runnable isoTrackMetadataReadyListener;
    private final MpvHlsProxy hlsProxy;
    private final List<String> recentLogs;
    private final List<ParcelFileDescriptor> contentFds;
    @Nullable
    private final File subtitleDiagnosticFile;
    private MediaItem mediaItem;
    private SurfaceHolder surfaceHolder;
    private Surface surface;
    private Surface attachedSurface;
    private Object videoOutput;
    private MpvLutShader lutShader;
    private String currentPlayableUri;
    private String currentIsoUri;
    private boolean isoTrackListDumped;
    private long isoMetadataListenerSessionId = -1;
    private String appliedLutShaderPath;
    private PlaybackParameters playbackParameters;
    private PlaybackException playerError;
    private Tracks currentTracks;
    private List<MediaEdition> currentChapters;
    private VideoSize videoSize;
    private String lastVideoSizeCandidateLog;
    private int playbackState;
    private long pendingSeekPositionMs;
    private long cachedPositionMs;
    private long cachedDurationMs;
    private long cachedCacheDurationMs;
    private long cachedCacheEndMs;
    private long cachedCacheReaderPositionMs;
    private long cachedCacheForwardBytes;
    private long cachedCacheTotalBytes;
    private long cachedCacheFileBytes;
    private long cachedCacheSpeedBytesPerSecond;
    private long textOffsetMs;
    private long audioOffsetMs;
    private float subtitleTextSize;
    private float subtitlePosition;
    private boolean playWhenReady;
    private boolean loading;
    private boolean repeatOne;
    private boolean ownsSurface;
    private boolean initialized;
    private boolean released;
    private boolean surfaceAttached;
    private boolean fileLoaded;
    private boolean loadStarted;
    private boolean playbackRestarted;
    private boolean stopping;
    private boolean eofReached;
    private boolean idleActive;
    private boolean currentLikelyHls;
    private boolean currentLikelyDash;
    private boolean sawNoAvData;
    private boolean sawInvalidData;
    private boolean sawPngVideo;
    private boolean sawNetworkError;
    private boolean sawDecodeError;
    private boolean sawVideoOutputError;
    private boolean sawDrmError;
    private boolean cachedCacheIdle;
    private boolean cachedCacheUnderrun;
    private boolean cachedCacheBof;
    private boolean cachedCacheEof;
    private boolean preferAacApplied;
    private boolean audioTrackManuallySelected;
    private int loadStartRetryCount;
    private int videoReconfigCount;
    private int currentChapter;
    private int cachedCacheBufferingState;
    private int surfaceWidth;
    private int surfaceHeight;
    private String attachedVo;
    private String lastFailureLog;
    private int lastEndFileReason;
    private int lastEndFileError;
    private String lastEndFileErrorText;
    private String cachedCurrentVo;
    private String cachedCurrentGpuContext;
    private String cachedGpuApi;
    private String cachedCurrentAo;
    private String cachedAudioDevice;
    private String cachedHwdecCurrent;
    private double cachedAvSyncSeconds;
    private double cachedDisplayFps;
    private double cachedEstimatedDisplayFps;
    private float cachedContentFrameRate;
    private long cachedDecoderDroppedFrames;
    private long cachedOutputDroppedFrames;
    private long cachedMistimedFrames;
    private long cachedDelayedFrames;
    private boolean cachedDisplaySyncActive;
    private float volume;

    public MpvPlayer(Context context, MpvPlayerConfig config) {
        super(Looper.getMainLooper());
        this.context = context.getApplicationContext();
        this.config = config;
        mainHandler = new Handler(Looper.getMainLooper());
        stateRefreshRunnable = this::refreshPlaybackState;
        endFileValidationRunnable = this::validateEarlyEndFile;
        loadStartRetryRunnable = this::retryLoadIfNotStarted;
        isoTrackMetadataReadyListener = this::onIsoTrackMetadataReady;
        hlsProxy = new MpvHlsProxy();
        recentLogs = new ArrayList<>();
        contentFds = new ArrayList<>();
        File externalFiles = this.context.getExternalFilesDir(null);
        subtitleDiagnosticFile = externalFiles == null ? null : new File(externalFiles, "mpv-subtitle-debug.log");
        if (subtitleDiagnosticFile != null && subtitleDiagnosticFile.length() > 2 * 1024 * 1024) subtitleDiagnosticFile.delete();
        playbackParameters = PlaybackParameters.DEFAULT;
        currentTracks = Tracks.EMPTY;
        currentChapters = List.of();
        videoSize = VideoSize.UNKNOWN;
        playbackState = Player.STATE_IDLE;
        pendingSeekPositionMs = C.TIME_UNSET;
        cachedDurationMs = C.TIME_UNSET;
        currentChapter = C.INDEX_UNSET;
        lastEndFileReason = MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_UNKNOWN;
        textOffsetMs = 0;
        audioOffsetMs = 0;
        subtitleTextSize = 0f;
        subtitlePosition = 0f;
        playWhenReady = true;
        volume = 1f;
    }

    @Override
    protected State getState() {
        int state = playbackState;
        MediaItem currentItem = mediaItem;
        if (currentItem == null && state != Player.STATE_IDLE && state != Player.STATE_ENDED) {
            Log.w(TAG, "Coerce empty playlist state=" + state + " loading=" + loading + " fileLoaded=" + fileLoaded + " playbackRestarted=" + playbackRestarted);
            state = Player.STATE_IDLE;
        }
        State.Builder builder = new State.Builder()
                .setAvailableCommands(COMMANDS)
                .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .setPlaybackState(state)
                .setIsLoading(currentItem != null && loading && state != Player.STATE_IDLE && state != Player.STATE_ENDED)
                .setPlayerError(playerError)
                .setRepeatMode(repeatOne ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF)
                .setPlaybackParameters(playbackParameters)
                .setTextOffsetMs(textOffsetMs)
                .setAudioOffsetMs(audioOffsetMs)
                .setVideoSize(videoSize)
                .setVolume(volume)
                .setCurrentMediaEditions(currentChapters)
                .setPlaylist(currentItem == null ? ImmutableList.of() : ImmutableList.of(mediaItemData(currentItem)))
                .setCurrentMediaItemIndex(currentItem == null ? C.INDEX_UNSET : 0);
        if (currentItem != null) {
            long duration = durationMs();
            long position = positionMs();
            PositionSupplier positionSupplier = isPlayingInternal()
                    ? PositionSupplier.getExtrapolating(position, playbackParameters.speed)
                    : PositionSupplier.getConstant(position);
            builder.setContentPositionMs(positionSupplier);
            builder.setContentBufferedPositionMs(PositionSupplier.getConstant(bufferedPositionMs(position, duration)));
            builder.setTotalBufferedDurationMs(PositionSupplier.getConstant(Math.max(0, bufferedPositionMs(position, duration) - position)));
        }
        return builder.build();
    }

    private MediaItemData mediaItemData(MediaItem item) {
        long duration = durationMs();
        return new MediaItemData.Builder(item.mediaId)
                .setMediaItem(item)
                .setMediaMetadata(item.mediaMetadata)
                .setDurationUs(duration == C.TIME_UNSET ? C.TIME_UNSET : duration * 1000)
                .setIsSeekable(duration > 0)
                .setIsDynamic(duration == C.TIME_UNSET)
                .setTracks(currentTracks)
                .build();
    }

    @Override
    protected ListenableFuture<?> handleSetMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
        mediaItem = mediaItems.isEmpty() ? null : mediaItems.get(0);
        pendingSeekPositionMs = mediaItem != null && startPositionMs > 0 ? startPositionMs : C.TIME_UNSET;
        cachedPositionMs = Math.max(0, startPositionMs == C.TIME_UNSET ? 0 : startPositionMs);
        cachedDurationMs = C.TIME_UNSET;
        resetCacheState();
        currentTracks = Tracks.EMPTY;
        currentChapters = List.of();
        playbackState = mediaItem == null ? Player.STATE_IDLE : Player.STATE_IDLE;
        loading = false;
        fileLoaded = false;
        playbackRestarted = false;
        loadStarted = false;
        loadStartRetryCount = 0;
        eofReached = false;
        idleActive = false;
        preferAacApplied = false;
        audioTrackManuallySelected = false;
        currentPlayableUri = null;
        closeIsoSession();
        currentLikelyHls = false;
        currentLikelyDash = false;
        currentChapter = C.INDEX_UNSET;
        resetFailureSignals();
        recentLogs.clear();
        playerError = null;
        resetMpvContextForNewMedia();
        mainHandler.removeCallbacks(endFileValidationRunnable);
        mainHandler.removeCallbacks(loadStartRetryRunnable);
        closeContentFds();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleAddMediaItems(int index, List<MediaItem> mediaItems) {
        if (mediaItem == null && !mediaItems.isEmpty()) mediaItem = mediaItems.get(0);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleReplaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems) {
        if (mediaItems.isEmpty()) {
            stopInternal(true);
            mediaItem = null;
            invalidateState();
        } else {
            mediaItem = mediaItems.get(0);
        }
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleRemoveMediaItems(int fromIndex, int toIndex) {
        stopInternal(true);
        mediaItem = null;
        invalidateState();
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
        if (initialized && playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED) {
            safeSetPropertyBoolean("pause", !playWhenReady);
        }
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleStop() {
        stopInternal(true);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleRelease() {
        released = true;
        stopInternal(false);
        hlsProxy.release();
        clearVideoOutput();
        mainHandler.removeCallbacks(stateRefreshRunnable);
        mainHandler.removeCallbacks(endFileValidationRunnable);
        releaseNativeContext("release");
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetRepeatMode(int repeatMode) {
        repeatOne = repeatMode == Player.REPEAT_MODE_ONE;
        if (initialized) safeSetPropertyString("loop-file", repeatOne ? "inf" : "no");
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSeek(int mediaItemIndex, long positionMs, int seekCommand) {
        if (positionMs == C.TIME_UNSET) positionMs = 0;
        cachedPositionMs = Math.max(0, positionMs);
        pendingSeekPositionMs = cachedPositionMs;
        if (initialized && playbackState != Player.STATE_IDLE) {
            seekMpv(cachedPositionMs);
            if (currentLikelyHls && playbackRestarted) hlsProxy.preloadAround(cachedPositionMs);
            if (playbackState == Player.STATE_ENDED) playbackState = Player.STATE_BUFFERING;
        }
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetPlaybackParameters(PlaybackParameters playbackParameters) {
        this.playbackParameters = playbackParameters;
        if (initialized) safeSetPropertyDouble("speed", playbackParameters.speed);
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetVolume(float volume, int volumeOperationType) {
        this.volume = Math.max(0f, Math.min(1f, volume));
        if (initialized) safeSetPropertyDouble("volume", this.volume * 100.0);
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetTextOffsetMs(long offsetMs) {
        textOffsetMs = offsetMs;
        applyTextOffset();
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetAudioOffsetMs(long offsetMs) {
        audioOffsetMs = offsetMs;
        applyAudioOffset();
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    public void setSubtitleStyle(float textSize, float position) {
        subtitleTextSize = textSize;
        subtitlePosition = position;
        applySubtitleStyle();
        invalidateState();
    }

    public String getAudioSpdifCodecs() {
        return config.audioSpdif();
    }

    public void setLutShader(@Nullable MpvLutShader shader) {
        lutShader = shader;
        applyShaderPipeline(false);
    }

    public PlayerCacheState getCacheState() {
        if (initialized && mediaItem != null) refreshCacheState();
        return new PlayerCacheState(
                true,
                config.cache(),
                cachedCacheIdle,
                cachedCacheUnderrun,
                cachedCacheBof,
                cachedCacheEof,
                cachedCacheBufferingState,
                cachedCacheDurationMs,
                cachedCacheEndMs,
                cachedCacheReaderPositionMs,
                cachedCacheForwardBytes,
                cachedCacheTotalBytes,
                cachedCacheFileBytes,
                cachedCacheSpeedBytesPerSecond,
                config.demuxerMaxBytes(),
                config.demuxerMaxBackBytes(),
                config.cacheSeconds(),
                config.demuxerReadaheadSeconds());
    }

    public String getRenderDiagnostics() {
        refreshRenderState();
        String requested = isConfiguredVulkan() ? "vulkan" : "opengl";
        String currentVo = firstNonEmpty(cachedCurrentVo, initialized ? stringProperty("current-vo", "") : "", config.vo());
        String currentGpuContext = firstNonEmpty(cachedCurrentGpuContext, initialized ? stringProperty("current-gpu-context", "") : "", config.gpuContext());
        String gpuApi = firstNonEmpty(cachedGpuApi, initialized ? stringProperty("gpu-api", "") : "", config.gpuApi(), config.openglEs() ? "opengl-es" : "");
        String actual = isRuntimeVulkan(currentVo, currentGpuContext, gpuApi) ? "vulkan" : "opengl";
        return "请求 " + requested
                + " / 实际 " + actual
                + " / vo " + emptyDash(currentVo)
                + " / context " + emptyDash(currentGpuContext)
                + " / api " + emptyDash(gpuApi);
    }

    public String getRuntimeDiagnostics() {
        refreshRuntimeDiagnostics();
        String hwdec = firstNonEmpty(cachedHwdecCurrent, initialized ? stringProperty("hwdec-current", "") : "", config.hwdec());
        String ao = firstNonEmpty(cachedCurrentAo, initialized ? stringProperty("current-ao", "") : "", config.ao());
        String audioDevice = firstNonEmpty(cachedAudioDevice, initialized ? stringProperty("audio-device", "") : "");
        return joinParts(
                "hwdec " + emptyDash(hwdec),
                "ao " + emptyDash(ao),
                TextUtils.isEmpty(audioDevice) ? "" : "device " + shortText(audioDevice, 32),
                formatAvSync(),
                formatDisplayFps(),
                formatDroppedFrames(),
                formatDisplaySync(),
                formatShader());
    }

    public long getDroppedFrames() {
        refreshRuntimeDiagnostics();
        return Math.max(0, cachedDecoderDroppedFrames) + Math.max(0, cachedOutputDroppedFrames);
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
    public void eventProperty(String property) {
        postToMain(() -> handleProperty(property, null));
    }

    @Override
    public void eventProperty(String property, long value) {
        postToMain(() -> handleProperty(property, value));
    }

    @Override
    public void eventProperty(String property, boolean value) {
        postToMain(() -> handleProperty(property, value));
    }

    @Override
    public void eventProperty(String property, String value) {
        postToMain(() -> handleProperty(property, value));
    }

    @Override
    public void eventProperty(String property, double value) {
        postToMain(() -> handleProperty(property, value));
    }

    @Override
    public void event(int eventId) {
        postToMain(() -> handleEvent(eventId));
    }

    @Override
    public void endFile(int reason, int error, String errorText) {
        postToMain(() -> handleEndFile(reason, error, errorText));
    }

    @Override
    public void logMessage(String prefix, int level, String text) {
        postToMain(() -> {
            if (released) return;
            String line = prefix + ": " + text;
            rememberLog(line);
            markFailureSignal(line);
            String lower = line.toLowerCase(Locale.US);
            if (lower.contains("sub") || lower.contains("font") || lower.contains("track switched") || lower.contains("mkv: select track")) appendSubtitleDiagnostic("native " + line);
            if (shouldDebugLogMpvLine(line)) SpiderDebug.log("mpv", "%s", line);
        });
    }

    private void openCurrent() {
        if (mediaItem == null || mediaItem.localConfiguration == null) return;
        try {
            ensureInitialized();
            playbackState = Player.STATE_BUFFERING;
            loading = true;
            playerError = null;
            fileLoaded = false;
            loadStarted = false;
            playbackRestarted = false;
            loadStartRetryCount = 0;
            videoReconfigCount = 0;
            lastVideoSizeCandidateLog = null;
            eofReached = false;
            idleActive = false;
            cachedDurationMs = C.TIME_UNSET;
            cachedCacheDurationMs = 0;
            resetRuntimeDiagnostics();
            resetFailureSignals();
            recentLogs.clear();
            mainHandler.removeCallbacks(endFileValidationRunnable);
            closeContentFds();
            if (hasDrmConfiguration(mediaItem)) {
                fail(mpvError(ERROR_DRM_UNSUPPORTED, "MediaItem DRM configuration is not supported by libmpv"), PlaybackException.ERROR_CODE_DRM_UNSPECIFIED);
                return;
            }
            Map<String, String> headers = applyMediaOptions(mediaItem);
            bindVideoOutput();
            safeSetPropertyBoolean("pause", !playWhenReady);
            safeSetPropertyString("loop-file", repeatOne ? "inf" : "no");
            safeSetPropertyDouble("speed", playbackParameters.speed);
            safeSetPropertyDouble("volume", volume * 100.0);
            applyTextOffset();
            applyAudioOffset();
            applySubtitleStyle();
            currentPlayableUri = playableUri(mediaItem);
            logSourceDiagnostics(mediaItem, currentPlayableUri, headers);
            boolean declaredIso = isLikelyIso(mediaItem, currentPlayableUri);
            if (!declaredIso && shouldProbeOpaqueIso(mediaItem, currentPlayableUri)) {
                String probingUri = currentPlayableUri;
                IsoSessionManager.probeAndCreateAsync(probingUri, headers, isoUri -> mainHandler.post(() -> {
                    if (released || stopping || !TextUtils.equals(currentPlayableUri, probingUri)) {
                        IsoSessionManager.closeUri(isoUri);
                        return;
                    }
                    currentIsoUri = isoUri;
                    attachIsoTrackMetadataListener();
                    if (currentIsoUri != null) currentPlayableUri = currentIsoUri;
                    continueOpenCurrent(headers);
                }));
                return;
            }
            if (declaredIso) {
                currentIsoUri = IsoSessionManager.create(currentPlayableUri, headers);
                attachIsoTrackMetadataListener();
            }
            if (currentIsoUri != null) currentPlayableUri = currentIsoUri;
            continueOpenCurrent(headers);
        } catch (Throwable e) {
            fail(classifyLoadError(e, e.getMessage()), PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
        }
    }

    private void continueOpenCurrent(Map<String, String> headers) {
        try {
            if (currentIsoUri != null) {
                currentLikelyHls = false;
                currentLikelyDash = false;
                hlsProxy.clear();
                Log.i(TAG, "load remote optical-disc ISO session");
            } else {
                currentLikelyHls = isLikelyHls(mediaItem, currentPlayableUri);
                currentLikelyDash = isLikelyDash(mediaItem, currentPlayableUri);
            }
            if (currentIsoUri == null && shouldProxyHls(currentPlayableUri, currentLikelyHls)) {
                String originalUri = currentPlayableUri;
                currentPlayableUri = hlsProxy.proxy(originalUri, headers);
                SpiderDebug.log("mpv", "hls proxy enabled original=%s proxy=%s", originalUri, currentPlayableUri);
            } else {
                hlsProxy.clear();
            }
            applyShaderPipeline(true);
            Log.d(TAG, "load scheme=" + safeScheme(currentPlayableUri) + " urlLen=" + (currentPlayableUri == null ? 0 : currentPlayableUri.length()) + " hls=" + currentLikelyHls + " dash=" + currentLikelyDash);
            SpiderDebug.log("mpv", "load scheme=%s urlLen=%d hls=%s dash=%s surface=%s attached=%s hwdec=%s vo=%s gpuContext=%s gpuApi=%s", safeScheme(currentPlayableUri), currentPlayableUri == null ? 0 : currentPlayableUri.length(), currentLikelyHls, currentLikelyDash, surface != null && surface.isValid(), surfaceAttached, config.hwdec(), config.vo(), config.gpuContext(), config.gpuApi());
            loadCurrentUri();
            scheduleLoadStartRetry();
            invalidateState();
            startStateRefresh();
        } catch (Throwable e) {
            fail(classifyLoadError(e, e.getMessage()), PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
        }
    }

    private void ensureInitialized() throws IOException {
        synchronized (NATIVE_CONTEXT_LOCK) {
            if (initialized && nativeContextOwner == this) return;
            if (nativeContextOwner != null && nativeContextOwner != this) {
                SpiderDebug.log("mpv", "native context takeover old=%s new=%s", identity(nativeContextOwner), identity(this));
                nativeContextOwner.released = true;
                nativeContextOwner.releaseNativeContextLocked("takeover");
            }
            if (!MPVLib.ensureLoaded(context)) {
                Throwable e = MPVLib.getLoadError();
                if (e instanceof IOException io) throw io;
                if (e instanceof RuntimeException runtime) throw runtime;
                throw new IOException(e == null ? "MPV native libraries are unavailable" : e.getMessage(), e);
            }
            copySupportAssets();
            nativeContextOwner = this;
            if (!MPVLib.tryCreate(context)) {
                throw new IOException("MPV native context creation is already in progress");
            }
            applyPreInitOptions();
            MPVLib.init();
            initialized = true;
            MPVLib.addObserver(this);
            MPVLib.addLogObserver(this);
            applyPostInitOptions();
            applyShaderPipeline(true);
            observeProperties();
        }
    }

    private void applyPreInitOptions() {
        setOption("config", "yes");
        setOption("config-dir", config.configDir().getAbsolutePath());
        setOption("gpu-shader-cache-dir", config.cacheDir().getAbsolutePath());
        setOption("icc-cache-dir", config.cacheDir().getAbsolutePath());
        setOption("vo", config.vo());
        setOption("gpu-context", config.gpuContext());
        if (!TextUtils.isEmpty(config.gpuApi())) setOption("gpu-api", config.gpuApi());
        if (config.openglEs()) setOption("opengl-es", "yes");
        setOption("hwdec", config.hwdec());
        setOption("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1");
        setOption("ao", config.ao());
        if (!TextUtils.isEmpty(config.audioSpdif())) setOption("audio-spdif", config.audioSpdif());
        setOption("audio-set-media-role", "yes");
        setOption("tls-verify", config.tlsVerify() ? "yes" : "no");
        if (config.caFile().isFile()) setOption("tls-ca-file", config.caFile().getAbsolutePath());
        setOption("input-default-bindings", "yes");
        setOption("cache", config.cache() ? "yes" : "no");
        setOption("cache-secs", String.valueOf(config.cacheSeconds()));
        setOption("cache-pause", "yes");
        setOption("cache-pause-initial", "no");
        setOption("cache-pause-wait", String.format(Locale.US, "%.3f", config.rebufferMs() / SECONDS_TO_MS));
        setOption("demuxer-thread", "yes");
        setOption("demuxer-seekable-cache", "auto");
        setOption("demuxer-max-bytes", String.valueOf(config.demuxerMaxBytes()));
        setOption("demuxer-max-back-bytes", String.valueOf(config.demuxerMaxBackBytes()));
        setOption("demuxer-readahead-secs", String.valueOf(config.demuxerReadaheadSeconds()));
        // These options are required for reliable text subtitle rendering on Android.
        // This libmpv build includes libass but not fontconfig, so let libass index the
        // Android system font directory directly instead of selecting an unavailable
        // fontconfig provider. Keep this in native initialization because user configs
        // may replace the bundled defaults.
        setOption("sub-ass", "yes");
        setOption("sub-ass-override", "yes");
        setOption("embeddedfonts", "yes");
        setOption("sub-fix-timing", "yes");
        setOption("sub-use-margins", "yes");
        setOption("sub-font-provider", "none");
        setOption("msg-level", config.logLevel());
        for (Map.Entry<String, String> entry : config.extraOptions().entrySet()) setOption(entry.getKey(), entry.getValue());
    }

    private void applyPostInitOptions() {
        setRuntimeString("save-position-on-quit", "no");
        setRuntimeString("force-window", "no");
        setRuntimeString("idle", "yes");
        if (config.performanceOptionsPriority()) applyPerformanceOptionOverlay();
        SpiderDebug.log("mpv", "option priority=%s effective cache maxBytes=%s backBytes=%s cacheSecs=%s readaheadSecs=%s initial=%s rebufferWait=%s", config.performanceOptionsPriority() ? "performance" : "mpv.conf", stringProperty("demuxer-max-bytes", "?"), stringProperty("demuxer-max-back-bytes", "?"), stringProperty("cache-secs", "?"), stringProperty("demuxer-readahead-secs", "?"), stringProperty("cache-pause-initial", "?"), stringProperty("cache-pause-wait", "?"));
    }

    private void applyPerformanceOptionOverlay() {
        setRuntimeString("vo", config.vo());
        setRuntimeString("gpu-context", config.gpuContext());
        if (!TextUtils.isEmpty(config.gpuApi())) setRuntimeString("gpu-api", config.gpuApi());
        if (config.openglEs()) setRuntimeString("opengl-es", "yes");
        setRuntimeString("hwdec", config.hwdec());
        setRuntimeString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1");
        setRuntimeString("ao", config.ao());
        setRuntimeString("audio-spdif", config.audioSpdif());
        setRuntimeString("cache", config.cache() ? "yes" : "no");
        setRuntimeString("cache-secs", String.valueOf(config.cacheSeconds()));
        setRuntimeString("cache-pause", "yes");
        setRuntimeString("cache-pause-initial", "no");
        setRuntimeString("cache-pause-wait", String.format(Locale.US, "%.3f", config.rebufferMs() / SECONDS_TO_MS));
        setRuntimeString("demuxer-thread", "yes");
        setRuntimeString("demuxer-seekable-cache", "auto");
        setRuntimeString("demuxer-max-bytes", String.valueOf(config.demuxerMaxBytes()));
        setRuntimeString("demuxer-max-back-bytes", String.valueOf(config.demuxerMaxBackBytes()));
        setRuntimeString("demuxer-readahead-secs", String.valueOf(config.demuxerReadaheadSeconds()));
        for (Map.Entry<String, String> entry : config.extraOptions().entrySet()) setRuntimeString(entry.getKey(), entry.getValue());
    }

    private void observeProperties() {
        observe("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("time-pos/full", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("duration/full", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("demuxer-cache-duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("demuxer-cache-time", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("demuxer-cache-idle", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        observe("cache-speed", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("cache-buffering-state", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("demuxer-cache-state/cache-duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("demuxer-cache-state/cache-end", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("demuxer-cache-state/reader-pts", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("demuxer-cache-state/fw-bytes", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("demuxer-cache-state/total-bytes", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("demuxer-cache-state/file-cache-bytes", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("demuxer-cache-state/raw-input-rate", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("demuxer-cache-state/bof-cached", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        observe("demuxer-cache-state/eof-cached", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        observe("demuxer-cache-state/idle", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        observe("demuxer-cache-state/underrun", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        observe("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        observe("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        observe("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        observe("idle-active", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        observe("width", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("height", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("video-params/w", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("video-params/h", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("video-params/dw", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("video-params/dh", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("video-out-params/w", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("video-out-params/h", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("video-out-params/dw", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("video-out-params/dh", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("container-fps", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("estimated-vf-fps", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("video-params/primaries", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("video-params/gamma", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("video-params/colorlevels", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("video-params/colormatrix", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("current-vo", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("current-gpu-context", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("gpu-api", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("current-ao", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("audio-device", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("hwdec-current", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("avsync", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("display-fps", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("estimated-display-fps", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("decoder-frame-drop-count", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("frame-drop-count", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("mistimed-frame-count", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("vo-delayed-frame-count", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("display-sync-active", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        observe("track-list/count", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("vid", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("aid", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("sid", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("secondary-sid", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("current-tracks/video/id", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("current-tracks/video/demux-w", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("current-tracks/video/demux-h", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("current-tracks/audio/id", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("current-tracks/sub/id", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("current-tracks/sub2/id", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("chapter", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("chapter-list", MPVLib.MpvFormat.MPV_FORMAT_STRING);
    }

    private void handleProperty(String property, @Nullable Object value) {
        if (released) return;
        if (mediaItem == null) {
            playbackState = Player.STATE_IDLE;
            loading = false;
            return;
        }
        switch (property) {
            case "time-pos", "time-pos/full" -> cachedPositionMs = doubleSecondsToMs(value, cachedPositionMs);
            case "duration", "duration/full" -> cachedDurationMs = doubleSecondsToMs(value, cachedDurationMs);
            case "demuxer-cache-duration", "demuxer-cache-state/cache-duration" -> cachedCacheDurationMs = Math.max(0, doubleSecondsToMs(value, cachedCacheDurationMs));
            case "demuxer-cache-time", "demuxer-cache-state/cache-end" -> cachedCacheEndMs = Math.max(0, doubleSecondsToMs(value, cachedCacheEndMs));
            case "demuxer-cache-state/reader-pts" -> cachedCacheReaderPositionMs = Math.max(0, doubleSecondsToMs(value, cachedCacheReaderPositionMs));
            case "cache-speed", "demuxer-cache-state/raw-input-rate" -> cachedCacheSpeedBytesPerSecond = Math.max(0, longValue(value, cachedCacheSpeedBytesPerSecond));
            case "cache-buffering-state" -> cachedCacheBufferingState = Math.max(0, (int) Math.min(100, longValue(value, cachedCacheBufferingState)));
            case "demuxer-cache-state/fw-bytes" -> cachedCacheForwardBytes = Math.max(0, longValue(value, cachedCacheForwardBytes));
            case "demuxer-cache-state/total-bytes" -> cachedCacheTotalBytes = Math.max(0, longValue(value, cachedCacheTotalBytes));
            case "demuxer-cache-state/file-cache-bytes" -> cachedCacheFileBytes = Math.max(0, longValue(value, cachedCacheFileBytes));
            case "demuxer-cache-idle", "demuxer-cache-state/idle" -> cachedCacheIdle = Boolean.TRUE.equals(value);
            case "demuxer-cache-state/underrun" -> cachedCacheUnderrun = Boolean.TRUE.equals(value);
            case "demuxer-cache-state/bof-cached" -> cachedCacheBof = Boolean.TRUE.equals(value);
            case "demuxer-cache-state/eof-cached" -> cachedCacheEof = Boolean.TRUE.equals(value);
            case "pause" -> {
                if (value instanceof Boolean paused) playWhenReady = !paused;
            }
            case "paused-for-cache" -> applyPausedForCache(Boolean.TRUE.equals(value));
            case "eof-reached" -> {
                eofReached = Boolean.TRUE.equals(value);
                if (eofReached) markPlaybackEnded("property:eof-reached");
            }
            case "idle-active" -> {
                idleActive = Boolean.TRUE.equals(value);
                if (idleActive && fileLoaded && !stopping) markPlaybackEnded("property:idle-active");
            }
            case "width", "height", "video-params/w", "video-params/h", "video-params/dw", "video-params/dh", "video-out-params/w", "video-out-params/h", "video-out-params/dw", "video-out-params/dh", "current-tracks/video/demux-w", "current-tracks/video/demux-h" -> {
                updateVideoSize("property:" + property);
                refreshTracks();
            }
            case "container-fps", "estimated-vf-fps" -> {
                cachedContentFrameRate = videoFrameRate();
                applySurfaceFrameRate();
                refreshTracks();
            }
            case "video-params/primaries", "video-params/gamma", "video-params/colorlevels", "video-params/colormatrix" -> refreshTracks();
            case "current-vo" -> cachedCurrentVo = stringValue(value, cachedCurrentVo);
            case "current-gpu-context" -> cachedCurrentGpuContext = stringValue(value, cachedCurrentGpuContext);
            case "gpu-api" -> cachedGpuApi = stringValue(value, cachedGpuApi);
            case "current-ao" -> cachedCurrentAo = stringValue(value, cachedCurrentAo);
            case "audio-device" -> cachedAudioDevice = stringValue(value, cachedAudioDevice);
            case "hwdec-current" -> cachedHwdecCurrent = stringValue(value, cachedHwdecCurrent);
            case "avsync" -> cachedAvSyncSeconds = doubleValue(value, cachedAvSyncSeconds);
            case "display-fps" -> cachedDisplayFps = doubleValue(value, cachedDisplayFps);
            case "estimated-display-fps" -> cachedEstimatedDisplayFps = doubleValue(value, cachedEstimatedDisplayFps);
            case "decoder-frame-drop-count" -> cachedDecoderDroppedFrames = Math.max(0, longValue(value, cachedDecoderDroppedFrames));
            case "frame-drop-count" -> cachedOutputDroppedFrames = Math.max(0, longValue(value, cachedOutputDroppedFrames));
            case "mistimed-frame-count" -> cachedMistimedFrames = Math.max(0, longValue(value, cachedMistimedFrames));
            case "vo-delayed-frame-count" -> cachedDelayedFrames = Math.max(0, longValue(value, cachedDelayedFrames));
            case "display-sync-active" -> cachedDisplaySyncActive = Boolean.TRUE.equals(value);
            case "track-list/count" -> {
                updateVideoSize("property:" + property);
                refreshTracks();
            }
            case "vid", "aid", "sid", "secondary-sid", "current-tracks/video/id", "current-tracks/audio/id", "current-tracks/sub/id", "current-tracks/sub2/id" -> refreshTracks();
            case "chapter" -> {
                if (value instanceof Number number) currentChapter = number.intValue();
                refreshChapters();
            }
            case "chapter-list" -> handleChapterListProperty(value);
            default -> {
            }
        }
        invalidateState();
    }

    public Tracks getCurrentTracksSnapshot() {
        return currentTracks;
    }

    public void resetTrackSelection() {
        audioTrackManuallySelected = true;
        preferAacApplied = true;
        setMpvTrack(C.TRACK_TYPE_VIDEO, "auto");
        setMpvTrack(C.TRACK_TYPE_AUDIO, "auto");
        setMpvTrack(C.TRACK_TYPE_TEXT, "auto");
        setSecondarySubtitleTrackSelection("no");
        refreshTracks();
        invalidateState();
    }

    public void setTrackSelection(int type, String mpvId) {
        if (TextUtils.isEmpty(mpvId)) return;
        if (type == C.TRACK_TYPE_AUDIO) {
            audioTrackManuallySelected = true;
            preferAacApplied = true;
        }
        if (type == C.TRACK_TYPE_TEXT) logSubtitleState("before-select requested=" + mpvId);
        setMpvTrack(type, mpvId);
        if (type == C.TRACK_TYPE_TEXT) {
            logSubtitleState("after-select requested=" + mpvId);
            mainHandler.postDelayed(() -> logSubtitleState("after-select-100ms requested=" + mpvId), 100);
            mainHandler.postDelayed(() -> logSubtitleState("after-select-500ms requested=" + mpvId), 500);
        }
        refreshTracks();
        invalidateState();
    }

    public void restoreVideoTrackSelection() {
        if (!initialized) return;
        int count = Math.max(0, intProperty("track-list/count", 0));
        for (int index = 0; index < count; index++) {
            TrackInfo info = readTrackInfo(index, C.INDEX_UNSET);
            if (info == null || info.type != C.TRACK_TYPE_VIDEO) continue;
            setMpvTrack(C.TRACK_TYPE_VIDEO, info.id);
            SpiderDebug.log("mpv", "restore video track id=%s codec=%s", info.id, info.codec);
            refreshTracks();
            invalidateState();
            return;
        }
        SpiderDebug.log("mpv", "restore video track skipped no video track");
    }

    public void setSecondarySubtitleTrackSelection(String mpvId) {
        if (TextUtils.isEmpty(mpvId) || !initialized) return;
        safeSetPropertyString("secondary-sid", mpvId);
        SpiderDebug.log("mpv", "select secondary subtitle id=%s", mpvId);
        refreshTracks();
        invalidateState();
    }

    public boolean isSecondarySubtitleSelected(String mpvId) {
        if (TextUtils.isEmpty(mpvId)) return false;
        String selected = secondarySubtitleTrackId();
        if (TextUtils.isEmpty(selected) || isAutoTrackChoice(selected) || isDisabledTrackChoice(selected)) return false;
        return selected.equals(mpvId) || normalizeTrackId(selected).equals(normalizeTrackId(mpvId));
    }

    public boolean selectEdition(MediaEdition edition) {
        if (edition == null || edition.index < 0 || edition.index >= currentChapters.size()) return false;
        currentChapter = edition.index;
        if (initialized) safeSetPropertyInt("chapter", edition.index);
        refreshChapters();
        invalidateState();
        return true;
    }

    private void setMpvTrack(int type, String mpvId) {
        if (!initialized) return;
        String property = mpvTrackProperty(type);
        if (property == null) return;
        try {
            MPVLib.setPropertyString(property, mpvId);
            Log.d(TAG, "set track property=" + property + " requested=" + mpvId + " actual=" + propertyStringOrInt(property));
            appendSubtitleDiagnostic("set-track property=" + property + " requested=" + mpvId + " actual=" + propertyStringOrInt(property));
        } catch (Throwable e) {
            Log.e(TAG, "set track failed property=" + property + " requested=" + mpvId, e);
            appendSubtitleDiagnostic("set-track-failed property=" + property + " requested=" + mpvId + " error=" + e);
        }
        SpiderDebug.log("mpv", "select track type=%d property=%s id=%s", type, property, mpvId);
    }

    private void logSubtitleState(String reason) {
        if (!initialized) return;
        String text = stringProperty("sub-text", "");
        String state = reason
                + " positionMs=" + cachedPositionMs
                + " sid=" + propertyStringOrInt("sid")
                + " currentSub=" + propertyStringOrInt("current-tracks/sub/id")
                + " visible=" + booleanProperty("sub-visibility", true)
                + " subStart=" + doubleProperty("sub-start", Double.NaN)
                + " subEnd=" + doubleProperty("sub-end", Double.NaN)
                + " textLength=" + text.length()
                + " text=" + (text.length() > 80 ? text.substring(0, 80) : text);
        Log.d(TAG, "subtitle-state " + state);
        appendSubtitleDiagnostic("state " + state);
    }

    private synchronized void appendSubtitleDiagnostic(String text) {
        if (subtitleDiagnosticFile == null) return;
        File parent = subtitleDiagnosticFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        String line = System.currentTimeMillis() + " " + text + "\n";
        try (OutputStream out = new FileOutputStream(subtitleDiagnosticFile, true)) {
            out.write(line.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    @Nullable
    private String mpvTrackProperty(int type) {
        return switch (type) {
            case C.TRACK_TYPE_VIDEO -> "vid";
            case C.TRACK_TYPE_AUDIO -> "aid";
            case C.TRACK_TYPE_TEXT -> "sid";
            default -> null;
        };
    }

    private void handleEvent(int eventId) {
        if (released) return;
        if (mediaItem == null && eventId != MPVLib.MpvEvent.MPV_EVENT_SHUTDOWN) {
            playbackState = Player.STATE_IDLE;
            loading = false;
            Log.d(TAG, "Ignore stale mpv event without media item event=" + eventId);
            invalidateState();
            return;
        }
        switch (eventId) {
            case MPVLib.MpvEvent.MPV_EVENT_START_FILE -> {
                loadStarted = true;
                playbackState = Player.STATE_BUFFERING;
                loading = true;
                fileLoaded = false;
                playbackRestarted = false;
                stopping = false;
                eofReached = false;
                idleActive = false;
                resetFailureSignals();
                SpiderDebug.log("mpv", "event=start-file uri=%s", currentPlayableUri);
                mainHandler.removeCallbacks(endFileValidationRunnable);
                mainHandler.removeCallbacks(loadStartRetryRunnable);
                startStateRefresh();
            }
            case MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                if (loadedUnexpectedImage()) {
                    fail(mpvError(ERROR_UNEXPECTED_IMAGE, "path=" + stringProperty("path", "")), PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED);
                    return;
                }
                fileLoaded = true;
                mainHandler.removeCallbacks(endFileValidationRunnable);
                playbackState = Player.STATE_BUFFERING;
                loading = true;
                cachedDurationMs = durationMs();
                updateVideoSize("event=file-loaded");
                refreshTracks();
                refreshChapters();
                SpiderDebug.log("mpv", "event=file-loaded duration=%d size=%dx%d path=%s", cachedDurationMs, videoSize.width, videoSize.height, stringProperty("path", ""));
                addSubtitleConfigurations();
                if (pendingSeekPositionMs != C.TIME_UNSET) {
                    seekMpv(pendingSeekPositionMs);
                    pendingSeekPositionMs = C.TIME_UNSET;
                }
                safeSetPropertyBoolean("pause", !playWhenReady);
                startStateRefresh();
            }
            case MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> {
                playbackRestarted = true;
                updateVideoSize("event=playback-restart");
                refreshTracks();
                refreshChapters();
                SpiderDebug.log("mpv", "event=playback-restart position=%d duration=%d size=%dx%d", positionMs(), durationMs(), videoSize.width, videoSize.height);
                if (playbackState != Player.STATE_ENDED) {
                    playbackState = Player.STATE_READY;
                    loading = false;
                    startStateRefresh();
                }
            }
            case MPVLib.MpvEvent.MPV_EVENT_VIDEO_RECONFIG -> {
                videoReconfigCount++;
                updateVideoSize("event=video-reconfig#" + videoReconfigCount);
            }
            case MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                handleEndFile(MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_UNKNOWN, MPVLib.MpvError.MPV_ERROR_SUCCESS, null);
                return;
            }
            case MPVLib.MpvEvent.MPV_EVENT_IDLE -> {
                if (fileLoaded && !stopping) {
                    markPlaybackEnded("event:idle");
                } else if (loading && !stopping) {
                    playbackState = Player.STATE_BUFFERING;
                    mainHandler.removeCallbacks(endFileValidationRunnable);
                    mainHandler.postDelayed(endFileValidationRunnable, END_FILE_VALIDATION_DELAY_MS);
                    startStateRefresh();
                }
            }
            case MPVLib.MpvEvent.MPV_EVENT_SHUTDOWN -> {
                if (fileLoaded && !stopping) {
                    markPlaybackEnded("event:shutdown");
                } else {
                    playbackState = Player.STATE_IDLE;
                    loading = false;
                    stopStateRefresh();
                }
            }
            default -> {
            }
        }
        invalidateState();
    }

    private void handleEndFile(int reason, int error, @Nullable String errorText) {
        if (released) return;
        lastEndFileReason = reason;
        lastEndFileError = error;
        lastEndFileErrorText = errorText;
        SpiderDebug.log("mpv", "event=end-file reason=%s(%d) error=%s(%d) text=%s loaded=%s restart=%s eof=%s stopping=%s uri=%s",
                endFileReasonName(reason), reason, mpvErrorName(error), error, TextUtils.isEmpty(errorText) ? "-" : errorText,
                fileLoaded, playbackRestarted, eofReached, stopping, currentPlayableUri);
        stopStateRefresh();
        loading = false;
        if (stopping) {
            stopping = false;
        } else if (reason == MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_ERROR) {
            fail(nativeEndFileError(reason, error, errorText), nativeEndFilePlaybackExceptionCode(error));
            return;
        } else if (isSuccessfulNaturalEof(reason, error)) {
            playbackState = Player.STATE_ENDED;
        } else if (isFailedLoadedMedia()) {
            fail(new IOException(failedLoadedMediaMessage()), PlaybackException.ERROR_CODE_DECODING_FAILED);
            return;
        } else if (fileLoaded || eofReached) {
            markPlaybackEnded("event:end-file");
        } else {
            loading = true;
            playbackState = Player.STATE_BUFFERING;
            mainHandler.removeCallbacks(endFileValidationRunnable);
            mainHandler.postDelayed(endFileValidationRunnable, END_FILE_VALIDATION_DELAY_MS);
            startStateRefresh();
        }
        invalidateState();
    }

    private void markPlaybackEnded(String reason) {
        if (playbackState == Player.STATE_ENDED) return;
        eofReached = true;
        loading = false;
        playbackState = Player.STATE_ENDED;
        stopStateRefresh();
        mainHandler.removeCallbacks(endFileValidationRunnable);
        mainHandler.removeCallbacks(loadStartRetryRunnable);
        SpiderDebug.log("mpv", "playback ended reason=%s position=%d duration=%d", reason, cachedPositionMs, cachedDurationMs);
    }

    private boolean isSuccessfulNaturalEof(int reason, int error) {
        if (reason != MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_EOF || error != MPVLib.MpvError.MPV_ERROR_SUCCESS || !fileLoaded) return false;
        if (eofReached) return true;
        long duration = cachedDurationMs;
        long position = cachedPositionMs;
        if (duration == C.TIME_UNSET || duration <= 0) return playbackRestarted && position > 0 && !sawNetworkError && !sawDecodeError && !sawDrmError;
        long tolerance = Math.max(3000L, Math.min(15000L, duration / 100L));
        return position >= Math.max(0, duration - tolerance);
    }

    private boolean isLikelyHls(MediaItem item, String uri) {
        if (uri != null && uri.startsWith("edl://")) return false;
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
        if (uri != null && uri.startsWith("edl://")) return false;
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

    private boolean loadedUnexpectedImage() {
        String path = stringProperty("path", "");
        if (!isImageUri(path)) return false;
        if (!TextUtils.isEmpty(currentPlayableUri) && sameUri(path, currentPlayableUri)) return false;
        Log.w(TAG, "unexpected image pathScheme=" + safeScheme(path)
                + " requestedScheme=" + safeScheme(currentPlayableUri)
                + " requestedLen=" + (currentPlayableUri == null ? 0 : currentPlayableUri.length()));
        return true;
    }

    private String safeScheme(String value) {
        try {
            return String.valueOf(Uri.parse(value).getScheme());
        } catch (Throwable ignored) {
            return "invalid";
        }
    }

    private boolean isImageUri(String uri) {
        if (TextUtils.isEmpty(uri)) return false;
        String lower = uri.toLowerCase(Locale.US);
        int end = lower.length();
        int query = lower.indexOf('?');
        int fragment = lower.indexOf('#');
        if (query >= 0) end = Math.min(end, query);
        if (fragment >= 0) end = Math.min(end, fragment);
        lower = lower.substring(0, end);
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".webp")
                || lower.endsWith(".gif")
                || lower.endsWith(".bmp")
                || lower.endsWith(".avif");
    }

    private boolean sameUri(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private boolean shouldProxyHls(String uri, boolean likelyHls) {
        if (!likelyHls || TextUtils.isEmpty(uri)) return false;
        Uri parsed = Uri.parse(uri);
        String scheme = parsed.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
        return !"/mpv/index.m3u8".equals(parsed.getPath()) && !"/mpv/item".equals(parsed.getPath());
    }

    private boolean hasDrmConfiguration(MediaItem item) {
        return item != null && item.localConfiguration != null && item.localConfiguration.drmConfiguration != null;
    }

    private Map<String, String> applyMediaOptions(MediaItem item) {
        Map<String, String> headers = new LinkedHashMap<>(extractHeaders(item));
        String userAgent = findHeader(headers, HttpHeaders.USER_AGENT);
        String referer = findHeader(headers, HttpHeaders.REFERER);
        boolean explicitReferer = !TextUtils.isEmpty(referer);
        boolean localProxy = item.localConfiguration != null && isOpaqueLocalProxy(item.localConfiguration.uri.toString());
        if (TextUtils.isEmpty(userAgent)) userAgent = config.userAgent();
        // Loopback media URLs are opaque proxy endpoints. Deriving their origin as
        // the upstream Referer can make the proxy forward 127.0.0.1 to providers
        // which reject it. Preserve only Referer values supplied by the source.
        if (TextUtils.isEmpty(referer) && !localProxy) referer = config.referer();
        if (TextUtils.isEmpty(referer) && !localProxy && item.localConfiguration != null) referer = originOf(item.localConfiguration.uri);
        String origin = findHeader(headers, HEADER_ORIGIN);
        if (!TextUtils.isEmpty(userAgent)) putHeader(headers, HttpHeaders.USER_AGENT, userAgent);
        if (!TextUtils.isEmpty(referer)) putHeader(headers, HttpHeaders.REFERER, referer);
        if (TextUtils.isEmpty(origin) && (!localProxy || explicitReferer)) origin = originOf(referer);
        if (!TextUtils.isEmpty(origin)) putHeader(headers, HEADER_ORIGIN, origin);
        if (TextUtils.isEmpty(findHeader(headers, HEADER_ACCEPT))) putHeader(headers, HEADER_ACCEPT, "*/*");
        String headerFields = buildHeaderFields(headers);
        setRuntimeString("user-agent", userAgent == null ? "" : userAgent);
        setRuntimeString("referrer", referer == null ? "" : referer);
        setRuntimeString("http-header-fields", headerFields);
        if (item.mediaMetadata.title != null) setRuntimeString("force-media-title", item.mediaMetadata.title.toString());
        SpiderDebug.log("mpv", "media options localProxy=%s uaEmpty=%s refererEmpty=%s originEmpty=%s headerNames=%s headerFields=%s",
                localProxy, TextUtils.isEmpty(userAgent), TextUtils.isEmpty(referer), TextUtils.isEmpty(origin), headerNames(headers), !TextUtils.isEmpty(headerFields));
        return headers;
    }

    private Map<String, String> extractHeaders(MediaItem item) {
        if (item.requestMetadata.extras == null) return Map.of();
        android.os.Bundle extras = item.requestMetadata.extras;
        java.util.LinkedHashMap<String, String> headers = new java.util.LinkedHashMap<>();
        for (String key : extras.keySet()) {
            String value = extras.getString(key);
            if (value != null) headers.put(key, value);
        }
        return headers;
    }

    private String buildHeaderFields(Map<String, String> headers) {
        if (headers.isEmpty()) return "";
        List<String> fields = new ArrayList<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            if (equalsHeader(key, HttpHeaders.USER_AGENT) || equalsHeader(key, HttpHeaders.REFERER) || equalsHeader(key, HttpHeaders.RANGE)) continue;
            fields.add(key + ": " + escapeListValue(entry.getValue()));
        }
        return String.join(",", fields);
    }

    private void putHeader(Map<String, String> headers, String name, String value) {
        if (TextUtils.isEmpty(value)) return;
        String existing = null;
        for (String key : headers.keySet()) {
            if (equalsHeader(key, name)) {
                existing = key;
                break;
            }
        }
        headers.put(existing == null ? name : existing, value.trim());
    }

    private List<String> headerNames(Map<String, String> headers) {
        if (headers.isEmpty()) return List.of();
        List<String> names = new ArrayList<>();
        for (String key : headers.keySet()) names.add(key);
        return names;
    }

    private String escapeListValue(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace(",", "\\,");
    }

    @Nullable
    private String findHeader(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (equalsHeader(entry.getKey(), name)) return entry.getValue();
        }
        return null;
    }

    private boolean equalsHeader(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }

    @Nullable
    private String originOf(String uri) {
        if (TextUtils.isEmpty(uri)) return null;
        try {
            return originOf(Uri.parse(uri));
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private String originOf(Uri uri) {
        if (uri == null || TextUtils.isEmpty(uri.getScheme()) || TextUtils.isEmpty(uri.getHost())) return null;
        String scheme = uri.getScheme();
        int port = uri.getPort();
        if (port > 0 && port != 80 && port != 443) return scheme + "://" + uri.getHost() + ":" + port;
        return scheme + "://" + uri.getHost();
    }

    private String playableUri(Uri uri) throws IOException {
        String value = uri.toString();
        if (isConcatenatingUri(value)) return edlUri(value);
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            ParcelFileDescriptor fd = context.getContentResolver().openFileDescriptor(uri, "r");
            if (fd == null) throw new IOException("Unable to open content uri: " + uri);
            contentFds.add(fd);
            return "fd://" + fd.getFd();
        }
        return value;
    }

    private String playableUri(MediaItem item) throws IOException {
        Uri requestUri = item.requestMetadata.mediaUri;
        if (requestUri != null && isConcatenatingUri(requestUri.toString())) return edlUri(requestUri.toString());
        return playableUri(item.localConfiguration.uri);
    }

    private boolean isConcatenatingUri(String uri) {
        return uri != null && uri.contains(CONCAT_SOURCE_SEPARATOR) && uri.contains(CONCAT_DURATION_SEPARATOR);
    }

    private String edlUri(String uri) throws IOException {
        StringBuilder builder = new StringBuilder("edl://");
        int count = 0;
        for (String split : uri.split(CONCAT_SOURCE_SEPARATOR_REGEX)) {
            String[] info = split.split(CONCAT_DURATION_SEPARATOR_REGEX, 2);
            if (info.length < 2 || TextUtils.isEmpty(info[0])) continue;
            if (count++ > 0) builder.append(';');
            builder.append("file=").append(edlValue(info[0]));
            long durationUs = parseLong(info[1], C.TIME_UNSET);
            if (durationUs > 0) builder.append(",length=").append(String.format(Locale.US, "%.3f", durationUs / MICROSECONDS_TO_SECONDS));
        }
        if (count == 0) throw new IOException("Invalid concatenating media uri");
        SpiderDebug.log("mpv", "concat uri converted to EDL segments=%d", count);
        return builder.toString();
    }

    private String edlValue(String value) {
        return "%" + value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + "%" + value;
    }

    private long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private void addSubtitleConfigurations() {
        if (mediaItem == null || mediaItem.localConfiguration == null || mediaItem.localConfiguration.subtitleConfigurations.isEmpty()) return;
        for (MediaItem.SubtitleConfiguration sub : mediaItem.localConfiguration.subtitleConfigurations) {
            Uri uri = sub.uri;
            try {
                MPVLib.command(new String[]{"sub-add", playableUri(uri), "auto"});
            } catch (Throwable ignored) {
            }
        }
    }

    private void setVideoOutput(Object output) {
        detachSurfaceHolder();
        surfaceWidth = 0;
        surfaceHeight = 0;
        Log.d(SIZE_TAG, "mpv setVideoOutput output=" + surfaceOutputName(output));
        if (output instanceof SurfaceView view) {
            updateSurfaceSize(view);
            setSurfaceHolder(view.getHolder());
        } else if (output instanceof TextureView view && view.getSurfaceTexture() != null) {
            updateSurfaceSize(view);
            releaseOwnedSurface();
            surface = new Surface(view.getSurfaceTexture());
            ownsSurface = true;
        } else if (output instanceof SurfaceHolder holder) {
            setSurfaceHolder(holder);
        } else if (output instanceof Surface s) {
            releaseOwnedSurface();
            surface = s;
            ownsSurface = false;
        }
        bindVideoOutput();
    }

    private void setSurfaceHolder(SurfaceHolder holder) {
        surfaceHolder = holder;
        updateSurfaceSize(holder);
        Log.d(SIZE_TAG, "mpv setSurfaceHolder frame=" + surfaceFrame(holder) + " cached=" + surfaceWidth + "x" + surfaceHeight);
        surfaceHolder.addCallback(surfaceCallback);
        surface = surfaceHolder.getSurface();
        ownsSurface = false;
    }

    private void bindVideoOutput() {
        if (!initialized || surface == null || !surface.isValid()) return;
        try {
            if (surfaceAttached && attachedSurface == surface) {
                setRuntimeString("force-window", "yes");
                applyAndroidSurfaceSize();
                applySurfaceFrameRate();
                if (!TextUtils.equals(attachedVo, config.vo())) {
                    safeSetPropertyString("vo", config.vo());
                    attachedVo = config.vo();
                }
                Log.d(SIZE_TAG, "mpv resize attached surface cached=" + surfaceWidth + "x" + surfaceHeight + " vo=" + config.vo());
                SpiderDebug.log("mpv", "surface resized surface=%s size=%dx%d vo=%s", surface, surfaceWidth, surfaceHeight, config.vo());
                return;
            }
            if (surfaceAttached) detachMpvSurface();
            MPVLib.attachSurface(surface);
            surfaceAttached = true;
            attachedSurface = surface;
            setRuntimeString("force-window", "yes");
            applyAndroidSurfaceSize();
            applySurfaceFrameRate();
            safeSetPropertyString("vo", config.vo());
            attachedVo = config.vo();
            Log.d(SIZE_TAG, "mpv bind surface valid=" + surface.isValid() + " cached=" + surfaceWidth + "x" + surfaceHeight + " vo=" + config.vo());
            SpiderDebug.log("mpv", "surface attached surface=%s size=%dx%d vo=%s", surface, surfaceWidth, surfaceHeight, config.vo());
        } catch (Throwable e) {
            fail(mpvError(ERROR_VIDEO_OUTPUT_FAILED, e.getMessage(), e), PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED);
        }
    }

    private void clearVideoOutput() {
        clearSurfaceFrameRate();
        detachSurfaceHolder();
        detachMpvSurface();
        releaseOwnedSurface();
        surface = null;
        surfaceWidth = 0;
        surfaceHeight = 0;
    }

    private void detachMpvSurface() {
        if (!initialized || !surfaceAttached) return;
        try {
            safeSetPropertyString("vo", "null");
            setRuntimeString("force-window", "no");
            MPVLib.detachSurface();
        } catch (Throwable ignored) {
        }
        surfaceAttached = false;
        attachedSurface = null;
        attachedVo = null;
    }

    private void detachSurfaceHolder() {
        if (surfaceHolder == null) return;
        try {
            surfaceHolder.removeCallback(surfaceCallback);
        } catch (Throwable ignored) {
        }
        surfaceHolder = null;
    }

    private void updateSurfaceSize(View view) {
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) return;
        surfaceWidth = view.getWidth();
        surfaceHeight = view.getHeight();
        Log.d(SIZE_TAG, "mpv updateSurfaceSize view=" + surfaceOutputName(view) + " size=" + surfaceWidth + "x" + surfaceHeight);
    }

    private void updateSurfaceSize(SurfaceHolder holder) {
        if (holder == null) return;
        Rect frame = holder.getSurfaceFrame();
        if (frame == null || frame.width() <= 0 || frame.height() <= 0) return;
        surfaceWidth = frame.width();
        surfaceHeight = frame.height();
        Log.d(SIZE_TAG, "mpv updateSurfaceSize holder frame=" + frame.width() + "x" + frame.height());
    }

    private void updateSurfaceSize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        surfaceWidth = width;
        surfaceHeight = height;
        Log.d(SIZE_TAG, "mpv updateSurfaceSize changed=" + surfaceWidth + "x" + surfaceHeight);
    }

    private void applyAndroidSurfaceSize() {
        if (surfaceWidth > 0 && surfaceHeight > 0) {
            safeSetPropertyString("android-surface-size", surfaceWidth + "x" + surfaceHeight);
            Log.d(SIZE_TAG, "mpv android-surface-size=" + surfaceWidth + "x" + surfaceHeight);
        } else {
            safeSetPropertyString("android-surface-size", "0x0");
            Log.d(SIZE_TAG, "mpv android-surface-size=0x0");
        }
    }

    private void applySurfaceFrameRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || surface == null || !surface.isValid()) return;
        float rate = MpvPerformanceSetting.getFrameRateMode() == MpvPerformanceSetting.FRAME_RATE_SEAMLESS ? cachedContentFrameRate : 0f;
        if (rate < 0) rate = 0f;
        try {
            surface.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE, Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);
            SpiderDebug.log("mpv", "surface frame rate request=%.3f mode=%s", rate, MpvPerformanceSetting.getFrameRateText());
        } catch (Throwable e) {
            SpiderDebug.log("mpv", "surface frame rate request failed rate=%.3f error=%s", rate, e.getMessage());
        }
    }

    private void clearSurfaceFrameRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || surface == null || !surface.isValid()) return;
        try {
            surface.setFrameRate(0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT, Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);
        } catch (Throwable ignored) {
        }
    }

    private String surfaceOutputName(Object output) {
        if (output == null) return "null";
        return output.getClass().getSimpleName();
    }

    private String surfaceFrame(SurfaceHolder holder) {
        if (holder == null || holder.getSurfaceFrame() == null) return "null";
        Rect frame = holder.getSurfaceFrame();
        return frame.width() + "x" + frame.height();
    }

    private void releaseOwnedSurface() {
        if (ownsSurface && surface != null) surface.release();
        ownsSurface = false;
    }

    private final SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            surface = holder.getSurface();
            updateSurfaceSize(holder);
            Log.d(SIZE_TAG, "mpv surfaceCreated frame=" + surfaceFrame(holder) + " valid=" + (surface != null && surface.isValid()));
            bindVideoOutput();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            surface = holder.getSurface();
            updateSurfaceSize(width, height);
            Log.d(SIZE_TAG, "mpv surfaceChanged format=" + format + " size=" + width + "x" + height + " frame=" + surfaceFrame(holder));
            bindVideoOutput();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.d(SIZE_TAG, "mpv surfaceDestroyed frame=" + surfaceFrame(holder));
            surface = null;
            detachMpvSurface();
        }
    };

    private void stopInternal(boolean resetState) {
        stopMpv(true);
        closeContentFds();
        loading = false;
        fileLoaded = false;
        loadStarted = false;
        playbackRestarted = false;
        loadStartRetryCount = 0;
        eofReached = false;
        preferAacApplied = false;
        audioTrackManuallySelected = false;
        cachedPositionMs = 0;
        cachedDurationMs = C.TIME_UNSET;
        resetCacheState();
        currentTracks = Tracks.EMPTY;
        currentChapters = List.of();
        videoSize = VideoSize.UNKNOWN;
        playerError = null;
        pendingSeekPositionMs = C.TIME_UNSET;
        idleActive = false;
        currentPlayableUri = null;
        closeIsoSession();
        currentLikelyHls = false;
        currentLikelyDash = false;
        currentChapter = C.INDEX_UNSET;
        resetFailureSignals();
        hlsProxy.clear();
        mainHandler.removeCallbacks(endFileValidationRunnable);
        mainHandler.removeCallbacks(loadStartRetryRunnable);
        if (resetState) playbackState = Player.STATE_IDLE;
        stopStateRefresh();
        invalidateState();
    }

    private void stopMpv(boolean markStopping) {
        if (!initialized) return;
        boolean previousStopping = stopping;
        if (markStopping) stopping = true;
        try {
            MPVLib.command(new String[]{"stop"});
        } catch (Throwable ignored) {
            stopping = previousStopping;
        }
    }

    private void resetMpvContextForNewMedia() {
        mainHandler.removeCallbacks(stateRefreshRunnable);
        mainHandler.removeCallbacks(endFileValidationRunnable);
        mainHandler.removeCallbacks(loadStartRetryRunnable);
        releaseNativeContext("new media");
    }

    private void releaseNativeContext(String reason) {
        synchronized (NATIVE_CONTEXT_LOCK) {
            releaseNativeContextLocked(reason);
        }
    }

    private void releaseNativeContextLocked(String reason) {
        if (!initialized) return;
        boolean ownsNativeContext = nativeContextOwner == this;
        try {
            if (ownsNativeContext && surfaceAttached) MPVLib.detachSurface();
        } catch (Throwable ignored) {
        }
        try {
            MPVLib.removeObserver(this);
            MPVLib.removeLogObserver(this);
            if (ownsNativeContext) MPVLib.destroyCreatedContext();
        } catch (Throwable ignored) {
        } finally {
            if (ownsNativeContext) nativeContextOwner = null;
            initialized = false;
            surfaceAttached = false;
            attachedSurface = null;
            attachedVo = null;
            stopping = false;
            loadStarted = false;
            loadStartRetryCount = 0;
        }
        SpiderDebug.log("mpv", "context released reason=%s owner=%s player=%s", reason, ownsNativeContext, identity(this));
    }

    private static String identity(MpvPlayer player) {
        return Integer.toHexString(System.identityHashCode(player));
    }

    private void seekMpv(long positionMs) {
        try {
            MPVLib.command(new String[]{"seek", String.format(Locale.US, "%.3f", positionMs / SECONDS_TO_MS), "absolute+exact"});
        } catch (Throwable e) {
            fail(e, PlaybackException.ERROR_CODE_UNSPECIFIED);
        }
    }

    private void loadCurrentUri() {
        if (currentLikelyHls) {
            MPVLib.command(new String[]{"loadfile", currentPlayableUri, "replace", "-1", HLS_LOAD_OPTIONS});
        } else if (currentLikelyDash) {
            MPVLib.command(new String[]{"loadfile", currentPlayableUri, "replace", "-1", DASH_LOAD_OPTIONS});
        } else {
            MPVLib.command(new String[]{"loadfile", currentPlayableUri, "replace"});
        }
    }

    private void scheduleLoadStartRetry() {
        mainHandler.removeCallbacks(loadStartRetryRunnable);
        mainHandler.postDelayed(loadStartRetryRunnable, LOAD_START_RETRY_DELAY_MS);
    }

    private void retryLoadIfNotStarted() {
        if (released || loadStarted || fileLoaded || playerError != null) return;
        if (playbackState != Player.STATE_BUFFERING || TextUtils.isEmpty(currentPlayableUri)) return;
        if (loadStartRetryCount >= MAX_LOAD_START_RETRIES) return;
        loadStartRetryCount++;
        SpiderDebug.log("mpv", "load retry attempt=%d uri=%s idle=%s", loadStartRetryCount, currentPlayableUri, booleanProperty("idle-active", idleActive));
        try {
            loadCurrentUri();
            scheduleLoadStartRetry();
        } catch (Throwable e) {
            fail(classifyLoadError(e, e.getMessage()), PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
        }
    }

    private void updateVideoSize(String reason) {
        SizeCandidate candidate = videoSizeCandidate();
        logVideoSizeCandidates(reason, candidate);
        if (candidate == null || candidate.width <= 0 || candidate.height <= 0) return;
        if (videoSize.width == candidate.width && videoSize.height == candidate.height) return;
        videoSize = new VideoSize(candidate.width, candidate.height);
        Log.d(SIZE_TAG, "mpv videoSize=" + candidate.width + "x" + candidate.height + " source=" + candidate.source + " reason=" + reason + " surface=" + surfaceWidth + "x" + surfaceHeight);
    }

    @Nullable
    private SizeCandidate videoSizeCandidate() {
        SizeCandidate candidate = candidateFromProperties("current-tracks/video/demux-w", "current-tracks/video/demux-h", "current-track");
        if (candidate != null) return candidate;
        candidate = candidateFromSelectedVideoTrack();
        if (candidate != null) return candidate;
        candidate = candidateFromProperties("video-params/dw", "video-params/dh", "video-params-display");
        if (candidate != null) return candidate;
        candidate = candidateFromProperties("video-params/w", "video-params/h", "video-params");
        if (candidate != null) return candidate;
        candidate = candidateFromProperties("width", "height", "width-height");
        if (candidate != null) return candidate;
        if (!canUseVideoOutSizeFallback()) return null;
        candidate = candidateFromProperties("video-out-params/dw", "video-out-params/dh", "video-out-display");
        if (candidate != null) return candidate;
        return candidateFromProperties("video-out-params/w", "video-out-params/h", "video-out");
    }

    private boolean canUseVideoOutSizeFallback() {
        return videoSize.width <= 0 && videoSize.height <= 0 && (fileLoaded || playbackRestarted);
    }

    @Nullable
    private SizeCandidate candidateFromProperties(String widthProperty, String heightProperty, String source) {
        int width = intProperty(widthProperty, 0);
        int height = intProperty(heightProperty, 0);
        return width > 0 && height > 0 ? new SizeCandidate(width, height, source) : null;
    }

    @Nullable
    private SizeCandidate candidateFromSelectedVideoTrack() {
        int count = Math.max(0, intProperty("track-list/count", 0));
        SizeCandidate firstVideo = null;
        for (int i = 0; i < count; i++) {
            String prefix = "track-list/" + i + "/";
            if (!"video".equals(stringProperty(prefix + "type", ""))) continue;
            if (booleanProperty(prefix + "albumart", false)) continue;
            int width = intProperty(prefix + "demux-w", 0);
            int height = intProperty(prefix + "demux-h", 0);
            if (width <= 0 || height <= 0) continue;
            SizeCandidate candidate = new SizeCandidate(width, height, "track-list/" + i);
            if (firstVideo == null) firstVideo = candidate;
            if (booleanProperty(prefix + "selected", false)) return candidate;
        }
        return firstVideo;
    }

    private void logVideoSizeCandidates(String reason, @Nullable SizeCandidate candidate) {
        String text = "mpv size candidates reason=" + reason
                + " selected=" + candidateText(candidate)
                + " stable=" + size("current", "current-tracks/video/demux-w", "current-tracks/video/demux-h")
                + " track=" + selectedTrackSizeText()
                + " paramsDisplay=" + size("vp-d", "video-params/dw", "video-params/dh")
                + " params=" + size("vp", "video-params/w", "video-params/h")
                + " legacy=" + size("wh", "width", "height")
                + " outDisplay=" + size("vo-d", "video-out-params/dw", "video-out-params/dh")
                + " out=" + size("vo", "video-out-params/w", "video-out-params/h")
                + " fileLoaded=" + fileLoaded
                + " restarted=" + playbackRestarted
                + " reconfig=" + videoReconfigCount
                + " surface=" + surfaceWidth + "x" + surfaceHeight;
        if (text.equals(lastVideoSizeCandidateLog)) return;
        lastVideoSizeCandidateLog = text;
        Log.d(SIZE_TAG, text);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("mpv", "%s", text);
    }

    private String candidateText(@Nullable SizeCandidate candidate) {
        return candidate == null ? "none" : candidate.source + ":" + candidate.width + "x" + candidate.height;
    }

    private String size(String label, String widthProperty, String heightProperty) {
        return label + ":" + intProperty(widthProperty, 0) + "x" + intProperty(heightProperty, 0);
    }

    private String selectedTrackSizeText() {
        int count = Math.max(0, intProperty("track-list/count", 0));
        String first = "none";
        for (int i = 0; i < count; i++) {
            String prefix = "track-list/" + i + "/";
            if (!"video".equals(stringProperty(prefix + "type", ""))) continue;
            if (booleanProperty(prefix + "albumart", false)) continue;
            String text = "track" + i + ":" + intProperty(prefix + "demux-w", 0) + "x" + intProperty(prefix + "demux-h", 0) + ":sel=" + booleanProperty(prefix + "selected", false);
            if ("none".equals(first)) first = text;
            if (booleanProperty(prefix + "selected", false)) return text;
        }
        return first;
    }

    private record SizeCandidate(int width, int height, String source) {
    }

    private void startStateRefresh() {
        mainHandler.removeCallbacks(stateRefreshRunnable);
        mainHandler.postDelayed(stateRefreshRunnable, STATE_REFRESH_INTERVAL_MS);
    }

    private void stopStateRefresh() {
        mainHandler.removeCallbacks(stateRefreshRunnable);
    }

    private void refreshPlaybackState() {
        if (released || mediaItem == null || playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED || playerError != null) return;
        cachedPositionMs = positionMs();
        cachedDurationMs = durationMs();
        if (fileLoaded && playbackRestarted && !stopping) applyPausedForCache(booleanProperty("paused-for-cache", loading));
        refreshCacheState();
        invalidateState();
        startStateRefresh();
    }

    private void applyPausedForCache(boolean pausedForCache) {
        loading = pausedForCache;
        playbackState = MpvPlaybackState.resolveAfterCachePoll(playbackState, fileLoaded, playbackRestarted, stopping, pausedForCache);
    }

    private void refreshCacheState() {
        if (!initialized) return;
        cachedCacheDurationMs = Math.max(0, doublePropertyMs("demuxer-cache-state/cache-duration", doublePropertyMs("demuxer-cache-duration", cachedCacheDurationMs)));
        cachedCacheEndMs = Math.max(0, doublePropertyMs("demuxer-cache-state/cache-end", doublePropertyMs("demuxer-cache-time", cachedCacheEndMs)));
        cachedCacheReaderPositionMs = Math.max(0, doublePropertyMs("demuxer-cache-state/reader-pts", cachedCacheReaderPositionMs));
        cachedCacheSpeedBytesPerSecond = Math.max(0, longProperty("demuxer-cache-state/raw-input-rate", longProperty("cache-speed", cachedCacheSpeedBytesPerSecond)));
        cachedCacheForwardBytes = Math.max(0, longProperty("demuxer-cache-state/fw-bytes", cachedCacheForwardBytes));
        cachedCacheTotalBytes = Math.max(0, longProperty("demuxer-cache-state/total-bytes", cachedCacheTotalBytes));
        cachedCacheFileBytes = Math.max(0, longProperty("demuxer-cache-state/file-cache-bytes", cachedCacheFileBytes));
        cachedCacheBufferingState = Math.max(0, Math.min(100, (int) longProperty("cache-buffering-state", cachedCacheBufferingState)));
        cachedCacheIdle = booleanProperty("demuxer-cache-state/idle", booleanProperty("demuxer-cache-idle", cachedCacheIdle));
        cachedCacheUnderrun = booleanProperty("demuxer-cache-state/underrun", cachedCacheUnderrun);
        cachedCacheBof = booleanProperty("demuxer-cache-state/bof-cached", cachedCacheBof);
        cachedCacheEof = booleanProperty("demuxer-cache-state/eof-cached", cachedCacheEof);
    }

    private void validateEarlyEndFile() {
        if (released || stopping || fileLoaded || eofReached || playerError != null || playbackState != Player.STATE_BUFFERING) return;
        if (booleanProperty("idle-active", idleActive)) {
            fail(classifyLoadError(null, "idle-active=true"), PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
        } else {
            startStateRefresh();
        }
    }

    private boolean isFailedLoadedMedia() {
        if (!fileLoaded) return false;
        if (sawNoAvData || sawInvalidData || sawPngVideo || sawNetworkError || sawDecodeError || sawVideoOutputError || sawDrmError) return true;
        if (recentLogsContain("no audio or video data played", "invalid data found when processing input", "video: png", "could not open codec", "failed to initialize decoder", "video output failed")) return true;
        return playbackRestarted && videoSize.width <= 0 && videoSize.height <= 0 && positionMs() <= 0 && durationMs() == C.TIME_UNSET;
    }

    private String failedLoadedMediaMessage() {
        if (currentLikelyHls && (sawNoAvData
                || sawInvalidData
                || sawPngVideo
                || recentLogsContain("no audio or video data played", "invalid data found when processing input", "video: png")
                || playbackRestarted && videoSize.width <= 0 && videoSize.height <= 0 && positionMs() <= 0)) {
            return ERROR_HLS_PLAYBACK_FAILED + detailSuffix("hls input failed");
        }
        if (sawDrmError) return ERROR_DRM_UNSUPPORTED + detailSuffix("drm/encrypted media");
        if (sawVideoOutputError) return ERROR_VIDEO_OUTPUT_FAILED + detailSuffix("video output failed");
        if (sawNetworkError) return ERROR_NETWORK_FAILED + detailSuffix("network/io failure");
        if (sawNoAvData || recentLogsContain("no audio or video data played")) return ERROR_NO_AV_DATA + detailSuffix("no audio or video data played");
        if (sawInvalidData || sawPngVideo || recentLogsContain("invalid data found when processing input", "video: png")) return ERROR_INVALID_MEDIA_DATA + detailSuffix("invalid media data");
        return ERROR_DECODE_FAILED + detailSuffix("no playable audio/video output");
    }

    private IOException classifyLoadError(@Nullable Throwable cause, @Nullable String detail) {
        String code;
        if (sawDrmError) code = ERROR_DRM_UNSUPPORTED;
        else if (sawVideoOutputError) code = ERROR_VIDEO_OUTPUT_FAILED;
        else if (sawNetworkError) code = ERROR_NETWORK_FAILED;
        else if (sawInvalidData || sawPngVideo) code = ERROR_INVALID_MEDIA_DATA;
        else if (sawNoAvData) code = ERROR_NO_AV_DATA;
        else code = ERROR_LOAD_FAILED;
        return cause == null ? mpvError(code, detail) : mpvError(code, detail, cause);
    }

    private IOException nativeEndFileError(int reason, int error, @Nullable String errorText) {
        return mpvError(nativeEndFileErrorCode(error), nativeEndFileDetail(reason, error, errorText));
    }

    private String nativeEndFileErrorCode(int error) {
        if (sawDrmError) return ERROR_DRM_UNSUPPORTED;
        if (sawVideoOutputError || error == MPVLib.MpvError.MPV_ERROR_VO_INIT_FAILED) return ERROR_VIDEO_OUTPUT_FAILED;
        if (sawNetworkError) return ERROR_NETWORK_FAILED;
        if (currentLikelyHls && (sawNoAvData
                || sawInvalidData
                || sawPngVideo
                || error == MPVLib.MpvError.MPV_ERROR_NOTHING_TO_PLAY
                || error == MPVLib.MpvError.MPV_ERROR_UNKNOWN_FORMAT
                || error == MPVLib.MpvError.MPV_ERROR_UNSUPPORTED)) {
            return ERROR_HLS_PLAYBACK_FAILED;
        }
        if (sawNoAvData || error == MPVLib.MpvError.MPV_ERROR_NOTHING_TO_PLAY) return ERROR_NO_AV_DATA;
        if (sawInvalidData
                || sawPngVideo
                || error == MPVLib.MpvError.MPV_ERROR_UNKNOWN_FORMAT
                || error == MPVLib.MpvError.MPV_ERROR_UNSUPPORTED) {
            return ERROR_INVALID_MEDIA_DATA;
        }
        if (sawDecodeError) return ERROR_DECODE_FAILED;
        return error == MPVLib.MpvError.MPV_ERROR_LOADING_FAILED ? ERROR_LOAD_FAILED : ERROR_DECODE_FAILED;
    }

    private int nativeEndFilePlaybackExceptionCode(int error) {
        if (error == MPVLib.MpvError.MPV_ERROR_LOADING_FAILED) return PlaybackException.ERROR_CODE_IO_UNSPECIFIED;
        if (error == MPVLib.MpvError.MPV_ERROR_VO_INIT_FAILED) return PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED;
        return PlaybackException.ERROR_CODE_DECODING_FAILED;
    }

    private String nativeEndFileDetail(int reason, int error, @Nullable String errorText) {
        StringBuilder builder = new StringBuilder();
        builder.append("native end-file reason=").append(endFileReasonName(reason)).append('(').append(reason).append(')');
        builder.append(" error=").append(mpvErrorName(error)).append('(').append(error).append(')');
        if (!TextUtils.isEmpty(errorText)) builder.append(' ').append(errorText);
        return builder.toString();
    }

    private String endFileReasonName(int reason) {
        return switch (reason) {
            case MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_EOF -> "eof";
            case MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_STOP -> "stop";
            case MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_QUIT -> "quit";
            case MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_ERROR -> "error";
            case MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_REDIRECT -> "redirect";
            case MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_UNKNOWN -> "unknown";
            default -> "unknown";
        };
    }

    private String mpvErrorName(int error) {
        return switch (error) {
            case MPVLib.MpvError.MPV_ERROR_SUCCESS -> "success";
            case MPVLib.MpvError.MPV_ERROR_LOADING_FAILED -> "loading_failed";
            case MPVLib.MpvError.MPV_ERROR_AO_INIT_FAILED -> "ao_init_failed";
            case MPVLib.MpvError.MPV_ERROR_VO_INIT_FAILED -> "vo_init_failed";
            case MPVLib.MpvError.MPV_ERROR_NOTHING_TO_PLAY -> "nothing_to_play";
            case MPVLib.MpvError.MPV_ERROR_UNKNOWN_FORMAT -> "unknown_format";
            case MPVLib.MpvError.MPV_ERROR_UNSUPPORTED -> "unsupported";
            case MPVLib.MpvError.MPV_ERROR_GENERIC -> "generic";
            default -> "unknown";
        };
    }

    private IOException mpvError(String code, @Nullable String detail) {
        return new IOException(code + detailSuffix(detail));
    }

    private IOException mpvError(String code, @Nullable String detail, Throwable cause) {
        return new IOException(code + detailSuffix(detail), cause);
    }

    private String detailSuffix(@Nullable String detail) {
        StringBuilder builder = new StringBuilder();
        if (!TextUtils.isEmpty(detail)) builder.append(": ").append(detail);
        String recent = recentLogSuffix();
        if (!TextUtils.isEmpty(recent)) builder.append(recent);
        return builder.toString();
    }

    private void rememberLog(String line) {
        if (recentLogs.size() >= RECENT_LOG_LIMIT) recentLogs.remove(0);
        recentLogs.add(line);
    }

    private void markFailureSignal(String line) {
        String lower = line == null ? "" : line.toLowerCase(Locale.US);
        if (lower.contains("no audio or video data played")) sawNoAvData = true;
        if (lower.contains("invalid data found when processing input")) sawInvalidData = true;
        if (lower.contains("video: png")) sawPngVideo = true;
        if (isNetworkFailureLog(lower)) sawNetworkError = true;
        if (isDecodeFailureLog(lower)) sawDecodeError = true;
        if (isVideoOutputFailureLog(lower)) sawVideoOutputError = true;
        if (isDrmFailureLog(lower)) sawDrmError = true;
        if (sawNoAvData || sawInvalidData || sawPngVideo || sawNetworkError || sawDecodeError || sawVideoOutputError || sawDrmError || lower.contains("failed") || lower.contains("error")) lastFailureLog = line;
    }

    private void resetCacheState() {
        cachedCacheDurationMs = 0;
        cachedCacheEndMs = 0;
        cachedCacheReaderPositionMs = 0;
        cachedCacheForwardBytes = 0;
        cachedCacheTotalBytes = 0;
        cachedCacheFileBytes = 0;
        cachedCacheSpeedBytesPerSecond = 0;
        cachedCacheBufferingState = 0;
        cachedCacheIdle = false;
        cachedCacheUnderrun = false;
        cachedCacheBof = false;
        cachedCacheEof = false;
    }

    private boolean isNetworkFailureLog(String lower) {
        return lower.contains("http error")
                || lower.contains("server returned")
                || lower.contains("connection timed out")
                || lower.contains("connection refused")
                || lower.contains("connection reset")
                || lower.contains("network is unreachable")
                || lower.contains("name resolution")
                || lower.contains("cannot resolve")
                || lower.contains("timed out")
                || ((lower.contains("tls") || lower.contains("ssl")) && (lower.contains("failed") || lower.contains("error") || lower.contains("certificate")))
                || lower.contains("error reading")
                || lower.contains("failed to open http")
                || lower.contains("failed to open https");
    }

    private boolean isDecodeFailureLog(String lower) {
        return lower.contains("could not open codec")
                || lower.contains("failed to initialize decoder")
                || lower.contains("failed to init decoder")
                || lower.contains("decoder init failed")
                || lower.contains("error while decoding")
                || lower.contains("error decoding")
                || lower.contains("decoding failed")
                || lower.contains("no decoders available")
                || lower.contains("hardware decoding failed");
    }

    private boolean isVideoOutputFailureLog(String lower) {
        return lower.contains("video output failed")
                || lower.contains("failed to create android surface")
                || lower.contains("could not create egl")
                || (lower.contains("egl") && lower.contains("failed"))
                || (lower.contains("vo/") && lower.contains("failed"));
    }

    private boolean isDrmFailureLog(String lower) {
        return (lower.contains("widevine") && (lower.contains("unsupported") || lower.contains("not supported") || lower.contains("failed") || lower.contains("error")))
                || (lower.contains("encrypted") && (lower.contains("unsupported") || lower.contains("not supported") || lower.contains("failed")))
                || (lower.contains("drm") && (lower.contains("unsupported") || lower.contains("not supported") || lower.contains("failed") || lower.contains("error")));
    }

    private boolean shouldDebugLogMpvLine(String line) {
        String lower = line == null ? "" : line.toLowerCase(Locale.US);
        return lower.contains("error")
                || lower.contains("failed")
                || lower.contains("invalid")
                || lower.contains("no audio")
                || lower.contains("video:")
                || lower.contains("audio:")
                || lower.contains("found 'hls'")
                || lower.contains("opening")
                || lower.contains("lavf")
                || lower.contains("demux")
                || lower.contains("codec")
                || lower.contains("track");
    }

    private void resetFailureSignals() {
        sawNoAvData = false;
        sawInvalidData = false;
        sawPngVideo = false;
        sawNetworkError = false;
        sawDecodeError = false;
        sawVideoOutputError = false;
        sawDrmError = false;
        lastFailureLog = null;
        lastEndFileReason = MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_UNKNOWN;
        lastEndFileError = MPVLib.MpvError.MPV_ERROR_SUCCESS;
        lastEndFileErrorText = null;
    }

    private boolean recentLogsContain(String... needles) {
        for (String log : recentLogs) {
            String lower = log == null ? "" : log.toLowerCase(Locale.US);
            for (String needle : needles) {
                if (lower.contains(needle)) return true;
            }
        }
        return false;
    }

    private long positionMs() {
        if (initialized) cachedPositionMs = Math.max(0, doublePropertyMs("time-pos/full", doublePropertyMs("time-pos", cachedPositionMs)));
        return cachedPositionMs;
    }

    private long durationMs() {
        if (initialized) {
            long duration = doublePropertyMs("duration/full", doublePropertyMs("duration", cachedDurationMs));
            cachedDurationMs = duration > 0 ? duration : C.TIME_UNSET;
        }
        return cachedDurationMs > 0 ? cachedDurationMs : C.TIME_UNSET;
    }

    private long bufferedPositionMs(long position, long duration) {
        if (duration == C.TIME_UNSET || duration <= 0) return position;
        if (cachedCacheDurationMs > 0) return Math.min(duration, position + cachedCacheDurationMs);
        if (!TextUtils.isEmpty(currentIsoUri)) return position;
        return playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED ? duration : position;
    }

    private boolean isPlayingInternal() {
        return playbackState == Player.STATE_READY && playWhenReady && !loading;
    }

    private void refreshTracks() {
        if (!initialized) {
            currentTracks = Tracks.EMPTY;
            return;
        }
        int count = Math.max(0, intProperty("track-list/count", 0));
        if (count <= 0) {
            currentTracks = Tracks.EMPTY;
            return;
        }
        if (!isoTrackListDumped && !TextUtils.isEmpty(currentIsoUri)) {
            isoTrackListDumped = true;
            MPVLib.dumpTrackList();
        }
        List<TrackInfo> infos = new ArrayList<>();
        int audioIndex = 0;
        int subtitleIndex = 0;
        for (int i = 0; i < count; i++) {
            String mpvType = stringProperty("track-list/" + i + "/type", "");
            int typeIndex = "audio".equals(mpvType) ? audioIndex++ : "sub".equals(mpvType) ? subtitleIndex++ : C.INDEX_UNSET;
            TrackInfo info = readTrackInfo(i, typeIndex);
            if (info == null) continue;
            infos.add(info);
        }
        if (infos.isEmpty()) {
            currentTracks = Tracks.EMPTY;
            return;
        }
        String selectedVideo = selectedTrackId(C.TRACK_TYPE_VIDEO);
        String selectedAudio = selectedTrackId(C.TRACK_TYPE_AUDIO);
        String selectedText = selectedTrackId(C.TRACK_TYPE_TEXT);
        boolean hasSelectedVideo = hasSelectedTrack(infos, C.TRACK_TYPE_VIDEO, selectedVideo);
        boolean hasSelectedAudio = hasSelectedTrack(infos, C.TRACK_TYPE_AUDIO, selectedAudio);
        boolean hasSelectedText = hasSelectedTrack(infos, C.TRACK_TYPE_TEXT, selectedText);
        boolean autoVideoFallbackUsed = false;
        boolean autoAudioFallbackUsed = false;
        boolean autoTextFallbackUsed = false;
        List<Tracks.Group> groups = new ArrayList<>();
        for (TrackInfo info : infos) {
            boolean selected = isTrackSelected(info, trackIdForType(info.type, selectedVideo, selectedAudio, selectedText));
            if (!selected && info.type == C.TRACK_TYPE_VIDEO && !hasSelectedVideo && isAutoOrUnknownTrackChoice(selectedVideo) && !autoVideoFallbackUsed) {
                selected = true;
                autoVideoFallbackUsed = true;
            } else if (!selected && info.type == C.TRACK_TYPE_AUDIO && !hasSelectedAudio && isAutoOrUnknownTrackChoice(selectedAudio) && !autoAudioFallbackUsed) {
                selected = true;
                autoAudioFallbackUsed = true;
            } else if (!selected && info.type == C.TRACK_TYPE_TEXT && !hasSelectedText && isAutoTrackChoice(selectedText) && !autoTextFallbackUsed) {
                selected = true;
                autoTextFallbackUsed = true;
            }
            Format format = info.toFormat();
            TrackGroup mediaGroup = new TrackGroup("mpv:" + info.type + ":" + info.id, format);
            groups.add(new Tracks.Group(mediaGroup, false, new int[]{C.FORMAT_HANDLED}, new boolean[]{selected}));
        }
        currentTracks = groups.isEmpty() ? Tracks.EMPTY : new Tracks(groups);
        maybeSelectPreferredAac(infos, selectedAudio);
        logTrackSnapshot(infos, selectedVideo, selectedAudio, selectedText, currentTracks);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("mpv", "tracks refreshed count=%d groups=%d", count, groups.size());
    }

    private void maybeSelectPreferredAac(List<TrackInfo> infos, String selectedAudio) {
        if (!PlayerSetting.isPreferAAC(PlayerSetting.MPV) || preferAacApplied || audioTrackManuallySelected || !initialized) return;
        TrackInfo selected = findTrack(infos, C.TRACK_TYPE_AUDIO, selectedAudio);
        if (selected != null && isAacTrack(selected)) {
            preferAacApplied = true;
            return;
        }
        TrackInfo preferred = findPreferredAacTrack(infos, selected);
        if (preferred == null || TextUtils.equals(preferred.id, selected == null ? "" : selected.id)) return;
        preferAacApplied = true;
        setMpvTrack(C.TRACK_TYPE_AUDIO, preferred.id);
        SpiderDebug.log("mpv", "prefer AAC audio track selected id=%s codec=%s previous=%s", preferred.id, preferred.codec, selected == null ? selectedAudio : selected.id);
    }

    @Nullable
    private TrackInfo findTrack(List<TrackInfo> infos, int type, String selectedId) {
        for (TrackInfo info : infos) if (info.type == type && isTrackSelected(info, selectedId)) return info;
        return null;
    }

    @Nullable
    private TrackInfo findPreferredAacTrack(List<TrackInfo> infos, @Nullable TrackInfo selected) {
        TrackInfo first = null;
        for (TrackInfo info : infos) {
            if (info.type != C.TRACK_TYPE_AUDIO || !isAacTrack(info)) continue;
            if (first == null) first = info;
            if (selected != null && !TextUtils.isEmpty(selected.lang) && TextUtils.equals(selected.lang, info.lang)) return info;
        }
        return first;
    }

    private boolean isAacTrack(TrackInfo info) {
        return info != null && sampleMimeType(info).equals(MimeTypes.AUDIO_AAC);
    }

    private void refreshChapters() {
        if (released || !initialized) return;
        currentChapter = intProperty("chapter", currentChapter);
        List<MediaEdition> chapters = parseChapters(stringProperty("chapter-list", ""));
        if (chapters.isEmpty()) chapters = readChaptersFromProperties();
        updateCurrentChapters(chapters);
    }

    private void handleChapterListProperty(@Nullable Object value) {
        List<MediaEdition> chapters = value instanceof String string ? parseChapters(string) : List.of();
        if (chapters.isEmpty()) chapters = readChaptersFromProperties();
        updateCurrentChapters(chapters);
    }

    private void updateCurrentChapters(List<MediaEdition> chapters) {
        if (chapters == null) chapters = List.of();
        if (chapters.equals(currentChapters)) return;
        currentChapters = chapters;
        SpiderDebug.log("mpv", "chapters refreshed count=%d selected=%d", chapters.size(), currentChapter);
        invalidateState();
    }

    private List<MediaEdition> parseChapters(String json) {
        if (TextUtils.isEmpty(json)) return List.of();
        String trimmed = json.trim();
        if (!trimmed.startsWith("[")) return List.of();
        try {
            JSONArray array = new JSONArray(trimmed);
            List<MediaEdition> items = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                items.add(MediaEdition.edition(i, secondsToUs(item.optDouble("time", 0)), chapterLabel(i, item.optString("title", null)), i == currentChapter));
            }
            return items;
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    private List<MediaEdition> readChaptersFromProperties() {
        int count = Math.max(0, intProperty("chapter-list/count", 0));
        if (count <= 0) return List.of();
        List<MediaEdition> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String prefix = "chapter-list/" + i + "/";
            items.add(MediaEdition.edition(i, secondsToUs(doubleProperty(prefix + "time", 0)), chapterLabel(i, stringProperty(prefix + "title", null)), i == currentChapter));
        }
        return items;
    }

    private String chapterLabel(int index, @Nullable String title) {
        title = emptyToNull(title);
        return title == null ? "Chapter " + (index + 1) : title;
    }

    private long secondsToUs(double seconds) {
        if (seconds <= 0 || Double.isNaN(seconds) || Double.isInfinite(seconds)) return 0;
        return Math.round(seconds * 1_000_000.0);
    }

    @Nullable
    private String emptyToNull(@Nullable String value) {
        if (TextUtils.isEmpty(value)) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void logTrackSnapshot(List<TrackInfo> infos, String selectedVideo, String selectedAudio, String selectedText, Tracks tracks) {
        StringBuilder builder = new StringBuilder();
        builder.append("tracks snapshot ");
        builder.append("vid=").append(selectedVideo).append(" aid=").append(selectedAudio).append(" sid=").append(selectedText);
        builder.append(" rawVid=").append(propertyStringOrInt("vid"));
        builder.append(" rawAid=").append(propertyStringOrInt("aid"));
        builder.append(" rawSid=").append(propertyStringOrInt("sid"));
        builder.append(" secondarySid=").append(secondarySubtitleTrackId());
        builder.append(" currentVideo=").append(propertyStringOrInt("current-tracks/video/id"));
        builder.append(" currentAudio=").append(propertyStringOrInt("current-tracks/audio/id"));
        builder.append(" currentSub=").append(propertyStringOrInt("current-tracks/sub/id"));
        builder.append(" currentSub2=").append(propertyStringOrInt("current-tracks/sub2/id"));
        builder.append(" size=").append(videoSize.width).append("x").append(videoSize.height);
        builder.append(" width=").append(intProperty("width", C.LENGTH_UNSET));
        builder.append(" height=").append(intProperty("height", C.LENGTH_UNSET));
        builder.append(" fps=").append(videoFrameRate());
        builder.append(" color primaries=").append(stringProperty("video-params/primaries", ""));
        builder.append(" gamma=").append(stringProperty("video-params/gamma", ""));
        builder.append(" levels=").append(stringProperty("video-params/colorlevels", ""));
        builder.append(" matrix=").append(stringProperty("video-params/colormatrix", ""));
        for (int i = 0; i < infos.size(); i++) {
            TrackInfo info = infos.get(i);
            builder.append(" | track[").append(i).append("]");
            builder.append(" type=").append(trackTypeName(info.type));
            builder.append(" id=").append(info.id);
            builder.append(" demuxId=").append(info.demuxId);
            builder.append(" srcId=").append(info.srcId);
            builder.append(" rawSelected=").append(info.selected);
            builder.append(" finalSelected=").append(isTrackSelectedInSnapshot(tracks, info));
            builder.append(" title=").append(info.title);
            builder.append(" lang=").append(info.lang);
            builder.append(" codec=").append(info.codec);
            Format format = info.toFormat();
            builder.append(" label=").append(format.label);
            builder.append(" formatLang=").append(format.language);
            builder.append(" size=").append(info.width).append("x").append(info.height);
            builder.append(" fps=").append(info.frameRate);
            builder.append(" sr=").append(info.sampleRate);
            builder.append(" ch=").append(info.channels);
            builder.append(" br=").append(info.bitrate);
            builder.append(" color=").append(info.colorInfo == null ? "" : info.colorInfo.toLogString());
        }
        String text = builder.toString();
        Log.d(TAG, text);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("mpv", "%s", text);
    }

    private boolean isTrackSelectedInSnapshot(Tracks tracks, TrackInfo info) {
        if (tracks == null || tracks.isEmpty()) return false;
        String groupId = "mpv:" + info.type + ":" + info.id;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.length <= 0 || !group.getMediaTrackGroup().id.equals(groupId)) continue;
            return group.isTrackSelected(0);
        }
        return false;
    }

    private String trackTypeName(int type) {
        return switch (type) {
            case C.TRACK_TYPE_VIDEO -> "video";
            case C.TRACK_TYPE_AUDIO -> "audio";
            case C.TRACK_TYPE_TEXT -> "text";
            default -> String.valueOf(type);
        };
    }

    private boolean hasSelectedTrack(List<TrackInfo> infos, int type, String selectedId) {
        for (TrackInfo info : infos) {
            if (info.type == type && isTrackSelected(info, selectedId)) return true;
        }
        return false;
    }

    private boolean isTrackSelected(TrackInfo info, String selectedId) {
        if (info.selected) return true;
        if (TextUtils.isEmpty(selectedId) || isAutoTrackChoice(selectedId) || isDisabledTrackChoice(selectedId)) return false;
        return selectedId.equals(info.id) || normalizeTrackId(selectedId).equals(normalizeTrackId(info.id));
    }

    private String selectedTrackId(int type) {
        String currentTrackId = currentTrackId(type);
        if (!TextUtils.isEmpty(currentTrackId)) return currentTrackId;
        String property = mpvTrackProperty(type);
        return property == null ? "" : propertyStringOrInt(property);
    }

    private String currentTrackId(int type) {
        String property = switch (type) {
            case C.TRACK_TYPE_VIDEO -> "current-tracks/video/id";
            case C.TRACK_TYPE_AUDIO -> "current-tracks/audio/id";
            case C.TRACK_TYPE_TEXT -> "current-tracks/sub/id";
            default -> null;
        };
        return property == null ? "" : propertyStringOrInt(property);
    }

    private String secondarySubtitleTrackId() {
        String current = propertyStringOrInt("current-tracks/sub2/id");
        if (!TextUtils.isEmpty(current)) return current;
        return propertyStringOrInt("secondary-sid");
    }

    private String propertyStringOrInt(String property) {
        String value = stringProperty(property, "");
        if (!TextUtils.isEmpty(value)) return value;
        int intValue = intProperty(property, Integer.MIN_VALUE);
        return intValue == Integer.MIN_VALUE ? "" : String.valueOf(intValue);
    }

    private String trackIdForType(int type, String selectedVideo, String selectedAudio, String selectedText) {
        return switch (type) {
            case C.TRACK_TYPE_VIDEO -> selectedVideo;
            case C.TRACK_TYPE_AUDIO -> selectedAudio;
            case C.TRACK_TYPE_TEXT -> selectedText;
            default -> "";
        };
    }

    private boolean isAutoTrackChoice(String value) {
        return "auto".equalsIgnoreCase(value);
    }

    private boolean isAutoOrUnknownTrackChoice(String value) {
        return TextUtils.isEmpty(value) || isAutoTrackChoice(value);
    }

    private boolean isDisabledTrackChoice(String value) {
        return "no".equalsIgnoreCase(value);
    }

    private String normalizeTrackId(String value) {
        if (value == null) return "";
        String normalized = value.trim();
        while (normalized.startsWith("0") && normalized.length() > 1) normalized = normalized.substring(1);
        return normalized;
    }

    @Nullable
    private TrackInfo readTrackInfo(int index, int typeIndex) {
        String prefix = "track-list/" + index + "/";
        String mpvType = stringProperty(prefix + "type", "");
        int type = mediaTrackType(mpvType);
        if (type == C.TRACK_TYPE_UNKNOWN) return null;
        if (type == C.TRACK_TYPE_VIDEO && booleanProperty(prefix + "albumart", false)) return null;
        String id = stringProperty(prefix + "id", "");
        if (TextUtils.isEmpty(id)) id = String.valueOf(intProperty(prefix + "id", index + 1));
        String title = stringProperty(prefix + "title", "");
        String lang = stringProperty(prefix + "lang", "");
        int demuxId = intProperty(prefix + "demux-id", C.INDEX_UNSET);
        int srcId = intProperty(prefix + "src-id", C.INDEX_UNSET);
        if (TextUtils.isEmpty(lang) && typeIndex >= 0 && !TextUtils.isEmpty(currentIsoUri)) {
            int isoTrackType = type == C.TRACK_TYPE_AUDIO ? 1 : type == C.TRACK_TYPE_TEXT ? 2 : 0;
            if (isoTrackType != 0) {
                int isoTrackId = demuxId > 0 ? demuxId : srcId;
                lang = IsoSessionManager.getTrackLanguage(IsoSessionManager.parseId(currentIsoUri), isoTrackType, isoTrackId, typeIndex);
            }
        }
        String codec = stringProperty(prefix + "codec", "");
        boolean selected = booleanProperty(prefix + "selected", false);
        int width = intProperty(prefix + "demux-w", C.LENGTH_UNSET);
        int height = intProperty(prefix + "demux-h", C.LENGTH_UNSET);
        float frameRate = type == C.TRACK_TYPE_VIDEO ? videoFrameRate() : C.RATE_UNSET;
        if (type == C.TRACK_TYPE_VIDEO) {
            if (width <= 0) width = videoSize.width > 0 ? videoSize.width : intProperty("width", C.LENGTH_UNSET);
            if (height <= 0) height = videoSize.height > 0 ? videoSize.height : intProperty("height", C.LENGTH_UNSET);
        }
        int sampleRate = intProperty(prefix + "demux-samplerate", C.RATE_UNSET_INT);
        int channels = intProperty(prefix + "demux-channel-count", C.LENGTH_UNSET);
        int bitrate = intProperty(prefix + "demux-bitrate", C.LENGTH_UNSET);
        if (bitrate <= 0 && type == C.TRACK_TYPE_VIDEO) bitrate = intProperty("video-bitrate", C.LENGTH_UNSET);
        if (bitrate <= 0 && type == C.TRACK_TYPE_AUDIO) bitrate = intProperty("audio-bitrate", C.LENGTH_UNSET);
        ColorInfo colorInfo = type == C.TRACK_TYPE_VIDEO ? videoColorInfo() : null;
        return new TrackInfo(type, id, demuxId, srcId, title, lang, codec, selected, width, height, frameRate, sampleRate, channels, bitrate, colorInfo);
    }

    private float videoFrameRate() {
        double fps = doubleProperty("container-fps", 0);
        if (fps <= 0) fps = doubleProperty("estimated-vf-fps", 0);
        return fps > 0 ? (float) fps : C.RATE_UNSET;
    }

    @Nullable
    private ColorInfo videoColorInfo() {
        int colorSpace = mpvColorSpace();
        int colorRange = mpvColorRange();
        int colorTransfer = mpvColorTransfer();
        if (colorSpace == C.LENGTH_UNSET && colorRange == C.LENGTH_UNSET && colorTransfer == C.LENGTH_UNSET) return null;
        ColorInfo.Builder builder = new ColorInfo.Builder();
        if (colorSpace != C.LENGTH_UNSET) builder.setColorSpace(colorSpace);
        if (colorRange != C.LENGTH_UNSET) builder.setColorRange(colorRange);
        if (colorTransfer != C.LENGTH_UNSET) builder.setColorTransfer(colorTransfer);
        return builder.build();
    }

    private int mpvColorSpace() {
        String primaries = lowerProperty("video-params/primaries");
        String matrix = lowerProperty("video-params/colormatrix");
        String value = !TextUtils.isEmpty(primaries) ? primaries : matrix;
        if (value.contains("bt.2020") || value.contains("bt2020") || value.contains("2020")) return C.COLOR_SPACE_BT2020;
        if (value.contains("bt.709") || value.contains("bt709") || value.contains("709")) return C.COLOR_SPACE_BT709;
        if (value.contains("bt.601") || value.contains("bt601") || value.contains("601") || value.contains("smpte-170m") || value.contains("smpte170m")) return C.COLOR_SPACE_BT601;
        return C.LENGTH_UNSET;
    }

    private int mpvColorRange() {
        String value = lowerProperty("video-params/colorlevels");
        if (value.contains("full") || value.contains("pc")) return C.COLOR_RANGE_FULL;
        if (value.contains("limited") || value.contains("tv")) return C.COLOR_RANGE_LIMITED;
        return C.LENGTH_UNSET;
    }

    private int mpvColorTransfer() {
        String value = lowerProperty("video-params/gamma");
        if (value.contains("pq") || value.contains("st.2084") || value.contains("st2084")) return C.COLOR_TRANSFER_ST2084;
        if (value.contains("hlg")) return C.COLOR_TRANSFER_HLG;
        if (value.contains("srgb")) return C.COLOR_TRANSFER_SRGB;
        if (value.contains("linear")) return C.COLOR_TRANSFER_LINEAR;
        if (value.contains("gamma2.2") || value.contains("bt.470m") || value.contains("bt470m")) return C.COLOR_TRANSFER_GAMMA_2_2;
        if (value.contains("bt.1886") || value.contains("bt1886") || value.contains("709") || value.contains("601")) return C.COLOR_TRANSFER_SDR;
        return C.LENGTH_UNSET;
    }

    private String lowerProperty(String property) {
        return stringProperty(property, "").toLowerCase(Locale.US);
    }

    private int mediaTrackType(String mpvType) {
        if ("video".equals(mpvType)) return C.TRACK_TYPE_VIDEO;
        if ("audio".equals(mpvType)) return C.TRACK_TYPE_AUDIO;
        if ("sub".equals(mpvType)) return C.TRACK_TYPE_TEXT;
        return C.TRACK_TYPE_UNKNOWN;
    }

    private String sampleMimeType(TrackInfo info) {
        String codec = info.codec == null ? "" : info.codec.toLowerCase(Locale.US);
        if (info.type == C.TRACK_TYPE_TEXT) {
            if (codec.contains("pgs") || codec.contains("hdmv")) return MimeTypes.APPLICATION_PGS;
            if (codec.contains("dvd") || codec.contains("vobsub")) return MimeTypes.APPLICATION_VOBSUB;
            if (codec.contains("dvb")) return MimeTypes.APPLICATION_DVBSUBS;
            if (codec.contains("ass") || codec.contains("ssa")) return MimeTypes.TEXT_SSA;
            if (codec.contains("webvtt") || codec.contains("vtt")) return MimeTypes.TEXT_VTT;
            if (codec.contains("srt") || codec.contains("subrip")) return MimeTypes.APPLICATION_SUBRIP;
            if (codec.contains("ttml")) return MimeTypes.APPLICATION_TTML;
            return TextUtils.isEmpty(codec) ? MimeTypes.TEXT_UNKNOWN : MimeTypes.BASE_TYPE_TEXT + "/" + codec;
        }
        if (info.type == C.TRACK_TYPE_AUDIO) {
            if (codec.contains("aac")) return MimeTypes.AUDIO_AAC;
            if (codec.contains("ac3")) return MimeTypes.AUDIO_AC3;
            if (codec.contains("eac3") || codec.contains("e-ac-3")) return MimeTypes.AUDIO_E_AC3;
            if (codec.contains("opus")) return MimeTypes.AUDIO_OPUS;
            if (codec.contains("vorbis")) return MimeTypes.AUDIO_VORBIS;
            if (codec.contains("flac")) return MimeTypes.AUDIO_FLAC;
            if (codec.contains("mp3")) return MimeTypes.AUDIO_MPEG;
            return MimeTypes.BASE_TYPE_AUDIO + "/" + (TextUtils.isEmpty(codec) ? "unknown" : codec);
        }
        if (codec.contains("hevc") || codec.contains("h265")) return MimeTypes.VIDEO_H265;
        if (codec.contains("h264") || codec.contains("avc")) return MimeTypes.VIDEO_H264;
        if (codec.contains("av1")) return MimeTypes.VIDEO_AV1;
        if (codec.contains("vp9")) return MimeTypes.VIDEO_VP9;
        if (codec.contains("vp8")) return MimeTypes.VIDEO_VP8;
        if (codec.contains("mpeg2")) return MimeTypes.VIDEO_MPEG2;
        return MimeTypes.BASE_TYPE_VIDEO + "/" + (TextUtils.isEmpty(codec) ? "unknown" : codec);
    }

    private long doublePropertyMs(String property, long fallback) {
        try {
            Double value = MPVLib.getPropertyDouble(property);
            if (value == null || value.isNaN() || value.isInfinite()) return fallback;
            return Math.max(0, Math.round(value * SECONDS_TO_MS));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private double doubleProperty(String property, double fallback) {
        try {
            Double value = MPVLib.getPropertyDouble(property);
            if (value == null || value.isNaN() || value.isInfinite()) return fallback;
            return value;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private double doubleValue(@Nullable Object value, double fallback) {
        if (!(value instanceof Number number)) return fallback;
        double result = number.doubleValue();
        return Double.isNaN(result) || Double.isInfinite(result) ? fallback : result;
    }

    private long doubleSecondsToMs(@Nullable Object value, long fallback) {
        if (value instanceof Number number) return Math.max(0, Math.round(number.doubleValue() * SECONDS_TO_MS));
        return fallback;
    }

    private long longValue(@Nullable Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        return fallback;
    }

    private long longProperty(String property, long fallback) {
        try {
            Integer value = MPVLib.getPropertyInt(property);
            return value == null ? fallback : value;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private int intProperty(String property, int fallback) {
        try {
            Integer value = MPVLib.getPropertyInt(property);
            return value == null ? fallback : value;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private boolean booleanProperty(String property, boolean fallback) {
        try {
            Boolean value = MPVLib.getPropertyBoolean(property);
            return value == null ? fallback : value;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private String stringProperty(String property, String fallback) {
        try {
            String value = MPVLib.getPropertyString(property);
            return value == null ? fallback : value;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private void refreshRenderState() {
        if (!initialized) return;
        cachedCurrentVo = firstNonEmpty(stringProperty("current-vo", cachedCurrentVo), cachedCurrentVo);
        cachedCurrentGpuContext = firstNonEmpty(stringProperty("current-gpu-context", cachedCurrentGpuContext), cachedCurrentGpuContext);
        cachedGpuApi = firstNonEmpty(stringProperty("gpu-api", cachedGpuApi), cachedGpuApi);
    }

    private void refreshRuntimeDiagnostics() {
        if (!initialized) return;
        cachedCurrentAo = firstNonEmpty(stringProperty("current-ao", cachedCurrentAo), cachedCurrentAo);
        cachedAudioDevice = firstNonEmpty(stringProperty("audio-device", cachedAudioDevice), cachedAudioDevice);
        cachedHwdecCurrent = firstNonEmpty(stringProperty("hwdec-current", cachedHwdecCurrent), cachedHwdecCurrent);
        cachedAvSyncSeconds = doubleProperty("avsync", cachedAvSyncSeconds);
        cachedDisplayFps = doubleProperty("display-fps", cachedDisplayFps);
        cachedEstimatedDisplayFps = doubleProperty("estimated-display-fps", cachedEstimatedDisplayFps);
        cachedDecoderDroppedFrames = Math.max(0, longProperty("decoder-frame-drop-count", cachedDecoderDroppedFrames));
        cachedOutputDroppedFrames = Math.max(0, longProperty("frame-drop-count", cachedOutputDroppedFrames));
        cachedMistimedFrames = Math.max(0, longProperty("mistimed-frame-count", cachedMistimedFrames));
        cachedDelayedFrames = Math.max(0, longProperty("vo-delayed-frame-count", cachedDelayedFrames));
        cachedDisplaySyncActive = booleanProperty("display-sync-active", cachedDisplaySyncActive);
    }

    private void resetRuntimeDiagnostics() {
        cachedCurrentVo = null;
        cachedCurrentGpuContext = null;
        cachedGpuApi = null;
        cachedCurrentAo = null;
        cachedAudioDevice = null;
        cachedHwdecCurrent = null;
        cachedAvSyncSeconds = 0;
        cachedDisplayFps = 0;
        cachedEstimatedDisplayFps = 0;
        cachedContentFrameRate = 0;
        cachedDecoderDroppedFrames = 0;
        cachedOutputDroppedFrames = 0;
        cachedMistimedFrames = 0;
        cachedDelayedFrames = 0;
        cachedDisplaySyncActive = false;
    }

    private String stringValue(@Nullable Object value, String fallback) {
        return value instanceof String text && !TextUtils.isEmpty(text) ? text : fallback;
    }

    private String joinParts(String... values) {
        StringBuilder builder = new StringBuilder();
        if (values == null) return "";
        for (String value : values) {
            if (TextUtils.isEmpty(value)) continue;
            if (builder.length() > 0) builder.append(" / ");
            builder.append(value);
        }
        return builder.toString();
    }

    private String shortText(String value, int maxLength) {
        if (TextUtils.isEmpty(value) || maxLength <= 0 || value.length() <= maxLength) return value;
        return value.substring(0, maxLength - 1) + "...";
    }

    private String formatAvSync() {
        long ms = Math.round(cachedAvSyncSeconds * 1000.0);
        return "A-V " + ms + "ms";
    }

    private String formatDisplayFps() {
        if (cachedDisplayFps <= 0 && cachedEstimatedDisplayFps <= 0) return "";
        if (cachedDisplayFps > 0 && cachedEstimatedDisplayFps > 0 && Math.abs(cachedDisplayFps - cachedEstimatedDisplayFps) >= 0.01) {
            return "刷新 " + formatHz(cachedDisplayFps) + "/估" + formatHz(cachedEstimatedDisplayFps);
        }
        return "刷新 " + formatHz(cachedDisplayFps > 0 ? cachedDisplayFps : cachedEstimatedDisplayFps);
    }

    private String formatDroppedFrames() {
        long total = cachedDecoderDroppedFrames + cachedOutputDroppedFrames;
        if (total <= 0 && cachedMistimedFrames <= 0 && cachedDelayedFrames <= 0) return "";
        return joinParts(
                total > 0 ? "掉帧 dec " + cachedDecoderDroppedFrames + "/out " + cachedOutputDroppedFrames : "",
                cachedMistimedFrames > 0 ? "mistimed " + cachedMistimedFrames : "",
                cachedDelayedFrames > 0 ? "delayed " + cachedDelayedFrames : "");
    }

    private String formatDisplaySync() {
        return cachedDisplaySyncActive ? "display-sync 开" : "";
    }

    private String formatShader() {
        return lutShader == null ? "" : "shader 开";
    }

    private String formatHz(double value) {
        if (value <= 0) return "-";
        double rounded = Math.rint(value);
        if (Math.abs(value - rounded) < 0.01) return String.valueOf((long) rounded) + "Hz";
        return String.format(Locale.US, "%.2fHz", value);
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private String emptyDash(String value) {
        return TextUtils.isEmpty(value) ? "-" : value;
    }

    private boolean isConfiguredVulkan() {
        return TextUtils.equals(config.vo(), "gpu-next")
                && TextUtils.equals(config.gpuContext(), "androidvk")
                && TextUtils.equals(config.gpuApi(), "vulkan");
    }

    private boolean isRuntimeVulkan(String currentVo, String currentGpuContext, String gpuApi) {
        return containsIgnoreCase(currentGpuContext, "vk")
                || containsIgnoreCase(currentGpuContext, "vulkan")
                || containsIgnoreCase(gpuApi, "vulkan")
                || containsIgnoreCase(currentVo, "gpu-next") && isConfiguredVulkan();
    }

    private boolean containsIgnoreCase(String value, String needle) {
        return value != null && needle != null && value.toLowerCase(Locale.US).contains(needle.toLowerCase(Locale.US));
    }

    private void fail(Throwable e, int errorCode) {
        playerError = new PlaybackException(e.getMessage(), e, errorCode);
        playbackState = Player.STATE_IDLE;
        loading = false;
        fileLoaded = false;
        closeContentFds();
        mainHandler.removeCallbacks(endFileValidationRunnable);
        stopStateRefresh();
        SpiderDebug.log("mpv", "fail code=%d message=%s diagnostics=%s", errorCode, e.getMessage(), diagnosticSummary());
        invalidateState();
    }

    private String diagnosticSummary() {
        List<String> parts = new ArrayList<>();
        parts.add("uri=" + currentPlayableUri);
        parts.add("hls=" + currentLikelyHls);
        parts.add("dash=" + currentLikelyDash);
        parts.add("loaded=" + fileLoaded);
        parts.add("started=" + loadStarted);
        parts.add("restart=" + playbackRestarted);
        parts.add("size=" + videoSize.width + "x" + videoSize.height);
        parts.add("position=" + cachedPositionMs);
        parts.add("duration=" + cachedDurationMs);
        parts.add("tracks=" + currentTracks.getGroups().size());
        parts.add("path=" + stringProperty("path", ""));
        parts.add("file-format=" + stringProperty("file-format", ""));
        parts.add("video-codec=" + stringProperty("video-codec", ""));
        parts.add("audio-codec=" + stringProperty("audio-codec", ""));
        parts.add("hwdec=" + stringProperty("hwdec-current", ""));
        parts.add("vo=" + stringProperty("current-vo", stringProperty("vo-configured", "")));
        parts.add("shader=" + (lutShader == null ? "-" : lutShader.diagnostics()));
        parts.add("end-file=" + endFileReasonName(lastEndFileReason) + "/" + mpvErrorName(lastEndFileError) + "(" + lastEndFileError + ")");
        if (!TextUtils.isEmpty(lastEndFileErrorText)) parts.add("end-file-text=" + lastEndFileErrorText);
        if (currentLikelyHls) parts.add("hls-proxy=" + hlsProxy.diagnostics());
        return String.join(" ", parts);
    }

    private String recentLogSuffix() {
        if (!TextUtils.isEmpty(lastFailureLog)) return ": " + lastFailureLog;
        if (recentLogs.isEmpty()) return "";
        return ": " + recentLogs.get(recentLogs.size() - 1);
    }

    private void applyShaderPipeline(boolean force) {
        if (!initialized) return;
        String target = lutShader == null ? "" : lutShader.getPath();
        if (!force && TextUtils.equals(appliedLutShaderPath, target)) return;
        if (!TextUtils.isEmpty(appliedLutShaderPath)) {
            safeCommand(new String[]{"change-list", "glsl-shaders", "remove", appliedLutShaderPath});
            SpiderDebug.log("mpv", "shader remove lut=%s", appliedLutShaderPath);
        }
        if (!TextUtils.isEmpty(target)) {
            safeCommand(new String[]{"change-list", "glsl-shaders", "append", target});
            SpiderDebug.log("mpv", "shader append lut=%s", target);
        }
        appliedLutShaderPath = target;
    }

    private void postToMain(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) runnable.run();
        else mainHandler.post(runnable);
    }

    private void setOption(String name, String value) {
        if (value == null) value = "";
        try {
            MPVLib.setOptionString(name, value);
        } catch (Throwable ignored) {
        }
    }

    private void setRuntimeString(String name, String value) {
        if (value == null) value = "";
        if (initialized) {
            try {
                MPVLib.setPropertyString(name, value);
                return;
            } catch (Throwable ignored) {
            }
        }
        setOption(name, value);
    }

    private void observe(String property, int format) {
        try {
            MPVLib.observeProperty(property, format);
        } catch (Throwable ignored) {
        }
    }

    private void safeSetPropertyBoolean(String property, boolean value) {
        try {
            MPVLib.setPropertyBoolean(property, value);
        } catch (Throwable ignored) {
        }
    }

    private void applyTextOffset() {
        if (initialized) safeSetPropertyDouble("sub-delay", textOffsetMs / SECONDS_TO_MS);
    }

    private void applyAudioOffset() {
        if (initialized) safeSetPropertyDouble("audio-delay", audioOffsetMs / SECONDS_TO_MS);
    }

    private void applySubtitleStyle() {
        if (!initialized) return;
        CaptionStyle style = captionStyle();
        safeSetPropertyDouble("sub-scale", subtitleScale());
        safeSetPropertyDouble("sub-pos", subtitlePosition());
        safeSetPropertyString("sub-font", style.font);
        safeSetPropertyString("sub-bold", style.bold ? "yes" : "no");
        safeSetPropertyString("sub-italic", style.italic ? "yes" : "no");
        safeSetPropertyString("sub-color", mpvColor(style.foreground));
        safeSetPropertyString("sub-border-color", mpvColor(style.edge));
        safeSetPropertyString("sub-shadow-color", mpvColor(style.back));
        safeSetPropertyString("sub-back-color", mpvColor(style.back));
        safeSetPropertyString("sub-border-style", style.borderStyle);
        safeSetPropertyDouble("sub-border-size", style.borderSize);
        safeSetPropertyDouble("sub-shadow-offset", style.shadowOffset);
        safeSetPropertyString("sub-ass-style-overrides", assStyleOverrides(style));
    }

    private double subtitleScale() {
        if (subtitleTextSize <= 0) return 1.0;
        return Math.max(0.5, Math.min(2.5, subtitleTextSize / DEFAULT_SUBTITLE_TEXT_SIZE_FRACTION));
    }

    private double subtitlePosition() {
        return Math.max(0, Math.min(150, 100.0 - subtitlePosition * 100.0));
    }

    private CaptionStyle captionStyle() {
        if (!PlayerSetting.isCaption()) return defaultCaptionStyle();
        try {
            CaptioningManager manager = (CaptioningManager) context.getSystemService(Context.CAPTIONING_SERVICE);
            CaptioningManager.CaptionStyle style = manager == null ? null : manager.getUserStyle();
            if (style == null) return defaultCaptionStyle();
            int foreground = style.hasForegroundColor() ? style.foregroundColor : Color.WHITE;
            int background = style.hasBackgroundColor() ? style.backgroundColor : Color.TRANSPARENT;
            int edge = style.hasEdgeColor() ? style.edgeColor : Color.BLACK;
            int edgeType = style.hasEdgeType() ? style.edgeType : CaptioningManager.CaptionStyle.EDGE_TYPE_OUTLINE;
            Typeface typeface = style.getTypeface();
            String font = captionFont(typeface);
            boolean bold = typeface != null && typeface.isBold();
            boolean italic = typeface != null && typeface.isItalic();
            return switch (edgeType) {
                case CaptioningManager.CaptionStyle.EDGE_TYPE_NONE -> captionStyle(font, bold, italic, foreground, edge, background, Color.TRANSPARENT, 0.0, 0.0);
                case CaptioningManager.CaptionStyle.EDGE_TYPE_DROP_SHADOW -> captionStyle(font, bold, italic, foreground, edge, background, edge, 0.0, 2.0);
                case CaptioningManager.CaptionStyle.EDGE_TYPE_RAISED, CaptioningManager.CaptionStyle.EDGE_TYPE_DEPRESSED -> captionStyle(font, bold, italic, foreground, edge, background, edge, 1.0, 1.0);
                default -> captionStyle(font, bold, italic, foreground, edge, background, Color.TRANSPARENT, 3.0, 0.0);
            };
        } catch (Throwable ignored) {
            return defaultCaptionStyle();
        }
    }

    private CaptionStyle captionStyle(String font, boolean bold, boolean italic, int foreground, int edge, int background, int shadow, double borderSize, double shadowOffset) {
        boolean hasBackground = Color.alpha(background) > 0;
        return new CaptionStyle(font, bold, italic, foreground, edge, hasBackground ? background : shadow, hasBackground ? "background-box" : "outline-and-shadow", borderSize, hasBackground ? 2.0 : shadowOffset);
    }

    private CaptionStyle defaultCaptionStyle() {
        return new CaptionStyle("sans-serif", false, false, Color.WHITE, Color.BLACK, Color.TRANSPARENT, "outline-and-shadow", 3.0, 0.0);
    }

    private String captionFont(@Nullable Typeface typeface) {
        if (typeface == null || Typeface.DEFAULT.equals(typeface) || Typeface.SANS_SERIF.equals(typeface)) return "sans-serif";
        if (Typeface.SERIF.equals(typeface)) return "serif";
        if (Typeface.MONOSPACE.equals(typeface)) return "monospace";
        String value = typeface.toString().toLowerCase(Locale.US);
        if (value.contains("mono")) return "monospace";
        if (value.contains("serif")) return "serif";
        return "sans-serif";
    }

    private String mpvColor(int color) {
        return String.format(Locale.US, "%.4f/%.4f/%.4f/%.4f", Color.red(color) / 255.0, Color.green(color) / 255.0, Color.blue(color) / 255.0, Color.alpha(color) / 255.0);
    }

    private String assStyleOverrides(CaptionStyle style) {
        return "FontName=" + style.font
                + ",Bold=" + (style.bold ? "1" : "0")
                + ",Italic=" + (style.italic ? "1" : "0")
                + ",PrimaryColour=" + assColor(style.foreground)
                + ",OutlineColour=" + assColor(style.edge)
                + ",BackColour=" + assColor(style.back)
                + ",BorderStyle=" + ("background-box".equals(style.borderStyle) ? "4" : "1")
                + ",Outline=" + String.format(Locale.US, "%.1f", style.borderSize)
                + ",Shadow=" + String.format(Locale.US, "%.1f", style.shadowOffset);
    }

    private String assColor(int color) {
        int alpha = 255 - Color.alpha(color);
        return String.format(Locale.US, "&H%02X%02X%02X%02X", alpha, Color.blue(color), Color.green(color), Color.red(color));
    }

    private void safeSetPropertyDouble(String property, double value) {
        try {
            MPVLib.setPropertyDouble(property, value);
        } catch (Throwable ignored) {
        }
    }

    private void safeSetPropertyInt(String property, int value) {
        try {
            MPVLib.setPropertyInt(property, value);
        } catch (Throwable ignored) {
        }
    }

    private void safeSetPropertyString(String property, String value) {
        try {
            MPVLib.setPropertyString(property, value);
        } catch (Throwable ignored) {
        }
    }

    private void safeCommand(String[] command) {
        try {
            MPVLib.command(command);
        } catch (Throwable ignored) {
        }
    }

    private void closeContentFds() {
        if (contentFds.isEmpty()) return;
        for (ParcelFileDescriptor fd : contentFds) {
            try {
                fd.close();
            } catch (IOException ignored) {
            }
        }
        contentFds.clear();
    }

    private void closeIsoSession() {
        if (TextUtils.isEmpty(currentIsoUri)) return;
        if (isoMetadataListenerSessionId > 0) {
            IsoSessionManager.removeTrackMetadataListener(isoMetadataListenerSessionId, isoTrackMetadataReadyListener);
            isoMetadataListenerSessionId = -1;
        }
        IsoSessionManager.closeUri(currentIsoUri);
        currentIsoUri = null;
        isoTrackListDumped = false;
    }

    private void attachIsoTrackMetadataListener() {
        long sessionId = IsoSessionManager.parseId(currentIsoUri);
        if (sessionId <= 0) return;
        if (isoMetadataListenerSessionId > 0) {
            IsoSessionManager.removeTrackMetadataListener(isoMetadataListenerSessionId, isoTrackMetadataReadyListener);
        }
        isoMetadataListenerSessionId = sessionId;
        IsoSessionManager.addTrackMetadataListener(sessionId, isoTrackMetadataReadyListener);
    }

    private void onIsoTrackMetadataReady() {
        long sessionId = isoMetadataListenerSessionId;
        mainHandler.post(() -> {
            if (released || sessionId <= 0 || sessionId != IsoSessionManager.parseId(currentIsoUri)) return;
            SpiderDebug.log("mpv", "iso track metadata ready session=%d", sessionId);
            refreshTracks();
            invalidateState();
        });
    }

    private boolean isLikelyIso(MediaItem item, String uri) {
        if (item != null && item.localConfiguration != null) {
            String mime = item.localConfiguration.mimeType;
            if ("video/x-iso".equalsIgnoreCase(mime)
                    || "application/x-iso9660-image".equalsIgnoreCase(mime)
                    || "application/x-iso9660".equalsIgnoreCase(mime)) return true;
        }
        String lower = uri == null ? "" : uri.toLowerCase(Locale.US);
        try {
            String path = Uri.parse(uri).getPath();
            if (path != null && path.toLowerCase(Locale.US).endsWith(".iso")) return true;
        } catch (Throwable ignored) {
        }
        if (lower.contains(".iso?") || lower.contains("%2eiso")) return true;
        CharSequence title = item == null ? null : item.mediaMetadata.title;
        return title != null && title.toString().trim().toLowerCase(Locale.US).endsWith(".iso");
    }

    private void logSourceDiagnostics(MediaItem item, String uri, Map<String, String> headers) {
        String scheme = "";
        String host = "";
        boolean loopback = false;
        boolean pathIso = false;
        try {
            Uri parsed = Uri.parse(uri);
            scheme = String.valueOf(parsed.getScheme());
            host = String.valueOf(parsed.getHost());
            loopback = "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host);
            String path = parsed.getPath();
            pathIso = path != null && path.toLowerCase(Locale.US).endsWith(".iso");
        } catch (Throwable ignored) {
        }
        String mime = item != null && item.localConfiguration != null ? item.localConfiguration.mimeType : null;
        String mediaId = item == null ? null : item.mediaId;
        CharSequence title = item == null ? null : item.mediaMetadata.title;
        Log.i(TAG, "source diagnostic scheme=" + scheme
                + " host=" + host
                + " loopback=" + loopback
                + " urlLen=" + (uri == null ? 0 : uri.length())
                + " pathIso=" + pathIso
                + " mime=" + mime
                + " mediaIdLen=" + (mediaId == null ? 0 : mediaId.length())
                + " mediaIdIso=" + containsIso(mediaId)
                + " titleIso=" + containsIso(title == null ? null : title.toString())
                + " headers=" + (headers == null ? 0 : headers.size()));
    }

    private boolean containsIso(String value) {
        return value != null && value.toLowerCase(Locale.US).contains(".iso");
    }

    private boolean isOpaqueLocalProxy(String uri) {
        try {
            Uri parsed = Uri.parse(uri);
            String host = parsed.getHost();
            return ("http".equalsIgnoreCase(parsed.getScheme()) || "https".equalsIgnoreCase(parsed.getScheme()))
                    && ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean shouldProbeOpaqueIso(MediaItem item, String uri) {
        if (!isOpaqueLocalProxy(uri)) return false;
        if (containsIso(uri)) return true;
        if (item == null) return false;
        if (containsIso(item.mediaId)) return true;
        CharSequence title = item.mediaMetadata.title;
        return title != null && containsIso(title.toString());
    }

    private void copySupportAssets() throws IOException {
        copyAsset("cacert.pem", config.caFile());
        copyFallbackSubtitleFont(new File(config.configDir(), "subfont.ttf"));
        writeFontsConf(new File(config.configDir(), "fonts.conf"));
    }

    private void copyFallbackSubtitleFont(File outFile) throws IOException {
        if (outFile.isFile() && outFile.length() > 0) return;
        String[] candidates = {
                "/system/fonts/NotoSansCJK-Regular.ttc",
                "/system/fonts/NotoSansSC-Regular.otf",
                "/system/fonts/DroidSansFallback.ttf",
                "/system/fonts/DroidSansFallbackBBK.ttf",
                "/product/fonts/NotoSansCJK-Regular.ttc",
                "/system/fonts/Roboto-Regular.ttf"
        };
        for (String path : candidates) {
            File source = new File(path);
            if (!source.isFile() || source.length() == 0) continue;
            copyFile(source, outFile);
            SpiderDebug.log("mpv", "subtitle fallback font copied source=%s size=%d", path, outFile.length());
            return;
        }
        SpiderDebug.log("mpv", "subtitle fallback font unavailable");
    }

    private void copyFile(File source, File outFile) throws IOException {
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Unable to create " + parent);
        try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
    }

    private void copyAsset(String name, File outFile) throws IOException {
        AssetManager assets = context.getAssets();
        try (InputStream in = assets.open(name, AssetManager.ACCESS_STREAMING)) {
            long size = in.available();
            if (outFile.length() == size && size > 0) return;
            File parent = outFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Unable to create " + parent);
            try (OutputStream out = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
        }
    }

    private void writeFontsConf(File file) {
        String text = "<fontconfig>\n"
                + "<dir>/system/fonts/</dir>\n"
                + "<dir>/product/fonts/</dir>\n"
                + "<cachedir>" + config.cacheDir().getAbsolutePath() + "</cachedir>\n"
                + "<alias><family>serif</family><prefer><family>Noto Serif</family></prefer></alias>\n"
                + "<alias><family>sans-serif</family><prefer><family>Roboto</family><family>Noto Sans</family></prefer></alias>\n"
                + "<alias><family>monospace</family><prefer><family>Droid Sans Mono</family></prefer></alias>\n"
                + "</fontconfig>\n";
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    private final class TrackInfo {
        final int type;
        final String id;
        final int demuxId;
        final int srcId;
        final String title;
        final String lang;
        final String codec;
        final boolean selected;
        final int width;
        final int height;
        final float frameRate;
        final int sampleRate;
        final int channels;
        final int bitrate;
        final ColorInfo colorInfo;

        TrackInfo(int type, String id, int demuxId, int srcId, String title, String lang, String codec, boolean selected, int width, int height, float frameRate, int sampleRate, int channels, int bitrate, @Nullable ColorInfo colorInfo) {
            this.type = type;
            this.id = id;
            this.demuxId = demuxId;
            this.srcId = srcId;
            this.title = title;
            this.lang = lang;
            this.codec = codec;
            this.selected = selected;
            this.width = width;
            this.height = height;
            this.frameRate = frameRate;
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.bitrate = bitrate;
            this.colorInfo = colorInfo;
        }

        Format toFormat() {
            String label = TextUtils.isEmpty(title) ? (TextUtils.isEmpty(lang) ? trackLabel() : null) : title;
            Format.Builder builder = new Format.Builder()
                    .setId(type + ":" + id)
                    .setLabel(label)
                    .setCodecs(TextUtils.isEmpty(codec) ? null : codec)
                    .setLanguage(TextUtils.isEmpty(lang) ? null : lang)
                    .setSampleMimeType(sampleMimeType(this));
            if (width > 0) builder.setWidth(width);
            if (height > 0) builder.setHeight(height);
            if (frameRate > 0) builder.setFrameRate(frameRate);
            if (colorInfo != null) builder.setColorInfo(colorInfo);
            if (sampleRate > 0) builder.setSampleRate(sampleRate);
            if (channels > 0) builder.setChannelCount(channels);
            if (bitrate > 0) builder.setAverageBitrate(bitrate);
            return builder.build();
        }

        private String trackLabel() {
            String prefix = switch (type) {
                case C.TRACK_TYPE_VIDEO -> "Video";
                case C.TRACK_TYPE_AUDIO -> "Audio";
                case C.TRACK_TYPE_TEXT -> "Subtitle";
                default -> "Track";
            };
            return prefix + " " + id;
        }
    }

    private record CaptionStyle(String font, boolean bold, boolean italic, int foreground, int edge, int back, String borderStyle, double borderSize, double shadowOffset) {
    }
}
