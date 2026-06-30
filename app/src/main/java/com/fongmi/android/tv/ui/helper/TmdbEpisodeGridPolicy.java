package com.fongmi.android.tv.ui.helper;

public final class TmdbEpisodeGridPolicy {

    public static final int WRAP_CONTENT = -2;
    public static final int GRID_CARD_HEIGHT_DP = 118;
    public static final int GRID_CARD_BOTTOM_MARGIN_DP = 10;
    public static final int NATIVE_GRID_CARD_HEIGHT_DP = 248;
    public static final int NATIVE_MOBILE_GRID_CARD_HEIGHT_DP = 190;
    public static final int NATIVE_GRID_CARD_BOTTOM_MARGIN_DP = 16;
    public static final int NATIVE_GRID_SCRIM_HEIGHT_DP = 148;
    public static final int NATIVE_MOBILE_GRID_SCRIM_HEIGHT_DP = 104;
    public static final int GRID_FALLBACK_IMAGE_LIMIT = 24;

    private static final int MAX_VISIBLE_ROWS = 3;

    private TmdbEpisodeGridPolicy() {
    }

    public static Layout layout(boolean gridMode, int itemCount, int spanCount, int rowHeightPx) {
        if (!gridMode || itemCount <= 0 || spanCount <= 0 || rowHeightPx <= 0) return Layout.wrap();
        int rows = (itemCount + spanCount - 1) / spanCount;
        if (rows <= MAX_VISIBLE_ROWS) return Layout.wrap();
        return new Layout(rowHeightPx * MAX_VISIBLE_ROWS, true);
    }

    public static int nativeEnhancedSpanCount(boolean mobile, boolean pad, boolean landscape, int screenWidthDp) {
        if (mobile) return pad ? landscape ? 4 : 3 : landscape ? 3 : 2;
        return tvAdaptiveSpanCount(screenWidthDp);
    }

    public static int tvAdaptiveSpanCount(int screenWidthDp) {
        if (screenWidthDp >= 1600) return 5;
        if (screenWidthDp >= 1200) return 4;
        return 3;
    }

    public static int nativeGridCardHeightDp(boolean phoneWidth) {
        return phoneWidth ? NATIVE_MOBILE_GRID_CARD_HEIGHT_DP : NATIVE_GRID_CARD_HEIGHT_DP;
    }

    public static int nativeGridScrimHeightDp(boolean phoneWidth) {
        return phoneWidth ? NATIVE_MOBILE_GRID_SCRIM_HEIGHT_DP : NATIVE_GRID_SCRIM_HEIGHT_DP;
    }

    public static boolean shouldUseFallbackImage(boolean gridMode, int itemCount) {
        return gridMode && itemCount <= GRID_FALLBACK_IMAGE_LIMIT;
    }

    public static boolean shouldUseFallbackImage(boolean gridMode, int itemCount, boolean hasTmdbEpisodeData) {
        return gridMode;
    }

    public record Layout(int heightPx, boolean nestedScrolling) {

        static Layout wrap() {
            return new Layout(WRAP_CONTENT, false);
        }
    }
}
