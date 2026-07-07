package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.lut.LutPreset;
import com.fongmi.android.tv.player.lut.LutSetting;
import com.fongmi.android.tv.player.lut.LutStore;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.crawler.SpiderDebug;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class LutPanelDialog extends BaseBottomSheetDialog {

    private static final long FAVORITE_DOUBLE_CLICK_MS = 450;
    private static final int[] PANEL_COLORS = new int[]{0xE62F315E, 0xD6282955, 0xCC303463};
    private static final int BUTTON_COLOR = 0x1FFFFFFF;
    private static final int BUTTON_FOCUS_COLOR = 0x2EFFFFFF;
    private static final int BUTTON_SELECTED_COLOR = 0x3DFFFFFF;
    private static final int BUTTON_STROKE_COLOR = 0x24FFFFFF;
    private static final int BUTTON_ACTIVE_STROKE_COLOR = 0x4DFFFFFF;

    private MaterialTextView title;
    private MaterialTextView all;
    private MaterialTextView delay;
    private MaterialTextView favorite;
    private MaterialTextView empty;
    private RecyclerView recycler;
    private PanelAdapter adapter;
    private PlayerManager player;
    private boolean favoriteOnly;
    private String lastClickId;
    private long lastClickTime;

    public static LutPanelDialog create() {
        return new LutPanelDialog();
    }

    public LutPanelDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof LutPanelDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        configureWindow(dialog);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        configureWindow(getDialog());
    }

    private void configureWindow(Dialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;
        Window window = dialog.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.setDimAmount(0f);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        WindowCompat.setDecorFitsSystemWindows(window, true);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return new SimpleBinding(createContent());
    }

    @Override
    protected void initView() {
        adapter = new PanelAdapter();
        recycler.setHasFixedSize(true);
        recycler.setItemAnimator(null);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        refreshList(true);
    }

    private View createContent() {
        LinearLayoutCompat root = new LinearLayoutCompat(requireContext());
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setOrientation(LinearLayoutCompat.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(9), dp(20));
        root.setBackground(panelBackground());

        LinearLayoutCompat header = new LinearLayoutCompat(requireContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayoutCompat.HORIZONTAL);
        root.addView(header, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        title = text(R.string.player_lut, 17, true);
        header.addView(title, new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        MaterialTextView reset = chip(R.string.lut_reset);
        reset.setOnClickListener(view -> select(null));
        header.addView(reset);

        MaterialTextView close = chip(R.string.lut_close);
        close.setOnClickListener(view -> dismiss());
        header.addView(close);

        LinearLayoutCompat tools = new LinearLayoutCompat(requireContext());
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setOrientation(LinearLayoutCompat.HORIZONTAL);
        LinearLayoutCompat.LayoutParams toolParams = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        toolParams.setMargins(0, dp(10), 0, dp(4));
        root.addView(tools, toolParams);

        delay = chip(0);
        delay.setOnClickListener(view -> cycleDelay());
        tools.addView(delay);

        all = chip(R.string.lut_all);
        all.setOnClickListener(view -> showAll());
        all.setOnFocusChangeListener((v, focused) -> setBackground((MaterialTextView) v, !favoriteOnly, focused));
        tools.addView(all);

        favorite = chip(R.string.lut_favorite);
        favorite.setOnClickListener(view -> toggleFavoriteOnly());
        favorite.setOnFocusChangeListener((v, focused) -> setBackground((MaterialTextView) v, favoriteOnly, focused));
        tools.addView(favorite);

        MaterialTextView importView = chip(R.string.lut_local);
        importView.setOnClickListener(view -> ((ControlDialog.Listener) requireActivity()).onLutImport());
        tools.addView(importView);

        MaterialTextView dirView = chip(R.string.lut_directory);
        dirView.setOnClickListener(view -> ((ControlDialog.Listener) requireActivity()).onLutDir());
        tools.addView(dirView);

        empty = text(R.string.lut_empty_presets, 14, false);
        empty.setGravity(Gravity.CENTER);
        root.addView(empty, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        recycler = new RecyclerView(requireContext());
        recycler.setClipToPadding(false);
        recycler.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(recycler, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    @Override
    protected void setBehavior(BottomSheetDialog dialog) {
        FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        sheet.setBackgroundColor(ResUtil.getColor(R.color.transparent));
        int height = getPanelHeight();
        ViewGroup.LayoutParams params = sheet.getLayoutParams();
        params.height = height;
        sheet.setLayoutParams(params);
        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
        behavior.setPeekHeight(height);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        behavior.setSkipCollapsed(true);
        behavior.setDraggable(false);
    }

    private int getPanelHeight() {
        int screen = ResUtil.getScreenHeight(requireContext());
        return Math.max(dp(300), Math.min(dp(460), Math.round(screen * 0.46f)));
    }

    private void refreshList() {
        refreshList(false);
    }

    private void refreshList(boolean rescan) {
        renderList(LutStore.getCachedPresets());
        if (rescan || !LutStore.hasCache()) LutStore.refreshPresetsAsync(this::renderList);
    }

    private void renderList(List<LutPreset> presets) {
        if (!isAdded() || adapter == null) return;
        List<Entry> items = new ArrayList<>();
        Set<String> favorites = LutSetting.favoriteIds();
        if (!favoriteOnly) items.add(Entry.original());
        for (LutPreset preset : presets) {
            if (favoriteOnly && !favorites.contains(preset.getId())) continue;
            items.add(Entry.preset(preset));
        }
        adapter.setItems(items);
        empty.setText(favoriteOnly ? R.string.lut_empty_favorites : R.string.lut_empty_presets);
        empty.setVisibility(items.isEmpty() || (!favoriteOnly && items.size() <= 1) ? View.VISIBLE : View.GONE);
        title.setText(ResUtil.getString(R.string.lut_title_value, ResUtil.getString(R.string.player_lut), LutSetting.getSummary()));
        delay.setText(ResUtil.getString(R.string.lut_preview_delay_value, LutSetting.getPreviewSeconds()));
        updateAllButton();
        updateFavoriteButton();
        scrollToSelected(items);
    }

    private void scrollToSelected(List<Entry> items) {
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isSelected()) continue;
            recycler.post(() -> recycler.scrollToPosition(Math.max(0, adapter.getSelectedPosition())));
            return;
        }
    }

    private void select(LutPreset preset) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-ui", "panel select preset=%s enabledBefore=%s current=%s", preset == null ? "original" : preset.getId(), LutSetting.isEnabled(), LutSetting.getPresetId());
        ((ControlDialog.Listener) requireActivity()).onLutSelected(preset);
        refreshControlDialog();
        refreshList();
        recycler.requestFocus();
    }

    private void toggleFavoriteOnly() {
        favoriteOnly = !favoriteOnly;
        resetClickTracking();
        refreshList();
        recycler.requestFocus();
    }

    private void showAll() {
        favoriteOnly = false;
        resetClickTracking();
        refreshList();
        recycler.requestFocus();
    }

    private void onEntryClick(Entry entry) {
        if (entry.preset != null && isFavoriteDoubleClick(entry.preset)) {
            toggleFavorite(entry.preset);
            return;
        }
        select(entry.preset);
    }

    private boolean isFavoriteDoubleClick(LutPreset preset) {
        long now = SystemClock.uptimeMillis();
        String id = preset.getId();
        boolean doubleClick = id.equals(lastClickId) && now - lastClickTime <= FAVORITE_DOUBLE_CLICK_MS;
        lastClickId = id;
        lastClickTime = now;
        return doubleClick;
    }

    private void toggleFavorite(LutPreset preset) {
        boolean enabled = LutSetting.toggleFavorite(preset);
        LutStore.resortCache();
        Notify.show(enabled ? R.string.lut_favorited : R.string.lut_unfavorited);
        resetClickTracking();
        refreshControlDialog();
        refreshList();
    }

    private void resetClickTracking() {
        lastClickId = null;
        lastClickTime = 0;
    }

    private void updateAllButton() {
        if (all == null) return;
        setBackground(all, !favoriteOnly, all.hasFocus());
    }

    private void updateFavoriteButton() {
        if (favorite == null) return;
        setBackground(favorite, favoriteOnly, favorite.hasFocus());
    }

    private void refreshControlDialog() {
        for (Fragment fragment : requireActivity().getSupportFragmentManager().getFragments()) {
            if (fragment instanceof ControlDialog dialog) dialog.setLut();
        }
    }

    private void cycleDelay() {
        int current = LutSetting.getPreviewSeconds();
        int next = current < 2 ? 2 : current < 3 ? 3 : current < 5 ? 5 : current < 8 ? 8 : 1;
        LutSetting.putPreviewSeconds(next);
        delay.setText(ResUtil.getString(R.string.lut_preview_delay_value, next));
        if (LutSetting.isEnabled() && player != null) player.applyLutPreview(false);
    }

    private MaterialTextView chip(int resId) {
        MaterialTextView view = text(resId, 13, false);
        view.setFocusable(true);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(34));
        view.setPadding(dp(12), 0, dp(12), 0);
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        params.setMarginStart(dp(7));
        view.setLayoutParams(params);
        setBackground(view, false, false);
        view.setOnFocusChangeListener((v, focused) -> setBackground((MaterialTextView) v, false, focused));
        return view;
    }

    private MaterialTextView text(int resId, int sp, boolean bold) {
        MaterialTextView view = new MaterialTextView(requireContext());
        if (resId != 0) view.setText(resId);
        view.setTextColor(0xE6FFFFFF);
        view.setTextSize(sp);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void setBackground(MaterialTextView view, boolean selected, boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(selected ? BUTTON_SELECTED_COLOR : focused ? BUTTON_FOCUS_COLOR : BUTTON_COLOR);
        drawable.setStroke(dp(1), selected || focused ? BUTTON_ACTIVE_STROKE_COLOR : BUTTON_STROKE_COLOR);
        drawable.setCornerRadius(dp(6));
        view.setBackground(drawable);
        view.setTextColor(selected ? Color.WHITE : 0xE6FFFFFF);
    }

    private GradientDrawable panelBackground() {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, PANEL_COLORS);
        drawable.setCornerRadii(new float[]{dp(22), dp(22), dp(22), dp(22), 0, 0, 0, 0});
        drawable.setStroke(dp(1), 0x66FFFFFF);
        return drawable;
    }

    private int dp(int value) {
        return ResUtil.dp2px(value);
    }

    private static class SimpleBinding implements ViewBinding {
        private final View root;

        private SimpleBinding(View root) {
            this.root = root;
        }

        @NonNull
        @Override
        public View getRoot() {
            return root;
        }
    }

    private static class Entry {
        private final LutPreset preset;

        private Entry(LutPreset preset) {
            this.preset = preset;
        }

        static Entry original() {
            return new Entry(null);
        }

        static Entry preset(LutPreset preset) {
            return new Entry(preset);
        }

        String getText() {
            return preset == null ? ResUtil.getString(R.string.lut_original) : preset.getName();
        }

        boolean isSelected() {
            return preset == null ? !LutSetting.isEnabled() : LutSetting.isEnabled() && preset.getId().equals(LutSetting.getPresetId());
        }
    }

    private class PanelAdapter extends RecyclerView.Adapter<PanelAdapter.ViewHolder> {
        private final List<Entry> items = new ArrayList<>();

        void setItems(List<Entry> next) {
            items.clear();
            items.addAll(next);
            notifyDataSetChanged();
        }

        int getSelectedPosition() {
            for (int i = 0; i < items.size(); i++) if (items.get(i).isSelected()) return i;
            return 0;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialTextView view = text(0, 15, false);
            view.setFocusable(true);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setSingleLine(true);
            view.setPadding(dp(14), 0, dp(14), 0);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
            params.setMargins(0, dp(7), dp(7), 0);
            view.setLayoutParams(params);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Entry entry = items.get(position);
            holder.text.setText(entry.getText());
            setBackground(holder.text, entry.isSelected(), holder.text.hasFocus());
            holder.text.setOnFocusChangeListener((view, focused) -> setBackground((MaterialTextView) view, entry.isSelected(), focused));
            holder.text.setOnClickListener(view -> onEntryClick(entry));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private class ViewHolder extends RecyclerView.ViewHolder {
            private final MaterialTextView text;

            private ViewHolder(@NonNull MaterialTextView itemView) {
                super(itemView);
                text = itemView;
            }
        }
    }
}
