package com.fongmi.android.tv.player.ijk;

import androidx.media3.common.PlaybackException;

import com.fongmi.android.tv.player.PlaybackAutoContext;
import com.fongmi.android.tv.player.PlaybackErrorClassifier;

import java.util.Set;

import tv.danmaku.ijk.media.player.IMediaPlayer;

/** Pure policy for IJK runtime health, failure attribution and bounded fallback. */
public final class IjkRuntimeProfilePolicy {

    public static final int MAX_FALLBACKS_PER_PLAYBACK = 2;
    public static final long STABLE_PLAYBACK_WINDOW_MS = 30_000L;
    public static final int MAX_STABLE_REBUFFER_COUNT = 0;
    public static final int MIN_STABLE_RENDERED_RATIO_PERMILLE = 850;
    public static final int MIN_STABLE_OUTPUT_DECODE_RATIO_PERMILLE = 900;
    public static final int MAX_STABLE_DROP_RATE_PERMILLE = 50;
    public static final long MAX_STABLE_DROPPED_FRAMES = 23L;
    public static final int MAX_SOFT_WIDTH = 1_920;
    public static final int MAX_SOFT_HEIGHT = 1_080;
    public static final int MAX_SOFT_FRAME_RATE_MILLI = 30_500;
    public static final long MAX_SOFT_BITRATE_BITS_PER_SECOND = 8_000_000L;
    public static final long MIN_SOFT_JAVA_HEADROOM_BYTES = 128L * 1024 * 1024;

    private IjkRuntimeProfilePolicy() {
    }

    public static FailureAssessment assessFailure(FailureInput input) {
        FailureInput current = input == null ? FailureInput.unknown() : input;
        if (current.stage() == PlaybackErrorClassifier.Stage.LOCAL_ENDPOINT
                || current.stage() == PlaybackErrorClassifier.Stage.NETWORK_IO) {
            return FailureAssessment.hold(FailureKind.NETWORK, Reason.NETWORK_FAILURE);
        }
        if (current.stage() == PlaybackErrorClassifier.Stage.DRM) {
            return FailureAssessment.hold(FailureKind.DRM, Reason.DRM_FAILURE);
        }
        if (current.stage() == PlaybackErrorClassifier.Stage.DECODER
                || current.stage() == PlaybackErrorClassifier.Stage.OUTPUT) {
            return new FailureAssessment(
                    FailureKind.DECODER,
                    true,
                    current.profileAvailable(),
                    current.stage() == PlaybackErrorClassifier.Stage.OUTPUT
                            ? Reason.OUTPUT_FAILURE : Reason.DECODER_FAILURE);
        }
        if (current.stage() == PlaybackErrorClassifier.Stage.MEDIA_PARSING) {
            boolean unsupported = current.errorCode()
                    == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
                    || current.errorCode()
                    == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                    || current.errorCode()
                    == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
                    || current.ijkWhat() == IMediaPlayer.MEDIA_ERROR_UNSUPPORTED;
            return new FailureAssessment(
                    unsupported ? FailureKind.UNSUPPORTED : FailureKind.MEDIA,
                    true,
                    unsupported && current.profileAvailable(),
                    unsupported ? Reason.UNSUPPORTED_MEDIA : Reason.MEDIA_FAILURE);
        }
        if (!current.path().ijk()) {
            return FailureAssessment.hold(
                    FailureKind.UNKNOWN, Reason.EXTERNAL_UNKNOWN_FAILURE);
        }
        if (isNetworkLikeIjkError(current)) {
            return FailureAssessment.hold(
                    FailureKind.NETWORK, Reason.NETWORK_FAILURE);
        }
        if (current.firstFrame()
                && current.actualDecodeMode()
                != PlaybackAutoContext.DecodeMode.UNKNOWN) {
            return new FailureAssessment(
                    FailureKind.RUNTIME,
                    true,
                    current.profileAvailable(),
                    Reason.IJK_RUNTIME_FAILURE);
        }
        return new FailureAssessment(
                FailureKind.STARTUP,
                true,
                false,
                current.prepared()
                        ? Reason.IJK_PREPARED_FAILURE : Reason.IJK_STARTUP_FAILURE);
    }

    private static boolean isNetworkLikeIjkError(FailureInput input) {
        return input.errorCode() == PlaybackException.ERROR_CODE_TIMEOUT
                || input.errorCode()
                == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                || input.errorCode()
                == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                || input.ijkWhat() == IMediaPlayer.MEDIA_ERROR_IO
                || input.ijkWhat() == IMediaPlayer.MEDIA_ERROR_TIMED_OUT;
    }

    public static boolean allowIjkSoftware(SoftEligibilityInput input) {
        SoftEligibilityInput current = input == null
                ? SoftEligibilityInput.unknown() : input;
        if (!current.automatic() || !current.videoFactsUsable()
                || !current.systemFactsUsable()) return false;
        int longEdge = Math.max(current.width(), current.height());
        int shortEdge = Math.min(current.width(), current.height());
        if (shortEdge <= 0
                || longEdge > MAX_SOFT_WIDTH
                || shortEdge > MAX_SOFT_HEIGHT) return false;
        if (current.frameRateMilli() <= 0
                || current.frameRateMilli() > MAX_SOFT_FRAME_RATE_MILLI) {
            return false;
        }
        if (current.bitrateBitsPerSecond() <= 0
                || current.bitrateBitsPerSecond()
                > MAX_SOFT_BITRATE_BITS_PER_SECOND) return false;
        if (current.lowRamDevice()
                || current.javaHeadroomBytes() < MIN_SOFT_JAVA_HEADROOM_BYTES) {
            return false;
        }
        if (current.memoryPressure()
                == PlaybackAutoContext.MemoryPressure.CRITICAL) return false;
        return current.thermalState()
                == PlaybackAutoContext.ThermalState.NOMINAL
                || current.thermalState()
                == PlaybackAutoContext.ThermalState.MODERATE;
    }

    public static Path nextFallback(
            Path current,
            Set<Path> visited,
            boolean softEligible,
            Path verifiedPreferred) {
        Path applied = current == null ? Path.IJK_HARD : current;
        Set<Path> seen = visited == null ? Set.of() : visited;
        if (verifiedPreferred != null
                && verifiedPreferred != applied
                && !seen.contains(verifiedPreferred)
                && (verifiedPreferred != Path.IJK_SOFT || softEligible)) {
            return verifiedPreferred;
        }
        if (applied == Path.IJK_HARD && softEligible
                && !seen.contains(Path.IJK_SOFT)) {
            return Path.IJK_SOFT;
        }
        if (applied != Path.EXO && !seen.contains(Path.EXO)) return Path.EXO;
        if (applied != Path.MPV && !seen.contains(Path.MPV)) return Path.MPV;
        return null;
    }

    public static HealthAssessment assessHealth(HealthInput input) {
        HealthInput current = input == null ? HealthInput.inactive() : input;
        if (!current.active()) return HealthAssessment.hold(HealthReason.INACTIVE);
        if (!current.firstFrame()) {
            return HealthAssessment.hold(HealthReason.FIRST_FRAME_MISSING);
        }
        if (current.observedDurationMs() < STABLE_PLAYBACK_WINDOW_MS) {
            return HealthAssessment.hold(HealthReason.CONFIRMING);
        }
        if (current.rebufferCount() > MAX_STABLE_REBUFFER_COUNT) {
            return HealthAssessment.hold(HealthReason.REBUFFERED);
        }

        int renderedRatio = -1;
        boolean frameRateUsable = false;
        boolean frameRateHealthy = false;
        if (current.targetFrameRateUsable()
                && finitePositive(current.targetFrameRate())
                && current.outputFrameRateUsable()
                && finitePositive(current.outputFrameRate())) {
            renderedRatio = ratioPermille(
                    current.outputFrameRate(), current.targetFrameRate());
            frameRateUsable = renderedRatio >= 0;
            frameRateHealthy = renderedRatio
                    >= MIN_STABLE_RENDERED_RATIO_PERMILLE;
        } else if (current.path().ijk()
                && current.decodeFrameRateUsable()
                && finitePositive(current.decodeFrameRate())
                && current.outputFrameRateUsable()
                && finitePositive(current.outputFrameRate())) {
            renderedRatio = ratioPermille(
                    current.outputFrameRate(), current.decodeFrameRate());
            frameRateUsable = renderedRatio >= 0;
            frameRateHealthy = renderedRatio
                    >= MIN_STABLE_OUTPUT_DECODE_RATIO_PERMILLE;
        }
        if (!frameRateUsable) {
            return new HealthAssessment(
                    false, HealthReason.FRAME_EVIDENCE_UNKNOWN, renderedRatio);
        }
        if (!frameRateHealthy) {
            return new HealthAssessment(
                    false, HealthReason.FRAME_RATE_UNHEALTHY, renderedRatio);
        }

        if (current.path().ijk()) {
            if (!current.dropRateUsable()) {
                return new HealthAssessment(
                        false, HealthReason.DROP_EVIDENCE_UNKNOWN, renderedRatio);
            }
            if (current.dropRatePermille() > MAX_STABLE_DROP_RATE_PERMILLE) {
                return new HealthAssessment(
                        false, HealthReason.DROPS_UNHEALTHY, renderedRatio);
            }
        } else {
            if (!current.droppedFramesUsable()) {
                return new HealthAssessment(
                        false, HealthReason.DROP_EVIDENCE_UNKNOWN, renderedRatio);
            }
            if (current.droppedFrames() > MAX_STABLE_DROPPED_FRAMES) {
                return new HealthAssessment(
                        false, HealthReason.DROPS_UNHEALTHY, renderedRatio);
            }
        }
        return new HealthAssessment(true, HealthReason.STABLE, renderedRatio);
    }

    private static int ratioPermille(float numerator, float denominator) {
        if (!finitePositive(numerator) || !finitePositive(denominator)) return -1;
        double ratio = numerator / (double) denominator * 1_000d;
        if (!Double.isFinite(ratio)) return -1;
        return (int) Math.max(0, Math.min(10_000, Math.round(ratio)));
    }

    private static boolean finitePositive(float value) {
        return Float.isFinite(value) && value > 0;
    }

    public enum Path {
        IJK_HARD("ijk-hard", true),
        IJK_SOFT("ijk-soft", true),
        MPV("mpv", false),
        EXO("exo", false);

        private final String label;
        private final boolean ijk;

        Path(String label, boolean ijk) {
            this.label = label;
            this.ijk = ijk;
        }

        public String label() {
            return label;
        }

        public boolean ijk() {
            return ijk;
        }
    }

    public enum FailureKind {
        NONE("none"),
        NETWORK("network"),
        DRM("drm"),
        MEDIA("media"),
        UNSUPPORTED("unsupported"),
        DECODER("decoder"),
        STARTUP("startup"),
        RUNTIME("runtime"),
        UNKNOWN("unknown");

        private final String label;

        FailureKind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Reason {
        NETWORK_FAILURE("network-failure"),
        DRM_FAILURE("drm-failure"),
        DECODER_FAILURE("decoder-failure"),
        OUTPUT_FAILURE("output-failure"),
        UNSUPPORTED_MEDIA("unsupported-media"),
        MEDIA_FAILURE("media-failure"),
        IJK_RUNTIME_FAILURE("ijk-runtime-failure"),
        IJK_PREPARED_FAILURE("ijk-prepared-failure"),
        IJK_STARTUP_FAILURE("ijk-startup-failure"),
        EXTERNAL_UNKNOWN_FAILURE("external-unknown-failure");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum HealthReason {
        INACTIVE("inactive"),
        FIRST_FRAME_MISSING("first-frame-missing"),
        CONFIRMING("confirming"),
        REBUFFERED("rebuffered"),
        FRAME_EVIDENCE_UNKNOWN("frame-evidence-unknown"),
        FRAME_RATE_UNHEALTHY("frame-rate-unhealthy"),
        DROP_EVIDENCE_UNKNOWN("drop-evidence-unknown"),
        DROPS_UNHEALTHY("drops-unhealthy"),
        STABLE("stable");

        private final String label;

        HealthReason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record FailureInput(
            Path path,
            PlaybackErrorClassifier.Stage stage,
            int errorCode,
            int ijkWhat,
            int ijkExtra,
            boolean prepared,
            boolean firstFrame,
            PlaybackAutoContext.DecodeMode actualDecodeMode,
            boolean profileAvailable) {

        public FailureInput {
            path = path == null ? Path.IJK_HARD : path;
            stage = stage == null ? PlaybackErrorClassifier.Stage.UNKNOWN : stage;
            actualDecodeMode = actualDecodeMode == null
                    ? PlaybackAutoContext.DecodeMode.UNKNOWN : actualDecodeMode;
        }

        static FailureInput unknown() {
            return new FailureInput(
                    Path.IJK_HARD,
                    PlaybackErrorClassifier.Stage.UNKNOWN,
                    PlaybackException.ERROR_CODE_UNSPECIFIED,
                    0,
                    0,
                    false,
                    false,
                    PlaybackAutoContext.DecodeMode.UNKNOWN,
                    false);
        }
    }

    public record FailureAssessment(
            FailureKind kind,
            boolean fallbackEligible,
            boolean persistable,
            Reason reason) {

        public FailureAssessment {
            kind = kind == null ? FailureKind.UNKNOWN : kind;
            reason = reason == null ? Reason.EXTERNAL_UNKNOWN_FAILURE : reason;
        }

        static FailureAssessment hold(FailureKind kind, Reason reason) {
            return new FailureAssessment(kind, false, false, reason);
        }
    }

    public record SoftEligibilityInput(
            boolean automatic,
            boolean videoFactsUsable,
            int width,
            int height,
            int frameRateMilli,
            long bitrateBitsPerSecond,
            boolean systemFactsUsable,
            boolean lowRamDevice,
            long javaHeadroomBytes,
            PlaybackAutoContext.MemoryPressure memoryPressure,
            PlaybackAutoContext.ThermalState thermalState) {

        public SoftEligibilityInput {
            width = Math.max(0, width);
            height = Math.max(0, height);
            frameRateMilli = Math.max(0, frameRateMilli);
            bitrateBitsPerSecond = Math.max(0, bitrateBitsPerSecond);
            javaHeadroomBytes = Math.max(0, javaHeadroomBytes);
            memoryPressure = memoryPressure == null
                    ? PlaybackAutoContext.MemoryPressure.UNKNOWN : memoryPressure;
            thermalState = thermalState == null
                    ? PlaybackAutoContext.ThermalState.UNKNOWN : thermalState;
        }

        static SoftEligibilityInput unknown() {
            return new SoftEligibilityInput(
                    false, false, 0, 0, 0, 0,
                    false, true, 0,
                    PlaybackAutoContext.MemoryPressure.UNKNOWN,
                    PlaybackAutoContext.ThermalState.UNKNOWN);
        }
    }

    public record HealthInput(
            Path path,
            boolean active,
            boolean firstFrame,
            long observedDurationMs,
            int rebufferCount,
            boolean targetFrameRateUsable,
            float targetFrameRate,
            boolean decodeFrameRateUsable,
            float decodeFrameRate,
            boolean outputFrameRateUsable,
            float outputFrameRate,
            boolean dropRateUsable,
            int dropRatePermille,
            boolean droppedFramesUsable,
            long droppedFrames) {

        public HealthInput {
            path = path == null ? Path.IJK_HARD : path;
            observedDurationMs = Math.max(0, observedDurationMs);
            rebufferCount = Math.max(0, rebufferCount);
            targetFrameRate = finitePositive(targetFrameRate)
                    ? targetFrameRate : 0f;
            decodeFrameRate = finitePositive(decodeFrameRate)
                    ? decodeFrameRate : 0f;
            outputFrameRate = finitePositive(outputFrameRate)
                    ? outputFrameRate : 0f;
            dropRatePermille = Math.max(0, dropRatePermille);
            droppedFrames = Math.max(0, droppedFrames);
        }

        static HealthInput inactive() {
            return new HealthInput(
                    Path.IJK_HARD, false, false, 0, 0,
                    false, 0f, false, 0f, false, 0f,
                    false, 0, false, 0);
        }
    }

    public record HealthAssessment(
            boolean stable,
            HealthReason reason,
            int renderedRatioPermille) {

        public HealthAssessment {
            reason = reason == null ? HealthReason.INACTIVE : reason;
            renderedRatioPermille = Math.max(-1, renderedRatioPermille);
        }

        static HealthAssessment hold(HealthReason reason) {
            return new HealthAssessment(false, reason, -1);
        }
    }
}
