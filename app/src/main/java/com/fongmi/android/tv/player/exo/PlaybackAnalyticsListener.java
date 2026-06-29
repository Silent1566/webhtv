package com.fongmi.android.tv.player.exo;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime;

import com.github.catvod.crawler.SpiderDebug;

public class PlaybackAnalyticsListener implements AnalyticsListener {

    private static volatile Snapshot snapshot = Snapshot.empty();

    private long totalDroppedFrames;

    public static Snapshot getSnapshot() {
        return snapshot;
    }

    public static void reset() {
        snapshot = Snapshot.empty();
    }

    @Override
    public void onPlaybackStateChanged(EventTime eventTime, @Player.State int state) {
        snapshot = snapshot.withState(stateName(state), eventTime.currentPlaybackPositionMs, eventTime.totalBufferedDurationMs);
        if (!SpiderDebug.isEnabled()) return;
        SpiderDebug.log("playback-metrics", "state=%s position=%d buffered=%d", stateName(state), eventTime.currentPlaybackPositionMs, eventTime.totalBufferedDurationMs);
    }

    @Override
    public void onVideoDecoderInitialized(EventTime eventTime, String decoderName, long initializedTimestampMs, long initializationDurationMs) {
        snapshot = snapshot.withDecoder(decoderName);
        if (!SpiderDebug.isEnabled()) return;
        SpiderDebug.log("playback-metrics", "video decoder=%s init=%dms", decoderName, initializationDurationMs);
    }

    @Override
    public void onVideoInputFormatChanged(EventTime eventTime, Format format, @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {
        snapshot = snapshot.withFormat(format);
        if (!SpiderDebug.isEnabled()) return;
        SpiderDebug.log("playback-metrics", "video format mime=%s codecs=%s size=%dx%d fps=%.3f bitrate=%d color=%s", format.sampleMimeType, format.codecs, format.width, format.height, format.frameRate, format.bitrate, format.colorInfo);
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

    private static String stateName(int state) {
        return switch (state) {
            case Player.STATE_IDLE -> "IDLE";
            case Player.STATE_BUFFERING -> "BUFFERING";
            case Player.STATE_READY -> "READY";
            case Player.STATE_ENDED -> "ENDED";
            default -> String.valueOf(state);
        };
    }

    public record Snapshot(String state, String decoderName, Format format, long droppedFrames, long positionMs, long bufferedMs) {

        public static Snapshot empty() {
            return new Snapshot("", "", null, 0, 0, 0);
        }

        private Snapshot withState(String state, long positionMs, long bufferedMs) {
            return new Snapshot(state, decoderName, format, droppedFrames, positionMs, bufferedMs);
        }

        private Snapshot withDecoder(String decoderName) {
            return new Snapshot(state, decoderName, format, droppedFrames, positionMs, bufferedMs);
        }

        private Snapshot withFormat(Format format) {
            return new Snapshot(state, decoderName, format, droppedFrames, positionMs, bufferedMs);
        }

        private Snapshot withDroppedFrames(long droppedFrames) {
            return new Snapshot(state, decoderName, format, droppedFrames, positionMs, bufferedMs);
        }
    }
}
