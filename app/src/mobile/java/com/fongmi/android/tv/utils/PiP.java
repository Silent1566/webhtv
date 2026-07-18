package com.fongmi.android.tv.utils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.util.Rational;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.media3.ui.R;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.receiver.ActionReceiver;
import com.fongmi.android.tv.setting.PlayerSetting;

import java.util.ArrayList;
import java.util.List;

public class PiP {

    private PictureInPictureParams.Builder builder;

    public static boolean noPiP() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !App.get().getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
    }

    public PiP() {
        if (noPiP()) return;
        this.builder = new PictureInPictureParams.Builder();
    }

    @TargetApi(Build.VERSION_CODES.O)
    private RemoteAction buildRemoteAction(Activity activity, @DrawableRes int icon, @StringRes int title, String action) {
        return new RemoteAction(Icon.createWithResource(activity, icon), activity.getString(title), "", ActionReceiver.getPendingIntent(activity, action));
    }

    private RemoteAction getPlayPauseAction(Activity activity, boolean play) {
        if (play) return buildRemoteAction(activity, R.drawable.exo_icon_pause, R.string.exo_controls_pause_description, ActionEvent.PAUSE);
        return buildRemoteAction(activity, R.drawable.exo_icon_play, R.string.exo_controls_play_description, ActionEvent.PLAY);
    }

    public void update(Activity activity, View view) {
        try {
            if (noPiP()) return;
            Rect rect = new Rect();
            view.getGlobalVisibleRect(rect);
            builder.setSourceRectHint(rect);
            setAutoEnter();
            activity.setPictureInPictureParams(builder.build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void update(Activity activity, int width, int height, int scale) {
        try {
            if (noPiP()) return;
            setAspectRatio(width, height, scale);
            setAutoEnter();
            activity.setPictureInPictureParams(builder.build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void update(Activity activity, boolean play) {
        try {
            if (noPiP()) return;
            List<RemoteAction> actions = new ArrayList<>();
            actions.add(buildRemoteAction(activity, com.fongmi.android.tv.R.drawable.ic_action_audio, R.string.exo_controls_hide, ActionEvent.AUDIO));
            actions.add(getPlayPauseAction(activity, play));
            actions.add(buildRemoteAction(activity, R.drawable.exo_icon_next, R.string.exo_controls_next_description, ActionEvent.NEXT));
            setAutoEnter();
            activity.setPictureInPictureParams(builder.setActions(actions).build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void disableAutoEnter(Activity activity) {
        try {
            if (noPiP() || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
            builder.setAutoEnterEnabled(false);
            activity.setPictureInPictureParams(builder.build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean enter(Activity activity, int width, int height, int scale) {
        return enter(activity, width, height, scale, false);
    }

    public boolean enter(Activity activity, int width, int height, int scale, boolean force) {
        try {
            if (noPiP() || activity.isInPictureInPictureMode() || (!force && !PlayerSetting.isBackgroundPiP())) return false;
            setAspectRatio(width, height, scale);
            setAutoEnter();
            return activity.enterPictureInPictureMode(builder.build());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void setAutoEnter() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(PlayerSetting.isBackgroundPiP());
        }
    }

    private void setAspectRatio(int width, int height, int scale) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setSeamlessResizeEnabled(true);
        if (scale == 1) builder.setAspectRatio(new Rational(16, 9));
        else if (scale == 2) builder.setAspectRatio(new Rational(4, 3));
        else builder.setAspectRatio(getRational(width, height));
    }

    private Rational getRational(int width, int height) {
        if (width <= 0 || height <= 0) return new Rational(16, 9);
        Rational limitWide = new Rational(239, 100);
        Rational limitTall = new Rational(100, 239);
        Rational rational = new Rational(width, height);
        if (rational.isInfinite()) return new Rational(16, 9);
        if (rational.floatValue() > limitWide.floatValue()) return limitWide;
        if (rational.floatValue() < limitTall.floatValue()) return limitTall;
        return rational;
    }
}
