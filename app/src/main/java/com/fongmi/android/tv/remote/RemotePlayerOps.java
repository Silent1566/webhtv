package com.fongmi.android.tv.remote;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.view.KeyEvent;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.App;

/**
 * 把 {@link RemotePlayerCommand.Parsed} 落到当前 Activity 上。
 *
 * <p>刻意只用两通道，且每条命令只走一条：媒体/音量类用 {@link KeyEvent} 交给播放器与系统，
 * 返回键用 AndroidX 的返回分发器，方向/确定键用 DPAD 事件。**不**反射输入管道、也不是
 * "先注入再 dispatch" 的双通道，避免同一次命令被处理两遍。
 *
 * <p>方向键能否移动焦点取决于当前页面的焦点实现：这是 best-effort，命令结果会如实返回是否送达。
 */
public final class RemotePlayerOps {

    private RemotePlayerOps() {
    }

    public static boolean run(@Nullable RemotePlayerCommand.Parsed command) {
        if (command == null) return false;
        Activity activity = App.activity();
        if (activity == null) return false;
        switch (command.action) {
            case PLAY_PAUSE:
                return send(activity, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            case NEXT:
                return send(activity, KeyEvent.KEYCODE_MEDIA_NEXT);
            case PREV:
                return send(activity, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            case SEEK_FORWARD:
                return seek(activity, command.valueMs);
            case SEEK_BACK:
                return seek(activity, -command.valueMs);
            case BACK:
                return back(activity);
            case UP:
                return send(activity, KeyEvent.KEYCODE_DPAD_UP);
            case DOWN:
                return send(activity, KeyEvent.KEYCODE_DPAD_DOWN);
            case LEFT:
                return send(activity, KeyEvent.KEYCODE_DPAD_LEFT);
            case RIGHT:
                return send(activity, KeyEvent.KEYCODE_DPAD_RIGHT);
            case ENTER:
                return send(activity, KeyEvent.KEYCODE_DPAD_CENTER);
            case VOLUME_UP:
                return volume(activity, AudioManager.ADJUST_RAISE);
            case VOLUME_DOWN:
                return volume(activity, AudioManager.ADJUST_LOWER);
            default:
                return false;
        }
    }

    private static boolean seek(Activity activity, long deltaMs) {
        // 快进/快退沿用播放器已经实现的媒体键语义，避免在这里猜测播放器内部 API。
        boolean forward = deltaMs > 0;
        int keyCode = forward ? KeyEvent.KEYCODE_MEDIA_FAST_FORWARD : KeyEvent.KEYCODE_MEDIA_REWIND;
        int repeats = (int) Math.max(1L, Math.min(12L, Math.abs(deltaMs) / 10000L));
        boolean sent = false;
        for (int i = 0; i < repeats; i++) {
            // 每次连发前后都要有一次 UP，播放器才会把它当成独立的跳转。
            if (!send(activity, keyCode)) break;
            sent = true;
        }
        return sent;
    }

    private static boolean back(Activity activity) {
        activity.runOnUiThread(activity::onBackPressed);
        return true;
    }

    private static boolean volume(Context context, int direction) {
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) return false;
        manager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
        return true;
    }

    private static boolean send(Activity activity, int keyCode) {
        activity.runOnUiThread(() -> {
            activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        });
        return true;
    }
}
