package com.fongmi.android.tv.ui.fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.FragmentSettingPersonalBinding;
import com.fongmi.android.tv.setting.AutoBackupPolicy;
import com.fongmi.android.tv.setting.GroupRuleConfig;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.GroupRuleDialog;
import com.fongmi.android.tv.ui.dialog.SpeedSettingDialog;
import com.fongmi.android.tv.ui.dialog.SliderNumberDialog;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

public class SettingPersonalFragment extends BaseFragment {

    private FragmentSettingPersonalBinding mBinding;
    private String[] searchUi;
    private String[] searchColumn;
    private String[] siteColumn;
    private String[] tmdbMatchMode;
    private String[] searchResultSort;

    public static SettingPersonalFragment newInstance() {
        return new SettingPersonalFragment();
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingPersonalBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        setText();
    }

    @Override
    protected void initEvent() {
        mBinding.searchThread.setOnClickListener(this::setSearchThread);
        mBinding.autoBackup.setOnClickListener(this::setAutoBackup);
        mBinding.playBackToDetail.setOnClickListener(this::setPlayBackToDetail);
        mBinding.episodeHistory.setOnClickListener(this::setEpisodeHistory);
        mBinding.playSpeed.setOnClickListener(this::setPlaySpeed);
        mBinding.tmdbMatchMode.setOnClickListener(this::setTmdbMatchMode);
        mBinding.personalRecommendation.setOnClickListener(this::setPersonalRecommendation);
        mBinding.groupRule.setOnClickListener(this::setGroupRule);
        mBinding.tmdbEpisodeFileSize.setOnClickListener(this::setTmdbEpisodeFileSize);
        mBinding.searchUi.setOnClickListener(this::setSearchUi);
        mBinding.searchColumn.setOnClickListener(this::setSearchColumn);
        mBinding.siteColumn.setOnClickListener(this::setSiteColumn);
        mBinding.searchResultSort.setOnClickListener(this::setSearchResultSort);
    }

    private void setText() {
        mBinding.searchThreadText.setText(String.valueOf(Setting.getSearchThread()));
        mBinding.autoBackupText.setText(getSwitch(isAutoBackupEnabled()));
        mBinding.playBackToDetailText.setText(getSwitch(Setting.isPlayBackToDetail()));
        mBinding.episodeHistoryText.setText(getSwitch(Setting.isEpisodeHistory()));
        mBinding.playSpeedText.setText(getSpeedText(PlayerSetting.getDefaultSpeed()));
        mBinding.tmdbMatchModeText.setText((tmdbMatchMode = getResources().getStringArray(R.array.select_tmdb_match_mode))[Setting.getTmdbMatchMode()]);
        mBinding.personalRecommendationText.setText(getSwitch(Setting.isPersonalRecommendation()));
        mBinding.groupRuleText.setText(getString(R.string.setting_group_rule_summary, GroupRuleConfig.enabledCount(), GroupRuleConfig.totalCount()));
        mBinding.tmdbEpisodeFileSizeText.setText(getSwitch(Setting.isTmdbEpisodeFileSize()));
        mBinding.searchUiText.setText((searchUi = getResources().getStringArray(R.array.select_search_ui))[Setting.getSearchUi()]);
        mBinding.searchColumnText.setText(getSearchColumnText());
        mBinding.siteColumnText.setText((siteColumn = getResources().getStringArray(R.array.select_site_column))[Setting.getSiteColumn() - 1]);
        mBinding.searchResultSortText.setText((searchResultSort = getResources().getStringArray(R.array.select_search_result_sort))[Setting.getSearchResultSort()]);
    }

    private String getSearchColumnText() {
        searchColumn = getResources().getStringArray(R.array.select_search_column);
        int column = Setting.getSearchColumn();
        if (column >= 0 && column < searchColumn.length) {
            return searchColumn[column];
        }
        return searchColumn[0];
    }

    private String getSpeedText(float speed) {
        return String.format(Locale.US, "%.2f", speed);
    }

    private void setSearchThread(View view) {
        SliderNumberDialog.show(requireActivity(), R.string.setting_search_thread, Setting.getSearchThread(), 1, 100, value -> {
            Setting.putSearchThread(value);
            setText();
        });
    }

    private void setAutoBackup(View view) {
        if (isAutoBackupEnabled()) {
            Setting.putAutoBackup(false);
            setText();
            return;
        }
        PermissionUtil.requestFile(this, allGranted -> {
            if (!allGranted) {
                Notify.show(R.string.backup_permission_denied);
                return;
            }
            Setting.putAutoBackup(true);
            setText();
        });
    }

    private boolean isAutoBackupEnabled() {
        return AutoBackupPolicy.isEffective(Setting.isAutoBackup(), Setting.hasFileAccess());
    }

    private void setPlayBackToDetail(View view) {
        Setting.putPlayBackToDetail(!Setting.isPlayBackToDetail());
        setText();
    }

    private void setEpisodeHistory(View view) {
        Setting.putEpisodeHistory(!Setting.isEpisodeHistory());
        setText();
    }

    private void setPlaySpeed(View view) {
        SpeedSettingDialog.show(requireActivity(), R.string.setting_play_speed, PlayerSetting.getDefaultSpeed(), 0.5f, 5f, 0.25f, value -> {
            PlayerSetting.putDefaultSpeed(value);
            setText();
        });
    }

    private void setTmdbMatchMode(View view) {
        Setting.putTmdbMatchMode((Setting.getTmdbMatchMode() + 1) % tmdbMatchMode.length);
        setText();
    }

    private void setPersonalRecommendation(View view) {
        if (Setting.isPersonalRecommendation()) {
            Setting.putPersonalRecommendation(false);
            setText();
            return;
        }
        new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.personal_recommendation_confirm_title)
                .setMessage(R.string.personal_recommendation_confirm_message)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                    Setting.putPersonalRecommendation(true);
                    setText();
                })
                .show();
    }

    private void setGroupRule(View view) {
        GroupRuleDialog.create(requireActivity()).onChanged(this::setText).show();
    }

    private void setTmdbEpisodeFileSize(View view) {
        Setting.putTmdbEpisodeFileSize(!Setting.isTmdbEpisodeFileSize());
        setText();
    }

    private void setSearchUi(View view) {
        Setting.putSearchUi((Setting.getSearchUi() + 1) % searchUi.length);
        setText();
    }

    private void setSearchColumn(View view) {
        int current = Setting.getSearchColumn();
        int next = (current + 1) % searchColumn.length;
        Setting.putSearchColumn(next);
        setText();
    }

    private void setSiteColumn(View view) {
        Setting.putSiteColumn(Setting.getSiteColumn() == 1 ? 2 : 1);
        setText();
    }

    private void setSearchResultSort(View view) {
        Setting.putSearchResultSort((Setting.getSearchResultSort() + 1) % searchResultSort.length);
        setText();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) setText();
    }
}
