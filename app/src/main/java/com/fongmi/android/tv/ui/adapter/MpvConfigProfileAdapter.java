package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.AdapterMpvConfigProfileBinding;
import com.fongmi.android.tv.player.mpv.MpvConfigStore;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MpvConfigProfileAdapter extends RecyclerView.Adapter<MpvConfigProfileAdapter.ViewHolder> {

    public interface Listener {
        void onSelect(MpvConfigStore.ConfigProfile profile);

        void onMore(View anchor, MpvConfigStore.ConfigProfile profile);
    }

    private final List<MpvConfigStore.ConfigProfile> items = new ArrayList<>();
    private final Listener listener;
    private boolean scripts;

    public MpvConfigProfileAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submit(List<MpvConfigStore.ConfigProfile> profiles, boolean scripts) {
        items.clear();
        if (profiles != null) items.addAll(profiles);
        this.scripts = scripts;
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= items.size() || items.get(position).id == null) return RecyclerView.NO_ID;
        return items.get(position).id.hashCode() & 0xffffffffL;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterMpvConfigProfileBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MpvConfigStore.ConfigProfile profile = items.get(position);
        holder.binding.name.setText(profile.name);
        holder.binding.detail.setText(detail(profile));
        holder.binding.active.setVisibility(profile.active && !scripts ? View.VISIBLE : View.GONE);
        holder.binding.root.setSelected(!scripts && profile.active);
        holder.binding.root.setOnClickListener(view -> listener.onSelect(profile));
        holder.binding.more.setOnClickListener(view -> listener.onMore(view, profile));
    }

    private String detail(MpvConfigStore.ConfigProfile profile) {
        if (profile.isDefault()) return profile.typeLabel();
        String time = profile.time <= 0 ? "" : DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(profile.time));
        return time.isEmpty() ? profile.typeLabel() : time + " · " + profile.typeLabel();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterMpvConfigProfileBinding binding;

        ViewHolder(AdapterMpvConfigProfileBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
