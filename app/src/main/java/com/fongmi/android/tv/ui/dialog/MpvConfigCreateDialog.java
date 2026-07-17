package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
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
    }

    @Override
    protected void initEvent() {
        binding.close.setOnClickListener(view -> dismiss());
        binding.textOption.setOnClickListener(view -> createText());
        binding.importOption.setOnClickListener(view -> chooseFile());
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
        window.setAttributes(params);
        window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
    }
}
