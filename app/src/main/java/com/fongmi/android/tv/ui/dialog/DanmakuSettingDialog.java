package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogDanmakuSettingBinding;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public final class DanmakuSettingDialog {

    private PlayerManager player;
    private boolean restoreParent;

    public static DanmakuSettingDialog create() {
        return new DanmakuSettingDialog();
    }

    public DanmakuSettingDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public DanmakuSettingDialog restoreParent(boolean restoreParent) {
        this.restoreParent = restoreParent;
        return this;
    }

    public void show(FragmentActivity activity) {
        FragmentManager manager = activity.getSupportFragmentManager();
        for (Fragment fragment : manager.getFragments()) if (fragment instanceof BottomSheet || fragment instanceof SideSheet) return;
        if (Util.isFullscreenLand(activity) || Util.isLeanback()) new SideSheet(player, restoreParent).show(manager, null);
        else new BottomSheet(player, restoreParent).show(manager, null);
    }

    private static DialogDanmakuSettingBinding inflate(LayoutInflater inflater, ViewGroup container) {
        return DialogDanmakuSettingBinding.inflate(inflater, container, false);
    }

    public static final class BottomSheet extends BaseBottomSheetDialog {

        private DialogDanmakuSettingBinding binding;
        private final PlayerManager player;
        private final boolean restoreParent;

        BottomSheet(PlayerManager player, boolean restoreParent) {
            this.player = player;
            this.restoreParent = restoreParent;
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

        @Override
        protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
            return binding = DanmakuSettingDialog.inflate(inflater, container);
        }

        @Override
        protected void initView() {
            new DanmakuSettingPanel(binding, player).bind();
        }

        @Override
        public void onDismiss(@NonNull DialogInterface dialog) {
            super.onDismiss(dialog);
            DanmakuSettingDialog.restoreParent(this, player, restoreParent);
        }

        @Override
        protected boolean transparent() {
            return true;
        }

        @Override
        protected boolean stableOverlay() {
            return true;
        }

        @Override
        protected void setBehavior(BottomSheetDialog dialog) {
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet == null) return;
            clearBackground(sheet);
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

        private void configureWindow(Dialog dialog) {
            if (dialog == null || dialog.getWindow() == null) return;
            Window window = dialog.getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.setDimAmount(0f);
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
            WindowCompat.setDecorFitsSystemWindows(window, true);
        }

        private void clearBackground(FrameLayout sheet) {
            int color = ResUtil.getColor(R.color.transparent);
            sheet.setBackgroundColor(color);
            if (sheet.getParent() instanceof View) ((View) sheet.getParent()).setBackgroundColor(color);
        }

        private int getPanelHeight() {
            int screen = ResUtil.getScreenHeight(requireContext());
            if (ResUtil.isLand(requireContext())) return Math.max(ResUtil.dp2px(280), Math.min(ResUtil.dp2px(440), Math.round(screen * 0.80f)));
            return Math.max(ResUtil.dp2px(380), Math.min(ResUtil.dp2px(560), Math.round(screen * 0.60f)));
        }
    }

    public static final class SideSheet extends AppCompatDialogFragment {

        private DialogDanmakuSettingBinding binding;
        private final PlayerManager player;
        private final boolean restoreParent;

        SideSheet(PlayerManager player, boolean restoreParent) {
            this.player = player;
            this.restoreParent = restoreParent;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Dialog dialog = super.onCreateDialog(savedInstanceState);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            configureWindow(dialog);
            return dialog;
        }

        @Override
        public void onStart() {
            super.onStart();
            configureWindow(getDialog());
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            binding = DanmakuSettingDialog.inflate(inflater, container);
            binding.getRoot().setBackgroundResource(R.drawable.shape_dialog_glass_panel);
            FrameLayout overlay = new FrameLayout(requireContext());
            overlay.setBackgroundColor(Color.TRANSPARENT);
            overlay.setOnClickListener(view -> dismiss());
            binding.getRoot().setClickable(true);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(getWidth(), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
            overlay.addView(binding.getRoot(), params);
            return overlay;
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            new DanmakuSettingPanel(binding, player).bind();
        }

        @Override
        public void onDismiss(@NonNull DialogInterface dialog) {
            super.onDismiss(dialog);
            DanmakuSettingDialog.restoreParent(this, player, restoreParent);
        }

        private int getWidth() {
            return Math.min(ResUtil.dp2px(420), ResUtil.getScreenWidth() / 2);
        }

        private void configureWindow(Dialog dialog) {
            if (dialog == null || dialog.getWindow() == null) return;
            Window window = dialog.getWindow();
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.setDimAmount(0f);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
            WindowCompat.setDecorFitsSystemWindows(window, false);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            Util.hideSystemUI(window);
        }
    }

    private static void restoreParent(Fragment fragment, PlayerManager player, boolean restoreParent) {
        FragmentActivity activity = fragment.getActivity();
        if (!restoreParent || activity == null || activity.isFinishing()) return;
        DanmakuDialog.create().player(player).show(activity);
    }
}
