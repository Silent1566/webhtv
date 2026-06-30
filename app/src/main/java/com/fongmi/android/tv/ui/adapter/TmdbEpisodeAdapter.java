package com.fongmi.android.tv.ui.adapter;

import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.databinding.AdapterTmdbEpisodeBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.helper.TmdbEpisodeGridPolicy;
import com.fongmi.android.tv.utils.EpisodeTitleFormatter;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TmdbEpisodeAdapter extends RecyclerView.Adapter<TmdbEpisodeAdapter.ViewHolder> {

    public enum Mode {
        LIST,
        GRID
    }

    public interface Listener {
        void onItemClick(Episode item);

        void onItemLongClick(View anchor, Episode item, int episodeNumber);
    }

    private final Listener listener;
    private final List<Episode> items = new ArrayList<>();
    private final Map<Integer, TmdbEpisode> tmdbItems = new HashMap<>();
    private final Map<Episode, Integer> episodeNumbers = new HashMap<>();
    private Episode selected;
    private Mode mode = Mode.LIST;
    private boolean light;
    private boolean compactPlain;
    private boolean nativeEnhanced;
    private int activeStrokeColor = 0xFF2CC56F;
    private int gridSpanCount = 2;
    private String fallbackStillUrl = "";
    private View.OnKeyListener keyListener;
    private View.OnFocusChangeListener focusChangeListener;

    public TmdbEpisodeAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Episode> episodes, Map<Integer, TmdbEpisode> tmdbEpisodes, Episode selected) {
        setItems(episodes, tmdbEpisodes, Map.of(), selected);
    }

    public void setItems(List<Episode> episodes, Map<Integer, TmdbEpisode> tmdbEpisodes, Map<Episode, Integer> numbers, Episode selected) {
        items.clear();
        items.addAll(episodes);
        tmdbItems.clear();
        tmdbItems.putAll(tmdbEpisodes);
        episodeNumbers.clear();
        episodeNumbers.putAll(numbers);
        compactPlain = items.size() == 1 && tmdbItems.isEmpty() && TextUtils.isEmpty(items.get(0).getDesc());
        this.selected = selected;
        notifyDataSetChanged();
    }

    public void setSelected(Episode selected) {
        int oldPosition = getPosition(this.selected);
        int newPosition = getPosition(selected);
        this.selected = selected;
        if (oldPosition == newPosition) {
            if (newPosition >= 0) notifyItemChanged(newPosition);
            return;
        }
        if (oldPosition >= 0) notifyItemChanged(oldPosition);
        if (newPosition >= 0) notifyItemChanged(newPosition);
    }

    public void setLight(boolean light) {
        this.light = light;
        notifyDataSetChanged();
    }

    public void setActiveStrokeColor(int activeStrokeColor) {
        this.activeStrokeColor = activeStrokeColor;
        notifyDataSetChanged();
    }

    public void setFallbackStillUrl(String fallbackStillUrl) {
        String value = TextUtils.isEmpty(fallbackStillUrl) ? "" : fallbackStillUrl;
        if (this.fallbackStillUrl.equals(value)) return;
        this.fallbackStillUrl = value;
        if (usesFallbackStill()) notifyDataSetChanged();
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.LIST : mode;
    }

    public void setGridSpanCount(int gridSpanCount) {
        this.gridSpanCount = Math.max(1, gridSpanCount);
    }

    public void setOnKeyListener(View.OnKeyListener keyListener) {
        this.keyListener = keyListener;
    }

    public void setOnFocusChangeListener(View.OnFocusChangeListener focusChangeListener) {
        this.focusChangeListener = focusChangeListener;
    }

    public void setNativeEnhanced(boolean nativeEnhanced) {
        if (this.nativeEnhanced == nativeEnhanced) return;
        this.nativeEnhanced = nativeEnhanced;
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
        int episodeNumber = episodeNumber(episode, position);
        TmdbEpisode tmdbEpisode = tmdbItems.get(episodeNumber);
        String tmdbTitle = tmdbEpisode != null ? tmdbEpisode.getTitle() : "";
        String cleanTitle = getCleanTitle(episode, episodeNumber, tmdbTitle);
        String title = titleWithFileSize(episode, cleanTitle);
        String fileSize = episodeFileSize(episode);
        String date = tmdbEpisode != null ? tmdbEpisode.getDate() : "";
        String overview = tmdbEpisode != null ? tmdbEpisode.getOverview() : episode.getDesc();
        boolean activated = episode.equals(selected);
        boolean compact = compactPlain && tmdbEpisode == null && TextUtils.isEmpty(overview);
        String stillUrl = tmdbEpisode != null ? tmdbEpisode.getStillUrl() : "";
        boolean allowFallback = mode == Mode.LIST || TmdbEpisodeGridPolicy.shouldUseFallbackImage(mode == Mode.GRID, items.size(), tmdbEpisode != null);
        String imageUrl = !TextUtils.isEmpty(stillUrl) ? stillUrl : (allowFallback ? fallbackStillUrl : "");
        String errorImageUrl = !TextUtils.isEmpty(stillUrl) && allowFallback ? fallbackStillUrl : "";
        boolean hasImage = !TextUtils.isEmpty(imageUrl);
        boolean showVisual = hasImage || !compact;

        applyCardSize(holder, compact);
        if (isNativeEnhanced()) {
            boolean phoneWidth = isPhoneWidth(holder.itemView);
            holder.binding.index.setText(nativeEnhancedIndexTitle(title, cleanTitle, phoneWidth, mode));
            holder.binding.index.setTextSize(nativeEnhancedIndexTextSize(phoneWidth, mode));
            holder.binding.fileSize.setVisibility(View.GONE);
            holder.binding.title.setVisibility(View.GONE);
            holder.binding.date.setText(nativeEnhancedMeta(tmdbEpisode));
            holder.binding.date.setVisibility(TextUtils.isEmpty(holder.binding.date.getText()) || mode != Mode.GRID ? View.GONE : View.VISIBLE);
            holder.binding.badge.setVisibility(View.GONE);
            holder.binding.overview.setText(overview);
            holder.binding.overview.setVisibility(mode == Mode.GRID && !TextUtils.isEmpty(overview) ? View.VISIBLE : View.GONE);
        } else if (mode == Mode.GRID) {
            holder.binding.index.setText(cleanTitle);
            holder.binding.index.setTextSize(14f);
            bindFileSize(holder, fileSize, !TextUtils.isEmpty(date));
            holder.binding.title.setVisibility(View.GONE);
            holder.binding.date.setText(date);
            holder.binding.date.setVisibility(TextUtils.isEmpty(date) ? View.GONE : View.VISIBLE);
            holder.binding.overview.setVisibility(View.GONE);
        } else if (compact) {
            holder.binding.index.setText(title);
            holder.binding.index.setTextSize(14f);
            holder.binding.fileSize.setVisibility(View.GONE);
            holder.binding.title.setVisibility(View.GONE);
            holder.binding.date.setVisibility(View.GONE);
            holder.binding.overview.setVisibility(View.GONE);
        } else {
            holder.binding.index.setText(isPhoneWidth(holder.itemView) ? cleanTitle : title);
            holder.binding.index.setTextSize(12f);
            if (isPhoneWidth(holder.itemView)) bindFileSize(holder, fileSize, !TextUtils.isEmpty(date));
            else holder.binding.fileSize.setVisibility(View.GONE);
            holder.binding.title.setVisibility(View.GONE);
            holder.binding.date.setText(date);
            holder.binding.date.setVisibility(TextUtils.isEmpty(date) ? View.GONE : View.VISIBLE);
            holder.binding.overview.setText(overview);
            holder.binding.overview.setVisibility(TextUtils.isEmpty(overview) ? View.GONE : View.VISIBLE);
        }
        holder.binding.index.setTextColor(showVisual || !light ? 0xFFFFFFFF : 0xFF15202B);
        holder.binding.title.setTextColor(showVisual || !light ? 0xE6FFFFFF : 0xCC15202B);
        holder.binding.date.setTextColor(showVisual || !light ? 0xCCFFFFFF : 0x9915202B);
        holder.binding.overview.setTextColor(showVisual || !light ? 0xE6FFFFFF : 0xB315202B);
        if (!isNativeEnhanced()) {
            holder.binding.badge.setText(episodeBadge(tmdbEpisode));
            holder.binding.badge.setVisibility(TextUtils.isEmpty(holder.binding.badge.getText()) ? View.GONE : View.VISIBLE);
        }
        applyBadgeStyle(holder.binding.date, showVisual);
        applyBadgeStyle(holder.binding.badge, showVisual);
        applyBadgeStyle(holder.binding.fileSize, showVisual);
        applyCardFocus(holder, activated);
        if (showVisual) {
            holder.binding.stillFrame.setVisibility(View.VISIBLE);
            ImgUtil.load(title, imageUrl, errorImageUrl, holder.binding.still, true, imageWidth(holder), imageHeight(holder));
        } else {
            holder.binding.stillFrame.setVisibility(View.GONE);
            ImgUtil.clear(holder.binding.still);
        }
        holder.binding.scrim.setVisibility(showVisual ? View.VISIBLE : View.GONE);
        holder.binding.getRoot().setOnKeyListener(keyListener);
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(episode));
        holder.binding.getRoot().setOnLongClickListener(view -> {
            listener.onItemLongClick(view, episode, episodeNumber);
            return true;
        });
    }

    private void applyCardSize(ViewHolder holder, boolean compact) {
        ViewGroup.LayoutParams params = holder.binding.getRoot().getLayoutParams();
        if (isNativeEnhanced()) {
            params.width = mode == Mode.GRID ? ViewGroup.LayoutParams.MATCH_PARENT : dp(holder.itemView, 280);
            params.height = dp(holder.itemView, mode == Mode.GRID ? nativeEnhancedGridCardHeight(holder.itemView) : 160);
        } else if (mode == Mode.GRID) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = dp(holder.itemView, 118);
        } else {
            params.width = compact ? dp(holder.itemView, 220) : listCardWidth(holder.itemView);
            params.height = dp(holder.itemView, compact ? 78 : (isPhoneWidth(holder.itemView) ? 172 : 190));
        }
        holder.binding.getRoot().setLayoutParams(params);
        if (params instanceof ViewGroup.MarginLayoutParams marginParams) {
            marginParams.setMarginEnd(dp(holder.itemView, isNativeEnhanced() ? 12 : mode == Mode.GRID ? 8 : 12));
            marginParams.bottomMargin = dp(holder.itemView, isNativeEnhanced() && mode == Mode.GRID ? 16 : mode == Mode.GRID ? 10 : 0);
            holder.binding.getRoot().setLayoutParams(marginParams);
        }
        ViewGroup.LayoutParams scrimParams = holder.binding.scrim.getLayoutParams();
        scrimParams.height = dp(holder.itemView, isNativeEnhanced() ? mode == Mode.GRID ? nativeEnhancedGridScrimHeight(holder.itemView) : 104 : 96);
        holder.binding.scrim.setLayoutParams(scrimParams);
        int horizontal = nativeEnhancedMobileGrid(holder.itemView) ? 10 : isNativeEnhanced() ? 12 : 10;
        int top = nativeEnhancedMobileGrid(holder.itemView) ? 18 : isNativeEnhanced() ? 0 : 18;
        int bottom = nativeEnhancedMobileGrid(holder.itemView) ? 10 : isNativeEnhanced() ? mode == Mode.GRID ? 14 : 12 : 10;
        holder.binding.textPanel.setPadding(dp(holder.itemView, horizontal), dp(holder.itemView, top), dp(holder.itemView, horizontal), dp(holder.itemView, bottom));
    }

    private boolean nativeEnhancedMobileGrid(View view) {
        return isNativeEnhanced() && mode == Mode.GRID && isPhoneWidth(view);
    }

    private int nativeEnhancedGridCardHeight(View view) {
        return TmdbEpisodeGridPolicy.nativeGridCardHeightDp(isPhoneWidth(view));
    }

    private int nativeEnhancedGridScrimHeight(View view) {
        return TmdbEpisodeGridPolicy.nativeGridScrimHeightDp(isPhoneWidth(view));
    }

    private int listCardWidth(View view) {
        if (!isPhoneWidth(view)) return dp(view, 230);
        int screen = view.getResources().getDisplayMetrics().widthPixels;
        return Math.max(dp(view, 168), Math.min(dp(view, 230), (screen - dp(view, 56)) / 2));
    }

    private int imageWidth(ViewHolder holder) {
        int width = holder.binding.still.getWidth();
        if (width > 0) return width;
        width = holder.binding.getRoot().getWidth();
        if (width > 0) return width;
        ViewGroup.LayoutParams params = holder.binding.getRoot().getLayoutParams();
        if (params != null && params.width > 0) return params.width;
        if (mode == Mode.GRID) {
            int screen = holder.itemView.getResources().getDisplayMetrics().widthPixels;
            int sidePadding = dp(holder.itemView, Util.isMobile() ? 32 : 48);
            int spacing = dp(holder.itemView, Math.max(0, gridSpanCount - 1) * 8);
            return Math.max(dp(holder.itemView, 160), (screen - sidePadding - spacing) / Math.max(1, gridSpanCount));
        }
        return listCardWidth(holder.itemView);
    }

    private int imageHeight(ViewHolder holder) {
        int height = holder.binding.still.getHeight();
        if (height > 0) return height;
        height = holder.binding.getRoot().getHeight();
        if (height > 0) return height;
        ViewGroup.LayoutParams params = holder.binding.getRoot().getLayoutParams();
        if (params != null && params.height > 0) return params.height;
        if (isNativeEnhanced()) return ResUtil.dp2px(mode == Mode.GRID ? nativeEnhancedGridCardHeight(holder.itemView) : 160);
        return ResUtil.dp2px(mode == Mode.GRID ? TmdbEpisodeGridPolicy.GRID_CARD_HEIGHT_DP : (isPhoneWidth(holder.itemView) ? 172 : 190));
    }

    private boolean isPhoneWidth(View view) {
        return view.getResources().getConfiguration().smallestScreenWidthDp < 600;
    }

    private void applyBadgeStyle(TextView view, boolean hasStill) {
        boolean darkBadge = hasStill || !light;
        view.setTextColor(darkBadge ? 0xE6FFFFFF : 0xCC15202B);
        GradientDrawable background = new GradientDrawable();
        background.setColor(darkBadge ? 0xB3000000 : 0x1F15202B);
        background.setCornerRadius(dp(view, 12));
        view.setBackground(background);
    }

    private void applyCardFocus(ViewHolder holder, boolean activated) {
        if (isNativeEnhanced()) {
            holder.binding.getRoot().setSelected(activated);
            holder.binding.getRoot().setForeground(ResUtil.getDrawable(R.drawable.selector_episode_card));
            holder.binding.getRoot().setCardBackgroundColor(0xFF141A20);
            holder.binding.getRoot().setStrokeWidth(0);
            holder.binding.getRoot().setCardElevation(0);
            holder.binding.getRoot().setTranslationZ(0);
            holder.binding.getRoot().setOnFocusChangeListener(focusChangeListener);
            return;
        }
        TmdbCardFocusHelper.bind(
                holder.binding.getRoot(),
                activated ? (light ? 0xFFE5F7EC : 0x6630A86B) : (light ? 0xEEFFFFFF : 0xCC16202A),
                activated ? activeStrokeColor : (light ? 0x33647480 : 0x33FFFFFF),
                activated ? 2 : 1,
                focused -> {
                    if (focusChangeListener != null) focusChangeListener.onFocusChange(holder.binding.getRoot(), focused);
                });
    }

    private boolean isNativeEnhanced() {
        return nativeEnhanced;
    }

    static String nativeEnhancedIndexTitle(String title, String cleanTitle, boolean phoneWidth, Mode mode) {
        return phoneWidth && mode == Mode.GRID ? cleanTitle : title;
    }

    static float nativeEnhancedIndexTextSize(boolean phoneWidth, Mode mode) {
        return phoneWidth && mode == Mode.GRID ? 14f : phoneWidth ? 12f : 18f;
    }

    public static String getTitle(Episode episode, int number, String tmdbTitle) {
        return titleWithFileSize(episode, getCleanTitle(episode, number, tmdbTitle));
    }

    public static String getCleanTitle(Episode episode, int number, String tmdbTitle) {
        String label = number > 0 ? String.valueOf(number) : episode.getName();
        return formatCleanTitle(label, episode.getName(), tmdbTitle);
    }

    public static String formatTitle(String label, String sourceName, String tmdbTitle) {
        return EpisodeTitleFormatter.withSourceFileSize(sourceName, formatCleanTitle(label, sourceName, tmdbTitle), Setting.isTmdbEpisodeFileSize());
    }

    public static String formatCleanTitle(String label, String sourceName, String tmdbTitle) {
        return EpisodeTitleFormatter.formatTmdbTitle(label, sourceName, tmdbTitle);
    }

    private static String titleWithFileSize(Episode episode, String title) {
        return EpisodeTitleFormatter.withSourceFileSize(episode.getName(), title, Setting.isTmdbEpisodeFileSize());
    }

    private String episodeFileSize(Episode episode) {
        if (!Setting.isTmdbEpisodeFileSize()) return "";
        return EpisodeTitleFormatter.extractFileSize(episode.getName());
    }

    private String nativeEnhancedMeta(TmdbEpisode episode) {
        if (episode == null) return "";
        List<String> parts = new ArrayList<>();
        if (!TextUtils.isEmpty(episode.getDate())) parts.add(episode.getDate());
        if (episode.getRuntime() > 0) parts.add(episode.getRuntime() + "m");
        return TextUtils.join(" / ", parts);
    }

    private void bindFileSize(ViewHolder holder, String fileSize, boolean belowDate) {
        holder.binding.fileSize.setText(fileSize);
        holder.binding.fileSize.setVisibility(TextUtils.isEmpty(fileSize) ? View.GONE : View.VISIBLE);
        ViewGroup.LayoutParams params = holder.binding.fileSize.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams marginParams) {
            marginParams.topMargin = dp(holder.itemView, belowDate ? 36 : 8);
            holder.binding.fileSize.setLayoutParams(marginParams);
        }
    }

    private int episodeNumber(Episode episode, int position) {
        Integer number = episodeNumbers.get(episode);
        return number == null ? position + 1 : number;
    }

    private boolean usesFallbackStill() {
        return !items.isEmpty();
    }

    private int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private String episodeBadge(TmdbEpisode episode) {
        if (episode == null) return "";
        if (Util.isMobile()) {
            if (episode.getVoteAverage() > 0) return "★" + mobileRating(episode.getVoteAverage());
            if (episode.getRuntime() > 0) return episode.getRuntime() + "m";
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (episode.getVoteAverage() > 0) parts.add("★ " + String.format(Locale.US, "%.1f", episode.getVoteAverage()));
        if (episode.getRuntime() > 0) parts.add(episode.getRuntime() + "m");
        return TextUtils.join(" · ", parts);
    }

    private String mobileRating(double rating) {
        double rounded = Math.round(rating * 10.0) / 10.0;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.001) return String.format(Locale.US, "%.0f", rounded);
        return String.format(Locale.US, "%.1f", rounded);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        ImgUtil.clear(holder.binding.still);
        super.onViewRecycled(holder);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterTmdbEpisodeBinding binding;

        ViewHolder(@NonNull AdapterTmdbEpisodeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
