package com.fongmi.android.tv.ui.adapter;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.databinding.AdapterEpisodeGridBinding;
import com.fongmi.android.tv.databinding.AdapterEpisodeHoriBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseEpisodeHolder;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.custom.EpisodeTitlePopup;
import com.fongmi.android.tv.ui.holder.EpisodeGridHolder;
import com.fongmi.android.tv.ui.holder.EpisodeHoriHolder;
import com.fongmi.android.tv.utils.EpisodeTitleFormatter;
import com.fongmi.android.tv.utils.EpisodeTitleCompact;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.List;

public class EpisodeAdapter extends RecyclerView.Adapter<BaseEpisodeHolder> {

    private final OnClickListener listener;
    private final List<Episode> mItems;
    private int viewType;
    private boolean useTmdbCard;
    private String fallbackStillUrl = "";

    public EpisodeAdapter(OnClickListener listener, int viewType) {
        this(listener, viewType, new ArrayList<>());
    }

    public EpisodeAdapter(OnClickListener listener, int viewType, ArrayList<Episode> items) {
        this.listener = listener;
        this.viewType = viewType;
        this.mItems = items;
    }

    public interface OnClickListener {

        void onItemClick(Episode item);
    }

    public void addAll(List<Episode> items) {
        EpisodeTitleCompact.apply(items);
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void setUseTmdbCard(boolean useTmdbCard) {
        if (this.useTmdbCard == useTmdbCard) return;
        this.useTmdbCard = useTmdbCard;
        notifyDataSetChanged();
    }

    public void setViewType(int viewType) {
        if (this.viewType == viewType) return;
        this.viewType = viewType;
        notifyDataSetChanged();
    }

    public boolean isUsingTmdbCard() {
        return useTmdbCard;
    }

    public void setFallbackStillUrl(String fallbackStillUrl) {
        String value = TextUtils.isEmpty(fallbackStillUrl) ? "" : fallbackStillUrl;
        if (this.fallbackStillUrl.equals(value)) return;
        this.fallbackStillUrl = value;
        if (useTmdbCard) notifyDataSetChanged();
    }

    public int getPosition() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isSelected()) return i;
        return 0;
    }

    public int getPosition(Episode item) {
        return mItems.indexOf(item);
    }

    public Episode getActivated() {
        return mItems.get(getPosition());
    }

    public Episode getNext() {
        int current = getPosition();
        int max = getItemCount() - 1;
        current = ++current > max ? max : current;
        return mItems.get(current);
    }

    public Episode getPrev() {
        int current = getPosition();
        current = --current < 0 ? 0 : current;
        return mItems.get(current);
    }

    public List<Episode> getItems() {
        return mItems;
    }

    public boolean isEmpty() {
        return getItemCount() == 0;
    }

    /**
     * 绑定标题和长按事件（供 Holder 调用）
     */
    public static String getTitle(Episode item) {
        if (item == null) return "";
        TmdbEpisode tmdbEpisode = item.getTmdbEpisode();
        if (tmdbEpisode != null) return getTmdbTitle(item, tmdbEpisode);
        return getNativeTitle(item);
    }

    public static String getNativeTitle(Episode item) {
        if (item == null) return "";
        String title = TextUtils.isEmpty(item.getDisplayName()) ? item.getName() : item.getDisplayName();
        if (TextUtils.isEmpty(item.getDesc()) || title.startsWith(item.getDesc())) return title;
        return item.getDesc().concat(title);
    }

    private static String getTmdbTitle(Episode item, TmdbEpisode tmdbEpisode) {
        String title = getCardTitle(item);
        return EpisodeTitleFormatter.withSourceFileSize(item.getName(), title, Setting.isTmdbEpisodeFileSize());
    }

    public static String getCardTitle(Episode item) {
        if (item == null) return "";
        TmdbEpisode tmdbEpisode = item.getTmdbEpisode();
        if (tmdbEpisode == null) return getNativeTitle(item);
        int number = tmdbEpisode.getNumber();
        String label = number > 0 ? String.valueOf(number) : item.getName();
        String title = EpisodeTitleFormatter.formatTmdbTitle(label, item.getName(), tmdbEpisode.getTitle(), Setting.getTmdbEpisodeShowScrapedName());
        if (TextUtils.isEmpty(title)) title = TextUtils.isEmpty(item.getName()) ? item.getDisplayName() : item.getName();
        return title;
    }

    public static String getCardFileSize(Episode item, String title) {
        return getCardFileSize(item, title, Setting.isTmdbEpisodeFileSize());
    }

    static String getCardFileSize(Episode item, String title, boolean includeFileSize) {
        if (item == null || !includeFileSize) return "";
        String fileSize = EpisodeTitleFormatter.extractFileSize(item.getName());
        if (TextUtils.isEmpty(fileSize) || EpisodeTitleFormatter.containsFileSize(title)) return "";
        return fileSize;
    }

    public static void bindTitle(MaterialTextView text, Episode item) {
        String title = getTitle(item);
        text.setText(title);
        applyMarquee(text, item.isSelected(), text.hasFocus());
        text.setOnFocusChangeListener((view, hasFocus) -> applyMarquee(text, item.isSelected(), hasFocus));
        bindTitlePopup(text, item);
    }

    public static void bindTitlePopup(View view, Episode item) {
        bindTitlePopup(view, item, true);
    }

    public static void bindNativeTitlePopup(View view, Episode item) {
        bindTitlePopup(view, item, false);
    }

    private static void bindTitlePopup(View view, Episode item, boolean tmdbTitle) {
        if (view == null) return;
        view.setOnLongClickListener(anchor -> showTitlePopup(anchor, item, tmdbTitle));
        view.setOnTouchListener(new View.OnTouchListener() {
            private final Handler handler = new Handler(Looper.getMainLooper());
            private final int slop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
            private float downX;
            private float downY;
            private boolean shown;
            private final Runnable show = () -> shown = showTitlePopup(view, item, tmdbTitle);

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        shown = false;
                        downX = event.getX();
                        downY = event.getY();
                        handler.postDelayed(show, ViewConfiguration.getLongPressTimeout());
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getX() - downX) > slop || Math.abs(event.getY() - downY) > slop) handler.removeCallbacks(show);
                        return false;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(show);
                        return shown;
                    default:
                        return false;
                }
            }
        });
    }

    public static boolean showTitlePopup(View anchor, Episode item) {
        return showTitlePopup(anchor, item, true);
    }

    private static boolean showTitlePopup(View anchor, Episode item, boolean tmdbTitle) {
        return EpisodeTitlePopup.show(anchor, tmdbTitle ? getTitle(item) : getNativeTitle(item));
    }

    public static void dismissTitlePopup() {
        EpisodeTitlePopup.dismiss();
    }

    private static void applyMarquee(MaterialTextView text, boolean selected, boolean focused) {
        text.setSelected(selected || focused);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @Override
    public int getItemViewType(int position) {
        return viewType;
    }

    @Override
    public void onBindViewHolder(@NonNull BaseEpisodeHolder holder, int position) {
        holder.setUseTmdbCard(useTmdbCard);
        holder.setFallbackStillUrl(fallbackStillUrl);
        holder.initView(mItems.get(position));
    }

    @NonNull
    @Override
    public BaseEpisodeHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == ViewType.HORI) {
            return new EpisodeHoriHolder(AdapterEpisodeHoriBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), listener);
        } else {
            return new EpisodeGridHolder(AdapterEpisodeGridBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), listener);
        }
    }
}
