package com.fongmi.android.tv.ui.adapter;

import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.AdapterSiteBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.github.catvod.crawler.SpiderDebug;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SiteAdapter extends RecyclerView.Adapter<SiteAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Site> allItems;
    private final List<Site> mItems;
    private boolean firstBindLogged;
    private int type;

    public SiteAdapter(OnClickListener listener) {
        this.listener = listener;
        this.allItems = new ArrayList<>();
        this.mItems = new ArrayList<>();
        this.addAll();
    }

    public interface OnClickListener {

        void onItemClick(Site item);
    }

    public void setType(int type) {
        this.type = type;
        notifyDataSetChanged();
    }

    public void filter(String group, String keyword) {
        String tag = group == null ? "" : group.trim();
        String text = keyword == null ? "" : keyword.trim().toLowerCase(Locale.getDefault());
        mItems.clear();
        for (Site site : allItems) {
            String name = site.getName();
            boolean matchGroup = TextUtils.isEmpty(tag) || name.contains(tag);
            boolean matchKeyword = TextUtils.isEmpty(text) || name.toLowerCase(Locale.getDefault()).contains(text) || site.getKey().toLowerCase(Locale.getDefault()).contains(text);
            if (matchGroup && matchKeyword) mItems.add(site);
        }
        notifyDataSetChanged();
    }

    public void selectAll() {
        setEnable(type != 3);
    }

    public void cancelAll() {
        setEnable(type == 3);
    }

    private void addAll() {
        for (Site site : VodConfig.get().getSites()) if (!site.isHide()) allItems.add(site);
        if (Setting.isSiteHealthDialogSort()) SiteHealthStore.sortSites(allItems);
        mItems.addAll(allItems);
    }

    public List<Site> getItems() {
        return mItems;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSiteBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Site item = mItems.get(position);
        if (!firstBindLogged) {
            firstBindLogged = true;
            SpiderDebug.log("site-dialog", "first bind position=%s name=%s", position, item.getName());
        }
        holder.binding.text.setText(item.getName());
        holder.binding.health.setBackgroundTintList(ColorStateList.valueOf(SiteHealthStore.getColor(item)));
        holder.binding.check.setChecked(getChecked(item));
        holder.binding.text.setSelected(item.isSelected());
        holder.binding.getRoot().setSelected(item.isSelected());
        holder.binding.getRoot().setOnFocusChangeListener((v, hasFocus) -> holder.binding.text.setSelected(hasFocus || item.isSelected()));
        holder.binding.check.setVisibility(type == 0 ? View.GONE : View.VISIBLE);
        holder.binding.getRoot().setOnLongClickListener(v -> setLongListener(item));
        holder.binding.getRoot().setOnClickListener(v -> setListener(item, position));
        holder.binding.text.setGravity(Gravity.CENTER);
    }

    private boolean getChecked(Site item) {
        if (type == 1) return item.isSearchable();
        if (type == 2) return item.isChangeable();
        return false;
    }

    private void setListener(Site item, int position) {
        if (type == 0) listener.onItemClick(item);
        if (type == 1) item.setSearchable(!item.isSearchable()).save();
        if (type == 2) item.setChangeable(!item.isChangeable()).save();
        if (type != 0) notifyItemChanged(position);
    }

    private boolean setLongListener(Site item) {
        if (type == 1) setEnable(!item.isSearchable());
        if (type == 2) setEnable(!item.isChangeable());
        return true;
    }

    private void setEnable(boolean enable) {
        if (type == 1) for (Site site : mItems) site.setSearchable(enable).save();
        if (type == 2) for (Site site : mItems) site.setChangeable(enable).save();
        notifyItemRangeChanged(0, getItemCount());
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterSiteBinding binding;

        ViewHolder(@NonNull AdapterSiteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
