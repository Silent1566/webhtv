package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.ui.SubtitleView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.databinding.DialogSubtitleBinding;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.bassaer.library.MDColor;

public final class SubtitleDialog extends BaseBottomSheetDialog {

    private DialogSubtitleBinding binding;
    private SubtitleView subtitleView;
    private Runnable searchAction;

    public static SubtitleDialog create() {
        return new SubtitleDialog();
    }

    public SubtitleDialog view(SubtitleView subtitleView) {
        this.subtitleView = subtitleView;
        return this;
    }

    public SubtitleDialog search(Runnable searchAction) {
        this.searchAction = searchAction;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof SubtitleDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    private boolean isFull() {
        return Util.isFullscreen(getActivity());
    }

    @Override
    protected boolean transparent() {
        return isFull();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogSubtitleBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        int count = binding.getRoot().getChildCount();
        if (isFull()) for (int i = 0; i < count; i++) ((ImageView) binding.getRoot().getChildAt(i)).getDrawable().setTint(MDColor.WHITE);
        binding.search.setVisibility(searchAction == null ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void initEvent() {
        binding.up.setOnClickListener(this::onUp);
        binding.down.setOnClickListener(this::onDown);
        binding.large.setOnClickListener(this::onLarge);
        binding.small.setOnClickListener(this::onSmall);
        binding.reset.setOnClickListener(this::onReset);
        binding.search.setOnClickListener(this::onSearch);
    }

    private void onUp(View view) {
        subtitleView.addPosition(0.005f);
        PlayerSetting.putSubtitlePosition(subtitleView.getPosition());
    }

    private void onDown(View view) {
        subtitleView.subPosition(0.005f);
        PlayerSetting.putSubtitlePosition(subtitleView.getPosition());
    }

    private void onLarge(View view) {
        subtitleView.addTextSize(0.002f);
        PlayerSetting.putSubtitleTextSize(subtitleView.getTextSize());
    }

    private void onSmall(View view) {
        subtitleView.subTextSize(0.002f);
        PlayerSetting.putSubtitleTextSize(subtitleView.getTextSize());
    }

    private void onReset(View view) {
        PlayerSetting.putSubtitleTextSize(0.0f);
        PlayerSetting.putSubtitlePosition(0.0f);
        subtitleView.reset();
    }

    private void onSearch(View view) {
        if (searchAction == null) return;
        dismiss();
        App.post(searchAction::run, 100);
    }

    @Override
    public void onResume() {
        super.onResume();
        getDialog().getWindow().setLayout(ResUtil.dp2px(isFull() ? 232 : 216), -1);
    }
}
