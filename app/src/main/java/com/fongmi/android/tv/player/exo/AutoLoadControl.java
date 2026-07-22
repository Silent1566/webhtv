package com.fongmi.android.tv.player.exo;

import androidx.media3.common.C;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.upstream.Allocator;

import com.fongmi.android.tv.setting.ExoPerformanceSetting;

final class AutoLoadControl implements LoadControl {

    static final int MAX_REBUFFER_MS = 8_000;
    private final DefaultLoadControl delegate;

    AutoLoadControl(DefaultLoadControl delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onPrepared(PlayerId playerId) {
        delegate.onPrepared(playerId);
    }

    @Override
    public void onTracksSelected(Parameters parameters, TrackGroupArray trackGroups, ExoTrackSelection[] trackSelections) {
        delegate.onTracksSelected(parameters, trackGroups, trackSelections);
    }

    @Override
    public void onStopped(PlayerId playerId) {
        delegate.onStopped(playerId);
    }

    @Override
    public void onReleased(PlayerId playerId) {
        delegate.onReleased(playerId);
    }

    @Override
    public Allocator getAllocator(PlayerId playerId) {
        return delegate.getAllocator(playerId);
    }

    @Override
    public long getBackBufferDurationUs(PlayerId playerId) {
        return delegate.getBackBufferDurationUs(playerId);
    }

    @Override
    public boolean retainBackBufferFromKeyframe(PlayerId playerId) {
        return delegate.retainBackBufferFromKeyframe(playerId);
    }

    @Override
    public boolean shouldContinueLoading(Parameters parameters) {
        return delegate.shouldContinueLoading(parameters);
    }

    @Override
    public boolean shouldStartPlayback(Parameters parameters) {
        boolean delegateReady = delegate.shouldStartPlayback(parameters);
        if (!parameters.rebuffering || delegateReady) return delegateReady;
        return reachedAdaptiveThreshold(parameters.bufferedDurationUs, parameters.playbackSpeed, parameters.targetLiveOffsetUs, ExoPerformanceSetting.getAutoSessionRebufferMs());
    }

    @Override
    public boolean shouldContinuePreloading(PlayerId playerId, Timeline timeline, MediaSource.MediaPeriodId mediaPeriodId, long bufferedDurationUs) {
        return delegate.shouldContinuePreloading(playerId, timeline, mediaPeriodId, bufferedDurationUs);
    }

    static boolean reachedAdaptiveThreshold(long bufferedDurationUs, float playbackSpeed, long targetLiveOffsetUs, int rebufferMs) {
        long requiredUs = rebufferMs * 1_000L;
        if (targetLiveOffsetUs != C.TIME_UNSET) requiredUs = Math.min(requiredUs, targetLiveOffsetUs / 2);
        long playoutBufferedUs = Util.getPlayoutDurationForMediaDuration(bufferedDurationUs, playbackSpeed);
        return playoutBufferedUs >= requiredUs;
    }
}
