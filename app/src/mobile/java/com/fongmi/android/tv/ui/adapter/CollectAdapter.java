package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterCollectBinding;

import java.util.List;

public class CollectAdapter extends BaseDiffAdapter<Collect, CollectAdapter.ViewHolder> {

    private final OnClickListener listener;
    private int progressCurrent;
    private int progressTotal;

    public CollectAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {

        void onItemClick(int position, Collect item);
    }

    public void add(List<Vod> items) {
        if (getItemCount() == 0) return;
        getItem(0).getList().addAll(items);
    }

    public void setProgress(int current, int total) {
        progressTotal = Math.max(0, total);
        progressCurrent = Math.max(0, Math.min(current, progressTotal));
        if (getItemCount() > 0) notifyItemChanged(0);
    }

    public int getPosition() {
        for (int i = 0; i < getItemCount(); i++) if (getItem(i).isSelected()) return i;
        return 0;
    }

    public Collect getActivated() {
        return getItems().get(getPosition());
    }

    public void setSelected(int position) {
        for (int i = 0; i < getItemCount(); i++) getItem(i).setSelected(i == position);
        notifyItemRangeChanged(0, getItemCount());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterCollectBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Collect item = getItem(position);
        boolean all = "all".equals(item.getSite().getKey());
        holder.binding.text.setSelected(item.isSelected());
        String name = item.getSite().getDisplayName();
        holder.binding.text.setText(all && progressTotal > 0 ? name + " " + progressCurrent + "/" + progressTotal : name);
        holder.binding.text.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onItemClick(pos, getItem(pos));
        });
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterCollectBinding binding;

        ViewHolder(@NonNull AdapterCollectBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
