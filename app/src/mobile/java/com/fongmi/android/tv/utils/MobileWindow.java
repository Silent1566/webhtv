package com.fongmi.android.tv.utils;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;

public final class MobileWindow {

    private MobileWindow() {
    }

    public static boolean isWide(Context context) {
        return getWidth(context) > getHeight(context);
    }

    public static int getWidth(Context context) {
        return getBounds(context).width();
    }

    public static int getHeight(Context context) {
        return getBounds(context).height();
    }

    private static Rect getBounds(Context context) {
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect bounds = manager.getCurrentWindowMetrics().getBounds();
            if (bounds.width() > 0 && bounds.height() > 0) return bounds;
        }
        DisplayMetrics metrics = new DisplayMetrics();
        if (manager != null) manager.getDefaultDisplay().getMetrics(metrics);
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) metrics = ResUtil.getDisplayMetrics(context);
        return new Rect(0, 0, metrics.widthPixels, metrics.heightPixels);
    }
}
