package com.fongmi.android.tv.ui.dialog;

import android.graphics.Rect;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.GroupRule;
import com.fongmi.android.tv.setting.GroupRuleConfig;
import com.fongmi.android.tv.setting.GroupRuleStore;
import com.fongmi.android.tv.utils.Notify;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public final class GroupRuleDialog {

    private final FragmentActivity activity;
    private Runnable onChanged;

    public static GroupRuleDialog create(FragmentActivity activity) {
        return new GroupRuleDialog(activity);
    }

    private GroupRuleDialog(FragmentActivity activity) {
        this.activity = activity;
    }

    public GroupRuleDialog onChanged(Runnable callback) {
        this.onChanged = callback;
        return this;
    }

    public void show() {
        List<GroupRuleConfig.Entry> entries = GroupRuleConfig.entries();
        CharSequence[] labels = new CharSequence[entries.size()];
        boolean[] checked = new boolean[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            GroupRuleConfig.Entry entry = entries.get(i);
            GroupRule rule = entry.rule();
            String name = TextUtils.isEmpty(rule.getName()) ? rule.getId() : rule.getName();
            labels[i] = name + "\n" + rule.getSummary();
            checked[i] = entry.enabled();
        }
        AlertDialog dialog = builder()
                .setTitle(R.string.setting_group_rule)
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> {
                    GroupRule rule = entries.get(which).rule();
                    if (rule.isUser()) {
                        rule.setEnabled(isChecked);
                        GroupRuleStore.updateUser(rule);
                    } else {
                        GroupRuleStore.setEnabled(rule.getId(), isChecked);
                    }
                    changed();
                })
                .setPositiveButton(R.string.dialog_positive, null)
                .setNeutralButton(R.string.group_rule_add, (d, which) -> showEditor(null))
                .setNegativeButton(R.string.group_rule_manage, (d, which) -> showManage())
                .create();
        dialog.show();
        LightDialog.apply(dialog);
    }

    private void showManage() {
        List<GroupRule> userRules = GroupRuleStore.loadUser();
        List<GroupRuleConfig.Entry> interfaceEntries = new ArrayList<>();
        for (GroupRuleConfig.Entry entry : GroupRuleConfig.entries()) {
            if (GroupRule.SOURCE_INTERFACE.equals(entry.rule().getSource())) interfaceEntries.add(entry);
        }
        if (userRules.isEmpty() && interfaceEntries.isEmpty()) {
            Notify.show(R.string.group_rule_empty_custom);
            show();
            return;
        }
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (GroupRule rule : userRules) {
            labels.add(activity.getString(R.string.group_rule_edit_item, displayName(rule), rule.getRegex()));
            actions.add(() -> showEditor(rule));
        }
        for (GroupRuleConfig.Entry entry : interfaceEntries) {
            GroupRule rule = entry.rule();
            labels.add(activity.getString(R.string.group_rule_interface_item, displayName(rule), rule.getRegex()));
            actions.add(() -> showDetail(rule));
        }
        AlertDialog dialog = builder()
                .setTitle(R.string.group_rule_manage)
                .setItems(labels.toArray(new CharSequence[0]), (d, which) -> actions.get(which).run())
                .setNegativeButton(R.string.dialog_negative, (d, which) -> show())
                .create();
        dialog.show();
        LightDialog.apply(dialog);
    }

    private void showDetail(GroupRule rule) {
        AlertDialog dialog = builder()
                .setTitle(displayName(rule))
                .setMessage(rule.getSummary())
                .setPositiveButton(R.string.dialog_positive, (d, which) -> show())
                .create();
        dialog.show();
        LightDialog.apply(dialog);
    }

    private void showEditor(GroupRule existing) {
        MaterialAlertDialogBuilder builder = builder();
        View view = LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_group_rule_edit, null);
        TextInputEditText nameInput = view.findViewById(R.id.nameInput);
        TextInputEditText regexInput = view.findViewById(R.id.regexInput);
        if (existing != null) {
            nameInput.setText(existing.getName());
            regexInput.setText(existing.getRegex());
        }
        builder.setTitle(existing == null ? R.string.group_rule_add : R.string.group_rule_edit)
                .setView(view)
                .setPositiveButton(R.string.dialog_positive, null)
                .setNegativeButton(R.string.dialog_negative, null);
        if (existing != null) {
            builder.setNeutralButton(R.string.group_rule_delete, null);
        }
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            View positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            View negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            View neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            wireTextDpadFocus(nameInput, null, regexInput, null, null);
            wireTextDpadFocus(regexInput, nameInput, positive, null, null);
            wireDpadFocus(positive, regexInput, null, null, null);
            wireDpadFocus(negative, regexInput, null, null, null);
            wireDpadFocus(neutral, regexInput, null, null, null);
            positive.setOnClickListener(v -> {
                String name = text(nameInput);
                String regex = text(regexInput);
                if (TextUtils.isEmpty(regex)) {
                    Notify.show(R.string.group_rule_regex_required);
                    regexInput.requestFocus();
                    return;
                }
                GroupRule probe = GroupRule.createUser(name, regex);
                if (!probe.isValid()) {
                    Notify.show(R.string.group_rule_regex_invalid);
                    regexInput.requestFocus();
                    return;
                }
                if (existing == null) {
                    GroupRuleStore.addUser(probe);
                } else {
                    existing.setName(name);
                    existing.setRegex(regex);
                    existing.setEnabled(true);
                    GroupRuleStore.updateUser(existing);
                }
                dialog.dismiss();
                changed();
                show();
            });
            negative.setOnClickListener(v -> {
                dialog.dismiss();
                show();
            });
            if (neutral != null && existing != null) {
                neutral.setOnClickListener(v -> {
                    GroupRuleStore.deleteUser(existing.getId());
                    dialog.dismiss();
                    changed();
                    show();
                });
            }
            nameInput.requestFocus();
        });
        dialog.show();
        LightDialog.apply(dialog);
    }

    private MaterialAlertDialogBuilder builder() {
        return new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog);
    }

    private static void wireDpadFocus(View view, View up, View down, View left, View right) {
        if (view == null) return;
        view.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && up != null) return requestFocus(up);
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && down != null) return requestFocus(down);
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && left != null) return requestFocus(left);
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && right != null) return requestFocus(right);
            return false;
        });
    }

    private static void wireTextDpadFocus(EditText view, View up, View down, View left, View right) {
        if (view == null) return;
        view.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && up != null) return requestFocus(up);
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && down != null) return requestFocus(down);
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && left != null && isCursorAtStart(view)) return requestFocus(left);
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && right != null && isCursorAtEnd(view)) return requestFocus(right);
            return false;
        });
    }

    private static boolean requestFocus(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE || !view.isEnabled()) return false;
        boolean focused = view.requestFocus();
        if (focused) {
            view.post(() -> view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), false));
        }
        return focused;
    }

    private static boolean isCursorAtStart(EditText view) {
        return Math.max(0, view.getSelectionStart()) <= 0;
    }

    private static boolean isCursorAtEnd(EditText view) {
        int length = view.getText() == null ? 0 : view.getText().length();
        return Math.max(view.getSelectionStart(), view.getSelectionEnd()) >= length;
    }

    private static String text(EditText input) {
        return input == null || input.getText() == null ? "" : input.getText().toString().trim();
    }

    private String displayName(GroupRule rule) {
        return TextUtils.isEmpty(rule.getName()) ? rule.getId() : rule.getName();
    }

    private void changed() {
        if (onChanged != null) onChanged.run();
    }
}
