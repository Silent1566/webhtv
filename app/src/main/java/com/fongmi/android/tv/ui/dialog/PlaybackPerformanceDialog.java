package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;

public final class PlaybackPerformanceDialog extends DialogFragment {

    private Runnable callback;
    private LinearLayout list;

    public static void show(Fragment fragment, Runnable callback) {
        PlaybackPerformanceDialog dialog = new PlaybackPerformanceDialog();
        dialog.callback = callback;
        dialog.show(fragment.getChildFragmentManager(), PlaybackPerformanceDialog.class.getSimpleName());
    }

    public static void show(FragmentActivity activity, Runnable callback) {
        PlaybackPerformanceDialog dialog = new PlaybackPerformanceDialog();
        dialog.callback = callback;
        dialog.show(activity.getSupportFragmentManager(), PlaybackPerformanceDialog.class.getSimpleName());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        PlaybackPerformanceSetting.ensureInitialized();
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(createView(LayoutInflater.from(requireContext()))).create();
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        Window window = dialog == null ? null : dialog.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = (int) (ResUtil.getScreenWidth(requireContext()) * (ResUtil.isLand(requireContext()) ? 0.58f : 0.92f));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
    }

    private View createView(LayoutInflater inflater) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.shape_shell_proxy_dialog);
        root.setPadding(dp(22), dp(22), dp(22), dp(18));

        MaterialTextView title = new MaterialTextView(requireContext());
        title.setText(R.string.player_performance);
        title.setTextColor(Color.parseColor("#202124"));
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        actions.addView(actionButton(R.string.player_performance_recommended, view -> apply(true)), actionParams(true));
        actions.addView(actionButton(R.string.player_performance_compatible, view -> apply(false)), actionParams(false));
        actions.addView(actionButton(R.string.dialog_reset, view -> apply(PlaybackPerformanceSetting.isCompatible() ? false : true)), actionParams(false));
        LinearLayout.LayoutParams actionLayout = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        actionLayout.topMargin = dp(14);
        root.addView(actions, actionLayout);

        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.min(dp(460), Math.max(dp(300), ResUtil.getScreenHeight(requireContext()) * 2 / 3)));
        scrollParams.topMargin = dp(16);
        root.addView(scroll, scrollParams);
        refreshRows();
        return root;
    }

    private MaterialButton actionButton(int text, View.OnClickListener listener) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setAllCaps(false);
        button.setText(text);
        button.setSingleLine(true);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(14);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(38));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setFocusable(true);
        button.setFocusableInTouchMode(Util.isLeanback());
        button.setCornerRadius(dp(6));
        button.setTextColor(ColorStateList.valueOf(Color.parseColor("#174EA6")));
        button.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
        button.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#8AB4F8")));
        button.setStrokeWidth(dp(1));
        button.setOnFocusChangeListener((view, hasFocus) -> styleAction(button, hasFocus));
        button.setOnClickListener(listener);
        return button;
    }

    private void styleAction(MaterialButton button, boolean focused) {
        button.setTextColor(ColorStateList.valueOf(Color.parseColor(focused ? "#FFFFFF" : "#174EA6")));
        button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(focused ? "#1A73E8" : "#FFFFFF")));
        button.setStrokeColor(ColorStateList.valueOf(Color.parseColor(focused ? "#1A73E8" : "#8AB4F8")));
        button.setStrokeWidth(dp(1));
    }

    private LinearLayout.LayoutParams actionParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        params.leftMargin = first ? 0 : dp(8);
        return params;
    }

    private void apply(boolean recommended) {
        if (recommended) PlaybackPerformanceSetting.applyRecommended();
        else PlaybackPerformanceSetting.applyCompatible();
        refresh();
    }

    private void refresh() {
        refreshRows();
        if (callback != null) callback.run();
    }

    private void refreshRows() {
        if (list == null) return;
        list.removeAllViews();
        addHeader("基础性能");
        addRow("性能配置", PlaybackPerformanceSetting.getProfileName(), null);
        addRow("渲染方式", renderText(), this::toggleRender);
        addRow("视频轨道限制", onOff(PlaybackPerformanceSetting.isTrackLimitEnabled()), () -> {
            PlaybackPerformanceSetting.putTrackLimitEnabled(!PlaybackPerformanceSetting.isTrackLimitEnabled());
            refresh();
        });
        addRow("自适应降级", onOff(PlaybackPerformanceSetting.isAdaptiveDowngradeEnabled()), () -> {
            PlaybackPerformanceSetting.putAdaptiveDowngradeEnabled(!PlaybackPerformanceSetting.isAdaptiveDowngradeEnabled());
            refresh();
        });
        addRow("带宽估算", onOff(PlaybackPerformanceSetting.isBandwidthMeterEnabled()), () -> {
            PlaybackPerformanceSetting.putBandwidthMeterEnabled(!PlaybackPerformanceSetting.isBandwidthMeterEnabled());
            refresh();
        });
        addRow("隧道模式", onOff(PlayerSetting.isTunnel()), () -> {
            PlayerSetting.putTunnel(!PlayerSetting.isTunnel());
            PlaybackPerformanceSetting.markCustom();
            refresh();
        });

        addHeader("缓冲与缓存");
        addRow("缓冲时间", PlayerSetting.getBuffer() + "/10", this::cycleBuffer);
        addRow("缓冲容量", bufferBytesText(), this::cycleBufferBytes);
        addRow("回退缓冲", backBufferText(), this::cycleBackBuffer);
        addRow("播放缓存", playCacheText(), this::cyclePlayCache);
        addRow("只加载选中轨道", onOff(PlaybackPerformanceSetting.isLoadOnlySelectedTracksEnabled()), () -> {
            PlaybackPerformanceSetting.putLoadOnlySelectedTracksEnabled(!PlaybackPerformanceSetting.isLoadOnlySelectedTracksEnabled());
            refresh();
        });

        addHeader("预载");
        addRow("预载", onOff(PreloadSetting.isPreload()), () -> {
            PreloadSetting.putPreload(!PreloadSetting.isPreload());
            PlaybackPerformanceSetting.markCustom();
            refresh();
        });
        addRow("预载线程", PreloadSetting.getPreloadThreads() + " 条", this::cyclePreloadThreads);
        addRow("预载容量", PreloadSetting.getPreloadSizeMb() + "MB", this::cyclePreloadSize);
        addRow("预载时间", PreloadSetting.getPreloadTimeSeconds() + " 秒", this::cyclePreloadTime);

        addHeader("解码与渲染");
        addRow("MediaCodec 异步队列", onOff(PlaybackPerformanceSetting.isCodecAsyncQueueingEnabled()), () -> {
            PlaybackPerformanceSetting.putCodecAsyncQueueingEnabled(!PlaybackPerformanceSetting.isCodecAsyncQueueingEnabled());
            refresh();
        });
        addRow("Media3 动态调度", onOff(PlaybackPerformanceSetting.isDynamicSchedulingEnabled()), () -> {
            PlaybackPerformanceSetting.putDynamicSchedulingEnabled(!PlaybackPerformanceSetting.isDynamicSchedulingEnabled());
            refresh();
        });
        addRow("解码耗时推进", onOff(PlaybackPerformanceSetting.isVideoDurationProgressEnabled()), () -> {
            PlaybackPerformanceSetting.putVideoDurationProgressEnabled(!PlaybackPerformanceSetting.isVideoDurationProgressEnabled());
            refresh();
        });
        addRow("输入丢帧阈值", onOff(PlaybackPerformanceSetting.isLateDropInputEnabled()), () -> {
            PlaybackPerformanceSetting.putLateDropInputEnabled(!PlaybackPerformanceSetting.isLateDropInputEnabled());
            refresh();
        });
        addRow("Surface 固定尺寸", onOff(PlaybackPerformanceSetting.isSurfaceFixedSizeEnabled()), () -> {
            PlaybackPerformanceSetting.putSurfaceFixedSizeEnabled(!PlaybackPerformanceSetting.isSurfaceFixedSizeEnabled());
            refresh();
        });
        addRow("解码器兜底", onOff(PlaybackPerformanceSetting.isDecoderFallbackEnabled()), () -> {
            PlaybackPerformanceSetting.putDecoderFallbackEnabled(!PlaybackPerformanceSetting.isDecoderFallbackEnabled());
            refresh();
        });
        addRow("软解降负载", onOff(PlaybackPerformanceSetting.isSoftVideoTuneEnabled()), () -> {
            PlaybackPerformanceSetting.putSoftVideoTuneEnabled(!PlaybackPerformanceSetting.isSoftVideoTuneEnabled());
            refresh();
        });

        addHeader("音频");
        addRow("音频直通", onOff(PlayerSetting.isAudioPassThrough()), () -> {
            PlayerSetting.putAudioPassThrough(!PlayerSetting.isAudioPassThrough());
            PlaybackPerformanceSetting.markCustom();
            refresh();
        });
        addRow("AAC 优先", onOff(PlayerSetting.isPreferAAC()), () -> {
            PlayerSetting.putPreferAAC(!PlayerSetting.isPreferAAC());
            PlaybackPerformanceSetting.markCustom();
            refresh();
        });
        addRow("音频软解优先", onOff(PlayerSetting.isAudioPrefer()), () -> {
            PlayerSetting.putAudioPrefer(!PlayerSetting.isAudioPrefer());
            PlaybackPerformanceSetting.markCustom();
            refresh();
        });
        addRow("视频软解优先", onOff(PlayerSetting.isVideoPrefer()), () -> {
            PlayerSetting.putVideoPrefer(!PlayerSetting.isVideoPrefer());
            PlaybackPerformanceSetting.markCustom();
            refresh();
        });
    }

    private void addHeader(String text) {
        MaterialTextView header = new MaterialTextView(requireContext());
        header.setText(text);
        header.setTextColor(Color.parseColor("#5F6368"));
        header.setTextSize(13);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28));
        params.topMargin = list.getChildCount() == 0 ? 0 : dp(8);
        list.addView(header, params);
    }

    private void addRow(String label, String value, Runnable action) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setSingleLine(false);
        button.setMinHeight(dp(46));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setText(label + "    " + value);
        button.setTextSize(14);
        button.setTextColor(ColorStateList.valueOf(Color.parseColor("#202124")));
        button.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
        button.setCornerRadius(dp(6));
        button.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#C4C7C5")));
        button.setStrokeWidth(dp(1));
        button.setFocusable(true);
        button.setFocusableInTouchMode(Util.isLeanback());
        button.setEnabled(action != null);
        button.setOnFocusChangeListener((view, hasFocus) -> styleRow(button, action != null, hasFocus));
        if (action != null) button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        params.bottomMargin = dp(7);
        list.addView(button, params);
    }

    private void styleRow(MaterialButton button, boolean enabled, boolean focused) {
        int text = focused ? Color.WHITE : enabled ? Color.parseColor("#202124") : Color.parseColor("#5F6368");
        int bg = focused ? Color.parseColor("#1A73E8") : Color.WHITE;
        int stroke = focused ? Color.parseColor("#1A73E8") : Color.parseColor("#C4C7C5");
        button.setTextColor(ColorStateList.valueOf(text));
        button.setBackgroundTintList(ColorStateList.valueOf(bg));
        button.setStrokeColor(ColorStateList.valueOf(stroke));
        button.setStrokeWidth(dp(focused ? 2 : 1));
    }

    private void toggleRender() {
        PlayerSetting.putRender(PlayerSetting.getRender() == PlayerSetting.RENDER_SURFACE ? PlayerSetting.RENDER_TEXTURE : PlayerSetting.RENDER_SURFACE);
        PlaybackPerformanceSetting.markCustom();
        refresh();
    }

    private void cycleBuffer() {
        PlayerSetting.putBuffer(PlayerSetting.getBuffer() >= 10 ? 1 : PlayerSetting.getBuffer() + 1);
        PlaybackPerformanceSetting.markCustom();
        refresh();
    }

    private void cycleBufferBytes() {
        PlayerSetting.putBufferBytesOption((PlayerSetting.getBufferBytesOption() + 1) % 4);
        PlaybackPerformanceSetting.markCustom();
        refresh();
    }

    private void cycleBackBuffer() {
        PlayerSetting.putBackBufferOption((PlayerSetting.getBackBufferOption() + 1) % 4);
        PlaybackPerformanceSetting.markCustom();
        refresh();
    }

    private void cyclePlayCache() {
        PlayerSetting.putPlayCacheOption((PlayerSetting.getPlayCacheOption() + 1) % 5);
        PlaybackPerformanceSetting.markCustom();
        refresh();
    }

    private void cyclePreloadThreads() {
        int value = PreloadSetting.getPreloadThreads() + 1;
        if (value > PreloadSetting.MAX_THREADS) value = PreloadSetting.MIN_THREADS;
        PreloadSetting.putPreloadThreads(value);
        PlaybackPerformanceSetting.markCustom();
        refresh();
    }

    private void cyclePreloadSize() {
        int value = PreloadSetting.getPreloadSizeMb() + PreloadSetting.STEP_SIZE_MB;
        if (value > PreloadSetting.MAX_SIZE_MB) value = PreloadSetting.MIN_SIZE_MB;
        PreloadSetting.putPreloadSizeMb(value);
        PlaybackPerformanceSetting.markCustom();
        refresh();
    }

    private void cyclePreloadTime() {
        int value = PreloadSetting.getPreloadTimeSeconds() + PreloadSetting.STEP_TIME_SECONDS;
        if (value > PreloadSetting.MAX_TIME_SECONDS) value = PreloadSetting.MIN_TIME_SECONDS;
        PreloadSetting.putPreloadTimeSeconds(value);
        PlaybackPerformanceSetting.markCustom();
        refresh();
    }

    private String renderText() {
        return PlayerSetting.getRender() == PlayerSetting.RENDER_SURFACE ? "SurfaceView" : "TextureView";
    }

    private String bufferBytesText() {
        return switch (PlayerSetting.getBufferBytesOption()) {
            case 1 -> "64MB";
            case 2 -> "128MB";
            case 3 -> "256MB";
            default -> "自动";
        };
    }

    private String backBufferText() {
        return switch (PlayerSetting.getBackBufferOption()) {
            case 1 -> "15秒";
            case 2 -> "30秒";
            case 3 -> "60秒";
            default -> "关闭";
        };
    }

    private String playCacheText() {
        return switch (PlayerSetting.getPlayCacheOption()) {
            case 1 -> "256MB";
            case 2 -> "512MB";
            case 3 -> "1GB";
            case 4 -> "2GB";
            default -> "128MB";
        };
    }

    private String onOff(boolean value) {
        return value ? "开" : "关";
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
