package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.textview.MaterialTextView;

public final class BackupProgressDialog extends DialogFragment {

    private String title = "";
    private String stage = "准备中";
    private int percent;
    private long bytes;
    private long total;
    private ProgressBar progress;
    private MaterialTextView detail;

    public static BackupProgressDialog open(FragmentManager manager, String title) {
        BackupProgressDialog dialog = new BackupProgressDialog();
        dialog.title = title == null ? "" : title;
        dialog.setCancelable(false);
        dialog.show(manager, BackupProgressDialog.class.getSimpleName());
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = new Dialog(requireContext());
        LinearLayoutCompat root = new LinearLayoutCompat(requireContext());
        root.setOrientation(LinearLayoutCompat.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(18));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(14));
        root.setBackground(background);

        MaterialTextView heading = text(title, 18, Color.parseColor("#202124"));
        heading.setGravity(Gravity.START);
        root.addView(heading, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        progress = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        LinearLayoutCompat.LayoutParams progressParams = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        progressParams.topMargin = dp(16);
        root.addView(progress, progressParams);

        detail = text("", 13, Color.parseColor("#5F6368"));
        LinearLayoutCompat.LayoutParams detailParams = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(10);
        root.addView(detail, detailParams);
        render();

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) window.setBackgroundDrawableResource(android.R.color.transparent);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog() == null ? null : getDialog().getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = Math.min(ResUtil.getScreenWidth() - dp(40), dp(480));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(params);
    }

    public void update(String stage, int percent, long bytes, long total) {
        this.stage = stage == null ? "" : stage;
        this.percent = Math.max(0, Math.min(100, percent));
        this.bytes = Math.max(0, bytes);
        this.total = Math.max(0, total);
        App.post(this::render);
    }

    public void finish() {
        App.post(this::dismissAllowingStateLoss);
    }

    private void render() {
        if (progress == null || detail == null) return;
        progress.setIndeterminate(percent <= 0);
        if (percent > 0) progress.setProgress(percent);
        String size = total > 0
                ? FileUtil.byteCountToDisplaySize(bytes) + " / " + FileUtil.byteCountToDisplaySize(total)
                : bytes > 0 ? FileUtil.byteCountToDisplaySize(bytes) : "";
        String value = percent > 0 ? percent + "%" : "";
        if (!size.isEmpty()) value = value.isEmpty() ? size : value + " · " + size;
        detail.setText(value.isEmpty() ? stage : stage + "\n" + value);
    }

    private MaterialTextView text(String value, int size, int color) {
        MaterialTextView view = new MaterialTextView(requireContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.1f);
        return view;
    }

    private int dp(int value) {
        return ResUtil.dp2px(value);
    }
}
