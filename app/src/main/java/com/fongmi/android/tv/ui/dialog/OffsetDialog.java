package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogOffsetBinding;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

public final class OffsetDialog {

    private PlayerManager player;
    private int type;

    public static OffsetDialog create() {
        return new OffsetDialog();
    }

    public OffsetDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public OffsetDialog type(int type) {
        this.type = type;
        return this;
    }

    public void show(FragmentActivity activity) {
        FragmentManager manager = activity.getSupportFragmentManager();
        for (Fragment f : manager.getFragments()) if (f instanceof BottomSheet || f instanceof SideSheet) return;
        if (Util.isLeanback()) new SideSheet(player, type).show(manager, null);
        else new BottomSheet(player, type).show(manager, null);
    }

    private static DialogOffsetBinding inflate(LayoutInflater inflater, ViewGroup container) {
        return DialogOffsetBinding.inflate(inflater, container, false);
    }

    public static final class BottomSheet extends BaseBottomSheetDialog {

        private DialogOffsetBinding binding;
        private final PlayerManager player;
        private final int type;

        BottomSheet(PlayerManager player, int type) {
            this.player = player;
            this.type = type;
        }

        @Override
        protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
            return binding = OffsetDialog.inflate(inflater, container);
        }

        @Override
        protected void initView() {
            new OffsetPanel(binding, player, type).bind();
        }

        @Override
        protected boolean transparent() {
            return true;
        }

        @Override
        protected boolean stableOverlay() {
            return true;
        }
    }

    public static final class SideSheet extends BaseSideSheetDialog {

        private DialogOffsetBinding binding;
        private final PlayerManager player;
        private final int type;

        SideSheet(PlayerManager player, int type) {
            this.player = player;
            this.type = type;
        }

        @Override
        protected int getWidth() {
            return Math.min(ResUtil.dp2px(320), ResUtil.getScreenWidth() / 2);
        }

        @Override
        protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
            return binding = OffsetDialog.inflate(inflater, container);
        }

        @Override
        protected void initView() {
            new OffsetPanel(binding, player, type).bind();
        }
    }
}
