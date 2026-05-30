package com.fongmi.android.tv.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.databinding.AdapterTmdbEpisodeBinding;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TmdbEpisodeAdapter extends RecyclerView.Adapter<TmdbEpisodeAdapter.ViewHolder> {

    public interface Listener {
        void onItemClick(Episode item);

        void onItemLongClick(Episode item, int episodeNumber);
    }

    private final Listener listener;
    private final List<Episode> items = new ArrayList<>();
    private final Map<Integer, TmdbEpisode> tmdbItems = new HashMap<>();
    private Episode selected;
    private boolean light;
    private boolean compactPlain;
    private int activeStrokeColor = 0xFF2CC56F;

    public TmdbEpisodeAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Episode> episodes, Map<Integer, TmdbEpisode> tmdbEpisodes, Episode selected) {
        items.clear();
        items.addAll(episodes);
        tmdbItems.clear();
        tmdbItems.putAll(tmdbEpisodes);
        compactPlain = items.size() == 1 && tmdbItems.isEmpty() && TextUtils.isEmpty(items.get(0).getDesc());
        this.selected = selected;
        notifyDataSetChanged();
    }

    public void setSelected(Episode selected) {
        this.selected = selected;
        notifyDataSetChanged();
    }

    public void setLight(boolean light) {
        this.light = light;
        notifyDataSetChanged();
    }

    public void setActiveStrokeColor(int activeStrokeColor) {
        this.activeStrokeColor = activeStrokeColor;
        notifyDataSetChanged();
    }

    public int getPosition(Episode episode) {
        return items.indexOf(episode);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterTmdbEpisodeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Episode episode = items.get(position);
        TmdbEpisode tmdbEpisode = tmdbItems.get(position + 1);
        String title = tmdbEpisode != null && !TextUtils.isEmpty(tmdbEpisode.getTitle()) ? tmdbEpisode.getTitle() : episode.getName();
        String date = tmdbEpisode != null ? tmdbEpisode.getDate() : "";
        String overview = tmdbEpisode != null ? tmdbEpisode.getOverview() : episode.getDesc();
        boolean activated = episode.equals(selected);
        boolean compact = compactPlain && tmdbEpisode == null && TextUtils.isEmpty(overview);

        applyCardSize(holder, compact);
        if (compact) {
            holder.binding.index.setText(episode.getName());
            holder.binding.index.setTextSize(14f);
            holder.binding.title.setVisibility(View.GONE);
            holder.binding.date.setVisibility(View.GONE);
            holder.binding.overview.setVisibility(View.GONE);
        } else {
            holder.binding.index.setText((position + 1) + ". " + episode.getName());
            holder.binding.index.setTextSize(12f);
            holder.binding.title.setText(title);
            holder.binding.title.setVisibility(View.VISIBLE);
            holder.binding.date.setText(date);
            holder.binding.date.setVisibility(TextUtils.isEmpty(date) ? View.GONE : View.VISIBLE);
            holder.binding.overview.setText(overview);
            holder.binding.overview.setVisibility(TextUtils.isEmpty(overview) ? View.GONE : View.VISIBLE);
        }
        holder.binding.index.setTextColor(light ? 0xFF15202B : 0xFFFFFFFF);
        holder.binding.title.setTextColor(light ? 0xCC15202B : 0xE6FFFFFF);
        holder.binding.date.setTextColor(light ? 0x9915202B : 0x99FFFFFF);
        holder.binding.overview.setTextColor(light ? 0xB315202B : 0xCCFFFFFF);
        TmdbCardFocusHelper.bind(
                holder.binding.getRoot(),
                activated ? (light ? 0xFFE5F7EC : 0x6630A86B) : (light ? 0xEEFFFFFF : 0xCC16202A),
                activated ? activeStrokeColor : (light ? 0x33647480 : 0x33FFFFFF),
                activated ? 2 : 1);
        if (tmdbEpisode != null && !TextUtils.isEmpty(tmdbEpisode.getStillUrl())) {
            holder.binding.still.setVisibility(View.VISIBLE);
            ImgUtil.load(title, tmdbEpisode.getStillUrl(), holder.binding.still);
        } else {
            holder.binding.still.setVisibility(View.GONE);
        }
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(episode));
        holder.binding.getRoot().setOnLongClickListener(view -> {
            listener.onItemLongClick(episode, position + 1);
            return true;
        });
    }

    private void applyCardSize(ViewHolder holder, boolean compact) {
        ViewGroup.LayoutParams params = holder.binding.getRoot().getLayoutParams();
        params.width = dp(holder.itemView, compact ? 220 : 230);
        params.height = dp(holder.itemView, compact ? 78 : 190);
        holder.binding.getRoot().setLayoutParams(params);
    }

    private int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterTmdbEpisodeBinding binding;

        ViewHolder(@NonNull AdapterTmdbEpisodeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
