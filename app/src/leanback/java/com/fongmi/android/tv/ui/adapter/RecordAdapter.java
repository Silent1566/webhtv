package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.databinding.AdapterSearchRecordBinding;
import com.fongmi.android.tv.setting.Setting;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

public class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<String> mItems;

    public RecordAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = getItems();
        this.listener.onDataChanged(mItems.size());
    }

    public interface OnClickListener {

        void onItemClick(String text);

        void onDataChanged(int size);
    }

    private List<String> getItems() {
        if (Setting.getKeyword().isEmpty()) return new ArrayList<>();
        return App.gson().fromJson(Setting.getKeyword(), TypeToken.getParameterized(List.class, String.class).getType());
    }

    private void checkToAdd(String item) {
        mItems.remove(item);
        mItems.add(0, item);
        if (mItems.size() > 9) mItems.remove(9);
    }

    public void add(String item) {
        checkToAdd(item);
        notifyDataSetChanged();
        listener.onDataChanged(getItemCount());
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSearchRecordBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String text = mItems.get(position);
        holder.binding.text.setText(text);
        holder.binding.text.setOnClickListener(v -> listener.onItemClick(text));
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnLongClickListener {

        private final AdapterSearchRecordBinding binding;

        public ViewHolder(@NonNull AdapterSearchRecordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnLongClickListener(this);
        }

        @Override
        public boolean onLongClick(View v) {
            mItems.remove(getLayoutPosition());
            notifyItemRemoved(getLayoutPosition());
            listener.onDataChanged(getItemCount());
            Setting.putKeyword(App.gson().toJson(mItems));
            return true;
        }
    }
}
