package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogSpeedBinding;
import com.fongmi.android.tv.impl.SpeedListener;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SpeedDialog extends BaseAlertDialog {

    private DialogSpeedBinding binding;
    private float value;

    public static void show(Fragment fragment) {
        new SpeedDialog().show(fragment.getChildFragmentManager(), null);
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = LightDialog.create(requireContext(), getString(R.string.player_speed), getBinding().getRoot(), getString(R.string.dialog_positive), view -> {
            onPositive(null, 0);
            dismiss();
        }, getString(R.string.dialog_negative), view -> {
            onNegative(null, 0);
            dismiss();
        });
        initView();
        return dialog;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSpeedBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle(R.string.player_speed).setView(getBinding().getRoot()).setPositiveButton(R.string.dialog_positive, this::onPositive).setNegativeButton(R.string.dialog_negative, this::onNegative);
    }

    @Override
    protected void initView() {
        binding.slider.setValue(value = PlayerSetting.getSpeed());
    }

    private void onPositive(DialogInterface dialog, int which) {
        ((SpeedListener) requireParentFragment()).setSpeed(binding.slider.getValue());
    }

    private void onNegative(DialogInterface dialog, int which) {
        ((SpeedListener) requireParentFragment()).setSpeed(value);
    }
}
