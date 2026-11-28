package com.fongmi.android.tv.ui.custom;

/**
 * 跨 Activity 的返回焦点意图。
 *
 * <p>TV 端从设置页按返回键回到首页时，系统默认焦点会落到布局第一个可聚焦控件，
 * 而不是用户来时的 Func 行「点播」。设置页在退出前写入「回到点播」意图，
 * 首页 onResume 消费后把焦点明确送回点播 chip。
 *
 * <p>这是一个一次性意图：消费后立即清除，避免下次正常进入首页也被强行改焦点。
 */
public final class ReturnFocusHint {

    private static boolean toVod;

    private ReturnFocusHint() {
    }

    /** 设置页退出前调用：下次首页回到前台时把焦点送回「点播」。 */
    public static void requestVodOnHomeReturn() {
        toVod = true;
    }

    /** 首页 onResume 调用：取出并清除意图。 */
    public static boolean consumeVodOnHomeReturn() {
        boolean value = toVod;
        toVod = false;
        return value;
    }

    /** 页面被销毁/切换时丢弃未消费的意图，避免陈旧意图影响后续进入首页。 */
    public static void clear() {
        toVod = false;
    }
}
