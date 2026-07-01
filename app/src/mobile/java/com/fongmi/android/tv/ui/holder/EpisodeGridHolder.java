package com.fongmi.android.tv.ui.holder;

import android.content.Context;
import android.content.ContextWrapper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.databinding.AdapterEpisodeGridBinding;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.base.BaseEpisodeHolder;
import com.fongmi.android.tv.ui.dialog.EpisodeDetailDialog;
import com.fongmi.android.tv.ui.helper.EpisodeCardPolicy;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EpisodeGridHolder extends BaseEpisodeHolder {

    private final EpisodeAdapter.OnClickListener listener;
    private final AdapterEpisodeGridBinding binding;
    private boolean useTmdbCard;
    private String fallbackStillUrl = "";

    public EpisodeGridHolder(@NonNull AdapterEpisodeGridBinding binding, EpisodeAdapter.OnClickListener listener) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
    }

    @Override
    public void setUseTmdbCard(boolean useTmdbCard) {
        this.useTmdbCard = useTmdbCard;
    }

    @Override
    public void setFallbackStillUrl(String fallbackStillUrl) {
        this.fallbackStillUrl = TextUtils.isEmpty(fallbackStillUrl) ? "" : fallbackStillUrl;
    }

    @Override
    public void initView(Episode item) {
        TmdbEpisode episode = item.getTmdbEpisode();
        if (EpisodeCardPolicy.shouldShowCard(useTmdbCard, episode != null, !TextUtils.isEmpty(fallbackStillUrl))) bindCard(item, episode);
        else bindText(item);
    }

    private void bindText(Episode item) {
        binding.card.setVisibility(View.GONE);
        binding.text.setVisibility(View.VISIBLE);
        binding.text.setActivated(item.isSelected());
        binding.text.setHorizontallyScrolling(true);
        binding.text.setText(EpisodeAdapter.getNativeTitle(item));
        setMarquee(binding.text.hasFocus() || item.isSelected());
        binding.text.setOnFocusChangeListener((view, hasFocus) -> setMarquee(hasFocus || binding.text.isActivated()));
        binding.text.setOnClickListener(v -> listener.onItemClick(item));
        binding.text.post(() -> setMarquee(binding.text.hasFocus() || binding.text.isActivated()));
        EpisodeAdapter.bindNativeTitlePopup(binding.getRoot(), item);
        EpisodeAdapter.bindNativeTitlePopup(binding.text, item);
    }

    private void bindCard(Episode item, TmdbEpisode episode) {
        binding.text.setVisibility(View.GONE);
        binding.card.setVisibility(View.VISIBLE);
        binding.card.setSelected(item.isSelected());
        bindCardActions(item, binding.getRoot(), binding.card, binding.imageFrame, binding.still, binding.textPanel, binding.cardTitle, binding.overview);

        binding.cardTitle.setText(EpisodeAdapter.getTitle(item));
        binding.cardTitle.setSelected(item.isSelected());

        String stillUrl = episode == null ? "" : episode.getStillUrl();
        String imageUrl = TextUtils.isEmpty(stillUrl) ? fallbackStillUrl : stillUrl;
        String errorImageUrl = TextUtils.isEmpty(stillUrl) ? "" : fallbackStillUrl;
        boolean hasStill = !TextUtils.isEmpty(imageUrl);
        binding.imageFrame.setVisibility(hasStill ? View.VISIBLE : View.GONE);
        binding.textPanel.setGravity(hasStill ? Gravity.NO_GRAVITY : Gravity.CENTER_VERTICAL);
        if (!hasStill) {
            Glide.with(binding.still.getContext()).clear(binding.still);
            binding.still.setImageDrawable(null);
        } else {
            ImgUtil.load(EpisodeAdapter.getTitle(item), imageUrl, errorImageUrl, binding.still, true, 0, 0);
        }

        if (episode == null || TextUtils.isEmpty(episode.getOverview())) {
            binding.overview.setVisibility(View.GONE);
        } else {
            binding.overview.setVisibility(View.VISIBLE);
            binding.overview.setText(episode.getOverview());
        }

        if (episode != null && episode.getVoteAverage() > 0) {
            binding.rating.setVisibility(View.VISIBLE);
            binding.rating.setText(String.format(Locale.US, "%.1f", episode.getVoteAverage()));
        } else {
            binding.rating.setVisibility(View.GONE);
        }

        String meta = episode == null || !hasStill ? "" : getMeta(episode);
        binding.meta.setVisibility(TextUtils.isEmpty(meta) ? View.GONE : View.VISIBLE);
        binding.meta.setText(meta);
    }

    private String getMeta(TmdbEpisode episode) {
        List<String> values = new ArrayList<>();
        if (!TextUtils.isEmpty(episode.getDate())) values.add(episode.getDate());
        if (episode.getRuntime() > 0) values.add(episode.getRuntime() + "m");
        return TextUtils.join(" / ", values);
    }

    private void setMarquee(boolean focused) {
        binding.text.setEllipsize(focused ? TextUtils.TruncateAt.MARQUEE : TextUtils.TruncateAt.END);
        binding.text.setSelected(focused);
    }

    private void bindDetailLongClick(Episode item, View... views) {
        View.OnLongClickListener longClickListener = view -> {
            FragmentActivity activity = getActivity(view);
            if (activity == null) return false;
            EpisodeDetailDialog.show(activity, item);
            return true;
        };
        for (View view : views) {
            if (view == null) continue;
            view.setOnTouchListener(null);
            view.setOnLongClickListener(longClickListener);
        }
    }

    private void bindCardActions(Episode item, View... views) {
        View.OnClickListener clickListener = view -> listener.onItemClick(item);
        for (View view : views) {
            if (view == null) continue;
            view.setOnClickListener(clickListener);
        }
        bindDetailLongClick(item, views);
    }

    private FragmentActivity getActivity(View view) {
        Context context = view.getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof FragmentActivity) return (FragmentActivity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
