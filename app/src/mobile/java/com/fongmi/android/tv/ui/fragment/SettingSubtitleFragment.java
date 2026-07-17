package com.fongmi.android.tv.ui.fragment;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.FragmentSettingSubtitleBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.subtitle.RealtimeSubtitleController;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.SubtitleSettingsDialog;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;

public class SettingSubtitleFragment extends BaseFragment {

    private FragmentSettingSubtitleBinding mBinding;
    private String[] subtitleLanguageLabels;
    private String[] subtitleLanguageValues;
    private String[] realtimeModelLabels;
    private String[] realtimeModelValues;

    public static SettingSubtitleFragment newInstance() {
        return new SettingSubtitleFragment();
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingSubtitleBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        subtitleLanguageLabels = ResUtil.getStringArray(R.array.select_subtitle_language);
        subtitleLanguageValues = ResUtil.getStringArray(R.array.select_subtitle_language_value);
        realtimeModelLabels = ResUtil.getStringArray(R.array.select_realtime_subtitle_model);
        realtimeModelValues = ResUtil.getStringArray(R.array.select_realtime_subtitle_model_value);
        mBinding.subtitleAutoMatchText.setText(getSwitch(Setting.isSubtitleAutoMatchEnabled()));
        mBinding.subtitleLanguageText.setText(getSubtitleLanguageText());
        mBinding.subtitleAssrtTokenText.setText(getSubtitleAssrtTokenText());
        setRealtimeModelStatus();
        setAiSubtitleSettings();
    }

    @Override
    protected void initEvent() {
        mBinding.subtitleAutoMatch.setOnClickListener(this::setSubtitleAutoMatch);
        mBinding.subtitleLanguage.setOnClickListener(this::onSubtitleLanguage);
        mBinding.subtitleAssrtToken.setOnClickListener(this::onSubtitleAssrtToken);
        mBinding.subtitleRealtimeModel.setOnClickListener(this::onRealtimeModel);
        mBinding.subtitleAiMaxConcurrency.setOnClickListener(this::onSubtitleAiMaxConcurrency);
        mBinding.subtitleAiChunkCount.setOnClickListener(this::onSubtitleAiChunkCount);
    }

    private void setSubtitleAutoMatch(View view) {
        Setting.putSubtitleAutoMatchEnabled(!Setting.isSubtitleAutoMatchEnabled());
        mBinding.subtitleAutoMatchText.setText(getSwitch(Setting.isSubtitleAutoMatchEnabled()));
    }

    private void onSubtitleLanguage(View view) {
        SubtitleSettingsDialog.showPreferredLanguage(requireActivity(), subtitleLanguageLabels, subtitleLanguageValues, Setting.getSubtitlePreferredLanguage(), value -> {
            Setting.putSubtitlePreferredLanguage(value);
            mBinding.subtitleLanguageText.setText(getSubtitleLanguageText());
        });
    }

    private void onSubtitleAssrtToken(View view) {
        SubtitleSettingsDialog.showAssrtToken(requireActivity(), Setting.getSubtitleAssrtToken(), value -> {
            Setting.putSubtitleAssrtToken(value);
            mBinding.subtitleAssrtTokenText.setText(getSubtitleAssrtTokenText());
        });
    }

    private void onRealtimeModel(View view) {
        RealtimeSubtitleController controller = RealtimeSubtitleController.get();
        if (controller.isModelDownloading() || controller.isModelDeleting()) return;
        SubtitleSettingsDialog.showRealtimeModel(requireActivity(), realtimeModelLabels, getRealtimeModelIndex(), controller.isModelReady(), this::selectRealtimeModel, () -> {
            mBinding.subtitleRealtimeModelText.setText(modelStatusText(R.string.subtitle_realtime_model_deleting));
            controller.deleteModel(error -> onRealtimeModelChanged(error, true));
        });
    }

    private void selectRealtimeModel(int index) {
        if (realtimeModelValues == null || index < 0 || index >= realtimeModelValues.length) return;
        Setting.putRealtimeSubtitleModel(realtimeModelValues[index]);
        RealtimeSubtitleController controller = RealtimeSubtitleController.get();
        setRealtimeModelStatus();
        if (controller.isModelReady()) return;
        mBinding.subtitleRealtimeModelText.setText(modelStatusText(R.string.subtitle_realtime_model_downloading));
        controller.downloadModel((percent, fileName) -> {
            if (!isAdded() || getView() == null) return;
            mBinding.subtitleRealtimeModelText.setText(modelProgressStatusText(percent));
        }, error -> onRealtimeModelChanged(error, false));
    }

    private void onRealtimeModelChanged(String error, boolean deleted) {
        if (!isAdded() || getView() == null) return;
        setRealtimeModelStatus();
        if (TextUtils.isEmpty(error)) Notify.show(deleted ? R.string.subtitle_realtime_model_delete_success : R.string.subtitle_realtime_model_download_success);
        else Notify.show(getString(deleted ? R.string.subtitle_realtime_model_delete_failed : R.string.subtitle_realtime_model_download_failed, error));
    }

    private void setRealtimeModelStatus() {
        RealtimeSubtitleController controller = RealtimeSubtitleController.get();
        if (controller.isModelDownloading() && controller.getModelDownloadProgress() > 0) {
            mBinding.subtitleRealtimeModelText.setText(modelProgressStatusText(controller.getModelDownloadProgress()));
            return;
        }
        int status = controller.isModelDeleting() ? R.string.subtitle_realtime_model_deleting : controller.isModelDownloading() ? R.string.subtitle_realtime_model_downloading : controller.isModelReady() ? R.string.subtitle_realtime_model_ready : R.string.subtitle_realtime_model_missing;
        mBinding.subtitleRealtimeModelText.setText(modelStatusText(status));
    }

    private String modelProgressStatusText(int progress) {
        int index = getRealtimeModelIndex();
        String label = realtimeModelLabels != null && index < realtimeModelLabels.length ? realtimeModelLabels[index] : Setting.getRealtimeSubtitleModel();
        return getString(R.string.subtitle_realtime_model_status, label, getString(R.string.subtitle_realtime_model_downloading_progress, progress));
    }

    private String modelStatusText(int status) {
        int index = getRealtimeModelIndex();
        String label = realtimeModelLabels != null && index < realtimeModelLabels.length ? realtimeModelLabels[index] : Setting.getRealtimeSubtitleModel();
        return getString(R.string.subtitle_realtime_model_status, label, getString(status));
    }

    private int getRealtimeModelIndex() {
        if (realtimeModelValues == null) return 0;
        for (int i = 0; i < realtimeModelValues.length; i++) if (TextUtils.equals(realtimeModelValues[i], Setting.getRealtimeSubtitleModel())) return i;
        return 0;
    }

    private void onSubtitleAiMaxConcurrency(View view) {
        SubtitleSettingsDialog.showNumber(requireActivity(), R.string.player_subtitle_ai_max_concurrency, Setting.getSubtitleAiMaxConcurrency(), 1, 8, value -> {
            Setting.putSubtitleAiMaxConcurrency(value);
            mBinding.subtitleAiMaxConcurrencyText.setText(String.valueOf(Setting.getSubtitleAiMaxConcurrency()));
        });
    }

    private void onSubtitleAiChunkCount(View view) {
        SubtitleSettingsDialog.showNumber(requireActivity(), R.string.player_subtitle_ai_chunk_count, Setting.getSubtitleAiChunkCount(), 1, 32, value -> {
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

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) initView();
    }
}
