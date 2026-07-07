package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.databinding.AdapterEpgDataBinding;
import com.fongmi.android.tv.setting.LiveSetting;

import java.util.ArrayList;
import java.util.List;

public class EpgDataAdapter extends RecyclerView.Adapter<EpgDataAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<EpgData> mItems;

    public EpgDataAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(EpgData item);
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public void addAll(List<EpgData> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void setSelected(EpgData item) {
        setSelected(mItems.indexOf(item));
    }

    public void setSelected(int position) {
        for (int i = 0; i < mItems.size(); i++) mItems.get(i).setSelected(i == position);
        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterEpgDataBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EpgData item = mItems.get(position);
        holder.binding.time.setText(item.getTime());
        holder.binding.title.setText(item.getTitle());
        setListStyle(holder);
        holder.binding.getRoot().setSelected(item.isSelected());
        holder.binding.getRoot().setOnClickListener(view -> {
            if (!item.isFuture()) listener.onItemClick(item);
        });
    }

    private void setListStyle(ViewHolder holder) {
        boolean classic = LiveSetting.isListStyleClassic();
        holder.binding.getRoot().setBackgroundResource(classic ? R.drawable.shape_live_classic : R.drawable.shape_live);
        holder.binding.title.setTextColor(ContextCompat.getColorStateList(holder.binding.title.getContext(), classic ? R.color.selector_live_text_classic : R.color.selector_live_text));
        holder.binding.time.setTextColor(ContextCompat.getColorStateList(holder.binding.time.getContext(), classic ? R.color.selector_live_text_classic : R.color.selector_live_text));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterEpgDataBinding binding;

        ViewHolder(@NonNull AdapterEpgDataBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
