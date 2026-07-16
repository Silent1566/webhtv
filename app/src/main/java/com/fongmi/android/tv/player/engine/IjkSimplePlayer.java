package com.fongmi.android.tv.player.engine;

import android.media.AudioManager;
import android.net.Uri;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
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
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.setting.IjkPerformanceSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Future;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.IjkTimedText;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;

@UnstableApi
class IjkSimplePlayer extends SimpleBasePlayer implements IMediaPlayer.Listener {

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
    private PlaybackParameters playbackParameters;
    private PlaybackException playerError;
    private Tracks currentTracks;
    private VideoSize videoSize;
    private IjkSubtitleTrack subtitleTrack;
    private CueGroup currentCues;
    private Future<?> subtitleLoad;
    private int playbackState;
    private int bufferingPercent;
    private int decode;
    private int subtitleSerial;
    private long pendingSeekPositionMs;
    private long pendingSeekRequestedAtMs;
    private boolean playWhenReady;
    private boolean loading;
    private boolean repeatOne;
    private boolean ownsSurface;
    private float volume;

    IjkSimplePlayer(int decode) {
        super(Looper.getMainLooper());
        this.decode = decode;
        ijk = new IjkMediaPlayer();
        ijk.setListener(this);
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
    }

    @Override
    protected State getState() {
        int state = playbackState;
        boolean isLoading = loading && state != Player.STATE_IDLE && state != Player.STATE_ENDED;
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
                .setVolume(volume)
                .setPlaylist(mediaItem == null ? ImmutableList.of() : ImmutableList.of(mediaItemData()))
                .setCurrentMediaItemIndex(mediaItem == null ? C.INDEX_UNSET : 0);
        if (mediaItem != null) {
            long duration = duration();
            long position = position();
            builder.setContentPositionMs(isPlayingInternal() ? PositionSupplier.getExtrapolating(position, playbackParameters.speed) : PositionSupplier.getConstant(position));
            builder.setContentBufferedPositionMs(PositionSupplier.getConstant(bufferedPosition(duration)));
            builder.setTotalBufferedDurationMs(PositionSupplier.getConstant(Math.max(0, bufferedPosition(duration) - position)));
        }
        return builder.build();
    }

    private MediaItemData mediaItemData() {
        long duration = duration();
        return new MediaItemData.Builder(mediaItem.mediaId)
                .setMediaItem(mediaItem)
                .setMediaMetadata(mediaItem.mediaMetadata)
                .setDurationUs(duration == C.TIME_UNSET ? C.TIME_UNSET : duration * 1000)
                .setIsSeekable(duration > 0)
                .setIsDynamic(duration == C.TIME_UNSET)
                .setTracks(currentTracks)
                .build();
    }

    Tracks getCurrentTracksSnapshot() {
        return currentTracks;
    }

    void setDecode(int decode) {
        this.decode = decode;
    }

    @Override
    protected ListenableFuture<?> handleSetMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
        clearSubtitles();
        mediaItem = mediaItems.isEmpty() ? null : mediaItems.get(0);
        setPendingSeek(mediaItem != null && startPositionMs > 0 ? startPositionMs : C.TIME_UNSET);
        playbackState = mediaItem == null ? Player.STATE_IDLE : Player.STATE_IDLE;
        loading = false;
        currentTracks = Tracks.EMPTY;
        playerError = null;
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleAddMediaItems(int index, List<MediaItem> mediaItems) {
        clearSubtitles();
        mediaItem = mediaItems.isEmpty() ? null : mediaItems.get(0);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleReplaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems) {
        clearSubtitles();
        mediaItem = mediaItems.isEmpty() ? null : mediaItems.get(0);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleRemoveMediaItems(int fromIndex, int toIndex) {
        clearSubtitles();
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
        if (playbackState == Player.STATE_READY) {
            if (playWhenReady) ijk.start();
            else ijk.pause();
        }
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleStop() {
        stopInternal(true);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleRelease() {
        stopInternal(false);
        hlsProxy.release();
        ijk.release();
        return Futures.immediateVoidFuture();
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
        setPendingSeek(positionMs > 0 ? positionMs : C.TIME_UNSET);
        if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
            ijk.seekTo(positionMs);
        }
        updateCurrentCues(positionMs);
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
        if (playWhenReady) ijk.start();
        invalidateState();
        startStateRefresh();
    }

    @Override
    public void onCompletion(IMediaPlayer mp) {
        setPendingSeek(C.TIME_UNSET);
        playbackState = Player.STATE_ENDED;
        loading = false;
        currentCues = CueGroup.EMPTY_TIME_ZERO;
        stopStateRefresh();
        invalidateState();
    }

    @Override
    public boolean onError(IMediaPlayer mp, int what, int extra) {
        setPendingSeek(C.TIME_UNSET);
        playbackState = Player.STATE_IDLE;
        loading = false;
        stopStateRefresh();
        clearSubtitles();
        playerError = new PlaybackException("IJK error: " + what + ", " + extra, null, errorCode(what));
        SpiderDebug.log("ijk", "error what=%d extra=%d mapped=%d decode=%d state=%d loading=%s uri=%s", what, extra, playerError.errorCode, decode, playbackState, loading, summarizeUri());
        if (BuildConfig.DEBUG) Log.e("WebHTV-IJK", "error what=" + what + " extra=" + extra + " uri=" + summarizeUri());
        invalidateState();
        return true;
    }

    @Override
    public void onInfo(IMediaPlayer mp, int what, int extra) {
        if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
            loading = true;
            playbackState = Player.STATE_BUFFERING;
            startStateRefresh();
        } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END || what == IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
            loading = false;
            playbackState = Player.STATE_READY;
            startStateRefresh();
        }
        invalidateState();
    }

    @Override
    public void onBufferingUpdate(IMediaPlayer mp, int percent) {
        bufferingPercent = percent;
        invalidateState();
    }

    @Override
    public void onBufferingUpdate(IMediaPlayer mp, long positionMs) {
        invalidateState();
    }

    @Override
    public void onVideoSizeChanged(IMediaPlayer mp, int width, int height, int sarNum, int sarDen) {
        videoSize = new VideoSize(width, height);
        refreshTracks();
        invalidateState();
    }

    @Override
    public void onTimedText(IMediaPlayer mp, IjkTimedText text) {
    }

    private void openCurrent() {
        if (mediaItem == null || mediaItem.localConfiguration == null) return;
        try {
            playbackState = Player.STATE_BUFFERING;
            loading = true;
            playerError = null;
            ijk.reset();
            startSubtitleLoad(mediaItem);
            hlsProxy.clear();
            ijk.setWakeMode(App.get(), PowerManager.PARTIAL_WAKE_LOCK);
            Uri sourceUri = mediaItem.localConfiguration.uri;
            Map<String, String> headers = ExoUtil.extractHeaders(mediaItem);
            String playableUrl = sourceUri.toString();
            boolean dash = isLikelyDash(mediaItem, playableUrl);
            if (BuildConfig.DEBUG) Log.e("WebHTV-IJK", "open dash=" + dash + " uri=" + playableUrl + " headers=" + headers.keySet());
            if (dash) {
                String originalUrl = playableUrl;
                playableUrl = hlsProxy.proxyDash(playableUrl, headers);
                SpiderDebug.log("ijk", "dash compatibility proxy enabled original=%s proxy=%s", originalUrl, playableUrl);
            } else if (shouldProxyHls(mediaItem, playableUrl)) {
                playableUrl = hlsProxy.proxy(playableUrl, headers);
                SpiderDebug.log("ijk", "hls proxy enabled original=%s proxy=%s", sourceUri, playableUrl);
            }
            SpiderDebug.log("ijk", "open dash=%s decode=%d uri=%s mime=%s headers=%s", dash, decode, summarizeUri(), mediaItem.localConfiguration.mimeType, headers.keySet());
            configureOptions(sourceUri, dash);
            bindVideoOutput();
            ijk.setDataSource(App.get(), Uri.parse(playableUrl), headers);
            ijk.setAudioStreamType(AudioManager.STREAM_MUSIC);
            ijk.setScreenOnWhilePlaying(true);
            ijk.setLooping(repeatOne);
            ijk.setSpeed(playbackParameters.speed);
            ijk.prepareAsync();
            invalidateState();
            startStateRefresh();
        } catch (Throwable e) {
            playerError = new PlaybackException(e.getMessage(), e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
            SpiderDebug.log("ijk", "open failed uri=%s error=%s", summarizeUri(), e.toString());
            playbackState = Player.STATE_IDLE;
            loading = false;
            clearSubtitles();
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
        loading = false;
        bufferingPercent = 0;
        currentTracks = Tracks.EMPTY;
        videoSize = VideoSize.UNKNOWN;
        clearSubtitles();
        if (resetState) playbackState = Player.STATE_IDLE;
        stopStateRefresh();
    }

    private void startStateRefresh() {
        App.removeCallbacks(stateRefreshRunnable);
        App.post(stateRefreshRunnable, subtitleTrack.isEmpty() ? STATE_REFRESH_INTERVAL_MS : SUBTITLE_REFRESH_INTERVAL_MS);
    }

    private void stopStateRefresh() {
        App.removeCallbacks(stateRefreshRunnable);
    }

    private void refreshPlaybackState() {
        if (mediaItem == null || playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED || playerError != null) return;
        updateCurrentCues(position());
        invalidateState();
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

    private void setVideoOutput(Object output) {
        detachSurfaceHolder();
        if (output instanceof SurfaceView view) {
            setSurfaceHolder(view.getHolder());
        } else if (output instanceof TextureView view && view.getSurfaceTexture() != null) {
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
        surfaceHolder.addCallback(surfaceCallback);
        surface = surfaceHolder.getSurface();
        ownsSurface = false;
    }

    private void bindVideoOutput() {
        try {
            if (surfaceHolder != null) {
                surface = surfaceHolder.getSurface();
                if (surface != null && surface.isValid()) {
                    ijk.setDisplay(surfaceHolder);
                }
            } else if (surface != null && surface.isValid()) {
                ijk.setSurface(surface);
            }
        } catch (Throwable ignored) {
        }
    }

    private void clearVideoOutput() {
        detachSurfaceHolder();
        releaseOwnedSurface();
        surface = null;
        ijk.setSurface(null);
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
        String url = uri.toString();
        boolean realtime = isRealtimeUrl(url);
        if (dash) ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "iformat", "dash");
        configureSoftDecodeOptions();
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_timeout", -1);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "fastseek");
        // SegmentBase MP4 relies on HTTP byte-range seeks for sidx/moof access.
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", dash ? 1 : 0);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", IjkPerformanceSetting.isAccurateSeek() ? 1 : 0);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", IjkPerformanceSetting.getFrameDropValue());
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", IjkPerformanceSetting.getBufferMb() * 1024L * 1024L);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", dash ? 0 : (IjkPerformanceSetting.isPacketBuffering() ? 1 : 0));
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "first-high-water-mark-ms", IjkPerformanceSetting.getFirstWaterMs());
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "next-high-water-mark-ms", IjkPerformanceSetting.getNextWaterMs());
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "last-high-water-mark-ms", IjkPerformanceSetting.getLastWaterMs());
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
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "video-pictq-size", IjkPerformanceSetting.getPictureQueue());
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "protocol_whitelist", "async,cache,crypto,file,http,https,pipe,rtmp,rtp,tcp,tls,udp,data,ijkinject,ijklongurl,ijksegment,ijkhttphook,ijklivehook,ijktcphook,ijkurlhook,ijkmediadatasource");
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "infbuf", IjkPerformanceSetting.useInfiniteBuffer(realtime) ? 1 : 0);
        applyProbeOptions();
        applyRtspOptions(url);
    }

    private void configureSoftDecodeOptions() {
        if (decode != PlayerEngine.SOFT || IjkPerformanceSetting.getSoftTuneMode() == IjkPerformanceSetting.SOFT_TUNE_OFF) return;
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "fast", 1);
        if (IjkPerformanceSetting.getSoftTuneMode() == IjkPerformanceSetting.SOFT_TUNE_AGGRESSIVE) {
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 32);
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_frame", 8);
        } else {
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 8);
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_frame", 0);
        }
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

    private boolean isRealtimeUrl(String url) {
        String lower = url.toLowerCase(Locale.US);
        return lower.startsWith("rtsp") || lower.startsWith("rtp") || lower.startsWith("udp") || lower.startsWith("rtmp");
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

    private long bufferedPosition(long duration) {
        if (duration == C.TIME_UNSET || duration <= 0) return position();
        return Math.min(duration, duration * bufferingPercent / 100);
    }

    private boolean isPlayingInternal() {
        try {
            return playbackState == Player.STATE_READY && ijk.isPlaying();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void refreshTracks() {
        try {
            List<ITrackInfo> infos = ijk.getTrackInfo();
            if (infos == null || infos.isEmpty()) {
                currentTracks = Tracks.EMPTY;
                return;
            }
            List<Tracks.Group> groups = new java.util.ArrayList<>();
            boolean selectedVideo = false;
            boolean selectedAudio = false;
            boolean selectedText = false;
            int index = 0;
            for (ITrackInfo info : infos) {
                int type = mediaTrackType(info.getTrackType());
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
                TrackGroup group = new TrackGroup("ijk:" + type + ":" + index, format);
                groups.add(new Tracks.Group(group, false, new int[]{C.FORMAT_HANDLED}, new boolean[]{selected}));
            }
            currentTracks = groups.isEmpty() ? Tracks.EMPTY : new Tracks(groups);
            if (SpiderDebug.isEnabled()) SpiderDebug.log("ijk", "tracks refreshed count=%d groups=%d", infos.size(), groups.size());
        } catch (Throwable e) {
            currentTracks = Tracks.EMPTY;
            SpiderDebug.log("ijk", "tracks refresh failed error=%s", e.getMessage());
        }
    }

    private int mediaTrackType(int ijkType) {
        return switch (ijkType) {
            case ITrackInfo.MEDIA_TRACK_TYPE_VIDEO -> C.TRACK_TYPE_VIDEO;
            case ITrackInfo.MEDIA_TRACK_TYPE_AUDIO -> C.TRACK_TYPE_AUDIO;
            case ITrackInfo.MEDIA_TRACK_TYPE_TEXT -> C.TRACK_TYPE_TEXT;
            default -> C.TRACK_TYPE_UNKNOWN;
        };
    }

    private Format buildFormat(ITrackInfo info, int type, int index) {
        String codec = info.getMimeType();
        Format.Builder builder = new Format.Builder()
                .setId(type + ":" + index)
                .setLabel(trackLabel(type, index))
                .setCodecs(TextUtils.isEmpty(codec) ? null : codec)
                .setLanguage(TextUtils.isEmpty(info.getLanguage()) ? null : info.getLanguage())
                .setSampleMimeType(sampleMimeType(type, codec));
        if (type == C.TRACK_TYPE_VIDEO) {
            int width = info.getWidth() > 0 ? info.getWidth() : videoSize.width;
            int height = info.getHeight() > 0 ? info.getHeight() : videoSize.height;
            if (width > 0) builder.setWidth(width);
            if (height > 0) builder.setHeight(height);
            if (info.getFps() > 0) builder.setFrameRate(info.getFps());
            ColorInfo colorInfo = colorInfo(info);
            if (colorInfo != null) builder.setColorInfo(colorInfo);
        } else if (type == C.TRACK_TYPE_AUDIO) {
            if (info.getChannelCount() > 0) builder.setChannelCount(info.getChannelCount());
        }
        int bitrate = info.getBitrate();
        if (bitrate <= 0 && type == C.TRACK_TYPE_VIDEO) bitrate = safeIntBitrate(ijk.getBitRate());
        if (bitrate > 0) builder.setAverageBitrate(bitrate);
        return builder.build();
    }

    @Nullable
    private ColorInfo colorInfo(ITrackInfo info) {
        int colorSpace = colorSpace(info);
        int colorRange = colorRange(info);
        int colorTransfer = colorTransfer(info);
        if (colorSpace == C.LENGTH_UNSET && colorRange == C.LENGTH_UNSET && colorTransfer == C.LENGTH_UNSET) return null;
        ColorInfo.Builder builder = new ColorInfo.Builder();
        if (colorSpace != C.LENGTH_UNSET) builder.setColorSpace(colorSpace);
        if (colorRange != C.LENGTH_UNSET) builder.setColorRange(colorRange);
        if (colorTransfer != C.LENGTH_UNSET) builder.setColorTransfer(colorTransfer);
        return builder.build();
    }

    private int colorSpace(ITrackInfo info) {
        String value = lower(joinColor(info.getColorPrimaries(), info.getColorSpace()));
        if (value.contains("bt2020") || value.contains("bt.2020") || value.contains("2020")) return C.COLOR_SPACE_BT2020;
        if (value.contains("bt709") || value.contains("bt.709") || value.contains("709")) return C.COLOR_SPACE_BT709;
        if (value.contains("bt601") || value.contains("bt.601") || value.contains("601") || value.contains("smpte170m") || value.contains("smpte-170m")) return C.COLOR_SPACE_BT601;
        return C.LENGTH_UNSET;
    }

    private int colorRange(ITrackInfo info) {
        String value = lower(info.getColorRange());
        if (value.contains("jpeg") || value.contains("pc") || value.contains("full")) return C.COLOR_RANGE_FULL;
        if (value.contains("mpeg") || value.contains("tv") || value.contains("limited")) return C.COLOR_RANGE_LIMITED;
        return C.LENGTH_UNSET;
    }

    private int colorTransfer(ITrackInfo info) {
        String value = lower(info.getColorTransfer());
        if (value.contains("smpte2084") || value.contains("st2084") || value.contains("pq")) return C.COLOR_TRANSFER_ST2084;
        if (value.contains("arib-std-b67") || value.contains("hlg")) return C.COLOR_TRANSFER_HLG;
        if (value.contains("iec61966") || value.contains("srgb")) return C.COLOR_TRANSFER_SRGB;
        if (value.contains("linear")) return C.COLOR_TRANSFER_LINEAR;
        if (value.contains("bt709") || value.contains("bt.709") || value.contains("bt601") || value.contains("bt.601") || value.contains("smpte170m") || value.contains("smpte-170m")) return C.COLOR_TRANSFER_SDR;
        return C.LENGTH_UNSET;
    }

    private String joinColor(String first, String second) {
        return (first == null ? "" : first) + " " + (second == null ? "" : second);
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
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

    private int errorCode(int what) {
        return switch (what) {
            case IMediaPlayer.MEDIA_ERROR_IO -> PlaybackException.ERROR_CODE_IO_UNSPECIFIED;
            case IMediaPlayer.MEDIA_ERROR_MALFORMED -> PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED;
            case IMediaPlayer.MEDIA_ERROR_UNSUPPORTED -> PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED;
            case IMediaPlayer.MEDIA_ERROR_TIMED_OUT -> PlaybackException.ERROR_CODE_TIMEOUT;
            default -> PlaybackException.ERROR_CODE_UNSPECIFIED;
        };
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
