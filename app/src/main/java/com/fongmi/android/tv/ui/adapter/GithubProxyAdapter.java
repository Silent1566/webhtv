package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.AdapterGithubProxyBinding;

import java.util.ArrayList;
import java.util.List;

public class GithubProxyAdapter extends RecyclerView.Adapter<GithubProxyAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<String> mItems;
    private int mSelected;

    public GithubProxyAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onActive(String item);

        void onRemove(String item);
    }

    public void setItems(List<String> items, String active) {
        mItems.clear();
        mItems.addAll(items);
        mSelected = Math.max(0, mItems.indexOf(active));
        notifyDataSetChanged();
    }

    public int getSelected() {
        return mSelected;
    }

    public String getItem(int position) {
        return mItems.get(position);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterGithubProxyBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String item = mItems.get(position);
        holder.binding.text.setText(item);
        holder.binding.text.setActivated(position == mSelected);
        holder.binding.text.setOnClickListener(v -> listener.onActive(item));
        holder.binding.remove.setOnClickListener(v -> listener.onRemove(item));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterGithubProxyBinding binding;

        public ViewHolder(@NonNull AdapterGithubProxyBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
