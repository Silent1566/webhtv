package com.fongmi.android.tv.player.exo;

import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime;

import com.github.catvod.crawler.SpiderDebug;

public class PlaybackAnalyticsListener implements AnalyticsListener {

    private static volatile Snapshot snapshot = Snapshot.empty();
    private static volatile long totalDroppedFrames;


    public static Snapshot getSnapshot() {
        return snapshot;
    }

    public static void reset() {
        snapshot = Snapshot.empty();
        totalDroppedFrames = 0;
    }

    @Override
    public void onPlaybackStateChanged(EventTime eventTime, @Player.State int state) {
        long now = SystemClock.elapsedRealtime();
        Snapshot next = snapshot.withState(stateName(state), eventTime.currentPlaybackPositionMs, eventTime.totalBufferedDurationMs);
        if (state == Player.STATE_BUFFERING && next.everReady() && next.rebufferStartMs() <= 0) next = next.withRebufferStart(now);
        if (state != Player.STATE_BUFFERING && next.rebufferStartMs() > 0) next = next.withRebufferEnd(now);
        if (state == Player.STATE_READY) next = next.withEverReady();
        snapshot = next;
        if (!SpiderDebug.isEnabled()) return;
        SpiderDebug.log("playback-metrics", "state=%s position=%d buffered=%d", stateName(state), eventTime.currentPlaybackPositionMs, eventTime.totalBufferedDurationMs);
    }

    @Override
    public void onVideoDecoderInitialized(EventTime eventTime, String decoderName, long initializedTimestampMs, long initializationDurationMs) {
        snapshot = snapshot.withVideoDecoder(decoderName);
        if (!SpiderDebug.isEnabled()) return;
        SpiderDebug.log("playback-metrics", "video decoder=%s init=%dms", decoderName, initializationDurationMs);
    }

    @Override
    public void onVideoInputFormatChanged(EventTime eventTime, Format format, @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {
        snapshot = snapshot.withVideoFormat(format);
        if (!SpiderDebug.isEnabled()) return;
        SpiderDebug.log("playback-metrics", "video format mime=%s codecs=%s size=%dx%d fps=%.3f bitrate=%d color=%s", format.sampleMimeType, format.codecs, format.width, format.height, format.frameRate, format.bitrate, format.colorInfo);
    }

    @Override
    public void onAudioDecoderInitialized(EventTime eventTime, String decoderName, long initializedTimestampMs, long initializationDurationMs) {
        snapshot = snapshot.withAudioDecoder(decoderName);
        if (!SpiderDebug.isEnabled()) return;
        SpiderDebug.log("playback-metrics", "audio decoder=%s init=%dms", decoderName, initializationDurationMs);
    }

    @Override
    public void onAudioInputFormatChanged(EventTime eventTime, Format format, @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {
        snapshot = snapshot.withAudioFormat(format);
        if (!SpiderDebug.isEnabled()) return;
        SpiderDebug.log("playback-metrics", "audio format mime=%s codecs=%s channels=%d sampleRate=%d bitrate=%d language=%s", format.sampleMimeType, format.codecs, format.channelCount, format.sampleRate, format.bitrate, format.language);
    }

    @Override
    public void onVideoSizeChanged(EventTime eventTime, VideoSize videoSize) {
        if (!SpiderDebug.isEnabled()) return;
        SpiderDebug.log("playback-metrics", "video size=%dx%d unappliedRotation=%d ratio=%.3f", videoSize.width, videoSize.height, videoSize.unappliedRotationDegrees, videoSize.pixelWidthHeightRatio);
    }

    @Override
    public void onDroppedVideoFrames(EventTime eventTime, int droppedFrames, long elapsedMs) {
        totalDroppedFrames += droppedFrames;
        snapshot = snapshot.withDroppedFrames(totalDroppedFrames);
        if (!SpiderDebug.isEnabled()) return;
        SpiderDebug.log("playback-metrics", "droppedFrames=%d total=%d elapsed=%dms position=%d", droppedFrames, totalDroppedFrames, elapsedMs, eventTime.currentPlaybackPositionMs);
    }

    @Override
    public void onBandwidthEstimate(EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
        snapshot = snapshot.withBandwidth(totalLoadTimeMs, totalBytesLoaded, bitrateEstimate);
        if (!SpiderDebug.isEnabled()) return;
        SpiderDebug.log("playback-metrics", "bandwidth=%d loadTime=%dms bytes=%d", bitrateEstimate, totalLoadTimeMs, totalBytesLoaded);
    }

    @Override
    public void onPlayerError(EventTime eventTime, PlaybackException error) {
        String code = PlaybackException.getErrorCodeName(error.errorCode);
        snapshot = snapshot.withError(code, error.getMessage());
        if (!SpiderDebug.isEnabled()) return;
        SpiderDebug.log("playback-metrics", "error code=%s message=%s", code, error.getMessage());
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

    public record Snapshot(String state, String videoDecoderName, Format videoFormat, String audioDecoderName, Format audioFormat, long droppedFrames, long positionMs, long bufferedMs, long bandwidthEstimate, int lastLoadTimeMs, long lastLoadBytes, int rebufferCount, long rebufferTotalMs, long rebufferStartMs, boolean everReady, String errorCode, String errorMessage) {

        public static Snapshot empty() {
            return new Snapshot("", "", null, "", null, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, "", "");
        }

        private Snapshot withState(String state, long positionMs, long bufferedMs) {
            return new Snapshot(state, videoDecoderName, videoFormat, audioDecoderName, audioFormat, droppedFrames, positionMs, Math.max(0, bufferedMs), bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs, rebufferStartMs, everReady, errorCode, errorMessage);
        }

        private Snapshot withVideoDecoder(String decoderName) {
            return new Snapshot(state, decoderName, videoFormat, audioDecoderName, audioFormat, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs, rebufferStartMs, everReady, errorCode, errorMessage);
        }

        private Snapshot withVideoFormat(Format format) {
            return new Snapshot(state, videoDecoderName, format, audioDecoderName, audioFormat, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs, rebufferStartMs, everReady, errorCode, errorMessage);
        }

        private Snapshot withAudioDecoder(String decoderName) {
            return new Snapshot(state, videoDecoderName, videoFormat, decoderName, audioFormat, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs, rebufferStartMs, everReady, errorCode, errorMessage);
        }

        private Snapshot withAudioFormat(Format format) {
            return new Snapshot(state, videoDecoderName, videoFormat, audioDecoderName, format, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs, rebufferStartMs, everReady, errorCode, errorMessage);
        }

        private Snapshot withDroppedFrames(long droppedFrames) {
            return new Snapshot(state, videoDecoderName, videoFormat, audioDecoderName, audioFormat, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs, rebufferStartMs, everReady, errorCode, errorMessage);
        }

        private Snapshot withBandwidth(int loadTimeMs, long bytesLoaded, long bitrateEstimate) {
            return new Snapshot(state, videoDecoderName, videoFormat, audioDecoderName, audioFormat, droppedFrames, positionMs, bufferedMs, Math.max(0, bitrateEstimate), Math.max(0, loadTimeMs), Math.max(0, bytesLoaded), rebufferCount, rebufferTotalMs, rebufferStartMs, everReady, errorCode, errorMessage);
        }

        private Snapshot withRebufferStart(long now) {
            return new Snapshot(state, videoDecoderName, videoFormat, audioDecoderName, audioFormat, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount + 1, rebufferTotalMs, now, everReady, errorCode, errorMessage);
        }

        private Snapshot withRebufferEnd(long now) {
            return new Snapshot(state, videoDecoderName, videoFormat, audioDecoderName, audioFormat, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs + Math.max(0, now - rebufferStartMs), 0, everReady, errorCode, errorMessage);
        }

        private Snapshot withEverReady() {
            return new Snapshot(state, videoDecoderName, videoFormat, audioDecoderName, audioFormat, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs, rebufferStartMs, true, errorCode, errorMessage);
        }

        private Snapshot withError(String code, String message) {
            return new Snapshot(state, videoDecoderName, videoFormat, audioDecoderName, audioFormat, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs, rebufferStartMs, everReady, code, message);
        }
    }
}
