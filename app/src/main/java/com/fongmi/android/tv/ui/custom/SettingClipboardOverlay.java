package com.fongmi.android.tv.ui.custom;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.widget.NestedScrollView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.dialog.LightDialog;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SettingClipboardOverlay {

    private static final int MAX_ITEMS = 100;
    private static final int MAX_DISPLAY = 260;
    private static final List<String> HISTORY = new ArrayList<>();
    private static int savedTriggerLeft = -1;
    private static int savedTriggerTop = -1;
    private static boolean triggerMovedByUser;

    private final Activity activity;
    private final DialogFragment fragment;
    private final View root;
    private final ViewGroup host;
    private final ClipboardManager clipboard;
    private final Set<String> selected = new LinkedHashSet<>();
    private boolean expanded;
    private final ClipboardManager.OnPrimaryClipChangedListener clipListener = this::captureSystemClipboard;
    private final ViewTreeObserver.OnGlobalFocusChangeListener focusListener = (oldFocus, newFocus) -> {
        if (newFocus instanceof EditText) lastInput = (EditText) newFocus;
    };
    private final ViewTreeObserver.OnGlobalLayoutListener layoutListener = () -> {
        if (expanded) {
            updatePanelSize();
            updateDialogAvoidance();
        }
    };
    private Dialog triggerDialog;
    private Dialog panelDialog;
    private LinearLayoutCompat panel;
    private LinearLayoutCompat list;
    private NestedScrollView scroll;
    private MaterialButton trigger;
    private EditText lastInput;
    private boolean dragging;
    private float downRawX;
    private float downRawY;
    private int downLeft;
    private int downTop;
    private int panelHeight;

    private SettingClipboardOverlay(Activity activity, DialogFragment fragment, View root, ViewGroup host) {
        this.activity = activity;
        this.fragment = fragment;
        this.root = root;
        this.host = host;
        this.clipboard = (ClipboardManager) root.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
    }

    public static SettingClipboardOverlay attach(DialogFragment fragment, View root) {
        if (!Util.isMobile()) return null;
        Activity activity = fragment.getActivity();
        if (activity == null || !(activity.getWindow().getDecorView() instanceof ViewGroup)) return null;
        SettingClipboardOverlay overlay = new SettingClipboardOverlay(activity, fragment, root, (ViewGroup) activity.getWindow().getDecorView());
        overlay.attach();
        return overlay;
    }

    public static void record(String value) {
        String text = normalize(value);
        if (TextUtils.isEmpty(text)) return;
        synchronized (HISTORY) {
            HISTORY.remove(text);
            HISTORY.add(0, text);
            while (HISTORY.size() > MAX_ITEMS) HISTORY.remove(HISTORY.size() - 1);
        }
    }

    public void detach() {
        if (clipboard != null) clipboard.removePrimaryClipChangedListener(clipListener);
        try {
            root.getViewTreeObserver().removeOnGlobalFocusChangeListener(focusListener);
        } catch (Throwable ignored) {
        }
        try {
            host.getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
        } catch (Throwable ignored) {
        }
        resetDialogAvoidance();
        if (triggerDialog != null) triggerDialog.dismiss();
        if (panelDialog != null) panelDialog.dismiss();
        triggerDialog = null;
        panelDialog = null;
        panel = null;
        list = null;
        scroll = null;
        trigger = null;
        selected.clear();
    }

    private void attach() {
        if (triggerDialog != null) return;
        captureSystemClipboard();
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(focusListener);
        host.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
        if (clipboard != null) clipboard.addPrimaryClipChangedListener(clipListener);
        buildTrigger();
        buildPanel();
        render();
    }

    private void buildTrigger() {
        trigger = iconButton();
        trigger.setOnClickListener(view -> {
            expanded = !expanded;
            if (expanded) hideKeyboard();
            captureSystemClipboard();
            render();
        });
        trigger.setFocusable(false);
        trigger.setOnTouchListener(this::onTriggerTouch);
        triggerDialog = floatingDialog(trigger, dp(42), dp(40));
        host.post(() -> {
            showDialog(triggerDialog, Gravity.TOP | Gravity.START, 0, 0);
            placeTrigger(false);
        });
    }

    private void buildPanel() {
        panel = new LinearLayoutCompat(root.getContext());
        panel.setOrientation(LinearLayoutCompat.VERTICAL);
        panel.setPadding(dp(10), dp(8), dp(10), dp(10));
        panel.setBackground(round(Color.WHITE, 10, Color.parseColor("#DADCE0")));
        panel.setClickable(true);
        panel.setFocusable(false);

        LinearLayoutCompat actions = new LinearLayoutCompat(root.getContext());
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setOrientation(LinearLayoutCompat.HORIZONTAL);
        addAction(actions, "插入", view -> insert());
        addAction(actions, "编辑", view -> edit());
        addAction(actions, "删除", view -> delete());
        addAction(actions, "复制", view -> copy());
        panel.addView(actions, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));

        scroll = new NestedScrollView(root.getContext());
        scroll.setFillViewport(false);
        list = new LinearLayoutCompat(root.getContext());
        list.setOrientation(LinearLayoutCompat.VERTICAL);
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayoutCompat.LayoutParams scrollParams = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220));
        scrollParams.topMargin = dp(8);
        panel.addView(scroll, scrollParams);

        panelDialog = floatingDialog(panel, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        host.post(this::updatePanelSize);
    }

    private void render() {
        if (trigger == null || panel == null || list == null) return;
        trigger.setChecked(expanded);
        if (expanded) {
            showDialog(panelDialog, Gravity.BOTTOM | Gravity.START, 0, panelBottomOffset());
            host.post(() -> {
                updatePanelSize();
                updateDialogAvoidance();
            });
        } else {
            if (panelDialog != null) panelDialog.dismiss();
            resetDialogAvoidance();
        }
        list.removeAllViews();
        List<String> items = snapshot();
        if (items.isEmpty()) {
            MaterialTextView empty = text("暂无剪贴内容", 13, Color.parseColor("#5F6368"), false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(20), 0, dp(20));
            list.addView(empty);
            return;
        }
        for (String item : items) list.addView(row(item));
    }

    private View row(String value) {
        LinearLayoutCompat row = new LinearLayoutCompat(root.getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayoutCompat.HORIZONTAL);
        row.setPadding(dp(2), dp(5), dp(4), dp(5));
        row.setBackground(round(selected.contains(value) ? Color.parseColor("#E8F0FE") : Color.parseColor("#F8F9FA"), 7, selected.contains(value) ? Color.parseColor("#D2E3FC") : Color.parseColor("#E0E3E7")));
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(5);
        row.setLayoutParams(params);

        MaterialCheckBox check = new MaterialCheckBox(root.getContext());
        check.setButtonTintList(ContextCompat.getColorStateList(root.getContext(), R.color.dialog_checkbox_tint));
        check.setChecked(selected.contains(value));
        check.setOnClickListener(view -> toggle(value));
        row.addView(check, new LinearLayoutCompat.LayoutParams(dp(38), dp(38)));

        MaterialTextView content = text(display(value), 13, Color.parseColor("#202124"), false);
        content.setMaxLines(3);
        content.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(content, new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.setOnClickListener(view -> toggle(value));
        return row;
    }

    private void toggle(String value) {
        if (selected.contains(value)) selected.remove(value);
        else selected.add(value);
        render();
    }

    private void insert() {
        EditText input = activeInput();
        if (input == null) {
            Notify.show("请先点一下输入框");
            return;
        }
        String text = selectedText();
        if (TextUtils.isEmpty(text)) {
            Notify.show("请选择剪贴内容");
            return;
        }
        int start = Math.max(0, input.getSelectionStart());
        int end = Math.max(0, input.getSelectionEnd());
        Editable editable = input.getText();
        if (editable == null) {
            input.setText(text);
            input.setSelection(input.length());
        } else {
            editable.replace(Math.min(start, end), Math.max(start, end), text);
        }
        input.requestFocus();
    }

    private void edit() {
        String item = firstSelected();
        if (TextUtils.isEmpty(item)) {
            Notify.show("请选择一条内容");
            return;
        }
        TextInputEditText input = new TextInputEditText(root.getContext());
        input.setSingleLine(false);
        input.setMinLines(4);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setText(item);
        TextInputLayout layout = new TextInputLayout(root.getContext());
        layout.setHint("剪贴内容");
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final Dialog[] holder = new Dialog[1];
        holder[0] = LightDialog.create(root.getContext(), "编辑剪贴板", layout, root.getContext().getString(R.string.dialog_positive), view -> {
            String next = normalize(input.getText() == null ? "" : input.getText().toString());
            if (TextUtils.isEmpty(next)) return;
            replace(item, next);
            selected.clear();
            selected.add(next);
            render();
            holder[0].dismiss();
        }, root.getContext().getString(R.string.dialog_negative), null);
        holder[0].show();
    }

    private void delete() {
        if (selected.isEmpty()) {
            Notify.show("请选择要删除的内容");
            return;
        }
        synchronized (HISTORY) {
            HISTORY.removeAll(selected);
        }
        selected.clear();
        render();
    }

    private void copy() {
        String text = selectedText();
        if (TextUtils.isEmpty(text)) {
            Notify.show("请选择剪贴内容");
            return;
        }
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("clipboard", text));
        record(text);
        Notify.show("已复制");
    }

    private EditText activeInput() {
        View focused = root.findFocus();
        if (focused instanceof EditText) lastInput = (EditText) focused;
        return lastInput;
    }

    private boolean onTriggerTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = false;
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                downLeft = triggerMovedByUser && savedTriggerLeft >= 0 ? savedTriggerLeft : defaultTriggerLeft();
                downTop = triggerMovedByUser && savedTriggerTop >= 0 ? savedTriggerTop : defaultTriggerTop();
                return false;
            case MotionEvent.ACTION_MOVE:
                int dx = Math.round(event.getRawX() - downRawX);
                int dy = Math.round(event.getRawY() - downRawY);
                if (!dragging && Math.abs(dx) < dp(4) && Math.abs(dy) < dp(4)) return false;
                dragging = true;
                moveTrigger(downLeft + dx, downTop + dy, true);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!dragging) return false;
                dragging = false;
                return true;
            default:
                return false;
        }
    }

    private void placeTrigger(boolean forceDefault) {
        if (triggerDialog == null || host.getWidth() == 0) return;
        if (!forceDefault && triggerMovedByUser && savedTriggerLeft >= 0 && savedTriggerTop >= 0) {
            moveTrigger(savedTriggerLeft, savedTriggerTop, false);
            return;
        }
        moveTrigger(defaultTriggerLeft(), defaultTriggerTop(), false);
    }

    private void moveTrigger(int left, int top, boolean persist) {
        if (triggerDialog == null || host.getWidth() == 0 || host.getHeight() == 0) return;
        int width = dp(42);
        int height = dp(40);
        int maxLeft = Math.max(dp(4), host.getWidth() - width - dp(4));
        int maxTop = Math.max(statusBarHeight() + dp(4), host.getHeight() - height - navigationBarHeight() - dp(4));
        int safeLeft = Math.max(dp(4), Math.min(left, maxLeft));
        int safeTop = Math.max(statusBarHeight() + dp(4), Math.min(top, maxTop));
        updateWindow(triggerDialog, Gravity.TOP | Gravity.START, safeLeft, safeTop, width, height);
        if (persist) {
            savedTriggerLeft = safeLeft;
            savedTriggerTop = safeTop;
            triggerMovedByUser = true;
        }
    }

    private int defaultTriggerLeft() {
        return host.getWidth() - dp(156);
    }

    private int defaultTriggerTop() {
        return statusBarHeight() + dp(8);
    }

    private void updatePanelSize() {
        if (panel == null || scroll == null || host.getHeight() == 0) return;
        int target = Math.min(dp(320), Math.max(dp(220), host.getHeight() * 42 / 100));
        panelHeight = target;
        ViewGroup.LayoutParams scrollParams = scroll.getLayoutParams();
        int scrollHeight = Math.max(dp(150), target - dp(58));
        if (scrollParams.height != scrollHeight) {
            scrollParams.height = scrollHeight;
            scroll.setLayoutParams(scrollParams);
        }
        updateWindow(panelDialog, Gravity.BOTTOM | Gravity.START, 0, panelBottomOffset(), ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void updateDialogAvoidance() {
        if (!expanded || panel == null || fragment.getDialog() == null || fragment.getDialog().getWindow() == null) return;
        Rect rootRect = new Rect();
        if (!root.getGlobalVisibleRect(rootRect)) return;
        int panelTop = panelTop();
        EditText input = activeInput();
        Rect inputRect = new Rect();
        boolean hasInput = input != null && input.getGlobalVisibleRect(inputRect);
        int overlap = hasInput ? inputRect.bottom - panelTop + dp(18) : rootRect.bottom - panelTop + dp(14);
        if (overlap <= 0) {
            root.animate().translationY(0).setDuration(140).start();
            return;
        }
        int safeTop = statusBarHeight() + dp(8);
        int maxShift = Math.max(0, rootRect.top - safeTop + dp(120));
        if (hasInput) maxShift = Math.max(maxShift, Math.max(0, inputRect.top - safeTop));
        int shift = Math.min(overlap, maxShift);
        root.animate().translationY(-shift).setDuration(160).start();
    }

    private void resetDialogAvoidance() {
        if (fragment.getDialog() == null || fragment.getDialog().getWindow() == null) return;
        root.animate().translationY(0).setDuration(120).start();
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) root.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            View focus = activeInput();
            if (imm != null && focus != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        } catch (Throwable ignored) {
        }
    }

    private Dialog floatingDialog(View content, int width, int height) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);
        dialog.setCanceledOnTouchOutside(false);
        Window window = dialog.getWindow();
        if (window != null) prepareWindow(window, width, height, false);
        return dialog;
    }

    private void showDialog(Dialog dialog, int gravity, int x, int y) {
        if (dialog == null) return;
        if (!dialog.isShowing()) dialog.show();
        updateWindow(dialog, gravity, x, y, dialog == triggerDialog ? dp(42) : ViewGroup.LayoutParams.MATCH_PARENT, dialog == triggerDialog ? dp(40) : ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void updateWindow(Dialog dialog, int gravity, int x, int y, int width, int height) {
        if (dialog == null || !dialog.isShowing()) return;
        Window window = dialog.getWindow();
        if (window == null) return;
        prepareWindow(window, width, height, dialog == triggerDialog);
        WindowManager.LayoutParams params = window.getAttributes();
        params.gravity = gravity;
        params.x = x;
        params.y = y;
        params.width = width;
        params.height = height;
        params.dimAmount = 0;
        window.setAttributes(params);
    }

    private void prepareWindow(Window window, int width, int height, boolean allowOutOfBounds) {
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        if (allowOutOfBounds) window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        else window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        window.setLayout(width, height);
    }

    private String selectedText() {
        if (selected.isEmpty()) return "";
        return TextUtils.join("\n", new ArrayList<>(selected));
    }

    private String firstSelected() {
        return selected.isEmpty() ? "" : selected.iterator().next();
    }

    private void captureSystemClipboard() {
        try {
            if (clipboard == null || !clipboard.hasPrimaryClip()) return;
            ClipData data = clipboard.getPrimaryClip();
            if (data == null || data.getItemCount() == 0) return;
            CharSequence text = data.getItemAt(0).coerceToText(root.getContext());
            record(text == null ? "" : text.toString());
        } catch (Throwable ignored) {
        }
    }

    private void replace(String oldValue, String newValue) {
        synchronized (HISTORY) {
            int index = HISTORY.indexOf(oldValue);
            HISTORY.remove(newValue);
            if (index >= 0) HISTORY.set(index, newValue);
            else HISTORY.add(0, newValue);
            while (HISTORY.size() > MAX_ITEMS) HISTORY.remove(HISTORY.size() - 1);
        }
    }

    private List<String> snapshot() {
        synchronized (HISTORY) {
            return new ArrayList<>(HISTORY);
        }
    }

    private void addAction(LinearLayoutCompat parent, String label, View.OnClickListener listener) {
        MaterialButton button = actionButton(label);
        button.setOnClickListener(listener);
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(0, dp(30), 1);
        if (parent.getChildCount() > 0) params.leftMargin = dp(6);
        parent.addView(button, params);
    }

    private MaterialButton iconButton() {
        MaterialButton button = new MaterialButton(root.getContext());
        button.setCheckable(true);
        button.setIconResource(R.drawable.ic_setting_clipboard);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setIconPadding(0);
        button.setIconSize(dp(20));
        button.setIconTint(ContextCompat.getColorStateList(root.getContext(), R.color.dialog_tonal_button_text));
        button.setBackgroundTintList(ContextCompat.getColorStateList(root.getContext(), R.color.dialog_tonal_button_bg));
        button.setStrokeColor(ContextCompat.getColorStateList(root.getContext(), R.color.dialog_outlined_button_stroke));
        button.setStrokeWidth(dp(1));
        button.setMinWidth(0);
        button.setPadding(0, 0, 0, 0);
        button.setContentDescription("剪贴板");
        return button;
    }

    private MaterialButton actionButton(String label) {
        MaterialButton button = new MaterialButton(root.getContext());
        button.setText(label);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(dp(30));
        button.setMinimumHeight(dp(30));
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setBackgroundTintList(ContextCompat.getColorStateList(root.getContext(), R.color.dialog_tonal_button_bg));
        button.setTextColor(ContextCompat.getColorStateList(root.getContext(), R.color.dialog_tonal_button_text));
        return button;
    }

    private MaterialTextView text(String value, int sp, int color, boolean bold) {
        MaterialTextView view = new MaterialTextView(root.getContext());
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setSingleLine(false);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private String display(String value) {
        String text = value == null ? "" : value.trim();
        if (text.length() <= MAX_DISPLAY) return text;
        return "..." + text.substring(text.length() - MAX_DISPLAY);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String text = value.trim();
        return text.length() == 0 ? "" : text;
    }

    private int dp(int value) {
        return ResUtil.dp2px(value);
    }

    private int statusBarHeight() {
        int id = root.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? root.getResources().getDimensionPixelSize(id) : 0;
    }

    private int navigationBarHeight() {
        int id = root.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? root.getResources().getDimensionPixelSize(id) : dp(8);
    }

    private int panelBottomOffset() {
        return 0;
    }

    private int panelTop() {
        Rect rect = new Rect();
        if (panel.getGlobalVisibleRect(rect) && rect.height() > 0) return rect.top;
        int height = panelHeight > 0 ? panelHeight : Math.min(dp(320), Math.max(dp(220), host.getHeight() * 42 / 100));
        return Math.max(statusBarHeight() + dp(80), host.getHeight() - height - panelBottomOffset());
    }
}
