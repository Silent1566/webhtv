package com.fongmi.android.tv.ui.dialog;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogAboutBinding;
import com.fongmi.android.tv.databinding.DialogGithubProxyBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.GithubProxyAdapter;
import com.fongmi.android.tv.utils.AppVersion;
import com.fongmi.android.tv.utils.GithubProxy;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class AboutDialog {

    private AboutDialog() {
    }

    public static void show(FragmentActivity activity, Runnable updateAction) {
        DialogAboutBinding binding = DialogAboutBinding.inflate(LayoutInflater.from(activity));
        binding.version.setText(activity.getString(R.string.about_version, AppVersion.fullName(), BuildConfig.FLAVOR_mode, BuildConfig.FLAVOR_abi));
        configureContentHeight(activity, binding);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setView(binding.getRoot())
                .create();
        binding.confirm.setOnClickListener(v -> dialog.dismiss());
        binding.checkUpdate.setOnClickListener(v -> {
            dialog.dismiss();
            if (updateAction != null) updateAction.run();
        });
        binding.githubProxy.setOnClickListener(v -> showGithubProxy(activity));
        dialog.setCanceledOnTouchOutside(false);
        boolean configured = configureWindow(activity, dialog);
        dialog.setOnShowListener(d -> {
            if (!configured) configureWindow(activity, dialog);
            binding.confirm.requestFocus();
        });
        dialog.show();
    }

    private static void showGithubProxy(FragmentActivity activity) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog);
        Context context = builder.getContext();
        DialogGithubProxyBinding binding = DialogGithubProxyBinding.inflate(LayoutInflater.from(context));
        GithubProxyAdapter adapter = new GithubProxyAdapter(new GithubProxyAdapter.OnClickListener() {
            @Override
            public void onActive(String item) {
                Setting.putGithubProxy(GithubProxy.setActive(item));
                refreshGithubProxy(binding);
            }

            @Override
            public void onRemove(String item) {
                Setting.putGithubProxy(GithubProxy.removeSource(item));
                refreshGithubProxy(binding);
            }
        });
        binding.list.setAdapter(adapter);
        binding.list.setHasFixedSize(true);
        refreshGithubProxy(binding);

        binding.add.setOnClickListener(v -> {
            String value = String.valueOf(binding.input.getText()).trim();
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                Notify.show(R.string.setting_github_proxy_invalid);
                return;
            }
            Setting.putGithubProxy(GithubProxy.addSource(value));
            binding.input.setText("");
            refreshGithubProxy(binding);
        });
        binding.reset.setOnClickListener(v -> {
            Setting.putGithubProxy(GithubProxy.defaultSources());
            refreshGithubProxy(binding);
        });

        View.OnKeyListener dpadNav = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && v == binding.input) {
                return binding.reset.requestFocus();
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && v == binding.input) {
                return binding.list.requestFocus();
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && v == binding.reset) {
                return binding.input.requestFocus();
            }
            return false;
        };
        binding.input.setOnKeyListener(dpadNav);
        binding.reset.setOnKeyListener(dpadNav);

        AlertDialog githubDialog = builder
                .setTitle(R.string.setting_github_proxy)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.dialog_positive, null)
                .create();
        githubDialog.setOnShowListener(d -> {
            binding.list.post(() -> {
                GithubProxyAdapter a = (GithubProxyAdapter) binding.list.getAdapter();
                if (a != null && a.getItemCount() > 0) {
                    binding.list.scrollToPosition(a.getSelected());
                    binding.list.requestFocus();
                }
            });
        });
        githubDialog.show();
        LightDialog.apply(githubDialog);
    }

    private static void refreshGithubProxy(DialogGithubProxyBinding binding) {
        ((GithubProxyAdapter) binding.list.getAdapter()).setItems(GithubProxy.getSources(), GithubProxy.getActive());
    }

    private static void configureContentHeight(FragmentActivity activity, DialogAboutBinding binding) {
        int screenHeight = ResUtil.getScreenHeight(activity);
        int maxHeight = (int) (screenHeight * (ResUtil.isLand(activity) ? 0.42f : 0.38f));
        int minHeight = ResUtil.dp2px(220);
        ViewGroup.LayoutParams params = binding.contentScroll.getLayoutParams();
        params.height = Math.max(minHeight, Math.min(maxHeight, ResUtil.dp2px(420)));
        binding.contentScroll.setLayoutParams(params);
    }

    private static boolean configureWindow(FragmentActivity activity, AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return false;
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = (int) (ResUtil.getScreenWidth(activity) * (ResUtil.isLand(activity) ? 0.62f : 0.92f));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setAttributes(params);
        window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
        return true;
    }
}
