package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.mpv.MpvConfigStore;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Objects;

public class MpvConfigDialog extends BaseAlertDialog {

    private TextInputLayout nameLayout;
    private TextInputLayout urlLayout;
    private TextInputEditText nameInput;
    private TextInputEditText urlInput;
    private MaterialButton historyButton;
    private TabLayout tabLayout;
    private Runnable callback;
    private boolean append = true;
    private String target = MpvConfigStore.TARGET_MPV_CONF;

    public static void show(Fragment fragment, Runnable callback) {
        MpvConfigDialog dialog = new MpvConfigDialog();
        dialog.callback = callback;
        dialog.show(fragment.getChildFragmentManager(), null);
    }

    public static void show(FragmentActivity activity, Runnable callback) {
        MpvConfigDialog dialog = new MpvConfigDialog();
        dialog.callback = callback;
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    @Override
    @NonNull
    public android.app.Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        android.app.Dialog dialog = LightDialog.create(requireContext(), getString(R.string.player_mpv_config), createContent(), getString(R.string.dialog_positive), this::applySource, getString(R.string.dialog_negative), null, getString(R.string.lut_reset), this::resetDefault);
        initView();
        initEvent();
        return dialog;
    }

    @Override
    protected androidx.viewbinding.ViewBinding getBinding() {
        return null;
    }

    @Override
    protected com.google.android.material.dialog.MaterialAlertDialogBuilder getBuilder() {
        return null;
    }

    private View createContent() {
        androidx.appcompat.widget.LinearLayoutCompat root = new androidx.appcompat.widget.LinearLayoutCompat(requireContext());
        root.setOrientation(androidx.appcompat.widget.LinearLayoutCompat.VERTICAL);

        root.addView(createTabs(), new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(44)));

        nameLayout = inputLayout(R.string.mpv_config_name_hint);
        nameInput = editText();
        nameInput.setMaxLines(1);
        nameInput.setSingleLine(true);
        nameInput.setMaxEms(10);
        nameLayout.addView(nameInput);
        androidx.appcompat.widget.LinearLayoutCompat.LayoutParams nameParams = new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = ResUtil.dp2px(10);
        root.addView(nameLayout, nameParams);

        urlLayout = inputLayout(R.string.dialog_config_hint);
        urlLayout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        urlLayout.setEndIconDrawable(R.drawable.ic_action_choose);
        urlLayout.setEndIconTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#1A73E8")));
        urlInput = editText();
        urlInput.setSingleLine(true);
        urlInput.setHorizontallyScrolling(true);
        urlLayout.addView(urlInput);

        historyButton = historyButton();
        androidx.appcompat.widget.LinearLayoutCompat sourceRow = new androidx.appcompat.widget.LinearLayoutCompat(requireContext());
        sourceRow.setGravity(Gravity.CENTER_VERTICAL);
        sourceRow.setOrientation(androidx.appcompat.widget.LinearLayoutCompat.HORIZONTAL);
        sourceRow.addView(urlLayout, new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        androidx.appcompat.widget.LinearLayoutCompat.LayoutParams historyParams = new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(ResUtil.dp2px(40), ResUtil.dp2px(56));
        historyParams.leftMargin = ResUtil.dp2px(8);
        sourceRow.addView(historyButton, historyParams);
        androidx.appcompat.widget.LinearLayoutCompat.LayoutParams rowParams = new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = ResUtil.dp2px(12);
        root.addView(sourceRow, rowParams);
        return root;
    }

    private MaterialButton historyButton() {
        MaterialButton button = new MaterialButton(requireContext());
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setText("");
        button.setContentDescription(getString(R.string.mpv_config_history));
        button.setIconResource(R.drawable.ic_setting_history);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setIconSize(ResUtil.dp2px(24));
        button.setIconPadding(0);
        button.setIconTint(ColorStateList.valueOf(Color.parseColor("#1A73E8")));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(0, 0, 0, 0);
        button.setCornerRadius(ResUtil.dp2px(20));
        button.setFocusable(Util.isLeanback());
        button.setFocusableInTouchMode(false);
        button.setStrokeWidth(0);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        button.setOnClickListener(this::showHistory);
        return button;
    }

    private View createTabs() {
        tabLayout = new TabLayout(requireContext());
        tabLayout.setTabMode(TabLayout.MODE_FIXED);
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);
        tabLayout.setSelectedTabIndicatorColor(Color.parseColor("#1A73E8"));
        tabLayout.setSelectedTabIndicatorHeight(ResUtil.dp2px(2));
        tabLayout.setTabTextColors(Color.parseColor("#5F6368"), Color.parseColor("#1A73E8"));
        tabLayout.setTabRippleColor(ColorStateList.valueOf(Color.TRANSPARENT));
        tabLayout.setUnboundedRipple(false);
        tabLayout.setBackgroundColor(Color.TRANSPARENT);
        tabLayout.setFocusable(Util.isLeanback());
        tabLayout.setFocusableInTouchMode(false);
        for (String label : getTargetLabels()) tabLayout.addTab(tabLayout.newTab().setText(label), TextUtils.equals(label, target));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                String[] labels = getTargetLabels();
                if (tab.getPosition() < 0 || tab.getPosition() >= labels.length) return;
                target = labels[tab.getPosition()];
                bindTarget();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        return tabLayout;
    }

    private TextInputLayout inputLayout(int hint) {
        TextInputLayout layout = new TextInputLayout(requireContext());
        layout.setHint(getString(hint));
        layout.setBoxBackgroundColor(Color.WHITE);
        layout.setBoxStrokeColor(Color.parseColor("#DADCE0"));
        layout.setHintTextColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#5F6368")));
        return layout;
    }

    private TextInputEditText editText() {
        TextInputEditText input = new TextInputEditText(requireContext());
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setTextColor(Color.parseColor("#202124"));
        input.setHintTextColor(Color.parseColor("#8A8F98"));
        return input;
    }

    protected void initView() {
        bindTarget();
    }

    protected void initEvent() {
        urlLayout.setEndIconOnClickListener(this::chooseFile);
        urlInput.addTextChangedListener(new CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                detect(s.toString());
            }
        });
        urlInput.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) applySource(textView);
            return true;
        });
        nameInput.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) applySource(textView);
            return true;
        });
    }

    private void chooseFile(View view) {
        FileChooser.from(launcher).show("text/*", new String[]{"text/*", "application/octet-stream", "*/*"});
    }

    private void showHistory(View view) {
        CharSequence[] items = MpvConfigStore.historyLabels(target);
        if (items.length == 0) {
            Notify.show(R.string.mpv_config_history_empty);
            return;
        }
        MpvConfigHistoryDialog.show(getChildFragmentManager(), target, new MpvConfigHistoryDialog.Listener() {
            @Override
            public void onHistoryClick(int position) {
                runAsync(() -> MpvConfigStore.applyHistory(target, position));
            }

            @Override
            public void onHistoryChanged() {
                historyButton.setEnabled(MpvConfigStore.hasHistory(target));
            }
        });
    }

    private void applySource(View view) {
        String source = Objects.toString(urlInput.getText(), "").trim();
        String name = Objects.toString(nameInput.getText(), "").trim();
        if (TextUtils.isEmpty(source)) {
            Notify.show(R.string.remote_trust_config_url_required);
            urlInput.requestFocus();
            return;
        }
        runAsync(() -> MpvConfigStore.applySource(target, source, name));
    }

    private void resetDefault(View view) {
        runAsync(() -> MpvConfigStore.applyDefault(target));
    }

    private void bindTarget() {
        TabLayout.Tab tab = tabLayout.getTabAt(getTargetIndex());
        if (tab != null && !tab.isSelected()) tab.select();
        nameInput.setText(MpvConfigStore.getName(target));
        urlInput.setText(MpvConfigStore.getSource(target));
        urlInput.setSelection(urlInput.length());
        urlLayout.setHint(MpvConfigStore.TARGET_SCRIPTS.equals(target) ? getString(R.string.mpv_config_script_hint) : getString(R.string.dialog_config_hint));
        historyButton.setEnabled(MpvConfigStore.hasHistory(target));
    }

    private String[] getTargetLabels() {
        return new String[]{MpvConfigStore.TARGET_MPV_CONF, MpvConfigStore.TARGET_INPUT_CONF, MpvConfigStore.TARGET_SCRIPTS};
    }

    private int getTargetIndex() {
        if (MpvConfigStore.TARGET_INPUT_CONF.equals(target)) return 1;
        if (MpvConfigStore.TARGET_SCRIPTS.equals(target)) return 2;
        return 0;
    }

    private void detect(String s) {
        if (append && "h".equalsIgnoreCase(s)) {
            append = false;
            urlInput.append("ttp://");
        } else if (append && "f".equalsIgnoreCase(s)) {
            append = false;
            urlInput.append("ile://");
        } else if (s.length() > 1) {
            append = false;
        } else if (s.isEmpty()) {
            append = true;
        }
    }

    private void runAsync(ThrowingRunnable runnable) {
        Notify.progress(requireContext());
        Task.execute(() -> {
            try {
                runnable.run();
                App.post(() -> {
                    Notify.dismiss();
                    Notify.show(R.string.mpv_config_saved);
                    if (callback != null) callback.run();
                    dismissAllowingStateLoss();
                });
            } catch (Throwable e) {
                App.post(() -> {
                    Notify.dismiss();
                    Notify.show(e.getMessage());
                });
            }
        });
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        String path = FileChooser.getPathFromUri(result.getData().getData());
        if (TextUtils.isEmpty(path)) return;
        String name = Objects.toString(nameInput.getText(), "").trim();
        runAsync(() -> MpvConfigStore.applyFile(target, path, name));
    });

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

}
