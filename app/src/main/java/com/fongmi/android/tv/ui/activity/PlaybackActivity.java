package com.fongmi.android.tv.ui.activity;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomSeekView;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.crawler.SpiderDebug;
import com.google.common.util.concurrent.ListenableFuture;

public abstract class PlaybackActivity extends BaseActivity implements MediaController.Listener, Player.Listener, ServiceConnection {

    private ListenableFuture<MediaController> mControllerFuture;
    private MediaController mController;
    private PlaybackService mService;
    private boolean audioOnly;
    private boolean redirect;
    private boolean playbackExiting;
    private boolean bound;
    private boolean stop;
    private boolean lock;
    private int render = -1;

    protected MediaController controller() {
        return mController;
    }

    protected PlaybackService service() {
        return mService;
    }

    protected PlayerManager player() {
        return mService == null ? null : mService.player();
    }

    protected boolean isRedirect() {
        return redirect;
    }

    protected void setRedirect(boolean redirect) {
        this.redirect = redirect;
        if (mService != null) mService.setNavigationCallback(redirect ? null : getNavigationCallback(), getPlaybackKey());
    }

    protected boolean isPlaybackExiting() {
        return playbackExiting;
    }

    protected void markPlaybackExiting() {
        this.playbackExiting = true;
    }

    protected void finishPlayback() {
        markPlaybackExiting();
        stopPlayback();
        finish();
    }

    protected void stopPlayback() {
        if (mService != null && isOwner()) {
            mService.shutdown();
        } else if (mController != null) {
            mController.stop();
        }
    }

    protected void updateNavigationKey() {
        if (mService != null) mService.setNavigationCallback(getNavigationCallback(), getPlaybackKey());
    }

    protected boolean isAudioOnly() {
        return audioOnly;
    }

    protected void setAudioOnly(boolean audioOnly) {
        this.audioOnly = audioOnly;
    }

    protected boolean isStop() {
        return stop;
    }

    protected void setStop(boolean stop) {
        this.stop = stop;
    }

    protected boolean isLock() {
        return lock;
    }

    protected void setLock(boolean lock) {
        this.lock = lock;
    }

    protected abstract PlaybackService.NavigationCallback getNavigationCallback();

    protected abstract CustomSeekView getSeekView();

    protected abstract PlayerView getExoView();

    protected abstract String getPlaybackKey();

    protected boolean deferPlaybackServiceBinding() {
        return false;
    }

    protected boolean isOwner() {
        String key = getPlaybackKey();
        PlayerManager manager = player();
        return key == null || (manager != null && key.equals(manager.getKey()));
    }

    protected boolean isIdle() {
        return mController.getPlaybackState() == Player.STATE_IDLE;
    }

    protected boolean isEnded() {
        return mController.getPlaybackState() == Player.STATE_ENDED;
    }

    protected boolean isBuffering() {
        return mController.getPlaybackState() == Player.STATE_BUFFERING;
    }

    protected boolean isPaused() {
        return !isBuffering() && !isIdle();
    }

    protected void onServiceConnected() {
    }

    protected boolean isLutAllowed() {
        return true;
    }

    protected void onPrepare() {
    }

    protected void onPlayerRebuilt() {
    }

    protected void onTracksChanged() {
    }

    protected void onTitlesChanged() {
    }

    protected void onError(String msg) {
    }

    protected void onReload(String msg) {
        onError(msg);
    }

    protected void onPlayingChanged(boolean isPlaying) {
    }

    protected void onStateChanged(int state) {
    }

    protected void onSizeChanged(VideoSize size) {
    }

    protected void onSurfaceAttached() {
    }

    protected void onSeekStarted() {
    }

    protected void applyResizeMode(int resizeMode) {
        PlayerView view = getExoView();
        view.setResizeMode(resizeMode);
        view.requestLayout();
        View surface = view.getVideoSurfaceView();
        if (surface != null) surface.requestLayout();
    }

    protected void onReclaim() {
    }

    protected boolean shouldBindPlaybackService() {
        return true;
    }

    protected boolean shouldPauseOnBackground() {
        return PlayerSetting.isBackgroundOff();
    }

    protected void seekTo(long time) {
        onSeekStarted();
        mController.seekTo(player().getPosition() + time);
        mController.play();
    }

    protected void startPlayer(String key, Result result, boolean useParse, long timeout, MediaMetadata metadata) {
        if (result.getDrm() != null && !FrameworkMediaDrm.isCryptoSchemeSupported(result.getDrm().getUUID())) {
            onError(ResUtil.getString(R.string.error_play_drm));
        } else if (result.hasMsg()) {
            onError(result.getMsg());
        } else if (result.getRealUrl().isEmpty()) {
            onError(ResUtil.getString(R.string.error_play_url));
        } else if (result.needParse() || useParse) {
            attachSurface();
            player().parse(key, result, useParse, metadata, PlayerSetting.isAutoPlay());
        } else {
            attachSurface();
            player().start(PlaySpec.from(result, key, metadata), timeout, PlayerSetting.isAutoPlay());
        }
        syncKeepScreenOn();
    }

    private void bindPlaybackService() {
        if (bound) return;
        long start = System.currentTimeMillis();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "bind service start key=%s", getPlaybackKey());
        startService(new Intent(this, PlaybackService.class));
        bindService(new Intent(this, PlaybackService.class).setAction(PlaybackService.LOCAL_BIND_ACTION), this, BIND_AUTO_CREATE);
        buildControllerAsync();
        bound = true;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "bind service requested cost=%dms key=%s", System.currentTimeMillis() - start, getPlaybackKey());
    }

    private void bindPlaybackServiceAfterFirstFrame() {
        View root = getExoView().getRootView();
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (root.getViewTreeObserver().isAlive()) root.getViewTreeObserver().removeOnPreDrawListener(this);
                root.post(() -> {
                    if (!isFinishing() && !isDestroyed()) bindPlaybackService();
                });
                return true;
            }
        });
    }

    private void buildControllerAsync() {
        long start = System.currentTimeMillis();
        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        mControllerFuture = new MediaController.Builder(this, token).setListener(this).buildAsync();
        mControllerFuture.addListener(this::handleControllerConnected, ContextCompat.getMainExecutor(this));
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "controller build requested cost=%dms key=%s", System.currentTimeMillis() - start, getPlaybackKey());
    }

    protected void onControllerConnected() {
    }

    private void handleControllerConnected() {
        long start = System.currentTimeMillis();
        try {
            mController = mControllerFuture.get();
            getSeekView().setPlayer(mController);
            getSeekView().setSeekListener(this::onSeekStarted);
            mController.addListener(this);
        } catch (Exception ignored) {
        }
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "controller connected cost=%dms key=%s", System.currentTimeMillis() - start, getPlaybackKey());
        syncKeepScreenOn();
        if (mController != null) onControllerConnected();
    }

    private PendingIntent buildSessionIntent() {
        Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        Bundle extras = getIntent().getExtras();
        if (extras != null) intent.putExtras(extras);
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private boolean shouldReclaim() {
        return mService != null && !isOwner();
    }

    private void closePiP() {
        if (!isInPictureInPictureMode()) return;
        detach();
        finish();
    }

    private void attachSurface() {
        if (mService == null) return;
        int targetRender = getRender();
        syncShutter(true);
        if (render != targetRender) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "switch render from=%d to=%d", render, targetRender);
            if (getExoView().getPlayer() != null) getExoView().setPlayer(null);
            getExoView().setRender(targetRender);
            render = targetRender;
            syncShutter(true);
        }
        if (getExoView().getPlayer() == null) {
            getExoView().setPlayer(player().getPlayer());
            syncVideoSurfaceSize(null);
            syncShutter();
            if (player().useNativeVideoOutput()) getExoView().post(this::syncShutter);
        }
        onSurfaceAttached();
    }

    private void syncVideoSurfaceSize(VideoSize size) {
        if (mService == null) return;
        View surface = getExoView().getVideoSurfaceView();
        if (!(surface instanceof SurfaceView surfaceView)) return;
        if (!PlaybackPerformanceSetting.isSurfaceFixedSizeEnabled() || getRender() != PlayerSetting.RENDER_SURFACE || player().isIjk()) {
            surfaceView.getHolder().setSizeFromLayout();
            return;
        }
        int width = size != null && size.width > 0 ? size.width : player().getVideoWidth();
        int height = size != null && size.height > 0 ? size.height : player().getVideoHeight();
        if (width <= 0 || height <= 0) return;
        ExoUtil.EnhancedVideoProfile profile = ExoUtil.getEnhancedVideoProfile();
        float scale = Math.min((float) profile.width() / width, (float) profile.height() / height);
        if (scale < 1f) {
            width = Math.max(1, Math.round(width * scale));
            height = Math.max(1, Math.round(height * scale));
        }
        surfaceView.getHolder().setFixedSize(width, height);
    }

    private void syncShutter() {
        syncShutter(false);
    }

    private void syncShutter(boolean restoreExo) {
        if (mService == null) return;
        boolean nativeOutput = player().useNativeVideoOutput();
        View shutter = getExoView().findViewById(androidx.media3.ui.R.id.exo_shutter);
        if (nativeOutput) {
            getExoView().setShutterBackgroundColor(Color.TRANSPARENT);
            if (shutter != null) shutter.setVisibility(View.GONE);
        } else if (restoreExo) {
            getExoView().setShutterBackgroundColor(Color.BLACK);
            if (shutter != null) shutter.setVisibility(View.VISIBLE);
        }
    }

    private void detachSurface() {
        getExoView().setPlayer(null);
    }

    private void resetVideoSurfaceForDecoderSwitch() {
        int targetRender = getRender();
        int temporaryRender = targetRender == PlayerSetting.RENDER_TEXTURE ? PlayerSetting.RENDER_SURFACE : PlayerSetting.RENDER_TEXTURE;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "reset video surface for decoder switch temp=%d target=%d", temporaryRender, targetRender);
        getExoView().setPlayer(null);
        getExoView().setRender(temporaryRender);
        getExoView().setRender(targetRender);
        render = -1;
    }

    protected void setRender() {
        render = -1;
        detachSurface();
        attachSurface();
    }

    private int getRender() {
        return mService == null ? PlayerSetting.getRender() : PlayerSetting.getRender(player().getPlayerType());
    }

    private void releasePlaybackService() {
        if (mService != null) releaseService(isOwner());
        detach();
    }

    private void releaseService(boolean owner) {
        mService.removePlayerCallback(mPlayerCallback);
        if (owner) mService.setNavigationCallback(null, null);
        if (owner && mService.isKeepAlive()) {
            mService.resetSessionActivity();
        } else if (mService.hasExternalClient() || mService.hasPlayerCallback()) {
            if (owner) mService.suspend();
            mService.resetSessionActivity();
        } else if (owner) {
            mService.shutdown();
        }
    }

    private void detach() {
        releaseController();
        releaseBinding();
    }

    private void releaseController() {
        if (mControllerFuture != null) MediaController.releaseFuture(mControllerFuture);
        if (mController != null) mController.removeListener(this);
        mControllerFuture = null;
        mController = null;
    }

    private void releaseBinding() {
        if (!bound) return;
        bound = false;
        if (mService != null) mService.removePlayerCallback(mPlayerCallback);
        unbindService(this);
        mService = null;
    }

    private void syncKeepScreenOn() {
        if (shouldKeepScreenOn()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private boolean shouldKeepScreenOn() {
        if (!isOwner()) return false;
        PlayerManager manager = player();
        if (manager == null || manager.isReleased() || manager.isEmpty()) return false;
        Player active = mController != null ? mController : manager.getPlayer();
        int state = active.getPlaybackState();
        if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) return false;
        if (active.isPlaying()) return true;
        return active.getPlayWhenReady() && (state == Player.STATE_BUFFERING || state == Player.STATE_READY);
    }

    private String lifecycleState() {
        String playerKey = null;
        boolean released = true;
        if (mService != null && mService.player() != null) {
            released = mService.player().isReleased();
            if (!released) playerKey = mService.player().getKey();
        }
        return "activity=" + getClass().getSimpleName() +
                " key=" + getPlaybackKey() +
                " playerKey=" + playerKey +
                " owner=" + isOwner() +
                " bound=" + bound +
                " service=" + (mService != null) +
                " controller=" + (mController != null) +
                " released=" + released +
                " redirect=" + redirect +
                " stop=" + stop +
                " finishing=" + isFinishing() +
                " destroyed=" + isDestroyed() +
                " keepScreen=" + ((getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0);
    }

    private final PlaybackService.PlayerCallback mPlayerCallback = new PlaybackService.PlayerCallback() {

        @Override
        public void onPrepare() {
            if (isOwner()) PlaybackActivity.this.onPrepare();
        }

        @Override
        public void onTracksChanged() {
            if (isOwner()) PlaybackActivity.this.onTracksChanged();
        }

        @Override
        public void onTitlesChanged() {
            if (isOwner()) PlaybackActivity.this.onTitlesChanged();
        }

        @Override
        public void onError(String msg) {
            if (isOwner()) PlaybackActivity.this.onError(msg);
        }

        @Override
        public void onReload(String msg) {
            if (isOwner()) PlaybackActivity.this.onReload(msg);
        }

        @Override
        public void onPlayerRebuild(Player player, boolean resetVideoSurface) {
            if (isOwner()) {
                if (resetVideoSurface) resetVideoSurfaceForDecoderSwitch();
                setRender();
                PlaybackActivity.this.onPlayerRebuilt();
            }
        }
    };

    @Override
    protected void initView(Bundle savedInstanceState) {
        long start = System.currentTimeMillis();
        super.initView(savedInstanceState);
        if (!shouldBindPlaybackService()) return;
        ExoUtil.setPlayerView(getExoView());
        if (deferPlaybackServiceBinding()) bindPlaybackServiceAfterFirstFrame();
        else bindPlaybackService();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "initView cost=%dms key=%s deferred=%s", System.currentTimeMillis() - start, getPlaybackKey(), deferPlaybackServiceBinding());
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        syncKeepScreenOn();
        if (!isOwner()) return;
        syncShutter();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "playing changed isPlaying=%s state=%d %s", isPlaying, mController == null ? -1 : mController.getPlaybackState(), lifecycleState());
        onPlayingChanged(isPlaying);
    }

    @Override
    public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
        syncKeepScreenOn();
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "state changed state=%d %s", state, lifecycleState());
        syncKeepScreenOn();
        if (!isOwner()) return;
        syncShutter();
        onStateChanged(state);
    }

    @Override
    public void onVideoSizeChanged(@NonNull VideoSize size) {
        if (!isOwner()) return;
        syncShutter();
        syncVideoSurfaceSize(size);
        onSizeChanged(size);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        long start = System.currentTimeMillis();
        mService = ((PlaybackService.LocalBinder) binder).getService();
        mService.replaceBinding(this::closePiP);
        mService.setSessionActivity(buildSessionIntent());
        mService.setNavigationCallback(getNavigationCallback(), getPlaybackKey());
        mService.addPlayerCallback(mPlayerCallback);
        player().setLutAllowed(isLutAllowed());
        syncKeepScreenOn();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "service connected cost=%dms key=%s", System.currentTimeMillis() - start, getPlaybackKey());
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "service connected %s", lifecycleState());
        onServiceConnected();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "service disconnected name=%s %s", name, lifecycleState());
        mService = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "activity resume %s", lifecycleState());
        playbackExiting = false;
        setRedirect(false);
        if (shouldReclaim()) {
            detachSurface();
            onReclaim();
        }
        syncKeepScreenOn();
    }

    @Override
    protected void onPause() {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "activity pause %s", lifecycleState());
        super.onPause();
        if (isRedirect() && mController != null) mController.pause();
    }

    @Override
    protected void onStop() {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "activity stop backgroundOff=%s %s", PlayerSetting.isBackgroundOff(), lifecycleState());
        super.onStop();
        if (isOwner() && shouldPauseOnBackground() && mController != null) mController.pause();
    }

    @Override
    public void onTrimMemory(int level) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "activity trimMemory level=%d %s", level, lifecycleState());
        super.onTrimMemory(level);
    }

    @Override
    protected void onDestroy() {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "activity destroy beforeRelease %s", lifecycleState());
        super.onDestroy();
        releasePlaybackService();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "activity destroy afterRelease activity=%s key=%s", getClass().getSimpleName(), getPlaybackKey());
    }
}
