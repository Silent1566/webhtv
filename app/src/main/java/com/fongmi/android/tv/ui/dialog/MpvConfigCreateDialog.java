package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogMpvConfigCreateBinding;
import com.fongmi.android.tv.player.mpv.MpvConfigStore;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MpvConfigCreateDialog extends BaseAlertDialog {

    public interface Listener {
        void onText(String name);

        void onImport(String name, String path);
    }

    private DialogMpvConfigCreateBinding binding;
    private Listener listener;
    private String target;

    public static void show(FragmentManager manager, String target, Listener listener) {
        MpvConfigCreateDialog dialog = new MpvConfigCreateDialog();
        dialog.target = target;
        dialog.listener = listener;
        dialog.show(manager, "mpv-config-create");
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogMpvConfigCreateBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        setupTvFocus();
    }

    @Override
    protected void initEvent() {
        binding.close.setOnClickListener(view -> dismiss());
        binding.textOption.setOnClickListener(view -> createText());
        binding.urlOption.setOnClickListener(view -> showUrlInput());
        binding.importOption.setOnClickListener(view -> chooseFile());
        binding.urlBack.setOnClickListener(view -> showOptions());
        binding.urlImport.setOnClickListener(view -> importUrl());
        binding.url.setOnEditorActionListener((view, actionId, event) -> {
            importUrl();
            return true;
        });
        binding.name.setOnEditorActionListener((view, actionId, event) -> {
            if (!Util.isLeanback()) return false;
            binding.textOption.requestFocus();
            return true;
        });
    }

    private void setupTvFocus() {
        if (!Util.isLeanback()) return;
        tvFocusable(binding.close);
        tvFocusable(binding.name);
        tvFocusable(binding.textOption);
        tvFocusable(binding.urlOption);
        tvFocusable(binding.importOption);
        tvFocusable(binding.url);
        tvFocusable(binding.urlBack);
        tvFocusable(binding.urlImport);
        binding.close.setNextFocusDownId(R.id.name);
        binding.name.setNextFocusUpId(R.id.close);
        binding.name.setNextFocusDownId(R.id.textOption);
        binding.textOption.setNextFocusUpId(R.id.name);
        binding.textOption.setNextFocusDownId(R.id.urlOption);
        binding.urlOption.setNextFocusUpId(R.id.textOption);
        binding.urlOption.setNextFocusDownId(R.id.importOption);
        binding.importOption.setNextFocusUpId(R.id.urlOption);
        binding.url.setNextFocusUpId(R.id.close);
        binding.url.setNextFocusDownId(R.id.urlBack);
        binding.urlBack.setNextFocusUpId(R.id.url);
        binding.urlBack.setNextFocusRightId(R.id.urlImport);
        binding.urlImport.setNextFocusUpId(R.id.url);
        binding.urlImport.setNextFocusLeftId(R.id.urlBack);
    }

    private static void tvFocusable(View view) {
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
    }

    private String name() {
        return binding.name.getText() == null ? "" : binding.name.getText().toString().trim();
    }

    private void createText() {
        String name = name();
        dismissAllowingStateLoss();
        App.post(() -> {
            if (listener != null) listener.onText(name);
        });
    }

    private void chooseFile() {
        String mime = MpvConfigStore.TARGET_SCRIPTS.equals(target) ? "application/octet-stream" : "text/*";
        FileChooser.from(launcher).show(mime, new String[]{"text/*", "application/octet-stream", "*/*"});
    }

    private void showUrlInput() {
        binding.chooseAction.setVisibility(View.GONE);
        binding.textOption.setVisibility(View.GONE);
        binding.urlOption.setVisibility(View.GONE);
        binding.importOption.setVisibility(View.GONE);
        binding.urlPanel.setVisibility(View.VISIBLE);
        binding.url.post(() -> binding.url.requestFocus());
    }

    private void showOptions() {
        binding.urlLayout.setError(null);
        binding.urlPanel.setVisibility(View.GONE);
        binding.chooseAction.setVisibility(View.VISIBLE);
        binding.textOption.setVisibility(View.VISIBLE);
        binding.urlOption.setVisibility(View.VISIBLE);
        binding.importOption.setVisibility(View.VISIBLE);
        binding.urlOption.requestFocus();
    }

    private void importUrl() {
        String url = binding.url.getText() == null ? "" : binding.url.getText().toString().trim();
        if (!isHttpUrl(url)) {
            binding.urlLayout.setError(getString(R.string.mpv_config_url_invalid));
            binding.url.requestFocus();
            return;
        }
        binding.urlLayout.setError(null);
        String name = name();
        dismissAllowingStateLoss();
        App.post(() -> {
            if (listener != null) listener.onImport(name, url);
        });
    }

    private static boolean isHttpUrl(String value) {
        return !TextUtils.isEmpty(value) && (value.regionMatches(true, 0, "http://", 0, 7)
                || value.regionMatches(true, 0, "https://", 0, 8));
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        String path = FileChooser.getPathFromUri(result.getData().getData());
        if (TextUtils.isEmpty(path)) {
            Notify.show(R.string.mpv_config_file_invalid);
            return;
        }
        String name = binding == null ? "" : name();
        dismissAllowingStateLoss();
        App.post(() -> {
            if (listener != null) listener.onImport(name, path);
        });
    });

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        Window window = dialog == null ? null : dialog.getWindow();
        if (window == null) return;
        boolean land = ResUtil.isLand(requireContext());
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = Math.min((int) (ResUtil.getScreenWidth(requireContext()) * (land ? 0.56f : 0.94f)), ResUtil.dp2px(620));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.dimAmount = 0.58f;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        window.setAttributes(params);
        window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
        if (Util.isLeanback()) binding.textOption.post(() -> binding.textOption.requestFocus());
    }
}
