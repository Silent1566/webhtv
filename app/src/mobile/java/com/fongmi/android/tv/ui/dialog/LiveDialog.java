package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.databinding.DialogLiveBinding;
import com.fongmi.android.tv.impl.LiveListener;
import com.fongmi.android.tv.ui.adapter.LiveAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class LiveDialog extends BaseBottomSheetDialog implements LiveAdapter.OnClickListener {

    private DialogLiveBinding binding;
    private LiveListener listener;
    private LiveAdapter adapter;
    private boolean drawer;

    public static LiveDialog create() {
        return new LiveDialog();
    }

    public static void show(FragmentActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || activity.getSupportFragmentManager().isStateSaved()) return;
        new LiveDialog().show(activity.getSupportFragmentManager(), null);
    }

    public static void show(Fragment fragment) {
        if (fragment == null || !fragment.isAdded() || fragment.isStateSaved() || fragment.getActivity() == null || fragment.getChildFragmentManager().isStateSaved()) return;
        new LiveDialog().show(fragment.getChildFragmentManager(), null);
    }

    public LiveDialog drawer() {
        drawer = true;
        return this;
    }

    private boolean isFull() {
        return getParentFragment() == null;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        listener = isFull() ? (LiveListener) context : (LiveListener) getParentFragment();
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
        if (adapter.getItemCount() == 0) dismiss();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogLiveBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        adapter = new LiveAdapter(this);
        adapter.setAction(!isFull());
        binding.recycler.setAdapter(adapter);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(true);
        if (drawer) fillDrawer();
        binding.recycler.setLayoutManager(isFull() && !drawer ? new GridLayoutManager(requireContext(), 2) : new LinearLayoutManager(requireContext()));
        binding.recycler.addItemDecoration(new SpaceItemDecoration(isFull() && !drawer ? 2 : 1, 12));
        binding.recycler.setMaxHeight(drawer ? ResUtil.getScreenHeight(requireContext()) : ResUtil.getScreenHeight(requireContext()) * (ResUtil.isLand(requireContext()) ? 72 : 48) / 100);
        binding.recycler.post(() -> binding.recycler.scrollToPosition(LiveConfig.getHomeIndex()));
    }

    @Override
    public void onItemClick(Live item) {
        listener.setLive(item);
        dismiss();
    }

    @Override
    public void onBootClick(int position, Live item) {
        item.boot(!item.isBoot()).save();
        adapter.notifyItemChanged(position);
    }

    @Override
    public void onPassClick(int position, Live item) {
        item.pass(!item.isPass()).save();
        adapter.notifyItemChanged(position);
    }

    @Override
    public boolean onBootLongClick(Live item) {
        boolean result = !item.isBoot();
        LiveConfig.get().getLives().forEach(live -> live.boot(result).save());
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        return true;
    }

    @Override
    public boolean onPassLongClick(Live item) {
        boolean result = !item.isPass();
        LiveConfig.get().getLives().forEach(live -> live.pass(result).save());
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        return true;
    }

    private void configureWindow(Dialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;
        Window window = dialog.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.setDimAmount(0f);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        if (drawer) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = Gravity.END | Gravity.TOP;
            params.width = getPanelWidth();
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            window.setAttributes(params);
            window.setLayout(getPanelWidth(), WindowManager.LayoutParams.MATCH_PARENT);
        }
        WindowCompat.setDecorFitsSystemWindows(window, true);
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
        sheet.setBackgroundColor(ResUtil.getColor(R.color.transparent));
        int height = drawer ? WindowManager.LayoutParams.MATCH_PARENT : getPanelHeight();
        ViewGroup.LayoutParams params = sheet.getLayoutParams();
        params.width = drawer ? ViewGroup.LayoutParams.MATCH_PARENT : params.width;
        params.height = height;
        sheet.setLayoutParams(params);
        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
        behavior.setPeekHeight(drawer ? ResUtil.getScreenHeight(requireContext()) : height);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        behavior.setSkipCollapsed(true);
        behavior.setDraggable(false);
    }

    private void fillDrawer() {
        binding.getRoot().setBackgroundResource(R.drawable.shape_dialog_control_glass_panel);
        binding.getRoot().getChildAt(0).setBackgroundResource(R.drawable.shape_dialog_control_glass_scrim);
        ViewGroup.LayoutParams params = binding.recycler.getLayoutParams();
        if (params instanceof LinearLayoutCompat.LayoutParams layoutParams) {
            layoutParams.height = 0;
            layoutParams.weight = 1;
            binding.recycler.setLayoutParams(layoutParams);
        }
    }

    private int getPanelWidth() {
        int screen = ResUtil.getScreenWidth(requireContext());
        if (ResUtil.isLand(requireContext())) return Math.max(ResUtil.dp2px(320), Math.min(ResUtil.dp2px(430), Math.round(screen * 0.36f)));
        return screen;
    }

    private int getPanelHeight() {
        int screen = ResUtil.getScreenHeight(requireContext());
        if (ResUtil.isLand(requireContext())) return Math.max(ResUtil.dp2px(240), Math.min(ResUtil.dp2px(430), Math.round(screen * 0.72f)));
        return Math.max(ResUtil.dp2px(300), Math.min(ResUtil.dp2px(520), Math.round(screen * 0.52f)));
    }
}
