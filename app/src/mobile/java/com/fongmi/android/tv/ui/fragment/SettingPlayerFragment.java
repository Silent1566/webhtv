package com.fongmi.android.tv.ui.fragment;

import android.content.Intent;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.FragmentSettingPlayerBinding;
import com.fongmi.android.tv.impl.BufferListener;
import com.fongmi.android.tv.impl.SpeedListener;
import com.fongmi.android.tv.impl.UaListener;
import com.fongmi.android.tv.player.lut.LutSetting;
import com.fongmi.android.tv.setting.PlayerButtonSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.BufferDialog;
import com.fongmi.android.tv.ui.dialog.LutDialog;
import com.fongmi.android.tv.ui.dialog.PlayerButtonConfigDialog;
import com.fongmi.android.tv.ui.dialog.SpeedDialog;
import com.fongmi.android.tv.ui.dialog.UaDialog;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.DecimalFormat;

public class SettingPlayerFragment extends BaseFragment implements UaListener, BufferListener, SpeedListener {

    private FragmentSettingPlayerBinding mBinding;
    private DecimalFormat format;
    private String[] background;
    private String[] backBuffer;
    private String[] bufferBytes;
    private String[] caption;
    private String[] kernel;
    private String[] playCache;
    private String[] render;
    private String[] scale;
    private String[] osd;

    public static SettingPlayerFragment newInstance() {
        return new SettingPlayerFragment();
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingPlayerBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        format = new DecimalFormat("0.#");
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
        mBinding.musicNotificationText.setText(getSwitch(PlayerSetting.isMusicNotification()));
        mBinding.audioBookNotificationText.setText(getSwitch(PlayerSetting.isAudioBookNotification()));
        mBinding.audioDecodeText.setText(getSwitch(PlayerSetting.isAudioPrefer()));
        mBinding.audioPassThroughText.setText(getSwitch(PlayerSetting.isAudioPassThrough()));
        mBinding.videoDecodeText.setText(getSwitch(PlayerSetting.isVideoPrefer()));
        mBinding.caption.setVisibility(PlayerSetting.hasCaption() ? View.VISIBLE : View.GONE);
        mBinding.osdText.setText(getOsdText(osd = ResUtil.getStringArray(R.array.select_player_osd)));
        mBinding.kernelText.setText((kernel = ResUtil.getStringArray(R.array.select_player_kernel))[PlayerSetting.getPlayer()]);
        mBinding.scaleText.setText((scale = ResUtil.getStringArray(R.array.select_scale))[PlayerSetting.getScale()]);
        mBinding.lutText.setText(LutSetting.getSummary());
        mBinding.renderText.setText((render = ResUtil.getStringArray(R.array.select_render))[PlayerSetting.getRender()]);
        mBinding.captionText.setText((caption = ResUtil.getStringArray(R.array.select_caption))[PlayerSetting.isCaption() ? 1 : 0]);
        mBinding.backgroundText.setText((background = ResUtil.getStringArray(R.array.select_background))[PlayerSetting.getBackground()]);
    }

    @Override
    protected void initEvent() {
        mBinding.ua.setOnClickListener(this::onUa);
        mBinding.aac.setOnClickListener(this::setAAC);
        mBinding.kernel.setOnClickListener(this::onKernel);
        mBinding.scale.setOnClickListener(this::onScale);
        mBinding.lut.setOnClickListener(this::onLut);
        mBinding.osd.setOnClickListener(this::onOsd);
        mBinding.playerButtons.setOnClickListener(view -> PlayerButtonConfigDialog.show(this, this::setPlayerButtonsText));
        mBinding.speed.setOnClickListener(this::onSpeed);
        mBinding.buffer.setOnClickListener(this::onBuffer);
        mBinding.bufferBytes.setOnClickListener(this::onBufferBytes);
        mBinding.backBuffer.setOnClickListener(this::onBackBuffer);
        mBinding.playCache.setOnClickListener(this::onPlayCache);
        mBinding.preload.setOnClickListener(this::setPreload);
        mBinding.preloadThread.setOnClickListener(this::onPreloadThread);
        mBinding.preloadSize.setOnClickListener(this::onPreloadSize);
        mBinding.preloadTime.setOnClickListener(this::onPreloadTime);
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

    private void onKernel(View view) {
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.player_kernel).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(kernel, PlayerSetting.getPlayer(), (dialog, which) -> {
            mBinding.kernelText.setText(kernel[which]);
            PlayerSetting.putPlayer(which);
            dialog.dismiss();
        }).show();
    }

    private void onScale(View view) {
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.player_scale).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(scale, PlayerSetting.getScale(), (dialog, which) -> {
            mBinding.scaleText.setText(scale[which]);
            PlayerSetting.putScale(which);
            dialog.dismiss();
        }).show();
    }

    private void onLut(View view) {
        LutDialog.show(this, () -> mBinding.lutText.setText(LutSetting.getSummary()));
    }

    private void onOsd(View view) {
        boolean[] checked = getOsdChecked();
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.player_osd).setNegativeButton(R.string.dialog_negative, null).setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
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

    private void onBufferBytes(View view) {
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.player_buffer_bytes).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(bufferBytes, PlayerSetting.getBufferBytesOption(), (dialog, which) -> {
            mBinding.bufferBytesText.setText(bufferBytes[which]);
            PlayerSetting.putBufferBytesOption(which);
            dialog.dismiss();
        }).show();
    }

    private void onBackBuffer(View view) {
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.player_back_buffer).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(backBuffer, PlayerSetting.getBackBufferOption(), (dialog, which) -> {
            mBinding.backBufferText.setText(backBuffer[which]);
            PlayerSetting.putBackBufferOption(which);
            dialog.dismiss();
        }).show();
    }

    private void onPlayCache(View view) {
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.player_cache).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(playCache, PlayerSetting.getPlayCacheOption(), (dialog, which) -> {
            mBinding.playCacheText.setText(playCache[which]);
            PlayerSetting.putPlayCacheOption(which);
            dialog.dismiss();
        }).show();
    }

    private void setPreload(View view) {
        PreloadSetting.putPreload(!PreloadSetting.isPreload());
        setPreloadText();
    }

    private void onPreloadThread(View view) {
        String[] items = getPreloadThreadItems();
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.player_preload_threads).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(items, PreloadSetting.getPreloadThreads() - PreloadSetting.MIN_THREADS, (dialog, which) -> {
            PreloadSetting.putPreloadThreads(PreloadSetting.MIN_THREADS + which);
            setPreloadText();
            dialog.dismiss();
        }).show();
    }

    private void onPreloadSize(View view) {
        String[] items = getPreloadSizeItems();
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.player_preload_size).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(items, getPreloadSizeIndex(), (dialog, which) -> {
            PreloadSetting.putPreloadSizeMb(PreloadSetting.MIN_SIZE_MB + which * PreloadSetting.STEP_SIZE_MB);
            setPreloadText();
            dialog.dismiss();
        }).show();
    }

    private void onPreloadTime(View view) {
        String[] items = getPreloadTimeItems();
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.player_preload_time).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(items, getPreloadTimeIndex(), (dialog, which) -> {
            PreloadSetting.putPreloadTimeSeconds(PreloadSetting.MIN_TIME_SECONDS + which * PreloadSetting.STEP_TIME_SECONDS);
            setPreloadText();
            dialog.dismiss();
        }).show();
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

    private String[] getPreloadThreadItems() {
        String[] items = new String[PreloadSetting.MAX_THREADS - PreloadSetting.MIN_THREADS + 1];
        for (int i = 0; i < items.length; i++) items[i] = getString(R.string.player_preload_threads_value, PreloadSetting.MIN_THREADS + i);
        return items;
    }

    private String[] getPreloadSizeItems() {
        String[] items = new String[getPreloadSizeCount()];
        for (int i = 0; i < items.length; i++) items[i] = FileUtil.byteCountToDisplaySize((PreloadSetting.MIN_SIZE_MB + i * PreloadSetting.STEP_SIZE_MB) * 1024L * 1024L);
        return items;
    }

    private String[] getPreloadTimeItems() {
        String[] items = new String[getPreloadTimeCount()];
        for (int i = 0; i < items.length; i++) items[i] = getString(R.string.player_preload_time_value, PreloadSetting.MIN_TIME_SECONDS + i * PreloadSetting.STEP_TIME_SECONDS);
        return items;
    }

    private int getPreloadSizeIndex() {
        return Math.min(Math.max((PreloadSetting.getPreloadSizeMb() - PreloadSetting.MIN_SIZE_MB) / PreloadSetting.STEP_SIZE_MB, 0), getPreloadSizeCount() - 1);
    }

    private int getPreloadTimeIndex() {
        return Math.min(Math.max((PreloadSetting.getPreloadTimeSeconds() - PreloadSetting.MIN_TIME_SECONDS) / PreloadSetting.STEP_TIME_SECONDS, 0), getPreloadTimeCount() - 1);
    }

    private int getPreloadSizeCount() {
        return (PreloadSetting.MAX_SIZE_MB - PreloadSetting.MIN_SIZE_MB) / PreloadSetting.STEP_SIZE_MB + 1;
    }

    private int getPreloadTimeCount() {
        return (PreloadSetting.MAX_TIME_SECONDS - PreloadSetting.MIN_TIME_SECONDS) / PreloadSetting.STEP_TIME_SECONDS + 1;
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

    private boolean onCaption(View view) {
        if (PlayerSetting.isCaption()) startActivity(new Intent(Settings.ACTION_CAPTIONING_SETTINGS));
        return PlayerSetting.isCaption();
    }

    private void setAdblock(View view) {
        Setting.putAdblock(!Setting.isAdblock());
        mBinding.adblockText.setText(getSwitch(Setting.isAdblock()));
    }

    private void onBackground(View view) {
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.player_background).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(background, PlayerSetting.getBackground(), (dialog, which) -> {
            mBinding.backgroundText.setText(background[which]);
            PlayerSetting.putBackground(which);
            dialog.dismiss();
        }).show();
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

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) initView();
    }
}
