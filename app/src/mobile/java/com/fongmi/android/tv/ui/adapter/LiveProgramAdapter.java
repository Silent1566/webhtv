package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.databinding.AdapterLiveProgramItemBinding;

import java.util.ArrayList;
import java.util.List;

public class LiveProgramAdapter extends RecyclerView.Adapter<LiveProgramAdapter.ViewHolder> {

    private final List<EpgData> items;

    public LiveProgramAdapter() {
        this.items = new ArrayList<>();
    }

    public void setEpg(Epg epg) {
        items.clear();
        items.addAll(epg.getList());
        notifyDataSetChanged();
    }

    public int getSelected() {
        for (int i = 0; i < items.size(); i++) if (items.get(i).isSelected()) return i;
        return -1;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterLiveProgramItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EpgData item = items.get(position);
        holder.binding.time.setText(item.getTime());
        holder.binding.title.setText(item.getTitle());
        holder.binding.status.setVisibility(item.isSelected() ? View.VISIBLE : View.GONE);
        holder.binding.status.setText(holder.itemView.getContext().getString(R.string.live_program_current));
        holder.binding.getRoot().setSelected(item.isSelected());
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterLiveProgramItemBinding binding;

        ViewHolder(@NonNull AdapterLiveProgramItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
