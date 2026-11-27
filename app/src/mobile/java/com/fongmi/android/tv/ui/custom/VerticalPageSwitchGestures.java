package com.fongmi.android.tv.ui.custom;

/**
 * 竖屏短剧整页切集的纯判定逻辑：不依赖 Android 框架，便于单元测试。
 * 阈值语义与 {@link VerticalPageSwitcher} 的拖动行为一致。
 */
public final class VerticalPageSwitchGestures {

    /** 速度阈值（px/s）：快速上滑/下滑即使位移不足也切集。 */
    private static final float FLICK_VELOCITY = 350f;
    /** 竖向位移判定的页面高度占比。 */
    private static final float DISTANCE_RATIO = 0.18f;

    private VerticalPageSwitchGestures() {
    }

    /** 只启用页面中间一半宽度，避免抢走左右两侧的亮度/音量手势。 */
    public static boolean arms(float rawX, int pageWidth) {
        return pageWidth > 0 && rawX > pageWidth * 0.25f && rawX < pageWidth * 0.75f;
    }

    /** 竖向位移达到 slop 且明显大于水平位移，才判定为上下翻页手势。 */
    public static boolean startsDrag(float dx, float dy, float slopPx) {
        return Math.abs(dy) >= slopPx && Math.abs(dy) >= Math.abs(dx);
    }

    /** 位移或速度任一达标即切集；阈值取页面比例与 dp 上限中的较小者。 */
    public static boolean commits(float dragY, float velocity, int pageHeight, float thresholdPx) {
        float distance = Math.min(pageHeight * DISTANCE_RATIO, thresholdPx);
        return Math.abs(dragY) > distance || Math.abs(velocity) > FLICK_VELOCITY;
    }
}
