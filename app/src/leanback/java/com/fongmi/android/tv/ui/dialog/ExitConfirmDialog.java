package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.databinding.DialogExitConfirmBinding;
import com.fongmi.android.tv.utils.ResUtil;

public final class ExitConfirmDialog extends DialogFragment {

    private Runnable onConfirm;

    public static ExitConfirmDialog create(Runnable onConfirm) {
        ExitConfirmDialog dialog = new ExitConfirmDialog();
        dialog.onConfirm = onConfirm;
        return dialog;
    }

    public void show(FragmentActivity activity) {
        for (Fragment fragment : activity.getSupportFragmentManager().getFragments()) {
            if (fragment instanceof ExitConfirmDialog) return;
        }
        show(activity.getSupportFragmentManager(), ExitConfirmDialog.class.getSimpleName());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        DialogExitConfirmBinding binding = DialogExitConfirmBinding.inflate(LayoutInflater.from(requireContext()));
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(binding.getRoot());
        dialog.setCanceledOnTouchOutside(false);
        binding.negative.setOnClickListener(view -> dismiss());
        binding.positive.setOnClickListener(view -> {
            Runnable confirm = onConfirm;
            dismissAllowingStateLoss();
            if (confirm != null) confirm.run();
        });
        dialog.setOnShowListener(view -> binding.positive.requestFocus());
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        Window window = dialog == null ? null : dialog.getWindow();
        if (window == null) return;
        int screenWidth = ResUtil.getScreenWidth(requireContext());
        int width = Math.max(ResUtil.dp2px(420), Math.min((int) (screenWidth * 0.42f), ResUtil.dp2px(560)));
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = width;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        params.dimAmount = 0.58f;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setAttributes(params);
        window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
    }
}
