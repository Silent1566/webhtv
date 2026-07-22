package com.fongmi.android.tv.player.exo;

import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime;
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer;

import com.fongmi.android.tv.setting.ExoPerformanceSetting;
import com.fongmi.android.tv.player.PlaybackTrace;
import com.github.catvod.crawler.SpiderDebug;

public class PlaybackAnalyticsListener implements AnalyticsListener {

    private static volatile Snapshot snapshot = Snapshot.empty();
    private static volatile String playbackTraceId = PlaybackTrace.NONE;
    private static volatile long totalDroppedFrames;
    private static volatile long lastBandwidthLogMs;
    private static volatile boolean loading;
    private static final long BANDWIDTH_LOG_INTERVAL_MS = 5_000;

    public static Snapshot getSnapshot() {
        return snapshot;
    }

    public static void beginSession(String traceId) {
        reset();
        playbackTraceId = PlaybackTrace.normalize(traceId);
    }

    public static String getPlaybackTraceId() {
        return playbackTraceId;
    }

    public static void reset() {
        snapshot = Snapshot.empty();
        totalDroppedFrames = 0;
        lastBandwidthLogMs = 0;
        loading = false;
        playbackTraceId = PlaybackTrace.NONE;
    }

    public static void finishSession(long finalPositionMs) {
        Snapshot finished = snapshot;
        if (finished.everReady()) {
            long rebufferTotalMs = finished.rebufferTotalMs();
            if (finished.rebufferStartMs() > 0) rebufferTotalMs += Math.max(0, SystemClock.elapsedRealtime() - finished.rebufferStartMs());
            long mediaBitrate = ExoPlaybackDiagnostics.combinedBitrate(finished.videoFormat(), finished.audioFormat());
            ExoPerformanceSetting.recordAutoSession(finished.rebufferCount(), rebufferTotalMs, Math.max(finished.positionMs(), finalPositionMs), mediaBitrate, finished.bandwidthEstimate());
        }
        reset();
    }

    @Override
    public void onPlaybackStateChanged(EventTime eventTime, @Player.State int state) {
        long now = SystemClock.elapsedRealtime();
        Snapshot previous = snapshot;
        Snapshot next = snapshot.withState(stateName(state), eventTime.currentPlaybackPositionMs, eventTime.totalBufferedDurationMs);
        if (state == Player.STATE_BUFFERING && next.everReady() && next.rebufferStartMs() <= 0) next = next.withRebufferStart(now);
        if (state != Player.STATE_BUFFERING && next.rebufferStartMs() > 0) next = next.withRebufferEnd(now);
        if (state == Player.STATE_READY) next = next.withEverReady();
        snapshot = next;
        if (!SpiderDebug.isEnabled()) return;
        boolean rebufferStarted = previous.rebufferStartMs() <= 0 && next.rebufferStartMs() > 0;
        boolean rebufferEnded = previous.rebufferStartMs() > 0 && next.rebufferStartMs() <= 0;
        if (rebufferStarted) {
            traceLog("rebuffer start count=%d position=%d buffered=%d loading=%s", next.rebufferCount(), eventTime.currentPlaybackPositionMs, eventTime.totalBufferedDurationMs, loading);
        } else if (rebufferEnded) {
            traceLog("rebuffer end duration=%dms total=%dms count=%d position=%d buffered=%d loading=%s", Math.max(0, now - previous.rebufferStartMs()), next.rebufferTotalMs(), next.rebufferCount(), eventTime.currentPlaybackPositionMs, eventTime.totalBufferedDurationMs, loading);
        } else {
            traceLog("state=%s position=%d buffered=%d loading=%s", stateName(state), eventTime.currentPlaybackPositionMs, eventTime.totalBufferedDurationMs, loading);
        }
    }

    @Override
    public void onIsLoadingChanged(EventTime eventTime, boolean isLoading) {
        if (loading == isLoading) return;
        loading = isLoading;
        if (SpiderDebug.isEnabled()) traceLog("loading=%s state=%s position=%d buffered=%d", isLoading, snapshot.state(), eventTime.currentPlaybackPositionMs, eventTime.totalBufferedDurationMs);
    }

    @Override
    public void onVideoDecoderInitialized(EventTime eventTime, String decoderName, long initializedTimestampMs, long initializationDurationMs) {
        snapshot = snapshot.withVideoDecoder(decoderName);
        if (!SpiderDebug.isEnabled()) return;
        traceLog("video decoder=%s init=%dms", decoderName, initializationDurationMs);
    }

    @Override
    public void onVideoInputFormatChanged(EventTime eventTime, Format format, @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {
        snapshot = snapshot.withVideoFormat(format);
        if (!SpiderDebug.isEnabled()) return;
        traceLog("video format mime=%s codecs=%s size=%dx%d fps=%.3f bitrate=%d bitrateSource=%s color=%s", format.sampleMimeType, format.codecs, format.width, format.height, format.frameRate, ExoPlaybackDiagnostics.formatBitrate(format), ExoPlaybackDiagnostics.bitrateSource(format), format.colorInfo);
        ExoPlaybackDiagnostics.logTrackFormats(snapshot.videoFormat(), snapshot.audioFormat(), ExoUtil.getBufferBudget().effectiveTargetBytes());
    }

    @Override
    public void onAudioDecoderInitialized(EventTime eventTime, String decoderName, long initializedTimestampMs, long initializationDurationMs) {
        snapshot = snapshot.withAudioDecoder(decoderName);
        if (!SpiderDebug.isEnabled()) return;
        traceLog("audio decoder=%s init=%dms", decoderName, initializationDurationMs);
    }

    @Override
    public void onAudioInputFormatChanged(EventTime eventTime, Format format, @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {
        snapshot = snapshot.withAudioFormat(format);
        if (!SpiderDebug.isEnabled()) return;
        traceLog("audio format mime=%s codecs=%s channels=%d sampleRate=%d bitrate=%d bitrateSource=%s language=%s", format.sampleMimeType, format.codecs, format.channelCount, format.sampleRate, ExoPlaybackDiagnostics.formatBitrate(format), ExoPlaybackDiagnostics.bitrateSource(format), format.language);
        ExoPlaybackDiagnostics.logTrackFormats(snapshot.videoFormat(), snapshot.audioFormat(), ExoUtil.getBufferBudget().effectiveTargetBytes());
    }

    @Override
    public void onVideoSizeChanged(EventTime eventTime, VideoSize videoSize) {
        if (!SpiderDebug.isEnabled()) return;
        traceLog("video size=%dx%d unappliedRotation=%d ratio=%.3f", videoSize.width, videoSize.height, videoSize.unappliedRotationDegrees, videoSize.pixelWidthHeightRatio);
    }

    @Override
    public void onDroppedVideoFrames(EventTime eventTime, int droppedFrames, long elapsedMs) {
        totalDroppedFrames += droppedFrames;
        snapshot = snapshot.withDroppedFrames(totalDroppedFrames);
        if (!SpiderDebug.isEnabled()) return;
        traceLog("droppedFrames=%d total=%d elapsed=%dms position=%d", droppedFrames, totalDroppedFrames, elapsedMs, eventTime.currentPlaybackPositionMs);
    }

    @Override
    public void onBandwidthEstimate(EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
        snapshot = snapshot.withBandwidth(totalLoadTimeMs, totalBytesLoaded, bitrateEstimate);
        if (!SpiderDebug.isEnabled()) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastBandwidthLogMs < BANDWIDTH_LOG_INTERVAL_MS) return;
        lastBandwidthLogMs = now;
        traceLog("bandwidth=%d loadTime=%dms bytes=%d", bitrateEstimate, totalLoadTimeMs, totalBytesLoaded);
    }

    @Override
    public void onPlayerError(EventTime eventTime, PlaybackException error) {
        String code = PlaybackException.getErrorCodeName(error.errorCode);
        ErrorDetails details = ErrorDetails.from(error);
        snapshot = snapshot.withError(code, error.getMessage(), details);
        if (!SpiderDebug.isEnabled()) return;
        traceLog("error code=%s message=%s details=%s", code, error.getMessage(), details.summary());
    }

    private static void traceLog(String format, Object... args) {
        PlaybackTrace.log("playback-metrics", playbackTraceId, format, args);
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

    public record Snapshot(String state, String videoDecoderName, Format videoFormat, String audioDecoderName, Format audioFormat, long droppedFrames, long positionMs, long bufferedMs, long bandwidthEstimate, int lastLoadTimeMs, long lastLoadBytes, int rebufferCount, long rebufferTotalMs, long rebufferStartMs, boolean everReady, String errorCode, String errorMessage, Format errorFormat, String errorDecoderName, String errorDiagnosticInfo, boolean errorSecureDecoderRequired, String errorCause) {

        public static Snapshot empty() {
            return new Snapshot("", "", null, "", null, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, "", "", null, "", "", false, "");
        }

        private Snapshot withState(String state, long positionMs, long bufferedMs) {
            return new Snapshot(state, videoDecoderName, videoFormat, audioDecoderName, audioFormat, droppedFrames, positionMs, Math.max(0, bufferedMs), bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs, rebufferStartMs, everReady, errorCode, errorMessage, errorFormat, errorDecoderName, errorDiagnosticInfo, errorSecureDecoderRequired, errorCause);
        }

        private Snapshot withVideoDecoder(String decoderName) {
            return new Snapshot(state, decoderName, videoFormat, audioDecoderName, audioFormat, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs, rebufferStartMs, everReady, errorCode, errorMessage, errorFormat, errorDecoderName, errorDiagnosticInfo, errorSecureDecoderRequired, errorCause);
        }

        private Snapshot withVideoFormat(Format format) {
            return new Snapshot(state, videoDecoderName, format, audioDecoderName, audioFormat, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs, rebufferStartMs, everReady, errorCode, errorMessage, errorFormat, errorDecoderName, errorDiagnosticInfo, errorSecureDecoderRequired, errorCause);
        }

        private Snapshot withAudioDecoder(String decoderName) {
            return new Snapshot(state, videoDecoderName, videoFormat, decoderName, audioFormat, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs, rebufferStartMs, everReady, errorCode, errorMessage, errorFormat, errorDecoderName, errorDiagnosticInfo, errorSecureDecoderRequired, errorCause);
        }

        private Snapshot withAudioFormat(Format format) {
            return new Snapshot(state, videoDecoderName, videoFormat, audioDecoderName, format, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs, rebufferStartMs, everReady, errorCode, errorMessage, errorFormat, errorDecoderName, errorDiagnosticInfo, errorSecureDecoderRequired, errorCause);
        }

        private Snapshot withDroppedFrames(long droppedFrames) {
            return new Snapshot(state, videoDecoderName, videoFormat, audioDecoderName, audioFormat, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs, rebufferStartMs, everReady, errorCode, errorMessage, errorFormat, errorDecoderName, errorDiagnosticInfo, errorSecureDecoderRequired, errorCause);
        }

        private Snapshot withBandwidth(int loadTimeMs, long bytesLoaded, long bitrateEstimate) {
            return new Snapshot(state, videoDecoderName, videoFormat, audioDecoderName, audioFormat, droppedFrames, positionMs, bufferedMs, Math.max(0, bitrateEstimate), Math.max(0, loadTimeMs), Math.max(0, bytesLoaded), rebufferCount, rebufferTotalMs, rebufferStartMs, everReady, errorCode, errorMessage, errorFormat, errorDecoderName, errorDiagnosticInfo, errorSecureDecoderRequired, errorCause);
        }

        private Snapshot withRebufferStart(long now) {
            return new Snapshot(state, videoDecoderName, videoFormat, audioDecoderName, audioFormat, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount + 1, rebufferTotalMs, now, everReady, errorCode, errorMessage, errorFormat, errorDecoderName, errorDiagnosticInfo, errorSecureDecoderRequired, errorCause);
        }

        private Snapshot withRebufferEnd(long now) {
            return new Snapshot(state, videoDecoderName, videoFormat, audioDecoderName, audioFormat, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs + Math.max(0, now - rebufferStartMs), 0, everReady, errorCode, errorMessage, errorFormat, errorDecoderName, errorDiagnosticInfo, errorSecureDecoderRequired, errorCause);
        }

        private Snapshot withEverReady() {
            return new Snapshot(state, videoDecoderName, videoFormat, audioDecoderName, audioFormat, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs, rebufferStartMs, true, errorCode, errorMessage, errorFormat, errorDecoderName, errorDiagnosticInfo, errorSecureDecoderRequired, errorCause);
        }

        private Snapshot withError(String code, String message, ErrorDetails details) {
            Format format = details.format() != null ? details.format() : videoFormat;
            return new Snapshot(state, videoDecoderName, format, audioDecoderName, audioFormat, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs, rebufferStartMs, everReady, code, message, details.format(), details.decoderName(), details.diagnosticInfo(), details.secureDecoderRequired(), details.cause());
        }
    }

    private record ErrorDetails(Format format, String decoderName, String diagnosticInfo, boolean secureDecoderRequired, String cause) {

        static ErrorDetails from(PlaybackException error) {
            Format format = null;
            String decoderName = "";
            String diagnosticInfo = "";
            boolean secure = false;
            if (error instanceof ExoPlaybackException exo) format = exo.rendererFormat;
            MediaCodecRenderer.DecoderInitializationException init = findDecoderInitException(error);
            if (init != null) {
                decoderName = init.codecInfo == null ? "" : init.codecInfo.name;
                diagnosticInfo = init.diagnosticInfo == null ? "" : init.diagnosticInfo;
                secure = init.secureDecoderRequired;
            }
            Throwable cause = rootCause(error);
            return new ErrorDetails(format, decoderName, diagnosticInfo, secure, cause == null ? "" : cause.getClass().getSimpleName() + ": " + cause.getMessage());
        }

        private String summary() {
            return "decoder=" + decoderName + " diagnostic=" + diagnosticInfo + " secure=" + secureDecoderRequired + " cause=" + cause;
        }
    }

    private static MediaCodecRenderer.DecoderInitializationException findDecoderInitException(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof MediaCodecRenderer.DecoderInitializationException init) return init;
        }
        return null;
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) current = current.getCause();
        return current;
    }
}
