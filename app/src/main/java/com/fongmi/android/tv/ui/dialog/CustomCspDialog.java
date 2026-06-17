package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.databinding.AdapterCustomCspBinding;
import com.fongmi.android.tv.databinding.DialogCustomCspBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.CustomCspSetting;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Path;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CustomCspDialog extends BaseAlertDialog {

    private static final int MIN_INSERT_INDEX = 0;
    private static final int MAX_INSERT_INDEX = 9;
    private static final String KIND_WEB_HOME = "webHome";
    private static final String KIND_CSP = "csp";
    private static final String KIND_LIVE = "live";

    private DialogCustomCspBinding binding;
    private CustomCspSetting.Registry registry;
    private CspAdapter adapter;
    private CustomCspSetting.Item pendingImport;
    private boolean pendingExtensionImport;
    private CspEditor editor;
    private TextInputEditText recognizeInput;
    private CustomCspSetting.Item editingItem;
    private int editingPosition = -1;
    private Runnable callback;
    private boolean enabled;
    private boolean textMode;
    private boolean editMode;
    private boolean recognizeMode;
    private boolean saved;
    private long lastAddTime;

    public static void show(Fragment fragment, Runnable callback) {
        CustomCspDialog dialog = new CustomCspDialog();
        dialog.callback = callback;
        dialog.show(fragment.getChildFragmentManager(), null);
    }

    public static void show(FragmentActivity activity, Runnable callback) {
        CustomCspDialog dialog = new CustomCspDialog();
        dialog.callback = callback;
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogCustomCspBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() == null) return;
        setCancelable(false);
        getDialog().setCanceledOnTouchOutside(false);
        Window window = getDialog().getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        int screenWidth = ResUtil.getScreenWidth(requireContext());
        int screenHeight = ResUtil.getScreenHeight(requireContext());
        boolean land = ResUtil.isLand(requireContext());
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        params.width = (int) (screenWidth * (land ? 0.76f : 0.94f));
        params.height = land ? (int) (screenHeight * 0.98f) : WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
        ViewGroup.LayoutParams rootParams = binding.root.getLayoutParams();
        rootParams.height = land ? params.height : ViewGroup.LayoutParams.WRAP_CONTENT;
        binding.root.setLayoutParams(rootParams);
        LinearLayoutCompat.LayoutParams scrollParams = (LinearLayoutCompat.LayoutParams) binding.contentScroll.getLayoutParams();
        scrollParams.height = land ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT;
        scrollParams.weight = land ? 1 : 0;
        binding.contentScroll.setLayoutParams(scrollParams);
        binding.contentScroll.setMaxHeight(land ? 0 : (int) (screenHeight * 0.58f));
        binding.enabled.requestFocus();
    }

    @Override
    protected void initView() {
        registry = CustomCspSetting.load();
        adapter = new CspAdapter(new ArrayList<>(registry.getItems()));
        enabled = registry.isEnabled();
        updateEnabledText();
        setInsertIndex(registry.getInsertIndex());
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setItemAnimator(null);
        binding.recycler.setAdapter(adapter);
        binding.modeGroup.check(R.id.uiMode);
        syncJsonFromForm(false);
        showTextMode(false);
        showList();
    }

    @Override
    protected void initEvent() {
        binding.enabled.setOnClickListener(view -> {
            enabled = !enabled;
            updateEnabledText();
        });
        binding.insertMinus.setOnClickListener(view -> changeInsertIndex(-1));
        binding.insertPlus.setOnClickListener(view -> changeInsertIndex(1));
        binding.modeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.textMode && !showTextMode(true)) binding.modeGroup.check(R.id.uiMode);
            if (checkedId == R.id.uiMode && !showTextMode(false)) binding.modeGroup.check(R.id.textMode);
        });
        setupScrollableText(binding.jsonText);
        binding.add.setOnClickListener(view -> addItem());
        binding.recognize.setOnClickListener(view -> showRecognizePanel());
        binding.negative.setOnClickListener(view -> {
            if (editMode) showList();
            else closeAndSave(false);
        });
        binding.positive.setOnClickListener(view -> onPositive());
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        save(false);
        super.onCancel(dialog);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        save(false);
        super.onDismiss(dialog);
    }

    private void updateEnabledText() {
        binding.enabled.setText(enabled ? R.string.setting_enable : R.string.setting_disable);
        binding.enabled.setAlpha(enabled ? 1.0f : 0.65f);
    }

    private void setupScrollableText(EditText input) {
        setupScrollableText(input, true);
    }

    private void setupScrollableText(EditText input, boolean horizontal) {
        input.setSelectAllOnFocus(false);
        input.setHorizontallyScrolling(horizontal);
        input.setHorizontalScrollBarEnabled(horizontal);
        input.setVerticalScrollBarEnabled(true);
        input.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                view.post(() -> disallowParentIntercept(view, false));
            } else {
                disallowParentIntercept(view, true);
            }
            return false;
        });
    }

    private void disallowParentIntercept(View view, boolean disallow) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
            parent = parent.getParent();
        }
    }

    private void changeInsertIndex(int delta) {
        setInsertIndex(getInsertIndex() + delta);
    }

    private void setInsertIndex(int index) {
        int value = clampInsertIndex(index);
        binding.insertIndex.setText(String.valueOf(value + 1));
        binding.insertMinus.setAlpha(value > MIN_INSERT_INDEX ? 1.0f : 0.45f);
        binding.insertPlus.setAlpha(value < MAX_INSERT_INDEX ? 1.0f : 0.45f);
    }

    private boolean showTextMode(boolean text) {
        if (editMode) return false;
        if (text == textMode) {
            updateModeVisibility();
            return true;
        }
        if (text && !syncJsonFromForm(true)) return false;
        else if (!syncFormFromJson(true)) return false;
        textMode = text;
        updateModeVisibility();
        return true;
    }

    private void updateModeVisibility() {
        binding.recycler.setVisibility(textMode ? View.GONE : View.VISIBLE);
        binding.jsonLayout.setVisibility(textMode ? View.VISIBLE : View.GONE);
        binding.editPanel.setVisibility(editMode ? View.VISIBLE : View.GONE);
        binding.recycler.setVisibility(textMode || editMode ? View.GONE : View.VISIBLE);
        binding.jsonLayout.setVisibility(textMode ? View.VISIBLE : View.GONE);
        binding.add.setVisibility(textMode || editMode ? View.GONE : View.VISIBLE);
        binding.recognize.setVisibility(editMode ? View.GONE : View.VISIBLE);
        binding.enabled.setVisibility(editMode ? View.GONE : View.VISIBLE);
        binding.globalPanel.setVisibility(editMode ? View.GONE : View.VISIBLE);
        binding.modeGroup.setVisibility(editMode ? View.GONE : View.VISIBLE);
    }

    private void addItem() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastAddTime < 500) return;
        lastAddTime = now;
        CustomCspSetting.Item item = CustomCspSetting.createDefaultItem();
        item.setName(nextName(KIND_WEB_HOME));
        showEdit(item, -1);
    }

    private String nextName(String kind) {
        String prefix = getKindPrefix(kind);
        int max = 0;
        for (CustomCspSetting.Item item : adapter.getItems()) {
            if (!item.getKind().equals(kind)) continue;
            String name = item.getName();
            if (name.equals(prefix)) max = Math.max(max, 1);
            else if (name.startsWith(prefix + " ")) max = Math.max(max, parseInt(name.substring(prefix.length() + 1), 0));
        }
        int next = Math.max(1, max + 1);
        return getString(KIND_WEB_HOME.equals(kind) ? R.string.setting_custom_csp_webhome_name : KIND_LIVE.equals(kind) ? R.string.setting_custom_csp_live_name : R.string.setting_custom_csp_common_name, next);
    }

    private String getKindPrefix(String kind) {
        return getString(KIND_WEB_HOME.equals(kind) ? R.string.setting_custom_csp_webhome : KIND_LIVE.equals(kind) ? R.string.setting_custom_csp_live : R.string.setting_custom_csp_common);
    }

    private boolean onPositive() {
        if (recognizeMode) return saveRecognize();
        if (editMode) return saveEdit();
        return closeAndSave(true);
    }

    private boolean closeAndSave(boolean validate) {
        if (!save(validate)) return false;
        focusBeforeDismiss();
        dismiss();
        return true;
    }

    private void focusBeforeDismiss() {
        if (binding == null) return;
        View focus = binding.root.findFocus();
        if (focus != null) focus.clearFocus();
        binding.positive.requestFocus();
    }

    private void showList() {
        editMode = false;
        recognizeMode = false;
        editor = null;
        recognizeInput = null;
        editingItem = null;
        editingPosition = -1;
        binding.editPanel.removeAllViews();
        binding.negative.setText(R.string.dialog_negative);
        binding.positive.setText(R.string.dialog_positive);
        updateModeVisibility();
        adapter.notifyDataSetChanged();
        if (textMode) binding.jsonText.requestFocus();
        else binding.add.requestFocus();
    }

    private void showEdit(CustomCspSetting.Item item, int position) {
        syncAllVisibleRows();
        editMode = true;
        editingPosition = position;
        editingItem = copy(item);
        binding.editPanel.removeAllViews();
        AdapterCustomCspBinding form = AdapterCustomCspBinding.inflate(LayoutInflater.from(requireContext()), binding.editPanel, false);
        binding.editPanel.addView(form.getRoot());
        editor = new CspEditor(form);
        editor.bind(editingItem);
        binding.negative.setText(R.string.playback_webhook_back);
        binding.positive.setText(R.string.playback_webhook_save);
        updateModeVisibility();
        binding.contentScroll.scrollTo(0, 0);
        form.name.requestFocus();
    }

    private boolean saveEdit() {
        if (editor == null || editingItem == null) return false;
        editor.sync();
        if (editingItem.hasInvalidExtensions()) {
            Notify.show(R.string.setting_custom_csp_extensions_invalid);
            return false;
        }
        if (editingPosition >= 0) adapter.replace(editingPosition, editingItem);
        else adapter.add(editingItem);
        showList();
        return true;
    }

    private void focusBeforeRemove(View removed) {
        if (binding == null || removed == null) return;
        View focus = binding.root.findFocus();
        if (isDescendant(focus, removed)) {
            focus.clearFocus();
            binding.add.requestFocus();
        }
    }

    private boolean isDescendant(View child, View parent) {
        if (child == null || parent == null) return false;
        if (child == parent) return true;
        ViewParent viewParent = child.getParent();
        while (viewParent instanceof View) {
            if (viewParent == parent) return true;
            viewParent = viewParent.getParent();
        }
        return false;
    }

    private boolean save(boolean validate) {
        if (saved) return true;
        if (textMode && !syncFormFromJson(validate)) {
            if (validate) return false;
            saved = true;
            return true;
        }
        syncAllVisibleRows();
        if (validate && adapter.hasInvalidExtensions()) {
            Notify.show(R.string.setting_custom_csp_extensions_invalid);
            return false;
        }
        registry.setEnabled(enabled);
        registry.setInsertIndex(getInsertIndex());
        registry.setItems(new ArrayList<>(adapter.getItems()));
        try {
            CustomCspSetting.save(registry);
        } catch (Exception e) {
            Notify.show(e.getMessage());
            return false;
        }
        reloadConfigs();
        if (callback != null) callback.run();
        saved = true;
        return true;
    }

    private void reloadConfigs() {
        VodConfig.get().clear().config(VodConfig.get().getConfig()).load(new Callback() {
        });
        if (LiveConfig.hasLoadedLives() || !LiveConfig.get().getConfig().isEmpty() || CustomCspSetting.hasLives()) LiveConfig.get().clear().config(LiveConfig.get().getConfig()).load(new Callback() {
        });
    }

    private boolean syncJsonFromForm(boolean validate) {
        syncAllVisibleRows();
        if (validate && adapter.hasInvalidExtensions()) {
            Notify.show(R.string.setting_custom_csp_extensions_invalid);
            return false;
        }
        registry.setEnabled(enabled);
        registry.setInsertIndex(getInsertIndex());
        registry.setItems(new ArrayList<>(adapter.getItems()));
        binding.jsonText.setText(new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(registry.normalize()));
        return true;
    }

    private boolean syncFormFromJson(boolean validate) {
        String text = binding.jsonText.getText() == null ? "" : binding.jsonText.getText().toString().trim();
        try {
            registry = TextUtils.isEmpty(text) ? new CustomCspSetting.Registry() : CustomCspSetting.parse(text);
        } catch (Exception e) {
            if (validate) Notify.show(R.string.setting_custom_csp_json_invalid);
            return false;
        }
        adapter.setItems(new ArrayList<>(registry.getItems()));
        enabled = registry.isEnabled();
        updateEnabledText();
        setInsertIndex(registry.getInsertIndex());
        return true;
    }

    private void showRecognizePanel() {
        syncAllVisibleRows();
        editMode = true;
        recognizeMode = true;
        editor = null;
        editingItem = null;
        editingPosition = -1;
        binding.editPanel.removeAllViews();
        recognizeInput = createInput(true);
        recognizeInput.setMinLines(10);
        recognizeInput.setMaxLines(16);
        setupScrollableText(recognizeInput);
        binding.editPanel.addView(createRecognizePanel(recognizeInput));
        binding.negative.setText(R.string.playback_webhook_back);
        binding.positive.setText(R.string.dialog_positive);
        updateModeVisibility();
        binding.contentScroll.scrollTo(0, 0);
        recognizeInput.requestFocus();
    }

    private View createRecognizePanel(TextInputEditText input) {
        LinearLayoutCompat container = new LinearLayoutCompat(requireContext());
        container.setOrientation(LinearLayoutCompat.VERTICAL);
        container.setPadding(0, dp(4), 0, 0);
        MaterialTextView title = text(getString(R.string.setting_custom_csp_recognize_title), 18, Color.BLACK, true);
        container.addView(title, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        container.addView(createInputPanel(R.string.setting_custom_csp_recognize_hint, input), params);
        return container;
    }

    private boolean saveRecognize() {
        if (recognizeInput == null) return false;
        if (!importRecognizedText(recognizeInput.getText() == null ? "" : recognizeInput.getText().toString())) return false;
        showList();
        return true;
    }

    private boolean importRecognizedText(String text) {
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(text.trim())) {
            Notify.show(R.string.setting_custom_csp_recognize_empty);
            return false;
        }
        List<CustomCspSetting.Item> items;
        try {
            items = recognizedItems(text);
        } catch (Exception e) {
            Notify.show(R.string.setting_custom_csp_recognize_invalid);
            return false;
        }
        if (items.isEmpty()) {
            Notify.show(R.string.setting_custom_csp_recognize_invalid);
            return false;
        }
        if (textMode && !syncFormFromJson(true)) return false;
        else if (!textMode) syncAllVisibleRows();
        List<CustomCspSetting.Item> next = new ArrayList<>(adapter.getItems());
        next.addAll(items);
        adapter.setItems(next);
        if (textMode) syncJsonFromForm(false);
        Notify.show(getString(R.string.setting_custom_csp_recognize_done, items.size()));
        return true;
    }

    private List<CustomCspSetting.Item> recognizedItems(String text) throws Exception {
        String value = stripRecognizeText(text);
        List<String> candidates = new ArrayList<>();
        candidates.add(value);
        String stripped = stripTrailingSeparators(value);
        if (!TextUtils.equals(value, stripped)) candidates.add(stripped);
        if (!stripped.startsWith("[")) candidates.add("[" + stripped + "]");
        Exception failure = null;
        for (String candidate : candidates) {
            try {
                CustomCspSetting.Registry parsed = CustomCspSetting.parse(candidate);
                List<CustomCspSetting.Item> items = new ArrayList<>(parsed.getItems());
                items.removeIf(item -> item == null || !item.isValid());
                if (!items.isEmpty()) return items;
            } catch (Exception e) {
                failure = e;
            }
        }
        if (failure != null) throw failure;
        return Collections.emptyList();
    }

    private String stripRecognizeText(String text) {
        String value = text == null ? "" : text.trim();
        value = value.replaceAll("(?m)^```[a-zA-Z0-9_-]*\\s*$", "");
        value = value.replaceAll("(?m)^```\\s*$", "");
        return value.trim();
    }

    private String stripTrailingSeparators(String text) {
        String value = text == null ? "" : text.trim();
        while (value.endsWith(",")) value = value.substring(0, value.length() - 1).trim();
        return value;
    }

    private int getInsertIndex() {
        try {
            return clampInsertIndex(Integer.parseInt(binding.insertIndex.getText().toString().trim()) - 1);
        } catch (Exception e) {
            return MIN_INSERT_INDEX;
        }
    }

    private int clampInsertIndex(int index) {
        return Math.max(MIN_INSERT_INDEX, Math.min(MAX_INSERT_INDEX, index));
    }

    private void syncAllVisibleRows() {
        if (editMode && editor != null) {
            editor.sync();
            return;
        }
        for (int i = 0; i < binding.recycler.getChildCount(); i++) {
            RecyclerView.ViewHolder holder = binding.recycler.getChildViewHolder(binding.recycler.getChildAt(i));
            if (holder instanceof CspAdapter.ViewHolder viewHolder) viewHolder.sync();
        }
    }

    private static void setText(EditText view, String text) {
        if (!TextUtils.equals(view.getText(), text)) view.setText(text);
    }

    private void chooseFile(CustomCspSetting.Item item) {
        syncAllVisibleRows();
        pendingImport = item;
        pendingExtensionImport = false;
        FileChooser.from(launcher).show("text/html", new String[]{"text/html", "text/*", "application/octet-stream"});
    }

    private void chooseExtensionFile(CustomCspSetting.Item item) {
        syncAllVisibleRows();
        pendingImport = item;
        pendingExtensionImport = true;
        FileChooser.from(launcher).show("text/*", new String[]{"text/javascript", "application/javascript", "application/json", "text/css", "text/*", "application/octet-stream"});
    }

    private void editCode(CustomCspSetting.Item item) {
        syncAllVisibleRows();
        TextInputEditText input = createInput(true);
        input.setMinLines(8);
        input.setMaxLines(14);
        input.setText(Path.read(CustomCspSetting.file(item.getId(), "index.html")));
        setupScrollableText(input);
        showManualCloseDialog(new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle(R.string.setting_custom_csp_code)
                .setView(createInputPanel(R.string.setting_custom_csp_code, input))
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> saveCode(item, input.getText().toString()))
                .setNegativeButton(R.string.dialog_negative, null));
    }

    private void editLink(CustomCspSetting.Item item) {
        syncAllVisibleRows();
        TextInputEditText input = createInput(false);
        input.setText(item.getHomePage());
        showManualCloseDialog(new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle(R.string.setting_custom_csp_link)
                .setView(createInputPanel(R.string.setting_custom_csp_link, input))
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                    item.setHomePage(input.getText().toString().trim());
                    if (editMode && editor != null && item == editingItem) editor.updateHomePage();
                    else adapter.notifyDataSetChanged();
                })
                .setNegativeButton(R.string.dialog_negative, null));
    }

    private void showManualCloseDialog(MaterialAlertDialogBuilder builder) {
        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    private TextInputEditText createInput(boolean multiline) {
        TextInputEditText input = new TextInputEditText(requireContext());
        input.setSelectAllOnFocus(false);
        input.setSingleLine(!multiline);
        input.setTextColor(Color.BLACK);
        input.setHintTextColor(Color.parseColor("#666666"));
        input.setInputType(multiline ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setGravity(multiline ? Gravity.START | Gravity.TOP : Gravity.CENTER_VERTICAL);
        return input;
    }

    private View createInputPanel(int hint, TextInputEditText input) {
        LinearLayoutCompat container = new LinearLayoutCompat(requireContext());
        container.setOrientation(LinearLayoutCompat.VERTICAL);
        container.setPadding(ResUtil.dp2px(20), ResUtil.dp2px(8), ResUtil.dp2px(20), 0);
        TextInputLayout layout = new TextInputLayout(requireContext());
        layout.setHint(hint);
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setBoxBackgroundColor(Color.WHITE);
        layout.setBoxStrokeColor(ResUtil.getColor(R.color.dialog_outlined_button_stroke));
        layout.setHintTextColor(ColorStateList.valueOf(Color.parseColor("#5F6368")));
        layout.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        container.addView(layout, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return container;
    }

    private void saveCode(CustomCspSetting.Item item, String code) {
        try {
            CustomCspSetting.writePage(item.getId(), code);
            item.setHomePage(CustomCspSetting.localUrl(item.getId(), "index.html"));
            if (editMode && editor != null && item == editingItem) editor.updateHomePage();
            else adapter.notifyDataSetChanged();
        } catch (Exception e) {
            Notify.show(e.getMessage());
        }
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null || pendingImport == null) return;
        String path = FileChooser.getPathFromUri(result.getData().getData());
        if (TextUtils.isEmpty(path)) return;
        try {
            if (pendingExtensionImport) {
                importExtensionFile(pendingImport, path);
                pendingImport = null;
                pendingExtensionImport = false;
                return;
            }
            CustomCspSetting.copyPage(Path.local(path), pendingImport.getId());
            pendingImport.setHomePage(CustomCspSetting.localUrl(pendingImport.getId(), "index.html"));
            boolean editingImport = editMode && editor != null && pendingImport == editingItem;
            pendingImport = null;
            pendingExtensionImport = false;
            if (editingImport) editor.updateHomePage();
            else adapter.notifyDataSetChanged();
        } catch (Exception e) {
            pendingExtensionImport = false;
            Notify.show(e.getMessage());
        }
    });

    private void importExtensionFile(CustomCspSetting.Item item, String path) throws Exception {
        String content = Path.read(Path.local(path));
        if (TextUtils.isEmpty(content)) throw new IllegalArgumentException(getString(R.string.web_home_extension_source_empty));
        String text = extensionArrayText(item, path, content);
        item.setExtensionsExpanded(true);
        item.setExtensionsText(text);
        boolean editingImport = editMode && editor != null && item == editingItem;
        if (editingImport) editor.updateExtensions();
        else adapter.notifyDataSetChanged();
    }

    private String extensionArrayText(CustomCspSetting.Item item, String path, String content) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        String lower = name.toLowerCase();
        JsonArray array = new JsonArray();
        if (lower.endsWith(".json")) {
            JsonElement element = JsonParser.parseString(content.trim());
            if (element.isJsonObject() && element.getAsJsonObject().has("extensions")) element = element.getAsJsonObject().get("extensions");
            if (element.isJsonArray()) return pretty(element);
            array.add(element);
            return pretty(array);
        }
        JsonObject object = new JsonObject();
        object.addProperty("id", extensionId(item, name));
        object.addProperty("name", name);
        object.addProperty("runAt", "document-end");
        object.addProperty("sourceType", "file");
        object.addProperty("code", lower.endsWith(".css") ? "GM_addStyle(" + App.gson().toJson(content) + ");" : content);
        array.add(object);
        return pretty(array);
    }

    private String extensionId(CustomCspSetting.Item item, String name) {
        String base = (item == null ? "" : item.getKey()) + "-" + name;
        String value = base.toLowerCase().replaceAll("[^a-z0-9_-]+", "-").replaceAll("^-+|-+$", "");
        return TextUtils.isEmpty(value) ? "local-extension" : value;
    }

    private String pretty(JsonElement element) {
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(element);
    }

    private CustomCspSetting.Item copy(CustomCspSetting.Item item) {
        return App.gson().fromJson(App.gson().toJson(item), CustomCspSetting.Item.class).normalize();
    }

    private String primaryDetail(CustomCspSetting.Item item) {
        if (item.isLive()) return getString(R.string.setting_custom_csp_live_url) + ": " + empty(item.getUrl());
        if (item.isWebHome()) return getString(R.string.setting_custom_csp_home_page) + ": " + empty(item.getHomePage());
        return getString(R.string.setting_custom_csp_api) + ": " + empty(item.getApi());
    }

    private String meta(CustomCspSetting.Item item) {
        String status = item.isEnabled() ? getString(item.isValid() ? R.string.playback_webhook_active : R.string.playback_webhook_incomplete) : getString(R.string.setting_disable);
        if (item.isWebHome() && item.hasInvalidExtensions()) status += " · " + getString(R.string.setting_custom_csp_extensions_invalid);
        if (item.isLive()) return status + " · " + getString(R.string.setting_custom_csp_player_type) + " " + empty(String.valueOf(item.getPlayerType()));
        if (item.isWebHome()) return status + " · " + getString(R.string.setting_custom_csp_extensions_toggle) + " " + (TextUtils.isEmpty(item.getExtensionsText()) ? getString(R.string.none) : getString(R.string.setting_enable));
        return status + " · " + getString(R.string.setting_custom_csp_type) + " " + item.getType();
    }

    private String kindName(CustomCspSetting.Item item) {
        return getString(item.isLive() ? R.string.setting_custom_csp_live : item.isWebHome() ? R.string.setting_custom_csp_webhome : R.string.setting_custom_csp_common);
    }

    private String empty(String value) {
        return TextUtils.isEmpty(value) || "null".equals(value) ? getString(R.string.none) : value;
    }

    private int statusColor(CustomCspSetting.Item item) {
        if (!item.isEnabled()) return Color.parseColor("#6F7378");
        return item.isValid() && !item.hasInvalidExtensions() ? Color.parseColor("#137333") : Color.parseColor("#B3261E");
    }

    private GradientDrawable rowBackground(CustomCspSetting.Item item) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(item.isValid() ? "#F5F6F7" : "#FFF7F7"));
        drawable.setStroke(dp(1), Color.parseColor(item.isValid() ? "#DADCE0" : "#F1C9C6"));
        drawable.setCornerRadius(dp(6));
        return drawable;
    }

    private MaterialTextView text(String value, int sp, int color, boolean bold) {
        MaterialTextView view = new MaterialTextView(requireContext());
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setSingleLine(false);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private MaterialTextView badge(String value, int color) {
        MaterialTextView view = text(value, 12, color, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setStroke(dp(1), color);
        drawable.setCornerRadius(dp(6));
        view.setBackground(drawable);
        return view;
    }

    private void addDetail(LinearLayoutCompat root, String value) {
        if (TextUtils.isEmpty(value)) return;
        MaterialTextView view = text(value, 12, Color.parseColor("#5F6368"), false);
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(3);
        root.addView(view, params);
    }

    private MaterialButton actionButton(int text, boolean tonal, boolean danger) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setText(text);
        button.setMinWidth(0);
        button.setMinHeight(dp(36));
        button.setMinimumHeight(dp(36));
        button.setPadding(dp(6), 0, dp(6), 0);
        ColorStateList bg = ContextCompat.getColorStateList(requireContext(), tonal ? R.color.dialog_tonal_button_bg : R.color.dialog_outlined_button_bg);
        ColorStateList fg = danger ? ColorStateList.valueOf(Color.parseColor("#B3261E")) : ContextCompat.getColorStateList(requireContext(), tonal ? R.color.dialog_tonal_button_text : R.color.dialog_outlined_button_text);
        button.setBackgroundTintList(bg);
        button.setTextColor(fg);
        if (!tonal) {
            button.setStrokeColor(ContextCompat.getColorStateList(requireContext(), R.color.dialog_outlined_button_stroke));
            button.setStrokeWidth(dp(1));
        }
        return button;
    }

    private LinearLayoutCompat.LayoutParams actionLayout(int marginStart) {
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(0, dp(36), 1);
        params.leftMargin = dp(marginStart);
        return params;
    }

    private AppCompatImageButton iconButton(int icon, int contentDescription, View.OnClickListener listener) {
        AppCompatImageButton button = new AppCompatImageButton(requireContext());
        button.setBackgroundResource(R.drawable.selector_dialog_switch);
        button.setImageResource(icon);
        button.setColorFilter(Color.parseColor("#5F6368"));
        button.setContentDescription(getString(contentDescription));
        button.setFocusable(true);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayoutCompat.LayoutParams iconLayout(int marginStart) {
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(dp(40), dp(40));
        params.leftMargin = dp(marginStart);
        return params;
    }

    private int dp(int value) {
        return ResUtil.dp2px(value);
    }

    private class CspAdapter extends RecyclerView.Adapter<CspAdapter.ViewHolder> {

        private final List<CustomCspSetting.Item> items;

        CspAdapter(List<CustomCspSetting.Item> items) {
            this.items = items;
        }

        List<CustomCspSetting.Item> getItems() {
            return items;
        }

        void add(CustomCspSetting.Item item) {
            items.add(item);
            notifyItemInserted(items.size() - 1);
            binding.recycler.scrollToPosition(items.size() - 1);
        }

        void replace(int position, CustomCspSetting.Item item) {
            if (position < 0 || position >= items.size()) return;
            CustomCspSetting.Item old = items.set(position, item);
            if (!old.isLive() && old.site().getKey().equals(registry.getHomeKey())) registry.setHomeKey(item.isLive() ? "" : item.site().getKey());
            notifyItemChanged(position);
        }

        void setItems(List<CustomCspSetting.Item> items) {
            this.items.clear();
            this.items.addAll(items);
            notifyDataSetChanged();
        }

        boolean hasInvalidExtensions() {
            for (CustomCspSetting.Item item : items) if (item.hasInvalidExtensions()) return true;
            return false;
        }

        void move(int from, int to) {
            if (from < 0 || to < 0 || from >= items.size() || to >= items.size()) return;
            Collections.swap(items, from, to);
            notifyItemMoved(from, to);
        }

        void remove(int position, View removed) {
            if (position < 0 || position >= items.size()) return;
            focusBeforeRemove(removed);
            CustomCspSetting.Item item = items.remove(position);
            if (!item.isLive() && item.site().getKey().equals(registry.getHomeKey())) registry.setHomeKey("");
            notifyItemRemoved(position);
        }

        void setHome(CustomCspSetting.Item item) {
            if (item.isLive()) return;
            String key = item.site().getKey();
            registry.setHomeKey(key.equals(registry.getHomeKey()) ? "" : key);
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayoutCompat root = new LinearLayoutCompat(parent.getContext());
            root.setOrientation(LinearLayoutCompat.VERTICAL);
            root.setPadding(dp(10), dp(9), dp(10), dp(9));
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(10);
            root.setLayoutParams(params);
            return new ViewHolder(root);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(items.get(position));
        }

        private class ViewHolder extends RecyclerView.ViewHolder {

            private final LinearLayoutCompat root;
            private CustomCspSetting.Item item;

            ViewHolder(@NonNull LinearLayoutCompat root) {
                super(root);
                this.root = root;
            }

            void bind(CustomCspSetting.Item item) {
                this.item = item;
                root.removeAllViews();
                root.setBackground(rowBackground(item));
                root.setFocusable(true);
                root.setClickable(true);
                root.setOnClickListener(view -> editCurrent());

                LinearLayoutCompat header = new LinearLayoutCompat(requireContext());
                header.setGravity(Gravity.CENTER_VERTICAL);
                header.setOrientation(LinearLayoutCompat.HORIZONTAL);
                root.addView(header, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                MaterialTextView title = text(item.getName(), 15, Color.BLACK, true);
                header.addView(title, new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                header.addView(badge(kindName(item), statusColor(item)));
                header.addView(iconButton(R.drawable.ic_subtitle_up, R.string.setting_custom_csp_up, view -> move(getBindingAdapterPosition(), getBindingAdapterPosition() - 1)), iconLayout(8));
                header.addView(iconButton(R.drawable.ic_subtitle_down, R.string.setting_custom_csp_down, view -> move(getBindingAdapterPosition(), getBindingAdapterPosition() + 1)), iconLayout(4));

                addDetail(root, primaryDetail(item));
                if (!item.isLive()) addDetail(root, getString(R.string.setting_custom_csp_key) + ": " + item.getKey());
                addDetail(root, meta(item));

                LinearLayoutCompat actions = new LinearLayoutCompat(requireContext());
                actions.setGravity(Gravity.CENTER_VERTICAL);
                actions.setOrientation(LinearLayoutCompat.HORIZONTAL);
                LinearLayoutCompat.LayoutParams actionParams = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                actionParams.topMargin = dp(7);
                root.addView(actions, actionParams);

                MaterialButton toggle = actionButton(item.isEnabled() ? R.string.setting_disable : R.string.setting_enable, !item.isEnabled(), false);
                toggle.setOnClickListener(view -> {
                    int position = getBindingAdapterPosition();
                    if (position == RecyclerView.NO_POSITION) return;
                    item.setEnabled(!item.isEnabled());
                    notifyItemChanged(position);
                });
                actions.addView(toggle, actionLayout(0));

                MaterialButton edit = actionButton(R.string.dialog_edit, false, false);
                edit.setOnClickListener(view -> editCurrent());
                actions.addView(edit, actionLayout(8));

                if (!item.isLive()) {
                    boolean home = item.site().getKey().equals(registry.getHomeKey());
                    MaterialButton homeButton = actionButton(R.string.setting_custom_csp_home, home, false);
                    homeButton.setOnClickListener(view -> setHome(item));
                    actions.addView(homeButton, actionLayout(8));
                }

                MaterialButton delete = actionButton(R.string.setting_delete, false, true);
                delete.setOnClickListener(view -> remove(getBindingAdapterPosition(), itemView));
                actions.addView(delete, actionLayout(8));
            }

            private void editCurrent() {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;
                showEdit(items.get(position), position);
            }

            void sync() {
            }
        }
    }

    private class CspEditor {

        private final AdapterCustomCspBinding binding;
        private CustomCspSetting.Item item;
        private boolean bindingItem;
        private boolean autoName;
        private boolean autoKey;

        CspEditor(@NonNull AdapterCustomCspBinding binding) {
            this.binding = binding;
            binding.name.addTextChangedListener(new TextSync(this));
            binding.key.addTextChangedListener(new TextSync(this));
            binding.type.addTextChangedListener(new TextSync(this));
            binding.api.addTextChangedListener(new TextSync(this));
            binding.homePage.addTextChangedListener(new TextSync(this));
            binding.extensions.addTextChangedListener(new TextSync(this));
            binding.ext.addTextChangedListener(new TextSync(this));
            binding.jar.addTextChangedListener(new TextSync(this));
            binding.click.addTextChangedListener(new TextSync(this));
            binding.playUrl.addTextChangedListener(new TextSync(this));
            binding.liveUrl.addTextChangedListener(new TextSync(this));
            binding.logo.addTextChangedListener(new TextSync(this));
            binding.epg.addTextChangedListener(new TextSync(this));
            binding.ua.addTextChangedListener(new TextSync(this));
            binding.referer.addTextChangedListener(new TextSync(this));
            binding.origin.addTextChangedListener(new TextSync(this));
            binding.timeZone.addTextChangedListener(new TextSync(this));
            binding.timeout.addTextChangedListener(new TextSync(this));
            binding.enabled.setOnClickListener(view -> toggleEnabled());
            binding.typeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> onTypeChecked(checkedId, isChecked));
            binding.liveTypeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> onLiveTypeChecked(checkedId, isChecked));
            binding.playerTypeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> onPlayerTypeChecked(checkedId, isChecked));
            binding.hide.setOnCheckedChangeListener((button, checked) -> sync());
            binding.searchable.setOnCheckedChangeListener((button, checked) -> sync());
            binding.changeable.setOnCheckedChangeListener((button, checked) -> sync());
            binding.quickSearch.setOnCheckedChangeListener((button, checked) -> sync());
            binding.importFile.setOnClickListener(view -> chooseFile(item));
            binding.code.setOnClickListener(view -> editCode(item));
            binding.link.setOnClickListener(view -> editLink(item));
            binding.extensionsToggle.setOnClickListener(view -> toggleExtensions());
            binding.extensionsFile.setOnClickListener(view -> chooseExtensionFile(item));
            binding.home.setVisibility(View.GONE);
            binding.up.setVisibility(View.GONE);
            binding.down.setVisibility(View.GONE);
            binding.delete.setVisibility(View.GONE);
            setupScrollableText(binding.extensions, false);
        }

        void bind(CustomCspSetting.Item item) {
            this.item = item;
            bindingItem = true;
            autoName = isAutoName(item.getName(), item.getKind());
            autoKey = isAutoKey(item.getKey());
            binding.enabled.setAlpha(item.isEnabled() ? 1.0f : 0.65f);
            binding.enabled.setText(item.isEnabled() ? R.string.setting_enable : R.string.setting_disable);
            binding.typeGroup.check(item.isLive() ? R.id.liveMode : item.isWebHome() ? R.id.webHomeMode : R.id.cspMode);
            setText(binding.name, item.getName());
            setText(binding.key, item.getKey());
            setText(binding.type, String.valueOf(item.getType()));
            setText(binding.api, item.getApi());
            setText(binding.homePage, item.getHomePage());
            setText(binding.extensions, item.getExtensionsText());
            setText(binding.ext, item.getExt());
            setText(binding.jar, item.getJar());
            setText(binding.click, item.getClick());
            setText(binding.playUrl, item.getPlayUrl());
            setText(binding.liveUrl, item.getUrl());
            setText(binding.logo, item.getLogo());
            setText(binding.epg, item.getEpg());
            setText(binding.ua, item.getUa());
            setText(binding.referer, item.getReferer());
            setText(binding.origin, item.getOrigin());
            setText(binding.timeZone, item.getTimeZone());
            setText(binding.timeout, item.getTimeout() == null ? "" : String.valueOf(item.getTimeout()));
            binding.liveTypeGroup.check(liveTypeId(item.getType()));
            binding.playerTypeGroup.check(playerTypeId(item.getPlayerType()));
            binding.hide.setChecked(item.getHide() == 1);
            binding.searchable.setChecked(item.getSearchable() == 1);
            binding.changeable.setChecked(item.getChangeable() == 1);
            binding.quickSearch.setChecked(item.getQuickSearch() == 1);
            updateTypePanels();
            updateExtensionsToggle();
            updateExtensionsError();
            updateValidity();
            bindingItem = false;
        }

        void updateHomePage() {
            if (item != null) setText(binding.homePage, item.getHomePage());
        }

        void updateExtensions() {
            if (item == null) return;
            setText(binding.extensions, item.getExtensionsText());
            updateTypePanels();
            updateExtensionsToggle();
            updateExtensionsError();
            updateValidity();
        }

        private void toggleEnabled() {
            if (item == null) return;
            boolean checked = !item.isEnabled();
            item.setEnabled(checked);
            binding.enabled.setAlpha(checked ? 1.0f : 0.65f);
            binding.enabled.setText(checked ? R.string.setting_enable : R.string.setting_disable);
        }

        private void toggleExtensions() {
            if (bindingItem || item == null) return;
            item.setExtensionsExpanded(!item.isExtensionsExpanded());
            if (!item.isExtensionsExpanded()) setText(binding.extensions, "");
            updateTypePanels();
            updateExtensionsToggle();
            updateExtensionsError();
            sync();
        }

        private void onTypeChecked(int checkedId, boolean isChecked) {
            if (bindingItem || item == null || !isChecked) return;
            String oldKind = item.getKind();
            String newKind = checkedId == R.id.liveMode ? KIND_LIVE : checkedId == R.id.webHomeMode ? KIND_WEB_HOME : KIND_CSP;
            if (oldKind.equals(newKind)) return;
            item.setKind(newKind);
            if (KIND_LIVE.equals(newKind) && !KIND_LIVE.equals(oldKind)) {
                item.setApi("");
                item.setExt("");
                item.setJar("");
                item.setClick("");
                setText(binding.api, "");
                setText(binding.ext, "");
                setText(binding.jar, "");
                setText(binding.click, "");
            }
            if (autoName) {
                String name = nextName(newKind);
                item.setName(name);
                setText(binding.name, name);
            }
            updateTypePanels();
            updateValidity();
        }

        private void onLiveTypeChecked(int checkedId, boolean isChecked) {
            if (bindingItem || item == null || !item.isLive() || !isChecked) return;
            item.setType(liveTypeFromId(checkedId));
            updateValidity();
        }

        private void onPlayerTypeChecked(int checkedId, boolean isChecked) {
            if (bindingItem || item == null || !item.isLive() || !isChecked) return;
            item.setPlayerType(playerTypeFromId(checkedId));
            updateValidity();
        }

        private void updateTypePanels() {
            boolean webHome = item != null && item.isWebHome();
            boolean live = item != null && item.isLive();
            binding.webHomePanel.setVisibility(webHome ? View.VISIBLE : View.GONE);
            binding.home.setVisibility(View.GONE);
            binding.apiLayout.setVisibility(webHome || live ? View.GONE : View.VISIBLE);
            binding.homePageLayout.setVisibility(webHome ? View.VISIBLE : View.GONE);
            binding.extensionsPanel.setVisibility(webHome ? View.VISIBLE : View.GONE);
            binding.extensionsFile.setVisibility(webHome && item.isExtensionsExpanded() ? View.VISIBLE : View.GONE);
            binding.extensionsLayout.setVisibility(webHome && item.isExtensionsExpanded() ? View.VISIBLE : View.GONE);
            binding.liveUrlLayout.setVisibility(live ? View.VISIBLE : View.GONE);
            binding.liveTypePanel.setVisibility(View.GONE);
            binding.cspOptionsPanel.setVisibility(!live ? View.VISIBLE : View.GONE);
            binding.keyLayout.setVisibility(!live ? View.VISIBLE : View.GONE);
            binding.typeLayout.setVisibility(!webHome && !live ? View.VISIBLE : View.GONE);
            binding.liveMetaPanel.setVisibility(live ? View.VISIBLE : View.GONE);
            binding.liveHeaderPanel.setVisibility(live ? View.VISIBLE : View.GONE);
            binding.liveTunePanel.setVisibility(live ? View.VISIBLE : View.GONE);
            binding.flagsPanel.setVisibility(!webHome && !live ? View.VISIBLE : View.GONE);
            binding.advancedPanel.setVisibility(!webHome && !live ? View.VISIBLE : View.GONE);
            binding.playPanel.setVisibility(!webHome && !live ? View.VISIBLE : View.GONE);
            binding.playUrlLayout.setVisibility(live ? View.GONE : View.VISIBLE);
        }

        void sync() {
            if (item == null || bindingItem) return;
            String name = binding.name.getText().toString().trim();
            String key = binding.key.getText().toString().trim();
            if (!key.equals(item.getKey())) autoKey = false;
            autoName = autoName || isAutoName(item.getName(), item.getKind());
            if (!name.equals(item.getName())) autoName = false;
            item.setName(name);
            if (autoKey && !item.isLive() && !binding.key.getText().toString().trim().equals(item.getKey())) {
                bindingItem = true;
                setText(binding.key, item.getKey());
                bindingItem = false;
            }
            if (item.isLive()) {
                item.setUrl(binding.liveUrl.getText().toString().trim());
                item.setExtensionsExpanded(false);
                item.setApi(binding.api.getText().toString().trim());
                item.setExt(binding.ext.getText().toString().trim());
                item.setJar(binding.jar.getText().toString().trim());
                item.setClick(binding.click.getText().toString().trim());
                item.setLogo(binding.logo.getText().toString().trim());
                item.setEpg(binding.epg.getText().toString().trim());
                item.setUa(binding.ua.getText().toString().trim());
                item.setReferer(binding.referer.getText().toString().trim());
                item.setOrigin(binding.origin.getText().toString().trim());
                item.setTimeZone(binding.timeZone.getText().toString().trim());
                item.setTimeout(parseOptionalInt(binding.timeout.getText().toString()));
                item.setHomePage("");
                item.setPlayUrl("");
            } else if (!item.isWebHome()) {
                item.setKey(binding.key.getText().toString().trim());
                item.setExtensionsExpanded(false);
                item.setType(parseInt(binding.type.getText().toString(), 3));
                item.setApi(binding.api.getText().toString().trim());
                item.setHide(binding.hide.isChecked() ? 1 : 0);
                item.setSearchable(binding.searchable.isChecked() ? 1 : 0);
                item.setChangeable(binding.changeable.isChecked() ? 1 : 0);
                item.setQuickSearch(binding.quickSearch.isChecked() ? 1 : 0);
            }
            if (item.isLive()) {
                item.setHomePage("");
                item.setPlayUrl("");
            } else if (!item.isWebHome()) {
                item.setHomePage("");
                item.setExt(binding.ext.getText().toString().trim());
                item.setJar(binding.jar.getText().toString().trim());
                item.setClick(binding.click.getText().toString().trim());
                item.setPlayUrl(binding.playUrl.getText().toString().trim());
            } else {
                item.setKey(binding.key.getText().toString().trim());
                item.setHomePage(binding.homePage.getText().toString().trim());
                item.setExtensionsText(item.isExtensionsExpanded() ? binding.extensions.getText().toString() : "");
                item.setClick("");
                item.setPlayUrl("");
            }
            updateExtensionsToggle();
            updateExtensionsError();
            updateValidity();
        }

        private int liveTypeId(int value) {
            if (value == 1) return R.id.liveType1;
            if (value == 2) return R.id.liveType2;
            return R.id.liveType0;
        }

        private int playerTypeId(Integer value) {
            if (value == null) return R.id.playerTypeUnset;
            if (value == 0) return R.id.playerType0;
            if (value == 1) return R.id.playerType1;
            return R.id.playerType2;
        }

        private int liveTypeFromId(int id) {
            if (id == R.id.liveType1) return 1;
            if (id == R.id.liveType2) return 2;
            return 0;
        }

        private Integer playerTypeFromId(int id) {
            if (id == R.id.playerTypeUnset) return null;
            if (id == R.id.playerType0) return 0;
            if (id == R.id.playerType1) return 1;
            return 2;
        }

        private boolean isAutoName(String name, String kind) {
            String prefix = getKindPrefix(kind);
            if (TextUtils.isEmpty(name)) return true;
            if (name.equals(prefix)) return true;
            return name.matches(java.util.regex.Pattern.quote(prefix) + " \\d+");
        }

        private boolean isAutoKey(String key) {
            return TextUtils.isEmpty(key) || key.startsWith("__custom_csp_");
        }

        private void updateValidity() {
            if (item == null) return;
            boolean invalid = item.isEnabled() && !item.isValid();
            binding.getRoot().setActivated(invalid);
        }

        private void updateExtensionsError() {
            binding.extensionsLayout.setError(item != null && item.hasInvalidExtensions() ? getString(R.string.setting_custom_csp_extensions_invalid) : null);
        }

        private void updateExtensionsToggle() {
            boolean expanded = item != null && item.isWebHome() && item.isExtensionsExpanded();
            binding.extensionsToggle.setSelected(expanded);
            binding.extensionsToggle.setAlpha(expanded ? 1.0f : 0.65f);
        }
    }

    private int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private Integer parseOptionalInt(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return null;
        return parseInt(value, 0);
    }

    private static class TextSync extends CustomTextListener {

        private final CspEditor editor;

        TextSync(CspEditor editor) {
            this.editor = editor;
        }

        @Override
        public void afterTextChanged(Editable editable) {
            editor.sync();
        }
    }
}
