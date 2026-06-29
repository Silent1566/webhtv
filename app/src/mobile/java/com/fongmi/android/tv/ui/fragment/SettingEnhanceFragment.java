package com.fongmi.android.tv.ui.fragment;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.AiConfig;
import com.fongmi.android.tv.bean.AudioConfig;
import com.fongmi.android.tv.bean.ShortDramaConfig;
import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.gitcloud.GitCloudAccountStore;
import com.fongmi.android.tv.playback.ViewingRecordSyncStore;
import com.fongmi.android.tv.remote.RemoteStore;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.databinding.FragmentSettingEnhanceBinding;
import com.fongmi.android.tv.setting.CustomCspSetting;
import com.fongmi.android.tv.setting.ProxySetting;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.ui.activity.HomeActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.AiConfigDialog;
import com.fongmi.android.tv.ui.dialog.AudioSourceDialog;
import com.fongmi.android.tv.ui.dialog.ShortDramaSourceDialog;
import com.fongmi.android.tv.ui.dialog.TmdbSourceDialog;
import com.fongmi.android.tv.ui.dialog.CustomCspDialog;
import com.fongmi.android.tv.ui.dialog.DebugLogDialog;
import com.fongmi.android.tv.ui.dialog.GitCloudDialog;
import com.fongmi.android.tv.ui.dialog.LightDialog;
import com.fongmi.android.tv.ui.dialog.LoginStateLearnDialog;
import com.fongmi.android.tv.ui.dialog.ManagePageDialog;
import com.fongmi.android.tv.ui.dialog.OneKeySyncDialog;
import com.fongmi.android.tv.ui.dialog.RemoteTrustDialog;
import com.fongmi.android.tv.ui.dialog.ShellProxyDialog;
import com.fongmi.android.tv.ui.dialog.SiteHealthDialog;
import com.fongmi.android.tv.ui.dialog.ViewingRecordSyncDialog;
import com.fongmi.android.tv.ui.dialog.WebHomeExtensionDialog;
import com.fongmi.android.tv.utils.LoginStateSync;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.github.catvod.crawler.SpiderDebug;
import com.fongmi.android.tv.web.ext.WebHomeExtensionRegistry;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonObject;

public class SettingEnhanceFragment extends BaseFragment {

    private static final String URL_GITHUB = "https://github.com/Silent1566/webhtv";
    private static final String URL_CNB = "https://cnb.cool/fish2018/ext";
    private static final int[] DETAIL_OPEN_MODES = {Setting.DETAIL_OPEN_ORIGINAL_ENHANCED, Setting.DETAIL_OPEN_FUSION, Setting.DETAIL_OPEN_ENHANCED, Setting.DETAIL_OPEN_PLAYER, Setting.DETAIL_OPEN_DIRECT};
    private static final int[] DETAIL_THEME_MODES = {Setting.DETAIL_STYLE_NATIVE, Setting.DETAIL_STYLE_PROFILE, Setting.DETAIL_STYLE_CINEMA};

    private FragmentSettingEnhanceBinding mBinding;

    public static SettingEnhanceFragment newInstance() {
        return new SettingEnhanceFragment();
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_enable : R.string.setting_disable);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingEnhanceBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        reorderItems();
        mBinding.tmdbModel.setVisibility(View.GONE);
        setText();
    }

    @Override
    protected void initEvent() {
        mBinding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.githubRepo) {
                openRepo(URL_GITHUB);
                return true;
            }
            if (item.getItemId() == R.id.cnbRepo) {
                openRepo(URL_CNB);
                return true;
            }
            return false;
        });
        mBinding.driveCheck.setOnClickListener(this::setDriveCheck);
        mBinding.audioSource.setOnClickListener(this::setAudioSource);
        mBinding.shortDramaSource.setOnClickListener(this::setShortDramaSource);
        mBinding.tmdbSource.setOnClickListener(this::setTmdbSource);
        mBinding.aiRecommendation.setOnClickListener(this::setAiRecommendation);
        mBinding.detailInteractionMode.setOnClickListener(this::setDetailOpenMode);
        mBinding.detailThemeMode.setOnClickListener(this::setDetailThemeMode);
        mBinding.debugLog.setOnClickListener(this::setDebugLog);
        mBinding.siteHealthSort.setOnClickListener(view -> SiteHealthDialog.show(this, this::setText));
        mBinding.siteHealthSort.setOnLongClickListener(this::clearSiteHealth);
        mBinding.webHomeExtension.setOnClickListener(view -> WebHomeExtensionDialog.show(this, this::setText));
        mBinding.webHomeExtension.setOnLongClickListener(this::clearWebHomeExtension);
        mBinding.webHomeFullscreen.setOnClickListener(this::setWebHomeFullscreen);
        mBinding.cspWarmup.setOnClickListener(this::setCspWarmup);
        mBinding.playbackArtworkWall.setOnClickListener(this::setPlaybackArtworkWall);
        mBinding.playbackWebhook.setOnClickListener(view -> ViewingRecordSyncDialog.show(this, this::setText));
        mBinding.managePage.setOnClickListener(view -> ManagePageDialog.show(this));
        mBinding.remoteTrust.setOnClickListener(view -> RemoteTrustDialog.show(this, this::setText));
        mBinding.gitCloud.setOnClickListener(view -> GitCloudDialog.show(this, this::setText));
        mBinding.shellProxy.setOnClickListener(view -> ShellProxyDialog.show(this, this::setText));
        mBinding.shellProxy.setOnLongClickListener(v -> false);
        mBinding.shellProxyConfig.setVisibility(View.GONE);
        mBinding.customCsp.setOnClickListener(view -> PermissionUtil.requestFile(this, granted -> {
            if (!isAdded() || isStateSaved() || getActivity() == null) return;
            if (granted) CustomCspDialog.show(this, this::setText);
            else Notify.show(R.string.setting_custom_csp_permission_required);
        }));
        mBinding.loginState.setOnClickListener(view -> LoginStateLearnDialog.show(this, this::setText));
        mBinding.oneKeySync.setOnClickListener(v -> OneKeySyncDialog.create().show(requireActivity()));
    }

    private void reorderItems() {
        ViewGroup parent = (ViewGroup) mBinding.customCsp.getParent();
        View[] order = {
                mBinding.customCsp,
                mBinding.webHomeExtension,
                mBinding.gitCloud,
                mBinding.remoteTrust,
                mBinding.oneKeySync,
                mBinding.loginState,
                mBinding.shellProxy,
                mBinding.shellProxyConfig,
                mBinding.managePage,
                mBinding.webHomeFullscreen,
                mBinding.cspWarmup,
                mBinding.playbackArtworkWall,
                mBinding.driveCheck,
                mBinding.audioSource,
                mBinding.shortDramaSource,
                mBinding.tmdbSource,
                mBinding.aiRecommendation,
                mBinding.tmdbModel,
                mBinding.detailInteractionMode,
                mBinding.detailThemeMode,
                mBinding.siteHealthSort,
                mBinding.debugLog,
                mBinding.playbackWebhook
        };
        for (View view : order) parent.removeView(view);
        for (View view : order) parent.addView(view);
    }

    private void setText() {
        if (!canSetText()) return;
        safeSet("driveCheck", mBinding.driveCheckText, () -> getSwitch(Setting.isDriveCheck()));
        safeSet("audioSource", mBinding.audioSourceText, () -> getSwitch(!AudioConfig.objectFrom(Setting.getAudioConfig()).getDisplayRules().isEmpty()));
        safeSet("shortDramaSource", mBinding.shortDramaSourceText, () -> getSwitch(!ShortDramaConfig.objectFrom(Setting.getShortDramaConfig()).getDisplayRules().isEmpty()));
        safeSet("tmdbSource", mBinding.tmdbSourceText, () -> getString(Setting.isTmdbReady() ? R.string.setting_configured : R.string.setting_unconfigured));
        safeSet("aiRecommendation", mBinding.aiRecommendationText, this::getAiRecommendationText);
        safeSet("detailInteractionMode", mBinding.detailInteractionModeText, this::getDetailOpenModeText);
        safeRun("detailThemeModeVisibility", () -> mBinding.detailThemeMode.setVisibility(Setting.isTmdbMode(Setting.getDetailOpenMode()) ? View.VISIBLE : View.GONE), null);
        safeSet("detailThemeMode", mBinding.detailThemeModeText, this::getDetailThemeModeText);
        safeSet("debugLog", mBinding.debugLogText, () -> getSwitch(Setting.isDebugLog()));
        safeSet("siteHealthSort", mBinding.siteHealthSortText, () -> getSwitch(Setting.isSiteHealthSort()));
        safeSet("webHomeExtension", mBinding.webHomeExtensionText, () -> {
            WebHomeExtensionRegistry.Snapshot webHomeExtension = WebHomeExtensionRegistry.get().snapshot();
            return getSwitch(Setting.isWebHomeExtension()) + " · " + webHomeExtension.readyCount + "/" + webHomeExtension.installedCount;
        });
        safeSet("webHomeFullscreen", mBinding.webHomeFullscreenText, () -> getSwitch(Setting.isWebHomeFullscreen()));
        safeSet("cspWarmup", mBinding.cspWarmupText, () -> getSwitch(Setting.isCspWarmup()));
        safeSet("playbackArtworkWall", mBinding.playbackArtworkWallText, () -> getSwitch(Setting.isPlaybackArtworkWall()));
        safeSet("playbackWebhook", mBinding.playbackWebhookText, () -> ViewingRecordSyncStore.summary(requireContext()));
        safeSet("managePage", mBinding.managePageText, () -> getString(R.string.manage_page_web));
        safeSet("remoteTrust", mBinding.remoteTrustText, () -> RemoteStore.summary(requireContext()));
        safeSet("gitCloud", mBinding.gitCloudText, () -> getString(R.string.git_cloud_account_count, GitCloudAccountStore.list().size()));
        setShellProxyText();
        setCustomCspText();
        safeSet("loginState", mBinding.loginStateText, () -> {
            int learned = LoginStateSync.learnedCount();
            int pending = LoginStateSync.pendingPaths().size();
            return getString(LoginStateSync.hasLearningSnapshot() ? R.string.login_state_learning_count : R.string.login_state_count, learned, pending);
        });
    }

    private boolean canSetText() {
        return mBinding != null && isAdded() && getContext() != null;
    }

    private void setShellProxyText() {
        safeRun("shellProxy", () -> {
            int count = ProxySetting.count();
            mBinding.shellProxyText.setText(getSwitch(Setting.isShellProxy()) + " · " + getString(R.string.setting_proxy_rule_count, count));
            mBinding.shellProxyConfigText.setText(getString(R.string.setting_proxy_rule_count, count));
        }, () -> {
            setError(mBinding.shellProxyText);
            setError(mBinding.shellProxyConfigText);
        });
    }

    private void setCustomCspText() {
        safeRun("customCsp", () -> {
            CustomCspSetting.Status status = CustomCspSetting.status();
            if (!status.available()) {
                setError(mBinding.customCspText);
                return;
            }
            CustomCspSetting.Count count = status.count();
            mBinding.customCspText.setText(getSwitch(status.enabled()) + " · " + getString(R.string.setting_custom_csp_count, count.active(), count.enabled()));
        }, () -> setError(mBinding.customCspText));
    }

    private void safeSet(String name, TextView view, TextSupplier supplier) {
        safeRun(name, () -> view.setText(supplier.get()), () -> setError(view));
    }

    private void safeRun(String name, Runnable action, Runnable fallback) {
        try {
            action.run();
        } catch (Throwable e) {
            SpiderDebug.log("enhance", "summary failed item=%s error=%s", name, e.toString());
            if (fallback == null) return;
            try {
                fallback.run();
            } catch (Throwable ignored) {
            }
        }
    }

    private void setError(TextView view) {
        if (view != null) view.setText(R.string.error_config_get);
    }

    private interface TextSupplier {
        CharSequence get();
    }

    private void setDriveCheck(View view) {
        Setting.putDriveCheck(!Setting.isDriveCheck());
        mBinding.driveCheckText.setText(getSwitch(Setting.isDriveCheck()));
    }

    private void setDebugLog(View view) {
        Setting.putDebugLog(!Setting.isDebugLog());
        mBinding.debugLogText.setText(getSwitch(Setting.isDebugLog()));
        if (!Setting.isDebugLog()) return;
        DebugLogDialog.show(this);
    }

    private void setAudioSource(View view) {
        AudioSourceDialog.create(requireActivity()).onDismiss(this::setText).show();
    }

    private void setShortDramaSource(View view) {
        ShortDramaSourceDialog.create(requireActivity()).onDismiss(this::setText).show();
    }

    private void setTmdbSource(View view) {
        com.fongmi.android.tv.ui.dialog.TmdbSourceDialog.create(requireActivity()).onDismiss(this::setText).show();
    }

    private void setAiRecommendation(View view) {
        AiConfigDialog.create(requireActivity()).onDismiss(this::setText).show();
    }

    private String getAiRecommendationText() {
        AiConfig config = AiConfig.objectFrom(Setting.getAiConfig());
        if (!config.isEnabled()) return getSwitch(false);
        return config.isReady() ? getString(R.string.setting_configured) : getString(R.string.setting_unconfigured);
    }

    private String getDetailOpenModeText() {
        String[] labels = getDetailOpenModes();
        int mode = Setting.getDetailOpenMode();
        for (int i = 0; i < DETAIL_OPEN_MODES.length; i++) if (DETAIL_OPEN_MODES[i] == mode) return labels[i];
        return labels[0];
    }

    private String[] getDetailOpenModes() {
        return new String[]{getString(R.string.setting_detail_open_original_enhanced), getString(R.string.setting_detail_open_fusion), getString(R.string.setting_detail_open_enhanced), getString(R.string.setting_detail_open_player), getString(R.string.setting_detail_open_direct)};
    }

    private String getDetailThemeModeText() {
        String[] labels = getDetailThemeModes();
        int mode = Setting.getTmdbDetailStyle();
        for (int i = 0; i < DETAIL_THEME_MODES.length; i++) if (DETAIL_THEME_MODES[i] == mode) return labels[i];
        return labels[0];
    }

    private String[] getDetailThemeModes() {
        return new String[]{getString(R.string.setting_detail_theme_native), getString(R.string.setting_detail_theme_profile), getString(R.string.setting_detail_theme_cinema)};
    }

    private void setDetailOpenMode(View view) {
        AlertDialog alert = new MaterialAlertDialogBuilder(requireActivity(), R.style.Theme_WebHTV_LightDialog).setTitle(R.string.setting_detail_open_mode).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(getDetailOpenModes(), getDetailOpenModeIndex(), (dialog, which) -> {
            int mode = DETAIL_OPEN_MODES[which];
            if (Setting.isTmdbMode(mode) && !Setting.isTmdbReady()) {
                dialog.dismiss();
                Notify.show(R.string.detail_tmdb_need_key);
                com.fongmi.android.tv.ui.dialog.TmdbSourceDialog.create(requireActivity()).onDismiss(() -> {
                    if (Setting.isTmdbReady()) Setting.putDetailOpenMode(mode);
                    setText();
                }).show();
                return;
            }
            Setting.putDetailOpenMode(mode);
            setText();
            dialog.dismiss();
        }).show();
        LightDialog.apply(alert);
    }

    private int getDetailOpenModeIndex() {
        int mode = Setting.getDetailOpenMode();
        for (int i = 0; i < DETAIL_OPEN_MODES.length; i++) if (DETAIL_OPEN_MODES[i] == mode) return i;
        return 0;
    }

    private void setDetailThemeMode(View view) {
        if (!Setting.isTmdbMode(Setting.getDetailOpenMode())) return;
        AlertDialog alert = new MaterialAlertDialogBuilder(requireActivity(), R.style.Theme_WebHTV_LightDialog).setTitle(R.string.setting_detail_theme_mode).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(getDetailThemeModes(), getDetailThemeModeIndex(), (dialog, which) -> {
            Setting.putTmdbDetailStyle(DETAIL_THEME_MODES[which]);
            setText();
            dialog.dismiss();
        }).show();
        LightDialog.apply(alert);
    }

    private int getDetailThemeModeIndex() {
        int mode = Setting.getTmdbDetailStyle();
        for (int i = 0; i < DETAIL_THEME_MODES.length; i++) if (DETAIL_THEME_MODES[i] == mode) return i;
        return 0;
    }

    private void setPlaybackArtworkWall(View view) {
        Setting.putPlaybackArtworkWall(!Setting.isPlaybackArtworkWall());
        mBinding.playbackArtworkWallText.setText(getSwitch(Setting.isPlaybackArtworkWall()));
    }

    private void setWebHomeFullscreen(View view) {
        Setting.putWebHomeFullscreen(!Setting.isWebHomeFullscreen());
        mBinding.webHomeFullscreenText.setText(getSwitch(Setting.isWebHomeFullscreen()));
        if (requireActivity() instanceof HomeActivity activity) {
            if (!Setting.isWebHomeFullscreen()) {
                JsonObject object = new JsonObject();
                object.addProperty("mode", "normal");
                activity.setWebHomeChrome(object);
            }
            activity.refreshWebHomeChromeState();
        }
    }

    private void setCspWarmup(View view) {
        Setting.putCspWarmup(!Setting.isCspWarmup());
        mBinding.cspWarmupText.setText(getSwitch(Setting.isCspWarmup()));
    }

    private boolean clearSiteHealth(View view) {
        SiteHealthStore.clear();
        Notify.show(R.string.site_health_clear_done);
        return true;
    }

    private boolean clearWebHomeExtension(View view) {
        WebHomeExtensionRegistry.get().clear();
        Notify.show(R.string.web_home_extension_clear_done);
        return true;
    }

    private void openRepo(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Notify.show(R.string.manage_page_no_browser);
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) setText();
    }

    @Override
    public void onResume() {
        super.onResume();
        setText();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }
}
