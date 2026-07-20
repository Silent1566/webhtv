package com.fongmi.android.tv.player.exo;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.source.preload.PreCacheHelper;

import com.fongmi.android.tv.player.PlaybackRoute;
import com.fongmi.android.tv.player.PlaybackTrace;
import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.setting.PlayerSetting;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PreCache implements Player.Listener {

    private static final long TICK_MS = 5000;
    private static final long MIN_STEP_MS = 5000;
    private static final long MAX_STEP_MS = 30000;
    private static final long BUFFER_GAP_MS = 1250;
    private static final int STEP_DIV = 4;

    private final PreloadLifecycleTracker lifecycle = new PreloadLifecycleTracker();
    private final PreCacheHelper.Listener preCacheListener = new PreCacheHelper.Listener() {
        @Override
        public void onPrepared(MediaItem originalMediaItem, MediaItem preparedMediaItem) {
            long sessionId = lifecycle.sessionId();
            if (sessionId > 0) PlaybackTrace.log("exo-preload", playbackTraceId, "event=helper-prepared session=%d generation=%d", sessionId, generation);
        }

        @Override
        public void onPreCacheCompleted(MediaItem mediaItem) {
            finishTask(PreloadLifecycleTracker.TaskEvent.Outcome.COMPLETED, "completed", null);
        }

        @Override
        public void onPrepareError(MediaItem mediaItem, IOException exception) {
            finishTask(PreloadLifecycleTracker.TaskEvent.Outcome.PREPARE_ERROR, "prepare-error", exception);
        }

        @Override
        public void onDownloadError(MediaItem mediaItem, IOException exception) {
            finishTask(PreloadLifecycleTracker.TaskEvent.Outcome.DOWNLOAD_ERROR, "download-error", exception);
        }
    };
    private ThreadPoolExecutor executor;
    private PreCacheHelper helper;
    private Handler handler;
    private HandlerThread worker;
    private Player player;
    private PlaybackRoute route;
    private PlaybackRoute.Resolution routeResolution = PlaybackRoute.resolve(null);
    private volatile String playbackTraceId = PlaybackTrace.NONE;
    private Runnable scheduledTask;
    private int threads;
    private volatile long generation;
    private long lastStartMs;
    private long seekStartMs;
    private boolean playable;
    private BufferGate bufferGate;
    private AutoPreloadPolicy autoPolicy;

    public void start(Player player, MediaItem mediaItem, String playbackTraceId, PlaybackRoute.Resolution routeResolution) {
        stop("replace-media");
        this.playbackTraceId = PlaybackTrace.normalize(playbackTraceId);
        PriorityTaskDataSource.resetDiagnostics();
        if (!PreloadSetting.isPreload(PlayerSetting.EXO) || !canPreCache(mediaItem)) return;
        this.player = player;
        this.handler = new Handler(player.getApplicationLooper());
        this.routeResolution = routeResolution == null ? PlaybackRoute.resolve(mediaItem.localConfiguration.uri.toString()) : routeResolution;
        this.route = this.routeResolution.route();
        this.autoPolicy = PlaybackPerformanceSetting.isAuto(PlayerSetting.EXO) ? new AutoPreloadPolicy() : null;
        this.helper = createHelper(mediaItem);
        clearSeek();
        lastStartMs = C.TIME_UNSET;
        playable = false;
        bufferGate = BufferGate.FIRST_FRAME;
        this.player.addListener(this);
        logSession(lifecycle.beginSession(), "generation=%d %s configuredThreads=%d effectiveThreads=%d durationTargetMs=%d cacheCapacityBytes=%d", generation, this.routeResolution.logSummary(), PreloadSetting.getPreloadThreads(PlayerSetting.EXO), threads, PreloadSetting.getPreloadDurationMs(PlayerSetting.EXO), MediaSourceFactory.getCacheCapacityBytes());
        transition(PreloadLifecycleTracker.State.WAIT_FIRST_FRAME, "session-start", "generation=%d position=%d buffered=%d loading=%s", generation, player.getCurrentPosition(), player.getTotalBufferedDuration(), player.isLoading());
        check();
    }

    public void stop() {
        stop("player-stop");
    }

    public void stop(String reason) {
        boolean active = helper != null || player != null;
        PriorityTaskDataSource.DiagnosticSnapshot priority = active ? PriorityTaskDataSource.getDiagnosticSnapshot() : null;
        long stoppedGeneration = generation;
        stopCurrentTask(reason);
        if (active) {
            logSession(lifecycle.endSession(reason), "generation=%d nextGeneration=%d waitCount=%d waitTotalMs=%d", stoppedGeneration, generation, priority.waitCount(), priority.waitTotalMs());
        }
        if (player != null) player.removeListener(this);
        if (helper != null) helper.release(false);
        handler = null;
        helper = null;
        player = null;
        route = null;
        routeResolution = PlaybackRoute.resolve(null);
        playbackTraceId = PlaybackTrace.NONE;
        autoPolicy = null;
        clearSeek();
        lastStartMs = C.TIME_UNSET;
        playable = false;
        bufferGate = BufferGate.FIRST_FRAME;
    }

    public void release() {
        stop("release");
        ThreadPoolExecutor retiringExecutor = executor;
        HandlerThread retiringWorker = worker;
        executor = null;
        worker = null;
        threads = 0;
        if (retiringWorker == null) {
            shutdownExecutor(retiringExecutor);
            return;
        }
        // PreCacheHelper.release() posts cancellation to this same looper.
        // Queue resource teardown behind it so SegmentDownloader cannot submit
        // work to an executor which has already entered SHUTTING_DOWN.
        new Handler(retiringWorker.getLooper()).post(() -> {
            shutdownExecutor(retiringExecutor);
            retiringWorker.quitSafely();
        });
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        if (state == Player.STATE_BUFFERING) {
            if (autoPolicy != null) autoPolicy.disrupt(SystemClock.elapsedRealtime());
            if (playable) bufferGate = BufferGate.RECOVERY;
            transition(PreloadLifecycleTracker.State.CANCELLED_BUFFERING, "buffering", "generation=%d position=%d buffered=%d loading=%s", generation, player.getCurrentPosition(), player.getTotalBufferedDuration(), player.isLoading());
            stopCurrentTask("buffering");
        } else if (state == Player.STATE_READY && playable) {
            check();
        } else if (isStopped(state)) {
            cancel();
        }
    }

    @Override
    public void onRenderedFirstFrame() {
        markPlayable();
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        if (!isPlaying || playable || player == null) return;
        if (!player.getCurrentTracks().containsType(C.TRACK_TYPE_VIDEO) && player.getCurrentTracks().containsType(C.TRACK_TYPE_AUDIO)) {
            markPlayable();
        }
    }

    @Override
    public void onIsLoadingChanged(boolean isLoading) {
        if (playable && bufferGate != BufferGate.OPEN) check();
    }

    @Override
    public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
        if (!isSeek(reason) || helper == null) return;
        transition(PreloadLifecycleTracker.State.CANCELLED_SEEK, "seek", "generation=%d oldPosition=%d newPosition=%d", generation, oldPosition.positionMs, newPosition.positionMs);
        if (autoPolicy != null) autoPolicy.disrupt(SystemClock.elapsedRealtime());
        stopCurrentTask("seek");
        markSeek(newPosition.positionMs);
        if (playable) bufferGate = BufferGate.RECOVERY;
        check();
    }

    private void check() {
        check(generation);
    }

    private void check(long expectedGeneration) {
        if (expectedGeneration != generation) {
            PlaybackTrace.log("exo-preload", playbackTraceId, "event=stale-skip session=%d expectedGeneration=%d currentGeneration=%d", lifecycle.sessionId(), expectedGeneration, generation);
            return;
        }
        cancel();
        if (update()) schedule(expectedGeneration);
    }

    private boolean update() {
        if (helper == null || player == null) return false;
        if (!PreloadSetting.isPreload(PlayerSetting.EXO)) {
            stop("disabled");
            return false;
        }
        int state = player.getPlaybackState();
        if (isStopped(state)) return false;
        if (state != Player.STATE_READY) return true;
        if (!playable) {
            transition(PreloadLifecycleTracker.State.WAIT_FIRST_FRAME, "first-frame", "generation=%d position=%d buffered=%d loading=%s", generation, player.getCurrentPosition(), player.getTotalBufferedDuration(), player.isLoading());
            return true;
        }
        if (bufferGate != BufferGate.OPEN) {
            SafeBufferStatus status = getSafeBufferStatus();
            if (!status.safe()) {
                PreloadLifecycleTracker.State waitState = status.recovery() ? PreloadLifecycleTracker.State.WAIT_RECOVERY_BUFFER : PreloadLifecycleTracker.State.WAIT_INITIAL_BUFFER;
                transition(waitState, status.recovery() ? "recovery-watermark" : "initial-watermark", "generation=%d recovery=%s requiredMs=%d bufferedMs=%d loading=%s bitrate=%d effectiveCapacityBytes=%d capacityDurationMs=%d", generation, status.recovery(), status.requiredMs(), status.bufferedMs(), status.loading(), status.bitrate(), status.effectiveCapacityBytes(), status.capacityDurationMs());
                return true;
            }
        }
        bufferGate = BufferGate.OPEN;
        if (player.isCurrentMediaItemLive()) {
            transition(PreloadLifecycleTracker.State.SKIPPED, "live", "generation=%d", generation);
            stop("live");
            return false;
        }
        AutoPreloadPolicy.Decision autoDecision = getAutoDecision();
        if (autoDecision != null && !autoDecision.enabled()) {
            transition(PreloadLifecycleTracker.State.PAUSED_AUTO, "auto-" + autoDecision.mode(), "generation=%d route=%s mode=%s position=%d buffered=%d bandwidth=%d bitrate=%d", generation, route, autoDecision.mode(), player.getCurrentPosition(), player.getTotalBufferedDuration(), PlaybackAnalyticsListener.getSnapshot().bandwidthEstimate(), getSelectedBitrate());
            return true;
        }
        if (autoDecision != null) setEffectiveThreads(autoDecision.threads());
        long startMs = getStart();
        long lengthMs = getLength(startMs, autoDecision == null ? PreloadSetting.getPreloadDurationMs(PlayerSetting.EXO) : autoDecision.durationMs());
        if (lengthMs <= 0) {
            transition(PreloadLifecycleTracker.State.NO_RANGE, "no-range", "generation=%d startMs=%d durationMs=%d", generation, startMs, player.getDuration());
            clearSeek();
            return true;
        }
        if (!shouldPreCache(startMs)) return true;
        long bitrate = getSelectedBitrate();
        long estimatedBytes = ExoPlaybackDiagnostics.estimateBytes(bitrate, lengthMs);
        PriorityTaskDataSource.DiagnosticSnapshot priority = PriorityTaskDataSource.getDiagnosticSnapshot();
        transition(PreloadLifecycleTracker.State.PRELOADING, "task-start", "generation=%d route=%s threads=%d", generation, route, threads);
        for (PreloadLifecycleTracker.TaskEvent event : lifecycle.startTask(generation, startMs, lengthMs)) {
            if (event.type() == PreloadLifecycleTracker.TaskEvent.Type.END) {
                logTask(event, "reason=next-range");
            } else {
                logTask(event, "estimatedBytes=%d bitrate=%d position=%d buffered=%d loading=%s waitCount=%d waitTotalMs=%d", estimatedBytes, bitrate, player.getCurrentPosition(), player.getTotalBufferedDuration(), player.isLoading(), priority.waitCount(), priority.waitTotalMs());
            }
        }
        try {
            helper.preCache(startMs, lengthMs);
        } catch (RuntimeException | Error e) {
            finishTask(PreloadLifecycleTracker.TaskEvent.Outcome.START_ERROR, "start-error", e);
            throw e;
        }
        lastStartMs = startMs;
        clearSeek();
        return true;
    }

    private void schedule(long expectedGeneration) {
        if (handler == null || expectedGeneration != generation) return;
        scheduledTask = () -> check(expectedGeneration);
        handler.postDelayed(scheduledTask, TICK_MS);
    }

    private void cancel() {
        if (handler != null && scheduledTask != null) handler.removeCallbacks(scheduledTask);
        scheduledTask = null;
    }

    private void stopCurrentTask(String reason) {
        logTask(lifecycle.endTask(PreloadLifecycleTracker.TaskEvent.Outcome.CANCELLED), "reason=%s", reason);
        generation++;
        cancel();
        if (helper != null) helper.stop();
        lastStartMs = C.TIME_UNSET;
    }

    private SafeBufferStatus getSafeBufferStatus() {
        long durationMs = player.getDuration();
        long positionMs = player.getCurrentPosition();
        long remainingMs = durationMs > 0 && positionMs >= 0 ? Math.max(0, durationMs - positionMs) : C.TIME_UNSET;
        boolean recovery = bufferGate == BufferGate.RECOVERY;
        long bitrate = getSelectedBitrate();
        int effectiveCapacityBytes = ExoUtil.getBufferBudget().effectiveTargetBytes();
        long requiredMs = PreCachePolicy.safeBufferTargetMs(recovery, remainingMs, bitrate, effectiveCapacityBytes);
        long bufferedMs = player.getTotalBufferedDuration();
        boolean loading = player.isLoading();
        boolean safe = PreCachePolicy.hasSafeBuffer(bufferedMs, loading, requiredMs, recovery);
        return new SafeBufferStatus(safe, recovery, requiredMs, bufferedMs, loading, bitrate, effectiveCapacityBytes, ExoPlaybackDiagnostics.capacityDurationMs(effectiveCapacityBytes, bitrate));
    }

    private long getSelectedBitrate() {
        Format video = TrackUtil.selectedFormat(player.getCurrentTracks(), C.TRACK_TYPE_VIDEO);
        Format audio = TrackUtil.selectedFormat(player.getCurrentTracks(), C.TRACK_TYPE_AUDIO);
        return ExoPlaybackDiagnostics.combinedBitrate(video, audio);
    }

    private void markPlayable() {
        if (!playable) {
            playable = true;
            bufferGate = BufferGate.INITIAL;
        }
        check();
    }

    private PreCacheHelper createHelper(MediaItem mediaItem) {
        DataSource.Factory upstreamFactory = MediaSourceFactory.createUpstreamDataSourceFactory(ExoUtil.extractHeaders(mediaItem));
        return new PreCacheHelper.Factory(MediaSourceFactory.getCache(), upstreamFactory, ExoUtil.buildRenderersFactory(), getWorker().getLooper())
                .setDownloadExecutor(getExecutor())
                .setListener(preCacheListener)
                .create(mediaItem);
    }

    private boolean canPreCache(MediaItem mediaItem) {
        if (mediaItem == null || mediaItem.localConfiguration == null) return false;
        MediaItem.LocalConfiguration local = mediaItem.localConfiguration;
        if (MediaSourceFactory.isHlsUrl(local.uri.toString())) return false;
        return canPreCache(local.uri.getScheme(), local.uri.toString());
    }

    static boolean canPreCache(String scheme, String url) {
        return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && !MediaSourceFactory.isConcatenatingUrl(url) && !MediaSourceFactory.isLocalProxyUrl(url);
    }

    private long getStart() {
        if (hasSeek()) return Math.max(0, seekStartMs);
        long bufferedPositionMs = player.getBufferedPosition();
        if (bufferedPositionMs < 0) return Math.max(0, player.getCurrentPosition());
        return bufferedPositionMs > Long.MAX_VALUE - BUFFER_GAP_MS ? bufferedPositionMs : bufferedPositionMs + BUFFER_GAP_MS;
    }

    private boolean shouldPreCache(long startMs) {
        if (hasSeek()) return true;
        if (lastStartMs == C.TIME_UNSET) return true;
        return Math.abs(startMs - lastStartMs) >= getStep();
    }

    private boolean isStopped(int state) {
        return state == Player.STATE_ENDED || state == Player.STATE_IDLE;
    }

    private long getLength(long startMs, long durationTargetMs) {
        long durationMs = player.getDuration();
        if (durationMs <= 0) return 0;
        long remainingMs = Math.max(0, durationMs - startMs);
        return PreCachePolicy.preloadLengthMs(durationTargetMs, remainingMs, getSelectedBitrate(), MediaSourceFactory.getCacheCapacityBytes());
    }

    private long getStep() {
        return Math.clamp(PreloadSetting.getPreloadDurationMs(PlayerSetting.EXO) / STEP_DIV, MIN_STEP_MS, MAX_STEP_MS);
    }

    private void markSeek(long startMs) {
        seekStartMs = startMs;
    }

    private void clearSeek() {
        seekStartMs = C.TIME_UNSET;
    }

    private boolean hasSeek() {
        return seekStartMs != C.TIME_UNSET;
    }

    private Executor getExecutor() {
        int requested = PreloadSetting.getPreloadThreads(PlayerSetting.EXO);
        int count = route == null ? requested : route.effectivePreloadThreads(requested);
        if (autoPolicy != null) count = route == null ? AutoPreloadPolicy.NORMAL_THREADS : route.effectivePreloadThreads(AutoPreloadPolicy.NORMAL_THREADS);
        if (executor != null) {
            setEffectiveThreads(count);
            return executor;
        }
        retireExecutor();
        threads = count;
        return executor = new ThreadPoolExecutor(count, count, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    }

    private AutoPreloadPolicy.Decision getAutoDecision() {
        if (autoPolicy == null) return null;
        PlaybackAnalyticsListener.Snapshot snapshot = PlaybackAnalyticsListener.getSnapshot();
        return autoPolicy.evaluate(SystemClock.elapsedRealtime(), route, player.getTotalBufferedDuration(), getSelectedBitrate(), snapshot.bandwidthEstimate(), snapshot.rebufferCount(), player.isLoading());
    }

    private void setEffectiveThreads(int requested) {
        if (executor == null) return;
        int count = route == null ? requested : route.effectivePreloadThreads(requested);
        if (count == threads) return;
        if (count > threads) {
            executor.setMaximumPoolSize(count);
            executor.setCorePoolSize(count);
        } else {
            executor.setCorePoolSize(count);
            executor.setMaximumPoolSize(count);
        }
        threads = count;
        long sessionId = lifecycle.sessionId();
        if (sessionId > 0) PlaybackTrace.log("exo-preload", playbackTraceId, "event=threads session=%d generation=%d threads=%d route=%s", sessionId, generation, threads, route);
    }

    private void retireExecutor() {
        if (executor == null) return;
        ThreadPoolExecutor retiringExecutor = executor;
        executor = null;
        if (worker == null) shutdownExecutor(retiringExecutor);
        else new Handler(worker.getLooper()).post(() -> shutdownExecutor(retiringExecutor));
    }

    private void shutdownExecutor(ThreadPoolExecutor target) {
        if (target == null) return;
        target.shutdownNow();
    }

    private HandlerThread getWorker() {
        if (worker != null) return worker;
        worker = new HandlerThread("CurrentMediaPreCache");
        worker.start();
        return worker;
    }

    private boolean isSeek(int reason) {
        return reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT;
    }

    private void transition(PreloadLifecycleTracker.State state, String reason, String format, Object... args) {
        PreloadLifecycleTracker.StateEvent event = lifecycle.transition(state, reason);
        if (event == null) return;
        PlaybackTrace.log("exo-preload", playbackTraceId, "event=state session=%d from=%s to=%s reason=%s %s", event.sessionId(), event.from().label(), event.to().label(), event.reason(), detail(format, args));
    }

    private void logSession(PreloadLifecycleTracker.SessionEvent event, String format, Object... args) {
        if (event == null) return;
        String type = event.type() == PreloadLifecycleTracker.SessionEvent.Type.START ? "session-start" : "session-end";
        PlaybackTrace.log("exo-preload", playbackTraceId, "event=%s session=%d reason=%s %s", type, event.sessionId(), event.reason(), detail(format, args));
    }

    private void logTask(PreloadLifecycleTracker.TaskEvent event, String format, Object... args) {
        if (event == null) return;
        String type = event.type() == PreloadLifecycleTracker.TaskEvent.Type.START ? "task-start" : "task-end";
        String outcome = event.outcome() == null ? "-" : event.outcome().label();
        PlaybackTrace.log("exo-preload", playbackTraceId, "event=%s session=%d task=%d generation=%d outcome=%s startMs=%d lengthMs=%d %s", type, event.sessionId(), event.taskId(), event.generation(), outcome, event.startMs(), event.lengthMs(), detail(format, args));
    }

    private void finishTask(PreloadLifecycleTracker.TaskEvent.Outcome outcome, String reason, Throwable error) {
        PreloadLifecycleTracker.TaskEvent event = lifecycle.endTask(outcome);
        if (event == null) return;
        if (error == null) logTask(event, "reason=%s", reason);
        else logTask(event, "reason=%s error=%s", reason, error.getClass().getSimpleName());
        PreloadLifecycleTracker.State state = outcome == PreloadLifecycleTracker.TaskEvent.Outcome.COMPLETED ? PreloadLifecycleTracker.State.WAIT_NEXT_RANGE : PreloadLifecycleTracker.State.WAIT_RETRY;
        transition(state, reason, "generation=%d task=%d", event.generation(), event.taskId());
    }

    private static String detail(String format, Object... args) {
        if (format == null || format.isBlank()) return "";
        try {
            return String.format(Locale.US, format, args);
        } catch (Throwable ignored) {
            return "detail-format-error";
        }
    }

    private record SafeBufferStatus(boolean safe, boolean recovery, long requiredMs, long bufferedMs, boolean loading, long bitrate, int effectiveCapacityBytes, long capacityDurationMs) {
    }

    private enum BufferGate {
        FIRST_FRAME,
        INITIAL,
        RECOVERY,
        OPEN
    }

}
