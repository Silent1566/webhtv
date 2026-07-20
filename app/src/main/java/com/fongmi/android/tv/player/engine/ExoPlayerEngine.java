package com.fongmi.android.tv.player.engine;

import androidx.media3.common.C;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.MediaEdition;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.player.PlaybackTrace;
import com.fongmi.android.tv.player.exo.ErrorMsgProvider;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.player.exo.PlaybackAnalyticsListener;
import com.fongmi.android.tv.player.exo.PreCache;
import com.fongmi.android.tv.player.exo.TrackUtil;
import com.fongmi.android.tv.setting.ExoPerformanceSetting;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ExoPlayerEngine implements PlayerEngine {

    private final ErrorMsgProvider provider;
    private final PreCache preCache;
    private final Set<String> attemptedFormats;
    private PlaySpec spec;
    private String activeFormat;
    private ExoPlayer player;
    private int decode;
    private boolean playWhenReady;

    public ExoPlayerEngine(int decode, Player.Listener listener) {
        this.player = ExoUtil.buildPlayer(decode, listener);
        this.provider = new ErrorMsgProvider();
        this.preCache = new PreCache();
        this.attemptedFormats = new HashSet<>();
        this.decode = decode;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void release() {
        preCache.release();
        PlaybackAnalyticsListener.finishSession(player.getCurrentPosition());
        player.release();
    }

    @Override
    public Player rebuild(Player.Listener listener) {
        preCache.stop("engine-rebuild");
        PlaybackAnalyticsListener.finishSession(player.getCurrentPosition());
        player.release();
        PlaybackTrace.log("player-engine", getPlaybackTraceId(), "rebuild decode=%d", decode);
        return player = ExoUtil.buildPlayer(decode, listener);
    }

    @Override
    public boolean isRepeatOne() {
        return player.getRepeatMode() == Player.REPEAT_MODE_ONE;
    }

    @Override
    public void setRepeatOne(boolean repeat) {
        player.setRepeatMode(repeat ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
    }

    @Override
    public int getDecode() {
        return decode;
    }

    @Override
    public void setDecode(int decode) {
        this.decode = decode;
    }

    @Override
    public boolean isHard() {
        return decode == HARD;
    }

    @Override
    public String getDecodeText() {
        return ResUtil.getStringArray(R.array.select_decode)[decode];
    }

    @Override
    public void start(PlaySpec spec) {
        start(spec, true);
    }

    @Override
    public void start(PlaySpec spec, boolean playWhenReady) {
        this.spec = spec;
        this.activeFormat = spec.getFormat();
        this.playWhenReady = playWhenReady;
        resetAttemptedFormats();
        PlaybackTrace.log("player-engine", getPlaybackTraceId(), "start decode=%d format=%s play=%s headers=%s urlLen=%d", decode, spec.getFormat(), playWhenReady, spec.getHeaders() == null ? 0 : spec.getHeaders().size(), spec.getUrl() == null ? 0 : spec.getUrl().length());
        startInternal(C.TIME_UNSET, playWhenReady);
    }

    @Override
    public void start(PlaySpec spec, long position, boolean playWhenReady) {
        this.spec = spec;
        this.activeFormat = spec.getFormat();
        this.playWhenReady = playWhenReady;
        resetAttemptedFormats();
        PlaybackTrace.log("player-engine", getPlaybackTraceId(), "start decode=%d format=%s position=%d play=%s headers=%s urlLen=%d", decode, spec.getFormat(), position, playWhenReady, spec.getHeaders() == null ? 0 : spec.getHeaders().size(), spec.getUrl() == null ? 0 : spec.getUrl().length());
        startInternal(position, playWhenReady);
    }

    @Override
    public void restart(PlaySpec spec, long position, boolean playWhenReady) {
        this.spec = spec;
        this.activeFormat = spec.getFormat();
        this.playWhenReady = playWhenReady;
        resetAttemptedFormats();
        PlaybackTrace.log("player-engine", getPlaybackTraceId(), "restart decode=%d format=%s position=%d play=%s headers=%s urlLen=%d", decode, spec.getFormat(), position, playWhenReady, spec.getHeaders() == null ? 0 : spec.getHeaders().size(), spec.getUrl() == null ? 0 : spec.getUrl().length());
        preCache.stop("engine-restart");
        player.stop();
        startInternal(position, playWhenReady);
    }

    @Override
    public void stop() {
        preCache.stop("player-stop");
        PlaybackAnalyticsListener.finishSession(player.getCurrentPosition());
        player.stop();
    }

    @Override
    public void setMetadata(MediaMetadata data) {
        MediaItem current = player.getCurrentMediaItem();
        if (current != null) player.replaceMediaItem(player.getCurrentMediaItemIndex(), current.buildUpon().setMediaMetadata(data).build());
    }

    @Override
    public boolean isLive() {
        return player.getDuration() < TimeUnit.MINUTES.toMillis(1) || player.isCurrentMediaItemLive();
    }

    @Override
    public boolean isVod() {
        return player.getDuration() > TimeUnit.MINUTES.toMillis(1) && !player.isCurrentMediaItemLive();
    }

    @Override
    public void setTrack(List<Track> tracks) {
        TrackUtil.setTrackSelection(player, tracks);
    }

    @Override
    public void resetTrack() {
        TrackUtil.reset(player);
    }

    @Override
public void resetTrack(int type) {
        TrackUtil.reset(player, type);
    }

    @Override
    public void restoreVideoTrack() {
        TrackUtil.enable(player, C.TRACK_TYPE_VIDEO);
    }

    @Override
    public boolean haveTrack(int type) {
        return TrackUtil.count(getCurrentTracks(), type) > 0;
    }

    @Override
    public Tracks getCurrentTracks() {
        return player.getCurrentTracks();
    }

    @Override
    public boolean supportsVideoEffects() {
        return true;
    }

    @Override
    public void setVideoEffects(List<Effect> effects) {
        player.setVideoEffects(effects);
    }

    @Override
    public Format getVideoFormat() {
        return player.getVideoFormat();
    }

    @Override
    public long getDroppedFrames() {
        return PlaybackAnalyticsListener.getSnapshot().droppedFrames();
    }

    @Override
    public String getPlaybackTraceId() {
        return spec == null ? PlaybackTrace.NONE : spec.getPlaybackTraceId();
    }

    @Override
    public boolean haveTitle() {
        return !player.getCurrentMediaEditions().isEmpty();
    }

    @Override
    public List<MediaEdition> getCurrentMediaEditions() {
        return player.getCurrentMediaEditions();
    }

    @Override
    public boolean selectEdition(MediaEdition edition) {
        return player.selectEdition(edition);
    }

    @Override
    public String getErrorMessage(PlaybackException e) {
        return provider.get(e);
    }

    @Override
    public ErrorAction handleError(PlaybackException e) {
        ErrorAction action = switch (e.errorCode) {
            case PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> seekToDefaultPosition();
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED, PlaybackException.ERROR_CODE_DECODING_FAILED -> ErrorAction.DECODE;
            case PlaybackException.ERROR_CODE_IO_UNSPECIFIED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> retryFormat(e.errorCode);
            default -> ErrorAction.FATAL;
        };
        PlaybackTrace.log("player-engine", getPlaybackTraceId(), "handleError code=%d action=%s decode=%d format=%s originalFormat=%s", e.errorCode, action, decode, activeFormat, spec == null ? null : spec.getFormat());
        return action;
    }

    private void startInternal() {
        startInternal(C.TIME_UNSET, true);
    }

    private void startInternal(long position) {
        startInternal(position, true);
    }

    private void startInternal(long position, boolean playWhenReady) {
        this.playWhenReady = playWhenReady;
        PlaybackAnalyticsListener.finishSession(player.getCurrentPosition());
        PlaybackAnalyticsListener.beginSession(spec.getPlaybackTraceId());
        PlaybackTrace.log("player-engine", getPlaybackTraceId(), "prepare position=%d decode=%d format=%s originalFormat=%s play=%s", position, decode, activeFormat, spec.getFormat(), playWhenReady);
        ExoPerformanceSetting.beginAutoSession();
        if (!playWhenReady) player.pause();
        MediaItem item = ExoUtil.getMediaItem(spec.copyWithFormat(activeFormat), decode);
        player.setMediaItem(item, position);
        preCache.start(player, item, spec.getPlaybackTraceId(), spec.getPlaybackRoute());
        player.prepare();
        if (playWhenReady) player.play();
    }

    private ErrorAction seekToDefaultPosition() {
        player.seekToDefaultPosition();
        player.prepare();
        return ErrorAction.RECOVERED;
    }

    private ErrorAction retryFormat(int errorCode) {
        String format = ExoUtil.getMimeType(errorCode);
        String key = formatKey(format);
        if (format == null || attemptedFormats.contains(key)) {
            PlaybackTrace.log("player-engine", getPlaybackTraceId(), "retryFormat stopped errorCode=%d attempted=%s", errorCode, attemptedFormats);
            return ErrorAction.FATAL;
        }
        attemptedFormats.add(key);
        activeFormat = format;
        PlaybackTrace.log("player-engine", getPlaybackTraceId(), "retryFormat errorCode=%d newFormat=%s position=%d", errorCode, format, player.getCurrentPosition());
        startInternal(player.getCurrentPosition());
        return ErrorAction.RECOVERED;
    }

    private void resetAttemptedFormats() {
        attemptedFormats.clear();
        attemptedFormats.add(formatKey(activeFormat));
    }

    private String formatKey(String format) {
        return format == null || format.isBlank() ? "<auto>" : format.toLowerCase(Locale.ROOT);
    }
}
