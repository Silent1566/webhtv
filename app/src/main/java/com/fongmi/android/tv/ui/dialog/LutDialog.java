package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.lut.LutPreset;
import com.fongmi.android.tv.player.lut.LutSetting;
import com.fongmi.android.tv.player.lut.LutStore;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public class LutDialog extends DialogFragment {

    private static final int[] STRENGTHS = new int[]{25, 50, 75, 100};
    private PlayerManager player;
    private Runnable callback;
    private List<LutPreset> presets;

    public static void show(Fragment fragment, Runnable callback) {
        LutDialog dialog = new LutDialog();
        dialog.callback = callback;
        dialog.show(fragment.getChildFragmentManager(), LutDialog.class.getSimpleName());
    }

    public static void show(FragmentActivity activity, PlayerManager player, Runnable callback) {
        LutDialog dialog = new LutDialog();
        dialog.player = player;
        dialog.callback = callback;
        dialog.show(activity.getSupportFragmentManager(), LutDialog.class.getSimpleName());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        presets = LutStore.refreshPresets();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(getTitleText())
                .setSingleChoiceItems(getLabels(), getCheckedIndex(), this::onSelect)
                .setNeutralButton(getStrengthText(), null)
                .setPositiveButton(R.string.dialog_positive, null);
        String message = getMessageText();
        if (!TextUtils.isEmpty(message)) builder.setMessage(message);
        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog == null) return;
        focusList(dialog);
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(view -> {
            LutSetting.putStrength(nextStrength());
            applyChange();
        });
    }

    private void focusList(AlertDialog dialog) {
        ListView list = dialog.getListView();
        if (list == null) return;
        int checked = Math.max(0, getCheckedIndex());
        list.post(() -> {
            list.setSelection(checked);
            list.requestFocus();
        });
    }

    private void onSelect(DialogInterface dialog, int which) {
        LutSetting.select(which == 0 ? null : presets.get(which - 1));
        applyChange();
    }

    private void applyChange() {
        if (player != null) player.applyLut(true);
        if (callback != null) callback.run();
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog == null) return;
        dialog.setTitle(getTitleText());
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setText(getStrengthText());
    }

    private int nextStrength() {
        int current = LutSetting.getStrength();
        for (int strength : STRENGTHS) if (current < strength) return strength;
        return STRENGTHS[0];
    }

    private String[] getLabels() {
        String[] labels = new String[presets.size() + 1];
        labels[0] = getString(R.string.lut_original);
        for (int i = 0; i < presets.size(); i++) labels[i + 1] = presets.get(i).getName();
        return labels;
    }

    private int getCheckedIndex() {
        if (!LutSetting.isEnabled()) return 0;
        String id = LutSetting.getPresetId();
        for (int i = 0; i < presets.size(); i++) if (presets.get(i).getId().equals(id)) return i + 1;
        return 0;
    }

    private String getTitleText() {
        return getString(R.string.lut_title_value, getString(R.string.player_lut), LutSetting.getSummary());
    }

    private String getStrengthText() {
        return getString(R.string.lut_strength_value, LutSetting.getStrength());
    }

    private String getMessageText() {
        StringBuilder builder = new StringBuilder();
        if (player != null && !TextUtils.isEmpty(player.getLutUnavailableReason())) builder.append(player.getLutUnavailableReason());
        if (presets.isEmpty()) {
            if (builder.length() > 0) builder.append('\n');
            builder.append(getString(R.string.lut_empty_presets));
        }
        return builder.toString();
    }
}
