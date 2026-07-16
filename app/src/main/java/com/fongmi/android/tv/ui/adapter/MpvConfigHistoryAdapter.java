package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterConfigBinding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MpvConfigHistoryAdapter extends RecyclerView.Adapter<MpvConfigHistoryAdapter.ViewHolder> {

    private final List<CharSequence> items;
    private final OnClickListener listener;

    public MpvConfigHistoryAdapter(CharSequence[] items, OnClickListener listener) {
        this.items = new ArrayList<>(Arrays.asList(items));
        this.listener = listener;
    }

    public interface OnClickListener {

        void onTextClick(int position);

        void onDeleteClick(int position);
    }

    public void remove(int position) {
        if (position < 0 || position >= items.size()) return;
        items.remove(position);
        notifyItemRemoved(position);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterConfigBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.binding.text.setText(items.get(position));
        holder.binding.text.setOnClickListener(view -> dispatchTextClick(holder));
        holder.binding.delete.setContentDescription(holder.itemView.getContext().getString(R.string.setting_delete));
        holder.binding.delete.setOnClickListener(view -> dispatchDeleteClick(holder));
    }

    private void dispatchTextClick(ViewHolder holder) {
        int position = holder.getBindingAdapterPosition();
        if (position != RecyclerView.NO_POSITION) listener.onTextClick(position);
    }

    private void dispatchDeleteClick(ViewHolder holder) {
        int position = holder.getBindingAdapterPosition();
        if (position != RecyclerView.NO_POSITION) listener.onDeleteClick(position);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterConfigBinding binding;

        public ViewHolder(@NonNull AdapterConfigBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
