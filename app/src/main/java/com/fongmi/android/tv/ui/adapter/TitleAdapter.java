package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaEdition;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.AdapterTitleBinding;
import com.fongmi.android.tv.utils.Util;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;

public class TitleAdapter extends RecyclerView.Adapter<TitleAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<MediaEdition> mItems;
    private final StringBuilder builder;
    private final Formatter formatter;

    public TitleAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
        this.builder = new StringBuilder();
        this.formatter = new Formatter(builder, Locale.getDefault());
    }

    public interface OnClickListener {

        void onItemClick(MediaEdition item);
    }

    public TitleAdapter addAll(List<MediaEdition> items) {
        mItems.addAll(items);
        notifyDataSetChanged();
        return this;
    }

    public int getSelected() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).selected) return i;
        return 0;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterTitleBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MediaEdition item = mItems.get(position);
        holder.binding.text.setSelected(item.selected);
        holder.binding.text.setText(item.label + " [" + Util.format(builder, formatter, item.durationUs / 1000) + "]");
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private final AdapterTitleBinding binding;

        public ViewHolder(@NonNull AdapterTitleBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            listener.onItemClick(mItems.get(getLayoutPosition()));
        }
    }
}
