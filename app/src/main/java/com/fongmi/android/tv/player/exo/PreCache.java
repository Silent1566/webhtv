package com.fongmi.android.tv.player.exo;

import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.source.preload.PreCacheHelper;

import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.setting.PlayerSetting;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PreCache implements Player.Listener {

    private static final long TICK_MS = 5000;
    private static final long MIN_STEP_MS = 5000;
    private static final long MAX_STEP_MS = 30000;
    private static final int STEP_DIV = 4;

    private final Runnable task;

    private ExecutorService executor;
    private PreCacheHelper helper;
    private Handler handler;
    private HandlerThread worker;
    private Player player;
    private int threads;
    private long lastStartMs;
    private long seekStartMs;

    public PreCache() {
        task = this::check;
    }

    public void start(Player player, MediaItem mediaItem) {
        stop();
        if (!PreloadSetting.isPreload(PlayerSetting.EXO) || !canPreCache(mediaItem)) return;
        this.player = player;
        this.handler = new Handler(player.getApplicationLooper());
        this.helper = createHelper(mediaItem);
        this.player.addListener(this);
        clearSeek();
        lastStartMs = C.TIME_UNSET;
        check();
    }

    public void stop() {
        cancel();
        if (player != null) player.removeListener(this);
        if (helper != null) helper.release(false);
        handler = null;
        helper = null;
        player = null;
        clearSeek();
        lastStartMs = C.TIME_UNSET;
    }

    public void release() {
        stop();
        ExecutorService retiringExecutor = executor;
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
        if (state == Player.STATE_READY) check();
        else if (isStopped(state)) cancel();
    }

    @Override
    public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
        if (!isSeek(reason) || helper == null) return;
        helper.stop();
        markSeek(newPosition.positionMs);
        check();
    }

    private void check() {
        cancel();
        if (update()) schedule();
    }

    private boolean update() {
        if (helper == null || player == null) return false;
        if (!PreloadSetting.isPreload(PlayerSetting.EXO)) {
            stop();
            return false;
        }
        int state = player.getPlaybackState();
        if (isStopped(state)) return false;
        if (state != Player.STATE_READY) return true;
        if (player.isCurrentMediaItemLive()) {
            stop();
            return false;
        }
        long startMs = getStart();
        long lengthMs = getLength(startMs);
        if (lengthMs <= 0) {
            clearSeek();
            return true;
        }
        if (!shouldPreCache(startMs)) return true;
        helper.preCache(startMs, lengthMs);
        lastStartMs = startMs;
        clearSeek();
        return true;
    }

    private void schedule() {
        if (handler != null) handler.postDelayed(task, TICK_MS);
    }

    private void cancel() {
        if (handler != null) handler.removeCallbacks(task);
    }

    private PreCacheHelper createHelper(MediaItem mediaItem) {
        DataSource.Factory upstreamFactory = MediaSourceFactory.createUpstreamDataSourceFactory(ExoUtil.extractHeaders(mediaItem));
        return new PreCacheHelper.Factory(MediaSourceFactory.getCache(), upstreamFactory, ExoUtil.buildRenderersFactory(), getWorker().getLooper()).setDownloadExecutor(getExecutor()).create(mediaItem);
    }

    private boolean canPreCache(MediaItem mediaItem) {
        if (mediaItem == null || mediaItem.localConfiguration == null) return false;
        MediaItem.LocalConfiguration local = mediaItem.localConfiguration;
        return canPreCache(local.uri.getScheme(), local.uri.toString());
    }

    static boolean canPreCache(String scheme, String url) {
        return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && !MediaSourceFactory.isConcatenatingUrl(url) && !MediaSourceFactory.isLocalProxyUrl(url);
    }

    private long getStart() {
        return Math.max(0, hasSeek() ? seekStartMs : player.getCurrentPosition());
    }

    private boolean shouldPreCache(long startMs) {
        if (hasSeek()) return true;
        if (lastStartMs == C.TIME_UNSET) return true;
        return Math.abs(startMs - lastStartMs) >= getStep();
    }

    private boolean isStopped(int state) {
        return state == Player.STATE_ENDED || state == Player.STATE_IDLE;
    }

    private long getLength(long startMs) {
        long durationMs = player.getDuration();
        if (durationMs <= 0) return 0;
        return Math.min(PreloadSetting.getPreloadDurationMs(PlayerSetting.EXO), durationMs - startMs);
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
        int count = PreloadSetting.getPreloadThreads(PlayerSetting.EXO);
        if (executor != null && threads == count) return executor;
        retireExecutor();
        threads = count;
        return executor = Executors.newFixedThreadPool(count);
    }

    private void retireExecutor() {
        if (executor == null) return;
        ExecutorService retiringExecutor = executor;
        executor = null;
        if (worker == null) shutdownExecutor(retiringExecutor);
        else new Handler(worker.getLooper()).post(() -> shutdownExecutor(retiringExecutor));
    }

    private void shutdownExecutor(ExecutorService target) {
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
}
