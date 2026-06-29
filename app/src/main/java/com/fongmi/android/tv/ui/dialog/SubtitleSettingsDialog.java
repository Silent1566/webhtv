package com.fongmi.android.tv.ui.dialog;

import android.text.InputType;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public final class SubtitleSettingsDialog {

    public interface ValueCallback {
        void onValue(String value);
    }

    private SubtitleSettingsDialog() {
    }

    public static void showPreferredLanguage(FragmentActivity activity, String[] labels, String[] values, String currentValue, ValueCallback callback) {
        if (activity == null || labels == null || values == null || labels.length == 0 || values.length == 0) return;
        int checked = indexOf(values, currentValue);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.player_subtitle_language)
                .setNegativeButton(R.string.dialog_negative, null)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    if (which >= 0 && which < values.length && callback != null) callback.onValue(values[which]);
                    d.dismiss();
                })
                .show();
        LightDialog.apply(dialog);
    }

    public static void showAssrtToken(FragmentActivity activity, String currentToken, ValueCallback callback) {
        if (activity == null) return;
        TextInputEditText input = new TextInputEditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setText(TextUtils.isEmpty(currentToken) ? "" : currentToken);
        if (input.getText() != null) input.setSelection(input.length());

        TextInputLayout layout = new TextInputLayout(activity);
        layout.setHint(activity.getString(R.string.player_subtitle_assrt_token));
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dp(activity, 20);
        container.setPadding(horizontal, dp(activity, 8), horizontal, 0);
        container.addView(layout, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.player_subtitle_assrt_token)
                .setView(container)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (d, which) -> {
                    if (callback != null) callback.onValue(input.getText() == null ? "" : input.getText().toString().trim());
                })
                .show();
        LightDialog.apply(dialog);
    }

    private static int indexOf(String[] values, String currentValue) {
        for (int i = 0; i < values.length; i++) if (TextUtils.equals(values[i], currentValue)) return i;
        return 0;
    }

    private static int dp(FragmentActivity activity, int value) {
        return Math.round(activity.getResources().getDisplayMetrics().density * value);
    }
}
