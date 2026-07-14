package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.HomeButton;
import com.fongmi.android.tv.databinding.ActivitySettingPersonalBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.HomeButtonDialog;
import com.fongmi.android.tv.ui.dialog.HomeMenuKeyDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SettingPersonalActivity extends BaseActivity {

    private static final int[] SEARCH_THREADS = {1, 2, 4, 6, 8, 10, 12, 16, 20, 32};

    private ActivitySettingPersonalBinding mBinding;
    private String[] fullscreenMenuKey;
    private String[] homeMenuKey;
    private String[] searchUi;
    private String[] searchColumn;
    private String[] searchResultSort;
    private String[] tmdbMatchMode;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingPersonalActivity.class));
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingPersonalBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.homeVodAutoLoad.requestFocus();
        setText();
    }

    @Override
    protected void initEvent() {
        mBinding.homeVodAutoLoad.setOnClickListener(this::setHomeVodAutoLoad);
        mBinding.homeButtons.setOnClickListener(this::onHomeButtons);
        mBinding.fullscreenMenuKey.setOnClickListener(this::setFullscreenMenuKey);
        mBinding.homeMenuKey.setOnClickListener(this::setHomeMenuKey);
        mBinding.playBackToDetail.setOnClickListener(this::setPlayBackToDetail);
        mBinding.tmdbMatchMode.setOnClickListener(this::setTmdbMatchMode);
        mBinding.personalRecommendation.setOnClickListener(this::setPersonalRecommendation);
        mBinding.homeHistory.setOnClickListener(this::setHomeHistory);
        mBinding.tmdbEpisodeFileSize.setOnClickListener(this::setTmdbEpisodeFileSize);
        mBinding.searchThread.setOnClickListener(this::setSearchThread);
        mBinding.searchUi.setOnClickListener(this::setSearchUi);
        mBinding.searchResultSort.setOnClickListener(this::setSearchResultSort);
        // mBinding.searchColumn.setOnClickListener(this::setSearchColumn); // 在搜索页面切换更方便
    }

    private void setText() {
        mBinding.homeVodAutoLoadText.setText(getSwitch(Setting.isHomeVodAutoLoad()));
        mBinding.homeButtonsText.setText(getString(R.string.home_buttons_selected, HomeButton.getButtons().size(), HomeButton.all().size()));
        mBinding.fullscreenMenuKeyText.setText((fullscreenMenuKey = getResources().getStringArray(R.array.select_fullscreen_menu_key))[Setting.getFullscreenMenuKey()]);
        mBinding.homeMenuKeyText.setText((homeMenuKey = getResources().getStringArray(R.array.select_home_menu_key))[Setting.getHomeMenuKey()]);
        mBinding.playBackToDetailText.setText(getSwitch(Setting.isPlayBackToDetail()));
        mBinding.tmdbMatchModeText.setText((tmdbMatchMode = getResources().getStringArray(R.array.select_tmdb_match_mode))[Setting.getTmdbMatchMode()]);
        mBinding.personalRecommendationText.setText(getSwitch(Setting.isPersonalRecommendation()));
        mBinding.homeHistoryText.setText(getSwitch(Setting.isHomeHistory()));
        mBinding.tmdbEpisodeFileSizeText.setText(getSwitch(Setting.isTmdbEpisodeFileSize()));
        mBinding.searchThreadText.setText(String.valueOf(Setting.getSearchThread()));
        mBinding.searchUiText.setText((searchUi = getResources().getStringArray(R.array.select_search_ui))[Setting.getSearchUi()]);
        mBinding.searchResultSortText.setText((searchResultSort = getResources().getStringArray(R.array.select_search_result_sort))[Setting.getSearchResultSort()]);
        // mBinding.searchColumnText.setText(getSearchColumnText()); // 在搜索页面切换更方便
    }

    private String getSearchColumnText() {
        searchColumn = getResources().getStringArray(R.array.select_search_column);
        int column = Setting.getSearchColumn();
        if (column >= 0 && column < searchColumn.length) {
            return searchColumn[column];
        }
        return searchColumn[0]; // 默认返回第一项
    }

    private void setHomeVodAutoLoad(View view) {
        Setting.putHomeVodAutoLoad(!Setting.isHomeVodAutoLoad());
        setText();
    }

    private void onHomeButtons(View view) {
        HomeButtonDialog.show(this, this::setText);
    }

    private void setFullscreenMenuKey(View view) {
        Setting.putFullscreenMenuKey((Setting.getFullscreenMenuKey() + 1) % fullscreenMenuKey.length);
        setText();
    }

    private void setHomeMenuKey(View view) {
        HomeMenuKeyDialog.show(this, this::setText);
    }

    private void setPlayBackToDetail(View view) {
        Setting.putPlayBackToDetail(!Setting.isPlayBackToDetail());
        setText();
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
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.personal_recommendation_confirm_title)
                .setMessage(R.string.personal_recommendation_confirm_message)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                    Setting.putPersonalRecommendation(true);
                    setText();
                })
                .show();
    }

    private void setHomeHistory(View view) {
        Setting.putHomeHistory(!Setting.isHomeHistory());
        RefreshEvent.history();
        setText();
    }

    private void setTmdbEpisodeFileSize(View view) {
        Setting.putTmdbEpisodeFileSize(!Setting.isTmdbEpisodeFileSize());
        setText();
    }

    private void setSearchThread(View view) {
        int index = 0;
        for (int i = 0; i < SEARCH_THREADS.length; i++) if (SEARCH_THREADS[i] == Setting.getSearchThread()) index = i;
        Setting.putSearchThread(SEARCH_THREADS[(index + 1) % SEARCH_THREADS.length]);
        setText();
    }

    private void setSearchUi(View view) {
        Setting.putSearchUi((Setting.getSearchUi() + 1) % searchUi.length);
        setText();
    }

    private void setSearchResultSort(View view) {
        Setting.putSearchResultSort((Setting.getSearchResultSort() + 1) % searchResultSort.length);
        setText();
    }

    // 在搜索页面切换更方便，此处不再提供设置入口
    /*
    private void setSearchColumn(View view) {
        int current = Setting.getSearchColumn();
        int next = (current + 1) % 3; // 0: 自适应, 1: 1列, 2: 默认5列
        Setting.putSearchColumn(next);
        setText();
    }
    */

}
