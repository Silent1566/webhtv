package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivitySettingSubtitleBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.SubtitleSettingsDialog;
import com.fongmi.android.tv.utils.ResUtil;

public class SettingSubtitleActivity extends BaseActivity {

    private ActivitySettingSubtitleBinding mBinding;
    private String[] subtitleLanguageLabels;
    private String[] subtitleLanguageValues;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingSubtitleActivity.class));
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingSubtitleBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.subtitleAutoMatch.requestFocus();
        subtitleLanguageLabels = ResUtil.getStringArray(R.array.select_subtitle_language);
        subtitleLanguageValues = ResUtil.getStringArray(R.array.select_subtitle_language_value);
        mBinding.subtitleAutoMatchText.setText(getSwitch(Setting.isSubtitleAutoMatchEnabled()));
        mBinding.subtitleLanguageText.setText(getSubtitleLanguageText());
        mBinding.subtitleAssrtTokenText.setText(getSubtitleAssrtTokenText());
        setAiSubtitleSettings();
    }

    @Override
    protected void initEvent() {
        mBinding.subtitleAutoMatch.setOnClickListener(this::setSubtitleAutoMatch);
        mBinding.subtitleLanguage.setOnClickListener(this::onSubtitleLanguage);
        mBinding.subtitleAssrtToken.setOnClickListener(this::onSubtitleAssrtToken);
        mBinding.subtitleAiMaxConcurrency.setOnClickListener(this::onSubtitleAiMaxConcurrency);
        mBinding.subtitleAiChunkCount.setOnClickListener(this::onSubtitleAiChunkCount);
    }

    private void setSubtitleAutoMatch(View view) {
        Setting.putSubtitleAutoMatchEnabled(!Setting.isSubtitleAutoMatchEnabled());
        mBinding.subtitleAutoMatchText.setText(getSwitch(Setting.isSubtitleAutoMatchEnabled()));
    }

    private void onSubtitleLanguage(View view) {
        SubtitleSettingsDialog.showPreferredLanguage(this, subtitleLanguageLabels, subtitleLanguageValues, Setting.getSubtitlePreferredLanguage(), value -> {
            Setting.putSubtitlePreferredLanguage(value);
            mBinding.subtitleLanguageText.setText(getSubtitleLanguageText());
        });
    }

    private void onSubtitleAssrtToken(View view) {
        SubtitleSettingsDialog.showAssrtToken(this, Setting.getSubtitleAssrtToken(), value -> {
            Setting.putSubtitleAssrtToken(value);
            mBinding.subtitleAssrtTokenText.setText(getSubtitleAssrtTokenText());
        });
    }

    private void onSubtitleAiMaxConcurrency(View view) {
        SubtitleSettingsDialog.showNumber(this, R.string.player_subtitle_ai_max_concurrency, Setting.getSubtitleAiMaxConcurrency(), 1, 8, value -> {
            Setting.putSubtitleAiMaxConcurrency(value);
            mBinding.subtitleAiMaxConcurrencyText.setText(String.valueOf(Setting.getSubtitleAiMaxConcurrency()));
        });
    }

    private void onSubtitleAiChunkCount(View view) {
        SubtitleSettingsDialog.showNumber(this, R.string.player_subtitle_ai_chunk_count, Setting.getSubtitleAiChunkCount(), 1, 32, value -> {
            Setting.putSubtitleAiChunkCount(value);
            mBinding.subtitleAiChunkCountText.setText(String.valueOf(Setting.getSubtitleAiChunkCount()));
        });
    }

    private void setAiSubtitleSettings() {
        boolean visible = Setting.isAiConfigReady();
        mBinding.subtitleAiSettings.setVisibility(visible ? View.VISIBLE : View.GONE);
        mBinding.subtitleAiMaxConcurrencyText.setText(String.valueOf(Setting.getSubtitleAiMaxConcurrency()));
        mBinding.subtitleAiChunkCountText.setText(String.valueOf(Setting.getSubtitleAiChunkCount()));
    }

    private String getSubtitleLanguageText() {
        int index = getSubtitleLanguageIndex();
        return subtitleLanguageLabels != null && index >= 0 && index < subtitleLanguageLabels.length ? subtitleLanguageLabels[index] : Setting.getSubtitlePreferredLanguage();
    }

    private int getSubtitleLanguageIndex() {
        if (subtitleLanguageValues == null) return 0;
        for (int i = 0; i < subtitleLanguageValues.length; i++) if (TextUtils.equals(subtitleLanguageValues[i], Setting.getSubtitlePreferredLanguage())) return i;
        return 0;
    }

    private String getSubtitleAssrtTokenText() {
        return getString(TextUtils.isEmpty(Setting.getSubtitleAssrtToken()) ? R.string.setting_unconfigured : R.string.setting_configured);
    }
}
