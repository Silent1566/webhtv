package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * TMDB 剧照横向滚动适配器。
 */
public class TmdbPhotoAdapter extends RecyclerView.Adapter<TmdbPhotoAdapter.ViewHolder> {

    private final List<String> items = new ArrayList<>();
    private Listener legacyListener;
    private OnItemClickListener listener;
    private boolean legacyMode;

    public interface OnItemClickListener {
        void onItemClick(String url, int position);
    }

    public interface Listener {
        void onItemClick(int position, String url);
    }

    public TmdbPhotoAdapter() {
    }

    public TmdbPhotoAdapter(Listener listener) {
        this.legacyListener = listener;
        this.legacyMode = true;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setLight(boolean light) {
        legacyMode = true;
    }

    public void setItems(List<String> photos) {
        List<String> next = photos == null ? List.of() : photos;
        if (items.equals(next)) return;
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    public List<String> getItems() {
        return new ArrayList<>(items);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_tmdb_photo, parent, false), legacyMode);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), position, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final ImageView photo;

        public ViewHolder(@NonNull android.view.View itemView) {
            super(itemView);
            if (!Util.isLeanback()) {
                itemView.setFocusable(false);
                itemView.setFocusableInTouchMode(false);
            }
            photo = itemView.findViewById(R.id.photo);
        }

        public ViewHolder(@NonNull android.view.View itemView, boolean legacyMode) {
            super(itemView);
            if (!legacyMode && !Util.isLeanback()) {
                itemView.setFocusable(false);
                itemView.setFocusableInTouchMode(false);
            }
            photo = itemView.findViewById(R.id.photo);
        }

        void bind(String url, int position, OnItemClickListener listener) {
            ImgUtil.load(photo.getContext().getString(R.string.tmdb_photos_label), url, photo);

            if (listener != null) {
                itemView.setOnClickListener(v -> listener.onItemClick(url, position));
            }
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        if (!legacyMode) return;
        holder.itemView.setOnClickListener(view -> {
            int position = holder.getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION || legacyListener == null) return;
            legacyListener.onItemClick(position, items.get(position));
        });
    }
}
