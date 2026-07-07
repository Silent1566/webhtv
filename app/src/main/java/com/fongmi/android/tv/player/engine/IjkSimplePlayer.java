package com.fongmi.android.tv.player.engine;

import android.media.AudioManager;
import android.net.Uri;
import android.os.Looper;
import android.os.PowerManager;
import android.text.TextUtils;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.SimpleBasePlayer;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.UnstableApi;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.IjkTimedText;

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
    private final Runnable stateRefreshRunnable;
    private MediaItem mediaItem;
    private SurfaceHolder surfaceHolder;
    private Surface surface;
    private Object videoOutput;
    private PlaybackParameters playbackParameters;
    private PlaybackException playerError;
    private VideoSize videoSize;
    private IjkSubtitleTrack subtitleTrack;
    private CueGroup currentCues;
    private Future<?> subtitleLoad;
    private int playbackState;
    private int bufferingPercent;
    private int decode;
    private int subtitleSerial;
    private long pendingSeekPositionMs;
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
        stateRefreshRunnable = this::refreshPlaybackState;
        playbackParameters = PlaybackParameters.DEFAULT;
        videoSize = VideoSize.UNKNOWN;
        subtitleTrack = IjkSubtitleTrack.EMPTY;
        currentCues = CueGroup.EMPTY_TIME_ZERO;
        playbackState = Player.STATE_IDLE;
        pendingSeekPositionMs = C.TIME_UNSET;
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
                .setTracks(Tracks.EMPTY)
                .build();
    }

    void setDecode(int decode) {
        this.decode = decode;
    }

    @Override
    protected ListenableFuture<?> handleSetMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
        clearSubtitles();
        mediaItem = mediaItems.isEmpty() ? null : mediaItems.get(0);
        pendingSeekPositionMs = mediaItem != null && startPositionMs > 0 ? startPositionMs : C.TIME_UNSET;
        playbackState = mediaItem == null ? Player.STATE_IDLE : Player.STATE_IDLE;
        loading = false;
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
        if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
            ijk.seekTo(positionMs);
        } else {
            pendingSeekPositionMs = positionMs;
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
        playbackState = Player.STATE_ENDED;
        loading = false;
        currentCues = CueGroup.EMPTY_TIME_ZERO;
        stopStateRefresh();
        invalidateState();
    }

    @Override
    public boolean onError(IMediaPlayer mp, int what, int extra) {
        playbackState = Player.STATE_IDLE;
        loading = false;
        stopStateRefresh();
        clearSubtitles();
        playerError = new PlaybackException("IJK error: " + what + ", " + extra, null, errorCode(what));
        SpiderDebug.log("ijk", "error what=%d extra=%d mapped=%d decode=%d state=%d loading=%s uri=%s", what, extra, playerError.errorCode, decode, playbackState, loading, summarizeUri());
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
            ijk.setWakeMode(App.get(), PowerManager.PARTIAL_WAKE_LOCK);
            configureOptions(mediaItem.localConfiguration.uri);
            bindVideoOutput();
            ijk.setDataSource(App.get(), mediaItem.localConfiguration.uri, ExoUtil.extractHeaders(mediaItem));
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
        loading = false;
        bufferingPercent = 0;
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

    private void configureOptions(Uri uri) {
        String url = uri.toString();
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48);
        if (decode == PlayerEngine.SOFT) {
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "fast", 1);
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_frame", 8);
        }
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_timeout", -1);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "fastseek");
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 0);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 15728640);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", decode);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-hevc", decode);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-all-videos", decode);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", decode);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", decode);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", IjkMediaPlayer.SDL_FCC_RV32);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "reconnect", 1);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "soundtouch", 1);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "subtitle", 1);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "video-pictq-size", 3);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "protocol_whitelist", "async,cache,crypto,file,http,https,pipe,rtmp,rtp,tcp,tls,udp,data,ijkinject,ijklongurl,ijksegment,ijkhttphook,ijklivehook,ijktcphook,ijkurlhook,ijkmediadatasource");
        if (url.contains("rtsp") || url.contains("udp") || url.contains("rtp")) {
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "infbuf", 1);
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_transport", "tcp");
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_flags", "prefer_tcp");
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 512000);
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 2000000);
        }
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
            return Math.max(0, ijk.getCurrentPosition());
        } catch (Throwable ignored) {
            return 0;
        }
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
