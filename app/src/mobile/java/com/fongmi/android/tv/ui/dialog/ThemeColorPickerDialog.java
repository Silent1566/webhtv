package com.fongmi.android.tv.ui.dialog;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogThemeColorPickerBinding;
import com.fongmi.android.tv.theme.ThemeColorUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Edits one explicit theme color without touching persisted preferences. */
public final class ThemeColorPickerDialog extends BaseAlertDialog {

    private static final String ARG_LABEL = "label";
    private static final String ARG_COLOR = "color";
    private DialogThemeColorPickerBinding binding;
    private OnColorSelectedListener listener;

    public static void show(Fragment fragment, String label, String color, OnColorSelectedListener listener) {
        ThemeColorPickerDialog dialog = new ThemeColorPickerDialog();
        dialog.listener = listener;
        dialog.setArguments(new android.os.Bundle());
        dialog.requireArguments().putString(ARG_LABEL, label);
        dialog.requireArguments().putString(ARG_COLOR, color);
        dialog.show(fragment.getChildFragmentManager(), ThemeColorPickerDialog.class.getSimpleName());
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogThemeColorPickerBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        String label = requireArguments().getString(ARG_LABEL, getString(R.string.theme_color_edit));
        return builder().setTitle(label).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        binding.input.setText(requireArguments().getString(ARG_COLOR, "#6750A4"));
        binding.buttonCancel.setOnClickListener(view -> dismiss());
        binding.buttonApply.setOnClickListener(view -> apply());
        binding.input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                render(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        render(binding.input.getText() == null ? "" : binding.input.getText().toString());
    }

    private void render(String raw) {
        String normalized = ThemeColorUtil.normalize(raw);
        boolean valid = normalized != null;
        binding.inputLayout.setError(valid ? null : getString(R.string.theme_color_invalid));
        binding.buttonApply.setEnabled(valid);
        if (!valid) {
            binding.contrast.setText(R.string.theme_color_contrast_unknown);
            return;
        }
        int color = ThemeColorUtil.parse(normalized, ThemeColorUtil.BLACK);
        int onColor = ThemeColorUtil.readableOn(color);
        double contrast = ThemeColorUtil.contrast(onColor, color);
        binding.swatch.setBackgroundColor(color);
        binding.contrast.setText(getString(R.string.theme_color_contrast, ThemeColorUtil.format(onColor), contrast));
    }

    private void apply() {
        String normalized = ThemeColorUtil.normalize(String.valueOf(binding.input.getText()));
        if (normalized == null) return;
        if (listener != null) listener.onColorSelected(normalized);
        dismiss();
    }

    public interface OnColorSelectedListener {

        void onColorSelected(String color);
    }
}
