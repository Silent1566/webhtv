package com.fongmi.android.tv.utils;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

/**
 * 语言 / 外观切换后的界面重建入口。
 *
 * <p>语言、主题、UI 缩放都会改变 Activity 的 Configuration 与资源表，必须重建界面才能生效。
 * 直接在各处调用 {@code recreate()} 有两个已知风险：一是调用点位于对话框的点击回调里，
 * 此时 Fragment 事务尚未真正提交，重建会连同待提交的对话框一起保存状态；二是某些设备上
 * 重建过程本身会抛出异常，用户看到的就是"点一下就退出"。
 *
 * <p>这里把重建统一推迟到下一帧执行，使其脱离对话框回调栈，并对重建失败做二次兜底
 * （重新拉起当前页面），保证最坏情况下也只是页面重开一次，而不是进程退出。
 */
public final class UiRestart {

    private UiRestart() {
    }

    public static void rebuild(Activity activity) {
        if (activity == null) return;
        Runnable task = () -> rebuildNow(activity);
        View decor = activity.getWindow() == null ? null : activity.getWindow().peekDecorView();
        if (decor != null) decor.post(task);
        else new Handler(Looper.getMainLooper()).post(task);
    }

    private static void rebuildNow(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try {
            activity.recreate();
        } catch (Throwable ignored) {
            relaunch(activity);
        }
    }

    private static void relaunch(Activity activity) {
        try {
            Intent intent = new Intent(activity, activity.getClass());
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
        } catch (Throwable ignored) {
        }
        try {
            activity.finish();
        } catch (Throwable ignored) {
        }
    }
}
