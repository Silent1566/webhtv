package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.AdapterPartBinding;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class PartAdapter extends RecyclerView.Adapter<PartAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<String> mItems;
    private final int maxWidth;
    private int nextFocusDown;
    private int nextFocusUp;

    public PartAdapter(OnClickListener listener) {
        mListener = listener;
        mItems = new ArrayList<>();
        maxWidth = ResUtil.getScreenWidth() - ResUtil.dp2px(48);
    }

    public void addAll(List<String> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public void setNextFocusUp(int nextFocusUp) {
        setNextFocus(nextFocusUp, nextFocusDown);
    }

    public void setNextFocus(int nextFocusUp, int nextFocusDown) {
        if (this.nextFocusUp == nextFocusUp && this.nextFocusDown == nextFocusDown) return;
        this.nextFocusUp = nextFocusUp;
        this.nextFocusDown = nextFocusDown;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterPartBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String text = mItems.get(position);
        holder.binding.text.setText(text);
        holder.binding.text.setMaxWidth(maxWidth);
        holder.binding.text.setNextFocusUpId(nextFocusUp == 0 ? View.NO_ID : nextFocusUp);
        holder.binding.text.setNextFocusDownId(nextFocusDown == 0 ? View.NO_ID : nextFocusDown);
        holder.binding.getRoot().setOnClickListener(v -> mListener.onItemClick(text));
    }

    public interface OnClickListener {

        void onItemClick(String item);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterPartBinding binding;

        ViewHolder(@NonNull AdapterPartBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
