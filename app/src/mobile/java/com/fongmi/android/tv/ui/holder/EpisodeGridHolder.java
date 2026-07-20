package com.fongmi.android.tv.ui.holder;

import android.content.Context;
import android.content.ContextWrapper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

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
import com.fongmi.android.tv.ui.helper.TmdbEpisodeMatcher;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EpisodeGridHolder extends BaseEpisodeHolder {

    private final EpisodeAdapter.OnClickListener listener;
    private final AdapterEpisodeGridBinding binding;
    private boolean useTmdbCard;
    private String fallbackStillUrl = "";
    private final int maxSingleWidth;
    private final int horizontalPadding;

    public EpisodeGridHolder(@NonNull AdapterEpisodeGridBinding binding, EpisodeAdapter.OnClickListener listener) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
        this.maxSingleWidth = ResUtil.getScreenWidth();
        this.horizontalPadding = ResUtil.dp2px(12);
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
        updateLayout();
        // 验证 TMDB 匹配：只有文件名有有效集号且与 TMDB 集号一致时才匹配
        TmdbEpisode episode = item.getTmdbEpisode();
        if (!TmdbEpisodeMatcher.shouldApply(item, episode)) {
            episode = null;
        }
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

        String cardTitle = EpisodeAdapter.getCardTitle(item, episode);
        binding.cardTitle.setText(cardTitle);
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
            ImgUtil.load(cardTitle, imageUrl, errorImageUrl, binding.still, true, 0, 0);
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
        boolean showMeta = !TextUtils.isEmpty(meta);
        binding.meta.setVisibility(showMeta ? View.VISIBLE : View.GONE);
        binding.meta.setText(meta);
        bindFileSize(EpisodeAdapter.getCardFileSize(item, cardTitle), showMeta);
    }

    private String getMeta(TmdbEpisode episode) {
        List<String> values = new ArrayList<>();
        if (!TextUtils.isEmpty(episode.getDate())) values.add(episode.getDate());
        if (episode.getRuntime() > 0) values.add(episode.getRuntime() + "m");
        return TextUtils.join(" / ", values);
    }

    private void bindFileSize(String fileSize, boolean belowMeta) {
        binding.fileSize.setText(fileSize);
        binding.fileSize.setVisibility(TextUtils.isEmpty(fileSize) ? View.GONE : View.VISIBLE);
        ViewGroup.LayoutParams params = binding.fileSize.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams marginParams) {
            marginParams.topMargin = ResUtil.dp2px(belowMeta ? 36 : 8);
            binding.fileSize.setLayoutParams(marginParams);
        }
    }

    private void updateLayout() {
        boolean single = getBindingAdapter() != null && getBindingAdapter().getItemCount() == 1;
        ViewGroup.LayoutParams params = binding.text.getLayoutParams();
        int width = single ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT;
        if (params.width != width) {
            params.width = width;
            binding.text.setLayoutParams(params);
        }
        binding.text.setMaxWidth(single ? maxSingleWidth : Integer.MAX_VALUE);
        binding.text.setPadding(horizontalPadding, 0, horizontalPadding, 0);
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
