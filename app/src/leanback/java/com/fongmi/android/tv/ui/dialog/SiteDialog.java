package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.DialogSiteBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.ui.adapter.SiteAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class SiteDialog extends BaseAlertDialog implements SiteAdapter.OnClickListener {

    private static final int GRID_COUNT = 3;
    private static final String TAG = "site_dialog";
    private static final int ITEM_HEIGHT = 46;
    private static final int ITEM_SPACE = 12;
    private static final int MAX_HEIGHT = 344;

    private static String selectedGroup = "";

    private RecyclerView.ItemDecoration decoration;
    private DialogSiteBinding binding;
    private FragmentActivity activity;
    private Dialog directDialog;
    private SiteListener listener;
    private SiteAdapter adapter;
    private List<String> groups;
    private long showStart;
    private boolean action;
    private int type;

    public static SiteDialog create() {
        return new SiteDialog();
    }

    public SiteDialog search() {
        type = 1;
        return this;
    }

    public SiteDialog action() {
        action = true;
        return this;
    }

    public void show(FragmentActivity activity) {
        showStart = System.currentTimeMillis();
        this.activity = activity;
        if (activity instanceof SiteListener) listener = (SiteListener) activity;
        if (activity.isFinishing() || activity.isDestroyed()) return;
        showDirect(activity);
    }

    private int getCount() {
        return GRID_COUNT;
    }

    private float getWidth() {
        return action ? 0.92f : 0.9f;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSiteBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = new Dialog(requireActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(getBinding().getRoot());
        initView();
        initEvent();
        return dialog;
    }

    private void showDirect(FragmentActivity activity) {
        binding = DialogSiteBinding.inflate(activity.getLayoutInflater());
        directDialog = new Dialog(activity);
        directDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        directDialog.setContentView(binding.getRoot());
        initView();
        initEvent();
        if (adapter.getItemCount() == 0) {
            directDialog = null;
            return;
        }
        directDialog.setOnDismissListener(d -> {
            directDialog = null;
            binding = null;
            this.activity = null;
        });
        directDialog.show();
        applyWindow(directDialog.getWindow());
    }

    @Override
    protected void initView() {
        adapter = new SiteAdapter(this);
        groups = getGroups();
        setRootWidth();
        setRecyclerHeight();
        binding.keyword.setVisibility(View.GONE);
        binding.action.setVisibility(action ? View.VISIBLE : View.GONE);
        binding.search.setVisibility(action ? View.VISIBLE : View.GONE);
        binding.change.setVisibility(action ? View.VISIBLE : View.GONE);
        binding.select.setVisibility(action ? View.VISIBLE : View.GONE);
        binding.cancel.setVisibility(action ? View.VISIBLE : View.GONE);
        binding.mode.setVisibility(View.GONE);
        setType(type);
        setRecyclerView();
        setMode();
        setGroupView();
    }

    @Override
    protected void initEvent() {
        binding.config.setOnClickListener(v -> {
            FragmentActivity activity = getDialogActivity();
            dismiss();
            App.post(() -> HistoryDialog.create().vod().readOnly().show(activity, item -> loadConfig(activity, item)), 100);
        });
        binding.mode.setOnClickListener(this::onMode);
        binding.select.setOnClickListener(v -> adapter.selectAll());
        binding.cancel.setOnClickListener(v -> adapter.cancelAll());
        binding.search.setOnClickListener(v -> setType(v.isSelected() ? 0 : 1));
        binding.change.setOnClickListener(v -> setType(v.isSelected() ? 0 : 2));
        binding.keyword.addTextChangedListener(new com.fongmi.android.tv.ui.custom.CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(selectedGroup, s.toString());
                setRecyclerView();
                setMode();
                setWidth();
            }
        });
    }

    private void setRecyclerView() {
        if (binding.recycler.getAdapter() == null) binding.recycler.setAdapter(adapter);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setItemAnimator(null);
        if (decoration == null) binding.recycler.addItemDecoration(decoration = new SpaceItemDecoration(getCount(), 16));
        if (binding.recycler.getLayoutManager() == null) binding.recycler.setLayoutManager(new GridLayoutManager(getDialogActivity(), getCount()));
    }

    private void setRecyclerHeight() {
        int rows = Math.max(1, (int) Math.ceil((double) adapter.getItemCount() / getCount()));
        int height = rows * ResUtil.dp2px(ITEM_HEIGHT) + Math.max(0, rows - 1) * ResUtil.dp2px(ITEM_SPACE);
        ViewGroup.LayoutParams params = binding.recycler.getLayoutParams();
        params.height = Math.min(height, ResUtil.dp2px(MAX_HEIGHT));
        binding.recycler.setLayoutParams(params);
    }

    private void setRootWidth() {
        ViewGroup.LayoutParams params = binding.getRoot().getLayoutParams();
        if (params == null) params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.width = (int) (ResUtil.getScreenWidth() * getWidth());
        binding.getRoot().setLayoutParams(params);
    }

    private void setType(int type) {
        binding.search.setSelected(type == 1);
        binding.change.setSelected(type == 2);
        binding.select.setClickable(type > 0);
        binding.cancel.setClickable(type > 0);
        adapter.setType(this.type = type);
    }

    private void setMode() {
        binding.mode.setEnabled(false);
    }

    private void setWidth() {
        setWidth(getWidth());
    }

    private void onMode(View view) {
        setRecyclerView();
        setMode();
        setWidth();
    }

    @Override
    public void onItemClick(Site item) {
        if (listener != null) listener.setSite(item);
        dismiss();
    }

    private void loadConfig(FragmentActivity activity, Config config) {
        if (config.getUrl().equals(VodConfig.getUrl())) return;
        VodConfig.load(config, new Callback() {
            @Override
            public void start() {
                Notify.progress(activity);
            }

            @Override
            public void success() {
                Notify.dismiss();
                LiveConfig.get().clear();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
            }
        });
    }

    private FragmentActivity getDialogActivity() {
        return activity != null ? activity : requireActivity();
    }

    private void applyWindow(Window window) {
        if (window == null) return;
        window.setWindowAnimations(0);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = (int) (ResUtil.getScreenWidth() * getWidth());
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(params);
    }

    @Override
    public void dismiss() {
        if (directDialog != null) directDialog.dismiss();
        else super.dismiss();
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog() == null ? null : getDialog().getWindow();
        applyWindow(window);
        if (adapter.getItemCount() == 0) dismiss();
    }

    private List<String> getGroups() {
        return new ArrayList<>(Site.getGroups(VodConfig.get().getSites()));
    }

    private void setGroupView() {
        if (groups.isEmpty()) {
            selectedGroup = "";
            binding.groupScroll.setVisibility(View.GONE);
            return;
        }
        if (!TextUtils.isEmpty(selectedGroup) && !groups.contains(selectedGroup)) selectedGroup = "";
        binding.groupScroll.setVisibility(View.VISIBLE);
        binding.groupList.removeAllViews();
        for (String group : groups) binding.groupList.addView(getGroupView(group));
        updateGroupView();
        // 让第一个分组按钮获取焦点
        binding.groupList.post(() -> {
            if (binding.groupList.getChildCount() > 0) {
                binding.groupList.getChildAt(0).requestFocus();
            }
        });
    }

    private androidx.appcompat.widget.AppCompatTextView getGroupView(String group) {
        androidx.appcompat.widget.AppCompatTextView button = new androidx.appcompat.widget.AppCompatTextView(getDialogActivity());
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(ResUtil.dp2px(12));
        button.setLayoutParams(params);
        button.setText(group);
        button.setSingleLine(true);
        button.setAllCaps(false);
        button.setGravity(android.view.Gravity.CENTER);
        button.setPadding(ResUtil.dp2px(16), ResUtil.dp2px(8), ResUtil.dp2px(16), ResUtil.dp2px(8));
        button.setTextColor(ContextCompat.getColorStateList(getDialogActivity(), R.color.selector_group_text));
        button.setBackgroundResource(R.drawable.selector_group_button);
        button.setFocusable(true);
        button.setClickable(true);
        button.setNextFocusDownId(binding.recycler.getId());
        // 焦点监听：获取焦点时自动切换分组
        button.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) onGroupFocus(group, button);
        });
        return button;
    }

    private void onGroupFocus(String group, View view) {
        // 如果已经是当前选中的分组，不需要重复处理
        if (group.equals(selectedGroup)) return;
        selectedGroup = group;
        updateGroupView();
        adapter.filter(selectedGroup, "");
        // 临时阻止 RecyclerView 的子 View 获取焦点
        int oldDescendantFocusability = binding.recycler.getDescendantFocusability();
        binding.recycler.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        adapter.notifyDataSetChanged();
        setRecyclerHeight();
        binding.recycler.scrollToPosition(0);
        // 延迟恢复焦点行为和请求焦点
        view.postDelayed(() -> {
            binding.recycler.setDescendantFocusability(oldDescendantFocusability);
            view.requestFocus();
        }, 50);
        if (!TextUtils.isEmpty(selectedGroup)) centerGroup(view);
    }

    private void updateGroupView() {
        for (int i = 0; i < binding.groupList.getChildCount(); i++) {
            View view = binding.groupList.getChildAt(i);
            String groupText = ((androidx.appcompat.widget.AppCompatTextView) view).getText().toString();
            boolean selected = !TextUtils.isEmpty(selectedGroup) && groupText.equals(selectedGroup);
            view.setSelected(selected);
        }
    }

    private void centerGroup(View view) {
        binding.groupScroll.post(() -> binding.groupScroll.smoothScrollTo(Math.max(0, view.getLeft() + view.getWidth() / 2 - binding.groupScroll.getWidth() / 2), 0));
    }
}
