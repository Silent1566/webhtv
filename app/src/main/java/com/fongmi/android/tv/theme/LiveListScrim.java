package com.fongmi.android.tv.theme;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

/**
 * 直播页频道抽屉（列表）的黑色遮盖层。
 *
 * <p>强度来自主题 profile 的 {@code liveListScrimOpacity}（见
 * {@link ThemeController#configuredLiveListScrimOpacity(boolean)}）：未设置时回落到
 * {@link #DEFAULT_OPACITY}，三段 alpha 与 {@code shape_live_list.xml} 的
 * {@code black_80 → black_40 → black_05} 逐值一致。色相恒为纯黑：主题 profile 只能缩放
 * alpha，不能改色相，{@code shape_live_focused.xml} 的焦点环对比度前提因此始终成立。
 */
public final class LiveListScrim {

    /** 未设置主题槽时的默认强度；0.80 对应左侧 80% / 中间 40% / 右侧 5% 黑。 */
    public static final float DEFAULT_OPACITY = 0.80f;

    /** 与 shape_live_list.xml 的 start/center/endColor 对应的三段 alpha 基准。 */
    private static final float[] BASE_ALPHAS = {0.80f, 0.40f, 0.05f};

    private LiveListScrim() {
    }

    /** The scrim for the current theme snapshot, or {@code null} when unavailable. */
    public static Drawable create(Context context) {
        if (context == null) return null;
        float opacity = resolveOpacity(ThemeController.configuredLiveListScrimOpacity(!ThemeController.isNight(context)));
        return new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, gradientColors(opacity));
    }

    static float resolveOpacity(Float configured) {
        float value = configured == null ? DEFAULT_OPACITY : configured;
        return Math.max(0f, Math.min(1f, value));
    }

    static int[] gradientColors(float opacity) {
        float clamped = Math.max(0f, Math.min(1f, opacity));
        float scale = clamped / DEFAULT_OPACITY;
        int[] colors = new int[BASE_ALPHAS.length];
        for (int index = 0; index < BASE_ALPHAS.length; index++) {
            colors[index] = withAlpha(Math.min(1f, BASE_ALPHAS[index] * scale));
        }
        return colors;
    }

    static int withAlpha(float opacity) {
        int alpha = Math.max(0, Math.min(255, Math.round(opacity * 255f)));
        return alpha << 24;
    }
}
