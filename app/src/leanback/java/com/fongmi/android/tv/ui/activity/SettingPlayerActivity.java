package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivitySettingPlayerBinding;
import com.fongmi.android.tv.impl.BufferListener;
import com.fongmi.android.tv.impl.SpeedListener;
import com.fongmi.android.tv.impl.UaListener;
import com.fongmi.android.tv.player.lut.LutSetting;
import com.fongmi.android.tv.setting.PlayerButtonSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.BufferDialog;
import com.fongmi.android.tv.ui.dialog.LutDialog;
import com.fongmi.android.tv.ui.dialog.PlayerButtonConfigDialog;
import com.fongmi.android.tv.ui.dialog.SpeedDialog;
import com.fongmi.android.tv.ui.dialog.UaDialog;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.DecimalFormat;

public class SettingPlayerActivity extends BaseActivity implements UaListener, BufferListener, SpeedListener {

    private ActivitySettingPlayerBinding mBinding;
    private DecimalFormat format;
    private String[] backBuffer;
    private String[] bufferBytes;
    private String[] caption;
    private String[] kernel;
    private String[] playCache;
    private String[] render;
    private String[] scale;
    private String[] osd;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingPlayerActivity.class));
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingPlayerBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setVisible();
        format = new DecimalFormat("0.#");
        mBinding.render.requestFocus();
        mBinding.uaText.setText(Setting.getUa());
        mBinding.aacText.setText(getSwitch(PlayerSetting.isPreferAAC()));
        mBinding.tunnelText.setText(getSwitch(PlayerSetting.isTunnel()));
        mBinding.exo4kCompatText.setText(getSwitch(PlayerSetting.isExoEnhanced()));
        setPlayerButtonsText();
        mBinding.adblockText.setText(getSwitch(Setting.isAdblock()));
        mBinding.speedText.setText(format.format(PlayerSetting.getSpeed()));
        mBinding.bufferText.setText(String.valueOf(PlayerSetting.getBuffer()));
        mBinding.bufferBytesText.setText((bufferBytes = ResUtil.getStringArray(R.array.select_buffer_bytes))[PlayerSetting.getBufferBytesOption()]);
        mBinding.backBufferText.setText((backBuffer = ResUtil.getStringArray(R.array.select_back_buffer))[PlayerSetting.getBackBufferOption()]);
        mBinding.playCacheText.setText((playCache = ResUtil.getStringArray(R.array.select_play_cache))[PlayerSetting.getPlayCacheOption()]);
        setPreloadText();
        mBinding.autoChangeText.setText(getSwitch(PlayerSetting.isAutoChange()));
        mBinding.autoSkipIntroOutroText.setText(getSwitch(Setting.isAutoSkipIntroOutro()));
        mBinding.backgroundText.setText(getSwitch(PlayerSetting.isBackgroundOn()));
        mBinding.musicNotificationText.setText(getSwitch(PlayerSetting.isMusicNotification()));
        mBinding.audioBookNotificationText.setText(getSwitch(PlayerSetting.isAudioBookNotification()));
        mBinding.audioDecodeText.setText(getSwitch(PlayerSetting.isAudioPrefer()));
        mBinding.audioPassThroughText.setText(getSwitch(PlayerSetting.isAudioPassThrough()));
        mBinding.videoDecodeText.setText(getSwitch(PlayerSetting.isVideoPrefer()));
        mBinding.osdText.setText(getOsdText(osd = ResUtil.getStringArray(R.array.select_player_osd)));
        mBinding.kernelText.setText((kernel = ResUtil.getStringArray(R.array.select_player_kernel))[PlayerSetting.getPlayer()]);
        mBinding.scaleText.setText((scale = ResUtil.getStringArray(R.array.select_scale))[PlayerSetting.getScale()]);
        mBinding.lutText.setText(LutSetting.getSummary());
        mBinding.renderText.setText((render = ResUtil.getStringArray(R.array.select_render))[PlayerSetting.getRender()]);
        mBinding.captionText.setText((caption = ResUtil.getStringArray(R.array.select_caption))[PlayerSetting.isCaption() ? 1 : 0]);
    }

    @Override
    protected void initEvent() {
        mBinding.ua.setOnClickListener(this::onUa);
        mBinding.aac.setOnClickListener(this::setAAC);
        mBinding.kernel.setOnClickListener(this::setKernel);
        mBinding.scale.setOnClickListener(this::setScale);
        mBinding.lut.setOnClickListener(this::onLut);
        mBinding.osd.setOnClickListener(this::onOsd);
        mBinding.playerButtons.setOnClickListener(view -> PlayerButtonConfigDialog.show(this, this::setPlayerButtonsText));
        mBinding.speed.setOnClickListener(this::onSpeed);
        mBinding.buffer.setOnClickListener(this::onBuffer);
        mBinding.bufferBytes.setOnClickListener(this::setBufferBytes);
        mBinding.backBuffer.setOnClickListener(this::setBackBuffer);
        mBinding.playCache.setOnClickListener(this::setPlayCache);
        mBinding.preload.setOnClickListener(this::setPreload);
        mBinding.preloadThread.setOnClickListener(this::setPreloadThread);
        mBinding.preloadSize.setOnClickListener(this::setPreloadSize);
        mBinding.preloadTime.setOnClickListener(this::setPreloadTime);
        mBinding.autoChange.setOnClickListener(this::setAutoChange);
        mBinding.autoSkipIntroOutro.setOnClickListener(this::setAutoSkipIntroOutro);
        mBinding.render.setOnClickListener(this::setRender);
        mBinding.tunnel.setOnClickListener(this::setTunnel);
        mBinding.exo4kCompat.setOnClickListener(this::setExo4KCompat);
        mBinding.caption.setOnClickListener(this::setCaption);
        mBinding.adblock.setOnClickListener(this::setAdblock);
        mBinding.caption.setOnLongClickListener(this::onCaption);
        mBinding.background.setOnClickListener(this::onBackground);
        mBinding.musicNotification.setOnClickListener(this::setMusicNotification);
        mBinding.audioBookNotification.setOnClickListener(this::setAudioBookNotification);
        mBinding.audioDecode.setOnClickListener(this::setAudioDecode);
        mBinding.audioPassThrough.setOnClickListener(this::setAudioPassThrough);
        mBinding.videoDecode.setOnClickListener(this::setVideoDecode);
    }

    private void setVisible() {
        if (PlayerSetting.getBackground() == 2) PlayerSetting.putBackground(1);
        mBinding.caption.setVisibility(PlayerSetting.hasCaption() ? View.VISIBLE : View.GONE);
    }

    private void onUa(View view) {
        UaDialog.show(this);
    }

    @Override
    public void setUa(String ua) {
        mBinding.uaText.setText(ua);
        Setting.putUa(ua);
    }

    private void setAAC(View view) {
        PlayerSetting.putPreferAAC(!PlayerSetting.isPreferAAC());
        mBinding.aacText.setText(getSwitch(PlayerSetting.isPreferAAC()));
    }

    private void setKernel(View view) {
        int index = (PlayerSetting.getPlayer() + 1) % kernel.length;
        mBinding.kernelText.setText(kernel[index]);
        PlayerSetting.putPlayer(index);
    }

    private void setScale(View view) {
        int index = (PlayerSetting.getScale() + 1) % scale.length;
        mBinding.scaleText.setText(scale[index]);
        PlayerSetting.putScale(index);
    }

    private void onLut(View view) {
        LutDialog.show(this, null, () -> mBinding.lutText.setText(LutSetting.getSummary()));
    }

    private void onOsd(View view) {
        boolean[] checked = getOsdChecked();
        new MaterialAlertDialogBuilder(this).setTitle(R.string.player_osd).setNegativeButton(R.string.dialog_negative, null).setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
            setOsdChecked(checked);
            mBinding.osdText.setText(getOsdText(osd));
        }).setMultiChoiceItems(osd, checked, (dialog, which, isChecked) -> checked[which] = isChecked).show();
    }

    private void setPlayerButtonsText() {
        mBinding.playerButtonsText.setText(getString(R.string.player_button_config_summary, PlayerButtonSetting.getVisibleCount(), PlayerButtonSetting.getTotalCount()));
    }

    private boolean[] getOsdChecked() {
        return PlayerSetting.getDisplayChecked();
    }

    private void setOsdChecked(boolean[] checked) {
        PlayerSetting.putDisplayChecked(checked);
    }

    private String getOsdText(String[] items) {
        boolean[] checked = getOsdChecked();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < checked.length; i++) {
            if (!checked[i]) continue;
            if (builder.length() > 0) builder.append(" / ");
            builder.append(items[i]);
        }
        return builder.length() == 0 ? getString(R.string.setting_off) : builder.toString();
    }

    private void onSpeed(View view) {
        SpeedDialog.show(this);
    }

    @Override
    public void setSpeed(float speed) {
        mBinding.speedText.setText(format.format(speed));
        PlayerSetting.putSpeed(speed);
    }

    private void onBuffer(View view) {
        BufferDialog.show(this);
    }

    @Override
    public void setBuffer(int times) {
        mBinding.bufferText.setText(String.valueOf(times));
        PlayerSetting.putBuffer(times);
    }

    private void setBufferBytes(View view) {
        int index = (PlayerSetting.getBufferBytesOption() + 1) % bufferBytes.length;
        mBinding.bufferBytesText.setText(bufferBytes[index]);
        PlayerSetting.putBufferBytesOption(index);
    }

    private void setBackBuffer(View view) {
        int index = (PlayerSetting.getBackBufferOption() + 1) % backBuffer.length;
        mBinding.backBufferText.setText(backBuffer[index]);
        PlayerSetting.putBackBufferOption(index);
    }

    private void setPlayCache(View view) {
        int index = (PlayerSetting.getPlayCacheOption() + 1) % playCache.length;
        mBinding.playCacheText.setText(playCache[index]);
        PlayerSetting.putPlayCacheOption(index);
    }

    private void setPreload(View view) {
        PreloadSetting.putPreload(!PreloadSetting.isPreload());
        setPreloadText();
    }

    private void setPreloadThread(View view) {
        int value = PreloadSetting.getPreloadThreads() + 1;
        if (value > PreloadSetting.MAX_THREADS) value = PreloadSetting.MIN_THREADS;
        PreloadSetting.putPreloadThreads(value);
        setPreloadText();
    }

    private void setPreloadSize(View view) {
        int value = PreloadSetting.getPreloadSizeMb() + PreloadSetting.STEP_SIZE_MB;
        if (value > PreloadSetting.MAX_SIZE_MB) value = PreloadSetting.MIN_SIZE_MB;
        PreloadSetting.putPreloadSizeMb(value);
        setPreloadText();
    }

    private void setPreloadTime(View view) {
        int value = PreloadSetting.getPreloadTimeSeconds() + PreloadSetting.STEP_TIME_SECONDS;
        if (value > PreloadSetting.MAX_TIME_SECONDS) value = PreloadSetting.MIN_TIME_SECONDS;
        PreloadSetting.putPreloadTimeSeconds(value);
        setPreloadText();
    }

    private void setPreloadText() {
        boolean preload = PreloadSetting.isPreload();
        mBinding.preloadText.setText(getSwitch(preload));
        mBinding.preloadThread.setVisibility(preload ? View.VISIBLE : View.GONE);
        mBinding.preloadSize.setVisibility(preload ? View.VISIBLE : View.GONE);
        mBinding.preloadTime.setVisibility(preload ? View.VISIBLE : View.GONE);
        mBinding.preloadThreadText.setText(getString(R.string.player_preload_threads_value, PreloadSetting.getPreloadThreads()));
        mBinding.preloadSizeText.setText(FileUtil.byteCountToDisplaySize(PreloadSetting.getPreloadSizeBytes()));
        mBinding.preloadTimeText.setText(getString(R.string.player_preload_time_value, PreloadSetting.getPreloadTimeSeconds()));
    }

    private void setAutoChange(View view) {
        PlayerSetting.putAutoChange(!PlayerSetting.isAutoChange());
        mBinding.autoChangeText.setText(getSwitch(PlayerSetting.isAutoChange()));
    }

    private void setAutoSkipIntroOutro(View view) {
        Setting.putAutoSkipIntroOutro(!Setting.isAutoSkipIntroOutro());
        mBinding.autoSkipIntroOutroText.setText(getSwitch(Setting.isAutoSkipIntroOutro()));
    }

    private void setRender(View view) {
        if (PlayerSetting.isTunnel() && PlayerSetting.getRender() == 0) setTunnel(view);
        int index = (PlayerSetting.getRender() + 1) % render.length;
        mBinding.renderText.setText(render[index]);
        PlayerSetting.putRender(index);
        mBinding.exo4kCompatText.setText(getSwitch(PlayerSetting.isExoEnhanced()));
    }

    private void setTunnel(View view) {
        PlayerSetting.putTunnel(!PlayerSetting.isTunnel());
        mBinding.tunnelText.setText(getSwitch(PlayerSetting.isTunnel()));
        if (PlayerSetting.isTunnel() && PlayerSetting.getRender() == 1) setRender(view);
    }

    private void setExo4KCompat(View view) {
        PlayerSetting.putExoEnhanced(!PlayerSetting.isExoEnhanced());
        mBinding.exo4kCompatText.setText(getSwitch(PlayerSetting.isExoEnhanced()));
        mBinding.renderText.setText(render[PlayerSetting.getRender()]);
    }

    private void setCaption(View view) {
        PlayerSetting.putCaption(!PlayerSetting.isCaption());
        mBinding.captionText.setText(caption[PlayerSetting.isCaption() ? 1 : 0]);
    }

    private void setAdblock(View view) {
        Setting.putAdblock(!Setting.isAdblock());
        mBinding.adblockText.setText(getSwitch(Setting.isAdblock()));
    }

    private boolean onCaption(View view) {
        if (PlayerSetting.isCaption()) startActivity(new Intent(Settings.ACTION_CAPTIONING_SETTINGS));
        return PlayerSetting.isCaption();
    }

    private void setMusicNotification(View view) {
        PlayerSetting.putMusicNotification(!PlayerSetting.isMusicNotification());
        mBinding.musicNotificationText.setText(getSwitch(PlayerSetting.isMusicNotification()));
    }

    private void setAudioBookNotification(View view) {
        PlayerSetting.putAudioBookNotification(!PlayerSetting.isAudioBookNotification());
        mBinding.audioBookNotificationText.setText(getSwitch(PlayerSetting.isAudioBookNotification()));
    }

    private void setAudioDecode(View view) {
        PlayerSetting.putAudioPrefer(!PlayerSetting.isAudioPrefer());
        mBinding.audioDecodeText.setText(getSwitch(PlayerSetting.isAudioPrefer()));
    }

    private void setAudioPassThrough(View view) {
        PlayerSetting.putAudioPassThrough(!PlayerSetting.isAudioPassThrough());
        mBinding.audioPassThroughText.setText(getSwitch(PlayerSetting.isAudioPassThrough()));
    }

    private void setVideoDecode(View view) {
        PlayerSetting.putVideoPrefer(!PlayerSetting.isVideoPrefer());
        mBinding.videoDecodeText.setText(getSwitch(PlayerSetting.isVideoPrefer()));
    }

    private void onBackground(View view) {
        PlayerSetting.putBackground(PlayerSetting.isBackgroundOn() ? 0 : 1);
        mBinding.backgroundText.setText(getSwitch(PlayerSetting.isBackgroundOn()));
    }

}
