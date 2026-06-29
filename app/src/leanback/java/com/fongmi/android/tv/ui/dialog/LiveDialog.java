package com.fongmi.android.tv.ui.dialog;

import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.WindowManager;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.databinding.DialogLiveBinding;
import com.fongmi.android.tv.impl.LiveListener;
import com.fongmi.android.tv.ui.adapter.LiveAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class LiveDialog extends BaseAlertDialog implements LiveAdapter.OnClickListener {

    private DialogLiveBinding binding;
    private LiveAdapter adapter;
    private boolean action;
    private boolean drawer;

    public static LiveDialog create() {
        return new LiveDialog();
    }

    public LiveDialog action() {
        action = true;
        return this;
    }

    public LiveDialog drawer() {
        drawer = true;
        return this;
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogLiveBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        adapter = new LiveAdapter(this);
        adapter.setAction(action);
        binding.recycler.setAdapter(adapter);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setItemAnimator(null);
        if (drawer) {
            binding.recycler.setPadding(24, 24, 24, 24);
            binding.recycler.setMaxHeight(ResUtil.getScreenHeight());
        }
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        binding.recycler.post(() -> binding.recycler.scrollToPosition(LiveConfig.getHomeIndex()));
    }

    @Override
    public void onItemClick(Live item) {
        ((LiveListener) requireActivity()).setLive(item);
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

    @Override
    public void onStart() {
        super.onStart();
        if (adapter.getItemCount() == 0) dismiss();
        else if (drawer) setDrawerWindow();
        else setWidth(0.4f);
    }

    private void setDrawerWindow() {
        if (getDialog() == null || getDialog().getWindow() == null) return;
        WindowManager.LayoutParams params = getDialog().getWindow().getAttributes();
        params.gravity = Gravity.END | Gravity.TOP;
        params.width = Math.max(ResUtil.dp2px(360), Math.min(ResUtil.dp2px(520), Math.round(ResUtil.getScreenWidth() * 0.32f)));
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(0xFF28282A));
        getDialog().getWindow().setAttributes(params);
        getDialog().getWindow().setLayout(params.width, WindowManager.LayoutParams.MATCH_PARENT);
    }
}
