package com.fongmi.android.tv.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.ui.helper.TmdbCinemaTheme;
import com.fongmi.android.tv.utils.ImgUtil;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TmdbRailAdapter extends RecyclerView.Adapter<TmdbRailAdapter.ViewHolder> {

    public interface Listener {
        void onItemClick(TmdbItem item);
    }

    public interface LongClickListener {
        boolean onItemLongClick(TmdbItem item);
    }

    public interface FocusListener {
        void onItemFocus(TmdbItem item, boolean focused);
    }

    private final Listener listener;
    private final List<TmdbItem> items = new ArrayList<>();
    private LongClickListener longClickListener;
    private FocusListener focusListener;
    private boolean cinema;
    private boolean light;

    public TmdbRailAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setOnItemLongClickListener(LongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setOnItemFocusListener(FocusListener listener) {
        this.focusListener = listener;
    }

    public void setItems(List<TmdbItem> values) {
        if (sameItems(values)) return;
        items.clear();
        if (values != null) items.addAll(values);
        notifyDataSetChanged();
    }

    public void setCinema(boolean cinema) {
        this.cinema = cinema;
        notifyDataSetChanged();
    }

    public void setLight(boolean light) {
        this.light = light;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return cinema ? 1 : 0;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == 1 ? R.layout.adapter_tmdb_rail_landscape : R.layout.adapter_tmdb_rail_item;
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TmdbItem item = items.get(position);
        CardMeta meta = CardMeta.from(item.getSubtitle());
        holder.title.setText(item.getTitle());
        holder.subtitle.setText(meta.subtitle);
        holder.subtitle.setVisibility(TextUtils.isEmpty(meta.subtitle) ? View.GONE : View.VISIBLE);
        holder.rating.setText(meta.rating);
        holder.rating.setVisibility(TextUtils.isEmpty(meta.rating) ? View.GONE : View.VISIBLE);
        TmdbCinemaTheme.Palette palette = TmdbCinemaTheme.palette(light);
        holder.title.setTextColor(0xFFFFFFFF);
        holder.subtitle.setTextColor(cinema ? 0xB3FFFFFF : 0x99FFFFFF);
        holder.rating.setTextColor(0xFFFFFFFF);
        TmdbCardFocusHelper.bind(holder.root, cinema ? 0xB314202A : 0xFF16202A, cinema ? palette.cardStroke() : 0x33FFFFFF, 1, focused -> {
            if (focusListener != null) focusListener.onItemFocus(item, focused);
        });
        String image = cinema && !TextUtils.isEmpty(item.getBackdropUrl()) ? item.getBackdropUrl() : item.getPosterUrl();
        ImgUtil.load(item.getTitle(), image, holder.poster);
        holder.root.setOnClickListener(view -> listener.onItemClick(item));
        holder.root.setOnLongClickListener(view -> longClickListener != null && longClickListener.onItemLongClick(item));
        if (holder.root.hasFocus() && focusListener != null) focusListener.onItemFocus(item, true);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private boolean sameItems(List<TmdbItem> values) {
        if (values == null) return items.isEmpty();
        if (items.size() != values.size()) return false;
        for (int i = 0; i < items.size(); i++) {
            if (!sameItem(items.get(i), values.get(i))) return false;
        }
        return true;
    }

    private boolean sameItem(TmdbItem first, TmdbItem second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        if (first.getTmdbId() > 0 && second.getTmdbId() > 0) {
            return first.getTmdbId() == second.getTmdbId() && first.getMediaType().equals(second.getMediaType());
        }
        return first.getTitle().equals(second.getTitle())
                && first.getSubtitle().equals(second.getSubtitle())
                && first.getPosterUrl().equals(second.getPosterUrl())
                && first.getBackdropUrl().equals(second.getBackdropUrl());
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final MaterialCardView root;
        private final AppCompatImageView poster;
        private final TextView title;
        private final TextView subtitle;
        private final TextView rating;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            root = (MaterialCardView) itemView;
            poster = itemView.findViewById(R.id.poster);
            title = itemView.findViewById(R.id.title);
            subtitle = itemView.findViewById(R.id.subtitle);
            rating = itemView.findViewById(R.id.rating);
        }
    }

    private record CardMeta(String subtitle, String rating) {

        static CardMeta from(String subtitle) {
            if (TextUtils.isEmpty(subtitle)) return new CardMeta("", "");
            List<String> meta = new ArrayList<>();
            String rating = "";
            for (String raw : subtitle.split("[·路]")) {
                String part = raw.trim();
                if (TextUtils.isEmpty(part)) continue;
                String lower = part.toLowerCase(Locale.ROOT);
                if (part.startsWith("评分") || lower.startsWith("score")) {
                    rating = part.replace("评分", "").replace("Score", "").replace("score", "").trim();
                } else {
                    meta.add(part);
                }
            }
            if (!TextUtils.isEmpty(rating)) rating = "★ " + rating;
            return new CardMeta(TextUtils.join(" · ", meta), rating);
        }
    }
}
