package com.fongmi.android.tv.player.mpv;

public final class MpvAutoOutputPolicy {

    private static final long HIGH_RESOLUTION_AREA = 2560L * 1440L;
    private static final int HIGH_RESOLUTION_EDGE = 2560;

    private MpvAutoOutputPolicy() {
    }

    public static Decision evaluate(int width, int height, boolean hardDecode, boolean leanback, boolean subtitleActive, boolean lutOrFilterActive, boolean customGpuProcessing) {
        if (!leanback) return new Decision(false, "not-tv");
        if (!hardDecode) return new Decision(false, "software-decode");
        if (!isHighResolution(width, height)) return new Decision(false, "below-high-resolution-threshold");
        if (subtitleActive) return new Decision(false, "subtitle-active");
        if (lutOrFilterActive) return new Decision(false, "lut-or-filter-active");
        if (customGpuProcessing) return new Decision(false, "custom-gpu-processing");
        return new Decision(true, "eligible-high-resolution-hardware-decode");
    }

    public static Transition transition(boolean eligible, boolean currentlyDirect) {
        if (eligible) return currentlyDirect ? Transition.KEEP_SURFACE_DIRECT : Transition.ENTER_SURFACE_DIRECT;
        return currentlyDirect ? Transition.LEAVE_SURFACE_DIRECT : Transition.KEEP_GPU;
    }

    public static boolean canEvaluateWithoutTracks(int width, int height, boolean externalSubtitleActive) {
        return !externalSubtitleActive && isHighResolution(width, height);
    }

    public static boolean requiresGpuSubtitle(boolean externalSubtitleActive, boolean userRequestedSubtitle) {
        return externalSubtitleActive || userRequestedSubtitle;
    }

    public enum Transition {
        KEEP_GPU,
        ENTER_SURFACE_DIRECT,
        KEEP_SURFACE_DIRECT,
        LEAVE_SURFACE_DIRECT
    }

    static boolean isHighResolution(int width, int height) {
        if (width <= 0 || height <= 0) return false;
        return Math.max(width, height) >= HIGH_RESOLUTION_EDGE && (long) width * height >= HIGH_RESOLUTION_AREA;
    }

    public record Decision(boolean eligible, String reason) {
    }
}
